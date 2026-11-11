package org.politisationpopulairefrancaise.ui.groups

import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.feed.RelativeTime
import org.politisationpopulairefrancaise.ui.feed.label
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.groups_about_less
import org.politisationpopulairefrancaise.ui.resources.groups_about_open
import org.politisationpopulairefrancaise.ui.resources.groups_mod_banned
import org.politisationpopulairefrancaise.ui.resources.groups_send_failed_banned
import org.politisationpopulairefrancaise.ui.resources.groups_about_more
import org.politisationpopulairefrancaise.ui.resources.groups_back
import org.politisationpopulairefrancaise.ui.resources.groups_closed_room
import org.politisationpopulairefrancaise.ui.resources.groups_create_account
import org.politisationpopulairefrancaise.ui.resources.groups_join
import org.politisationpopulairefrancaise.ui.resources.groups_join_to_write
import org.politisationpopulairefrancaise.ui.resources.groups_leave
import org.politisationpopulairefrancaise.ui.resources.groups_message_hint
import org.politisationpopulairefrancaise.ui.resources.groups_message_pending
import org.politisationpopulairefrancaise.ui.resources.groups_message_you
import org.politisationpopulairefrancaise.ui.resources.groups_messages_loading
import org.politisationpopulairefrancaise.ui.resources.groups_messages_none
import org.politisationpopulairefrancaise.ui.resources.groups_messages_offline
import org.politisationpopulairefrancaise.ui.resources.groups_messages_refresh
import org.politisationpopulairefrancaise.ui.resources.groups_messages_unreachable
import org.politisationpopulairefrancaise.ui.resources.groups_need_account_write
import org.politisationpopulairefrancaise.ui.resources.groups_public_notice
import org.politisationpopulairefrancaise.ui.resources.groups_send
import org.politisationpopulairefrancaise.ui.resources.groups_send_failed
import org.politisationpopulairefrancaise.ui.resources.groups_send_failed_closed
import org.politisationpopulairefrancaise.ui.resources.groups_send_failed_not_found
import org.politisationpopulairefrancaise.ui.resources.groups_territory_unknown
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the rooms, also useful for accessibility tooling. */
object GroupsTags {
    // The "Collectifs" view.
    const val PAGE = "groups-page"
    const val LIST = "groups-list"
    const val SEARCH = "groups-search"
    const val REFRESH = "groups-refresh"
    const val CREATE = "groups-create"
    const val CREATE_FROM_QUERY = "groups-create-from-query"
    const val STATUS = "groups-status"
    const val TERRITORY = "groups-territory-" // followed by the normalized territory, or "all"
    const val MINE = "groups-mine"
    const val MINE_EMPTY = "groups-mine-empty"
    const val SUGGESTIONS = "groups-suggestions"
    const val EMPTY = "groups-empty"
    const val ROOM = "groups-room-" // followed by the room id
    const val JOIN = "groups-join-" // followed by the room id
    const val JOINED = "groups-joined-" // followed by the room id
    const val SELECT_HINT = "groups-select-hint"

    // A room.
    const val SCREEN = "group-screen"
    const val BACK = "group-back"
    const val TITLE = "group-title"
    const val NOTICE = "group-public-notice"
    const val ROOM_JOIN = "group-join"
    const val ROOM_LEAVE = "group-leave"
    const val REFRESH_MESSAGES = "group-refresh"
    const val MESSAGES = "group-messages"
    const val MESSAGES_EMPTY = "group-messages-empty"
    const val MESSAGES_STATUS = "group-messages-status"
    const val MESSAGE = "group-message-" // followed by the event id
    const val MESSAGE_PENDING = "group-message-pending-" // followed by the event id
    const val INPUT = "group-input"
    const val SEND = "group-send"
    const val SEND_ERROR = "group-send-error"
    const val NEED_ACCOUNT = "group-need-account"
    const val CREATE_ACCOUNT = "group-create-account"
    const val JOIN_TO_WRITE = "group-join-to-write"
    const val CLOSED = "group-closed"

