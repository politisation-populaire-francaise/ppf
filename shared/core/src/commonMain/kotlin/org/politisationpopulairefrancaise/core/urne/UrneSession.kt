package org.politisationpopulairefrancaise.core.urne

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.signing.EventSigner

/** What a synchronisation of the voting tokens did. */
sealed interface UrneSyncOutcome {
    /** [received] new tokens are on the device; [pending] boxes are still to be served (next pass). */
    data class Done(val received: Int, val pending: Int) : UrneSyncOutcome

    /** No network or the server refused: nothing changed, try again later. */
    data class Unavailable(val code: String?) : UrneSyncOutcome

    /** The self-test failed on this device: voting stays closed here (D-110, condition b). */
    data object VotingClosedOnThisDevice : UrneSyncOutcome

    /**
     * A proof did not match the public key the server announces to everyone (D-110, 6.2): the
     * server may be trying to recognise this device. Nothing is kept; it should be reported.
     */
    data object KeyMismatch : UrneSyncOutcome
}

/** What happened to a choice. */
sealed interface VoteOutcome {
    /** Recorded on the device; the ballot leaves after [sendAfter] (seconds since 1970), by itself. */
    data class Scheduled(val sendAfter: Long) : VoteOutcome

    /** No token for this box on the device yet (synchronise first). */
    data object NoToken : VoteOutcome

    data object UnknownChoice : VoteOutcome

    data object VotingClosedOnThisDevice : VoteOutcome
}

/**
 * Anonymous voting on the device (D-110, D-112; step 3 of the design).
 *
 * - [sync], with the account: registers it once, asks for a token for **every** open ballot box it
 *   has none for (everyone asks for everything: the request says nothing), checks every proof
 *   against the public key the server shows everyone, and keeps the tokens.
 * - [vote], without any network: records the choice and the moment the ballot may leave, drawn
 *   between 0 and [maxDelaySeconds] for everyone (a common protection, D-112, no setting).
 * - [sendDue]: sends the ballots whose moment has come, without the account, through the
 *   anonymous client of [UrneRemote]; a failure keeps them for the next pass.
 *
 * Voting stays closed when the self-test of the device failed.
 */
