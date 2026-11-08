package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.engine.connector
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** A section of a pole (contract, section 14): the guards of the base around its pages and forms. */
class BackOfficeSectionTest {
    private val clock = MutableClock(java.time.Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    /** A section of the tests: a page, a plain form, a sensitive form, a large form. */
    private class TestSection(override val id: String = "essai.notes", override val maxBodyBytes: Int = MAX_BODY_BYTES) : BackOfficeSection {
        override val title: String = "Notes d’essai"
        override val permission: String = Permissions.editorialWrite("comprendre")

        /** How many times a form handler ran (the guards of the base must stop a refused post before it). */
        val calls = java.util.concurrent.atomic.AtomicInteger()

        override fun routes(routes: SectionRoutes) {
            routes.get("") {
                respondPage(title) {
                    h1 { +title }
                    form(action = path("note"), method = FormMethod.post) {
                        csrfInput(this)
                        button(type = ButtonType.submit) { +"Écrire" }
                    }
                }
            }
            routes.get("fiches/{id}") {
                val id = pathParameter("id").orEmpty()
                if (id != "1") return@get notFound()
                respondPage(title) { p { +"Fiche $id" } }
            }
            routes.post("note") { form ->
                calls.incrementAndGet()
                audit("note_written", "n-1", form["motif"], mapOf("size" to form["texte"].orEmpty().length.toString()))
                redirect(query = "ok=1")
            }
            routes.post("sensible") { _ ->
                if (!requireFresh()) return@post
                audit("sensitive_done", "n-2")
                redirect()
            }
        }
    }

    private fun ApplicationTestBuilder.start(backOffice: BackOffice): HttpClient {
        engine {
            connector {
                host = "localhost"
                port = BackOfficeTestData.PORT
            }
        }
        application {
            install(ContentNegotiation) { json() }
            install(backOfficeHeaders(BackOfficeTestData.PORT))
            routing { createChild(PortSelector(BackOfficeTestData.PORT)).backOfficeRoutes(backOffice) }
        }
        return createClient { followRedirects = false }
    }

    private fun backOffice(vararg sections: BackOfficeSection): BackOffice =
        BackOfficeTestData.backOffice(clock).also { bo -> sections.forEach(bo.sections::register) }

    private suspend fun HttpClient.page(path: String, cookie: String?): HttpResponse = get("$bo$path") {
        cookie?.let { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") }
    }

    private suspend fun HttpClient.form(path: String, cookie: String?, fields: Map<String, String>): HttpResponse =
        submitForm("$bo$path", parameters { fields.forEach { (key, value) -> append(key, value) } }) {
            cookie?.let { header(HttpHeaders.Cookie, "$SESSION_COOKIE=$it") }
            header("Sec-Fetch-Site", "same-origin")
        }

    private suspend fun HttpClient.json(path: String, body: String, cookie: String, csrf: String): HttpResponse = post("$bo$path") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Origin, BackOfficeTestData.ORIGIN)
        header("Sec-Fetch-Site", "same-origin")
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
        header(Csrf.HEADER, csrf)
        setBody(body)
    }