    // The creation form.
    const val FORM = "group-form"
    const val FORM_CLOSE = "group-form-close"
    const val FORM_NAME = "group-form-name"
    const val FORM_TERRITORY = "group-form-territory"
    const val FORM_ABOUT = "group-form-about"
    const val FORM_ID = "group-form-id"
    const val FORM_SIMILAR = "group-form-similar"
    const val FORM_CHARTER = "group-form-charter"
    const val FORM_SUBMIT = "group-form-submit"
    const val FORM_CANCEL = "group-form-cancel"
    const val FORM_BLOCKED = "group-form-blocked"
    const val FORM_ERROR = "group-form-error"
    const val FORM_EXISTS = "group-form-exists"
    const val FORM_JOIN_EXISTING = "group-form-join-existing"
    const val FORM_EDIT = "group-form-edit"
    const val FORM_NEED_ACCOUNT = "group-form-need-account"
    const val FORM_CREATE_ACCOUNT = "group-form-create-account"
    const val FORM_NO_SERVER = "group-form-no-server"
    const val FORM_IDENTITY = "group-form-identity"
    const val FORM_SIMILAR_SERVER = "group-form-similar-server"
    const val FORM_SIMILAR_JOIN = "group-form-similar-join-" // followed by the room id
    const val FORM_DIFFERENCE = "group-form-difference"
    const val FORM_DIFFERENCE_MISSING = "group-form-difference-missing"
    const val FORM_CONFIRM_SIMILAR = "group-form-confirm-similar"
    const val FORM_HELD = "group-form-held"
    const val FORM_HELD_CLOSE = "group-form-held-close"
}

/** Two letters for the mark of a room: the first letters of its first two words. */
fun roomLetters(name: String): String {
    val words = name.split(' ', '-', '\'', '’').map { word -> word.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    val letters = when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> "?"
    }
    return letters.uppercase()
}

/**
 * A room of a collective: its description and territory, the notice that it is public and not
 * end-to-end encrypted, its messages with the newest at the bottom, and the field to write one
 * signed by the account. Reading needs no account; writing needs one and the room joined (a local
 * act). [onBack] is null when the room sits beside the list (wide windows).
 */
@Composable
fun GroupScreen(
    groups: GroupsLibrary,
    room: Group,
    session: AccountSession,
    onBack: (() -> Unit)?,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val account = session.state as? AccountUiState.Ready
    val me = account?.publicKey?.hex
    val joined = groups.isMember(room.id)
    val timeline = groups.timeline
    val pending = groups.pendingMessageIds
    val now = remember(timeline) { currentEpochSeconds() }
    val padding = if (compact) 16.dp else 24.dp
    // The roles, exclusions and edits of the room, computed on the device from its public journal (D-131).
    val state = groups.moderation?.takeIf { groups.selected?.id == room.id }
    val shown = state?.applyTo(room) ?: room
    var showAbout by rememberSaveable(room.id) { mutableStateOf(false) }
    var request by remember(room.id) { mutableStateOf<ModerationRequest?>(null) }

    // The caller's modifier may carry its own test tag (the first one wins): the screen's goes on an inner column.
    Box(modifier.fillMaxSize()) {
        if (showAbout) {
            RoomAboutPage(shown, state, me, onClose = { showAbout = false }, compact = compact)
        } else {
            Column(Modifier.fillMaxSize().background(palette.surface1).testTag(GroupsTags.SCREEN)) {
                Header(groups, shown, joined, onBack, compact, onAbout = { showAbout = true })
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag(GroupsTags.MESSAGES),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = padding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Reversed: the first item sits at the bottom, so the newest message comes first.
                    items(timeline.asReversed(), key = { it.id }) { item ->
                        when (item) {
                            is RoomItem.Message -> MessageBubble(
                                item.message,
                                mine = item.message.author == me,
                                pending = item.message.id in pending,
                                now = now,
                                choices = if (item.message.id in pending) emptyList() else moderationChoices(state, me, item.message),
                                onChoose = { request = it },
                            )
                            is RoomItem.Removed -> RemovedBubble(item)
                        }
                    }
                    item(key = "intro") { RoomIntro(shown, empty = timeline.isEmpty()) }
                }
                Composer(groups, shown, session, account, joined, onNavigate, padding, bannedReason = me?.let { state?.banned?.get(it) })
            }
        }
    }
    val asked = request
    if (asked != null && me != null) {
        ModerationDialog(asked, groups, session.signer, me, onDone = { request = null })
    }
}

