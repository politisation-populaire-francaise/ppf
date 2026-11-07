package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.ErrorReport
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirRequests
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The discreet link at the end of every page of "Voir". */
@Composable
internal fun ReportErrorLink(onClick: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    PpfTextButton(
        onClick = onClick,
        text = stringResource(Res.string.voir_req_report_link),
        color = PpfTheme.palette.inkMuted,
        modifier = Modifier.testTag(VoirRequestTags.REPORT_LINK),
    )
}

/**
 * The form to report an error (VO-22): what is wrong, the correction proposed, a source. Sent
 * anonymously by default, with a key used for this report only (protection by default: the team
 * then cannot answer); sending it with the account is the user's choice, after a warning.
 */
@Composable
internal fun ErrorReportPage(item: VoirItem, session: VoirRequestSession, callbacks: RequestCallbacks) {
    val palette = PpfTheme.palette
    remember(item.address) { session.startReport(item.address) }
    var outcome by remember { mutableStateOf<RequestOutcome?>(null) }
    var sentAnonymously by remember { mutableStateOf(true) }
    var tried by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf(false) }
    val draft = session.reportDraft
    val withAccount = draft.withAccount && session.hasAccount && session.canWriteMessages
    val subject = stringResource(Res.string.voir_req_report_subject, item.title.withoutExample())

    Column(Modifier.fillMaxWidth().testTag(VoirRequestTags.REPORT_FORM)) {
        Text(stringResource(Res.string.voir_req_report_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.voir_req_content, item.title.withoutExample()), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))

        val done = outcome
        if (done != null && done != RequestOutcome.FAILED) {
            val sent = done == RequestOutcome.SENT
            OutcomeBox(
                title = if (sent) Res.string.voir_req_report_sent_title else Res.string.voir_req_pending_title,
                text = when {
                    sent && sentAnonymously -> Res.string.voir_req_report_sent_anonymous
                    sent -> Res.string.voir_req_report_sent_account
                    sentAnonymously -> Res.string.voir_req_report_pending_anonymous
                    else -> Res.string.voir_req_report_pending_account
                },
                pending = !sent,
                callbacks = callbacks,
                showMessages = !sentAnonymously,
            )
            return@Column
        }

        Text(stringResource(Res.string.voir_req_report_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(Res.string.voir_req_report_wrong))
        PpfTextField(
            value = draft.wrong,
            onValueChange = { session.reportDraft = draft.copy(wrong = it) },
            placeholder = stringResource(Res.string.voir_req_report_wrong),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(VoirRequestTags.REPORT_WRONG),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(Res.string.voir_req_report_correction))
        PpfTextField(
            value = draft.correction,
            onValueChange = { session.reportDraft = draft.copy(correction = it) },
            placeholder = stringResource(Res.string.voir_req_report_correction),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(VoirRequestTags.REPORT_CORRECTION),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(Res.string.voir_req_report_source))
        PpfTextField(
            value = draft.source,
            onValueChange = { session.reportDraft = draft.copy(source = it.take(VoirRequests.MAX_SOURCE_CHARS + 20)) },
            placeholder = "https://",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(VoirRequestTags.REPORT_SOURCE),
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(Res.string.voir_req_report_how))
        if (session.hasAccount && session.canWriteMessages) {
            Column(Modifier.selectableGroup()) {
                PpfRadioRow(
                    label = stringResource(Res.string.voir_req_report_anonymous),
                    supporting = stringResource(Res.string.voir_req_report_anonymous_hint),
                    selected = !withAccount,
                    onClick = { session.reportDraft = draft.copy(withAccount = false) },
                    modifier = Modifier.testTag(VoirRequestTags.REPORT_ANONYMOUS),
                )
                PpfRadioRow(
                    label = stringResource(Res.string.voir_req_report_account),
                    supporting = stringResource(Res.string.voir_req_report_account_hint),
                    selected = withAccount,
                    // Leaving anonymity is a loosening: a warning first, with confirm and cancel (principle 10).
                    onClick = { if (!withAccount) warning = true },
                    modifier = Modifier.testTag(VoirRequestTags.REPORT_ACCOUNT),
                )
            }
        } else {
            InfoBox(Lucide.EyeOff, Modifier.testTag(VoirRequestTags.NO_ACCOUNT)) {
                Text(stringResource(Res.string.voir_req_report_no_account), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.voir_req_encrypted), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)

        val request = ErrorReport(
            content = item.address.toString(),
            contentTitle = item.title,
            wrong = draft.wrong.trim(),
            correction = draft.correction.trim().takeIf { it.isNotEmpty() },
            source = draft.source.trim().takeIf { it.isNotEmpty() },
            anonymous = !withAccount,
        )
        val problems = remember(request) { request.problems() }
        if (tried && problems.isNotEmpty()) ProblemsBox(problems)
        if (done == RequestOutcome.FAILED) FailedBox()
        Spacer(Modifier.height(16.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            PpfPrimaryButton(
                onClick = {
                    tried = true
                    if (problems.isEmpty()) {
                        sentAnonymously = request.anonymous
                        session.sendReport(request, subject) { outcome = it }
                    }
                },
                text = stringResource(if (session.sending) Res.string.voir_req_sending else Res.string.voir_req_report_send),
                enabled = !session.sending,
                icon = Lucide.Send,
                modifier = Modifier.testTag(VoirRequestTags.REPORT_SEND),
            )
            PpfTextButton(onClick = callbacks.back, text = stringResource(Res.string.voir_req_cancel))
        }
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            modifier = Modifier.testTag(VoirRequestTags.REPORT_WARNING),
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.voir_req_report_warning_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(Res.string.voir_req_report_warning_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                PpfTextButton(
                    onClick = {
                        session.reportDraft = session.reportDraft.copy(withAccount = true)
                        warning = false
                    },
                    text = stringResource(Res.string.voir_req_report_warning_confirm),
                    modifier = Modifier.testTag(VoirRequestTags.REPORT_WARNING_CONFIRM),
                )
            },
            dismissButton = {
                PpfTextButton(onClick = { warning = false }, text = stringResource(Res.string.voir_req_cancel), modifier = Modifier.testTag(VoirRequestTags.REPORT_WARNING_CANCEL))
            },
        )
    }
}
