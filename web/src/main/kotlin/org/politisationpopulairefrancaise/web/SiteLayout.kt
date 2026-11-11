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
    // "Actu" is the label of the feed since D-215; its stable id stays "fil".
    Space.FIL to "Actu",
    Space.EN_DIRECT to "En direct",
    Space.CARTE to "Carte",
    Space.MESSAGES to "Messages",
    Space.REVENDICATIONS to "Revendications",
    Space.VOIR to "Voir",
    Space.COMPRENDRE to "Comprendre",
    Space.AGIR to "Agir",
    Space.DECIDER to "Décider",
    Space.MON_ESPACE to "Mon espace",
    Space.APPELS to "Appels",
)

/** The preview of a shared link: the description, the image (absolute, on the main domain) and its alternative text. */
internal const val SHARE_DESCRIPTION = "Voir, comprendre, agir, décider. Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement."
internal const val SHARE_IMAGE = "https://politisation-populaire-francaise.org/static/og-image-1200x630.png"
internal const val SHARE_IMAGE_ALT = "Logo de Politisation Populaire Française : une Marianne coiffée du bonnet phrygien brandit le drapeau français au milieu d’une foule de citoyens et de musiciens."

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
        // The official logo of PPF (design/identite/logo-officiel, D-187), served by our own server only: on a
        // white disc, readable on light and dark tabs; the icon of an iPhone home screen.
        link(rel = "icon", href = "/favicon.ico") { attributes["sizes"] = "16x16 32x32 48x48" }
        link(rel = "icon", href = "/static/favicon-32x32.png", type = "image/png") { attributes["sizes"] = "32x32" }
        link(rel = "apple-touch-icon", href = "/static/apple-touch-icon.png")
        meta(name = "theme-color") {
            attributes["content"] = "#2B3A8F"
            attributes["media"] = "(prefers-color-scheme: light)"
        }
        meta(name = "theme-color") {
            attributes["content"] = "#0F1220"
            attributes["media"] = "(prefers-color-scheme: dark)"
        }
        // The preview of a shared link (messaging, social networks): an image of our own domain.
        meta { attributes["property"] = "og:type"; attributes["content"] = "website" }
        meta { attributes["property"] = "og:site_name"; attributes["content"] = AppInfo.NAME }
        meta { attributes["property"] = "og:title"; attributes["content"] = "$pageTitle | ${AppInfo.NAME}" }
        meta { attributes["property"] = "og:description"; attributes["content"] = SHARE_DESCRIPTION }
        meta { attributes["property"] = "og:image"; attributes["content"] = SHARE_IMAGE }
        meta { attributes["property"] = "og:image:width"; attributes["content"] = "1200" }
        meta { attributes["property"] = "og:image:height"; attributes["content"] = "630" }
        meta { attributes["property"] = "og:image:alt"; attributes["content"] = SHARE_IMAGE_ALT }
        meta(name = "twitter:card", content = "summary_large_image")
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
