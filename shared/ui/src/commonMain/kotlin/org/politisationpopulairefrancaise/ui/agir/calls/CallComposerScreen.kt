package org.politisationpopulairefrancaise.ui.agir.calls

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallField
import org.politisationpopulairefrancaise.core.agir.calls.CallSlots
import org.politisationpopulairefrancaise.core.agir.calls.CallFieldKind
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallIssue
import org.politisationpopulairefrancaise.core.agir.calls.CallIssueKind
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.core.agir.calls.CallSimilarity
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.agir.collectives.CollectiveComposerNote
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object CallComposerTags {
    const val SCREEN = "call-composer"
    const val CLOSE = "call-composer-close"
    const val TYPE = "call-composer-type-" // followed by the type tag value
    const val CHANGE_TYPE = "call-composer-change-type"
    const val TIPS = "call-composer-tips"
    const val WITNESSES_RULES = "call-composer-witnesses"
    const val FUNDRAISING_RULES = "call-composer-fundraising"
    const val BLOCKADE_RULES = "call-composer-blockade"
    const val PLEDGE = "call-composer-pledge"
    const val FIELD = "call-composer-field-" // followed by "title", "text", "summary", "territory" or a tag
    const val DATE = "call-composer-date-" // followed by a tag
    const val TIME = "call-composer-time-" // followed by a tag
    const val CHOICE = "call-composer-choice-" // followed by tag:value
    const val FLAG = "call-composer-flag-" // followed by a tag
    const val FIELD_ERROR = "call-composer-error-" // followed by the field
    const val SIMILAR = "call-composer-similar"
    const val SIMILAR_OPEN = "call-composer-similar-" // followed by the slug
    const val ISSUES = "call-composer-issues"
    const val ISSUE = "call-composer-issue-" // followed by kind:field in lowercase
    const val CHARTER = "call-composer-charter"
    const val SEND = "call-composer-send"
    const val BLOCKED = "call-composer-blocked"
    const val NEED_ACCOUNT = "call-composer-need-account"
    const val CREATE_ACCOUNT = "call-composer-create-account"
    const val RESULT = "call-composer-result-" // followed by the outcome in lowercase
    const val RESULT_BACK = "call-composer-result-back"
    const val RESULT_AGAIN = "call-composer-result-again"
}

/** Findings shown under their field as soon as they appear; the others wait in the "Avant d'envoyer" list. */
private val INLINE_KINDS = setOf(
    CallIssueKind.TOO_LONG, CallIssueKind.BAD_NUMBER, CallIssueKind.BAD_DATE, CallIssueKind.PAST_DATE,
    CallIssueKind.END_BEFORE_START, CallIssueKind.BAD_LINK, CallIssueKind.BAD_CHOICE, CallIssueKind.PERSONAL_DATA,
)

/**
 * "Lancer un appel" (AP-01, AP-10): the type first, each with one sentence, then the common fields
 * (title, text, summary, territory, goal, deadline, place, link), then the fields of the type, with
 * a guided template (tips and an example of title); the calls close to the draft before sending
 * (AP-02); the charter; the result of the sending (`201` published, `202` held for review).
 * Writing needs no account; sending does (a call is signed by its author). The fundraising type
 * is offered to verified organisations only (AP-08).
 */