@Composable
private fun Header(groups: GroupsLibrary, room: Group, joined: Boolean, onBack: (() -> Unit)?, compact: Boolean, onAbout: () -> Unit) {
    val palette = PpfTheme.palette
    Surface(color = palette.surface0, contentColor = palette.ink) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.groups_back), onBack, Modifier.testTag(GroupsTags.BACK), tint = palette.ink)
                    Spacer(Modifier.width(4.dp))
                }
                Avatar(seed = room.id, letters = roomLetters(room.displayName), size = 40.dp, rounded = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        room.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }.testTag(GroupsTags.TITLE),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            room.territory ?: stringResource(Res.string.groups_territory_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (room.example) {
                            Spacer(Modifier.width(6.dp))
                            ExampleBadge()
                        }
                    }
                }
                PpfIconButton(Lucide.Info, stringResource(Res.string.groups_about_open), onAbout, Modifier.testTag(ModerationTags.ABOUT_OPEN))
                if (groups.hasRemote) {
                    PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.groups_messages_refresh), groups::refreshMessages, Modifier.testTag(GroupsTags.REFRESH_MESSAGES))
                }
                if (joined && compact) {
                    // On a phone the name keeps the room: leaving is an icon, announced as such.
                    PpfIconButton(Lucide.LogOut, stringResource(Res.string.groups_leave), { groups.leave(room.id) }, Modifier.testTag(GroupsTags.ROOM_LEAVE))
                } else if (joined) {
                    PpfTextButton(
                        onClick = { groups.leave(room.id) },
                        text = stringResource(Res.string.groups_leave),
                        color = palette.inkMuted,
                        modifier = Modifier.testTag(GroupsTags.ROOM_LEAVE),
                    )
                } else {
                    PpfPrimaryButton(
                        onClick = { groups.join(room.id) },
                        text = stringResource(Res.string.groups_join),
                        small = true,
                        icon = if (compact) null else Lucide.Plus,
                        modifier = Modifier.testTag(GroupsTags.ROOM_JOIN),
                    )
                }
            }
            HorizontalDivider(color = palette.border)
        }
    }
}

