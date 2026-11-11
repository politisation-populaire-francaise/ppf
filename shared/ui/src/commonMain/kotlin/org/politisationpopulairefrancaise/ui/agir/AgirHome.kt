package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.agir.engagement.EngagementFeatured
import org.politisationpopulairefrancaise.ui.agir.timer.TimerHomeBlock
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agir_agenda_text
import org.politisationpopulairefrancaise.ui.resources.agir_agenda_title
import org.politisationpopulairefrancaise.ui.resources.agir_calls_text
import org.politisationpopulairefrancaise.ui.resources.agir_calls_title
import org.politisationpopulairefrancaise.ui.resources.agir_collectives_text
import org.politisationpopulairefrancaise.ui.resources.agir_collectives_title
import org.politisationpopulairefrancaise.ui.resources.agir_guides_text
import org.politisationpopulairefrancaise.ui.resources.agir_guides_title
import org.politisationpopulairefrancaise.ui.resources.agir_help_text
import org.politisationpopulairefrancaise.ui.resources.agir_help_title
import org.politisationpopulairefrancaise.ui.resources.agir_intro
import org.politisationpopulairefrancaise.ui.resources.agir_material_text
import org.politisationpopulairefrancaise.ui.resources.agir_material_title
import org.politisationpopulairefrancaise.ui.resources.agir_offline_text
import org.politisationpopulairefrancaise.ui.resources.agir_offline_title
import org.politisationpopulairefrancaise.ui.resources.agir_organisations_text
import org.politisationpopulairefrancaise.ui.resources.agir_organisations_title
import org.politisationpopulairefrancaise.ui.resources.agir_protest_mode_text
import org.politisationpopulairefrancaise.ui.resources.agir_protest_mode_title
import org.politisationpopulairefrancaise.ui.resources.agir_rubrics_title
import org.politisationpopulairefrancaise.ui.resources.agir_safety_text
import org.politisationpopulairefrancaise.ui.resources.agir_safety_title
import org.politisationpopulairefrancaise.ui.resources.agir_solidarity_text
import org.politisationpopulairefrancaise.ui.resources.agir_solidarity_title
import org.politisationpopulairefrancaise.ui.resources.agir_soon_badge
import org.politisationpopulairefrancaise.ui.resources.agir_soon_state
import org.politisationpopulairefrancaise.ui.resources.agir_soon_text
import org.politisationpopulairefrancaise.ui.resources.agir_soon_title
import org.politisationpopulairefrancaise.ui.resources.agir_testify_text
import org.politisationpopulairefrancaise.ui.resources.agir_testify_title
import org.politisationpopulairefrancaise.ui.resources.engager_rubric_text
import org.politisationpopulairefrancaise.ui.resources.engager_rubric_title
import org.politisationpopulairefrancaise.ui.resources.federer_rubric_text
import org.politisationpopulairefrancaise.ui.resources.federer_rubric_title
import org.politisationpopulairefrancaise.ui.resources.proches_rubric_text
import org.politisationpopulairefrancaise.ui.resources.proches_rubric_title
import org.politisationpopulairefrancaise.ui.resources.space_agir
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** A rubric of the home of "Agir": its route when it opens something, its label, its explanation, its icon. */
data class AgirRubric(val id: String, val title: StringResource, val text: StringResource, val icon: ImageVector)

/** The rubrics of "Agir", in the order of the home (inventory B6, entry "Réorganiser le cahier fonctionnel par espaces"). */
object AgirRubrics {
    val AGENDA = AgirRubric("agenda", Res.string.agir_agenda_title, Res.string.agir_agenda_text, Lucide.Calendar)
    val CALLS = AgirRubric("appels", Res.string.agir_calls_title, Res.string.agir_calls_text, Lucide.Megaphone)
    val ORGANISATIONS = AgirRubric("organisations", Res.string.agir_organisations_title, Res.string.agir_organisations_text, Lucide.Building2)
    val COLLECTIVES = AgirRubric("collectifs", Res.string.agir_collectives_title, Res.string.agir_collectives_text, Lucide.Users)
    val GUIDES = AgirRubric("guides", Res.string.agir_guides_title, Res.string.agir_guides_text, Lucide.BookOpen)
    val TESTIFY = AgirRubric("temoigner", Res.string.agir_testify_title, Res.string.agir_testify_text, Lucide.Camera)

    /** The protest mode (AG-13, M25): prepare a protest, offline. */
    val MANIFESTATION = AgirRubric("mode-manifestation", Res.string.agir_protest_mode_title, Res.string.agir_protest_mode_text, Lucide.HandFist)

    /** Solidarity funds (M35, AG-10): opens the solidarity rubric on its funds. */
    val SOLIDARITY = AgirRubric("solidarite", Res.string.agir_solidarity_title, Res.string.agir_solidarity_text, Lucide.HandHelping)

    /** "Je veux aider" (M35, AG-12): opens the solidarity rubric on the volunteer missions. */
    val HELP = AgirRubric("aider", Res.string.agir_help_title, Res.string.agir_help_text, Lucide.Heart)

    /** « Mes proches à mobiliser » (AG-63): the private list of a few relatives, on the device only. */
    val PROCHES = AgirRubric("proches", Res.string.proches_rubric_title, Res.string.proches_rubric_text, Lucide.UsersRound)

