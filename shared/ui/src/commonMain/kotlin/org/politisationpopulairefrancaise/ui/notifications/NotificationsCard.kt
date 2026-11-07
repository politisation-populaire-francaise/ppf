package org.politisationpopulairefrancaise.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.PushState
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.notif_categories_title
import org.politisationpopulairefrancaise.ui.resources.notif_category_followed
import org.politisationpopulairefrancaise.ui.resources.notif_category_followed_hint
import org.politisationpopulairefrancaise.ui.resources.notif_category_messages
import org.politisationpopulairefrancaise.ui.resources.notif_category_messages_hint
import org.politisationpopulairefrancaise.ui.resources.notif_category_messages_no_account
import org.politisationpopulairefrancaise.ui.resources.notif_category_mobilisations
import org.politisationpopulairefrancaise.ui.resources.notif_category_mobilisations_hint
import org.politisationpopulairefrancaise.ui.resources.notif_category_team
import org.politisationpopulairefrancaise.ui.resources.notif_category_team_hint
import org.politisationpopulairefrancaise.ui.resources.notif_category_votes
import org.politisationpopulairefrancaise.ui.resources.notif_category_votes_hint
import org.politisationpopulairefrancaise.ui.resources.notif_details
import org.politisationpopulairefrancaise.ui.resources.notif_details_hint
import org.politisationpopulairefrancaise.ui.resources.notif_display_title
import org.politisationpopulairefrancaise.ui.resources.notif_intro
import org.politisationpopulairefrancaise.ui.resources.notif_mailbox_needs_messages
import org.politisationpopulairefrancaise.ui.resources.notif_mailbox_wake
import org.politisationpopulairefrancaise.ui.resources.notif_mailbox_wake_hint
import org.politisationpopulairefrancaise.ui.resources.notif_no_server
import org.politisationpopulairefrancaise.ui.resources.notif_permission_refused
import org.politisationpopulairefrancaise.ui.resources.notif_public_wake
import org.politisationpopulairefrancaise.ui.resources.notif_public_wake_hint
import org.politisationpopulairefrancaise.ui.resources.notif_public_wake_needs_category
import org.politisationpopulairefrancaise.ui.resources.notif_push_active
import org.politisationpopulairefrancaise.ui.resources.notif_push_no_distributor
import org.politisationpopulairefrancaise.ui.resources.notif_push_refused
import org.politisationpopulairefrancaise.ui.resources.notif_push_retry
import org.politisationpopulairefrancaise.ui.resources.notif_push_stay_without
import org.politisationpopulairefrancaise.ui.resources.notif_push_step_1
import org.politisationpopulairefrancaise.ui.resources.notif_push_step_2
import org.politisationpopulairefrancaise.ui.resources.notif_push_step_3
import org.politisationpopulairefrancaise.ui.resources.notif_push_unreachable
import org.politisationpopulairefrancaise.ui.resources.notif_push_waiting
import org.politisationpopulairefrancaise.ui.resources.notif_silence
import org.politisationpopulairefrancaise.ui.resources.notif_silence_hint
import org.politisationpopulairefrancaise.ui.resources.notif_title
import org.politisationpopulairefrancaise.ui.resources.notif_wake_intro
import org.politisationpopulairefrancaise.ui.resources.notif_wake_title
import org.politisationpopulairefrancaise.ui.resources.notif_warning_confirm
import org.politisationpopulairefrancaise.ui.resources.notif_warning_details
import org.politisationpopulairefrancaise.ui.resources.notif_warning_keep
import org.politisationpopulairefrancaise.ui.resources.notif_warning_mailbox_wake
import org.politisationpopulairefrancaise.ui.resources.notif_warning_public_wake
import org.politisationpopulairefrancaise.ui.resources.notif_warning_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the notification settings. */
object NotificationsTags {
    const val CARD = "notifications-card"
    const val CATEGORY = "notifications-category-" // followed by the category key
    const val DETAILS = "notifications-details"
    const val SILENCE = "notifications-silence"
    const val PUBLIC_WAKE = "notifications-public-wake"
    const val MAILBOX_WAKE = "notifications-mailbox-wake"
    const val PUBLIC_STATE = "notifications-public-state"
    const val MAILBOX_STATE = "notifications-mailbox-state"
    const val NO_SERVER = "notifications-no-server"
    const val PERMISSION_REFUSED = "notifications-permission-refused"
    const val RETRY = "notifications-retry-" // followed by the scope key
    const val WARNING_CONFIRM = "notifications-warning-confirm"
    const val WARNING_KEEP = "notifications-warning-keep"
}

