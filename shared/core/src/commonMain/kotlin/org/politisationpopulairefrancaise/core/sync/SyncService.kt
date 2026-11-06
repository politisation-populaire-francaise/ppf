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

    /** Publishes locally pending events, oldest first; stops at the first network failure. */
    suspend fun publishPending(): SyncReport {
        var report = SyncReport()
        for (event in local.pending()) {
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
     * Fetches events of [kinds] newer than what the device already holds and stores the authentic ones.
     * The cursor is the newest `created_at` known locally: an event back-dated by its author could be
     * missed, which a later server-side cursor will fix.
     */
    suspend fun pull(kinds: List<Int>, limit: Int = 200): SyncReport {
        val since = local.latestCreatedAt(kinds)
        return when (val result = remote.fetch(RemoteFilter(kinds = kinds, since = since, limit = limit))) {
            is FetchResult.Unavailable -> SyncReport(reachable = false)
            is FetchResult.Events -> SyncReport(pulled = storeAuthentic(result.events))
        }
    }

    /** Pushes then pulls. */
    suspend fun sync(kinds: List<Int>): SyncReport {
        val up = publishPending()
        if (!up.reachable) return up
        return up + pull(kinds)
    }

    private fun storeAuthentic(events: List<SignedEvent>): Int =
        events.count { event -> event.verify() && local.store(event, pending = false) == LocalStoreOutcome.STORED }
}
