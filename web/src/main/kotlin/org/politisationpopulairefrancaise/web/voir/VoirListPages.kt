package org.politisationpopulairefrancaise.web.voir

import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.web.siteLayout

/** The list pages of "Voir": the parties and the public persons, leading to their existing sheets. */
object VoirListLinks {
    const val PARTIES: String = "${VoirLinks.HOME}/partis"
    const val PERSONS: String = "${VoirLinks.HOME}/personnes"
}

fun Route.voirListPages(source: VoirSource) {
    get(VoirListLinks.PARTIES) {
        val catalog = source.catalog()
        call.respondHtml { partiesDocument(catalog) }
    }
    get(VoirListLinks.PERSONS) {
        val catalog = source.catalog()
        call.respondHtml { personsDocument(catalog) }
    }
}

fun HTML.partiesDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Partis politiques", currentSpace = Space.VOIR) {
        h1 { +"Partis politiques" }
        p(classes = "lead") { +"Chaque fiche présente les mêmes rubriques, dans le même ordre, quel que soit le parti." }
        val parties = catalog.parties.sortedBy { FrenchText.fold(it.name) }
        if (parties.isEmpty()) p(classes = "muted") { +"Aucune fiche de parti publiée pour l’instant." }
        div(classes = "card-grid") {
            parties.forEach { party ->
                article(classes = "card") {
                    h2(classes = "card-title") {
                        a(href = VoirLinks.pathOf(VoirItem.OfParty(party))) {
                            +party.name
                            party.acronym?.takeIf { it.isNotBlank() && it != party.name }?.let { +" ($it)" }
                        }
                    }
                    party.article.summary?.let { p { +it } }
                    val members = catalog.membersOf(party.address).size
                    if (members > 0) p(classes = "muted") { +(if (members == 1) "1 responsable public avec une fiche" else "$members responsables publics avec une fiche") }
                }
            }
        }
        p { a(href = VoirListLinks.PERSONS) { +"Tous les responsables publics" } }
    }
}

fun HTML.personsDocument(catalog: VoirCatalog) {
    siteLayout(pageTitle = "Responsables publics", currentSpace = Space.VOIR) {
        h1 { +"Responsables publics" }
        p(classes = "lead") { +"Chaque fiche présente les mêmes rubriques, dans le même ordre, quelle que soit la personne." }
        val persons = catalog.persons(null).sortedBy { FrenchText.fold(it.name) }
        if (persons.isEmpty()) p(classes = "muted") { +"Aucune fiche publiée pour l’instant." }
        div(classes = "card-grid") { persons.forEach { personCard(it, catalog, level = 2) } }
        p { a(href = VoirListLinks.PARTIES) { +"Les partis politiques" } }
    }
}
