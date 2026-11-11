package org.politisationpopulairefrancaise.core.agir.kits

/** What a kit helps to do: the filters of « Kits d'action citoyenne » (D-232). */
enum class KitGoal(val id: String) {
    MANIFESTER("manifester"),
    GREVE("greve"),
    ORGANISER("s-organiser"),
    CONVAINCRE("convaincre"),
    SE_PRESENTER("se-presenter"),
    SE_PROTEGER("se-proteger"),
}

/** What a kit opens: a rubric of « Agir » (by its id) or a practical guide (by its slug), read inside « Agir ». */
sealed interface KitTarget {
    data class Rubric(val id: String) : KitTarget

    data class Guide(val slug: String) : KitTarget
}

/**
 * One kit of « Kits d'action citoyenne »: something that already exists in the application, a tool of « Agir »
 * or a signed practical guide, gathered here and filtered by [goals]. [offline]: usable without network (the
 * guides are bundled with the application, the tools work on the device).
 */
data class CitizenKit(val id: String, val goals: Set<KitGoal>, val offline: Boolean, val target: KitTarget)

/**
 * The kits (demand of the owner, 11 November 2026: « dans la rubrique Agir, il faut créer une sous-rubrique
 * "Kits d'actions citoyenne" et dedans on mettrait tous les kits qu'on pourrait filtrer de manière pertinente »).
 * Nothing is removed from the rubrics: each kit stays where it is and is also listed here. Tools first, then guides,
 * in the order of the goals.
 */
object CitizenKits {
    private fun rubric(id: String, offline: Boolean, vararg goals: KitGoal) = CitizenKit(id, goals.toSet(), offline, KitTarget.Rubric(id))
    private fun guide(slug: String, vararg goals: KitGoal) = CitizenKit(slug, goals.toSet(), offline = true, target = KitTarget.Guide(slug))

    /** The rubric of the posters and tracts with a QR code (AG-61): made from a mobilization of the agenda. */
    const val POSTER: String = "affiche-qr"

    val all: List<CitizenKit> = listOf(
        // The tools of « Agir ».
        rubric("mode-manifestation", true, KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        CitizenKit(POSTER, setOf(KitGoal.MANIFESTER, KitGoal.ORGANISER, KitGoal.CONVAINCRE), offline = true, target = KitTarget.Rubric("agenda")),
        rubric("simulateur-greve", true, KitGoal.GREVE),
        rubric("solidarite", true, KitGoal.GREVE, KitGoal.ORGANISER),
        rubric("se-reunir", false, KitGoal.ORGANISER),
        rubric("collectifs", false, KitGoal.ORGANISER),
        rubric("federer", true, KitGoal.CONVAINCRE),
        rubric("s-engager", true, KitGoal.SE_PRESENTER),
        rubric("harcelement", true, KitGoal.SE_PROTEGER),
        // The practical guides, bundled with the application.
        guide("droits-en-manifestation", KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        guide("declarer-une-manifestation", KitGoal.MANIFESTER, KitGoal.ORGANISER),
        guide("garde-a-vue", KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        guide("filmer-les-forces-de-l-ordre", KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        guide("apres-une-blessure-en-manifestation", KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        guide("securite-numerique-des-militants", KitGoal.MANIFESTER, KitGoal.SE_PROTEGER),
        guide("faire-greve-vos-droits", KitGoal.GREVE),
        guide("creer-une-association", KitGoal.ORGANISER),
        guide("organiser-une-reunion-publique", KitGoal.ORGANISER),
        guide("coller-des-affiches-et-distribuer-des-tracts", KitGoal.ORGANISER, KitGoal.CONVAINCRE),
        guide("collecter-de-l-argent-pour-une-cause", KitGoal.ORGANISER, KitGoal.GREVE),
        guide("lancer-une-petition-locale", KitGoal.ORGANISER),
        guide("convaincre-autour-de-soi", KitGoal.CONVAINCRE),
        guide("creer-ou-rejoindre-un-parti", KitGoal.SE_PRESENTER),
        guide("constituer-une-liste-citoyenne", KitGoal.SE_PRESENTER),
        guide("se-presenter-departementales-regionales-2028", KitGoal.SE_PRESENTER),
        guide("se-presenter-aux-legislatives", KitGoal.SE_PRESENTER),
        guide("voter-par-procuration", KitGoal.SE_PRESENTER),
        guide("harcelement-en-ligne-et-divulgation", KitGoal.SE_PROTEGER),
        guide("aide-juridictionnelle", KitGoal.SE_PROTEGER),
        guide("saisir-le-defenseur-des-droits", KitGoal.SE_PROTEGER),
        guide("treve-hivernale", KitGoal.SE_PROTEGER),
    )

    /** The kits for [goal] (null: all), only those usable without network when [offlineOnly]; the order is kept. */
    fun filter(goal: KitGoal?, offlineOnly: Boolean): List<CitizenKit> =
        all.filter { (goal == null || goal in it.goals) && (!offlineOnly || it.offline) }
}
