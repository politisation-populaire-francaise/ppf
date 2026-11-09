package org.politisationpopulairefrancaise.ui.agir.strike

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.strike.StrikeObligation
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSource
import org.politisationpopulairefrancaise.core.agir.strike.StrikeStatus
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.DirectoryServer
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityExamples
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityLibrary
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityPageTags
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityTags
import org.politisationpopulairefrancaise.ui.agir.solidarity.showInList
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * "Agir" alone in a window of the given size, with a solidarity rubric that reads nothing bundled
 * and a strike simulator the tests can look at. [visible] false takes "Agir" out of the screen, as
 * when the person leaves the space: everything typed in the simulator goes with it.
 */
class StrikeHarness(val dependencies: AppDependencies = AgirFixtures.dependencies()) {
    val uris = RecordingUriHandler()
    lateinit var solidarity: SolidarityLibrary

    /** The simulator of the "Agir" on screen; a new one each time "Agir" comes back. */
    var strike: StrikeSimulatorState? = null
    var visible: Boolean by mutableStateOf(true)

    @Composable
    fun Content(width: Int, height: Int, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUriHandler provides uris) {
                    if (visible) {
                        val library = rememberContentLibrary(dependencies)
                        val organisations = rememberOrganisationsLibrary(dependencies)
                        val scope = rememberCoroutineScope()
                        solidarity = remember(dependencies) {
                            SolidarityLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope, bundled = { null })
                        }
                        val simulator = rememberStrikeSimulatorState(dependencies)
                        strike = simulator
                        val session = rememberAccountSession(dependencies.identityStore)
                        AgirScreen(
                            dependencies = dependencies,
                            library = library,
                            session = session,
                            windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                            modifier = Modifier.fillMaxSize(),
                            organisations = organisations,
                            solidarity = solidarity,
                            strikeSimulator = simulator,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The strike simulator of "Agir" (AG-60), written without compiling (docs/integration/agir-simulateur-greve.md):
 * opened from the funds of the solidarity rubric and back; the estimate that changes with the status
 * and the days; the notice and the declaration of the sector; the pay that stays in memory and is
 * never stored nor sent; the strike funds near the person; the sources opened only after "Vous
 * quittez PPF"; on a phone (360 dp), a tablet (700 dp) and a desktop (1000 dp, side by side), light and dark.
 */
@OptIn(ExperimentalTestApi::class)
class StrikeSimulatorScreenTest {

    private fun ComposeUiTest.openSimulator(
        harness: StrikeHarness,
        width: Int = 360,
        height: Int = 740,
        dark: Boolean = false,
        storeExamples: Boolean = true,
    ) {
        if (storeExamples) runBlocking { SolidarityExamples.storeAll(harness.dependencies.contentStore!!) }
        setContent { harness.Content(width, height, dark) }
        enterSimulator()
    }

    /** From the home of "Agir": the solidarity rubric, then the entry at the end of its funds. */
    private fun ComposeUiTest.enterSimulator() {
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.SOLIDARITY.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
        showInList(SolidarityTags.OPEN_STRIKE_SIMULATOR)
        onNodeWithTag(SolidarityTags.OPEN_STRIKE_SIMULATOR).performScrollTo().performClick()
        waitForIdle()
        awaitTag(StrikeTags.SCREEN)
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.type(tag: String, text: String) {
        onNodeWithTag(tag).performScrollTo().performTextInput(text)
        waitForIdle()
    }

    private fun ComposeUiTest.amountIs(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithTag(StrikeTags.AMOUNT).performScrollTo().assertTextContains(text, substring = true) }.isSuccess
        }
    }

    @Test
    fun theSimulatorOpensFromTheFundsAndLeadsBack() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        onNodeWithTag(StrikeTags.DISCLAIMER).performScrollTo()
        onNodeWithText("Estimation, pas un conseil juridique ni un bulletin de paie", substring = true).assertExists()
        onNodeWithTag(StrikeTags.INCOMPLETE).performScrollTo()
        assertEquals(0, count(StrikeTags.AMOUNT), "no amount before the pay is typed")
        onNodeWithTag(StrikeTags.AI_LINE).performScrollTo().assertTextContains("Rédigé par l’IA, règles vérifiées le 9 novembre 2026", substring = true)

        onNodeWithTag(StrikeTags.BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(SolidarityTags.LIST)
        assertEquals(0, count(StrikeTags.SCREEN))
    }

    @Test
    fun theEstimateChangesWithTheStatusAndTheDays() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        type(StrikeTags.PAY, "2400")
        // Private sector, one day of 7 hours out of 151,67: 2 400 × 420 × 12 ÷ 109 200 = 110,77 €.
        amountIs("110,77")
        onNodeWithTag(StrikeTags.obligation(StrikeObligation.NO_NOTICE_PRIVATE)).performScrollTo()

        tap(StrikeTags.STATUS + StrikeStatus.STATE.id)
        amountIs("80,00")
        onNodeWithTag(StrikeTags.RULE).performScrollTo().assertTextContains("trentième indivisible", substring = true)
        onNodeWithTag(StrikeTags.obligation(StrikeObligation.NOTICE_PUBLIC_SERVICE)).performScrollTo()

        // Two hours: still a whole thirtieth in the State, proportional in the territorial service.
        tap(StrikeTags.kind(0, StrikeDayKind.HOURS))
        type(StrikeTags.HOURS + 0, "2")
        amountIs("80,00")
        tap(StrikeTags.STATUS + StrikeStatus.TERRITORIAL.id)
        amountIs("31,65")
        onNodeWithTag(StrikeTags.RULE).performScrollTo().assertTextContains("proportionnelle", substring = true)
        onNodeWithTag(StrikeTags.obligation(StrikeObligation.NOTICE_TERRITORIAL)).performScrollTo()
        tap(StrikeTags.STATUS + StrikeStatus.PRIVATE.id)
        amountIs("31,65")
        assertEquals(StrikeStatus.PRIVATE, harness.strike!!.status)
    }

    @Test
    fun daysWithoutServiceCountInTheStateAndTheSectorBringsItsDeclaration() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        tap(StrikeTags.STATUS + StrikeStatus.STATE.id)
        type(StrikeTags.PAY, "2400")
        assertEquals(0, count(StrikeTags.BETWEEN), "nothing lies between a single day")
        // Friday, one hour; Monday, the whole day; Saturday and Sunday in between: 4/30.
        tap(StrikeTags.kind(0, StrikeDayKind.HOURS))
        type(StrikeTags.HOURS + 0, "1")
        tap(StrikeTags.ADD_DAY)
        awaitTag(StrikeTags.DAY + 1)
        tap(StrikeTags.BETWEEN_MORE)
        tap(StrikeTags.BETWEEN_MORE)
        onNodeWithTag(StrikeTags.BETWEEN_VALUE).assertTextContains("2 jours sans service")
        amountIs("320,00")
        onNodeWithTag(StrikeTags.SHARE).performScrollTo().assertTextContains("13,33", substring = true)

        tap(StrikeTags.SECTOR + "ecole")
        onNodeWithTag(StrikeTags.obligation(StrikeObligation.NOTICE_PRIMARY_SCHOOL)).performScrollTo()
        onNodeWithTag(StrikeTags.obligationText(StrikeObligation.DECLARATION_PRIMARY_SCHOOL)).performScrollTo()
            .assertTextContains("au moins 48 heures avant", substring = true)

        // Removing a day leaves a single day: the days without service no longer count.
        tap(StrikeTags.REMOVE + 1)
        assertEquals(0, count(StrikeTags.BETWEEN))
        amountIs("80,00")
    }

