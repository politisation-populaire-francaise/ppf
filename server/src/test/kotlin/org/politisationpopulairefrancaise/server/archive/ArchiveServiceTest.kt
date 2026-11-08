package org.politisationpopulairefrancaise.server.archive

import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.bouncycastle.tsp.TimeStampRequest
import org.politisationpopulairefrancaise.core.archive.OtsAttestation
import org.politisationpopulairefrancaise.core.archive.OtsBitcoinVerifier
import org.politisationpopulairefrancaise.core.archive.OtsFile
import org.politisationpopulairefrancaise.core.archive.OtsOperation
import org.politisationpopulairefrancaise.core.archive.OtsTimestamp
import org.politisationpopulairefrancaise.server.TestDatabase
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The archive against fake services (MockEngine) and the embedded PostgreSQL: batches, the rule of
 * two aggregators, the whitelist, upgrades, Bitcoin checks with two explorers, RFC 3161 tokens.
 */
class ArchiveServiceTest {
    private val fakes = FakeServices()
    private val clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)
    private val pauses = CopyOnWriteArrayList<Duration>()
    private val store = ArchiveStore(TestDatabase.dataSource)

    private fun service(config: ArchiveConfig = ArchiveConfig(enabled = true)): ArchiveService = ArchiveService(
        store,
        config,
        ArchiveHttp(fakes.client(), config.requestTimeout, pause = { pauses += it }),
        clock,
        SecureRandom(),
        pause = { pauses += it },
    )

    @BeforeTest
    fun emptyTable() = ArchiveFixtures.reset()

    /** Every node message of a proof. */
    private fun messages(timestamp: OtsTimestamp): List<ByteArray> {
        val found = ArrayList<ByteArray>()
        val stack = ArrayDeque(listOf(timestamp))
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.message?.let { found += it }
            node.operations.forEach { stack += it.second }
        }
        return found
    }

    @Test
    fun onlyTheRootOfABatchLeavesTheServer() = runBlocking {
        val archive = service()
        val digests = (1..3).map(::exampleDigest)
        digests.forEach { assertTrue(archive.stamp(it)) }
        assertFalse(archive.stamp(digests[0]), "a digest is queued once")

        val outcome = archive.stampRound()
        assertEquals(ArchiveService.StampOutcome(digests = 3, answers = 4, stamped = true, rfc3161 = true), outcome)

        // One POST per aggregator, all with the same 32 bytes: the root, which is none of the digests.
        val posts = fakes.posts("/digest")
        assertEquals(4, posts.size)
        assertEquals(FakeServices.CALENDAR_OF.keys, posts.map { it.url.removePrefix("https://").substringBefore('/') }.toSet())
        val root = posts.first().body
        assertEquals(32, root.size)
        posts.forEach { assertContentEquals(root, it.body) }
        digests.forEach { digest -> assertFalse(root.contentEquals(digest)) }
        // The timestamping authority got the same root, and only it.
        val tsaRequests = fakes.posts("/tsr")
        assertEquals(1, tsaRequests.size)
        assertContentEquals(root, TimeStampRequest(tsaRequests.single().body).messageImprintDigest)
        // No digest anywhere in what left: neither in an address nor in a body.
        for (request in fakes.requests) {
            for (digest in digests) {
                assertFalse(request.url.contains(digest.toHexString()))
                assertFalse(request.body.containsBytes(digest))
            }
        }

        for (digest in digests) {
            val proof = assertNotNull(archive.find(digest))
            assertEquals(ProofState.PENDING, proof.state)
            assertEquals(LocalDate.of(2026, 11, 7), proof.requestedOn)
            assertContentEquals(root, proof.batchRoot)
            val file = OtsFile.read(assertNotNull(proof.ots))
            assertContentEquals(digest, file.digest)
            // The path of each digest reaches the root, then the four calendars.
            assertTrue(messages(file.timestamp).any { it.contentEquals(root) })
            val calendars = file.timestamp.allAttestations().map { it.second }.filterIsInstance<OtsAttestation.Pending>().map { it.calendarUri }
            assertEquals(FakeServices.CALENDAR_OF.values.toSet(), calendars.toSet())
            val token = assertNotNull(proof.rfc3161)
            assertContentEquals(sha256(fakes.tsa.certificate.encoded), token.tsaCertSha256)
        }

        // Nothing queued: no request at all.
        val before = fakes.requests.size
        assertEquals(ArchiveService.StampOutcome(0, 0, false), archive.stampRound())
        assertEquals(before, fakes.requests.size)
    }

    @Test
    fun aBatchNeedsTwoAggregatorsElseItsDigestsWaitForTheNextOne() = runBlocking {
        val archive = service()
        fakes.aggregatorModes["b.pool.opentimestamps.org"] = FakeServices.AggregatorMode.FAIL
        fakes.aggregatorModes["a.pool.eternitywall.com"] = FakeServices.AggregatorMode.UNKNOWN_CALENDAR
        fakes.aggregatorModes["ots.btc.catallaxy.com"] = FakeServices.AggregatorMode.GARBAGE
        val digest = exampleDigest(1)
        archive.stamp(digest)

        val (first, logs) = ArchiveFixtures.captureLogs { archive.stampRound() }
        assertEquals(ArchiveService.StampOutcome(digests = 1, answers = 1, stamped = false), first)
        val queued = assertNotNull(archive.find(digest))
        assertEquals(ProofState.QUEUED, queued.state)
        assertNull(queued.ots)
        assertTrue(fakes.posts("/tsr").isEmpty(), "no token for a batch that was not stamped")
        // The failing aggregator was tried three times, 2 then 4 seconds apart; the others once.
        assertEquals(3, fakes.requests.count { it.url.startsWith("https://b.pool.opentimestamps.org/") })
        assertEquals(1, fakes.requests.count { it.url.startsWith("https://a.pool.opentimestamps.org/") })
        assertEquals(listOf(2.seconds, 4.seconds), pauses.toList())
        val firstRoot = fakes.posts("/digest").first().body
        assertFalse(logs.contains(digest.toHexString()))
        assertFalse(logs.contains(firstRoot.toHexString()))

        // Next interval: two aggregators answer, the batch leaves again with a new root.
        fakes.aggregatorModes["b.pool.opentimestamps.org"] = FakeServices.AggregatorMode.STAMP
        fakes.requests.clear()
        val second = archive.stampRound()
        assertTrue(second.stamped)
        assertEquals(2, second.answers)
        val secondRoot = fakes.posts("/digest").first().body
        assertFalse(firstRoot.contentEquals(secondRoot), "new nonces, new root")
        val proof = assertNotNull(archive.find(digest))
        assertEquals(ProofState.PENDING, proof.state)
        // Only the answers that lead to a calendar of the whitelist were kept.
        val calendars = OtsFile.read(proof.ots!!).timestamp.allAttestations().map { it.second }
            .filterIsInstance<OtsAttestation.Pending>().map { it.calendarUri }.toSet()
        assertEquals(setOf("https://alice.btc.calendar.opentimestamps.org", "https://bob.btc.calendar.opentimestamps.org"), calendars)
    }

    @Test
    fun theWhitelistRefusesUnknownHostsAndPlainHttp() {
        val whitelist = ArchiveConfig().calendars
        FakeServices.CALENDAR_OF.values.forEach { assertTrue(whitelist.allows(it), it) }
        val refused = listOf(
            "http://alice.btc.calendar.opentimestamps.org",
            "https://calendar.evil.example",
            "https://calendar.opentimestamps.org",
            "https://evilcalendar.opentimestamps.org",
            "https://alice.btc.calendar.opentimestamps.org.evil.example",
            "https://alice.btc.calendar.opentimestamps.org:8443",
            "https://alice.btc.calendar.opentimestamps.org/",
            "https://alice.btc.calendar.opentimestamps.org/elsewhere",
            "https://alice.btc.calendar.opentimestamps.org?x=1",
            "https://user@alice.btc.calendar.opentimestamps.org",
            "HTTPS://alice.btc.calendar.opentimestamps.org",
            "https://a..calendar.opentimestamps.org",
            "alice.btc.calendar.opentimestamps.org",
            "",
        )
        refused.forEach { assertFalse(whitelist.allows(it), it) }
        val exact = CalendarWhitelist(listOf("calendar.example.org"))
        assertTrue(exact.allows("https://calendar.example.org"))
        assertFalse(exact.allows("https://a.calendar.example.org"))
    }

    @Test
    fun aPendingAttestationOutsideTheWhitelistIsNeverFollowed() = runBlocking {
        val digest = exampleDigest(7)
        val file = OtsFile.forSha256(digest)
        val node = file.timestamp.addOperation(OtsOperation.Append(ByteArray(16) { 7 })).addOperation(OtsOperation.Sha256)
        node.addAttestation(OtsAttestation.Pending("https://calendar.evil.example"))
        node.addAttestation(OtsAttestation.Pending("http://alice.btc.calendar.opentimestamps.org"))
        store.queue(digest, LocalDate.of(2026, 11, 7))
        store.saveStamped(node.message!!, listOf(digest to file.toByteArray()))

        val outcome = service().upgradeRound()
        assertEquals(2, outcome.refusedCalendars)
        assertEquals(0, outcome.calendarRequests)
        assertTrue(fakes.requests.isEmpty(), "nobody was contacted")
        assertEquals(ProofState.PENDING, proofOf(digest).state)
    }

    private fun proofOf(digest: ByteArray): StoredProof = assertNotNull(store.find(digest))

    @Test
    fun anUpgradeMergesTheCalendarAnswerThenTheBlockIsCheckedAgainstTwoExplorers() = runBlocking {
        val archive = service()
        fakes.pendingAnswers["alice.btc.calendar.opentimestamps.org"] = 1
        val digests = listOf(exampleDigest(1), exampleDigest(2))
        digests.forEach { archive.stamp(it) }
        assertTrue(archive.stampRound().stamped)
        val root = fakes.posts("/digest").first().body
        fakes.requests.clear()

        val (first, firstLogs) = ArchiveFixtures.captureLogs { archive.upgradeRound() }
        // The two proofs share their calendar nodes: one request per calendar, all answering 404.
        assertEquals(4, first.calendarRequests)
        assertEquals(0, first.upgraded)
        assertEquals(FakeServices.CALENDAR_HOSTS.toSet(), fakes.requests.map { it.url.removePrefix("https://").substringBefore('/') }.toSet())
        fakes.requests.forEach { assertTrue(Regex("^https://[a-z.]+/timestamp/[0-9a-f]{64}$").matches(it.url), it.url) }
        digests.forEach { assertEquals(ProofState.PENDING, proofOf(it).state) }
        fakes.requests.clear()

        val (second, secondLogs) = ArchiveFixtures.captureLogs { archive.upgradeRound() }
        assertEquals(2, second.upgraded)
        assertEquals(2, second.anchored)
        assertEquals(2, second.verified)
        // One block for both proofs: its hash from each explorer, then its header from each.
        val explorerRequests = fakes.requests.filter { it.url.contains("/api/") }
        assertEquals(4, explorerRequests.size)
        val height = fakes.chain.keys.single()
        val header = fakes.chain.getValue(height)
        assertEquals(
            setOf("/api/block-height/$height", "/api/block/${header.hashHex}/header"),
            explorerRequests.map { it.url.substringAfter(".info").substringAfter(".space") }.toSet(),
        )
        for (digest in digests) {
            val proof = proofOf(digest)
            assertEquals(ProofState.VERIFIED, proof.state)
            val anchor = assertNotNull(proof.bitcoin)
            assertEquals(height, anchor.height)
            assertEquals(header.hashHex, anchor.blockHash)
            assertEquals(header.time, anchor.time)
            val file = OtsFile.read(proof.ots!!)
            assertTrue(file.timestamp.isComplete)
            assertContentEquals(digest, file.digest)
            val (attestedHeight, message) = OtsBitcoinVerifier.bitcoinAttestations(file.timestamp).single()
            assertEquals(height, attestedHeight)
            assertContentEquals(header.merkleRoot, message)
        }

        // Nothing left to do: no request at all.
        fakes.requests.clear()
        assertEquals(ArchiveService.UpgradeOutcome(), archive.upgradeRound())
        assertTrue(fakes.requests.isEmpty())

        // The logs never name a digest, the root, a message sent to a calendar nor a proof.
        val logs = firstLogs + "\n" + secondLogs
        assertTrue(logs.contains("verified"), logs)
        val secret = digests.map { it.toHexString() } + root.toHexString() + digests.map { Base64.getEncoder().encodeToString(it) }
        secret.forEach { assertFalse(logs.contains(it)) }
        assertFalse(logs.contains("/timestamp/"))
        assertFalse(logs.contains("https://"))
    }

    /** A proof of the genesis Merkle root, anchored at height 0 (the core tests use it too). */
    private fun anchoredGenesisProof(): ByteArray {
        val digest = ArchiveFixtures.GENESIS.merkleRoot
        val file = OtsFile.forSha256(digest)
        file.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(0))
        store.queue(digest, LocalDate.of(2026, 11, 7))
        store.saveStamped(digest, listOf(digest to file.toByteArray()))
        assertTrue(store.updateOts(digest, ProofState.PENDING, file.toByteArray(), ProofState.ANCHORED))
        fakes.chain[0] = ArchiveFixtures.GENESIS
        return digest
    }

    @Test
    fun twoExplorersThatDisagreeNeverGiveVerified() = runBlocking {
        val digest = anchoredGenesisProof()
        val archive = service()

        fakes.secondExplorerForgesHeader = true
        val forgedHeader = archive.upgradeRound()
        assertEquals(0, forgedHeader.verified)
        assertEquals(mapOf("SOURCES_DISAGREE" to 1), forgedHeader.notVerified)
        assertEquals(ProofState.ANCHORED, proofOf(digest).state)
        assertNull(proofOf(digest).bitcoin)

        fakes.secondExplorerForgesHeader = false
        fakes.secondExplorerForgesHash = true
        val forgedHash = archive.upgradeRound()
        assertEquals(mapOf("SOURCES_DISAGREE" to 1), forgedHash.notVerified)
        assertEquals(ProofState.ANCHORED, proofOf(digest).state)

        fakes.secondExplorerForgesHash = false
        fakes.firstExplorerDown = true
        val down = archive.upgradeRound()
        assertEquals(0, down.verified)
        assertEquals(1, down.problems["explorers_down"])
        assertEquals(ProofState.ANCHORED, proofOf(digest).state)

        // Both explorers agree on the genesis block: the proof is verified, with the block's own time.
        fakes.firstExplorerDown = false
        assertEquals(1, archive.upgradeRound().verified)
        val anchor = assertNotNull(proofOf(digest).bitcoin)
        assertEquals(0L, anchor.height)
        assertEquals(ArchiveFixtures.GENESIS_HASH, anchor.blockHash)
        assertEquals(1_231_006_505L, anchor.time)
        assertEquals(ProofState.VERIFIED, proofOf(digest).state)
    }

    @Test
    fun aFalseProofStaysAnchoredButNotVerified() = runBlocking {
        val digest = exampleDigest(9)
        val file = OtsFile.forSha256(digest)
        file.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(0))
        store.queue(digest, LocalDate.of(2026, 11, 7))
        store.saveStamped(digest, listOf(digest to file.toByteArray()))
        store.updateOts(digest, ProofState.PENDING, file.toByteArray(), ProofState.ANCHORED)
        fakes.chain[0] = ArchiveFixtures.GENESIS

        val outcome = service().upgradeRound()
        assertEquals(mapOf("WRONG_MERKLE_ROOT" to 1), outcome.notVerified)
        assertEquals(ProofState.ANCHORED, proofOf(digest).state)
    }

    @Test
    fun aMissingRfc3161TokenIsAskedAgainAtMostOncePerSecond() = runBlocking {
        val archive = service()
        fakes.tsaMode = FakeTsa.Mode.UNAVAILABLE
        archive.stamp(exampleDigest(1))
        val first = archive.stampRound()
        assertTrue(first.stamped, "the batch is stamped even without its RFC 3161 token")
        assertFalse(first.rfc3161)
        assertNull(proofOf(exampleDigest(1)).rfc3161)
        assertEquals(3, fakes.posts("/tsr").size, "three attempts at most")

        fakes.tsaMode = FakeTsa.Mode.TAMPERED
        archive.stamp(exampleDigest(2))
        assertFalse(archive.stampRound().rfc3161, "a token whose signature fails is refused")
        assertNull(proofOf(exampleDigest(2)).rfc3161)

        fakes.tsaMode = FakeTsa.Mode.OK
        pauses.clear()
        assertEquals(2, archive.timestampRound())
        listOf(1, 2).forEach { assertNotNull(proofOf(exampleDigest(it)).rfc3161) }
        // The second request waited for the second that FreeTSA asks between two requests.
        assertTrue(pauses.any { it > 0.5.seconds && it <= 1.seconds }, pauses.toString())
        assertEquals(0, archive.timestampRound())
    }

    @Test
    fun anArchiveSwitchedOffContactsNobodyButStillQueues() = runBlocking {
        val off = ArchiveService(store, ArchiveConfig(enabled = false), null, clock, SecureRandom(), pause = { pauses += it })
        assertFalse(off.enabled)
        assertTrue(off.stamp(exampleDigest(1)))
        assertEquals(ArchiveService.StampOutcome(0, 0, false), off.stampRound())
        assertEquals(ArchiveService.UpgradeOutcome(), off.upgradeRound())
        assertEquals(0, off.timestampRound())
        assertTrue(off.start(this).isEmpty())
        assertEquals(ProofState.QUEUED, proofOf(exampleDigest(1)).state)
        assertTrue(fakes.requests.isEmpty())
        assertTrue(fakes.requests.none { it.method == HttpMethod.Post })
    }
}
