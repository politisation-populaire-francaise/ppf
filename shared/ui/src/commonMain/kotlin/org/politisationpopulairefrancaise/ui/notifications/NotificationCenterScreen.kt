package org.politisationpopulairefrancaise.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.notifications.AccountAlerts
import org.politisationpopulairefrancaise.core.voir.parlement.VoteAlertCodes
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationEntry
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.listTimeLabel
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.notif_center_back
import org.politisationpopulairefrancaise.ui.resources.notif_center_cancel
import org.politisationpopulairefrancaise.ui.resources.notif_center_clear
import org.politisationpopulairefrancaise.ui.resources.notif_center_clear_confirm
import org.politisationpopulairefrancaise.ui.resources.notif_center_clear_text
import org.politisationpopulairefrancaise.ui.resources.notif_center_clear_title
import org.politisationpopulairefrancaise.ui.resources.notif_center_description
import org.politisationpopulairefrancaise.ui.resources.notif_center_empty
import org.politisationpopulairefrancaise.ui.resources.notif_center_mark_all
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_contact
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_contact_detail
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_own
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_own_detail
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_phrase
import org.politisationpopulairefrancaise.ui.resources.notif_center_account_phrase_detail
import org.politisationpopulairefrancaise.ui.resources.notif_center_message_from
import org.politisationpopulairefrancaise.ui.resources.notif_center_message_generic
import org.politisationpopulairefrancaise.ui.resources.notif_center_settings
import org.politisationpopulairefrancaise.ui.resources.notif_center_title
import org.politisationpopulairefrancaise.ui.resources.notif_center_unread
import org.politisationpopulairefrancaise.ui.resources.voir_alert_my_officials
import org.politisationpopulairefrancaise.ui.resources.voir_alert_title
import org.politisationpopulairefrancaise.ui.resources.voir_alert_word
import org.politisationpopulairefrancaise.ui.resources.voir_alert_word_generic
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the notification centre. */
object NotificationCenterTags {
    const val SCREEN = "notification-center"
    const val BACK = "notification-center-back"
    const val ENTRY = "notification-entry-" // followed by the entry id
    const val UNREAD = "notification-unread-" // followed by the entry id
    const val MARK_ALL = "notification-center-mark-all"
    const val CLEAR = "notification-center-clear"
    const val CLEAR_CONFIRM = "notification-center-clear-confirm"
    const val CLEAR_CANCEL = "notification-center-clear-cancel"
    const val EMPTY = "notification-center-empty"
    const val SETTINGS = "notification-center-settings"
}

private fun NotificationCategory.icon(): ImageVector = when (this) {
    NotificationCategory.MESSAGES -> Lucide.MessageCircle
    NotificationCategory.MOBILISATIONS -> Lucide.Megaphone
    NotificationCategory.VOTES -> Lucide.Vote
    NotificationCategory.FOLLOWED -> Lucide.Bookmark
    NotificationCategory.TEAM -> Lucide.ShieldCheck
    NotificationCategory.ACCOUNT -> Lucide.KeyRound
}