internal fun NotificationCategory.label(): StringResource = when (this) {
    NotificationCategory.MESSAGES -> Res.string.notif_category_messages
    NotificationCategory.MOBILISATIONS -> Res.string.notif_category_mobilisations
    NotificationCategory.VOTES -> Res.string.notif_category_votes
    NotificationCategory.FOLLOWED -> Res.string.notif_category_followed
    NotificationCategory.TEAM -> Res.string.notif_category_team
}

private fun NotificationCategory.hint(): StringResource = when (this) {
    NotificationCategory.MESSAGES -> Res.string.notif_category_messages_hint
    NotificationCategory.MOBILISATIONS -> Res.string.notif_category_mobilisations_hint
    NotificationCategory.VOTES -> Res.string.notif_category_votes_hint
    NotificationCategory.FOLLOWED -> Res.string.notif_category_followed_hint
    NotificationCategory.TEAM -> Res.string.notif_category_team_hint
}

/** How often the card reads again where the wake-ups stand while one of them is on. */
private const val PUSH_STATE_REFRESH_MILLIS = 2_000L

/**
 * "Mon espace": the notifications of the device (M31). Every switch starts off; the categories
 * other than messages need no account. The wake-ups by the server are explained in plain words
 * (what the server learns) and every loosening is confirmed first, the safe choice first.
 */
@Composable
fun NotificationsCard(state: NotificationsSettingsState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val settings = state.settings

    LaunchedEffect(state, settings.publicWake, settings.mailboxWake) {
        while (settings.publicWake || settings.mailboxWake) {
            delay(PUSH_STATE_REFRESH_MILLIS)
            state.refreshPushStates()
        }
    }

    PpfCard(modifier.fillMaxWidth().testTag(NotificationsTags.CARD)) {
        Text(stringResource(Res.string.notif_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.notif_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)

        SectionTitle(Res.string.notif_categories_title)
        NotificationCategory.entries.forEach { category ->
            val needsAccount = category == NotificationCategory.MESSAGES && !state.hasAccount
            SwitchRow(
                label = stringResource(category.label()),
                hint = stringResource(if (needsAccount) Res.string.notif_category_messages_no_account else category.hint()),
                checked = settings.isEnabled(category),
                enabled = !needsAccount || settings.isEnabled(category),
                onChange = { state.toggleCategory(category) },
                tag = NotificationsTags.CATEGORY + category.key,
            )
        }
        if (state.permissionRefused) {
            Spacer(Modifier.height(8.dp))
            InfoBox(Lucide.Info, Modifier.testTag(NotificationsTags.PERMISSION_REFUSED)) {
                Text(stringResource(Res.string.notif_permission_refused), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionTitle(Res.string.notif_display_title)
        SwitchRow(
            label = stringResource(Res.string.notif_details),
            hint = stringResource(Res.string.notif_details_hint),
            checked = settings.showDetails,
            onChange = state::setShowDetails,
            tag = NotificationsTags.DETAILS,
        )
        SwitchRow(
            label = stringResource(Res.string.notif_silence),
            hint = stringResource(Res.string.notif_silence_hint),
            checked = settings.totalSilence,
            onChange = state::setTotalSilence,
            tag = NotificationsTags.SILENCE,
        )

        SectionTitle(Res.string.notif_wake_title)
        if (!state.hasPush) {
            Text(
                stringResource(Res.string.notif_no_server),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(NotificationsTags.NO_SERVER),
            )
        } else {
            Text(stringResource(Res.string.notif_wake_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(4.dp))
            val anyCategory = settings.enabled.isNotEmpty()
            SwitchRow(
                label = stringResource(Res.string.notif_public_wake),
                hint = stringResource(if (anyCategory || settings.publicWake) Res.string.notif_public_wake_hint else Res.string.notif_public_wake_needs_category),
                checked = settings.publicWake,
                enabled = anyCategory || settings.publicWake,
                onChange = state::setPublicWake,
                tag = NotificationsTags.PUBLIC_WAKE,
            )
            if (settings.publicWake) PushStateNote(state, state.publicState, NotificationsTags.PUBLIC_STATE, "public")
            if (state.hasAccount) {
                val messagesOn = settings.isEnabled(NotificationCategory.MESSAGES)
                SwitchRow(
                    label = stringResource(Res.string.notif_mailbox_wake),
                    hint = stringResource(if (messagesOn || settings.mailboxWake) Res.string.notif_mailbox_wake_hint else Res.string.notif_mailbox_needs_messages),
                    checked = settings.mailboxWake,
                    enabled = messagesOn || settings.mailboxWake,
                    onChange = state::setMailboxWake,
                    tag = NotificationsTags.MAILBOX_WAKE,
                )
                if (settings.mailboxWake) PushStateNote(state, state.mailboxState, NotificationsTags.MAILBOX_STATE, "mailbox")
            }
        }
    }

    state.pending?.let { pending -> RelaxationDialog(pending.warning, onKeep = state::cancel, onConfirm = state::confirm) }
}

@Composable
private fun SectionTitle(text: StringResource) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
}

/** A setting that is on or off: the whole row is one switch for screen readers, 56 dp high at least. */
@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    val palette = PpfTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onBrand,
                checkedTrackColor = palette.brand,
                uncheckedThumbColor = palette.inkMuted,
                uncheckedTrackColor = palette.surface1,
                uncheckedBorderColor = palette.borderStrong,
            ),
        )
    }
}

