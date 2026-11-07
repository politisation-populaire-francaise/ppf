package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Voir", read offline from the contents bundled with the application (all marked [EXEMPLE]). */
@OptIn(ExperimentalTestApi::class)
class VoirScreenTest {

    private val database = JvmDatabase.inMemory()

    private fun dependencies() = AppDependencies(
        identityStore = InMemoryIdentityStore(),
        contentStore = LocalEventStore(database),
        editorialTrust = EditorialTrust.bundledExamples(),
        contentFollowStore = ContentFollowStore(database),
    )

    private fun ComposeUiTest.openVoir(dependencies: AppDependencies, width: Int, height: Int) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Voir").performClick()
        waitForIdle()
        if (width < 600) {
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun keyFiguresShowEverySourceSideBySideAndOpenTheirPage() = runComposeUiTest {
        openVoir(dependencies(), width = 1000, height = 740)
        val card = onNodeWithTag(VoirTags.card("manifestants-journees"))
        card.assert(hasText("Autorités"))
        card.assert(hasText("Organisateurs"))
        card.assert(hasText("Comptage indépendant"))
        card.assert(hasText("Sources divergentes"))
        card.performClick()
        waitForIdle()

        onNodeWithTag(VoirTags.detail("manifestants-journees")).assertExists()
        onNodeWithTag(VoirTags.CHART).assertExists()
        onNodeWithText("Les sources ne sont jamais fusionnées", substring = true).assertExists()
        // The same data as a table, for screen readers.
        onNodeWithTag(VoirTags.CHART_TOGGLE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.TABLE).assertExists()
        onNodeWithTag(VoirTags.CHART).assertDoesNotExist()
        // The correction is shown on the page.
        onNodeWithText("Corrigé le 18 octobre 2026", substring = true).performScrollTo().assertExists()
        // A source opens only after a warning: the site would see the reader's IP address.
        onAllNodesWithText("Ouvrir la source").onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.LINK_DIALOG).assertExists()
        onNodeWithText("example.org", substring = true).assertExists()
        onNodeWithText("Rester ici").performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.LINK_DIALOG).assertDoesNotExist()

