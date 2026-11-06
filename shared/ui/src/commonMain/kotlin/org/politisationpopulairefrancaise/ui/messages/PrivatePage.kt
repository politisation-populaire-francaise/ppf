package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.ThreadRow
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_status_loading
import org.politisationpopulairefrancaise.ui.resources.feed_create_account
import org.politisationpopulairefrancaise.ui.resources.messages_conversations_heading
import org.politisationpopulairefrancaise.ui.resources.messages_empty
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_limit
import org.politisationpopulairefrancaise.ui.resources.messages_encryption_open
import org.politisationpopulairefrancaise.ui.resources.messages_my_key_label
import org.politisationpopulairefrancaise.ui.resources.messages_need_account
import org.politisationpopulairefrancaise.ui.resources.messages_open_conversation
import org.politisationpopulairefrancaise.ui.resources.messages_pending
import org.politisationpopulairefrancaise.ui.resources.messages_preview_mine
import org.politisationpopulairefrancaise.ui.resources.messages_private_text
import org.politisationpopulairefrancaise.ui.resources.messages_private_title
import org.politisationpopulairefrancaise.ui.resources.messages_refresh
import org.politisationpopulairefrancaise.ui.resources.messages_retention_note
import org.politisationpopulairefrancaise.ui.resources.messages_select_hint
import org.politisationpopulairefrancaise.ui.resources.messages_status_offline
import org.politisationpopulairefrancaise.ui.resources.messages_status_synced
import org.politisationpopulairefrancaise.ui.resources.messages_status_syncing
import org.politisationpopulairefrancaise.ui.resources.messages_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.messages_unavailable
import org.politisationpopulairefrancaise.ui.resources.messages_unread_count
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object PrivateTags {
    const val LIST = "private-list"
    const val REFRESH = "private-refresh"
    const val STATUS = "private-status"
    const val EMPTY = "private-empty"
    const val INVITATION = "private-invitation"
    const val CREATE_ACCOUNT = "private-create-account"
    const val UNAVAILABLE = "private-unavailable"
    const val SELECT_HINT = "private-select-hint"
    const val MY_KEY = "private-my-key"
    const val ENCRYPTION = "private-encryption"

    /** The row of a conversation: named after the other participants, so that it does not depend on the user's own key. */
    fun row(conversationId: String, myKey: String?): String =
        "conversation-" + Conversation.participantsOf(conversationId).filter { it != myKey }.joinToString("-") { it.take(8) }

    /** The unread badge of that row. */
    fun unread(conversationId: String, myKey: String?): String = row(conversationId, myKey) + "-unread"
}

/** Below this width the list and the open thread share one pane (phones, tablets in portrait). */
internal val TWO_PANES_MIN_WIDTH: Dp = 640.dp

/** The width of the list when it sits next to the open thread. */
internal fun listPaneWidth(available: Dp): Dp = (available * 0.42f).coerceIn(300.dp, 400.dp)

/**
 * The private and small-group conversations (NIP-17, decision D-027). Without an account, an
 * invitation to create one (reading the rest of the application needs none); with one, the list of
 * conversations (other participant or group, last message on one line, time, unread badge, a clock
 * when the last message still waits for the server) and the open conversation: next to the list
 * when the window is wide enough, in its place with a back button otherwise (on phones the open
 * conversation takes the whole screen, see [MessagesScreen]). [onNavigate] is optional: when the
 * host gives it, the invitation offers a button to "Mon espace".
 */
