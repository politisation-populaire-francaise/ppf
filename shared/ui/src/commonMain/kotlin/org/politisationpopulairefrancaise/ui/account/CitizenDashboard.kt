package org.politisationpopulairefrancaise.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.ui.agir.AgirRoute
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.content.ComprendreSection
import org.politisationpopulairefrancaise.ui.decider.DeciderHighlight
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.shell.LocalMenuTargetHandler
import org.politisationpopulairefrancaise.ui.shell.MenuTarget
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirDestination

object DashboardTags {
    const val JOURNEY = "dashboard-journey"
    const val SHORTCUTS = "dashboard-shortcuts"
    fun mission(id: String) = "dashboard-mission-$id"
    fun missionOpen(id: String) = "dashboard-mission-open-$id"
}

/** One mission of the journey: short, a couple of minutes, and the page where it is done. */
data class JourneyMission(val id: String, val label: StringResource, val minutes: Int, val target: MenuTarget)

/** One step of the journey, in the order of the seven rubrics of the menu (D-240); its level is earned when every mission is done. */
data class JourneyStep(val level: StringResource, val missions: List<JourneyMission>)

/**
 * The journey of the democratic citizen (prototype of E-04, 11 November 2026): seven steps that follow
 * the rubrics, two or three missions each. The person marks a mission as done; nothing is checked nor
 * sent (an opinion can show in what one did, the progress stays on the device, D-240 safeguards).
 */
val citizenJourney: List<JourneyStep> = listOf(
    JourneyStep(Res.string.journey_level_1, listOf(
        JourneyMission("chiffres", Res.string.mission_chiffres, 2, MenuTarget.VoirAxisHome(VoirAxis.MOUVEMENT)),
        JourneyMission("verifier", Res.string.mission_verifier, 2, MenuTarget.Voir(VoirDestination.Verify)),
        JourneyMission("actu", Res.string.mission_actu, 3, MenuTarget.OpenSpace(Space.FIL)),
    )),
    JourneyStep(Res.string.journey_level_2, listOf(
        JourneyMission("cap", Res.string.mission_cap, 4, MenuTarget.Comprendre(ComprendreSection.CAP)),
        JourneyMission("cinq-minutes", Res.string.mission_cinq_minutes, 5, MenuTarget.Comprendre(ComprendreSection.COURSES)),
        JourneyMission("qui-decide", Res.string.mission_qui_decide, 2, MenuTarget.Comprendre(ComprendreSection.WHO)),
    )),
    JourneyStep(Res.string.journey_level_3, listOf(
        JourneyMission("temoigner", Res.string.mission_temoigner, 3, MenuTarget.Agir(AgirRoute.TESTIFY)),
        JourneyMission("camera", Res.string.mission_camera, 2, MenuTarget.HelpSheet),
    )),
    JourneyStep(Res.string.journey_level_4, listOf(
        JourneyMission("voter", Res.string.mission_voter_revendication, 2, MenuTarget.Revendications("tout")),
        JourneyMission("constitution", Res.string.mission_constitution, 4, MenuTarget.Decider(DeciderHighlight.PACTE)),
        JourneyMission("petition", Res.string.mission_petition, 3, MenuTarget.Decider(DeciderHighlight.PETITIONS)),
    )),
    JourneyStep(Res.string.journey_level_5, listOf(
        JourneyMission("collectif", Res.string.mission_collectif, 3, MenuTarget.Agir(AgirRoute.COLLECTIVES)),
        JourneyMission("federer", Res.string.mission_federer, 5, MenuTarget.Agir(AgirRoute.FEDERER)),
    )),
    JourneyStep(Res.string.journey_level_6, listOf(
        JourneyMission("listes", Res.string.mission_listes, 3, MenuTarget.Agir(AgirRoute.ENGAGER)),
        JourneyMission("elus", Res.string.mission_elus, 2, MenuTarget.Comprendre(ComprendreSection.ELUS)),
        JourneyMission("interpeller", Res.string.mission_interpeller, 5, MenuTarget.Decider(null)),
    )),
    JourneyStep(Res.string.journey_level_7, listOf(
        JourneyMission("appel", Res.string.mission_appel, 2, MenuTarget.OpenSpace(Space.APPELS)),
        JourneyMission("manifestation", Res.string.mission_manifestation, 4, MenuTarget.Agir(AgirRoute.MANIFESTATION)),
    )),
)

/** First round of the presidential election, 18 April 2027 (to replace by the date of the convocation decree). */
private val PRESIDENTIAL_FIRST_ROUND = CivilDate(2027, 4, 18)

/**
 * The top of "Mon espace" as a personal dashboard (E-04): the journey of the citizen, then shortcuts
 * as widgets. The account and the settings follow underneath, unchanged.
 */
