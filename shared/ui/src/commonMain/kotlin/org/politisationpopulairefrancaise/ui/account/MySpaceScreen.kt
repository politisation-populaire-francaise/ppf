package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.identity.PhraseLanguage
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SettingRow
import org.politisationpopulairefrancaise.ui.components.avatarLetters
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.account_cancel
import org.politisationpopulairefrancaise.ui.resources.account_create
import org.politisationpopulairefrancaise.ui.resources.account_failed
import org.politisationpopulairefrancaise.ui.resources.account_hide
import org.politisationpopulairefrancaise.ui.resources.account_intro_text
import org.politisationpopulairefrancaise.ui.resources.account_intro_title
import org.politisationpopulairefrancaise.ui.resources.account_language_english
import org.politisationpopulairefrancaise.ui.resources.account_language_french
import org.politisationpopulairefrancaise.ui.resources.account_loading
import org.politisationpopulairefrancaise.ui.resources.account_phrase_noted
import org.politisationpopulairefrancaise.ui.resources.account_phrase_title
import org.politisationpopulairefrancaise.ui.resources.account_phrase_warning
import org.politisationpopulairefrancaise.ui.resources.account_pseudonym_hint
import org.politisationpopulairefrancaise.ui.resources.account_public_key_explanation
import org.politisationpopulairefrancaise.ui.resources.account_public_key_label
import org.politisationpopulairefrancaise.ui.resources.account_ready_title
import org.politisationpopulairefrancaise.ui.resources.account_restore
import org.politisationpopulairefrancaise.ui.resources.account_restore_ambiguous
import org.politisationpopulairefrancaise.ui.resources.account_restore_error_checksum
import org.politisationpopulairefrancaise.ui.resources.account_restore_error_key
import org.politisationpopulairefrancaise.ui.resources.account_restore_error_unknown_words
import org.politisationpopulairefrancaise.ui.resources.account_restore_error_word_count
import org.politisationpopulairefrancaise.ui.resources.account_restore_label
import org.politisationpopulairefrancaise.ui.resources.account_restore_submit
import org.politisationpopulairefrancaise.ui.resources.account_restore_title
import org.politisationpopulairefrancaise.ui.resources.account_reveal_confirm
import org.politisationpopulairefrancaise.ui.resources.account_reveal_phrase_warning
import org.politisationpopulairefrancaise.ui.resources.account_reveal_secret_warning
import org.politisationpopulairefrancaise.ui.resources.account_reveal_warning_title
import org.politisationpopulairefrancaise.ui.resources.account_review_phrase
import org.politisationpopulairefrancaise.ui.resources.account_secret_label
import org.politisationpopulairefrancaise.ui.resources.account_show_secret
import org.politisationpopulairefrancaise.ui.resources.account_sign_out
import org.politisationpopulairefrancaise.ui.resources.account_sign_out_confirm
import org.politisationpopulairefrancaise.ui.resources.account_sign_out_warning
import org.politisationpopulairefrancaise.ui.resources.account_verify_error
import org.politisationpopulairefrancaise.ui.resources.account_verify_instruction
import org.politisationpopulairefrancaise.ui.resources.account_verify_submit
import org.politisationpopulairefrancaise.ui.resources.account_verify_title
import org.politisationpopulairefrancaise.ui.resources.account_verify_word_label
import org.politisationpopulairefrancaise.ui.resources.account_visibility_title
import org.politisationpopulairefrancaise.ui.resources.account_votes_explanation
import org.politisationpopulairefrancaise.ui.resources.account_votes_label
import org.politisationpopulairefrancaise.ui.resources.account_votes_never
import org.politisationpopulairefrancaise.ui.resources.account_phrase_paper
import org.politisationpopulairefrancaise.ui.resources.account_verify_later
import org.politisationpopulairefrancaise.ui.resources.account_verify_later_hint
import org.politisationpopulairefrancaise.ui.resources.lost_open
import org.politisationpopulairefrancaise.ui.resources.desc_mon_espace
import org.politisationpopulairefrancaise.ui.resources.space_mon_espace
import org.politisationpopulairefrancaise.ui.resources.platform_label
import org.politisationpopulairefrancaise.ui.resources.version_label
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.currentPlatform
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.notifications.NotificationsCard
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState

