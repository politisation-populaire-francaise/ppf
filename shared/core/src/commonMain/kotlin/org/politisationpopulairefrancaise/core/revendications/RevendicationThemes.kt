package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * A broad theme of the demands wall (DE-01: "filtres par thème"; views by theme asked by the
 * owner on 2026-11-06). A demand carries topics (`t` tags); it belongs to a theme when one of its
 * topics is the theme's [id] or one of its [aliases]. New demands are proposed with theme ids, so
 * aliases only bridge older or free topics ("ecole" → "education").
 *
 * The id is stable: it is written in signed events and in the views saved on the device; the
 * label shown to people lives in the interface's texts (one string per id).
 */
data class RevendicationTheme(val id: String, val aliases: Set<String> = emptySet()) {
    init {
        require(id.isNotEmpty() && id.all { it in 'a'..'z' || it == '-' }) { "A theme id is made of lowercase letters and dashes" }
    }

    /** True when [topic] (any spelling: accents, case, spaces) names this theme. */
    fun matches(topic: String): Boolean {
        val folded = RevendicationThemes.foldTopic(topic)
        return folded == id || folded in aliases
    }
}

/**
 * The themes proposed for the wall. The list is a proposal of the development team, to be
 * confirmed by the owner (brainstorming of the cahier fonctionnel, entry of 2026-11-06 on views
 * and filters): adding a theme is free, renaming an id means migrating the saved views.
 */
object RevendicationThemes {
    val POUVOIR_D_ACHAT = RevendicationTheme("pouvoir-d-achat", setOf("vie-chere", "prix", "inflation", "energie", "consommation"))
    val TRAVAIL = RevendicationTheme("travail", setOf("emploi", "salaires", "salaire", "chomage", "retraites", "retraite", "syndicats"))
    val SANTE = RevendicationTheme("sante", setOf("medical", "medecine", "hopital", "hopitaux", "soins", "medicaments", "sante-mentale"))
    val EDUCATION = RevendicationTheme("education", setOf("ecole", "ecoles", "universite", "enseignement", "recherche", "cantine", "cantines"))
    val LOGEMENT = RevendicationTheme("logement", setOf("loyers", "loyer", "hlm", "sans-abri", "habitat"))
    val TRANSPORTS = RevendicationTheme("transports", setOf("transport", "mobilite", "metro", "train", "trains", "bus"))
    val ENVIRONNEMENT = RevendicationTheme("environnement", setOf("climat", "ecologie", "biodiversite", "eau", "pollution"))
    val SERVICES_PUBLICS = RevendicationTheme("services-publics", setOf("service-public", "administration", "poste"))
    val DEMOCRATIE = RevendicationTheme("democratie", setOf("institutions", "transparence", "elections", "ric", "referendum", "constitution"))
    val JUSTICE = RevendicationTheme("justice", setOf("libertes", "police", "droits-humains", "droits-de-l-homme"))
    val SOCIETE = RevendicationTheme("societe", setOf("egalite", "discriminations", "solidarite", "famille", "familles", "handicap", "jeunesse", "femmes"))
    val FISCALITE = RevendicationTheme("fiscalite", setOf("impots", "impot", "taxes", "finances-publiques", "budget"))
    val AGRICULTURE = RevendicationTheme("agriculture", setOf("alimentation", "agriculteurs", "paysans"))
    val NUMERIQUE = RevendicationTheme("numerique", setOf("internet", "donnees", "vie-privee"))
    val CULTURE = RevendicationTheme("culture", setOf("medias", "sport", "patrimoine"))

    /** Every theme, in the order the interface lists them. */
    val ALL: List<RevendicationTheme> = listOf(
        SOCIETE, SANTE, EDUCATION, TRAVAIL, POUVOIR_D_ACHAT, LOGEMENT, TRANSPORTS, ENVIRONNEMENT,
        SERVICES_PUBLICS, DEMOCRATIE, JUSTICE, FISCALITE, AGRICULTURE, NUMERIQUE, CULTURE,
    )

    private val byId: Map<String, RevendicationTheme> = ALL.associateBy { it.id }

    fun byId(id: String): RevendicationTheme? = byId[id]

    /** The themes of a list of topics, in the order of [ALL], without repetition. */
    fun of(topics: List<String>): List<RevendicationTheme> = ALL.filter { theme -> topics.any(theme::matches) }

    /** The themes of a demand. */
    fun of(article: Article): List<RevendicationTheme> = of(article.topics)

    /** A topic as compared with theme ids: folded, words joined by dashes ("Vie chère" → "vie-chere"). */
    fun foldTopic(topic: String): String = FrenchText.fold(topic).replace(' ', '-')
}
