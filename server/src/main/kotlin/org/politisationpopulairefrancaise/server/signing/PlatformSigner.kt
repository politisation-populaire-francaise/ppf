package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.platform.ApprovalProof
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/** A request for the platform signature, to follow with [PlatformSigner.status]. */
@JvmInline
value class SigningTicket(val id: String)

/** Where a request for the platform signature stands. */
sealed interface SigningStatus {
    data object Pending : SigningStatus

    /** Signed: publish [event] through the single publication port, unchanged. */
    data class Signed(val event: SignedEvent) : SigningStatus

    /**
     * Refused by the signing service, with its stable code (`docs/integration/service-signature.md`,
     * section 5: `approvals_missing`, `author_cannot_approve`, `key_revoked`...), or by this server:
     * `signer_unavailable` (the service did not answer), `unknown_ticket`.
     */
    data class Refused(val code: String, val detail: String? = null) : SigningStatus
}

/**
 * The platform signature for the poles (contract docs/integration/service-signature.md, section 5;
 * D-129). The pole builds the unsigned event with the current key of its use ([currentKey]), the
 * back-office collects the approvals over the challenge of the event (`ApprovalChallenge`), the pole
 * calls [request], then publishes the signed event through the single publication port.
 *
 * The server never holds a platform key: [HttpPlatformSigner] asks the separate signing service.
 */
interface PlatformSigner {
    /** The public key (hex) to put in the events of [use] now, null when the use has no certified key. */
    suspend fun currentKey(use: SigningUse): String?

    /** Asks for the platform signature of [unsigned] for [use]; [approval] carries the WebAuthn assertions. */
    suspend fun request(unsigned: UnsignedEvent, use: SigningUse, approval: ApprovalProof): SigningTicket

    suspend fun status(ticket: SigningTicket): SigningStatus
}
