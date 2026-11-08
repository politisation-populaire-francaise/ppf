package org.politisationpopulairefrancaise.server.editorial

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.journalAfter
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.lastSeq
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData.member
import org.politisationpopulairefrancaise.server.backoffice.Csrf
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.backoffice.SESSION_COOKIE
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.sql.SQLException
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The drafts of the editorial circuit (step 3) in the back-office, on the real PostgreSQL of the tests. */
class EditorialDraftsTest {
    private val clock = MutableClock(java.time.Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val section = "/equipe/sections/editorial.comprendre"
    private val editorKeys = IdentityKeys.generate()
    private val policy = PublicationPolicy(setOf(editorKeys.publicKey.hex))

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun ApplicationTestBuilder.start(): HttpClient {
        engine {
            connector {
                host = "localhost"
                port = 8080
            }
            connector {
                host = "localhost"
                port = BackOfficeTestData.PORT
            }
        }
        application {
            module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock))
        }
        return createClient { followRedirects = false }
    }

    private val data get() = BackOfficeTestData.backOffice(clock)
    private val drafts = EditorialDrafts(TestDatabase.dataSource)

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get("$bo$path") {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
    }

    private suspend fun HttpClient.form(path: String, cookie: String, fields: List<Pair<String, String>>): HttpResponse =
        submitForm("$bo$path", parameters { fields.forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
            header("Sec-Fetch-Site", "same-origin")
        }

    /** A complete fact-check, without any problem. */
    private fun validDecryptage(token: String, slug: String = "exemple-decryptage", title: String = "[EXEMPLE] Une affirmation vérifiée"): List<Pair<String, String>> = listOf(
        "csrf" to Csrf.tokenFor(token),
        "action" to "save",
        "c.slug" to slug,
        "c.title" to title,
        "c.summary" to "[EXEMPLE] En partie seulement.",
        "c.topics" to "institutions",
        "c.published_at" to "2026-11-08",
        "c.updated_at" to "2026-11-08",
        "origin" to "human",
        "rows.verdict" to "1",
        "f.verdict.0" to "trompeur",
        "rows.claim" to "1",
        "f.claim.0" to "[EXEMPLE] Une loi est adoptée sans vote.",
        "rows.claim_context" to "1",
        "f.claim_context.0" to "",
        "body" to BODY,
    )

    private fun draftIdOf(response: HttpResponse): UUID {
        val location = assertNotNull(response.headers[HttpHeaders.Location], "redirected to the draft")
        return UUID.fromString(location.substringAfter("/brouillons/").substringBefore('?'))
    }

    @Test
    fun anEditorCreatesADraftFromTheFormOfItsTypeAndTheJournalKeepsIt() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val home = client.page(section, editor.token)
        assertEquals(HttpStatusCode.OK, home.status)
        val html = home.bodyAsText()
        ComprendreSchemas.all.forEach { assertTrue(html.contains("href=\"$section/nouveau/${it.type.tagValue}\""), it.type.tagValue) }
        assertTrue(html.contains(EditorialTexts.DRAFTS_EMPTY))

        val empty = client.page("$section/nouveau/decryptage", editor.token).bodyAsText()
        assertTrue(empty.contains("::: objections"), "the template of the type")
        assertFalse(empty.contains("checked"), "no answer ticked in advance, the origin least of all")

        val seq = lastSeq()
        val created = client.form("$section/nouveau/decryptage", editor.token, validDecryptage(editor.token))
        assertEquals(HttpStatusCode.Found, created.status)
        val id = draftIdOf(created)
        val draft = assertNotNull(drafts.find(id))
        assertEquals("exemple-decryptage", draft.slug)
        assertEquals(ArticleType.DECRYPTAGE, draft.type)
        assertEquals(1, draft.latestVersion)
        assertEquals(DraftStatus.DRAFT, draft.status)
        assertEquals(editor.id, draft.author)
        val version = assertNotNull(drafts.version(id, 1))
        assertEquals(0, version.problems, EditorialEngine.check(ComprendreSchemas.DECRYPTAGE, version.content.draft(ArticleType.DECRYPTAGE), PublishedContents(EventRepository(TestDatabase.dataSource), policy::isEditor).snapshot().context(EditorialSpace.COMPRENDRE, emptyMap())).toString())
        assertEquals(
            listOf("slug", "title", "summary", "topics", "type", "origin", "published_at", "updated_at", "verdict", "claim"),
            version.content.entries.map { it.first },
            "the entries in the order of a file of content/, the empty optional field left out",
        )
        val line = journalAfter(seq).single()
        assertEquals("section.editorial.comprendre.draft_created", line.action)
        assertEquals("editorial.comprendre:$id", line.targetId)
        assertEquals(mapOf("version" to "1", "type" to "decryptage", "slug" to "exemple-decryptage", "problems" to "0"), line.details)

        val page = client.page("$section/brouillons/$id?ok=created", editor.token).bodyAsText()
        assertTrue(page.contains(EditorialTexts.CREATED))
        assertTrue(page.contains(EditorialTexts.NO_PROBLEM))
        assertTrue(page.contains("name=\"version\" value=\"1\""))
        assertTrue(client.page(section, editor.token).bodyAsText().contains("$section/brouillons/$id"))
        // A moderator cannot open the pages of the section.
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        assertEquals(HttpStatusCode.Forbidden, client.page("$section/brouillons/$id", moderator.token).status)
    }

    @Test
    fun anIncompleteDraftIsSavedAndItsProblemsAreLinkedToTheirFields() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val overrides = mapOf("c.updated_at" to "", "body" to "Texte sans source.")
        val fields = validDecryptage(editor.token).filter { it.first !in setOf("origin", "f.verdict.0") }.map { (key, value) -> key to (overrides[key] ?: value) }
        val created = client.form("$section/nouveau/decryptage", editor.token, fields)
        assertEquals(HttpStatusCode.Found, created.status, "never lose the work: an invalid draft is saved")
        val id = draftIdOf(created)
        val version = assertNotNull(drafts.version(id, 1))
        assertTrue(version.problems >= 4, "origin, verdict, updated_at, Sources, objections: ${version.problems}")
        val html = client.page("$section/brouillons/$id", editor.token).bodyAsText()
        assertTrue(html.contains("role=\"alert\""))
        assertTrue(html.contains(EditorialTexts.SUMMARY_TITLE))
        // The summary links to the controls, which are marked invalid and described by their message.
        listOf("origin-ai-assisted", "f-verdict-0-v0", "c-updated_at", "body").forEach { anchor ->
            assertTrue(html.contains("href=\"#$anchor\""), "summary link to $anchor")
            val control = Regex("<(input|textarea|select)[^>]*id=\"$anchor\"[^>]*>").find(html)?.value
            assertNotNull(control, "control $anchor")
            assertTrue(control.contains("aria-invalid=\"true\""), control)
            val describedBy = Regex("aria-describedby=\"([^\"]+)\"").find(control)?.groupValues?.get(1)
            assertNotNull(describedBy, control)
            assertTrue(describedBy.split(' ').any { it.startsWith("err-") }, control)
        }
        assertTrue(html.contains("Origine (IA ou non) : ce champ est obligatoire"), "French messages with the labels of the form")
        assertTrue(html.contains("Texte (Markdown) : il manque la section « ## Sources »"))
        assertFormIsLabelled(html)
    }

    @Test
    fun everyControlOfEveryFormHasALabelAndEveryDescriptionExists() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        ComprendreSchemas.all.forEach { schema ->
            val html = client.page("$section/nouveau/${schema.type.tagValue}", editor.token).also { assertEquals(HttpStatusCode.OK, it.status) }.bodyAsText()
            assertFormIsLabelled(html)
            assertFalse(Regex("<script(?![^>]*src=)").containsMatchIn(html), "no inline script")
            assertFalse(html.contains("<script"), "no script at all on a form page")
            assertFalse(html.contains("style="), "no inline style")
            assertFalse(html.contains("—"), "no em dash")
        }
    }

    /** Every visible control has an id and a label for it; every id an aria-describedby names exists. */
    private fun assertFormIsLabelled(html: String) {
        val controls = Regex("<(input|textarea|select)\\b([^>]*)>").findAll(html).map { it.groupValues[2] }.filter { !it.contains("type=\"hidden\"") }.toList()
        assertTrue(controls.isNotEmpty())
        val ids = Regex("\\bid=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(ids.size, ids.toSet().size, "ids are unique: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
        controls.forEach { attributes ->
            val id = Regex("\\bid=\"([^\"]+)\"").find(attributes)?.groupValues?.get(1)
            assertNotNull(id, "a control without id: $attributes")
            assertTrue(html.contains("for=\"$id\""), "a control without label: $id")
            Regex("aria-describedby=\"([^\"]+)\"").find(attributes)?.groupValues?.get(1)?.split(' ')?.forEach { described ->
                assertTrue(described in ids, "$id is described by a missing $described")
            }
        }
    }

    @Test
    fun aPreviewRendersTheBuiltContentWithoutSavingIt() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val fields = validDecryptage(editor.token).map { if (it.first == "action") "action" to "preview" else it }
        val preview = client.form("$section/nouveau/decryptage", editor.token, fields)
        assertEquals(HttpStatusCode.OK, preview.status)
        val html = preview.bodyAsText()
        assertTrue(html.contains(EditorialTexts.PREVIEW_INTRO))
        assertTrue(html.contains("<h3"), "the « ## » headings of the body, one level under the title")
        assertTrue(html.contains("Ce que dit l’affirmation") || html.contains("Ce que dit l'affirmation"))
        assertTrue(html.contains("ppf-verdict"), "the tags of the built event")
        assertTrue(html.contains("&lt;b&gt;"), "the HTML of a body is escaped")
        assertTrue(drafts.list(EditorialSpace.COMPRENDRE).isEmpty(), "nothing saved")
    }

    @Test
    fun aSecondSaveOnAStaleVersionIsRefusedWithAClearMessageAndOverwritesNothing() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", alice.token, validDecryptage(alice.token)))
        val path = "$section/brouillons/$id"
        // Both open version 1; Alice saves first.
        val aliceSave = client.form(path, alice.token, validDecryptage(alice.token, title = "[EXEMPLE] Titre d’Alice") + ("version" to "1"))
        assertEquals(HttpStatusCode.Found, aliceSave.status)
        assertEquals(2, drafts.find(id)!!.latestVersion)
        val seq = lastSeq()
        val stale = client.form(path, bruno.token, validDecryptage(bruno.token, title = "[EXEMPLE] Titre de Bruno") + ("version" to "1"))
        assertEquals(HttpStatusCode.Conflict, stale.status)
        val html = stale.bodyAsText()
        assertTrue(html.contains(EditorialTexts.format(EditorialTexts.CONFLICT, 2)))
        assertTrue(html.contains(EditorialTexts.CONFLICT_DIFF))
        assertTrue(html.contains("[EXEMPLE] Titre de Bruno"), "Bruno's text is kept in the form")
        assertTrue(html.contains("name=\"version\" value=\"2\""), "now based on the latest version")
        val after = drafts.find(id)!!
        assertEquals(2, after.latestVersion)
        assertEquals("[EXEMPLE] Titre d’Alice", after.title, "nothing overwritten")
        assertTrue(journalAfter(seq).isEmpty())
        // Saving again, knowingly, on version 2.
        assertEquals(HttpStatusCode.Found, client.form(path, bruno.token, validDecryptage(bruno.token, title = "[EXEMPLE] Titre de Bruno") + ("version" to "2")).status)
        assertEquals(3, drafts.find(id)!!.latestVersion)
        assertEquals(bruno.id, drafts.version(id, 3)!!.writtenBy)
        assertEquals(listOf(3, 2, 1), drafts.versions(id).map { it.version })
        // The same text again adds no version.
        val same = client.form(path, bruno.token, validDecryptage(bruno.token, title = "[EXEMPLE] Titre de Bruno") + ("version" to "3"))
        assertEquals("$path?ok=unchanged", same.headers[HttpHeaders.Location])
        assertEquals(3, drafts.find(id)!!.latestVersion)
    }

    @Test
    fun aPublishedContentIsUpdatedThroughADraftAndItsDiffShowsTheChanges() = testApplication {
        val signer = LocalEventSigner(editorKeys)
        val published = EditorialEngine.build(
            ComprendreSchemas.DECRYPTAGE,
            ContentDraft(
                ArticleType.DECRYPTAGE,
                listOf(
                    "slug" to "exemple-publie", "title" to "[EXEMPLE] Titre publié", "summary" to "[EXEMPLE] Résumé.", "type" to "decryptage", "origin" to "human",
                    "verdict" to "exact", "claim" to "[EXEMPLE] Affirmation.", "published_at" to "2026-11-01", "updated_at" to "2026-11-01",
                ),
                BODY,
            ),
            editorKeys.publicKey,
        )
        val event = runBlocking { signer.sign(published) }
        assertEquals(StoreOutcome.STORED, EventRepository(TestDatabase.dataSource).store(event))
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val home = client.page(section, editor.token).bodyAsText()
        assertTrue(home.contains("value=\"exemple-publie\""), "the published contents can be updated")
        val created = client.form("$section/mise-a-jour", editor.token, listOf("csrf" to Csrf.tokenFor(editor.token), "slug" to "exemple-publie"))
        val id = draftIdOf(created)
        val draft = drafts.find(id)!!
        assertEquals(event.id, draft.baseEventId)
        assertEquals(0, drafts.version(id, 1)!!.problems)
        val form = client.page("$section/brouillons/$id", editor.token).bodyAsText()
        assertTrue(form.contains("name=\"change_kind\""), "an update asks the nature of the change")
        assertEquals(HttpStatusCode.OK, client.page("$section/brouillons/$id/differences", editor.token).status)
        assertTrue(client.page("$section/brouillons/$id/differences", editor.token).bodyAsText().contains(EditorialTexts.DIFF_SAME))

        val fields = validDecryptage(editor.token, slug = "exemple-publie", title = "[EXEMPLE] Titre corrigé") +
            listOf("version" to "1", "change_kind" to "correction", "change_note" to "[EXEMPLE] Le titre était inexact.")
        assertEquals(HttpStatusCode.Found, client.form("$section/brouillons/$id", editor.token, fields).status)
        val saved = drafts.version(id, 2)!!
        assertEquals(ChangeKind.CORRECTION, saved.content.changeKind)
        val diff = client.page("$section/brouillons/$id/differences", editor.token).bodyAsText()
        assertTrue(diff.contains("+ title: [EXEMPLE] Titre corrigé"))
        assertTrue(diff.contains("- title: [EXEMPLE] Titre publié"))
        assertTrue(diff.contains("${EditorialTexts.DIFF_ADDED} : "), "the change is said in words, not only in colour")
        // The preview and the versions pages answer too; a second draft of the same address is refused.
        assertTrue(client.page("$section/brouillons/$id/apercu", editor.token).bodyAsText().contains("[EXEMPLE] Titre corrigé"))
        assertTrue(client.page("$section/brouillons/$id/versions", editor.token).bodyAsText().contains("Éditrice"))
        val again = client.form("$section/mise-a-jour", editor.token, listOf("csrf" to Csrf.tokenFor(editor.token), "slug" to "exemple-publie"))
        assertEquals("$section?erreur=slug_taken", again.headers[HttpHeaders.Location])
    }

    @Test
    fun theSectionIsNeverServedOnThePublicPort() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        listOf("", "/nouveau/decryptage").forEach { path ->
            val response = client.get("http://localhost:8080$section$path") { header(HttpHeaders.Cookie, "$SESSION_COOKIE=${editor.token}") }
            assertEquals(HttpStatusCode.NotFound, response.status, path)
        }
        val post = client.submitForm("http://localhost:8080$section/nouveau/decryptage", parameters { validDecryptage(editor.token).forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${editor.token}")
            header("Sec-Fetch-Site", "same-origin")
        }
        assertEquals(HttpStatusCode.NotFound, post.status)
        assertTrue(drafts.list(EditorialSpace.COMPRENDRE).isEmpty())
        assertEquals(HttpStatusCode.OK, client.page(section, editor.token).status, "only on the back-office port")
    }

    @Test
    fun aTextOverItsLimitInCharactersIsRefusedWithAClearMessageAndKeptInTheForm() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val empty = client.page("$section/nouveau/decryptage", editor.token).bodyAsText()
        assertTrue(empty.contains("maxlength=\"60000\""), "the browser stops at the limit too")
        assertTrue(empty.contains("60\u202F000 caractères au plus"), "the limit is said in the help of the text")
        val tooLong = client.form("$section/nouveau/decryptage", editor.token, validDecryptage(editor.token).map { if (it.first == "body") "body" to "a".repeat(EditorialDrafts.MAX_BODY + 1) else it })
        assertEquals(HttpStatusCode.UnprocessableEntity, tooLong.status)
        val html = tooLong.bodyAsText()
        assertTrue(html.contains("Le texte a 60\u202F002 caractères, 60\u202F000 au plus. Rien n’a été enregistré"), "clear French message")
        assertTrue(html.contains("a".repeat(1000)), "the text stays in the form")
        assertTrue(drafts.list(EditorialSpace.COMPRENDRE).isEmpty(), "nothing saved")
        // A field of the front matter is never cut silently either.
        val field = client.form("$section/nouveau/decryptage", editor.token, validDecryptage(editor.token).map { if (it.first == "f.claim.0") it.first to "b".repeat(EditorialForm.MAX_VALUE + 1) else it })
        assertEquals(HttpStatusCode.UnprocessableEntity, field.status)
        assertTrue(field.bodyAsText().contains("Le champ « Affirmation vérifiée » a 5001 caractères"))
        assertTrue(drafts.list(EditorialSpace.COMPRENDRE).isEmpty())
    }

    @Test
    fun whenTheJournalCannotBeWrittenTheEditorSeesAnErrorNeverASuccess() = testApplication {
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/decryptage", editor.token, validDecryptage(editor.token)))
        // The database refuses the lines of the drafts for the time of the test.
        sql(
            "CREATE OR REPLACE FUNCTION ppf_test_refuse_line() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'refused for the test'; END $$",
            "CREATE TRIGGER ppf_test_refuse_line BEFORE INSERT ON team_audit_log FOR EACH ROW WHEN (NEW.action LIKE 'section.editorial.%') EXECUTE FUNCTION ppf_test_refuse_line()",
        )
        try {
            val saved = client.form("$section/brouillons/$id", editor.token, validDecryptage(editor.token, title = "[EXEMPLE] Autre titre") + ("version" to "1"))
            assertEquals(HttpStatusCode.InternalServerError, saved.status, "never a redirect to a success")
            assertNull(saved.headers[HttpHeaders.Location])
            val html = saved.bodyAsText()
            assertTrue(html.contains(EditorialTexts.format(EditorialTexts.JOURNAL_FAILED, 2)))
            assertFalse(html.contains(EditorialTexts.SAVED))
            assertEquals(2, drafts.find(id)!!.latestVersion, "the version is written first, then the line: the work is kept")
            val created = client.form("$section/nouveau/fiche-pratique", editor.token, validDecryptage(editor.token, slug = "autre-fiche"))
            assertEquals(HttpStatusCode.InternalServerError, created.status)
        } finally {
            sql("DROP TRIGGER IF EXISTS ppf_test_refuse_line ON team_audit_log", "DROP FUNCTION IF EXISTS ppf_test_refuse_line()")
        }
    }

    private fun sql(vararg statements: String) = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    @Test
    fun theStoreKeepsEveryVersionAndOneOpenDraftPerAddress() {
        val author = member(data, "Éditrice", listOf(TeamRole.EDITOR)).id
        val content = DraftContent(listOf("slug" to "meme-adresse", "title" to "[EXEMPLE] Un"), "Texte\n")
        val first = assertIs<SaveResult.Saved>(drafts.create(EditorialSpace.COMPRENDRE, ArticleType.FICHE_PRATIQUE, content, 3, author)).draft
        assertEquals(SaveResult.SlugTaken, drafts.create(EditorialSpace.COMPRENDRE, ArticleType.DECRYPTAGE, content, 3, author))
        // A draft without a valid slug is kept all the same.
        val noSlug = assertIs<SaveResult.Saved>(drafts.create(EditorialSpace.COMPRENDRE, ArticleType.FICHE_PRATIQUE, DraftContent(listOf("slug" to "Pas Valide"), ""), 5, author)).draft
        assertNull(noSlug.slug)
        assertIs<SaveResult.Unchanged>(drafts.save(first.id, 1, content, 3, author))
        assertIs<SaveResult.Conflict>(drafts.save(first.id, 7, content.copy(body = "Autre\n"), 3, author))
        assertIs<SaveResult.Saved>(drafts.save(first.id, 1, content.copy(body = "Autre\n"), 2, author))
        assertEquals(SaveResult.NotFound, drafts.save(UUID.randomUUID(), 1, content, 0, author))
        // A version is never modified.
        TestDatabase.dataSource.connection.use { connection ->
            assertFailsWith<SQLException> { connection.createStatement().use { it.executeUpdate("UPDATE editorial_draft_version SET body = 'x' WHERE draft_id = '${first.id}'") } }
        }
        assertEquals(listOf("Autre\n", "Texte\n"), drafts.versions(first.id).map { it.content.body })
        // Nothing about the device of an editor: no address, no browser, no e-mail.
        val columns = TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_name LIKE 'editorial_draft%'").use { statement ->
                statement.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toList() }
            }
        }
        assertTrue(columns.contains("written_by"))
        assertTrue(columns.none { Regex("(^|_)(ip|address|agent|browser|email|mail)(_|$)").containsMatchIn(it) }, columns.toString())
        assertEquals(mapOf("meme-adresse" to ArticleType.FICHE_PRATIQUE), drafts.openSlugs())

        // A draft never submitted and unchanged for 180 days is deleted with its versions; the others stay.
        val past = MutableClock(java.time.Instant.now().minus(java.time.Duration.ofDays(181)))
        val old = assertIs<SaveResult.Saved>(EditorialDrafts(TestDatabase.dataSource, past).create(EditorialSpace.COMPRENDRE, ArticleType.FICHE_PRATIQUE, DraftContent(listOf("slug" to "ancien"), ""), 1, author)).draft
        assertEquals(1, drafts.purgeStale())
        assertNull(drafts.find(old.id))
        assertNotNull(drafts.find(first.id))
    }

    companion object {
        val BODY: String = """
            ::: en-bref
            - [EXEMPLE] Un point essentiel.
            :::

            ## Ce que dit l'affirmation

            [EXEMPLE] Texte de démonstration, avec <b>du HTML</b> qui doit rester du texte.

            ::: objections
            ? « [EXEMPLE] Une objection »
            > [EXEMPLE] La réponse, avec sa source.
            :::

            ## Sources

            - [EXEMPLE] Constitution, article 49 : https://www.legifrance.gouv.fr/
        """.trimIndent() + "\n"
    }
}
