package org.politisationpopulairefrancaise.ui.agir.agenda

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.CloseMobilization
import org.politisationpopulairefrancaise.core.agir.agenda.DeclarationState
import org.politisationpopulairefrancaise.core.agir.agenda.DraftField
import org.politisationpopulairefrancaise.core.agir.agenda.DraftProblem
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_blocked_charter
import org.politisationpopulairefrancaise.ui.resources.agenda_blocked_fix
import org.politisationpopulairefrancaise.ui.resources.agenda_charter_accept
import org.politisationpopulairefrancaise.ui.resources.agenda_counter
import org.politisationpopulairefrancaise.ui.resources.agenda_create_account
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_en_cours
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_faite
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_non_faite
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_public_note
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_unset
import org.politisationpopulairefrancaise.ui.resources.agenda_field_declaration
import org.politisationpopulairefrancaise.ui.resources.agenda_field_declaration_help
import org.politisationpopulairefrancaise.ui.resources.agenda_field_date_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_description
import org.politisationpopulairefrancaise.ui.resources.agenda_field_description_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_end
import org.politisationpopulairefrancaise.ui.resources.agenda_field_end_date_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_end_time_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_link
import org.politisationpopulairefrancaise.ui.resources.agenda_field_link_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_location
import org.politisationpopulairefrancaise.ui.resources.agenda_field_location_help
import org.politisationpopulairefrancaise.ui.resources.agenda_field_location_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_start
import org.politisationpopulairefrancaise.ui.resources.agenda_field_summary
import org.politisationpopulairefrancaise.ui.resources.agenda_field_summary_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_territory
import org.politisationpopulairefrancaise.ui.resources.agenda_field_territory_help
import org.politisationpopulairefrancaise.ui.resources.agenda_field_territory_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_time_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_title
import org.politisationpopulairefrancaise.ui.resources.agenda_field_title_placeholder
import org.politisationpopulairefrancaise.ui.resources.agenda_field_type
import org.politisationpopulairefrancaise.ui.resources.agenda_form_close
import org.politisationpopulairefrancaise.ui.resources.agenda_form_intro
import org.politisationpopulairefrancaise.ui.resources.agenda_form_title
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_date
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_list
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_location
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_territory
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_time
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_title
import org.politisationpopulairefrancaise.ui.resources.agenda_missing_type
import org.politisationpopulairefrancaise.ui.resources.agenda_need_account
import org.politisationpopulairefrancaise.ui.resources.agenda_no_server
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_description_too_long
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_end_before_start
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_end_date_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_end_time_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_end_time_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_link_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_location_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_location_too_long
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_start_date_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_start_date_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_start_in_past
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_start_time_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_start_time_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_summary_too_long
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_territory_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_territory_too_long
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_title_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_title_too_long
import org.politisationpopulairefrancaise.ui.resources.agenda_problem_type_missing
import org.politisationpopulairefrancaise.ui.resources.agenda_pseudonym_note
import org.politisationpopulairefrancaise.ui.resources.agenda_result_again
import org.politisationpopulairefrancaise.ui.resources.agenda_result_back
import org.politisationpopulairefrancaise.ui.resources.agenda_result_correct
import org.politisationpopulairefrancaise.ui.resources.agenda_result_failed_text
import org.politisationpopulairefrancaise.ui.resources.agenda_result_failed_title
import org.politisationpopulairefrancaise.ui.resources.agenda_result_published_text
import org.politisationpopulairefrancaise.ui.resources.agenda_result_published_title
import org.politisationpopulairefrancaise.ui.resources.agenda_result_refused_text
import org.politisationpopulairefrancaise.ui.resources.agenda_result_refused_title
import org.politisationpopulairefrancaise.ui.resources.agenda_result_review_text
import org.politisationpopulairefrancaise.ui.resources.agenda_result_review_title
import org.politisationpopulairefrancaise.ui.resources.agenda_result_waiting_text
import org.politisationpopulairefrancaise.ui.resources.agenda_result_waiting_title
import org.politisationpopulairefrancaise.ui.resources.agenda_send
import org.politisationpopulairefrancaise.ui.resources.agenda_sending
import org.politisationpopulairefrancaise.ui.resources.agenda_similar_close_place
import org.politisationpopulairefrancaise.ui.resources.agenda_similar_close_title
import org.politisationpopulairefrancaise.ui.resources.agenda_similar_open
import org.politisationpopulairefrancaise.ui.resources.agenda_similar_text
import org.politisationpopulairefrancaise.ui.resources.agenda_similar_title
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_title
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the "Proposer un événement" form. */
object ProposeTags {
    const val FORM = "agenda-form"
    const val CLOSE = "agenda-form-close"
    const val TYPE = "agenda-form-type-" // followed by the type tag
    const val TITLE = "agenda-form-title"
    const val START_DATE = "agenda-form-start-date"
    const val START_TIME = "agenda-form-start-time"
    const val END_DATE = "agenda-form-end-date"
    const val END_TIME = "agenda-form-end-time"
    const val LOCATION = "agenda-form-location"
    const val TERRITORY = "agenda-form-territory"
    const val SUMMARY = "agenda-form-summary"
    const val DESCRIPTION = "agenda-form-description"
    const val LINK = "agenda-form-link"
    const val DECLARATION = "agenda-form-declaration-" // followed by unset or the declaration tag
    const val DECLARATION_PUBLIC = "agenda-form-declaration-public"
    const val PROBLEM = "agenda-form-problem-" // followed by the problem in lowercase
    const val SIMILAR = "agenda-form-similar"
    const val SIMILAR_ITEM = "agenda-form-similar-" // followed by the identifier
    const val SIMILAR_OPEN = "agenda-form-similar-open-" // followed by the identifier
    const val CHARTER = "agenda-form-charter"
    const val SEND = "agenda-form-send"
    const val MISSING = "agenda-form-missing"
    const val BLOCKED = "agenda-form-blocked"
    const val NO_SERVER = "agenda-form-no-server"
    const val CREATE_ACCOUNT = "agenda-form-create-account"
    const val RESULT = "agenda-form-result"
    const val RESULT_BACK = "agenda-form-result-back"
    const val RESULT_AGAIN = "agenda-form-result-again"
}

