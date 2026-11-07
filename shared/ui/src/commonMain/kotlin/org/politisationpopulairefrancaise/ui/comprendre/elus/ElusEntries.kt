package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_elus_local_pending
import org.politisationpopulairefrancaise.ui.resources.elus_box_all
import org.politisationpopulairefrancaise.ui.resources.elus_box_directories
import org.politisationpopulairefrancaise.ui.resources.elus_box_for
import org.politisationpopulairefrancaise.ui.resources.elus_box_pick
import org.politisationpopulairefrancaise.ui.resources.elus_change
import org.politisationpopulairefrancaise.ui.resources.elus_choose
import org.politisationpopulairefrancaise.ui.resources.elus_home_commune
import org.politisationpopulairefrancaise.ui.resources.elus_home_intro
import org.politisationpopulairefrancaise.ui.resources.elus_home_none
import org.politisationpopulairefrancaise.ui.resources.elus_home_offline
import org.politisationpopulairefrancaise.ui.resources.elus_home_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The entry « Mes élus » of the home of "Comprendre": the chosen commune, or what to do to see one's officials. */
@Composable
fun MesElusCard(elus: ElusLibrary, onOpen: () -> Unit, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    LaunchedEffect(elus) { elus.load() }
    val mine = elus.mine
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth().testTag(ElusTags.HOME_ENTRY)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.elus_home_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            if (mine?.example == true) ExampleBadge()
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.elus_home_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(6.dp))
        val line = when {
            mine != null -> stringResource(Res.string.elus_home_commune, mine.officials.commune.name)
            elus.hasServer -> stringResource(Res.string.elus_home_none)
            else -> stringResource(Res.string.elus_home_offline)
        }
        Text(line, style = MaterialTheme.typography.labelLarge, color = palette.brand)
    }
}

/**
 * The end of the box « Qui décide ? Les élus concernés »: the commune whose officials are named,
 * or the invitation to indicate one (a server is configured), or, without server, the sentence
 * that waits for the directory and the way to the official directories.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElusBoxFooter(elus: ElusLibrary?, hasLocalLevel: Boolean, onOpenPage: (() -> Unit)?) {
    val palette = PpfTheme.palette
    val mine = elus?.mine
    Column(Modifier.fillMaxWidth()) {
        when {
            elus != null && mine != null -> {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.elus_box_for, mine.officials.commune.name), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onOpenPage != null) PpfTextButton(onClick = onOpenPage, text = stringResource(Res.string.elus_box_all), modifier = Modifier.testTag(ElusTags.BOX_ALL))
                    PpfTextButton(onClick = elus::startChoosing, text = stringResource(Res.string.elus_change), color = palette.inkMuted, modifier = Modifier.testTag(ElusTags.CHANGE))
                }
            }
            elus != null && elus.hasServer -> {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.elus_box_pick), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(onClick = elus::startChoosing, text = stringResource(Res.string.elus_choose), icon = Lucide.MapPin, small = true, modifier = Modifier.testTag(ElusTags.BOX_CHOOSE))
            }
            hasLocalLevel -> {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.comprendre_elus_local_pending), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                if (onOpenPage != null) {
                    PpfTextButton(onClick = onOpenPage, text = stringResource(Res.string.elus_box_directories), modifier = Modifier.testTag(ElusTags.BOX_DIRECTORIES))
                }
            }
        }
    }
}
