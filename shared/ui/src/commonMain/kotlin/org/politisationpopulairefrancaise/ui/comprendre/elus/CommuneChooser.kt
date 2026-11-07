package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.elus.ElusPackageEntry
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.elus_back
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_cancel
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_continue
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_datasaver_confirm
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_datasaver_text
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_datasaver_title
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_download
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_download_size
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_download_text
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_downloading
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_loading
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_pick
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_postal_code
import org.politisationpopulairefrancaise.ui.resources.elus_chooser_title
import org.politisationpopulairefrancaise.ui.resources.elus_error_no_commune
import org.politisationpopulairefrancaise.ui.resources.elus_error_postal_code
import org.politisationpopulairefrancaise.ui.resources.elus_error_refused
import org.politisationpopulairefrancaise.ui.resources.elus_error_unknown
import org.politisationpopulairefrancaise.ui.resources.elus_error_unreachable
import org.politisationpopulairefrancaise.ui.resources.elus_error_unreadable
import org.politisationpopulairefrancaise.ui.resources.elus_privacy
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The choice of the commune (contract, 1.6): the postal code, then the size of the list of the
 * department before anything is downloaded (and a second confirmation in data saving mode), then
 * the commune when the postal code covers several. The privacy sentence stays visible throughout.
 * Escape, a tap outside and the system's back close it; nothing half-done is kept.
 */
@Composable
fun CommuneChooserDialog(elus: ElusLibrary) {
    val step = elus.chooser ?: return
    val palette = PpfTheme.palette
    Dialog(onDismissRequest = elus::cancelChoosing, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().testTag(ElusTags.CHOOSER),
                shape = MaterialTheme.shapes.medium,
                color = palette.surface2,
                contentColor = palette.ink,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.MapPin, contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.elus_chooser_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Spacer(Modifier.height(12.dp))
                    when (step) {
                        is ChooserStep.PostalCode -> PostalCodeStep(elus, step)
                        is ChooserStep.Download -> DownloadStep(elus, step)
                        is ChooserStep.PickCommune -> PickCommuneStep(elus, step)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(Res.string.elus_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(ElusTags.PRIVACY))
                }
            }
        }
    }
}

@Composable
private fun PostalCodeStep(elus: ElusLibrary, step: ChooserStep.PostalCode) {
    var text by remember(step) { mutableStateOf(step.text) }
    PpfTextField(
        value = text,
        onValueChange = { value -> text = value.filter { it.isDigit() || it == ' ' }.take(6) },
        placeholder = stringResource(Res.string.elus_chooser_postal_code),
        modifier = Modifier.fillMaxWidth().testTag(ElusTags.POSTAL_CODE),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!step.working) elus.submitPostalCode(text) }),
    )
    step.error?.let { ErrorText(it, text) }
    if (step.working) Working(stringResource(Res.string.elus_chooser_loading))
    Spacer(Modifier.height(16.dp))
    Buttons(
        primary = stringResource(Res.string.elus_chooser_continue),
        onPrimary = { elus.submitPostalCode(text) },
        primaryEnabled = !step.working,
        primaryTag = ElusTags.CONTINUE,
        onCancel = elus::cancelChoosing,
    )
}

@Composable
private fun DownloadStep(elus: ElusLibrary, step: ChooserStep.Download) {
    val palette = PpfTheme.palette
    val size = fileSize(step.plan.bytes)
    Text(
        stringResource(Res.string.elus_chooser_download_text, step.plan.postalCode.orEmpty(), step.plan.departments.joinToString(", ") { departmentLabel(it) }),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(6.dp))
    Text(stringResource(Res.string.elus_chooser_download_size, size), style = MaterialTheme.typography.titleSmall, color = palette.brand)
    if (step.dataSaverWarning) {
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION, title = stringResource(Res.string.elus_chooser_datasaver_title), modifier = Modifier.testTag(ElusTags.DATA_SAVER)) {
            Text(stringResource(Res.string.elus_chooser_datasaver_text, size), style = MaterialTheme.typography.bodyMedium)
        }
    }
    step.error?.let { ErrorText(it, step.plan.postalCode.orEmpty()) }
    if (step.working) Working(stringResource(Res.string.elus_chooser_downloading))
    Spacer(Modifier.height(16.dp))
    Buttons(
        primary = if (step.dataSaverWarning) stringResource(Res.string.elus_chooser_datasaver_confirm) else stringResource(Res.string.elus_chooser_download, size),
        onPrimary = elus::download,
        primaryEnabled = !step.working,
        primaryTag = ElusTags.DOWNLOAD,
        onCancel = elus::cancelChoosing,
        onBack = elus::backToPostalCode,
    )
}

@Composable
private fun PickCommuneStep(elus: ElusLibrary, step: ChooserStep.PickCommune) {
    Text(stringResource(Res.string.elus_chooser_pick, step.postalCode), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        step.choices.forEach { choice ->
            PpfCard(onClick = { elus.pick(choice) }, modifier = Modifier.fillMaxWidth().testTag(ElusTags.commune(choice.commune.code))) {
                Text(choice.commune.name, style = MaterialTheme.typography.titleSmall)
                Text("${choice.department.name} (${choice.department.code})", style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkMuted)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Buttons(primary = null, onPrimary = {}, primaryEnabled = false, primaryTag = "", onCancel = elus::cancelChoosing, onBack = elus::backToPostalCode)
}

private fun departmentLabel(entry: ElusPackageEntry): String = "${entry.name} (${entry.code})"

@Composable
private fun ErrorText(error: ChooserError, postalCode: String) {
    val text = when (error) {
        ChooserError.POSTAL_CODE -> stringResource(Res.string.elus_error_postal_code)
        ChooserError.UNKNOWN_POSTAL_CODE -> stringResource(Res.string.elus_error_unknown, postalCode.trim())
        ChooserError.UNREACHABLE -> stringResource(Res.string.elus_error_unreachable)
        ChooserError.REFUSED -> stringResource(Res.string.elus_error_refused)
        ChooserError.UNREADABLE -> stringResource(Res.string.elus_error_unreadable)
        ChooserError.NO_COMMUNE -> stringResource(Res.string.elus_error_no_commune, postalCode.trim())
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = PpfTheme.palette.accentInk,
        modifier = Modifier.testTag(ElusTags.CHOOSER_ERROR).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Working(label: String) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = PpfTheme.palette.brand, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
private fun Buttons(primary: String?, onPrimary: () -> Unit, primaryEnabled: Boolean, primaryTag: String, onCancel: () -> Unit, onBack: (() -> Unit)? = null) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (primary != null) PpfPrimaryButton(onClick = onPrimary, text = primary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth().testTag(primaryTag))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onBack != null) PpfTextButton(onClick = onBack, text = stringResource(Res.string.elus_back), color = palette.inkMuted, modifier = Modifier.testTag(ElusTags.CHOOSER_BACK))
            PpfTextButton(onClick = onCancel, text = stringResource(Res.string.elus_chooser_cancel), color = palette.inkMuted, modifier = Modifier.testTag(ElusTags.CANCEL))
        }
    }
}
