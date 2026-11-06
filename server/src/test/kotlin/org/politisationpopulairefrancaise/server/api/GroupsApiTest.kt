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
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupsApiTest {

    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun metadata(signer: LocalEventSigner, id: String, name: String, flags: List<String> = emptyList()): SignedEvent =
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(listOf("d", id), listOf("name", name), listOf("about", "[EXEMPLE] Un salon de démonstration")) + flags.map { listOf(it) },
            createdAt = 1_700_000_000,
        )

    @Test
    fun describesListsAndServesOpenGroupsAndTheirMessages() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex))) }

        val fromCitizen = client.publish(metadata(citizen, "demo", "[EXEMPLE] Faux salon"))
        assertEquals(HttpStatusCode.Forbidden, fromCitizen.status)
        assertEquals("not_allowed", fromCitizen.error())
        assertEquals("[]", client.get("/api/v1/groups").bodyAsText())

        val description = metadata(editor, "demo", "[EXEMPLE] Collectif")
        assertEquals(HttpStatusCode.Created, client.publish(description).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(editor, "ferme", "[EXEMPLE] Fermé", flags = listOf("closed"))).status)

        val groups = json.decodeFromString<List<Group>>(client.get("/api/v1/groups").bodyAsText())
        assertEquals(listOf("demo", "ferme"), groups.map { it.id })
        assertEquals(Group("demo", "[EXEMPLE] Collectif", "[EXEMPLE] Un salon de démonstration", null, isPublic = true, isOpen = true, event = description), groups[0])
        assertTrue(client.get("/api/v1/groups").bodyAsText().contains("\"public\":true"), "JSON field names follow the tags")
        val one = client.get("/api/v1/groups/demo")
        assertEquals(HttpStatusCode.OK, one.status)
        assertEquals(groups[0], json.decodeFromString<Group>(one.bodyAsText()))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/groups/inconnu").status)
        assertEquals("group_not_found", client.get("/api/v1/groups/inconnu").error())
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/groups/pas%20valide").status)

        val hello = citizen.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Bonjour à toutes et tous", tags = listOf(listOf("h", "demo")), createdAt = 1_700_000_100)
        val reply = editor.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Bienvenue", tags = listOf(listOf("h", "demo")), createdAt = 1_700_000_200)
        assertEquals(HttpStatusCode.Created, client.publish(hello).status)
        assertEquals(HttpStatusCode.Created, client.publish(reply).status)
        val messages = json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events?kind=9&tag=h:demo").bodyAsText())
        assertEquals(listOf(reply, hello), messages)
        assertEquals("[]", client.get("/api/v1/events?kind=9&tag=h:ferme").bodyAsText())

        val toClosed = client.publish(citizen.sign(kind = GroupKinds.CHAT_MESSAGE, content = "x", tags = listOf(listOf("h", "ferme")), createdAt = 1))
        assertEquals(HttpStatusCode.Forbidden, toClosed.status)
        assertEquals("group_closed", toClosed.error())
        val toUnknown = client.publish(citizen.sign(kind = GroupKinds.CHAT_MESSAGE, content = "x", tags = listOf(listOf("h", "inconnu")), createdAt = 1))
        assertEquals(HttpStatusCode.NotFound, toUnknown.status)
        assertEquals("group_not_found", toUnknown.error())
        val withoutGroup = client.publish(citizen.sign(kind = GroupKinds.CHAT_MESSAGE, content = "x", createdAt = 1))
        assertEquals(HttpStatusCode.BadRequest, withoutGroup.status)
        assertEquals("missing_group", withoutGroup.error())
    }

    @Test
    fun refusesMemberListsMembershipEventsAndForeignRedefinitions() = testApplication {
        val otherEditor = LocalEventSigner(IdentityKeys.generate())
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex, otherEditor.publicKey.hex))) }
        assertEquals(HttpStatusCode.Created, client.publish(metadata(editor, "demo", "[EXEMPLE] Collectif")).status)

        val members = client.publish(editor.sign(kind = GroupKinds.MEMBERS, tags = listOf(listOf("d", "demo"), listOf("p", citizen.publicKey.hex)), createdAt = 1))
        assertEquals(HttpStatusCode.Forbidden, members.status)
        assertEquals("members_list_refused", members.error())

        for (kind in listOf(9000, 9001, 9002, 9005, 9020, GroupKinds.JOIN_REQUEST, GroupKinds.LEAVE_REQUEST)) {
            val response = client.publish(citizen.sign(kind = kind, tags = listOf(listOf("h", "demo")), createdAt = 1))
            assertEquals(HttpStatusCode.BadRequest, response.status, "kind $kind")
            assertEquals("unsupported_kind", response.error())
        }

        val taken = client.publish(metadata(otherEditor, "demo", "[EXEMPLE] Usurpation"))
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("group_taken", taken.error())
        val badId = client.publish(metadata(editor, "pas valide", "[EXEMPLE] Mauvais identifiant"))
        assertEquals(HttpStatusCode.BadRequest, badId.status)
        assertEquals("bad_group_id", badId.error())

        // Nothing of all that was stored.
        assertEquals(1, json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events").bodyAsText()).size)
    }
}
