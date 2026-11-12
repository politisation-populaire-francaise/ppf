package org.politisationpopulairefrancaise.server.voir

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirLinks
import org.politisationpopulairefrancaise.server.BundledContents
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import org.politisationpopulairefrancaise.web.voir.VoirSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pages of "Voir" on the web site, served by the whole application (security headers included)
 * from the example contents (voir.json), read as the server reads them: authentic events of the
 * editorial key only. Reading them from PostgreSQL is tested by [EditorialVoirSourceTest].
 */
class VoirPagesTest {
    private val examples = VoirCatalog.from(
        BundledContents.load(BundledContents.VOIR_FILE).filter { it.pubkey == EditorialTrust.EXAMPLE_EDITOR }.mapNotNull(Article::from),
    )

    private fun ApplicationTestBuilder.start() {
        assertTrue(examples.facts.size > 10, "voir.json is bundled and authentic")
        application { module(null, PublicationPolicy(setOf(EditorialTrust.EXAMPLE_EDITOR)), TEST_RELAY, testRevendications(), testAgir(), voir = VoirSource { examples }) }
    }

    private suspend fun HttpClient.page(path: String, status: HttpStatusCode = HttpStatusCode.OK): String {
        val response: HttpResponse = get(path)
        val body = response.bodyAsText()
        assertEquals(status, response.status, "$path: $body")
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true, path)
        // Readable without script, strict policy kept on every page.
        assertFalse(body.contains("<script", ignoreCase = true), path)
        assertFalse(body.contains("style=\""), "$path: no inline style (the CSP forbids it)")
        val csp = response.headers["Content-Security-Policy"].orEmpty()
        assertTrue(csp.contains("script-src 'self'") && csp.contains("style-src 'self'"), csp)
        assertFalse(csp.contains("unsafe-inline") || csp.contains("unsafe-eval"), csp)
        return body
    }

    @Test
    fun theHomeShowsTheThreeAxes() = testApplication {
        start()
        val html = client.page("/voir")
        listOf("Le mouvement", "Le pouvoir", "Libertés et contre-pouvoirs", "Chiffres clés", "Chronologie du mouvement", "Dans le monde",
            "Responsables publics", "Textes en discussion", "Derniers faits sourcés", "Tableau de bord des contre-pouvoirs",
            "Observatoire des libertés publiques", "Dossiers", "Et maintenant ?").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("href=\"/voir/chiffres/manifestants-journees\""))
        assertTrue(html.contains("href=\"/voir/personnes/alex-modele\""))
        assertTrue(html.contains("href=\"/voir/methode\""))
        // The placeholder of the phase 0 is gone.
        assertFalse(html.contains("emplacement réservé"))
        assertTrue(html.contains("<a href=\"/voir\" aria-current=\"page\">Accueil de S’informer</a>"))
    }

    @Test
    fun theListsOfPersonsAndPartiesLeadToTheSheetsAndTheSiteSearchFindsThem() = testApplication {
        start()
        val persons = client.page("/voir/personnes")
        assertTrue(persons.contains("href=\"/voir/personnes/alex-modele\""))
        val parties = client.page("/voir/partis")
        assertTrue(parties.contains("<h1>Partis politiques</h1>"))
        examples.parties.firstOrNull()?.let { assertTrue(parties.contains(it.name)) }
        // One search over the site: the person is found by the beginning of a word, accents ignored.
        val found = client.page("/recherche?q=modele")
        assertTrue(found.contains("href=\"/voir/personnes/alex-modele\""), found)
        assertTrue(client.page("/recherche").contains("role=\"search\""))
        // The site opens on « En direct », as the application does.
        assertTrue(client.page("/").contains("<h1>En direct</h1>"))
    }

    @Test
    fun anIndicatorHasItsChartAndTheSameDataAsATable() = testApplication {
        start()
        val html = client.page("/voir/chiffres/manifestants-journees")
        assertTrue(html.contains("Dernière mesure"))
        assertTrue(html.contains("Journée du 5 novembre"))
        // Every source side by side, with its reliability and its link, marked as leaving the site.
        listOf("Autorités", "Organisateurs", "Comptage indépendant", "Fiabilité élevée", "Fiabilité moyenne").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("href=\"https://example.org/exemple/comptage-05-11\" rel=\"noopener noreferrer nofollow external\""))
        assertTrue(html.contains("(site extérieur)"))
        // The chart, drawn on the server, and the table right after it in the same section.
        val evolution = html.substringAfter("id=\"evolution\"").substringBefore("</section>")
        val svg = evolution.indexOf("<svg")
        val table = evolution.indexOf("<table class=\"chart-table\"")
        assertTrue(svg >= 0 && table > svg, evolution)
        assertTrue(evolution.contains("role=\"img\" aria-labelledby=\"graphique-titre graphique-description\""))
        assertTrue(evolution.contains("<title id=\"graphique-titre\">Évolution de « [EXEMPLE] Manifestants lors des journées nationales »</title>"))
        assertTrue(evolution.contains("chart-series chart-s0") && evolution.contains("chart-series chart-s1") && evolution.contains("chart-series chart-s2"))
        assertTrue(evolution.contains("<th scope=\"row\">5 nov. 2026</th>"))
        listOf("Toutes les mesures", "Méthode", "Pourquoi les chiffres diffèrent", "Corrections", "Sources", "Et maintenant ?").forEach { assertTrue(html.contains(it), it) }
        // "Et maintenant ?" leads to pages of the site: the map space of the app becomes its space page.
        assertTrue(html.substringAfter("id=\"et-maintenant\"").contains("href=\"/carte\""))
    }

    @Test
    fun aCaseShowsItsProcedureAndThePresumptionOfInnocence() = testApplication {
        start()
        val html = client.page("/voir/faits/affaire-dominique-marche-public")
        assertTrue(html.contains("En cours d’enquête"))
        assertTrue(html.contains("Où en est la procédure"))
        assertTrue(html.contains("Présomption d’innocence : tant qu’elle n’a pas été condamnée définitivement, la personne est présumée innocente."))
        assertTrue(html.contains("href=\"/voir/personnes/dominique-fictif\""))
        assertTrue(html.contains("Droit de réponse de [EXEMPLE] Dominique Fictif") || html.contains("Droit de réponse de Dominique Fictif"), html)
        assertTrue(html.contains("Exercer un droit de réponse"))
        assertTrue(html.contains("Corrigé le"))
    }

    @Test
    fun everyPersonHasTheSameSectionsInTheSameOrder() = testApplication {
        start()
        val sections = listOf("Mandats et fonctions", "Votes (", "Promesses (", "Discours et votes (", "Affaires judiciaires (",
            "Déclarations contredites par les faits (", "Intérêts et pantouflage (", "Rencontres avec des représentants d’intérêts (", "Argent public (")
        listOf("alex-modele", "camille-exemple", "dominique-fictif", "sacha-temoin").forEach { slug ->
            val html = client.page("/voir/personnes/$slug")
            val positions = sections.map { html.indexOf(it) }
            assertTrue(positions.all { it >= 0 } && positions == positions.sorted(), "$slug: $positions")
            assertTrue(html.contains("Chaque fiche présente les mêmes rubriques"))
        }
    }

    @Test
    fun aBillShowsItsStageItsVoteAndWhoGainsOrLoses() = testApplication {
        start()
        val html = client.page("/voir/textes/pjl-ordre-public-manifestations")
        listOf("Projet de loi, déposé par le gouvernement", "Prochain vote le", "Étape 4 sur 7 : Sénat", "aria-current=\"step\"",
            "Article 49.3 : 1 recours", "Qui gagne, qui perd", "Y perd", "Votes des élus", "Influence des représentants d’intérêts").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("href=\"/decider\""))
    }

    @Test
    fun theOtherPagesAnswer() = testApplication {
        start()
        assertTrue(client.page("/voir/monde/monde-pays-a-vie-chere").contains("Ce qu’ils demandaient"))
        val dossier = client.page("/voir/dossiers/dossier-proprietaires-medias")
        assertTrue(dossier.contains("<aside class=\"md-box\"") && dossier.contains("<strong>Groupe Exemple Médias</strong>"))
        val chronology = client.page("/voir/chronologie")
        assertTrue(chronology.contains("Première journée nationale de mobilisation"))
        assertTrue(client.page("/voir/chronologie?type=mobilisation").contains("aria-current=\"true\">Mobilisation<"))
        assertTrue(client.page("/voir/libertes").contains("Dissolution du Collectif"))
        val corrections = client.page("/voir/corrections")
        assertTrue(corrections.contains("href=\"/voir/chiffres/manifestants-journees\""))
        assertTrue(client.page("/voir/methode").contains("Les étapes d’une procédure judiciaire"))
    }

    @Test
    fun aPartyHasItsPageAtTheAddressOfVoirLinks() = testApplication {
        start()
        val party = examples.parties.first { it.article.slug == "parti-exemple-a" }
        val path = VoirLinks.pathOf(VoirItem.OfParty(party))
        assertEquals("/voir/partis/parti-exemple-a", path)
        val html = client.page(path)
        assertTrue(html.contains("Parti politique · PEA"), html)
        assertTrue(html.contains("Statuts déposés en préfecture (exemple)"), "its sources")
        // Every party of the examples answers, and a party slug under another kind does not.
        examples.parties.forEach { client.page(VoirLinks.pathOf(VoirItem.OfParty(it))) }
        client.page("/voir/faits/parti-exemple-a", HttpStatusCode.NotFound)
    }

    @Test
    fun everyContentPageOffersItsReferenceToCite() = testApplication {
        start()
        val fact = examples.facts.first { it.address.identifier == "affaire-dominique-marche-public" }
        val html = client.page("/voir/faits/affaire-dominique-marche-public")
        assertTrue(html.contains("<details class=\"cite\"><summary>Citer ce contenu</summary>"), html)
        val id = fact.article.event.id
        assertTrue(html.contains("version ${id.take(12)}, https://politisation-populaire-francaise.org/voir/faits/affaire-dominique-marche-public, empreinte SHA-256 $id, licence CC BY 4.0, consulté le "), html)
        assertTrue(html.contains("&quot;type&quot;:&quot;webpage&quot;"), "the CSL-JSON, escaped as text: $html")
        // The same block on every kind of page.
        listOf("/voir/chiffres/manifestants-journees", "/voir/personnes/alex-modele", "/voir/partis/parti-exemple-a", "/voir/textes/pjl-ordre-public-manifestations").forEach { path ->
            assertTrue(client.page(path).contains("Citer ce contenu"), path)
        }
    }

    @Test
    fun unknownContentsAnswerNotFound() = testApplication {
        start()
        listOf("/voir/faits/inexistant", "/voir/inconnu/manifestants-journees", "/voir/personnes/manifestants-journees", "/voir/partis/inexistant").forEach { path ->
            val html = client.page(path, HttpStatusCode.NotFound)
            assertTrue(html.contains("Contenu introuvable"), path)
        }
    }
}
