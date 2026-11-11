package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin

/**
 * What a text of the series « Le cap du mouvement » is (docs/ROADMAP-MVP1.md, section 5; D-200): sourced facts,
 * or a position of PPF. The applications and the site show it in the label of origin (D-212): a position
 * written with the help of an AI reads « Position de PPF, rédigée par l'IA, en attente de relecture ».
 */
enum class CapNature(val tagValue: String) {
    FACTS("faits"),
    POSITION("position"),
    ;

    companion object {
        fun fromTag(value: String?): CapNature? = entries.firstOrNull { it.tagValue == value?.trim() }
    }
}

/**
 * A text of the series « Le cap du mouvement » (`content/cap`, [ArticleType.CAP]): short, sourced, linked to an
 * action of the application, read in the order of its [rank].
 */
data class CapText(
    val article: Article,
    val nature: CapNature,
    /** The place of the text in the series, from 1. */
    val rank: Int,
) {
    /** True for a position of PPF written with the help of an AI and not yet reviewed by a human. */
    val isUnreviewedAiPosition: Boolean
        get() = nature == CapNature.POSITION && article.origin == ContentOrigin.AI_ASSISTED

    companion object {
        /** The tag of the nature (`faits` or `position`). */
        const val TAG_NATURE: String = "ppf-nature"

        /** The tag of the rank in the series. */
        const val TAG_RANK: String = "ppf-rank"

        /** Null when the content is not a text of the series or misses its nature or rank: it is then not shown. */
        fun from(article: Article): CapText? {
            if (article.type != ArticleType.CAP) return null
            val nature = CapNature.fromTag(article.event.firstTagValue(TAG_NATURE)) ?: return null
            val rank = article.event.firstTagValue(TAG_RANK)?.trim()?.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return CapText(article, nature, rank)
        }

        /** The series in reading order: by rank, then by title. */
        fun ordered(texts: List<CapText>): List<CapText> = texts.sortedWith(compareBy<CapText>({ it.rank }, { it.article.title.lowercase() }))
    }
}
