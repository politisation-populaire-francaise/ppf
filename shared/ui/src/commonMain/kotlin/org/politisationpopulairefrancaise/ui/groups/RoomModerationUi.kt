package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupMessage
import org.politisationpopulairefrancaise.core.groups.ModerationAction
import org.politisationpopulairefrancaise.core.groups.ModerationEntry
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.groups_about_admins
import org.politisationpopulairefrancaise.ui.resources.groups_about_banned_count
import org.politisationpopulairefrancaise.ui.resources.groups_about_charter
import org.politisationpopulairefrancaise.ui.resources.groups_about_charter_text
import org.politisationpopulairefrancaise.ui.resources.groups_about_close
import org.politisationpopulairefrancaise.ui.resources.groups_about_description
import org.politisationpopulairefrancaise.ui.resources.groups_about_journal
import org.politisationpopulairefrancaise.ui.resources.groups_about_journal_empty
import org.politisationpopulairefrancaise.ui.resources.groups_about_journal_intro
import org.politisationpopulairefrancaise.ui.resources.groups_about_moderators
import org.politisationpopulairefrancaise.ui.resources.groups_about_no_description
import org.politisationpopulairefrancaise.ui.resources.groups_about_none
import org.politisationpopulairefrancaise.ui.resources.groups_about_roles
import org.politisationpopulairefrancaise.ui.resources.groups_about_territory
import org.politisationpopulairefrancaise.ui.resources.groups_about_title
import org.politisationpopulairefrancaise.ui.resources.groups_about_you
import org.politisationpopulairefrancaise.ui.resources.groups_journal_edited
import org.politisationpopulairefrancaise.ui.resources.groups_journal_excluded
import org.politisationpopulairefrancaise.ui.resources.groups_journal_named_admin
import org.politisationpopulairefrancaise.ui.resources.groups_journal_named_moderator
import org.politisationpopulairefrancaise.ui.resources.groups_journal_readmitted
import org.politisationpopulairefrancaise.ui.resources.groups_journal_removed
import org.politisationpopulairefrancaise.ui.resources.groups_journal_role_removed
import org.politisationpopulairefrancaise.ui.resources.groups_mod_cancel
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_admin_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_admin_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_exclude
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_exclude_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_exclude_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_moderator_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_moderator_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_name
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_readmit
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_readmit_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_readmit_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove_role
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove_role_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove_role_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove_text
import org.politisationpopulairefrancaise.ui.resources.groups_mod_confirm_remove_title
import org.politisationpopulairefrancaise.ui.resources.groups_mod_error_last_admin
import org.politisationpopulairefrancaise.ui.resources.groups_mod_error_not_allowed
import org.politisationpopulairefrancaise.ui.resources.groups_mod_error_offline
import org.politisationpopulairefrancaise.ui.resources.groups_mod_error_other
import org.politisationpopulairefrancaise.ui.resources.groups_mod_error_reason
import org.politisationpopulairefrancaise.ui.resources.groups_mod_exclude
import org.politisationpopulairefrancaise.ui.resources.groups_mod_message_menu
import org.politisationpopulairefrancaise.ui.resources.groups_mod_name_admin
import org.politisationpopulairefrancaise.ui.resources.groups_mod_name_moderator
import org.politisationpopulairefrancaise.ui.resources.groups_mod_public_signed
import org.politisationpopulairefrancaise.ui.resources.groups_mod_readmit
import org.politisationpopulairefrancaise.ui.resources.groups_mod_reason_label
import org.politisationpopulairefrancaise.ui.resources.groups_mod_reason_missing
import org.politisationpopulairefrancaise.ui.resources.groups_mod_reason_placeholder
import org.politisationpopulairefrancaise.ui.resources.groups_mod_remove_message
import org.politisationpopulairefrancaise.ui.resources.groups_mod_remove_role
import org.politisationpopulairefrancaise.ui.resources.groups_mod_removed_by_author
import org.politisationpopulairefrancaise.ui.resources.groups_mod_removed_by_moderator
import org.politisationpopulairefrancaise.ui.resources.groups_mod_sending
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the moderation of a room (D-131). */
object ModerationTags {
    const val MESSAGE_MENU = "group-message-menu-" // followed by the event id
    const val MENU_REMOVE = "group-menu-remove"
    const val MENU_ADMIN = "group-menu-admin"
    const val MENU_MODERATOR = "group-menu-moderator"
    const val MENU_REMOVE_ROLE = "group-menu-remove-role"
    const val MENU_EXCLUDE = "group-menu-exclude"
    const val MENU_READMIT = "group-menu-readmit"
    const val REMOVED = "group-removed-" // followed by the event id of the removed message
    const val DIALOG = "group-mod-dialog"
    const val DIALOG_PUBLIC = "group-mod-dialog-public"
    const val DIALOG_REASON = "group-mod-dialog-reason"
    const val DIALOG_REASON_MISSING = "group-mod-dialog-reason-missing"
    const val DIALOG_CANCEL = "group-mod-dialog-cancel"
    const val DIALOG_CONFIRM = "group-mod-dialog-confirm"
    const val ERROR = "group-mod-error"
    const val BANNED = "group-banned"
    const val ABOUT_OPEN = "group-about-open"
    const val ABOUT = "group-about"
    const val ABOUT_CLOSE = "group-about-close"
    const val ABOUT_DESCRIPTION = "group-about-description"
    const val ABOUT_TERRITORY = "group-about-territory"
    const val ABOUT_CHARTER = "group-about-charter"
    const val ABOUT_ADMIN = "group-about-admin-" // followed by the key
    const val ABOUT_MODERATOR = "group-about-moderator-" // followed by the key
    const val ABOUT_BANNED = "group-about-banned"
    const val ABOUT_JOURNAL = "group-about-journal"
    const val ABOUT_JOURNAL_ENTRY = "group-about-journal-" // followed by the event id
}

