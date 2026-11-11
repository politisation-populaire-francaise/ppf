package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.timer.ArmRequest
import org.politisationpopulairefrancaise.core.agir.timer.ArmResult
import org.politisationpopulairefrancaise.core.agir.timer.CheckInResult
import org.politisationpopulairefrancaise.core.agir.timer.DeliveryReport
import org.politisationpopulairefrancaise.core.agir.timer.InMemoryTimerStorage
import org.politisationpopulairefrancaise.core.agir.timer.MessagingTimerTransport
import org.politisationpopulairefrancaise.core.agir.timer.RelativeTimer
import org.politisationpopulairefrancaise.core.agir.timer.RelativeTimerPhase
import org.politisationpopulairefrancaise.core.agir.timer.ReportResult
import org.politisationpopulairefrancaise.core.agir.timer.TimerCandidate
import org.politisationpopulairefrancaise.core.agir.timer.TimerDeviceData
import org.politisationpopulairefrancaise.core.agir.timer.TimerFormats
import org.politisationpopulairefrancaise.core.agir.timer.TimerInbox
import org.politisationpopulairefrancaise.core.agir.timer.TimerRecipient
import org.politisationpopulairefrancaise.core.agir.timer.TimerRecipientSource
import org.politisationpopulairefrancaise.core.agir.timer.TimerService
import org.politisationpopulairefrancaise.core.agir.timer.TimerStorage
import org.politisationpopulairefrancaise.core.agir.timer.TimerStore
import org.politisationpopulairefrancaise.core.agir.timer.TimerTransport
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.messaging.IncomingRumors
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.notifications.NotificationCandidate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.agir.manifestation.HelpState
import org.politisationpopulairefrancaise.ui.messages.MessagingSession
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.timer_notif_text
import org.politisationpopulairefrancaise.ui.resources.timer_notif_title

/** What the screen shows once something was done; the very same for the « je vais bien » code and the duress code. */
sealed interface TimerOutcome {
    data class Armed(val shownDeadline: Long, val recipients: Int, val delivery: DeliveryReport) : TimerOutcome

    data object Cancelled : TimerOutcome

    data class Reported(val shownDeadline: Long) : TimerOutcome
}

/**
 * The person's own timer as the screens show it: never the codes, never the secret, and the
 * deadline of the screen ([shownDeadline], moved by a duress « Repousser » while the recipients keep
 * the real one).
 */
data class OwnTimer(
    val id: String,
    val shownDeadline: Long,
    val recipients: List<TimerRecipient>,
    /** Recipients whose deposit is still on this device: the alert cannot reach them yet. */
    val waitingDeposits: Int,
)

/**
 * « Sans nouvelles de moi » (AG-55, D-088) for the whole run of the shell: the person's own timer
 * ([TimerService]) and the timers of relatives received on this device ([TimerInbox]), on one
 * storage of the platform ([TimerStore]). The slow steps (two derivations of a code) run on
 * [worker]. Nothing here tells the server anything but envelopes.
 *
 * [tick] moves the clock of the screens (countdowns, deadlines reached), asks the messages again
 * ([refreshMessages]) when alerts become due (a cancellation may be on its way) and every
 * [REFRESH_EVERY_TICKS] beats while a timer of a relative runs, notifies the alerts still due one
 * tick later ([onNotify]) and retries the deliveries waiting for the network.
 */
