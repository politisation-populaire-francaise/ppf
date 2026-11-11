package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.components.SymbolTags
import org.politisationpopulairefrancaise.ui.endirect.EnDirectTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tutorial of the first launch (D-211, D-216): shown once, in the owner's order, « Passer »
 * always visible, the « Bientôt » boxes, the four choices of the last screen, seen again from
 * Mon espace; on a phone, a phone on its side and a computer, with a large font.
 */
@OptIn(ExperimentalTestApi::class)
class FirstLaunchTutorialTest {

    private fun dependencies(store: TutorialStore?) = AppDependencies(identityStore = InMemoryIdentityStore(), tutorial = store)

    private fun ComposeUiTest.show(store: TutorialStore?, width: Int = 360, height: Int = 740, fontScale: Float = 1f) {
        val provided = dependencies(store)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(provided) }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.has(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theFirstLaunchShowsTheSevenScreensInTheOrderOfTheOwner() = runComposeUiTest {
        val store = InMemoryTutorialStore()
        show(store)
        onNodeWithTag(TutorialTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertDoesNotExist()
        // The first screen presents the platform with its symbol, named for screen readers (D-185).
        onNodeWithTag(SymbolTags.SYMBOL).assertIsDisplayed().assertContentDescriptionContains("Symbole de Politisation Populaire Française", substring = true)
        val titles = listOf(
            "Reprendre la parole, ensemble",
            "Ici, vous êtes chez vous",
            "Agir près de chez vous",
            "Se réunir",
            "Décider ensemble",
            "En cas de besoin : AIDE",
            "Par où commencer ?",
        )
        // Only the screens that mention what is not built yet carry a « Bientôt » box.
        val soon = setOf(2, 3, 5)
        titles.forEachIndexed { index, title ->
            onNodeWithText(title).assertExists()
            onNodeWithText("Étape ${index + 1} sur 7").assertExists()
            onNodeWithTag(TutorialTags.SKIP).assertIsDisplayed()
            assertEquals(index in soon, has(TutorialTags.SOON), "Bientôt on screen ${index + 1}")
            assertEquals(index > 0, has(TutorialTags.PREVIOUS), "Précédent on screen ${index + 1}")
            if (index < titles.lastIndex) {
                onNodeWithTag(TutorialTags.NEXT).performClick()
                waitForIdle()
            }
        }
        onNodeWithTag(TutorialTags.NEXT).assertDoesNotExist()
        onNodeWithText("Je veux…").assertExists()
        for (choice in TutorialStart.entries) onNodeWithTag(TutorialTags.start(choice)).assertExists()
        assertFalse(store.seen(), "not marked seen before the end")

        // « m'allier » leaves the tutorial for Agir; the tutorial will not open again by itself.
        onNodeWithTag(TutorialTags.start(TutorialStart.ALLY)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).assertIsSelected()
        assertTrue(store.seen())
    }

    @Test
    fun theFourChoicesLeadToTheirSpaces() {
        val expected = mapOf(
            TutorialStart.ACT to Space.AGIR,
            TutorialStart.OPINION to Space.DECIDER,
            TutorialStart.UNDERSTAND to Space.COMPRENDRE,
            TutorialStart.ALLY to Space.AGIR,
        )
        assertEquals(expected, TutorialStart.entries.associateWith { it.space })
        // On a computer every space is in the side bar: the chosen one is selected.
        for ((choice, space) in expected) runComposeUiTest {
            show(InMemoryTutorialStore(), width = 1000, height = 740)
            repeat(TutorialStep.entries.size - 1) {
                onNodeWithTag(TutorialTags.NEXT).performClick()
                waitForIdle()
            }
            onNodeWithTag(TutorialTags.start(choice)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(ShellTags.navItem(space)).assertIsSelected()
        }
    }

    @Test
    fun skippingOpensTheLiveHomeAndIsRemembered() = runComposeUiTest {
        val store = InMemoryTutorialStore()
        show(store)
        onNodeWithTag(TutorialTags.NEXT).performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SKIP).performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).assertIsSelected()
        onNodeWithTag(EnDirectTags.SCREEN).assertExists()
        assertTrue(store.seen())
    }

    @Test
    fun aDeviceThatSawItOpensOnTheLiveHomeAndCanSeeItAgainFromMySpace() = runComposeUiTest {
        show(InMemoryTutorialStore(seen = true))
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(EnDirectTags.SCREEN).assertExists()
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.REPLAY).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertIsDisplayed()
        onNodeWithText("Étape 1 sur 7").assertExists()
    }

    @Test
    fun withoutAStoreThereIsNoTutorial() = runComposeUiTest {
        // Tests and previews: the hosts of the application are the only ones to give a store.
        show(store = null)
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.REPLAY).assertDoesNotExist()
    }

    @Test
    fun skipAndNextStayOnScreenOnEveryFormatWithALargeFont() {
        // A small phone, the same on its side, a computer; the largest font of the review (1.3).
        for ((width, height) in listOf(360 to 640, 640 to 360, 1000 to 740)) runComposeUiTest {
            show(InMemoryTutorialStore(), width = width, height = height, fontScale = 1.3f)
            repeat(TutorialStep.entries.size) { index ->
                onNodeWithTag(TutorialTags.SKIP).assertIsDisplayed()
                if (index < TutorialStep.entries.lastIndex) {
                    onNodeWithTag(TutorialTags.NEXT).assertIsDisplayed().performClick()
                    waitForIdle()
                }
            }
            for (choice in TutorialStart.entries) onNodeWithTag(TutorialTags.start(choice)).performScrollTo().assertIsDisplayed()
        }
    }
}