    @Test
    fun withoutASessionEveryPageAndFormLeadsToTheSignIn() = testApplication {
        val client = start(backOffice(TestSection()))
        val page = client.page("/equipe/sections/essai.notes", null)
        assertEquals(HttpStatusCode.Found, page.status)
        assertEquals("/connexion", page.headers[HttpHeaders.Location])
        assertEquals("/connexion", client.page("/equipe/sections/essai.notes/fiches/1", null).headers[HttpHeaders.Location])
        val post = client.form("/equipe/sections/essai.notes/note", null, mapOf("csrf" to "x"))
        assertEquals(HttpStatusCode.Found, post.status)
        assertEquals("/connexion", post.headers[HttpHeaders.Location])
        assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun aMemberWithoutThePermissionIsRefusedAndDoesNotSeeTheMenuEntry() = testApplication {
        val bo = backOffice(TestSection())
        val client = start(bo)
        val moderator = member(bo, "Modo", listOf(TeamRole.MODERATOR))
        val seq = lastSeq()
        assertEquals(HttpStatusCode.Forbidden, client.page("/equipe/sections/essai.notes", moderator.token).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/equipe/sections/essai.notes/note", moderator.token, mapOf("csrf" to Csrf.tokenFor(moderator.token))).status)
        assertTrue(journalAfter(seq).none { it.action.startsWith("section.") })
        assertFalse(client.page("/", moderator.token).bodyAsText().contains("/equipe/sections/essai.notes"))
        // With a single key, an editor's permissions are reduced (backup key rule): refused too.
        val single = member(bo, "Seule", listOf(TeamRole.EDITOR), keys = 1)
        assertEquals(HttpStatusCode.Forbidden, client.page("/equipe/sections/essai.notes", single.token).status)
    }

    @Test
    fun anEditorOpensTheSectionInTheLayoutOfTheBase() = testApplication {
        val bo = backOffice(TestSection())
        val client = start(bo)
        val editor = member(bo, "Éditrice", listOf(TeamRole.EDITOR))
        val page = client.page("/equipe/sections/essai.notes", editor.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("<html lang=\"fr\""))
        assertTrue(html.contains("href=\"/equipe/sections/essai.notes\" aria-current=\"page\""), "menu entry of the section, current")
        assertTrue(html.contains("name=\"csrf\" value=\"${Csrf.tokenFor(editor.token)}\""))
        assertTrue(html.contains("action=\"/equipe/sections/essai.notes/note\""))
        assertFalse(Regex("<script(?![^>]*src=)").containsMatchIn(html), "no inline script")
        // The dashboard shows the entry too; path parameters reach the section.
        assertTrue(client.page("/", editor.token).bodyAsText().contains("href=\"/equipe/sections/essai.notes\""))
        assertEquals(HttpStatusCode.OK, client.page("/equipe/sections/essai.notes/fiches/1", editor.token).status)
        assertEquals(HttpStatusCode.NotFound, client.page("/equipe/sections/essai.notes/fiches/2", editor.token).status)
        assertEquals(HttpStatusCode.NotFound, client.page("/equipe/sections/autre", editor.token).status)
    }

    @Test
    fun aFormWithoutAValidAntiForgeryTokenIsRefusedAndNothingIsJournaled() = testApplication {
        val section = TestSection()
        val bo = backOffice(section)
        val client = start(bo)
        val editor = member(bo, "Éditrice", listOf(TeamRole.EDITOR))
        val seq = lastSeq()
        assertEquals(HttpStatusCode.Forbidden, client.form("/equipe/sections/essai.notes/note", editor.token, emptyMap()).status)
        assertEquals(HttpStatusCode.Forbidden, client.form("/equipe/sections/essai.notes/note", editor.token, mapOf("csrf" to Csrf.tokenFor(Tokens.generate()))).status)
        val crossSite = client.submitForm("http://localhost:${BackOfficeTestData.PORT}/equipe/sections/essai.notes/note", parameters { append("csrf", Csrf.tokenFor(editor.token)) }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${editor.token}")
            header("Sec-Fetch-Site", "cross-site")
        }
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        val otherOrigin = client.submitForm("http://localhost:${BackOfficeTestData.PORT}/equipe/sections/essai.notes/note", parameters { append("csrf", Csrf.tokenFor(editor.token)) }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${editor.token}")
            header(HttpHeaders.Origin, "https://ailleurs.example")
        }
        assertEquals(HttpStatusCode.Forbidden, otherOrigin.status)
        assertTrue(journalAfter(seq).isEmpty())
        assertEquals(0, section.calls.get(), "the handler never ran")
        // The same form, valid, does reach it.
        assertEquals(HttpStatusCode.Found, client.form("/equipe/sections/essai.notes/note", editor.token, mapOf("csrf" to Csrf.tokenFor(editor.token))).status)
        assertEquals(1, section.calls.get())
    }

    @Test
    fun anActionOfTheSectionIsJournaledByTheBase() = testApplication {
        val bo = backOffice(TestSection())
        val client = start(bo)
        val editor = member(bo, "Éditrice", listOf(TeamRole.EDITOR))
        val seq = lastSeq()
        val done = client.form("/equipe/sections/essai.notes/note", editor.token, mapOf("csrf" to Csrf.tokenFor(editor.token), "motif" to "essai", "texte" to "[EXEMPLE] texte"))
        assertEquals(HttpStatusCode.Found, done.status)
        assertEquals("/equipe/sections/essai.notes?ok=1", done.headers[HttpHeaders.Location])
        val line = journalAfter(seq).single()
        assertEquals("section.essai.notes.note_written", line.action)
        assertEquals(BackOffice.TARGET_SECTION_ITEM, line.targetType)
        assertEquals("essai.notes:n-1", line.targetId)
        assertEquals(editor.id.toString(), line.actorId)
        assertEquals("essai", line.reason)
        assertEquals(mapOf("size" to "15"), line.details)
        // The base refuses a line for a member who may not open the section, or a free identifier.
        val moderator = member(bo, "Modo", listOf(TeamRole.MODERATOR))
        assertFailsWith<IllegalArgumentException> { bo.recordSectionAction(BackOfficeTestData.auth(bo, moderator).actor, "essai.notes", "note_written", null, null, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { bo.recordSectionAction(BackOfficeTestData.auth(bo, editor).actor, "essai.notes", "Note écrite", null, null, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { bo.recordSectionAction(BackOfficeTestData.auth(bo, editor).actor, "inconnue", "note", null, null, emptyMap()) }
        // The details are bounded: no text of a content can enter the journal through them.
        val actor = BackOfficeTestData.auth(bo, editor).actor
        fun record(details: Map<String, String>) = bo.recordSectionAction(actor, "essai.notes", "note_written", "n-1", null, details)
        record((1..20).associate { "cle_$it" to "x".repeat(200) })
        assertFailsWith<IllegalArgumentException> { record((1..21).associate { "cle_$it" to "1" }) }
        assertFailsWith<IllegalArgumentException> { record(mapOf("texte" to "x".repeat(201))) }
        assertFailsWith<IllegalArgumentException> { record(mapOf("Texte libre" to "1")) }
        assertFailsWith<IllegalArgumentException> { record(mapOf("k".repeat(41) to "1")) }
    }

    @Test
    fun aSensitiveFormAsksForTheKeyAgainAfterFiveMinutes() = testApplication {
        val bo = backOffice(TestSection())
        val client = start(bo)
        val editor = member(bo, "Éditrice", listOf(TeamRole.EDITOR))
        val csrf = Csrf.tokenFor(editor.token)
        val seq = lastSeq()
        // Fresh session: done at once.
        assertEquals(HttpStatusCode.Found, client.form("/equipe/sections/essai.notes/sensible", editor.token, mapOf("csrf" to csrf, "x" to "1")).status)
        assertEquals(listOf("section.essai.notes.sensitive_done"), journalAfter(seq).map { it.action })

        clock.advance(6.minutes)
        val seq2 = lastSeq()
        val confirm = client.form("/equipe/sections/essai.notes/sensible", editor.token, mapOf("csrf" to csrf, "x" to "1"))
        assertEquals(HttpStatusCode.OK, confirm.status)
        val page = confirm.bodyAsText()
        assertTrue(page.contains(BackOfficeTexts.CONFIRM_TITLE))
        assertTrue(page.contains("action=\"/equipe/sections/essai.notes/sensible\""), "the confirmation posts to the same address")
        assertTrue(page.contains("name=\"x\" value=\"1\""), "with the same fields")
        assertTrue(journalAfter(seq2).isEmpty(), "nothing done before the key")

        val options = Json.decodeFromString<RequestOptionsResponse>(client.json("/webauthn/confirmation/options", "{}", editor.token, csrf).bodyAsText())
        val confirmed = client.json("/webauthn/confirmation", Json.encodeToString(AssertionResponse.serializer(), editor.keys[0].assertion(options, csrf)), editor.token, csrf)
        assertEquals(HttpStatusCode.OK, confirmed.status, confirmed.bodyAsText())
        assertEquals(HttpStatusCode.Found, client.form("/equipe/sections/essai.notes/sensible", editor.token, mapOf("csrf" to csrf, "x" to "1")).status)
        assertEquals(listOf("auth.confirmed", "section.essai.notes.sensitive_done"), journalAfter(seq2).map { it.action })
    }

    @Test
    fun theBodyOfAFormIsBoundedByTheSection() = testApplication {
        val bo = backOffice(TestSection(), TestSection("essai.long", maxBodyBytes = BackOfficeSection.MAX_SECTION_BODY_BYTES))
        val client = start(bo)
        val editor = member(bo, "Éditrice", listOf(TeamRole.EDITOR))
        val fields = mapOf("csrf" to Csrf.tokenFor(editor.token), "texte" to "a".repeat(100_000))
        assertEquals(HttpStatusCode.PayloadTooLarge, client.form("/equipe/sections/essai.notes/note", editor.token, fields).status)
        assertEquals(HttpStatusCode.Found, client.form("/equipe/sections/essai.long/note", editor.token, fields).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.form("/equipe/sections/essai.long/note", editor.token, fields + ("texte" to "a".repeat(300_000))).status)
    }

    @Test
    fun sectionsAreRegisteredOnceAndBeforeTheRoutesAreMounted() {
        val sections = BackOfficeSections()
        sections.register(TestSection())
        assertFailsWith<IllegalArgumentException> { sections.register(TestSection()) }
        assertFailsWith<IllegalArgumentException> { sections.register(TestSection("Pas Valide")) }
        assertFailsWith<IllegalArgumentException> { sections.register(TestSection("essai.trop", maxBodyBytes = 300 * 1024)) }
        sections.markMounted()
        assertFailsWith<IllegalStateException> { sections.register(TestSection("essai.tard")) }
    }
}
