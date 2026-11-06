package org.politisationpopulairefrancaise.core

/**
 * The navigation spaces of the platform (cahier fonctionnel, S-05 and M18).
 *
 * The daily entries sit in the bottom bar on phones: the social feed (default screen), the live
 * home, the map, the messages and the demands wall (decisions of the owner, 6 November 2026).
 * The personal dashboard is reached from the avatar of the top bar. The four pillars of the
 * vision (Voir, Comprendre, Agir, Décider) live in the menu.
 *
 * [id] is a stable identifier used in routes and deep links; it never changes
 * even if a label is renamed.
 */
enum class Space(val id: String) {
    FIL("fil"),
    EN_DIRECT("en-direct"),
    CARTE("carte"),
    MESSAGES("messages"),
    REVENDICATIONS("revendications"),
    VOIR("voir"),
    COMPRENDRE("comprendre"),
    AGIR("agir"),
    DECIDER("decider"),
    MON_ESPACE("mon-espace");

    companion object {
        /** The default screen. */
        val default: Space = FIL

        /** Bottom bar on phones, first entries of the rail and of the side bar. */
        val quickAccess: List<Space> = listOf(FIL, EN_DIRECT, CARTE, MESSAGES, REVENDICATIONS)

        /** The four pillars, in the menu. */
        val pillars: List<Space> = listOf(VOIR, COMPRENDRE, AGIR, DECIDER)

        /** Every space with its own page on the web site, in display order. */
        val primary: List<Space> = listOf(FIL, EN_DIRECT, CARTE, MESSAGES, REVENDICATIONS, VOIR, COMPRENDRE, AGIR, DECIDER)

        fun fromId(id: String): Space? = entries.firstOrNull { it.id == id }
    }
}
