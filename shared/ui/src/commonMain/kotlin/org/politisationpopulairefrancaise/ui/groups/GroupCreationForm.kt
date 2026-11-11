package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.groups_create_account
import org.politisationpopulairefrancaise.ui.resources.groups_form_about
import org.politisationpopulairefrancaise.ui.resources.groups_form_about_placeholder
import org.politisationpopulairefrancaise.ui.resources.groups_form_blocked_charter
import org.politisationpopulairefrancaise.ui.resources.groups_form_blocked_fields
import org.politisationpopulairefrancaise.ui.resources.groups_form_cancel
import org.politisationpopulairefrancaise.ui.resources.groups_form_confirm_similar
import org.politisationpopulairefrancaise.ui.resources.groups_form_difference_label
import org.politisationpopulairefrancaise.ui.resources.groups_form_difference_missing
import org.politisationpopulairefrancaise.ui.resources.groups_form_difference_placeholder
import org.politisationpopulairefrancaise.ui.resources.groups_form_held
import org.politisationpopulairefrancaise.ui.resources.groups_form_held_close
import org.politisationpopulairefrancaise.ui.resources.groups_form_held_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_too_many
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_too_recent
import org.politisationpopulairefrancaise.ui.resources.groups_form_similar_join
import org.politisationpopulairefrancaise.ui.resources.groups_form_similar_server
import org.politisationpopulairefrancaise.ui.resources.groups_form_similar_server_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_charter_accept
import org.politisationpopulairefrancaise.ui.resources.groups_form_charter_rooms
import org.politisationpopulairefrancaise.ui.resources.groups_form_charter_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_close
import org.politisationpopulairefrancaise.ui.resources.groups_form_counter
import org.politisationpopulairefrancaise.ui.resources.groups_form_edit
import org.politisationpopulairefrancaise.ui.resources.groups_form_exists
import org.politisationpopulairefrancaise.ui.resources.groups_form_exists_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_id
import org.politisationpopulairefrancaise.ui.resources.groups_form_identity_text
import org.politisationpopulairefrancaise.ui.resources.groups_form_identity_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_intro
import org.politisationpopulairefrancaise.ui.resources.groups_form_invalid
import org.politisationpopulairefrancaise.ui.resources.groups_form_join_existing
import org.politisationpopulairefrancaise.ui.resources.groups_form_name
import org.politisationpopulairefrancaise.ui.resources.groups_form_name_placeholder
import org.politisationpopulairefrancaise.ui.resources.groups_form_need_account
import org.politisationpopulairefrancaise.ui.resources.groups_form_no_server
import org.politisationpopulairefrancaise.ui.resources.groups_form_offline
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_charter
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_name
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_territory
import org.politisationpopulairefrancaise.ui.resources.groups_form_rejected_too_long
import org.politisationpopulairefrancaise.ui.resources.groups_form_similar_text
import org.politisationpopulairefrancaise.ui.resources.groups_form_similar_title
import org.politisationpopulairefrancaise.ui.resources.groups_form_submit
import org.politisationpopulairefrancaise.ui.resources.groups_form_submitting
import org.politisationpopulairefrancaise.ui.resources.groups_form_taken
import org.politisationpopulairefrancaise.ui.resources.groups_form_territory
import org.politisationpopulairefrancaise.ui.resources.groups_form_territory_help
import org.politisationpopulairefrancaise.ui.resources.groups_form_territory_placeholder
import org.politisationpopulairefrancaise.ui.resources.groups_form_title
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden_text
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The creation of a room (decision D-028): its name, its territory and an optional description,
 * the rooms that already look like it (search before creating), then the charter of values to
 * accept. The safe choice comes first: the charter is unticked until the user ticks it, and the
 * button that cancels stands before the one that creates. When the server finds a room of the same
 * name on the same territory, the form offers to join it instead.
 */
