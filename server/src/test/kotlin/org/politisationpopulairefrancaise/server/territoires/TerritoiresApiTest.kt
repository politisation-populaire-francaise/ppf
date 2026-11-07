package org.politisationpopulairefrancaise.server.territoires

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.territoires.TerritoiresDigest
import org.politisationpopulairefrancaise.core.territoires.TerritoiresReader
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.util.zip.GZIPInputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** GET /api/v1/territoires and GET /api/v1/territoires/{code}, and the imports into PostgreSQL. */
class TerritoiresApiTest {
    private val clock = MutableClock(Instant.parse("2026-11-07T08:00:00Z"))
    private val setup = TerritoiresSetup(TerritoiresConfig(refreshDays = 0), clock)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun importExample(): TerritoiresService {
        val service = TerritoiresService(TerritoiresStore(TestDatabase.dataSource), setup)
        assertIs<TerritoiresImportOutcome.Imported>(service.importNow(ExampleTerritoiresSource()))
        return service
    }

    private fun ApplicationTestBuilder.start() {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), territoires = setup) }
    }

    @Test
    fun beforeTheFirstImportTheReferentialIsUnavailable() = testApplication {
        start()
        val response = client.get("/api/v1/territoires")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue("territoires_unavailable" in response.bodyAsText())
        assertEquals("60", response.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun theNationalLevelAndThePackagesAnswerWithTheirSha256AsETag() = testApplication {
        importExample()
        start()
        val national = client.get("/api/v1/territoires")
        assertEquals(HttpStatusCode.OK, national.status)
        val bytes = national.bodyAsBytes()
        val sha = TerritoiresDigest.sha256Hex(bytes)
        assertEquals("\"$sha\"", national.headers[HttpHeaders.ETag])
        assertEquals("public, no-cache", national.headers[HttpHeaders.CacheControl])
        val read = assertNotNull(TerritoiresReader.readNational(bytes.toString(Charsets.UTF_8)))

        assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/territoires") { header(HttpHeaders.IfNoneMatch, "\"$sha\"") }.status)

        val entry = assertNotNull(read.entry("69"))
        val pkg = client.get("/api/v1/territoires/69")
        assertEquals(HttpStatusCode.OK, pkg.status)
        val body = pkg.bodyAsBytes()
        assertTrue(TerritoiresDigest.matches(body, entry.sha256))
        assertEquals(entry.bytes, body.size.toLong())
        assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/territoires/69") { header(HttpHeaders.IfNoneMatch, entry.sha256) }.status)

        val index = client.get("/api/v1/territoires/communes")
        assertEquals(HttpStatusCode.OK, index.status)
        assertNotNull(TerritoiresReader.readCommunesIndex(index.bodyAsText()))

        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/territoires/59").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/territoires/FR").status)
    }

    @Test
    fun aClientThatAcceptsGzipGetsTheStoredCompressedBody() = testApplication {
        importExample()
        start()
        val raw = createClient { }
        val plain = raw.get("/api/v1/territoires/69").bodyAsBytes()
        val compressed = raw.get("/api/v1/territoires/69") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", compressed.headers[HttpHeaders.ContentEncoding])
        assertContentEquals(plain, GZIPInputStream(compressed.bodyAsBytes().inputStream()).use { it.readAllBytes() })
    }

    @Test
    fun aSecondImportReplacesTheFirstAndAFailureChangesNothing() {
        val service = importExample()
        val first = assertNotNull(service.current())
        clock.instant = clock.instant.plusSeconds(3600)
        assertIs<TerritoiresImportOutcome.Imported>(service.importNow(ExampleTerritoiresSource()))
        val second = assertNotNull(service.current())
        assertEquals(first.nationalSha256, second.nationalSha256)
        assertTrue(second.id > first.id)

        val broken = object : TerritoiresSource {
            override val example = true
            override val kind = TerritoiresSources.EXAMPLE
            override fun prepare() = ExampleTerritoiresSource().prepare()
            override fun open(file: TerritoiresFile) =
                if (file == TerritoiresFile.STATISTICS) "niveau;code\n".byteInputStream() else ExampleTerritoiresSource().open(file)
        }
        assertIs<TerritoiresImportOutcome.Refused>(service.importNow(broken))
        assertEquals(second.id, assertNotNull(service.reload()).id)
        assertEquals("failed", TerritoiresStore(TestDatabase.dataSource).lastAttempt()?.status)
    }
}
