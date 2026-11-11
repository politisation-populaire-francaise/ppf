package org.politisationpopulairefrancaise.ui.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article

/**
 * The « Je veux comprendre… » entries of the home of Comprendre: each theme gathers the contents
 * whose topics belong to it. The topics are the free `topics` of the contents (content/README.md),
 * compared without accents or case, so a new content joins its theme without any change here.
 */
enum class ComprendreTheme(val key: String, internal val topics: Set<String>) {
    RIGHTS("droits", setOf("droits", "justice", "police", "libertes", "recours", "manifestation", "garde-a-vue", "victimes", "preuves", "harcelement", "ordre-public", "securite")),
    INSTITUTIONS("institutions", setOf("institutions", "parlement", "constitution", "elections", "presidentielle", "suffrage", "vote", "vote-blanc", "referendum", "collectivites", "gouvernement", "assemblee-nationale", "loi", "49-3", "europe", "union-europeenne", "laicite", "candidature", "listes", "partis")),
    MONEY("argent-public", setOf("budget", "fiscalite", "impots", "finances-publiques", "economie", "protection-sociale", "retraites", "services-publics", "services", "sante", "argent", "financement")),
    WORK("travail", setOf("travail", "syndicats", "greve", "greves", "salaires", "negociation")),
    LIVING("vie-chere", setOf("logement", "vie-chere", "energie", "transports", "mobilite", "eau", "dechets", "inegalites")),
    MEDIA("medias", setOf("medias", "transparence", "democratie", "pouvoir", "numerique")),
    ORGANISE("s-organiser", setOf("mouvement", "s-organiser", "mobilisation", "strategie", "convaincre", "petitions", "solidarite", "association", "reunion", "affichage")),
    HISTORY("histoire", setOf("histoire", "esclavage", "mouvements", "international")),
}

/** Search and theme filter of the home of Comprendre, entirely on the device (offline-first). */
object ComprendreFinder {
    /** Average reading speed used for the reading time shown on the cards, in words per minute. */
    private const val WORDS_PER_MINUTE = 200

    /** Lower case without French accents, for comparisons. */
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text.lowercase()) {
            out.append(
                when (c) {
                    'à', 'â', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'î', 'ï' -> 'i'
                    'ô', 'ö' -> 'o'
                    'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    'ÿ' -> 'y'
                    '’' -> '\''
                    else -> c
                },
            )
        }
        return out.toString()
    }

    fun isInTheme(article: Article, theme: ComprendreTheme): Boolean = isInTheme(article.topics, theme)

    fun isInTheme(topics: List<String>, theme: ComprendreTheme): Boolean =
        topics.any { normalize(it.trim()) in theme.topics }

    /** True when every word of [query] appears in the title, the summary or the topics. */
    fun matchesQuery(title: String, summary: String?, topics: List<String>, query: String): Boolean {
        val words = normalize(query).split(' ', ',', '\'').map { it.trim() }.filter { it.length >= 2 }
        if (words.isEmpty()) return true
        val haystack = normalize(listOfNotNull(title, summary, topics.joinToString(" ")).joinToString(" "))
        return words.all { it in haystack }
    }

    /** True when the content passes both the theme (if any) and the query (if any). */
    fun matches(title: String, summary: String?, topics: List<String>, theme: ComprendreTheme?, query: String): Boolean =
        (theme == null || isInTheme(topics, theme)) && matchesQuery(title, summary, topics, query)

    fun matches(article: Article, theme: ComprendreTheme?, query: String): Boolean =
        matches(article.title, article.summary, article.topics, theme, query)

    /** Estimated reading time of a text, in whole minutes, at least one. */
    fun readingMinutes(body: String): Int {
        val words = body.split(' ', '\n', '\t').count { it.isNotBlank() }
        return ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1)
    }
}
