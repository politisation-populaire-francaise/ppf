package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * A part of the application able to tell, on the device, what is new since a date: unread
 * messages, a followed content updated, an answered call that changed. Each space plugs its own
 * source; none of them asks anything to the server that reveals what the user follows (D-044).
 */
interface NotificationSource {
    /** Stable name, used to remember how far this source was read. */
    val name: String

    /** The novelties after [after] (seconds, exclusive), or since the beginning when null. */
    suspend fun collect(after: Long?): List<NotificationCandidate>
}

/**
 * What the platform shows in the system's notifications for one category. [count] novelties are
 * grouped; [title] and [detail] are present only when the user allowed details (otherwise the
 * platform shows a generic text such as "3 nouveaux messages"). [silent] asks no sound, vibration
 * nor banner. The lock screen never shows any of it: the platform uses a generic public version.
 */
data class SystemNotification(
    val category: NotificationCategory,
    val count: Int,
    val title: String? = null,
    val detail: String? = null,
    val target: String? = null,
    val silent: Boolean = false,
)

/** The platform side: posts notifications to the system (Android notification channels, desktop tray). */
interface SystemNotifier {
    fun post(notification: SystemNotification)
}

/** What one round of [NotificationEngine.check] did. */
data class NotificationReport(val added: Int = 0, val posted: List<SystemNotification> = emptyList())

/**
 * Turns what the sources find into entries of the centre and system notifications, following the
 * user's [NotificationSettings]. Called after each synchronization: when the app is open, and when
 * an empty wake-up arrives from the server (contract docs/integration/notifications.md).
 *
 * A source is read only if its category is enabled; on the first reading of a source, only what is
 * newer than [firstLookBackSeconds] is notified, so enabling notifications does not flood the user
 * with old novelties. A category [NotificationCategory.alwaysInCentre] is read even while it is off,
 * from the start the first time (its entries are current matters of the account, not a stream),
 * and reaches the system only while it is on.
 */
class NotificationEngine(
    private val settings: () -> NotificationSettings,
    private val center: NotificationCenterStore,
    private val sources: List<Pair<NotificationCategory, NotificationSource>>,
    private val notifier: SystemNotifier?,
    private val clock: () -> Long = ::currentEpochSeconds,
    private val firstLookBackSeconds: Long = 24L * 3600,
) {
    suspend fun check(): NotificationReport {
        val current = settings()
        val found = mutableListOf<NotificationCandidate>()
        val now = clock()
        for ((category, source) in sources) {
            if (!current.isEnabled(category) && !category.alwaysInCentre) continue
            val mark = center.mark(source.name)
            val after = mark ?: if (category.alwaysInCentre) null else now - firstLookBackSeconds
            val candidates = source.collect(after).filter { it.category == category && (after == null || it.createdAt > after) }
            found += candidates
            val seen = candidates.maxOfOrNull { minOf(it.createdAt, now) }
            when {
                after == null -> seen?.let { center.setMark(source.name, it) }
                else -> center.setMark(source.name, maxOf(after, seen ?: after))
            }
        }
        return deliverFound(found, current)
    }

    /**
     * Delivers novelties found outside the sources, by a screen that learnt them from the server
     * (the account of the device declared compromised): the same rules as [check], and an id already
     * in the centre is never notified twice.
     */
    fun deliver(candidates: List<NotificationCandidate>): NotificationReport {
        val current = settings()
        return deliverFound(candidates.filter { current.isEnabled(it.category) || it.category.alwaysInCentre }, current)
    }

    /** Marks every source as read up to now, without notifying (used when a category is enabled). */
    fun skipToNow(category: NotificationCategory) {
        // Read even while off: nothing piled up that could flood the user.
        if (category.alwaysInCentre) return
        val now = clock()
        sources.filter { it.first == category }.forEach { center.setMark(it.second.name, now) }
    }

    private fun deliverFound(found: List<NotificationCandidate>, current: NotificationSettings): NotificationReport {
        val added = center.add(found)
        val posted = group(added.filter { current.isEnabled(it.category) }, current)
        posted.forEach { notifier?.post(it) }
        return NotificationReport(added.size, posted)
    }

    private fun group(added: List<NotificationCandidate>, current: NotificationSettings): List<SystemNotification> =
        added.groupBy { it.category }.entries.sortedBy { it.key.ordinal }.map { (category, items) ->
            val single = items.singleOrNull()
            SystemNotification(
                category = category,
                count = items.size,
                // The titles of the account's alerts are fixed codes, never personal content.
                title = if (current.showDetails || category.alwaysInCentre) single?.title else null,
                detail = if (current.showDetails) single?.detail else null,
                target = single?.target ?: items.mapNotNull { it.target }.distinct().singleOrNull(),
                silent = current.totalSilence,
            )
        }
}