/**
 * "Proposer un événement" (AG-02): type, title, start and end, place, territory, summary,
 * description, link and, for a demonstration, its declaration (declared, in progress, or not declared,
 * a public mention the form says so: D-044); then the events that look like it
 * the same day (AG-21), the charter (unchecked by default), the warning that the pseudonym is tied
 * to the event, and the sending through the single publication port. Wrong values are explained
 * under their field as they are typed; what is still missing is listed under the button. Writing
 * needs no account; sending does (the event is signed by its author).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProposeMobilizationScreen(
    agenda: AgendaLibrary,
    signer: EventSigner?,
    windowClass: WindowClass,
    names: AgendaNames,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val composer = agenda.composer
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val draft = composer.draft
    val problems = remember(draft, agenda.now) { composer.problems() }
    val known = agenda.known
    val similar = remember(draft, known) { composer.duplicates(known) }
    val scope = rememberCoroutineScope()
    val padding = if (compact) 16.dp else 24.dp

    Box(modifier.fillMaxSize().background(palette.surface1).testTag(ProposeTags.FORM)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                val outcome = composer.outcome
                if (outcome != null) {
                    ResultCard(
                        outcome = outcome,
                        onBack = {
                            val toMine = outcome == MobilizationSendOutcome.SENT_FOR_REVIEW || outcome == MobilizationSendOutcome.WAITING_FOR_SERVER
                            composer.reset()
                            composer.open = false
                            agenda.tab = if (toMine) AgendaTab.MINE else AgendaTab.UPCOMING
                        },
                        onAgain = composer::reset,
                        onCorrect = composer::dismissOutcome,
                    )
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.agenda_form_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    PpfIconButton(Lucide.X, stringResource(Res.string.agenda_form_close), { composer.open = false }, Modifier.testTag(ProposeTags.CLOSE), tint = palette.ink)
                }
                Spacer(Modifier.height(12.dp))
                InfoBox(Lucide.Calendar) { Text(stringResource(Res.string.agenda_form_intro), style = MaterialTheme.typography.bodyMedium) }

                Field(stringResource(Res.string.agenda_field_type)) {
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MobilizationType.entries.forEach { type ->
                            PpfSelectableChip(
                                label = typeLabel(type),
                                selected = draft.type == type,
                                onClick = { composer.draft = draft.copy(type = if (draft.type == type) null else type) },
                                leadingIcon = if (draft.type == type) null else typeIcon(type),
                                modifier = Modifier.testTag(ProposeTags.TYPE + type.tagValue),
                            )
                        }
                    }
                }

                Field(
                    label = stringResource(Res.string.agenda_field_title),
                    counter = stringResource(Res.string.agenda_counter, draft.title.trim().length, AgirFormats.MAX_TITLE),
                ) {
                    TextInput(draft.title, { composer.draft = draft.copy(title = it) }, stringResource(Res.string.agenda_field_title_placeholder), ProposeTags.TITLE, problems, DraftField.TITLE, minLines = 2)
                }

                Field(stringResource(Res.string.agenda_field_start)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1.4f)) {
                            TextInput(draft.startDate, { composer.draft = draft.copy(startDate = it.take(10)) }, stringResource(Res.string.agenda_field_date_placeholder), ProposeTags.START_DATE, problems, DraftField.START_DATE, singleLine = true)
                        }
                        Column(Modifier.weight(1f)) {
                            TextInput(draft.startTime, { composer.draft = draft.copy(startTime = it.take(8)) }, stringResource(Res.string.agenda_field_time_placeholder), ProposeTags.START_TIME, problems, DraftField.START_TIME, singleLine = true)
                        }
                    }
                }

                Field(stringResource(Res.string.agenda_field_end)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1.4f)) {
                            TextInput(draft.endDate, { composer.draft = draft.copy(endDate = it.take(10)) }, stringResource(Res.string.agenda_field_end_date_placeholder), ProposeTags.END_DATE, problems, DraftField.END_DATE, singleLine = true)
                        }
                        Column(Modifier.weight(1f)) {
                            TextInput(draft.endTime, { composer.draft = draft.copy(endTime = it.take(8)) }, stringResource(Res.string.agenda_field_end_time_placeholder), ProposeTags.END_TIME, problems, DraftField.END_TIME, singleLine = true)
                        }
                    }
                }

                Field(stringResource(Res.string.agenda_field_location), help = stringResource(Res.string.agenda_field_location_help)) {
                    TextInput(draft.location, { composer.draft = draft.copy(location = it) }, stringResource(Res.string.agenda_field_location_placeholder), ProposeTags.LOCATION, problems, DraftField.LOCATION)
                }
                Field(stringResource(Res.string.agenda_field_territory), help = stringResource(Res.string.agenda_field_territory_help)) {
                    TextInput(draft.territory, { composer.draft = draft.copy(territory = it) }, stringResource(Res.string.agenda_field_territory_placeholder), ProposeTags.TERRITORY, problems, DraftField.TERRITORY, singleLine = true)
                }

                if (similar.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SimilarEvents(similar, onOpen = { m ->
                        composer.open = false
                        agenda.open(m)
                    })
                }

                Field(stringResource(Res.string.agenda_field_summary)) {
                    TextInput(draft.summary, { composer.draft = draft.copy(summary = it) }, stringResource(Res.string.agenda_field_summary_placeholder), ProposeTags.SUMMARY, problems, DraftField.SUMMARY)
                }
                Field(stringResource(Res.string.agenda_field_description)) {
                    TextInput(draft.description, { composer.draft = draft.copy(description = it) }, stringResource(Res.string.agenda_field_description_placeholder), ProposeTags.DESCRIPTION, problems, DraftField.DESCRIPTION, minLines = 4)
                }
                Field(stringResource(Res.string.agenda_field_link)) {
                    TextInput(draft.link, { composer.draft = draft.copy(link = it.trim().take(Mobilization.MAX_LINK)) }, stringResource(Res.string.agenda_field_link_placeholder), ProposeTags.LINK, problems, DraftField.LINK, singleLine = true)
                }
                if (draft.type == MobilizationType.MANIFESTATION) {
                    Field(stringResource(Res.string.agenda_field_declaration), help = stringResource(Res.string.agenda_field_declaration_help)) {
                        // Decision of the owner (D-044): an undeclared demonstration is shown as such; the author is
                        // told that the mention is public, without being prevented from choosing it.
                        Column(Modifier.selectableGroup()) {
                            listOf<Pair<DeclarationState?, StringResource>>(
                                null to Res.string.agenda_declaration_unset,
                                DeclarationState.FAITE to Res.string.agenda_declaration_faite,
                                DeclarationState.EN_COURS to Res.string.agenda_declaration_en_cours,
                                DeclarationState.NON_FAITE to Res.string.agenda_declaration_non_faite,
                            ).forEach { (state, label) ->
                                PpfRadioRow(
                                    label = stringResource(label),
                                    selected = draft.declaration == state,
                                    onClick = { composer.draft = draft.copy(declaration = state) },
                                    modifier = Modifier.testTag(ProposeTags.DECLARATION + (state?.tagValue ?: "unset")),
                                )
                            }
                        }
                        if (draft.declaration == DeclarationState.NON_FAITE) {
                            Spacer(Modifier.height(8.dp))
                            InfoBox(Lucide.Eye, Modifier.testTag(ProposeTags.DECLARATION_PUBLIC)) {
                                Text(stringResource(Res.string.agenda_declaration_public_note), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Charter(composer.charterAccepted, onToggle = { composer.charterAccepted = !composer.charterAccepted })

                Spacer(Modifier.height(16.dp))
                InfoBox(Lucide.User) { Text(stringResource(Res.string.agenda_pseudonym_note), style = MaterialTheme.typography.bodyMedium) }
                if (!composer.hasServer || agenda.status == LibraryStatus.Unreachable) {
                    Spacer(Modifier.height(8.dp))
                    InfoBox(Lucide.Clock, Modifier.testTag(ProposeTags.NO_SERVER)) { Text(stringResource(Res.string.agenda_no_server), style = MaterialTheme.typography.bodyMedium) }
                }

                Spacer(Modifier.height(20.dp))
                if (signer == null) {
                    InfoBox(Lucide.KeyRound) {
                        Text(stringResource(Res.string.agenda_need_account), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        PpfPrimaryButton(onCreateAccount, stringResource(Res.string.agenda_create_account), Modifier.testTag(ProposeTags.CREATE_ACCOUNT))
                    }
                } else {
                    PpfPrimaryButton(
                        onClick = {
                            scope.launch {
                                composer.send(signer)
                                agenda.onSent()
                            }
                        },
                        text = stringResource(if (composer.sending) Res.string.agenda_sending else Res.string.agenda_send),
                        enabled = composer.ready(problems),
                        icon = Lucide.Send,
                        modifier = Modifier.testTag(ProposeTags.SEND),
                    )
                }
                Blockers(problems, composer.charterAccepted, started = !draft.isBlank)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** A labelled block of the form: bold label, optional help line and counter, then the control. */
