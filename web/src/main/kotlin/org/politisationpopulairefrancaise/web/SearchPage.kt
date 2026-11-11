package org.politisationpopulairefrancaise.web

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import org.politisationpopulairefrancaise.web.decider.DeciderLinks
import org.politisationpopulairefrancaise.web.decider.DeciderSource
import org.politisationpopulairefrancaise.web.voir.VoirSource

const val SEARCH_PATH: String = "/recherche"
private const val MAX_SEARCH: Int = 100
private const val PER_SPACE: Int = 20

/** One result of the search: its title, its page and a short line under it. */
data class SearchHit(val title: String, val href: String, val line: String? = null)

/** The results by space, in the order of the menu; an empty space is left out. */
class SearchResults(val query: String, val groups: List<Pair<String, List<SearchHit>>>) {
    val total: Int get() = groups.sumOf { it.second.size }
}

/**
 * GET /recherche?q=: one search over the published contents of the site, read without script. It
 * reuses the searches of the catalogs (Voir, Décider) and the same word matching for Comprendre
 * (accents and case ignored, word beginnings: "manif" finds "manifestation"). Nothing is logged.
 */
fun Route.searchPage(voir: VoirSource?, comprendre: ComprendreSource?, decider: DeciderSource?) {
    get(SEARCH_PATH) {
        val query = call.request.queryParameters["q"]?.trim()?.take(MAX_SEARCH).orEmpty()
        val results = if (FrenchText.words(query).isEmpty()) SearchResults(query, emptyList()) else SearchResults(query, buildList {
            voir?.catalog()?.search(query, PER_SPACE)?.map { SearchHit(it.title, VoirLinks.pathOf(it), it.article.summary) }
                ?.let { add("Voir" to it) }
            comprendre?.catalog()?.let { catalog ->
                val articles: List<Article> = catalog.fiches + catalog.parcours.map { it.article } + catalog.decryptages.map { it.article } +
                    catalog.sheets.map { it.article } + catalog.capTexts.map { it.article } + catalog.stories.map { it.article }
                val hits = articles.filter { FrenchText.matchesAllWords(listOfNotNull(it.title, it.summary).joinToString(" "), query) }
                    .mapNotNull { a -> ComprendreLinks.pathOf(a)?.let { SearchHit(a.title, it, a.summary) } }
                val words = catalog.glossary.search(query).mapNotNull { entry ->
                    ComprendreLinks.pathOf(entry.article)?.let { SearchHit("${entry.term} (glossaire)", it, entry.article.summary) }
                }
                add("Comprendre" to (hits + words).take(PER_SPACE))
            }
            decider?.catalog()?.searchPetitions(query)?.take(PER_SPACE)?.map { SearchHit(it.title, DeciderLinks.petition(it), it.article.summary) }
                ?.let { add("Décider" to it) }
        }.filter { it.second.isNotEmpty() })
        call.respondHtml { searchDocument(results) }
    }
}

fun HTML.searchDocument(results: SearchResults) {
    siteLayout(pageTitle = if (results.query.isEmpty()) "Rechercher" else "Recherche : ${results.query}") {
        h1 { +"Rechercher" }
        form(action = SEARCH_PATH, method = FormMethod.get, classes = "search-form") {
            attributes["role"] = "search"
            label {
                htmlFor = "q"
                +"Chercher dans Voir, Comprendre et Décider"
            }
            input(type = InputType.search, name = "q") {
                id = "q"
                value = results.query
                attributes["maxlength"] = MAX_SEARCH.toString()
            }
            button(type = ButtonType.submit) { +"Chercher" }
        }
        if (results.query.isEmpty()) return@siteLayout
        p {
            attributes["role"] = "status"
            +when (results.total) {
                0 -> "Aucun résultat pour « ${results.query} »."
                1 -> "1 résultat pour « ${results.query} »."
                else -> "${results.total} résultats pour « ${results.query} »."
            }
        }
        results.groups.forEach { (space, hits) ->
            section {
                h2 { +space }
                ul(classes = "card-list") {
                    hits.forEach { hit ->
                        li {
                            a(href = hit.href) { +hit.title }
                            hit.line?.let { p(classes = "muted") { +it } }
                        }
                    }
                }
            }
        }
        p(classes = "muted") { +"Les messages, les salons et vos données restent dans l’application : la recherche du site ne porte que sur les contenus publiés." }
    }
}
