package org.politisationpopulairefrancaise.server.revendications

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.revendications.checks.CheckKind
import org.politisationpopulairefrancaise.core.revendications.checks.CheckState
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSource
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewOutcome
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewRequest
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TEST_REVENDICATIONS_CONFIG
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.alteredDemand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.draft
import org.politisationpopulairefrancaise.server.testRevendications
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Citizen demands through the real API: held for review, checked in the background, followed by their author. */
class RevendicationProposalsApiTest {
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)

    /** The team's side of the queue (no HTTP route yet): the same database as the application. */
    private val queue = ProposalQueue(TestDatabase.dataSource, events)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun ApplicationTestBuilder.start(setup: RevendicationsSetup = testRevendications()) {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, setup) }
    }

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = Json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpResponse.publishStatus(): String = Json.decodeFromString<PublishResponse>(bodyAsText()).status

    private suspend fun HttpClient.statuses(vararg ids: String): List<ProposalStatusView> {
        val response = get("/api/v1/revendications/proposals?" + ids.joinToString("&") { "id=$it" })
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(ListSerializer(ProposalStatusView.serializer()), response.bodyAsText())
    }

    private fun ProposalStatusView.decodedReport(): ProposalReport? =
        report?.let { ProposalQueue.reportJson.decodeFromJsonElement(ProposalReport.serializer(), it) }

    /** Waits for the background checks of [id] (they never take long without a real model). */
    private suspend fun HttpClient.reportOf(id: String): ProposalReport {
        repeat(200) {
            statuses(id).singleOrNull()?.decodedReport()?.let { return it }
            delay(50)
        }
        fail("No report for the proposal after 10 seconds")
    }

    @Test
    fun aCitizenDemandWaitsForReviewInsteadOfBeingPublished() = testApplication {
        start()
        val event = citizen.demand()
        val response = client.publish(event)
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("""{"id":"${event.id}","status":"pending_review"}""", response.bodyAsText())

        assertEquals("[]", client.get("/api/v1/events?kind=${Article.KIND}").bodyAsText(), "nothing published")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${event.id}").status)
        assertEquals(0, events.count())
        assertEquals(ProposalStatus.PENDING, queue.find(event.id)?.status)

        // The same event again: still waiting, nothing new.
        assertEquals(HttpStatusCode.Accepted, client.publish(event).status)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun editorsStillPublishDemandsDirectly() = testApplication {
        start()
        val event = editor.demand()
        assertEquals(HttpStatusCode.Created, client.publish(event).status)
        assertEquals(event, events.find(event.id))
        assertNull(queue.find(event.id))

        // Other editorial contents stay reserved to the team.
        val guide = citizen.alteredDemand { it.copy(tags = it.tags.map { tag -> if (tag[0] == Article.TAG_TYPE) listOf(Article.TAG_TYPE, "fiche-pratique") else tag }) }
        val refused = client.publish(guide)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("not_allowed", refused.error())
    }

    @Test
    fun theRulesOfTheFormAreAppliedAgainOnTheServer() = testApplication {
        start()
        for (draft in listOf(
            draft(title = "Trop court"),
            draft(themes = emptyList()),
            draft(why = "[EXEMPLE] Écrivez-moi à prenom.nom@exemple.invalid pour en parler."),
            draft(asks = "x".repeat(4_001)),
        )) {
            val response = client.publish(citizen.demand(draft))
            assertEquals(HttpStatusCode.BadRequest, response.status, draft.title)
            assertEquals("draft_rules", response.error())
            assertFalse("prenom.nom" in response.bodyAsText(), "what the rules found is never echoed")
        }
        val withoutTerritory = citizen.alteredDemand { it.copy(tags = it.tags.filter { tag -> tag[0] != Article.TAG_TERRITORY }) }
        assertEquals("draft_rules", client.publish(withoutTerritory).error())
        val personal = client.publish(citizen.demand(draft(why = "[EXEMPLE] Écrivez-moi à prenom.nom@exemple.invalid.")))
        assertTrue("personal_data (why)" in Json.decodeFromString<ApiError>(personal.bodyAsText()).message)

        val promoted = citizen.alteredDemand { it.copy(tags = it.tags.map { tag -> if (tag[0] == Article.TAG_STATUS) listOf(Article.TAG_STATUS, "retenue") else tag }) }
        val status = client.publish(promoted)
        assertEquals(HttpStatusCode.BadRequest, status.status)
        assertEquals("status_not_allowed", status.error())

        val smuggled = citizen.alteredDemand { it.copy(tags = it.tags + listOf(listOf("h", "collectif-demonstration"))) }
        assertEquals("malformed_demand", client.publish(smuggled).error())

        assertTrue(queue.pending().isEmpty(), "nothing refused was queued")
    }

    @Test
    fun aNewVersionReplacesThePendingOne() = testApplication {
        start()
        val now = DemandFixtures.nowSeconds()
        val first = citizen.demand(createdAt = now - 120)
        val second = citizen.demand(draft(title = "Un médecin de garde joignable le soir et le week-end dans chaque canton"), createdAt = now - 60)
        assertEquals(HttpStatusCode.Accepted, client.publish(first).status)
        assertEquals(HttpStatusCode.Accepted, client.publish(second).status)

        assertEquals(listOf(second.id), client.statuses(first.id, second.id).map { it.id }, "the first version is gone")
        val replay = client.publish(first)
        assertEquals(HttpStatusCode.OK, replay.status)
        assertEquals("superseded", replay.publishStatus())
        assertEquals(listOf(second.id), queue.pending().map { it.id })
    }

    @Test
    fun aKeyMayNotFloodTheQueue() = testApplication {
        start(testRevendications(TEST_REVENDICATIONS_CONFIG.copy(maxPendingPerKey = 2, perKeyPerDay = 3)))
        val now = DemandFixtures.nowSeconds()
        assertEquals(HttpStatusCode.Accepted, client.publish(citizen.demand(slug = "a", createdAt = now - 100)).status)
        assertEquals(HttpStatusCode.Accepted, client.publish(citizen.demand(slug = "b", createdAt = now - 100)).status)

        val third = client.publish(citizen.demand(slug = "c", createdAt = now - 100))
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        assertEquals("too_many_pending", third.error())
        assertEquals("3600", third.headers[HttpHeaders.RetryAfter])

        // A new version of a waiting demand does not add to the queue, but counts in the daily budget.
        assertEquals(HttpStatusCode.Accepted, client.publish(citizen.demand(slug = "a", createdAt = now - 50)).status)
        val overBudget = client.publish(citizen.demand(slug = "b", createdAt = now - 50))
        assertEquals(HttpStatusCode.TooManyRequests, overBudget.status)
        assertEquals("daily_limit", overBudget.error())
        val retryAfter = assertNotNull(overBudget.headers[HttpHeaders.RetryAfter]?.toLongOrNull())
        assertTrue(retryAfter in 1..8 * 3_600 + 1, "one token every 8 hours with 3 per day, got $retryAfter")

        val other = LocalEventSigner(IdentityKeys.generate())
        assertEquals(HttpStatusCode.Accepted, client.publish(other.demand()).status, "another key has its own limits")
    }

    @Test
    fun theQueueOfTheWholeServerIsBounded() = testApplication {
        start(testRevendications(TEST_REVENDICATIONS_CONFIG.copy(maxPending = 1)))
        assertEquals(HttpStatusCode.Accepted, client.publish(citizen.demand()).status)
        val full = client.publish(LocalEventSigner(IdentityKeys.generate()).demand())
        assertEquals(HttpStatusCode.TooManyRequests, full.status)
        assertEquals("queue_full", full.error())
        assertEquals("3600", full.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun theBackgroundChecksFindTheDuplicateOfAnExampleDemand() = testApplication {
        val examples = BundledContents.load("revendications.json")
        assertTrue(examples.size >= 8, "the bundled example demands are found")
        examples.forEach(events::store)
        val cantine = examples.mapNotNull(Article::from).single { it.slug == "cantines-tarif-social" }
        start()

        val event = citizen.demand(draft(title = "Cantine à un euro pour les familles modestes", themes = listOf("education")), slug = "cantine-euro-0001")
        assertEquals(HttpStatusCode.Accepted, client.publish(event).status)
        val report = client.reportOf(event.id)

        val duplicate = report.issues(IssueKind.DUPLICATE).single()
        assertEquals(IssueSource.SIMILARITY, duplicate.source)
        assertEquals(cantine.address.toString(), duplicate.related.first().address)
        assertTrue(report.canSubmit, "a duplicate is a warning: the team decides")
        assertEquals(CheckState.DONE, report.state(CheckKind.RULES))
        assertEquals(CheckState.DONE, report.state(CheckKind.SIMILARITY))
        assertEquals(examples.size, report.checks.first { it.kind == CheckKind.SIMILARITY }.compared)
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.LEGAL), "no legal corpus yet")
        assertEquals(CheckState.UNAVAILABLE, report.state(CheckKind.AI), "no model configured")
    }

    @Test
    fun theModelReadsTheProposalInTheBackground() = testApplication {
        val seen = AtomicReference<ReviewRequest?>(null)
        val model = ProposalReviewModel { request ->
            seen.set(request)
            ReviewOutcome.Reviewed(
                listOf(ProposalIssue(IssueKind.EXISTING_LAW, IssueSeverity.INFO, IssueSource.AI, detail = "[EXEMPLE] Des permanences existent déjà dans certains départements.")),
                model = "modele-de-test",
            )
        }
        val published = editor.demand(draft(title = "Un médecin de garde joignable dans chaque bassin de vie, le soir et le week-end"), slug = "medecin-de-garde")
        events.store(published)
        start(testRevendications(model = model))

        val event = citizen.demand()
        assertEquals(HttpStatusCode.Accepted, client.publish(event).status)
        val report = client.reportOf(event.id)

        assertEquals(CheckState.DONE, report.state(CheckKind.AI))
        assertEquals("modele-de-test", report.checks.first { it.kind == CheckKind.AI }.model)
        assertEquals(IssueSource.AI, report.issues(IssueKind.EXISTING_LAW).single().source)
        val request = assertNotNull(seen.get())
        assertEquals(DemandFixtures.draft(), request.draft, "the draft rebuilt from the event, as its author wrote it")
        assertEquals(listOf("30023:${editor.publicKey.hex}:medecin-de-garde"), request.candidates.map { it.address })
    }

    @Test
    fun theStatusEndpointIsBoundedAndLeaksNothing() = testApplication {
        start()
        val event = citizen.demand()
        assertEquals(HttpStatusCode.Accepted, client.publish(event).status)
        val unknown = "0".repeat(64)

        val response = client.get("/api/v1/revendications/proposals?id=$unknown&id=${event.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val text = response.bodyAsText()
        val listed = Json.decodeFromString(ListSerializer(ProposalStatusView.serializer()), text).single()
        assertEquals(event.id, listed.id)
        assertEquals("pending", listed.status)
        assertNull(listed.decidedAt)
        assertNull(listed.reason)
        assertTrue(listed.receivedAt > 0)
        assertFalse(citizen.publicKey.hex in text, "never the key of the author")
        assertFalse("permanence" in text, "never the content")
        assertFalse(event.sig in text)
        // Every field is there, null or not.
        for (field in listOf("\"id\"", "\"status\"", "\"received_at\"", "\"decided_at\"", "\"reason\"", "\"report\"")) {
            assertTrue(field in text, field)
        }

        assertEquals(emptyList(), client.statuses(unknown))
        for (query in listOf("", "id=", "id=${event.id.uppercase()}", "id=${event.id.dropLast(1)}", "id=${event.id}z", (1..21).joinToString("&") { "id=$unknown" })) {
            val refused = client.get("/api/v1/revendications/proposals?$query")
            assertEquals(HttpStatusCode.BadRequest, refused.status, query)
            assertEquals("bad_parameter", refused.error())
        }
        assertEquals(listOf(event.id), client.statuses(*Array(19) { unknown }, event.id).map { it.id }, "20 ids at most, repeated ones included")
    }

    @Test
    fun anUnpublishedProposalOfSomeoneElseIsNeverShownToAnAuthor() = testApplication {
        start()
        val first = citizen.demand(slug = "medecin-garde-0001")
        assertEquals(HttpStatusCode.Accepted, client.publish(first).status)
        client.reportOf(first.id)
        val other = LocalEventSigner(IdentityKeys.generate())
        val second = other.demand(slug = "medecin-garde-0002")
        assertEquals(HttpStatusCode.Accepted, client.publish(second).status)
        val served = client.reportOf(second.id)

        val firstAddress = "30023:${citizen.publicKey.hex}:medecin-garde-0001"
        val stored = assertNotNull(queue.find(second.id)?.report)
        assertTrue(stored.issues.any { issue -> issue.related.any { it.address == firstAddress } }, "the team sees the other proposal")
        assertTrue(served.issues.none { issue -> issue.related.any { it.address == firstAddress } }, "its author does not")
        assertFalse(citizen.publicKey.hex in client.get("/api/v1/revendications/proposals?id=${second.id}").bodyAsText())

        // Once published, it is a demand like any other, which every author may be pointed to.
        assertEquals(DecisionOutcome.Approved(StoreOutcome.STORED), queue.approve(first.id))
        val afterApproval = assertNotNull(client.statuses(second.id).single().decodedReport())
        assertTrue(afterApproval.issues.any { issue -> issue.related.any { it.address == firstAddress } })
    }

    @Test
    fun approvingPublishesTheDemandAndRejectingTellsTheAuthorWhy() = testApplication {
        start()
        val approved = citizen.demand(slug = "publiee-0001")
        val rejected = citizen.demand(draft(title = "Des crèches ouvertes le soir pour les parents en horaires décalés", themes = listOf("societe")), slug = "refusee-0001")
        assertEquals(HttpStatusCode.Accepted, client.publish(approved).status)
        assertEquals(HttpStatusCode.Accepted, client.publish(rejected).status)

        assertEquals(DecisionOutcome.Approved(StoreOutcome.STORED), queue.approve(approved.id))
        val listed = Json.decodeFromString(ListSerializer(SignedEvent.serializer()), client.get("/api/v1/events?kind=${Article.KIND}&author=${citizen.publicKey.hex}").bodyAsText())
        assertEquals(listOf(approved), listed, "published as its author signed it")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/events/${approved.id}").status)
        val approvedStatus = client.statuses(approved.id).single()
        assertEquals("approved", approvedStatus.status)
        assertNotNull(approvedStatus.decidedAt)
        assertEquals("duplicate", client.publish(approved).publishStatus(), "already published")

        assertEquals(DecisionOutcome.Rejected, queue.reject(rejected.id, "[EXEMPLE] Une revendication identique est déjà en débat."))
        val rejectedStatus = client.statuses(rejected.id).single()
        assertEquals("rejected", rejectedStatus.status)
        assertEquals("[EXEMPLE] Une revendication identique est déjà en débat.", rejectedStatus.reason)
        assertNotNull(rejectedStatus.decidedAt)
        assertNull(events.find(rejected.id), "nothing published")
        val again = client.publish(rejected)
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("rejected", again.error())
    }
}
