package org.politisationpopulairefrancaise.server.signing

import org.politisationpopulairefrancaise.core.platform.DraftAuthor
import org.politisationpopulairefrancaise.core.platform.DraftStatusResponse
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
     * section 5: `approvals_missing`, `unknown_draft`, `key_revoked`...), or by this server:
     * `signer_unavailable` (the service did not answer), `bad_signature_from_signer`,
     * `untrusted_key_from_signer` (the key is not certified by the root for the use), `unknown_ticket`.
     */
    data class Refused(val code: String, val detail: String? = null) : SigningStatus
}

/** What became of a draft submitted for approval. */
sealed interface DraftSubmission {
    /**
     * Known by the signing service: show [approvalUrl] to the members who may approve it. The page is
     * served by the signing service itself, on its own origin, never by this server.
     */
    data class Submitted(val draftId: String, val approvalUrl: String) : DraftSubmission

    /** Refused at once (the whole policy applies when the draft is submitted), with the same codes as [SigningStatus.Refused]. */
    data class Refused(val code: String, val detail: String? = null) : DraftSubmission
}

/**
 * The platform signature for the poles (contract docs/integration/service-signature.md, section 5,
 * version 1.2; D-129).
 *
 * For a human use the pole builds the unsigned event with the current key of its use ([currentKey]),
 * submits it ([submitDraft], with the double validation and the author told by its queue), and the
 * back-office shows the approval link to the members: they read the content and approve it on the signing
 * service's own page, which checks their passkeys itself. When [draftStatus] says the approvals are
 * there, the pole calls [request], then publishes the signed event, unchanged, through the single
 * publication port. This server never sees nor carries an approval. An automatic use calls [request]
 * directly, without a draft.
 *
 * The server never holds a platform key: [HttpPlatformSigner] asks the separate signing service.
 */
interface PlatformSigner {
    /** The public key (hex) to put in the events of [use] now, null when the use has no certified key. */
    suspend fun currentKey(use: SigningUse): String?

    /**
     * Submits [unsigned] of a human use for approval. [doubleValidation] asks for two approvals of two
     * different accounts, [author] being neither of them (required then). The rule is fixed by the first
     * submission; submitting the same event again (after a restart of the service) restores its content.
     */
    suspend fun submitDraft(unsigned: UnsignedEvent, use: SigningUse, doubleValidation: Boolean = false, author: DraftAuthor? = null): DraftSubmission

    /** The approvals recorded so far for the draft [eventId] of [use]; null when the service does not know it (or does not answer). */
    suspend fun draftStatus(use: SigningUse, eventId: String): DraftStatusResponse?

    /** Asks for the platform signature of [unsigned] for [use]: an automatic use, or a human use whose draft is approved. */
    suspend fun request(unsigned: UnsignedEvent, use: SigningUse): SigningTicket

    suspend fun status(ticket: SigningTicket): SigningStatus
}
