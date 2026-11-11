package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.contact.CaseSender
import org.politisationpopulairefrancaise.core.contact.ContentReports
import org.politisationpopulairefrancaise.core.contact.ReportOutcome
import org.politisationpopulairefrancaise.core.contact.ReportReason
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.report_cancel
import org.politisationpopulairefrancaise.ui.resources.report_close
import org.politisationpopulairefrancaise.ui.resources.report_comment
import org.politisationpopulairefrancaise.ui.resources.report_intro
import org.politisationpopulairefrancaise.ui.resources.report_no_server
import org.politisationpopulairefrancaise.ui.resources.report_open
import org.politisationpopulairefrancaise.ui.resources.report_reason_autre
import org.politisationpopulairefrancaise.ui.resources.report_reason_donnees_personnelles
import org.politisationpopulairefrancaise.ui.resources.report_reason_haine
import org.politisationpopulairefrancaise.ui.resources.report_reason_illicite
import org.politisationpopulairefrancaise.ui.resources.report_reason_spam
import org.politisationpopulairefrancaise.ui.resources.report_reason_tromperie
import org.politisationpopulairefrancaise.ui.resources.report_refused
import org.politisationpopulairefrancaise.ui.resources.report_send
import org.politisationpopulairefrancaise.ui.resources.report_sending
import org.politisationpopulairefrancaise.ui.resources.report_sent
import org.politisationpopulairefrancaise.ui.resources.report_title
import org.politisationpopulairefrancaise.ui.resources.report_unreachable
import org.politisationpopulairefrancaise.ui.resources.report_warning
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Where reports go: the server adapter, given by the shell; null without a server (the dialog says so). */
val LocalCaseSender = staticCompositionLocalOf<CaseSender?> { null }

/** Test tags of « Signaler ce contenu ». */
object ReportTags {
    const val OPEN = "report-open"
    const val DIALOG = "report-dialog"
    const val REASON = "report-reason-" // followed by the wire code of the reason
    const val COMMENT = "report-comment"
    const val SEND = "report-send"
    const val CANCEL = "report-cancel"
    const val RESULT = "report-result"
    const val CLOSE = "report-close"
}

internal fun reasonLabel(reason: ReportReason): StringResource = when (reason) {
    ReportReason.ILLEGAL -> Res.string.report_reason_illicite
    ReportReason.HATE -> Res.string.report_reason_haine
    ReportReason.PERSONAL_DATA -> Res.string.report_reason_donnees_personnelles
    ReportReason.DECEPTION -> Res.string.report_reason_tromperie
    ReportReason.SPAM -> Res.string.report_reason_spam
    ReportReason.OTHER -> Res.string.report_reason_autre
}

private sealed interface ReportStep {
    data object Choosing : ReportStep
    data object Sending : ReportStep
    data class Done(val outcome: ReportOutcome?) : ReportStep
}

/**
 * « Signaler » (S-38, D-234), to put next to any content published by a user (call, event, demand, post, room):
 * a reason among six, an optional comment, then a case of M30 sent to the inbox of the team, without an account.
 * The address of the content and the reason travel in clear, the comment encrypted. Nothing is hidden by a report:
 * a person of the team decides. [address] is the stable address of the content (`<kind>:<key>:<identifier>`).
 */
@Composable
fun ReportContentButton(address: String, modifier: Modifier = Modifier) {
    if (!ContentReports.isContentAddress(address)) return
    var open by remember { mutableStateOf(false) }
    PpfTextButton(
        onClick = { open = true },
        text = stringResource(Res.string.report_open),
        modifier = modifier.heightIn(min = 48.dp).testTag(ReportTags.OPEN),
        color = PpfTheme.palette.inkMuted,
    )
    if (open) ReportDialog(address, onDismiss = { open = false })
}

@Composable
private fun ReportDialog(address: String, onDismiss: () -> Unit) {
    val sender = LocalCaseSender.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var comment by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<ReportStep>(ReportStep.Choosing) }
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = { if (step != ReportStep.Sending) onDismiss() },
        modifier = Modifier.testTag(ReportTags.DIALOG),
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val current = step) {
                    is ReportStep.Done -> Text(
                        stringResource(
                            when (val outcome = current.outcome) {
                                is ReportOutcome.Sent -> Res.string.report_sent
                                ReportOutcome.Unreachable -> Res.string.report_unreachable
                                is ReportOutcome.Refused -> Res.string.report_refused
                                null -> Res.string.report_no_server
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(ReportTags.RESULT).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    else -> {
                        Text(stringResource(Res.string.report_intro), style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.selectableGroup()) {
                            ReportReason.entries.forEach { candidate ->
                                PpfRadioRow(
                                    label = stringResource(reasonLabel(candidate)),
                                    selected = reason == candidate,
                                    onClick = { reason = candidate },
                                    modifier = Modifier.testTag(ReportTags.REASON + candidate.wire),
                                    enabled = step == ReportStep.Choosing,
                                )
                            }
                        }
                        PpfTextField(
                            value = comment,
                            onValueChange = { comment = it.take(ContentReports.MAX_COMMENT) },
                            placeholder = stringResource(Res.string.report_comment),
                            modifier = Modifier.fillMaxWidth().testTag(ReportTags.COMMENT),
                            minLines = 2,
                        )
                        Text(stringResource(Res.string.report_warning), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                is ReportStep.Done -> PpfPrimaryButton(onClick = onDismiss, text = stringResource(Res.string.report_close), modifier = Modifier.testTag(ReportTags.CLOSE))
                else -> PpfPrimaryButton(
                    onClick = {
                        val chosen = reason ?: return@PpfPrimaryButton
                        if (sender == null) {
                            step = ReportStep.Done(null)
                            return@PpfPrimaryButton
                        }
                        step = ReportStep.Sending
                        scope.launch {
                            val outcome = try {
                                // The proof of work of the case takes a moment: off the main thread.
                                val prepared = withContext(Dispatchers.Default) { ContentReports.prepare(address, chosen, comment) }
                                sender.createCase(prepared.creation)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ReportOutcome.Unreachable
                            }
                            step = ReportStep.Done(outcome)
                        }
                    },
                    text = stringResource(if (step == ReportStep.Sending) Res.string.report_sending else Res.string.report_send),
                    enabled = reason != null && step == ReportStep.Choosing,
                    modifier = Modifier.testTag(ReportTags.SEND),
                )
            }
        },
        dismissButton = {
            if (step == ReportStep.Choosing) {
                PpfTextButton(onClick = onDismiss, text = stringResource(Res.string.report_cancel), modifier = Modifier.testTag(ReportTags.CANCEL))
            }
        },
    )
}