/** A moderation action the user asked for, waiting for their confirmation. */
sealed interface ModerationRequest {
    data class RemoveMessage(val message: GroupMessage) : ModerationRequest

    data class Name(val target: String, val role: RoomRole) : ModerationRequest

    data class RemoveRole(val target: String) : ModerationRequest

    data class Exclude(val target: String) : ModerationRequest

    data class Readmit(val target: String) : ModerationRequest
}

/** The actions [me] may take on [message] and its author, in the order of the menu. */
internal fun moderationChoices(state: RoomModeration?, me: String?, message: GroupMessage): List<ModerationRequest> {
    if (state == null || me == null) return emptyList()
    val author = message.author
    return buildList {
        if (state.canRemove(me, author)) add(ModerationRequest.RemoveMessage(message))
        if (author != me && state.canNameRoles(me)) {
            if (state.roleOf(author) != RoomRole.ADMIN) add(ModerationRequest.Name(author, RoomRole.ADMIN))
            if (state.roleOf(author) != RoomRole.MODERATOR) add(ModerationRequest.Name(author, RoomRole.MODERATOR))
        }
        if (state.canRemoveRole(me, author)) add(ModerationRequest.RemoveRole(author))
        if (author != me && !state.isBanned(author) && state.canExclude(me, author)) add(ModerationRequest.Exclude(author))
        if (author != me && state.canReadmit(me, author)) add(ModerationRequest.Readmit(author))
    }
}

