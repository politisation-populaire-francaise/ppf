package org.politisationpopulairefrancaise.ui.agir.collectives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.groups.CreationError
import org.politisationpopulairefrancaise.ui.groups.CreationErrorView
import org.politisationpopulairefrancaise.ui.groups.HeldView
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.collectifs_checking
import org.politisationpopulairefrancaise.ui.resources.collectifs_create_room
import org.politisationpopulairefrancaise.ui.resources.collectifs_need_account
import org.politisationpopulairefrancaise.ui.resources.collectifs_need_account_button
import org.politisationpopulairefrancaise.ui.resources.collectifs_needs_verified_how
import org.politisationpopulairefrancaise.ui.resources.collectifs_needs_verified_not_yet
import org.politisationpopulairefrancaise.ui.resources.collectifs_needs_verified_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_needs_verified_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_needs_verified_unknown
import org.politisationpopulairefrancaise.ui.resources.collectifs_open_my_space
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_about_too_long
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_charter_not_accepted
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_internal_charter_too_long
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_name_missing
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_name_too_long
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_scope_missing
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_territory_missing
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_territory_too_long
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_theme_too_long
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_too_many_themes
import org.politisationpopulairefrancaise.ui.resources.collectifs_problem_type_missing
import org.politisationpopulairefrancaise.ui.resources.collectifs_room_instead
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_about
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_about_placeholder
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_cancel
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_charter_accept
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_charter_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_charter_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_close
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_counter
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_id
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_internal_charter
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_internal_charter_help
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_internal_charter_placeholder
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_language
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_missing
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_name
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_name_placeholder
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_next
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_no_server
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_previous
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_refused
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_scope
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_soon_invitation
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_soon_private
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_soon_request
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_soon_secret
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_soon_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_step
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_step_charter
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_step_confirm
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_step_identity
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_step_presentation
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_submit
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_submitting
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_summary_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_territory
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_territory_code
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_territory_code_clear
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_territory_help
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_territory_placeholder
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_themes
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_themes_help
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_themes_placeholder
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_type
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_visibility_public
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_visibility_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_warning_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_wizard_warning_title
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden_text
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * « Créer un collectif » (S-129, D-087). An account not validated in person gets the explanation,
 * the way to get validated (the card « Mon niveau de votant » of "Mon espace") and the creation of a
 * salon, open to all; never the wizard. A validated account goes through four steps: name, territory
 * and scope; presentation, type, themes and language; internal charter; then a summary with the
 * visibility (public and open only, the rest said honestly as coming), the charter of PPF unticked
 * by default and the warning that the founder's pseudonym is publicly linked to the collectif, with
 * « Annuler » before « Créer le collectif ». The sending goes through the creation path of the rooms,
 * whose views say what the server answered (duplicate, close names, held, offline).
 */
