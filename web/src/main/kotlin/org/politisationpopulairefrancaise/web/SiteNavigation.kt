package org.politisationpopulairefrancaise.web

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/** A sub-rubric of the side menu: its label and its page; without page, it is announced as coming. */
data class NavLink(val label: String, val href: String?)

/**
 * One rubric of the side menu, as in the application (D-240): its label, its icon (an id of
 * `/static/icons.svg`, the Lucide icon of the application), its home page when it has one, the
 * spaces of the site it covers (to open it on their pages) and its sub-rubrics, in an accordion.
 */
data class NavRubric(
    val id: String,
    val label: String,
    val icon: String,
    val home: String?,
    val spaces: Set<Space>,
    val children: List<NavLink>,
) {
    /** Where the rubric leads in the rail of icons: its home, else its first page. */
    val href: String get() = home ?: children.firstNotNullOf { it.href }
}

/**
 * The menu of the site, the same as the one of the application (owner's session « Ergonomie »,
 * D-240): « En direct », then the seven rubrics from getting informed to acting, with the same
 * labels and in the same order (`ui/shell/MenuTree.kt`, `strings_menu.xml`). A sub-rubric that only
 * exists in the application leads to the page of its space, which says so.
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

    private fun fiche(list: String): String = "${ComprendreLinks.HOME}/$list"

    val rubrics: List<NavRubric> = listOf(
        NavRubric(
            "s-informer", "S’informer", "newspaper", VoirLinks.HOME, setOf(Space.VOIR, Space.FIL),
            listOf(
                NavLink("Accueil de S’informer", VoirLinks.HOME),
                NavLink("Actu", "/${Space.FIL.id}"),
                NavLink("Le mouvement", "${VoirLinks.HOME}#mouvement"),
                NavLink("Le pouvoir et le Parlement", "${VoirLinks.HOME}#pouvoir"),
                NavLink("Partis politiques", "${VoirLinks.HOME}/partis"),
                NavLink("Responsables publics", "${VoirLinks.HOME}/personnes"),
                NavLink("Textes en discussion", "${VoirLinks.HOME}#pouvoir"),
                NavLink("Libertés et contre-pouvoirs", "${VoirLinks.HOME}#libertes"),
                NavLink("Vérifier une info reçue", "/verifier"),
                NavLink("Transparence de PPF", "/transparence"),
            ),
        ),
        NavRubric(
            "comprendre", "Comprendre", "book-open", ComprendreLinks.HOME, setOf(Space.COMPRENDRE),
            listOf(
                NavLink("Accueil de Comprendre", ComprendreLinks.HOME),
                NavLink("Le cap du mouvement", fiche(ComprendreLinks.CAP)),
                NavLink("Comprendre en 5 minutes", fiche(ComprendreLinks.PARCOURS)),
                NavLink("Qui décide ?", fiche(ComprendreLinks.QUI_DECIDE)),
                NavLink("Mes élus", ComprendreLinks.HOME),
                NavLink("Connaître ses droits", fiche(ComprendreLinks.FICHES)),
                NavLink("Le vrai du faux", fiche(ComprendreLinks.DECRYPTAGES)),
                NavLink("Histoire des luttes", fiche(ComprendreLinks.HISTOIRE)),
                NavLink("Glossaire", fiche(ComprendreLinks.GLOSSAIRE)),
            ),
        ),
        NavRubric(
            "temoigner", "Témoigner", "camera", null, emptySet(),
            listOf(
                NavLink("Envoyer un témoignage", "/agir#temoigner"),
                NavLink("Caméra protégée", HELP_PATH),
                NavLink("Harcèlement en ligne", HELP_PATH),
                NavLink("Appels à témoins", null),
                NavLink("Mes témoignages", null),
            ),
        ),
        NavRubric(
            "revendiquer", "Revendiquer", "megaphone", "/revendications", setOf(Space.REVENDICATIONS),
            listOf(
                NavLink("Toutes les revendications", "/revendications"),
                NavLink("Proposer une revendication", "/revendications"),
                NavLink("Mes revendications", "/revendications"),
                NavLink("Constitution Citoyenne et Démocratique", "/decider#pacte"),
                NavLink("Pétitions officielles", "/decider#petitions-ouvertes"),
                NavLink("Pétitions locales", "/decider"),
                NavLink("Consultations publiques", "/decider#consultations"),
            ),
        ),
        NavRubric(
            "s-unir", "S’unir et s’organiser", "users", null, setOf(Space.MESSAGES),
            listOf(
                NavLink("Messages", "/${Space.MESSAGES.id}"),
                NavLink("Collectifs", "/agir/collectifs"),
                NavLink("Associations et syndicats", "/organisations"),
                NavLink("Se réunir", "/agir#collectifs"),
                NavLink("Fédérer mon entourage", "/agir"),
                NavLink("Mes proches à mobiliser", "/agir"),
                NavLink("Solidarité et caisses de grève", "/agir/solidarite"),
            ),
        ),
        NavRubric(
            "se-politiser", "Se politiser", "landmark", "/decider", setOf(Space.DECIDER),
            listOf(
                NavLink("S’engager en politique", "/agir/s-engager"),
                NavLink("Prochaines élections", "/decider#calendrier"),
                NavLink("Comparer les candidats", "/decider#comparateur"),
                NavLink("Interpeller mes élus", "/decider#courriers"),
                NavLink("Demander un document administratif", "/decider"),
            ),
        ),
        NavRubric(
            "agir", "Agir", "hand-fist", "/agir", setOf(Space.AGIR, Space.APPELS, Space.CARTE),
            listOf(
                NavLink("Accueil d’Agir", "/agir"),
                NavLink("Appels à la mobilisation", "/appels"),
                NavLink("Carte et agenda", "/agenda"),
                NavLink("Kits d’action citoyenne", "/agir/kits"),
                NavLink("Simulateur du gréviste", "/agir"),
                NavLink("Mode manifestation", HELP_PATH),
            ),
        ),
    )
}
