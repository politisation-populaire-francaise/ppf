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
        val status = when (val answer = remote.status(signer)) {
            is UrneAnswer.Ok -> answer.value
            is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(answer.code)
        }
        val batch = status.pending.take(batchSize)
        if (batch.isEmpty()) return UrneSyncOutcome.Done(received = 0, pending = 0)
        val byNumber = when (val boxes = boxesFrom(batch.first())) {
            null -> return UrneSyncOutcome.Unavailable(null)
            else -> boxes
        }
        if (batch.any { it !in byNumber }) return UrneSyncOutcome.Unavailable("unknown_urne")
        // A box whose key changed since a token was received for it: the server is not showing everyone the same key.
        if (store.tokens().any { stored -> byNumber[stored.urneNumber]?.let { it.keys[stored.level.code] != stored.publicKey } == true }) {
            return UrneSyncOutcome.KeyMismatch
        }
        // A request already sent and not answered is sent again unchanged: no second token, none lost.
        val previous = store.blinds().associateBy { it.urneNumber }
        val blinds = batch.map { number ->
            previous[number] ?: run {
                val y = secureRandomBytes(32)
                val blinded = voprf.blind(y)
                PendingBlind(number, y, blinded.blind, blinded.blindedElement).also { store.saveBlind(it) }
            }
        }
        val request = TokenRequest(blinds.map { BlindedRequest(it.urneNumber, it.blinded.toHexString()) })
        val response = when (val answer = remote.tokens(signer, request, difficulties.tokens)) {
            is UrneAnswer.Ok -> answer.value
            is UrneAnswer.Failure -> return UrneSyncOutcome.Unavailable(answer.code)
        }
        val blindsByNumber = blinds.associateBy { it.urneNumber }
        var received = 0
        for (issued in response.tokens) {
            val blind = blindsByNumber[issued.urne] ?: return UrneSyncOutcome.KeyMismatch
            val box = byNumber[issued.urne] ?: return UrneSyncOutcome.KeyMismatch
            val level = VoteLevel.of(issued.level) ?: return UrneSyncOutcome.KeyMismatch
            val publicKey = box.keys[issued.level] ?: return UrneSyncOutcome.KeyMismatch
            val output = try {
                voprf.finalize(
                    blind.y,
                    VoprfBlinded(blind.blind, blind.blinded),
                    issued.evaluated.hexToByteArray(),
                    issued.proof.hexToByteArray(),
                    publicKey.hexToByteArray(),
                )
            } catch (e: VoprfError) {
                return UrneSyncOutcome.KeyMismatch
            } catch (e: IllegalArgumentException) {
                return UrneSyncOutcome.KeyMismatch
            }
            store.saveToken(StoredToken(issued.urne, box.id, level, publicKey, blind.y, output, choice = null, sendAfter = null, sentChoice = null))
            received++
        }
        UrneSyncOutcome.Done(received, pending = (status.pending.size - received).coerceAtLeast(0))
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

    /** Sends the ballots whose moment has come; returns how many the server accepted. */
    suspend fun sendDue(ballotDifficulty: Int): Int = lock.withLock {
        var accepted = 0
        for (token in store.due(now())) {
            val choice = token.choice ?: continue
            val ballot = Ballot.withProofOfWork(token.urneNumber, token.level.code, token.y.toHexString(), token.token.toHexString(), choice, ballotDifficulty)
            when (val answer = remote.ballot(ballot)) {
                is UrneAnswer.Ok -> {
                    store.markSent(token.urneNumber, choice)
                    accepted++
                }
                is UrneAnswer.Failure -> when (answer.code) {
                    // Closed or unknown: it will never be accepted; keep the choice, stop sending.
                    "urne_closed", "unknown_urne", "invalid_token", "unknown_choice" -> store.stopSending(token.urneNumber)
                    else -> Unit // no network, rate limited: next pass
                }
            }
        }
        accepted
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