/** Where one wake-up stands, in plain words, with the steps to get a distributor when it is missing. */
@Composable
private fun PushStateNote(state: NotificationsSettingsState, push: PushState, tag: String, scopeKey: String) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (push) {
            PushState.OFF -> Unit
            PushState.ACTIVE -> Text(stringResource(Res.string.notif_push_active), style = MaterialTheme.typography.bodySmall, color = palette.success)
            PushState.WAITING_FOR_ADDRESS -> Text(stringResource(Res.string.notif_push_waiting), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            PushState.NO_DISTRIBUTOR -> {
                Text(stringResource(Res.string.notif_push_no_distributor), style = MaterialTheme.typography.bodyMedium, color = palette.warning)
                Text(stringResource(Res.string.notif_push_step_1), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(stringResource(Res.string.notif_push_step_2, state.distributorServer), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(Res.string.notif_push_step_3), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.notif_push_stay_without), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                RetryButton(state, scopeKey)
            }
            PushState.SERVER_UNREACHABLE -> {
                Text(stringResource(Res.string.notif_push_unreachable), style = MaterialTheme.typography.bodyMedium, color = palette.warning)
                RetryButton(state, scopeKey)
            }
            PushState.SERVER_REFUSED -> Text(stringResource(Res.string.notif_push_refused), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk)
        }
    }
}

@Composable
private fun RetryButton(state: NotificationsSettingsState, scopeKey: String) {
    PpfTextButton(onClick = state::retryPush, text = stringResource(Res.string.notif_push_retry), modifier = Modifier.testTag(NotificationsTags.RETRY + scopeKey))
}

/** The warning before a loosening: the safe choice first, then "Activer quand même". */
@Composable
private fun RelaxationDialog(warning: NotificationWarning, onKeep: () -> Unit, onConfirm: () -> Unit) {
    val palette = PpfTheme.palette
    val text = when (warning) {
        NotificationWarning.DETAILS -> Res.string.notif_warning_details
        NotificationWarning.PUBLIC_WAKE -> Res.string.notif_warning_public_wake
        NotificationWarning.MAILBOX_WAKE -> Res.string.notif_warning_mailbox_wake
    }
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.notif_warning_title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(stringResource(text), style = MaterialTheme.typography.bodyLarge) },
        // The safe choice comes first (the dialog's first button), the loosening second.
        dismissButton = {
            TextButton(onClick = onKeep, modifier = Modifier.testTag(NotificationsTags.WARNING_KEEP)) {
                Text(stringResource(Res.string.notif_warning_keep), color = palette.brand)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(NotificationsTags.WARNING_CONFIRM)) {
                Text(stringResource(Res.string.notif_warning_confirm), color = palette.inkMuted)
            }
        },
    )
}
