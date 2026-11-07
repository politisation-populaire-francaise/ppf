package org.politisationpopulairefrancaise.server.archive

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.cmp.PKIFailureInfo
import org.bouncycastle.asn1.cmp.PKIStatus
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import org.politisationpopulairefrancaise.core.archive.BitcoinHeader
import org.politisationpopulairefrancaise.core.archive.OtsAttestation
import org.politisationpopulairefrancaise.core.archive.OtsOperation
import org.politisationpopulairefrancaise.core.archive.OtsTimestamp
import org.politisationpopulairefrancaise.server.TestDatabase
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** A digest of a fictitious content: the tests never stamp anything real. */
fun exampleDigest(n: Int): ByteArray = sha256("[EXEMPLE] contenu publié n° $n".encodeToByteArray())

/** True when [needle] appears somewhere in [haystack]. */
fun ByteArray.containsBytes(needle: ByteArray): Boolean =
    (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }

object ArchiveFixtures {
    /** The genesis block of 3 January 2009: public and fixed forever. */
    val GENESIS: BitcoinHeader = BitcoinHeader(
        (
            "01000000" + "0".repeat(64) +
                "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a" +
                "29ab5f49" + "ffff001d" + "1dac2b7c"
            ).hexToByteArray(),
    )
    const val GENESIS_HASH: String = "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"

    /** Empties the table of V10__archives.sql (TestDatabase.reset does not know it). */
    fun reset() {
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("TRUNCATE archive_proof") }
        }
    }

    /**
     * A header with Merkle root [merkleRoot] that meets its own target, mined at the difficulty of
     * Bitcoin's test networks (`nBits` 0x207fffff: about one try in two succeeds).
     */
    fun mine(merkleRoot: ByteArray, time: Long): BitcoinHeader {
        for (nonce in 0L until 10_000L) {
            val raw = ByteArray(BitcoinHeader.SIZE)
            raw[0] = 1
            merkleRoot.copyInto(raw, 36)
            putLittleEndian(raw, 68, time)
            putLittleEndian(raw, 72, 0x207fffffL)
            putLittleEndian(raw, 76, nonce)
            val header = BitcoinHeader(raw)
            if (header.meetsItsTarget()) return header
        }
        error("no nonce found")
    }

    private fun putLittleEndian(bytes: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 4) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    /** Runs [block] and returns every message logged meanwhile, by any logger. */
    inline fun <T> captureLogs(block: () -> T): Pair<T, String> {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(appender)
        try {
            val result = block()
            val text = appender.list.joinToString("\n") { event ->
                event.formattedMessage + (event.throwableProxy?.let { " " + it.className + " " + it.message } ?: "")
            }
            return result to text
        } finally {
            root.detachAppender(appender)
        }
    }
}

/**
 * The services of the archive, faked in memory with Ktor's MockEngine: the four aggregators, their
 * four calendars, the two Esplora explorers and FreeTSA. Every request is recorded; no host is
 * ever contacted.
 */
class FakeServices {
    class Request(val method: HttpMethod, val url: String, val body: ByteArray)

    enum class AggregatorMode { STAMP, FAIL, UNKNOWN_CALENDAR, GARBAGE }

    val requests = CopyOnWriteArrayList<Request>()
    val tsa = FakeTsa()

    @Volatile
    var tsaMode: FakeTsa.Mode = FakeTsa.Mode.OK

    /** How each aggregator answers (STAMP by default). */
    val aggregatorModes = ConcurrentHashMap<String, AggregatorMode>()

    /** How many times each calendar answers 404 to a message before giving its Bitcoin attestation. */
    val pendingAnswers = ConcurrentHashMap<String, Int>()
    private val calendarCalls = ConcurrentHashMap<String, Int>()

    /** The fake chain: height to header, served by both explorers. */
    val chain = ConcurrentHashMap<Long, BitcoinHeader>()
    private val heights = ConcurrentHashMap<String, Long>()
    private val nextHeight = AtomicLong(920_000)

    /** The second explorer gives another header than the first. */
    @Volatile
    var secondExplorerForgesHeader = false

