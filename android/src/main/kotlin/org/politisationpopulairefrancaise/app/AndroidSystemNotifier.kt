package org.politisationpopulairefrancaise.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.SystemNotification
import org.politisationpopulairefrancaise.core.notifications.SystemNotifier

/**
 * The system notifications of Android: one channel per category (the user can also silence each
 * one in the system settings) and one "silence" channel for the "silence total" option. The lock
 * screen only ever shows the generic public version, "PPF : nouvelle activité" (S-25); the sender
 * and the excerpt appear once unlocked, and only if the user allowed details.
 */
class AndroidSystemNotifier(private val context: Context) : SystemNotifier {

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val channels = NotificationCategory.entries.map { category ->
            NotificationChannel(channelOf(category), context.getString(labelOf(category)), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
            }
        } + NotificationChannel(SILENT_CHANNEL, context.getString(R.string.notification_channel_silent), NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(channels)
    }

    override fun post(notification: SystemNotification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // The notification centre of the app still has it.
        }
        val channel = if (notification.silent) SILENT_CHANNEL else channelOf(notification.category)
        val generic = context.resources.getQuantityString(countTextOf(notification.category), notification.count, notification.count)
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .build()
        val notificationBuilder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title?.let { shortKey(it) } ?: context.getString(labelOf(notification.category)))
            .setContentText(notification.detail ?: generic)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(notification.target))
        manager.notify(notification.category.ordinal, notificationBuilder.build())
    }

    private fun openApp(target: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (target != null) data = Uri.parse(target)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A public key is shown shortened, like everywhere else in the application. */
    private fun shortKey(title: String): String =
        if (title.length == 64 && title.all { it in '0'..'9' || it in 'a'..'f' }) title.take(8) + "…" else title

    private fun channelOf(category: NotificationCategory): String = "ppf-" + category.key

    private fun labelOf(category: NotificationCategory): Int = when (category) {
        NotificationCategory.MESSAGES -> R.string.notification_channel_messages
        NotificationCategory.MOBILISATIONS -> R.string.notification_channel_mobilisations
        NotificationCategory.VOTES -> R.string.notification_channel_votes
        NotificationCategory.FOLLOWED -> R.string.notification_channel_followed
        NotificationCategory.TEAM -> R.string.notification_channel_team
    }

    private fun countTextOf(category: NotificationCategory): Int = when (category) {
        NotificationCategory.MESSAGES -> R.plurals.notification_count_messages
        else -> R.plurals.notification_count_other
    }

    private companion object {
        const val SILENT_CHANNEL = "ppf-silence"
    }
}
