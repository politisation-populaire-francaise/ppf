package org.politisationpopulairefrancaise.web.voir

import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks

/**
 * Where the pages of "Voir" read their contents: provided by the server, which assembles the
 * catalog from the editorial contents signed by the trusted editorial keys only (D-034).
 */
fun interface VoirSource {
    suspend fun catalog(): VoirCatalog
}

/**
 * The contents of a catalog by the path segment of their kind and their identifier, as the
 * addresses of [VoirLinks] name them. When two trusted keys used the same identifier for the same
 * kind, the most recent version is the one the address leads to.
 */
class VoirIndex(val catalog: VoirCatalog) {
    private val bySlug: Map<Pair<String, String>, VoirItem> = buildList {
        catalog.indicators.forEach { add(VoirItem.OfIndicator(it)) }
        catalog.facts.forEach { add(VoirItem.OfFact(it)) }
        catalog.persons.forEach { add(VoirItem.OfPerson(it)) }
        catalog.parties.forEach { add(VoirItem.OfParty(it)) }
        catalog.texts.forEach { add(VoirItem.OfText(it)) }
        catalog.movements.forEach { add(VoirItem.OfMovement(it)) }
        catalog.dossiers.forEach { add(VoirItem.OfDossier(it)) }
    }.groupBy { VoirLinks.segmentOf(it) to it.address.identifier }
        .mapValues { (_, items) -> items.maxBy { updatedAt(it) } }

    fun find(segment: String, identifier: String): VoirItem? = bySlug[segment to identifier]

    private fun updatedAt(item: VoirItem): Long = when (item) {
        is VoirItem.OfIndicator -> item.indicator.article.updatedAt
        is VoirItem.OfFact -> item.fact.article.updatedAt
        is VoirItem.OfPerson -> item.person.article.updatedAt
        is VoirItem.OfParty -> item.party.article.updatedAt
        is VoirItem.OfText -> item.text.article.updatedAt
        is VoirItem.OfMovement -> item.movement.article.updatedAt
        is VoirItem.OfDossier -> item.dossier.article.updatedAt
    }
}
