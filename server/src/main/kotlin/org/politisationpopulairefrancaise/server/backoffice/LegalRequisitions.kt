package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.politisationpopulairefrancaise.server.legal.RequisitionRows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The search a judicial requisition asks of the legal register (D-077): a public key and/or the id of an
 * event, and a period of reception [from, until). It lives only in the pending approval (two-person rule),
 * never in the journal, and leaves the approval once answered (database function answer_requisition) or
 * once its moment has passed (periodic task of the back-office).
 */
data class RequisitionSearch(val pubkey: String?, val eventId: String?, val from: Instant, val until: Instant) {
    init {
        require(pubkey != null || eventId != null) { "A requisition names a key or an event" }
        require(until.isAfter(from)) { "The period of a requisition is not empty" }
    }

    /** The entries of the approval's payload (dates in seconds). */
    fun payload(): Map<String, String> = buildMap {
        pubkey?.let { put(PUBKEY, it) }
        eventId?.let { put(EVENT, it) }
        put(FROM, from.epochSecond.toString())
        put(UNTIL, until.epochSecond.toString())
    }

    companion object {
        const val PUBKEY: String = "pubkey"
        const val EVENT: String = "event"
        const val FROM: String = "from"
        const val UNTIL: String = "until"

        /** What a requisition's approval holds of the search: never journaled, erased after the answer. */
        val FIELDS: Set<String> = setOf(PUBKEY, EVENT, FROM, UNTIL)

        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /** The search of an approval's payload, or null once it has been answered or forgotten. */
        fun of(payload: Map<String, String>): RequisitionSearch? {
            val from = payload[FROM]?.toLongOrNull() ?: return null
            val until = payload[UNTIL]?.toLongOrNull() ?: return null
            val pubkey = payload[PUBKEY]?.takeIf(::isHexKey)
            val event = payload[EVENT]?.takeIf(::isHexKey)
            if ((pubkey == null && event == null) || until <= from) return null
            return RequisitionSearch(pubkey, event, Instant.ofEpochSecond(from), Instant.ofEpochSecond(until))
        }

        /**
         * The search typed by the team: a key (npub or 64 hexadecimal characters) and/or an event id (64
         * hexadecimal characters), and two dates AAAA-MM-JJ, Paris time, the end included.
         */
        fun parse(key: String?, event: String?, from: String?, until: String?): Outcome<RequisitionSearch> {
            val keyText = key?.trim()?.takeIf { it.isNotEmpty() }
            val eventText = event?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (keyText == null && eventText == null) return Outcome.Denied("search_required")
            val pubkey = keyText?.let { BackOffice.parseKey(it) ?: return Outcome.Denied("bad_key") }
            val eventId = eventText?.let { if (isHexKey(it)) it else return Outcome.Denied("bad_event_id") }
            val fromText = from?.trim()?.takeIf { it.isNotEmpty() }
            val untilText = until?.trim()?.takeIf { it.isNotEmpty() }
            if (fromText == null || untilText == null) return Outcome.Denied("period_required")
            val (start, end) = try {
                LocalDate.parse(fromText).atStartOfDay(PARIS).toInstant() to LocalDate.parse(untilText).plusDays(1).atStartOfDay(PARIS).toInstant()
            } catch (e: DateTimeParseException) {
                return Outcome.Denied("bad_date")
            }
            if (!end.isAfter(start)) return Outcome.Denied("bad_period")
            return Outcome.Ok(RequisitionSearch(pubkey, eventId, start, end))
        }
    }
}

/** Where a requisition stands, as its page shows it. */
enum class RequisitionState {
    /** Waiting for a second holder of `legal.requisition`. */
    WAITING,

    /** Approved: its answer may be downloaded once, within [LegalRequisitions.DOWNLOAD_WINDOW]. */
    READY,
    ANSWERED,

    /** Approved, but not downloaded in time: the search was forgotten, a new request is needed. */
    LAPSED,
    REFUSED,
    EXPIRED,
    CANCELLED,
    FAILED,
}

/** The answer to one requisition, built once from the register (the rows never enter the journal). */
data class RequisitionAnswer(
    val requisitionId: String,
    val reference: String,
    val search: RequisitionSearch,
    val rows: RequisitionRows,
    val generatedAt: Instant,
)

object LegalRequisitions {
    /** The journal action of an answer (reference and counts only). */
    const val ACTION_ANSWERED: String = "legal.requisition_answered"

    /** The payload entry the database writes once the requisition is answered (seconds). */
    const val ANSWERED_AT: String = "answered_at"

    /** An approved requisition is downloaded within this, or forgotten. */
    val DOWNLOAD_WINDOW: Duration = 7.days

    private val UTC_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private val pretty = Json { prettyPrint = true }

    fun state(approval: PendingApproval, now: Instant): RequisitionState = when (approval.status) {
        ApprovalStatus.PENDING -> if (approval.expiresAt.isAfter(now)) RequisitionState.WAITING else RequisitionState.EXPIRED
        ApprovalStatus.APPROVED -> when {
            approval.payload.containsKey(ANSWERED_AT) -> RequisitionState.ANSWERED
            RequisitionSearch.of(approval.payload) == null -> RequisitionState.LAPSED
            approval.decidedAt?.plusSeconds(DOWNLOAD_WINDOW.inWholeSeconds)?.isAfter(now) == true -> RequisitionState.READY
            else -> RequisitionState.LAPSED
        }
        ApprovalStatus.REFUSED -> RequisitionState.REFUSED
        ApprovalStatus.EXPIRED -> RequisitionState.EXPIRED
        ApprovalStatus.CANCELLED -> RequisitionState.CANCELLED
        ApprovalStatus.FAILED -> RequisitionState.FAILED
    }

    /** Whether [accountId] may download the answer of [approval] now: ready, and they asked for it or approved it. */
    fun mayDownload(approval: PendingApproval, accountId: UUID, now: Instant): Boolean =
        state(approval, now) == RequisitionState.READY && (approval.requestedBy == accountId || approval.decidedBy == accountId)

    fun utc(instant: Instant): String = UTC_SECONDS.format(instant)

    /**
     * The file given to the authority: the reference, the search, what the register is and holds, then the rows
     * exactly as the database built them.
     */
    fun json(answer: RequisitionAnswer): String {
        val search = buildMap<String, JsonElement> {
            answer.search.pubkey?.let { put("pubkey", JsonPrimitive(it)) }
            answer.search.eventId?.let { put("event_id", JsonPrimitive(it)) }
            put("received_from", JsonPrimitive(utc(answer.search.from)))
            put("received_until", JsonPrimitive(utc(answer.search.until)))
        }
        val document = JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(FORMAT),
                "requisition" to JsonPrimitive(answer.requisitionId),
                "reference" to JsonPrimitive(answer.reference),
                "generated_at" to JsonPrimitive(utc(answer.generatedAt)),
                "register" to JsonPrimitive(BackOfficeTexts.REQUISITION_FILE_NOTE),
                "search" to JsonObject(search),
                "content_operations" to answer.rows.contentOperations,
                "pseudonyms" to answer.rows.pseudonyms,
            ),
        )
        return pretty.encodeToString(JsonObject.serializer(), document)
    }

    /** The name of the downloaded file (date of the answer, UTC). */
    fun fileName(answer: RequisitionAnswer): String = "reponse-requisition-${utc(answer.generatedAt).substring(0, 10)}.json"

    /** Version of the file given to the authorities. */
    const val FORMAT: String = "ppf-registre-legal/1"
}
