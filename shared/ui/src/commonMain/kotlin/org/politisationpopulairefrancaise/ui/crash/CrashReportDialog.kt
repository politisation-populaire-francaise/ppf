package org.politisationpopulairefrancaise.ui.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.crash_close
import org.politisationpopulairefrancaise.ui.resources.crash_copied
import org.politisationpopulairefrancaise.ui.resources.crash_copy
import org.politisationpopulairefrancaise.ui.resources.crash_delete
import org.politisationpopulairefrancaise.ui.resources.crash_share
import org.politisationpopulairefrancaise.ui.resources.crash_share_title
import org.politisationpopulairefrancaise.ui.resources.crash_text
import org.politisationpopulairefrancaise.ui.resources.crash_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * A crash report waiting on the device (S-101): the host reads it at launch and hands it over with what
 * erasing it means. Nothing is ever sent by the application.
 */
class PendingCrashReport(val text: String, val delete: () -> Unit)

/** Test tags of [CrashReportDialog]. */
object CrashReportTags {
    const val DIALOG = "crash-report-dialog"
    const val TEXT = "crash-report-text"
    const val COPY = "crash-report-copy"
    const val SHARE = "crash-report-share"
    const val DELETE = "crash-report-delete"
    const val CLOSE = "crash-report-close"
}

/** Shares a text through the system (share sheet); null where the platform has none (the report is then copied). */
@Composable
internal expect fun rememberTextSharer(title: String): ((String) -> Unit)?

/**
 * Offered at the launch that follows a crash: the whole report, readable and selectable, then the
 * person's choice. "Copier" and "Partager" let the person send it where they want (a message to the
 * team, a ticket later, M30); "Effacer" erases it at once; "Plus tard" keeps it for the next launch,
 * until it expires on its own after a week.
 */
@Composable
fun CrashReportDialog(report: PendingCrashReport, onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    // The suspending LocalClipboard needs a ClipEntry built by platform code; plain text is enough here.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val share = rememberTextSharer(stringResource(Res.string.crash_share_title))
    var copied by remember(report) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(CrashReportTags.DIALOG),
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.crash_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.crash_text), style = MaterialTheme.typography.bodyLarge)
                SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 240.dp).testTag(CrashReportTags.TEXT)) {
                    Text(
                        report.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = palette.ink,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report.text))
                        copied = true
                    },
                    text = stringResource(if (copied) Res.string.crash_copied else Res.string.crash_copy),
                    icon = if (copied) Lucide.Check else Lucide.Copy,
                    modifier = Modifier.fillMaxWidth().testTag(CrashReportTags.COPY),
                )
                if (share != null) {
                    PpfSecondaryButton(
                        onClick = { share(report.text) },
                        text = stringResource(Res.string.crash_share),
                        icon = Lucide.Share2,
                        modifier = Modifier.fillMaxWidth().testTag(CrashReportTags.SHARE),
                    )
                }
                PpfSecondaryButton(
                    onClick = {
                        report.delete()
                        onDismiss()
                    },
                    text = stringResource(Res.string.crash_delete),
                    icon = Lucide.X,
                    modifier = Modifier.fillMaxWidth().testTag(CrashReportTags.DELETE),
                )
                PpfTextButton(onClick = onDismiss, text = stringResource(Res.string.crash_close), modifier = Modifier.testTag(CrashReportTags.CLOSE))
            }
        },
    )
}