/** What sits above the oldest message: the description and the notice that the room is public. */
@Composable
private fun RoomIntro(room: Group, empty: Boolean) {
    val palette = PpfTheme.palette
    var expanded by rememberSaveable(room.id) { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 8.dp)) {
        room.about?.let { about ->
            Text(
                about,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                maxLines = if (expanded) Int.MAX_VALUE else ABOUT_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (about.length > LONG_ABOUT) {
                PpfTextButton(
                    onClick = { expanded = !expanded },
                    text = stringResource(if (expanded) Res.string.groups_about_less else Res.string.groups_about_more),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        InfoBox(Lucide.Globe, Modifier.testTag(GroupsTags.NOTICE)) {
            Text(stringResource(Res.string.groups_public_notice), style = MaterialTheme.typography.bodyMedium)
        }
        if (empty) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.groups_messages_none),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(GroupsTags.MESSAGES_EMPTY),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: GroupMessage,
    mine: Boolean,
    pending: Boolean,
    now: Long,
    choices: List<ModerationRequest> = emptyList(),
    onChoose: (ModerationRequest) -> Unit = {},
) {
    val palette = PpfTheme.palette
    val author = if (mine) stringResource(Res.string.groups_message_you) else shortNpub(message.author)
    val time = RelativeTime.between(message.createdAt, now).label()
    Row(
        modifier = Modifier.fillMaxWidth().testTag(GroupsTags.MESSAGE + message.id),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            Avatar(seed = message.author, letters = avatarLetters(message.author), size = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 560.dp)
                .background(if (mine) palette.brandTint else palette.surface2, MaterialTheme.shapes.medium)
                .border(1.dp, if (mine) palette.brandTint else palette.border, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(author, style = MaterialTheme.typography.labelMedium, color = if (mine) palette.brand else palette.ink, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(time, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            if (pending) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(GroupsTags.MESSAGE_PENDING + message.id)) {
                    Icon(Lucide.Clock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.groups_message_pending), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            }
        }
        // The menu of the message and its author (D-131), outside the merged bubble so that it is reached on its own.
        MessageMenu(message.id, choices, onChoose)
    }
}

/** The bottom of the room: the state of the messages, then the field to write (or what writing needs). */
@Composable
private fun Composer(
    groups: GroupsLibrary,
    room: Group,
    session: AccountSession,
    account: AccountUiState.Ready?,
    joined: Boolean,
    onNavigate: (Space) -> Unit,
    padding: Dp,
    /** The reason of the user's exclusion from this room, when excluded (D-131). */
    bannedReason: String? = null,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    Surface(color = palette.surface0, contentColor = palette.ink) {
        Column {
            HorizontalDivider(color = palette.border)
            Column(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 10.dp)) {
                val status = when (groups.messagesStatus) {
                    GroupsStatus.Loading -> Res.string.groups_messages_loading
                    GroupsStatus.Offline -> Res.string.groups_messages_offline
                    GroupsStatus.Unreachable -> Res.string.groups_messages_unreachable
                    else -> null
                }
                if (status != null) {
                    Text(
                        stringResource(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                        modifier = Modifier.padding(bottom = 8.dp).testTag(GroupsTags.MESSAGES_STATUS),
                    )
                }
                groups.sendError?.let { error ->
                    val text = when (error) {
                        SendError.CLOSED -> Res.string.groups_send_failed_closed
                        SendError.NOT_FOUND -> Res.string.groups_send_failed_not_found
                        SendError.BANNED -> Res.string.groups_send_failed_banned
                        SendError.REFUSED -> Res.string.groups_send_failed
                    }
                    Text(
                        stringResource(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accentInk,
                        modifier = Modifier.padding(bottom = 8.dp).testTag(GroupsTags.SEND_ERROR),
                    )
                }
                // A compromised account no longer writes anywhere (S-146): the common notice says why.
                val canPublish = LocalCanPublish.current
                when {
                    account == null -> InfoBox(Lucide.KeyRound, Modifier.testTag(GroupsTags.NEED_ACCOUNT)) {
                        Text(stringResource(Res.string.groups_need_account_write), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        PpfPrimaryButton(
                            onClick = { onNavigate(Space.MON_ESPACE) },
                            text = stringResource(Res.string.groups_create_account),
                            small = true,
                            modifier = Modifier.testTag(GroupsTags.CREATE_ACCOUNT),
                        )
                    }
                    !canPublish -> PublishBlockedNotice()
                    bannedReason != null -> InfoBox(Lucide.Ban, Modifier.testTag(ModerationTags.BANNED)) {
                        Text(stringResource(Res.string.groups_mod_banned, bannedReason), style = MaterialTheme.typography.bodyMedium)
                    }
                    !room.open -> Text(
                        stringResource(Res.string.groups_closed_room),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        modifier = Modifier.testTag(GroupsTags.CLOSED),
                    )
                    !joined -> PpfPrimaryButton(
                        onClick = { groups.join(room.id) },
                        text = stringResource(Res.string.groups_join_to_write),
                        icon = Lucide.Plus,
                        modifier = Modifier.fillMaxWidth().testTag(GroupsTags.JOIN_TO_WRITE),
                    )
                    else -> Row(verticalAlignment = Alignment.Bottom) {
                        PpfTextField(
                            value = groups.messageDraft,
                            onValueChange = { groups.messageDraft = it.take(GroupMessage.MAX_TEXT_LENGTH) },
                            placeholder = stringResource(Res.string.groups_message_hint),
                            modifier = Modifier.weight(1f).heightIn(max = 160.dp).testTag(GroupsTags.INPUT),
                        )
                        Spacer(Modifier.width(8.dp))
                        PpfIconButton(
                            icon = Lucide.Send,
                            contentDescription = stringResource(Res.string.groups_send),
                            onClick = {
                                val signer = session.signer
                                if (signer != null) scope.launch { groups.send(signer) }
                            },
                            enabled = groups.messageDraft.isNotBlank() && !groups.sending,
                            tint = palette.brand,
                            modifier = Modifier.testTag(GroupsTags.SEND),
                        )
                    }
                }
            }
        }
    }
}

private const val ABOUT_LINES = 3
private const val LONG_ABOUT = 160
