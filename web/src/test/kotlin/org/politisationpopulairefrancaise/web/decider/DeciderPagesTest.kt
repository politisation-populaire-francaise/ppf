package org.politisationpopulairefrancaise.web.decider

import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pages of "Décider" rendered from the example contents bundled with the applications. */
class DeciderPagesTest {
    private val events: List<SignedEvent> = run {
        val path = listOf(Path.of("..", DIRECTORY, FILE), Path.of(DIRECTORY, FILE)).first(Files::exists)
        Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(path))
    }
    private val catalog = DeciderCatalog.from(events.filter { it.pubkey == EditorialTrust.EXAMPLE_EDITOR }.mapNotNull(Article::from))
    private val today = CivilDate(2026, 11, 8)

    private fun render(document: HTML.() -> Unit): String = createHTML().html { document() }

    /** Every page: complete without script, no inline style nor handler, no em dash. */
    private fun checkPage(html: String) {
        assertTrue(html.contains("<html lang=\"fr\">"))
        val scripts = Regex("<script[^>]*>").findAll(html).map { it.value }.toList()
        assertTrue(scripts.all { it == "<script src=\"/static/pwa.js\" defer=\"defer\">" }, scripts.toString())
        assertFalse(html.contains("style=\""), "no inline style")
        assertFalse(Regex(" on[a-z]+=\"").containsMatchIn(html), "no event handler")
        assertFalse(html.contains("—"), "no em dash")
    }

    @Test
    fun theHomeShowsThePactAndTheOpenPetitionsApartFromTheClosedOnes() {
        assertEquals(5, catalog.petitions.size)
        val html = render { deciderHomeDocument(catalog, today) }
        checkPage(html)
        assertTrue(html.contains("<a href=\"/decider\" aria-current=\"page\">Décider</a>"))
        assertTrue(html.contains("href=\"/decider/pacte/pacte-election-exemple\""))
        listOf("petition-an-ordre-public", "petition-cese-vie-chere", "petition-ice-energie", "petition-senat-indexation", "petition-senat-retraites")
            .forEach { assertTrue(html.contains("href=\"/decider/petitions/$it\""), it) }
        assertTrue(html.indexOf("petition-senat-retraites") > html.indexOf("Pétitions closes"), "the closed one is listed apart")
        assertTrue(html.contains("PPF ne recueille aucune signature"))
    }

    @Test
    fun aPetitionLeadsToTheOfficialSiteOnly() {
        val petition = catalog.petitions.first { it.address.identifier == "petition-senat-indexation" }
        val html = render { petitionDocument(petition, today) }
        checkPage(html)
        assertTrue(html.contains("Signer sur le site officiel"))
        val links = Regex("<a href=\"(https?://[^\"]+)\" rel=\"([^\"]+)\"").findAll(html).toList()
        assertTrue(links.isNotEmpty())
        assertTrue(links.all { it.groupValues[2].contains("noopener") && it.groupValues[2].contains("noreferrer") }, "no referrer to an outside site")
        assertTrue(html.contains("href=\"${petition.officialUrl}\""))
        assertTrue(html.contains("<caption>Relevés du compteur officiel"))
        // A closed one says so.
        val closed = catalog.petitions.first { it.address.identifier == "petition-senat-retraites" }
        assertTrue(render { petitionDocument(closed, today) }.contains("Voir la pétition sur le site officiel"))
    }

    @Test
    fun thePactShowsItsMeasuresMethodAndTable() {
        val pacte = catalog.pactes.single()
        val html = render { pacteDocument(pacte, catalog.board(pacte, VoirCatalog.EMPTY, today)) }
        checkPage(html)
        pacte.measures.forEach { assertTrue(html.contains(it.title.replace("’", "’")), it.title) }
        assertTrue(html.contains("ordre alphabétique"))
        assertTrue(html.contains("<caption>Réponse de chaque candidat, mesure par mesure</caption>"))
        assertTrue(html.contains("scope=\"row\""))
    }

    @Test
    fun anUnknownPageSaysSo() {
        val html = render { deciderNotFoundDocument() }
        checkPage(html)
        assertTrue(html.contains("Page introuvable"))
    }

    private companion object {
        const val DIRECTORY = "shared/ui/src/commonMain/composeResources/files/contents"
        const val FILE = "decider.json"
    }
}
