package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * Something new that may deserve a notification, found by a [NotificationSource] on the device.
 * [id] must be stable (the same novelty always gives the same id) so it is never notified twice.
 * [title] and [detail] are shown only once the phone is unlocked and only if the user allowed
 * details; [target] is an internal link (`ppf:<space>/...`) opened when the notification is tapped.
 */
data class NotificationCandidate(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val detail: String? = null,
    val target: String? = null,
    val createdAt: Long,
)

/** An entry of the notification centre (S-24). */
data class NotificationEntry(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val detail: String?,
    val target: String?,
    val createdAt: Long,
    val read: Boolean,
)

/**
 * The notification centre, on the device only: what the device notified, newest first. Entries
 * older than [retentionSeconds] or beyond [maxEntries] are dropped; the user can empty it at once.
 */
class NotificationCenterStore(
    private val database: PpfDatabase,
    private val clock: () -> Long = ::currentEpochSeconds,
    private val retentionSeconds: Long = DEFAULT_RETENTION_SECONDS,
    private val maxEntries: Long = DEFAULT_MAX_ENTRIES,
) {
    private val queries get() = database.notificationsQueries

    /** Adds the candidates not already in the centre; returns those actually added. */
    fun add(candidates: List<NotificationCandidate>): List<NotificationCandidate> = database.transactionWithResult {
        val added = candidates.filter { candidate ->
            val before = queries.hasEntry(candidate.id).executeAsOne() > 0
            if (before) {
                false
            } else {
                queries.insertEntry(candidate.id, candidate.category.key, candidate.title, candidate.detail, candidate.target, candidate.createdAt)
                true
            }
        }
        prune()
        added
    }

    fun entries(limit: Long = maxEntries): List<NotificationEntry> =
        queries.selectEntries(limit).executeAsList().mapNotNull { row ->
            NotificationCategory.fromKey(row.category)?.let {
                NotificationEntry(row.id, it, row.title, row.detail, row.target, row.created_at, row.is_read)
            }
        }

    fun unreadCount(): Long = queries.countUnreadEntries().executeAsOne()

    fun markRead(id: String) = queries.markEntryRead(id)

    fun markAllRead() = queries.markAllEntriesRead()

    fun delete(id: String) = queries.deleteEntry(id)

    /** Empties the centre and forgets how far the sources were read (used when the account is forgotten). */
    fun clear() = database.transaction {
        queries.deleteAllEntries()
        queries.deleteAllMarks()
    }

    internal fun mark(source: String): Long? = queries.getMark(source).executeAsOneOrNull()

    internal fun setMark(source: String, seenUntil: Long) = queries.setMark(source, seenUntil)

    private fun prune() {
        queries.deleteEntriesBefore(clock() - retentionSeconds)
        queries.deleteEntriesBeyond(maxEntries)
    }

    companion object {
        const val DEFAULT_RETENTION_SECONDS: Long = 30L * 24 * 3600
        const val DEFAULT_MAX_ENTRIES: Long = 200
    }
}
