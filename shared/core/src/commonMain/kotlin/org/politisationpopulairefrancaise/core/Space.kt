package org.politisationpopulairefrancaise.core

/**
 * The navigation spaces of the platform (cahier fonctionnel, S-05 and M18).
 *
 * The daily entries sit in the bottom bar on phones, in this order (decision of the owner,
 * 11 November 2026, D-215): the live home (first screen after the tutorial), the news feed
 * ("Actu"), the map in the centre, "Agir" and the wall of calls ("Appels"). The messages sit in the
 * top bar, beside the AIDE button; the personal dashboard is reached from the avatar. The other
 * pillars of the vision (Voir, Comprendre, Décider) and the demands wall live in the menu:
 * nothing already built is removed (D-210).
 *
 * [id] is a stable identifier used in routes and deep links; it never changes
 * even if a label is renamed ("fil" is shown as "Actu" since D-215).
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
    MON_ESPACE("mon-espace"),

    /** The wall of calls of "Agir" (strike, demonstrate, support, block…), on its own (D-215). */
    APPELS("appels");

    companion object {
        /** The screen shown after the tutorial, and where the system's back leads. */
        val default: Space = EN_DIRECT

        /** Bottom bar on phones, first entries of the rail and of the side bar (D-215). */
        val quickAccess: List<Space> = listOf(EN_DIRECT, FIL, CARTE, AGIR, APPELS)

        /** The four pillars of the vision. */
        val pillars: List<Space> = listOf(VOIR, COMPRENDRE, AGIR, DECIDER)

        /**
         * The rest of the navigation, after the daily entries: the pillars that are not already among
         * them, then the demands wall. Shown in the menu of phones, under the daily entries of the
         * rail and of the side bar. The messages are in the top bar, "Mon espace" behind the avatar.
         */
        val more: List<Space> = pillars.filterNot { it in quickAccess } + REVENDICATIONS

        /** Every space with its own page on the web site, in display order. */
        val primary: List<Space> = quickAccess + more.filterNot { it in quickAccess } + MESSAGES

        fun fromId(id: String): Space? = entries.firstOrNull { it.id == id }
    }
}
