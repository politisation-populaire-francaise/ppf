package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaState
import org.politisationpopulairefrancaise.core.agir.agenda.CloseMobilization
import org.politisationpopulairefrancaise.core.agir.agenda.DraftProblem
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationDraft
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationDuplicates
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.PublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.agir.collectives.CollectiveLink
import kotlin.random.Random

/** How the sending of a proposed event ended. */
enum class MobilizationSendOutcome {
    /** On the agenda at once (answer 201: editorial key or verified organisation). */
    PUBLISHED,

    /** Held by the server for the team's review (answer 202, the usual case). */
    SENT_FOR_REVIEW,

    /** Signed and kept on the device; it leaves with the next synchronization that reaches the server. */
    WAITING_FOR_SERVER,

    /** The server refused it for good; nothing is kept. */
    REFUSED,

    /** It could not even be stored on the device; the draft is untouched. */
    FAILED,
}

/**
 * The state of the "Proposer un événement" form (AG-02): the draft, the acceptance of the charter
 * and the sending. The draft stays in memory until it is sent, like a message being typed.
 *
 * Sending signs the event with the author's key, stores it on the device as waiting to leave
 * (offline-first: nothing is lost without network), then hands it to the single publication port.
 * The server publishes the events of editorial keys and verified organisations (201) and holds the
 * others for review (202); the answer is recorded so that "Mes propositions" shows it.
 */
class MobilizationComposer(
    private val store: LocalEventStore?,
    private val state: AgendaState,
    private val remote: PublicationPort?,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
) {
    /** True while the form is shown on the agenda screen. */
    var open: Boolean by mutableStateOf(false)

    var draft: MobilizationDraft by mutableStateOf(MobilizationDraft())
    var charterAccepted: Boolean by mutableStateOf(false)

    /**
     * The collectif the event is proposed in the name of (AG-49, D-087), set by its page; null for an
     * event of the author alone. It adds the tag `ppf-collective`, which the server accepts from its
     * founder, administrators and moderators only.
     */
    var collective: CollectiveLink? by mutableStateOf(null)

    var sending: Boolean by mutableStateOf(false)
        private set
    var outcome: MobilizationSendOutcome? by mutableStateOf(null)
        private set

    /** The last event sent from this form, when the sending reached the device's store. */
    var sent: SignedEvent? by mutableStateOf(null)
        private set

    /** False when no server is configured: the form says that the event will leave later. */
    val hasServer: Boolean get() = remote != null

    fun problems(): List<DraftProblem> = draft.problems(clock())

    /** True when nothing blocks the sending (the button also needs an account). */
    fun ready(problems: List<DraftProblem> = problems()): Boolean = problems.isEmpty() && charterAccepted && !sending

    /**
     * The known mobilizations that look like the draft (AG-21), once its date, hour and a title or
     * a place are given.
     */
    fun duplicates(known: List<Mobilization>): List<CloseMobilization> {
        val start = draft.start(ParisTime.moment(clock()).date) ?: return emptyList()
        if (draft.title.isBlank() && draft.location.isBlank()) return emptyList()
        return MobilizationDuplicates.find(draft.title, draft.location, draft.territory, start, known)
    }

    suspend fun send(signer: EventSigner): MobilizationSendOutcome {
        val store = store ?: return finish(MobilizationSendOutcome.FAILED)
        if (!ready()) return finish(MobilizationSendOutcome.FAILED)
        sending = true
        try {
            val now = clock()
            val unsigned = draft.toUnsignedEvent(signer.publicKey, now, Mobilization.newIdentifier(draft.title, random))
                ?: return finish(MobilizationSendOutcome.FAILED)
            val event = signer.sign(collective?.let { CollectiveFormat.withCollective(unsigned, it.id) } ?: unsigned)
            withContext(Dispatchers.Default) { store.storeSending(event) }
            sent = event
            val result = store.sending(event) { remote?.publish(event) ?: PublicationResult.Unavailable() }
            return finish(
                when (result) {
                    is PublicationResult.Accepted -> {
                        withContext(Dispatchers.Default) {
                            store.markPublished(event.id)
                            state.recordProposal(event.id, ProposalRecord(if (result.heldForReview) ProposalReview.REVIEW else ProposalReview.PUBLISHED))
                        }
                        if (result.heldForReview) MobilizationSendOutcome.SENT_FOR_REVIEW else MobilizationSendOutcome.PUBLISHED
                    }
                    is PublicationResult.Rejected -> {
                        withContext(Dispatchers.Default) { store.delete(event.id) }
                        sent = null
                        MobilizationSendOutcome.REFUSED
                    }
                    // Kept as pending: the next synchronization of the agenda sends it.
                    is PublicationResult.Unavailable -> MobilizationSendOutcome.WAITING_FOR_SERVER
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finish(MobilizationSendOutcome.FAILED)
        } finally {
            sending = false
        }
    }

    /** Back to an empty form, after an event was sent. */
    fun reset() {
        draft = MobilizationDraft()
        charterAccepted = false
        outcome = null
        sent = null
    }

    /** Closes the result and keeps the draft (after a refusal or a failure, to correct it). */
    fun dismissOutcome() {
        outcome = null
    }

    private fun finish(result: MobilizationSendOutcome): MobilizationSendOutcome {
        outcome = result
        if (result == MobilizationSendOutcome.PUBLISHED || result == MobilizationSendOutcome.SENT_FOR_REVIEW || result == MobilizationSendOutcome.WAITING_FOR_SERVER) {
            draft = MobilizationDraft()
            charterAccepted = false
        }
        return result
    }
}
