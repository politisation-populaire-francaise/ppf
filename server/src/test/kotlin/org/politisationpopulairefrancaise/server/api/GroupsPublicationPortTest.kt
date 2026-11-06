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
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.groups.Group
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.relay.MailboxSettings
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import org.politisationpopulairefrancaise.server.relay.WrapRetention
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.days

/**
 * The rooms of collectives and the mailbox setting (D-028) end to end: the application-side adapter
 * of shared/core talking to the real server code, as the app does.
 */
class GroupsPublicationPortTest {

    private val creator = LocalEventSigner(IdentityKeys.generate())
    private val latecomer = LocalEventSigner(IdentityKeys.generate())
    private val member = LocalEventSigner(IdentityKeys.generate())
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun room(signer: LocalEventSigner, id: String, name: String, territory: String, about: String? = null, createdAt: Long = 1_700_000_000): SignedEvent =
        signer.sign(
            kind = GroupKinds.METADATA,
            tags = listOfNotNull(
                listOf("d", id),
                listOf(GroupKinds.NAME_TAG, name),
                about?.let { listOf(GroupKinds.ABOUT_TAG, it) },
                listOf(GroupKinds.TERRITORY_TAG, territory),
                listOf(GroupKinds.CHARTER_TAG, GroupKinds.CHARTER_VERSION),
            ),
            createdAt = createdAt,
        )