/** The "…" button of a message and its menu; nothing when [choices] is empty. */
@Composable
internal fun MessageMenu(messageId: String, choices: List<ModerationRequest>, onChoose: (ModerationRequest) -> Unit) {
    if (choices.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        PpfIconButton(
            icon = Lucide.Ellipsis,
            contentDescription = stringResource(Res.string.groups_mod_message_menu),
            onClick = { expanded = true },
            modifier = Modifier.testTag(ModerationTags.MESSAGE_MENU + messageId),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = PpfTheme.palette.surface2) {
            choices.forEach { choice ->
                val (label, tag) = when (choice) {
                    is ModerationRequest.RemoveMessage -> Res.string.groups_mod_remove_message to ModerationTags.MENU_REMOVE
                    is ModerationRequest.Name -> if (choice.role == RoomRole.ADMIN) {
                        Res.string.groups_mod_name_admin to ModerationTags.MENU_ADMIN
                    } else {
                        Res.string.groups_mod_name_moderator to ModerationTags.MENU_MODERATOR
                    }
                    is ModerationRequest.RemoveRole -> Res.string.groups_mod_remove_role to ModerationTags.MENU_REMOVE_ROLE
                    is ModerationRequest.Exclude -> Res.string.groups_mod_exclude to ModerationTags.MENU_EXCLUDE
                    is ModerationRequest.Readmit -> Res.string.groups_mod_readmit to ModerationTags.MENU_READMIT
                }
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(iconOf(choice), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onChoose(choice)
                    },
                    modifier = Modifier.testTag(tag),
                )
            }
        }
    }
}

private fun iconOf(choice: ModerationRequest) = when (choice) {
    is ModerationRequest.RemoveMessage -> Lucide.X
    is ModerationRequest.Name -> Lucide.ShieldCheck
    is ModerationRequest.RemoveRole -> Lucide.Minus
    is ModerationRequest.Exclude -> Lucide.Ban
    is ModerationRequest.Readmit -> Lucide.Check
}

/**
 * The confirmation of a moderation action: what it does, that it is public and signed with the
 * user's pseudonym, the reason when it is mandatory (removal, exclusion); the safe choice comes first
 * and most visible ("Annuler"). The dialog stays open while the server answers, and shows its refusal.
 */