@Composable
private fun Field(label: String, help: String? = null, counter: String? = null, content: @Composable () -> Unit) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        if (counter != null) Text(counter, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    if (help != null) {
        Spacer(Modifier.height(4.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    content()
}

/**
 * A text field and, under it, what is wrong with its value. Missing values are not shown here
 * (the list under the button says them): only the values that cannot be read or are too long.
 */
@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    problems: List<DraftProblem>,
    field: DraftField,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    val problem = problems.firstOrNull { it.field == field && !it.missing }
    val message = problem?.let { stringResource(problemText(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PpfTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().testTag(tag).then(if (message != null) Modifier.semantics { error(message) } else Modifier),
            minLines = minLines,
            singleLine = singleLine,
        )
        if (problem != null && message != null) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { }.testTag(ProposeTags.PROBLEM + problem.name.lowercase())) {
                Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = PpfTheme.palette.accentInk, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.accentInk)
            }
        }
    }
}

private fun problemText(problem: DraftProblem): StringResource = when (problem) {
    DraftProblem.TYPE_MISSING -> Res.string.agenda_problem_type_missing
    DraftProblem.TITLE_MISSING -> Res.string.agenda_problem_title_missing
    DraftProblem.TITLE_TOO_LONG -> Res.string.agenda_problem_title_too_long
    DraftProblem.START_DATE_MISSING -> Res.string.agenda_problem_start_date_missing
    DraftProblem.START_DATE_INVALID -> Res.string.agenda_problem_start_date_invalid
    DraftProblem.START_TIME_MISSING -> Res.string.agenda_problem_start_time_missing
    DraftProblem.START_TIME_INVALID -> Res.string.agenda_problem_start_time_invalid
    DraftProblem.START_IN_PAST -> Res.string.agenda_problem_start_in_past
    DraftProblem.END_DATE_INVALID -> Res.string.agenda_problem_end_date_invalid
    DraftProblem.END_TIME_MISSING -> Res.string.agenda_problem_end_time_missing
    DraftProblem.END_TIME_INVALID -> Res.string.agenda_problem_end_time_invalid
    DraftProblem.END_BEFORE_START -> Res.string.agenda_problem_end_before_start
    DraftProblem.LOCATION_MISSING -> Res.string.agenda_problem_location_missing
    DraftProblem.LOCATION_TOO_LONG -> Res.string.agenda_problem_location_too_long
    DraftProblem.TERRITORY_MISSING -> Res.string.agenda_problem_territory_missing
    DraftProblem.TERRITORY_TOO_LONG -> Res.string.agenda_problem_territory_too_long
    DraftProblem.SUMMARY_TOO_LONG -> Res.string.agenda_problem_summary_too_long
    DraftProblem.DESCRIPTION_TOO_LONG -> Res.string.agenda_problem_description_too_long
    DraftProblem.LINK_INVALID -> Res.string.agenda_problem_link_invalid
}