/** Test tags of the account screens. */
object AccountTags {
    const val CREATE = "account-create"
    const val RESTORE = "account-restore"
    const val PHRASE_WORD = "phrase-word-" // followed by the zero-based index
    const val PHRASE_NOTED = "account-phrase-noted"
    const val VERIFY_INSTRUCTION = "account-verify-instruction"
    const val VERIFY_FIELD = "verify-word-" // followed by 0, 1 or 2
    const val VERIFY_SUBMIT = "account-verify-submit"
    const val VERIFY_ERROR = "account-verify-error"
    const val RESTORE_INPUT = "restore-input"
    const val RESTORE_SUBMIT = "account-restore-submit"
    const val RESTORE_ERROR = "account-restore-error"
    const val PUBLIC_KEY = "account-public-key"
    const val SHOW_PHRASE = "account-show-phrase"
    const val SHOW_SECRET = "account-show-secret"
    const val SECRET_KEY = "account-secret-key"
    const val SIGN_OUT = "account-sign-out"
    const val DIALOG_CONFIRM = "account-dialog-confirm"
    const val CANCEL = "account-cancel"
}

/**
 * "Mon espace": the personal dashboard. For now it holds the account itself: creation with a
 * recovery phrase, restoration, and the identity once it exists. Everything stays on the device.
 */
@Composable
fun MySpaceScreen(
    session: AccountSession,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    /** How long the server keeps the account's private messages; hidden when null. */
    mailbox: MailboxSetting? = null,
    /** The notifications of the device (M31); hidden when null. The categories other than messages need no account. */
    notifications: NotificationsSettingsState? = null,
) {
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(
        modifier = modifier.fillMaxSize().background(PpfTheme.palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(stringResource(Res.string.space_mon_espace), stringResource(Res.string.desc_mon_espace))
            Spacer(Modifier.height(4.dp))
            val state = session.state
            if (session.lostControl.state != null && state != AccountUiState.Loading && state != AccountUiState.Unreadable) {
                // "J'ai perdu le contrôle de mon compte" takes the page while it is open.
                LostControlCard(session, windowClass)
            } else when (state) {
                AccountUiState.Loading -> LoadingCard()
                AccountUiState.Unreadable -> MessageCard(stringResource(Res.string.account_failed))
                AccountUiState.NoAccount -> {
                    NoAccountCard(session)
                    if (notifications != null) NotificationsCard(notifications)
                }
                is AccountUiState.Creating -> CreatingCard(session, state, windowClass)
                is AccountUiState.Restoring -> RestoringCard(session, state)
                is AccountUiState.Ready -> ReadyCards(session, state, windowClass, mailbox, notifications)
            }
            // Which version is installed, on which system: what a tester reports with a problem.
            val platform = currentPlatform()
            Text(
                stringResource(Res.string.version_label, AppInfo.VERSION) + " · " + stringResource(Res.string.platform_label, platform.name, platform.version),
                style = MaterialTheme.typography.bodySmall,
                color = PpfTheme.palette.inkSubtle,
                modifier = Modifier.padding(top = 12.dp).testTag(MY_SPACE_VERSION_TAG),
            )
        }
    }
}

/** The line that names the installed version, at the bottom of "Mon espace". */
const val MY_SPACE_VERSION_TAG: String = "my-space-version"

@Composable
private fun LoadingCard() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = PpfTheme.palette.brand)
        Text(stringResource(Res.string.account_loading), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun MessageCard(text: String) {
    PpfCard(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NoAccountCard(session: AccountSession) {
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.account_intro_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.account_intro_text), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfPrimaryButton(onClick = session::beginCreation, text = stringResource(Res.string.account_create), modifier = Modifier.weight(1f).testTag(AccountTags.CREATE))
            PpfSecondaryButton(onClick = session::beginRestore, text = stringResource(Res.string.account_restore), modifier = Modifier.weight(1f).testTag(AccountTags.RESTORE))
        }
        Spacer(Modifier.height(8.dp))
        PpfTextButton(onClick = session.lostControl::begin, text = stringResource(Res.string.lost_open), modifier = Modifier.testTag(LostControlTags.OPEN), color = PpfTheme.palette.inkMuted)
    }
}

