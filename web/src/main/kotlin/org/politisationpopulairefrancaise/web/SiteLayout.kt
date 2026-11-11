package org.politisationpopulairefrancaise.web

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.HTMLTag
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
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

/** The disclaimer under the menu of the application, word for word. */
internal const val NAV_DISCLAIMER = "Plateforme citoyenne indépendante, sans lien avec l’État ni le gouvernement."

/** An icon of the site sprite (the Lucide icons of the application), decorative: the text beside it says what it is. */
internal fun HTMLTag.icon(name: String, classes: String = "icon") {
    unsafe { +"<svg class=\"$classes\" aria-hidden=\"true\" focusable=\"false\"><use href=\"/static/icons.svg#$name\"></use></svg>" }
}

/**
 * Common layout of every page, built like the desktop application (D-215): a top bar with the logo,
 * the red AIDE button, Messages and Mon espace; a side menu in the order of the application, each
 * pillar opening its sub-sections in an accordion (`details`). The menu folds into a rail of icons
 * to leave room to the content; on a phone it opens over the page. All of it without script (a
 * check box and CSS), with the self-hosted stylesheet and icons only. The content goes in <main>.
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
        // The switch of the menu, before everything it acts on (CSS sibling selectors): on a phone it opens the
        // menu over the page, on a wider screen it folds the menu into a rail of icons.
        input(type = InputType.checkBox, classes = "nav-toggle") {
            id = "nav-toggle"
            attributes["aria-label"] = "Afficher ou réduire le menu"
            attributes["aria-controls"] = "menu"
        }
        header(classes = "topbar") {
            label(classes = "menu-button") {
                htmlFor = "nav-toggle"
                attributes["aria-hidden"] = "true"
                icon("menu")
                span(classes = "menu-button-label") { +"Menu" }
            }
            a(href = "/", classes = "brand") {
                img(src = "/static/icon-192.png", alt = "", classes = "brand-logo") {
                    attributes["width"] = "36"
                    attributes["height"] = "36"
                }
                span(classes = "brand-name") { +AppInfo.NAME }
            }
            div(classes = "top-actions") {
                a(href = SiteNavigation.HELP_PATH, classes = "help-button") {
                    attributes["aria-label"] = "AIDE : urgence, avocat, numéros utiles"
                    +"AIDE"
                }
                topIcon(Space.MESSAGES, currentSpace)
                topIcon(Space.MON_ESPACE, currentSpace)
            }
        }
        div(classes = "shell") {
            nav(classes = "sidenav") {
                id = "menu"
                attributes["aria-label"] = "Navigation principale"
                ul(classes = "nav-list") { SiteNavigation.quick.forEach { li { navEntry(it, currentSpace) } } }
                div(classes = "nav-divider") { attributes["role"] = "separator" }
                ul(classes = "nav-list") { SiteNavigation.more.forEach { li { navEntry(it, currentSpace) } } }
                p(classes = "nav-disclaimer") { +NAV_DISCLAIMER }
            }
            // On a phone, a tap beside the open menu closes it.
            label(classes = "nav-backdrop") {
                htmlFor = "nav-toggle"
                attributes["aria-hidden"] = "true"
            }
            div(classes = "page") {
                main {
                    id = "contenu"
                    content()
                }
                siteFooter()
            }
        }
    }
}

private fun FlowContent.topIcon(space: Space, currentSpace: Space?) {
    a(href = "/${space.id}", classes = "top-icon") {
        if (space == currentSpace) attributes["aria-current"] = "page"
        icon(SiteNavigation.iconOf(space))
        span(classes = "top-icon-label") { +spaceLabels.getValue(space) }
    }
}

private fun LI.navEntry(entry: NavEntry, currentSpace: Space?) {
    val current = entry.space == currentSpace
    if (entry.children.isEmpty()) {
        a(href = entry.href, classes = "nav-link") {
            if (current) attributes["aria-current"] = "page"
            icon(entry.icon)
            span(classes = "nav-label") { +entry.label }
        }
        return
    }
    // In the rail of icons, the pillar is a plain link to its page.
    a(href = entry.href, classes = "nav-link nav-rail-link") {
        if (current) attributes["aria-current"] = "page"
        attributes["title"] = entry.label
        icon(entry.icon)
        span(classes = "nav-label") { +entry.label }
    }
    details(classes = "nav-group") {
        if (current) attributes["open"] = "open"
        summary(classes = "nav-link" + if (current) " is-current" else "") {
            icon(entry.icon)
            span(classes = "nav-label") { +entry.label }
            icon("chevron-down", classes = "icon chevron")
        }
        ul(classes = "nav-sub") {
            entry.children.forEachIndexed { index, child ->
                li {
                    a(href = child.href) {
                        // The first link is the page of the space itself.
                        if (current && index == 0) attributes["aria-current"] = "page"
                        +child.label
                    }
                }
            }
        }
    }
}

private fun FlowContent.siteFooter() {
    footer(classes = "site-footer") {
        div(classes = "footer-columns") {
            footerColumn("La plateforme", listOf(
                NavLink("Recevoir de l’aide", SiteNavigation.HELP_PATH),
                NavLink("Vérifier un contenu", "/verifier"),
                NavLink("Données ouvertes", "/donnees"),
                NavLink("Lire hors ligne", "/comprendre/hors-ligne"),
            ))
            footerColumn("PPF", listOf(
                NavLink("Transparence de PPF", "/transparence"),
                NavLink("Nos victoires", "/victoires"),
                NavLink("Espace presse", "/presse"),
                NavLink("Tableau de bord", "/tableau-de-bord"),
            ))
            footerColumn("Informations légales", LegalPages.ALL.map { NavLink(it.footerLabel, it.path) })
            footerColumn("Sécurité", listOf(
                NavLink("Signaler une faille de sécurité", "/securite"),
                NavLink("Réutiliser nos contenus", "/donnees#licence"),
            ))
        }
        div(classes = "footer-bottom") {
            p { +"$NAV_DISCLAIMER Aucun traceur, aucune ressource tierce : tout est hébergé par ${AppInfo.NAME}." }
            p { +"Textes et images de PPF : licence CC BY 4.0 ; données : Licence Ouverte 2.0. Version ${AppInfo.VERSION}." }
        }
    }
}

private fun FlowContent.footerColumn(title: String, links: List<NavLink>) {
    div(classes = "footer-column") {
        h2 { +title }
        ul { links.forEach { link -> li { a(href = link.href) { +link.label } } } }
    }
}
