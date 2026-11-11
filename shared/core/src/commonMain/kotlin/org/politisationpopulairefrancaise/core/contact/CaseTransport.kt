package org.politisationpopulairefrancaise.core.contact

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The JSON of the public API of the cases (docs/integration/contact-reclamations.md, section 6), the same on
 * the device and on the server. Errors are `{"error": "<code>", "message": "<English>"}` as everywhere else.
 */
object CaseApi {
    const val CASES: String = "/api/v1/cases"

    /** `GET` the state of a case (with `Authorization: Bearer <token>`), optionally `?after=<envelope id>`. */
    fun case(number: CaseNumber): String = "$CASES/${number.display}"

    /** `POST` a follow-up message (with the token). */
    fun messages(number: CaseNumber): String = "$CASES/${number.display}/messages"

    /** How many envelopes of the team one answer of `GET` holds at most (then `more` is true). */
    const val MAX_ENVELOPES_PER_ANSWER: Int = 20

    /** The JSON settings of these requests and answers. */
    val json: Json = CaseApiJson
}

private val CaseApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** `POST /api/v1/cases`: a new case, its opening envelope for the inbox and what the server keeps of its secrets. */
@Serializable
data class CaseCreation(
    val number: String,
    val type: CaseType,
    val subtype: TicketSubtype? = null,
    val category: ContactCategory? = null,
    val motif: ClaimMotif? = null,
    /** The date of the contested moderation decision (seconds since 1970), for a claim of that motif only. */
    @SerialName("decision_at") val decisionAt: Long? = null,
    /** SHA-256 of the access token, lowercase hexadecimal. */
    @SerialName("access_hash") val accessHash: String,
    /** The public key of the case, x only, lowercase hexadecimal. */
    @SerialName("case_pubkey") val casePubkey: String,
    val envelope: SignedEvent,
    /** A report only (S-38, D-234): the address of the content reported, in clear for the back office. */
    @SerialName("report_content") val reportContent: String? = null,
    /** A report only: the code of its [ReportReason], in clear. */
    @SerialName("report_reason") val reportReason: String? = null,
) {
    val nature: CaseNature? get() = CaseNature.of(type, subtype, category, motif)

    companion object {
        fun of(secrets: CaseSecrets, nature: CaseNature, envelope: SignedEvent, decisionAt: Long? = null): CaseCreation = CaseCreation(
            number = secrets.number.display,
            type = nature.type,
            subtype = nature.subtype,
            category = nature.category,
            motif = nature.motif,
            decisionAt = decisionAt,
            accessHash = secrets.accessHash,
            casePubkey = secrets.publicKey.hex,
            envelope = envelope,
        )
    }
}

/** `201` to a creation (and `200` to the very same creation sent again). */
@Serializable
data class CaseCreated(
    val number: String,
    val status: CaseStatus,
    @SerialName("deadline_at") val deadlineAt: Long? = null,
    @SerialName("deadline_kind") val deadlineKind: DeadlineKind? = null,
)

/** `GET /api/v1/cases/{number}`: where the case stands, and the envelopes of the team after the one asked. */
@Serializable
data class CaseState(
    val number: String,
    val status: CaseStatus,
    @SerialName("deadline_at") val deadlineAt: Long? = null,
    @SerialName("deadline_kind") val deadlineKind: DeadlineKind? = null,
    /** Whether the deadline of a request on one's data was extended (the reason comes in a status message). */
    val extended: Boolean = false,
    /** Envelopes of the team (`out`), oldest first. */
    val envelopes: List<SignedEvent> = emptyList(),
    /** More envelopes wait: ask again with `?after=<id of the last one>`. */
    val more: Boolean = false,
)

/** `POST /api/v1/cases/{number}/messages`. */
@Serializable
data class CaseFollowUp(val envelope: SignedEvent)

/** `201` (or `200` for an envelope already received) to a follow-up message. */
@Serializable
data class CaseMessageAccepted(val id: String, val status: CaseStatus)
