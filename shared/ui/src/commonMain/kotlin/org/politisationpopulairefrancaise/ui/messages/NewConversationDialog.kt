package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.Nip19
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.ui.components.PpfRemovableChip
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.messages_new_add
import org.politisationpopulairefrancaise.ui.resources.messages_new_cancel
import org.politisationpopulairefrancaise.ui.resources.messages_new_confirm
import org.politisationpopulairefrancaise.ui.resources.messages_new_followed_label
import org.politisationpopulairefrancaise.ui.resources.messages_new_followed_none
import org.politisationpopulairefrancaise.ui.resources.messages_new_key_error
import org.politisationpopulairefrancaise.ui.resources.messages_new_key_help
import org.politisationpopulairefrancaise.ui.resources.messages_new_key_hint
import org.politisationpopulairefrancaise.ui.resources.messages_new_key_label
import org.politisationpopulairefrancaise.ui.resources.messages_new_key_self
import org.politisationpopulairefrancaise.ui.resources.messages_new_not_selected
import org.politisationpopulairefrancaise.ui.resources.messages_new_privacy
import org.politisationpopulairefrancaise.ui.resources.messages_new_recipients
import org.politisationpopulairefrancaise.ui.resources.messages_new_remove
import org.politisationpopulairefrancaise.ui.resources.messages_new_selected
import org.politisationpopulairefrancaise.ui.resources.messages_new_subject_hint
import org.politisationpopulairefrancaise.ui.resources.messages_new_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object NewConversationTags {
    const val DIALOG = "new-conversation"
    const val KEY = "new-conversation-key"
    const val ADD = "new-conversation-add"
    const val SUBJECT = "new-conversation-subject"
    const val CONFIRM = "new-conversation-confirm"
    const val CANCEL = "new-conversation-cancel"
    const val ERROR = "new-conversation-error"
    const val ADDED = "new-conversation-added-" // followed by the first 8 characters of the key
    const val FOLLOWED = "new-conversation-followed-" // followed by the first 8 characters of the key
}

/** Reads a public key typed by the user: `npub1…` (any case) or 64 hexadecimal characters. Null when it is neither. */
internal fun parsePublicKey(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (text.startsWith(Nip19.PUBLIC_KEY + "1", ignoreCase = true)) {
        return runCatching { PublicKey.fromNpub(text.lowercase()).hex }.getOrNull()
    }
    val hex = text.lowercase()
    return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
}

/**
 * Reads one or several keys separated by spaces, commas, semicolons or line breaks, in the order
 * typed, without duplicates. Null when one of them is not a key: nothing is guessed.
 */
internal fun parsePublicKeys(input: String): List<String>? {
    val tokens = input.split(' ', ',', ';', '\n', '\r', '\t').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val keys = tokens.map { parsePublicKey(it) ?: return null }
    return keys.distinct()
}

private enum class KeyError { INVALID, SELF }

/**
 * The box opened by "Nouvelle conversation": one or several keys typed by hand (`npub1…` or
 * hexadecimal), some of the followed accounts, or both, and an optional subject. Several recipients
 * make a small group. Every key is checked before the conversation opens (a valid curve point, not
 * one's own), so that a mistyped key is caught at once. Styled like the dialogs of "Mon espace".
 */
@Composable
fun NewConversationDialog(
    messaging: MessagingSession,
    followed: Set<String>,
    trust: EditorialTrust,
    onStart: (recipients: List<String>, subject: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = PpfTheme.palette
    var typed by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(emptyList<String>()) }
    var subject by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<KeyError?>(null) }
    val candidates = remember(followed, messaging.myKey) { followed.filter { it != messaging.myKey }.sorted() }
    val selectedLabel = stringResource(Res.string.messages_new_selected)
    val notSelectedLabel = stringResource(Res.string.messages_new_not_selected)

    /** The keys typed so far, checked; null (with [error] set) when one of them cannot be used. */
    fun checkTyped(): List<String>? {
        val keys = parsePublicKeys(typed)
        val problem = when {
            keys == null -> KeyError.INVALID
            keys.any { it == messaging.myKey } -> KeyError.SELF
            keys.any { !messaging.canEncryptFor(it) } -> KeyError.INVALID
            else -> null
        }
        error = problem
        return if (problem == null) keys else null
    }

    val add: () -> Unit = {
        checkTyped()?.let { keys ->
            added = (added + keys).distinct()
            typed = ""
        }
    }
    val start: () -> Unit = {
        checkTyped()?.let { keys ->
            val recipients = (added + keys + selected.sorted()).distinct()
            if (recipients.isNotEmpty()) onStart(recipients, subject.trim().takeIf { it.isNotEmpty() })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(NewConversationTags.DIALOG),
        icon = { Icon(Lucide.Lock, contentDescription = null, tint = palette.brand) },
        title = { Text(stringResource(Res.string.messages_new_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.messages_new_key_label), style = MaterialTheme.typography.labelLarge, color = palette.ink)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PpfTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            error = null
                        },
                        placeholder = stringResource(Res.string.messages_new_key_hint),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag(NewConversationTags.KEY),
                    )
                    Spacer(Modifier.width(8.dp))
                    PpfSecondaryButton(
                        onClick = add,
                        text = stringResource(Res.string.messages_new_add),
                        enabled = typed.isNotBlank(),
                        small = true,
                        icon = Lucide.Plus,
                        modifier = Modifier.testTag(NewConversationTags.ADD),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.messages_new_key_help), style = MaterialTheme.typography.bodySmall)
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(if (it == KeyError.SELF) Res.string.messages_new_key_self else Res.string.messages_new_key_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accentInk,
                        modifier = Modifier.testTag(NewConversationTags.ERROR),
                    )
                }
                if (added.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.messages_new_recipients), style = MaterialTheme.typography.labelLarge, color = palette.ink)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        added.forEach { key ->
                            val name = shortNpub(key)
                            PpfRemovableChip(
                                label = name,
                                onRemove = { added = added - key },
                                removeDescription = stringResource(Res.string.messages_new_remove, name),
                                modifier = Modifier.testTag(NewConversationTags.ADDED + key.take(8)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.messages_new_followed_label), style = MaterialTheme.typography.labelLarge, color = palette.ink)
                Spacer(Modifier.height(6.dp))
                if (candidates.isEmpty()) {
                    Text(stringResource(Res.string.messages_new_followed_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    candidates.forEach { key ->
                        val isSelected = key in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Checkbox,
                                    onClick = { selected = if (isSelected) selected - key else selected + key },
                                )
                                .semantics { stateDescription = if (isSelected) selectedLabel else notSelectedLabel }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 4.dp)
                                .testTag(NewConversationTags.FOLLOWED + key.take(8)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccountAvatar(key, trust, Modifier.clearAndSetSemantics { }, size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = accountName(key, trust, messaging.myKey),
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(Lucide.Check, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PpfTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    placeholder = stringResource(Res.string.messages_new_subject_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(NewConversationTags.SUBJECT),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.messages_new_privacy), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = start,
                enabled = typed.isNotBlank() || added.isNotEmpty() || selected.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.brand),
                modifier = Modifier.testTag(NewConversationTags.CONFIRM),
            ) {
                Text(stringResource(Res.string.messages_new_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(NewConversationTags.CANCEL)) {
                Text(stringResource(Res.string.messages_new_cancel), color = palette.inkMuted)
            }
        },
    )
}
