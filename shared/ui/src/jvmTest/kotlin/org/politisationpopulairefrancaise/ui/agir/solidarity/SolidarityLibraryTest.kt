package org.politisationpopulairefrancaise.ui.agir.solidarity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.DirectoryServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The solidarity rubric on the device: only contents of trusted editorial keys are imported or kept,
 * the server receives two general requests and nothing of what the user types or picks (territory,
 * words, skills, the open fund), each type follows its own cursor, and the private choices can be
 * forgotten at once.
 */
class SolidarityLibraryTest {

    private fun CoroutineScope.library(store: LocalEventStore, remote: RemoteEvents?, bundled: ByteArray? = null, trust: EditorialTrust = AgirFixtures.trust) =
        SolidarityLibrary(store, trust, remote, this, bundled = { bundled })

    private suspend fun SolidarityLibrary.awaitSettled() = withTimeout(20_000) {
        while (status == SolidarityStatus.Idle || status == SolidarityStatus.Loading) delay(20)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    private fun json(events: List<SignedEvent>): ByteArray =
        Json.encodeToString(ListSerializer(SignedEvent.serializer()), events).encodeToByteArray()

    /** The `ppf-type` each request to the server asked for. */
    private fun DirectoryServer.askedTypes(): List<String> = tagRequests.map { (tags, _) -> tags.single { it.name == AgirFormats.TAG_TYPE }.value }

    @Test
    fun withoutAServerTheBundledContentsOfTrustedKeysAreReadOffline() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val bundled = json(
            listOf(
                SolidarityExamples.fund("vraie", "[EXEMPLE] Caisse de confiance", managerName = "Collectif fictif"),
                SolidarityExamples.fund("intruse", "[EXEMPLE] Caisse d'un inconnu", managerName = "Collectif fictif", signer = AgirFixtures.stranger),
                SolidarityExamples.mission("mission-vraie", "[EXEMPLE] Mission de confiance", listOf("traduction")),
                SolidarityExamples.mission("mission-intruse", "[EXEMPLE] Mission d'un inconnu", listOf("traduction"), signer = AgirFixtures.stranger),
            ),
        )
        val library = library(store, remote = null, bundled = bundled)
        library.ensureStarted()
        library.awaitSettled()

        assertEquals(SolidarityStatus.Offline, library.status)
        assertEquals(listOf("vraie"), library.funds.map { it.slug })
        assertEquals(listOf("mission-vraie"), library.missions.map { it.slug })
        assertEquals(2L, store.count(), "nothing of an unknown key is kept on the device")
    }

    @Test
    fun withoutTrustNothingIsImported() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val library = library(store, remote = null, bundled = json(SolidarityExamples.all()), trust = EditorialTrust(emptySet()))
        library.ensureStarted()
        library.awaitSettled()
        assertTrue(library.funds.isEmpty() && library.missions.isEmpty())
        assertEquals(0L, store.count())
    }

    @Test
    fun theServerReceivesTwoGeneralRequestsAndNothingOfTheUsersChoices() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = DirectoryServer()
        server.toServe += SolidarityExamples.all()
        server.toServe += SolidarityExamples.fund("imposture", "[EXEMPLE] Fausse caisse", managerName = "Inconnu", signer = AgirFixtures.stranger)
        val library = library(store, server)
        library.ensureStarted()
        library.awaitSettled()

        assertEquals(SolidarityStatus.Synced(7), library.status, "four funds and three missions, the stranger's fund left out")
        assertFalse("imposture" in library.funds.map { it.slug })
        assertEquals(listOf(SolidarityFormats.TYPE_FUND, SolidarityFormats.TYPE_MISSION), server.askedTypes())
        server.tagRequests.forEach { (_, filter) ->
            assertEquals(listOf(30023), filter.kinds)
            assertTrue(filter.authors.isEmpty() && filter.identifiers.isEmpty(), "a general request, the same for everybody")
        }

        // Everything the user types or picks stays on the device: no new request, nothing of it sent.
        library.setNear("59000")
        library.searchFunds("caisse")
        library.toggleFundType(SolidarityFormats.FUND_LEGAL)
        library.openFund(library.fundResults.single())
        library.selectTab(SolidarityTab.MISSIONS)
        library.toggleSkill("juriste")
        library.toggleRemoteOnly()
        library.openMission(library.missionResults.single())
        assertEquals(SolidarityExamples.LAWYERS, library.selectedMission?.slug)
        assertEquals(2, server.tagRequests.size)
        assertTrue(server.fetches.isEmpty(), "no other request")

        // The next pass starts, for each type, from the last content received.
        library.refresh()
        awaitUntil { server.tagRequests.size == 4 }
        library.awaitSettled()
        val fundsSince = server.tagRequests.filter { (tags, _) -> tags.single().value == SolidarityFormats.TYPE_FUND }.last().second.since
        assertEquals(server.toServe.filter { it.firstTagValue(AgirFormats.TAG_TYPE) == SolidarityFormats.TYPE_FUND }.maxOf { it.createdAt }, fundsSince)
        // The cursor never goes beyond the device's clock.
        assertEquals(minOf(1_794_000_000L, store.now()), store.cursor(SolidarityLibrary.CURSOR_MISSIONS))
    }

    @Test
    fun anUnreachableServerLeavesTheDeviceCopy() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        SolidarityExamples.storeAll(store)
        val server = DirectoryServer().apply { reachable = false }
        val library = library(store, server)
        library.ensureStarted()
        library.awaitSettled()
        assertEquals(SolidarityStatus.Unreachable, library.status)
        assertEquals(4, library.funds.size)
        assertEquals(3, library.missions.size)
    }

    @Test
    fun aNewerVersionReplacesTheOpenFundAndClosingItRemovesItsLink() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = DirectoryServer()
        server.toServe += SolidarityExamples.fund("caisse-test", "[EXEMPLE] Caisse de test", managerName = "Collectif fictif", updatedAt = 1_794_000_000)
        val library = library(store, server)
        library.ensureStarted()
        library.awaitSettled()
        library.openFund(library.funds.single())
        assertEquals("https://example.org/caisse-test", library.selectedFund?.openableDonationLink)

        // The team closes the fund: the open page follows, without a link any more.
        server.toServe.clear()
        server.toServe += SolidarityExamples.fund("caisse-test", "[EXEMPLE] Caisse de test", managerName = "Collectif fictif", status = SolidarityFormats.FUND_CLOSED, updatedAt = 1_794_100_000)
        library.refresh()
        awaitUntil { server.tagRequests.size == 4 }
        library.awaitSettled()
        assertTrue(library.selectedFund?.isClosed == true)
        assertNull(library.selectedFund?.openableDonationLink)
        assertTrue(library.fundResults.isEmpty(), "a closed fund leaves the list by default")
        assertTrue(library.hasClosedFunds)
    }

    @Test
    fun theUsersChoicesCanBeForgottenAtOnce() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        SolidarityExamples.storeAll(store)
        val library = library(store, remote = null)
        library.ensureStarted()
        library.awaitSettled()
        library.setNear("59000")
        library.toggleSkill("traduction")
        library.openMission(library.missions.first())
        library.clearPrivateChoices()
        assertTrue(library.fundQuery.isUnconstrained && library.missionQuery.isUnconstrained)
        assertNull(library.selectedMission)
        assertFalse(library.hasSelection)
    }
}
