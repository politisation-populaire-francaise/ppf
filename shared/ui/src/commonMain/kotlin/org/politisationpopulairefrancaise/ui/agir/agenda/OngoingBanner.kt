package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.OngoingMobilizations
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_now_blockades
import org.politisationpopulairefrancaise.ui.resources.agenda_now_count
import org.politisationpopulairefrancaise.ui.resources.agenda_now_more
import org.politisationpopulairefrancaise.ui.resources.agenda_now_open
import org.politisationpopulairefrancaise.ui.resources.agenda_now_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the banner « En ce moment ». */
object OngoingTags {
    const val BANNER = "agenda-now"
    const val SUMMARY = "agenda-now-summary"
    const val ITEM = "agenda-now-item-" // followed by the identifier of the mobilization
}

/** How many ongoing mobilizations the banner names; the others are counted. */
private const val SHOWN = 3

/**
 * « En ce moment » (D-197): above the list and the map of the agenda, the mobilizations happening now, from the
 * dates of the agenda, those that end soonest first, each opening its page. The blockades counted are those
 * announced by their organisers (D-190: never the position of the forces of order). Nothing when nothing happens.
 */
@Composable
internal fun OngoingBanner(ongoing: OngoingMobilizations, onOpen: (Mobilization) -> Unit, modifier: Modifier = Modifier) {
    if (ongoing.isEmpty) return
    val palette = PpfTheme.palette
    PpfCard(modifier.fillMaxWidth().testTag(OngoingTags.BANNER), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A red dot: something is happening now (the text says it, the dot is a hint).
            Box(Modifier.size(10.dp).background(palette.accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.agenda_now_title), style = MaterialTheme.typography.titleMedium, color = palette.accentInk, modifier = Modifier.semantics { heading() })
        }
        val summary = buildString {
            append(pluralStringResource(Res.plurals.agenda_now_count, ongoing.count, ongoing.count))
            if (ongoing.blockades > 0) append(", ").append(pluralStringResource(Res.plurals.agenda_now_blockades, ongoing.blockades, ongoing.blockades))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp).testTag(OngoingTags.SUMMARY))
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ongoing.all.take(SHOWN).forEach { mobilization ->
                val label = stringResource(Res.string.agenda_now_open, mobilization.displayTitle)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(onClickLabel = label) { onOpen(mobilization) }
                        .testTag(OngoingTags.ITEM + mobilization.identifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeMark(mobilization)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mobilization.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${mobilization.location} · ${mobilization.territory}", style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                }
            }
            val more = ongoing.count - SHOWN
            if (more > 0) Text(pluralStringResource(Res.plurals.agenda_now_more, more, more), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}