@Composable
fun PrivatePage(
    session: AccountSession,
    messaging: MessagingSession,
    trust: EditorialTrust,
    compact: Boolean,
    onNavigate: ((Space) -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    when (session.state) {
        AccountUiState.Loading -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Text(stringResource(Res.string.content_status_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            return
        }
        is AccountUiState.Ready -> Unit
        else -> {
            Invitation(compact, onCreate = onNavigate?.let { navigate -> { navigate(Space.MON_ESPACE) } })
            return
        }
    }
    if (messaging.status == MessagingStatus.Unavailable) {
        Box(Modifier.fillMaxSize().padding(if (compact) 16.dp else 24.dp), contentAlignment = Alignment.TopStart) {
            Text(
                text = stringResource(Res.string.messages_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(PrivateTags.UNAVAILABLE),
            )
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = listPaneWidth(maxWidth)
        if (maxWidth >= TWO_PANES_MIN_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                ConversationList(messaging, trust, Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider(color = palette.border)
                if (messaging.open != null) {
                    ConversationScreen(messaging, trust, onBack = null, modifier = Modifier.weight(1f).fillMaxHeight())
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.messages_select_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkMuted,
                            modifier = Modifier.widthIn(max = 360.dp).testTag(PrivateTags.SELECT_HINT),
                        )
                    }
                }
            }
        } else if (messaging.open != null) {
            ConversationScreen(messaging, trust, onBack = messaging::close, modifier = Modifier.fillMaxSize())
        } else {
            ConversationList(messaging, trust, Modifier.fillMaxSize())
        }
    }
}

/** What people without an account see: the promise of the view and how to get an account. */
@Composable
private fun Invitation(compact: Boolean, onCreate: (() -> Unit)?) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
        Column(Modifier.widthIn(max = 720.dp).testTag(PrivateTags.INVITATION)) {
            Text(stringResource(Res.string.messages_private_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.messages_private_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(12.dp))
            InfoBox(Lucide.KeyRound) {
                Text(stringResource(Res.string.messages_need_account), style = MaterialTheme.typography.bodyMedium)
                if (onCreate != null) {
                    Spacer(Modifier.height(12.dp))
                    PpfPrimaryButton(onClick = onCreate, text = stringResource(Res.string.feed_create_account), modifier = Modifier.testTag(PrivateTags.CREATE_ACCOUNT))
                }
            }
        }
    }
}

/**
 * The list of conversations, most recent activity first: a heading with the refresh button, a
 * discreet status line (offline, unreachable, up to date), the rows or an explanation when there
 * is nothing yet, then the user's own key (to give to the people who want to write) and how the
 * messages are protected.
 */
@Composable
private fun ConversationList(messaging: MessagingSession, trust: EditorialTrust, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val now = currentEpochSeconds()
    val me = messaging.myKey
    val pendingLabel = stringResource(Res.string.messages_pending)
    val openLabel = stringResource(Res.string.messages_open_conversation)
    var explaining by remember { mutableStateOf(false) }
    LazyColumn(modifier.background(palette.surface0).testTag(PrivateTags.LIST)) {
        item(key = "heading") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.messages_conversations_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (messaging.hasServer) {
                    PpfIconButton(
                        icon = Lucide.RefreshCw,
                        contentDescription = stringResource(Res.string.messages_refresh),
                        onClick = messaging::refresh,
                        enabled = !messaging.refreshing,
                        modifier = Modifier.testTag(PrivateTags.REFRESH),
                    )
                }
            }
        }
        item(key = "status") {
            val status = statusText(messaging)
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).testTag(PrivateTags.STATUS),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        if (messaging.conversations.isEmpty()) {
            if (messaging.status != MessagingStatus.Loading) {
                item(key = "empty") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(PrivateTags.EMPTY)) {
                        InfoBox(Lucide.MessageSquare) {
                            Text(stringResource(Res.string.messages_empty), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.messages_retention_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                        }
                    }
                }
            }
        } else {
            items(messaging.conversations, key = { it.id }) { summary ->
                val conversation = summary.conversation
                val others = conversation.participants.filter { it != me }
                val last = summary.lastMessage
                ThreadRow(
                    title = conversationTitle(others, conversation.subject, trust),
                    preview = last?.let { if (it.author == me) stringResource(Res.string.messages_preview_mine, it.text) else it.text },
                    time = listTimeLabel(conversation.lastMessageAt, now),
                    onClick = { messaging.open(conversation) },
                    onClickLabel = openLabel,
                    unread = conversation.unreadCount,
                    unreadDescription = pluralStringResource(Res.plurals.messages_unread_count, conversation.unreadCount.toInt(), conversation.unreadCount.toInt()),
                    pending = summary.pending,
                    pendingDescription = pendingLabel,
                    selected = messaging.open?.id == conversation.id,
                    modifier = Modifier.testTag(PrivateTags.row(conversation.id, me)),
                    unreadTag = PrivateTags.unread(conversation.id, me),
                    avatar = { ConversationAvatar(others, conversation.id, trust) },
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(start = 68.dp))
            }
        }
        if (me != null) {
            item(key = "my-key") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Inset(Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.messages_my_key_label), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = remember(me) { PublicKey.fromHex(me).npub },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink,
                                modifier = Modifier.testTag(PrivateTags.MY_KEY),
                            )
                        }
                    }
                }
            }
        }
        item(key = "encryption") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag(PrivateTags.ENCRYPTION)) {
                InfoBox(Lucide.ShieldCheck) {
                    Text(stringResource(Res.string.messages_encryption_limit), style = MaterialTheme.typography.bodySmall)
                    PpfTextButton(onClick = { explaining = true }, text = stringResource(Res.string.messages_encryption_open))
                }
            }
        }
    }
    if (explaining) EncryptionDialog(onDismiss = { explaining = false })
}

@Composable
private fun statusText(messaging: MessagingSession): String? = when {
    messaging.refreshing -> stringResource(Res.string.messages_status_syncing)
    messaging.status == MessagingStatus.Offline -> stringResource(Res.string.messages_status_offline)
    messaging.status == MessagingStatus.Unreachable -> stringResource(Res.string.messages_status_unreachable)
    messaging.status is MessagingStatus.Synced -> stringResource(Res.string.messages_status_synced)
    else -> null
}
