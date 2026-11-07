package org.politisationpopulairefrancaise.server.elus

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.util.zip.GZIPInputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GET /api/v1/elus and GET /api/v1/elus/{code} (contract, section 1.4). */
class ElusApiTest {
    private val setup = ElusSetup(ElusConfig(refreshDays = 0))

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun importExample() {
        assertIs<ImportOutcome.Imported>(ElusImporter(ElusStore(TestDatabase.dataSource)).run(ExampleSource()))
    }

    private fun ApplicationTestBuilder.start() {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), elus = setup) }
    }

    @Test
    fun beforeTheFirstImportTheDirectoryIsUnavailable() = testApplication {
        start()
        val response = client.get("/api/v1/elus")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"elus_unavailable\""))
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/elus/69").status)
    }

    @Test
    fun theIndexAndThePackagesAnswerWithTheirSha256AsETag() = testApplication {
        importExample()
        start()
        val response = client.get("/api/v1/elus")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("application/json; charset=UTF-8", response.headers[HttpHeaders.ContentType])
        val body = response.bodyAsBytes()
        assertEquals("\"${sha256Hex(body)}\"", response.headers[HttpHeaders.ETag])
        assertEquals("public, no-cache", response.headers[HttpHeaders.CacheControl])
        val index = ElusJson.decodeFromString(ElusIndex.serializer(), body.toString(Charsets.UTF_8))
        assertTrue(index.example)

        val entry = index.packages.first { it.code == "69" }
        val pack = client.get("/api/v1/elus/69")
        assertEquals(HttpStatusCode.OK, pack.status)
        val packBody = pack.bodyAsBytes()
        assertEquals(entry.sha256, sha256Hex(packBody))
        assertEquals(entry.bytes, packBody.size.toLong())
        assertEquals("\"${entry.sha256}\"", pack.headers[HttpHeaders.ETag])
        val decoded = ElusJson.decodeFromString(ElusPackage.serializer(), packBody.toString(Charsets.UTF_8))
        assertTrue(decoded.example)
        assertEquals("69", decoded.code)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/elus/FR").status)
    }

    @Test
    fun aKnownETagAnswers304() = testApplication {
        importExample()
        start()
        val sha = client.get("/api/v1/elus/69").headers[HttpHeaders.ETag]!!.trim('"')
        for (value in listOf("\"$sha\"", sha, "W/\"$sha\"", "\"other\", \"$sha\"", "*")) {
            val response = client.get("/api/v1/elus/69") { header(HttpHeaders.IfNoneMatch, value) }
            assertEquals(HttpStatusCode.NotModified, response.status, value)
            assertEquals("\"$sha\"", response.headers[HttpHeaders.ETag])
            assertEquals(0, response.bodyAsBytes().size)
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/elus/69") { header(HttpHeaders.IfNoneMatch, "\"0000\"") }.status)
        val index = client.get("/api/v1/elus").headers[HttpHeaders.ETag]!!
        assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/elus") { header(HttpHeaders.IfNoneMatch, index) }.status)
    }

    @Test
    fun anUnknownCodeAnswers404UnknownPackage() = testApplication {
        importExample()
        start()
        for (code in listOf("00", "96", "2C", "fr", "69M", "ZZ", "01")) {
            val response = client.get("/api/v1/elus/$code")
            assertEquals(HttpStatusCode.NotFound, response.status, code)
            assertTrue(response.bodyAsText().contains("\"unknown_package\""), code)
        }
    }

    @Test
    fun aClientAcceptingGzipGetsTheStoredCompressedBody() = testApplication {
        importExample()
        start()
        val plain = client.get("/api/v1/elus/69").bodyAsBytes()
        val compressed = client.get("/api/v1/elus/69") { header(HttpHeaders.AcceptEncoding, "gzip, deflate, br") }
        assertEquals(HttpStatusCode.OK, compressed.status)
        assertEquals("gzip", compressed.headers[HttpHeaders.ContentEncoding])
        assertEquals("Accept-Encoding", compressed.headers[HttpHeaders.Vary])
        val bytes = compressed.bodyAsBytes()
        assertTrue(bytes.size < plain.size)
        assertContentEquals(plain, GZIPInputStream(bytes.inputStream()).use { it.readAllBytes() })
        val refused = client.get("/api/v1/elus/69") { header(HttpHeaders.AcceptEncoding, "gzip;q=0, identity") }
        assertNull(refused.headers[HttpHeaders.ContentEncoding])
        assertContentEquals(plain, refused.bodyAsBytes())
    }

    @Test
    fun headerParsing() {
        assertTrue(acceptsGzip("gzip"))
        assertTrue(acceptsGzip("deflate, gzip;q=0.5"))
        assertTrue(acceptsGzip("*"))
        assertFalse(acceptsGzip("gzip;q=0"))
        assertFalse(acceptsGzip("br, deflate"))
        assertFalse(acceptsGzip(null))
        assertTrue(ifNoneMatch("\"abc\"", "abc"))
        assertTrue(ifNoneMatch("W/\"abc\"", "abc"))
        assertFalse(ifNoneMatch("\"abd\"", "abc"))
        assertFalse(ifNoneMatch("", "abc"))
    }

    @Test
    fun theDevelopmentDefaultsServeTheExampleSet() {
        val defaults = ElusConfig.fromEnvironment { null }
        assertEquals(ElusConfig(ElusSources.EXAMPLE, 7, officialRefused = false), defaults)
        // `official` is honoured in production and preproduction (owner's decision of 7 November 2026), not in development.
        val preproduction = ElusConfig.fromEnvironment(mapOf("PPF_RNE_SOURCE" to "official", "PPF_ENVIRONMENT" to "preproduction")::get)
        assertEquals(ElusSources.OFFICIAL, preproduction.source)
        val development = ElusConfig.fromEnvironment(mapOf("PPF_RNE_SOURCE" to "official")::get)
        assertEquals(ElusSources.EXAMPLE, development.source)
        assertTrue(development.officialRefused)
        val production = ElusConfig.fromEnvironment(mapOf("PPF_RNE_SOURCE" to " Official ", "PPF_ENVIRONMENT" to "production", "PPF_RNE_REFRESH_DAYS" to "14")::get)
        assertEquals(ElusConfig(ElusSources.OFFICIAL, 14), production)
        assertEquals(0, ElusConfig.fromEnvironment(mapOf("PPF_RNE_REFRESH_DAYS" to "0")::get).refreshDays)
        assertEquals(7, ElusConfig.fromEnvironment(mapOf("PPF_RNE_REFRESH_DAYS" to "-3")::get).refreshDays)
        assertIs<ExampleSource>(ElusSetup(defaults).newSource())
        assertIs<OfficialSource>(ElusSetup(production).newSource())
        assertNotNull(production)
    }
}
