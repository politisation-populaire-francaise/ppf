package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.decider.DeliberationDrafts
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.revendications.propose.SendOutcome
import kotlin.random.Random

/** What a contribution answers: a new proposition of a deliberation, or an amendment of a proposition. */
sealed interface ContributionTarget {
    data class NewProposition(val deliberation: ContentAddress) : ContributionTarget
    data class NewAmendment(val proposition: ContentAddress, val originalText: String) : ContributionTarget
}

/**
 * The form to propose a measure or an amendment in a deliberation (DE-25, MVP 1): like the form of the
 * demands, the contribution is signed by its author, kept on the device as waiting to leave (offline-first),
 * then handed to the single publication port; the server holds it for the review of a person.
 */
class DeliberationComposer(
    private val store: LocalEventStore?,
    private val remote: PublicationPort?,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    var target: ContributionTarget? by mutableStateOf(null)
        private set
    var title: String by mutableStateOf("")
    var text: String by mutableStateOf("")
    var justification: String by mutableStateOf("")
    var charterAccepted: Boolean by mutableStateOf(false)
    var sending: Boolean by mutableStateOf(false)
        private set
    var outcome: SendOutcome? by mutableStateOf(null)
        private set

    /** Opens the form for [target]; an amendment starts from the wording it rewrites. */
    fun start(target: ContributionTarget) {
        this.target = target
        title = ""
        text = (target as? ContributionTarget.NewAmendment)?.originalText.orEmpty()
        justification = ""
        charterAccepted = false
        outcome = null
    }

    fun close() {
        target = null
        outcome = null
    }

    val ready: Boolean
        get() {
            val current = target ?: return false
            val titleOk = current !is ContributionTarget.NewProposition || title.trim().length in 1..DeliberationFormats.MAX_TITLE
            val changed = current !is ContributionTarget.NewAmendment || text.trim() != current.originalText.trim()
            return titleOk && changed && text.trim().length in 1..DeliberationFormats.MAX_TEXT &&
                justification.trim().length <= DeliberationFormats.MAX_JUSTIFICATION && charterAccepted && !sending
        }

    suspend fun send(signer: EventSigner): SendOutcome {
        val current = target ?: return finish(SendOutcome.FAILED)
        val store = store ?: return finish(SendOutcome.FAILED)
        if (!ready) return finish(SendOutcome.FAILED)
        sending = true
        try {
            val now = clock()
            val identifier = "contribution-$now-${Random.nextLong(0, Long.MAX_VALUE).toString(36)}"
            val unsigned = when (current) {
                is ContributionTarget.NewProposition -> DeliberationDrafts.proposition(signer.publicKey, current.deliberation, title, text, justification, now, identifier)
                is ContributionTarget.NewAmendment -> DeliberationDrafts.amendment(signer.publicKey, current.proposition, text, justification, now, identifier)
            }
            val event = signer.sign(unsigned)
            withContext(Dispatchers.Default) { store.storeSending(event) }
            return finish(
                when (val result = store.sending(event) { remote?.publish(event) ?: PublicationResult.Unavailable() }) {
                    is PublicationResult.Accepted -> {
                        withContext(Dispatchers.Default) { store.markPublished(event.id) }
                        if (result.heldForReview) SendOutcome.SENT_FOR_REVIEW else SendOutcome.PUBLISHED
                    }
                    is PublicationResult.Rejected -> {
                        withContext(Dispatchers.Default) { store.delete(event.id) }
                        SendOutcome.REFUSED
                    }
                    is PublicationResult.Unavailable -> SendOutcome.WAITING_FOR_SERVER
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finish(SendOutcome.FAILED)
        } finally {
            sending = false
        }
    }

    private fun finish(result: SendOutcome): SendOutcome {
        outcome = result
        return result
    }
}
