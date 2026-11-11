package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.ui.agir.AgirRoute
import org.politisationpopulairefrancaise.ui.content.ComprendreSection
import org.politisationpopulairefrancaise.ui.decider.DeciderHighlight
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.messages.MessagesView
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.voir.VoirDestination

/**
 * Where an entry of the menu leads: a space, or a precise page inside one (decision of the owner,
 * 11 November 2026, D-240: seven rubrics, each with its sub-rubrics shown as an accordion).
 */
sealed interface MenuTarget {
    data class OpenSpace(val space: Space) : MenuTarget
    data class Agir(val route: AgirRoute) : MenuTarget
    data class VoirAxisHome(val axis: VoirAxis) : MenuTarget
    data class Voir(val destination: VoirDestination) : MenuTarget
    data class Comprendre(val section: ComprendreSection) : MenuTarget
    data class Decider(val highlight: DeciderHighlight?) : MenuTarget
    data object DeciderDocuments : MenuTarget
    data class Revendications(val viewId: String) : MenuTarget
    data object ProposeRevendication : MenuTarget
    data class Messages(val view: MessagesView) : MenuTarget
    data object HelpSheet : MenuTarget
    data object HarassmentKit : MenuTarget

    /** Not built yet: shown with « Bientôt », does nothing (D-210: announced, never hidden). */
    data object Soon : MenuTarget
}

/** A sub-rubric: its label, where it leads, and the space it belongs to (to mark it current). */
data class MenuEntry(val id: String, val label: StringResource, val target: MenuTarget) {
    val space: Space?
        get() = when (target) {
            is MenuTarget.OpenSpace -> target.space
            is MenuTarget.Agir -> Space.AGIR
            is MenuTarget.VoirAxisHome, is MenuTarget.Voir -> Space.VOIR
            is MenuTarget.Comprendre -> Space.COMPRENDRE
            is MenuTarget.Decider, MenuTarget.DeciderDocuments -> Space.DECIDER
            is MenuTarget.Revendications, MenuTarget.ProposeRevendication -> Space.REVENDICATIONS
            is MenuTarget.Messages -> Space.MESSAGES
            MenuTarget.HelpSheet, MenuTarget.HarassmentKit, MenuTarget.Soon -> null
        }
}

/**
 * The seven rubrics of the menu, a logical journey from getting informed to acting (D-240). They
 * replace the four pillars in the menu; the spaces and their stable identifiers do not change.
 */
enum class MenuRubric(val id: String, val title: StringResource, val icon: ImageVector, val home: Space?) {
    INFORMER("s-informer", Res.string.menu_informer, Lucide.Newspaper, Space.VOIR),
    COMPRENDRE("comprendre", Res.string.menu_comprendre, Lucide.BookOpen, Space.COMPRENDRE),
    TEMOIGNER("temoigner", Res.string.menu_temoigner, Lucide.Camera, null),
    REVENDIQUER("revendiquer", Res.string.menu_revendiquer, Lucide.Megaphone, Space.REVENDICATIONS),
    UNIR("s-unir", Res.string.menu_unir, Lucide.Users, null),
    POLITISER("se-politiser", Res.string.menu_politiser, Lucide.Landmark, Space.DECIDER),
    AGIR("agir", Res.string.menu_agir, Lucide.HandFist, Space.AGIR);

    val entries: List<MenuEntry> get() = menuEntries.getValue(this)
}

private fun e(id: String, label: StringResource, target: MenuTarget) = MenuEntry(id, label, target)

