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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.DaySeparator
import org.politisationpopulairefrancaise.ui.components.EncryptedNotice
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.MessageBubble
import org.politisationpopulairefrancaise.ui.components.PpfPillTextField
import org.politisationpopulairefrancaise.ui.components.PpfRoundButton
import org.politisationpopulairefrancaise.ui.components.ThreadHeader
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.messages_back
import org.politisationpopulairefrancaise.ui.resources.messages_compose_hint
import org.politisationpopulairefrancaise.ui.resources.messages_day_today
import org.politisationpopulairefrancaise.ui.resources.messages_day_yesterday
import org.politisationpopulairefrancaise.ui.resources.messages_encrypted
import org.politisationpopulairefrancaise.ui.resources.messages_first_message
import org.politisationpopulairefrancaise.ui.resources.messages_group_participants
import org.politisationpopulairefrancaise.ui.resources.messages_pending
import org.politisationpopulairefrancaise.ui.resources.messages_send
import org.politisationpopulairefrancaise.ui.resources.messages_send_error_long
import org.politisationpopulairefrancaise.ui.resources.messages_send_error_recipient
import org.politisationpopulairefrancaise.ui.resources.messages_status_offline
import org.politisationpopulairefrancaise.ui.resources.messages_subject
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ConversationTags {
    const val SCREEN = "conversation-screen"
    const val BACK = "conversation-back"
    const val TITLE = "conversation-title"
    const val LIST = "conversation-list"
    const val MESSAGE = "message-" // followed by the first 8 characters of the message id
    const val PENDING = "message-pending-" // same suffix: the clock of a message still waiting for the server
    const val COMPOSER = "conversation-composer"
    const val SEND = "conversation-send"
    const val ERROR = "conversation-error"
    const val OFFLINE = "conversation-offline"
}

/** What the list and the header call a conversation: the other person, or the subject or size of a group. */
@Composable
internal fun conversationTitle(others: List<String>, subject: String?): String = when {
    others.size == 1 -> shortNpub(others.single())
    subject != null -> subject
    else -> stringResource(Res.string.messages_group_participants, others.size + 1)
}

/** The avatar of a conversation: the other person's, or a squared one for a group. */
@Composable
internal fun ConversationAvatar(others: List<String>, conversationId: String, size: Dp = 40.dp) {
    if (others.size == 1) {
        Avatar(seed = others.single(), letters = avatarLetters(others.single()), size = size)
    } else {
        Avatar(seed = conversationId, letters = (others.size + 1).toString(), size = size, rounded = true)
    }
}

/** One element of the thread: a day separator or a message, oldest first. */
private sealed interface ThreadItem {
    val key: String

    data class Day(val day: Long, val label: String) : ThreadItem {
        override val key: String get() = "day-$day"
    }

    data class Entry(val message: ChatMessage, val moment: LocalMoment) : ThreadItem {
        override val key: String get() = message.id
    }
}

/**
 * The open conversation, after the owner's mock-up: a 64 dp header (back, avatar, name, the green
 * "Chiffré de bout en bout" line), the bubbles (others on the left, mine on the right, a clock on
 * those still waiting for the server, a separator between days), and the composer at the bottom
 * (pill field, round brand send button). While it is shown and a server is configured, the server
 * is asked again every [MessagingSession.REFRESH_PERIOD_SECONDS]. No read receipt, no typing
 * indicator: nothing about reading leaves the device.
 */
@Composable
fun ConversationScreen(messaging: MessagingSession, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val open = messaging.open ?: return
    val me = messaging.myKey
    val others = open.others(me)
    val title = conversationTitle(others, open.subject)
    val scope = rememberCoroutineScope()
    var draft by remember(open.id) { mutableStateOf("") }
    val todayLabel = stringResource(Res.string.messages_day_today)
    val yesterdayLabel = stringResource(Res.string.messages_day_yesterday)
    val pendingLabel = stringResource(Res.string.messages_pending)

    LaunchedEffect(open.id, messaging.hasServer) {
        if (!messaging.hasServer) return@LaunchedEffect
        while (true) {
            delay(MessagingSession.REFRESH_PERIOD_SECONDS * 1_000L)
            messaging.refresh()
        }
    }

    val send: () -> Unit = {
        val text = draft
        if (text.isNotBlank()) scope.launch { if (messaging.send(text)) draft = "" }
    }

    val items = remember(messaging.messages, todayLabel, yesterdayLabel) {
        val today = LocalMoment.of(currentEpochSeconds()).day
        buildList {
            var lastDay: Long? = null
            messaging.messages.forEach { message ->
                val moment = LocalMoment.of(message.createdAt)
                if (moment.day != lastDay) {
                    lastDay = moment.day
                    val label = when (moment.day) {
                        today -> todayLabel
                        today - 1 -> yesterdayLabel
                        else -> moment.date.toFrench()
                    }
                    add(ThreadItem.Day(moment.day, label))
                }
                add(ThreadItem.Entry(message, moment))
            }
        }.asReversed()
    }

    Column(modifier.fillMaxSize().background(palette.surface1).testTag(ConversationTags.SCREEN)) {
        ThreadHeader(
            title = title,
            onBack = onBack,
            backDescription = stringResource(Res.string.messages_back),
            modifier = Modifier.testTag(ConversationTags.TITLE),
            avatar = { ConversationAvatar(others, open.id) },
            subtitle = { EncryptedNotice(stringResource(Res.string.messages_encrypted)) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messaging.messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    InfoBox(Lucide.Lock, Modifier.widthIn(max = 480.dp)) {
                        Text(stringResource(Res.string.messages_first_message, title), style = MaterialTheme.typography.bodyMedium)
                        if (open.subject != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.messages_subject, open.subject), style = MaterialTheme.typography.labelLarge)
                        }
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
                            is ThreadItem.Day -> DaySeparator(item.label)
                            is ThreadItem.Entry -> {
                                val message = item.message
                                val mine = message.author == me
                                val suffix = message.id.take(8)
                                val pending = mine && message.id in messaging.pending
                                MessageBubble(
                                    text = message.text,
                                    mine = mine,
                                    time = item.moment.hourLabel,
                                    author = if (mine) null else shortNpub(message.author),
                                    authorSeed = message.author,
                                    pending = pending,
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
                if (!messaging.hasServer) {
                    Text(
                        text = stringResource(Res.string.messages_status_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).testTag(ConversationTags.OFFLINE),
                    )
                }
                messaging.sendError?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                SendError.INVALID_RECIPIENT -> Res.string.messages_send_error_recipient
                                SendError.TOO_LONG -> Res.string.messages_send_error_long
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
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.testTag(ConversationTags.SEND),
                    )
                }
            }
        }
    }
}
