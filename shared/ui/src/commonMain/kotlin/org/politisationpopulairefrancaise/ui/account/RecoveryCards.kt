package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SettingRow
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.LocalMoment
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_cancel
import org.politisationpopulairefrancaise.ui.resources.account_check_done_erased
import org.politisationpopulairefrancaise.ui.resources.account_check_done_kept
import org.politisationpopulairefrancaise.ui.resources.account_check_other_account
import org.politisationpopulairefrancaise.ui.resources.account_check_phrase
import org.politisationpopulairefrancaise.ui.resources.account_check_submit
import org.politisationpopulairefrancaise.ui.resources.account_check_whole
import org.politisationpopulairefrancaise.ui.resources.account_check_whole_label
import org.politisationpopulairefrancaise.ui.resources.account_check_words
import org.politisationpopulairefrancaise.ui.resources.account_check_wrong_words
import org.politisationpopulairefrancaise.ui.resources.account_close
import org.politisationpopulairefrancaise.ui.resources.account_compromised_next
import org.politisationpopulairefrancaise.ui.resources.account_compromised_successor
import org.politisationpopulairefrancaise.ui.resources.account_compromised_text
import org.politisationpopulairefrancaise.ui.resources.account_compromised_title
import org.politisationpopulairefrancaise.ui.resources.account_erase_after_check
import org.politisationpopulairefrancaise.ui.resources.account_erase_phrase
import org.politisationpopulairefrancaise.ui.resources.account_keep_confirm
import org.politisationpopulairefrancaise.ui.resources.account_keep_phrase
import org.politisationpopulairefrancaise.ui.resources.account_keep_phrase_on
import org.politisationpopulairefrancaise.ui.resources.account_keep_phrase_undo
import org.politisationpopulairefrancaise.ui.resources.account_keep_warning
import org.politisationpopulairefrancaise.ui.resources.account_keep_warning_title
import org.politisationpopulairefrancaise.ui.resources.account_no_phrase
import org.politisationpopulairefrancaise.ui.resources.account_phrase_state_awaiting
import org.politisationpopulairefrancaise.ui.resources.account_phrase_state_erased
import org.politisationpopulairefrancaise.ui.resources.account_phrase_state_kept
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_explanation
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_label
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_mismatch
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_mismatch_short
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_missing
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_missing_short
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_pending
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_set
import org.politisationpopulairefrancaise.ui.resources.account_recovery_key_unknown
import org.politisationpopulairefrancaise.ui.resources.account_recovery_title
import org.politisationpopulairefrancaise.ui.resources.account_reminder_text
import org.politisationpopulairefrancaise.ui.resources.account_reminder_title
import org.politisationpopulairefrancaise.ui.resources.account_retry
import org.politisationpopulairefrancaise.ui.resources.account_show_phrase
import org.politisationpopulairefrancaise.ui.resources.account_verify_word_label
import org.politisationpopulairefrancaise.ui.resources.moment_at
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the recovery screens (S-146). */
object RecoveryTags {
    const val COMPROMISED_BANNER = "account-compromised-banner"
    const val REMINDER = "account-phrase-reminder"
    const val RECOVERY_CARD = "account-recovery-card"
    const val RECOVERY_KEY = "account-recovery-key"
    const val PHRASE_STATE = "account-phrase-state"
    const val CHECK_PHRASE = "account-check-phrase"
    const val ERASE_PHRASE = "account-erase-phrase"
    const val KEEP_PHRASE = "account-keep-phrase"
    const val KEEP_UNDO = "account-keep-undo"
    const val KEEP_CONFIRM = "account-keep-confirm"
    const val KEEP_CANCEL = "account-keep-cancel"
    const val CHECK_FIELD = "check-word-" // followed by 0, 1 or 2
    const val CHECK_WHOLE = "check-whole"
    const val CHECK_SUBMIT = "account-check-submit"
    const val CHECK_ERROR = "account-check-error"
    const val CHECK_DONE = "account-check-done"
    const val CHECK_CLOSE = "account-check-close"
    const val VERIFY_LATER = "account-verify-later"
    const val RETRY = "account-recovery-retry"
}

/** "8 novembre 2026 à 14:05", in the device's time zone. */
@Composable
internal fun momentLabel(epochSeconds: Long): String {
    val moment = LocalMoment.of(epochSeconds)
    return stringResource(Res.string.moment_at, moment.date.toFrench(), moment.hourLabel)
}

/** A highlighted warning in the warning tint, with an icon: what the user must know at once. */
@Composable
internal fun WarningBox(icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = PpfTheme.palette
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = palette.warningTint, contentColor = palette.ink) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = palette.warning, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), content = content)
        }
    }
}

