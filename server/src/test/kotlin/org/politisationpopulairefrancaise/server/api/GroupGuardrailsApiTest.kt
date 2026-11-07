package org.politisationpopulairefrancaise.server.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisationTags
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.GroupCreationsQueue
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.CreationState
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupDirectory
import org.politisationpopulairefrancaise.server.groups.GroupGuardrails
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.OrganisationNames
import org.politisationpopulairefrancaise.server.module
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guardrails of the creation of a room (D-132, docs/integration/salons-roles.md, section 3 bis). */
class GroupGuardrailsApiTest {

    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val other = LocalEventSigner(IdentityKeys.generate())
    private val union = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(editor.publicKey.hex))
    private val json = Json { ignoreUnknownKeys = true }
    private val actor = TeamActor.of(UUID.randomUUID(), "Modo", setOf(TeamRole.MODERATOR), 2)
    private var clock = 1_700_000_000L

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpClient.ids(): List<String> = json.decodeFromString<List<Group>>(get("/api/v1/groups").bodyAsText()).map { it.id }

    private suspend fun room(signer: LocalEventSigner, id: String, name: String, territory: String = "Lyon", extra: List<List<String>> = emptyList()) =
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(
                listOf("d", id),
                listOf("name", name),
                listOf(GroupKinds.TERRITORY_TAG, territory),
                listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
            ) + extra,
            createdAt = ++clock,
        )

    private fun confirmed(difference: String = "[EXEMPLE] Nous parlons des hôpitaux, pas des cabinets") =
        listOf(listOf(GroupKinds.SIMILAR_TAG, GroupKinds.SIMILAR_CONFIRMED), listOf(GroupKinds.DIFFERENCE_TAG, difference))

    /** Makes [signer] known to the server for 8 days (its first event received 8 days ago). */
    private fun age(signer: LocalEventSigner) {
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE events SET received_at = now() - interval '8 days' WHERE pubkey = ?").use { statement ->
                statement.setString(1, signer.publicKey.hex)
                statement.executeUpdate()
            }
        }
    }

    @Test
    fun aCloseNameOnTheSameOrAParentTerritoryIsRefusedWithTheRoomsToJoinUnlessConfirmed() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(room(citizen, "sante-lyon", "[EXEMPLE] Collectif Santé")).status)
        assertEquals(HttpStatusCode.Created, client.publish(room(citizen, "logement-france", "[EXEMPLE] Logement digne", territory = "France")).status)

        for (name in listOf("[EXEMPLE] Santé", "[EXEMPLE] Collectif de la santé Lyon", "[EXEMPLE] Collectif Santee")) {
            val response = client.publish(room(other, "autre-sante", name))
            assertEquals(HttpStatusCode.Conflict, response.status, name)
            val body = json.decodeFromString<GroupSimilarError>(response.bodyAsText())
            assertEquals("group_similar", body.error)
            assertEquals(listOf("sante-lyon"), body.similar.map { it.id }, name)
            assertEquals("[EXEMPLE] Collectif Santé", body.similar.single().name)
            assertEquals("Lyon", body.similar.single().territory)
        }
        // A parent territory: the national rooms are compared too.
        val parent = client.publish(room(other, "logement-paris", "[EXEMPLE] Logement dignes", territory = "Paris"))
        assertEquals("group_similar", parent.error())
        assertTrue(parent.bodyAsText().contains("logement-france"))
        // Another name, or the same name in another town: fine.
        assertEquals(HttpStatusCode.Created, client.publish(room(other, "sante-paris", "[EXEMPLE] Collectif Santé", territory = "Paris")).status)

        // Confirming needs the difference; with it, the room is created and the difference is public.
        val withoutDifference = client.publish(room(other, "autre-sante", "[EXEMPLE] Santé", extra = listOf(listOf(GroupKinds.SIMILAR_TAG, GroupKinds.SIMILAR_CONFIRMED))))
        assertEquals(HttpStatusCode.BadRequest, withoutDifference.status)
        assertEquals("bad_difference", withoutDifference.error())
        val forced = room(other, "autre-sante", "[EXEMPLE] Santé", extra = confirmed())
        assertEquals(HttpStatusCode.Created, client.publish(forced).status)
        assertTrue(client.get("/api/v1/groups/autre-sante").bodyAsText().contains(GroupKinds.DIFFERENCE_TAG))
    }

    @Test
    fun anAccountCreatesAtMostThreeRoomsADay() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY) }
        listOf("Logement", "Transports", "Écoles").forEachIndexed { i, name ->
            assertEquals(HttpStatusCode.Created, client.publish(room(citizen, "salon-$i", "[EXEMPLE] $name")).status, name)
        }
        val fourth = client.publish(room(citizen, "salon-4", "[EXEMPLE] Culture"))
        assertEquals(HttpStatusCode.TooManyRequests, fourth.status)
        assertEquals("too_many_groups", fourth.error())
        // A new version of an existing room is not a new room.
        assertEquals(HttpStatusCode.Created, client.publish(room(citizen, "salon-0", "[EXEMPLE] Logement")).status)
        // Another account is not concerned.
        assertEquals(HttpStatusCode.Created, client.publish(room(other, "salon-5", "[EXEMPLE] Culture")).status)
    }

    @Test
    fun aNameCloseToAVerifiedOrganisationIsHeldForTheTeamAndRefusedToARecentAccount() = testApplication {
        application { module(TestDatabase.dataSource, policy, TEST_RELAY) }
        val entry = editor.organisation(tags = organisationTags(d = "union-locataires", member = union.publicKey.hex, title = "[EXEMPLE] Union des Locataires"))
        assertEquals(HttpStatusCode.Created, client.publish(entry).status)

        // A recent account may not use the name at all.
        val recent = client.publish(room(citizen, "union-lyon", "[EXEMPLE] Union des locataires Lyon"))
        assertEquals(HttpStatusCode.Forbidden, recent.status)
        assertEquals("account_too_recent", recent.error())

        // An older account waits for the team.
        assertEquals(HttpStatusCode.Created, client.publish(room(citizen, "velo-lyon", "[EXEMPLE] Vélo")).status)
        age(citizen)
        val held = room(citizen, "union-lyon", "[EXEMPLE] Union des locataires Lyon")
        val response = client.publish(held)
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("held", json.decodeFromString<PublishResponse>(response.bodyAsText()).status)
        assertEquals(listOf("velo-lyon"), client.ids(), "not published yet")
        val pending = json.decodeFromString<List<CreationState>>(client.get("/api/v1/group-creations?id=${held.id}").bodyAsText()).single()
        assertEquals("pending", pending.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/group-creations?id=abc").status)

        // The organisation itself, and the editorial team, create such a room at once.
        assertEquals(HttpStatusCode.Created, client.publish(room(union, "union-paris", "[EXEMPLE] Union des locataires", territory = "Paris")).status)

        // The team approves: the room is published exactly as signed.
        val directory = GroupDirectory(EventRepository(TestDatabase.dataSource), guardrails = GroupGuardrails(TestDatabase.dataSource, OrganisationNames(TestDatabase.dataSource, policy::isEditor)))
        val queue = GroupCreationsQueue(directory)
        assertEquals(1, queue.pendingCount())
        assertEquals(listOf("approve", "reject"), queue.actions(held.id).map { it.id })
        assertTrue(queue.actions(held.id).single { it.id == "reject" }.reasonRequired)
        assertEquals(ActionOutcome.Done, queue.perform(held.id, "approve", null, actor))
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform(held.id, "approve", null, actor))
        assertTrue("union-lyon" in client.ids())
        assertEquals(held, json.decodeFromString<Group>(client.get("/api/v1/groups/union-lyon").bodyAsText()).event)
        assertEquals("approved", json.decodeFromString<List<CreationState>>(client.get("/api/v1/group-creations?id=${held.id}").bodyAsText()).single().status)

        // A refusal needs a reason, which its author reads.
        val second = room(other, "union-nantes", "[EXEMPLE] Union des locataires", territory = "Nantes")
        assertEquals(HttpStatusCode.Created, client.publish(room(other, "jardins-nantes", "[EXEMPLE] Jardins partagés", territory = "Nantes")).status)
        age(other)
        assertEquals(HttpStatusCode.Accepted, client.publish(second).status)
        assertTrue(queue.perform(second.id, "reject", " ", actor) is ActionOutcome.Refused)
        assertEquals(ActionOutcome.Done, queue.perform(second.id, "reject", "[EXEMPLE] Ce salon n'est pas celui de l'organisation", actor))
        val refused = json.decodeFromString<List<CreationState>>(client.get("/api/v1/group-creations?id=${second.id}").bodyAsText()).single()
        assertEquals("rejected", refused.status)
        assertEquals("[EXEMPLE] Ce salon n'est pas celui de l'organisation", refused.reason)
        assertTrue("union-nantes" !in client.ids())

        // An administrator may not rename a room after the organisation either.
        val rename = client.publish(other.sign(kind = GroupKinds.EDIT_METADATA, tags = listOf(listOf("h", "jardins-nantes"), listOf("name", "[EXEMPLE] Union des locataires")), createdAt = ++clock))
        assertEquals(HttpStatusCode.Forbidden, rename.status)
        assertEquals("organisation_name", rename.error())
    }
}
