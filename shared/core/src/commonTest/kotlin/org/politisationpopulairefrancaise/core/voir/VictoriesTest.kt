package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Nos victoires » (PP-03): no victory without its day and its source; newest first; links to the blocks of Voir. */
class VictoriesTest {
    private val keys = IdentityKeys.generate()
    private val author = keys.publicKey.hex

    private fun victory(slug: String, title: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), ArticleType.VICTOIRE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Ce qui a été obtenu.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val source = listOf(VoirTags.SOURCE, "texte-officiel", "Journal officiel (exemple)", "https://example.org/exemple/jo", "2026-12-01")

    @Test
    fun aVictoryNeedsItsKindItsDayAndAtLeastOneSource() {
        val kept = assertNotNull(
            Victory.from(
                victory(
                    "droit-manifester", "[EXEMPLE] Le droit de manifester préservé", listOf(VoirTags.VICTORY, "engagement-tenu"), listOf(VoirTags.DATE, "2026-12-01"), source,
                    listOf(VoirTags.LINK, "30023:$author:promesse-camille-droit-manifester", "", VoirTags.ROLE_RELATED),
                ),
            ),
        )
        assertEquals(VictoryKind.COMMITMENT_KEPT, kept.kind)
        assertEquals(CivilDate(2026, 12, 1), kept.date)
        assertEquals(listOf("promesse-camille-droit-manifester"), kept.related.map { it.identifier })
        assertNull(Victory.from(victory("sans-source", "[EXEMPLE] Sans source", listOf(VoirTags.VICTORY, "loi-modifiee"), listOf(VoirTags.DATE, "2026-12-01"))), "no victory without its proof")
        assertNull(Victory.from(victory("sans-date", "[EXEMPLE] Sans date", listOf(VoirTags.VICTORY, "loi-modifiee"), source)))
        assertNull(Victory.from(victory("sans-genre", "[EXEMPLE] Sans genre", listOf(VoirTags.DATE, "2026-12-01"), source)))
    }

    @Test
    fun theVictoriesAreListedNewestFirstAndStayOutOfTheAxes() {
        val catalog = VoirCatalog.from(
            listOf(
                victory("a", "[EXEMPLE] Première", listOf(VoirTags.VICTORY, "loi-modifiee"), listOf(VoirTags.DATE, "2026-10-01"), source),
                victory("b", "[EXEMPLE] Seconde", listOf(VoirTags.VICTORY, "revendication-satisfaite"), listOf(VoirTags.DATE, "2026-12-01"), source),
            ),
        )
        assertEquals(listOf("b", "a"), catalog.victories.map { it.article.slug })
        assertTrue(catalog.isEmpty, "never a block of the axes")
        assertTrue(ArticleType.VICTOIRE in VoirCatalog.TYPES)
    }

    @Test
    fun theSchemaRefusesAVictoryWithoutSource() {
        val context = CheckContext.of(author, mapOf("promesse-camille-droit-manifester" to ArticleType.FAIT))
        val entries = listOf(
            "slug" to "droit-manifester",
            "title" to "[EXEMPLE] Le droit de manifester préservé",
            "summary" to "Une promesse tenue.",
            "type" to "victoire",
            "origin" to "human",
            "published_at" to "2026-12-02",
            "updated_at" to "2026-12-02",
            "victory" to "engagement-tenu",
            "date" to "2026-12-01",
            "related" to "promesse-camille-droit-manifester",
            "source" to "texte-officiel | Journal officiel (exemple) | https://example.org/exemple/jo | 2026-12-01",
        )
        assertEquals(emptyList(), EditorialEngine.check(VoirSchemas.VICTOIRE, ContentDraft(ArticleType.VICTOIRE, entries, "[EXEMPLE] Corps.\n"), context).map { it.code })
        val withoutSource = entries.filterNot { it.first == "source" }
        assertTrue(EditorialEngine.check(VoirSchemas.VICTOIRE, ContentDraft(ArticleType.VICTOIRE, withoutSource, "[EXEMPLE] Corps.\n"), context).isNotEmpty())
    }
}
