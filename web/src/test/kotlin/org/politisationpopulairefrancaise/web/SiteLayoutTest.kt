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
}