@Composable
internal fun ModerationDialog(
    request: ModerationRequest,
    groups: GroupsLibrary,
    signer: EventSigner?,
    me: String,
    onDone: () -> Unit,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var reason by remember(request) { mutableStateOf("") }
    var missing by remember(request) { mutableStateOf(false) }
    val needsReason = request is ModerationRequest.RemoveMessage || request is ModerationRequest.Exclude
    val target = when (request) {
        is ModerationRequest.RemoveMessage -> request.message.author
        is ModerationRequest.Name -> request.target
        is ModerationRequest.RemoveRole -> request.target
        is ModerationRequest.Exclude -> request.target
        is ModerationRequest.Readmit -> request.target
    }
    val who = if (target == me) stringResource(Res.string.groups_about_you) else shortNpub(target)
    val (title, text, confirm) = when (request) {
        is ModerationRequest.RemoveMessage -> Triple(stringResource(Res.string.groups_mod_confirm_remove_title), stringResource(Res.string.groups_mod_confirm_remove_text), Res.string.groups_mod_confirm_remove)
        is ModerationRequest.Name -> if (request.role == RoomRole.ADMIN) {
            Triple(stringResource(Res.string.groups_mod_confirm_admin_title, who), stringResource(Res.string.groups_mod_confirm_admin_text), Res.string.groups_mod_confirm_name)
        } else {
            Triple(stringResource(Res.string.groups_mod_confirm_moderator_title, who), stringResource(Res.string.groups_mod_confirm_moderator_text), Res.string.groups_mod_confirm_name)
        }
        is ModerationRequest.RemoveRole -> Triple(stringResource(Res.string.groups_mod_confirm_remove_role_title, who), stringResource(Res.string.groups_mod_confirm_remove_role_text), Res.string.groups_mod_confirm_remove_role)
        is ModerationRequest.Exclude -> Triple(stringResource(Res.string.groups_mod_confirm_exclude_title, who), stringResource(Res.string.groups_mod_confirm_exclude_text), Res.string.groups_mod_confirm_exclude)
        is ModerationRequest.Readmit -> Triple(stringResource(Res.string.groups_mod_confirm_readmit_title, who), stringResource(Res.string.groups_mod_confirm_readmit_text), Res.string.groups_mod_confirm_readmit)
    }
    val dismiss = {
        groups.dismissModerationError()
        onDone()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(ModerationTags.DIALOG),
        icon = { Icon(iconOf(request), contentDescription = null, tint = palette.brand) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.groups_mod_public_signed, shortNpub(me)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink,
                    modifier = Modifier.testTag(ModerationTags.DIALOG_PUBLIC),
                )
                if (needsReason) {
                    Text(stringResource(Res.string.groups_mod_reason_label), style = MaterialTheme.typography.titleSmall, color = palette.ink)
                    PpfTextField(
                        value = reason,
                        onValueChange = {
                            reason = it.take(RoomModeration.MAX_REASON_LENGTH)
                            missing = false
                        },
                        placeholder = stringResource(Res.string.groups_mod_reason_placeholder),
                        modifier = Modifier.fillMaxWidth().testTag(ModerationTags.DIALOG_REASON),
                        minLines = 2,
                    )
                    if (missing) {
                        Text(
                            stringResource(Res.string.groups_mod_reason_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.accentInk,
                            modifier = Modifier.testTag(ModerationTags.DIALOG_REASON_MISSING),
                        )
                    }
                }
                groups.moderationError?.let { error ->
                    val message = when (error) {
                        ModerationError.NOT_ALLOWED -> Res.string.groups_mod_error_not_allowed
                        ModerationError.LAST_ADMIN -> Res.string.groups_mod_error_last_admin
                        ModerationError.REASON -> Res.string.groups_mod_error_reason
                        ModerationError.OFFLINE -> Res.string.groups_mod_error_offline
                        ModerationError.OTHER -> Res.string.groups_mod_error_other
                    }
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk, modifier = Modifier.testTag(ModerationTags.ERROR))
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = dismiss, text = stringResource(Res.string.groups_mod_cancel), modifier = Modifier.fillMaxWidth().testTag(ModerationTags.DIALOG_CANCEL))
                PpfSecondaryButton(
                    onClick = {
                        if (needsReason && reason.isBlank()) {
                            missing = true
                        } else if (signer != null) {
                            scope.launch {
                                val done = when (request) {
                                    is ModerationRequest.RemoveMessage -> groups.removeMessage(signer, request.message, reason)
                                    is ModerationRequest.Name -> groups.setRole(signer, request.target, request.role)
                                    is ModerationRequest.RemoveRole -> groups.setRole(signer, request.target, null)
                                    is ModerationRequest.Exclude -> groups.exclude(signer, request.target, reason)
                                    is ModerationRequest.Readmit -> groups.readmit(signer, request.target)
                                }
                                if (done) onDone()
                            }
                        }
                    },
                    text = stringResource(if (groups.moderationBusy) Res.string.groups_mod_sending else confirm),
                    enabled = !groups.moderationBusy,
                    modifier = Modifier.fillMaxWidth().testTag(ModerationTags.DIALOG_CONFIRM),
                )
            }
        },
    )
}

