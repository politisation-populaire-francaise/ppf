package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.decider.DeliberationBoard
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test

/** The page of a deliberation (DE-25, MVP 1), on fictional contents built here. */
@OptIn(ExperimentalTestApi::class)
class DeliberationViewsTest {
    private val team = "a".repeat(64)
    private val alice = "b".repeat(64)
    private var counter = 0

    private fun event(pubkey: String, tags: List<List<String>>, content: String = "[EXEMPLE] Texte de la mesure"): SignedEvent {
        counter++
        return SignedEvent(id = counter.toString(16).padStart(64, '0'), pubkey = pubkey, createdAt = 100L + counter, kind = EventKinds.LONG_FORM_ARTICLE, tags = tags, content = content, sig = "0".repeat(128))
    }

    private val deliberation = event(
        team,
        listOf(
            listOf("d", "pacte-exemple"), listOf("title", "[EXEMPLE] Les mesures du Pacte"), listOf("ppf-type", "deliberation"),
            listOf("ppf-phase", "propositions", "1000", "2000"), listOf("ppf-phase", "amendements", "2000", "3000"), listOf("ppf-phase", "vote", "3000", "4000"),
            listOf("ppf-measures", "5", "10"),
        ),
    )
    private val link = ContentAddress.of(deliberation)!!.toString()
    private val start = event(team, listOf(listOf("d", "depart"), listOf("title", "[EXEMPLE] Mesure de départ"), listOf("ppf-type", "proposition"), listOf("a", link), listOf("ppf-origin", "depart")))
    private val fromAlice = event(alice, listOf(listOf("d", "alice"), listOf("title", "[EXEMPLE] Mesure d’une citoyenne"), listOf("ppf-type", "proposition"), listOf("a", link), listOf("ppf-justification", "[EXEMPLE] Parce que")))
    private val board = DeliberationBoard.from(listOf(deliberation, start, fromAlice)) { it == team }.single()

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    @Test
    fun theStepsTheStartingProposalAndTheFormOfAProposition() = runComposeUiTest {
        val composer = DeliberationComposer(store = null, remote = null)
        setContent {
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 760.dp).verticalScroll(rememberScrollState())) {
                    DeliberationPage(board, now = 1500, composer = composer, signer = null)
                }
            }
        }
        onNodeWithTag(DeliberationTags.PHASES).assert(has("Propositions")).assert(has("En cours"))
        onNodeWithTag(DeliberationTags.proposition("depart")).performScrollTo().assert(has("Proposition de départ, pas encore votée"))
        onNodeWithTag(DeliberationTags.proposition("alice")).performScrollTo().assert(has("Pourquoi : [EXEMPLE] Parce que"))

        onNodeWithTag(DeliberationTags.PROPOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(DeliberationTags.FORM).assertExists()
        onNodeWithTag(DeliberationTags.FORM_SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(DeliberationTags.FORM_TITLE).performTextInput("[EXEMPLE] Une mesure")
        onNodeWithTag(DeliberationTags.FORM_TEXT).performTextInput("[EXEMPLE] Le texte complet")
        onNodeWithTag(DeliberationTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        // Without an account, nothing is sent: the button waits for one.
        onNodeWithTag(DeliberationTags.FORM_SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(DeliberationTags.FORM).assert(has("Créez un compte"))
    }

    @Test
    fun anAmendmentStartsFromTheWordingItRewritesAndMustChangeIt() = runComposeUiTest {
        val composer = DeliberationComposer(store = null, remote = null)
        setContent {
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 760.dp).verticalScroll(rememberScrollState())) {
                    DeliberationPage(board, now = 2500, composer = composer, signer = null)
                }
            }
        }
        onNodeWithTag(DeliberationTags.amend("alice")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(DeliberationTags.FORM_TEXT).assert(has("[EXEMPLE] Texte de la mesure"))
        onNodeWithTag(DeliberationTags.FORM_CHARTER).performScrollTo().performClick()
        waitForIdle()
        kotlin.test.assertFalse(composer.ready, "an unchanged wording is not an amendment")
        onNodeWithTag(DeliberationTags.FORM_TEXT).performTextInput(" [EXEMPLE] changée")
        waitForIdle()
        kotlin.test.assertTrue(composer.ready)
        onNodeWithTag(DeliberationTags.FORM_SEND).performScrollTo().assertIsNotEnabled()
    }
}
