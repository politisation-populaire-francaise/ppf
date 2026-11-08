package org.politisationpopulairefrancaise.server.editorial

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The key of a space (D-095): the current key of its use once a signing service is configured, the former rule otherwise. */
class PublishedContentsTest {
    private val events = EventRepository(TestDatabase.dataSource)
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val platform = IdentityKeys.generate().publicKey.hex
    private val editors = setOf(editor.publicKey.hex, platform)

    @BeforeTest
    fun emptyTables() = TestDatabase.reset()

    private fun publishByEditor(slug: String) = runBlocking {
        val unsigned = Article.build(editor.publicKey, slug, "[EXEMPLE] $slug", null, emptyList(), ArticleType.DECRYPTAGE, ContentOrigin.HUMAN, 1_900_000_000, 1_900_000_000, "[EXEMPLE] Texte.\n")
        events.store(editor.sign(unsigned))
    }

    @Test
    fun theCurrentKeyOfTheUseIsTheKeyOfTheSpaceOnceASignerIsConfigured() {
        publishByEditor("un")
        publishByEditor("deux")
        // Without a signing service: the editorial key that signed the most contents of the space (D-093).
        assertEquals(editor.publicKey.hex, PublishedContents(events, editors::contains).snapshot().authorOf(EditorialSpace.COMPRENDRE))
        // With one: the current key of the use, even before it signed anything.
        val withSigner = PublishedContents(events, editors::contains, keyOf = { space -> if (space == EditorialSpace.COMPRENDRE) platform else null })
        assertEquals(platform, withSigner.snapshot().authorOf(EditorialSpace.COMPRENDRE))
        assertEquals(PublishedContents.PREVIEW_KEY, withSigner.snapshot().authorOf(EditorialSpace.VOIR), "a use without key keeps the former rule")
        // A service that fails or answers nonsense keeps the former rule.
        assertEquals(editor.publicKey.hex, PublishedContents(events, editors::contains, keyOf = { error("down") }).snapshot().authorOf(EditorialSpace.COMPRENDRE))
        assertEquals(editor.publicKey.hex, PublishedContents(events, editors::contains, keyOf = { "pas-une-cle" }).snapshot().authorOf(EditorialSpace.COMPRENDRE))
    }
}
