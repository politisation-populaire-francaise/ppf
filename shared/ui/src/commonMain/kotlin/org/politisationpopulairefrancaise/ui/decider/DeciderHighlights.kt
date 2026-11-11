package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_highlights_title
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_demands
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_demands_count
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_demands_none
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_pacte
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_pacte_measures
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_pacte_none
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_petitions
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_petitions_count
import org.politisationpopulairefrancaise.ui.resources.decider_highlight_petitions_none
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The three flagship tools of "Décider" (MVP 1, jalon 3): demands, official petitions, Pacte démocratique. */
enum class DeciderHighlight(val id: String) {
    REVENDICATIONS("revendications"),
    PETITIONS("petitions"),
    PACTE("pacte"),
}

object DeciderHighlightTags {
    const val BLOCK = "decider-highlights"
    fun item(highlight: DeciderHighlight) = "decider-highlight-" + highlight.id
}

/** What the three cards say: the counts come from the contents on the device, nothing from a server. */
data class DeciderHighlightFigures(val demands: Int, val openPetitions: Int, val pacteMeasures: Int?) {
    companion object {
        fun of(demands: Int, catalog: DeciderCatalog, today: CivilDate) = DeciderHighlightFigures(
            demands = demands,
            openPetitions = catalog.openPetitions(today).size,
            pacteMeasures = (catalog.currentPacte() ?: catalog.pactes.firstOrNull())?.measures?.size,
        )
    }
}

/**
 * The three flagship tools, one touch each: at the top of "Décider" and on "En direct" (MVP 1).
 * Narrow windows stack them as full-width rows, wider ones put them side by side.
 * [withTitle] adds the heading « Décider ensemble » (on "En direct", where the space is not named yet).
 */
@Composable
fun DeciderHighlights(
    figures: DeciderHighlightFigures,
    onOpen: (DeciderHighlight) -> Unit,
    modifier: Modifier = Modifier,
    withTitle: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag(DeciderHighlightTags.BLOCK)) {
        val sideBySide = maxWidth >= 600.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (withTitle) {
                Text(
                    stringResource(Res.string.decider_highlights_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            val items: List<@Composable (Modifier) -> Unit> = listOf(
                { m ->
                    HighlightCard(
                        icon = Lucide.Megaphone,
                        title = stringResource(Res.string.decider_highlight_demands),
                        detail = if (figures.demands > 0) pluralStringResource(Res.plurals.decider_highlight_demands_count, figures.demands, figures.demands) else stringResource(Res.string.decider_highlight_demands_none),
                        onClick = { onOpen(DeciderHighlight.REVENDICATIONS) },
                        modifier = m.testTag(DeciderHighlightTags.item(DeciderHighlight.REVENDICATIONS)),
                    )
                },
                { m ->
                    HighlightCard(
                        icon = Lucide.ScrollText,
                        title = stringResource(Res.string.decider_highlight_petitions),
                        detail = if (figures.openPetitions > 0) pluralStringResource(Res.plurals.decider_highlight_petitions_count, figures.openPetitions, figures.openPetitions) else stringResource(Res.string.decider_highlight_petitions_none),
                        onClick = { onOpen(DeciderHighlight.PETITIONS) },
                        modifier = m.testTag(DeciderHighlightTags.item(DeciderHighlight.PETITIONS)),
                    )
                },
                { m ->
                    val measures = figures.pacteMeasures
                    HighlightCard(
                        icon = Lucide.BadgeCheck,
                        title = stringResource(Res.string.decider_highlight_pacte),
                        detail = if (measures != null && measures > 0) pluralStringResource(Res.plurals.decider_highlight_pacte_measures, measures, measures) else stringResource(Res.string.decider_highlight_pacte_none),
                        onClick = { onOpen(DeciderHighlight.PACTE) },
                        modifier = m.testTag(DeciderHighlightTags.item(DeciderHighlight.PACTE)),
                    )
                },
            )
            if (sideBySide) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                }
            } else {
                items.forEach { it(Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun HighlightCard(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}
