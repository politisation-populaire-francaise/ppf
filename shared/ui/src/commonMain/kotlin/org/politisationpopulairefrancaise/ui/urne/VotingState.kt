package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.urne.Ristretto255
import org.politisationpopulairefrancaise.core.urne.UrneAnswer
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneRemote
import org.politisationpopulairefrancaise.core.urne.UrneSession
import org.politisationpopulairefrancaise.core.urne.UrneSyncOutcome
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState

/**
 * Anonymous voting in the application (D-110, D-112, step 4 of the design), one for the whole run.
 *
 * It keeps the public list of the ballot boxes with their results (asked for by the anonymous
 * client, the whole list at once: reading it says nothing of what one looks at), the tokens and the
 * choices of this device, and two tasks that never run together:
 * - [syncTokens], with the account, at the start and then about every half hour (or only when a
 *   vote is shown, in data saver mode, D-110, 6.3): a token for **every** open box;
 * - [sendDue], without the account, every few seconds: the ballot whose random moment came, one at
 *   a time, never in the same pass as a synchronisation.
 *
 * Without a server or a local database, voting is shown as unavailable and nothing runs.
 */
class VotingState internal constructor(
    private val remote: UrneRemote?,
    private val store: UrneTokenStore?,
    private val scope: CoroutineScope,
    private val now: () -> Long = { currentEpochSeconds() },
    private val loadGroup: suspend () -> Ristretto255 = { LibsodiumRistretto255.load() },
) {
    /** Voting can work here: a server and a local database. */
    val enabled: Boolean get() = remote != null && store != null

    /** The ballot boxes, by the address of what is voted on (`kind:key:d`). */
    var boxes: Map<String, UrneInfo> by mutableStateOf(emptyMap())
        private set

    /** Numbers of the boxes this device holds a token for. */
    var tokens: Set<Long> by mutableStateOf(emptySet())
        private set

    /** The choice of this device in each box (number), and whether the server has it. */
    var choices: Map<Long, Pair<String?, Boolean>> by mutableStateOf(emptyMap())
        private set

    /**
     * A proof or a level did not match what the server shows everyone (D-110, 6.2): the server may be
     * trying to recognise this device. Shown in "Mon espace"; nothing was kept.
     */
    var alert: Boolean by mutableStateOf(false)
        private set

    /** The self-test failed on this device: voting stays closed here (D-110, condition b). */
    var closedHere: Boolean by mutableStateOf(false)
        private set

    /**
     * The account is a certified elected official (level « élu », D-115): its votes are public,
     * signed with its key and sent at once, instead of anonymous ballots.
     */
    var elected: Boolean by mutableStateOf(false)
        private set

    /** The public votes of this elected account, by box number (what the server recorded). */
    var publicChoices: Map<Long, String> by mutableStateOf(emptyMap())
        private set

    /** The account's signer, for the public votes of an elected official (set by [RunVoting]). */
    internal var signerProvider: () -> EventSigner? = { null }

    /** An account is open on this device: voting needs one (set by [RunVoting]). */
    var hasAccount: Boolean by mutableStateOf(false)
        internal set

    /**
     * A vote on screen waits for its box (a demand just put in debate) or for its token: the next
     * synchronisation comes within minutes instead of half an hour. Set by the vote, reset at each pass.
     */
    var awaited: Boolean = false
        internal set

    /** Set when a vote is shown: in data saver mode, the tokens wait for it (D-110, 6.3). */
    var wanted: Boolean by mutableStateOf(false)
        private set

    private var session: UrneSession? = null
    private val sessionLock = Mutex()

    private suspend fun session(): UrneSession? = sessionLock.withLock {
        session ?: run {
            val tokenStore = store ?: return@withLock null
            val voteRemote = remote ?: return@withLock null
            val group = try {
                // Loading the native library may take a moment: never on the main thread.
                withContext(Dispatchers.Default) { loadGroup() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                closedHere = true
                return@withLock null
            }
            UrneSession(tokenStore, voteRemote, group, now).also { session = it }
        }
    }

    /** What the vote control shows for the box of [subject]; null when nothing is put to the vote there. */
    fun stateFor(subject: String): VoteUiState? {
        val box = boxes[subject] ?: return null
        if (elected) {
            val choice = publicChoices[box.number]
            return VoteUiState(available = enabled && !closedHere, hasToken = true, closed = box.closed, choice = choice, sent = choice != null)
        }
        val choice = choices[box.number]
        return VoteUiState(
            available = enabled && !closedHere,
            hasToken = box.number in tokens,
            closed = box.closed,
            choice = choice?.first,
            sent = choice?.second ?: false,
        )
    }

    fun box(subject: String): UrneInfo? = boxes[subject]

    /** Records [choice] for the box of [subject]; the ballot leaves later, by itself, without the account. */
    fun choose(subject: String, choice: String) {
        val box = boxes[subject] ?: return
        scope.launch {
            val current = session() ?: return@launch
            if (elected) {
                // Public by nature: signed with the account's key and sent at once (D-115).
                val signer = signerProvider() ?: return@launch
                if (withContext(Dispatchers.Default) { current.votePublicly(signer, box, choice) }) {
                    publicChoices = publicChoices + (box.number to choice)
                    refreshBoxes()
                }
                return@launch
            }
            withContext(Dispatchers.Default) { current.vote(box.number, choice, box.choices) }
            reloadDevice()
        }
    }

    /** A vote is on screen: in data saver mode, the next pass of [syncTokens] may run. */
    fun shown() {
        wanted = true
    }

    /** A vote on screen waits for its box or its token: the next pass comes sooner. */
    fun awaiting() {
        awaited = true
    }

    /** The public list of every box, page by page (no account, the anonymous client). */
    suspend fun refreshBoxes() {
        val voteRemote = remote ?: return
        val found = LinkedHashMap<String, UrneInfo>()
        var after = 0L
        repeat(MAX_LIST_PAGES) {
            val page = when (val answer = voteRemote.list(after, LIST_LIMIT)) {
                is UrneAnswer.Ok -> answer.value.urnes
                is UrneAnswer.Failure -> return
            }
            page.forEach { found[it.subject] = it }
            if (page.size < LIST_LIMIT) {
                boxes = found
                return
            }
            after = page.last().number
        }
        boxes = found
    }

    /** Moments (seconds) of the last request made with the account, and of the last ballot sent. */
    private var lastSync = 0L
    private var lastSend = 0L

    /**
     * Tokens for every open box, a batch at a time, with the account (registration included). It
     * waits until [QUIET_SECONDS] have passed since the last ballot sent: a request made with the
     * account never follows a ballot closely.
     */
    suspend fun syncTokens(signer: EventSigner) {
        val current = session() ?: return
        while (true) {
            val sinceSend = now() - lastSend
            if (sinceSend >= QUIET_SECONDS) break
            delay((QUIET_SECONDS - sinceSend) * 1000)
        }
        lastSync = now()
        try {
            syncBatches(current, signer)
        } finally {
            lastSync = now()
            elected = current.level == VoteLevel.ELU
        }
    }

    private suspend fun syncBatches(current: UrneSession, signer: EventSigner) {
        repeat(MAX_BATCHES) {
            when (val outcome = withContext(Dispatchers.Default) { current.sync(signer) }) {
                is UrneSyncOutcome.Done -> if (outcome.pending == 0 || outcome.received == 0) {
                    reloadDevice()
                    return
                }
                is UrneSyncOutcome.Unavailable -> {
                    reloadDevice()
                    return
                }
                UrneSyncOutcome.KeyMismatch -> {
                    alert = true
                    return
                }
                UrneSyncOutcome.VotingClosedOnThisDevice -> {
                    closedHere = true
                    return
                }
            }
        }
        reloadDevice()
    }

    /**
     * Sends the ballot whose moment came, if any (one per call), but never within [QUIET_SECONDS]
     * of a request made with the account: the ballot then waits for a later pass.
     */
    suspend fun sendDue() {
        val current = session() ?: return
        if (now() - lastSync < QUIET_SECONDS) return
        if (withContext(Dispatchers.Default) { current.sendDue() } > 0) {
            lastSend = now()
            reloadDevice()
            // The results with this ballot: the public list again, by the anonymous client as always.
            refreshBoxes()
        }
    }

    /** At the start: the ballots whose moment passed while the application was closed get a new one. */
    suspend fun redrawOverdue() {
        val current = session() ?: return
        withContext(Dispatchers.Default) { current.redrawOverdue() }
        reloadDevice()
    }

    /** Erases every token and choice of this device (the account was erased). */
    fun forget() {
        scope.launch {
            session()?.forget()
            tokens = emptySet()
            choices = emptyMap()
            alert = false
        }
    }

    private suspend fun reloadDevice() {
        val tokenStore = store ?: return
        val stored = withContext(Dispatchers.Default) { tokenStore.tokens() }
        tokens = stored.mapTo(HashSet()) { it.urneNumber }
        choices = stored.associate { it.urneNumber to (it.choice to (it.choice != null && it.choice == it.sentChoice && it.sendAfter == null)) }
    }

    /** Called once by [rememberVotingState]: what the device already holds. */
    internal suspend fun loadDevice() = reloadDevice()

    companion object {
        private const val LIST_LIMIT = 500
        private const val MAX_LIST_PAGES = 20
        private const val MAX_BATCHES = 10

        /** About every half hour, plus a random part: the synchronisations do not tell the time of a vote. */
        const val SYNC_EVERY_SECONDS: Long = 30 * 60
        const val SYNC_JITTER_SECONDS: Int = 10 * 60

        /** The sooner pass while a vote on screen waits ([awaited]). */
        const val SOON_SECONDS: Long = 2 * 60
        const val SOON_JITTER_SECONDS: Int = 2 * 60

        /** How often the device looks for a ballot whose moment came. */
        const val SEND_CHECK_SECONDS: Long = 15

        /** No ballot this close to a request made with the account, and no such request this close to a ballot. */
        const val QUIET_SECONDS: Long = 120

        fun of(dependencies: AppDependencies, scope: CoroutineScope): VotingState =
            VotingState(dependencies.urneRemote, dependencies.database?.let(::UrneTokenStore), scope)
    }
}

/** The voting of the application, kept for the whole run (its tasks run in [RunVoting]). */
@Composable
fun rememberVotingState(dependencies: AppDependencies): VotingState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) { VotingState.of(dependencies, scope) }
}

