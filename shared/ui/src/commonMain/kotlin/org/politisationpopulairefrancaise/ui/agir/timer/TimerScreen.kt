package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.timer.ArmProblem
import org.politisationpopulairefrancaise.core.agir.timer.ArmRequest
import org.politisationpopulairefrancaise.core.agir.timer.ArmResult
import org.politisationpopulairefrancaise.core.agir.timer.CheckInResult
import org.politisationpopulairefrancaise.core.agir.timer.ReportResult
import org.politisationpopulairefrancaise.core.agir.timer.TimerCandidate
import org.politisationpopulairefrancaise.core.agir.timer.TimerCodeProblem
import org.politisationpopulairefrancaise.core.agir.timer.TimerCodeRules
import org.politisationpopulairefrancaise.core.agir.timer.TimerFormats
import org.politisationpopulairefrancaise.core.agir.timer.TimerService
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.agir.security.Announced
import org.politisationpopulairefrancaise.ui.agir.security.PinField
import org.politisationpopulairefrancaise.ui.agir.security.waitText
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.timer_armed_left
import org.politisationpopulairefrancaise.ui.resources.timer_armed_past
import org.politisationpopulairefrancaise.ui.resources.timer_armed_recipients
import org.politisationpopulairefrancaise.ui.resources.timer_armed_title
import org.politisationpopulairefrancaise.ui.resources.timer_armed_until
import org.politisationpopulairefrancaise.ui.resources.timer_armed_waiting
import org.politisationpopulairefrancaise.ui.resources.timer_armed_warning
import org.politisationpopulairefrancaise.ui.resources.timer_arming
import org.politisationpopulairefrancaise.ui.resources.timer_back
import org.politisationpopulairefrancaise.ui.resources.timer_cancel
import org.politisationpopulairefrancaise.ui.resources.timer_cancelled_text
import org.politisationpopulairefrancaise.ui.resources.timer_cancelled_title
import org.politisationpopulairefrancaise.ui.resources.timer_checking
import org.politisationpopulairefrancaise.ui.resources.timer_close
import org.politisationpopulairefrancaise.ui.resources.timer_code_checkin
import org.politisationpopulairefrancaise.ui.resources.timer_code_confirm
import org.politisationpopulairefrancaise.ui.resources.timer_code_duress
import org.politisationpopulairefrancaise.ui.resources.timer_code_duress_text
import org.politisationpopulairefrancaise.ui.resources.timer_code_label
import org.politisationpopulairefrancaise.ui.resources.timer_code_text_ok
import org.politisationpopulairefrancaise.ui.resources.timer_code_text_postpone
import org.politisationpopulairefrancaise.ui.resources.timer_code_title_ok
import org.politisationpopulairefrancaise.ui.resources.timer_code_title_postpone
import org.politisationpopulairefrancaise.ui.resources.timer_code_wrong
import org.politisationpopulairefrancaise.ui.resources.timer_codes_text
import org.politisationpopulairefrancaise.ui.resources.timer_codes_title
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_arm
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_forget
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_seized
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_title
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_when
import org.politisationpopulairefrancaise.ui.resources.timer_confirm_who
import org.politisationpopulairefrancaise.ui.resources.timer_continue
import org.politisationpopulairefrancaise.ui.resources.timer_deadline_invalid
import org.politisationpopulairefrancaise.ui.resources.timer_deadline_title
import org.politisationpopulairefrancaise.ui.resources.timer_done_text
import org.politisationpopulairefrancaise.ui.resources.timer_done_title
import org.politisationpopulairefrancaise.ui.resources.timer_done_waiting
import org.politisationpopulairefrancaise.ui.resources.timer_how_1
import org.politisationpopulairefrancaise.ui.resources.timer_how_2
import org.politisationpopulairefrancaise.ui.resources.timer_how_3
import org.politisationpopulairefrancaise.ui.resources.timer_how_4
import org.politisationpopulairefrancaise.ui.resources.timer_how_5
import org.politisationpopulairefrancaise.ui.resources.timer_how_title
import org.politisationpopulairefrancaise.ui.resources.timer_intro
import org.politisationpopulairefrancaise.ui.resources.timer_legal_none
import org.politisationpopulairefrancaise.ui.resources.timer_legal_text
import org.politisationpopulairefrancaise.ui.resources.timer_legal_title
import org.politisationpopulairefrancaise.ui.resources.timer_no_account
import org.politisationpopulairefrancaise.ui.resources.timer_no_server
import org.politisationpopulairefrancaise.ui.resources.timer_no_storage
import org.politisationpopulairefrancaise.ui.resources.timer_ok
import org.politisationpopulairefrancaise.ui.resources.timer_place_hint
import org.politisationpopulairefrancaise.ui.resources.timer_place_label
import org.politisationpopulairefrancaise.ui.resources.timer_place_title
import org.politisationpopulairefrancaise.ui.resources.timer_postpone
import org.politisationpopulairefrancaise.ui.resources.timer_problem_already
import org.politisationpopulairefrancaise.ui.resources.timer_problem_checkin_format
import org.politisationpopulairefrancaise.ui.resources.timer_problem_checkin_mismatch
import org.politisationpopulairefrancaise.ui.resources.timer_problem_checkin_simple
import org.politisationpopulairefrancaise.ui.resources.timer_problem_duress_format
import org.politisationpopulairefrancaise.ui.resources.timer_problem_duress_same
import org.politisationpopulairefrancaise.ui.resources.timer_problem_failed
import org.politisationpopulairefrancaise.ui.resources.timer_problem_invalid_recipient
import org.politisationpopulairefrancaise.ui.resources.timer_problem_no_recipient
import org.politisationpopulairefrancaise.ui.resources.timer_problem_text
import org.politisationpopulairefrancaise.ui.resources.timer_problem_too_many
import org.politisationpopulairefrancaise.ui.resources.timer_recipient_not_selected
import org.politisationpopulairefrancaise.ui.resources.timer_recipient_selected
import org.politisationpopulairefrancaise.ui.resources.timer_recipients_none
import org.politisationpopulairefrancaise.ui.resources.timer_recipients_text
import org.politisationpopulairefrancaise.ui.resources.timer_recipients_title
import org.politisationpopulairefrancaise.ui.resources.timer_reported_text
import org.politisationpopulairefrancaise.ui.resources.timer_reported_title
import org.politisationpopulairefrancaise.ui.resources.timer_setup_title
import org.politisationpopulairefrancaise.ui.resources.timer_text_label
import org.politisationpopulairefrancaise.ui.resources.timer_text_template
import org.politisationpopulairefrancaise.ui.resources.timer_text_title
import org.politisationpopulairefrancaise.ui.resources.timer_title
import org.politisationpopulairefrancaise.ui.resources.timer_validate
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * « Sans nouvelles de moi » (AG-55, D-088), opened from the protest mode: the explanation, the
 * setup (who, until when, the alert, the planned place typed by hand, the two codes), then the
 * timer in progress with « Je vais bien » and « Repousser ». After a code, the screen shows the
 * outcome: exactly the same for the « je vais bien » code and the duress code. Nothing is armed by
 * default; arming shows first who receives what and when, with « Annuler » before « Armer le
 * minuteur » (principle 10). Without storage, server or account, the screen says why and offers
 * nothing; a compromised account (S-146) cannot arm, but can still cancel its timer.
 */
