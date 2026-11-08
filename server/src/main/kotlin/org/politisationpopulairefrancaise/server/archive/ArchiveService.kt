package org.politisationpopulairefrancaise.server.archive

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.archive.BitcoinCheck
import org.politisationpopulairefrancaise.core.archive.BitcoinHeader
import org.politisationpopulairefrancaise.core.archive.OtsAttestation
import org.politisationpopulairefrancaise.core.archive.OtsBatch
import org.politisationpopulairefrancaise.core.archive.OtsBitcoinVerifier
import org.politisationpopulairefrancaise.core.archive.OtsFile
import org.politisationpopulairefrancaise.core.archive.OtsTimestamp
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.Random
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The timestamped archive on the server (D-111, docs/integration/archivage-horodate.md).
 *
 * Server code calls [stamp] with the SHA-256 digest of a public content; the digests queued during
 * an interval leave together: [stampRound] puts them in one Merkle tree ([OtsBatch], 16 random bytes
 * per leaf) and sends only its root to every aggregator; the batch counts as stamped with at least
 * [ArchiveConfig.MIN_AGGREGATOR_ANSWERS] answers, otherwise its digests stay queued for the next
 * batch (with new nonces, hence a new root). The root also gets an RFC 3161 token.
 * [upgradeRound] asks the calendars of the whitelist for the rest of each pending proof, then checks
 * every Bitcoin attestation against the header given by two independent explorers.
 *
 * What leaves the server: a batch root (aggregators, timestamping authority), intermediate values of
 * the proofs (calendars), block heights and hashes (explorers). The logs get counts, short codes and
 * class names only: never a digest, a proof, an address holding a digest, nor a body received.
 */