    /** « Fédérer » (D-195): convince one's circle; the list of relatives, the kit and its cards to share. */
    val FEDERER = AgirRubric("federer", Res.string.federer_rubric_title, Res.string.federer_rubric_text, Lucide.MessageCircle)

    /** « S'engager en politique » (D-196, D-213): the steps in the order of the electoral calendar. */
    val ENGAGER = AgirRubric("s-engager", Res.string.engager_rubric_title, Res.string.engager_rubric_text, Lucide.Vote)

    /** What can be opened today. */
    val available: List<AgirRubric> = listOf(AGENDA, CALLS, FEDERER, ENGAGER, TESTIFY, MANIFESTATION, ORGANISATIONS, SOLIDARITY, HELP, PROCHES, COLLECTIVES, GUIDES)

    /** Planned but not ready: shown honestly, disabled, without any date. */
    val inPreparation: List<AgirRubric> = listOf(
        AgirRubric("secours", Res.string.agir_safety_title, Res.string.agir_safety_text, Lucide.LifeBuoy),
        AgirRubric("materiel", Res.string.agir_material_title, Res.string.agir_material_text, Lucide.Printer),
        AgirRubric("sans-internet", Res.string.agir_offline_title, Res.string.agir_offline_text, Lucide.Bluetooth),
    )
}

/** Test tags of the home of "Agir". */
object AgirTags {
    const val HOME = "agir-home"
    const val RUBRIC = "agir-rubric-" // followed by the rubric id
    const val SOON = "agir-soon-" // followed by the rubric id
    const val SOON_TITLE = "agir-soon-title"
    const val BACK = "agir-back"
    const val PENDING = "agir-pending"
    const val COLLECTIVES = "agir-collectives"

    /** The plain rooms (salons) reached from the directory of the collectifs. */
    const val ROOMS = "agir-rooms"
}

/** How many cards a row of the home holds for a given width: 1 on phones, 2 on tablets, 3 on wide windows. */
fun agirColumns(width: Dp): Int = when {
    width < 560.dp -> 1
    width < 900.dp -> 2
    else -> 3
}

/**
 * The home of "Agir": a sentence of the vision, then the rubrics that open today, then those in
 * preparation, disabled, with one honest line of explanation and no date.
 */
@Composable
internal fun AgirHome(
    windowClass: WindowClass,
    onOpen: (AgirRubric) -> Unit,
    modifier: Modifier = Modifier,
    today: CivilDate = ParisTime.dateOf(ParisTime.epochDay(currentEpochSeconds())),
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
        Column(Modifier.widthIn(max = 1100.dp).testTag(AgirTags.HOME)) {
            PageHeader(stringResource(Res.string.space_agir), stringResource(Res.string.agir_intro))
            // AG-55 « Sans nouvelles de moi »: the alerts of relatives first, then the person's own timer and the discreet lines.
            TimerHomeBlock(onOpenOwn = { onOpen(AgirRubrics.MANIFESTATION) }, modifier = Modifier.padding(top = 16.dp))
            // D-213: « S'engager en politique » put forward, with the next milestone of the electoral calendar.
            EngagementFeatured(today, onOpen = { onOpen(AgirRubrics.ENGAGER) }, modifier = Modifier.padding(top = 16.dp).widthIn(max = 720.dp))
            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(Res.string.agir_rubrics_title))
            Spacer(Modifier.height(12.dp))
            RubricGrid(AgirRubrics.available) { rubric, cardModifier -> RubricCard(rubric, onClick = { onOpen(rubric) }, cardModifier) }
            Spacer(Modifier.height(32.dp))
            SectionTitle(stringResource(Res.string.agir_soon_title), Modifier.testTag(AgirTags.SOON_TITLE))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.agir_soon_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Spacer(Modifier.height(12.dp))
            RubricGrid(AgirRubrics.inPreparation) { rubric, cardModifier -> RubricCard(rubric, onClick = null, cardModifier) }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

/** The cards in rows of equal height, 1 to 3 per row depending on the width available. */
@Composable
private fun RubricGrid(rubrics: List<AgirRubric>, card: @Composable (AgirRubric, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = agirColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rubrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { rubric -> card(rubric, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One rubric: an icon, its name, one line of explanation. [onClick] null: in preparation, disabled. */
@Composable
private fun RubricCard(rubric: AgirRubric, onClick: (() -> Unit)?, modifier: Modifier) {
    val palette = PpfTheme.palette
    val title = stringResource(rubric.title)
    val enabled = onClick != null
    // An open rubric is one button whose name is its title and explanation; one in preparation says it is not available yet.
    val semantics = if (enabled) {
        Modifier.semantics { role = Role.Button }
    } else {
        val state = stringResource(Res.string.agir_soon_state)
        Modifier.semantics(mergeDescendants = true) {
            disabled()
            stateDescription = state
        }
    }
    PpfCard(
        onClick = onClick,
        modifier = modifier.then(semantics).testTag((if (enabled) AgirTags.RUBRIC else AgirTags.SOON) + rubric.id),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).background(if (enabled) palette.brandTint else palette.surface1, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(rubric.icon, contentDescription = null, tint = if (enabled) palette.brand else palette.inkMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) palette.ink else palette.inkMuted)
                if (!enabled) {
                    Spacer(Modifier.height(4.dp))
                    PpfBadge(stringResource(Res.string.agir_soon_badge), BadgeStyle.NEUTRAL)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(rubric.text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            if (enabled) {
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
            }
        }
    }
}
