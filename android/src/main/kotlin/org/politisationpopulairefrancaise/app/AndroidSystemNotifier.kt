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
import org.politisationpopulairefrancaise.core.notifications.AccountAlerts
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.SystemNotification
import org.politisationpopulairefrancaise.core.notifications.SystemNotifier
import org.politisationpopulairefrancaise.ui.agir.security.DiscreetMode

/**
 * The system notifications of Android: one channel per category (the user can also silence each
 * one in the system settings) and one "silence" channel for the "silence total" option. The lock
 * screen only ever shows the generic public version, "PPF : nouvelle activité" (S-25); the sender
 * and the excerpt appear once unlocked, and only if the user allowed details. In discreet mode
 * (S-45, the launcher shows "Outils"), everything turns neutral and the lock screen shows nothing.
 */
class AndroidSystemNotifier(private val context: Context) : SystemNotifier {

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** The mode the channels were last named for (null before the first creation). */
    @Volatile
    private var channelsDiscreet: Boolean? = null

    /**
     * Creates (or renames) the channels. In discreet mode (S-45, the launcher shows "Outils"), every
     * channel takes a neutral name in the system settings and shows nothing on the lock screen.
     */
    fun createChannels(discreet: Boolean = DiscreetMode.isOn(context)) {
        val lockScreen = if (discreet) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE
        val channels = NotificationCategory.entries.map { category ->
            val name = if (discreet) context.getString(R.string.notification_channel_neutral) else context.getString(labelOf(category))
            NotificationChannel(channelOf(category), name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = lockScreen
                setShowBadge(!discreet)
            }
        } + NotificationChannel(
            SILENT_CHANNEL,
            context.getString(if (discreet) R.string.notification_channel_neutral_silent else R.string.notification_channel_silent),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(channels)
        channelsDiscreet = discreet
    }

    override fun post(notification: SystemNotification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // The notification centre of the app still has it.
        }
        // Read before each notification: the user may have switched to "Outils" since the start.
        val discreet = DiscreetMode.isOn(context)
        if (channelsDiscreet != discreet) createChannels(discreet)
        val channel = if (notification.silent) SILENT_CHANNEL else channelOf(notification.category)
        val builder = if (discreet) discreetBuilder(channel) else usualBuilder(channel, notification)
        manager.notify(notification.category.ordinal, builder.setContentIntent(openApp(notification.target)).build())
    }

    /** The usual notification: its category, then the sender and excerpt only if the user allowed them. */
    private fun usualBuilder(channel: String, notification: SystemNotification): Notification.Builder {
        val generic = context.resources.getQuantityString(countTextOf(notification.category), notification.count, notification.count)
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .build()
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleOf(notification))
            .setContentText(textOf(notification, generic))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
    }

    /**
     * Discreet mode (S-45): the neutral title "Outils", the neutral small icon, one neutral line, no
     * category, no sender, no excerpt, no count, and nothing at all on the lock screen.
     */
    private fun discreetBuilder(channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_neutral)
            .setContentTitle(context.getString(R.string.app_name_neutral))
            .setContentText(context.getString(R.string.notification_neutral_text))
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    private fun openApp(target: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (target != null) data = Uri.parse(target)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun titleOf(notification: SystemNotification): String =
        if (notification.category == NotificationCategory.ACCOUNT) {
            context.getString(labelOf(notification.category))
        } else {
            notification.title?.let { shortKey(it) } ?: context.getString(labelOf(notification.category))
        }

    /** The account's alerts carry a fixed code (S-146): a fixed text, never the contact's key. */
    private fun textOf(notification: SystemNotification, generic: String): String {
        if (notification.category != NotificationCategory.ACCOUNT) return notification.detail ?: generic
        val text = when (notification.title) {
            AccountAlerts.PHRASE_CHECK -> R.string.notification_account_phrase
            AccountAlerts.OWN_COMPROMISED -> R.string.notification_account_own
            AccountAlerts.CONTACT_COMPROMISED -> R.string.notification_account_contact
            else -> null
        }
        return text?.let(context::getString) ?: generic
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
        NotificationCategory.ACCOUNT -> R.string.notification_channel_account
    }

    private fun countTextOf(category: NotificationCategory): Int = when (category) {
        NotificationCategory.MESSAGES -> R.plurals.notification_count_messages
        else -> R.plurals.notification_count_other
    }

    private companion object {
        const val SILENT_CHANNEL = "ppf-silence"
    }
}