private fun missingText(problem: DraftProblem): StringResource? = when (problem) {
    DraftProblem.TYPE_MISSING -> Res.string.agenda_missing_type
    DraftProblem.TITLE_MISSING -> Res.string.agenda_missing_title
    DraftProblem.START_DATE_MISSING -> Res.string.agenda_missing_date
    DraftProblem.START_TIME_MISSING -> Res.string.agenda_missing_time
    DraftProblem.LOCATION_MISSING -> Res.string.agenda_missing_location
    DraftProblem.TERRITORY_MISSING -> Res.string.agenda_missing_territory
    else -> null
}

/** Under the button: what is still missing, what must be corrected, or the charter. */
@Composable
private fun Blockers(problems: List<DraftProblem>, charterAccepted: Boolean, started: Boolean) {
    val palette = PpfTheme.palette
    val missing = problems.mapNotNull(::missingText).map { stringResource(it) }
    val wrong = problems.any { !it.missing }
    if (missing.isNotEmpty() && started) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.agenda_missing_list, missing.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(ProposeTags.MISSING))
    }
    val blocked = when {
        wrong -> Res.string.agenda_blocked_fix
        problems.isEmpty() && !charterAccepted -> Res.string.agenda_blocked_charter
        else -> null
    }
    if (blocked != null) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(blocked), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(ProposeTags.BLOCKED))
    }
}

