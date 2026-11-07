package org.politisationpopulairefrancaise.server.urne

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.urne.Ballot
import org.politisationpopulairefrancaise.core.urne.IssuedToken
import org.politisationpopulairefrancaise.core.urne.LibsodiumRistretto255
import org.politisationpopulairefrancaise.core.urne.ProofOfWork
import org.politisationpopulairefrancaise.core.urne.PublishedBallot
import org.politisationpopulairefrancaise.core.urne.TokenRequest
import org.politisationpopulairefrancaise.core.urne.TokenResponse
import org.politisationpopulairefrancaise.core.urne.UrneClosing
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.core.urne.UrneKeyInfo
import org.politisationpopulairefrancaise.core.urne.UrneTally
import org.politisationpopulairefrancaise.core.urne.VoprfError
import org.politisationpopulairefrancaise.core.urne.VoprfKeyPair
import org.politisationpopulairefrancaise.core.urne.VoprfMode
import org.politisationpopulairefrancaise.core.urne.Voprf
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.core.urne.UrneSelfTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Why a request about votes was refused: a fixed code, safe to return, never a value of the request. */
class UrneRefusal(val status: Int, val code: String) : Exception(code)

/**
 * The ballot boxes on the server (D-110, D-112; `docs/integration/une-personne-une-voix.md`).
 *
 * Registration and token delivery are authenticated (NIP-98, by the routes); ballots never are.
 * A ballot is accepted when its token is the VOPRF output of its secret identifier under the key
 * of its ballot box and level: the server can check it, never tell whose it is. Nothing here logs
 * a key, a token, an identifier or a body.
 */
