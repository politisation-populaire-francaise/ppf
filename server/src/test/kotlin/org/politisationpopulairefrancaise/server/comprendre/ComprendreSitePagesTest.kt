package org.politisationpopulairefrancaise.server.comprendre

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import org.politisationpopulairefrancaise.web.comprendre.ComprendreCatalog
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The pages of "Comprendre" served by the whole application (D-091): addresses, 404, security headers
 * unchanged, the service worker; then the contents read from PostgreSQL, editorial keys only.
 */
class ComprendreSitePagesTest {
    private val files = listOf("fiches.json") + BundledContents.COMPRENDRE_FILES
    private val examples = files.flatMap(BundledContents::load)
    private val catalog = ComprendreCatalog.fromEvents(examples) { it == EditorialTrust.EXAMPLE_EDITOR }

    private fun ApplicationTestBuilder.start(source: ComprendreSource = ComprendreSource { catalog }) {
        application { module(null, PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR)), TEST_RELAY, testRevendications(), testAgir(), comprendre = source) }
    }

    private suspend fun HttpClient.page(path: String, status: HttpStatusCode = HttpStatusCode.OK): String {
        val response: HttpResponse = get(path)
        val body = response.bodyAsText()
        assertEquals(status, response.status, "$path: $body")
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true, path)
        // Readable without script: the only script is the optional registration of the worker.
        Regex("<script[^>]*>").findAll(body).forEach { assertEquals("<script src=\"/static/pwa.js\" defer=\"defer\">", it.value, path) }
        assertFalse(body.contains("style=\""), "$path: no inline style")
        val csp = response.headers["Content-Security-Policy"].orEmpty()
        assertEquals(
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; manifest-src 'self'; " +
                "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
            csp,
        )
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertTrue(response.headers["Set-Cookie"] == null, "$path: no cookie")
        return body
    }

    @Test
    fun everyPageIsServedAtItsAddress() = testApplication {
        start()
        val home = client.page("/comprendre")
        assertFalse(home.contains("emplacement réservé"))
        listOf(
            "/comprendre/fiches", "/comprendre/fiches/garde-a-vue", "/comprendre/parcours", "/comprendre/parcours/le-49-3-et-la-motion-de-censure",
            "/comprendre/decryptages", "/comprendre/decryptages/49-3-loi-sans-vote", "/comprendre/histoire", "/comprendre/histoire/front-populaire-1936",
            "/comprendre/qui-decide", "/comprendre/qui-decide/eau-du-robinet", "/comprendre/glossaire", "/comprendre/glossaire?q=censure",
            "/comprendre/glossaire/49-3", "/comprendre/methode", "/comprendre/hors-ligne", "/comprendre/enseignants",
        ).forEach { client.page(it) }
    }

    @Test
    fun anUnknownAddressAnswersA404Page() = testApplication {
        start()
        listOf("/comprendre/fiches/inexistante", "/comprendre/parcours/inexistant", "/comprendre/glossaire/inexistant", "/comprendre/autre/chose")
            .forEach { assertTrue(client.page(it, HttpStatusCode.NotFound).contains("Contenu introuvable"), it) }
    }

    @Test
    fun withoutMethodPageTheAddressIsNotFound() = testApplication {
        start { ComprendreCatalog.EMPTY }
        client.page("/comprendre/methode", HttpStatusCode.NotFound)
        assertTrue(client.page("/comprendre").contains("Aucun contenu publié"))
    }

    @Test
    fun theServiceWorkerAndItsScriptAreServed() = testApplication {
        start()
        val worker = client.get("/sw.js")
        assertEquals(HttpStatusCode.OK, worker.status)
        assertTrue(worker.contentType()?.match(ContentType.Application.JavaScript) == true)
        assertEquals("no-cache", worker.headers["Cache-Control"])
        assertTrue(worker.bodyAsText().contains("\"/comprendre/fiches/garde-a-vue\""))
        val register = client.get("/static/pwa.js")
        assertEquals(HttpStatusCode.OK, register.status)
        assertTrue(register.bodyAsText().contains("serviceWorker.register('/sw.js')"))
    }

    @Test
    fun theDatabaseIsReadForTheEditorialKeysOnly() {
        TestDatabase.reset()
        val events = EventRepository(TestDatabase.dataSource)
        examples.forEach(events::store)
        val stranger = LocalEventSigner(IdentityKeys.generate())
        val unsigned = Article.build(stranger.publicKey, "fausse-fiche", "Fausse fiche", null, emptyList(), ArticleType.FICHE_PRATIQUE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.")
        events.store(runBlocking { stranger.sign(unsigned) })
        val read = runBlocking { EditorialComprendreSource(events, PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR)), ttl = Duration.ZERO).catalog() }
        assertEquals(catalog.fiches.size, read.fiches.size)
        assertEquals(catalog.parcours.size, read.parcours.size)
        assertEquals(catalog.glossary.size, read.glossary.size)
        assertEquals(catalog.sheets.size, read.sheets.size)
        assertTrue(read.method != null)
        assertTrue(read.fiche("fausse-fiche") == null)
        assertTrue(runBlocking { EditorialComprendreSource(events, PublicationPolicy(emptySet()), ttl = Duration.ZERO).catalog() }.isEmpty)
    }
}
