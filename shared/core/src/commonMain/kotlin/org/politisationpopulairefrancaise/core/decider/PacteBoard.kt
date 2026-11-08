package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/** What the public table shows for one candidate and one measure (M13.5: « signé, refusé, sans réponse »). */
enum class CellStatus {
    SIGNED,
    REFUSED,
    /** No answer yet, and the deadline has not passed. */
    AWAITING,
    /** No answer, and the deadline has passed (or the election is over). */
    NO_ANSWER,
}

/** One cell of the table, with what the candidate did afterwards on this measure. */
data class BoardCell(val measure: PacteMeasure, val status: CellStatus, val comment: String?, val followUps: List<PacteFollowUp>)

/** One candidate's line: every measure, in the order of the pact, whatever the answers. */
data class CandidateRow(
    val candidate: ContentAddress,
    val name: String,
    /** The answer counted, null when none was received in time. */
    val engagement: Engagement?,
    val cells: List<BoardCell>,
) {
    val signed: Int get() = cells.count { it.status == CellStatus.SIGNED }
    val refused: Int get() = cells.count { it.status == CellStatus.REFUSED }
    val unanswered: Int get() = cells.count { it.status == CellStatus.NO_ANSWER || it.status == CellStatus.AWAITING }
}

/** How the candidates answered one measure. */
data class MeasureSummary(val measure: PacteMeasure, val signed: Int, val refused: Int, val unanswered: Int)

/** Why an answer received is not counted; shown below the table, for transparency. */
enum class SetAsideReason {
    /** The answer was given to another text than the one sent to everyone. */
    OTHER_TEXT,
    /** The answer comes from someone who is not among the candidates listed by the pact. */
    NOT_A_CANDIDATE,
    /**
     * The answer was received after the deadline: not counted, the same rule for everyone
     * (owner's decision of 7 November 2026, D-062: « si le candidat est en retard c'est qu'il
     * n'a pas pris les choses au sérieux »).
     */
    LATE,
}

data class SetAside(val engagement: Engagement, val reason: SetAsideReason)

/**
 * The public table of a pact, by candidate and by measure (M13.5). Its rules are the same for
 * everyone, which is what gives the pact its weight (cahier des charges, 9.2: « traités de façon
 * strictement identique »):
 * - every candidate listed by the pact has a line, with every measure, answered or not;
 * - the lines are in alphabetical order of the names, never in the order of the answers;
 * - only an answer to the exact text sent ([Pacte.fingerprint]) counts; the others are listed apart;
 * - a measure left unanswered is « en attente » until the deadline, then « sans réponse »;
 * - an answer received after the deadline is not counted; it is listed apart, so that everyone
 *   can see it arrived late (D-062).
 */
class PacteBoard(
    val pacte: Pacte,
    val rows: List<CandidateRow>,
    val summaries: List<MeasureSummary>,
    val setAside: List<SetAside>,
    /** The deadline has passed: the table is final for the answers. */
    val deadlinePassed: Boolean,
) {
    /** A pact that was not sent yet has no table: the starting list is still to be chosen. */
    val isOpenForAnswers: Boolean get() = pacte.status != PacteStatus.PROPOSED

    val answeredCandidates: Int get() = rows.count { it.engagement != null }

    fun row(candidate: ContentAddress): CandidateRow? = rows.firstOrNull { it.candidate == candidate }

    companion object {
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        fun build(
            pacte: Pacte,
            engagements: List<Engagement>,
            nameOf: (ContentAddress) -> String?,
            followUps: List<PacteFollowUp>,
            today: CivilDate,
        ): PacteBoard {
            val deadline = pacte.deadline
            val deadlinePassed = pacte.status == PacteStatus.CLOSED || (deadline != null && today > deadline)
            if (pacte.status == PacteStatus.PROPOSED) {
                return PacteBoard(pacte, emptyList(), emptyList(), emptyList(), deadlinePassed = false)
            }
            val candidates = pacte.candidates.toSet()
            val mine = engagements.filter { it.pacte == pacte.address }
            val late: (Engagement) -> Boolean = { deadline != null && it.receivedOn > deadline }
            val setAside = buildList {
                mine.forEach { e ->
                    when {
                        e.candidate !in candidates -> add(SetAside(e, SetAsideReason.NOT_A_CANDIDATE))
                        e.textFingerprint != pacte.fingerprint -> add(SetAside(e, SetAsideReason.OTHER_TEXT))
                        late(e) -> add(SetAside(e, SetAsideReason.LATE))
                    }
                }
            }
            val counted = mine.filter { it.candidate in candidates && it.textFingerprint == pacte.fingerprint && !late(it) }
                .groupBy { it.candidate }
                .mapValues { (_, list) -> list.maxWith(compareBy<Engagement>({ it.receivedOn }, { it.article.updatedAt })) }
            val follow = followUps.filter { it.pacte == pacte.address }
            val rows = pacte.candidates.distinct().map { candidate ->
                val engagement = counted[candidate]
                val cells = pacte.measures.map { measure ->
                    val answer = engagement?.answer(measure.id)
                    val status = when {
                        answer?.status == AnswerStatus.SIGNED -> CellStatus.SIGNED
                        answer?.status == AnswerStatus.REFUSED -> CellStatus.REFUSED
                        deadlinePassed -> CellStatus.NO_ANSWER
                        else -> CellStatus.AWAITING
                    }
                    val facts = follow.filter { it.measureId == measure.id && candidate in it.persons }.sortedByDescending { it.date }
                    BoardCell(measure, status, answer?.comment, facts)
                }
                CandidateRow(
                    candidate = candidate,
                    name = nameOf(candidate) ?: candidate.identifier,
                    engagement = engagement,
                    cells = cells,
                )
            }.sortedWith(compareBy<CandidateRow>({ sortKey(it.name) }, { it.candidate.toString() }))
            val summaries = pacte.measures.mapIndexed { index, measure ->
                MeasureSummary(
                    measure = measure,
                    signed = rows.count { it.cells[index].status == CellStatus.SIGNED },
                    refused = rows.count { it.cells[index].status == CellStatus.REFUSED },
                    unanswered = rows.count { it.cells[index].status == CellStatus.NO_ANSWER || it.cells[index].status == CellStatus.AWAITING },
                )
            }
            return PacteBoard(pacte, rows, summaries, setAside, deadlinePassed)
        }

        /** Alphabetical order of the names, accents and case ignored, the example marker aside. */
        fun sortKey(name: String): String = FrenchText.fold(name.removePrefix(EXAMPLE_MARKER).trim())
    }
}
