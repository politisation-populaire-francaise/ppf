package org.politisationpopulairefrancaise.ui.agir

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.AgirHighlights
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.ui.agir.calls.countdown
import org.politisationpopulairefrancaise.ui.agir.calls.parisHour
import org.politisationpopulairefrancaise.ui.agir.kits.goalLabel
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « En ce moment » and « Je veux… » on the home of « Agir ». */
object AgirNowTags {
    const val CARD = "agir-now"
    const val MOBILIZATION = "agir-now-mobilization-" // followed by the address
    const val CALL = "agir-now-call-" // followed by the address
    const val EMPTY = "agir-now-empty"
    const val PROPOSE = "agir-now-propose"
    const val LAUNCH = "agir-now-launch"
    const val GOAL = "agir-goal-" // followed by the id of the goal
    const val SOON_TOGGLE = "agir-soon-toggle"
    const val TERRITORY = "agir-now-territory"
    const val CHOOSE_TERRITORY = "agir-now-choose-territory"
}

/** What « En ce moment » opens: a mobilization of the agenda, a call, or the forms to propose one. */
class AgirNowActions(
    val openMobilization: (Mobilization) -> Unit,
    val openCall: (Call) -> Unit,
    val proposeEvent: () -> Unit,
    val launchCall: () -> Unit,
)

/**
 * « En ce moment »: the mobilizations under way or of the coming week and the calls due within three days, each one
 * tap away; when nothing is urgent, the two ways to start something. Read from the contents already on the device.
 */
@Composable
internal fun AgirNowCard(
    highlights: AgirHighlights,
    now: Long,
    actions: AgirNowActions,
    modifier: Modifier = Modifier,
    territory: MyTerritory? = null,
    onChooseTerritory: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val nearLabel = stringResource(Res.string.agenda_near_you)
    val near: (String) -> String? = { place -> if (territory?.isNear(place) == true) nearLabel else null }
    PpfCard(modifier.fillMaxWidth().testTag(AgirNowTags.CARD)) {
        Text(stringResource(Res.string.agir_now_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        // Near me first: the commune of « Mes élus », or the way to choose it.
        if (territory != null) {
            Text(stringResource(Res.string.agir_now_near_first, territory.commune), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(AgirNowTags.TERRITORY))
        } else if (onChooseTerritory != null) {
            org.politisationpopulairefrancaise.ui.components.PpfTextButton(onChooseTerritory, stringResource(Res.string.agir_now_choose_territory), Modifier.testTag(AgirNowTags.CHOOSE_TERRITORY))
        }
        if (highlights.isEmpty) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.agir_now_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(AgirNowTags.EMPTY))
        }
        highlights.mobilizations.forEach { mobilization ->
            Spacer(Modifier.height(10.dp))
            NowRow(
                icon = Lucide.Calendar,
                badge = if (AgirHighlights.isUnderWay(mobilization, now)) stringResource(Res.string.agir_now_under_way) else dayWord(mobilization.start, now),
                badgeStyle = if (AgirHighlights.isUnderWay(mobilization, now)) BadgeStyle.SUCCESS else BadgeStyle.BRAND,
                title = mobilization.displayTitle,
                line = listOfNotNull(parisHour(mobilization.start), mobilization.territory, near(mobilization.territory)).joinToString(" · "),
                onClick = { actions.openMobilization(mobilization) },
                modifier = Modifier.testTag(AgirNowTags.MOBILIZATION + mobilization.address),
            )
        }
        if (highlights.mobilizations.isNotEmpty() && highlights.urgentCalls.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = palette.border)
        }
        highlights.urgentCalls.forEach { call ->
            Spacer(Modifier.height(10.dp))
            NowRow(
                icon = Lucide.Megaphone,
                badge = countdown(requireNotNull(call.deadline), now),
                badgeStyle = BadgeStyle.WARNING,
                title = call.displayTitle,
                line = listOfNotNull(call.territory, near(call.territory)).joinToString(" · "),
                onClick = { actions.openCall(call) },
                modifier = Modifier.testTag(AgirNowTags.CALL + call.address),
            )
        }
        Spacer(Modifier.height(12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSecondaryButton(actions.proposeEvent, stringResource(Res.string.agir_now_propose), icon = Lucide.Plus, small = true, modifier = Modifier.testTag(AgirNowTags.PROPOSE))
            PpfSecondaryButton(actions.launchCall, stringResource(Res.string.agir_now_launch), icon = Lucide.Megaphone, small = true, modifier = Modifier.testTag(AgirNowTags.LAUNCH))
        }
    }
}

@Composable
private fun NowRow(icon: androidx.compose.ui.graphics.vector.ImageVector, badge: String, badgeStyle: BadgeStyle, title: String, line: String, onClick: () -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PpfBadge(badge, badgeStyle)
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(line, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/** « Aujourd'hui », « Demain », or the day of the week and its date, as the days of the agenda. */
@Composable
private fun dayWord(start: Long, now: Long): String =
    org.politisationpopulairefrancaise.ui.agir.agenda.dayHeader(org.politisationpopulairefrancaise.core.agir.agenda.ParisTime.moment(start).epochDay, now)

/** « Je veux… »: one tap from a goal to the kits that serve it (D-232). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgirGoals(onGoal: (KitGoal) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(Res.string.kits_filter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KitGoal.entries.forEach { goal ->
                PpfSelectableChip(stringResource(goalLabel(goal)), selected = false, onClick = { onGoal(goal) }, modifier = Modifier.testTag(AgirNowTags.GOAL + goal.id))
            }
        }
    }
}
