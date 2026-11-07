package org.politisationpopulairefrancaise.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import org.politisationpopulairefrancaise.tools.opendata.ExampleSources
import org.politisationpopulairefrancaise.tools.opendata.ExitCodes
import org.politisationpopulairefrancaise.tools.opendata.Importer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `/api/v1/parlement`: the files written by the import tool, read-only, as the application fetches them. */
class ParlementRoutesTest {
    private val keys = IdentityKeys.generate()
    private val directory = Files.createTempDirectory("ppf-parlement").also { dir ->
        check(Importer(ExampleSources(), keys, dir, log = {}).run() == ExitCodes.OK)
        // Files of the folder that are not packages of the index are never served.
        Files.writeString(dir.resolve("secret.txt"), "not a package")
        Files.writeString(dir.resolve("notes.json"), "{}")
    }

    private fun app(config: OpenDataConfig = OpenDataConfig(directory), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(policy = NO_EDITORS, relay = TEST_RELAY, revendications = testRevendications())
            routing { parlementRoutes(config) }
        }
        block()
    }

    @Test
    fun theIndexIsTheSignedEventOfTheImport() = app {
        val response = client.get("/api/v1/parlement")
        assertEquals(HttpStatusCode.OK, response.status)
        val event = SignedEvent.fromJson(response.bodyAsText())
        val reading = assertIs<IndexReading.Accepted>(ParliamentIndexReader.read(event, OpenDataTrust.of(keys.publicKey.hex)))
        val tag = assertNotNull(response.headers[HttpHeaders.ETag])
        assertEquals("\"${ParliamentFormat.sha256Hex(response.readRawBytes())}\"", tag)
        assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/parlement") { header(HttpHeaders.IfNoneMatch, tag) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/parlement") { header(HttpHeaders.IfNoneMatch, "\"${"0".repeat(64)}\"") }.status)

        // Every package of the index, with its fingerprint as ETag.
        reading.index.packages.forEach { ref ->
            val pkg = client.get("/api/v1/parlement/${ref.name}")
            assertEquals(HttpStatusCode.OK, pkg.status, ref.name)
            assertTrue(ParliamentIndexReader.matches(ref, pkg.readRawBytes()), ref.name)
            assertEquals("\"${ref.sha256}\"", pkg.headers[HttpHeaders.ETag])
            assertEquals(HttpStatusCode.NotModified, client.get("/api/v1/parlement/${ref.name}") { header(HttpHeaders.IfNoneMatch, ref.sha256) }.status)
        }
    }

    @Test
    fun unknownNamesAndPathsOutsideTheFolderAreRefused() = app {
        listOf("inconnu", "notes", "secret.txt", "index.event", "..", "..%2Fsecret.txt", "%2E%2E%2Fhealth", "membres.json", "MEMBRES", "a".repeat(65)).forEach { name ->
            val response = client.get("/api/v1/parlement/$name")
            assertEquals(HttpStatusCode.NotFound, response.status, name)
            if (response.bodyAsText().isNotEmpty()) assertTrue("unknown_package" in response.bodyAsText(), name)
        }
        assertTrue("unknown_package" in client.get("/api/v1/parlement/inconnu").bodyAsText())
    }

    @Test
    fun beforeTheFirstImportTheIndexIsUnavailable() = app(OpenDataConfig(Files.createTempDirectory("ppf-parlement-empty"))) {
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/parlement").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/parlement/membres").status)
    }

    @Test
    fun withoutTheVariableTheRoutesDoNotExist() = testApplication {
        application { module(policy = NO_EDITORS, relay = TEST_RELAY, revendications = testRevendications()) }
        // Another route of the API answers (503 without a database): not ours.
        val response = client.get("/api/v1/parlement")
        assertTrue(response.status != HttpStatusCode.OK)
        assertTrue("no_open_data" !in response.bodyAsText() && ParliamentFormat.INDEX_D !in response.bodyAsText())
    }

    @Test
    fun ifNoneMatchAcceptsListsWeakTagsAndAStar() {
        val tag = "a".repeat(64)
        assertTrue(etagMatches("\"$tag\"", tag))
        assertTrue(etagMatches("W/\"$tag\"", tag))
        assertTrue(etagMatches("\"other\", \"$tag\"", tag))
        assertTrue(etagMatches("*", tag))
        assertTrue(!etagMatches("\"b\"", tag))
    }
}
