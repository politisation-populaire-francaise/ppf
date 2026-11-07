package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.DaySeparator
import org.politisationpopulairefrancaise.ui.components.EncryptedNotice
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.MessageBubble
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPillTextField
import org.politisationpopulairefrancaise.ui.components.PpfRoundButton
import org.politisationpopulairefrancaise.ui.components.ThreadHeader
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.messages_back
import org.politisationpopulairefrancaise.ui.resources.messages_compose_hint
import org.politisationpopulairefrancaise.ui.resources.messages_encrypted
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_close
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_limit
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_open
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_text
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_title
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_verify
import org.politisationpopulairefrancaise.ui.resources.messages_first_message
import org.politisationpopulairefrancaise.ui.resources.messages_local_note
import org.politisationpopulairefrancaise.ui.resources.messages_pending
import org.politisationpopulairefrancaise.ui.resources.messages_send
import org.politisationpopulairefrancaise.ui.resources.messages_send_error_long
import org.politisationpopulairefrancaise.ui.resources.messages_send_error_other
import org.politisationpopulairefrancaise.ui.resources.messages_send_error_recipient
import org.politisationpopulairefrancaise.ui.resources.messages_status_offline
import org.politisationpopulairefrancaise.ui.resources.messages_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.messages_subject
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ConversationTags {
    const val SCREEN = "conversation-screen"
    const val TITLE = "conversation-title"
    const val INFO = "conversation-encryption-info"
    const val LIST = "conversation-list"
    const val EMPTY = "conversation-empty"
    const val DAY = "conversation-day-" // followed by the local day number
    const val MESSAGE = "message-" // followed by the first 8 characters of the message id
    const val PENDING = "message-pending-" // same suffix: the clock of a message still waiting for the server
    const val COMPOSER = "conversation-composer"
    const val SEND = "conversation-send"
    const val ERROR = "conversation-error"
    const val STATUS = "conversation-status"
    const val ENCRYPTION_DIALOG = "encryption-dialog"
    const val ENCRYPTION_CLOSE = "encryption-dialog-close"
}

/** One element of a thread: a day separator or an entry, oldest first. */
internal sealed interface ThreadItem<out T> {
    val key: String

    data class Day(val moment: LocalMoment) : ThreadItem<Nothing> {
        override val key: String get() = "day-${moment.day}"
    }

    data class Entry<T>(val value: T, override val key: String, val moment: LocalMoment) : ThreadItem<T>
}

/** Groups [entries] (oldest first) by local day, a separator before each day; newest first, ready for a reversed list. */
internal fun <T> threadItems(entries: List<T>, key: (T) -> String, date: (T) -> Long): List<ThreadItem<T>> = buildList {
    var lastDay: Long? = null
    entries.forEach { entry ->
        val moment = LocalMoment.of(date(entry))
        if (moment.day != lastDay) {
            lastDay = moment.day
            add(ThreadItem.Day(moment))
        }
        add(ThreadItem.Entry(entry, key(entry), moment))
    }
}.asReversed()

/**
 * The open conversation, after the owner's mock-up: a 64 dp header (back on phones, avatar, name,
 * the green "Chiffré de bout en bout" line, the explanation of the encryption), the bubbles (others
 * on the left, mine on the right, a clock on those still waiting for the server, a separator
 * between days, the local hour of each), and the composer at the bottom (pill field, round brand
 * send button; Enter sends, Shift+Enter starts a new line). The messages shown are marked read on
 * the device only: no read receipt, no typing indicator.
 */
