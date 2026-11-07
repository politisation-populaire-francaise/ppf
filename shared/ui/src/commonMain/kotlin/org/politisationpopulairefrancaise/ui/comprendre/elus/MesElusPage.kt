package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.elus.MyElus
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.comprendre.levelLabel
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.elus_back
import org.politisationpopulairefrancaise.ui.resources.elus_change
import org.politisationpopulairefrancaise.ui.resources.elus_check_update
import org.politisationpopulairefrancaise.ui.resources.elus_choose
import org.politisationpopulairefrancaise.ui.resources.elus_commune_label
import org.politisationpopulairefrancaise.ui.resources.elus_commune_value
import org.politisationpopulairefrancaise.ui.resources.elus_directories_intro
import org.politisationpopulairefrancaise.ui.resources.elus_directories_no_server
import org.politisationpopulairefrancaise.ui.resources.elus_directories_unreachable
import org.politisationpopulairefrancaise.ui.resources.elus_downloaded
import org.politisationpopulairefrancaise.ui.resources.elus_example_text
import org.politisationpopulairefrancaise.ui.resources.elus_example_title
import org.politisationpopulairefrancaise.ui.resources.elus_forget
import org.politisationpopulairefrancaise.ui.resources.elus_forget_confirm
import org.politisationpopulairefrancaise.ui.resources.elus_forget_keep
import org.politisationpopulairefrancaise.ui.resources.elus_forget_text
import org.politisationpopulairefrancaise.ui.resources.elus_forget_title
import org.politisationpopulairefrancaise.ui.resources.elus_none_yet
import org.politisationpopulairefrancaise.ui.resources.elus_page_intro
import org.politisationpopulairefrancaise.ui.resources.elus_page_title
import org.politisationpopulairefrancaise.ui.resources.elus_privacy
import org.politisationpopulairefrancaise.ui.resources.elus_source_licence
import org.politisationpopulairefrancaise.ui.resources.elus_source_open
import org.politisationpopulairefrancaise.ui.resources.elus_source_title
import org.politisationpopulairefrancaise.ui.resources.elus_source_updated
import org.politisationpopulairefrancaise.ui.resources.elus_up_to_date
import org.politisationpopulairefrancaise.ui.resources.elus_update_available
import org.politisationpopulairefrancaise.ui.resources.elus_update_download
import org.politisationpopulairefrancaise.ui.resources.elus_update_failed
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * « Mes élus » (cahier fonctionnel, M13.1; D-047): every elected official of the chosen commune,
 * level by level, the source and its date, a banner while the data are fictitious; without server
 * (or when it does not answer), the official directories, each behind « Vous quittez PPF ».
 */
