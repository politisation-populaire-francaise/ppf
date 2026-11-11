package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.Victory
import org.politisationpopulairefrancaise.core.voir.VictoryKind
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Nos victoires ». */
object VictoriesTags {
    const val OPEN = "victories-open"
    const val PAGE = "victories-page"
    const val EMPTY = "victories-empty"
    fun kind(value: String) = "victories-kind-$value"
    fun victory(slug: String) = "victories-victory-$slug"
}

/**
 * « Nos victoires » (M34, PP-03): what the movement obtained, newest first, each with its day, its kind, the
 * blocks of Voir it concerns and its sources; a filter by kind. A victory is the movement's, never PPF's alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VictoriesPage(catalog: VoirCatalog, onOpen: (ContentAddress) -> Unit, onNext: (NextTarget) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    var kind by remember { mutableStateOf<VictoryKind?>(null) }
    val shown = catalog.victories.filter { kind == null || it.kind == kind }
    Column(modifier.fillMaxWidth().widthIn(max = 860.dp).testTag(VictoriesTags.PAGE), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.victories_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.victories_intro), style = MaterialTheme.typography.bodyMedium)
        if (catalog.victories.any { it.title.startsWith(VoirCatalog.EXAMPLE_MARKER) }) {
            Callout(CalloutType.ATTENTION) { Text(stringResource(Res.string.victories_example), style = MaterialTheme.typography.bodyMedium) }
        }
        if (catalog.victories.isEmpty()) {
            Text(stringResource(Res.string.victories_empty), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(VictoriesTags.EMPTY))
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(stringResource(Res.string.victories_all), selected = kind == null, onClick = { kind = null }, modifier = Modifier.testTag(VictoriesTags.kind("all")))
            VictoryKind.entries.filter { k -> catalog.victories.any { it.kind == k } }.forEach { k ->
                PpfSelectableChip(stringResource(k.label), selected = kind == k, onClick = { kind = k }, modifier = Modifier.testTag(VictoriesTags.kind(k.tagValue)))
            }
        }
        shown.forEach { VictoryCard(it, catalog, onOpen, onNext) }
    }
}

@Composable
private fun VictoryCard(victory: Victory, catalog: VoirCatalog, onOpen: (ContentAddress) -> Unit, onNext: (NextTarget) -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(VictoriesTags.victory(victory.article.slug))) {
        Text(stringResource(Res.string.victories_meta, stringResource(victory.kind.label), victory.date.toFrench()), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        VoirTitle(victory.title, style = MaterialTheme.typography.titleMedium)
        victory.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (victory.article.body.isNotBlank()) MarkdownText(victory.article.body, glossary = false)
        // The blocks of Voir it concerns, when the device holds them.
        victory.related.mapNotNull(catalog::item).forEach { item ->
            LinkRow(item.title.withoutExample(), Lucide.ChevronRight, onClick = { onOpen(item.address) })
        }
        ArchivesSection(victory.archives)
        SourcesSection(victory.sources)
        CorrectionsSection(victory.corrections)
        if (victory.nextSteps.isNotEmpty()) NextStepsSection(victory.nextSteps, onNext = onNext)
    }
}

internal val VictoryKind.label: StringResource get() = when (this) {
    VictoryKind.COMMITMENT_KEPT -> Res.string.victories_kind_commitment
    VictoryKind.DEMAND_MET -> Res.string.victories_kind_demand
    VictoryKind.LAW_CHANGED -> Res.string.victories_kind_law
    VictoryKind.OTHER -> Res.string.victories_kind_other
}
