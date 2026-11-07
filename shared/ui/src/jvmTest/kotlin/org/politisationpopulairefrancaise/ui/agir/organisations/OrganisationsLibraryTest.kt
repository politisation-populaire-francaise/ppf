package org.politisationpopulairefrancaise.ui.agir.organisations

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.DirectoryServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The directory on the device: the bundled examples are imported, only trusted entries are kept,
 * the server is asked with the general request only (never the user's territory nor the open
 * organisation), the verified keys follow the entries, and the open organisation's notes come from
 * the device.
 */
class OrganisationsLibraryTest {

    private fun CoroutineScope.library(store: LocalEventStore, remote: RemoteEvents?, trust: EditorialTrust = AgirFixtures.trust) =
        OrganisationsLibrary(store, trust, remote, this)

    private suspend fun OrganisationsLibrary.awaitSettled() = withTimeout(20_000) {
        while (status == OrganisationsStatus.Idle || status == OrganisationsStatus.Loading) delay(20)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    @Test
    fun theBundledExamplesAreReadableOfflineAndTheirKeysVerified() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val library = library(store, remote = null)
        library.ensureStarted()
        library.awaitSettled()

        assertEquals(OrganisationsStatus.Offline, library.status)
        assertEquals(AgirFixtures.EXAMPLES.sorted(), library.organisations.map { it.slug }.sorted())
        assertTrue(library.organisations.all { it.example && it.editor == EditorialTrust.EXAMPLE_EDITOR })
        assertEquals(AgirFormats.ORG_TYPES.toSet(), library.organisations.map { it.type }.toSet(), "one example of each type")
        assertTrue(library.organisations.any { it.isNational } && library.organisations.any { !it.isNational })
        assertEquals(3, library.verified.size)
        assertTrue(library.verified.isVerified(AgirFixtures.LANTERNE_ACCOUNT))
        assertEquals(AgirFixtures.LANTERNE, library.verified.organisationFor(AgirFixtures.LANTERNE_ACCOUNT)?.slug)
        // Example contacts only: example.org addresses (RFC 2606) and the Arcep blocks reserved for fiction.
        val fiction = listOf("01 99 00", "02 61 91", "03 53 01", "04 65 71", "05 36 49", "06 39 98")
        assertTrue(library.organisations.mapNotNull { it.phone }.all { phone -> fiction.any(phone::startsWith) })
        assertTrue(library.organisations.mapNotNull { it.email }.all { it.endsWith("@example.org") })
        assertTrue(library.organisations.flatMap { it.websites }.all { it.startsWith("https://example.org/") })
    }

