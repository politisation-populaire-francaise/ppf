package org.politisationpopulairefrancaise.core.sync

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncServiceTest {

    /** A remote that remembers what it received and can be switched off. */
    private class FakeRemote : RemoteEvents {
        val received = mutableListOf<SignedEvent>()
        val toServe = mutableListOf<SignedEvent>()
        var online = true
        var lastFilter: RemoteFilter? = null

        override suspend fun publish(event: SignedEvent): PublicationResult {
            if (!online) return PublicationResult.Unavailable()
            if (!event.verify()) return PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE)
            received += event
            return PublicationResult.Accepted(event.id)
        }

        override suspend fun fetch(filter: RemoteFilter): FetchResult {
            lastFilter = filter
            if (!online) return FetchResult.Unavailable()
            val since = filter.since
            return FetchResult.Events(toServe.filter { since == null || it.createdAt >= since })
        }
    }

    private val local = LocalEventStore(JvmDatabase.inMemory())
    private val remote = FakeRemote()
    private val sync = SyncService(local, remote)
    private val me = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())

    @Test
    fun pendingEventsGoUpWhenTheServerIsReachable() = runTest {
        val first = me.sign(kind = EventKinds.TEXT_NOTE, content = "1", createdAt = 10)
        val second = me.sign(kind = EventKinds.TEXT_NOTE, content = "2", createdAt = 20)
        local.store(first, pending = true)
        local.store(second, pending = true)

        remote.online = false
        assertEquals(SyncReport(reachable = false), sync.publishPending())
        assertEquals(2, local.pending().size)

        remote.online = true
        assertEquals(SyncReport(published = 2), sync.publishPending())
        assertEquals(listOf(first, second), remote.received)
        assertEquals(emptyList(), local.pending())
    }

    @Test
    fun refusedEventsAreDroppedLocally() = runTest {
        val good = me.sign(kind = EventKinds.TEXT_NOTE, content = "ok", createdAt = 10)
        val forged = good.copy(id = "f".repeat(64))
        local.store(good, pending = true)
        local.store(forged, pending = true)
        assertEquals(SyncReport(published = 1, rejected = 1), sync.publishPending())
        assertNull(local.find(forged.id))
        assertEquals(good, local.find(good.id))
    }

    @Test
    fun pullStoresOnlyAuthenticEventsAndUsesACursor() = runTest {
        val article = editor.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "fiche", tags = listOf(listOf("d", "a")), createdAt = 100)
        val forged = article.copy(id = "0".repeat(64))
        remote.toServe += listOf(article, forged)

        assertEquals(SyncReport(pulled = 1), sync.pull(listOf(EventKinds.LONG_FORM_ARTICLE)))
        assertEquals(null, remote.lastFilter?.since)
        assertEquals(article, local.find(article.id))
        assertNull(local.find(forged.id))

        // Second pull asks only for what is newer than the newest known event and stores nothing twice.
        assertEquals(SyncReport(pulled = 0), sync.pull(listOf(EventKinds.LONG_FORM_ARTICLE)))
        assertEquals(100, remote.lastFilter?.since)

        val newer = editor.sign(kind = EventKinds.LONG_FORM_ARTICLE, content = "fiche v2", tags = listOf(listOf("d", "a")), createdAt = 200)
        remote.toServe += newer
        assertEquals(SyncReport(pulled = 1), sync.pull(listOf(EventKinds.LONG_FORM_ARTICLE)))
        assertNull(local.find(article.id))
        assertEquals(newer, local.find(newer.id))

        remote.online = false
        assertEquals(SyncReport(reachable = false), sync.sync(listOf(EventKinds.LONG_FORM_ARTICLE)))
    }
}