@Composable
fun CitizenDashboard(modifier: Modifier = Modifier) {
    val onTarget = LocalMenuTargetHandler.current
    var done by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JourneyCard(done, onToggle = { id -> done = if (id in done) done - id else done + id }, onOpen = onTarget)
        Shortcuts(onTarget)
    }
}

@Composable
private fun JourneyCard(done: List<String>, onToggle: (String) -> Unit, onOpen: (MenuTarget) -> Unit) {
    val palette = PpfTheme.palette
    val total = citizenJourney.sumOf { it.missions.size }
    val doneCount = citizenJourney.sumOf { step -> step.missions.count { it.id in done } }
    val stepIndex = citizenJourney.indexOfFirst { step -> step.missions.any { it.id !in done } }
    PpfCard(Modifier.fillMaxWidth().testTag(DashboardTags.JOURNEY)) {
        Text(stringResource(Res.string.dashboard_journey_title), style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        if (stepIndex < 0) {
            Text(stringResource(Res.string.dashboard_journey_done), style = MaterialTheme.typography.bodyMedium, color = palette.success)
        } else {
            val step = citizenJourney[stepIndex]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.dashboard_journey_level, stepIndex + 1, citizenJourney.size, stringResource(step.level)),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.brand,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.dashboard_journey_progress, doneCount, total), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { doneCount.toFloat() / total },
                color = palette.brand,
                trackColor = palette.brandTint,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.dashboard_journey_next), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted)
            Spacer(Modifier.height(4.dp))
            step.missions.forEach { mission -> MissionRow(mission, mission.id in done, onToggle, onOpen) }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.dashboard_journey_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Text(stringResource(Res.string.dashboard_journey_prototype), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun MissionRow(mission: JourneyMission, isDone: Boolean, onToggle: (String) -> Unit, onOpen: (MenuTarget) -> Unit) {
    val palette = PpfTheme.palette
    val label = stringResource(mission.label)
    val toggleLabel = stringResource(if (isDone) Res.string.dashboard_mission_mark_undone else Res.string.dashboard_mission_mark_done, label)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // The round to tick: its own 48 dp target, apart from the way to the page.
        Box(
            Modifier.size(48.dp)
                .toggleable(value = isDone, role = Role.Checkbox, onValueChange = { onToggle(mission.id) })
                .semantics { contentDescription = toggleLabel }
                .testTag(DashboardTags.mission(mission.id)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(if (isDone) palette.success else palette.brandTint),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) Icon(Lucide.Check, contentDescription = null, tint = palette.surface2, modifier = Modifier.size(14.dp))
            }
        }
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.dashboard_mission_open)) { onOpen(mission.target) }
                .testTag(DashboardTags.missionOpen(mission.id))
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isDone) palette.inkSubtle else palette.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.dashboard_mission_minutes, mission.minutes), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Shortcuts(onOpen: (MenuTarget) -> Unit) {
    val palette = PpfTheme.palette
    val today = CivilDate.fromEpochSeconds(currentEpochSeconds())
    val days = (PRESIDENTIAL_FIRST_ROUND.toEpochDays() - today.toEpochDays()).toInt()
    Column(Modifier.testTag(DashboardTags.SHORTCUTS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.dashboard_shortcuts_title), style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (days >= 0) {
                Widget(Lucide.Vote, stringResource(Res.string.dashboard_election_label), stringResource(Res.string.dashboard_election_days, days), Modifier.weight(1f)) {
                    onOpen(MenuTarget.Decider(null))
                }
            }
            Widget(Lucide.MapPin, stringResource(Res.string.dashboard_shortcut_nearby), null, Modifier.weight(1f)) { onOpen(MenuTarget.OpenSpace(Space.CARTE)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Widget(Lucide.Landmark, stringResource(Res.string.dashboard_shortcut_elus), null, Modifier.weight(1f)) { onOpen(MenuTarget.Comprendre(ComprendreSection.ELUS)) }
            Widget(Lucide.Lightbulb, stringResource(Res.string.dashboard_shortcut_lesson), null, Modifier.weight(1f)) { onOpen(MenuTarget.Comprendre(ComprendreSection.COURSES)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Widget(Lucide.Megaphone, stringResource(Res.string.dashboard_shortcut_demands), null, Modifier.weight(1f)) { onOpen(MenuTarget.Revendications("mes-revendications")) }
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Plus, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.dashboard_add_widget), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                PpfBadge(stringResource(Res.string.menu_soon), BadgeStyle.NEUTRAL)
            }
        }
    }
}

@Composable
private fun Widget(icon: ImageVector, label: String, value: String?, modifier: Modifier, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(modifier, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 2)
        if (value != null) Text(value, style = MaterialTheme.typography.titleLarge, color = palette.ink)
    }
}