    @Test
    fun withoutTrustNothingIsImported() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val library = library(store, remote = null, trust = EditorialTrust(emptySet()))
        library.ensureStarted()
        library.awaitSettled()
        assertTrue(library.organisations.isEmpty())
        assertEquals(0, library.verified.size)
        assertEquals(0L, store.count())
    }

    @Test
    fun theServerIsAskedForTheDirectoryOnlyAndOnlyTrustedEntriesAreKept() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = DirectoryServer()
        val unionKey = IdentityKeys.generate().publicKey.hex
        val impostorKey = IdentityKeys.generate().publicKey.hex
        server.toServe += AgirFixtures.entry("syndicat-test", type = "syndicat", territory = "Lille (59)", accountKey = unionKey)
        server.toServe += AgirFixtures.entry("imposteur", accountKey = impostorKey, signer = AgirFixtures.stranger)
        val library = library(store, server)
        library.ensureStarted()
        library.awaitSettled()

        assertEquals(OrganisationsStatus.Synced(1), library.status)
        assertTrue("syndicat-test" in library.organisations.map { it.slug })
        assertFalse("imposteur" in library.organisations.map { it.slug })
        assertTrue(library.verified.isVerified(unionKey))
        assertFalse(library.verified.isVerified(impostorKey))

        // The search and "près de chez moi" stay on the device: no new request, nothing of them sent.
        library.setNear("59000")
        library.search("syndicat test")
        library.toggleType("syndicat")
        assertEquals(listOf("syndicat-test"), library.results.map { it.slug })
        library.open(library.results.single())
        assertEquals(1, server.tagRequests.size)
        val (tags, filter) = server.tagRequests.single()
        assertEquals(listOf(AgirFormats.TAG_TYPE to AgirFormats.TYPE_ORGANISATION), tags.map { it.name to it.value })
        assertEquals(listOf(30023), filter.kinds)
        assertTrue(filter.authors.isEmpty() && filter.identifiers.isEmpty())
        assertTrue(server.fetches.isEmpty(), "no other request")

        // The next pass starts from the last entry received.
        library.refresh()
        awaitUntil { server.tagRequests.size == 2 }
        library.awaitSettled()
        assertEquals(server.toServe.maxOf { it.createdAt }, server.tagRequests.last().second.since)
    }

    @Test
    fun anUnreachableServerLeavesTheDeviceCopy() = runBlocking {
        val server = DirectoryServer().apply { reachable = false }
        val library = library(LocalEventStore(JvmDatabase.inMemory()), server)
        library.ensureStarted()
        library.awaitSettled()
        assertEquals(OrganisationsStatus.Unreachable, library.status)
        assertEquals(AgirFixtures.EXAMPLES.size, library.organisations.size)
    }

    @Test
    fun aNewerVersionReplacesTheEntryAndAWithdrawnKeyIsNoLongerVerified() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val server = DirectoryServer()
        val key = IdentityKeys.generate().publicKey.hex
        server.toServe += AgirFixtures.entry("collectif-test", accountKey = key, updatedAt = 1_793_500_000)
        val library = library(store, server)
        library.ensureStarted()
        library.awaitSettled()
        assertTrue(library.verified.isVerified(key))
        library.open(library.organisations.single { it.slug == "collectif-test" })

        // The team publishes the same entry again, without the key: the device follows.
        server.toServe.clear()
        server.toServe += AgirFixtures.entry("collectif-test", name = "[EXEMPLE] Nouveau nom", accountKey = null, updatedAt = 1_793_600_000)
        library.refresh()
        awaitUntil { server.tagRequests.size == 2 }
        library.awaitSettled()
        assertFalse(library.verified.isVerified(key))
        assertEquals("Nouveau nom", library.selected?.displayName, "the open page shows the new version")
        assertFalse(library.openKey(key))
    }

    @Test
    fun aRevokedEntryIsNoLongerVerifiedButItsPageStillSaysSo() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val key = IdentityKeys.generate().publicKey.hex
        store.store(AgirFixtures.entry("collectif-retire", accountKey = key, updatedAt = 1_793_500_000))
        store.store(AgirFixtures.entry("collectif-retire", accountKey = key, updatedAt = 1_794_038_400, status = "revoquee"))
        val library = library(store, null)
        library.ensureStarted()
        library.awaitSettled()
        assertFalse(library.verified.isVerified(key))
        assertTrue(library.hasRevoked)
        assertFalse(library.results.any { it.slug == "collectif-retire" }, "left out of the list by default")
        library.toggleIncludeRevoked()
        assertTrue(library.results.single { it.slug == "collectif-retire" }.revoked)
        // Reached by a link (an organiser of the agenda): the page opens and says the verification was withdrawn.
        assertTrue(library.openKey(key))
        assertTrue(library.selected?.revoked == true)
    }

    @Test
    fun theOpenOrganisationShowsItsNotesFoundOnTheDevice() = runBlocking {
        val store = LocalEventStore(JvmDatabase.inMemory())
        val organisation = LocalEventSigner(IdentityKeys.generate())
        val someoneElse = LocalEventSigner(IdentityKeys.generate())
        store.store(organisation.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Réunion publique jeudi.", createdAt = 1_793_900_100))
        store.store(organisation.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Merci à toutes et à tous.", createdAt = 1_793_900_200))
        store.store(someoneElse.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Autre compte.", createdAt = 1_793_900_300))
        store.store(AgirFixtures.entry("avec-compte", accountKey = organisation.publicKey.hex))
        val library = library(store, remote = null)
        library.ensureStarted()
        library.awaitSettled()

        assertTrue(library.openKey(organisation.publicKey.hex))
        awaitUntil { library.publications.size == 2 }
        assertEquals(listOf("[EXEMPLE] Merci à toutes et à tous.", "[EXEMPLE] Réunion publique jeudi."), library.publications.map { it.content })
        assertFalse(library.openKey(someoneElse.publicKey.hex), "not a verified organisation")
        library.close()
        assertNull(library.selected)
        assertTrue(library.publications.isEmpty())
    }
}
