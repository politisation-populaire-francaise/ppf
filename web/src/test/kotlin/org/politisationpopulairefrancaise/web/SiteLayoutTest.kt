package org.politisationpopulairefrancaise.web

import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.Space
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteLayoutTest {

    private fun render(space: Space? = null): String = createHTML().html {
        siteLayout(pageTitle = "Test", currentSpace = space) {
            h1 { +"Titre de test" }
        }
    }

    @Test
    fun layoutIsFrenchAndAccessible() {
        val html = render()
        assertTrue(html.contains("lang=\"fr\""))
        assertTrue(html.contains("class=\"skip-link\""))
        assertTrue(html.contains("aria-label=\"Navigation principale\""))
        assertTrue(html.contains("<main id=\"contenu\">"))
    }

    @Test
    fun currentSpaceIsMarked() {
        // A space without sub-sections is a plain link.
        assertTrue(render(Space.CARTE).contains("<a href=\"/carte\" class=\"nav-link\" aria-current=\"page\">"))
        // A pillar opens its accordion, and its first sub-section (its own page) is marked.
        val html = render(Space.AGIR)
        assertTrue(html.contains("<details class=\"nav-group\" open=\"open\">"))
        assertTrue(html.contains("<a href=\"/agir\" aria-current=\"page\">Toutes les rubriques</a>"))
        assertTrue(html.contains("<a href=\"/agenda\">Agenda des mobilisations</a>"))
        // The other accordions stay closed.
        assertTrue(Regex("<details class=\"nav-group\" open").findAll(html).count() == 1)
    }

    @Test
    fun theShellLooksLikeTheApplication() {
        val html = render()
        // The red AIDE button, Messages and Mon espace in the top bar; the menu switch works without script.
        assertTrue(html.contains("<a href=\"/aide\" class=\"help-button\""))
        assertTrue(html.indexOf("href=\"/messages\"") < html.indexOf("<nav class=\"sidenav\""))
        assertTrue(html.indexOf("href=\"/mon-espace\"") < html.indexOf("<nav class=\"sidenav\""))
        assertTrue(html.contains("<input type=\"checkbox\" class=\"nav-toggle\" id=\"nav-toggle\""))
        assertTrue(html.contains("<label class=\"menu-button\" for=\"nav-toggle\""))
        // The icons come from our own sprite; the footer is in columns, without the placeholder of phase 0.
        assertTrue(html.contains("<use href=\"/static/icons.svg#radio\">"))
        assertTrue(javaClass.classLoader.getResource("static/icons.svg") != null)
        assertTrue(html.contains("class=\"footer-columns\""))
        assertFalse(html.contains("[EXEMPLE]"))
        for (page in LegalPages.ALL) assertTrue(html.contains("href=\"${page.path}\""), page.path)
    }

    @Test
    fun noScriptAndNoExternalResource() {
        val html = render()
        assertFalse(html.contains("<script"))
        // Nothing is loaded from elsewhere: no source or link to another server.
        assertFalse(Regex("""(src|href)="https?://""").containsMatchIn(html))
        // The only absolute address is the preview image of a shared link, on our own domain (D-185).
        val absolute = Regex("""https?://[^"]+""").findAll(html).map { it.value }.toList()
        assertTrue(absolute.all { it.startsWith("https://politisation-populaire-francaise.org/") }, absolute.toString())
    }

    @Test
    fun theSymbolOfPpfIsTheIconOfEveryPage() {
        val html = render()
        assertTrue(html.contains("href=\"/favicon.ico\""))
        assertTrue(html.contains("href=\"/static/favicon-32x32.png\""))
        assertTrue(html.contains("href=\"/static/apple-touch-icon.png\""))
        assertTrue(html.contains("property=\"og:image\" content=\"https://politisation-populaire-francaise.org/static/og-image-1200x630.png\""))
        assertTrue(html.contains("og:image:alt"))
        for (file in listOf("favicon.ico", "favicon-16x16.png", "favicon-32x32.png", "apple-touch-icon.png", "icon-192.png", "icon-512.png", "icon-maskable-192.png", "icon-maskable-512.png", "og-image-1200x630.png")) {
            assertTrue(javaClass.classLoader.getResource("static/$file") != null, "static/$file is served")
        }
        val manifest = javaClass.classLoader.getResource("static/manifest.webmanifest")!!.readText()
        assertTrue(manifest.contains("\"short_name\": \"Politisation\""))
        assertTrue(manifest.contains("/static/icon-maskable-512.png"))
    }

    @Test
    fun theNavigationFollowsTheOrderOfTheApplication() {
        // D-215: the menu lists the spaces in the order of the application, "Fil" is shown as "Actu".
        val html = render()
        val menu = html.substring(html.indexOf("<nav class=\"sidenav\""), html.indexOf("</nav>"))
        val labels = listOf("En direct", "Actu", "Carte", "Agir", "Appels", "Voir", "Comprendre", "Décider", "Revendications")
        val positions = labels.map { menu.indexOf("<span class=\"nav-label\">$it</span>") }
        assertTrue(positions.all { it >= 0 }, "every label is in the navigation: $positions")
        assertTrue(positions == positions.sorted(), "in this order: $labels")
        assertTrue(menu.contains("href=\"/appels\""))
        assertFalse(html.contains(">Fil<"))
    }
}
