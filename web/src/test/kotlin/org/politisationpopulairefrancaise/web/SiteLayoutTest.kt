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
        assertFalse(Regex("https?://").containsMatchIn(html))
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
