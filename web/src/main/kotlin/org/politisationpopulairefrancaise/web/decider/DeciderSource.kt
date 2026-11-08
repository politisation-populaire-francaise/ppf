package org.politisationpopulairefrancaise.web.decider

import org.politisationpopulairefrancaise.core.decider.DeciderCatalog

/**
 * Where the pages of "Décider" read their contents: on the server, the petitions, pacts, candidates'
 * answers and comparators signed by the editorial keys (`EditorialDeciderSource`); in the tests, a fixed
 * catalog.
 */
fun interface DeciderSource {
    suspend fun catalog(): DeciderCatalog
}
