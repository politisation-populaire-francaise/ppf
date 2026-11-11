package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.urne_gauge_line
import org.politisationpopulairefrancaise.ui.resources.urne_gauge_none
import org.politisationpopulairefrancaise.ui.resources.urne_gauge_votes
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object VoteGaugeTags {
    const val GAUGE = "vote-gauge"
    const val LINE = "vote-gauge-line"
}

/**
 * The citizens' results of a box at a glance (MVP 1): one bar split into « pour », « contre » and « blanc », the
 * percentages in words beside it (words before colours, WCAG 1.4.1), and the number of votes. Shown from the first
 * ballot (D-156). Before any ballot, one line says so.
 */
@Composable
fun VoteGauge(citizens: ChoiceCounts, modifier: Modifier = Modifier, height: Int = 10) {
    val palette = PpfTheme.palette
    val forCount = citizens.counts["pour"] ?: 0L
    val against = citizens.counts["contre"] ?: 0L
    val blank = citizens.counts["blanc"] ?: 0L
    val total = forCount + against + blank
    Column(modifier.fillMaxWidth().testTag(VoteGaugeTags.GAUGE)) {
        if (total == 0L) {
            Text(stringResource(Res.string.urne_gauge_none), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(VoteGaugeTags.LINE))
            return@Column
        }
        fun percent(value: Long): Int = ((value * 100.0) / total + 0.5).toInt()
        val line = stringResource(Res.string.urne_gauge_line, percent(forCount), percent(against), percent(blank))
        val votes = pluralStringResource(Res.plurals.urne_gauge_votes, total.toInt(), FrenchNumbers.format(total.toDouble()))
        Row(
            Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(palette.border)
                .clearAndSetSemantics { contentDescription = "$line, $votes" },
        ) {
            Segment(forCount, palette.success)
            Segment(against, palette.accent)
            Segment(blank, palette.inkSubtle)
        }
        Spacer(Modifier.height(4.dp))
        Text("$line · $votes", style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(VoteGaugeTags.LINE))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(value: Long, color: Color) {
    if (value <= 0L) return
    Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color))
}