/** The place of a removed message: who removed it (a moderator, or its author) and why. */
@Composable
internal fun RemovedBubble(item: RoomItem.Removed) {
    val palette = PpfTheme.palette
    val text = if (item.removal.byAuthor) {
        stringResource(Res.string.groups_mod_removed_by_author, item.removal.reason)
    } else {
        stringResource(Res.string.groups_mod_removed_by_moderator, item.removal.reason)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .border(1.dp, palette.border, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { }
            .testTag(ModerationTags.REMOVED + item.removal.eventId),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.EyeOff, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
}

/**
 * "À propos du salon": its presentation, territory and charter, the roles in force, the number of
 * excluded accounts and the public journal of moderation, newest first. Everything shown here is
 * public and signed; no list of members exists.
 */
@Composable
internal fun RoomAboutPage(room: Group, state: RoomModeration?, me: String?, onClose: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val padding = if (compact) 16.dp else 24.dp
    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(padding).testTag(ModerationTags.ABOUT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.groups_about_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                PpfIconButton(Lucide.X, stringResource(Res.string.groups_about_close), onClose, Modifier.testTag(ModerationTags.ABOUT_CLOSE), tint = palette.ink)
            }
            Text(room.displayName, style = MaterialTheme.typography.titleMedium)
            Section(Res.string.groups_about_description)
            Text(
                room.about ?: stringResource(Res.string.groups_about_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = if (room.about == null) palette.inkMuted else palette.ink,
                modifier = Modifier.testTag(ModerationTags.ABOUT_DESCRIPTION),
            )
            Section(Res.string.groups_about_territory)
            Text(room.territory.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(ModerationTags.ABOUT_TERRITORY))
            Section(Res.string.groups_about_charter)
            Text(
                stringResource(Res.string.groups_about_charter_text, room.charter ?: Group.CHARTER_VERSION),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(ModerationTags.ABOUT_CHARTER),
            )

            Section(Res.string.groups_about_roles)
            val admins = state?.admins ?: listOf(room.event.pubkey)
            val moderators = state?.moderators.orEmpty()
            RoleList(stringResource(Res.string.groups_about_admins), admins, me, ModerationTags.ABOUT_ADMIN)
            RoleList(stringResource(Res.string.groups_about_moderators), moderators, me, ModerationTags.ABOUT_MODERATOR)
            Text(
                stringResource(Res.string.groups_about_banned_count, state?.banned?.size ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(ModerationTags.ABOUT_BANNED),
            )

            Section(Res.string.groups_about_journal)
            Text(stringResource(Res.string.groups_about_journal_intro), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            Column(Modifier.fillMaxWidth().testTag(ModerationTags.ABOUT_JOURNAL), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val journal = state?.journal.orEmpty().asReversed()
                if (journal.isEmpty()) {
                    Text(stringResource(Res.string.groups_about_journal_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                journal.forEach { entry ->
                    Text(
                        journalLine(entry, me),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().testTag(ModerationTags.ABOUT_JOURNAL_ENTRY + entry.event.id),
                    )
                    HorizontalDivider(color = palette.border)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: StringResource) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun RoleList(label: String, keys: List<String>, me: String?, tag: String) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().heightIn(min = 24.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.ink)
        if (keys.isEmpty()) {
            Text(stringResource(Res.string.groups_about_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        val you = stringResource(Res.string.groups_about_you)
        keys.forEach { key ->
            Text(if (key == me) "${shortNpub(key)} ($you)" else shortNpub(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(tag + key))
        }
    }
}

/** One line of the journal, in French: "npub1… a exclu npub1… : motif". */
@Composable
private fun journalLine(entry: ModerationEntry, me: String?): String {
    val you = stringResource(Res.string.groups_about_you)
    fun who(key: String) = if (key == me) you else shortNpub(key)
    val actor = who(entry.actor)
    return when (val action = entry.action) {
        is ModerationAction.Named -> stringResource(
            if (action.role == RoomRole.ADMIN) Res.string.groups_journal_named_admin else Res.string.groups_journal_named_moderator,
            actor,
            who(action.target),
        )
        is ModerationAction.RoleRemoved -> stringResource(Res.string.groups_journal_role_removed, actor, who(action.target))
        is ModerationAction.Excluded -> stringResource(Res.string.groups_journal_excluded, actor, who(action.target), action.reason)
        is ModerationAction.Readmitted -> stringResource(Res.string.groups_journal_readmitted, actor, who(action.target))
        is ModerationAction.Edited -> stringResource(Res.string.groups_journal_edited, actor)
        is ModerationAction.Removed -> stringResource(Res.string.groups_journal_removed, actor, action.reason)
    }
}
