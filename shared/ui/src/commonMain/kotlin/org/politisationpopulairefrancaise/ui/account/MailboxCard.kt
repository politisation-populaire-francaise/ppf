package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.groups.MailboxRetention
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_1_year
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_3_months
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_3_months_hint
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_6_months
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_cancel
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_confirm
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_confirm_text
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_confirm_title
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_default
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_explanation
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_longer_hint
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_pending
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_public
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_rejected
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_saved
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_saving
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_title
import org.politisationpopulairefrancaise.ui.resources.account_mailbox_unavailable
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the mailbox setting. */
object MailboxTags {
    const val CARD = "mailbox-card"
    const val OPTION = "mailbox-option-" // followed by the number of days
    const val STATUS = "mailbox-status"
    const val CONFIRM = "mailbox-confirm"
    const val CANCEL = "mailbox-cancel"
}

private fun MailboxRetention.label(): StringResource = when (this) {
    MailboxRetention.THREE_MONTHS -> Res.string.account_mailbox_3_months
    MailboxRetention.SIX_MONTHS -> Res.string.account_mailbox_6_months
    MailboxRetention.ONE_YEAR -> Res.string.account_mailbox_1_year
}

/**
 * "Mon espace": how long the server keeps the private messages addressed to the user (3 months by
 * default, the shortest and most protective; 6 months or 1 year on request), what it keeps, and
 * where the choice stands. Keeping the envelopes longer is a loosening: it is confirmed first, and
 * going back to 3 months always stays one tap away.
 */
@Composable
internal fun MailboxCard(setting: MailboxSetting, session: AccountSession) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf<MailboxRetention?>(null) }
    val choose: (MailboxRetention) -> Unit = { choice ->
        val signer = session.signer
        if (signer != null) scope.launch { setting.choose(signer, choice) }
    }
    val enabled = setting.status != MailboxStatus.UNAVAILABLE && !setting.saving

    PpfCard(Modifier.fillMaxWidth().testTag(MailboxTags.CARD)) {
        Text(stringResource(Res.string.account_mailbox_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.account_mailbox_explanation), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            MailboxRetention.entries.forEach { option ->
                PpfRadioRow(
                    label = stringResource(option.label()),
                    selected = setting.retention == option,
                    onClick = {
                        when {
                            option == setting.retention -> Unit
                            option.days > setting.retention.days -> asked = option
                            else -> choose(option)
                        }
                    },
                    supporting = when (option) {
                        MailboxRetention.THREE_MONTHS -> stringResource(Res.string.account_mailbox_3_months_hint)
                        MailboxRetention.ONE_YEAR -> stringResource(Res.string.account_mailbox_longer_hint)
                        else -> null
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag(MailboxTags.OPTION + option.days),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val status = when {
            setting.saving -> Res.string.account_mailbox_saving
            else -> when (setting.status) {
                MailboxStatus.DEFAULT -> Res.string.account_mailbox_default
                MailboxStatus.SAVED -> Res.string.account_mailbox_saved
                MailboxStatus.PENDING -> Res.string.account_mailbox_pending
                MailboxStatus.REJECTED -> Res.string.account_mailbox_rejected
                MailboxStatus.UNAVAILABLE -> Res.string.account_mailbox_unavailable
            }
        }
        Text(
            stringResource(status),
            style = MaterialTheme.typography.bodySmall,
            color = if (setting.status == MailboxStatus.REJECTED) palette.accentInk else palette.inkSubtle,
            modifier = Modifier.testTag(MailboxTags.STATUS),
        )
    }
    InfoBox(Lucide.Info) {
        Text(stringResource(Res.string.account_mailbox_public), style = MaterialTheme.typography.bodyMedium)
    }

    asked?.let { option ->
        val label = stringResource(option.label())
        AlertDialog(
            onDismissRequest = { asked = null },
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
            title = { Text(stringResource(Res.string.account_mailbox_confirm_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(Res.string.account_mailbox_confirm_text, label), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { asked = null; choose(option) }, modifier = Modifier.testTag(MailboxTags.CONFIRM)) {
                    Text(stringResource(Res.string.account_mailbox_confirm, label), color = palette.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { asked = null }, modifier = Modifier.testTag(MailboxTags.CANCEL)) {
                    Text(stringResource(Res.string.account_mailbox_cancel), color = palette.inkMuted)
                }
            },
        )
    }
}
