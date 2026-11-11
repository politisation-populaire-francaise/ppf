package org.politisationpopulairefrancaise.web.decider

import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
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

/**
 * The pages of "Décider" rendered from fictional contents: the pact of the examples bundled with the applications, and
 * the fictional petitions of the tests (`content/tests/decider`); the real petitions the applications bundle are left out.
 */
class DeciderPagesTest {
    private fun read(directory: String, file: String): List<SignedEvent> {
        val path = listOf(Path.of("..", directory, file), Path.of(directory, file)).first(Files::exists)
        return Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(path))
    }
    private val catalog = run {
        val bundled = read(DIRECTORY, FILE).mapNotNull(Article::from).filter { it.type !in FIXTURE_TYPES }
        val fixtures = read(TESTS_DIRECTORY, TESTS_FILE).mapNotNull(Article::from)
        DeciderCatalog.from((bundled + fixtures).filter { it.event.pubkey == EditorialTrust.EXAMPLE_EDITOR })
    }
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
        assertTrue(html.contains("<a href=\"/decider\" aria-current=\"page\">Vue d’ensemble</a>"))
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

    /** DE-25, MVP 1: the deliberation reads without script; proposing and voting stay in the application. */
    @Test
    fun theDeliberationShowsItsStepsPropositionsAndAmendments() {
        var n = 0
        fun event(pubkey: String, tags: List<List<String>>, content: String = "[EXEMPLE] Texte") =
            SignedEvent(id = (++n).toString(16).padStart(64, '0'), pubkey = pubkey, createdAt = 100L + n, kind = Article.KIND, tags = tags, content = content, sig = "0".repeat(128))
        val team = "a".repeat(64)
        val deliberation = event(team, listOf(
            listOf("d", "pacte-exemple"), listOf("title", "[EXEMPLE] Les mesures de la Constitution Citoyenne et Démocratique"), listOf("ppf-type", "deliberation"),
            listOf("ppf-phase", "propositions", "1000", "2000"), listOf("ppf-phase", "amendements", "2000", "3000"), listOf("ppf-phase", "vote", "3000", "4000"),
            listOf("ppf-measures", "5", "10"),
        ))
        val link = org.politisationpopulairefrancaise.core.signing.ContentAddress.of(deliberation)!!.toString()
        val proposition = event("b".repeat(64), listOf(listOf("d", "p1"), listOf("title", "[EXEMPLE] Une mesure"), listOf("ppf-type", "proposition"), listOf("a", link)))
        val amendment = event("c".repeat(64), listOf(listOf("d", "a1"), listOf("ppf-type", "amendement"), listOf("a", org.politisationpopulairefrancaise.core.signing.ContentAddress.of(proposition)!!.toString())), "[EXEMPLE] Autre rédaction")
        val board = org.politisationpopulairefrancaise.core.decider.DeliberationBoard.from(listOf(deliberation, proposition, amendment)) { it == team }.single()

        val home = render { deciderHomeDocument(catalog, today, listOf(board), 2500) }
        assertTrue(home.contains("href=\"/decider/deliberations/pacte-exemple\""))
        val html = render { deliberationDocument(board, 2500) }
        checkPage(html)
        assertTrue(html.contains("Étape en cours : Amendements"))
        assertTrue(html.contains("[EXEMPLE] Une mesure"))
        assertTrue(html.contains("[EXEMPLE] Autre rédaction"))
        assertTrue(html.contains("se font dans l’application PPF"))
    }

    /** D-152: the wall on the site, the demands of the movement first, with who carries them and their sources. */
    @Test
    fun theDemandsOfTheMovementComeFirstWithWhoCarriesThemAndTheirSources() {
        val demands = read(DIRECTORY, "revendications.json").mapNotNull(Article::from).filter { it.event.pubkey == EditorialTrust.EXAMPLE_EDITOR }
        val html = render { revendicationsDocument(demands) }
        checkPage(html)
        assertTrue(html.contains("Ce que le mouvement demande déjà"))
        assertTrue(html.contains("href=\"/revendications/mouvement-hausse-du-smic\""))
        assertTrue(html.indexOf("mouvement-hausse-du-smic") < html.indexOf("Revendications citoyennes"), "the movement first")
        assertTrue(html.contains("Également portée par : NPA-L’Anticapitaliste (parti politique), 4 novembre 2026"))
        assertTrue(html.indexOf("Exemples") > html.indexOf("Revendications citoyennes"), "the examples last")

        val smic = demands.single { it.slug == "mouvement-hausse-du-smic" }
        val page = render { revendicationDocument(smic) }
        checkPage(page)
        assertTrue(page.contains("Portée par :"))
        assertTrue(page.contains("href=\"https://www.cgt.fr/17-novembre\""), "the sources are links")
        assertTrue(page.contains("dans l’application PPF"))
    }

    /** MVP 1: every entry of Décider in the side menu leads to a section that always exists, and each item to its page. */
    @Test
    fun everyEntryOfTheMenuLeadsToARealPage() {
        val home = render { deciderHomeDocument(catalog, today) }
        checkPage(home)
        for (id in listOf("pacte", "deliberation", "petitions-ouvertes", "comparateur", "calendrier", "consultations", "courriers")) {
            assertTrue(home.contains("id=\"$id\""), id)
        }
        assertTrue(home.contains("n’est pas encore ouverte"), "the deliberation says when nothing is open")
        catalog.comparators.forEach { sheet ->
            assertTrue(home.contains("href=\"${DeciderMoreLinks.comparator(sheet)}\""))
            checkPage(render { comparatorDocument(catalog.grid(sheet, VoirCatalog.EMPTY)) })
        }
        catalog.elections.forEach { election ->
            assertTrue(home.contains("href=\"${DeciderMoreLinks.election(election)}\""))
            checkPage(render { electionDocument(election) }.also { assertTrue(it.contains("dans l’application PPF")) })
        }
        catalog.consultations.forEach { consultation ->
            assertTrue(home.contains("href=\"${DeciderMoreLinks.consultation(consultation)}\""))
            checkPage(render { consultationDocument(consultation, today) })
        }
        catalog.letters.forEach { letter ->
            assertTrue(home.contains("href=\"${DeciderMoreLinks.letter(letter)}\""))
            checkPage(render { letterDocument(letter) }.also { assertTrue(it.contains("PPF n’envoie rien")) })
        }
        assertTrue(catalog.elections.isNotEmpty() && catalog.letters.isNotEmpty() && catalog.consultations.isNotEmpty(), "the real contents of Décider are read")
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
        const val TESTS_DIRECTORY = "shared/ui/src/jvmTest/resources/contents"
        const val TESTS_FILE = "decider-tests.json"

        /** The types whose bundled contents are real: the tests read their fictional ones instead. */
        val FIXTURE_TYPES = setOf(ArticleType.PETITION, ArticleType.ELECTION, ArticleType.MODELE_COURRIER, ArticleType.CONSULTATION)
    }
}