    private suspend fun HttpClient.publishRaw(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    /** The refusal of [result] is definitive (not a signature problem, nothing to retry). */
    private fun assertRefused(result: PublicationResult, event: SignedEvent) {
        val rejected = assertIs<PublicationResult.Rejected>(result)
        assertEquals(event.id, rejected.id)
        assertNotEquals(RejectionReason.INVALID_SIGNATURE, rejected.reason)
    }

    @Test
    fun aCitizenCreatesARoomAnotherIsSentToItAndMembersTalkThere() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val port = ServerPublicationPort(client, baseUrl = "")

        // Any account creates a room, without editor key.
        val created = room(creator, "education-lyon", "[EXEMPLE] Collectif Éducation", "Lyon", about = "[EXEMPLE] Écoles publiques")
        assertEquals(PublicationResult.Accepted(created.id), port.publish(created))
        assertEquals(PublicationResult.Accepted(created.id), port.publish(created), "a replay is accepted again")

        // The same room under another spelling, on the same territory: refused, with the room to join.
        val duplicate = room(latecomer, "educ-lyon", "[EXEMPLE] collectif education !", "LYON")
        assertRefused(port.publish(duplicate), duplicate)
        val answer = client.publishRaw(duplicate)
        assertEquals(HttpStatusCode.Conflict, answer.status)
        val body = json.decodeFromString<GroupExistsError>(answer.bodyAsText())
        assertEquals("group_exists", body.error)
        assertEquals("education-lyon", body.existing)

        // The id of the room belongs to its creator.
        val squat = room(latecomer, "education-lyon", "[EXEMPLE] Autre chose", "Paris")
        assertRefused(port.publish(squat), squat)
        assertEquals("group_taken", json.decodeFromString<ApiError>(client.publishRaw(squat).bodyAsText()).error)

        // A room without the charter, or without territory, is refused.
        val noCharter = latecomer.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "sans-charte"), listOf("name", "[EXEMPLE] Sans charte"), listOf(GroupKinds.TERRITORY_TAG, "Lyon")), createdAt = 1)
        assertRefused(port.publish(noCharter), noCharter)
        val noTerritory = latecomer.sign(kind = GroupKinds.METADATA, tags = listOf(listOf("d", "sans-lieu"), listOf("name", "[EXEMPLE] Sans lieu"), listOf(GroupKinds.CHARTER_TAG, "1.0")), createdAt = 1)
        assertRefused(port.publish(noTerritory), noTerritory)

        // The latecomer finds the room by searching, then joins it (on the device) and writes there.
        val found = json.decodeFromString<List<Group>>(client.get("/api/v1/groups?q=education&territory=lyon&limit=10").bodyAsText())
        assertEquals(listOf("education-lyon"), found.map { it.id })
        assertEquals(created, found.single().event, "the signed description comes with the entry")
        val hello = latecomer.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Bonjour, je rejoins le collectif", tags = listOf(listOf("h", "education-lyon")), createdAt = 1_700_000_100)
        val reply = member.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] Bienvenue", tags = listOf(listOf("h", "education-lyon")), createdAt = 1_700_000_200)
        assertEquals(PublicationResult.Accepted(hello.id), port.publish(hello))
        assertEquals(PublicationResult.Accepted(reply.id), port.publish(reply))
        assertEquals(
            FetchResult.Events(listOf(reply, hello)),
            port.fetchByTags(listOf(TagFilter("h", "education-lyon")), RemoteFilter(kinds = listOf(GroupKinds.CHAT_MESSAGE))),
        )
        val lost = member.sign(kind = GroupKinds.CHAT_MESSAGE, content = "[EXEMPLE] perdu", tags = listOf(listOf("h", "educ-lyon")), createdAt = 1)
        assertRefused(port.publish(lost), lost)

        // The creator edits the description: the new version replaces the old one, still not a duplicate of itself.
        val edited = room(creator, "education-lyon", "[EXEMPLE] Collectif Éducation", "Lyon", about = "[EXEMPLE] Écoles et collèges", createdAt = 1_700_000_500)
        assertEquals(PublicationResult.Accepted(edited.id), port.publish(edited))
        assertEquals(FetchResult.Events(listOf(edited)), port.fetch(RemoteFilter(kinds = listOf(GroupKinds.METADATA))))
        assertEquals(listOf("education-lyon"), json.decodeFromString<List<Group>>(client.get("/api/v1/groups?q=colleges").bodyAsText()).map { it.id })
        assertEquals(emptyList(), json.decodeFromString<List<Group>>(client.get("/api/v1/groups?q=publiques").bodyAsText()), "the old text is gone")

        // Same name on another territory: another room.
        val paris = room(latecomer, "education-paris", "[EXEMPLE] Collectif Éducation", "Paris")
        assertEquals(PublicationResult.Accepted(paris.id), port.publish(paris))
        assertEquals(2, json.decodeFromString<List<Group>>(client.get("/api/v1/groups?q=education").bodyAsText()).size)
    }

    @Test
    fun theMailboxSettingPublishedThroughThePortKeepsTheEnvelopesLonger() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        val port = ServerPublicationPort(client, baseUrl = "")
        val patient = LocalEventSigner(IdentityKeys.generate())
        val hurried = LocalEventSigner(IdentityKeys.generate())

        // The recipient asks for 180 days (NIP-78 event of their own); the other one never chose.
        val setting = patient.sign(
            kind = MailboxSettings.KIND,
            tags = listOf(listOf("d", MailboxSettings.IDENTIFIER), listOf(MailboxSettings.RETENTION_DAYS_TAG, "180")),
            createdAt = 1_700_000_000,
        )
        assertEquals(PublicationResult.Accepted(setting.id), port.publish(setting))
        assertEquals(
            FetchResult.Events(listOf(setting)),
            port.fetch(RemoteFilter(kinds = listOf(MailboxSettings.KIND), authors = listOf(patient.publicKey.hex), identifiers = listOf(MailboxSettings.IDENTIFIER))),
        )

        // An envelope for each, received 100 days ago.
        val sender = LocalEventSigner(IdentityKeys.generate())
        val forPatient = sender.sign(kind = RelayKinds.GIFT_WRAP, content = "[EXEMPLE] chiffré", tags = listOf(listOf("p", patient.publicKey.hex)), createdAt = 1)
        val forHurried = sender.sign(kind = RelayKinds.GIFT_WRAP, content = "[EXEMPLE] chiffré", tags = listOf(listOf("p", hurried.publicKey.hex)), createdAt = 2)
        assertEquals(PublicationResult.Accepted(forPatient.id), port.publish(forPatient))
        assertEquals(PublicationResult.Accepted(forHurried.id), port.publish(forHurried))
        val now = Instant.now()
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE events SET received_at = ? WHERE kind = ?").use { statement ->
                statement.setObject(1, now.minusSeconds(100 * 86_400).atOffset(ZoneOffset.UTC))
                statement.setInt(2, RelayKinds.GIFT_WRAP)
                assertEquals(2, statement.executeUpdate())
            }
        }

        val repository = EventRepository(TestDatabase.dataSource)
        assertEquals(1, WrapRetention(repository, 90.days, 365.days).purgeOnce())
        assertEquals(listOf(forPatient), repository.query(EventFilter(kinds = listOf(RelayKinds.GIFT_WRAP))))
    }
}
