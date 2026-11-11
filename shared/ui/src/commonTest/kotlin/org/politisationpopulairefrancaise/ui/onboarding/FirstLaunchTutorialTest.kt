package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import org.politisationpopulairefrancaise.ui.account.AccountTags
import org.politisationpopulairefrancaise.ui.agir.gather.GatherTags
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.endirect.EnDirectTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tutorial of the first launch, version 2 (D-216, D-186): shown once, the owner's eleven screens in
 * their order, « Passer » always visible, her pictures, the « Bientôt » boxes, no label of origin, the
 * buttons of the last screen and where they lead, seen again from Mon espace; on a phone, a phone on its
 * side and a computer, with a large font.
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

    private fun ComposeUiTest.toLastScreen() {
        repeat(TutorialStep.entries.size - 1) {
            onNodeWithTag(TutorialTags.NEXT).performClick()
            waitForIdle()
        }
    }

    @Test
    fun theFirstLaunchShowsTheElevenScreensOfTheOwnerInHerOrder() = runComposeUiTest {
        val store = InMemoryTutorialStore()
        show(store)
        onNodeWithTag(TutorialTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(ShellTags.BOTTOM_BAR).assertDoesNotExist()
        val titles = listOf(
            "Ensemble, sauvons la démocratie",
            "Comment faire",
            "S’informer",
            "Comprendre",
            "Témoigner",
            "Revendiquer",
            "Communiquer",
            "Se politiser",
            "Agir",
            "Vos données vous appartiennent",
            "Par où commencer",
        )
        assertEquals(TutorialStep.entries.size, titles.size)
        // Her pictures on the screens 1, 2 and 6; the « Bientôt » boxes on « s'unir » and « Agir » only.
        val pictures = setOf(0, 1, 5)
        val soon = setOf(6, 8)
        titles.forEachIndexed { index, title ->
            onNodeWithTag(TutorialTags.TITLE).assert(hasText(title, substring = true))
            // « non enlève les étapes »: no number in the titles, only the counter of the interface.
            assertFalse(onNodeWithTag(TutorialTags.TITLE).fetchSemanticsNode().config.toString().contains("Étape"), "no step number in the title ${index + 1}")
            onNodeWithText("Étape ${index + 1} sur 11").assertExists()
            onNodeWithTag(TutorialTags.SKIP).assertIsDisplayed()
            assertEquals(index in pictures, has(TutorialTags.ILLUSTRATION), "picture on screen ${index + 1}")
            assertEquals(index in soon, has(TutorialTags.SOON), "Bientôt on screen ${index + 1}")
            assertEquals(index > 0, has(TutorialTags.PREVIOUS), "Précédent on screen ${index + 1}")
            // No label of origin on the tutorial any more (« tu pourras retirer "écrit par une ia" »).
            assertTrue(onAllNodesWithText("par l’IA", substring = true).fetchSemanticsNodes().isEmpty(), "label of origin on screen ${index + 1}")
            if (index == 5) assertTrue(onAllNodesWithText("Constitution Citoyenne et Démocratique", substring = true).fetchSemanticsNodes().isNotEmpty(), "her new name of the pact")
            if (index == 0) {
                onNodeWithTag(TutorialTags.ILLUSTRATION).assertIsDisplayed()
                assertTrue(onAllNodesWithText("sauver notre démocratie", substring = true).fetchSemanticsNodes().isNotEmpty(), "her text, word for word")
            }
            if (index < titles.lastIndex) {
                onNodeWithTag(TutorialTags.NEXT).performClick()
                waitForIdle()
            }
        }
        onNodeWithTag(TutorialTags.NEXT).assertDoesNotExist()
        onNodeWithTag(TutorialTags.start(TutorialStart.HELP)).assertExists()
        for (choice in TutorialStart.grid) onNodeWithTag(TutorialTags.start(choice)).assertExists()
        assertFalse(store.seen(), "not marked seen before the end")

        // « Rejoindre un collectif » leaves the tutorial for the page « Se réunir » of Agir.
        onNodeWithTag(TutorialTags.start(TutorialStart.GATHER)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).assertIsSelected()
        waitUntil(timeoutMillis = 10_000) { has(GatherTags.SCREEN) }
        assertTrue(store.seen())
    }

    @Test
    fun theButtonsOfTheLastScreenLeadToTheirPlaces() {
        assertEquals(
            listOf("informer", "comprendre", "temoigner", "revendiquer", "collectif", "politiser", "agir", "soutenir"),
            TutorialStart.grid.map { it.id },
        )
        // On a computer every space is in the side bar: the chosen one is selected.
        for (choice in TutorialStart.grid) runComposeUiTest {
            show(InMemoryTutorialStore(), width = 1000, height = 740)
            toLastScreen()
            onNodeWithTag(TutorialTags.start(choice)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(ShellTags.navItem(choice.space!!)).assertIsSelected()
        }
    }

    @Test
    fun receivingHelpOpensTheAideSheet() = runComposeUiTest {
        show(InMemoryTutorialStore())
        toLastScreen()
        onNodeWithTag(TutorialTags.start(TutorialStart.HELP)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        waitUntil(timeoutMillis = 10_000) { has(ManifestationTags.SHEET) }
    }

    @Test
    fun theScreenOnDataOpensTheSettingsOfMySpace() = runComposeUiTest {
        show(InMemoryTutorialStore(), width = 1000, height = 740)
        repeat(TutorialStep.PRIVACY.ordinal) {
            onNodeWithTag(TutorialTags.NEXT).performClick()
            waitForIdle()
        }
        onNodeWithTag(TutorialTags.start(TutorialStart.PRIVACY_SETTINGS)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(TutorialTags.SCREEN).assertDoesNotExist()
        // Mon espace, without an account: its card to create one, above the settings.
        waitUntil(timeoutMillis = 10_000) { has(AccountTags.CREATE) }
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
        onNodeWithText("Étape 1 sur 11").assertExists()
        onNodeWithTag(TutorialTags.ILLUSTRATION, useUnmergedTree = true).assertExists()
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
    fun skipAndNextStayOnScreenAndEveryTextCanBeReadOnEveryFormatWithALargeFont() {
        // A small phone, the same on its side, a computer; the largest font of the review (1.3).
        for ((width, height) in listOf(360 to 640, 640 to 360, 1000 to 740)) runComposeUiTest {
            show(InMemoryTutorialStore(), width = width, height = height, fontScale = 1.3f)
            repeat(TutorialStep.entries.size) { index ->
                onNodeWithTag(TutorialTags.SKIP).assertIsDisplayed()
                // The title of every screen can be reached by scrolling, under its picture.
                onNodeWithTag(TutorialTags.TITLE).performScrollTo().assertIsDisplayed()
                if (index < TutorialStep.entries.lastIndex) {
                    onNodeWithTag(TutorialTags.NEXT).assertIsDisplayed().performClick()
                    waitForIdle()
                }
            }
            onNodeWithTag(TutorialTags.start(TutorialStart.HELP)).performScrollTo().assertIsDisplayed()
            for (choice in TutorialStart.grid) onNodeWithTag(TutorialTags.start(choice)).performScrollTo().assertIsDisplayed()
        }
    }
}