@Composable
fun GroupCreationForm(
    groups: GroupsLibrary,
    session: AccountSession,
    onClose: () -> Unit,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val draft = groups.creationDraft
    val account = session.state as? AccountUiState.Ready
    val scope = rememberCoroutineScope()
    val known = groups.groups
    val similar = remember(draft.name, draft.territory, known) { groups.similarTo(draft) }
    val padding = if (compact) 16.dp else 24.dp

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(padding).testTag(GroupsTags.FORM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.groups_form_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    PpfIconButton(Lucide.X, stringResource(Res.string.groups_form_close), onClose, Modifier.testTag(GroupsTags.FORM_CLOSE), tint = palette.ink)
                }
                Spacer(Modifier.height(12.dp))
                InfoBox(Lucide.Globe) {
                    Text(stringResource(Res.string.groups_form_intro), style = MaterialTheme.typography.bodyMedium)
                }
                if (!groups.hasRemote) {
                    Spacer(Modifier.height(12.dp))
                    Callout(CalloutType.ATTENTION, Modifier.testTag(GroupsTags.FORM_NO_SERVER)) {
                        Text(stringResource(Res.string.groups_form_no_server), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                val held = groups.heldCreation
                if (held != null) {
                    HeldView(held, onClose)
                } else {
                    Field(
                        label = stringResource(Res.string.groups_form_name),
                        counter = stringResource(Res.string.groups_form_counter, draft.name.trim().length, Group.MAX_NAME_LENGTH),
                        overLimit = draft.nameTooLong,
                    ) {
                        PpfTextField(
                            value = draft.name,
                            onValueChange = { groups.updateDraft(draft.copy(name = it)) },
                            placeholder = stringResource(Res.string.groups_form_name_placeholder),
                            modifier = Modifier.fillMaxWidth().testTag(GroupsTags.FORM_NAME),
                            singleLine = true,
                        )
                    }
                    Field(
                        label = stringResource(Res.string.groups_form_territory),
                        help = stringResource(Res.string.groups_form_territory_help),
                        overLimit = draft.territoryTooLong,
                    ) {
                        PpfTextField(
                            value = draft.territory,
                            onValueChange = { groups.updateDraft(draft.copy(territory = it)) },
                            placeholder = stringResource(Res.string.groups_form_territory_placeholder),
                            modifier = Modifier.fillMaxWidth().testTag(GroupsTags.FORM_TERRITORY),
                            singleLine = true,
                        )
                    }
                    Field(
                        label = stringResource(Res.string.groups_form_about),
                        counter = stringResource(Res.string.groups_form_counter, draft.about.trim().length, Group.MAX_ABOUT_LENGTH),
                        overLimit = draft.aboutTooLong,
                    ) {
                        PpfTextField(
                            value = draft.about,
                            onValueChange = { groups.updateDraft(draft.copy(about = it)) },
                            placeholder = stringResource(Res.string.groups_form_about_placeholder),
                            modifier = Modifier.fillMaxWidth().testTag(GroupsTags.FORM_ABOUT),
                            minLines = 3,
                        )
                    }
                    if (draft.proposedId.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.groups_form_id, draft.proposedId),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSubtle,
                            modifier = Modifier.testTag(GroupsTags.FORM_ID),
                        )
                    }

                    if (similar.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(Modifier.testTag(GroupsTags.FORM_SIMILAR), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.groups_form_similar_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            Text(stringResource(Res.string.groups_form_similar_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                            similar.forEach { room ->
                                RoomCard(
                                    room = room,
                                    joined = groups.isMember(room.id),
                                    selected = false,
                                    onOpen = { groups.openInstead(room) },
                                    onJoin = { groups.joinExisting(room.id) },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Charter(draft.charterAccepted, onToggle = { groups.updateDraft(draft.copy(charterAccepted = !draft.charterAccepted)) })

                    Spacer(Modifier.height(20.dp))
                    // The work starts from the form's scope: the error box leaves the screen as soon as a new attempt begins.
                    CreationErrorView(groups, session, launch = { block -> scope.launch { block() } })

                    if (account == null) {
                        InfoBox(Lucide.KeyRound, Modifier.testTag(GroupsTags.FORM_NEED_ACCOUNT)) {
                            Text(stringResource(Res.string.groups_form_need_account), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            PpfPrimaryButton(
                                onClick = { onNavigate(Space.MON_ESPACE) },
                                text = stringResource(Res.string.groups_create_account),
                                modifier = Modifier.testTag(GroupsTags.FORM_CREATE_ACCOUNT),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PpfTextButton(onClick = onClose, text = stringResource(Res.string.groups_form_cancel), color = palette.inkMuted, modifier = Modifier.testTag(GroupsTags.FORM_CANCEL))
                    } else {
                        // Creating publishes the creator's key next to a collective (GDPR, article 9): said before the button.
                        Callout(CalloutType.ATTENTION, Modifier.testTag(GroupsTags.FORM_IDENTITY), title = stringResource(Res.string.groups_form_identity_title)) {
                            Text(stringResource(Res.string.groups_form_identity_text), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        Actions(groups, session, onClose, launch = { block -> scope.launch { block() } })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Leaves the form for a room found by the search before creating. */
private fun GroupsLibrary.openInstead(room: Group) {
    cancelCreation()
    open(room)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(groups: GroupsLibrary, session: AccountSession, onClose: () -> Unit, launch: (suspend () -> Unit) -> Unit) {
    val palette = PpfTheme.palette
    val draft = groups.creationDraft
    val ready = draft.complete && draft.charterAccepted && groups.hasRemote && !groups.creationBusy
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PpfSecondaryButton(onClick = onClose, text = stringResource(Res.string.groups_form_cancel), modifier = Modifier.testTag(GroupsTags.FORM_CANCEL))
        PpfPrimaryButton(
            onClick = {
                val signer = session.signer
                if (signer != null) launch { groups.create(signer) }
            },
            text = stringResource(if (groups.creationBusy) Res.string.groups_form_submitting else Res.string.groups_form_submit),
            enabled = ready,
            icon = Lucide.Plus,
            modifier = Modifier.testTag(GroupsTags.FORM_SUBMIT),
        )
    }
    val blocked = when {
        !groups.hasRemote -> null // already said at the top of the form
        !draft.complete -> Res.string.groups_form_blocked_fields
        !draft.charterAccepted -> Res.string.groups_form_blocked_charter
        else -> null
    }
    if (blocked != null) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(blocked), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(GroupsTags.FORM_BLOCKED))
    }
}

/** The creation is held for the team's review (D-132): "En attente de vérification". Also shown by the wizard of the collectifs of Agir. */
@Composable
internal fun HeldView(room: Group, onClose: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Callout(CalloutType.CONSEIL, Modifier.testTag(GroupsTags.FORM_HELD), title = stringResource(Res.string.groups_form_held_title)) {
        Text(room.displayName, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.groups_form_held), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(16.dp))
    PpfPrimaryButton(onClick = onClose, text = stringResource(Res.string.groups_form_held_close), modifier = Modifier.testTag(GroupsTags.FORM_HELD_CLOSE))
    Spacer(Modifier.height(24.dp))
}

/**
 * Rooms of close name found by the server (D-132): join one of them (first and most visible), or
 * create anyway after saying how the room differs, which is published with its description.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimilarView(groups: GroupsLibrary, session: AccountSession, error: CreationError.Similar, launch: (suspend () -> Unit) -> Unit) {
    val palette = PpfTheme.palette
    val draft = groups.creationDraft
    var missing by remember(error) { mutableStateOf(false) }
    Callout(CalloutType.ATTENTION, Modifier.testTag(GroupsTags.FORM_SIMILAR_SERVER), title = stringResource(Res.string.groups_form_similar_server_title)) {
        Text(stringResource(Res.string.groups_form_similar_server), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error.rooms.forEach { room ->
                val name = room.name?.trimStart()?.removePrefix(Group.EXAMPLE_MARKER)?.trim()?.takeIf { it.isNotEmpty() } ?: room.id
                PpfPrimaryButton(
                    onClick = { launch { groups.joinSimilar(room.id) } },
                    text = stringResource(Res.string.groups_form_similar_join, name, room.territory.orEmpty()),
                    icon = Lucide.Users,
                    modifier = Modifier.testTag(GroupsTags.FORM_SIMILAR_JOIN + room.id),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.groups_form_difference_label), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        PpfTextField(
            value = draft.difference,
            onValueChange = {
                groups.updateDifference(it.take(Group.MAX_DIFFERENCE_LENGTH))
                missing = false
            },
            placeholder = stringResource(Res.string.groups_form_difference_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(GroupsTags.FORM_DIFFERENCE),
            minLines = 2,
        )
        if (missing) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.groups_form_difference_missing), style = MaterialTheme.typography.bodySmall, color = palette.accentInk, modifier = Modifier.testTag(GroupsTags.FORM_DIFFERENCE_MISSING))
        }
        Spacer(Modifier.height(12.dp))
        PpfSecondaryButton(
            onClick = {
                val signer = session.signer
                if (draft.difference.isBlank()) {
                    missing = true
                } else if (signer != null) {
                    launch { groups.confirmSimilar(signer) }
                }
            },
            text = stringResource(if (groups.creationBusy) Res.string.groups_form_submitting else Res.string.groups_form_confirm_similar),
            enabled = !groups.creationBusy,
            modifier = Modifier.testTag(GroupsTags.FORM_CONFIRM_SIMILAR),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** What went wrong with the last attempt; for a duplicate, the offer to join the existing room. Also shown by the wizard of the collectifs of Agir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreationErrorView(groups: GroupsLibrary, session: AccountSession, launch: (suspend () -> Unit) -> Unit) {
    val palette = PpfTheme.palette
    when (val error = groups.creationError) {
        null -> Unit
        is CreationError.Similar -> SimilarView(groups, session, error, launch)
        is CreationError.Exists -> {
            Callout(CalloutType.ATTENTION, Modifier.testTag(GroupsTags.FORM_EXISTS), title = stringResource(Res.string.groups_form_exists_title)) {
                Text(stringResource(Res.string.groups_form_exists, error.existingName), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PpfPrimaryButton(
                        onClick = { groups.joinExisting(error.existingId) },
                        text = stringResource(Res.string.groups_form_join_existing),
                        icon = Lucide.Users,
                        modifier = Modifier.testTag(GroupsTags.FORM_JOIN_EXISTING),
                    )
                    PpfTextButton(onClick = groups::dismissCreationError, text = stringResource(Res.string.groups_form_edit), modifier = Modifier.testTag(GroupsTags.FORM_EDIT))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        else -> {
            val text = when (error) {
                CreationError.Taken -> Res.string.groups_form_taken
                CreationError.InvalidName -> Res.string.groups_form_invalid
                CreationError.Offline -> Res.string.groups_form_offline
                CreationError.TooMany -> Res.string.groups_form_rejected_too_many
                CreationError.TooRecent -> Res.string.groups_form_rejected_too_recent
                is CreationError.Similar -> Res.string.groups_form_rejected // handled above
                is CreationError.Refused -> when (error.reason) {
                    RejectionReason.MISSING_TERRITORY -> Res.string.groups_form_rejected_territory
                    RejectionReason.MISSING_CHARTER -> Res.string.groups_form_rejected_charter
                    RejectionReason.MISSING_NAME -> Res.string.groups_form_rejected_name
                    RejectionReason.DESCRIPTION_TOO_LONG -> Res.string.groups_form_rejected_too_long
                    else -> Res.string.groups_form_rejected
                }
                is CreationError.Exists -> Res.string.groups_form_rejected // handled above
            }
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk, modifier = Modifier.testTag(GroupsTags.FORM_ERROR))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A labelled block of the form: bold label, optional help line and counter, then the control. */
@Composable
private fun ColumnScope.Field(
    label: String,
    help: String? = null,
    counter: String? = null,
    overLimit: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        if (counter != null) Text(counter, style = MaterialTheme.typography.bodySmall, color = if (overLimit) palette.accentInk else palette.inkSubtle)
    }
    if (help != null) {
        Spacer(Modifier.height(4.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    content()
}

/** The charter of values: what is allowed, what is forbidden, and the box to tick (unticked by default). */
@Composable
private fun Charter(accepted: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Text(stringResource(Res.string.groups_form_charter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.groups_form_charter_rooms), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.LEGAL, title = stringResource(Res.string.propose_charter_allowed)) {
        Text(stringResource(Res.string.propose_charter_allowed_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.ILLEGAL, title = stringResource(Res.string.propose_charter_forbidden)) {
        Text(stringResource(Res.string.propose_charter_forbidden_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp)
            .testTag(GroupsTags.FORM_CHARTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp)
                .background(if (accepted) palette.brand else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .border(2.dp, if (accepted) palette.brand else palette.borderStrong, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (accepted) Icon(Lucide.Check, contentDescription = null, tint = palette.onBrand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.groups_form_charter_accept), style = MaterialTheme.typography.bodyLarge)
    }
}
