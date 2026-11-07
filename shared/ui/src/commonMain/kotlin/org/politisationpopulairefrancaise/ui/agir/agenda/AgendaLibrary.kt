package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaState
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaTiming
import org.politisationpopulairefrancaise.core.agir.agenda.InMemoryAgendaState
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.publication.HeldStatusSource
import org.politisationpopulairefrancaise.core.publication.HeldStatuses
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.sync.SyncReport
import org.politisationpopulairefrancaise.core.sync.SyncService
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.resources.Res

/** Where one of the user's own proposals stands, as the device knows it. */
enum class OwnProposalState {
    /** Signed and kept on the device; it leaves with the next synchronization that reaches the server. */
    WAITING,

    /** Received by the server and held for the team's review (also shown until the server says more). */
    IN_REVIEW,

    /** On the agenda. */
    PUBLISHED,

    /** The team did not publish it; the reason says why. */
    NOT_PUBLISHED,
}

/** One of the user's own proposals, with what the server said about it. */
data class OwnProposal(val mobilization: Mobilization, val state: OwnProposalState, val reason: String? = null)

/** How the cancellation of one of the user's events ended. */
enum class CancelOutcome {
    /** Published at once: the event shows "Annulé" for everyone. */
    CANCELLED,

    /** The server holds the new version for review (not expected: the author may cancel without review). */
    HELD,

    /** Signed and kept on the device; it leaves with the next synchronization that reaches the server. */
    WAITING_FOR_SERVER,

    /** The server refused it; the event stays as it was. */
    REFUSED,

    FAILED,
}

/** The three tabs of the agenda. */
enum class AgendaTab { UPCOMING, PAST, MINE }

/**
 * The agenda of mobilizations on the device (M1, AG-01): the examples bundled with the application
 * (`agenda.json`, imported once and shown only when signed by a trusted editorial key), the
 * mobilizations pulled from the server (kind 31923, through the generic events API, like the feed
 * and the demands), and the user's own proposals. Everything is kept in the local event store and
 * readable without network.
 *
 * Who is shown follows the demands wall: the server is the gatekeeper of publication (it serves a
 * contributor's event only once the team approved it, contract of the Agir space, section 5), so
 * every event it serves is on the agenda; the user's own proposals appear on the agenda only once
 * published, and in "Mes propositions" with their state meanwhile.
 *
 * The answers "J'y serai" and "Intéressé" (AG-49) stay in [AgendaState] on the device: they are
 * never sent, counted or listed anywhere, and are erased [RESPONSE_GRACE] after the event is over.
 * The filters run on the device too ([AgendaQuery]); nothing personal travels to the server.
 */
