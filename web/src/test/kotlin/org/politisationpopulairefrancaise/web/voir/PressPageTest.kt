package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Espace presse » on the site (PP-04): releases, media kit, reusable figures, contact through M30; no repeated identifier. */
class PressPageTest {
    private val keys = IdentityKeys.generate()

    private fun release(slug: String, date: String): Article {
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Communiqué $slug", "Un chapô.", emptyList(), ArticleType.COMMUNIQUE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Texte.", extraTags = listOf(listOf(VoirTags.DATE, date)))
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    @Test
    fun theFourPartsOfThePressSpace() {
        val catalog = VoirCatalog.from(listOf(release("un", "2026-10-01"), release("deux", "2026-11-09")))
        val html = createHTML(prettyPrint = false).html { pressDocument(catalog) }
        assertTrue(html.indexOf("Communiqué deux") < html.indexOf("Communiqué un"), "newest first")
        listOf("Kit média", "#2B3A8F", "Atkinson Hyperlegible", "href=\"/donnees\"", "href=\"/transparence\"", "href=\"/victoires\"", "« Journaliste »", "15 jours").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("href=\"/presse\""), "linked from the footer")
        val ids = Regex("""\bid="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(ids.distinct(), ids)
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("Marianne"))
    }
}