private val menuEntries: Map<MenuRubric, List<MenuEntry>> = mapOf(
    MenuRubric.INFORMER to listOf(
        e("informer-accueil", Res.string.menu_home_informer, MenuTarget.OpenSpace(Space.VOIR)),
        e("actu", Res.string.menu_actu, MenuTarget.OpenSpace(Space.FIL)),
        e("mouvement", Res.string.menu_mouvement, MenuTarget.VoirAxisHome(VoirAxis.MOUVEMENT)),
        e("pouvoir", Res.string.menu_pouvoir, MenuTarget.VoirAxisHome(VoirAxis.POUVOIR)),
        e("partis", Res.string.menu_partis, MenuTarget.Voir(VoirDestination.Parties)),
        e("responsables", Res.string.menu_responsables, MenuTarget.Voir(VoirDestination.People)),
        e("textes", Res.string.menu_textes, MenuTarget.Voir(VoirDestination.Bills)),
        e("libertes", Res.string.menu_libertes, MenuTarget.VoirAxisHome(VoirAxis.LIBERTES)),
        e("verifier", Res.string.menu_verifier, MenuTarget.Voir(VoirDestination.Verify)),
        e("transparence", Res.string.menu_transparence, MenuTarget.Voir(VoirDestination.Transparency)),
    ),
    MenuRubric.COMPRENDRE to listOf(
        e("comprendre-accueil", Res.string.menu_home_comprendre, MenuTarget.OpenSpace(Space.COMPRENDRE)),
        e("cap", Res.string.menu_cap, MenuTarget.Comprendre(ComprendreSection.CAP)),
        e("cinq-minutes", Res.string.menu_cinq_minutes, MenuTarget.Comprendre(ComprendreSection.COURSES)),
        e("qui-decide", Res.string.menu_qui_decide, MenuTarget.Comprendre(ComprendreSection.WHO)),
        e("mes-elus", Res.string.menu_mes_elus, MenuTarget.Comprendre(ComprendreSection.ELUS)),
        e("droits", Res.string.menu_droits, MenuTarget.Comprendre(ComprendreSection.RIGHTS)),
        e("vrai-faux", Res.string.menu_vrai_faux, MenuTarget.Comprendre(ComprendreSection.CHECKS)),
        e("histoire", Res.string.menu_histoire, MenuTarget.Comprendre(ComprendreSection.HISTORY)),
        e("glossaire", Res.string.menu_glossaire, MenuTarget.Comprendre(ComprendreSection.GLOSSARY)),
    ),
    MenuRubric.TEMOIGNER to listOf(
        e("temoigner", Res.string.menu_envoyer_temoignage, MenuTarget.Agir(AgirRoute.TESTIFY)),
        e("camera", Res.string.menu_camera, MenuTarget.HelpSheet),
        e("harcelement", Res.string.menu_harcelement, MenuTarget.HarassmentKit),
        e("appels-temoins", Res.string.menu_appels_temoins, MenuTarget.Soon),
        e("mes-temoignages", Res.string.menu_mes_temoignages, MenuTarget.Soon),
    ),
    MenuRubric.REVENDIQUER to listOf(
        e("revendications", Res.string.menu_toutes_revendications, MenuTarget.Revendications("tout")),
        e("proposer", Res.string.menu_proposer, MenuTarget.ProposeRevendication),
        e("mes-revendications", Res.string.menu_mes_revendications, MenuTarget.Revendications("mes-revendications")),
        e("constitution", Res.string.menu_constitution, MenuTarget.Decider(DeciderHighlight.PACTE)),
        e("petitions", Res.string.menu_petitions, MenuTarget.Decider(DeciderHighlight.PETITIONS)),
        e("petitions-locales", Res.string.menu_petitions_locales, MenuTarget.Decider(null)),
        e("consultations", Res.string.menu_consultations, MenuTarget.Decider(null)),
    ),
    MenuRubric.UNIR to listOf(
        e("messages", Res.string.menu_messages, MenuTarget.Messages(MessagesView.PRIVATE)),
        e("collectifs", Res.string.menu_collectifs, MenuTarget.Agir(AgirRoute.COLLECTIVES)),
        e("organisations", Res.string.menu_organisations, MenuTarget.Agir(AgirRoute.ORGANISATIONS)),
        e("se-reunir", Res.string.menu_se_reunir, MenuTarget.Agir(AgirRoute.GATHER)),
        e("federer", Res.string.menu_federer, MenuTarget.Agir(AgirRoute.FEDERER)),
        e("proches", Res.string.menu_proches, MenuTarget.Agir(AgirRoute.PROCHES)),
        e("solidarite", Res.string.menu_solidarite, MenuTarget.Agir(AgirRoute.SOLIDARITY)),
    ),
    MenuRubric.POLITISER to listOf(
        e("s-engager", Res.string.menu_s_engager, MenuTarget.Agir(AgirRoute.ENGAGER)),
        e("elections", Res.string.menu_elections, MenuTarget.Decider(null)),
        e("comparer", Res.string.menu_comparer, MenuTarget.Decider(null)),
        e("interpeller", Res.string.menu_interpeller, MenuTarget.Decider(null)),
        e("documents", Res.string.menu_documents, MenuTarget.DeciderDocuments),
    ),
    MenuRubric.AGIR to listOf(
        e("agir-accueil", Res.string.menu_home_agir, MenuTarget.OpenSpace(Space.AGIR)),
        e("appels", Res.string.menu_appels, MenuTarget.OpenSpace(Space.APPELS)),
        e("carte", Res.string.menu_carte, MenuTarget.OpenSpace(Space.CARTE)),
        e("kits", Res.string.menu_kits, MenuTarget.Agir(AgirRoute.KITS)),
        e("simulateur", Res.string.menu_simulateur, MenuTarget.Agir(AgirRoute.STRIKE_SIMULATOR)),
        e("manifestation", Res.string.menu_manifestation, MenuTarget.Agir(AgirRoute.MANIFESTATION)),
    ),
)

/** The rubric to open at first: the one whose home is the current space, else the first holding it. */
fun rubricFor(space: Space): MenuRubric? =
    MenuRubric.entries.firstOrNull { it.home == space } ?: MenuRubric.entries.firstOrNull { rubric -> rubric.entries.any { it.space == space } }