/**
 * The notification centre (S-24): what the device notified, newest first, kept on the device only.
 * Unread entries say so in words, not only by their look. Tapping an entry marks it read and
 * [onOpen] opens what it points to when the application knows the way. On phones it takes the whole
 * screen; the system's back closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterScreen(
    center: NotificationCenterState,
    windowClass: WindowClass,
    onBack: () -> Unit,
    onOpen: (NotificationEntry) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SystemBack(enabled = true, onBack = onBack)
    val now = currentEpochSeconds()

    Column(
        modifier = modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState())
            .padding(if (compact) 16.dp else 24.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp).testTag(NotificationCenterTags.SCREEN), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.notif_center_back), onBack, Modifier.testTag(NotificationCenterTags.BACK), tint = palette.ink)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.notif_center_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(Res.string.notif_center_description), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PpfSecondaryButton(
                    onClick = center::markAllRead,
                    text = stringResource(Res.string.notif_center_mark_all),
                    enabled = center.unreadCount > 0,
                    icon = Lucide.Check,
                    modifier = Modifier.testTag(NotificationCenterTags.MARK_ALL),
                )
                PpfTextButton(
                    onClick = { confirmClear = true },
                    text = stringResource(Res.string.notif_center_clear),
                    enabled = center.entries.isNotEmpty(),
                    color = palette.accentInk,
                    modifier = Modifier.testTag(NotificationCenterTags.CLEAR),
                )
                PpfTextButton(
                    onClick = onSettings,
                    text = stringResource(Res.string.notif_center_settings),
                    modifier = Modifier.testTag(NotificationCenterTags.SETTINGS),
                )
            }
            if (center.entries.isEmpty()) {
                PpfCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(Res.string.notif_center_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(NotificationCenterTags.EMPTY),
                    )
                }
            }
            center.entries.forEach { entry ->
                EntryCard(entry, center.showDetails, now) { onOpen(entry) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
            title = { Text(stringResource(Res.string.notif_center_clear_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(Res.string.notif_center_clear_text), style = MaterialTheme.typography.bodyLarge) },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.testTag(NotificationCenterTags.CLEAR_CANCEL)) {
                    Text(stringResource(Res.string.notif_center_cancel), color = palette.brand)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; center.clearAll() }, modifier = Modifier.testTag(NotificationCenterTags.CLEAR_CONFIRM)) {
                    Text(stringResource(Res.string.notif_center_clear_confirm), color = palette.accentInk)
                }
            },
        )
    }
}

@Composable
private fun EntryCard(entry: NotificationEntry, showDetails: Boolean, now: Long, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    // A private message names its sender and shows an excerpt only if the user allowed details.
    val (title, detail) = when (entry.category) {
        NotificationCategory.MESSAGES ->
            if (showDetails) stringResource(Res.string.notif_center_message_from, shortNpub(entry.title)) to entry.detail
            else stringResource(Res.string.notif_center_message_generic) to null
        // The account's alerts (S-146) carry a fixed code, translated here; the detail is at most a key.
        NotificationCategory.ACCOUNT -> when (entry.title) {
            AccountAlerts.PHRASE_CHECK ->
                stringResource(Res.string.notif_center_account_phrase) to stringResource(Res.string.notif_center_account_phrase_detail)
            AccountAlerts.OWN_COMPROMISED ->
                stringResource(Res.string.notif_center_account_own) to stringResource(Res.string.notif_center_account_own_detail)
            AccountAlerts.CONTACT_COMPROMISED ->
                stringResource(Res.string.notif_center_account_contact) to
                    stringResource(Res.string.notif_center_account_contact_detail, shortNpub(entry.detail.orEmpty()))
            else -> entry.title to entry.detail
        }
        // « Mes élus ont voté » (VO-34): a fixed title; the followed word only if the user allowed details.
        NotificationCategory.VOTES -> when (entry.title) {
            VoteAlertCodes.MY_OFFICIALS -> {
                val count = entry.detail?.toIntOrNull() ?: 0
                stringResource(Res.string.voir_alert_title) to pluralStringResource(Res.plurals.voir_alert_my_officials, count, count)
            }
            VoteAlertCodes.WORD ->
                stringResource(Res.string.voir_alert_title) to
                    (entry.detail?.takeIf { showDetails }?.let { stringResource(Res.string.voir_alert_word, it) } ?: stringResource(Res.string.voir_alert_word_generic))
            else -> entry.title to entry.detail
        }
        else -> entry.title to entry.detail
    }
    PpfCard(
        modifier = Modifier.fillMaxWidth().testTag(NotificationCenterTags.ENTRY + entry.id),
        onClick = onClick,
        selected = !entry.read,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(entry.category.icon(), contentDescription = null, tint = if (entry.read) palette.inkMuted else palette.brand, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (!entry.read) {
                    PpfBadge(stringResource(Res.string.notif_center_unread), BadgeStyle.BRAND, Modifier.testTag(NotificationCenterTags.UNREAD + entry.id))
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.read) FontWeight.Normal else FontWeight.Bold,
                    color = palette.ink,
                )
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(entry.category.label()) + " · " + listTimeLabel(entry.createdAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                )
            }
        }
    }
}
