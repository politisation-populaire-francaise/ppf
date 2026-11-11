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
        val html = render(Space.AGIR)
        assertTrue(html.contains("<a href=\"/agir\" aria-current=\"page\">Agir</a>"))
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
        // D-215: the site lists the spaces in the order of the bottom bar of the application, "Fil" is shown as "Actu".
        val html = render()
        val labels = listOf("En direct", "Actu", "Carte", "Agir", "Appels", "Voir", "Comprendre", "Décider", "Revendications", "Messages")
        val positions = labels.map { html.indexOf(">$it</a>") }
        assertTrue(positions.all { it >= 0 }, "every label is in the navigation: $positions")
        assertTrue(positions == positions.sorted(), "in this order: $labels")
        assertTrue(html.contains("href=\"/appels\""))
        assertFalse(html.contains(">Fil</a>"))
    }
}
