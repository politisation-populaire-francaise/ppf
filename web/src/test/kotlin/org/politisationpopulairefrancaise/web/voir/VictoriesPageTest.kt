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

/** « Nos victoires » on the site (PP-03): newest first, the blocks of Voir linked, the sources, no repeated identifier. */
class VictoriesPageTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun article(slug: String, type: ArticleType, title: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Texte.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val source = listOf(VoirTags.SOURCE, "texte-officiel", "Journal officiel (exemple)", "https://example.org/exemple/jo", "2026-12-01")

    @Test
    fun theVictoriesWithTheirBlocksAndSources() {
        val catalog = VoirCatalog.from(
            listOf(
                article("promesse", ArticleType.FAIT, "[EXEMPLE] « Je m'opposerai à toute restriction »", listOf(VoirTags.FACT, "promesse"), listOf(VoirTags.DATE, "2026-01-10"), listOf(VoirTags.FACT_STATUS, "etabli"), listOf(VoirTags.PROMISE, "tenue"), source),
                article("v1", ArticleType.VICTOIRE, "[EXEMPLE] Première victoire", listOf(VoirTags.VICTORY, "loi-modifiee"), listOf(VoirTags.DATE, "2026-10-01"), source),
                article("v2", ArticleType.VICTOIRE, "[EXEMPLE] Seconde victoire", listOf(VoirTags.VICTORY, "engagement-tenu"), listOf(VoirTags.DATE, "2026-12-01"), source, listOf(VoirTags.LINK, "30023:$author:promesse", "", VoirTags.ROLE_RELATED)),
            ),
        )
        val html = createHTML(prettyPrint = false).html { victoriesDocument(catalog) }
        assertTrue(html.indexOf("Seconde victoire") < html.indexOf("Première victoire"), "newest first")
        assertTrue(html.contains("Engagement tenu · "))
        if (catalog.facts.isNotEmpty()) assertTrue(html.contains("href=\"/voir/faits/promesse\""), "the promise kept is linked")
        assertTrue(html.contains("Journal officiel (exemple)"))
        assertTrue(html.contains("href=\"/victoires\""), "linked from the footer")
        val ids = Regex("""\bid="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(ids.distinct(), ids)
        assertFalse(html.contains("<script"))
    }
}
