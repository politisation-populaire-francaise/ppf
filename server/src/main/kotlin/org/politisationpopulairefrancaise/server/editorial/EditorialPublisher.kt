package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
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
    val author: UUID,
    val approvedBy: UUID,
    val correction: Boolean,
)

/** What the publisher answered. */
sealed interface PublisherAnswer {
    /** The signing service holds the request; [approvalUrl] is its own page where two editors approve and sign. */
    data class Submitted(val approvalUrl: String) : PublisherAnswer

    /** Nothing was sent; [message] (French) says why, the version stays ready to sign. */
    data class NotConnected(val message: String) : PublisherAnswer
}

/**
 * The seam between the queue and the signing service (step 5, docs/integration/circuit-editorial.md, state
 * after the D-129 revision). Step 5 plugs here: build the unsigned event with `PlatformSigner.currentKey(use)`
 * and [org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine.build], call
 * `submitDraft(unsigned, use, doubleValidation, author)` (which answers the address of the approval page on
 * the signing service), later `request(unsigned, use)` for the signed event, then the single
 * `PublicationPort`. No passkey, no approval ceremony on the main server: the two-person validation and the
 * signature happen on the pages of the signing service.
 *
 * Called after the decision is stored and journaled, outside its transaction; a failure never undoes the
 * decision (the version stays `ready_to_sign` and can be handed over again by step 5).
 */
fun interface EditorialPublisher {
    fun readyToSign(version: ReadyToSign): PublisherAnswer
}

/** The default until step 5: nothing is sent anywhere, the queue says that the signing service is not plugged yet. */
object UnpluggedEditorialPublisher : EditorialPublisher {
    override fun readyToSign(version: ReadyToSign): PublisherAnswer = PublisherAnswer.NotConnected(EditorialTexts.SIGNER_NOT_PLUGGED)
}