@Composable
internal fun CollectiveCreationWizard(
    library: CollectivesLibrary,
    session: AccountSession,
    compact: Boolean,
    onNavigate: (Space) -> Unit,
    onCreateRoom: () -> Unit,
) {
    val palette = PpfTheme.palette
    val check = library.founderCheck
    // The views of the rooms' form may open a room: created after confirming despite close names, or an existing one joined.
    val opened = library.groups.selected
    LaunchedEffect(opened?.id) { opened?.let(library::roomOpenedFromWizard) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(CollectivesTags.WIZARD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.collectifs_wizard_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                PpfIconButton(Lucide.X, stringResource(Res.string.collectifs_wizard_close), library::cancelCreation, Modifier.testTag(CollectivesTags.WIZARD_CLOSE), tint = palette.ink)
            }
            Spacer(Modifier.height(12.dp))
            when {
                check == FounderCheck.NoAccount -> InfoBox(Lucide.KeyRound, Modifier.testTag(CollectivesTags.NEED_ACCOUNT)) {
                    Text(stringResource(Res.string.collectifs_need_account), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    PpfPrimaryButton(onClick = { onNavigate(Space.MON_ESPACE) }, text = stringResource(Res.string.collectifs_need_account_button))
                }
                check == FounderCheck.Checking -> Text(
                    stringResource(Res.string.collectifs_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(CollectivesTags.CHECKING),
                )
                !check.canFound -> NeedsVerified(check, onNavigate, onCreateRoom)
                else -> Steps(library, session)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Why this account cannot found a collectif yet, how to get validated, and the salon open to all meanwhile. */
@Composable
private fun NeedsVerified(check: FounderCheck, onNavigate: (Space) -> Unit, onCreateRoom: () -> Unit) {
    val notYet = check is FounderCheck.Known && check.level == VoteLevel.NON_VERIFIE
    Callout(CalloutType.CONSEIL, Modifier.testTag(CollectivesTags.NEEDS_VERIFIED), title = stringResource(Res.string.collectifs_needs_verified_title)) {
        Text(stringResource(Res.string.collectifs_needs_verified_text), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (notYet) Res.string.collectifs_needs_verified_not_yet else Res.string.collectifs_needs_verified_unknown),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(Res.string.collectifs_needs_verified_how), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    PpfPrimaryButton(
        onClick = { onNavigate(Space.MON_ESPACE) },
        text = stringResource(Res.string.collectifs_open_my_space),
        modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.OPEN_MY_SPACE),
    )
    Spacer(Modifier.height(16.dp))
    InfoBox(Lucide.MessagesSquare) {
        Text(stringResource(Res.string.collectifs_room_instead), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        PpfSecondaryButton(
            onClick = onCreateRoom,
            text = stringResource(Res.string.collectifs_create_room),
            icon = Lucide.Plus,
            modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.CREATE_ROOM),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Steps(library: CollectivesLibrary, session: AccountSession) {
    val palette = PpfTheme.palette
    val step = library.step
    val title = when (step) {
        1 -> Res.string.collectifs_wizard_step_identity
        2 -> Res.string.collectifs_wizard_step_presentation
        3 -> Res.string.collectifs_wizard_step_charter
        else -> Res.string.collectifs_wizard_step_confirm
    }
    Text(
        stringResource(Res.string.collectifs_wizard_step, step, CollectivesLibrary.LAST_STEP),
        style = MaterialTheme.typography.labelLarge,
        color = palette.brand,
        modifier = Modifier.testTag(CollectivesTags.STEP),
    )
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    when (step) {
        1 -> StepIdentity(library)
        2 -> StepPresentation(library)
        3 -> StepInternalCharter(library)
        else -> StepConfirm(library, session)
    }
    if (step < CollectivesLibrary.LAST_STEP) {
        val problems = library.draft.problems(step)
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 1) {
                PpfSecondaryButton(onClick = library::previous, text = stringResource(Res.string.collectifs_wizard_previous), modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.PREVIOUS))
            }
            PpfPrimaryButton(
                onClick = library::next,
                text = stringResource(Res.string.collectifs_wizard_next),
                enabled = problems.isEmpty(),
                modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.NEXT),
            )
        }
        Missing(problems)
    }
}

/** What still blocks the step, in one sentence under the buttons. */
@Composable
private fun Missing(problems: List<WizardProblem>) {
    if (problems.isEmpty()) return
    val list = problems.map { stringResource(problemText(it)) }.joinToString(", ")
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(Res.string.collectifs_wizard_missing, list),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkMuted,
        modifier = Modifier.testTag(CollectivesTags.BLOCKED),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepIdentity(library: CollectivesLibrary) {
    val palette = PpfTheme.palette
    val draft = library.draft
    Field(
        label = stringResource(Res.string.collectifs_wizard_name),
        counter = stringResource(Res.string.collectifs_wizard_counter, draft.name.trim().length, Group.MAX_NAME_LENGTH),
        overLimit = draft.name.trim().length > Group.MAX_NAME_LENGTH,
    ) {
        PpfTextField(
            value = draft.name,
            onValueChange = { library.updateDraft(draft.copy(name = it)) },
            placeholder = stringResource(Res.string.collectifs_wizard_name_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.NAME),
            singleLine = true,
        )
    }
    Field(label = stringResource(Res.string.collectifs_wizard_territory), help = stringResource(Res.string.collectifs_wizard_territory_help)) {
        PpfTextField(
            value = draft.territory,
            onValueChange = {
                library.updateDraft(draft.copy(territory = it))
                library.suggestTerritories(it)
            },
            placeholder = stringResource(Res.string.collectifs_wizard_territory_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.TERRITORY),
            singleLine = true,
        )
        val choices = library.territoryChoices
        if (choices.isNotEmpty() && draft.territoryCode == null) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    PpfSelectableChip(
                        label = choice.label,
                        selected = false,
                        onClick = { library.pickTerritory(choice) },
                        leadingIcon = Lucide.MapPin,
                        modifier = Modifier.testTag(CollectivesTags.TERRITORY_CHOICE + choice.code),
                    )
                }
            }
        }
        draft.territoryCode?.let { code ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.collectifs_wizard_territory_code, code),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.weight(1f).testTag(CollectivesTags.TERRITORY_CODE),
                )
                PpfTextButton(onClick = library::clearTerritoryCode, text = stringResource(Res.string.collectifs_wizard_territory_code_clear), modifier = Modifier.testTag(CollectivesTags.TERRITORY_CODE_CLEAR))
            }
        }
    }
    Field(label = stringResource(Res.string.collectifs_wizard_scope)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectiveScope.entries.forEach { scope ->
                PpfSelectableChip(
                    label = stringResource(scopeLabel(scope)),
                    selected = draft.scope == scope,
                    onClick = { library.updateDraft(draft.copy(scope = scope)) },
                    modifier = Modifier.testTag(CollectivesTags.SCOPE_CHOICE + scope.tagValue),
                )
            }
        }
    }
    if (draft.proposedId.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.collectifs_wizard_id, draft.proposedId), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPresentation(library: CollectivesLibrary) {
    val draft = library.draft
    Field(
        label = stringResource(Res.string.collectifs_wizard_about),
        counter = stringResource(Res.string.collectifs_wizard_counter, draft.about.trim().length, Group.MAX_ABOUT_LENGTH),
        overLimit = draft.about.trim().length > Group.MAX_ABOUT_LENGTH,
    ) {
        PpfTextField(
            value = draft.about,
            onValueChange = { library.updateDraft(draft.copy(about = it)) },
            placeholder = stringResource(Res.string.collectifs_wizard_about_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.ABOUT),
            minLines = 3,
        )
    }
    Field(label = stringResource(Res.string.collectifs_wizard_type)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectiveType.entries.forEach { type ->
                PpfSelectableChip(
                    label = stringResource(typeLabel(type)),
                    selected = draft.type == type,
                    onClick = { library.updateDraft(draft.copy(type = type)) },
                    modifier = Modifier.testTag(CollectivesTags.TYPE_CHOICE + type.tagValue),
                )
            }
        }
    }
    Field(label = stringResource(Res.string.collectifs_wizard_themes), help = stringResource(Res.string.collectifs_wizard_themes_help)) {
        PpfTextField(
            value = draft.themes,
            onValueChange = { library.updateDraft(draft.copy(themes = it)) },
            placeholder = stringResource(Res.string.collectifs_wizard_themes_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.THEMES),
            singleLine = true,
        )
        val themes = draft.themeList
        if (themes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                themes.forEach { PpfBadge("#$it") }
            }
        }
    }
    Field(label = stringResource(Res.string.collectifs_wizard_language)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LANGUAGES.forEach { (code, label) ->
                PpfSelectableChip(
                    label = stringResource(label),
                    selected = draft.language == code,
                    onClick = { library.updateDraft(draft.copy(language = code)) },
                    modifier = Modifier.testTag(CollectivesTags.LANGUAGE + code),
                )
            }
        }
    }
}

