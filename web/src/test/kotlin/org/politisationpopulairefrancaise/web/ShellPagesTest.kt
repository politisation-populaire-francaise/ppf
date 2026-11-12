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
    fun thePostsOfPpfAndTheWeekInParliamentAreOnTheSite() {
        val week = org.politisationpopulairefrancaise.core.voir.parlement.WeekItem(
            id = "an-1",
            chamber = org.politisationpopulairefrancaise.core.voir.parlement.Chamber.AN,
            start = "2026-11-17T15:00",
            kind = org.politisationpopulairefrancaise.core.voir.parlement.SittingKind.PLENARY,
            title = "Questions au Gouvernement",
            url = "https://www.assemblee-nationale.fr/agenda",
        )
        val contents = LiveContents(
            posts = listOf(
                LivePost("a", "Rassemblement en cours place de la République.", 1_794_000_000, example = true, live = true),
                LivePost("b", "Le point de la semaine.", 1_793_900_000, example = false, live = false),
            ),
            parliament = listOf(week),
        )
        val html = createHTML().html { liveDocument(contents) }
        for (id in listOf("en-cours", "actu", "parlement")) assertTrue(html.contains("id=\"$id\""), id)
        // The live post goes to « En cours », the other one to « L'actu de PPF », the example marked as such.
        assertTrue(html.indexOf("Rassemblement en cours") in html.indexOf("id=\"en-cours\"")..html.indexOf("id=\"alertes\""))
        assertTrue(html.indexOf("Le point de la semaine") > html.indexOf("id=\"actu\""))
        assertTrue(html.contains("[EXEMPLE]"))
        assertTrue(html.contains("Assemblée nationale · 17 novembre 2026 · 15h00"))
        assertTrue(html.contains("Questions au Gouvernement"))
        assertTrue(html.contains("(site extérieur)"))
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
        SiteNavigation.rubrics.flatMap { it.children }
            .mapNotNull { it.href?.substringAfter("/agir#", "")?.ifEmpty { null } }
            .forEach { anchor -> assertTrue(agir.contains("id=\"$anchor\""), anchor) }
        assertTrue(agir.contains("href=\"/agenda\""))
    }
}