@Composable
fun MesElusPage(elus: ElusLibrary, onBack: (() -> Unit)?, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    var leaving by remember { mutableStateOf<String?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    LaunchedEffect(elus) { elus.load() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ElusTags.PAGE)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.elus_back), onBack, Modifier.testTag(ElusTags.PAGE_BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            PageHeader(stringResource(Res.string.elus_page_title), stringResource(Res.string.elus_page_intro))
            Spacer(Modifier.height(16.dp))
            val mine = elus.mine
            when {
                !elus.hasServer -> {
                    OfficialDirectoriesSection(stringResource(Res.string.elus_directories_no_server), onOpen = { leaving = it })
                }
                !elus.loaded -> CircularProgressIndicator(Modifier.size(24.dp), color = palette.brand)
                mine == null -> {
                    InfoBox(Lucide.MapPin) {
                        Text(stringResource(Res.string.elus_none_yet), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        PpfPrimaryButton(onClick = elus::startChoosing, text = stringResource(Res.string.elus_choose), icon = Lucide.MapPin, modifier = Modifier.testTag(ElusTags.CHOOSE))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.elus_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    Spacer(Modifier.height(24.dp))
                    OfficialDirectoriesSection(
                        stringResource(if (elus.unreachable) Res.string.elus_directories_unreachable else Res.string.elus_directories_intro),
                        onOpen = { leaving = it },
                    )
                }
                else -> {
                    Officials(elus, mine, onForget = { confirmForget = true }, onOpenLink = { leaving = it })
                    Spacer(Modifier.height(24.dp))
                    OfficialDirectoriesSection(
                        stringResource(if (elus.unreachable) Res.string.elus_directories_unreachable else Res.string.elus_directories_intro),
                        onOpen = { leaving = it },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    leaving?.let { url -> ElusLeaveDialog(url, onStay = { leaving = null }, onDone = { leaving = null }) }
    if (confirmForget) ForgetDialog(onKeep = { confirmForget = false }, onForget = { confirmForget = false; elus.forget() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Officials(elus: ElusLibrary, mine: MyElus, onForget: () -> Unit, onOpenLink: (String) -> Unit) {
    val palette = PpfTheme.palette
    val officials = mine.officials
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(ElusTags.COMMUNE)) {
        Text(stringResource(Res.string.elus_commune_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.MapPin, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(Res.string.elus_commune_value, officials.commune.name, officials.department.name, officials.department.code),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PpfSecondaryButton(onClick = elus::startChoosing, text = stringResource(Res.string.elus_change), small = true, modifier = Modifier.testTag(ElusTags.CHANGE))
            PpfTextButton(onClick = onForget, text = stringResource(Res.string.elus_forget), color = palette.accentInk, modifier = Modifier.testTag(ElusTags.FORGET))
        }
    }
    if (mine.example) {
        Spacer(Modifier.height(12.dp))
        Callout(CalloutType.ATTENTION, title = stringResource(Res.string.elus_example_title), modifier = Modifier.testTag(ElusTags.EXAMPLE_BANNER)) {
            Text(stringResource(Res.string.elus_example_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
    DecisionLevel.entries.forEach { level ->
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().testTag(ElusTags.level(level))) {
            Text(levelLabel(level), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            LevelOfficialsBlock(officials.level(level), officials)
        }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = palette.border)
    }
    Spacer(Modifier.height(20.dp))
    Source(elus, mine, onOpenLink)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(Res.string.elus_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(ElusTags.PRIVACY))
}

@Composable
private fun Source(elus: ElusLibrary, mine: MyElus, onOpenLink: (String) -> Unit) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(ElusTags.SOURCE)) {
        Text(stringResource(Res.string.elus_source_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        mine.source?.let { source ->
            Text(source.name, style = MaterialTheme.typography.bodyMedium)
            source.licence?.let { Text(stringResource(Res.string.elus_source_licence, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        }
        (mine.officials.sourceUpdatedOn ?: mine.source?.updatedOn)?.let {
            Text(stringResource(Res.string.elus_source_updated, it.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Text(stringResource(Res.string.elus_downloaded, CivilDate.fromEpochSeconds(mine.downloadedAt).toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        mine.source?.url?.let { url ->
            PpfTextButton(onClick = { onOpenLink(url) }, text = stringResource(Res.string.elus_source_open), modifier = Modifier.testTag(ElusTags.SOURCE_OPEN))
        }
        UpdateLine(elus)
    }
}

@Composable
private fun UpdateLine(elus: ElusLibrary) {
    val palette = PpfTheme.palette
    when (elus.updateState) {
        UpdateState.IDLE -> PpfTextButton(onClick = elus::checkUpdate, text = stringResource(Res.string.elus_check_update), modifier = Modifier.testTag(ElusTags.CHECK_UPDATE))
        UpdateState.CHECKING, UpdateState.DOWNLOADING -> CircularProgressIndicator(Modifier.padding(vertical = 8.dp).size(20.dp), color = palette.brand, strokeWidth = 2.dp)
        UpdateState.UP_TO_DATE -> Text(stringResource(Res.string.elus_up_to_date), style = MaterialTheme.typography.bodySmall, color = palette.success, modifier = Modifier.testTag(ElusTags.UPDATE_STATE))
        UpdateState.FAILED -> {
            Text(stringResource(Res.string.elus_update_failed), style = MaterialTheme.typography.bodySmall, color = palette.accentInk, modifier = Modifier.testTag(ElusTags.UPDATE_STATE))
            PpfTextButton(onClick = elus::checkUpdate, text = stringResource(Res.string.elus_check_update), modifier = Modifier.testTag(ElusTags.CHECK_UPDATE))
        }
        UpdateState.AVAILABLE -> {
            val size = fileSize(elus.update?.bytes ?: 0)
            Text(stringResource(Res.string.elus_update_available, size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(ElusTags.UPDATE_STATE))
            Spacer(Modifier.height(6.dp))
            PpfSecondaryButton(onClick = elus::downloadUpdate, text = stringResource(Res.string.elus_update_download), small = true, modifier = Modifier.testTag(ElusTags.UPDATE))
        }
    }
}

@Composable
private fun ForgetDialog(onKeep: () -> Unit, onForget: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(ElusTags.FORGET_DIALOG),
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(stringResource(Res.string.elus_forget_title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(stringResource(Res.string.elus_forget_text), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = onKeep, text = stringResource(Res.string.elus_forget_keep), modifier = Modifier.fillMaxWidth().testTag(ElusTags.FORGET_KEEP))
                PpfTextButton(onClick = onForget, text = stringResource(Res.string.elus_forget_confirm), color = palette.accentInk, modifier = Modifier.testTag(ElusTags.FORGET_CONFIRM))
            }
        },
    )
}
