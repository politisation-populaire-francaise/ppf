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
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.voir.VoirSchemas
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
import org.politisationpopulairefrancaise.server.backoffice.TestMember
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The editorial circuit of « Voir » (step 6): the section `editorial.voir` and its queue, on the schemas of
 * core/voir/VoirSchemas.kt, tested on a fact and a party as the steps 3 and 4 were on Comprendre, on the
 * real PostgreSQL of the tests.
 */
class VoirEditorialTest {
    private val clock = MutableClock(Instant.now())
    private val bo = "http://localhost:${BackOfficeTestData.PORT}"
    private val section = "/equipe/sections/editorial.voir"
    private val policy = PublicationPolicy(setOf(IdentityKeys.generate().publicKey.hex))
    private val drafts = EditorialDrafts(TestDatabase.dataSource, clock)
    private val data get() = BackOfficeTestData.backOffice(clock)

    /** A fake of the signing seam: records each hand-over, answers an approval page. */
    private class RecordingPublisher : EditorialPublisher {
        val handedOver = mutableListOf<ReadyToSign>()
        private val answer = PublisherAnswer.AwaitingApproval("draft-1", "https://signature.example/approbation/1", approvals = 0, required = 2)

        override suspend fun handOver(version: ReadyToSign): PublisherAnswer {
            handedOver += version
            return answer
        }

        override suspend fun state(version: ReadyToSign): PublisherAnswer = answer
    }

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun ApplicationTestBuilder.start(publisher: EditorialPublisher = RecordingPublisher()): HttpClient {
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
            module(TestDatabase.dataSource, policy, TEST_RELAY, testRevendications(), testAgir(), backOffice = BackOfficeSetup(BackOfficeTestData.CONFIG, clock), editorialPublisher = publisher)
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.page(path: String, who: TestMember): HttpResponse = get("$bo$path") {
        header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}")
    }

    private suspend fun HttpClient.form(path: String, who: TestMember, fields: List<Pair<String, String>>): HttpResponse =
        submitForm("$bo$path", parameters { (fields + ("csrf" to Csrf.tokenFor(who.token))).forEach { (key, value) -> append(key, value) } }) {
            header(HttpHeaders.Cookie, "$SESSION_COOKIE=${who.token}")
            header("Sec-Fetch-Site", "same-origin")
        }

    private fun header(slug: String, title: String) = listOf(
        "action" to "save",
        "c.slug" to slug,
        "c.title" to title,
        "c.summary" to "[EXEMPLE] Ce que dit ce contenu en une phrase.",
        "c.published_at" to "2026-11-08",
        "c.updated_at" to "2026-11-08",
        "origin" to "human",
        "body" to "[EXEMPLE] Contenu de démonstration : les personnes, les organisations et les chiffres sont fictifs.\n",
    )

    /** A fact of the chronology, one source, nothing that names a person. */
    private val fact = header("exemple-mobilisation", "[EXEMPLE] Une journée de mobilisation") + listOf(
        "rows.fact" to "1", "f.fact.0" to "mobilisation",
        "rows.date" to "1", "f.date.0" to "2026-10-02",
        "rows.place" to "1", "f.place.0" to "Exempleville",
        "rows.fact-status" to "1", "f.fact-status.0" to "etabli",
        "rows.source" to "1",
        "f.source.0.kind" to "medias",
        "f.source.0.name" to "Article du journal d’exemple",
        "f.source.0.url" to "https://example.org/exemple/article",
        "f.source.0.date" to "2026-10-03",
    )

