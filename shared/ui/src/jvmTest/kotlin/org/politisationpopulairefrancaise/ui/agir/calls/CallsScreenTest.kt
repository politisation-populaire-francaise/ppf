package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
import org.politisationpopulairefrancaise.core.agir.calls.CallStatus
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The wall of calls, its filters, the detail of a call, the answer kept on the device, and the warning before a link. */
@OptIn(ExperimentalTestApi::class)
class CallsScreenTest {

    private fun androidx.compose.ui.test.ComposeUiTest.cardsShown(): Set<String> =
        (Examples.OPEN + Examples.CLOSED).filter { onAllNodesWithTag(CallTags.CARD + it).fetchSemanticsNodes().isNotEmpty() }.toSet()

    /** The slugs of the cards shown, in the order of the semantics tree (the order of the wall). */
    private fun androidx.compose.ui.test.ComposeUiTest.cardOrder(): List<String> {
        val slugs = (Examples.OPEN + Examples.CLOSED).associateBy { CallTags.CARD + it }
        return onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a call card") { node ->
            val tag = androidx.compose.ui.semantics.SemanticsProperties.TestTag
            tag in node.config && node.config[tag] in slugs.keys
        }).fetchSemanticsNodes().map { slugs.getValue(it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]) }
    }

    /** A text of the detail pane (the card of the list may say the same). */
    private fun androidx.compose.ui.test.ComposeUiTest.inDetail(text: String) =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(CallTags.DETAIL)), useUnmergedTree = true)

    @Test
    fun theWallShowsTheOpenExampleCallsAndOpensOneBesideTheListOnWideWindows() = runComposeUiTest {
        val harness = showCalls(width = 1000, height = 740)
        assertEquals(8, harness.calls.calls.size)
        assertEquals(Examples.OPEN.toSet(), cardsShown())
        onNodeWithTag(CallTags.COUNT).assertTextContains("7 appels")
        onAllNodesWithText("Signer une pétition").onFirst().assertExists()

        onNodeWithTag(CallTags.CARD + Examples.MEETING).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.DETAIL).assertExists()
        onNodeWithTag(CallTags.WALL).assertExists()
        onNodeWithTag(CallTags.DETAIL_BACK).assertDoesNotExist()
        inDetail("Assemblée de quartier sur la hausse des loyers").assertExists()
        // The fields of the type, and the date in Paris time.
        onNodeWithText("[EXEMPLE] Collectif fictif des locataires des Tilleuls").performScrollTo().assertExists()
        inDetail("Le 14 novembre 2026 à 18\u00A0h\u00A030").assertExists()
        onNodeWithTag(CallTags.COUNTDOWN).assertTextContains("Dans 8 jours")
    }

    @Test
    fun filtersNarrowTheWallAndTheirChipsRemoveThem() = runComposeUiTest {
        val harness = showCalls()
        onNodeWithTag(CallTags.FILTERS).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.FILTER_TYPE + CallType.PETITION.tagValue).performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf(Examples.PETITION), cardsShown())
        onNodeWithTag(CallTags.CHIP + "type:petition").performScrollTo().performClick()
        waitForIdle()
        assertEquals(Examples.OPEN.toSet(), cardsShown())

        // This month, in Paris: the meeting of the 14th and the march of the 21st.
        onNodeWithTag(CallTags.FILTER_DUE + "this_month").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf(Examples.MEETING, Examples.MARCH), cardsShown())
        onNodeWithTag(CallTags.FILTER_DUE + "none").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf(Examples.INFORMATION), cardsShown())
        onNodeWithTag(CallTags.FILTER_DUE + "any").performScrollTo().performClick()

        // Closed calls stay readable.
        onNodeWithTag(CallTags.FILTER_STATUS + "closed").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf(Examples.CLOSED), cardsShown())
        onNodeWithTag(CallTags.FILTER_STATUS + "all").performScrollTo().performClick()
        waitForIdle()
        assertEquals(8, cardsShown().size)

        // A territory and words.
        onNodeWithTag(CallTags.FILTER_STATUS + "open").performScrollTo().performClick()
        onNodeWithTag(CallTags.FILTER_TERRITORY + "nord").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf(Examples.MARCH), cardsShown())
        onNodeWithTag(CallTags.CLEAR_ALL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.FILTER_WORDS).performScrollTo().performTextInput("bureau poste")
        waitForIdle()
        assertEquals(setOf(Examples.PETITION), cardsShown())
        onNodeWithTag(CallTags.FILTER_WORDS).performTextInput(" introuvable")
        waitForIdle()
        onNodeWithTag(CallTags.EMPTY_CLEAR).performScrollTo().performClick()
        waitForIdle()
        assertEquals(Examples.OPEN.toSet(), cardsShown())
        assertEquals(0, harness.calls.query.constraintCount)
    }

    @Test
    fun theSortByDeadlineIsOfferedAndPopularityIsExplainedAsAbsent() = runComposeUiTest {
        val harness = showCalls()
        onNodeWithTag(CallTags.SORT).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.SORT_NOTE).assertTextContains("Pas de tri par popularité", substring = true)
        onNodeWithTag(CallTags.SORT_OPTION + "deadline").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag(CallTags.SORT).assertTextContains("Tri : Échéance la plus proche", substring = true)
        // The cards in the order of the wall: the closest date first, the calls without a date, then the facts already past.
        assertEquals(
            listOf(Examples.MEETING, Examples.MARCH, Examples.INTERPELLATION, Examples.PETITION, Examples.VOLUNTEERS, Examples.INFORMATION, Examples.WITNESSES),
            cardOrder(),
        )
        assertEquals(org.politisationpopulairefrancaise.core.agir.calls.CallSort.DEADLINE, harness.calls.query.sort)
        onNodeWithTag(CallTags.SORT).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.SORT_OPTION + "newest").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(
            listOf(Examples.INTERPELLATION, Examples.INFORMATION, Examples.MEETING, Examples.MARCH, Examples.WITNESSES, Examples.PETITION, Examples.VOLUNTEERS),
            cardOrder(),
        )
    }

    @Test
    fun anAnswerStaysOnTheDeviceAndCanBeWithdrawnOrErased() = runComposeUiTest {
        val server = CallServer()
        val harness = showCalls(remote = server)
        onNodeWithTag(CallTags.CARD + Examples.PETITION).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER).performScrollTo().assertTextContains("J’ai signé", substring = true).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER_DONE).assertExists()
        onNodeWithTag(CallTags.ANSWER_NOTE).assertTextContains("reste sur cet appareil", substring = true)
        val address = harness.call(Examples.PETITION).address.toString()
        waitUntil(timeoutMillis = 5_000) { harness.responses.all() == setOf(address) }
        // Nothing left the device.
        assertEquals(emptyList(), server.received)

        onNodeWithTag(CallTags.TAB + "answered").performClick()
        waitForIdle()
        assertEquals(setOf(Examples.PETITION), cardsShown())
        onNodeWithTag(CallTags.ANSWERED_BADGE, useUnmergedTree = true).assertExists()

        // Erasing asks first, the safe choice first.
        onNodeWithTag(CallTags.CLEAR_ANSWERS).performScrollTo().performClick()
        waitForIdle()
        assertTrue(onNodeWithTag(CallTags.KEEP).getBoundsInRoot().top < onNodeWithTag(CallTags.CONFIRM).getBoundsInRoot().top)
        onNodeWithTag(CallTags.KEEP).performClick()
        waitForIdle()
        assertEquals(setOf(address), harness.responses.all())
        onNodeWithTag(CallTags.CLEAR_ANSWERS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.responses.count() == 0L }
        onNodeWithTag(CallTags.EMPTY).assertExists()
    }

    @Test
    fun anAnswerAlreadyGivenIsShownAgainAfterARestart() = runComposeUiTest {
        val database = org.politisationpopulairefrancaise.core.local.JvmDatabase.inMemory()
        val first = CallsHarness(database)
        first.responses.respond("30023:23484985405d16929a93f9aea29135678ad0ced909bf5a264f7b923d619b012f:${Examples.MEETING}")
        showCalls(database = database)
        onNodeWithTag(CallTags.CARD + Examples.MEETING).performScrollTo()
        onAllNodesWithTag(CallTags.ANSWERED_BADGE, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        onNodeWithTag(CallTags.CARD + Examples.MEETING).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER_DONE).performScrollTo().assertExists()
        onNodeWithTag(CallTags.ANSWER_WITHDRAW).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER).assertExists()
    }

    @Test
    fun witnessesWaitForTheSecureChannelAndClosedCallsShowTheirReport() = runComposeUiTest {
        val harness = showCalls()
        onNodeWithTag(CallTags.CARD + Examples.WITNESSES).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER).performScrollTo().assertIsNotEnabled().assertTextContains("Je témoigne", substring = true)
        onNodeWithTag(CallTags.ANSWER_NOTE).assertTextContains("canal sécurisé", substring = true)
        onNodeWithText("Du 31 octobre 2026 à 17\u00A0h au 31 octobre 2026 à 19\u00A0h").performScrollTo().assertExists()

        val closed = harness.call(Examples.CLOSED)
        assertEquals(CallStatus.CLOSED, closed.status)
        harness.calls.open(closed)
        waitForIdle()
        onNodeWithTag(CallTags.REPORT).performScrollTo().assertExists()
        onNodeWithText("320 personnes ont écrit", substring = true).assertExists()
        onNodeWithTag(CallTags.ANSWER).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallTags.ANSWER_NOTE).assertTextContains("clos", substring = true)
        onNodeWithTag(CallTags.COUNTDOWN).assertDoesNotExist()
    }

    @Test
    fun everyLinkGoesThroughTheWarningWithTheSafeChoiceFirst() = runComposeUiTest {
        val harness = showCalls()
        onNodeWithTag(CallTags.CARD + Examples.PETITION).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.LINK + 0).performScrollTo().assertTextContains("Ouvrir la pétition", substring = true).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.LEAVE_DIALOG).assertExists()
        onNode(hasText("example.org", substring = true) and hasAnyAncestor(hasTestTag(CallTags.LEAVE_DIALOG))).assertExists()
        assertTrue(onNodeWithTag(CallTags.LEAVE_STAY).getBoundsInRoot().top < onNodeWithTag(CallTags.LEAVE_OPEN).getBoundsInRoot().top)
        onNodeWithTag(CallTags.LEAVE_STAY).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.LEAVE_DIALOG).assertDoesNotExist()
        assertEquals(emptyList(), harness.opened)

        onNodeWithTag(CallTags.LINK + 0).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.LEAVE_OPEN).performClick()
        waitForIdle()
        assertEquals(listOf("https://example.org/petition-bureau-de-poste"), harness.opened)

        // A source that is a link goes through the same warning.
        harness.calls.open(harness.call(Examples.INFORMATION))
        waitForIdle()
        onNodeWithTag(CallTags.SOURCE_LINK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.LEAVE_DIALOG).assertExists()
        onNodeWithTag(CallTags.LEAVE_STAY).performClick()
        waitForIdle()
        assertEquals(1, harness.opened.size)
    }

    @Test
    fun theGoalAndTheCountdownShowWithoutAProgressBar() = runComposeUiTest {
        showCalls()
        onNodeWithTag(CallTags.CARD + Examples.PETITION).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.GOAL).assertTextContains("Objectif : 1 000")
        onNodeWithTag(CallTags.COUNTDOWN).assertTextContains("Dans 39 jours")
        onNodeWithText("Pas de barre de progression", substring = true).assertExists()
    }

    @Test
    fun theShareKitIsReadyAndTheHooksAppearOnlyWhenGiven() = runComposeUiTest {
        var event: String? = null
        var demand: String? = null
        showCalls(onCreateEvent = { event = it.slug }, onCreateDemand = { demand = it.slug })
        onNodeWithTag(CallTags.CARD + Examples.MARCH).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.SHARE_TEXT).performScrollTo()
        onNodeWithText("Retrouvez cet appel dans l’application", substring = true).assertExists()
        onNodeWithTag(CallTags.DISCUSS).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallTags.MAKE_EVENT).performScrollTo().performClick()
        onNodeWithTag(CallTags.MAKE_DEMAND).performScrollTo().performClick()
        assertEquals(Examples.MARCH, event)
        assertEquals(Examples.MARCH, demand)
    }

    @Test
    fun withoutHooksTheGrowButtonsAreAbsent() = runComposeUiTest {
        showCalls()
        onNodeWithTag(CallTags.CARD + Examples.MARCH).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.SHARE_COPY).performScrollTo().assertExists()
        onNodeWithTag(CallTags.MAKE_EVENT).assertDoesNotExist()
        onNodeWithTag(CallTags.MAKE_DEMAND).assertDoesNotExist()
    }

    @Test
    fun phonesAndTabletsShowOnePaneWithABackButton() = runComposeUiTest {
        var back = 0
        showCalls(width = 360, height = 700, onBack = { back++ })
        onNodeWithTag(CallTags.BACK).performClick()
        assertEquals(1, back)
        onNodeWithTag(CallTags.CARD + Examples.VOLUNTEERS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.DETAIL).assertExists()
        onNodeWithTag(CallTags.WALL).assertDoesNotExist()
        onNodeWithTag(CallTags.DETAIL_BACK).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.WALL).assertExists()
        onNodeWithTag(CallTags.DETAIL).assertDoesNotExist()
    }

    @Test
    fun aTabletInLandscapeKeepsOnePane() = runComposeUiTest {
        showCalls(width = 760, height = 600)
        onNodeWithTag(CallTags.CARD + Examples.MEETING).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.DETAIL).assertExists()
        onNodeWithTag(CallTags.WALL).assertDoesNotExist()
        onNodeWithTag(CallTags.DETAIL_BACK).assertExists()
    }
}
