package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallIssue
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.agir.collectives.CollectiveLink

/**
 * The state of the "Lancer un appel" form (AP-01, AP-10): the type, the draft, the acceptance of
 * the charter, and the sending. The rules run on every change ([issues]); the draft stays in memory
 * only until it is sent, like a message being typed.
 *
 * Sending signs the call with the author's key, stores it on the device as waiting to leave
 * (offline-first: nothing is lost without network), then hands it to the single publication port.
 * The server publishes it (`201`) or holds it for review (`202`) according to the author and the
 * type (contract of the Agir space, section 5).
 */
class CallComposer(
    private val store: LocalEventStore?,
    private val remote: PublicationPort?,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** True while the form is shown over the wall. */
    var open: Boolean by mutableStateOf(false)

    var draft: CallDraft by mutableStateOf(CallDraft())
    var charterAccepted: Boolean by mutableStateOf(false)

    /**
     * The collectif the call is launched in the name of (AG-50, D-087), set by its page; null for a
     * call of the author alone. It adds the tag `ppf-collective`, which the server accepts from its
     * founder, administrators and moderators only.
     */
    var collective: CollectiveLink? by mutableStateOf(null)
    var sending: Boolean by mutableStateOf(false)
        private set
    var outcome: CallSendOutcome? by mutableStateOf(null)
        private set

    /** The last event sent from this form, when the sending reached the device's store. */
    var sent: SignedEvent? by mutableStateOf(null)
        private set

    fun issues(canFundraise: Boolean): List<CallIssue> = CallRules.check(draft, canFundraise, clock())

    /** True when nothing blocks the sending (the button also needs an account). */
    fun ready(issues: List<CallIssue>): Boolean = CallRules.canSubmit(issues) && charterAccepted && !sending

    /** Chooses the type; the common fields already typed are kept. */
    fun chooseType(type: CallType) {
        draft = draft.copy(type = type)
    }

    /** Back to the choice of the type, keeping what was typed. */
    fun changeType() {
        draft = draft.copy(type = null)
    }

    suspend fun send(signer: EventSigner, canFundraise: Boolean): CallSendOutcome {
        val store = store ?: return finish(CallSendOutcome.FAILED)
        if (!ready(issues(canFundraise))) return finish(CallSendOutcome.FAILED)
        sending = true
        try {
            val unsigned = draft.toUnsignedEvent(signer.publicKey, clock())
            val event = signer.sign(collective?.let { CollectiveFormat.withCollective(unsigned, it.id) } ?: unsigned)
            withContext(Dispatchers.Default) { store.store(event, pending = true) }
            sent = event
            val result = remote?.publish(event) ?: PublicationResult.Unavailable()
            return finish(
                when (result) {
                    is PublicationResult.Accepted -> {
                        withContext(Dispatchers.Default) { store.markPublished(event.id) }
                        if (result.heldForReview) CallSendOutcome.SENT_FOR_REVIEW else CallSendOutcome.PUBLISHED
                    }
                    is PublicationResult.Rejected -> {
                        withContext(Dispatchers.Default) { store.delete(event.id) }
                        sent = null
                        CallSendOutcome.REFUSED
                    }
                    // Kept as pending: the next refresh of the calls sends it.
                    is PublicationResult.Unavailable -> CallSendOutcome.WAITING_FOR_SERVER
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return finish(CallSendOutcome.FAILED)
        } finally {
            sending = false
        }
    }

    /** Back to an empty form, after a call was sent. */
    fun reset() {
        draft = CallDraft()
        charterAccepted = false
        outcome = null
        sent = null
    }

    /** Closes the result and keeps the draft (after a refusal or a failure, to correct it). */
    fun dismissOutcome() {
        outcome = null
    }

    private fun finish(result: CallSendOutcome): CallSendOutcome {
        outcome = result
        if (result == CallSendOutcome.PUBLISHED || result == CallSendOutcome.SENT_FOR_REVIEW || result == CallSendOutcome.WAITING_FOR_SERVER) {
            draft = CallDraft()
            charterAccepted = false
        }
        return result
    }
}
