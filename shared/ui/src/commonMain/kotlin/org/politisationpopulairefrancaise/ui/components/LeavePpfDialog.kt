package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The test tags of one use of [LeavePpfDialog]: the dialog, "stay" and "open". */
data class LeaveDialogTags(val dialog: String, val stay: String, val open: String)

/** The host of a link, to tell the reader where it leads ("www.senat.fr"); the start of the link when it cannot be read. */
fun leavingHost(url: String): String = CallRules.hostOf(url) ?: url.take(80)

/**
 * "Vous quittez PPF" (AP-08, point of attention 12), shared by every space that links outside: the
 * text names the host of the page, the safe choice comes first and most visible ("Rester sur PPF"),
 * then the one that opens the page. Only full https addresses are ever opened; [note] adds a word
 * of caution (fake fundraisings, for example).
 */
@Composable
fun LeavePpfDialog(
    url: String,
    title: String,
    text: String,
    stayLabel: String,
    openLabel: String,
    onStay: () -> Unit,
    onDone: () -> Unit,
    tags: LeaveDialogTags,
    note: String? = null,
) {
    val palette = PpfTheme.palette
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(tags.dialog),
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (note != null) Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = onStay, text = stayLabel, modifier = Modifier.fillMaxWidth().testTag(tags.stay))
                PpfTextButton(
                    onClick = {
                        if (CallRules.isValidLink(url)) runCatching { uriHandler.openUri(url.trim()) }
                        onDone()
                    },
                    text = openLabel,
                    modifier = Modifier.testTag(tags.open),
                )
            }
        },
    )
}