@Composable
private fun CreatingCard(session: AccountSession, state: AccountUiState.Creating, windowClass: WindowClass) {
    PpfCard(Modifier.fillMaxWidth()) {
        when (state.step) {
            CreationStep.SHOW_PHRASE -> {
                Text(stringResource(Res.string.account_phrase_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.account_phrase_warning), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                // D-134: without the sheet of paper, an account never checked in person cannot be recovered.
                Text(stringResource(Res.string.account_phrase_paper), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.ink)
                Spacer(Modifier.height(16.dp))
                PhraseGrid(state.phrase, windowClass)
                Spacer(Modifier.height(16.dp))
                PpfPrimaryButton(onClick = session::phraseNoted, text = stringResource(Res.string.account_phrase_noted), modifier = Modifier.fillMaxWidth().testTag(AccountTags.PHRASE_NOTED))
                PpfTextButton(onClick = session::cancel, text = stringResource(Res.string.account_cancel), modifier = Modifier.testTag(AccountTags.CANCEL), color = PpfTheme.palette.inkMuted)
            }
            CreationStep.VERIFY -> VerifyStep(session, state)
        }
    }
}

@Composable
private fun VerifyStep(session: AccountSession, state: AccountUiState.Creating) {
    val answers = remember(state.phrase) { mutableStateOf(List(state.checkedPositions.size) { "" }) }
    val humanPositions = state.checkedPositions.map { it + 1 }
    Text(stringResource(Res.string.account_verify_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.account_verify_instruction, humanPositions[0], humanPositions[1], humanPositions[2]),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(AccountTags.VERIFY_INSTRUCTION),
    )
    Spacer(Modifier.height(8.dp))
    humanPositions.forEachIndexed { index, position ->
        OutlinedTextField(
            value = answers.value[index],
            onValueChange = { typed -> answers.value = answers.value.toMutableList().also { it[index] = typed } },
            label = { Text(stringResource(Res.string.account_verify_word_label, position)) },
            singleLine = true,
            isError = state.verificationFailed,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag(AccountTags.VERIFY_FIELD + index),
        )
    }
    if (state.verificationFailed) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.account_verify_error),
            color = PpfTheme.palette.accentInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(AccountTags.VERIFY_ERROR),
        )
    }
    Spacer(Modifier.height(12.dp))
    var warning by remember { mutableStateOf(false) }
    KeepChoice(keep = state.keepPhrase, onKeep = { warning = true }, onUndo = { session.setCreationKeepPhrase(false) })
    if (warning) KeepPhraseDialog(onConfirm = { session.setCreationKeepPhrase(true); warning = false }, onCancel = { warning = false })
    Spacer(Modifier.height(12.dp))
    PpfPrimaryButton(onClick = { session.verifyAndCreate(answers.value) }, text = stringResource(Res.string.account_verify_submit), modifier = Modifier.fillMaxWidth().testTag(AccountTags.VERIFY_SUBMIT))
    Row {
        PpfTextButton(onClick = session::reviewPhrase, text = stringResource(Res.string.account_review_phrase))
        PpfTextButton(onClick = session::cancel, text = stringResource(Res.string.account_cancel), modifier = Modifier.testTag(AccountTags.CANCEL), color = PpfTheme.palette.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.account_verify_later_hint), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    PpfTextButton(onClick = session::postponeVerification, text = stringResource(Res.string.account_verify_later), modifier = Modifier.testTag(RecoveryTags.VERIFY_LATER), color = PpfTheme.palette.inkMuted)
}