/** AG-21: the events of the same day that look like this one, with the way to read them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimilarEvents(similar: List<CloseMobilization>, onOpen: (Mobilization) -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(ProposeTags.SIMILAR)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Copy, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.agenda_similar_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.agenda_similar_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        similar.forEach { close ->
            val m = close.mobilization
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag(ProposeTags.SIMILAR_ITEM + m.identifier)) {
                Text(m.displayTitle, style = MaterialTheme.typography.titleSmall)
                MetaLine(Lucide.Clock, whenText(m, full = true))
                MetaLine(Lucide.MapPin, "${m.location} · ${m.territory}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (close.closeTitle) Text(stringResource(Res.string.agenda_similar_close_title), style = MaterialTheme.typography.labelMedium, color = palette.brand, modifier = Modifier.align(Alignment.CenterVertically))
                    if (close.closePlace) Text(stringResource(Res.string.agenda_similar_close_place), style = MaterialTheme.typography.labelMedium, color = palette.brand, modifier = Modifier.align(Alignment.CenterVertically))
                    PpfSecondaryButton(
                        onClick = { onOpen(m) },
                        text = stringResource(Res.string.agenda_similar_open, m.displayTitle.take(40)),
                        small = true,
                        modifier = Modifier.testTag(ProposeTags.SIMILAR_OPEN + m.identifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun Charter(accepted: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Text(stringResource(Res.string.propose_charter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
            .testTag(ProposeTags.CHARTER),
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
        Text(stringResource(Res.string.agenda_charter_accept), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(outcome: MobilizationSendOutcome, onBack: () -> Unit, onAgain: () -> Unit, onCorrect: () -> Unit) {
    val palette = PpfTheme.palette
    val (title, text) = when (outcome) {
        MobilizationSendOutcome.PUBLISHED -> Res.string.agenda_result_published_title to Res.string.agenda_result_published_text
        MobilizationSendOutcome.SENT_FOR_REVIEW -> Res.string.agenda_result_review_title to Res.string.agenda_result_review_text
        MobilizationSendOutcome.WAITING_FOR_SERVER -> Res.string.agenda_result_waiting_title to Res.string.agenda_result_waiting_text
        MobilizationSendOutcome.REFUSED -> Res.string.agenda_result_refused_title to Res.string.agenda_result_refused_text
        MobilizationSendOutcome.FAILED -> Res.string.agenda_result_failed_title to Res.string.agenda_result_failed_text
    }
    val success = outcome == MobilizationSendOutcome.PUBLISHED || outcome == MobilizationSendOutcome.SENT_FOR_REVIEW || outcome == MobilizationSendOutcome.WAITING_FOR_SERVER
    PpfCard(Modifier.fillMaxWidth().testTag(ProposeTags.RESULT + "-" + outcome.name.lowercase())) {
        Box(Modifier.size(48.dp).background(if (success) palette.successTint else palette.accentTint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (success) Lucide.CircleCheck else Lucide.TriangleExclamationPoint, contentDescription = null, tint = if (success) palette.success else palette.accentInk, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (success) {
                PpfPrimaryButton(onBack, stringResource(Res.string.agenda_result_back), Modifier.testTag(ProposeTags.RESULT_BACK))
                PpfSecondaryButton(onAgain, stringResource(Res.string.agenda_result_again), Modifier.testTag(ProposeTags.RESULT_AGAIN))
            } else {
                PpfPrimaryButton(onCorrect, stringResource(Res.string.agenda_result_correct), Modifier.testTag(ProposeTags.RESULT_AGAIN))
                PpfSecondaryButton(onBack, stringResource(Res.string.agenda_result_back), Modifier.testTag(ProposeTags.RESULT_BACK))
            }
        }
    }
}