class ArchiveService internal constructor(
    private val store: ArchiveStore,
    private val config: ArchiveConfig,
    /** Null when the archive is switched off: nobody is contacted. */
    private val http: ArchiveHttp?,
    private val clock: Clock,
    private val random: Random,
    pause: suspend (Duration) -> Unit,
) {
    private val mutex = Mutex()
    private val tsa: Rfc3161Client? = if (http != null && config.tsaUrl != null) {
        Rfc3161Client(http, config.tsaUrl, random, MinimumSpacing(Rfc3161Client.SPACING, pause))
    } else {
        null
    }

    /** True when the server contacts the services (PPF_ARCHIVE_ENABLED). */
    val enabled: Boolean get() = http != null

    /**
     * Queues [digest] (a SHA-256, 32 bytes) for the next batch; false when it is already known.
     * Works even when the archive is off: the digests then wait until it is switched on. Blocking
     * (database): call it from Dispatchers.IO.
     */
    fun stamp(digest: ByteArray): Boolean {
        require(digest.size == DIGEST_SIZE) { "a SHA-256 digest is $DIGEST_SIZE bytes" }
        return store.queue(digest.copyOf(), LocalDate.now(clock))
    }

    /** The stored proof of [digest], or null. Blocking (database). */
    fun find(digest: ByteArray): StoredProof? = store.find(digest)

    /** What a stamping round did (for the tests and the logs). */
    data class StampOutcome(val digests: Int, val answers: Int, val stamped: Boolean, val rfc3161: Boolean = false)

    /** What an upgrade round did. [notVerified] counts the codes of the Bitcoin checks that failed. */
    data class UpgradeOutcome(
        val proofs: Int = 0,
        val calendarRequests: Int = 0,
        val upgraded: Int = 0,
        val anchored: Int = 0,
        val refusedCalendars: Int = 0,
        val checked: Int = 0,
        val verified: Int = 0,
        val notVerified: Map<String, Int> = emptyMap(),
        /** Short codes of what went wrong (calendar unavailable, unreadable answer...), counted. */
        val problems: Map<String, Int> = emptyMap(),
    )

    /** Sends the digests queued now as one batch. */
    suspend fun stampRound(): StampOutcome = mutex.withLock { stampBatch() }

    /** Upgrades the pending proofs, then checks the anchored ones against Bitcoin. */
    suspend fun upgradeRound(): UpgradeOutcome = mutex.withLock {
        val http = http ?: return@withLock UpgradeOutcome()
        val counts = Counts()
        upgrade(http, counts)
        check(http, counts)
        counts.outcome().also(::logRound)
    }

    /** Asks an RFC 3161 token again for the recent batches still without one; returns how many were stored. */
    suspend fun timestampRound(): Int = mutex.withLock {
        val tsa = tsa ?: return@withLock 0
        val roots = io { store.rootsWithoutToken(LocalDate.now(clock).minusDays(TSA_RETRY_DAYS), MAX_TSA_PER_ROUND) }
        var stored = 0
        for (root in roots) {
            when (timestampRoot(tsa, root)) {
                is Rfc3161Result.Accepted -> stored++
                is Rfc3161Result.Refused -> Unit
                // The authority is down: the other batches wait for the next round.
                is Rfc3161Result.Unavailable -> break
            }
        }
        stored
    }

    // ---------------------------------------------------------------- stamping

    private sealed interface Submission {
        class Accepted(val proof: OtsTimestamp) : Submission

        data class Refused(val code: String) : Submission
    }

    private suspend fun stampBatch(): StampOutcome {
        val http = http ?: return StampOutcome(0, 0, false)
        val digests = io { store.queued(MAX_BATCH) }
        if (digests.isEmpty()) return StampOutcome(0, 0, false)
        val batch = OtsBatch(digests) { ByteArray(OtsBatch.NONCE_SIZE).also(random::nextBytes) }
        val root = batch.root
        val answers = coroutineScope { config.aggregators.map { base -> async { submit(http, base, root) } }.awaitAll() }
        val proofs = answers.filterIsInstance<Submission.Accepted>().map { it.proof }
        if (proofs.size < ArchiveConfig.MIN_AGGREGATOR_ANSWERS) {
            val codes = answers.filterIsInstance<Submission.Refused>().groupingBy { it.code }.eachCount()
            log.warn(
                "Archive: batch of ${digests.size} digest(s) not stamped, ${proofs.size} answer(s) of ${config.aggregators.size} " +
                    "aggregator(s) $codes; the digests stay queued for the next batch.",
            )
            return StampOutcome(digests.size, proofs.size, false)
        }
        proofs.forEach(batch::addCalendarProof)
        val files = batch.files.map { it.digest to it.toByteArray() }
        val saved = io { store.saveStamped(root, files) }
        log.info("Archive: batch of $saved digest(s) stamped by ${proofs.size} of ${config.aggregators.size} aggregator(s).")
        val token = tsa?.let { timestampRoot(it, root) } is Rfc3161Result.Accepted
        return StampOutcome(digests.size, proofs.size, true, token)
    }

    /** `POST <aggregator>/digest`: an answer counts only if it leads to a calendar of the whitelist (or already to Bitcoin). */
    private suspend fun submit(http: ArchiveHttp, base: String, root: ByteArray): Submission {
        val answer = http.post("${base.trimEnd('/')}/digest", root, ContentType.Application.OctetStream, OTS_MEDIA_TYPE, MAX_CALENDAR_ANSWER)
        val body = (answer as? HttpAnswer.Ok)?.body ?: return Submission.Refused(code(answer))
        val proof = try {
            OtsTimestamp.read(body, root)
        } catch (e: Exception) {
            return Submission.Refused("unreadable")
        }
        val useful = proof.allAttestations().any { (message, attestation) ->
            message != null && (
                attestation is OtsAttestation.BitcoinBlockHeader ||
                    (attestation is OtsAttestation.Pending && config.calendars.allows(attestation.calendarUri))
                )
        }
        return if (useful) Submission.Accepted(proof) else Submission.Refused("no_known_calendar")
    }

    private suspend fun timestampRoot(tsa: Rfc3161Client, root: ByteArray): Rfc3161Result {
        val result = tsa.timestamp(root)
        when (result) {
            is Rfc3161Result.Accepted -> io { store.saveToken(root, result.token) }
            is Rfc3161Result.Refused -> log.warn("Archive: RFC 3161 answer refused (${result.code}); the batch is tried again at the next round.")
            is Rfc3161Result.Unavailable -> log.warn("Archive: timestamping authority unavailable (${result.code}); the batch is tried again at the next round.")
        }
        return result
    }

    // ---------------------------------------------------------------- upgrade

    private class Counts {
        var proofs = 0
        var calendarRequests = 0
        var upgraded = 0
        var anchored = 0
        var refusedCalendars = 0
        var checked = 0
        var verified = 0
        val problems = HashMap<String, Int>()
        val notVerified = HashMap<String, Int>()

        fun outcome() = UpgradeOutcome(
            proofs, calendarRequests, upgraded, anchored, refusedCalendars, checked, verified, notVerified.toMap(), problems.toMap(),
        )
    }

    private fun HashMap<String, Int>.bump(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private sealed interface CalendarAnswer {
        class Upgrade(val proof: OtsTimestamp) : CalendarAnswer

        /** 404: the calendar has not reached Bitcoin yet. */
        data object NotYet : CalendarAnswer

        data class Failed(val code: String) : CalendarAnswer
    }

    private suspend fun upgrade(http: ArchiveHttp, counts: Counts) {
        val proofs = io { store.withState(ProofState.PENDING, MAX_PROOFS_PER_ROUND) }
        counts.proofs += proofs.size
        // The proofs of one batch share their calendar nodes: one request per calendar and message.
        val answers = HashMap<String, CalendarAnswer>()
        val down = HashSet<String>()
        for (proof in proofs) {
            val before = proof.ots ?: continue
            val file = try {
                OtsFile.read(before)
            } catch (e: Exception) {
                counts.problems.bump("unreadable_proof")
                continue
            }
            for ((node, pending) in pendingNodes(file.timestamp)) {
                val message = node.message ?: continue
                val uri = pending.calendarUri
                if (!config.calendars.allows(uri)) {
                    counts.refusedCalendars++
                    continue
                }
                // A calendar that failed in this round is not asked again before the next one.
                if (uri in down) continue
                val answer = answers.getOrPut("$uri ${message.toHexString()}") {
                    counts.calendarRequests++
                    askCalendar(http, uri, message)
                }
                when (answer) {
                    is CalendarAnswer.Upgrade -> node.merge(answer.proof)
                    CalendarAnswer.NotYet -> Unit
                    is CalendarAnswer.Failed -> {
                        down += uri
                        counts.problems.bump("calendar_${answer.code}")
                    }
                }
            }
            val after = file.toByteArray()
            if (after.contentEquals(before)) continue
            if (after.size > MAX_OTS_BYTES) {
                counts.problems.bump("proof_too_large")
                continue
            }
            val complete = file.timestamp.isComplete
            if (io { store.updateOts(proof.digest, ProofState.PENDING, after, if (complete) ProofState.ANCHORED else ProofState.PENDING) }) {
                counts.upgraded++
                if (complete) counts.anchored++
            }
        }
    }

    /** `GET <calendar>/timestamp/<hex of the message at that node>`. */
    private suspend fun askCalendar(http: ArchiveHttp, uri: String, message: ByteArray): CalendarAnswer =
        when (val answer = http.get("$uri/timestamp/${message.toHexString()}", OTS_MEDIA_TYPE, MAX_CALENDAR_ANSWER)) {
            is HttpAnswer.Ok -> try {
                CalendarAnswer.Upgrade(OtsTimestamp.read(answer.body, message))
            } catch (e: Exception) {
                CalendarAnswer.Failed("unreadable")
            }
            is HttpAnswer.Status -> if (answer.code == 404) CalendarAnswer.NotYet else CalendarAnswer.Failed("http_${answer.code}")
            else -> CalendarAnswer.Failed(code(answer))
        }

    // ---------------------------------------------------------------- Bitcoin check

    private sealed interface Block {
        class Found(val hash: String, val first: BitcoinHeader, val second: BitcoinHeader) : Block

        data object Disagree : Block

        /** [down]: an explorer did not answer; no other block is looked up in this round. */
        data class Unavailable(val code: String, val down: Boolean) : Block
    }

    private suspend fun check(http: ArchiveHttp, counts: Counts) {
        val proofs = io { store.withState(ProofState.ANCHORED, MAX_PROOFS_PER_ROUND) }
        val blocks = HashMap<Long, Block>()
        for (proof in proofs) {
            val ots = proof.ots ?: continue
            val file = try {
                OtsFile.read(ots)
            } catch (e: Exception) {
                counts.problems.bump("unreadable_proof")
                continue
            }
            val attestations = OtsBitcoinVerifier.bitcoinAttestations(file.timestamp)
                .distinctBy { (height, message) -> "$height ${message.toHexString()}" }
                .sortedBy { it.first }
            counts.checked++
            var anchor: BitcoinAnchor? = null
            for ((height, message) in attestations) {
                val block = blocks.getOrPut(height) { lookUpBlock(http, height) }
                val result = when (block) {
                    is Block.Found -> OtsBitcoinVerifier.check(message, block.hash, block.first, block.second).name
                    Block.Disagree -> BitcoinCheck.SOURCES_DISAGREE.name
                    is Block.Unavailable -> "unavailable_${block.code}"
                }
                if (block is Block.Found && result == BitcoinCheck.VERIFIED.name) {
                    // The lowest height that checks out: the earliest moment the proof exists in Bitcoin.
                    anchor = BitcoinAnchor(height, block.hash, block.first.time)
                    break
                }
                counts.notVerified.bump(result)
                if (block is Block.Unavailable && block.down) {
                    counts.problems.bump("explorers_down")
                    return
                }
            }
            if (anchor != null && io { store.markVerified(proof.digest, anchor) }) counts.verified++
        }
    }

    /** The block at [height]: its hash from both explorers, which must agree, then its header from both. */
    private suspend fun lookUpBlock(http: ArchiveHttp, height: Long): Block {
        val (first, second) = config.explorers.map { it.trimEnd('/') }
        val hashes = listOf(first, second).map { base ->
            when (val answer = http.get("$base/block-height/$height", TEXT, MAX_EXPLORER_ANSWER)) {
                is HttpAnswer.Ok -> answer.body.decodeToString().trim().lowercase().takeIf { BLOCK_HASH.matches(it) }
                    ?: return Block.Unavailable("bad_answer", down = false)
                else -> return Block.Unavailable(code(answer), down = isDown(answer))
            }
        }
        if (hashes[0] != hashes[1]) return Block.Disagree
        val hash = hashes[0]
        val headers = listOf(first, second).map { base ->
            when (val answer = http.get("$base/block/$hash/header", TEXT, MAX_EXPLORER_ANSWER)) {
                is HttpAnswer.Ok -> answer.body.decodeToString().trim().lowercase().takeIf { HEADER_HEX.matches(it) }
                    ?.let { BitcoinHeader(it.hexToByteArray()) }
                    ?: return Block.Unavailable("bad_answer", down = false)
                else -> return Block.Unavailable(code(answer), down = isDown(answer))
            }
        }
        return Block.Found(hash, headers[0], headers[1])
    }

    // ---------------------------------------------------------------- schedule

    /**
     * The two periodic tasks, until [scope] (the application) is cancelled: the batches every
     * [ArchiveConfig.batchInterval], the upgrades, checks and missing RFC 3161 tokens every
     * [ArchiveConfig.upgradeInterval]. The first round waits a little, so that a server restarting
     * in a loop never floods anyone. Nothing runs when the archive is off.
     */
    fun start(scope: CoroutineScope): List<Job> {
        if (http == null) return emptyList()
        return listOf(
            scope.launch(Dispatchers.IO) { every(config.batchInterval, "stamping") { stampRound() } },
            scope.launch(Dispatchers.IO) {
                every(config.upgradeInterval, "upgrade") {
                    upgradeRound()
                    timestampRound()
                }
            },
        )
    }

    private suspend fun every(interval: Duration, name: String, round: suspend () -> Unit) {
        delay(minOf(interval, FIRST_ROUND_DELAY))
        while (currentCoroutineContext().isActive) {
            try {
                round()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Archive: $name round failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }

    private fun logRound(outcome: UpgradeOutcome) {
        if (outcome.proofs == 0 && outcome.checked == 0) return
        log.info(
            "Archive: ${outcome.proofs} pending proof(s), ${outcome.calendarRequests} calendar request(s), ${outcome.upgraded} upgraded, " +
                "${outcome.anchored} anchored in Bitcoin, ${outcome.refusedCalendars} calendar address(es) refused; " +
                "${outcome.checked} checked, ${outcome.verified} verified; problems ${outcome.problems}.",
        )
        val alarming = outcome.notVerified.filterKeys { it in ALARMS }
        if (alarming.isNotEmpty()) {
            log.warn("Archive: Bitcoin checks that need the team: $alarming; these proofs are not marked verified.")
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val DIGEST_SIZE: Int = 32

        /** The media type of OpenTimestamps answers, asked by the official client. */
        const val OTS_MEDIA_TYPE: String = "application/vnd.opentimestamps.v1"
        private const val TEXT = "text/plain"

        /** Answers of aggregators and calendars, as the official client limits them. */
        const val MAX_CALENDAR_ANSWER: Int = 10_000

        /** A block hash or a header in hexadecimal: a few hundred bytes. */
        const val MAX_EXPLORER_ANSWER: Int = 1_000

        /** The largest proof stored (also a CHECK of V13__archives.sql). */
        const val MAX_OTS_BYTES: Int = 262_144

        /** Digests per batch; the others wait for the next one. */
        const val MAX_BATCH: Int = 10_000

        /** Proofs read per round and per state; their calendar requests are shared within a batch. */
        const val MAX_PROOFS_PER_ROUND: Int = 5_000

        /** RFC 3161 tokens asked again per round, for batches of the last [TSA_RETRY_DAYS] days. */
        const val MAX_TSA_PER_ROUND: Int = 5
        const val TSA_RETRY_DAYS: Long = 7

        val FIRST_ROUND_DELAY: Duration = 2.minutes

        private val BLOCK_HASH = Regex("^[0-9a-f]{64}$")
        private val HEADER_HEX = Regex("^[0-9a-f]{160}$")

        /** Results that mean a false proof or a lying explorer, not a passing trouble. */
        private val ALARMS = setOf(
            BitcoinCheck.SOURCES_DISAGREE.name,
            BitcoinCheck.WRONG_BLOCK.name,
            BitcoinCheck.NOT_MINED.name,
            BitcoinCheck.WRONG_MERKLE_ROOT.name,
        )

        internal val log: Logger = LoggerFactory.getLogger(ArchiveService::class.java)

        private fun code(answer: HttpAnswer): String = when (answer) {
            is HttpAnswer.Ok -> "ok"
            is HttpAnswer.Status -> "http_${answer.code}"
            is HttpAnswer.Failed -> answer.reason
            HttpAnswer.TooLarge -> "too_large"
        }

        private fun isDown(answer: HttpAnswer): Boolean = ArchiveHttp.mayPass(answer)

        /** Every node of [root] that carries a pending attestation, with that attestation. */
        internal fun pendingNodes(root: OtsTimestamp): List<Pair<OtsTimestamp, OtsAttestation.Pending>> {
            val found = ArrayList<Pair<OtsTimestamp, OtsAttestation.Pending>>()
            val stack = ArrayDeque<OtsTimestamp>().apply { add(root) }
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                node.attestations.filterIsInstance<OtsAttestation.Pending>().forEach { found += node to it }
                node.operations.forEach { stack += it.second }
            }
            return found
        }

        /** Builds the service for the application: the periodic tasks and the HTTP client live and die with it. */
        fun start(application: Application, dataSource: DataSource, setup: ArchiveSetup): ArchiveService {
            val config = setup.config
            val client = if (config.enabled) setup.client(config) else null
            val service = ArchiveService(
                ArchiveStore(dataSource),
                config,
                client?.let { ArchiveHttp(it, config.requestTimeout, setup.pause) },
                setup.clock,
                setup.random,
                setup.pause,
            )
            if (client == null) {
                log.info("Archive: timestamping off (${ArchiveConfig.ENABLED_VARIABLE}); digests are queued and stored proofs served, nobody is contacted.")
                return service
            }
            service.start(application)
            application.monitor.subscribe(ApplicationStopped) { client.close() }
            log.info(
                "Archive: timestamping on, ${config.aggregators.size} aggregator(s), RFC 3161 ${if (config.tsaUrl != null) "on" else "off"}, " +
                    "proxy ${if (config.proxy != null) "set" else "none"}.",
            )
            return service
        }
    }
}
