package org.politisationpopulairefrancaise.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.Space
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellPagesTest {

    @Test
    fun enDirectIsAPageWithItsSectionsEvenWithoutContents() {
        val html = createHTML().html { liveDocument(LiveContents()) }
        for (id in listOf("mobilisations", "chiffres", "faits", "decider", "vrai-faux", "application")) {
            assertTrue(html.contains("id=\"$id\""), id)
        }
        assertTrue(html.contains("href=\"/agenda\""))
        assertTrue(html.contains("href=\"/voir/chronologie\""))
        assertFalse(html.contains("[EXEMPLE]"))
        assertFalse(html.contains("emplacement réservé"))
    }

    @Test
    fun theViewsOfEnDirectAreLinksThatOrderTheBlocks() {
        val essential = createHTML().html { liveDocument(LiveContents()) }
        assertTrue(essential.contains("<a href=\"/en-direct?vue=mobilisation\">Mobilisation</a>"))
        assertTrue(essential.contains("<a href=\"/en-direct\" aria-current=\"page\">L’essentiel</a>"))
        val mobilisation = createHTML().html { liveDocument(LiveContents(), viewOf("mobilisation")) }
        // « Mobilisation »: the agenda and the calls first, no fact-checks.
        assertTrue(mobilisation.indexOf("id=\"mobilisations\"") < mobilisation.indexOf("id=\"appels\""))
        assertFalse(mobilisation.contains("id=\"vrai-faux\""))
        assertTrue(mobilisation.contains("aria-current=\"page\">Mobilisation</a>"))
        // An unknown view is « L'essentiel ».
        assertTrue(viewOf("inconnue") == org.politisationpopulairefrancaise.core.endirect.LivePreset.ESSENTIEL)
    }

    @Test
    fun theHelpPageGivesTheEmergencyNumbersFirst() {
        val html = createHTML().html { helpDocument() }
        val samu = html.indexOf("href=\"tel:15\"")
        assertTrue(samu > 0 && samu < html.indexOf("href=\"tel:3114\""))
        assertTrue(html.contains("href=\"tel:112\""))
        assertTrue(html.contains("href=\"sms:114\""))
        assertTrue(html.contains("href=\"/comprendre/fiches/garde-a-vue\""))
    }

    @Test
    fun theSpacesOfTheApplicationSayWhatTheyDoAndWhatTheSiteOffers() {
        for (space in appSpaces) {
            val html = createHTML().html { appSpaceDocument(space) }
            assertFalse(html.contains("emplacement réservé"), space.id)
            assertTrue(html.contains("<h1>${spaceLabels.getValue(space)}</h1>"), space.id)
        }
        val agir = createHTML().html { appSpaceDocument(Space.AGIR) }
        // Every anchor of the menu of "Agir" leads to a section of the page.
        SiteNavigation.quick.first { it.space == Space.AGIR }.children
            .mapNotNull { it.href.substringAfter("/agir#", "").ifEmpty { null } }
            .forEach { anchor -> assertTrue(agir.contains("id=\"$anchor\""), anchor) }
        assertTrue(agir.contains("href=\"/agenda\""))
    }
}
