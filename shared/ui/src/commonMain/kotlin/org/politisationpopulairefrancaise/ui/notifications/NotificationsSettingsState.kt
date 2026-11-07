package org.politisationpopulairefrancaise.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationSettings
import org.politisationpopulairefrancaise.core.notifications.Notifications
import org.politisationpopulairefrancaise.core.notifications.PushManager
import org.politisationpopulairefrancaise.core.notifications.PushScope
import org.politisationpopulairefrancaise.core.notifications.PushState
import org.politisationpopulairefrancaise.ui.AppDependencies

/** The loosenings that are confirmed first, each with its own warning. */
enum class NotificationWarning { DETAILS, PUBLIC_WAKE, MAILBOX_WAKE }

/** A loosening waiting for the user's confirmation: [next] is saved only if confirmed. */
data class PendingRelaxation(val warning: NotificationWarning, val next: NotificationSettings)

/**
 * The notification settings of "Mon espace" (M31, contract docs/integration/notifications.md):
 * the categories, what is shown, and the wake-ups asked from the server. Everything starts at the
 * most protective setting; every change that [NotificationSettings.relaxes] them waits in
 * [pending] until the user confirms the warning, and stays one tap away from being undone.
 *
 * Enabling a category first asks the system's permission; refused, the category is enabled anyway
 * and [permissionRefused] tells the screen to explain that the application's own centre still shows
 * them. A category enabled starts from now ([org.politisationpopulairefrancaise.core.notifications.NotificationEngine.skipToNow]),
 * so old novelties do not flood the centre. After each saved change, [push] is brought in line.
 */
class NotificationsSettingsState(
    private val notifications: Notifications,
    private val push: PushManager?,
    private val requestPermission: ((granted: Boolean) -> Unit) -> Unit,
    private val scope: CoroutineScope,
    /** The address of our ntfy server, to type in the distributor application (contract, section 6). */
    val distributorServer: String = DEFAULT_DISTRIBUTOR_SERVER,
) {
    var settings: NotificationSettings by mutableStateOf(NotificationSettings.MOST_PROTECTIVE)
        private set

    /** True when an account exists: the mailbox wake-up and the messages need one. */
    var hasAccount: Boolean by mutableStateOf(false)
        private set

    var pending: PendingRelaxation? by mutableStateOf(null)
        private set

    /** The system refused to show notifications; they still go to the centre of the application. */
    var permissionRefused: Boolean by mutableStateOf(false)
        private set

    var publicState: PushState by mutableStateOf(PushState.OFF)
        private set

    var mailboxState: PushState by mutableStateOf(PushState.OFF)
        private set

    /** Without a server (or a push system), nothing can wake the device: notifications arrive while the app is open. */
    val hasPush: Boolean get() = push != null

    fun load() {
        settings = notifications.settings.load()
        refreshPushStates()
    }

    fun setAccount(present: Boolean) {
        hasAccount = present
        load()
    }

    /** Reads again where the push registrations stand (their answers come back asynchronously). */
    fun refreshPushStates() {
        val push = push ?: return
        publicState = push.state(PushScope.PUBLIC)
        mailboxState = push.state(PushScope.MAILBOX)
    }

    fun toggleCategory(category: NotificationCategory) {
        val current = settings
        if (current.isEnabled(category)) {
            val enabled = current.enabled - category
            // Tightening only: the wake-ups that no longer serve anything are turned off with it.
            save(
                current.copy(
                    enabled = enabled,
                    mailboxWake = current.mailboxWake && category != NotificationCategory.MESSAGES,
                    publicWake = current.publicWake && enabled.isNotEmpty(),
                ),
            )
            return
        }
        if (category == NotificationCategory.MESSAGES && !hasAccount) return
        requestPermission { granted ->
            permissionRefused = !granted
            notifications.engine.skipToNow(category)
            save(settings.copy(enabled = settings.enabled + category))
        }
    }

    fun setShowDetails(on: Boolean) = propose(NotificationWarning.DETAILS, settings.copy(showDetails = on))

    fun setPublicWake(on: Boolean) {
        if (on && (push == null || settings.enabled.isEmpty())) return
        propose(NotificationWarning.PUBLIC_WAKE, settings.copy(publicWake = on))
    }

    fun setMailboxWake(on: Boolean) {
        if (on && (push == null || !hasAccount || !settings.isEnabled(NotificationCategory.MESSAGES))) return
        propose(NotificationWarning.MAILBOX_WAKE, settings.copy(mailboxWake = on))
    }

    fun setTotalSilence(on: Boolean) = save(settings.copy(totalSilence = on))

    /** "Activer quand même": the loosening is saved. */
    fun confirm() {
        val waiting = pending ?: return
        pending = null
        save(waiting.next)
    }

    /** "Garder désactivé": nothing changes. */
    fun cancel() {
        pending = null
    }

    /** Asks the server again (after installing a distributor, or when it was unreachable). */
    fun retryPush() {
        val push = push ?: return
        val current = settings
        scope.launch {
            runCatching { push.apply(current) }
            refreshPushStates()
        }
    }

    private fun propose(warning: NotificationWarning, next: NotificationSettings) {
        if (next == settings) return
        if (settings.relaxes(next)) pending = PendingRelaxation(warning, next) else save(next)
    }

    private fun save(next: NotificationSettings) {
        settings = next
        notifications.settings.save(next)
        val push = push ?: return
        scope.launch {
            runCatching { push.apply(next) }
            refreshPushStates()
        }
    }

    companion object {
        /** Our production ntfy server (contract, section 6). */
        const val DEFAULT_DISTRIBUTOR_SERVER: String = "https://push.politisation-populaire-francaise.org"
    }
}

/** The notification settings for the whole run of the application; null without notifications (tests, previews). */
@Composable
fun rememberNotificationsSettings(dependencies: AppDependencies, accountKey: String?): NotificationsSettingsState? {
    val notifications = dependencies.notifications ?: return null
    val scope = rememberCoroutineScope()
    val state = remember(dependencies) {
        NotificationsSettingsState(notifications, dependencies.push, dependencies.requestNotificationPermission, scope)
    }
    LaunchedEffect(state, accountKey) { state.setAccount(accountKey != null) }
    return state
}