class AgendaLibrary(
    private val store: LocalEventStore?,
    private val state: AgendaState,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    /** Seconds since 1970; injectable so that tests and captures show a fixed day. */
    val clock: () -> Long = ::currentEpochSeconds,
) {
    private val passes = Mutex()
    private val answerWrites = Mutex()
    private var started = false

    var status: LibraryStatus by mutableStateOf(LibraryStatus.Loading)
        private set

    /** The published mobilizations, by start date. */
    var mobilizations: List<Mobilization> by mutableStateOf(emptyList())
        private set

    /** The user's own proposals, newest first (empty without an account). */
    var mine: List<OwnProposal> by mutableStateOf(emptyList())
        private set

    /** The user's answers, by address of mobilization. Private: never sent. */
    var responses: Map<String, AgendaResponse> by mutableStateOf(emptyMap())
        private set

    var selected: Mobilization? by mutableStateOf(null)
        private set

    /** The current time, refreshed at each reload: the agenda groups by day around it. */
    var now: Long by mutableStateOf(clock())
        private set

    var tab: AgendaTab by mutableStateOf(AgendaTab.UPCOMING)

    /** The filters of the list, in memory for the run of the application (the timing comes from [tab]). */
    var filters: AgendaQuery by mutableStateOf(AgendaQuery())

    /** True when an account is on the device ("Mes propositions" is offered). */
    val hasAccount: Boolean get() = accountKey != null

    /** The "Proposer un événement" form; its draft survives a visit to another tab. */
    val composer: MobilizationComposer = MobilizationComposer(store, state, remote, clock)

    private var accountKey: String? by mutableStateOf(null)

    /** The query of the list shown by the current tab. */
    val query: AgendaQuery get() = filters.copy(timing = if (tab == AgendaTab.PAST) AgendaTiming.PAST else AgendaTiming.UPCOMING)

    /** The mobilizations of the current tab, filtered and sorted. */
    fun shown(): List<Mobilization> = query.apply(mobilizations, now)

    /** Every mobilization the device knows of, published or among the user's own (for the duplicates of the form). */
    val known: List<Mobilization> get() = (mobilizations + mine.map { it.mobilization }).distinctBy { it.address.toString() }

    fun start() {
        if (started) return
        started = true
        if (store == null) {
            status = LibraryStatus.Unavailable
            return
        }
        scope.launch {
            passes.withLock {
                importBundled(store)
                withContext(Dispatchers.Default) { state.purgeResponses(clock() - RESPONSE_GRACE) }
                reload(store)
            }
            if (remote == null) status = LibraryStatus.Offline else pull(store, remote)
        }
    }

    /** Asks the server again (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pull(store, remote) }
    }

    /** Tells the library which account is on the device (null after the keys are erased). */
    fun setAccount(pubkeyHex: String?) {
        if (pubkeyHex == accountKey) return
        accountKey = pubkeyHex
        if (pubkeyHex == null && tab == AgendaTab.MINE) tab = AgendaTab.UPCOMING
        val store = store ?: return
        scope.launch {
            passes.withLock { reload(store) }
            refreshReviews()
        }
    }

    /**
     * Asks the server where the user's proposals stand (`GET /api/v1/held`), at most
     * [HeldStatusSource.MAX_IDS] ids per request: only ids travel, which the server knows already.
     * Called when the agenda is shown and after a proposal is sent.
     */
    fun refreshReviews() {
        val store = store ?: return
        scope.launch {
            val source = remote as? HeldStatusSource
            if (source != null) askReviews(store, source)
            // Reloaded in any case: the day may have changed since the agenda was last shown.
            passes.withLock { reload(store) }
        }
    }

    /** Called by the form once a proposal is signed: it shows at once among the user's own. */
    fun onSent() {
        val store = store ?: return
        scope.launch {
            passes.withLock { reload(store) }
            refreshReviews()
        }
    }

    fun open(mobilization: Mobilization) {
        selected = mobilization
    }

    /** Opens the mobilization of a content address (`31923:pubkey:d`), published or one of the user's own. */
    fun openAddress(address: String) {
        known.firstOrNull { it.address.toString() == address }?.let { selected = it }
    }

    fun close() {
        selected = null
    }

    /** The user's answer to [mobilization], or null. */
    fun responseTo(mobilization: Mobilization): AgendaResponse? = responses[mobilization.address.toString()]

    /** Records the answer on the device, or removes it when the same answer is chosen again. Nothing is sent. */
    fun respond(mobilization: Mobilization, response: AgendaResponse) {
        val address = mobilization.address.toString()
        val next = if (responses[address] == response) null else response
        responses = if (next == null) responses - address else responses + (address to next)
        // One write at a time, in the order the user acted.
        scope.launch { answerWrites.withLock { withContext(Dispatchers.Default) { state.respond(address, next, mobilization.endsAt) } } }
    }

    /** Removes a proposal the team did not publish from "Mes propositions" (and from the device). */
    fun forget(proposal: OwnProposal) {
        if (proposal.state != OwnProposalState.NOT_PUBLISHED) return
        val store = store ?: return
        scope.launch {
            passes.withLock {
                withContext(Dispatchers.Default) {
                    store.delete(proposal.mobilization.event.id)
                    state.forgetProposal(proposal.mobilization.event.id)
                }
                reload(store)
            }
        }
    }

    /** True when the account on the device may cancel [mobilization]: its own, published, neither cancelled nor over. */
    fun canCancel(mobilization: Mobilization): Boolean =
        accountKey == mobilization.author && !mobilization.cancelled && !mobilization.isPast(now) &&
            ownProposal(mobilization)?.state == OwnProposalState.PUBLISHED

    /**
     * Cancels one of the user's published events (decision of the owner of 2026-11-07: the author
     * cancels without review): a new version under the same address where only the status becomes
     * "annule" ([Mobilization.cancellation]), signed, kept on the device, then sent through the
     * single publication port. If the server refuses it, the previous version is put back.
     */
    suspend fun cancel(mobilization: Mobilization, signer: EventSigner): CancelOutcome {
        val store = store ?: return CancelOutcome.FAILED
        if (signer.publicKey.hex != mobilization.author || !canCancel(mobilization)) return CancelOutcome.FAILED
        val outcome = try {
            val event = signer.sign(Mobilization.cancellation(mobilization, clock()))
            passes.withLock {
                withContext(Dispatchers.Default) { store.store(event, pending = true) }
                val outcome = when (val result = remote?.publish(event) ?: PublicationResult.Unavailable()) {
                    is PublicationResult.Accepted -> {
                        withContext(Dispatchers.Default) {
                            store.markPublished(event.id)
                            state.recordProposal(event.id, ProposalRecord(if (result.heldForReview) ProposalReview.REVIEW else ProposalReview.PUBLISHED))
                        }
                        if (result.heldForReview) CancelOutcome.HELD else CancelOutcome.CANCELLED
                    }
                    is PublicationResult.Rejected -> {
                        withContext(Dispatchers.Default) {
                            store.delete(event.id)
                            store.store(mobilization.event)
                        }
                        CancelOutcome.REFUSED
                    }
                    is PublicationResult.Unavailable -> CancelOutcome.WAITING_FOR_SERVER
                }
                reload(store)
                outcome
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CancelOutcome.FAILED
        }
        return outcome
    }

    /** True for a key of the editorial team (the author of the bundled examples). */
    fun isEditorial(pubkeyHex: String): Boolean = trust.isTrusted(pubkeyHex)

    /** The state of one of the user's own proposals; null for anyone else's. */
    fun ownProposal(mobilization: Mobilization): OwnProposal? = mine.firstOrNull { it.mobilization.event.id == mobilization.event.id }

    private suspend fun importBundled(store: LocalEventStore) {
        val bytes = runCatching { Res.readBytes(BUNDLED_FILE) }.getOrNull() ?: return
        val events = runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.Default) {
            events.filter { it.kind == Mobilization.KIND && it.verify() && trust.isTrusted(it.pubkey) }.forEach { store.store(it) }
        }
    }

    private suspend fun pull(store: LocalEventStore, remote: RemoteEvents) {
        status = LibraryStatus.Loading
        val report = passes.withLock {
            val up = publishOwnPending(store, remote)
            val report = if (up) withContext(Dispatchers.Default) { SyncService(store, remote).pull(kinds = listOf(Mobilization.KIND)) } else SyncReport(reachable = false)
            reload(store)
            report
        }
        (remote as? HeldStatusSource)?.let { if (askReviews(store, it)) passes.withLock { reload(store) } }
        status = if (report.reachable) LibraryStatus.Synced(report.pulled) else LibraryStatus.Unreachable
    }

    /**
     * Sends the user's proposals still waiting on the device, oldest first, and records whether the
     * server published each one (201) or holds it for review (202). False when the server could not
     * be reached. A proposal refused for good is kept, marked "Non publié", so that its author learns it.
     */
    private suspend fun publishOwnPending(store: LocalEventStore, remote: RemoteEvents): Boolean {
        val waiting = withContext(Dispatchers.Default) { store.pending().filter { it.kind == Mobilization.KIND } }
        for (event in waiting) {
            when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> withContext(Dispatchers.Default) {
                    store.markPublished(event.id)
                    state.recordProposal(event.id, ProposalRecord(if (result.heldForReview) ProposalReview.REVIEW else ProposalReview.PUBLISHED))
                }
                is PublicationResult.Rejected -> withContext(Dispatchers.Default) {
                    store.markPublished(event.id)
                    state.recordProposal(event.id, ProposalRecord(ProposalReview.REJECTED))
                }
                is PublicationResult.Unavailable -> return false
            }
        }
        return true
    }

    /**
     * Updates the review of the user's proposals that are not decided yet. An id the server does not
     * hold was published at once (201) when the device never learnt it was held; one known to be held
     * keeps its state (the server forgets decisions 30 days after them). True when something changed.
     */
    private suspend fun askReviews(store: LocalEventStore, source: HeldStatusSource): Boolean {
        val key = accountKey ?: return false
        val (ids, records) = withContext(Dispatchers.Default) {
            val pending = store.pending().map { it.id }.toSet()
            val records = state.proposals()
            val own = store.recent(kinds = listOf(Mobilization.KIND), limit = MAX_EVENTS)
                .filter { it.pubkey == key && it.id !in pending && !trust.isTrusted(it.pubkey) }
                .filter { records[it.id]?.review.let { review -> review == null || review == ProposalReview.REVIEW } }
                .map { it.id }
            own to records
        }
        if (ids.isEmpty()) return false
        var changed = false
        for (batch in ids.chunked(HeldStatusSource.MAX_IDS)) {
            when (val answer = source.heldStatuses(batch)) {
                is HeldStatuses.Unavailable -> break
                is HeldStatuses.Known -> withContext(Dispatchers.Default) {
                    for (id in batch) {
                        val known = answer.states.firstOrNull { it.id == id }
                        val previous = records[id]
                        val next = when (known?.status) {
                            HeldStatus.PENDING -> ProposalRecord(ProposalReview.REVIEW)
                            HeldStatus.APPROVED -> ProposalRecord(ProposalReview.PUBLISHED)
                            HeldStatus.REJECTED -> ProposalRecord(ProposalReview.REJECTED, known.reason)
                            null -> if (previous == null) ProposalRecord(ProposalReview.PUBLISHED) else previous
                        }
                        if (next != previous) {
                            state.recordProposal(id, next)
                            changed = true
                        }
                    }
                }
            }
        }
        return changed
    }

    private suspend fun reload(store: LocalEventStore) {
        val key = accountKey
        val snapshot = withContext(Dispatchers.Default) {
            val at = clock()
            val pending = store.pending().map { it.id }.toSet()
            val records = state.proposals()
            val all = store.recent(kinds = listOf(Mobilization.KIND), limit = MAX_EVENTS).mapNotNull(Mobilization::from)
            val own = all.filter { key != null && it.author == key }
                .sortedByDescending { it.updatedAt }
                .map { ownOf(it, pending, records[it.event.id]) }
            val published = all.filter { m ->
                val id = m.event.id
                val record = records[id]
                id !in pending &&
                    (record == null || record.review == ProposalReview.PUBLISHED) &&
                    (m.author != key || own.any { it.mobilization.event.id == id && it.state == OwnProposalState.PUBLISHED })
            }
            val answers = state.responses()
            // A newer version may move an answered event: its answer is erased after the new end.
            published.filter { it.address.toString() in answers }.forEach { state.updateEnd(it.address.toString(), it.endsAt) }
            Snapshot(at, AgendaQuery.sort(published, AgendaTiming.ALL), own, answers)
        }
        now = snapshot.now
        mobilizations = snapshot.published
        mine = snapshot.own
        responses = snapshot.answers
        selected = selected?.let { current -> snapshot.published.firstOrNull { it.address == current.address } ?: snapshot.own.firstOrNull { it.mobilization.address == current.address }?.mobilization }
    }

    private fun ownOf(mobilization: Mobilization, pending: Set<String>, record: ProposalRecord?): OwnProposal = when {
        mobilization.event.id in pending -> OwnProposal(mobilization, OwnProposalState.WAITING)
        trust.isTrusted(mobilization.author) -> OwnProposal(mobilization, OwnProposalState.PUBLISHED)
        record?.review == ProposalReview.PUBLISHED -> OwnProposal(mobilization, OwnProposalState.PUBLISHED)
        record?.review == ProposalReview.REJECTED -> OwnProposal(mobilization, OwnProposalState.NOT_PUBLISHED, record.reason)
        else -> OwnProposal(mobilization, OwnProposalState.IN_REVIEW)
    }

    private data class Snapshot(val now: Long, val published: List<Mobilization>, val own: List<OwnProposal>, val answers: Map<String, AgendaResponse>)

    companion object {
        /** Answers are erased this long after the end of their event (seconds). */
        const val RESPONSE_GRACE: Long = 24 * 3_600

        /** At most this many mobilizations are read from the local store. */
        const val MAX_EVENTS: Int = 1_000

        const val BUNDLED_FILE: String = "files/contents/agenda.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** The usual wiring: the stores of the device when there is a database, memory otherwise. */
        fun of(dependencies: AppDependencies, scope: CoroutineScope, clock: () -> Long = ::currentEpochSeconds): AgendaLibrary = AgendaLibrary(
            store = dependencies.contentStore,
            state = dependencies.database?.let { AgendaStore(it) } ?: InMemoryAgendaState(),
            trust = dependencies.editorialTrust,
            remote = dependencies.remote,
            scope = scope,
            clock = clock,
        )
    }
}

/** The agenda of the screen, started once; the shell may hoist it to keep it for the whole run. */
@Composable
fun rememberAgendaLibrary(dependencies: AppDependencies): AgendaLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) { AgendaLibrary.of(dependencies, scope).also { it.start() } }
}