@Composable
fun ConversationScreen(messaging: MessagingSession, trust: EditorialTrust, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val open = messaging.open ?: return
    val me = messaging.myKey
    val others = open.others(me)
    val title = conversationTitle(others, open.subject, trust)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(open.id) { mutableStateOf("") }
    var explaining by remember { mutableStateOf(false) }
    val pendingLabel = stringResource(Res.string.messages_pending)
    val today = LocalMoment.of(currentEpochSeconds())

    // What is shown is read: the unread count of the list drops, on this device only.
    LaunchedEffect(open.id, messaging.messages) { messaging.markOpenRead() }

    val send: () -> Unit = {
        val text = draft
        if (text.isNotBlank() && messaging.canWrite) scope.launch { if (messaging.send(text)) draft = "" }
    }
    val items = remember(messaging.messages) { threadItems(messaging.messages, key = ChatMessage::id, date = ChatMessage::createdAt) }

    Column(modifier.fillMaxSize().background(palette.surface1).testTag(ConversationTags.SCREEN)) {
        ThreadHeader(
            title = title,
            onBack = onBack,
            backDescription = stringResource(Res.string.messages_back),
            modifier = Modifier.testTag(ConversationTags.TITLE),
            avatar = { ConversationAvatar(others, open.id, trust) },
            subtitle = {
                Column {
                    EncryptedNotice(stringResource(Res.string.messages_encrypted))
                    VerifiedLine(messaging, others)
                }
            },
            actions = {
                PpfIconButton(
                    icon = Lucide.Info,
                    contentDescription = stringResource(Res.string.messages_encryption_open),
                    onClick = { explaining = true },
                    modifier = Modifier.testTag(ConversationTags.INFO),
                )
                ConversationMoreMenu(messaging, me, others)
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messaging.messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    InfoBox(Lucide.Lock, Modifier.widthIn(max = 480.dp).testTag(ConversationTags.EMPTY)) {
                        Text(stringResource(Res.string.messages_first_message, title), style = MaterialTheme.typography.bodyMedium)
                        if (open.subject != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.messages_subject, open.subject), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.messages_local_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                }
            } else {
                // Newest at the bottom, where the list starts: a new message appears without scrolling.
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ConversationTags.LIST),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is ThreadItem.Day -> DaySeparator(dayLabel(item.moment, today), Modifier.testTag(ConversationTags.DAY + item.moment.day))
                            is ThreadItem.Entry -> {
                                val message = item.value
                                val mine = message.author == me
                                val suffix = message.id.take(8)
                                MessageBubble(
                                    text = org.politisationpopulairefrancaise.ui.voir.voirRequestText(message.text),
                                    mine = mine,
                                    time = item.moment.hourLabel,
                                    // In a group, each bubble of the others says who wrote it.
                                    author = if (!mine && others.size > 1) accountName(message.author, trust, me) else null,
                                    authorSeed = message.author,
                                    pending = mine && message.id in messaging.pending,
                                    pendingDescription = pendingLabel,
                                    pendingModifier = Modifier.testTag(ConversationTags.PENDING + suffix),
                                    modifier = Modifier.testTag(ConversationTags.MESSAGE + suffix),
                                )
                            }
                        }
                    }
                    if (open.subject != null) {
                        item(key = "subject") {
                            Text(
                                text = stringResource(Res.string.messages_subject, open.subject),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.inkMuted,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { heading() },
                            )
                        }
                    }
                }
            }
        }
        Surface(color = palette.surface0, contentColor = palette.ink, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                val status = when (messaging.status) {
                    MessagingStatus.Offline -> stringResource(Res.string.messages_status_offline)
                    MessagingStatus.Unreachable -> stringResource(Res.string.messages_status_unreachable)
                    else -> null
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).testTag(ConversationTags.STATUS),
                    )
                }
                messaging.sendError?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                SendError.INVALID_RECIPIENT -> Res.string.messages_send_error_recipient
                                SendError.TOO_LONG -> Res.string.messages_send_error_long
                                SendError.FAILED -> Res.string.messages_send_error_other
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.accentInk,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).testTag(ConversationTags.ERROR),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    PpfPillTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = stringResource(Res.string.messages_compose_hint),
                        onSend = send,
                        modifier = Modifier.weight(1f).testTag(ConversationTags.COMPOSER),
                    )
                    Spacer(Modifier.width(8.dp))
                    PpfRoundButton(
                        icon = Lucide.Send,
                        contentDescription = stringResource(Res.string.messages_send),
                        onClick = send,
                        enabled = draft.isNotBlank() && messaging.canWrite,
                        modifier = Modifier.testTag(ConversationTags.SEND),
                    )
                }
            }
        }
    }

    if (explaining) EncryptionDialog(onDismiss = { explaining = false })
}

/**
 * How the private messages are protected, in plain words, with the known limits until MLS
 * (no forward secrecy, metadata a hostile server could infer) and the coming QR code check.
 */
@Composable
internal fun EncryptionDialog(onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(ConversationTags.ENCRYPTION_DIALOG),
        icon = { Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.success) },
        title = { Text(stringResource(Res.string.messages_encryption_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.messages_encryption_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.messages_encryption_limit), style = MaterialTheme.typography.bodyMedium, color = palette.ink)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.messages_encryption_verify), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.messages_local_note), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(ConversationTags.ENCRYPTION_CLOSE)) {
                Text(stringResource(Res.string.messages_encryption_close), color = palette.brand)
            }
        },
    )
}
