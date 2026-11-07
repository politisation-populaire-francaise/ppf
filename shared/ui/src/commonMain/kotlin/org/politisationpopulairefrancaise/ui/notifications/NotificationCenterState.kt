package org.politisationpopulairefrancaise.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.notifications.NotificationEntry
import org.politisationpopulairefrancaise.core.notifications.Notifications
import org.politisationpopulairefrancaise.ui.AppDependencies

/**
 * The notification centre (S-24) as the screens need it: its entries, newest first, and the unread
 * count of the bell. Everything stays on the device.
 */
class NotificationCenterState(private val notifications: Notifications) {

    var entries: List<NotificationEntry> by mutableStateOf(emptyList())
        private set

    var unreadCount: Long by mutableStateOf(0)
        private set

    /** Whether the user allowed the sender and an excerpt of the messages to be shown. */
    var showDetails: Boolean by mutableStateOf(false)
        private set

    fun reload() {
        val center = notifications.center
        entries = center.entries()
        unreadCount = center.unreadCount()
        showDetails = notifications.settings.load().showDetails
    }

    fun markRead(id: String) {
        notifications.center.markRead(id)
        reload()
    }

    fun markAllRead() {
        notifications.center.markAllRead()
        reload()
    }

    /**
     * Empties the centre. Entry by entry, so that the sources keep how far they were read and the
     * same novelties do not come back at the next round.
     */
    fun clearAll() {
        val center = notifications.center
        center.entries().forEach { center.delete(it.id) }
        reload()
    }

    /** One round while the application is open, only when at least one category is enabled. */
    suspend fun tick() {
        if (notifications.settings.load().enabled.isNotEmpty()) {
            runCatching { withContext(Dispatchers.Default) { notifications.onWake() } }
        }
        reload()
    }

    companion object {
        /** How often the open application looks for what is new (contract, section 5). */
        const val ROUND_PERIOD_MILLIS: Long = 60_000
    }
}

/**
 * The centre for the whole run of the application, filled while it is open: one round at start,
 * then every [NotificationCenterState.ROUND_PERIOD_MILLIS], so the computer and the phones without a
 * distributor still get their notifications. Null without notifications.
 */
@Composable
fun rememberNotificationCenter(dependencies: AppDependencies): NotificationCenterState? {
    val notifications = dependencies.notifications
    val state = remember(notifications) { notifications?.let(::NotificationCenterState) }
    LaunchedEffect(state) {
        val center = state ?: return@LaunchedEffect
        while (true) {
            center.tick()
            delay(NotificationCenterState.ROUND_PERIOD_MILLIS)
        }
    }
    return state
}
