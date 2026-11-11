package org.politisationpopulairefrancaise.web

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/** A link of the side menu, under a space. */
data class NavLink(val label: String, val href: String)

/**
 * One space of the side menu: its label, its icon (an id of `/static/icons.svg`, the Lucide icon of
 * the application) and its sub-sections, shown in an accordion. Without sub-sections the entry is a
 * plain link.
 */
data class NavEntry(val space: Space, val icon: String, val children: List<NavLink> = emptyList()) {
    val label: String get() = spaceLabels.getValue(space)
    val href: String get() = "/${space.id}"
}

/**
 * The menu of the site, in the order of the application (D-215): the daily entries first, then the
 * rest of the pillars and the wall of demands. Messages and Mon espace are in the top bar, as in the
 * application. The sub-sections point at pages of the site; what only exists in the application is
 * presented on the page of its space.
 */
object SiteNavigation {
    const val HELP_PATH: String = "/aide"

    private val icons: Map<Space, String> = mapOf(
        Space.EN_DIRECT to "radio",
        Space.FIL to "newspaper",
        Space.CARTE to "map",
        Space.AGIR to "megaphone",
        Space.APPELS to "hand-fist",
        Space.VOIR to "eye",
        Space.COMPRENDRE to "book-open",
        Space.DECIDER to "vote",
        Space.REVENDICATIONS to "scroll-text",
        Space.MESSAGES to "message-square",
        Space.MON_ESPACE to "circle-user-round",
    )

    fun iconOf(space: Space): String = icons.getValue(space)

    private val children: Map<Space, List<NavLink>> = mapOf(
        Space.AGIR to listOf(
            NavLink("Toutes les rubriques", "/agir"),
            NavLink("Agenda des mobilisations", "/agenda"),
            NavLink("Appels à agir", "/agir/appels"),
            NavLink("Kits d’action citoyenne", AgirSitePaths.KITS),
            NavLink("S’engager en politique", AgirSitePaths.ENGAGER),
            NavLink("Collectifs", AgirSitePaths.COLLECTIVES),
            NavLink("Solidarité et caisses de grève", AgirSitePaths.SOLIDARITY),
            NavLink("Annuaire des organisations", "/organisations"),
            NavLink("Témoigner", "/agir#temoigner"),
        ),
        Space.VOIR to listOf(
            NavLink("Vue d’ensemble", VoirLinks.HOME),
            NavLink("Le mouvement", "${VoirLinks.HOME}#mouvement"),
            NavLink("Le pouvoir", "${VoirLinks.HOME}#pouvoir"),
            NavLink("Les libertés", "${VoirLinks.HOME}#libertes"),
            NavLink("Chronologie du mouvement", VoirLinks.CHRONOLOGY),
            NavLink("Observatoire des libertés", VoirLinks.LIBERTIES),
            NavLink("Tableau de bord", "/tableau-de-bord"),
            NavLink("Journal des corrections", VoirLinks.CORRECTIONS),
        ),
        Space.COMPRENDRE to listOf(
            NavLink("Vue d’ensemble", ComprendreLinks.HOME),
            NavLink("Fiches pratiques", "${ComprendreLinks.HOME}/${ComprendreLinks.FICHES}"),
            NavLink("Comprendre en 5 minutes", "${ComprendreLinks.HOME}/${ComprendreLinks.PARCOURS}"),
            NavLink("Le vrai du faux", "${ComprendreLinks.HOME}/${ComprendreLinks.DECRYPTAGES}"),
            NavLink("Qui décide de ça ?", "${ComprendreLinks.HOME}/${ComprendreLinks.QUI_DECIDE}"),
            NavLink("Le cap du mouvement", "${ComprendreLinks.HOME}/${ComprendreLinks.CAP}"),
            NavLink("Histoire des luttes", "${ComprendreLinks.HOME}/${ComprendreLinks.HISTOIRE}"),
            NavLink("Glossaire", "${ComprendreLinks.HOME}/${ComprendreLinks.GLOSSAIRE}"),
            NavLink("Pour les enseignants", "${ComprendreLinks.HOME}/${ComprendreLinks.ENSEIGNANTS}"),
            NavLink("Lire hors ligne", ComprendreLinks.OFFLINE),
        ),
        Space.DECIDER to listOf(
            NavLink("Vue d’ensemble", "/decider"),
            NavLink("Constitution Citoyenne et Démocratique", "/decider#pacte"),
            NavLink("Délibération citoyenne", "/decider#deliberation"),
            NavLink("Pétitions ouvertes", "/decider#petitions-ouvertes"),
        ),
    )

    /** The daily entries, as in the bottom bar of the phone application. */
    val quick: List<NavEntry> = Space.quickAccess.map { NavEntry(it, iconOf(it), children[it].orEmpty()) }

    /** The rest, as in the menu of the application. */
    val more: List<NavEntry> = Space.more.map { NavEntry(it, iconOf(it), children[it].orEmpty()) }
}