/**
 * The two tasks of [voting], placed once in the shell: the sending of the ballots, alone, and the
 * synchronisation of the tokens while an account is open (in data saver mode, only once a vote is
 * shown). Nothing runs without a server or a local database.
 */
@Composable
fun RunVoting(voting: VotingState, account: AccountSession, dataSaver: () -> Boolean) {
    val accountKey = (account.state as? AccountUiState.Ready)?.publicKey?.hex
    SideEffect {
        voting.hasAccount = accountKey != null
        voting.signerProvider = { account.signer }
    }
    if (!voting.enabled) return
    LaunchedEffect(voting) {
        voting.loadDevice()
        voting.redrawOverdue()
        while (true) {
            delay(VotingState.SEND_CHECK_SECONDS * 1000)
            runCatching { voting.sendDue() }.onFailure { if (it is CancellationException) throw it }
        }
    }
    LaunchedEffect(voting, accountKey, voting.wanted) {
        while (true) {
            if (!dataSaver() || voting.wanted) {
                voting.awaited = false
                runCatching { voting.refreshBoxes() }.onFailure { if (it is CancellationException) throw it }
                val signer = account.signer
                if (accountKey != null && signer != null) {
                    runCatching { voting.syncTokens(signer) }.onFailure { if (it is CancellationException) throw it }
                }
            }
            // About every half hour; sooner (a few minutes) while a vote on screen waits for its box or its token.
            val usual = VotingState.SYNC_EVERY_SECONDS + UrneSession.uniform(VotingState.SYNC_JITTER_SECONDS)
            val soon = VotingState.SOON_SECONDS + UrneSession.uniform(VotingState.SOON_JITTER_SECONDS)
            var waited = 0L
            while (waited < usual && !(voting.awaited && waited >= soon)) {
                delay(VotingState.SEND_CHECK_SECONDS * 1000)
                waited += VotingState.SEND_CHECK_SECONDS
            }
        }
    }
}