@Composable
private fun StepInternalCharter(library: CollectivesLibrary) {
    val draft = library.draft
    Field(
        label = stringResource(Res.string.collectifs_wizard_internal_charter),
        help = stringResource(Res.string.collectifs_wizard_internal_charter_help),
        counter = stringResource(Res.string.collectifs_wizard_counter, draft.internalCharter.trim().length, CollectiveFormat.MAX_INTERNAL_CHARTER_LENGTH),
        overLimit = draft.internalCharter.trim().length > CollectiveFormat.MAX_INTERNAL_CHARTER_LENGTH,
    ) {
        PpfTextField(
            value = draft.internalCharter,
            onValueChange = { library.updateDraft(draft.copy(internalCharter = it)) },
            placeholder = stringResource(Res.string.collectifs_wizard_internal_charter_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(CollectivesTags.INTERNAL_CHARTER),
            minLines = 5,
        )
    }
}

/** The summary, the visibility, the charter of PPF, the warning, then « Annuler » and « Créer le collectif ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepConfirm(library: CollectivesLibrary, session: AccountSession) {
    val palette = PpfTheme.palette
    val groups = library.groups
    val draft = library.draft
    val scope = rememberCoroutineScope()
    val held = library.held
    if (held != null) {
        // Not public yet: the team checks the name first (D-132), as for any room.
        HeldView(held, onClose = library::cancelCreation)
        return
    }

    Spacer(Modifier.height(16.dp))
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.collectifs_wizard_summary_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(draft.name.trim(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            draft.type?.let { PpfBadge(stringResource(typeLabel(it))) }
            draft.scope?.let { PpfBadge(stringResource(scopeLabel(it))) }
            PpfBadge(draft.territory.trim())
            PpfBadge(languageLabel(draft.language))
        }
        if (draft.themeList.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(draft.themeList.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = palette.brand)
        }
        draft.about.trim().takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 4)
        }
    }

    // Step 1 of M24: public and open only; the rest is said, not offered (D-087).
    Spacer(Modifier.height(20.dp))
    Text(stringResource(Res.string.collectifs_wizard_visibility_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(stringResource(Res.string.collectifs_wizard_visibility_public), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.testTag(CollectivesTags.SOON)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Res.string.collectifs_wizard_soon_private,
                Res.string.collectifs_wizard_soon_secret,
                Res.string.collectifs_wizard_soon_request,
                Res.string.collectifs_wizard_soon_invitation,
            ).forEach { PpfSelectableChip(label = stringResource(it), selected = false, onClick = {}, enabled = false, leadingIcon = Lucide.Lock) }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.collectifs_wizard_soon_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }

    Spacer(Modifier.height(20.dp))
    Charter(draft.charterAccepted) { library.updateDraft(draft.copy(charterAccepted = !draft.charterAccepted)) }

    // Founding publishes the founder's key next to a collectif (GDPR, article 9): said before the button.
    Spacer(Modifier.height(16.dp))
    Callout(CalloutType.ATTENTION, Modifier.testTag(CollectivesTags.WARNING), title = stringResource(Res.string.collectifs_wizard_warning_title)) {
        Text(stringResource(Res.string.collectifs_wizard_warning_text), style = MaterialTheme.typography.bodyMedium)
    }
    if (!library.hasRemote) {
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION) { Text(stringResource(Res.string.collectifs_wizard_no_server), style = MaterialTheme.typography.bodyMedium) }
    }

    if (library.submitted) {
        Spacer(Modifier.height(16.dp))
        // The answers of the server, as the rooms' form shows them (duplicate, close names, too many, offline...).
        CreationErrorView(groups, session, launch = { block -> scope.launch { block() } })
        if (groups.creationError is CreationError.Refused) {
            Text(stringResource(Res.string.collectifs_wizard_refused), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(CollectivesTags.REFUSED))
            Spacer(Modifier.height(12.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    // A compromised account no longer publishes (S-146): the button is greyed out and says why.
    PublishBlockedNotice()
    PpfTextButton(onClick = library::previous, text = stringResource(Res.string.collectifs_wizard_previous), modifier = Modifier.testTag(CollectivesTags.PREVIOUS))
    val signer = session.signer
    val ready = LocalCanPublish.current && draft.complete && library.hasRemote && !groups.creationBusy && signer != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PpfSecondaryButton(onClick = library::cancelCreation, text = stringResource(Res.string.collectifs_wizard_cancel), modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.CANCEL))
        PpfPrimaryButton(
            onClick = { if (signer != null) scope.launch { library.create(signer) } },
            text = stringResource(if (groups.creationBusy) Res.string.collectifs_wizard_submitting else Res.string.collectifs_wizard_submit),
            enabled = ready,
            icon = Lucide.Users,
            modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.SUBMIT),
        )
    }
    Missing(draft.problems(CollectivesLibrary.LAST_STEP))
}

/** The charter of values of PPF: what is allowed, what is forbidden, and the box to tick (unticked by default). */
@Composable
private fun Charter(accepted: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Text(stringResource(Res.string.collectifs_wizard_charter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.collectifs_wizard_charter_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
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
            .testTag(CollectivesTags.CHARTER),
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
        Text(stringResource(Res.string.collectifs_wizard_charter_accept), style = MaterialTheme.typography.bodyLarge)
    }
}

/** A labelled block of the form: bold label, optional help line and counter, then the control. */
@Composable
private fun Field(
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

private fun problemText(problem: WizardProblem): StringResource = when (problem) {
    WizardProblem.NAME_MISSING -> Res.string.collectifs_problem_name_missing
    WizardProblem.NAME_TOO_LONG -> Res.string.collectifs_problem_name_too_long
    WizardProblem.TERRITORY_MISSING -> Res.string.collectifs_problem_territory_missing
    WizardProblem.TERRITORY_TOO_LONG -> Res.string.collectifs_problem_territory_too_long
    WizardProblem.SCOPE_MISSING -> Res.string.collectifs_problem_scope_missing
    WizardProblem.ABOUT_TOO_LONG -> Res.string.collectifs_problem_about_too_long
    WizardProblem.TYPE_MISSING -> Res.string.collectifs_problem_type_missing
    WizardProblem.TOO_MANY_THEMES -> Res.string.collectifs_problem_too_many_themes
    WizardProblem.THEME_TOO_LONG -> Res.string.collectifs_problem_theme_too_long
    WizardProblem.INTERNAL_CHARTER_TOO_LONG -> Res.string.collectifs_problem_internal_charter_too_long
    WizardProblem.CHARTER_NOT_ACCEPTED -> Res.string.collectifs_problem_charter_not_accepted
}