    /** A party with repeated lines: two years of public aid, seats in two chambers. */
    private val party = header("parti-exemple-z", "[EXEMPLE] Parti d’exemple Z") + listOf(
        "rows.acronym" to "1", "f.acronym.0" to "PEZ",
        "rows.financing" to "1",
        "f.financing.0.kind" to "association-de-financement",
        "f.financing.0.reference" to "Agrément (exemple)",
        "f.financing.0.since" to "2009-07-02",
        "f.financing.0.source-kind" to "texte-officiel",
        "f.financing.0.source-name" to "Journal officiel, avis d’agrément (exemple)",
        "f.financing.0.source-url" to "https://example.org/exemple/jo",
        "rows.public-funding" to "2",
        "f.public-funding.0.year" to "2026", "f.public-funding.0.amount" to "1 210 345,60",
        "f.public-funding.0.decree" to "Décret de répartition pour 2026 (exemple)", "f.public-funding.0.url" to "https://example.org/exemple/decret-2026",
        "f.public-funding.1.year" to "2025", "f.public-funding.1.amount" to "1 105 120,00",
        "f.public-funding.1.decree" to "Décret de répartition pour 2025 (exemple)", "f.public-funding.1.url" to "https://example.org/exemple/decret-2025",
        "rows.seats" to "2",
        "f.seats.0.chamber" to "assemblee", "f.seats.0.count" to "12", "f.seats.0.date" to "2026-10-01",
        "f.seats.0.source-kind" to "parlement", "f.seats.0.source-name" to "Assemblée nationale, groupes (exemple)", "f.seats.0.source-url" to "https://example.org/exemple/an",
        "f.seats.1.chamber" to "senat", "f.seats.1.count" to "4", "f.seats.1.date" to "2026-10-01",
        "f.seats.1.source-kind" to "parlement", "f.seats.1.source-name" to "Sénat, groupes (exemple)", "f.seats.1.source-url" to "https://example.org/exemple/senat",
        "rows.source" to "1",
        "f.source.0.kind" to "texte-officiel",
        "f.source.0.name" to "Statuts déposés en préfecture (exemple)",
        "f.source.0.url" to "https://example.org/exemple/statuts",
        "f.source.0.date" to "2009-05-16",
    )

    private fun draftIdOf(response: HttpResponse): UUID {
        val location = assertNotNull(response.headers[HttpHeaders.Location], "redirected to the draft: ${response.status}")
        return UUID.fromString(location.substringAfter("/brouillons/").substringBefore('?'))
    }

