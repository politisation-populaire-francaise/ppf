package org.politisationpopulairefrancaise.server.agir

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.callTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.error
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.publish
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.published
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withTag
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.revendications.DemandFixtures.demand
import org.politisationpopulairefrancaise.server.revendications.ProposalQueue
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who publishes what in the Agir space, through the real API (contract, section 5, every line of the table). */
class AgirPublicationApiTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val organisationKey = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val queue = HeldQueue(TestDatabase.dataSource, events)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun ApplicationTestBuilder.start(config: AgirConfig = AgirConfig(cleanupInterval = kotlin.time.Duration.ZERO)) {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications(), testAgir(config)) }
    }

    /** The editorial team verifies [organisationKey] through the API, as in production. */
    private suspend fun HttpClient.verifyOrganisation() {
        assertEquals(HttpStatusCode.Created, publish(editor.organisation(organisationTags(member = organisationKey.publicKey.hex))).status)
    }

    private suspend fun HttpClient.assertPublished(event: SignedEvent) {
        val response = publish(event)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        assertEquals(PublishResponse(event.id, "stored"), response.published())
        assertEquals(event, events.find(event.id))
        assertNull(queue.find(event.id))
    }

    private suspend fun HttpClient.assertHeld(event: SignedEvent) {
        val response = publish(event)
        assertEquals(HttpStatusCode.Accepted, response.status, response.bodyAsText())
        // The same body as for a citizen demand.
        assertEquals(PublishResponse(event.id, "pending_review"), response.published())
        assertNull(events.find(event.id))
        assertEquals(HeldStatus.PENDING, queue.find(event.id)?.status)
    }

    private suspend fun HttpClient.assertRefused(event: SignedEvent, status: HttpStatusCode, error: String) {
        val response = publish(event)
        assertEquals(status, response.status, response.bodyAsText())
        assertEquals(error, response.error().error)
        assertNull(events.find(event.id))
        assertNull(queue.find(event.id))
    }

    private suspend fun HttpClient.held(vararg ids: String): List<HeldState> {
        val response = get("/api/v1/held?" + ids.joinToString("&") { "id=$it" })
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        return Json.decodeFromString(ListSerializer(HeldState.serializer()), response.bodyAsText())
    }

    @Test
    fun mobilizationsArePublishedByTrustedKeysAndHeldOtherwise() = testApplication {
        start()
        client.verifyOrganisation()
        client.assertPublished(editor.mobilization())
        client.assertPublished(organisationKey.mobilization())
        client.assertHeld(citizen.mobilization())
    }

    @Test
    fun callsArePublishedByTrustedKeysAndHeldOtherwise() = testApplication {
        // One citizen sends a call of every type: more than the default limits per key.
        start(AgirConfig(maxPendingPerKey = 50, perKeyPerDay = 50, cleanupInterval = kotlin.time.Duration.ZERO))
        client.verifyOrganisation()
        val ordinary = AgirFormats.CALL_TYPES - AgirFormats.CALL_TYPE_WITNESSES - AgirFormats.CALL_TYPE_FUNDRAISING
        for (type in ordinary) {
            client.assertPublished(editor.call(type))
            client.assertPublished(organisationKey.call(type))
            client.assertHeld(citizen.call(type))
        }
    }

    @Test
    fun callsForWitnessesAreAlwaysHeld() = testApplication {
        start()
        client.verifyOrganisation()
        for (author in listOf(editor, organisationKey, citizen)) client.assertHeld(author.call(AgirFormats.CALL_TYPE_WITNESSES))
    }

    @Test
    fun onlyVerifiedOrganisationsCallForFunds() = testApplication {
        start()
        client.assertRefused(organisationKey.call(AgirFormats.CALL_TYPE_FUNDRAISING), HttpStatusCode.Forbidden, "not_allowed")
        client.verifyOrganisation()
        client.assertPublished(organisationKey.call(AgirFormats.CALL_TYPE_FUNDRAISING))
        // An editorial key is not an organisation (point of attention 12), and a citizen even less.
        client.assertRefused(editor.call(AgirFormats.CALL_TYPE_FUNDRAISING), HttpStatusCode.Forbidden, "not_allowed")
        client.assertRefused(citizen.call(AgirFormats.CALL_TYPE_FUNDRAISING), HttpStatusCode.Forbidden, "not_allowed")
        // The shape comes first, whoever sends it.
        val incomplete = citizen.call(AgirFormats.CALL_TYPE_FUNDRAISING, callTags(AgirFormats.CALL_TYPE_FUNDRAISING).filterNot { it[0] == "ppf-beneficiary" })
        client.assertRefused(incomplete, HttpStatusCode.BadRequest, "missing_field")
    }

    @Test
    fun onlyEditorsPublishTheDirectory() = testApplication {
        start()
        client.assertPublished(editor.organisation())
        client.assertRefused(citizen.organisation(), HttpStatusCode.Forbidden, "not_allowed")
        client.assertRefused(organisationKey.organisation(organisationTags(d = "moi-meme", member = organisationKey.publicKey.hex)), HttpStatusCode.Forbidden, "not_allowed")
    }

    @Test
    fun shapeErrorsAreAnsweredBeforeAnythingIsStored() = testApplication {
        start()
        val response = client.publish(citizen.mobilization(mobilizationTags().withTag(AgirFormats.TAG_EVENT_TYPE, "emeute")))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("bad_event_type", response.error().error)
        val missing = client.publish(citizen.call("soutenir", callTags("soutenir").filterNot { it[0] == "ppf-cause" }))
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("missing_field", missing.error().error)
        assertEquals("ppf-cause", missing.error().message)
        assertEquals(0, events.count())
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun aHeldContentNamingAGroupNeedsAnOpenGroup() = testApplication {
        start()
        val toUnknownRoom = citizen.mobilization(mobilizationTags() + listOf(listOf(GroupKinds.GROUP_TAG, "salon-inconnu")))
        client.assertRefused(toUnknownRoom, HttpStatusCode.NotFound, "group_not_found")
    }

    @Test
    fun theAuthorFollowsHeldContentsWithoutSeeingAnythingElse() = testApplication {
        start()
        val first = citizen.mobilization()
        val second = citizen.call("debattre")
        client.assertHeld(first)
        client.assertHeld(second)
        // Sending the same event again costs nothing and says the same.
        val again = client.publish(first)
        assertEquals(HttpStatusCode.Accepted, again.status)
        assertEquals("pending_review", again.published().status)

        val unknown = "0".repeat(64)
        val states = client.held(second.id, unknown, first.id, second.id)
        assertEquals(listOf(second.id, first.id), states.map { it.id })
        assertTrue(states.all { it.status == HeldStatus.PENDING && it.decidedAt == null && it.reason == null })
        // Never the key nor the content: exactly the five fields of the contract.
        val raw = client.get("/api/v1/held?id=${first.id}").bodyAsText()
        assertEquals(setOf("id", "status", "received_at", "decided_at", "reason"), Json.parseToJsonElement(raw).let { it as kotlinx.serialization.json.JsonArray }.single().jsonObject.keys)
        assertFalse(raw.contains(citizen.publicKey.hex))
        assertFalse(raw.contains("EXEMPLE"))

        assertTrue(queue.reject(second.id, "[EXEMPLE] Hors charte : précisez l'objet.") is HeldDecision.Rejected)
        assertTrue(queue.approve(first.id) is HeldDecision.Approved)
        val decided = client.held(first.id, second.id).associateBy { it.id }
        assertEquals(HeldStatus.APPROVED, decided.getValue(first.id).status)
        assertNotNull(decided.getValue(first.id).decidedAt)
        assertEquals(HeldStatus.REJECTED, decided.getValue(second.id).status)
        assertEquals("[EXEMPLE] Hors charte : précisez l'objet.", decided.getValue(second.id).reason)
        // Approved: published exactly as signed, and served by the generic API.
        val listed = Json.decodeFromString(ListSerializer(SignedEvent.serializer()), client.get("/api/v1/events?kind=${AgirFormats.KIND_MOBILIZATION}").bodyAsText())
        assertEquals(listOf(first), listed)
        // Rejected: the same version again is refused with 409, a corrected one waits again.
        val resent = client.publish(second)
        assertEquals(HttpStatusCode.Conflict, resent.status)
        assertEquals("rejected", resent.error().error)
        val corrected = citizen.call("debattre", createdAt = second.createdAt + 1)
        client.assertHeld(corrected)
        // Approved: the same event again is a duplicate.
        val replay = client.publish(first)
        assertEquals(HttpStatusCode.OK, replay.status)
        assertEquals("duplicate", replay.published().status)
    }

    @Test
    fun theStatusRouteChecksItsParameters() = testApplication {
        start()
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/held").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/held?id=xyz").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/held?id=${"A".repeat(64)}").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/held?" + (1..21).joinToString("&") { "id=${it.toString().padStart(64, '0')}" }).status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/held?" + (1..20).joinToString("&") { "id=${it.toString().padStart(64, '0')}" }).status)
        assertEquals("bad_parameter", client.get("/api/v1/held").error().error)
    }

    @Test
    fun heldContentsHaveTheirLimits() = testApplication {
        start(AgirConfig(maxPendingPerKey = 2, perKeyPerDay = 3, maxPending = 3, cleanupInterval = kotlin.time.Duration.ZERO))
        client.assertHeld(citizen.mobilization(mobilizationTags(d = "un")))
        client.assertHeld(citizen.mobilization(mobilizationTags(d = "deux")))
        val third = client.publish(citizen.mobilization(mobilizationTags(d = "trois")))
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        assertEquals("too_many_pending", third.error().error)
        assertEquals("3600", third.headers[HttpHeaders.RetryAfter])
        // A new version replaces the pending one and does not count twice, but it spends the daily budget.
        client.assertHeld(citizen.mobilization(mobilizationTags(d = "un"), createdAt = AgirFixtures.nowSeconds()))
        val overBudget = client.publish(citizen.mobilization(mobilizationTags(d = "deux"), createdAt = AgirFixtures.nowSeconds() + 1))
        assertEquals(HttpStatusCode.TooManyRequests, overBudget.status)
        assertEquals("daily_limit", overBudget.error().error)
        assertNotNull(overBudget.headers[HttpHeaders.RetryAfter])
        // The whole queue: keys are free to mint.
        client.assertHeld(LocalEventSigner(IdentityKeys.generate()).mobilization())
        val full = client.publish(LocalEventSigner(IdentityKeys.generate()).mobilization())
        assertEquals(HttpStatusCode.TooManyRequests, full.status)
        assertEquals("queue_full", full.error().error)
        assertEquals(3, queue.pending().size)
    }

    @Test
    fun demandsAndRoomsKeepTheirBehaviour() = testApplication {
        start()
        // A citizen demand still goes to its own queue, with its own report.
        val demand = citizen.demand()
        val held = client.publish(demand)
        assertEquals(HttpStatusCode.Accepted, held.status)
        assertNotNull(ProposalQueue(TestDatabase.dataSource, events).find(demand.id))
        assertNull(queue.find(demand.id))
        // A practical guide stays reserved to the team.
        val fiche = listOf(listOf("d", "fiche"), listOf("title", "[EXEMPLE] Fiche"), listOf("ppf-type", "fiche-pratique"), listOf("ppf-origin", "human"))
        assertEquals(HttpStatusCode.Forbidden, client.publish(citizen.sign(kind = Article.KIND, content = "x", tags = fiche)).status)
        assertEquals(HttpStatusCode.Created, client.publish(editor.sign(kind = Article.KIND, content = "x", tags = fiche)).status)
        // Any account still creates a room.
        val room = citizen.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(listOf("d", "salon-agir"), listOf(GroupKinds.NAME_TAG, "[EXEMPLE] Salon"), listOf(GroupKinds.TERRITORY_TAG, "Lyon"), listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION)),
        )
        assertEquals(HttpStatusCode.Created, client.publish(room).status)
    }
}
