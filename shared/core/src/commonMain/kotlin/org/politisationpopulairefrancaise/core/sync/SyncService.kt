package org.politisationpopulairefrancaise.core.sync

import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/** What a synchronization did. [reachable] is false when the remote could not be contacted. */
data class SyncReport(
    val published: Int = 0,
    val rejected: Int = 0,
    val pulled: Int = 0,
    val reachable: Boolean = true,
) {
    operator fun plus(other: SyncReport): SyncReport = SyncReport(
        published + other.published,
        rejected + other.rejected,
        pulled + other.pulled,
        reachable && other.reachable,
    )
}

/**
 * Offline-first synchronization between the device's [LocalEventStore] and a [RemoteEvents]:
 * the user's pending publications go up, new contents come down. Everything received is
 * signature-checked before being stored; whatever fails the check is dropped.
 */
class SyncService(private val local: LocalEventStore, private val remote: RemoteEvents) {

    /**
     * Publishes locally pending events, oldest first; stops at the first network failure. Leaves out
     * those a screen is sending at this moment ([LocalEventStore.storeSending]).
     */
    suspend fun publishPending(): SyncReport {
        var report = SyncReport()
        for (event in local.pendingToSend()) {
            when (val result = remote.publish(event)) {
                is PublicationResult.Accepted -> {
                    local.markPublished(event.id)
                    report = report.copy(published = report.published + 1)
                }
                is PublicationResult.Rejected -> {
                    // Definitive: the server will never take it. Keep nothing the server refuses.
                    local.delete(event.id)
                    report = report.copy(rejected = report.rejected + 1)
                }
                is PublicationResult.Unavailable -> return report.copy(reachable = false)
            }
        }
        return report
    }

    /**
     * Fetches events of [kinds] newer than this pass's own cursor and stores the authentic ones.
     *
     * The cursor belongs to the pass (table `sync_cursor`), not to the whole local store: events that
     * arrive by other means (followed authors, bundled contents) never move it, so nothing published
     * in between is skipped. It is advanced only after a complete pass, never beyond the current
     * time (an event dated in the future would otherwise freeze the pull), and full pages are
     * followed with `until` up to [maxPages] pages. A device from before the cursor existed starts
     * from the newest event it holds. An event back-dated by its author below the cursor is still
     * missed; a server-side cursor on the reception date would fix that.
     */
    suspend fun pull(kinds: List<Int>, limit: Int = 200, maxPages: Int = 5): SyncReport {
        val name = cursorName(kinds)
        val since = local.cursor(name) ?: local.latestCreatedAt(kinds)
        var until: Long? = null
        var pulled = 0
        var newest: Long? = null
        var pages = 0
        while (pages < maxPages) {
            pages++
            val result = remote.fetch(RemoteFilter(kinds = kinds, since = since, until = until, limit = limit))
            val events = when (result) {
                is FetchResult.Unavailable -> return SyncReport(pulled = pulled, reachable = false)
                is FetchResult.Events -> result.events
            }
            pulled += storeAuthentic(events)
            events.maxOfOrNull { it.createdAt }?.let { newest = maxOf(newest ?: it, it) }
            if (events.size < limit) break
            val oldest = events.minOf { it.createdAt }
            if (until != null && oldest >= until) break // a whole page at one second: stop rather than loop
            until = oldest
        }
        val now = local.now()
        val advanceTo = listOfNotNull(newest, since).maxOrNull()?.coerceAtMost(now)
        if (advanceTo != null) local.setCursor(name, advanceTo)
        return SyncReport(pulled = pulled)
    }

    /** Pushes then pulls. */
    suspend fun sync(kinds: List<Int>): SyncReport {
        val up = publishPending()
        if (!up.reachable) return up
        return up + pull(kinds)
    }

    private fun storeAuthentic(events: List<SignedEvent>): Int =
        events.count { event -> event.verify() && local.store(event, pending = false) == LocalStoreOutcome.STORED }

    companion object {
        /** The name of the general pull's cursor for a set of kinds. */
        fun cursorName(kinds: List<Int>): String = "pull:" + kinds.sorted().joinToString(",")
    }
}
