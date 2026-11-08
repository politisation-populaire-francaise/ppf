package org.politisationpopulairefrancaise.ui.agir.solidarity

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirHarness
import org.politisationpopulairefrancaise.ui.agir.AgirRubric
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.DirectoryServer
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationPageTags
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The solidarity rubric of "Agir" (M35): opened from the home of "Agir" on the funds or on the
 * missions, the funds filtered on the device ("près de chez moi" first local, then national), the
 * donation page opened only after "Vous quittez PPF" (safe choice first), the manager said in words
 * and its page in the directory, the missions sorted by the user's skills kept on the device,
 * "Proposer mon aide" and the mutual aid announced, nothing typed or picked sent to the server; on
 * a phone (360 dp), a tablet (700 dp) and a desktop (1000 dp, two panes), light and dark.
 */
@OptIn(ExperimentalTestApi::class)
class SolidarityScreenTest {

    private fun ComposeUiTest.openRubric(
        harness: SolidarityHarness,
        rubric: AgirRubric = AgirRubrics.SOLIDARITY,
        width: Int = 360,
        height: Int = 740,
        dark: Boolean = false,
        storeExamples: Boolean = true,
    ) {
        if (storeExamples) runBlocking { SolidarityExamples.storeAll(harness.dependencies.contentStore!!) }
        setContent { harness.Content(width, height, dark) }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + rubric.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
    }

