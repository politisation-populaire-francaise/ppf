package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_cancel
import org.politisationpopulairefrancaise.ui.resources.account_close
import org.politisationpopulairefrancaise.ui.resources.account_phrase_noted
import org.politisationpopulairefrancaise.ui.resources.account_phrase_paper
import org.politisationpopulairefrancaise.ui.resources.account_phrase_warning
import org.politisationpopulairefrancaise.ui.resources.account_review_phrase
import org.politisationpopulairefrancaise.ui.resources.account_verify_error
import org.politisationpopulairefrancaise.ui.resources.account_verify_instruction
import org.politisationpopulairefrancaise.ui.resources.account_verify_submit
import org.politisationpopulairefrancaise.ui.resources.lost_confirm_button
import org.politisationpopulairefrancaise.ui.resources.lost_confirm_no_successor
import org.politisationpopulairefrancaise.ui.resources.lost_confirm_successor
import org.politisationpopulairefrancaise.ui.resources.lost_confirm_text
import org.politisationpopulairefrancaise.ui.resources.lost_confirm_title
import org.politisationpopulairefrancaise.ui.resources.lost_continue
import org.politisationpopulairefrancaise.ui.resources.lost_done_new
import org.politisationpopulairefrancaise.ui.resources.lost_done_text
import org.politisationpopulairefrancaise.ui.resources.lost_done_title
import org.politisationpopulairefrancaise.ui.resources.lost_earlier_day
import org.politisationpopulairefrancaise.ui.resources.lost_earlier_hour
import org.politisationpopulairefrancaise.ui.resources.lost_edit
import org.politisationpopulairefrancaise.ui.resources.lost_error_already
import org.politisationpopulairefrancaise.ui.resources.lost_error_device_account
import org.politisationpopulairefrancaise.ui.resources.lost_error_invalid
import org.politisationpopulairefrancaise.ui.resources.lost_error_no_commitment
import org.politisationpopulairefrancaise.ui.resources.lost_error_no_server
import org.politisationpopulairefrancaise.ui.resources.lost_error_refused
import org.politisationpopulairefrancaise.ui.resources.lost_error_successor_invalid
import org.politisationpopulairefrancaise.ui.resources.lost_error_successor_self
import org.politisationpopulairefrancaise.ui.resources.lost_error_successor_taken
import org.politisationpopulairefrancaise.ui.resources.lost_error_unavailable
import org.politisationpopulairefrancaise.ui.resources.lost_error_wrong_phrase
import org.politisationpopulairefrancaise.ui.resources.lost_intro
import org.politisationpopulairefrancaise.ui.resources.lost_later_day
import org.politisationpopulairefrancaise.ui.resources.lost_later_hour
import org.politisationpopulairefrancaise.ui.resources.lost_new_title
import org.politisationpopulairefrancaise.ui.resources.lost_phrase_label
import org.politisationpopulairefrancaise.ui.resources.lost_sending
import org.politisationpopulairefrancaise.ui.resources.lost_since_hint
import org.politisationpopulairefrancaise.ui.resources.lost_since_now
import org.politisationpopulairefrancaise.ui.resources.lost_since_title
import org.politisationpopulairefrancaise.ui.resources.lost_successor_current
import org.politisationpopulairefrancaise.ui.resources.lost_successor_hint
import org.politisationpopulairefrancaise.ui.resources.lost_successor_new
import org.politisationpopulairefrancaise.ui.resources.lost_successor_none
import org.politisationpopulairefrancaise.ui.resources.lost_successor_other
import org.politisationpopulairefrancaise.ui.resources.lost_successor_other_label
import org.politisationpopulairefrancaise.ui.resources.lost_successor_title
import org.politisationpopulairefrancaise.ui.resources.lost_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of "J'ai perdu le contrôle de mon compte". */
object LostControlTags {
    const val OPEN = "lost-control-open"
    const val CARD = "lost-control-card"
    const val PHRASE = "lost-control-phrase"
    const val SINCE = "lost-control-since"
    const val SINCE_EARLIER_DAY = "lost-control-earlier-day"
    const val SINCE_EARLIER_HOUR = "lost-control-earlier-hour"
    const val SINCE_LATER_HOUR = "lost-control-later-hour"
    const val SINCE_LATER_DAY = "lost-control-later-day"
    const val SINCE_NOW = "lost-control-now"
    const val SUCCESSOR = "lost-control-successor-" // followed by the choice's name
    const val OTHER_KEY = "lost-control-other-key"
    const val CONTINUE = "lost-control-continue"
    const val ERROR = "lost-control-error"
    const val NEW_NOTED = "lost-control-new-noted"
    const val NEW_FIELD = "lost-control-new-word-" // followed by 0, 1 or 2
    const val NEW_SUBMIT = "lost-control-new-submit"
    const val NEW_ERROR = "lost-control-new-error"
    const val CONFIRM_DIALOG = "lost-control-confirm-dialog"
    const val CONFIRM = "lost-control-confirm"
    const val EDIT = "lost-control-edit"
    const val DONE = "lost-control-done"
    const val CLOSE = "lost-control-close"
    const val CANCEL = "lost-control-cancel"
}