class TimerState(
    private val service: TimerService,
    private val inbox: TimerInbox,
    private val store: TimerStore,
    private val scope: CoroutineScope,
    /** False where the platform has nowhere to keep a timer (iOS for now): the screen says so. */
    val hasStorage: Boolean,
    /** False when the build has no server: nothing can be deposited in advance. */
    val hasServer: Boolean,
    /** Who can receive the alert: the contacts of the private conversations today, the circles (M28) later. */
    private val contacts: TimerRecipientSource,
    /** The verified legal teams of the directory that have an account. */
    private val legalTeams: () -> List<TimerCandidate> = { emptyList() },
    /** When the private messages were last fetched completely, for the honest note of the alerts. */
    private val lastSync: () -> Long? = { null },
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** The account of the device (lowercase hexadecimal), or null. */
    var account: String? by mutableStateOf(null)
        private set

    private var transport: TimerTransport? = null

    /** An account and a way to send: a timer can be armed (the screen also checks S-146). */
    var canSend: Boolean by mutableStateOf(false)
        private set

    private var data: TimerDeviceData by mutableStateOf(TimerDeviceData())

    /** The clock of the screens, moved by [tick]. */
    var now: Long by mutableStateOf(clock())
        private set

    /** True while a code is being checked or the deposits prepared. */
    var busy: Boolean by mutableStateOf(false)
        private set

    var lastSyncAt: Long? by mutableStateOf(null)
        private set

    /** What to show after an action; null shows the screen itself. */
    var outcome: TimerOutcome? by mutableStateOf(null)
        private set

    /** Another screen (the home of "Agir") asks the protest mode to open the timer. */
    var screenRequested: Boolean by mutableStateOf(false)
        private set

    /** Asks the server for the private messages again (the shell: [MessagingSession.refresh]). */
    var refreshMessages: () -> Unit = {}

    /** Called one tick later with the alerts still due: the notification centre. */
    var onNotify: (List<RelativeTimer>) -> Unit = {}

    private val announced = mutableSetOf<String>()
    private val notified = mutableSetOf<String>()
    private var ticks = 0

    val own: OwnTimer?
        get() {
            val timer = data.armed ?: return null
            val waiting = data.pending.filter { it.timerId == timer.id && it.action == TimerFormats.ACTION_DEPOSIT }.map { it.recipient }.distinct().size
            return OwnTimer(timer.id, timer.shownDeadline, timer.recipients, waiting)
        }

    /** Every timer of relatives the device holds, resolved at [now]. */
    val relatives: List<RelativeTimer>
        get() = data.received.mapNotNull { TimerInbox.resolve(it, now) }.sortedBy { it.deadline }

    /** The alerts to show at the top of "Agir" and of the help sheet. */
    val alerts: List<RelativeTimer> get() = relatives.filter { it.phase == RelativeTimerPhase.ALERT }

    /** The discreet lines: the timers still running, and those confirmed in the last hours. */
    val quiet: List<RelativeTimer>
        get() = relatives.filter {
            it.phase == RelativeTimerPhase.WAITING ||
                (it.phase == RelativeTimerPhase.CANCELLED && now - (it.cancelledAt ?: 0L) <= CONFIRMED_SHOWN_SECONDS)
        }

    /** Who can be chosen, contacts first, then the legal teams; empty without an account. */
    fun candidates(): List<TimerCandidate> {
        val me = account ?: return emptyList()
        val people = runCatching { contacts.candidates(me) }.getOrDefault(emptyList()).filter { !it.legalTeam }
        val teams = runCatching { legalTeams() }.getOrDefault(emptyList()).map { it.copy(legalTeam = true) }
        return (people + teams).filter { it.key != me }.distinctBy { it.key }
    }

    /**
     * Follows the account of the device: [key] null without one. [forget] erases what the device
     * keeps (the keys were just erased: like the conversations, the timer goes with them). What a
     * former account left is never shown to another one.
     */
    suspend fun setAccount(key: String?, transport: TimerTransport?, forget: Boolean = false) {
        this.transport = transport
        account = key
        canSend = key != null && transport != null
        // A failure here must not stop the application; the quick erase reports its own failures.
        if (forget) safely { eraseFromDevice() }
        reload()
        if (transport != null && data.pending.isNotEmpty()) flushSoon()
    }

    /** Arms a timer; [onFailure] when something unexpected stopped it (nothing was armed then). */
    fun arm(request: ArmRequest, onFailure: () -> Unit = {}, onResult: (ArmResult) -> Unit) {
        val current = transport ?: return onFailure()
        work({ service.arm(request, current) }, onFailure) { result ->
            if (result is ArmResult.Armed) outcome = TimerOutcome.Armed(result.shownDeadline, result.recipients, result.delivery)
            onResult(result)
        }
    }

    /** « Je vais bien ». Both codes end the same way on screen; the real one sends the cancellations. */
    fun checkIn(code: String, onResult: (CheckInResult) -> Unit) {
        val current = transport ?: return
        work({ service.checkIn(code, current) }) { result ->
            if (result == CheckInResult.Cancelled) {
                outcome = TimerOutcome.Cancelled
                // Whatever waits (with the real code: the cancellations) leaves now; with the duress code, nothing new.
                flushSoon()
            }
            onResult(result)
        }
    }

    /** « Repousser » to [deadline]. */
    fun report(code: String, deadline: Long, onResult: (ReportResult) -> Unit) {
        val current = transport ?: return
        work({ service.report(code, deadline, current) }) { result ->
            if (result is ReportResult.Reported) {
                outcome = TimerOutcome.Reported(result.shownDeadline)
                flushSoon()
            }
            onResult(result)
        }
    }

    fun clearOutcome() {
        outcome = null
    }

    fun requestScreen() {
        screenRequested = true
    }

    fun screenShown() {
        screenRequested = false
    }

    /** Hides the alert of a relative (they had news by another way). */
    fun dismiss(timer: RelativeTimer) {
        scope.launch {
            safely { withContext(worker) { inbox.dismiss(timer.author, timer.id) } }
            reload()
        }
    }

    /** A rumor of a timer received by the messaging, on its worker ([IncomingRumors]). */
    suspend fun accept(rumor: Rumor, recipient: String) {
        if (inbox.accept(rumor, recipient)) scope.launch { reload() }
    }

    /** One beat of the clock of the screens (every [TICK_MILLIS] while the shell runs). */
    fun tick() {
        now = clock()
        val due = alerts
        val again = due.filter { keyOf(it) in announced && keyOf(it) !in notified }
        if (again.isNotEmpty()) {
            notified += again.map(::keyOf)
            onNotify(again)
        }
        val fresh = due.filter { keyOf(it) !in announced }
        ticks++
        val running = relatives.any { it.phase == RelativeTimerPhase.WAITING }
        if (fresh.isNotEmpty()) {
            announced += fresh.map(::keyOf)
            refreshMessages()
        } else if (running && ticks % REFRESH_EVERY_TICKS == 0) {
            // A cancellation or a report may be waiting on the server: fetched before the deadline rather than after.
            refreshMessages()
        }
        if (ticks % FLUSH_EVERY_TICKS == 0 && data.pending.isNotEmpty()) flushSoon()
    }

    /** Ticks for as long as the caller stays composed (the shell: the whole run). */
    suspend fun run() {
        while (true) {
            tick()
            delay(TICK_MILLIS)
        }
    }

    /**
     * The part `agir-minuteur` of the quick erase (S-44): the person's timer, its deliveries still
     * waiting and the timers of relatives received, then checked. It never cancels a deposit already
     * on a recipient's device: the alert still goes at its deadline, which is the point.
     */
    suspend fun eraseFromDevice() {
        withContext(worker) {
            store.clear()
            check(store.read().isEmpty) { "the timer is not erased" }
        }
        data = TimerDeviceData()
        outcome = null
        announced.clear()
        notified.clear()
    }

    /** Reads the device again. */
    suspend fun reload() {
        val read = safely { withContext(worker) { store.read() to runCatching { lastSync() }.getOrNull() } } ?: return
        val me = account
        val stored = read.first
        data = if (me != null && (stored.owner == null || stored.owner == me)) stored else TimerDeviceData()
        lastSyncAt = read.second
        now = clock()
    }

    private fun flushSoon() {
        val current = transport ?: return
        scope.launch {
            safely { withContext(worker) { service.flush(current) } }
            reload()
        }
    }

    private fun <T : Any> work(block: suspend () -> T, onFailure: () -> Unit = {}, onResult: (T) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val result = safely { withContext(worker) { block() } }
            busy = false
            reload()
            if (result != null) onResult(result) else onFailure()
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun keyOf(timer: RelativeTimer): String = timer.author + ":" + timer.id

    companion object {
        const val TICK_MILLIS: Long = 15_000
        const val FLUSH_EVERY_TICKS: Int = 4

        /** While a timer of a relative runs, the messages are asked again every 2 minutes. */
        const val REFRESH_EVERY_TICKS: Int = 8

        /** A confirmation of a relative stays as a discreet line this long. */
        const val CONFIRMED_SHOWN_SECONDS: Long = 12 * 3_600L

        /** The internal link of the notifications of the timer (opens "Agir" once the shell knows the way). */
        const val TARGET: String = "ppf:agir/minuteur"
    }
}

/** Lets tests and previews replace where the timer is kept; null uses the platform's. */
val LocalTimerStorage = staticCompositionLocalOf<TimerStorage?> { null }

/**
 * Where this platform keeps the timer: a private preference of the Android application (system
 * backups are forbidden by the manifest), a private file of the user's application data on a
 * computer, nothing on iOS yet. Null when there is none.
 */
@Composable
expect fun rememberPlatformTimerStorage(): TimerStorage?

/**
 * The timer of the shell, for the whole run (AG-55, D-088): it follows the account, registers the
 * handler of the timer rumors in the messaging of the process ([IncomingRumors.Process]), ticks,
 * and attaches itself to [help] so that the protest mode, the help sheet and the home of "Agir"
 * find it. The quick erase part is registered by the shell (`agir-minuteur`).
 */
@Composable
fun rememberTimerState(dependencies: AppDependencies, help: HelpState, session: AccountSession, messaging: MessagingSession): TimerState {
    val provided = LocalTimerStorage.current
    val platform = rememberPlatformTimerStorage()
    val ephemeral = remember(dependencies) { InMemoryTimerStorage() }
    // The timer lives no longer than the identity it belongs to: with an identity kept in memory
    // only (tests, previews), the timer is kept in memory too, never in the platform's file.
    val platformStorage = provided ?: if (platform != null && dependencies.identityStore is InMemoryIdentityStore) ephemeral else platform
    val scope = rememberCoroutineScope()
    val notificationTitle = stringResource(Res.string.timer_notif_title)
    val notificationText = stringResource(Res.string.timer_notif_text)
    val state = remember(dependencies, platformStorage) {
        val store = TimerStore(platformStorage ?: InMemoryTimerStorage())
        val security = dependencies.securityStorage
        // The Keystore key of the lock (D-082) also peppers the codes of the timer; none on a computer.
        val pepper: (ByteArray) -> ByteArray = { bytes -> security?.pepper(bytes) ?: bytes }
        val messages = dependencies.database?.let(::MessageStore)
        TimerState(
            service = TimerService(store, pepper),
            inbox = TimerInbox(store),
            store = store,
            scope = scope,
            hasStorage = platformStorage != null,
            hasServer = dependencies.remote != null,
            contacts = TimerRecipientSource { me ->
                messaging.conversations
                    .flatMap { it.conversation.participants }
                    .filter { it != me && messaging.alerts?.notices?.containsKey(it) != true }
                    .distinct()
                    .map { TimerCandidate(it) }
            },
            legalTeams = { help.legalTeamCandidates() },
            lastSync = { messages?.lastPullAt() },
        )
    }
    help.timer = state
    state.refreshMessages = { messaging.refresh() }
    state.onNotify = { due ->
        val notifications = dependencies.notifications
        if (notifications != null) {
            scope.launch {
                withContext(Dispatchers.Default) {
                    runCatching {
                        notifications.engine.deliver(
                            due.map {
                                NotificationCandidate(
                                    id = "agir-minuteur:" + it.author + ":" + it.id,
                                    category = NotificationCategory.MOBILISATIONS,
                                    title = notificationTitle,
                                    detail = notificationText,
                                    target = TimerState.TARGET,
                                    createdAt = it.deadline,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    // D-088: the rumors of timers received by the messaging come here (MessagingService.pull).
    DisposableEffect(state) {
        val remove = IncomingRumors.Process.register(TimerFormats.KIND) { rumor, recipient -> state.accept(rumor, recipient) }
        onDispose { remove() }
    }
    val key = (session.state as? AccountUiState.Ready)?.publicKey?.hex
    val holder = remember(state) { AccountMemory() }
    LaunchedEffect(state, key) {
        // Keys erased during the run (« Effacer mes clés », the quick erase): the timer goes with them.
        val signedOut = key == null && holder.hadAccount && session.state is AccountUiState.NoAccount
        if (key != null) holder.hadAccount = true
        if (signedOut) holder.hadAccount = false
        val signer = if (key != null) session.messagingSigner else null
        val remote = dependencies.remote
        val transport = if (signer != null && remote != null) MessagingTimerTransport(signer, remote) else null
        state.setAccount(key, transport, forget = signedOut)
    }
    LaunchedEffect(state) { state.run() }
    return state
}

/** Whether an account was seen during this run, to tell keys erased from a device that never had any. */
private class AccountMemory {
    var hadAccount: Boolean = false
}
