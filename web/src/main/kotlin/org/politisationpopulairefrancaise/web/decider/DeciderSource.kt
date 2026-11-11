package org.politisationpopulairefrancaise.web.decider

import org.politisationpopulairefrancaise.core.decider.DeciderCatalog

/**
 * Where the pages of "Décider" read their contents: on the server, the petitions, pacts, candidates'
 * answers and comparators signed by the editorial keys (`EditorialDeciderSource`); in the tests, a fixed
 * catalog.
 */
fun interface DeciderSource {
    suspend fun catalog(): DeciderCatalog

    /**
     * The citizen deliberations (DE-25): opened by the editorial team, with the propositions and amendments
     * the server published after the review of a person. None by default (tests, sources without them).
     */
    suspend fun deliberations(): List<org.politisationpopulairefrancaise.core.decider.DeliberationBoard> = emptyList()

    /** The demands signed by the editorial team (D-152), newest first. None by default. */
    suspend fun demands(): List<org.politisationpopulairefrancaise.core.editorial.Article> = emptyList()
}