class UrneSession(
    private val store: UrneTokenStore,
    private val remote: UrneRemote,
    private val group: Ristretto255,
    private val now: () -> Long,
    private val selfTest: suspend () -> UrneSelfTestResult = { UrneSelfTest.onThisDevice() },
    private val randomDelay: (Int) -> Int = { bound -> uniform(bound) },
    private val maxDelaySeconds: Int = MAX_DELAY_SECONDS,
    private val batchSize: Int = BATCH_SIZE,
) {
    private val voprf = Voprf(group, VoprfMode.VOPRF)
    private val lock = Mutex()

    /**
     * The level of the account as the server last told it ([sync]); « élu » means public votes
     * ([votePublicly]) instead of anonymous ballots for the boxes opened while it is elected (D-115).
     */
    var level: VoteLevel? = null
        private set

    /** Registers if needed and fetches the tokens of the open boxes, a batch at a time. */
    suspend fun sync(signer: EventSigner): UrneSyncOutcome = lock.withLock {
        if (!selfTest().passed) return UrneSyncOutcome.VotingClosedOnThisDevice
        val account = signer.publicKey.hex
        val state = store.state()
        if (state == null || state.first != account) {
            // Tokens belong to one account: another account starts from nothing.
            store.clear()
            store.setState(account, registered = false)
        }
        val difficulties = when (val list = remote.list(0, 1)) {
            is UrneAnswer.Ok -> list.value.difficulties ?: Difficulties(0, 0, 0)
            is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(list.code)
        }
        if (store.state()?.second != true) {
            when (val registered = remote.register(signer, difficulties.register)) {
                is UrneAnswer.Ok -> store.setState(account, registered = true)
                is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(registered.code)
            }
        }
        var received = 0
        // A request whose answer never came back goes again first, unchanged: the server gives the same
        // answer to the same request for a day, so a lost answer costs no token and never gives two.
        val waiting = store.blinds()
        if (waiting.isNotEmpty()) {
            val status = when (val answer = remote.status(signer)) {
                is UrneAnswer.Ok -> answer.value
                is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(answer.code)
            }
            when (val replay = request(signer, status, waiting, difficulties)) {
                is Served.Tokens -> received += replay.received
                Served.Refused -> Unit // neither answered again nor still pending: those requests are gone
                is Served.Stopped -> return replay.outcome
            }
        }
        val status = when (val answer = remote.status(signer)) {
            is UrneAnswer.Ok -> answer.value
            is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(answer.code)
        }
        level = VoteLevel.of(status.level)
        val batch = status.pending.take(batchSize)
        if (batch.isEmpty()) return UrneSyncOutcome.Done(received, pending = 0)
        val blinds = batch.map { number ->
            val y = secureRandomBytes(32)
            val blinded = voprf.blind(y)
            PendingBlind(number, y, blinded.blind, blinded.blindedElement).also { store.saveBlind(it) }
        }
        when (val fresh = request(signer, status, blinds, difficulties)) {
            is Served.Tokens -> received += fresh.received
            Served.Refused -> return UrneSyncOutcome.Unavailable("pending_changed")
            is Served.Stopped -> return fresh.outcome
        }
        UrneSyncOutcome.Done(received, pending = (status.pending.size - batch.size).coerceAtLeast(0))
    }

    /** What became of one request of tokens. */
    private sealed interface Served {
        data class Tokens(val received: Int) : Served

        /** The server neither answers it again nor has those boxes pending: its blinds are dropped. */
        data object Refused : Served

        data class Stopped(val outcome: UrneSyncOutcome) : Served
    }

    /**
     * Sends the request of [blinds] (in the order of their numbers, so that a request sent again is
     * the same request), checks every proof against the public key the server shows everyone and
     * every level against the account's own, keeps the tokens. A blind left unanswered is dropped:
     * the same request would get the same answer.
     */
    private suspend fun request(signer: EventSigner, status: VoterStatus, blinds: List<PendingBlind>, difficulties: Difficulties): Served {
        val ordered = blinds.sortedBy { it.urneNumber }
        val byNumber = boxesFrom(ordered.first().urneNumber) ?: return Served.Stopped(UrneSyncOutcome.Unavailable(null))
        if (ordered.any { it.urneNumber !in byNumber }) return Served.Stopped(UrneSyncOutcome.Unavailable("unknown_urne"))
        // A box whose key changed since a token was received for it: the server is not showing everyone the same key.
        if (store.tokens().any { stored -> byNumber[stored.urneNumber]?.let { it.keys[stored.level.code] != stored.publicKey } == true }) {
            return Served.Stopped(UrneSyncOutcome.KeyMismatch)
        }
        val request = TokenRequest(ordered.map { BlindedRequest(it.urneNumber, it.blinded.toHexString()) })
        val response = when (val answer = remote.tokens(signer, request, difficulties.tokens)) {
            is UrneAnswer.Ok -> answer.value
            is UrneAnswer.Failure -> {
                if (answer.code != "pending_changed") return Served.Stopped(UrneSyncOutcome.Unavailable(answer.code))
                ordered.forEach { store.dropBlind(it.urneNumber) }
                return Served.Refused
            }
        }
        val blindsByNumber = ordered.associateBy { it.urneNumber }
        var received = 0
        for (issued in response.tokens) {
            val blind = blindsByNumber[issued.urne] ?: return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            val box = byNumber[issued.urne] ?: return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            val level = VoteLevel.of(issued.level) ?: return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            // The level of the week of opening, as the server states it to this account (D-110, 6.4).
            val expected = status.levelFor(box.opensAt)
            if (expected != null && level != expected) return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            val publicKey = box.keys[issued.level] ?: return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            val output = try {
                voprf.finalize(
                    blind.y,
                    VoprfBlinded(blind.blind, blind.blinded),
                    issued.evaluated.hexToByteArray(),
                    issued.proof.hexToByteArray(),
                    publicKey.hexToByteArray(),
                )
            } catch (e: VoprfError) {
                return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            } catch (e: IllegalArgumentException) {
                return Served.Stopped(UrneSyncOutcome.KeyMismatch)
            }
            store.saveToken(StoredToken(issued.urne, box.id, level, publicKey, blind.y, output, choice = null, sendAfter = null, sentChoice = null))
            received++
        }
        val answered = response.tokens.mapTo(HashSet()) { it.urne }
        ordered.filter { it.urneNumber !in answered }.forEach { store.dropBlind(it.urneNumber) }
        return Served.Tokens(received)
    }

    /** The boxes numbered from [first] on, page by page (500 at most each), until the batch is covered. */
    private suspend fun boxesFrom(first: Long): Map<Long, UrneInfo>? {
        val found = LinkedHashMap<Long, UrneInfo>()
        var after = first - 1
        repeat(MAX_LIST_PAGES) {
            val page = when (val answer = remote.list(after, LIST_LIMIT)) {
                is UrneAnswer.Ok -> answer.value.urnes
                is UrneAnswer.Failure -> return null
            }
            page.forEach { found[it.number] = it }
            if (page.size < LIST_LIMIT) return found
            after = page.last().number
            if (found.size >= batchSize * 5) return found
        }
        return found
    }

    /** Records [choice] for the box [urneNumber]; the ballot leaves later by itself ([sendDue]). */
    suspend fun vote(urneNumber: Long, choice: String, choices: List<String>): VoteOutcome = lock.withLock {
        if (!selfTest().passed) return VoteOutcome.VotingClosedOnThisDevice
        if (choice != UrneInfo.WITHDRAWN && choice !in choices) return VoteOutcome.UnknownChoice
        store.token(urneNumber) ?: return VoteOutcome.NoToken
        val sendAfter = now() + randomDelay(maxDelaySeconds + 1)
        store.choose(urneNumber, choice, sendAfter)
        VoteOutcome.Scheduled(sendAfter)
    }

    /** The choice recorded on this device for a box, and whether the server has it. */
    fun choiceOf(urneNumber: Long): Pair<String?, Boolean>? = store.token(urneNumber)?.let { it.choice to (it.choice != null && it.choice == it.sentChoice && it.sendAfter == null) }

    /**
     * Sends the ballot whose moment came first, one per call, with the proof of work every device
     * makes ([Ballot.DIFFICULTY], never a number the server could vary from one device to another);
     * returns how many the server accepted (0 or 1). Called often by one task of the application,
     * apart from [sync]: a ballot never leaves together with a request made with the account.
     */
    suspend fun sendDue(): Int = sendDue(Ballot.DIFFICULTY)

    /** [sendDue] with another proof of work, for the tests (a light one). */
    suspend fun sendDue(ballotDifficulty: Int): Int = lock.withLock {
        val token = store.due(now()).firstOrNull() ?: return 0
        val choice = token.choice ?: return 0
        val ballot = Ballot.withProofOfWork(token.urneNumber, token.level.code, token.y.toHexString(), token.token.toHexString(), choice, ballotDifficulty)
        when (val answer = remote.ballot(ballot)) {
            is UrneAnswer.Ok -> {
                store.markSent(token.urneNumber, choice)
                1
            }
            is UrneAnswer.Failure -> {
                when (answer.code) {
                    // Closed or unknown: it will never be accepted; keep the choice, stop sending.
                    "urne_closed", "unknown_urne", "invalid_token", "unknown_choice" -> store.stopSending(token.urneNumber)
                    // No network, rate limited: it leaves again at a new random moment, not with the others.
                    else -> store.choose(token.urneNumber, choice, now() + randomDelay(maxDelaySeconds + 1))
                }
                0
            }
        }
    }

    /**
     * After a pause (application closed, no network), the ballots whose moment passed leave again at
     * a new random moment: never all at once, never together with the synchronisation of the start.
     * Returns how many were drawn again.
     */
    suspend fun redrawOverdue(): Int = lock.withLock {
        val overdue = store.due(now())
        overdue.forEach { token -> token.choice?.let { store.choose(token.urneNumber, it, now() + randomDelay(maxDelaySeconds + 1)) } }
        overdue.size
    }

    /**
     * The public vote of a certified elected official (D-115): signed with the account's own key and
     * sent at once (it is public by nature: no delay, no anonymous channel). `retire` withdraws it.
     * Returns whether the server recorded it.
     */
    suspend fun votePublicly(signer: EventSigner, urne: UrneInfo, choice: String): Boolean {
        if (choice != UrneInfo.WITHDRAWN && choice !in urne.choices) return false
        val event = PublicVote.sign(signer, urne, choice, now())
        return remote.publicVote(event) is UrneAnswer.Ok
    }

    /** Erases every token, request and choice of this device (with the keys). */
    suspend fun forget() = lock.withLock { store.clear() }

    companion object {
        /** The ballot leaves between 0 and 10 minutes after the choice (D-112). */
        const val MAX_DELAY_SECONDS: Int = 600
        const val BATCH_SIZE: Int = 100
        private const val LIST_LIMIT: Int = 500
        private const val MAX_LIST_PAGES: Int = 4

        /** A uniform integer in [0, bound), from the secure random generator. */
        fun uniform(bound: Int): Int {
            require(bound > 0)
            val bytes = secureRandomBytes(4)
            val value = bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
            return (value % bound).toInt()
        }
    }
}
