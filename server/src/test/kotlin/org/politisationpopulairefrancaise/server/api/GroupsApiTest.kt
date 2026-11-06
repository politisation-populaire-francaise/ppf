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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupsApiTest {

    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val otherCitizen = LocalEventSigner(IdentityKeys.generate())
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpClient.messagesOf(groupId: String): List<SignedEvent> =
        json.decodeFromString<List<SignedEvent>>(get("/api/v1/events?kind=9&tag=h:$groupId").bodyAsText())

    private suspend fun HttpClient.groups(query: String = ""): List<Group> =
        json.decodeFromString<List<Group>>(get("/api/v1/groups$query").bodyAsText())

    private suspend fun metadata(
        signer: LocalEventSigner,
        id: String,
        name: String,
        flags: List<String> = emptyList(),
        territory: String? = "Lyon",
        charter: String? = GroupKinds.CHARTER_VERSION,
        about: String? = "[EXEMPLE] Un salon de démonstration",
        createdAt: Long = 1_700_000_000,
    ): SignedEvent = signer.sign(
        kind = GroupKinds.METADATA,
        tags = listOfNotNull(
            listOf("d", id),
            listOf("name", name),
            about?.let { listOf("about", it) },
            territory?.let { listOf(GroupKinds.TERRITORY_TAG, it) },
            charter?.let { listOf(GroupKinds.CHARTER_TAG, it) },
        ) + flags.map { listOf(it) },
        createdAt = createdAt,
    )

    private suspend fun message(signer: LocalEventSigner, content: String, vararg groupIds: String, createdAt: Long = 1): SignedEvent =
        signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = content, tags = groupIds.map { listOf("h", it) }, createdAt = createdAt)

    @Test
    fun anyAccountCreatesAGroupWhichIsListedServedAndOpenToMessages() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals("[]", client.get("/api/v1/groups").bodyAsText())

        val description = metadata(citizen, "demo", "[EXEMPLE] Collectif")
        assertEquals(HttpStatusCode.Created, client.publish(description).status, "no editor key needed")
        assertEquals(HttpStatusCode.Created, client.publish(metadata(otherCitizen, "ferme", "[EXEMPLE] Fermé", flags = listOf("closed"), territory = "Paris")).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "ecole", "[EXEMPLE] École", territory = "Nantes")).status)

        val groups = client.groups()
        assertEquals(listOf("demo", "ecole", "ferme"), groups.map { it.id }, "sorted by name, accents and case ignored")
        assertEquals(
            Group("demo", "[EXEMPLE] Collectif", "[EXEMPLE] Un salon de démonstration", null, territory = "Lyon", charter = "1.0", isPublic = true, isOpen = true, event = description),
            groups[0],
        )
        val raw = client.get("/api/v1/groups").bodyAsText()
        assertTrue(raw.contains("\"public\":true"), "JSON field names follow the tags")
        assertTrue(raw.contains("\"territory\":\"Lyon\"") && raw.contains("\"charter\":\"1.0\""), raw)
        val one = client.get("/api/v1/groups/demo")
        assertEquals(HttpStatusCode.OK, one.status)
        assertEquals(groups[0], json.decodeFromString<Group>(one.bodyAsText()))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/groups/inconnu").status)
        assertEquals("group_not_found", client.get("/api/v1/groups/inconnu").error())
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/groups/pas%20valide").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/groups/Demo").status, "ids are lowercase")

        val hello = message(otherCitizen, "[EXEMPLE] Bonjour à toutes et tous", "demo", createdAt = 1_700_000_100)
        val reply = message(citizen, "[EXEMPLE] Bienvenue", "demo", createdAt = 1_700_000_200)
        assertEquals(HttpStatusCode.Created, client.publish(hello).status)
        assertEquals(HttpStatusCode.Created, client.publish(reply).status)
        assertEquals(listOf(reply, hello), client.messagesOf("demo"))
        assertEquals(emptyList(), client.messagesOf("ferme"))

        val toClosed = client.publish(message(citizen, "x", "ferme"))
        assertEquals(HttpStatusCode.Forbidden, toClosed.status)
        assertEquals("group_closed", toClosed.error())
        val toUnknown = client.publish(message(citizen, "x", "inconnu"))
        assertEquals(HttpStatusCode.NotFound, toUnknown.status)
        assertEquals("group_not_found", toUnknown.error())
        val withoutGroup = client.publish(message(citizen, "x"))
        assertEquals(HttpStatusCode.BadRequest, withoutGroup.status)
        assertEquals("missing_group", withoutGroup.error())
    }

    @Test
    fun aDescriptionNeedsANameATerritoryAndTheCharter() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }

        val noCharter = client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif", charter = null))
        assertEquals(HttpStatusCode.BadRequest, noCharter.status)
        assertEquals("missing_charter", noCharter.error())
        val oldCharter = client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif", charter = "0.9"))
        assertEquals("missing_charter", oldCharter.error())
        val noTerritory = client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif", territory = null))
        assertEquals(HttpStatusCode.BadRequest, noTerritory.status)
        assertEquals("missing_territory", noTerritory.error())
        val blankTerritory = client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif", territory = "  "))
        assertEquals("missing_territory", blankTerritory.error())
        val noName = client.publish(metadata(citizen, "demo", ""))
        assertEquals(HttpStatusCode.BadRequest, noName.status)
        assertEquals("missing_name", noName.error())
        assertEquals("[]", client.get("/api/v1/groups").bodyAsText(), "nothing was stored")

        // about and picture are optional.
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif", about = null)).status)
        assertNull(client.groups().single().about)
    }

    @Test
    fun aSecondGroupWithTheSameNameOnTheSameTerritoryIsRefusedWithTheOneToJoin() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon")).status)

        val duplicate = client.publish(metadata(otherCitizen, "autre", "[EXEMPLE] collectif éducation !", territory = "LYON"))
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val body = json.decodeFromString<GroupExistsError>(duplicate.bodyAsText())
        assertEquals("group_exists", body.error)
        assertEquals("education-lyon", body.existing)
        assertTrue(duplicate.bodyAsText().contains("\"existing\":\"education-lyon\""), duplicate.bodyAsText())
        assertTrue(body.message.isNotBlank())

        // Same name on another territory, another name on the same territory: both fine.
        assertEquals(HttpStatusCode.Created, client.publish(metadata(otherCitizen, "education-paris", "[EXEMPLE] Collectif Éducation", territory = "Paris")).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(otherCitizen, "logement-lyon", "[EXEMPLE] Collectif Logement", territory = "Lyon")).status)

        // The creator republishes its description: replaced, not duplicated.
        val edited = metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon", about = "[EXEMPLE] Texte revu", createdAt = 1_700_000_500)
        assertEquals(HttpStatusCode.Created, client.publish(edited).status)
        assertEquals("[EXEMPLE] Texte revu", json.decodeFromString<Group>(client.get("/api/v1/groups/education-lyon").bodyAsText()).about)
        // ...but may not take the name of another group of the same territory.
        val renamed = client.publish(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Logement", territory = "Lyon", createdAt = 1_700_000_600))
        assertEquals(HttpStatusCode.Conflict, renamed.status)
        assertEquals("logement-lyon", json.decodeFromString<GroupExistsError>(renamed.bodyAsText()).existing)
        assertEquals(listOf("education-lyon", "education-paris", "logement-lyon"), client.groups().map { it.id })
        assertEquals("[EXEMPLE] Texte revu", json.decodeFromString<Group>(client.get("/api/v1/groups/education-lyon").bodyAsText()).about, "the refused rename changed nothing")
    }

    @Test
    fun searchesRoomsByTextTerritoryAndLimitKeepingTheEntryShape() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "education-lyon", "[EXEMPLE] Collectif Éducation", territory = "Lyon", about = "[EXEMPLE] Écoles du 7e")).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "education-paris", "[EXEMPLE] Collectif Éducation", territory = "Paris", about = null)).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(otherCitizen, "logement-lyon", "[EXEMPLE] Logement digne", territory = "LYON", about = null)).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(otherCitizen, "transports", "[EXEMPLE] Transports", territory = "Saint-Étienne", about = null)).status)

        assertEquals(listOf("education-lyon", "education-paris", "logement-lyon", "transports"), client.groups().map { it.id })
        assertEquals(listOf("education-lyon", "education-paris"), client.groups("?q=%C3%A9ducation").map { it.id }, "q=éducation")
        assertEquals(listOf("education-lyon", "logement-lyon"), client.groups("?territory=lyon").map { it.id })
        assertEquals(listOf("education-lyon"), client.groups("?q=Education&territory=Lyon").map { it.id })
        assertEquals(listOf("education-lyon"), client.groups("?q=ecoles").map { it.id }, "the description is searched")
        assertEquals(listOf("transports"), client.groups("?q=etienne").map { it.id }, "the territory is searched")
        assertEquals(listOf("transports"), client.groups("?territory=saint+etienne").map { it.id })
        assertEquals(emptyList(), client.groups("?territory=Lyon%203e"), "a territory is compared whole")
        assertEquals(4, client.groups("?q=&territory=").size, "empty parameters do not filter")
        assertEquals(listOf("education-lyon", "education-paris"), client.groups("?limit=2").map { it.id })
        assertEquals(4, client.groups("?limit=500").size)

        // Each entry keeps the shape of the listing, with the territory and the charter added.
        val entry = Json.parseToJsonElement(client.get("/api/v1/groups?territory=paris").bodyAsText()).jsonArray.single().jsonObject
        assertEquals(listOf("id", "name", "about", "picture", "territory", "charter", "public", "open", "event"), entry.keys.toList())
        assertEquals("Paris", entry["territory"]!!.jsonPrimitive.content)
        assertEquals("1.0", entry["charter"]!!.jsonPrimitive.content)
    }

    @Test
    fun badSearchParametersAreRefused() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val long = "a".repeat(MAX_GROUP_QUERY_LENGTH + 1)
        for (query in listOf("limit=0", "limit=501", "limit=-1", "limit=abc", "limit=", "limit=1&limit=2", "q=a&q=b", "territory=a&territory=b", "q=$long", "territory=$long")) {
            val response = client.get("/api/v1/groups?$query")
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals("bad_parameter", response.error(), query)
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/groups?q=${"a".repeat(MAX_GROUP_QUERY_LENGTH)}&limit=1").status)
    }

    @Test
    fun aDescriptionStaysSmall() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val longName = client.publish(metadata(citizen, "long", "[EXEMPLE] " + "n".repeat(GroupKinds.MAX_NAME_LENGTH)))
        assertEquals(HttpStatusCode.BadRequest, longName.status)
        assertEquals("description_too_long", longName.error())
        assertEquals("description_too_long", client.publish(metadata(citizen, "long", "[EXEMPLE] Nom", territory = "t".repeat(GroupKinds.MAX_TERRITORY_LENGTH + 1))).error())
        assertEquals("description_too_long", client.publish(metadata(citizen, "long", "[EXEMPLE] Nom", about = "a".repeat(GroupKinds.MAX_DESCRIPTION_BYTES))).error())
        assertEquals("[]", client.get("/api/v1/groups").bodyAsText())
        // At the bounds, it passes.
        val atBounds = metadata(citizen, "large", "[EXEMPLE] " + "n".repeat(GroupKinds.MAX_NAME_LENGTH - 10), territory = "t".repeat(GroupKinds.MAX_TERRITORY_LENGTH), about = "[EXEMPLE] " + "a".repeat(6_000))
        assertEquals(HttpStatusCode.Created, client.publish(atBounds).status)
    }

    @Test
    fun theSameRoomCreatedAtTheSameInstantByEightAccountsExistsOnce() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val descriptions = List(8) { i -> metadata(LocalEventSigner(IdentityKeys.generate()), "salon-$i", "[EXEMPLE] Collectif Éducation", territory = "Lyon") }
        val responses = coroutineScope { descriptions.map { async(Dispatchers.IO) { client.publish(it) } }.awaitAll() }

        assertEquals(1, responses.count { it.status == HttpStatusCode.Created }, responses.map { it.status }.toString())
        val winner = client.groups().single().id
        val refused = responses.filter { it.status == HttpStatusCode.Conflict }.map { json.decodeFromString<GroupExistsError>(it.bodyAsText()) }
        assertEquals(7, refused.size)
        assertTrue(refused.all { it.error == "group_exists" && it.existing == winner }, refused.toString())
    }

    @Test
    fun aMessageNamesOneGroupSoAClosedFeedCannotBeEnteredThroughAnOpenOne() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "demo", "[EXEMPLE] Ouvert")).status)
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "ferme", "[EXEMPLE] Fermé", flags = listOf("closed"))).status)

        val smuggled = client.publish(message(otherCitizen, "[EXEMPLE] contrebande", "demo", "ferme"))
        assertEquals(HttpStatusCode.BadRequest, smuggled.status)
        assertEquals("multiple_groups", smuggled.error())
        assertEquals(emptyList(), client.messagesOf("ferme"))
        assertEquals(emptyList(), client.messagesOf("demo"))
        // Any kind sent to several groups is refused the same way.
        val note = client.publish(otherCitizen.sign(kind = 1, content = "x", tags = listOf(listOf("h", "demo"), listOf("h", "ferme")), createdAt = 1))
        assertEquals("multiple_groups", note.error())
    }

    @Test
    fun refusesMemberListsMembershipEventsPrivateGroupsForeignRedefinitionsAndCompanionsFromCitizens() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(metadata(citizen, "demo", "[EXEMPLE] Collectif")).status)

        val members = client.publish(editor.sign(kind = GroupKinds.MEMBERS, tags = listOf(listOf("d", "demo"), listOf("p", citizen.publicKey.hex)), createdAt = 1))
        assertEquals(HttpStatusCode.Forbidden, members.status)
        assertEquals("members_list_refused", members.error())

        for (kind in listOf(9000, 9001, 9002, 9005, 9020, GroupKinds.JOIN_REQUEST, GroupKinds.LEAVE_REQUEST)) {
            val response = client.publish(citizen.sign(kind = kind, tags = listOf(listOf("h", "demo")), createdAt = 1))
            assertEquals(HttpStatusCode.BadRequest, response.status, "kind $kind")
            assertEquals("unsupported_kind", response.error())
        }

        // Admins and roles stay reserved to the editorial keys, and to the managing key of the group.
        val adminsFromCreator = client.publish(citizen.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo"), listOf("p", citizen.publicKey.hex, "admin")), createdAt = 1))
        assertEquals(HttpStatusCode.Forbidden, adminsFromCreator.status)
        assertEquals("not_allowed", adminsFromCreator.error())
        val adminsFromEditor = client.publish(editor.sign(kind = GroupKinds.ADMINS, tags = listOf(listOf("d", "demo")), createdAt = 1))
        assertEquals(HttpStatusCode.Forbidden, adminsFromEditor.status, "the editor does not manage this group")

        val taken = client.publish(metadata(otherCitizen, "demo", "[EXEMPLE] Usurpation", territory = "Paris"))
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("group_taken", taken.error())
        assertTrue(!taken.bodyAsText().contains("existing"), "group_taken names no group to join")
        // Nor through a second d tag hidden behind a free id.
        val twoIds = otherCitizen.sign(
            kind = GroupKinds.METADATA,
            tags = listOf(listOf("d", "libre"), listOf("d", "demo"), listOf("name", "[EXEMPLE] Double"), listOf(GroupKinds.TERRITORY_TAG, "Lyon"), listOf(GroupKinds.CHARTER_TAG, "1.0")),
            createdAt = 1,
        )
        val hijack = client.publish(twoIds)
        assertEquals(HttpStatusCode.BadRequest, hijack.status)
        assertEquals("bad_group_id", hijack.error())
        assertEquals("[EXEMPLE] Collectif", json.decodeFromString<Group>(client.get("/api/v1/groups/demo").bodyAsText()).name)

        for (badId in listOf("pas valide", "Demo", "", "x".repeat(65))) {
            val response = client.publish(metadata(citizen, badId, "[EXEMPLE] Mauvais identifiant"))
            assertEquals(HttpStatusCode.BadRequest, response.status, badId)
            assertEquals("bad_group_id", response.error())
        }

        for (flag in listOf("private", "hidden")) {
            val response = client.publish(metadata(citizen, "secret", "[EXEMPLE] Secret", flags = listOf(flag)))
            assertEquals(HttpStatusCode.BadRequest, response.status, flag)
            assertEquals("unsupported_group_flag", response.error())
        }

        // Nothing of all that was stored.
        assertEquals(1, json.decodeFromString<List<SignedEvent>>(client.get("/api/v1/events").bodyAsText()).size)
        assertEquals(listOf("demo"), client.groups().map { it.id })
    }
}
