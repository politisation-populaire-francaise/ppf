package org.politisationpopulairefrancaise.server.archive

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.archive.OtsAttestation
import org.politisationpopulairefrancaise.core.archive.OtsFile
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.elus.ElusConfig
import org.politisationpopulairefrancaise.server.elus.ElusSetup
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.math.BigInteger
import java.time.LocalDate
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** GET /api/v1/archives/{sha256} and GET /api/v1/archives/{sha256}.ots. */
class ArchiveApiTest {
    private val store = ArchiveStore(TestDatabase.dataSource)
    private val tsa = FakeTsa()

    @BeforeTest
    fun emptyTable() = ArchiveFixtures.reset()

    private fun ApplicationTestBuilder.start() {
        application {
            module(
                TestDatabase.dataSource,
                NO_EDITORS,
                TEST_RELAY,
                testRevendications(),
                elus = ElusSetup(ElusConfig(refreshDays = 0)),
                archive = ArchiveSetup(ArchiveConfig(enabled = false)),
            )
        }
    }

    /** A verified proof of the genesis Merkle root, with an RFC 3161 token of the fake authority. */
    private fun verifiedProof(): Pair<ByteArray, Rfc3161Token> {
        val digest = ArchiveFixtures.GENESIS.merkleRoot
        val file = OtsFile.forSha256(digest)
        file.timestamp.addAttestation(OtsAttestation.BitcoinBlockHeader(0))
        store.queue(digest, LocalDate.of(2026, 11, 7))
        store.saveStamped(digest, listOf(digest to file.toByteArray()))
        store.updateOts(digest, ProofState.PENDING, file.toByteArray(), ProofState.ANCHORED)
        store.markVerified(digest, BitcoinAnchor(0, ArchiveFixtures.GENESIS_HASH, ArchiveFixtures.GENESIS.time))
        val request = Rfc3161Verifier.request(digest, BigInteger.TEN)
        val token = assertIs<Rfc3161Result.Accepted>(Rfc3161Verifier.verify(request, tsa.answer(request.encoded, FakeTsa.Mode.OK))).token
        assertEquals(1, store.saveToken(digest, token))
        return digest to token
    }

    @Test
    fun aVerifiedProofIsServedAsJsonAndAsAnOtsFile() = testApplication {
        val (digest, token) = verifiedProof()
        start()
        val hex = digest.toHexString()

        val response = client.get("/api/v1/archives/$hex")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, no-cache", response.headers[HttpHeaders.CacheControl])
        val view = Json.decodeFromString(ArchiveProofView.serializer(), response.bodyAsText())
        assertEquals(hex, view.digest)
        assertEquals("verified", view.state)
        assertEquals(BitcoinView(0, ArchiveFixtures.GENESIS_HASH, 1_231_006_505L), view.bitcoin)
        val rfc3161 = assertNotNull(view.rfc3161)
        assertContentEquals(token.token, Base64.getDecoder().decode(rfc3161.token))
        assertEquals(token.time.epochSecond, rfc3161.time)
        assertEquals(sha256(tsa.certificate.encoded).toHexString(), rfc3161.tsaCertSha256)
        val fromJson = OtsFile.read(Base64.getDecoder().decode(assertNotNull(view.ots)))
        assertContentEquals(digest, fromJson.digest)

        val file = client.get("/api/v1/archives/$hex.ots")
        assertEquals(HttpStatusCode.OK, file.status)
        assertEquals("application/octet-stream", file.headers[HttpHeaders.ContentType])
        assertEquals("attachment; filename=$hex.ots", file.headers[HttpHeaders.ContentDisposition])
        val read = OtsFile.read(file.bodyAsBytes())
        assertContentEquals(digest, read.digest)
        assertTrue(read.timestamp.isComplete)
        // Upper case is accepted, the answer is the same.
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/archives/${hex.uppercase()}.ots").status)
    }

    @Test
    fun aQueuedDigestHasNoProofYetAndAnUnknownOneIsNotFound() = testApplication {
        val digest = exampleDigest(1)
        store.queue(digest, LocalDate.of(2026, 11, 7))
        start()
        val hex = digest.toHexString()
        val view = Json.decodeFromString(ArchiveProofView.serializer(), client.get("/api/v1/archives/$hex").bodyAsText())
        assertEquals(ArchiveProofView(hex, "queued", null, null, null), view)
        val text = client.get("/api/v1/archives/$hex").bodyAsText()
        assertTrue(text.contains("\"ots\":null") && text.contains("\"bitcoin\":null") && text.contains("\"rfc3161\":null"), text)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/archives/$hex.ots").status)

        val unknown = client.get("/api/v1/archives/${exampleDigest(2).toHexString()}")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertTrue(unknown.bodyAsText().contains("\"not_found\""))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/archives/${exampleDigest(2).toHexString()}.ots").status)
        for (bad in listOf("abc", "z".repeat(64), "${hex}00", "$hex.txt", "$hex.ots.ots")) {
            val response = client.get("/api/v1/archives/$bad")
            assertEquals(HttpStatusCode.BadRequest, response.status, bad)
            assertTrue(response.bodyAsText().contains("\"bad_parameter\""), bad)
        }
    }

    @Test
    fun withoutDatabaseTheArchiveAnswers503() = testApplication {
        application { module(null, NO_EDITORS, TEST_RELAY, testRevendications(), archive = ArchiveSetup(ArchiveConfig(enabled = false))) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/archives/${exampleDigest(1).toHexString()}").status)
    }
}
