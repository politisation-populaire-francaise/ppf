package org.politisationpopulairefrancaise.web

import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.AppInfo
import org.politisationpopulairefrancaise.core.Space

/** French labels of the spaces for the web site. */
val spaceLabels: Map<Space, String> = mapOf(
    Space.FIL to "Fil",
    Space.EN_DIRECT to "En direct",
    Space.CARTE to "Carte",
    Space.MESSAGES to "Messages",
    Space.REVENDICATIONS to "Revendications",
    Space.VOIR to "Voir",
    Space.COMPRENDRE to "Comprendre",
    Space.AGIR to "Agir",
    Space.DECIDER to "Décider",
    Space.MON_ESPACE to "Mon espace",
)

/**
 * Common layout of every page: French, accessible landmarks, skip link,
 * self-hosted stylesheet, no script. The content is rendered inside <main>.
 */
fun HTML.siteLayout(pageTitle: String, currentSpace: Space? = null, content: MAIN.() -> Unit) {
    lang = "fr"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        meta(name = "description", content = "Politisation Populaire Française : voir, comprendre, agir, décider.")
        title { +"$pageTitle | ${AppInfo.NAME}" }
        link(rel = "stylesheet", href = "/static/site.css")
        link(rel = "manifest", href = "/static/manifest.webmanifest")
        link(rel = "icon", href = "/static/icon.svg", type = "image/svg+xml")
    }
    body {
        a(href = "#contenu", classes = "skip-link") { +"Aller au contenu" }
        header(classes = "site-header") {
            a(href = "/", classes = "site-title") { +AppInfo.NAME }
            nav {
                attributes["aria-label"] = "Navigation principale"
                ul {
                    Space.primary.forEach { space ->
                        li {
                            a(href = "/${space.id}") {
                                if (space == currentSpace) attributes["aria-current"] = "page"
                                +spaceLabels.getValue(space)
                            }
                        }
                    }
                    li {
                        a(href = "/${Space.MON_ESPACE.id}", classes = "nav-my-space") {
                            if (currentSpace == Space.MON_ESPACE) attributes["aria-current"] = "page"
                            +spaceLabels.getValue(Space.MON_ESPACE)
                        }
                    }
                }
            }
        }
        main {
            id = "contenu"
            content()
        }
        footer(classes = "site-footer") {
            p { +"[EXEMPLE] Squelette de la phase 0. Aucun contenu réel. Version ${AppInfo.VERSION}." }
            p { +"Aucun traceur, aucune ressource tierce : tout est hébergé par Politisation Populaire Française." }
            p {
                +"Textes et images de PPF : licence CC BY 4.0 ; données : Licence Ouverte 2.0. "
                a(href = "/donnees#licence") { +"Réutiliser nos contenus" }
            }
            p { a(href = "/verifier") { +"Vérifier un contenu" } }
            p { a(href = "/donnees") { +"Données ouvertes" } }
            p { a(href = "/transparence") { +"Transparence de PPF" } }
            p { a(href = "/victoires") { +"Nos victoires" } }
            p { a(href = "/presse") { +"Espace presse" } }
            p { a(href = "/tableau-de-bord") { +"Tableau de bord" } }
            p { a(href = "/securite") { +"Signaler une faille de sécurité" } }
        }
    }
}
