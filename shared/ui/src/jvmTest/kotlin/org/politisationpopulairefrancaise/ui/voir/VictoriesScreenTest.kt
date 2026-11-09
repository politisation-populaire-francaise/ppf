package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** « Nos victoires » (PP-03): every victory with its sources, a filter by kind, nothing without a victory. */
@OptIn(ExperimentalTestApi::class)
class VictoriesScreenTest {
    private val keys = IdentityKeys.generate()

    private fun victory(slug: String, kind: String, date: String): Article {
        val tags = listOf(
            listOf(VoirTags.VICTORY, kind), listOf(VoirTags.DATE, date),
            listOf(VoirTags.SOURCE, "texte-officiel", "Journal officiel (exemple)", "https://example.org/exemple/jo", date),
        )
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Victoire $slug", null, emptyList(), ArticleType.VICTOIRE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Ce qui a été obtenu.", extraTags = tags)
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    @Test
    fun theVictoriesAndTheFilterByKind() = runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes) {
        val catalog = VoirCatalog.from(listOf(victory("loi", "loi-modifiee", "2026-10-01"), victory("promesse", "engagement-tenu", "2026-12-01")))
        setContent {
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(900.dp, 760.dp)) { VictoriesPage(catalog, onOpen = {}, onNext = {}, modifier = Modifier.verticalScroll(rememberScrollState())) }
            }
        }
        waitForIdle()
        onNodeWithTag(VictoriesTags.victory("loi")).assertExists()
        onNodeWithTag(VictoriesTags.victory("promesse")).assertExists()
        onNodeWithTag(VictoriesTags.kind("loi-modifiee")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(VictoriesTags.victory("promesse")).fetchSemanticsNodes().size)
        onNodeWithTag(VictoriesTags.victory("loi")).assertExists()
        assertEquals(0, onAllNodesWithTag(VictoriesTags.kind("revendication-satisfaite")).fetchSemanticsNodes().size, "only the kinds that have a victory")
    }

    @Test
    fun withoutVictoryThePageSaysSo() = runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes) {
        setContent { PpfTheme(darkTheme = false) { VictoriesPage(VoirCatalog.EMPTY, onOpen = {}, onNext = {}) } }
        waitForIdle()
        onNodeWithTag(VictoriesTags.EMPTY).assertExists()
    }
}
