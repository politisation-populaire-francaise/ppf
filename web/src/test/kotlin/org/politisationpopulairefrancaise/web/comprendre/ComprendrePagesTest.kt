package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.html.HTML
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pages of "Comprendre" rendered from the example contents bundled with the applications
 * (signed by the example editorial key), as the server shows them (D-091).
 */
class ComprendrePagesTest {
    private fun load(file: String): List<SignedEvent> {
        val path = listOf(Path.of("..", DIRECTORY, file), Path.of(DIRECTORY, file)).first(Files::exists)
        return Json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(path))
    }

    private val events = FILES.flatMap(::load)
    private val catalog = ComprendreCatalog.fromEvents(events) { it == EditorialTrust.EXAMPLE_EDITOR }

    private fun render(document: HTML.() -> Unit): String = createHTML().html { document() }

    /** Every page: complete without script (only the optional worker registration), no inline style, French. */
    private fun checkPage(html: String) {
        assertTrue(html.contains("<html lang=\"fr\">"))
        val scripts = Regex("<script[^>]*>").findAll(html).map { it.value }.toList()
        assertTrue(scripts.all { it == "<script src=\"/static/pwa.js\" defer=\"defer\">" }, scripts.toString())
        assertFalse(html.contains("style=\""), "no inline style")
        assertFalse(Regex(" on[a-z]+=\"").containsMatchIn(html), "no event handler")
        assertFalse(html.contains("—"), "no em dash")
        assertFalse(html.contains("href=\"javascript:", ignoreCase = true))
    }

    @Test
    fun theBundledContentsAreAllRead() {
        assertEquals(26, catalog.fiches.size)
        assertEquals(20, catalog.parcours.size)
        assertEquals(7, catalog.decryptages.size)
        assertEquals(3, catalog.stories.size)
        assertEquals(12, catalog.sheets.size)
        assertEquals(72, catalog.glossary.size)
        assertNotNull(catalog.method)
    }

    @Test
    fun aContentOfAnotherKeyIsNeverShown() = runBlocking {
        val stranger = LocalEventSigner(IdentityKeys.generate())
        val unsigned = Article.build(stranger.publicKey, "garde-a-vue", "Fausse fiche", "Fausse.", emptyList(), ArticleType.FICHE_PRATIQUE, ContentOrigin.HUMAN, 1_900_000_000, 1_900_000_000, "Texte piégé.")
        val forged = stranger.sign(unsigned)
        val mixed = ComprendreCatalog.fromEvents(events + forged) { it == EditorialTrust.EXAMPLE_EDITOR }
        assertEquals("Garde à vue : vos droits", mixed.fiche("garde-a-vue")!!.title)
        // A tampered event of the trusted key is refused too (its signature no longer matches).
        val original = events.first { it.tags.any { t -> t[0] == "d" && t[1] == "garde-a-vue" } }
        val tampered = original.copy(content = "Texte modifié.", createdAt = original.createdAt + 10)
        assertEquals(original.content, ComprendreCatalog.fromEvents(listOf(tampered, original)) { it == EditorialTrust.EXAMPLE_EDITOR }.fiche("garde-a-vue")!!.body)
        assertTrue(ComprendreCatalog.fromEvents(events) { false }.isEmpty)
    }

    @Test
    fun addressesAreStableAndReadable() {
        assertEquals("/comprendre/fiches/garde-a-vue", ComprendreLinks.pathOf(ArticleType.FICHE_PRATIQUE, "garde-a-vue"))
        assertEquals("/comprendre/parcours/le-49-3-et-la-motion-de-censure", ComprendreLinks.pathOf(catalog.course("le-49-3-et-la-motion-de-censure")!!.article))
        assertEquals("/comprendre/glossaire/49-3", ComprendreLinks.glossaryPath("glossaire-49-3"))
        assertEquals("/comprendre/qui-decide/%C3%A9cole", ComprendreLinks.pathOf(ArticleType.QUI_DECIDE, "école"))
        assertNull(ComprendreLinks.pathOf(ArticleType.FAIT, "x"))
        assertNotNull(catalog.glossaryEntry("49-3"))
        assertNotNull(catalog.glossaryEntry("glossaire-49-3"))
    }

    @Test
    fun theHomeLeadsToEveryPart() {
        val html = render { comprendreHomeDocument(catalog) }
        checkPage(html)
        listOf("Connaître ses droits", "Comprendre en 5 minutes", "Qui décide de ça ?", "Le vrai du faux", "Histoire des luttes", "Glossaire", "Notre méthode").forEach { assertTrue(html.contains(it), it) }
        listOf("/comprendre/fiches", "/comprendre/parcours", "/comprendre/decryptages", "/comprendre/histoire", "/comprendre/qui-decide", "/comprendre/glossaire", "/comprendre/methode", "/comprendre/hors-ligne")
            .forEach { assertTrue(html.contains("href=\"$it"), it) }
        assertTrue(html.contains("<a href=\"/comprendre\" aria-current=\"page\">Vue d’ensemble</a>"))
    }

    @Test
    fun aPracticalGuideShowsItsBoxesGlossaryNextStepsAndSourcesLast() {
        val html = render { ficheDocument(catalog.fiche("garde-a-vue")!!, catalog) }
        checkPage(html)
        listOf("callout callout-en-bref", "callout callout-legal", "callout callout-illegal", "callout callout-conseil").forEach { assertTrue(html.contains(it), it) }
        // A real content (owner's decision of 9 November 2026): no example badge, the origin is shown.
        assertFalse(html.contains("Contenu d’exemple"))
        assertTrue(html.contains("Rédigé avec l’aide d’une IA"))
        // External addresses: a link marked as leaving the site, without referrer.
        assertTrue(Regex("href=\"https://www\\.legifrance\\.gouv\\.fr[^\"]*\" rel=\"noopener noreferrer nofollow external\"").containsMatchIn(html))
        assertTrue(html.contains("(site extérieur)"))
        val next = html.indexOf("Et maintenant ?")
        val sources = html.indexOf("id=\"sources\"")
        assertTrue(next in 1 until sources, "sources come last")
        assertTrue(html.indexOf("class=\"origin\"") > sources)
    }

    @Test
    fun everyContentEndsWithItsLicenceItsReferenceAndItsVerificationCode() {
        val fiche = catalog.fiche("garde-a-vue")!!
        val html = render { ficheDocument(fiche, catalog) }
        checkPage(html)
        assertTrue(html.indexOf("class=\"origin\"") > html.indexOf("id=\"sources\""), "after the sources")
        // The licence of the texts of PPF (D-103), a link that leaves the site without referrer.
        assertTrue(html.contains("Texte sous licence <a href=\"https://creativecommons.org/licenses/by/4.0/deed.fr\" rel=\"noopener noreferrer nofollow external\">"))
        // « Citer ce contenu » (S-144): the stable address of the page, this version and its fingerprint.
        assertTrue(html.contains("<summary>Citer ce contenu</summary>"))
        assertTrue(html.contains("https://politisation-populaire-francaise.org/comprendre/fiches/garde-a-vue"))
        assertTrue(html.contains("empreinte SHA-256 ${fiche.event.id}, licence CC BY 4.0"))
        // The code of this version (S-141), to check a printed or copied page.
        assertTrue(html.contains("<code>${fiche.event.id.take(16)}</code>"))
        assertTrue(html.contains("href=\"/v/${fiche.event.id.take(16)}\""))
        // A course too, under its own address.
        val course = catalog.course("le-referendum")!!
        val courseHtml = render { parcoursDocument(course, catalog) }
        assertTrue(courseHtml.contains("https://politisation-populaire-francaise.org/comprendre/parcours/le-referendum"))
        assertTrue(courseHtml.contains("href=\"/v/${course.article.event.id.take(16)}\""))
    }

    @Test
    fun glossaryTermsAreLinkedOnceWithAnAccessibleDefinition() {
        val course = catalog.course("le-49-3-et-la-motion-de-censure")!!
        val html = render { parcoursDocument(course, catalog) }
        checkPage(html)
        val links = Regex("<a href=\"/comprendre/glossaire/49-3\" class=\"gloss\" aria-describedby=\"(def-[a-z0-9-]+)\">").findAll(html).toList()
        assertEquals(1, links.size, "first occurrence only")
        val id = links.single().groupValues[1]
        assertTrue(html.contains("<dd id=\"$id\">"), "the definition the link points to is in the page")
        // Never in a heading or in the sources.
        val sourcesAt = html.indexOf("id=\"sources\"")
        assertFalse(html.substring(sourcesAt).substringBefore("class=\"origin\"").contains("class=\"gloss\""))
        assertFalse(Regex("<h[1-6][^>]*>[^<]*<a[^>]*class=\"gloss\"").containsMatchIn(html))
        // Every described id exists once.
        Regex("aria-describedby=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.toSet().forEach { d ->
            assertEquals(1, Regex("id=\"$d\"").findAll(html).count(), d)
        }
    }

    @Test
    fun aQuizIsReadableWithoutScript() {
        val course = catalog.course("le-49-3-et-la-motion-de-censure")!!
        val html = render { parcoursDocument(course, catalog) }
        assertEquals(course.quizCount, Regex("<details class=\"quiz-answer\">").findAll(html).count())
        assertTrue(html.contains("<summary>Voir la bonne réponse</summary>"))
        assertTrue(html.contains("La bonne réponse : A. "))
        assertFalse(html.contains("<form"), "no answer is ever sent")
        // The only input is the switch of the menu of the site.
        assertFalse(Regex("<input(?! type=\"checkbox\" class=\"nav-toggle\")").containsMatchIn(html))
        assertTrue(html.contains("href=\"#etape-1\""))
        assertTrue(html.contains("id=\"etape-${course.steps.size}\""))
        // « Et maintenant ? » leads to the spaces of the site.
        assertTrue(html.contains("<a href=\"/revendications\">Voir les revendications des citoyens</a>"))
    }

    @Test
    fun aFactCheckShowsTheClaimAndItsVerdict() {
        val html = render { decryptageDocument(catalog.decryptage("49-3-loi-sans-vote")!!, catalog) }
        checkPage(html)
        assertTrue(html.contains("Verdict : Trompeur"))
        assertTrue(html.contains("« Avec le 49.3, une loi est adoptée sans aucun vote. »"))
        assertTrue(html.contains("Objections et réponses"))
        assertTrue(html.contains("href=\"/comprendre/methode\""))
        assertTrue(html.contains("Rédigé avec l’aide d’une IA"))
    }

    @Test
    fun aStoryShowsItsPeriodClaimsResultsAndChronology() {
        val html = render { storyDocument(catalog.story("front-populaire-1936")!!, catalog) }
        checkPage(html)
        assertTrue(html.contains("France · 1936-1938"))
        assertTrue(html.contains("Ce qui était demandé"))
        assertTrue(html.contains("Résultats obtenus"))
    }

    @Test
    fun aWhoDecidesSheetShowsTheChainAndTheOfficialDirectoriesOnly() {
        val html = render { sheetDocument(catalog.sheet("eau-du-robinet")!!, catalog) }
        checkPage(html)
        listOf("Commune", "Intercommunalité", "Département", "État", "Union européenne", "Trouver vos élus").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("href=\"https://lannuaire.service-public.gouv.fr/navigation/mairie\" rel=\"noopener noreferrer nofollow external\""))
        assertFalse(html.contains("::: decideurs"))
        assertFalse(html.contains("name=\"commune\""), "the site never asks for the town")
    }

    @Test
    fun theGlossaryIsAlphabeticalSearchableAndHasAPagePerEntry() {
        val html = render { glossaryDocument(catalog) }
        checkPage(html)
        assertTrue(html.contains("href=\"#lettre-a\""))
        assertTrue(html.contains("<form action=\"/comprendre/glossaire\" method=\"get\""))
        assertTrue(html.contains("href=\"/comprendre/glossaire/49-3\""))
        val search = render { glossaryDocument(catalog, "censure") }
        assertTrue(search.contains("résultat"))
        assertTrue(search.contains("/comprendre/glossaire/motion-de-censure"))
        val entry = render { glossaryEntryDocument(catalog.glossaryEntry("49-3")!!, catalog) }
        checkPage(entry)
        assertTrue(entry.contains("Voir aussi"))
        assertTrue(entry.contains("href=\"/comprendre/parcours/le-49-3-et-la-motion-de-censure\""))
        assertFalse(entry.contains("href=\"/comprendre/glossaire/49-3\" class=\"gloss\""), "an entry never links itself")
    }

    @Test
    fun theOfflinePageHoldsEveryGuideAndTheWorkerKeepsThem() {
        val html = render { offlineDocument(catalog) }
        checkPage(html)
        catalog.fiches.forEach { assertTrue(html.contains(it.title), it.title) }
        val script = OfflineWorker.script(catalog)
        catalog.fiches.forEach { assertTrue(script.contains("\"${ComprendreLinks.pathOf(it)}\"")) }
        assertTrue(script.contains("\"/comprendre/hors-ligne\""))
        assertFalse(script.contains("__"), "every placeholder is replaced")
        assertTrue(Regex("const VERSION = \"ppf-[0-9a-f]{16}\";").containsMatchIn(script))
        assertFalse(script.contains("http://") || script.contains("https://"), "no other host")
        assertEquals(OfflineWorker.version(catalog), OfflineWorker.version(catalog))
        assertTrue(OfflineWorker.version(catalog) != OfflineWorker.version(ComprendreCatalog.EMPTY))
    }

    @Test
    fun theMarkdownNeverWritesTheHtmlOfAContent() {
        val renderer = ComprendreRenderer()
        val html = createHTML().html {
            body {
                renderer.markdown(this, "<script>alert(1)</script>\n\n[piège](javascript:alert(1)) et [site](ppf:inconnu) et [ok](ppf:agir)\n\n::: inconnu\nTexte **gras** <b>x</b>\n:::")
            }
        }
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("<a href=\"/agir\">ok</a>"))
        assertTrue(html.contains("<strong>gras</strong>"))
        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt;"))
    }

    @Test
    fun theNotFoundPageSaysSo() {
        val html = render { comprendreNotFoundDocument() }
        checkPage(html)
        assertTrue(html.contains("Contenu introuvable"))
    }

    private companion object {
        const val DIRECTORY = "shared/ui/src/commonMain/composeResources/files/contents"
        val FILES = listOf("fiches.json", "parcours.json", "comprendre.json", "glossaire.json")
    }
}

