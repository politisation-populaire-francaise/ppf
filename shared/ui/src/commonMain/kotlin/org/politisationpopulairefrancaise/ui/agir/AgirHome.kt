package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.core.agir.AgirHighlights
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
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
import androidx.compose.ui.text.style.TextOverflow
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
import org.politisationpopulairefrancaise.ui.resources.agir_soon_hide
import org.politisationpopulairefrancaise.ui.resources.agir_soon_show
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
import org.politisationpopulairefrancaise.ui.resources.agir_section_federate
import org.politisationpopulairefrancaise.ui.resources.agir_section_hold
import org.politisationpopulairefrancaise.ui.resources.agir_section_mobilize
import org.politisationpopulairefrancaise.ui.resources.agir_section_protect
import org.politisationpopulairefrancaise.ui.resources.gather_rubric_text
import org.politisationpopulairefrancaise.ui.resources.greve_entry_text
import org.politisationpopulairefrancaise.ui.resources.kits_rubric_text
import org.politisationpopulairefrancaise.ui.resources.kits_rubric_title
import org.politisationpopulairefrancaise.ui.resources.greve_entry_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_help_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_help_title
import org.politisationpopulairefrancaise.ui.resources.gather_rubric_title
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

    /** « Se réunir » (D-198): collectifs, organisations, « Créer une association », rooms. */
    val GATHER = AgirRubric("se-reunir", Res.string.gather_rubric_title, Res.string.gather_rubric_text, Lucide.Users)

    /** « Kit contre le harcèlement » (AG-59, D-230): opens the kit the shell draws over the window. */
    val HARASSMENT = AgirRubric("harcelement", Res.string.harcelement_help_title, Res.string.harcelement_help_text, Lucide.ShieldCheck)

    /** The strike simulator (AG-60) in « Tenir » (D-190); it is also reached from the strike funds. */
    val STRIKE = AgirRubric("simulateur-greve", Res.string.greve_entry_title, Res.string.greve_entry_text, Lucide.ListChecks)

    /**
     * The sections of the home, in the order of the roadmap of the MVP 1 (jalon 2, D-231): one heading each,
     * so that a phone shows a few cards at a time instead of one long list. « S'engager en politique » is put
     * forward above them (D-213); nothing is hidden (D-211).
     */
    val sections: List<AgirSection> = listOf(
        AgirSection("se-mobiliser", Res.string.agir_section_mobilize, listOf(AGENDA, CALLS, GATHER, COLLECTIVES, ORGANISATIONS)),
        AgirSection("tenir", Res.string.agir_section_hold, listOf(SOLIDARITY, HELP, STRIKE, GUIDES)),
        AgirSection("federer", Res.string.agir_section_federate, listOf(FEDERER, PROCHES)),
        AgirSection("se-proteger", Res.string.agir_section_protect, listOf(MANIFESTATION, HARASSMENT, TESTIFY)),
    )

    /** « Kits d'action citoyenne » (D-232): a card of its own under the highlight, above the sections. */
    val KITS = AgirRubric("kits", Res.string.kits_rubric_title, Res.string.kits_rubric_text, Lucide.ListChecks)

    /** What can be opened today: the kits, the cards of the sections, then the highlight of « S'engager en politique ». */
    val available: List<AgirRubric> = listOf(KITS) + sections.flatMap { it.rubrics } + ENGAGER

    /** Planned but not ready: shown honestly, disabled, without any date. */
    val inPreparation: List<AgirRubric> = listOf(
        AgirRubric("secours", Res.string.agir_safety_title, Res.string.agir_safety_text, Lucide.LifeBuoy),
        AgirRubric("materiel", Res.string.agir_material_title, Res.string.agir_material_text, Lucide.Printer),
        AgirRubric("sans-internet", Res.string.agir_offline_title, Res.string.agir_offline_text, Lucide.Bluetooth),
    )
}

