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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncServiceTest {

    /** A remote that remembers what it received, serves pages like the server, and can be switched off. */
    private class FakeRemote : RemoteEvents {
        val received = mutableListOf<SignedEvent>()
        val toServe = mutableListOf<SignedEvent>()
        var online = true
        var lastFilter: RemoteFilter? = null
        val filters = mutableListOf<RemoteFilter>()

        override suspend fun publish(event: SignedEvent): PublicationResult {
            if (!online) return PublicationResult.Unavailable()
            if (!event.verify()) return PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE)
            received += event
            return PublicationResult.Accepted(event.id)
        }

        override suspend fun fetch(filter: RemoteFilter): FetchResult {
            lastFilter = filter
            filters += filter
            if (!online) return FetchResult.Unavailable()
            val since = filter.since
            val until = filter.until
            val page = toServe
                .filter { (filter.kinds.isEmpty() || it.kind in filter.kinds) && (since == null || it.createdAt >= since) && (until == null || it.createdAt <= until) }
                .sortedWith(compareByDescending<SignedEvent> { it.createdAt }.thenBy { it.id })
                .take(filter.limit)
            return FetchResult.Events(page)
        }
    }

    private val now = 1_000_000L
    private val local = LocalEventStore(JvmDatabase.inMemory(), clock = { now })
    private val remote = FakeRemote()
    private val sync = SyncService(local, remote)
    private val me = LocalEventSigner(IdentityKeys.generate())
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val article = EventKinds.LONG_FORM_ARTICLE

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
        val fiche = editor.sign(kind = article, content = "fiche", tags = listOf(listOf("d", "a")), createdAt = 100)
        val forged = fiche.copy(id = "0".repeat(64))
        remote.toServe += listOf(fiche, forged)

        assertEquals(SyncReport(pulled = 1), sync.pull(listOf(article)))
        assertEquals(null, remote.lastFilter?.since)
        assertEquals(fiche, local.find(fiche.id))
        assertNull(local.find(forged.id))
        assertEquals(100, local.cursor(SyncService.cursorName(listOf(article))))

        // Second pull asks only for what is newer than the cursor and stores nothing twice.
        assertEquals(SyncReport(pulled = 0), sync.pull(listOf(article)))
        assertEquals(100, remote.lastFilter?.since)

        val newer = editor.sign(kind = article, content = "fiche v2", tags = listOf(listOf("d", "a")), createdAt = 200)
        remote.toServe += newer
        assertEquals(SyncReport(pulled = 1), sync.pull(listOf(article)))
        assertNull(local.find(fiche.id))
        assertEquals(newer, local.find(newer.id))

        remote.online = false
        assertEquals(SyncReport(reachable = false), sync.sync(listOf(article)))
    }

    /** Notes stored by other means (followed authors, bundled contents) must not move the general cursor. */
    @Test
    fun eventsStoredOutsideThePullDoNotMoveItsCursor() = runTest {
        val kinds = listOf(EventKinds.TEXT_NOTE)
        val early = editor.sign(kind = EventKinds.TEXT_NOTE, content = "early", createdAt = 100)
        remote.toServe += early
        assertEquals(SyncReport(pulled = 1), sync.pull(kinds))

        // A followed author's note, fetched by the feed on its own, newer than anything the pull saw.
        val followed = me.sign(kind = EventKinds.TEXT_NOTE, content = "followed", createdAt = 500)
        local.store(followed)
        // Meanwhile someone else published between the two passes.
        val between = editor.sign(kind = EventKinds.TEXT_NOTE, content = "between", createdAt = 300)
        remote.toServe += between

        assertEquals(SyncReport(pulled = 1), sync.pull(kinds))
        assertEquals(100, remote.lastFilter?.since)
        assertNotNull(local.find(between.id))
        assertEquals(300, local.cursor(SyncService.cursorName(kinds)))
    }

    @Test
    fun aFutureDatedEventDoesNotFreezeTheCursor() = runTest {
        val kinds = listOf(EventKinds.TEXT_NOTE)
        val future = editor.sign(kind = EventKinds.TEXT_NOTE, content = "future", createdAt = now + 86_400)
        remote.toServe += future
        assertEquals(SyncReport(pulled = 1), sync.pull(kinds))
        assertEquals(now, local.cursor(SyncService.cursorName(kinds)))

        val normal = editor.sign(kind = EventKinds.TEXT_NOTE, content = "normal", createdAt = now + 10)
        remote.toServe += normal
        assertEquals(SyncReport(pulled = 1), sync.pull(kinds))
        assertEquals(now, remote.lastFilter?.since)
    }

    @Test
    fun fullPagesAreFollowedWithUntil() = runTest {
        val kinds = listOf(EventKinds.TEXT_NOTE)
        remote.toServe += (1..5).map { editor.sign(kind = EventKinds.TEXT_NOTE, content = "n$it", createdAt = 100L * it) }
        assertEquals(SyncReport(pulled = 5), sync.pull(kinds, limit = 2))
        // `until` is inclusive, so each page repeats the boundary event: five requests, the last one short.
        assertEquals(5, remote.filters.size)
        assertEquals(listOf(null, 400L, 300L, 200L, 100L), remote.filters.map { it.until })
        assertEquals(500, local.cursor(SyncService.cursorName(kinds)))

        // Unreachable in the middle of a pass: what was stored stays, the cursor does not move.
        remote.toServe += editor.sign(kind = EventKinds.TEXT_NOTE, content = "n6", createdAt = 600)
        remote.online = false
        assertEquals(SyncReport(pulled = 0, reachable = false), sync.pull(kinds, limit = 2))
        assertEquals(500, local.cursor(SyncService.cursorName(kinds)))
    }
}