/**
 * "J'ai perdu le contrôle de mon compte" (S-146, D-134): the phrase of the lost account, since
 * when (from 30 days back to now, now by default), the successor (this device's account, a new
 * one, none, or another key), then the irreversible confirmation, the publication and its answer.
 */
@Composable
internal fun LostControlCard(session: AccountSession, windowClass: WindowClass) {
    val flow = session.lostControl
    val state = flow.state ?: return
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    PpfCard(Modifier.fillMaxWidth().testTag(LostControlTags.CARD)) {
        Text(stringResource(Res.string.lost_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        when (state) {
            is LostControlState.Form -> LostControlForm(flow, state)
            is LostControlState.NewAccount -> NewAccountStep(flow, state, windowClass)
            is LostControlState.Confirm -> {
                LostControlForm(flow, LostControlState.Form(input = state.input))
                ConfirmDialog(state, onConfirm = { scope.launch { flow.confirm() } }, onBack = flow::back)
            }
            LostControlState.Sending -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                CircularProgressIndicator(color = palette.brand)
                Text(stringResource(Res.string.lost_sending), modifier = Modifier.padding(start = 16.dp))
            }
            is LostControlState.Done -> {
                Column(Modifier.testTag(LostControlTags.DONE).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(Res.string.lost_done_title), style = MaterialTheme.typography.titleSmall, color = palette.success)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.lost_done_text, momentLabel(state.since), shortNpub(state.compromised)), style = MaterialTheme.typography.bodyMedium)
                    if (state.replacedThisDevice) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(Res.string.lost_done_new), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PpfPrimaryButton(onClick = flow::cancel, text = stringResource(Res.string.account_close), modifier = Modifier.testTag(LostControlTags.CLOSE))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LostControlForm(flow: LostControlFlow, state: LostControlState.Form) {
    val palette = PpfTheme.palette
    val previous = state.input
    var phrase by rememberSaveable { mutableStateOf(previous?.phraseText ?: "") }
    // "Compromis depuis", as hours before now: 0 (now) to 720 (30 days).
    var hoursBack by rememberSaveable { mutableStateOf(previous?.let { ((currentEpochSeconds() - it.since) / 3600).toInt().coerceIn(0, MAX_HOURS_BACK) } ?: 0) }
    var successor by rememberSaveable { mutableStateOf(previous?.successor ?: flow.defaultSuccessor()) }
    var otherKey by rememberSaveable { mutableStateOf(previous?.otherKey ?: "") }
    val opened = remember { mutableLongStateOf(currentEpochSeconds()) }
    val since = opened.longValue - hoursBack * 3600L

    Text(stringResource(Res.string.lost_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = phrase,
        onValueChange = { phrase = it },
        label = { Text(stringResource(Res.string.lost_phrase_label)) },
        minLines = 3,
        isError = state.error is LostControlError.Phrase,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(LostControlTags.PHRASE),
    )

    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.lost_since_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Text(
        text = if (hoursBack == 0) stringResource(Res.string.lost_since_now) else momentLabel(since),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag(LostControlTags.SINCE).semantics { liveRegion = LiveRegionMode.Polite },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PpfSecondaryButton(onClick = { hoursBack = (hoursBack + 24).coerceAtMost(MAX_HOURS_BACK) }, text = stringResource(Res.string.lost_earlier_day), small = true, enabled = hoursBack < MAX_HOURS_BACK, modifier = Modifier.testTag(LostControlTags.SINCE_EARLIER_DAY))
        PpfSecondaryButton(onClick = { hoursBack = (hoursBack + 1).coerceAtMost(MAX_HOURS_BACK) }, text = stringResource(Res.string.lost_earlier_hour), small = true, enabled = hoursBack < MAX_HOURS_BACK, modifier = Modifier.testTag(LostControlTags.SINCE_EARLIER_HOUR))
        PpfSecondaryButton(onClick = { hoursBack = (hoursBack - 1).coerceAtLeast(0) }, text = stringResource(Res.string.lost_later_hour), small = true, enabled = hoursBack > 0, modifier = Modifier.testTag(LostControlTags.SINCE_LATER_HOUR))
        PpfSecondaryButton(onClick = { hoursBack = (hoursBack - 24).coerceAtLeast(0) }, text = stringResource(Res.string.lost_later_day), small = true, enabled = hoursBack > 0, modifier = Modifier.testTag(LostControlTags.SINCE_LATER_DAY))
        PpfTextButton(onClick = { hoursBack = 0 }, text = stringResource(Res.string.lost_since_now), enabled = hoursBack > 0, modifier = Modifier.testTag(LostControlTags.SINCE_NOW))
    }
    Text(stringResource(Res.string.lost_since_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)

    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.lost_successor_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    val device = flow.deviceAccount()
    Column {
        if (device != null) {
            PpfRadioRow(stringResource(Res.string.lost_successor_current, shortNpub(device)), selected = successor == SuccessorChoice.CURRENT, onClick = { successor = SuccessorChoice.CURRENT }, modifier = Modifier.testTag(LostControlTags.SUCCESSOR + SuccessorChoice.CURRENT.name))
        }
        PpfRadioRow(stringResource(Res.string.lost_successor_new), selected = successor == SuccessorChoice.NEW, onClick = { successor = SuccessorChoice.NEW }, modifier = Modifier.testTag(LostControlTags.SUCCESSOR + SuccessorChoice.NEW.name))
        PpfRadioRow(stringResource(Res.string.lost_successor_none), selected = successor == SuccessorChoice.NONE, onClick = { successor = SuccessorChoice.NONE }, modifier = Modifier.testTag(LostControlTags.SUCCESSOR + SuccessorChoice.NONE.name))
        PpfRadioRow(stringResource(Res.string.lost_successor_other), selected = successor == SuccessorChoice.OTHER, onClick = { successor = SuccessorChoice.OTHER }, modifier = Modifier.testTag(LostControlTags.SUCCESSOR + SuccessorChoice.OTHER.name))
    }
    if (successor == SuccessorChoice.OTHER) {
        OutlinedTextField(
            value = otherKey,
            onValueChange = { otherKey = it },
            label = { Text(stringResource(Res.string.lost_successor_other_label)) },
            singleLine = true,
            isError = state.error == LostControlError.InvalidSuccessor,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag(LostControlTags.OTHER_KEY),
        )
    }
    Text(stringResource(Res.string.lost_successor_hint), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)

    state.error?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = lostControlErrorText(error),
            color = palette.accentInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(LostControlTags.ERROR).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(16.dp))
    PpfPrimaryButton(
        onClick = {
            // The date is computed when the user continues, so that "now" is really now.
            val now = currentEpochSeconds()
            flow.submit(LostControlInput(phrase, since = now - hoursBack * 3600L, successor = successor, otherKey = otherKey))
        },
        text = stringResource(Res.string.lost_continue),
        enabled = phrase.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag(LostControlTags.CONTINUE),
    )
    PpfTextButton(onClick = flow::cancel, text = stringResource(Res.string.account_cancel), color = palette.inkMuted, modifier = Modifier.testTag(LostControlTags.CANCEL))
}

/** The phrase of the new account: noted on paper and checked before the notice names it. */
@Composable
private fun NewAccountStep(flow: LostControlFlow, state: LostControlState.NewAccount, windowClass: WindowClass) {
    val palette = PpfTheme.palette
    Text(stringResource(Res.string.lost_new_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    if (!state.verifying) {
        Text(stringResource(Res.string.account_phrase_warning), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.account_phrase_paper), style = MaterialTheme.typography.bodyMedium, color = palette.ink)
        Spacer(Modifier.height(12.dp))
        PhraseGrid(state.phrase, windowClass)
        Spacer(Modifier.height(12.dp))
        PpfPrimaryButton(onClick = flow::newPhraseNoted, text = stringResource(Res.string.account_phrase_noted), modifier = Modifier.fillMaxWidth().testTag(LostControlTags.NEW_NOTED))
    } else {
        var answers by remember(state.positions) { mutableStateOf(List(state.positions.size) { "" }) }
        val human = state.positions.map { it + 1 }
        Text(stringResource(Res.string.account_verify_instruction, human[0], human[1], human[2]), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        WordFields(human, answers, onChange = { answers = it }, isError = state.failed, tagPrefix = LostControlTags.NEW_FIELD)
        if (state.failed) {
            Text(stringResource(Res.string.account_verify_error), color = palette.accentInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(LostControlTags.NEW_ERROR))
        }
        Spacer(Modifier.height(12.dp))
        PpfPrimaryButton(onClick = { flow.checkNewPhrase(answers) }, text = stringResource(Res.string.account_verify_submit), modifier = Modifier.fillMaxWidth().testTag(LostControlTags.NEW_SUBMIT))
        PpfTextButton(onClick = flow::reviewNewPhrase, text = stringResource(Res.string.account_review_phrase))
    }
    PpfTextButton(onClick = flow::cancel, text = stringResource(Res.string.account_cancel), color = palette.inkMuted, modifier = Modifier.testTag(LostControlTags.CANCEL))
}

/** The irreversible step, said plainly; the safe choice ("Modifier") dismisses it. */
@Composable
private fun ConfirmDialog(state: LostControlState.Confirm, onConfirm: () -> Unit, onBack: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onBack,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(LostControlTags.CONFIRM_DIALOG),
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.lost_confirm_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(stringResource(Res.string.lost_confirm_text, shortNpub(state.compromised), momentLabel(state.input.since)), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.successor?.let { stringResource(Res.string.lost_confirm_successor, shortNpub(it)) } ?: stringResource(Res.string.lost_confirm_no_successor),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(LostControlTags.CONFIRM)) {
                Text(stringResource(Res.string.lost_confirm_button), color = palette.accentInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack, modifier = Modifier.testTag(LostControlTags.EDIT)) {
                Text(stringResource(Res.string.lost_edit), color = palette.brand)
            }
        },
    )
}

@Composable
private fun lostControlErrorText(error: LostControlError): String = when (error) {
    is LostControlError.Phrase -> restoreErrorText(error.error)
    LostControlError.InvalidSuccessor -> stringResource(Res.string.lost_error_successor_invalid)
    LostControlError.SuccessorIsCompromised -> stringResource(Res.string.lost_error_successor_self)
    LostControlError.DeviceHasAnotherAccount -> stringResource(Res.string.lost_error_device_account)
    LostControlError.NoServer -> stringResource(Res.string.lost_error_no_server)
    LostControlError.Unavailable -> stringResource(Res.string.lost_error_unavailable)
    LostControlError.NoCommitment -> stringResource(Res.string.lost_error_no_commitment)
    LostControlError.WrongPhrase -> stringResource(Res.string.lost_error_wrong_phrase)
    LostControlError.AlreadyCompromised -> stringResource(Res.string.lost_error_already)
    LostControlError.SuccessorTaken -> stringResource(Res.string.lost_error_successor_taken)
    LostControlError.InvalidNotice -> stringResource(Res.string.lost_error_invalid)
    LostControlError.Refused -> stringResource(Res.string.lost_error_refused)
}

/** 30 days back at most (D-134, decision 2). */
private val MAX_HOURS_BACK: Int = (AccountRecovery.MAX_BACKDATING_SECONDS / 3600).toInt()