    @Test
    fun theSectionOfVoirOffersEveryTypeOfVoirToEditorsOnly() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val moderator = member(data, "Modo", listOf(TeamRole.MODERATOR))
        val home = client.page(section, alice)
        assertEquals(HttpStatusCode.OK, home.status)
        val html = home.bodyAsText()
        assertTrue(html.contains(EditorialTexts.SECTION_VOIR), html)
        VoirSchemas.all.forEach { schema ->
            assertTrue(html.contains("href=\"$section/nouveau/${schema.type.tagValue}\""), schema.type.tagValue)
            // The form generated from each schema of Voir: no script, no inline style, no em dash.
            val form = client.page("$section/nouveau/${schema.type.tagValue}", alice).also { assertEquals(HttpStatusCode.OK, it.status, schema.type.tagValue) }.bodyAsText()
            assertTrue(form.contains("name=\"c.title\""), schema.type.tagValue)
            listOf("<script", "style=", "—").forEach { assertFalse(form.contains(it), "${schema.type.tagValue}: $it") }
        }
        assertFalse(html.contains("nouveau/decryptage"), "the types of Comprendre stay in their section")
        assertEquals(HttpStatusCode.Forbidden, client.page(section, moderator).status)
        assertEquals(HttpStatusCode.NotFound, client.page("/files/editorial.voir", moderator).status, "the queue is hidden from who may not review")
    }

    @Test
    fun aFactIsWrittenPreviewedAsOnTheSiteAndSubmittedToTheQueueOfVoir() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/fait", alice, fact))
        val draft = drafts.find(id)!!
        assertEquals(EditorialSpace.VOIR, draft.space)
        assertEquals(ArticleType.FAIT, draft.type)
        val page = client.page("$section/brouillons/$id", alice).bodyAsText()
        assertTrue(page.contains(EditorialTexts.SUBMIT), "a fact without any problem can be submitted: $page")

        // The preview is the page of the site, under the headings of the back-office.
        val preview = client.page("$section/brouillons/$id/apercu", alice).bodyAsText()
        assertTrue(preview.contains("<div class=\"voir-preview\">"), preview)
        assertTrue(preview.contains("<h2>[EXEMPLE] Une journée de mobilisation</h2>"), preview)
        assertTrue(preview.contains("<h3 id=\"sources\">Sources</h3>"), preview)
        assertTrue(preview.contains("Article du journal d’exemple"))
        assertFalse(preview.contains(EditorialTexts.PREVIEW_IMPOSSIBLE))

        val seq = lastSeq()
        val submit = client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        assertEquals("$section/brouillons/$id?ok=submitted", submit.headers[HttpHeaders.Location])
        assertEquals(DraftStatus.IN_REVIEW, drafts.find(id)!!.status)
        assertEquals(listOf("section.editorial.voir.submit_requested", "section.editorial.voir.submit"), journalAfter(seq).map { it.action })
        assertEquals("editorial.voir:$id", journalAfter(seq).last().targetId)
        // The queue editorial.voir lists it for the editors; the queue of Comprendre does not.
        val queue = client.page("/files/editorial.voir", alice)
        assertEquals(HttpStatusCode.OK, queue.status)
        assertTrue(queue.bodyAsText().contains("[EXEMPLE] Une journée de mobilisation"))
        assertFalse(client.page("/files/editorial.comprendre", alice).bodyAsText().contains("[EXEMPLE] Une journée de mobilisation"))
    }

    @Test
    fun aPartyWithRepeatedLinesIsSavedWithoutProblemAndPreviewedAsItsPage() = testApplication {
        val client = start()
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/parti", alice, party))
        val content = drafts.version(id, 1)!!.content
        assertEquals(2, content.entries.count { it.first == "public-funding" }, content.entries.toString())
        assertEquals(2, content.entries.count { it.first == "seats" }, content.entries.toString())
        assertTrue(content.entries.contains("public-funding" to "2026 | 1 210 345,60 | Décret de répartition pour 2026 (exemple) | https://example.org/exemple/decret-2026"), content.entries.toString())
        assertTrue(client.page("$section/brouillons/$id", alice).bodyAsText().contains(EditorialTexts.SUBMIT), "no problem")
        val preview = client.page("$section/brouillons/$id/apercu", alice).bodyAsText()
        assertTrue(preview.contains("<h2>[EXEMPLE] Parti d’exemple Z</h2>"), preview)
        assertTrue(preview.contains("Parti politique · PEZ"), preview)
        assertTrue(preview.contains("Statuts déposés en préfecture (exemple)"))
    }

    @Test
    fun anotherEditorApprovesAFactOfVoirForTheSigningUseOfVoir() = testApplication {
        val publisher = RecordingPublisher()
        val client = start(publisher)
        val alice = member(data, "Alice", listOf(TeamRole.EDITOR))
        val bruno = member(data, "Bruno", listOf(TeamRole.EDITOR))
        val id = draftIdOf(client.form("$section/nouveau/fait", alice, fact))
        client.form("$section/brouillons/$id/soumettre", alice, listOf("version" to "1"))
        val review = client.page("$section/relecture/$id", bruno).bodyAsText()
        assertTrue(review.contains("<h2>[EXEMPLE] Une journée de mobilisation</h2>"), "the submitted fact, as on the site: $review")
        val seq = lastSeq()
        val approved = client.form("$section/relecture/$id", bruno, listOf("decision" to EditorialDraftsSection.ACTION_APPROVE, "version" to "1"))
        assertEquals(HttpStatusCode.OK, approved.status, approved.bodyAsText())
        assertEquals(DraftStatus.READY_TO_SIGN, drafts.find(id)!!.status)
        val call = publisher.handedOver.single()
        assertEquals(EditorialSpace.VOIR, call.space)
        assertEquals(ArticleType.FAIT, call.type)
        assertEquals(SigningUse.EDITORIAL_VOIR, call.use)
        assertEquals(alice.id, call.author)
        assertEquals(bruno.id, call.approvedBy)
        assertEquals(listOf("section.editorial.voir.approve_requested", "section.editorial.voir.approve"), journalAfter(seq).map { it.action })
    }
}