    /** A chip, a field or a card of the list: brought on screen first (the list is lazy). */
    private fun ComposeUiTest.tap(tag: String) {
        showInList(tag)
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.countIs(tag: String, text: String) {
        showInList(tag)
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(tag).assertTextContains(text) }.isSuccess }
    }

    private fun ComposeUiTest.openFund(slug: String) {
        tap(SolidarityTags.FUND_CARD + slug)
        awaitTag(SolidarityPageTags.FUND_PAGE)
    }

    @Test
    fun theHomeOpensTheFundsOrTheMissionsAndLeadsBack() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness)
        onNodeWithTag(SolidarityTags.TAB + SolidarityTab.FUNDS.id).assertIsSelected()
        showInList(SolidarityTags.FUND_SEARCH)
        onNodeWithText("chaque caisse renvoie vers sa propre page de dons", substring = true).assertExists()
        showInList(SolidarityTags.BACK)
        onNodeWithTag(SolidarityTags.BACK).performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)

        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.HELP.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
        onNodeWithTag(SolidarityTags.TAB + SolidarityTab.MISSIONS.id).assertIsSelected()
        showInList(SolidarityTags.MISSIONS_INTRO)

        // Mutual aid is announced honestly, nothing more.
        tap(SolidarityTags.TAB + SolidarityTab.MUTUAL_AID.id)
        showInList(SolidarityTags.MUTUAL_AID)
        onNodeWithText("Entraide : bientôt").assertExists()
        assertEquals(0, count(SolidarityTags.FUND_CARD + SolidarityExamples.LILLE))
    }

    @Test
    fun theFundsAreFilteredOnTheDeviceAndNearMeKeepsTheLocalOnesFirst() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness)
        countIs(SolidarityTags.FUND_COUNT, "3 caisses")
        assertEquals(0, count(SolidarityTags.FUND_CARD + SolidarityExamples.CLOSED), "a closed fund only on demand")

        tap(SolidarityTags.FUND_TYPE + SolidarityFormats.FUND_LEGAL)
        countIs(SolidarityTags.FUND_COUNT, "1 caisse")
        tap(SolidarityTags.FUND_TYPE + "all")
        countIs(SolidarityTags.FUND_COUNT, "3 caisses")

        tap(SolidarityTags.INCLUDE_CLOSED_FUNDS)
        countIs(SolidarityTags.FUND_COUNT, "4 caisses")
        showInList(SolidarityTags.FUND_CARD + SolidarityExamples.CLOSED)
        // The state is written in the badge, not shown by colour alone.
        onNodeWithTag(SolidarityTags.FUND_STATUS + SolidarityExamples.CLOSED, useUnmergedTree = true).assertExists()
        onNodeWithTag(SolidarityTags.FUND_CARD + SolidarityExamples.CLOSED).assertTextContains("Close")
        tap(SolidarityTags.INCLUDE_CLOSED_FUNDS)
        countIs(SolidarityTags.FUND_COUNT, "3 caisses")

        showInList(SolidarityTags.NEAR_PRIVATE)
        onNodeWithText("Ce territoire reste sur votre appareil", substring = true).assertExists()
        showInList(SolidarityTags.NEAR)
        onNodeWithTag(SolidarityTags.NEAR).performTextInput("59000")
        waitForIdle()
        countIs(SolidarityTags.FUND_COUNT, "3 caisses")
        showInList(SolidarityTags.NEAR_SECTION)
        showInList(SolidarityTags.NATIONAL_SECTION)
        assertEquals(
            listOf(SolidarityExamples.LILLE, SolidarityExamples.LEGAL, SolidarityExamples.LIVREURS),
            harness.solidarity.fundResults.map { it.slug },
            "Lille and the Nord by the postal code, then the fund that serves everywhere in France",
        )
        // The territory lives in memory, in the query of the device.
        assertEquals("59000", harness.solidarity.fundQuery.near)
    }

    @Test
    fun aDonationPageOpensOnlyAfterVousQuittezPpfWhoseSafeChoiceComesFirst() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness)
        openFund(SolidarityExamples.LILLE)
        onNodeWithTag(SolidarityPageTags.FUND_NAME).assertTextContains("Caisse de grève fictive de Lille")
        onNodeWithText("Caisse vérifiée par PPF le 5 novembre 2026").assertExists()
        onNodeWithTag(SolidarityPageTags.DONATE_WHERE).performScrollTo().assertTextContains("example.org", substring = true)

        onNodeWithTag(SolidarityPageTags.DONATE).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityPageTags.LEAVE_DIALOG)
        onNodeWithText("Vous quittez PPF").assertExists()
        onNodeWithText("Ce lien ouvre la page de dons example.org, hors de PPF", substring = true).assertExists()
        onNodeWithText("les fausses cagnottes existent", substring = true).assertExists()
        assertTrue(harness.uris.opened.isEmpty(), "nothing opens before the user chooses")
        val stay = onNodeWithTag(SolidarityPageTags.LEAVE_STAY).fetchSemanticsNode().boundsInRoot.top
        val open = onNodeWithTag(SolidarityPageTags.LEAVE_OPEN).fetchSemanticsNode().boundsInRoot.top
        assertTrue(stay < open, "the safe choice comes first")

        onNodeWithTag(SolidarityPageTags.LEAVE_STAY).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(0, count(SolidarityPageTags.LEAVE_DIALOG))
        assertTrue(harness.uris.opened.isEmpty())

        onNodeWithTag(SolidarityPageTags.DONATE).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityPageTags.LEAVE_OPEN)
        onNodeWithTag(SolidarityPageTags.LEAVE_OPEN).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(listOf(SolidarityExamples.LILLE_LINK), harness.uris.opened)
    }

    @Test
    fun aClosedFundOffersNoLinkAndSaysWhy() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness)
        tap(SolidarityTags.INCLUDE_CLOSED_FUNDS)
        openFund(SolidarityExamples.CLOSED)
        onNodeWithTag(SolidarityPageTags.NO_DONATION).performScrollTo().assertTextContains("Cette caisse est close", substring = true)
        assertEquals(0, count(SolidarityPageTags.DONATE))
        assertTrue(harness.uris.opened.isEmpty())
    }

    @Test
    fun onATabletTheManagerIsSaidInWordsAndItsPageOpensInTheDirectory() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness, width = 700)
        openFund(SolidarityExamples.LEGAL)
        // The directory loads on its own (the examples bundled with the application).
        waitUntil(timeoutMillis = 60_000) {
            runCatching { onNodeWithText("Gérée par Legal team de démonstration du Nord, organisation vérifiée par PPF").assertExists() }.isSuccess
        }
        onNodeWithTag(SolidarityPageTags.FUND_OPEN_ORGANISATION).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.PAGE)
        onNodeWithTag(OrganisationPageTags.NAME).assertTextContains("Legal team de démonstration du Nord")

        // Back from the page, then from the directory: the fund is still open.
        onNodeWithTag(OrganisationPageTags.BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationsTags.LIST)
        onNodeWithTag(OrganisationsTags.BACK).performClick()
        waitForIdle()
        awaitTag(SolidarityPageTags.FUND_PAGE)
        onNodeWithTag(SolidarityPageTags.FUND_NAME).assertTextContains("Caisse juridique fictive du Nord")

        onNodeWithTag(SolidarityPageTags.FUND_BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
        openFund(SolidarityExamples.LIVREURS)
        onNodeWithTag(SolidarityPageTags.FUND_MANAGER).performScrollTo().assertTextContains("Gérée par Intersyndicale fictive des livreurs, qui n", substring = true)
        assertEquals(0, count(SolidarityPageTags.FUND_OPEN_ORGANISATION), "no page to open for a manager outside the directory")
    }

    @Test
    fun theUsersSkillsSortTheMissionsAndOfferingHelpIsAnnounced() = runComposeUiTest {
        val harness = SolidarityHarness()
        openRubric(harness, rubric = AgirRubrics.HELP)
        countIs(SolidarityTags.MISSION_COUNT, "3 missions")
        showInList(SolidarityTags.SKILLS_PRIVATE)
        onNodeWithText("Vos compétences restent sur votre appareil", substring = true).assertExists()

        tap(SolidarityTags.SKILL + "traduction")
        countIs(SolidarityTags.MISSION_COUNT, "1 mission")
        showInList(SolidarityTags.MISSION_CARD + SolidarityExamples.TRANSLATION)
        assertEquals(setOf("traduction"), harness.solidarity.missionQuery.skills, "kept in memory, on the device")
        tap(SolidarityTags.SKILL + "juriste")
        countIs(SolidarityTags.MISSION_COUNT, "2 missions")
        tap(SolidarityTags.SKILL + "all")
        countIs(SolidarityTags.MISSION_COUNT, "3 missions")
        tap(SolidarityTags.REMOTE_ONLY)
        countIs(SolidarityTags.MISSION_COUNT, "2 missions")

        tap(SolidarityTags.MISSION_CARD + SolidarityExamples.TRANSLATION)
        awaitTag(SolidarityPageTags.MISSION_PAGE)
        onNodeWithTag(SolidarityPageTags.MISSION_TITLE).assertTextContains("Traduire une fiche")
        onNodeWithTag(SolidarityPageTags.MISSION_OFFER).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SolidarityPageTags.MISSION_OFFER_SOON).performScrollTo()
        onNodeWithText("Proposer votre aide depuis l", substring = true).assertExists()
        onNodeWithTag(SolidarityPageTags.MISSION_BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
    }

    @Test
    fun nothingTypedOrPickedIsSentToTheServer() = runComposeUiTest {
        val server = DirectoryServer()
        runBlocking { server.toServe += SolidarityExamples.all() }
        val harness = SolidarityHarness(AgirFixtures.dependencies(remote = server))
        openRubric(harness, storeExamples = false)
        waitUntil(timeoutMillis = 60_000) { harness.solidarity.status is SolidarityStatus.Synced }
        fun solidarityRequests() = server.tagRequests.map { (tags, _) -> tags.single { it.name == AgirFormats.TAG_TYPE }.value }
            .filter { it == SolidarityFormats.TYPE_FUND || it == SolidarityFormats.TYPE_MISSION }
        assertEquals(listOf(SolidarityFormats.TYPE_FUND, SolidarityFormats.TYPE_MISSION), solidarityRequests())
        countIs(SolidarityTags.FUND_COUNT, "3 caisses")

        showInList(SolidarityTags.NEAR)
        onNodeWithTag(SolidarityTags.NEAR).performTextInput("59000")
        showInList(SolidarityTags.FUND_SEARCH)
        onNodeWithTag(SolidarityTags.FUND_SEARCH).performTextInput("juridique")
        waitForIdle()
        countIs(SolidarityTags.FUND_COUNT, "1 caisse")
        openFund(SolidarityExamples.LEGAL)
        onNodeWithTag(SolidarityPageTags.FUND_BACK).performScrollTo().performClick()
        waitForIdle()
        tap(SolidarityTags.TAB + SolidarityTab.MISSIONS.id)
        tap(SolidarityTags.SKILL + "juriste")
        countIs(SolidarityTags.MISSION_COUNT, "1 mission")

        assertEquals(listOf(SolidarityFormats.TYPE_FUND, SolidarityFormats.TYPE_MISSION), solidarityRequests(), "no new request")
        val sent = server.tagRequests.flatMap { (tags, _) -> tags.map { it.value } }
        assertTrue(sent.none { "59000" in it || "juridique" in it || "juriste" in it }, "nothing of the user's choices in any request")
    }

    @Test
    fun theRubricWorksOnAPhoneATabletAndADesktopInLightAndDark() {
        for ((width, dark) in listOf(360 to false, 360 to true, 700 to false, 700 to true, 1000 to false, 1000 to true)) {
            runComposeUiTest {
                val harness = SolidarityHarness()
                openRubric(harness, width = width, dark = dark)
                if (width == 1000) awaitTag(SolidarityTags.SELECT_HINT)
                openFund(SolidarityExamples.LILLE)
                if (width == 1000) {
                    // Two panes: the list stays beside the page, which has no back button.
                    onNodeWithTag(SolidarityTags.LIST).assertExists()
                    assertEquals(0, count(SolidarityPageTags.FUND_BACK), "no back button beside the list at $width dp")
                    tap(SolidarityTags.FUND_CARD + SolidarityExamples.LIVREURS)
                    onNodeWithTag(SolidarityPageTags.FUND_NAME).assertTextContains("Caisse fictive des livreurs")
                } else {
                    assertEquals(0, count(SolidarityTags.LIST), "one pane at $width dp")
                    onNodeWithTag(SolidarityPageTags.FUND_BACK).performScrollTo().performClick()
                    waitForIdle()
                    awaitTag(SolidarityTags.LIST)
                }
            }
        }
    }

    @Test
    fun theUsersChoicesAreHandedToTheQuickErase() = runComposeUiTest {
        val harness = AgirHarness()
        setContent { harness.Content(360, 740) }
        awaitTag(AgirTags.HOME)
        assertTrue(SolidarityLibrary.ERASABLE_ID in harness.dependencies.erasables.ids)
    }
}
