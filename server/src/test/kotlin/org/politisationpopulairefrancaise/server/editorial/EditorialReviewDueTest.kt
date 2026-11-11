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
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialDates
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeSetup
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The review date (`ppf-review-by`, decision of the team lead of 9 November 2026) in the back-office: the
 * follow-up queue « À revoir » and the check of the date in a draft, on the real PostgreSQL of the tests.
 */
class EditorialReviewDueTest {
    private val clock = MutableClock(Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val section = "/equipe/sections/editorial.comprendre"
    private val editorKeys = IdentityKeys.generate()
    private val policy = PublicationPolicy(setOf(editorKeys.publicKey.hex))
    private val data get() = BackOfficeTestData.backOffice(clock)
    private val today: LocalDate get() = LocalDate.now(clock.withZone(ZoneId.of("Europe/Paris")))

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

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get("$bo$path") {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
    }

    private suspend fun HttpClient.form(path: String, cookie: String, fields: List<Pair<String, String>>): HttpResponse =
        submitForm("$bo$path", parameters { fields.forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=$cookie")
            header("Sec-Fetch-Site", "same-origin")
        }

    /** Publishes a fact-check of « Comprendre » under the editorial key, updated on [updated], to review by [review]. */
    private fun publish(slug: String, title: String, updated: LocalDate, review: LocalDate?) {
        val entries = listOf(
            "slug" to slug, "title" to title, "summary" to "[EXEMPLE] Résumé.", "type" to "decryptage", "origin" to "human",
            "verdict" to "exact", "claim" to "[EXEMPLE] Affirmation.", "published_at" to updated.toString(), "updated_at" to updated.toString(),
        ) + listOfNotNull(review?.let { ContentSchema.REVIEW_BY to it.toString() })
        val unsigned = EditorialEngine.build(ComprendreSchemas.DECRYPTAGE, ContentDraft(ArticleType.DECRYPTAGE, entries, EditorialDraftsTest.BODY), editorKeys.publicKey)
        val event = runBlocking { LocalEventSigner(editorKeys).sign(unsigned) }
        assertEquals(StoreOutcome.STORED, EventRepository(TestDatabase.dataSource).store(event))
    }

    private fun queue() = EditorialReviewDueQueue(EditorialSpace.COMPRENDRE, PublishedContents(EventRepository(TestDatabase.dataSource), policy::isEditor), clock)

    @Test
    fun thePastAndComingDatesAreListedTheLateOnesFirst() {
        publish("en-retard", "[EXEMPLE] En retard", updated = today.minusDays(400), review = today.minusDays(10))
        publish("bientot", "[EXEMPLE] Bientôt", updated = today.minusDays(100), review = today.plusDays(30))
        publish("plus-tard", "[EXEMPLE] Plus tard", updated = today.minusDays(100), review = today.plusDays(61))
        publish("sans-date", "[EXEMPLE] Sans date", updated = today.minusDays(100), review = null)
        val queue = queue()
        assertEquals("editorial.comprendre.a_revoir", queue.id)
        assertEquals(EditorialQueue.reviewPermission(EditorialSpace.COMPRENDRE), queue.viewPermission, "the editors of the space")
        // A date beyond 60 days, or none: never listed, never hidden from the public either.
        assertEquals(2, queue.pendingCount())
        val items = queue.page(null, 10).items
        assertEquals(listOf("en-retard", "bientot"), items.map { it.id })
        val late = items.first()
        assertEquals(EditorialReviewDueQueue.LEVEL_OVERDUE, late.level)
        assertEquals(EditorialDates.noonUtc(today.minusDays(10).toString()), late.deadline, "the review date is the deadline")
        assertTrue(late.summary.orEmpty().contains("date passée depuis 10 jour(s)"), late.summary)
        assertEquals(EditorialReviewDueQueue.LEVEL_SOON, items[1].level)
        assertTrue(items[1].summary.orEmpty().contains("dans 30 jour(s)"), items[1].summary)
        // The detail: its date, how to review it, and the link that opens an update draft.
        val html = createHTML().div { queue.render("en-retard", this) }
        assertTrue(html.contains(EditorialTexts.REVIEW_DUE_HOW))
        assertTrue(html.contains("href=\"$section/mise-a-jour?slug=en-retard\""))
        assertTrue(html.contains(EditorialTexts.REVIEW_DUE_OPEN))
        assertTrue(queue.actions("en-retard").isEmpty(), "the update draft is opened on the page of the section")
        assertTrue(createHTML().div { queue.render("plus-tard", this) }.contains(EditorialTexts.REVIEW_DUE_GONE))
        // The day the date of « plus-tard » enters the window, it is listed too.
        clock.advance(kotlin.time.Duration.parse("1d"))
        assertEquals(3, queue().pendingCount())
    }

    @Test
    fun theReviewOpensAnUpdateDraftThatKeepsTheDate() = testApplication {
        publish("a-revoir", "[EXEMPLE] À revoir", updated = today.minusDays(400), review = today.minusDays(1))
        val client = start()
        val editor = member(data, "Éditrice", listOf(TeamRole.EDITOR))
        val page = client.page("$section/mise-a-jour?slug=a-revoir", editor.token)
        assertEquals(HttpStatusCode.OK, page.status)
        val body = page.bodyAsText()
        assertTrue(body.contains(EditorialTexts.REVIEW_DUE_PAGE_TITLE))
        assertTrue(body.contains("type=\"hidden\" name=\"slug\" value=\"a-revoir\""), "the slug is posted with the form")
        assertTrue(body.contains(EditorialTexts.REVIEW_DUE_OPEN))
        // Unknown slug: back to the section with the usual message.
        assertEquals(HttpStatusCode.Found, client.page("$section/mise-a-jour?slug=inconnu", editor.token).status)
        // The button creates the update draft; the old date comes back in it, and must be moved after today.
        val created = client.form("$section/mise-a-jour", editor.token, listOf("csrf" to Csrf.tokenFor(editor.token), "slug" to "a-revoir"))
        val location = assertNotNull(created.headers[HttpHeaders.Location])
        val id = java.util.UUID.fromString(location.substringAfter("/brouillons/").substringBefore('?'))
        val drafts = EditorialDrafts(TestDatabase.dataSource)
        val content = assertNotNull(drafts.version(id, 1)).content
        assertEquals(today.minusDays(1).toString(), content.entries.last { it.first == ContentSchema.REVIEW_BY }.second)
        val form = client.page("$section/brouillons/$id", editor.token).bodyAsText()
        assertTrue(form.contains("name=\"c.review_by\""), "the date is a field of the form")
        assertTrue(form.contains("type=\"date\" name=\"c.review_by\""), "entered as a date")
        assertTrue(form.contains("la date de revue (${today.minusDays(1)})"), "a past date is flagged before the submission")
    }
}
