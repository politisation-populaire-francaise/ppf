package org.politisationpopulairefrancaise.core.editorial

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleTest {

    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)

    @Test
    fun buildsAndReadsBackAnArticle() = runTest {
        val unsigned = Article.build(
            author = keys.publicKey,
            slug = "garde-a-vue",
            title = "[EXEMPLE] Garde à vue",
            summary = "Vos droits",
            topics = listOf("droits", "garde-a-vue", "droits"),
            type = ArticleType.FICHE_PRATIQUE,
            origin = ContentOrigin.HUMAN,
            publishedAt = 1_793_880_000,
            updatedAt = 1_794_500_000,
            body = "## Durée\n\nVingt-quatre heures.\n",
        )
        val event = signer.sign(unsigned)
        assertTrue(event.verify())
        val article = Article.from(event)!!
        assertEquals("garde-a-vue", article.slug)
        assertEquals("[EXEMPLE] Garde à vue", article.title)
        assertEquals("Vos droits", article.summary)
        assertEquals(listOf("droits", "garde-a-vue"), article.topics)
        assertEquals(ArticleType.FICHE_PRATIQUE, article.type)
        assertEquals(ContentOrigin.HUMAN, article.origin)
        assertEquals(1_793_880_000, article.publishedAt)
        assertEquals(1_794_500_000, article.updatedAt)
        assertEquals(keys.publicKey.hex, article.author)
        assertEquals("30023:${keys.publicKey.hex}:garde-a-vue", article.address.toString())
        assertTrue(article.body.startsWith("## Durée"))
    }

    @Test
    fun rejectsWhatIsNotAnArticle() = runTest {
        assertNull(Article.from(signer.sign(kind = EventKinds.TEXT_NOTE, content = "x")))
        val withoutTitle = signer.sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a"), listOf(Article.TAG_ORIGIN, "human")))
        assertNull(Article.from(withoutTitle))
        val withoutOrigin = signer.sign(kind = Article.KIND, content = "x", tags = listOf(listOf("d", "a"), listOf(Article.TAG_TITLE, "T")))
        assertNull(Article.from(withoutOrigin))
        assertFailsWith<IllegalArgumentException> {
            Article.build(keys.publicKey, "two words", "T", null, emptyList(), ArticleType.ARTICLE, ContentOrigin.HUMAN, 1, 1, "")
        }
    }

    @Test
    fun trustIsAnExplicitList() {
        val trust = EditorialTrust.bundledExamples()
        assertTrue(trust.isTrusted(EditorialTrust.EXAMPLE_EDITOR))
        assertEquals(false, trust.isTrusted(keys.publicKey.hex))
        assertTrue(trust.plus(keys.publicKey.hex).isTrusted(keys.publicKey.hex))
    }
}
