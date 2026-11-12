package org.politisationpopulairefrancaise.ui.agir.calls

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
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallQuery
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.agir.calls.SlotClient
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupStore
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.publication.HeldStatusSource
import org.politisationpopulairefrancaise.core.publication.HeldStatuses
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import org.politisationpopulairefrancaise.ui.resources.Res

/** The tabs of the wall: every published call, the user's own, the ones the user answered. */
enum class CallsTab { ALL, MINE, ANSWERED }

/** One of the user's own calls, with what the server said about it (AP-02). */
data class OwnCall(
    val call: Call,
    /** Waiting to leave, in review, published or not published, as for the demands. */
    val state: OwnDemandState,
    /** The team's reason, for a call not published. */
    val reason: String? = null,
)

/** How a sending of the user (a new call, or the closing of one) ended. */
enum class CallSendOutcome {
    /** Published at once (`201`): a verified organisation or an editorial account. */
    PUBLISHED,

    /** Held for the team's review before publication (`202`): new accounts, and every call for witnesses. */
    SENT_FOR_REVIEW,

    /** Signed and kept on the device; it leaves with the next refresh that reaches the server. */
    WAITING_FOR_SERVER,

    /** The server refused it for good; nothing is kept. */
    REFUSED,

    /** It could not even be prepared or stored; nothing changed. */
    FAILED,
}

/**
 * The calls available on the device (AP-05): the examples bundled with the application (imported
 * once, kept only when their author is a trusted editorial account), those the server serves (it
 * serves published calls only: the others wait for the team's review), and the user's own, with
 * where each one stands. Everything lives in the device's [LocalEventStore], readable offline;
 * every event is signature-checked before it is stored.
 *
 * The answers of the user (AP-03) are kept in [CallResponseStore], on the device only: nothing is
 * ever sent about them (contract of the Agir space, section 6), and each is erased
 * [CallResponseStore.KEPT_AFTER_END] after its call is over ([Call.answersEndAt]: closing or
 * deadline), at the next loading of the calls. Without a store (tests, previews) they live in memory
 * for the life of the process.
 */
class CallsLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    private val responses: CallResponseStore? = null,
    private val clock: () -> Long = ::currentEpochSeconds,
    /** The bundled examples; injectable so that tests can start from an empty wall. */
    private val bundled: suspend () -> ByteArray? = { runCatching { Res.readBytes(BUNDLED_FILE) }.getOrNull() },
    /** The slots of the calls (AP-11): the user's signups, the server and the reminders. */
    val slots: SlotsLibrary = SlotsLibrary(null, null, null, clock),
    /** « Près de chez moi »: reads the commune of « Mes élus » on the device. */
    private val territoryOf: suspend () -> org.politisationpopulairefrancaise.ui.agir.MyTerritory? = { null },
) {
    /** The person's territory, once read ([loadTerritory]); null when none is chosen. */
    var territory: org.politisationpopulairefrancaise.ui.agir.MyTerritory? by mutableStateOf(null)
        private set

    suspend fun loadTerritory() {
        territory = territoryOf()
    }

    private val responseWrites = Mutex()

    var status: LibraryStatus by mutableStateOf(LibraryStatus.Loading)
        private set

    /** The published calls of the wall, newest version of each address (open and closed). */
    var calls: List<Call> by mutableStateOf(emptyList())
        private set

    /** The user's own calls, published or not, newest first. */
    var myCalls: List<OwnCall> by mutableStateOf(emptyList())
        private set

    /** The addresses of the calls the user answered. Private: never sent anywhere. */
    var responded: Set<String> by mutableStateOf(emptySet())
        private set

    var selected: Call? by mutableStateOf(null)
        private set

    /** The form to launch a call: its draft survives a visit to another space. */
    val composer: CallComposer = CallComposer(store, remote, clock)

    /** The filters and the sort of the wall, kept in memory for the run (never saved, never sent). */
    var query: CallQuery by mutableStateOf(CallQuery())

    /** The tab of the wall shown. */
    var tab: CallsTab by mutableStateOf(CallsTab.ALL)

    private var accountKey: String? = null

    /** What the server said about the user's calls held for review, by event id; asked again at each refresh. */
    private var heldStates: Map<String, HeldStatus> = emptyMap()
    private var heldReasons: Map<String, String> = emptyMap()

    /** Ids the server answered about without knowing them: published at once, or decided long ago. */
    private var unknownToHeld: Set<String> = emptySet()

    /** Ids the user's device saw published at once (`201`). */
    private var publishedIds: Set<String> = emptySet()

    fun now(): Long = clock()

    private var started = false

    /** Loads the calls the first time they are shown; later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        if (store == null) {
            status = LibraryStatus.Unavailable
            return
        }
        scope.launch {
            loadResponses()
            importBundled(store)
            reload(store)
            purgeExpiredResponses()
            if (remote == null) status = LibraryStatus.Offline else pull(store, remote)
        }
    }

    /** Asks the server again (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pull(store, remote) }
    }

    /** Which account is on the device (null after the keys are erased), to tell its calls apart and follow their review. */
    fun setAccount(pubkeyHex: String?) {
        if (pubkeyHex == accountKey) return
        accountKey = pubkeyHex
        heldStates = emptyMap()
        heldReasons = emptyMap()
        unknownToHeld = emptySet()
        val store = store ?: return
        scope.launch {
            reload(store)
            if (refreshHeld()) reload(store)
        }
    }

    fun ownCall(call: Call): OwnCall? = myCalls.firstOrNull { it.call.address == call.address }

    fun isResponded(call: Call): Boolean = call.address.toString() in responded

    fun open(call: Call) {
        selected = call
    }

    /** Opens the call of a content address (`30023:<key>:<d>`), published or one of the user's own. */
    fun openAddress(address: String) {
        (calls + myCalls.map { it.call }).firstOrNull { it.address.toString() == address }?.let { selected = it }
    }

    fun close() {
        selected = null
    }

    /** Answers a call or withdraws the answer: on the device only (AP-03). */
    fun toggleResponse(call: Call) {
        val address = call.address.toString()
        val answered = address !in responded
        responded = if (answered) responded + address else responded - address
        scope.launch { persistResponses { if (answered) it.respond(address) else it.withdraw(address) } }
    }

    /** Forgets every answer of this device (the user's choice; the shell may also call it when the keys are erased). */
    fun clearResponses() {
        responded = emptySet()
        scope.launch { persistResponses { it.clear() } }
    }

    /** Called by the form after a sending: shows the call at once among the user's own and asks where it stands. */
    fun onCallSent(event: SignedEvent?, outcome: CallSendOutcome) {
        if (event != null && outcome == CallSendOutcome.PUBLISHED) publishedIds = publishedIds + event.id
        val store = store ?: return
        scope.launch {
            reload(store)
            if (refreshHeld()) reload(store)
        }
    }

    /**
     * Closes one of the user's own calls with its [report] (AP-09): a new version of the same
     * address, signed, stored as waiting to leave, then handed to the publication port. The server
     * publishes a closing at once (decision of the owner, 2026-11-07): only the status and the report
     * change. An answer `202` is still reported as such.
     */
    suspend fun closeCall(signer: EventSigner, call: Call, report: String): CallSendOutcome {
        val store = store ?: return CallSendOutcome.FAILED
        if (call.author != signer.publicKey.hex || !call.isOpen || CallRules.checkReport(report).isNotEmpty()) return CallSendOutcome.FAILED
        val outcome = try {
            val event = signer.sign(call.closedVersion(report, clock()))
            val stored = withContext(Dispatchers.Default) { store.storeSending(event) }
            if (stored != LocalStoreOutcome.STORED) return CallSendOutcome.FAILED
            when (val result = store.sending(event) { remote?.publish(event) ?: PublicationResult.Unavailable() }) {
                is PublicationResult.Accepted -> {
                    withContext(Dispatchers.Default) { store.markPublished(event.id) }
                    if (result.heldForReview) {
                        CallSendOutcome.SENT_FOR_REVIEW
                    } else {
                        publishedIds = publishedIds + event.id
                        CallSendOutcome.PUBLISHED
                    }
                }
                is PublicationResult.Rejected -> {
                    // The local copy of the open version is gone (replaced): the next pull brings it back.
                    withContext(Dispatchers.Default) { store.delete(event.id) }
                    CallSendOutcome.REFUSED
                }
                is PublicationResult.Unavailable -> CallSendOutcome.WAITING_FOR_SERVER
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CallSendOutcome.FAILED
        }
        reload(store)
        if (refreshHeld()) reload(store)
        return outcome
    }

    private suspend fun persistResponses(write: (CallResponseStore) -> Unit) {
        val responses = responses ?: return
        responseWrites.withLock { withContext(Dispatchers.Default) { write(responses) } }
    }

    /**
     * Erases the answers to the calls over for more than [CallResponseStore.KEPT_AFTER_END] (decision
     * of the owner, 7 November 2026, question 8, option C): no history of the calls answered builds up.
     */
    private suspend fun purgeExpiredResponses() {
        val expired = CallResponseStore.expired(responded, calls + myCalls.map { it.call }, clock())
        if (expired.isEmpty()) return
        responded = responded - expired
        persistResponses { store -> expired.forEach(store::withdraw) }
    }

    private suspend fun loadResponses() {
        val responses = responses ?: return
        responded = withContext(Dispatchers.Default) { responses.all() }
    }

    private suspend fun importBundled(store: LocalEventStore) {
        val bytes = bundled() ?: return
        val events = runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.Default) {
            events.filter { Call.isCall(it) && it.verify() && trust.isTrusted(it.pubkey) }.forEach { store.store(it) }
        }
    }

    /**
     * Sends the user's calls waiting to leave, then fetches the new published calls by their tag
     * (`kind=30023&tag=ppf-type:appel`, with this pass's own cursor), then asks where the user's
     * calls held for review stand. Only the tag and the cursor travel: no filter of the wall.
     */
    private suspend fun pull(store: LocalEventStore, remote: RemoteEvents) {
        status = LibraryStatus.Loading
        var reachable = publishPending(store, remote)
        var pulled = 0
        if (reachable) {
            val since = store.cursor(CURSOR)
            val filter = RemoteFilter(kinds = listOf(Call.KIND), since = since, limit = PULL_LIMIT)
            when (val result = remote.fetchByTags(listOf(TagFilter(AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL)), filter)) {
                is FetchResult.Unavailable -> reachable = false
                is FetchResult.Events -> {
                    pulled = withContext(Dispatchers.Default) {
                        result.events.count { Call.isCall(it) && it.verify() && store.store(it) == LocalStoreOutcome.STORED }
                    }
                    val newest = result.events.maxOfOrNull { it.createdAt }
                    val advanceTo = listOfNotNull(newest, since).maxOrNull()?.coerceAtMost(clock())
                    if (advanceTo != null) withContext(Dispatchers.Default) { store.setCursor(CURSOR, advanceTo) }
                }
            }
        }
        // The calls just sent are no longer waiting: their review can be asked about.
        reload(store)
        if (refreshHeld()) reload(store)
        // A call closed meanwhile may end the keeping of its answer.
        purgeExpiredResponses()
        status = if (reachable) LibraryStatus.Synced(pulled) else LibraryStatus.Unreachable
    }

    /** Publishes the user's calls kept on the device; false when the server could not be reached. */
    private suspend fun publishPending(store: LocalEventStore, remote: RemoteEvents): Boolean {
        val pending = withContext(Dispatchers.Default) { store.pendingToSend().filter(Call::isCall) }
        for (event in pending) {
            when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> {
                    withContext(Dispatchers.Default) { store.markPublished(event.id) }
                    if (!result.heldForReview) publishedIds = publishedIds + event.id
                }
                is PublicationResult.Rejected -> withContext(Dispatchers.Default) { store.delete(event.id) }
                is PublicationResult.Unavailable -> return false
            }
        }
        return true
    }

    /**
     * Asks the server where the user's sent calls stand, [HeldStatusSource.MAX_IDS] ids per request.
     * Only ids travel; the server knows them already. True when something changed.
     */
    private suspend fun refreshHeld(): Boolean {
        val source = remote as? HeldStatusSource ?: return false
        val ids = myCalls.filter { it.state != OwnDemandState.WAITING && !trust.isTrusted(it.call.author) && it.call.event.id !in publishedIds }
            .map { it.call.event.id }
        if (ids.isEmpty()) return false
        var states = heldStates
        var reasons = heldReasons
        var unknown = unknownToHeld
        for (batch in ids.chunked(HeldStatusSource.MAX_IDS)) {
            when (val answer = source.heldStatuses(batch)) {
                is HeldStatuses.Unavailable -> break
                is HeldStatuses.Known -> {
                    val known = answer.states.filter { it.id in batch }
                    val knownIds = known.map { it.id }.toSet()
                    val missing = batch.toSet() - knownIds
                    // A fresh answer wins over what an older one said.
                    states = states - missing + known.associate { it.id to it.status }
                    reasons = reasons - missing - knownIds + known.mapNotNull { state -> state.reason?.let { state.id to it } }
                    unknown = unknown - knownIds + missing
                }
            }
        }
        val changed = states != heldStates || reasons != heldReasons || unknown != unknownToHeld
        heldStates = states
        heldReasons = reasons
        unknownToHeld = unknown
        return changed
    }

    private suspend fun reload(store: LocalEventStore) {
        val key = accountKey
        val (wall, own) = withContext(Dispatchers.Default) {
            val pendingIds = store.pending().map { it.id }.toSet()
            val all = store.byTag(listOf(Call.KIND), AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL, limit = LOCAL_LIMIT)
                .mapNotNull(Call::from)
                .sortedWith(compareByDescending<Call> { it.publishedAt ?: it.updatedAt }.thenBy { it.address.toString() })
            val own = all.filter { it.author == key }.map { ownCallOf(it, pendingIds) }
            val publishedOwn = own.filter { it.state == OwnDemandState.PUBLISHED }.map { it.call.event.id }.toSet()
            // Someone else's call reaches the device only from the server (published) or bundled (trusted).
            all.filter { it.author != key || it.event.id in publishedOwn } to own
        }
        calls = wall
        myCalls = own
        selected = selected?.let { current ->
            wall.firstOrNull { it.address == current.address } ?: own.firstOrNull { it.call.address == current.address }?.call
        }
    }

    private fun ownCallOf(call: Call, pendingIds: Set<String>): OwnCall {
        val id = call.event.id
        return when {
            id in pendingIds -> OwnCall(call, OwnDemandState.WAITING)
            trust.isTrusted(call.author) || id in publishedIds -> OwnCall(call, OwnDemandState.PUBLISHED)
            heldStates[id] == HeldStatus.APPROVED -> OwnCall(call, OwnDemandState.PUBLISHED)
            heldStates[id] == HeldStatus.REJECTED -> OwnCall(call, OwnDemandState.NOT_PUBLISHED, heldReasons[id])
            heldStates[id] == HeldStatus.PENDING -> OwnCall(call, OwnDemandState.IN_REVIEW)
            // The server keeps a held content 30 days after its decision: one it does not know was published at once.
            id in unknownToHeld -> OwnCall(call, OwnDemandState.PUBLISHED)
            else -> OwnCall(call, OwnDemandState.IN_REVIEW)
        }
    }

    companion object {
        const val BUNDLED_FILE: String = "files/contents/appels.json"

        /** The cursor of the pull of calls in the table `sync_cursor`, apart from the general pull. */
        const val CURSOR: String = "pull:30023:ppf-type=appel"

        const val PULL_LIMIT: Int = 200
        const val LOCAL_LIMIT: Int = 500

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Composable
fun rememberCallsLibrary(dependencies: AppDependencies, start: Boolean = true): CallsLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        CallsLibrary(
            store = dependencies.contentStore,
            trust = dependencies.editorialTrust,
            remote = dependencies.remote,
            scope = scope,
            responses = dependencies.database?.let(::CallResponseStore),
            slots = SlotsLibrary(dependencies.database?.let(::SlotSignupStore), dependencies.remote as? SlotClient, dependencies.database?.let { ReminderStore(it) }),
            territoryOf = { org.politisationpopulairefrancaise.ui.agir.MyTerritory.of(dependencies) },
        ).also { if (start) it.start() }
    }
}
