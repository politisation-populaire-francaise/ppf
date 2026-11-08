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
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupRoles
import org.politisationpopulairefrancaise.server.module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Roles and moderation of the rooms by their collective (D-131, docs/integration/salons-roles.md, section 2). */
class GroupModerationApiTest {

    private val creator = LocalEventSigner(IdentityKeys.generate())
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())
    private val carol = LocalEventSigner(IdentityKeys.generate())
    private val json = Json { ignoreUnknownKeys = true }
    private var clock = 1_700_000_000L

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun HttpResponse.error(): String = json.decodeFromString<ApiError>(bodyAsText()).error

    private suspend fun HttpClient.roles(id: String = ROOM): GroupRoles = json.decodeFromString(get("/api/v1/groups/$id/roles").bodyAsText())

    private suspend fun HttpClient.messages(id: String = ROOM): List<SignedEvent> =
        json.decodeFromString(get("/api/v1/events?kind=9&tag=h:$id").bodyAsText())

    private suspend fun HttpClient.journal(id: String = ROOM): List<SignedEvent> =
        json.decodeFromString(get("/api/v1/events?kind=9000&kind=9001&kind=9002&kind=9005&tag=h:$id").bodyAsText())

    private fun next(): Long = ++clock

    private suspend fun description(signer: LocalEventSigner = creator, id: String = ROOM, name: String = "[EXEMPLE] Collectif Logement", territory: String = "Lyon", about: String? = null) =
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOfNotNull(
                listOf("d", id),
                listOf("name", name),
                about?.let { listOf("about", it) },
                listOf(GroupKinds.TERRITORY_TAG, territory),
                listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
            ),
            createdAt = next(),
        )

    private suspend fun message(signer: LocalEventSigner, text: String = "[EXEMPLE] Bonjour") =
        signer.sign(kind = GroupKinds.CHAT_MESSAGE, content = text, tags = listOf(listOf("h", ROOM)), createdAt = next())

    private suspend fun putUser(by: LocalEventSigner, target: LocalEventSigner, role: String? = null) =
        by.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("h", ROOM), listOfNotNull("p", target.publicKey.hex, role)), createdAt = next())

    private suspend fun removeUser(by: LocalEventSigner, target: LocalEventSigner, reason: String = "[EXEMPLE] Propos hors charte") =
        by.sign(kind = GroupKinds.REMOVE_USER, content = reason, tags = listOf(listOf("h", ROOM), listOf("p", target.publicKey.hex)), createdAt = next())

    private suspend fun deleteEvent(by: LocalEventSigner, target: SignedEvent, reason: String = "[EXEMPLE] Hors sujet") =
        by.sign(kind = GroupKinds.DELETE_EVENT, content = reason, tags = listOf(listOf("h", ROOM), listOf("e", target.id)), createdAt = next())

    private suspend fun edit(by: LocalEventSigner, vararg tags: List<String>) =
        by.sign(kind = GroupKinds.EDIT_METADATA, tags = listOf(listOf("h", ROOM)) + tags.toList(), createdAt = next())

    @Test
    fun theCreatorIsTheFirstAdministratorAndNamesAdministratorsAndModerators() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description()).status)
        val first = client.roles()
        assertEquals(listOf(creator.publicKey.hex to "admin"), first.roles.map { it.pubkey to it.role })
        assertEquals(creator.publicKey.hex, first.roles.single().grantedBy)
        assertEquals(0, first.banned)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/groups/inconnu/roles").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/groups/Pas%20valide/roles").status)

        // A key without role names nobody.
        val usurper = client.publish(putUser(alice, alice, "admin"))
        assertEquals(HttpStatusCode.Forbidden, usurper.status)
        assertEquals("not_allowed", usurper.error())

        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, alice, "admin")).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser(alice, bob, "moderator")).status, "a new administrator names in turn")
        val roles = client.roles().roles.associate { it.pubkey to it.role }
        assertEquals(mapOf(creator.publicKey.hex to "admin", alice.publicKey.hex to "admin", bob.publicKey.hex to "moderator"), roles)
        assertEquals(alice.publicKey.hex, client.roles().roles.first { it.pubkey == bob.publicKey.hex }.grantedBy)

        // A moderator names nobody and takes no role back.
        assertEquals("not_allowed", client.publish(putUser(bob, carol, "moderator")).error())
        assertEquals("not_allowed", client.publish(putUser(bob, alice)).error())

        // An administrator takes a role back with a put-user without role.
        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, bob)).status)
        assertEquals(setOf(creator.publicKey.hex, alice.publicKey.hex), client.roles().roles.map { it.pubkey }.toSet())

        // The journal is public: every accepted event is served, refused ones are not stored.
        assertEquals(3, client.journal().size)
    }

    @Test
    fun theLastAdministratorKeepsTheRole() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description()).status)

        val leave = client.publish(putUser(creator, creator, "moderator"))
        assertEquals(HttpStatusCode.Conflict, leave.status)
        assertEquals("last_admin", leave.error())
        assertEquals("last_admin", client.publish(putUser(creator, creator)).error())
        assertEquals("last_admin", client.publish(removeUser(creator, creator)).error())

        // With a second administrator, the creator may step down; the other one is then the last.
        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, alice, "admin")).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, creator)).status)
        assertEquals(listOf(alice.publicKey.hex), client.roles().roles.map { it.pubkey })
        assertEquals("last_admin", client.publish(putUser(alice, alice)).error())
        // The creator who gave up the role no longer acts as an administrator, nor redescribes the room.
        assertEquals("not_allowed", client.publish(putUser(creator, bob, "moderator")).error())
        val redescribed = client.publish(description(about = "[EXEMPLE] Nouveau texte"))
        assertEquals(HttpStatusCode.Forbidden, redescribed.status)
        assertEquals("not_allowed", redescribed.error())
    }

    @Test
    fun anExcludedAccountCannotWriteUntilReadmitted() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description()).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, bob, "moderator")).status)
        val before = message(carol, "[EXEMPLE] Avant l'exclusion")
        assertEquals(HttpStatusCode.Created, client.publish(before).status)

        // The reason is mandatory.
        val noReason = client.publish(removeUser(bob, carol, reason = "  "))
        assertEquals(HttpStatusCode.BadRequest, noReason.status)
        assertEquals("reason_required", noReason.error())
        // A key without role excludes nobody; a moderator does not exclude an administrator.
        assertEquals("not_allowed", client.publish(removeUser(alice, carol)).error())
        assertEquals("not_allowed", client.publish(removeUser(bob, creator)).error())

        val exclusion = removeUser(bob, carol)
        assertEquals(HttpStatusCode.Created, client.publish(exclusion).status)
        assertEquals(1, client.roles().banned)
        val refused = client.publish(message(carol, "[EXEMPLE] Après"))
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("banned", refused.error())
        assertEquals(listOf(before.id), client.messages().map { it.id }, "messages already published stay")
        assertTrue(client.journal().any { it.id == exclusion.id && it.content == "[EXEMPLE] Propos hors charte" }, "the reason is public")

        // A moderator readmits (a put-user without role for a key without role).
        assertEquals(HttpStatusCode.Created, client.publish(putUser(bob, carol)).status)
        assertEquals(0, client.roles().banned)
        assertEquals(HttpStatusCode.Created, client.publish(message(carol, "[EXEMPLE] De retour")).status)

        // An administrator excluding a moderator takes the role too.
        assertEquals(HttpStatusCode.Created, client.publish(removeUser(creator, bob)).status)
        assertEquals(listOf(creator.publicKey.hex), client.roles().roles.map { it.pubkey })
        assertEquals("banned", client.publish(message(bob)).error())
    }

    @Test
    fun aRemovedMessageIsNoLongerServedButItsRemovalIs() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description()).status)
        assertEquals(HttpStatusCode.Created, client.publish(putUser(creator, bob, "moderator")).status)
        val spam = message(carol, "[EXEMPLE] Publicité")
        val mine = message(alice, "[EXEMPLE] Je me suis trompée")
        val kept = message(alice, "[EXEMPLE] Bonne nouvelle")
        listOf(spam, mine, kept).forEach { assertEquals(HttpStatusCode.Created, client.publish(it).status) }

        assertEquals("not_allowed", client.publish(deleteEvent(carol, kept)).error(), "nobody else removes a message")
        assertEquals("reason_required", client.publish(deleteEvent(bob, spam, reason = "")).error())

        val removal = deleteEvent(bob, spam)
        assertEquals(HttpStatusCode.Created, client.publish(removal).status)
        assertEquals(HttpStatusCode.Created, client.publish(deleteEvent(alice, mine, reason = "[EXEMPLE] Erreur de ma part")).status, "the author removes their own message")
        assertEquals(listOf(kept.id), client.messages().map { it.id })
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/events/${spam.id}").status)
        val served = client.journal().first { it.id == removal.id }
        assertEquals("[EXEMPLE] Hors sujet", served.content)
        assertEquals(listOf(spam.id), served.tagValues("e"))
        assertTrue(!served.toJson().contains("Publicité"), "the removal does not carry the removed text")

        // The legal register (D-077) gets one "delete" row per removal: who removed what, never the text.
        val deletions = TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT event_id, kind, pubkey, removed_event_id, removed_created_at, removed_pubkey " +
                        "FROM legal_register.content_operation WHERE operation = 'delete' ORDER BY id",
                )
                buildList { while (rows.next()) add(listOf(rows.getString(1), rows.getInt(2).toString(), rows.getString(3), rows.getString(4), rows.getLong(5).toString(), rows.getString(6))) }
            }
        }
        assertEquals(
            listOf(
                listOf(removal.id, GroupKinds.DELETE_EVENT.toString(), bob.publicKey.hex, spam.id, spam.createdAt.toString(), carol.publicKey.hex),
                listOf(deletions[1][0], GroupKinds.DELETE_EVENT.toString(), alice.publicKey.hex, mine.id, mine.createdAt.toString(), alice.publicKey.hex),
            ),
            deletions,
        )
        val register = TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT string_agg(t::text, ' ') FROM legal_register.content_operation t")
                rows.next()
                rows.getString(1).orEmpty()
            }
        }
        assertTrue(!register.contains("Publicité") && !register.contains("Hors sujet"), "the register keeps neither the text nor the reason")

        // Only a message of this room.
        assertEquals(HttpStatusCode.Created, client.publish(description(id = "autre", name = "[EXEMPLE] Autre salon")).status)
        val elsewhere = alice.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Ailleurs", tags = listOf(listOf("h", "autre")), createdAt = next())
        assertEquals(HttpStatusCode.Created, client.publish(elsewhere).status)
        assertEquals("not_a_message", client.publish(deleteEvent(creator, elsewhere)).error())
        val unknown = client.publish(creator.sign(kind = GroupKinds.DELETE_EVENT, content = "x", tags = listOf(listOf("h", ROOM), listOf("e", "0".repeat(64))), createdAt = next()))
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("event_not_found", unknown.error())
    }

    @Test
    fun anAdministratorEditsTheNameAndPresentationButNeverTheTerritoryNorTheCharter() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description(about = "[EXEMPLE] Texte d'origine")).status)
        assertEquals(HttpStatusCode.Created, client.publish(description(signer = alice, id = "transports-lyon", name = "[EXEMPLE] Transports")).status)

        assertEquals("not_allowed", client.publish(edit(alice, listOf("name", "[EXEMPLE] Pris"))).error())
        for (forbidden in listOf(listOf(GroupKinds.TERRITORY_TAG, "Paris"), listOf(GroupKinds.CHARTER_TAG, "1.0"), listOf("picture", "https://exemple.invalid/a.png"), listOf("closed"))) {
            val response = client.publish(edit(creator, listOf("name", "[EXEMPLE] Nouveau"), forbidden))
            assertEquals(HttpStatusCode.BadRequest, response.status, forbidden.toString())
            assertEquals("edit_not_allowed", response.error())
        }
        assertEquals("edit_not_allowed", client.publish(edit(creator)).error(), "an edit changes something")
        val duplicate = client.publish(edit(creator, listOf("name", "[EXEMPLE] transports !")))
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("group_exists", duplicate.error())

        val renamed = edit(creator, listOf("name", "[EXEMPLE] Collectif Logement digne"))
        assertEquals(HttpStatusCode.Created, client.publish(renamed).status)
        assertEquals(HttpStatusCode.Created, client.publish(edit(creator, listOf("about", "[EXEMPLE] Texte revu"))).status)
        val group = json.decodeFromString<Group>(client.get("/api/v1/groups/$ROOM").bodyAsText())
        assertEquals("[EXEMPLE] Collectif Logement digne", group.name, "successive edits are merged")
        assertEquals("[EXEMPLE] Texte revu", group.about)
        assertEquals("Lyon", group.territory)
        assertNotNull(group.edit)
        assertEquals(listOf(ROOM), json.decodeFromString<List<Group>>(client.get("/api/v1/groups?q=digne").bodyAsText()).map { it.id }, "the directory searches the name in force")

        // The territory of a new version of the description never changes either.
        val moved = client.publish(description(territory = "Paris"))
        assertEquals(HttpStatusCode.BadRequest, moved.status)
        assertEquals("territory_fixed", moved.error())
    }

    @Test
    fun badlyShapedModerationEventsAreRefused() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(description()).status)
        val noGroup = client.publish(creator.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("p", alice.publicKey.hex)), createdAt = next()))
        assertEquals("missing_group", noGroup.error())
        val badRole = client.publish(putUser(creator, alice, "owner"))
        assertEquals("bad_role", badRole.error())
        val twoKeys = client.publish(creator.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("h", ROOM), listOf("p", alice.publicKey.hex), listOf("p", bob.publicKey.hex)), createdAt = next()))
        assertEquals("bad_target", twoKeys.error())
        val unknownRoom = client.publish(creator.sign(kind = GroupKinds.PUT_USER, tags = listOf(listOf("h", "inconnu"), listOf("p", alice.publicKey.hex)), createdAt = next()))
        assertEquals(HttpStatusCode.NotFound, unknownRoom.status)
        assertEquals(1, client.roles().roles.size)
    }

    private companion object {
        const val ROOM = "logement-lyon"
    }
}