@Composable
fun TimerScreen(state: TimerState, windowClass: WindowClass, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState())
            .padding(if (compact) 16.dp else 24.dp).testTag(TimerTags.SCREEN),
    ) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.timer_back), onBack, Modifier.testTag(TimerTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
                PageHeader(stringResource(Res.string.timer_title), stringResource(Res.string.timer_intro))
            }
            val outcome = state.outcome
            val own = state.own
            when {
                outcome != null -> OutcomeCard(state, outcome)
                !state.hasStorage -> Unavailable(Res.string.timer_no_storage)
                !state.hasServer -> Unavailable(Res.string.timer_no_server)
                state.account == null -> Unavailable(Res.string.timer_no_account)
                own != null -> ArmedCard(state, own)
                else -> {
                    HowItWorks()
                    PublishBlockedNotice()
                    SetupCard(state, enabled = LocalCanPublish.current && state.canSend)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Unavailable(text: StringResource) {
    InfoBox(Lucide.Info, Modifier.testTag(TimerTags.UNAVAILABLE)) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HowItWorks() {
    PpfCard(Modifier.fillMaxWidth()) {
        TimerHeading(stringResource(Res.string.timer_how_title), Lucide.ShieldCheck)
        Spacer(Modifier.height(8.dp))
        listOf(Res.string.timer_how_1, Res.string.timer_how_2, Res.string.timer_how_3, Res.string.timer_how_4, Res.string.timer_how_5).forEach { line ->
            Text("• " + stringResource(line), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** What a refusal of the core means for the person. */
private fun armProblemText(result: ArmResult.Refused): StringResource = when (result.problem) {
    ArmProblem.NO_RECIPIENT -> Res.string.timer_problem_no_recipient
    ArmProblem.TOO_MANY_RECIPIENTS -> Res.string.timer_problem_too_many
    ArmProblem.INVALID_RECIPIENT -> Res.string.timer_problem_invalid_recipient
    ArmProblem.DEADLINE_TOO_SOON, ArmProblem.DEADLINE_TOO_FAR -> Res.string.timer_deadline_invalid
    ArmProblem.NO_TEXT -> Res.string.timer_problem_text
    ArmProblem.CODES -> result.codes?.let(::codeProblemText) ?: Res.string.timer_problem_failed
    ArmProblem.ALREADY_ARMED -> Res.string.timer_problem_already
}

private fun codeProblemText(problem: TimerCodeProblem): StringResource = when (problem) {
    TimerCodeProblem.CHECK_IN_FORMAT -> Res.string.timer_problem_checkin_format
    TimerCodeProblem.CHECK_IN_SIMPLE -> Res.string.timer_problem_checkin_simple
    TimerCodeProblem.CHECK_IN_MISMATCH -> Res.string.timer_problem_checkin_mismatch
    TimerCodeProblem.DURESS_FORMAT -> Res.string.timer_problem_duress_format
    TimerCodeProblem.DURESS_SAME -> Res.string.timer_problem_duress_same
}

@Composable
private fun SetupCard(state: TimerState, enabled: Boolean) {
    val palette = PpfTheme.palette
    val candidates = state.candidates()
    val people = candidates.filter { !it.legalTeam }
    val teams = candidates.filter { it.legalTeam }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val choice = remember { DeadlineChoice(initialHours = 2) }
    val template = stringResource(Res.string.timer_text_template)
    var text by remember { mutableStateOf(template) }
    var place by remember { mutableStateOf("") }
    // The codes stay in memory only, never in the saved state of the screen.
    var code by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<StringResource?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val now = state.now
    val deadline = choice.deadline(now, now)
    val toggle: (String) -> Unit = { key -> selected = if (key in selected) selected - key else selected + key }
    val check: () -> Unit = {
        val chosen = candidates.count { it.key in selected }
        problem = when {
            chosen == 0 -> Res.string.timer_problem_no_recipient
            chosen > TimerService.MAX_RECIPIENTS -> Res.string.timer_problem_too_many
            !isValidDeadline(deadline, now) -> Res.string.timer_deadline_invalid
            TimerFormats.cleanText(text).isEmpty() -> Res.string.timer_problem_text
            else -> TimerCodeRules.check(code, confirmation, duress)?.let(::codeProblemText)
        }
        if (problem == null) confirming = true
    }

    PpfCard(Modifier.fillMaxWidth().testTag(TimerTags.SETUP)) {
        TimerHeading(stringResource(Res.string.timer_setup_title), Lucide.Clock)
        Spacer(Modifier.height(12.dp))

        SubHeading(stringResource(Res.string.timer_recipients_title))
        Text(stringResource(Res.string.timer_recipients_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        if (people.isEmpty()) {
            Text(stringResource(Res.string.timer_recipients_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            people.forEach { candidate -> RecipientRow(candidate, candidate.key in selected, enabled) { toggle(candidate.key) } }
        }
        Spacer(Modifier.height(12.dp))
        SubHeading(stringResource(Res.string.timer_legal_title))
        Text(stringResource(Res.string.timer_legal_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        if (teams.isEmpty()) {
            Text(stringResource(Res.string.timer_legal_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        } else {
            teams.forEach { candidate -> RecipientRow(candidate, candidate.key in selected, enabled) { toggle(candidate.key) } }
        }

        Spacer(Modifier.height(16.dp))
        SubHeading(stringResource(Res.string.timer_deadline_title))
        Spacer(Modifier.height(4.dp))
        DeadlinePicker(choice, presets = listOf(2, 4, 6), base = now, now = now)

        Spacer(Modifier.height(16.dp))
        SubHeading(stringResource(Res.string.timer_text_title))
        Spacer(Modifier.height(4.dp))
        PpfTextField(
            value = text,
            onValueChange = { text = it.take(TimerFormats.MAX_TEXT) },
            placeholder = stringResource(Res.string.timer_text_label),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(TimerTags.TEXT),
        )

        Spacer(Modifier.height(16.dp))
        SubHeading(stringResource(Res.string.timer_place_title))
        Spacer(Modifier.height(4.dp))
        PpfTextField(
            value = place,
            onValueChange = { place = it.take(TimerFormats.MAX_PLACE) },
            placeholder = stringResource(Res.string.timer_place_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TimerTags.PLACE),
        )
        Text(stringResource(Res.string.timer_place_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)

        Spacer(Modifier.height(16.dp))
        SubHeading(stringResource(Res.string.timer_codes_title))
        Text(stringResource(Res.string.timer_codes_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PinField(code, { code = it.take(TimerCodeRules.MAX_LENGTH) }, stringResource(Res.string.timer_code_checkin), check, fieldModifier = Modifier.testTag(TimerTags.CODE))
            PinField(confirmation, { confirmation = it.take(TimerCodeRules.MAX_LENGTH) }, stringResource(Res.string.timer_code_confirm), check, fieldModifier = Modifier.testTag(TimerTags.CODE_CONFIRM))
            PinField(duress, { duress = it.take(TimerCodeRules.MAX_LENGTH) }, stringResource(Res.string.timer_code_duress), check, fieldModifier = Modifier.testTag(TimerTags.DURESS))
            Text(stringResource(Res.string.timer_code_duress_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }

        Spacer(Modifier.height(12.dp))
        problem?.let { Announced(stringResource(it), Modifier.testTag(TimerTags.PROBLEM)) }
        if (state.busy) Announced(stringResource(Res.string.timer_arming), warning = false)
        Spacer(Modifier.height(8.dp))
        PpfPrimaryButton(
            onClick = check,
            text = stringResource(Res.string.timer_continue),
            enabled = enabled && !state.busy,
            modifier = Modifier.testTag(TimerTags.CONTINUE),
        )
    }

    val chosenDeadline = deadline
    if (confirming && chosenDeadline != null) {
        val chosen = candidates.filter { it.key in selected }
        ConfirmDialog(
            count = chosen.size,
            deadline = chosenDeadline,
            now = now,
            onCancel = { confirming = false },
            onArm = {
                confirming = false
                val request = ArmRequest(
                    recipients = chosen.map { it.toRecipient() },
                    deadline = chosenDeadline,
                    text = text,
                    place = place,
                    checkInCode = code,
                    checkInConfirmation = confirmation,
                    duressCode = duress.ifEmpty { null },
                )
                state.arm(request, onFailure = { problem = Res.string.timer_problem_failed }) { result ->
                    when (result) {
                        is ArmResult.Armed -> {
                            code = ""
                            confirmation = ""
                            duress = ""
                        }
                        is ArmResult.Refused -> problem = armProblemText(result)
                    }
                }
            },
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** A person or a legal team to tick: the whole row is one checkbox of at least 48 dp. */
@Composable
private fun RecipientRow(candidate: TimerCandidate, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    val name = personName(candidate.key, candidate.label)
    val stateText = stringResource(if (checked) Res.string.timer_recipient_selected else Res.string.timer_recipient_not_selected)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = stateText }
            .testTag(TimerTags.RECIPIENT + candidate.key),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.borderStrong, checkmarkColor = palette.onBrand),
        )
        Spacer(Modifier.width(8.dp))
        Avatar(
            seed = candidate.key,
            letters = if (candidate.legalTeam) name.take(2).uppercase() else avatarLetters(candidate.key),
            size = 32.dp,
            rounded = candidate.legalTeam,
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Principle 10: who receives what and when, before anything is armed; « Annuler » comes first. */
@Composable
private fun ConfirmDialog(count: Int, deadline: Long, now: Long, onCancel: () -> Unit, onArm: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(TimerTags.CONFIRM_DIALOG),
        icon = { Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.brand) },
        title = { Text(stringResource(Res.string.timer_confirm_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pluralStringResource(Res.plurals.timer_confirm_who, count, count), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.timer_confirm_when, timeLabel(deadline, now)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.timer_confirm_seized), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.timer_confirm_forget), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onArm, modifier = Modifier.testTag(TimerTags.CONFIRM_ARM)) {
                Text(stringResource(Res.string.timer_confirm_arm), color = palette.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(TimerTags.CONFIRM_CANCEL)) {
                Text(stringResource(Res.string.timer_cancel), color = palette.inkMuted)
            }
        },
    )
}

private enum class CodeDialogMode { OK, POSTPONE }

/** The timer in progress: its hour, what is left, who is told, and the two buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArmedCard(state: TimerState, own: OwnTimer) {
    val palette = PpfTheme.palette
    val now = state.now
    val left = own.shownDeadline - now
    var dialog by remember { mutableStateOf<CodeDialogMode?>(null) }
    PpfCard(Modifier.fillMaxWidth().testTag(TimerTags.ARMED)) {
        TimerHeading(stringResource(Res.string.timer_armed_title), Lucide.Clock)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.timer_armed_until, timeLabel(own.shownDeadline, now)),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(TimerTags.COUNTDOWN),
        )
        Spacer(Modifier.height(4.dp))
        if (left > 0) {
            Text(stringResource(Res.string.timer_armed_left, durationLabel(left)), style = MaterialTheme.typography.bodyLarge)
        } else {
            Announced(stringResource(Res.string.timer_armed_past))
        }
        Spacer(Modifier.height(8.dp))
        Text(pluralStringResource(Res.plurals.timer_armed_recipients, own.recipients.size, own.recipients.size), style = MaterialTheme.typography.bodyMedium)
        Text(own.recipients.joinToString(", ") { recipientName(it) }, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        if (own.waitingDeposits > 0) {
            Spacer(Modifier.height(8.dp))
            InfoBox(Lucide.TriangleExclamationPoint, Modifier.testTag(TimerTags.WAITING_DEPOSITS)) {
                Text(pluralStringResource(Res.plurals.timer_armed_waiting, own.waitingDeposits, own.waitingDeposits), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.timer_armed_warning), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfPrimaryButton(
                onClick = { dialog = CodeDialogMode.OK },
                text = stringResource(Res.string.timer_ok),
                icon = Lucide.CircleCheck,
                enabled = state.canSend,
                modifier = Modifier.testTag(TimerTags.OK),
            )
            PpfSecondaryButton(
                onClick = { dialog = CodeDialogMode.POSTPONE },
                text = stringResource(Res.string.timer_postpone),
                icon = Lucide.Clock,
                enabled = state.canSend,
                modifier = Modifier.testTag(TimerTags.POSTPONE),
            )
        }
    }
    dialog?.let { mode -> CodeDialog(state, mode, own) { dialog = null } }
}

/**
 * « Je vais bien » or « Repousser »: the code, and for « Repousser » the new hour. A wrong code
 * costs a growing wait, as on the lock screen. The right code and the duress code close the dialog
 * the same way, and the screen shows the same outcome.
 */
@Composable
private fun CodeDialog(state: TimerState, mode: CodeDialogMode, own: OwnTimer, onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<StringResource?>(null) }
    var wait by remember { mutableStateOf(0L) }
    val choice = remember { DeadlineChoice(initialHours = 1) }
    val now = state.now
    val base = maxOf(now, own.shownDeadline)
    val submit: () -> Unit = submit@{
        if (state.busy || code.isEmpty()) return@submit
        message = null
        wait = 0
        when (mode) {
            CodeDialogMode.OK -> state.checkIn(code) { result ->
                when (result) {
                    CheckInResult.Cancelled, CheckInResult.NoTimer -> {
                        code = ""
                        onDismiss()
                    }
                    is CheckInResult.Wrong -> {
                        code = ""
                        message = Res.string.timer_code_wrong
                        wait = result.waitSeconds
                    }
                    is CheckInResult.TooEarly -> wait = result.waitSeconds
                }
            }
            CodeDialogMode.POSTPONE -> {
                val deadline = choice.deadline(base, now)
                if (deadline == null || !isValidDeadline(deadline, now)) {
                    message = Res.string.timer_deadline_invalid
                } else {
                    state.report(code, deadline) { result ->
                        when (result) {
                            is ReportResult.Reported, ReportResult.NoTimer -> {
                                code = ""
                                onDismiss()
                            }
                            is ReportResult.Wrong -> {
                                code = ""
                                message = Res.string.timer_code_wrong
                                wait = result.waitSeconds
                            }
                            is ReportResult.TooEarly -> wait = result.waitSeconds
                            ReportResult.InvalidDeadline -> message = Res.string.timer_deadline_invalid
                        }
                    }
                }
            }
        }
    }
    val (title, explanation) = when (mode) {
        CodeDialogMode.OK -> Res.string.timer_code_title_ok to Res.string.timer_code_text_ok
        CodeDialogMode.POSTPONE -> Res.string.timer_code_title_postpone to Res.string.timer_code_text_postpone
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(TimerTags.CODE_DIALOG),
        icon = { Icon(if (mode == CodeDialogMode.OK) Lucide.CircleCheck else Lucide.Clock, contentDescription = null, tint = palette.brand) },
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(explanation), style = MaterialTheme.typography.bodyMedium)
                if (mode == CodeDialogMode.POSTPONE) DeadlinePicker(choice, presets = listOf(1, 2, 4), base = base, now = now)
                PinField(code, { code = it.take(TimerCodeRules.MAX_LENGTH) }, stringResource(Res.string.timer_code_label), submit, fieldModifier = Modifier.testTag(TimerTags.CODE_INPUT))
                if (state.busy) Announced(stringResource(Res.string.timer_checking), warning = false)
                message?.let { Announced(stringResource(it), Modifier.testTag(TimerTags.CODE_MESSAGE)) }
                if (wait > 0) Announced(waitText(wait))
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = !state.busy && code.isNotEmpty(), modifier = Modifier.testTag(TimerTags.CODE_SUBMIT)) {
                Text(stringResource(Res.string.timer_validate), color = if (!state.busy && code.isNotEmpty()) palette.brand else palette.inkSubtle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TimerTags.CODE_CANCEL)) {
                Text(stringResource(Res.string.timer_cancel), color = palette.inkMuted)
            }
        },
    )
}

/** After an action. « Minuteur annulé » is the same whichever code was typed. */
@Composable
private fun OutcomeCard(state: TimerState, outcome: TimerOutcome) {
    val now = state.now
    PpfCard(Modifier.fillMaxWidth().testTag(TimerTags.OUTCOME)) {
        val announced = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when (outcome) {
            is TimerOutcome.Armed -> {
                TimerHeading(stringResource(Res.string.timer_done_title), Lucide.CircleCheck, announced)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.timer_armed_until, timeLabel(outcome.shownDeadline, now)), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.timer_done_text, outcome.delivery.delivered, outcome.recipients), style = MaterialTheme.typography.bodyMedium)
                if (outcome.delivery.delivered < outcome.recipients) {
                    Text(stringResource(Res.string.timer_done_waiting), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TimerOutcome.Cancelled -> {
                TimerHeading(stringResource(Res.string.timer_cancelled_title), Lucide.CircleCheck, announced)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.timer_cancelled_text), style = MaterialTheme.typography.bodyMedium)
            }
            is TimerOutcome.Reported -> {
                TimerHeading(stringResource(Res.string.timer_reported_title), Lucide.Clock, announced)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.timer_reported_text, timeLabel(outcome.shownDeadline, now)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        PpfPrimaryButton(onClick = state::clearOutcome, text = stringResource(Res.string.timer_close), modifier = Modifier.testTag(TimerTags.OUTCOME_CLOSE))
    }
}
