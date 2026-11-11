package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * How the person wants the document (code des relations entre le public et l'administration, article L311-9): by
 * e-mail, free when it exists in electronic form; a copy, at the cost of reproduction at most; or consulted on the spot,
 * free. [key] is stored and must not change.
 */
enum class DocumentAccessMode(val key: String) {
    EMAIL("courriel"),
    COPY("copie"),
    CONSULTATION("consultation"),
    ;

    companion object {
        fun fromKey(key: String?): DocumentAccessMode = entries.firstOrNull { it.key == key } ?: EMAIL
    }
}

/**
 * One request for an administrative document (DE-31), as the person notes it on the device after sending it herself or
 * himself: to which administration, which document, how, and the days that count. Never sent, never published from
 * here (publishing the answer, with the data of third parties masked, comes with M4).
 */
data class DocumentRequestNote(
    val id: String,
    val administration: String,
    val document: String,
    val mode: DocumentAccessMode,
    /** The day the administration received the request (for an e-mail, the day it left): the deadlines run from it. */
    val receivedOn: CivilDate,
    /** The day the document, or an answer that settles the request, arrived. */
    val answeredOn: CivilDate? = null,
    /** The day a written refusal was notified; null while there is none (silence counts after [DocumentRequestTimeline.answerBy]). */
    val refusedOn: CivilDate? = null,
    /** The day the CADA registered the referral (its acknowledgement says it). */
    val cadaOn: CivilDate? = null,
)

/** Where a request stands on a given day (DE-31). */
enum class DocumentRequestStage {
    /** Within the month the administration has to answer (R311-13). */
    WAITING,

    /** Refused, in writing or by a month of silence: the CADA can be asked within two months (R343-1). */
    REFUSED,

    /** The two months to ask the CADA are over: a new request remains possible. */
    CADA_TOO_LATE,

    /** The CADA was asked: its opinion within a month (R343-3); the administration's silence is a refusal after two (R343-5). */
    WITH_CADA,

    /** Two months after the CADA registered the referral, without the document: a refusal, which the administrative court can hear. */
    REFUSED_AFTER_CADA,

    /** The document, or an answer that settles the request, arrived. */
    ANSWERED,
}

/**
 * The deadlines of a request for an administrative document, counted on the device from the days the person noted
 * (verified on Légifrance on 9 November 2026, `docs/integration/demander-un-document.md`):
 * - the administration has one month from the reception of the request (CRPA, R311-13), and its silence is a refusal
 *   (R*311-12, which R311-13 refers to; service-public.gouv.fr, F2467);
 * - the CADA can be asked within two months of the refusal, written or tacit (R343-1); asking it is required before
 *   going to court (L342-1);
 * - the CADA gives its opinion within one month of registering the referral (R343-3); without the document two months
 *   after that registration, the administration has refused (R*343-4, R343-5).
 * The days are indicative: a month runs from date to date (the 31st becomes the last day of a shorter month).
 */
data class DocumentRequestTimeline(
    /** Last day of the month the administration has to answer. */
    val answerBy: CivilDate,
    /** The refusal the deadlines run from: written, or tacit the day after [answerBy]; null while neither. */
    val refusal: CivilDate?,
    /** Last day to ask the CADA; null without refusal. */
    val cadaBy: CivilDate?,
    /** Day by which the CADA should give its opinion; null before the referral. */
    val cadaOpinionBy: CivilDate?,
    /** Day from which the administration's silence after the referral is a refusal; null before the referral. */
    val refusedAfterCadaOn: CivilDate?,
) {
    companion object {
        fun of(note: DocumentRequestNote, today: CivilDate): DocumentRequestTimeline {
            val answerBy = note.receivedOn.plusMonths(1)
            val tacit = answerBy.plusDays(1)
            val refusal = note.refusedOn ?: tacit.takeIf { today >= it }
            return DocumentRequestTimeline(
                answerBy = answerBy,
                refusal = refusal,
                cadaBy = refusal?.plusMonths(2),
                cadaOpinionBy = note.cadaOn?.plusMonths(1),
                refusedAfterCadaOn = note.cadaOn?.plusMonths(2),
            )
        }

        fun stageOf(note: DocumentRequestNote, today: CivilDate): DocumentRequestStage {
            if (note.answeredOn != null) return DocumentRequestStage.ANSWERED
            val timeline = of(note, today)
            note.cadaOn?.let {
                val refusedOn = timeline.refusedAfterCadaOn ?: return DocumentRequestStage.WITH_CADA
                return if (today >= refusedOn) DocumentRequestStage.REFUSED_AFTER_CADA else DocumentRequestStage.WITH_CADA
            }
            val cadaBy = timeline.cadaBy ?: return DocumentRequestStage.WAITING
            return if (today > cadaBy) DocumentRequestStage.CADA_TOO_LATE else DocumentRequestStage.REFUSED
        }
    }
}

/** The day [months] months later, from date to date; a day that does not exist becomes the last day of that month. */
fun CivilDate.plusMonths(months: Int): CivilDate {
    val index = year * 12 + (month - 1) + months
    val newYear = index.floorDiv(12)
    val newMonth = index.mod(12) + 1
    return CivilDate(newYear, newMonth, minOf(day, ParisTime.daysInMonth(newYear, newMonth)))
}

/** The day [days] days later. */
fun CivilDate.plusDays(days: Int): CivilDate = CivilDate.fromEpochDays(toEpochDays() + days)

/**
 * The reminders of a request (DE-31, contract `docs/integration/rappels.md`), at 10 h, Paris time, asked by the person
 * for this request only: the day after the month of the administration is over without an answer, and seven days
 * before the last day to ask the CADA.
 */
object DocumentRequestReminders {
    const val OWNER_PREFIX: String = "decider:document:"

    const val NO_ANSWER: String = "sans-reponse"
    const val CADA: String = "cada"

    /** Seven days: the time to gather the request and the refusal and to fill in the CADA's form. */
    const val CADA_DAYS_BEFORE: Int = 7

    fun owner(note: DocumentRequestNote): String = OWNER_PREFIX + note.id

    fun reminderId(note: DocumentRequestNote, key: String): String = owner(note) + "/" + key

    /** The refusal the CADA deadline runs from: the written one, or the silence of a month (the day after it). */
    fun refusalOf(note: DocumentRequestNote): CivilDate = note.refusedOn ?: note.receivedOn.plusMonths(1).plusDays(1)

    /** The last day to ask the CADA, whether or not the month of silence is over yet. */
    fun cadaBy(note: DocumentRequestNote): CivilDate = refusalOf(note).plusMonths(2)

    /** `key` to due time, those after [now] only; nothing once the document arrived or the CADA was asked. */
    fun plan(note: DocumentRequestNote, now: Long): List<Pair<String, Long>> {
        if (note.answeredOn != null || note.cadaOn != null) return emptyList()
        val refusal = refusalOf(note)
        return buildList {
            if (note.refusedOn == null) add(NO_ANSWER to ParisTime.toEpochSeconds(refusal, ElectionReminders.HOUR, 0))
            val cadaBy = cadaBy(note)
            val soon = CivilDate.fromEpochDays(maxOf(cadaBy.toEpochDays() - CADA_DAYS_BEFORE, refusal.toEpochDays() + 1))
            add(CADA to ParisTime.toEpochSeconds(soon, ElectionReminders.HOUR, 0))
        }.filter { it.second > now }.sortedBy { it.second }
    }
}

/**
 * The requests for administrative documents noted on the device (table `document_request`, DE-31). Part of the quick
 * erase (S-44) as [ERASABLE_ID].
 */
class DocumentRequestStore(
    private val database: PpfDatabase,
    private val newId: () -> String = { secureRandomBytes(16).toHexString() },
) : DeviceErasable {

    private val queries get() = database.documentRequestsQueries

    override val erasableId: String = ERASABLE_ID

    /** Notes a request the person just sent, received by the administration on [receivedOn]. */
    fun add(administration: String, document: String, mode: DocumentAccessMode, receivedOn: CivilDate): DocumentRequestNote {
        val note = DocumentRequestNote(newId(), administration.trim().take(MAX_ADMINISTRATION), document.trim().take(MAX_DOCUMENT), mode, receivedOn)
        queries.insertRequest(note.id, note.administration, note.document, note.mode.key, receivedOn.toEpochDays())
        return note
    }

    fun setAnswered(id: String, day: CivilDate?) = queries.setAnswered(day?.toEpochDays(), id)

    fun setRefused(id: String, day: CivilDate?) = queries.setRefused(day?.toEpochDays(), id)

    fun setCada(id: String, day: CivilDate?) = queries.setCada(day?.toEpochDays(), id)

    fun remove(id: String) = queries.deleteRequest(id)

    fun request(id: String): DocumentRequestNote? = queries.selectRequest(id).executeAsOneOrNull()?.let(::toNote)

    /** Every request noted, the latest received first. */
    fun all(): List<DocumentRequestNote> = queries.selectRequests().executeAsList().map(::toNote)

    fun clear() = queries.deleteAllRequests()

    override suspend fun eraseFromDevice() {
        clear()
        check(queries.countRequests().executeAsOne() == 0L) { "document requests left on the device" }
    }

    private fun toNote(row: org.politisationpopulairefrancaise.core.db.Document_request) = DocumentRequestNote(
        id = row.id,
        administration = row.administration,
        document = row.document,
        mode = DocumentAccessMode.fromKey(row.mode),
        receivedOn = CivilDate.fromEpochDays(row.received_on),
        answeredOn = row.answered_on?.let(CivilDate::fromEpochDays),
        refusedOn = row.refused_on?.let(CivilDate::fromEpochDays),
        cadaOn = row.cada_on?.let(CivilDate::fromEpochDays),
    )

    companion object {
        /** The part of the quick erase (S-44) that empties the requests for documents. */
        const val ERASABLE_ID: String = "decider-documents"

        const val MAX_ADMINISTRATION: Int = 200
        const val MAX_DOCUMENT: Int = 2_000
    }
}