/** The numbered words, two columns on a phone, three elsewhere. */
@Composable
internal fun PhraseGrid(phrase: RecoveryPhrase, windowClass: WindowClass) {
    val columns = if (windowClass.width == WindowWidthClass.COMPACT) 2 else 3
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            phrase.words.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (index, word) ->
                        Text(
                            text = "${index + 1}. $word",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f).testTag(AccountTags.PHRASE_WORD + index),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoringCard(session: AccountSession, state: AccountUiState.Restoring) {
    var input by rememberSaveable { mutableStateOf("") }
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.account_restore_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(Res.string.account_restore_label)) },
            minLines = 3,
            isError = state.error != null,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag(AccountTags.RESTORE_INPUT),
        )
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = restoreErrorText(error),
                color = PpfTheme.palette.accentInk,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AccountTags.RESTORE_ERROR),
            )
            if (error is RestoreError.AmbiguousLanguage) {
                Row {
                    error.candidates.forEach { language ->
                        PpfTextButton(
                            onClick = { session.restore(input, language) },
                            text = stringResource(if (language == PhraseLanguage.FRENCH) Res.string.account_language_french else Res.string.account_language_english),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PpfPrimaryButton(onClick = { session.restore(input) }, text = stringResource(Res.string.account_restore_submit), modifier = Modifier.fillMaxWidth().testTag(AccountTags.RESTORE_SUBMIT))
        PpfTextButton(onClick = session::cancel, text = stringResource(Res.string.account_cancel), modifier = Modifier.testTag(AccountTags.CANCEL), color = PpfTheme.palette.inkMuted)
    }
}

@Composable
internal fun restoreErrorText(error: RestoreError): String = when (error) {
    RestoreError.WordCount -> stringResource(Res.string.account_restore_error_word_count)
    is RestoreError.UnknownWords -> stringResource(Res.string.account_restore_error_unknown_words, error.words.joinToString(", "))
    RestoreError.Checksum -> stringResource(Res.string.account_restore_error_checksum)
    is RestoreError.AmbiguousLanguage -> stringResource(Res.string.account_restore_ambiguous)
    RestoreError.InvalidSecretKey -> stringResource(Res.string.account_restore_error_key)
}

private enum class PendingDialog { REVEAL_PHRASE, REVEAL_SECRET, SIGN_OUT }

/** The account once it exists: profile card, identity and secrets, what others can see. */
@Composable
private fun ReadyCards(
    session: AccountSession,
    state: AccountUiState.Ready,
    windowClass: WindowClass,
    mailbox: MailboxSetting?,
    notifications: NotificationsSettingsState?,
) {
    val palette = PpfTheme.palette
    var pending by remember { mutableStateOf<PendingDialog?>(null) }
    val hex = state.publicKey.hex

    session.compromise?.let { CompromisedBanner(it) }
    if (state.reminderDue && session.phraseCheck == null) PhraseReminderCard(session)

    PpfCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(seed = hex, letters = avatarLetters(hex), size = 80.dp)
            Spacer(Modifier.height(8.dp))
            Text(shortNpub(hex), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(Res.string.account_pseudonym_hint), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        }
    }

    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.account_ready_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.account_public_key_label), style = MaterialTheme.typography.labelLarge)
        SelectionContainer {
            Text(
                text = state.publicKey.npub,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag(AccountTags.PUBLIC_KEY),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.account_public_key_explanation), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(16.dp))

        when (val revealed = state.revealed) {
            is Revealed.Phrase -> {
                Text(stringResource(Res.string.account_phrase_title), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                PhraseGrid(revealed.phrase, windowClass)
                Spacer(Modifier.height(12.dp))
                PpfSecondaryButton(onClick = session::hideRevealed, text = stringResource(Res.string.account_hide), icon = Lucide.EyeOff)
            }
            is Revealed.SecretKey -> {
                Text(stringResource(Res.string.account_secret_label), style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(
                        text = revealed.nsec,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag(AccountTags.SECRET_KEY),
                    )
                }
                Spacer(Modifier.height(12.dp))
                PpfSecondaryButton(onClick = session::hideRevealed, text = stringResource(Res.string.account_hide), icon = Lucide.EyeOff)
            }
            null -> {
                PpfSecondaryButton(onClick = { pending = PendingDialog.REVEAL_SECRET }, text = stringResource(Res.string.account_show_secret), icon = Lucide.KeyRound, modifier = Modifier.fillMaxWidth().testTag(AccountTags.SHOW_SECRET))
            }
        }
    }

    // The phrase, its check and the recovery key (S-146, D-134).
    RecoveryCard(session, state, onShowPhrase = { pending = PendingDialog.REVEAL_PHRASE })

    PpfCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(Res.string.account_visibility_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        SettingRow(stringResource(Res.string.account_votes_label), divider = false) {
            Icon(Lucide.Lock, contentDescription = null, tint = palette.success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.account_votes_never), style = MaterialTheme.typography.labelLarge, color = palette.success)
        }
    }
    InfoBox(Lucide.Info) {
        Text(stringResource(Res.string.account_votes_explanation), style = MaterialTheme.typography.bodyMedium)
    }

    // A compromised account can no longer change its settings on the server: the card would only fail.
    if (mailbox != null && session.compromise == null) MailboxCard(mailbox, session)

    if (notifications != null) NotificationsCard(notifications)

    PpfTextButton(onClick = session.lostControl::begin, text = stringResource(Res.string.lost_open), modifier = Modifier.testTag(LostControlTags.OPEN), color = palette.inkMuted)
    PpfTextButton(onClick = { pending = PendingDialog.SIGN_OUT }, text = stringResource(Res.string.account_sign_out), modifier = Modifier.testTag(AccountTags.SIGN_OUT), color = palette.accentInk)

    pending?.let { dialog ->
        val (title, text, confirmLabel) = when (dialog) {
            PendingDialog.REVEAL_PHRASE -> Triple(Res.string.account_reveal_warning_title, Res.string.account_reveal_phrase_warning, Res.string.account_reveal_confirm)
            PendingDialog.REVEAL_SECRET -> Triple(Res.string.account_reveal_warning_title, Res.string.account_reveal_secret_warning, Res.string.account_reveal_confirm)
            PendingDialog.SIGN_OUT -> Triple(Res.string.account_sign_out, Res.string.account_sign_out_warning, Res.string.account_sign_out_confirm)
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
            title = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(text), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (dialog) {
                            PendingDialog.REVEAL_PHRASE -> session.revealPhrase()
                            PendingDialog.REVEAL_SECRET -> session.revealSecretKey()
                            PendingDialog.SIGN_OUT -> session.signOut()
                        }
                        pending = null
                    },
                    modifier = Modifier.testTag(AccountTags.DIALOG_CONFIRM),
                ) {
                    Text(stringResource(confirmLabel), color = if (dialog == PendingDialog.SIGN_OUT) palette.accentInk else palette.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(Res.string.account_cancel), color = palette.inkMuted) }
            },
        )
    }
}