    /** The second explorer gives another block hash at every height. */
    @Volatile
    var secondExplorerForgesHash = false

    /** The first explorer does not answer at all. */
    @Volatile
    var firstExplorerDown = false

    fun client(): HttpClient = HttpClient(MockEngine { request -> handle(request) }) {
        // As in ArchiveHttp.defaultClient: no redirection followed.
        expectSuccess = false
        followRedirects = false
    }

    fun posts(path: String): List<Request> = requests.filter { it.method == HttpMethod.Post && it.url.endsWith(path) }

    fun gets(host: String): List<Request> = requests.filter { it.method == HttpMethod.Get && it.url.startsWith("https://$host/") }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
        requests += Request(request.method, request.url.toString(), body)
        val host = request.url.host
        val path = request.url.encodedPath
        return when {
            request.method == HttpMethod.Post && path == "/digest" && host in CALENDAR_OF -> aggregator(host, body)
            request.method == HttpMethod.Get && path.startsWith("/timestamp/") -> calendar(host, path.removePrefix("/timestamp/"))
            host == "mempool.space" || host == "blockstream.info" -> explorer(first = host == "mempool.space", path = path.removePrefix("/api"))
            request.method == HttpMethod.Post && host == "freetsa.org" && path == "/tsr" ->
                if (tsaMode == FakeTsa.Mode.UNAVAILABLE) respond(ByteArray(0), HttpStatusCode.ServiceUnavailable) else respond(tsa.answer(body, tsaMode))
            else -> respond(ByteArray(0), HttpStatusCode.NotFound)
        }
    }

    private fun MockRequestHandleScope.aggregator(host: String, root: ByteArray): HttpResponseData {
        val salt = sha256(host.encodeToByteArray()).copyOf(16)
        val proof = OtsTimestamp(root)
        val node = proof.addOperation(OtsOperation.Append(salt)).addOperation(OtsOperation.Sha256)
        return when (aggregatorModes[host] ?: AggregatorMode.STAMP) {
            AggregatorMode.STAMP -> {
                node.addAttestation(OtsAttestation.Pending(CALENDAR_OF.getValue(host)))
                respond(proof.toByteArray())
            }
            AggregatorMode.UNKNOWN_CALENDAR -> {
                node.addAttestation(OtsAttestation.Pending("https://calendar.evil.example"))
                respond(proof.toByteArray())
            }
            AggregatorMode.GARBAGE -> respond("not a proof".encodeToByteArray())
            AggregatorMode.FAIL -> respond(ByteArray(0), HttpStatusCode.InternalServerError)
        }
    }

    private fun MockRequestHandleScope.calendar(host: String, hex: String): HttpResponseData {
        val key = "$host $hex"
        val calls = calendarCalls.merge(key, 1) { a, b -> a + b }!!
        if (calls <= (pendingAnswers[host] ?: Int.MAX_VALUE)) {
            return respond("Pending confirmation in Bitcoin blockchain".encodeToByteArray(), HttpStatusCode.NotFound)
        }
        val message = hex.hexToByteArray()
        val height = heights.computeIfAbsent(hex) { nextHeight.getAndIncrement() }
        // The block whose Merkle root is SHA-256(message), as a calendar's transaction would put it.
        chain.computeIfAbsent(height) { ArchiveFixtures.mine(sha256(message), BLOCK_TIME + height) }
        val proof = OtsTimestamp(message)
        proof.addOperation(OtsOperation.Sha256).addAttestation(OtsAttestation.BitcoinBlockHeader(height))
        return respond(proof.toByteArray())
    }

    private fun MockRequestHandleScope.explorer(first: Boolean, path: String): HttpResponseData {
        if (first && firstExplorerDown) return respond(ByteArray(0), HttpStatusCode.BadGateway)
        val heightPath = Regex("^/block-height/(\\d+)$").find(path)
        if (heightPath != null) {
            val header = chain[heightPath.groupValues[1].toLong()] ?: return respond(ByteArray(0), HttpStatusCode.NotFound)
            val hash = if (!first && secondExplorerForgesHash) header.hashHex.reversed() else header.hashHex
            return respond(hash.encodeToByteArray())
        }
        val headerPath = Regex("^/block/([0-9a-f]{64})/header$").find(path) ?: return respond(ByteArray(0), HttpStatusCode.NotFound)
        val header = chain.values.firstOrNull { it.hashHex == headerPath.groupValues[1] } ?: return respond(ByteArray(0), HttpStatusCode.NotFound)
        val bytes = header.toByteArray()
        if (!first && secondExplorerForgesHeader) bytes[79] = (bytes[79].toInt() xor 1).toByte()
        return respond(bytes.toHexString().encodeToByteArray())
    }

    companion object {
        /** Each default aggregator hands its batch to one calendar of the official whitelist. */
        val CALENDAR_OF: Map<String, String> = mapOf(
            "a.pool.opentimestamps.org" to "https://alice.btc.calendar.opentimestamps.org",
            "b.pool.opentimestamps.org" to "https://bob.btc.calendar.opentimestamps.org",
            "a.pool.eternitywall.com" to "https://finney.calendar.eternitywall.com",
            "ots.btc.catallaxy.com" to "https://btc.calendar.catallaxy.com",
        )
        val CALENDAR_HOSTS: List<String> = CALENDAR_OF.values.map { it.removePrefix("https://") }
        const val BLOCK_TIME: Long = 1_793_000_000L
    }
}

