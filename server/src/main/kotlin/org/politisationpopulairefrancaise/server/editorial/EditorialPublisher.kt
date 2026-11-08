package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.platform.SigningUse
import java.util.UUID

/**
 * What the queue knows of a version approved for publication (state `ready_to_sign`): identifiers only,
 * nothing secret, never the text (the publisher reads the version itself from [EditorialDrafts]).
 */
data class ReadyToSign(
    val space: EditorialSpace,
    val draftId: UUID,
    val version: Int,
    val type: ArticleType,
    val slug: String?,
    /** The published event this version updates, null for a new content. */
    val baseEventId: String?,
    /** The author of the draft: never one of the approvals on the signing service (`DraftAuthor`). */
    val author: UUID,
    val authorPubkey: String?,
    /** The editor who approved it in the queue. */
    val approvedBy: UUID,
    val correction: Boolean,
) {
    /** The use of the platform signature for the contents of [space]. */
    val use: SigningUse get() = if (space == EditorialSpace.VOIR) SigningUse.EDITORIAL_VOIR else SigningUse.EDITORIAL_COMPRENDRE

    /** Editorial contents always ask for two approvals of two accounts that are not the author (M17). */
    val doubleValidation: Boolean get() = true
}

/** Where a version ready to sign stands on the side of the signing service. */
sealed interface PublisherAnswer {
    /** The default until step 5: nothing is sent anywhere. */
    data object NotConnected : PublisherAnswer

    /** The use has no certified key now (`currentKey` answers null): nothing is sent, never a content published unsigned. */
    data object KeyUnavailable : PublisherAnswer

    /**
     * Submitted (`DraftSubmission.Submitted`): [approvalUrl] is the signing service's own page, shown as a plain
     * link to the members allowed to approve; [approvals] of [required] recorded (`draftStatus`, read when the
     * review page is opened, never by a background loop).
     */
    data class AwaitingApproval(val signerDraftId: String, val approvalUrl: String, val approvals: Int = 0, val required: Int = 2) : PublisherAnswer

    /** Refused by the signing service or by this server, with the stable code of `SigningStatus.Refused` / `DraftSubmission.Refused`. */
    data class Refused(val code: String, val detail: String? = null) : PublisherAnswer

    /** Approved on the signing service, the signature requested and not given yet (`SigningStatus.Pending`): asked again on reload. */
    data object SignaturePending : PublisherAnswer

    /** The signing service (or the publication port) does not answer now: nothing lost, asked again on the next reload. */
    data object Unavailable : PublisherAnswer

    /** The submission expired after 7 days on the signing service: a retry builds a new event (new `created_at`). */
    data object Expired : PublisherAnswer

    /** Signed and published, unchanged, through the single publication port: [eventId] of the signed event. */
    data class Published(val eventId: String) : PublisherAnswer
}

/**
 * The seam between the queue and the platform signature (step 5), shaped on `PlatformSigner`
 * (`server/.../signing/PlatformSigner.kt`, D-129) so that step 5 is a thin plug:
 *
 * 1. [handOver], once, right after the approval in the queue: `currentKey(use)` (null: [PublisherAnswer.KeyUnavailable],
 *    nothing is published), build the unsigned event with that key ([org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine.build],
 *    `created_at` strictly after the published version), `submitDraft(unsigned, use, doubleValidation = true,
 *    DraftAuthor(author, authorPubkey))`: `Submitted(draftId, approvalUrl)` or `Refused(code, detail)`;
 * 2. [state], each time the review page of a version ready to sign is opened: `draftStatus(use, eventId)`;
 *    when `ready`, `request(unsigned, use)` then `status(ticket)`: `Pending`, `Signed(event)` (publish
 *    `event` unchanged through the `PublicationPort`, then [PublisherAnswer.Published]) or `Refused`.
 *    A draft expires after 7 days on the signing service: it needs a new event, with a new `created_at`.
 *
 * No passkey, no approval ceremony on this server: two editors approve on the signing service's own page.
 * Called after the decision is stored and journaled, outside its transaction; a failure never undoes the
 * decision (the version stays `ready_to_sign`).
 */
interface EditorialPublisher {
    suspend fun handOver(version: ReadyToSign): PublisherAnswer

    suspend fun state(version: ReadyToSign): PublisherAnswer

    /**
     * The action `retry_signing` (step 5): after an expiry or a refusal, the living submissions of the version are
     * closed and a new event is built, with a `created_at` strictly after every earlier one, then submitted.
     */
    suspend fun retry(version: ReadyToSign): PublisherAnswer = handOver(version)
}

/** The default until step 5: nothing is sent, the review page says that the signing service is not plugged yet. */
object UnpluggedEditorialPublisher : EditorialPublisher {
    override suspend fun handOver(version: ReadyToSign): PublisherAnswer = PublisherAnswer.NotConnected

    override suspend fun state(version: ReadyToSign): PublisherAnswer = PublisherAnswer.NotConnected
}
