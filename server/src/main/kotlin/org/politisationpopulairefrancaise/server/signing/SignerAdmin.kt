package org.politisationpopulairefrancaise.server.signing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.platform.KeysResponse
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * One line of the journal of the signing service, as `GET /v1/journal` gives it (contract, section 5):
 * what was done, for which use, on which event, with which key, why it was refused, which accounts approved
 * it, and its place in the hash chain. Never the content of an event. Read leniently (unknown fields are
 * ignored), checked for continuity by the copy of the back-office ([org.politisationpopulairefrancaise.server.backoffice.SignerJournalCopy]).
 */
@Serializable
data class SignerJournalLine(
    val seq: Long,
    val at: Long,
    val action: String,
    val use: String? = null,
    val event: String? = null,
    @SerialName("event_created_at") val eventCreatedAt: Long? = null,
    val key: String? = null,
    val code: String? = null,
    val approvers: List<String> = emptyList(),
    @SerialName("double_validation") val doubleValidation: Boolean? = null,
    @SerialName("author_account") val authorAccount: String? = null,
    @SerialName("author_pubkey") val authorPubkey: String? = null,
    val credential: String? = null,
    val d: String? = null,
    @SerialName("previous_hash") val previousHash: String,
    val hash: String,
)

/** The last line of the journal of the service (`GET /v1/journal/head`). */
@Serializable
data class SignerJournalHead(val seq: Long, val hash: String)

/** What became of a root request deposited in the service (D-141). */
sealed interface RootRequestResult {
    /** Kept by the service 24 hours at most: [id] and [sha256] go into the QR code. */
    data class Created(val id: String, val sha256: String, val expiresAt: Long) : RootRequestResult

    /** Refused by the service (a code of `SigningRefusals`), or by this server (`signer_unavailable`, `bad_answer_from_signer`...). */
    data class Refused(val code: String, val detail: String? = null) : RootRequestResult
}

/**
 * The administration routes of the signing service, for the back-office only (« Clés de la plateforme » and
 * the copy of the journal, contract step 4, D-141). The poles keep [PlatformSigner], unchanged.
 * Every call answers null (or [RootRequestResult.Refused] with `signer_unavailable`) when the service
 * cannot be reached.
 */
interface SignerAdmin {
    /** The root public key of this environment (lowercase hex), the `pubkey` of every event the root signs; null when none is configured. */
    val rootPublicKey: String?

    /** `GET /v1/keys`: the working keys and their states, the team list held and its end. */
    suspend fun keys(): KeysResponse?

    /** `POST /v1/root-requests`: deposits the unsigned [events] the root must sign, for the owner's phone. */
    suspend fun rootRequest(events: List<UnsignedEvent>): RootRequestResult

    /** `GET /v1/certificates`: the certificates and revocations the service holds. */
    suspend fun certificates(): List<SignedEvent>?

    /** `GET /v1/team-list`: the team list the service holds; null when it holds none or does not answer. */
    suspend fun teamList(): SignedEvent?

    /** `GET /v1/journal?after=&limit=`: the lines after [after], [limit] at most (1 to 1000). */
    suspend fun journal(after: Long, limit: Int): List<SignerJournalLine>?

    /** `GET /v1/journal/head`. */
    suspend fun journalHead(): SignerJournalHead?
}
