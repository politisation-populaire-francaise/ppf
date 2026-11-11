package org.politisationpopulairefrancaise.web

import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegalPagesTest {

    private fun render(page: LegalPage): String {
        val text = assertNotNull(LegalPages.load(page), "missing resource legal/${page.file}")
        return createHTML().html { legalPage(text) }
    }

    @Test
    fun everyLegalTextIsAResourceOfTheSiteWithItsTitle() {
        assertEquals(
            listOf("/mentions-legales", "/confidentialite", "/conditions-utilisation", "/charte", "/signaler"),
            LegalPages.ALL.map { it.path },
        )
        for (page in LegalPages.ALL) {
            val text = assertNotNull(LegalPages.load(page), page.file)
            assertTrue(text.title.isNotBlank(), page.file)
            assertEquals("2026-11-11", text.updatedAt, page.file)
            // The body starts after the front matter and never repeats the title as a heading.
            assertFalse(text.body.startsWith("---"), page.file)
            assertFalse(text.body.lines().any { it.startsWith("# ") }, page.file)
        }
    }

    @Test
    fun everyPageSaysItIsADraftForTheLawyerAndHasOneH1() {
        for (page in LegalPages.ALL) {
            val html = render(page)
            assertTrue("PROJET, À FAIRE RELIRE PAR UN AVOCAT" in html, page.file)
            assertEquals(1, Regex("<h1[ >]").findAll(html).count(), page.file)
            assertTrue("Version du 11 novembre 2026" in html, page.file)
            assertFalse("<script" in html, page.file)
            // No resource is loaded from another server (links to official sources are allowed).
            assertFalse(Regex("""src="https?://""").containsMatchIn(html), page.file)
        }
    }

    @Test
    fun theTextsNeverShowMarkdownTheSiteCannotRender() {
        for (page in LegalPages.ALL) {
            val body = assertNotNull(LegalPages.load(page)).body
            assertFalse(body.lines().any { it.trimStart().startsWith("|") }, "${page.file}: tables are not rendered")
            assertFalse('`' in body, "${page.file}: code is not rendered")
        }
    }

    @Test
    fun theLegalTextsLinkToEachOtherOnTheSite() {
        assertTrue("href=\"/signaler\"" in render(LegalPages.ALL.single { it.path == "/mentions-legales" }))
        val report = render(LegalPages.ALL.single { it.path == "/signaler" })
        assertTrue("href=\"/securite\"" in report)
        assertTrue("href=\"/transparence\"" in report)
        assertTrue("href=\"/charte\"" in render(LegalPages.ALL.single { it.path == "/conditions-utilisation" }))
    }

    @Test
    fun theFooterOfEveryPageLinksTheLegalTexts() {
        val html = createHTML().html { siteLayout(pageTitle = "Test") { h1 { +"Test" } } }
        for (page in LegalPages.ALL) assertTrue("href=\"${page.path}\"" in html, page.path)
    }

    @Test
    fun frontMatterAndHeadingGiveTheTitle() {
        val withFields = LegalPages.parse("---\ntitle: Charte\nupdated_at: 2026-11-11\n---\n**PROJET**\n\n## Règles\n- Une\n")
        assertEquals("Charte", withFields.title)
        assertEquals("2026-11-11", withFields.updatedAt)
        assertEquals("**PROJET**\n\n## Règles\n- Une", withFields.body)

        val withHeading = LegalPages.parse("# Mentions légales\r\n\r\nTexte.\r\n")
        assertEquals("Mentions légales", withHeading.title)
        assertNull(withHeading.updatedAt)
        assertEquals("Texte.", withHeading.body)
    }

    @Test
    fun datesAreWrittenInFrench() {
        assertEquals("11 novembre 2026", frenchDate("2026-11-11"))
        assertEquals("1er août 2027", frenchDate("2027-08-01"))
        assertEquals("bientôt", frenchDate("bientôt"))
    }

    @Test
    fun blanksStopProductionButNotPreproduction() {
        assertEquals(
            listOf("[À COMPLÉTER : adresse de contact]", "[À REMPLIR : nom]", "[À CONFIRMER au contrat]", "[À VÉRIFIER avant publication]"),
            LegalPages.blanksIn(
                "Contact : [À COMPLÉTER : adresse de contact]. Directeur : [À REMPLIR : nom]. " +
                    "Lieu : [À CONFIRMER au contrat]. Cookies : [À VÉRIFIER avant publication]. " +
                    "Base juridique : [À FAIRE PRÉCISER PAR L'AVOCAT, question 2]. Lien : [charte](/charte).",
            ),
        )
        // Today the publisher is still blank: it is filled in when the association is declared.
        val blanks = LegalPages.blanks()
        assertTrue(blanks.getValue("mentions-legales.md").any { it.startsWith("[À COMPLÉTER à la déclaration") }, blanks.toString())
        LegalPages.requireComplete("development")
        LegalPages.requireComplete("preproduction")
        val refused = assertFailsWith<IllegalArgumentException> { LegalPages.requireComplete("production") }
        assertTrue("mentions-legales.md" in refused.message.orEmpty(), refused.message)
    }
}