/**
 * A timestamping authority built with BouncyCastle in the test itself: a P-384 key, a self-signed
 * certificate with the critical extended key usage `timeStamping`, and the answers a real authority
 * gives, plus the faulty ones the server must refuse.
 */
class FakeTsa {
    enum class Mode { OK, WRONG_NONCE, WRONG_IMPRINT, REJECTED, NO_CERTIFICATE, TAMPERED, UNAVAILABLE }

    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair()
    private val serial = AtomicLong(1)

    val certificate: X509Certificate = run {
        val name = X500Name("CN=Fausse autorite d'horodatage [EXEMPLE]")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), Date(now - 3_600_000L), Date(now + 365L * 86_400_000L), name, keys.public)
            .addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
            .build(JcaContentSignerBuilder(SIGNATURE).build(keys.private))
        JcaX509CertificateConverter().getCertificate(holder)
    }

    fun answer(requestBytes: ByteArray, mode: Mode): ByteArray {
        val request = TimeStampRequest(requestBytes)
        fun other(imprint: ByteArray = request.messageImprintDigest, nonce: BigInteger = request.nonce): TimeStampRequest =
            TimeStampRequestGenerator().apply { setCertReq(true) }.generate(TSPAlgorithms.SHA256, imprint, nonce)
        return when (mode) {
            Mode.OK, Mode.UNAVAILABLE -> generator(true).generate(request, next(), Date()).encoded
            Mode.WRONG_NONCE -> generator(true).generate(other(nonce = request.nonce.add(BigInteger.ONE)), next(), Date()).encoded
            Mode.WRONG_IMPRINT -> generator(true).generate(other(imprint = sha256(request.messageImprintDigest)), next(), Date()).encoded
            Mode.REJECTED -> generator(true).generateFailResponse(PKIStatus.REJECTION, PKIFailureInfo.badRequest, "[EXEMPLE] refused").encoded
            Mode.NO_CERTIFICATE -> generator(false).generate(request, next(), Date()).encoded
            Mode.TAMPERED -> generator(true).generate(request, next(), Date()).encoded.also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        }
    }

    private fun next(): BigInteger = BigInteger.valueOf(serial.getAndIncrement())

    private fun generator(withCertificate: Boolean): TimeStampResponseGenerator {
        val signer = JcaSimpleSignerInfoGeneratorBuilder().build(SIGNATURE, keys.private, certificate)
        val digest = JcaDigestCalculatorProviderBuilder().build().get(AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256))
        val tokens = TimeStampTokenGenerator(signer, digest, ASN1ObjectIdentifier("1.3.6.1.4.1.32473.1"))
        if (withCertificate) tokens.addCertificates(JcaCertStore(listOf(certificate)))
        return TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED)
    }

    private companion object {
        const val SIGNATURE = "SHA384withECDSA"
    }
}
