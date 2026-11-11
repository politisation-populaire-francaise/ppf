package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/**
 * The stable web addresses of the contents of "Comprendre" (D-091), in the style of [VoirLinks]: one
 * page per content, built from its identifier (the `d` tag, which never changes when the content is
 * corrected). Used by the web site and, later, by the "Partager" button of the applications.
 *
 * | Content | Path |
 * |---|---|
 * | Practical guide | `/comprendre/fiches/<identifier>` |
 * | Course "Comprendre en 5 minutes" | `/comprendre/parcours/<identifier>` |
 * | Fact-check | `/comprendre/decryptages/<identifier>` |
 * | Story of the history of social movements | `/comprendre/histoire/<identifier>` |
 * | « Qui décide de ça ? » sheet | `/comprendre/qui-decide/<identifier>` |
 * | Entry of the glossary | `/comprendre/glossaire/<identifier without its "glossaire-" prefix>` |
 * | Teaching sheet for the classroom (CO-13) | `/comprendre/enseignants/<identifier>` |
 * | Text of the series « Le cap du mouvement » (D-200) | `/comprendre/cap/<identifier>` |
 *
 * The identifier is percent-encoded like the ones of [VoirLinks] (RFC 3986).
 */
object ComprendreLinks {
    const val HOME: String = "/comprendre"
    const val METHOD: String = "/comprendre/methode"
    const val OFFLINE: String = "/comprendre/hors-ligne"

    const val FICHES: String = "fiches"
    const val PARCOURS: String = "parcours"
    const val DECRYPTAGES: String = "decryptages"
    const val HISTOIRE: String = "histoire"
    const val QUI_DECIDE: String = "qui-decide"
    const val GLOSSAIRE: String = "glossaire"
    const val ENSEIGNANTS: String = "enseignants"
    const val CAP: String = "cap"

    /** The slugs of the glossary all start with this prefix (contents of the third wave); the address leaves it out. */
    const val GLOSSARY_PREFIX: String = "glossaire-"

    /** The segment of each type of content that has a page in "Comprendre"; null for the others. */
    fun segmentOf(type: ArticleType): String? = when (type) {
        ArticleType.FICHE_PRATIQUE -> FICHES
        ArticleType.PARCOURS -> PARCOURS
        ArticleType.DECRYPTAGE -> DECRYPTAGES
        ArticleType.HISTOIRE -> HISTOIRE
        ArticleType.QUI_DECIDE -> QUI_DECIDE
        ArticleType.GLOSSAIRE -> GLOSSAIRE
        ArticleType.FICHE_PEDAGOGIQUE -> ENSEIGNANTS
        ArticleType.CAP -> CAP
        else -> null
    }

    /** The list page of a segment: `/comprendre/fiches`. */
    fun listPath(segment: String): String = "$HOME/$segment"

    /** The page of a content of [type] named [slug], or null when "Comprendre" has no page for that type. */
    fun pathOf(type: ArticleType, slug: String): String? {
        val segment = segmentOf(type) ?: return null
        val identifier = if (type == ArticleType.GLOSSAIRE) slug.removePrefix(GLOSSARY_PREFIX).ifEmpty { slug } else slug
        return "$HOME/$segment/${VoirLinks.encodeSegment(identifier)}"
    }

    /** The page of [article], or null when "Comprendre" has no page for its type. */
    fun pathOf(article: Article): String? = pathOf(article.type, article.slug)

    /** The page of the glossary entry [slug]. */
    fun glossaryPath(slug: String): String = pathOf(ArticleType.GLOSSAIRE, slug)!!

    /** The full address of a page, to share it. */
    fun urlOf(path: String, base: String = VoirLinks.SITE): String = base.trimEnd('/') + path
}
