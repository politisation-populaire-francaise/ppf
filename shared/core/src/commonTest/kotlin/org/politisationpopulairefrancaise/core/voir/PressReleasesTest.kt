package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Espace presse » (PP-04): a press release needs its day; newest first; never a block of the axes. */
class PressReleasesTest {
    private val keys = IdentityKeys.generate()

    private fun release(slug: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Communiqué $slug", "Un chapô.", emptyList(), ArticleType.COMMUNIQUE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Texte du communiqué.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    @Test
    fun aPressReleaseNeedsItsDayAndTheNewestComesFirst() {
        assertNull(PressRelease.from(release("sans-date")))
        val catalog = VoirCatalog.from(listOf(release("ancien", listOf(VoirTags.DATE, "2026-10-01")), release("recent", listOf(VoirTags.DATE, "2026-11-09"))))
        assertEquals(listOf("recent", "ancien"), catalog.pressReleases.map { it.article.slug })
        assertTrue(catalog.isEmpty, "never a block of the axes")
        assertTrue(ArticleType.COMMUNIQUE in VoirCatalog.TYPES)
        assertEquals(VoirCatalog.TYPES, VoirSchemas.all.map { it.type })
    }

    @Test
    fun theMediaKitNamesPpfWithTheColoursOfItsIdentity() {
        assertEquals("Politisation Populaire Française", MediaKit.NAME)
        assertEquals("#2B3A8F", MediaKit.BRAND_COLOR, "the brand colour of PpfTheme (D-025)")
        assertEquals(15, MediaKit.PRESS_REPLY_GOAL_DAYS, "the goal of the contact of a journalist (M30)")
    }
}