@Composable
internal fun CallComposerScreen(
    composer: CallComposer,
    calls: List<Call>,
    signer: EventSigner?,
    canFundraise: Boolean,
    windowClass: WindowClass,
    onClose: () -> Unit,
    onOpenCall: (Call) -> Unit,
    onCreateAccount: (() -> Unit)?,
    onSent: (SignedEvent?, CallSendOutcome) -> Unit,
) {
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val padding = if (compact) 16.dp else 24.dp
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(PpfTheme.palette.surface1).testTag(CallComposerTags.SCREEN)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                val outcome = composer.outcome
                if (outcome != null) {
                    ResultCard(
                        outcome = outcome,
                        onBack = {
                            composer.reset()
                            onClose()
                        },
                        onAgain = composer::reset,
                        onCorrect = composer::dismissOutcome,
                    )
                    return@Column
                }
                Header(onClose)
                // Opened from the page of a collectif (M24): in whose name the call is launched.
                CollectiveComposerNote(composer.collective)
                val type = composer.draft.type
                if (type == null) {
                    TypeChoice(canFundraise, composer::chooseType)
                } else {
                    Form(composer, type, calls, signer, canFundraise, onOpenCall, onCreateAccount) {
                        val author = signer ?: return@Form
                        scope.launch {
                            val result = composer.send(author, canFundraise)
                            onSent(composer.sent, result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.appels_compose_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        PpfIconButton(Lucide.X, stringResource(Res.string.appels_compose_close), onClose, Modifier.testTag(CallComposerTags.CLOSE), tint = PpfTheme.palette.ink)
    }
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.Megaphone) { Text(stringResource(Res.string.appels_compose_intro), style = MaterialTheme.typography.bodyMedium) }
}

/** The types, one sentence each; fundraising disabled, and said why, for other accounts. */
@Composable
private fun TypeChoice(canFundraise: Boolean, onChoose: (CallType) -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(stringResource(Res.string.appels_compose_type_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    PpfCard(Modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup()) {
            CallType.entries.forEach { type ->
                val allowed = !type.isFundraising || canFundraise
                val description = stringResource(typeDescriptionResource(type))
                PpfRadioRow(
                    label = stringResource(typeLabelResource(type)),
                    selected = false,
                    onClick = { onChoose(type) },
                    supporting = if (allowed) description else "$description ${stringResource(Res.string.appels_compose_fundraising_reserved)}",
                    enabled = allowed,
                    modifier = Modifier.testTag(CallComposerTags.TYPE + type.tagValue),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Form(
    composer: CallComposer,
    type: CallType,
    calls: List<Call>,
    signer: EventSigner?,
    canFundraise: Boolean,
    onOpenCall: (Call) -> Unit,
    onCreateAccount: (() -> Unit)?,
    onSend: () -> Unit,
) {
    val palette = PpfTheme.palette
    val draft = composer.draft
    val issues = composer.issues(canFundraise)
    val similar = remember(draft.title, draft.summary, draft.territory, draft.type, calls) { CallSimilarity.find(draft, calls) }

    Spacer(Modifier.height(20.dp))
    // The chosen type, its guided template, and its rules.
    PpfCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(typeIcon(type), contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(typeLabelResource(type)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(typeDescriptionResource(type)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
        }
        Spacer(Modifier.height(4.dp))
        PpfTextButton(composer::changeType, stringResource(Res.string.appels_compose_change_type), Modifier.testTag(CallComposerTags.CHANGE_TYPE))
    }
    Spacer(Modifier.height(12.dp))
    val (tips, example) = typeTipsResource(type)
    Callout(CalloutType.CONSEIL, title = stringResource(Res.string.appels_compose_tips), modifier = Modifier.testTag(CallComposerTags.TIPS)) {
        Text(stringResource(tips), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.appels_compose_example, stringResource(example)), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
    if (type.isWitnesses) {
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION, modifier = Modifier.testTag(CallComposerTags.WITNESSES_RULES)) {
            Text(stringResource(Res.string.appels_witnesses_rules), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (type.isBlockade) {
        // D-199: always shown, never dismissed; the call is always read by the team before publication.
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION, title = stringResource(Res.string.appels_blockade_title), modifier = Modifier.testTag(CallComposerTags.BLOCKADE_RULES)) {
            Text(stringResource(Res.string.appels_blockade_rules), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.appels_blockade_law), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (type.isFundraising) {
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION, modifier = Modifier.testTag(CallComposerTags.FUNDRAISING_RULES)) {
            Text(stringResource(Res.string.appels_fundraising_rules), style = MaterialTheme.typography.bodyMedium)
        }
    }

    // The common part.
    Spacer(Modifier.height(20.dp))
    Text(stringResource(Res.string.appels_compose_common), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    TextInput(CallDraft.FIELD_TITLE, type, issues, draft.title, { composer.draft = draft.copy(title = it) },
        placeholder = stringResource(Res.string.appels_field_title_placeholder), help = stringResource(Res.string.appels_field_title_help), max = CallRules.TITLE_MAX)
    if (similar.isNotEmpty()) SimilarCalls(similar.map { it.call to it.likelyDuplicate }, onOpenCall)
    TextInput(CallDraft.FIELD_TEXT, type, issues, draft.text, { composer.draft = draft.copy(text = it) },
        placeholder = stringResource(Res.string.appels_field_text_placeholder), help = stringResource(Res.string.appels_field_text_help), max = CallRules.TEXT_MAX, minLines = 5)
    TextInput(CallDraft.FIELD_SUMMARY, type, issues, draft.summary, { composer.draft = draft.copy(summary = it) },
        placeholder = stringResource(Res.string.appels_field_summary_placeholder), state = stringResource(Res.string.appels_optional), max = CallRules.SUMMARY_MAX)
    TextInput(CallDraft.FIELD_TERRITORY, type, issues, draft.territory, { composer.draft = draft.copy(territory = it) },
        placeholder = stringResource(Res.string.appels_field_territory_placeholder), help = stringResource(Res.string.appels_field_territory_help), singleLine = true)
    TextInput(AgirFormats.TAG_GOAL, type, issues, draft.goal, { composer.draft = draft.copy(goal = it.filter(Char::isDigit).take(9)) },
        placeholder = stringResource(Res.string.appels_field_goal_help), state = requiredState(type, AgirFormats.TAG_GOAL), singleLine = true)
    DateInput(AgirFormats.TAG_DEADLINE, type, issues, composer, help = stringResource(Res.string.appels_field_deadline_help))
    TextInput(AgirFormats.TAG_LOCATION, type, issues, draft.location, { composer.draft = draft.copy(location = it) },
        placeholder = stringResource(Res.string.appels_field_value_placeholder), state = requiredState(type, AgirFormats.TAG_LOCATION))
    TextInput(AgirFormats.TAG_LINK, type, issues, draft.link, { composer.draft = draft.copy(link = it.trim()) },
        placeholder = "https://", help = stringResource(Res.string.appels_field_link_help), state = requiredState(type, AgirFormats.TAG_LINK), singleLine = true)

    // The part proper to the type (AP-10).
    val specific = CallFields.specific(type)
    if (specific.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.appels_compose_specific), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        specific.forEach { field -> SpecificInput(field, type, issues, composer) }
    }
    if (type in CallSlots.TYPES) {
        Spacer(Modifier.height(24.dp))
        SlotsEditor(composer)
    }
    if (type.isFundraising) {
        Spacer(Modifier.height(16.dp))
        CheckRow(stringResource(Res.string.appels_fundraising_pledge), draft.notForCandidateOrParty, { composer.draft = draft.copy(notForCandidateOrParty = !draft.notForCandidateOrParty) }, CallComposerTags.PLEDGE)
    }

    Spacer(Modifier.height(20.dp))
    IssuesCard(issues, type)

    Spacer(Modifier.height(20.dp))
    Charter(composer.charterAccepted) { composer.charterAccepted = !composer.charterAccepted }

    Spacer(Modifier.height(20.dp))
    if (signer == null) {
        InfoBox(Lucide.KeyRound, Modifier.testTag(CallComposerTags.NEED_ACCOUNT)) {
            Text(stringResource(Res.string.appels_need_account), style = MaterialTheme.typography.bodyMedium)
            if (onCreateAccount != null) {
                Spacer(Modifier.height(8.dp))
                PpfPrimaryButton(onCreateAccount, stringResource(Res.string.appels_create_account), Modifier.testTag(CallComposerTags.CREATE_ACCOUNT))
            }
        }
    } else {
        // A compromised account no longer publishes (S-146): the button is greyed out and says why.
        PpfPrimaryButton(
            onClick = onSend,
            text = stringResource(if (composer.sending) Res.string.appels_sending else Res.string.appels_send),
            enabled = LocalCanPublish.current && composer.ready(issues),
            icon = Lucide.Send,
            modifier = Modifier.testTag(CallComposerTags.SEND),
        )
        PublishBlockedNotice(Modifier.padding(top = 8.dp))
        val reason = when {
            !CallRules.canSubmit(issues) -> Res.string.appels_blocked_issues
            !composer.charterAccepted -> Res.string.appels_blocked_charter
            else -> null
        }
        if (reason != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(CallComposerTags.BLOCKED))
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** "facultatif" or "obligatoire pour ce type", for a common field that some types require. */
@Composable
private fun requiredState(type: CallType, tag: String): String =
    stringResource(if (type.requires(tag)) Res.string.appels_required_for_type else Res.string.appels_optional)

/** A labelled block: bold label (with its state), help line and counter, then the control, then its errors. */
@Composable
private fun FieldBlock(
    field: String,
    type: CallType,
    issues: List<CallIssue>,
    state: String? = null,
    help: String? = null,
    counter: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = PpfTheme.palette
    val label = stringResource(fieldLabelResource(field, type))
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (state != null) stringResource(Res.string.appels_label_with_state, label, state) else label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (counter != null) Text(counter, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    if (help != null) {
        Spacer(Modifier.height(4.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    content()
    issues.filter { it.field == field && it.kind in INLINE_KINDS }.forEach { issue ->
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag(CallComposerTags.FIELD_ERROR + field)) {
            Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.accentInk, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(issueText(issue, type), style = MaterialTheme.typography.bodySmall, color = palette.accentInk)
        }
    }
}

@Composable
private fun TextInput(
    field: String,
    type: CallType,
    issues: List<CallIssue>,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    help: String? = null,
    state: String? = null,
    max: Int? = null,
    minLines: Int = 1,
    singleLine: Boolean = false,
) {
    FieldBlock(field, type, issues, state, help, counter = max?.let { stringResource(Res.string.appels_counter, value.trim().length, it) }) {
        PpfTextField(value, onChange, placeholder, Modifier.fillMaxWidth().testTag(CallComposerTags.FIELD + field), minLines = minLines, singleLine = singleLine)
    }
}

/** A date and an hour, typed (no gesture needed): "14/11/2026" and "18:30". */
@Composable
private fun DateInput(tag: String, type: CallType, issues: List<CallIssue>, composer: CallComposer, help: String? = null) {
    val draft = composer.draft
    val value = draft.date(tag)
    val label = stringResource(fieldLabelResource(tag, type))
    val state = if (tag == AgirFormats.TAG_DEADLINE) requiredState(type, tag) else null
    FieldBlock(tag, type, issues, state, help) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.appels_date_label), style = MaterialTheme.typography.labelMedium, color = PpfTheme.palette.inkMuted)
                val dateDescription = stringResource(Res.string.appels_date_field, label)
                PpfTextField(
                    value = value.date,
                    onValueChange = { composer.draft = composer.draft.withDate(tag, value.copy(date = it.take(10))) },
                    placeholder = stringResource(Res.string.appels_date_placeholder),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = dateDescription }.testTag(CallComposerTags.DATE + tag),
                    singleLine = true,
                )
            }
            Column(Modifier.width(120.dp)) {
                Text(stringResource(Res.string.appels_time_label), style = MaterialTheme.typography.labelMedium, color = PpfTheme.palette.inkMuted)
                val timeDescription = stringResource(Res.string.appels_time_field, label)
                PpfTextField(
                    value = value.time,
                    onValueChange = { composer.draft = composer.draft.withDate(tag, value.copy(time = it.take(7))) },
                    placeholder = stringResource(Res.string.appels_time_placeholder),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = timeDescription }.testTag(CallComposerTags.TIME + tag),
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecificInput(field: CallField, type: CallType, issues: List<CallIssue>, composer: CallComposer) {
    val draft = composer.draft
    val help = fieldHelpResource(field.tag)?.let { stringResource(it) }
    when (field.kind) {
        CallFieldKind.DATE_TIME -> DateInput(field.tag, type, issues, composer, help)
        CallFieldKind.TEXT, CallFieldKind.LONG_TEXT -> TextInput(
            field.tag, type, issues, draft.field(field.tag), { composer.draft = composer.draft.withField(field.tag, it) },
            placeholder = stringResource(Res.string.appels_field_value_placeholder),
            help = help,
            max = field.maxLength,
            minLines = if (field.kind == CallFieldKind.LONG_TEXT) 3 else 1,
        )
        CallFieldKind.NUMBER -> TextInput(
            field.tag, type, issues, draft.field(field.tag), { composer.draft = composer.draft.withField(field.tag, it.filter(Char::isDigit).take(9)) },
            placeholder = stringResource(Res.string.appels_field_value_placeholder),
            help = help,
            singleLine = true,
        )
        CallFieldKind.CHOICE -> FieldBlock(field.tag, type, issues, help = help) {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                field.choices.forEach { choice ->
                    ChoiceChip(
                        label = choiceLabelResource(field.tag, choice)?.let { stringResource(it) } ?: choice,
                        selected = draft.field(field.tag) == choice,
                        onClick = { composer.draft = composer.draft.withField(field.tag, choice) },
                        modifier = Modifier.testTag(CallComposerTags.CHOICE + field.tag + ":" + choice),
                    )
                }
            }
        }
        CallFieldKind.FLAG -> {
            Spacer(Modifier.height(12.dp))
            val checked = draft.field(field.tag) == CallFields.YES
            CheckRow(
                label = stringResource(fieldLabelResource(field.tag, type)),
                checked = checked,
                onToggle = { composer.draft = composer.draft.withField(field.tag, if (checked) "" else CallFields.YES) },
                tag = CallComposerTags.FLAG + field.tag,
            )
        }
    }
}

/** The calls that seem to say the same thing (AP-02): shown, never blocking. */
@Composable
private fun SimilarCalls(similar: List<Pair<Call, Boolean>>, onOpenCall: (Call) -> Unit) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    PpfCard(Modifier.fillMaxWidth().testTag(CallComposerTags.SIMILAR)) {
        Text(stringResource(Res.string.appels_similar_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.appels_similar_help), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        similar.forEach { (call, duplicate) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeBadge(call.type)
                PpfBadge(stringResource(if (duplicate) Res.string.appels_similar_duplicate else Res.string.appels_similar_close), if (duplicate) BadgeStyle.EXAMPLE else BadgeStyle.NEUTRAL)
            }
            Spacer(Modifier.height(4.dp))
            Text(call.displayTitle, style = MaterialTheme.typography.bodyLarge)
            Text(call.territory, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            PpfTextButton({ onOpenCall(call) }, stringResource(Res.string.appels_similar_open, call.displayTitle), Modifier.testTag(CallComposerTags.SIMILAR_OPEN + call.slug))
        }
    }
}

/** What still blocks the sending, in words. */
@Composable
private fun IssuesCard(issues: List<CallIssue>, type: CallType) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(CallComposerTags.ISSUES)) {
        Text(stringResource(Res.string.appels_issues_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (issues.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.appels_issues_none), style = MaterialTheme.typography.bodyMedium)
            }
        }
        issues.forEach { issue ->
            Row(Modifier.padding(vertical = 3.dp).testTag(CallComposerTags.ISSUE + "${issue.kind.name.lowercase()}:${issue.field}"), verticalAlignment = Alignment.Top) {
                Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.accentInk, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(issueText(issue, type), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Charter(accepted: Boolean, onToggle: () -> Unit) {
    Text(stringResource(Res.string.appels_charter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.LEGAL, title = stringResource(Res.string.appels_charter_allowed)) {
        Text(stringResource(Res.string.appels_charter_allowed_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.ILLEGAL, title = stringResource(Res.string.appels_charter_forbidden)) {
        Text(stringResource(Res.string.appels_charter_forbidden_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    CheckRow(stringResource(Res.string.appels_charter_accept), accepted, onToggle, CallComposerTags.CHARTER)
}

/** A checkbox and its label, 48 dp high, announced as a checkbox. */
@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit, tag: String) {
    val palette = PpfTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp)
                .background(if (checked) palette.brand else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .border(2.dp, if (checked) palette.brand else palette.borderStrong, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, tint = palette.onBrand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(outcome: CallSendOutcome, onBack: () -> Unit, onAgain: () -> Unit, onCorrect: () -> Unit) {
    val palette = PpfTheme.palette
    val (title, text) = when (outcome) {
        CallSendOutcome.PUBLISHED -> Res.string.appels_result_published_title to Res.string.appels_result_published_text
        CallSendOutcome.SENT_FOR_REVIEW -> Res.string.appels_result_review_title to Res.string.appels_result_review_text
        CallSendOutcome.WAITING_FOR_SERVER -> Res.string.appels_result_waiting_title to Res.string.appels_result_waiting_text
        CallSendOutcome.REFUSED -> Res.string.appels_result_refused_title to Res.string.appels_result_refused_text
        CallSendOutcome.FAILED -> Res.string.appels_result_failed_title to Res.string.appels_result_failed_text
    }
    val success = outcome == CallSendOutcome.PUBLISHED || outcome == CallSendOutcome.SENT_FOR_REVIEW || outcome == CallSendOutcome.WAITING_FOR_SERVER
    PpfCard(Modifier.fillMaxWidth().testTag(CallComposerTags.RESULT + outcome.name.lowercase())) {
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
                PpfPrimaryButton(onBack, stringResource(Res.string.appels_result_back), Modifier.testTag(CallComposerTags.RESULT_BACK))
                PpfSecondaryButton(onAgain, stringResource(Res.string.appels_result_again), Modifier.testTag(CallComposerTags.RESULT_AGAIN))
            } else {
                PpfPrimaryButton(onCorrect, stringResource(Res.string.appels_result_correct), Modifier.testTag(CallComposerTags.RESULT_AGAIN))
                PpfSecondaryButton(onBack, stringResource(Res.string.appels_result_back), Modifier.testTag(CallComposerTags.RESULT_BACK))
            }
        }
    }
}