        // Back to the home of the space.
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.tab(0)).assertExists()
    }

    @Test
    fun followingStaysOnTheDeviceAndAppearsAboveTheTabs() = runComposeUiTest {
        val dependencies = dependencies()
        openVoir(dependencies, width = 1000, height = 740)
        onNodeWithTag(VoirTags.card("recours-49-3"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(VoirTags.card("etablissements-bloques")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.FOLLOW).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.FOLLOWED).assertExists()
        waitUntil(timeoutMillis = 5_000) { dependencies.contentFollowStore!!.all().isNotEmpty() }
        assertEquals(listOf("etablissements-bloques"), dependencies.contentFollowStore!!.all().map { it.identifier })
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.section("followed")).assert(hasAnyDescendant(hasText("Lycées et universités bloqués")))
        onNodeWithText("Ce que vous suivez reste sur cet appareil", substring = true).assertExists()
    }

    @Test
    fun everySheetHasTheSameSectionsAndCasesShowThePresumptionOfInnocence() = runComposeUiTest {
        openVoir(dependencies(), width = 1000, height = 740)
        onNodeWithTag(VoirTags.tab(1)).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("dominique-fictif")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("dominique-fictif")).assertExists()
        listOf("vote", "promesse", "discours-vote", "affaire", "declaration-contredite", "interets", "influence", "argent-public").forEach { section ->
            onNodeWithTag(VoirTags.section(section)).performScrollTo().assertExists()
        }
        onNodeWithTag(VoirTags.PRESUMPTION).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.card("affaire-dominique-marche-public")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("affaire-dominique-marche-public")).assertExists()
        onNodeWithText("Enquête en cours").assertExists()
        onNodeWithText("Droit de réponse de Dominique Fictif").performScrollTo().assertExists()
        onNodeWithTag(VoirTags.REPLY_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPLY_FORM).assertExists()
        // Back three times: the form, the fact, then the sheet.
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("affaire-dominique-marche-public")).assertExists()
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("dominique-fictif")).assertExists()
    }

    @Test
    fun aProhibitionAppearsInTheChronologyAndInTheObservatoryOfFreedoms() = runComposeUiTest {
        openVoir(dependencies(), width = 1000, height = 740)
        onNodeWithTag(VoirTags.seeAll("chronology")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("chrono-arrete-interdiction-exempleville")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.filter("mobilisation")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("chrono-arrete-interdiction-exempleville")).assertDoesNotExist()
        onNodeWithTag(VoirTags.card("chrono-quatrieme-journee")).assertExists()
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()

        onNodeWithTag(VoirTags.tab(2)).performClick()
        waitForIdle()
        // A measure without an occasion is named by its date, the French way.
        onNodeWithTag(VoirTags.card("recours-49-3")).assert(hasText("4 nov. 2026"))
        onNodeWithTag(VoirTags.seeAll("liberties")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("chrono-arrete-interdiction-exempleville")).performScrollTo().assertExists()
        onNodeWithTag(VoirTags.card("chrono-arrete-interdiction-exempleville")).assert(hasText("Recours : Suspendu par le juge"))
    }

    @Test
    fun billsShowTheirStageTheirNextVoteAndWhoGainsOrLoses() = runComposeUiTest {
        openVoir(dependencies(), width = 1000, height = 740)
        onNodeWithTag(VoirTags.tab(1)).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("pjl-ordre-public-manifestations")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Prochain vote le 18 novembre 2026", substring = true).assertExists()
        onNodeWithText("Étape 4 sur 7 : Sénat").assertExists()
        onNodeWithTag(VoirTags.section("impacts")).performScrollTo().assert(hasAnyDescendant(hasText("Personnes qui manifestent")))
        onNodeWithTag(VoirTags.section("votes")).performScrollTo().assert(hasAnyDescendant(hasText("Camille Exemple")))
        onNodeWithTag(VoirTags.section("lobbying")).performScrollTo().assertExists()
    }

    @Test
    fun searchFindsAPersonWithoutAccents() = runComposeUiTest {
        openVoir(dependencies(), width = 1000, height = 740)
        onNodeWithTag(VoirTags.SEARCH).performTextInput("temoin")
        waitForIdle()
        onNodeWithTag(VoirTags.SEARCH_RESULTS).assert(hasAnyDescendant(hasText("Sacha Témoin")))
        onNodeWithTag(VoirTags.card("sacha-temoin")).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("sacha-temoin")).assertExists()
        onNodeWithTag(VoirTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.SEARCH_CLEAR, useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.tab(0)).assertExists()
    }

    @Test
    fun onAPhoneTheNextStepOpensThePracticalGuide() = runComposeUiTest {
        openVoir(dependencies(), width = 400, height = 740)
        onNodeWithTag(VoirTags.card("interpellations")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.NEXT + 0).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ContentTags.GUIDE_DETAIL).assertExists()
        onNodeWithText("Garde à vue : vos droits").assertExists()
    }

    @Test
    fun theGlossaryExplainsTheLabels() = runComposeUiTest {
        openVoir(dependencies(), width = 400, height = 740)
        onNodeWithTag(VoirTags.GLOSSARY_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.GLOSSARY).assertExists()
        onNodeWithText("Allégué : affirmé par une source identifiée", substring = true).assertExists()
        onNodeWithText("Mise en examen : un juge d’instruction", substring = true).assertExists()
    }

    @Test
    fun theSystemBackClosesThePageThenReturnsToTheFeed() = runComposeUiTest {
        val input = DirectNavigationEventInput()
        setContent {
            val owner = LocalNavigationEventDispatcherOwner.current
            DisposableEffect(owner) {
                owner?.navigationEventDispatcher?.addInput(input)
                onDispose { owner?.navigationEventDispatcher?.removeInput(input) }
            }
            Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies()) }
        }
        onNodeWithText("Voir").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(VoirTags.card("manifestants-journees")).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("manifestants-journees")).assertExists()
        // First back: the page closes, "Voir" stays.
        runOnIdle { input.backCompleted() }
        waitForIdle()
        onNodeWithTag(VoirTags.detail("manifestants-journees")).assertDoesNotExist()
        onNodeWithTag(VoirTags.tab(0)).assertExists()
        // Second back: the feed, the default screen.
        runOnIdle { input.backCompleted() }
        waitForIdle()
        onNodeWithTag(VoirTags.SCREEN).assertDoesNotExist()
    }
}
