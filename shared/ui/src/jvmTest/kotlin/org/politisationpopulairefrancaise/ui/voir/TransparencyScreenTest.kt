package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** « Transparence de PPF » (PP-01): the four rubrics always shown, functions before names, the result computed, « non mesuré » rather than zero. */
@OptIn(ExperimentalTestApi::class)
class TransparencyScreenTest {
    private val keys = IdentityKeys.generate()

    private fun page(slug: String, title: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), ArticleType.TRANSPARENCE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Texte de la page.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val catalog = VoirCatalog.from(
        listOf(
            page(
                "bureau", "[EXEMPLE] Le bureau", listOf(VoirTags.RUBRIC, "instance"), listOf(VoirTags.INSTANCE, "bureau"),
                listOf(VoirTags.SEAT, "Présidence", "", "2027-03-01", "Élue par le conseil d'administration"),
                listOf(VoirTags.SEAT, "Trésorerie", "[EXEMPLE] Camille Exemple", "2027-03-01", ""),
            ),
            page(
                "comptes-2027", "[EXEMPLE] Comptes de l'exercice 2027", listOf(VoirTags.RUBRIC, "comptes"), listOf(VoirTags.YEAR, "2027"),
                listOf(VoirTags.ACCOUNT_LINE, "produit", "Cotisations", "300"),
                listOf(VoirTags.ACCOUNT_LINE, "charge", "Serveur", "450,50"),
            ),
            page(
                "rapport-2027", "[EXEMPLE] Rapport 2027", listOf(VoirTags.RUBRIC, "rapport"), listOf(VoirTags.PERIOD, "2027-01-01", "2027-12-31"),
                listOf(VoirTags.FIGURE, "signalements-recus", "120"),
            ),
        ),
    )

    @Test
    fun theFourRubricsFunctionsBeforeNamesAndTheComputedResult() = runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes) {
        setContent {
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(900.dp, 4000.dp)) { TransparencyPage(catalog.transparency, Modifier.verticalScroll(rememberScrollState())) }
            }
        }
        waitForIdle()
        onNodeWithTag(TransparencyTags.EXAMPLE).assertExists()
        // The decisions have nothing yet: the rubric is there and says so.
        assertEquals(1, onAllNodesWithText("Rien n’est encore publié dans cette rubrique.").fetchSemanticsNodes().size)
        onNodeWithTag(TransparencyTags.section("decisions")).assertExists()
        listOf("instances", "accounts", "reports").forEach { onNodeWithTag(TransparencyTags.section(it)).assertExists() }

        // A function without name, a name only when written.
        assertEquals(1, onAllNodesWithText("Présidence").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("Trésorerie : Camille Exemple").fetchSemanticsNodes().size)

        // The result is computed: 300 € minus 450,50 €, a deficit.
        assertEquals(1, onAllNodesWithText("Résultat (produits moins charges) : -" + euros(15_050), substring = true).fetchSemanticsNodes().size)

        // A figure not given is « non mesuré », never zero.
        assertEquals(1, onAllNodesWithText("Signalements reçus : 120").fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithText("Transmissions aux autorités : non mesuré").fetchSemanticsNodes().size)
        assertTrue(onAllNodesWithText("Transmissions aux autorités : 0").fetchSemanticsNodes().isEmpty())
    }
}
