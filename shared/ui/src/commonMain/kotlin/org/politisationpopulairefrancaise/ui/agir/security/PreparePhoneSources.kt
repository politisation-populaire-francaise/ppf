package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.compositionLocalOf
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState

/**
 * The notification settings of Mon espace (M31, pole Communauté), provided by the shell so that
 * « Préparer mon téléphone » (AG-56) can tell whether the notifications are discreet and hide their
 * details in one gesture. Null without notifications (tests, outside the shell, a platform without them).
 */
val LocalPreparePhoneNotifications = compositionLocalOf<NotificationsSettingsState?> { null }
