package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.ComparatorCell
import org.politisationpopulairefrancaise.core.decider.ComparatorGrid
import org.politisationpopulairefrancaise.core.decider.ComparatorRow
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_aside
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_card_line
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_method
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_method_title
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_none
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_quote
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_sheet
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_source
import org.politisationpopulairefrancaise.ui.resources.decider_comparator_themes
import org.politisationpopulairefrancaise.ui.revendications.themeLabel
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** Test tags of the comparator (DE-16). */
object ComparatorTags {
    const val PAGE = "decider-comparator"
    const val METHOD = "decider-comparator-method"
    const val ASIDE = "decider-comparator-aside"
    fun theme(id: String) = "decider-comparator-theme-$id"
    fun row(candidate: String) = "decider-comparator-row-$candidate"
    fun cell(candidate: String, theme: String) = "decider-comparator-cell-$candidate-$theme"
}

/** The card of the home: the comparator, how many candidates and themes, nothing about anyone. */
@Composable
internal fun ComparatorCard(grid: ComparatorGrid, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(grid.sheet.article.slug))) {
        Text(grid.sheet.article.title.withoutExample(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.decider_comparator_card_line, grid.rows.size, grid.themes.size),
            style = MaterialTheme.typography.bodyMedium,
            color = PpfTheme.palette.inkMuted,
        )
    }
}

/**
 * The comparator of an election (DE-16), under the rule of identical treatment (cahier des charges,
 * 9.2): every candidate with the same themes in the same order, alphabetical order, a missing
 * position shown as missing, the candidate's own words with their source, no score, no ranking.
 *
 * On a phone, one theme at a time (every candidate under it, in the same order); on a wide window,
 * a table with one column per candidate, which scrolls sideways when they are many.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComparatorPage(grid: ComparatorGrid, wide: Boolean, onOpenSheet: (ContentAddress) -> Unit) {
    val palette = PpfTheme.palette
    var theme by rememberSaveable(grid.sheet.address.toString()) { mutableStateOf(grid.themes.first()) }
    Column(Modifier.fillMaxWidth().testTag(ComparatorTags.PAGE)) {
        PageHeader(grid.sheet.article.title.withoutExample(), grid.sheet.election.label)
        Spacer(Modifier.height(12.dp))
        InfoBox(Lucide.ListChecks, Modifier.testTag(ComparatorTags.METHOD)) {
            Text(stringResource(Res.string.decider_comparator_method_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.decider_comparator_method), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        if (wide) {
            ComparatorTable(grid, onOpenSheet)
        } else {
            SectionHeader(stringResource(Res.string.decider_comparator_themes))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grid.themes.forEach { id ->
                    PpfSelectableChip(
                        label = themeLabel(id),
                        selected = id == theme,
                        onClick = { theme = id },
                        modifier = Modifier.testTag(ComparatorTags.theme(id)),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(themeLabel(theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            grid.column(theme).forEachIndexed { index, (row, cell) ->
                if (index > 0) HorizontalDivider(color = palette.border)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(ComparatorTags.row(row.candidate.identifier))) {
                    Text(row.name.withoutExample(), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    PositionText(row, cell)
                    PpfTextButton(onClick = { onOpenSheet(row.candidate) }, text = stringResource(Res.string.decider_comparator_sheet))
                }
            }
        }
        if (grid.aside.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.decider_comparator_aside, grid.aside.size),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
                modifier = Modifier.testTag(ComparatorTags.ASIDE),
            )
        }
    }
}

/** One column per candidate, one line per theme: the same cells for all, sideways scrolling when needed. */
@Composable
private fun ComparatorTable(grid: ComparatorGrid, onOpenSheet: (ContentAddress) -> Unit) {
    val palette = PpfTheme.palette
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(160.dp)) {
            Spacer(Modifier.height(56.dp))
            grid.themes.forEach { id ->
                HorizontalDivider(color = palette.border)
                Text(themeLabel(id), style = MaterialTheme.typography.labelLarge, modifier = Modifier.height(CELL_HEIGHT).padding(vertical = 8.dp).semantics { heading() })
            }
        }
        grid.rows.forEach { row ->
            Column(Modifier.width(280.dp).padding(start = 12.dp).testTag(ComparatorTags.row(row.candidate.identifier))) {
                Column(Modifier.height(56.dp)) {
                    Text(row.name.withoutExample(), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    PpfTextButton(onClick = { onOpenSheet(row.candidate) }, text = stringResource(Res.string.decider_comparator_sheet))
                }
                row.cells.forEach { cell ->
                    HorizontalDivider(color = palette.border)
                    Column(Modifier.height(CELL_HEIGHT).padding(vertical = 8.dp)) { PositionText(row, cell) }
                }
            }
        }
    }
}

/** The summary, the candidate's own words and their source; « none published » otherwise, never filled in. */
@Composable
private fun PositionText(row: ComparatorRow, cell: ComparatorCell) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    Column(Modifier.widthIn(max = 720.dp).testTag(ComparatorTags.cell(row.candidate.identifier, cell.theme))) {
        val position = cell.position
        if (position == null) {
            Text(stringResource(Res.string.decider_comparator_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            return@Column
        }
        Text(position.summary.withoutExample(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.decider_comparator_quote, position.quote.text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        PpfTextButton(
            onClick = { open(position.quote.url) },
            text = stringResource(Res.string.decider_comparator_source, position.quote.date.toFrenchShort()),
        )
    }
}

private val CELL_HEIGHT = 200.dp
