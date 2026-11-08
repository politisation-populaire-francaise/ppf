package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.social.SafetyNumber
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.conversation_delete
import org.politisationpopulairefrancaise.ui.resources.conversation_delete_cancel
import org.politisationpopulairefrancaise.ui.resources.conversation_delete_confirm
import org.politisationpopulairefrancaise.ui.resources.conversation_delete_text
import org.politisationpopulairefrancaise.ui.resources.conversation_delete_title
import org.politisationpopulairefrancaise.ui.resources.conversation_more
import org.politisationpopulairefrancaise.ui.resources.conversation_verified
import org.politisationpopulairefrancaise.ui.resources.conversation_verify
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_close
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_done
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_explain
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_mismatch
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_number_label
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_status_no
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_status_yes
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_title
import org.politisationpopulairefrancaise.ui.resources.conversation_verify_undo
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ConversationActionTags {
    const val MORE = "conversation-more"
    const val VERIFY = "conversation-verify"
    const val DELETE = "conversation-delete"
    const val VERIFY_DIALOG = "conversation-verify-dialog"
    const val SAFETY_NUMBER = "conversation-safety-number"
    const val VERIFY_DONE = "conversation-verify-done"
    const val VERIFY_UNDO = "conversation-verify-undo"
    const val VERIFY_CLOSE = "conversation-verify-close"
    const val DELETE_DIALOG = "conversation-delete-dialog"
    const val DELETE_CONFIRM = "conversation-delete-confirm"
    const val DELETE_CANCEL = "conversation-delete-cancel"
    const val VERIFIED_BADGE = "conversation-verified"
}

/**
 * The "more" menu of a conversation: check the contact in person (only between two people, S-128)
 * and delete the conversation from this device.
 */
@Composable
internal fun ConversationMoreMenu(messaging: MessagingSession, me: String?, others: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val contact = others.singleOrNull()
    Box {
        PpfIconButton(
            icon = Lucide.Ellipsis,
            contentDescription = stringResource(Res.string.conversation_more),
            onClick = { expanded = true },
            modifier = Modifier.testTag(ConversationActionTags.MORE),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = PpfTheme.palette.surface2) {
            if (me != null && contact != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.conversation_verify)) },
                    leadingIcon = { Icon(Lucide.ShieldCheck, contentDescription = null) },
                    onClick = {
                        expanded = false
                        verifying = true
                    },
                    modifier = Modifier.testTag(ConversationActionTags.VERIFY),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.conversation_delete)) },
                leadingIcon = { Icon(Lucide.X, contentDescription = null) },
                onClick = {
                    expanded = false
                    deleting = true
                },
                modifier = Modifier.testTag(ConversationActionTags.DELETE),
            )
        }
    }
    if (verifying && me != null && contact != null) {
        VerifyContactDialog(messaging, me, contact, onDismiss = { verifying = false })
    }
    if (deleting) {
        DeleteConversationDialog(
            onConfirm = {
                deleting = false
                messaging.deleteOpen()
            },
            onDismiss = { deleting = false },
        )
    }
}

/** The line under the title of a conversation with a contact checked in person. */
@Composable
internal fun VerifiedLine(messaging: MessagingSession, others: List<String>) {
    val contact = others.singleOrNull() ?: return
    // Read again whenever a verification changes.
    @Suppress("UNUSED_VARIABLE")
    val version = messaging.verificationsVersion
    if (!messaging.isVerified(contact)) return
    Text(
        stringResource(Res.string.conversation_verified),
        style = MaterialTheme.typography.labelSmall,
        color = PpfTheme.palette.success,
        modifier = Modifier.testTag(ConversationActionTags.VERIFIED_BADGE),
    )
}

/**
 * The safety number to compare with the other person, face to face or on a call one trusts:
 * if both read the same digits, each holds the real key of the other (S-128).
 */
@Composable
private fun VerifyContactDialog(messaging: MessagingSession, me: String, contact: String, onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    val groups = remember(me, contact) { SafetyNumber.of(me, contact) }
    @Suppress("UNUSED_VARIABLE")
    val version = messaging.verificationsVersion
    val verified = messaging.isVerified(contact)
    val numberLabel = stringResource(Res.string.conversation_verify_number_label, groups.joinToString(", "))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(ConversationActionTags.VERIFY_DIALOG),
        icon = { Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.success) },
        title = { Text(stringResource(Res.string.conversation_verify_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.conversation_verify_explain), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                // Three lines of four groups, in a fixed-width font, read aloud group by group.
                Column(Modifier.semantics { contentDescription = numberLabel }.testTag(ConversationActionTags.SAFETY_NUMBER)) {
                    groups.chunked(4).forEach { line ->
                        Text(line.joinToString("  "), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = palette.ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.conversation_verify_mismatch), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (verified) Res.string.conversation_verify_status_yes else Res.string.conversation_verify_status_no),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (verified) palette.success else palette.ink,
                )
            }
        },
        confirmButton = {
            if (verified) {
                TextButton(onClick = { messaging.setVerified(contact, false) }, modifier = Modifier.testTag(ConversationActionTags.VERIFY_UNDO)) {
                    Text(stringResource(Res.string.conversation_verify_undo), color = palette.inkMuted)
                }
            } else {
                TextButton(
                    onClick = {
                        messaging.setVerified(contact, true)
                        onDismiss()
                    },
                    modifier = Modifier.testTag(ConversationActionTags.VERIFY_DONE),
                ) {
                    Text(stringResource(Res.string.conversation_verify_done), color = palette.brand)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ConversationActionTags.VERIFY_CLOSE)) {
                Text(stringResource(Res.string.conversation_verify_close), color = palette.inkMuted)
            }
        },
    )
}

/** Deleting a conversation cannot be undone on this device: a confirmation, the safe choice first. */
@Composable
private fun DeleteConversationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(ConversationActionTags.DELETE_DIALOG),
        title = { Text(stringResource(Res.string.conversation_delete_title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(stringResource(Res.string.conversation_delete_text), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ConversationActionTags.DELETE_CANCEL)) {
                Text(stringResource(Res.string.conversation_delete_cancel), color = palette.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(ConversationActionTags.DELETE_CONFIRM)) {
                Text(stringResource(Res.string.conversation_delete_confirm), color = palette.accentInk)
            }
        },
    )
}