    @Test
    fun aCompanyInChargeOfAPublicServiceGetsNoAmountButItsNotice() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        type(StrikeTags.PAY, "2000")
        amountIs("92,31")
        tap(StrikeTags.SECTOR + "service-public-entreprise")
        awaitTag(StrikeTags.NOT_SIMULATED)
        assertEquals(0, count(StrikeTags.AMOUNT))
        onNodeWithTag(StrikeTags.obligationText(StrikeObligation.NOTICE_PUBLIC_SERVICE)).performScrollTo()
            .assertTextContains("cinq jours francs", substring = true)
    }

    @Test
    fun thePayStaysInMemoryAndIsNeverSentNorStored() = runComposeUiTest {
        val server = DirectoryServer()
        runBlocking { server.toServe += SolidarityExamples.all() }
        val harness = StrikeHarness(AgirFixtures.dependencies(remote = server))
        openSimulator(harness, storeExamples = false)
        assertTrue(StrikeSimulatorState.ERASABLE_ID in harness.dependencies.erasables.ids, "handed to the quick erase")
        // A pay with a comma and a territory with letters that no key or timestamp of a request can contain by chance.
        type(StrikeTags.PAY, "2471,93")
        type(StrikeTags.NEAR, "Quimper")
        // 2 471,93 × 7 ÷ 151,67 = 114,09 €
        amountIs("114,09")
        val first = assertNotNull(harness.strike)
        assertEquals("2471,93", first.payText, "kept in memory, in the state of the screen")

        // Nothing typed reaches the server, in any request of any part of the application.
        val sent = server.tagRequests.flatMap { (tags, filter) -> tags.map { it.value } + filter.toString() } + server.fetches.map { it.toString() }
        assertTrue(sent.none { "2471,93" in it || "2471.93" in it || "Quimper" in it }, "nothing of the pay or the territory in any request")

        // Leaving "Agir" forgets everything: the simulator that comes back is a new one, empty.
        runOnIdle { harness.visible = false }
        waitForIdle()
        assertEquals(0, count(StrikeTags.SCREEN))
        runOnIdle { harness.visible = true }
        waitForIdle()
        enterSimulator()
        val second = assertNotNull(harness.strike)
        assertNotSame(first, second)
        assertEquals("", second.payText, "the pay was never written anywhere")
        assertEquals("", second.near)
        awaitTag(StrikeTags.INCOMPLETE)

        // The quick erase forgets what is typed, and the default answers come back.
        type(StrikeTags.PAY, "1800")
        runOnIdle { second.clear() }
        waitForIdle()
        assertEquals("", second.payText)
        assertEquals(1, second.days.size)
    }

    @Test
    fun theStrikeFundsBlockShowsTheOpenStrikeFundsNearThePerson() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        onNodeWithTag(StrikeTags.FUNDS).performScrollTo()
        waitUntil(timeoutMillis = 60_000) { count(StrikeTags.FUND + SolidarityExamples.LIVREURS) == 1 }
        // Strike funds only, open only: neither the legal fund nor the closed one.
        assertEquals(1, count(StrikeTags.FUND + SolidarityExamples.LILLE))
        assertEquals(0, count(StrikeTags.FUND + SolidarityExamples.LEGAL))
        assertEquals(0, count(StrikeTags.FUND + SolidarityExamples.CLOSED))

        // Near Rennes, the fund of Lille goes; the one that serves everywhere in France stays.
        type(StrikeTags.NEAR, "35000")
        waitUntil(timeoutMillis = 10_000) { count(StrikeTags.FUND + SolidarityExamples.LILLE) == 0 }
        assertEquals(1, count(StrikeTags.FUND + SolidarityExamples.LIVREURS))
        assertEquals("35000", harness.strike!!.near)
        assertEquals(null, harness.solidarity.fundQuery.near, "the rubric's own search is not touched")

        // A fund opens on its page in the solidarity rubric.
        tap(StrikeTags.FUND + SolidarityExamples.LIVREURS)
        awaitTag(SolidarityPageTags.FUND_PAGE)
        onNodeWithTag(SolidarityPageTags.FUND_NAME).assertTextContains("Caisse fictive des livreurs")
    }

    @Test
    fun aSourceOpensOnlyAfterVousQuittezPpf() = runComposeUiTest {
        val harness = StrikeHarness()
        openSimulator(harness)
        tap(StrikeTags.source(StrikeSource.SERVICE_PUBLIC_PRIVATE))
        awaitTag(StrikeTags.LEAVE_DIALOG)
        onNodeWithText("Ce lien ouvre www.service-public.gouv.fr", substring = true).assertExists()
        assertTrue(harness.uris.opened.isEmpty(), "nothing opens before the person chooses")
        onNodeWithTag(StrikeTags.LEAVE_STAY).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertTrue(harness.uris.opened.isEmpty())

        tap(StrikeTags.source(StrikeSource.SERVICE_PUBLIC_PRIVATE))
        awaitTag(StrikeTags.LEAVE_OPEN)
        onNodeWithTag(StrikeTags.LEAVE_OPEN).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(listOf(StrikeSource.SERVICE_PUBLIC_PRIVATE.url), harness.uris.opened)
    }

    @Test
    fun theSimulatorWorksOnAPhoneATabletAndADesktopInLightAndDark() {
        for ((width, dark) in listOf(360 to false, 360 to true, 700 to false, 700 to true, 1000 to false, 1000 to true)) {
            runComposeUiTest {
                val harness = StrikeHarness()
                openSimulator(harness, width = width, dark = dark)
                type(StrikeTags.PAY, "2400")
                amountIs("110,77")
                val inputs = onNodeWithTag(StrikeTags.INPUTS).fetchSemanticsNode().boundsInRoot
                val results = onNodeWithTag(StrikeTags.RESULTS).fetchSemanticsNode().boundsInRoot
                if (width == 1000) {
                    assertTrue(results.left >= inputs.right - 1f, "the estimate beside the questions at $width dp")
                } else {
                    assertTrue(results.top >= inputs.bottom - 1f, "the estimate under the questions at $width dp")
                }
            }
        }
    }
}