class UrneService(
    private val store: UrneStore,
    private val config: UrneConfig,
    private val voprf: Voprf,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val keyCache = ConcurrentHashMap<Pair<Long, VoteLevel>, VoprfKeyPair>()
    private val random = SecureRandom()

    val available: Boolean get() = config.seeds != null

    // ------------------------------------------------------------------ opening (server code only)

    /**
     * Opens a ballot box with the current seed; the same [id] twice returns the existing one. A box
     * opens when it is opened: [opensAt] is now or past, never a later moment (its number of order is
     * given here, and the delivery of tokens follows that order: a box numbered now and opened later
     * would be skipped by every account served meanwhile; review R5).
     */
    fun open(id: String, subject: String, choices: List<String>, opensAt: Instant, closesAt: Instant?, liveResults: Boolean): Urne {
        val seeds = config.seeds ?: throw UrneRefusal(503, "voting_unavailable")
        require(choices.isNotEmpty() && choices.none { it == UrneInfo.WITHDRAWN } && choices.distinct().size == choices.size) { "invalid choices" }
        require('\u001f' !in id) { "invalid identifier" }
        store.urneById(id)?.let { return it }
        require(!opensAt.isAfter(clock.instant())) { "a ballot box is opened when it opens, never in advance" }
        val salt = ByteArray(UrneKeyInfo.SALT_SIZE).also(random::nextBytes)
        return store.open(id, subject, choices, seeds.current, opensAt, closesAt, mondayOf(opensAt), liveResults, salt)
    }

    /** The open boxes of one use (their identifiers start with [prefix]), for the code that opens them. */
    fun openWithPrefix(prefix: String): List<Urne> = store.openWithPrefix(prefix)

    // ------------------------------------------------------------------ public

    /** The boxes after [after], with the results of those that show them, summed in one query. */
    fun list(after: Long, limit: Int): List<UrneInfo> {
        val urnes = store.urnes(after, limit.coerceIn(1, 500))
        val counts = store.countsByBox(urnes.filter { it.liveResults || it.closed }.map { it.number })
        return urnes.map { urne ->
            info(urne).copy(results = if (urne.liveResults || urne.closed) UrneTally.fromCounts(counts[urne.number].orEmpty()) else null)
        }
    }

    fun journal(number: Long): UrneJournal {
        val urne = store.urne(number) ?: throw UrneRefusal(404, "unknown_urne")
        val ballots = store.ballots(number)
        val counted = ballots.filter { it.choice != UrneInfo.WITHDRAWN }
        return UrneJournal(
            number = number,
            accounts = store.accountsByLevel(),
            issued = store.issuedByLevel(number),
            ballots = VoteLevel.entries.associate { level -> level.code to counted.count { it.level == level }.toLong() },
            replacements = store.replacements(number),
            results = if (urne.liveResults || urne.closed) UrneTally.results(ballots.map { it.level to it.choice }) else null,
            closed = urne.closed,
        )
    }

    /** What is published at the closing: keys, anonymous ballots sorted by y, totals (D-110, 6.7). */
    fun closing(number: Long): UrneClosing {
        val urne = store.urne(number) ?: throw UrneRefusal(404, "unknown_urne")
        if (!urne.closed) throw UrneRefusal(409, "not_closed")
        val ballots = store.ballots(number)
        return UrneClosing(
            number = number,
            id = urne.id,
            secretKeys = VoteLevel.entries.associate { it.code to keys(urne, it).secretKey.toHexString() },
            publicKeys = VoteLevel.entries.associate { it.code to keys(urne, it).publicKey.toHexString() },
            issued = store.issuedByLevel(number),
            ballots = ballots.map { PublishedBallot(it.y.toHexString(), it.token.toHexString(), it.level.code, it.choice, it.hour.truncatedTo(ChronoUnit.DAYS).epochSecond) },
            results = UrneTally.results(ballots.map { it.level to it.choice }),
        )
    }

    // ------------------------------------------------------------------ authenticated (the routes check NIP-98)

    /** Registers [pubkey] (not verified) and returns its status; [created] tells whether it is new. */
    fun register(pubkey: String): Pair<VoterStatus, Boolean> {
        val created = store.register(pubkey, mondayOf(clock.instant()))
        return status(pubkey) to created
    }

    fun status(pubkey: String): VoterStatus {
        val account = store.account(pubkey) ?: throw UrneRefusal(404, "not_registered")
        return VoterStatus(
            level = account.level.code,
            levelWeek = account.levelWeek.toString(),
            pending = pending(account).map { it.number },
            previousLevel = account.previousLevel.code,
            registeredWeek = account.registeredWeek.toString(),
        )
    }

    /**
     * Delivers tokens for a prefix of the account's pending ballot boxes, every one in order (D-110,
     * 6.3: everyone asks for everything, so the request says nothing). The same request again within a
     * day gets the same answer: a lost answer never costs a token, and never gives a second one.
     */
    fun issue(pubkey: String, body: String): TokenResponse {
        val account = store.account(pubkey) ?: throw UrneRefusal(404, "not_registered")
        val requestHash = MessageDigest.getInstance("SHA-256").digest(body.encodeToByteArray())
        val today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
        if (account.lastRequest != null && MessageDigest.isEqual(account.lastRequest, requestHash) &&
            account.lastResponseDay != null && !account.lastResponseDay.isBefore(today.minusDays(1))
        ) {
            return Json.decodeFromString(TokenResponse.serializer(), account.lastResponse!!)
        }
        val request = runCatching { Json.decodeFromString(TokenRequest.serializer(), body) }.getOrNull() ?: throw UrneRefusal(400, "malformed_request")
        if (request.requests.size > config.maxTokensPerRequest) throw UrneRefusal(400, "too_many_tokens")
        val pending = pending(account)
        if (request.requests.isEmpty()) return TokenResponse(emptyList(), account.issuedUpTo)
        val expected = pending.take(request.requests.size)
        if (expected.size != request.requests.size || expected.map { it.number } != request.requests.map { it.urne }) {
            throw UrneRefusal(409, "pending_changed")
        }
        val tokens = expected.zip(request.requests).map { (urne, asked) ->
            val level = account.levelAt(urne.openedWeek)
            val blinded = runCatching { asked.blinded.hexToByteArray() }.getOrNull() ?: throw UrneRefusal(400, "malformed_request")
            val evaluation = try {
                voprf.blindEvaluate(keys(urne, level), listOf(blinded))
            } catch (e: VoprfError) {
                throw UrneRefusal(400, "invalid_element")
            }
            IssuedToken(urne.number, level.code, evaluation.evaluatedElements.single().toHexString(), evaluation.proof!!.toHexString())
        }
        val response = TokenResponse(tokens, expected.last().number)
        val json = Json.encodeToString(TokenResponse.serializer(), response)
        val outcome = store.deliver(pubkey, account.issuedUpTo, expected.last().number, tokens.map { it.urne to VoteLevel.of(it.level)!! }, requestHash, json, today)
        if (outcome == Delivery.CURSOR_MOVED) throw UrneRefusal(409, "pending_changed")
        return response
    }

    // ------------------------------------------------------------------ anonymous

    /** Records an anonymous ballot (D-110, 6.5 and 6.6): a new choice with the same y replaces the old one. */
    fun ballot(ballot: Ballot) {
        val now = clock.instant()
        val urne = store.urne(ballot.urne) ?: throw UrneRefusal(404, "unknown_urne")
        if (!urne.isOpenAt(now)) throw UrneRefusal(409, "urne_closed")
        val level = VoteLevel.of(ballot.level) ?: throw UrneRefusal(400, "unknown_level")
        if (ballot.choice != UrneInfo.WITHDRAWN && ballot.choice !in urne.choices) throw UrneRefusal(400, "unknown_choice")
        val y = runCatching { ballot.y.hexToByteArray() }.getOrNull()?.takeIf { it.size == 32 } ?: throw UrneRefusal(400, "malformed_ballot")
        val token = runCatching { ballot.token.hexToByteArray() }.getOrNull()?.takeIf { it.size == Voprf.OUTPUT_SIZE } ?: throw UrneRefusal(400, "malformed_ballot")
        if (ProofOfWork.leadingZeroBits(ballot.powDigest()) < config.ballotDifficulty) throw UrneRefusal(400, "proof_of_work_too_low")
        val expected = try {
            voprf.evaluate(keys(urne, level).secretKey, y)
        } catch (e: VoprfError) {
            throw UrneRefusal(400, "malformed_ballot")
        }
        if (!MessageDigest.isEqual(expected, token)) throw UrneRefusal(403, "invalid_token")
        store.recordBallot(urne.number, y, token, level, ballot.choice, now.truncatedTo(ChronoUnit.HOURS))
    }

    // ------------------------------------------------------------------ closing

    /** Closes the ballot boxes whose end has come; their keys and ballots become public (D-110, 6.7). */
    fun closeDue(): Int = store.closeDue(clock.instant()).size

    /**
     * Closes the ballot box [id] early (a cancelled mobilisation, a call closed by its author, a
     * demand that leaves the debate): its end moves to [at], or to now when [at] has passed, and
     * never later than it was. The closing and its publication then follow the usual course; an end
     * already reached closes at once. Returns the box as it now is, or null when there is none.
     */
    fun closeEarly(id: String, at: Instant): Urne? {
        val now = clock.instant()
        val end = if (at.isBefore(now)) now else at
        store.bringEndForward(id, end)
        if (!end.isAfter(now)) store.closeDue(now)
        return store.urneById(id)
    }

    /**
     * The task of the ballot boxes, every [every]: first [keepers] open and close the boxes of each
     * use (the demands in debate, [RevendicationUrnes]), then the boxes whose end has come close.
     */
    fun startClosing(application: Application, every: Duration, log: Logger, keepers: List<() -> Unit> = emptyList()): Job {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        application.monitor.subscribe(ApplicationStopped) { scope.cancel() }
        return scope.launch {
            while (isActive) {
                for (keeper in keepers) {
                    try {
                        keeper()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Voting: opening of ballot boxes failed (${e::class.simpleName}).")
                    }
                }
                try {
                    closeDue()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Voting: closing of ballot boxes failed (${e::class.simpleName}).")
                }
                delay(every)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun pending(account: VoteAccount): List<Urne> = store.openAfter(account.issuedUpTo, clock.instant(), config.maxTokensPerRequest + 1)

    private fun info(urne: Urne) = UrneInfo(
        number = urne.number,
        id = urne.id,
        subject = urne.subject,
        choices = urne.choices,
        opensAt = urne.opensAt.epochSecond,
        closesAt = urne.closesAt?.epochSecond,
        seedVersion = urne.seedVersion,
        keys = VoteLevel.entries.associate { it.code to keys(urne, it).publicKey.toHexString() },
        liveResults = urne.liveResults,
        closed = urne.closed,
    )

    private fun keys(urne: Urne, level: VoteLevel): VoprfKeyPair = keyCache.getOrPut(urne.number to level) {
        val seed = config.seeds?.seed(urne.seedVersion) ?: throw UrneRefusal(503, "voting_unavailable")
        voprf.deriveKeyPair(seed, UrneKeyInfo.of(urne.id, urne.salt, level))
    }

    companion object {
        val CLOSING_INTERVAL: Duration = 1.minutes

        /** The Monday of the week of [instant] (UTC): levels and openings are kept by the week, never the day. */
        fun mondayOf(instant: Instant): LocalDate =
            LocalDate.ofInstant(instant, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /**
         * The service with libsodium loaded (blocking: called once at startup). The known answers of
         * RFC 9497 are replayed first with the libsodium of this machine; if they fail, voting stays
         * unavailable (503) rather than issue tokens nobody could check (review R5).
         */
        fun create(store: UrneStore, config: UrneConfig, clock: Clock = Clock.systemUTC()): UrneService {
            val group = runBlocking { LibsodiumRistretto255.load() }
            val selfTest = UrneSelfTest.run(group)
            val checked = if (selfTest.passed) {
                config
            } else {
                LoggerFactory.getLogger(UrneService::class.java).error("Voting: the self-test of the VOPRF failed at ${selfTest.failedCheck}; voting is unavailable.")
                config.copy(seeds = null)
            }
            return UrneService(store, checked, Voprf(group, VoprfMode.VOPRF), clock)
        }
    }
}
