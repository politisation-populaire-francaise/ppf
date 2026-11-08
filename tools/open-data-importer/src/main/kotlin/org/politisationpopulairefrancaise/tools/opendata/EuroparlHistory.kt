package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef
import org.politisationpopulairefrancaise.core.voir.parlement.Position

/*
 * The history of the roll-call votes of the European Parliament (voir-historique-sources.md): the
 * vote of each member is published for the roll-call votes only (`VOTE_ELECTRONIC_ROLLCALL`), and
 * only since 15 July 2019 (9th term). The distribution counts the members elected in France, per
 * national party at the date of the vote (`hasMembership`, `NATIONAL_POLITICAL_GROUP`); the totals
 * are those of the whole Parliament. Only counts are kept: no name, no nominal vote.
 */
object EuroparlHistory {
    /** The terms imported, with the first and last day of their roll-call votes with the vote of each member. */
    val TERMS: Map<Int, Pair<String, String?>> = linkedMapOf(
        9 to ("2019-07-15" to "2024-07-15"),
        10 to (Europarl.TERM_START to null),
    )

    private const val NATIONAL = "NATIONAL_POLITICAL_GROUP"
    private const val EU_GROUP = "EU_POLITICAL_GROUP"
    const val NO_PARTY: String = "aucun"

    private class Mep(val parties: List<Span>, val seats: List<Span>)

    /** The packages of every term up to [today], in order. */
    fun packages(api: EuroparlApi, today: String, example: Boolean): List<HistoryPackage> {
        val bodies = HashMap<String, EpBody?>()
        val sittingsByYear = HashMap<Int, List<String>>()
        return TERMS.mapNotNull { (term, range) ->
            val (first, last) = range
            if (first > today) return@mapNotNull null
            val end = last?.takeIf { it < today } ?: today
            term(api, term, first, end, example, bodies, sittingsByYear)
        }
    }

    private fun term(
        api: EuroparlApi,
        term: Int,
        first: String,
        last: String,
        example: Boolean,
        bodies: HashMap<String, EpBody?>,
        sittingsByYear: HashMap<Int, List<String>>,
    ): HistoryPackage {
        val ids = Interner()
        val meps = api.frenchMeps(term).associate { id ->
            val memberships = api.memberships(id)
            // Only the dated memberships of a national party or a European group; nothing else of the member.
            ids.of(id) to Mep(
                parties = memberships.filter { it.classification == NATIONAL }.map { Span(ids.of(it.organization), it.start, it.end) },
                seats = memberships.filter { it.classification == EU_GROUP || it.classification == NATIONAL }.map { Span(it.organization, it.start, it.end) },
            )
        }
        if (meps.isEmpty()) throw UnreadableSourceException("europarl: no member elected in France in term $term")

        fun lineOf(mep: Mep?, day: String) = Line("pe-org:${mep?.parties?.at(day)?.organ ?: NO_PARTY}", PartyBasis.PARTY)
        val membersByDay = HashMap<String, Map<Line, Int>>()
        fun members(day: String) = membersByDay.getOrPut(day) {
            val counts = HashMap<Line, Int>()
            meps.values.forEach { mep -> if (mep.seats.any { it.covers(day) }) lineOf(mep, day).let { counts[it] = (counts[it] ?: 0) + 1 } }
            counts
        }

        val sittings = (first.take(4).toInt()..last.take(4).toInt())
            .flatMap { year -> sittingsByYear.getOrPut(year) { api.plenarySittings(year) } }
            .distinct().sorted()
            .filter { JsonEuroparlApi.sittingDate(it) in first..last }
        val lines = HashSet<Line>()
        val ballots = sittings.flatMap { sitting ->
            val rollCalls = api.decisions(sitting).filter { it.method == "VOTE_ELECTRONIC_ROLLCALL" }.sortedWith(compareBy({ it.order ?: Int.MAX_VALUE }, { it.id }))
            rollCalls.mapIndexed { index, d ->
                // The same number as in the monthly packages: activity_order, or the rank in the sitting.
                val number = d.order ?: (index + 1)
                val distribution = Distribution()
                listOf(d.votersInFavour to Position.FOR, d.votersAgainst to Position.AGAINST, d.votersAbstaining to Position.ABSTENTION).forEach { (voters, position) ->
                    voters.distinct().forEach { voter -> meps[voter]?.let { distribution.add(lineOf(it, d.date), position) } }
                }
                val present = members(d.date)
                // Every party with members that day has a line; its members on no list of the roll
                // call did not vote (absent or present without voting: the source does not tell).
                present.keys.forEach { distribution.add(it, Position.NOT_VOTING, 0) }
                val built = distribution.build { present[it] ?: 0 }.map { line ->
                    line.copy(notVoting = maxOf(0, line.members - line.inFavour - line.against - line.abstention))
                }
                lines += distribution.lines
                HistoryBallot(
                    id = "pe:${d.date}:$number",
                    date = d.date,
                    title = d.title ?: "Vote par appel nominal n° $number",
                    kind = "appel-nominal",
                    text = d.reference,
                    adopted = when {
                        d.outcome == null -> null
                        d.outcome.endsWith("ADOPTED") -> true
                        d.outcome.endsWith("REJECTED") -> false
                        else -> null
                    },
                    counts = Counts(
                        inFavour = d.inFavour ?: d.votersInFavour.size,
                        against = d.against ?: d.votersAgainst.size,
                        abstention = d.abstention ?: d.votersAbstaining.size,
                    ),
                    parties = built,
                    url = "${Europarl.SITE}/doceo/document/PV-$term-${d.date}-RCV_FR.html",
                )
            }
        }.distinctBy { it.id }
        val parties = lines.map { line -> partyRef(line, bodies, api) }
        return buildHistoryPackage(example, Chamber.PE, term, ballots, parties)
    }

    private fun partyRef(line: Line, bodies: HashMap<String, EpBody?>, api: EuroparlApi): PartyRef {
        val id = line.id.removePrefix("pe-org:")
        if (id == NO_PARTY) return partyRef(line, "Sans parti national connu à cette date", null)
        val body = bodies.getOrPut(id) { api.body(id) }
        return partyRef(line, body?.label ?: "Organisation $id", body?.shortLabel)
    }
}
