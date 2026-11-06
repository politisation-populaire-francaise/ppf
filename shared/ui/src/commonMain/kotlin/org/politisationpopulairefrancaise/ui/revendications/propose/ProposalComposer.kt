package org.politisationpopulairefrancaise.ui.revendications.propose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalChecker
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.revendications.checks.RevendicationDraft
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** How the sending of a demand ended. */
enum class SendOutcome {
    /** The server keeps it for the team's review before publication (the usual case). */
    SENT_FOR_REVIEW,

    /** Published at once: the author is a member of the editorial team. */
    PUBLISHED,

    /** Signed and kept on the device; it leaves with the next synchronization that reaches the server. */
    WAITING_FOR_SERVER,

    /** The server refused it for good (rules, limits); nothing is kept. */
    REFUSED,

    /** It could not even be stored on the device; the draft is untouched. */
    FAILED,
}

/**
 * The state of the "Proposer une revendication" form (DE-18): the draft, the acceptance of the
 * charter, and the sending. The automatic checks run on every change ([report]); the draft itself
 * stays in memory only until it is sent, like a message being typed.
 *
 * Sending signs the demand with the author's key, stores it on the device as waiting to leave
 * (offline-first: nothing is lost without network), then hands it to the single publication
 * port. The server holds citizen demands for review (answer 202), see docs/FORMATS.md.
 */
class ProposalComposer(
    private val store: LocalEventStore?,
    private val remote: PublicationPort?,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val checker = ProposalChecker(clock = clock)

    /** True while the form is shown on the demands screen (kept when the user visits another space). */
    var open: Boolean by mutableStateOf(false)

    var draft: RevendicationDraft by mutableStateOf(RevendicationDraft(title = ""))
    var charterAccepted: Boolean by mutableStateOf(false)
    var sending: Boolean by mutableStateOf(false)
        private set
    var outcome: SendOutcome? by mutableStateOf(null)
        private set

    /** The last event sent from this form, when the sending reached the device's store. */
    var sent: SignedEvent? by mutableStateOf(null)
        private set

    /** The checks the device runs alone, against the demands it holds. */
    fun report(existing: List<DemandText>): ProposalReport = checker.checkOnDevice(draft, existing)

    /** True when nothing blocks the sending (the button also needs an account). */
    fun ready(report: ProposalReport): Boolean = report.canSubmit && charterAccepted && !sending

    fun toggleTheme(themeId: String) {
        val themes = draft.themes
        draft = draft.copy(themes = if (themeId in themes) themes - themeId else themes + themeId)
    }

    suspend fun send(signer: EventSigner, existing: List<DemandText>): SendOutcome {
        val store = store ?: return finish(SendOutcome.FAILED)
        if (!ready(report(existing))) return finish(SendOutcome.FAILED)
        sending = true
        try {
            val event = signer.sign(draft.toUnsignedEvent(signer.publicKey, clock()))
            withContext(Dispatchers.Default) { store.store(event, pending = true) }
            sent = event
            val result = remote?.publish(event) ?: PublicationResult.Unavailable()
            return finish(
                when (result) {
                    is PublicationResult.Accepted -> {
                        withContext(Dispatchers.Default) { store.markPublished(event.id) }
                        if (result.heldForReview) SendOutcome.SENT_FOR_REVIEW else SendOutcome.PUBLISHED
                    }
                    is PublicationResult.Rejected -> {
                        withContext(Dispatchers.Default) { store.delete(event.id) }
                        sent = null
                        SendOutcome.REFUSED
                    }
                    // Kept as pending: the next synchronization sends it (SyncService.publishPending).
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

    /** Back to an empty form, after a demand was sent. */
    fun reset() {
        draft = RevendicationDraft(title = "")
        charterAccepted = false
        outcome = null
        sent = null
    }

    /** Closes the result and keeps the draft (after a refusal or a failure, to correct it). */
    fun dismissOutcome() {
        outcome = null
    }

    private fun finish(result: SendOutcome): SendOutcome {
        outcome = result
        if (result == SendOutcome.SENT_FOR_REVIEW || result == SendOutcome.PUBLISHED || result == SendOutcome.WAITING_FOR_SERVER) {
            draft = RevendicationDraft(title = "")
            charterAccepted = false
        }
        return result
    }
}
