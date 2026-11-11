package org.politisationpopulairefrancaise.core.local

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LocalEventStoreTest {

    private val store = LocalEventStore(JvmDatabase.inMemory(), clock = { 1_700_000_000 })
    private val alice = LocalEventSigner(IdentityKeys.generate())
    private val bob = LocalEventSigner(IdentityKeys.generate())

    @Test
    fun storesFindsAndRefusesDuplicates() = runTest {
        val note = alice.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] bonjour", createdAt = 10)
        assertEquals(LocalStoreOutcome.STORED, store.store(note))
        assertEquals(LocalStoreOutcome.DUPLICATE, store.store(note))
        assertEquals(note, store.find(note.id))
        assertNull(store.find("0".repeat(64)))
        assertEquals(1, store.count())
    }

    @Test
    fun addressableContentKeepsTheNewestVersionAndItsTags() = runTest {
        val v1 = alice.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "v1", tags = listOf(listOf("d", "fiche"), listOf("t", "droits")), createdAt = 10)
        val v2 = alice.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "v2", tags = listOf(listOf("d", "fiche"), listOf("t", "greve")), createdAt = 20)
        assertEquals(LocalStoreOutcome.STORED, store.store(v1))
        assertEquals(LocalStoreOutcome.STORED, store.store(v2))
        assertEquals(LocalStoreOutcome.SUPERSEDED, store.store(v1))
        assertNull(store.find(v1.id))
        assertEquals(listOf(v2), store.byTag(listOf(EventKinds.LONG_FORM_ARTICLE), "t", "greve"))
        assertEquals(emptyList(), store.byTag(listOf(EventKinds.LONG_FORM_ARTICLE), "t", "droits"))
        assertEquals(listOf(v2), store.byTag(listOf(EventKinds.LONG_FORM_ARTICLE), "d", "fiche"))
        assertEquals(1, store.count())
    }

    @Test
    fun listsNewestFirstAndFiltersByKind() = runTest {
        val a = alice.sign(kind = EventKinds.TEXT_NOTE, content = "a", createdAt = 100)
        val b = bob.sign(kind = EventKinds.TEXT_NOTE, content = "b", createdAt = 300)
        val article = bob.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "f", tags = listOf(listOf("d", "x")), createdAt = 200)
        listOf(a, b, article).forEach { store.store(it) }
        assertEquals(listOf(b, article, a), store.recent())
        assertEquals(listOf(b, a), store.recent(kinds = listOf(EventKinds.TEXT_NOTE)))
        assertEquals(listOf(b), store.recent(limit = 1))
        assertEquals(300, store.latestCreatedAt(listOf(EventKinds.TEXT_NOTE)))
        assertEquals(null, store.latestCreatedAt(listOf(EventKinds.METADATA)))
    }

    @Test
    fun pendingPublicationsAreTrackedSeparately() = runTest {
        val mine = alice.sign(kind = EventKinds.TEXT_NOTE, content = "en attente", createdAt = 50)
        val received = bob.sign(kind = EventKinds.TEXT_NOTE, content = "reçu", createdAt = 60)
        store.store(mine, pending = true)
        store.store(received)
        assertEquals(listOf(mine), store.pending())
        // Pending events do not move the synchronization cursor.
        assertEquals(60, store.latestCreatedAt(listOf(EventKinds.TEXT_NOTE)))
        store.markPublished(mine.id)
        assertEquals(emptyList(), store.pending())
        store.delete(received.id)
        assertNull(store.find(received.id))
        assertEquals(1, store.count())
    }

    @Test
    fun aPublicationItsScreenIsSendingIsLeftToItForAWhile() = runTest {
        val time = TestTimeSource()
        val store = LocalEventStore(JvmDatabase.inMemory(), clock = { 1_700_000_000 }, sendingTime = time)
        val sent = alice.sign(kind = EventKinds.TEXT_NOTE, content = "envoyé par l'écran", createdAt = 50)
        val waiting = alice.sign(kind = EventKinds.TEXT_NOTE, content = "en attente", createdAt = 40)
        store.storeSending(sent)
        store.store(waiting, pending = true)
        assertEquals(listOf(waiting, sent), store.pending())
        // The synchronization leaves the one being sent to its screen ...
        assertEquals(listOf(waiting), store.pendingToSend())
        // ... and takes it once the screen had its chance (no network, for example).
        time += LocalEventStore.SENDING_GRACE + 1.seconds
        assertEquals(listOf(waiting, sent), store.pendingToSend())
    }

    @Test
    fun aFailedAttemptHandsThePublicationBackToTheSynchronization() = runTest {
        val event = alice.sign(kind = EventKinds.TEXT_NOTE, content = "hors ligne", createdAt = 50)
        store.storeSending(event)
        store.sending(event) { PublicationResult.Unavailable() }
        assertEquals(listOf(event), store.pendingToSend())
        // A success keeps it for the screen until it is marked published.
        val other = alice.sign(kind = EventKinds.TEXT_NOTE, content = "en ligne", createdAt = 60)
        store.storeSending(other)
        store.sending(other) { PublicationResult.Accepted(other.id) }
        assertEquals(listOf(event), store.pendingToSend())
        store.markPublished(other.id)
        assertEquals(listOf(event), store.pendingToSend())
    }
}
