package org.politisationpopulairefrancaise.ui.groups

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The logic of the rooms behind the screens: suggestions, ids of new rooms, refused messages, joined rooms. */
class GroupsLibraryTest {

    private val database = JvmDatabase.inMemory()
    private val store = LocalEventStore(database)
    private val memberships = GroupStore(database)
    private val signer = LocalEventSigner(RoomsFixtures.phrase.deriveKeys())

    private fun CoroutineScope.library(server: RoomsServer?) = GroupsLibrary(store, server, memberships, this, searchDelayMillis = 10)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(20)
    }

    private suspend fun GroupsLibrary.started() {
        ensureStarted()
        awaitUntil { status == GroupsStatus.Synced || status == GroupsStatus.Offline || status == GroupsStatus.Unreachable }
    }

    @Test
    fun suggestionsPutTheTerritoriesOfTheJoinedRoomsFirst() = runBlocking {
        val server = RoomsFixtures.server().apply {
            seed(RoomsFixtures.room("collectif-vert-paris", "[EXEMPLE] Collectif vert", "paris"))
            seed(RoomsFixtures.room("amap-paris", "[EXEMPLE] Amap", "Paris"))
        }
        val groups = library(server)
        groups.started()
        // Most rooms first, written as most rooms write it.
        assertEquals(listOf("Paris", "Lyon"), groups.territories)
        assertEquals(listOf("amap-paris", "assemblee-du-quartier-paris", "collectif-sante-lyon", "collectif-vert-paris", "syndicat-des-livreurs-lyon"), groups.suggestions.map { it.id })

        groups.join(RoomsFixtures.LIVREURS)
        assertEquals(listOf(RoomsFixtures.LIVREURS), groups.mine.map { it.id })
        // The other Lyon room comes first now.
        assertEquals(RoomsFixtures.SANTE, groups.suggestions.first().id)
        assertTrue(groups.isNearJoined(groups.suggestions.first()))

        groups.selectTerritory("PARIS")
        assertEquals(listOf("amap-paris", "assemblee-du-quartier-paris", "collectif-vert-paris"), groups.suggestions.map { it.id })
        groups.selectTerritory(null)
        groups.search("santé")
        assertEquals(listOf(RoomsFixtures.SANTE), groups.suggestions.map { it.id })

        awaitUntil { memberships.isMember(RoomsFixtures.LIVREURS) }
        groups.clearMemberships()
        awaitUntil { memberships.count() == 0L }
        assertTrue(groups.joined.isEmpty())
        assertTrue(server.published.isEmpty(), "joining and leaving publish nothing")
    }

    @Test
    fun anIdTakenOnTheServerIsNumbered() = runBlocking {
        val server = RoomsFixtures.server().apply {
            // Another room already uses the id the new one would get, unknown to the device.
            seed(RoomsFixtures.room("collectif-velo-lyon", "[EXEMPLE] Vélo", "Villeurbanne"))
            hiddenFromDirectory += "collectif-velo-lyon"
        }
        val groups = library(server)
        groups.started()
        groups.beginCreation("[EXEMPLE] Collectif vélo")
        groups.updateDraft(groups.creationDraft.copy(territory = "Lyon", charterAccepted = true))
        val created = assertNotNull(groups.create(signer))
        assertEquals("collectif-velo-lyon-2", created.id)
        assertEquals(2, server.published.size, "one refusal, then the numbered id")
        assertTrue(groups.isMember(created.id))
        assertEquals(created.id, groups.selected?.id)
        assertEquals(GroupDraft(), groups.creationDraft)
    }

    @Test
    fun anIdKnownOnTheDeviceIsSkippedWithoutAskingTheServer() = runBlocking {
        val server = RoomsFixtures.server().apply {
            seed(RoomsFixtures.room("collectif-velo-lyon", "[EXEMPLE] Vélo", "Villeurbanne"))
        }
        val groups = library(server)
        groups.started()
        groups.beginCreation("[EXEMPLE] Collectif vélo")
        groups.updateDraft(groups.creationDraft.copy(territory = "Lyon", charterAccepted = true))
        assertEquals("collectif-velo-lyon-2", groups.create(signer)?.id)
        assertEquals(1, server.published.size)
    }

    @Test
    fun aDuplicateIsDecidedByTheServerAndNamesTheRoomToJoin() = runBlocking {
        val server = RoomsFixtures.server()
        val groups = library(server)
        groups.started()
        groups.beginCreation("[EXEMPLE] COLLECTIF SANTÉ")
        groups.updateDraft(groups.creationDraft.copy(territory = "lyon", charterAccepted = true))
        // The device's look-alikes are a hint shown in the form...
        assertEquals(RoomsFixtures.SANTE, groups.similarTo(groups.creationDraft).first().id)
        // ...the server's answer is what counts.
        assertNull(groups.create(signer))
        val exists = assertIs<CreationError.Exists>(groups.creationError)
        assertEquals(RoomsFixtures.SANTE, exists.existingId)
        assertEquals("Collectif santé", exists.existingName)
        assertTrue(groups.creating, "the form stays open with its values")

        groups.joinExisting(exists.existingId)
        assertTrue(groups.isMember(RoomsFixtures.SANTE))
        assertEquals(RoomsFixtures.SANTE, groups.selected?.id)
        assertTrue(!groups.creating)
    }

    @Test
    fun creationNeedsTheServerAndTheCharter() = runBlocking {
        val offline = library(server = null)
        offline.started()
        assertEquals(GroupsStatus.Offline, offline.status)
        offline.beginCreation("[EXEMPLE] Collectif")
        offline.updateDraft(offline.creationDraft.copy(territory = "Lille", charterAccepted = true))
        assertNull(offline.create(signer))
        assertEquals(CreationError.Offline, offline.creationError)

        val server = RoomsFixtures.server()
        val online = library(server)
        online.started()
        online.beginCreation("[EXEMPLE] Collectif")
        online.updateDraft(online.creationDraft.copy(territory = "Lille"))
        assertNull(online.create(signer), "the charter is never accepted on the user's behalf")
        assertTrue(server.published.isEmpty())

        server.online = false
        online.updateDraft(online.creationDraft.copy(charterAccepted = true))
        assertNull(online.create(signer))
        assertEquals(CreationError.Offline, online.creationError)
        assertEquals("[EXEMPLE] Collectif", online.creationDraft.name)
    }

    @Test
    fun aMessageRefusedByAClosedRoomGivesItsTextBack() = runBlocking {
        val server = RoomsFixtures.server()
        val closed = RoomsFixtures.other.sign(
            Group.build(RoomsFixtures.other.publicKey, RoomsFixtures.FERME, "[EXEMPLE] Salon fermé", null, "Marseille", createdAt = 1_000)
                .let { it.copy(tags = it.tags + listOf(listOf("closed"))) },
        )
        server.seed(closed)
        val groups = library(server)
        groups.started()
        val room = groups.groups.single { it.id == RoomsFixtures.FERME }
        groups.open(room)
        groups.messageDraft = "[EXEMPLE] Il y a quelqu’un ?"
        assertTrue(groups.send(signer))
        assertEquals(SendError.CLOSED, groups.sendError)
        assertEquals("[EXEMPLE] Il y a quelqu’un ?", groups.messageDraft)
        assertTrue(store.pending().isEmpty(), "nothing refused is kept")
        assertTrue(groups.messages.isEmpty())
    }

    @Test
    fun theIdOfADraftAndItsLimits() {
        val draft = GroupDraft(name = "[EXEMPLE] Collectif santé", territory = "Lyon")
        assertEquals("collectif-sante-lyon", draft.proposedId)
        assertTrue(draft.complete)
        assertTrue(!draft.charterAccepted)
        assertTrue(!GroupDraft(name = "!!!", territory = "Lyon").complete)
        assertTrue(!GroupDraft(name = "Nom", territory = " ").complete)
        assertTrue(GroupDraft(name = "n".repeat(Group.MAX_NAME_LENGTH + 1), territory = "Lyon").nameTooLong)
        assertTrue(!GroupDraft(name = "n".repeat(Group.MAX_NAME_LENGTH + 1), territory = "Lyon").complete)
    }
}