/** The banner at the top of Mon espace when the account of this device is declared compromised. */
@Composable
internal fun CompromisedBanner(compromise: OwnCompromise) {
    WarningBox(Lucide.TriangleExclamationPoint, Modifier.testTag(RecoveryTags.COMPROMISED_BANNER).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(Res.string.account_compromised_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.account_compromised_text, momentLabel(compromise.since)), style = MaterialTheme.typography.bodyMedium)
        compromise.successor?.let { successor ->
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.account_compromised_successor, shortNpub(successor)), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.account_compromised_next), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The reminder of a check postponed at signup (a week later, D-134). */
@Composable
internal fun PhraseReminderCard(session: AccountSession) {
    WarningBox(Lucide.KeyRound, Modifier.testTag(RecoveryTags.REMINDER)) {
        Text(stringResource(Res.string.account_reminder_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.account_reminder_text), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        PpfPrimaryButton(onClick = { session.beginPhraseCheck() }, text = stringResource(Res.string.account_check_phrase), small = true)
    }
}

/**
 * The phrase and the recovery key: where the phrase stands on this device, "Vérifier ma phrase",
 * keeping or erasing it (principle 10: keeping it asks a warning first, and stays reversible), and
 * whether the server holds the account's recovery key.
 */
@Composable
internal fun RecoveryCard(session: AccountSession, state: AccountUiState.Ready, onShowPhrase: () -> Unit) {
    val palette = PpfTheme.palette
    var warning by remember { mutableStateOf<(() -> Unit)?>(null) }
    PpfCard(Modifier.fillMaxWidth().testTag(RecoveryTags.RECOVERY_CARD)) {
        Text(stringResource(Res.string.account_recovery_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        val phraseText = when (state.phrase) {
            PhraseOnDevice.KEPT -> Res.string.account_phrase_state_kept
            PhraseOnDevice.AWAITING_CHECK -> Res.string.account_phrase_state_awaiting
            PhraseOnDevice.ERASED -> Res.string.account_phrase_state_erased
            PhraseOnDevice.NONE -> Res.string.account_no_phrase
        }
        Text(stringResource(phraseText), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(RecoveryTags.PHRASE_STATE))
        Spacer(Modifier.height(12.dp))

        val check = session.phraseCheck
        if (check != null) {
            PhraseCheckForm(session, check, onKeep = { warning = { session.setCheckKeep(true) } })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PpfSecondaryButton(
                    onClick = { session.beginPhraseCheck() },
                    text = stringResource(Res.string.account_check_phrase),
                    icon = Lucide.CircleCheck,
                    modifier = Modifier.fillMaxWidth().testTag(RecoveryTags.CHECK_PHRASE),
                )
                when (state.phrase) {
                    PhraseOnDevice.KEPT -> {
                        PpfSecondaryButton(onClick = onShowPhrase, text = stringResource(Res.string.account_show_phrase), icon = Lucide.Eye, modifier = Modifier.fillMaxWidth().testTag(AccountTags.SHOW_PHRASE))
                        PpfTextButton(onClick = session::erasePhrase, text = stringResource(Res.string.account_erase_phrase), modifier = Modifier.testTag(RecoveryTags.ERASE_PHRASE))
                    }
                    PhraseOnDevice.AWAITING_CHECK ->
                        PpfSecondaryButton(onClick = onShowPhrase, text = stringResource(Res.string.account_show_phrase), icon = Lucide.Eye, modifier = Modifier.fillMaxWidth().testTag(AccountTags.SHOW_PHRASE))
                    PhraseOnDevice.ERASED ->
                        PpfTextButton(
                            onClick = { warning = { session.beginPhraseCheck(keep = true) } },
                            text = stringResource(Res.string.account_keep_phrase),
                            modifier = Modifier.testTag(RecoveryTags.KEEP_PHRASE),
                            color = palette.inkMuted,
                        )
                    PhraseOnDevice.NONE -> Unit
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingRow(stringResource(Res.string.account_recovery_key_label), divider = false) {
            val (label, color) = when (session.recoveryKey) {
                RecoveryKeyState.SET -> Res.string.account_recovery_key_set to palette.success
                RecoveryKeyState.PENDING -> Res.string.account_recovery_key_pending to palette.inkMuted
                RecoveryKeyState.UNKNOWN -> Res.string.account_recovery_key_unknown to palette.inkMuted
                RecoveryKeyState.MISSING -> Res.string.account_recovery_key_missing_short to palette.warning
                RecoveryKeyState.MISMATCH -> Res.string.account_recovery_key_mismatch_short to palette.accentInk
            }
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.testTag(RecoveryTags.RECOVERY_KEY))
        }
        when (session.recoveryKey) {
            RecoveryKeyState.MISSING -> Text(stringResource(Res.string.account_recovery_key_missing), style = MaterialTheme.typography.bodyMedium, color = palette.ink)
            RecoveryKeyState.MISMATCH -> Text(stringResource(Res.string.account_recovery_key_mismatch), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk)
            RecoveryKeyState.PENDING, RecoveryKeyState.UNKNOWN ->
                PpfTextButton(onClick = session::refreshRecovery, text = stringResource(Res.string.account_retry), modifier = Modifier.testTag(RecoveryTags.RETRY))
            RecoveryKeyState.SET -> Unit
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.account_recovery_key_explanation), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }

    warning?.let { onConfirm -> KeepPhraseDialog(onConfirm = { onConfirm(); warning = null }, onCancel = { warning = null }) }
}

/** "Vérifier ma phrase": three words when the phrase is on the device, the whole phrase otherwise. */
@Composable
private fun PhraseCheckForm(session: AccountSession, check: PhraseCheckState, onKeep: () -> Unit) {
    val palette = PpfTheme.palette
    if (check.done) {
        Text(
            text = stringResource(if (check.keep) Res.string.account_check_done_kept else Res.string.account_check_done_erased),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.success,
            modifier = Modifier.testTag(RecoveryTags.CHECK_DONE).semantics { liveRegion = LiveRegionMode.Polite },
        )
        PpfTextButton(onClick = session::closePhraseCheck, text = stringResource(Res.string.account_close), modifier = Modifier.testTag(RecoveryTags.CHECK_CLOSE))
        return
    }
    val positions = check.positions
    var answers by remember(check.positions) { mutableStateOf(List(positions?.size ?: 0) { "" }) }
    var whole by remember { mutableStateOf("") }
    if (positions != null) {
        val human = positions.map { it + 1 }
        Text(stringResource(Res.string.account_check_words, human[0], human[1], human[2]), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        WordFields(human, answers, onChange = { answers = it }, isError = check.failure != null, tagPrefix = RecoveryTags.CHECK_FIELD)
    } else {
        Text(stringResource(Res.string.account_check_whole), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = whole,
            onValueChange = { whole = it },
            label = { Text(stringResource(Res.string.account_check_whole_label)) },
            minLines = 3,
            isError = check.failure != null,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag(RecoveryTags.CHECK_WHOLE),
        )
    }
    check.failure?.let { failure ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (failure) {
                PhraseCheckFailure.WrongWords -> stringResource(Res.string.account_check_wrong_words)
                PhraseCheckFailure.OtherAccount -> stringResource(Res.string.account_check_other_account)
                is PhraseCheckFailure.Invalid -> restoreErrorText(failure.error)
            },
            color = palette.accentInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(RecoveryTags.CHECK_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(8.dp))
    KeepChoice(keep = check.keep, onKeep = onKeep, onUndo = { session.setCheckKeep(false) })
    Spacer(Modifier.height(8.dp))
    PpfPrimaryButton(
        onClick = { if (positions != null) session.submitPhraseWords(answers) else session.submitWholePhrase(whole) },
        text = stringResource(Res.string.account_check_submit),
        modifier = Modifier.fillMaxWidth().testTag(RecoveryTags.CHECK_SUBMIT),
    )
    PpfTextButton(onClick = session::closePhraseCheck, text = stringResource(Res.string.account_cancel), color = palette.inkMuted)
}

/** What becomes of the phrase after the check: erased by default; kept only after the warning. */
@Composable
internal fun KeepChoice(keep: Boolean, onKeep: () -> Unit, onUndo: () -> Unit) {
    val palette = PpfTheme.palette
    if (keep) {
        Text(stringResource(Res.string.account_keep_phrase_on), style = MaterialTheme.typography.bodyMedium, color = palette.warning)
        PpfTextButton(onClick = onUndo, text = stringResource(Res.string.account_keep_phrase_undo), modifier = Modifier.testTag(RecoveryTags.KEEP_UNDO))
    } else {
        Text(stringResource(Res.string.account_erase_after_check), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        PpfTextButton(onClick = onKeep, text = stringResource(Res.string.account_keep_phrase), modifier = Modifier.testTag(RecoveryTags.KEEP_PHRASE), color = palette.inkMuted)
    }
}

/** Three fields for the words asked back, labelled with their number. */
@Composable
internal fun WordFields(humanPositions: List<Int>, answers: List<String>, onChange: (List<String>) -> Unit, isError: Boolean, tagPrefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        humanPositions.forEachIndexed { index, position ->
            OutlinedTextField(
                value = answers.getOrElse(index) { "" },
                onValueChange = { typed -> onChange(answers.toMutableList().also { it[index] = typed }) },
                label = { Text(stringResource(Res.string.account_verify_word_label, position)) },
                singleLine = true,
                isError = isError,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().testTag(tagPrefix + index),
            )
        }
    }
}

/** The warning before keeping the phrase on the device (principle 10), with Confirm and Cancel. */
@Composable
internal fun KeepPhraseDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.account_keep_warning_title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(stringResource(Res.string.account_keep_warning), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(RecoveryTags.KEEP_CONFIRM)) {
                Text(stringResource(Res.string.account_keep_confirm), color = palette.accentInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(RecoveryTags.KEEP_CANCEL)) {
                Text(stringResource(Res.string.account_cancel), color = palette.brand)
            }
        },
    )
}
