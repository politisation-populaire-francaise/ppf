package org.politisationpopulairefrancaise.core.notifications

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/**
 * The kinds of notifications the user can enable one by one (M31, S-23). Each is also a channel of
 * the system's notifications, so it can be silenced there too. [key] is stored and must not change.
 */
enum class NotificationCategory(val key: String) {
    /** Private messages and rooms (M14). */
    MESSAGES("messages"),

    /** Mobilisations, events and calls the user answered (Agir). */
    MOBILISATIONS("mobilisations"),

    /** Petitions, votes of the Parliament, consultations (Décider, Voir). */
    VOTES("votes"),

    /** Contents the user follows (figures, persons, bills, sheets). */
    FOLLOWED("suivis"),

    /** Messages of the PPF team (replies to the user's requests, service status). */
    TEAM("equipe"),
    ;

    companion object {
        fun fromKey(key: String): NotificationCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Which push registrations the device asked for (contract, section 2). Both are off by default. */
enum class PushScope(val key: String) {
    /** Anonymous: woken when new public contents are published, never linked to an account. */
    PUBLIC("public"),

    /** Linked to the account's public key: woken when a private message arrives for it. */
    MAILBOX("mailbox"),
}

/**
 * The user's notification settings. The defaults are the most protective ones (principle 10):
 * nothing enabled, no wake-up asked from the server, no sender nor excerpt shown.
 * The lock screen never shows any content, whatever the settings (S-25).
 */
data class NotificationSettings(
    val enabled: Set<NotificationCategory> = emptySet(),
    /** Anonymous wake-ups for public contents (PushScope.PUBLIC). */
    val publicWake: Boolean = false,
    /** Wake-ups for private messages, linked to the account key (PushScope.MAILBOX). */
    val mailboxWake: Boolean = false,
    /**
     * Shows the sender and an excerpt once the phone is unlocked. Off by default: someone looking
     * over the shoulder would otherwise read political exchanges. Relaxing it asks a confirmation.
     */
    val showDetails: Boolean = false,
    /** "Silence total": notifications go to the centre only, without sound, vibration or banner. */
    val totalSilence: Boolean = false,
) {
    fun isEnabled(category: NotificationCategory): Boolean = category in enabled

    /** True when this change makes the settings less protective, so a warning must be confirmed first. */
    fun relaxes(next: NotificationSettings): Boolean =
        (!showDetails && next.showDetails) || (!mailboxWake && next.mailboxWake) || (!publicWake && next.publicWake)

    companion object {
        val MOST_PROTECTIVE: NotificationSettings = NotificationSettings()
    }
}

/** Keeps the [NotificationSettings] on the device only. */
class NotificationSettingsStore(private val database: PpfDatabase) {

    private val queries get() = database.notificationsQueries

    fun load(): NotificationSettings {
        fun flag(name: String): Boolean = queries.getSetting(name).executeAsOneOrNull() == "1"
        val enabled = queries.getSetting(ENABLED).executeAsOneOrNull()
            ?.split(',')
            ?.mapNotNull { NotificationCategory.fromKey(it.trim()) }
            ?.toSet()
            .orEmpty()
        return NotificationSettings(
            enabled = enabled,
            publicWake = flag(PUBLIC_WAKE),
            mailboxWake = flag(MAILBOX_WAKE),
            showDetails = flag(SHOW_DETAILS),
            totalSilence = flag(TOTAL_SILENCE),
        )
    }

    fun save(settings: NotificationSettings) = database.transaction {
        queries.setSetting(ENABLED, settings.enabled.sortedBy { it.ordinal }.joinToString(",") { it.key })
        queries.setSetting(PUBLIC_WAKE, settings.publicWake.bit())
        queries.setSetting(MAILBOX_WAKE, settings.mailboxWake.bit())
        queries.setSetting(SHOW_DETAILS, settings.showDetails.bit())
        queries.setSetting(TOTAL_SILENCE, settings.totalSilence.bit())
    }

    /** Back to the most protective defaults (used when the account is forgotten). */
    fun reset() = queries.deleteAllSettings()

    private fun Boolean.bit(): String = if (this) "1" else "0"

    private companion object {
        const val ENABLED = "enabled"
        const val PUBLIC_WAKE = "public_wake"
        const val MAILBOX_WAKE = "mailbox_wake"
        const val SHOW_DETAILS = "show_details"
        const val TOTAL_SILENCE = "total_silence"
    }
}
