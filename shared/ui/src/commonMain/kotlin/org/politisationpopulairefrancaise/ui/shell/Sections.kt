package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.desc_agir
import org.politisationpopulairefrancaise.ui.resources.desc_appels
import org.politisationpopulairefrancaise.ui.resources.desc_carte
import org.politisationpopulairefrancaise.ui.resources.desc_comprendre
import org.politisationpopulairefrancaise.ui.resources.desc_decider
import org.politisationpopulairefrancaise.ui.resources.desc_en_direct
import org.politisationpopulairefrancaise.ui.resources.desc_fil
import org.politisationpopulairefrancaise.ui.resources.desc_messages
import org.politisationpopulairefrancaise.ui.resources.desc_mon_espace
import org.politisationpopulairefrancaise.ui.resources.desc_revendications
import org.politisationpopulairefrancaise.ui.resources.desc_voir
import org.politisationpopulairefrancaise.ui.resources.space_agir
import org.politisationpopulairefrancaise.ui.resources.space_appels
import org.politisationpopulairefrancaise.ui.resources.space_carte
import org.politisationpopulairefrancaise.ui.resources.space_comprendre
import org.politisationpopulairefrancaise.ui.resources.space_decider
import org.politisationpopulairefrancaise.ui.resources.space_en_direct
import org.politisationpopulairefrancaise.ui.resources.space_fil
import org.politisationpopulairefrancaise.ui.resources.space_messages
import org.politisationpopulairefrancaise.ui.resources.space_mon_espace
import org.politisationpopulairefrancaise.ui.resources.space_revendications
import org.politisationpopulairefrancaise.ui.resources.space_revendications_bar
import org.politisationpopulairefrancaise.ui.resources.space_voir

/** What the navigation knows about a space: its label, its description and its icon (Lucide set). */
data class SectionSpec(
    val space: Space,
    val title: StringResource,
    val description: StringResource,
    val icon: ImageVector,
    /** A hyphenated form of the label for the narrow bottom bar and rail; the title otherwise. */
    val barLabel: StringResource = title,
)

private val sections: Map<Space, SectionSpec> = listOf(
    SectionSpec(Space.FIL, Res.string.space_fil, Res.string.desc_fil, Lucide.Newspaper),
    SectionSpec(Space.EN_DIRECT, Res.string.space_en_direct, Res.string.desc_en_direct, Lucide.Radio),
    SectionSpec(Space.CARTE, Res.string.space_carte, Res.string.desc_carte, Lucide.Map),
    SectionSpec(Space.MESSAGES, Res.string.space_messages, Res.string.desc_messages, Lucide.MessageSquare),
    SectionSpec(Space.REVENDICATIONS, Res.string.space_revendications, Res.string.desc_revendications, Lucide.ScrollText, barLabel = Res.string.space_revendications_bar),
    SectionSpec(Space.VOIR, Res.string.space_voir, Res.string.desc_voir, Lucide.Eye),
    SectionSpec(Space.COMPRENDRE, Res.string.space_comprendre, Res.string.desc_comprendre, Lucide.BookOpen),
    SectionSpec(Space.AGIR, Res.string.space_agir, Res.string.desc_agir, Lucide.Megaphone),
    SectionSpec(Space.DECIDER, Res.string.space_decider, Res.string.desc_decider, Lucide.Vote),
    SectionSpec(Space.MON_ESPACE, Res.string.space_mon_espace, Res.string.desc_mon_espace, Lucide.CircleUserRound),
    SectionSpec(Space.APPELS, Res.string.space_appels, Res.string.desc_appels, Lucide.HandFist),
).associateBy { it.space }

/** The daily entries: bottom bar on phones, top of the rail and of the side bar elsewhere. */
val quickAccessSections: List<SectionSpec> = Space.quickAccess.map(sections::getValue)

/** The rest of the navigation (D-215): the menu of phones, under the daily entries of the rail and of the side bar. */
val moreSections: List<SectionSpec> = Space.more.map(sections::getValue)

fun sectionFor(space: Space): SectionSpec = sections.getValue(space)
