package org.politisationpopulairefrancaise.web

import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.web.comprendre.ComprendreSource
import org.politisationpopulairefrancaise.web.comprendre.comprendrePages
import org.politisationpopulairefrancaise.web.voir.VoirSource
import org.politisationpopulairefrancaise.web.voir.voirPages

/** French description of each space, same wording as the applications. */
private val spaceDescriptions: Map<Space, String> = mapOf(
    Space.FIL to "Les publications des comptes que vous suivez, les mobilisations et les contenus du moment.",
    Space.EN_DIRECT to "Ce qu’il faut savoir en moins de trente secondes : chiffres clés, prochaines mobilisations, alertes.",
    Space.CARTE to "Manifestations, rassemblements et lieux utiles près de chez vous. Bientôt disponible.",
    Space.MESSAGES to "Conversations privées et de groupe chiffrées de bout en bout, salons des collectifs, flux des médias et des élus suivis. Bientôt disponible.",
    Space.REVENDICATIONS to "Le mur des revendications : les citoyens proposent ce qui doit changer et votent, un compte, une voix.",
    Space.VOIR to "Rendre visible, de façon chiffrée et sourcée, ce qui se passe et ce que fait le pouvoir.",
    Space.COMPRENDRE to "Qui décide de quoi, comment fonctionnent les institutions, vos droits, le vrai et le faux.",
    Space.AGIR to "Se mobiliser, s’organiser, s’entraider, témoigner, communiquer.",
    Space.DECIDER to "Revendiquer, faire campagne, obliger les élus à s’engager, décider ensemble.",
    Space.MON_ESPACE to "Votre tableau de bord personnel : votre territoire, vos suivis, vos votes. Tout reste sur votre appareil.",
)

/**
 * Routes of the web site. Mounted by the server. With a [voir] source (a database is configured),
 * the pages of "Voir" replace the placeholder of the space; likewise the pages of "Comprendre"
 * with a [comprendre] source (D-091), which also serves the service worker of offline reading.
 */
fun Route.webRoutes(voir: VoirSource? = null, comprendre: ComprendreSource? = null) {
    staticResources("/static", "static")
    voir?.let { voirPages(it) }
    comprendre?.let { comprendrePages(it) }

    get("/") {
        call.respondHtml {
            siteLayout(pageTitle = "Accueil") {
                h1 { +"Voir, comprendre, agir, décider" }
                p { +"Politisation Populaire Française est un collectif citoyen qui veut redonner l’espace politique aux citoyens." }
                section {
                    h2 { +"Les espaces de la plateforme" }
                    ul {
                        Space.entries.forEach { space ->
                            li {
                                +"${spaceLabels.getValue(space)} : ${spaceDescriptions.getValue(space)}"
                            }
                        }
                    }
                }
            }
        }
    }

    Space.entries.filter { (voir == null || it != Space.VOIR) && (comprendre == null || it != Space.COMPRENDRE) }.forEach { space ->
        get("/${space.id}") {
            call.respondHtml {
                siteLayout(pageTitle = spaceLabels.getValue(space), currentSpace = space) {
                    h1 { +spaceLabels.getValue(space) }
                    p { +spaceDescriptions.getValue(space) }
                    p { +"[EXEMPLE] Cette page est un emplacement réservé de la phase 0." }
                }
            }
        }
    }
}
