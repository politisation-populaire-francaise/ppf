package org.politisationpopulairefrancaise.core.publication

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The in-memory server applies the rules of the real one for the rooms of collectives (docs/FORMATS.md, section 5; D-028). */
class InMemoryPublicationPortGroupsTest {

    private val port = InMemoryPublicationPort()
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())

    private suspend fun EventSigner.describe(id: String, name: String, territory: String, about: String? = null, createdAt: Long = 1_000) =
        sign(Group.build(publicKey, id, name, about, territory, createdAt = createdAt))

    @Test
    fun describesRoomsAndListsThemByName() = runTest {
        val lyon = alice.describe("collectif-lyon", "[EXEMPLE] Collectif Lyon", "Lyon", about = "Mobilisation locale")
        val paris = bob.describe("assemblee-paris", "[EXEMPLE] Assemblée Paris", "Paris")
        assertEquals(PublicationResult.Accepted(lyon.id), port.publish(lyon))
        assertEquals(PublicationResult.Accepted(paris.id), port.publish(paris))

        val all = assertIs<GroupsResult.Groups>(port.fetchGroups())
        assertEquals(listOf("assemblee-paris", "collectif-lyon"), all.groups.map { it.id })
        assertEquals(listOf("collectif-lyon"), assertIs<GroupsResult.Groups>(port.fetchGroups(query = "lyon")).groups.map { it.id })
        assertEquals(listOf("collectif-lyon"), assertIs<GroupsResult.Groups>(port.fetchGroups(query = "MOBILISATION")).groups.map { it.id })
        assertEquals(listOf("assemblee-paris"), assertIs<GroupsResult.Groups>(port.fetchGroups(territory = "paris")).groups.map { it.id })
        assertEquals(listOf("assemblee-paris"), assertIs<GroupsResult.Groups>(port.fetchGroups(limit = 1)).groups.map { it.id })
        assertEquals(emptyList(), assertIs<GroupsResult.Groups>(port.fetchGroups(query = "marseille")).groups)
    }

    @Test
    fun aNewerDescriptionBySameAuthorReplacesTheOlderOne() = runTest {
        val first = alice.describe("collectif-lyon", "[EXEMPLE] Collectif Lyon", "Lyon", createdAt = 1_000)
        val edited = alice.describe("collectif-lyon", "[EXEMPLE] Collectif Lyon", "Lyon", about = "Nouvelle description", createdAt = 2_000)
        port.publish(first)
        port.publish(edited)
        assertEquals(listOf(edited), port.events)
        // An older version is acknowledged but changes nothing.
        assertEquals(PublicationResult.Accepted(first.id), port.publish(first))
        assertEquals(listOf(edited), port.events)
        assertEquals("Nouvelle description", assertIs<GroupsResult.Groups>(port.fetchGroups()).groups.single().about)
    }

    @Test
    fun refusesDuplicatesAndTakenIdsLikeTheServer() = runTest {
        val lyon = alice.describe("collectif-lyon", "[EXEMPLE] Collectif Lyon", "Lyon")
        port.publish(lyon)

        // Same name (accents and case apart) on the same territory, another id: join the existing room.
        val duplicate = bob.describe("collectif-lyon-2", "[EXEMPLE] collectif LYON", "lyon")
        assertEquals(PublicationResult.Rejected(duplicate.id, RejectionReason.GROUP_EXISTS, existing = "collectif-lyon"), port.publish(duplicate))
        // Same id by another key: taken.
        val taken = bob.describe("collectif-lyon", "[EXEMPLE] Autre nom", "Marseille")
        assertEquals(PublicationResult.Rejected(taken.id, RejectionReason.GROUP_TAKEN, existing = "collectif-lyon"), port.publish(taken))
        // Same name on another territory: a different room, accepted.
        val marseille = bob.describe("collectif-lyon-marseille", "[EXEMPLE] Collectif Lyon", "Marseille")
        assertEquals(PublicationResult.Accepted(marseille.id), port.publish(marseille))
        assertEquals(2, assertIs<GroupsResult.Groups>(port.fetchGroups()).groups.size)
    }

    @Test
    fun refusesMalformedDescriptions() = runTest {
        val author = alice.publicKey
        val noTerritory = alice.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "x"), listOf("name", "X"), listOf("ppf-charter", "1.0")))
        assertEquals(RejectionReason.MISSING_TERRITORY, assertIs<PublicationResult.Rejected>(port.publish(noTerritory)).reason)
        val noCharter = alice.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "x"), listOf("name", "X"), listOf("ppf-territory", "Lyon")))
        assertEquals(RejectionReason.MISSING_CHARTER, assertIs<PublicationResult.Rejected>(port.publish(noCharter)).reason)
        val noName = alice.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "x"), listOf("ppf-territory", "Lyon"), listOf("ppf-charter", "1.0")))
        assertEquals(RejectionReason.MISSING_NAME, assertIs<PublicationResult.Rejected>(port.publish(noName)).reason)
        val twoIds = alice.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "x"), listOf("d", "y"), listOf("ppf-territory", "Lyon"), listOf("ppf-charter", "1.0")))
        assertEquals(RejectionReason.REFUSED, assertIs<PublicationResult.Rejected>(port.publish(twoIds)).reason)
        val badId = alice.sign(kind = EventKinds.GROUP_METADATA, tags = listOf(listOf("d", "Bad Id"), listOf("ppf-territory", "Lyon"), listOf("ppf-charter", "1.0")))
        assertEquals(RejectionReason.REFUSED, assertIs<PublicationResult.Rejected>(port.publish(badId)).reason)
        val private = alice.sign(Group.build(author, "p", "P", null, "Lyon", createdAt = 1).let { it.copy(tags = it.tags + listOf(listOf("private"))) })
        assertEquals(RejectionReason.REFUSED, assertIs<PublicationResult.Rejected>(port.publish(private)).reason)
        assertEquals(emptyList(), port.events)
    }

    @Test
    fun roomMessagesNeedAnExistingOpenRoom() = runTest {
        val lyon = alice.describe("collectif-lyon", "[EXEMPLE] Collectif Lyon", "Lyon")
        val closed = alice.sign(Group.build(alice.publicKey, "ferme", "Fermé", null, "Lyon", createdAt = 1).let { it.copy(tags = it.tags + listOf(listOf("closed"))) })
        port.publish(lyon)
        port.publish(closed)

        val hello = bob.sign(GroupMessage.build(bob.publicKey, "collectif-lyon", "[EXEMPLE] Bonjour", createdAt = 10))
        assertEquals(PublicationResult.Accepted(hello.id), port.publish(hello))
        val nowhere = bob.sign(GroupMessage.build(bob.publicKey, "inconnu", "[EXEMPLE] Personne", createdAt = 11))
        assertEquals(PublicationResult.Rejected(nowhere.id, RejectionReason.GROUP_NOT_FOUND), port.publish(nowhere))
        val toClosed = bob.sign(GroupMessage.build(bob.publicKey, "ferme", "[EXEMPLE] Porte close", createdAt = 12))
        assertEquals(PublicationResult.Rejected(toClosed.id, RejectionReason.GROUP_CLOSED, existing = "ferme"), port.publish(toClosed))
        val noRoom = bob.sign(kind = EventKinds.GROUP_CHAT, content = "x")
        assertEquals(RejectionReason.REFUSED, assertIs<PublicationResult.Rejected>(port.publish(noRoom)).reason)
        val twoRooms = bob.sign(kind = EventKinds.GROUP_CHAT, content = "x", tags = listOf(listOf("h", "collectif-lyon"), listOf("h", "ferme")))
        assertEquals(RejectionReason.REFUSED, assertIs<PublicationResult.Rejected>(port.publish(twoRooms)).reason)
        // A note without any h tag is not a room event and goes through as before.
        val note = bob.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note")
        assertEquals(PublicationResult.Accepted(note.id), port.publish(note))

        // Read back like the application does.
        val messages = assertIs<FetchResult.Events>(port.fetchByTags(listOf(TagFilter("h", "collectif-lyon")), RemoteFilter(kinds = listOf(EventKinds.GROUP_CHAT))))
        assertEquals(listOf(hello), messages.events)
    }
}