/** A heading of the home of "Agir" and its cards. */
data class AgirSection(val id: String, val title: StringResource, val rubrics: List<AgirRubric>)

/** Test tags of the home of "Agir". */
object AgirTags {
    const val HOME = "agir-home"
    const val RUBRIC = "agir-rubric-" // followed by the rubric id
    const val SOON = "agir-soon-" // followed by the rubric id
    const val SOON_TITLE = "agir-soon-title"
    const val SECTION = "agir-section-" // followed by the section id
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
    highlights: AgirHighlights? = null,
    now: Long = currentEpochSeconds(),
    nowActions: AgirNowActions? = null,
    onGoal: ((KitGoal) -> Unit)? = null,
    territory: MyTerritory? = null,
    onChooseTerritory: (() -> Unit)? = null,
) {
    var soonOpen by rememberSaveable { mutableStateOf(false) }
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
        Column(Modifier.widthIn(max = 1100.dp).testTag(AgirTags.HOME)) {
            PageHeader(stringResource(Res.string.space_agir), stringResource(Res.string.agir_intro))
            // AG-55 « Sans nouvelles de moi »: the alerts of relatives first, then the person's own timer and the discreet lines.
            TimerHomeBlock(onOpenOwn = { onOpen(AgirRubrics.MANIFESTATION) }, modifier = Modifier.padding(top = 16.dp))
            // « En ce moment »: what is happening this week, one tap away; or the two ways to start something.
            if (highlights != null && nowActions != null) {
                AgirNowCard(highlights, now, nowActions, Modifier.padding(top = 16.dp).widthIn(max = 720.dp), territory, onChooseTerritory)
            }
            // « Je veux… »: from a goal straight to its kits.
            if (onGoal != null) AgirGoals(onGoal, Modifier.padding(top = 16.dp).widthIn(max = 720.dp))
            // D-213: « S'engager en politique » put forward, with the next milestone of the electoral calendar.
            EngagementFeatured(today, onOpen = { onOpen(AgirRubrics.ENGAGER) }, modifier = Modifier.padding(top = 16.dp).widthIn(max = 720.dp))
            // D-232: every kit and guide in one place, filtered by goal.
            RubricCard(AgirRubrics.KITS, onClick = { onOpen(AgirRubrics.KITS) }, Modifier.padding(top = 12.dp).widthIn(max = 720.dp).fillMaxWidth(), compact)
            AgirRubrics.sections.forEach { section ->
                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(section.title), Modifier.testTag(AgirTags.SECTION + section.id))
                Spacer(Modifier.height(12.dp))
                RubricGrid(section.rubrics) { rubric, cardModifier -> RubricCard(rubric, onClick = { onOpen(rubric) }, cardModifier, compact) }
            }
            Spacer(Modifier.height(32.dp))
            // The rubrics in preparation stay listed (« tout reste visible »), folded under one line so that they do not take a screen.
            PpfTextButton(
                onClick = { soonOpen = !soonOpen },
                text = if (soonOpen) stringResource(Res.string.agir_soon_hide) else stringResource(Res.string.agir_soon_show, AgirRubrics.inPreparation.size),
                modifier = Modifier.testTag(AgirNowTags.SOON_TOGGLE),
            )
            if (soonOpen) {
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(Res.string.agir_soon_title), Modifier.testTag(AgirTags.SOON_TITLE))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.agir_soon_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(12.dp))
                RubricGrid(AgirRubrics.inPreparation) { rubric, cardModifier -> RubricCard(rubric, onClick = null, cardModifier, compact) }
            }
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
private fun RubricCard(rubric: AgirRubric, onClick: (() -> Unit)?, modifier: Modifier, compact: Boolean = false) {
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
                // On phones two lines of explanation; the screen reader still reads it whole.
                Text(
                    stringResource(rubric.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    maxLines = if (compact) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) {
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
            }
        }
    }
}
