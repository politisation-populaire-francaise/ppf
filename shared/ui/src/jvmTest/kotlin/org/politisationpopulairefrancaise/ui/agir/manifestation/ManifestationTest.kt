package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.manifestation.InMemoryRallyPointStorage
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPoint
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A server that records everything it is asked, to prove that the help functions send nothing about the user. */
private class WatchingServer : RemoteEvents {
    val published = mutableListOf<SignedEvent>()
    val requests = mutableListOf<String>()

    override suspend fun publish(event: SignedEvent): PublicationResult {
        published += event
        return PublicationResult.Accepted(event.id)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult {
        requests += filter.toString()
        return FetchResult.Events(emptyList())
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
        requests += "$tags $filter"
        return FetchResult.Events(emptyList())
    }
}

/** "Agir" with the AIDE button and its sheet, as the shell shows them, in a window of the given size. */
private class HelpHarness(
    val dependencies: AppDependencies = fileDependencies(),
    val storage: InMemoryRallyPointStorage = InMemoryRallyPointStorage(),
) {
    val uris = RecordingUriHandler()
    lateinit var help: HelpState

    @Composable
    fun Content(width: Int, height: Int = 800, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUriHandler provides uris, LocalRallyPointStorage provides storage) {
                    val library = rememberContentLibrary(dependencies)
                    val organisations = rememberOrganisationsLibrary(dependencies)
                    help = rememberHelpState(dependencies, organisations, library)
                    val session = rememberAccountSession(dependencies.identityStore)
                    ProvideHelp(help) {
                        Box(Modifier.fillMaxSize()) {
                            AgirScreen(
                                dependencies = dependencies,
                                library = library,
                                session = session,
                                windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                                modifier = Modifier.fillMaxSize(),
                                organisations = organisations,
                            )
                            HelpButton(Modifier.align(Alignment.BottomEnd))
                        }
                        HelpSheetHost(help)
                    }
                }
            }
        }
    }
}

/** The dependencies of the tests of "Agir" (a database file is locked by the concurrent imports of the libraries). */
private fun fileDependencies(remote: RemoteEvents? = null): AppDependencies = AgirFixtures.dependencies(remote)

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openSheet() {
    onNodeWithTag(ManifestationTags.HELP_BUTTON).performClick()
    waitForIdle()
    awaitTag(ManifestationTags.SHEET)
}

/** The guides are imported in the background: their text arrives a moment after the page. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.awaitText(text: String) =
    waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.top(tag: String): Float = onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

@OptIn(ExperimentalTestApi::class)
class ManifestationTest {

    @Test
    fun theAideButtonIsOnPhonesTabletsAndComputersAboveTheBottomBar() {
        for (width in listOf(360, 700, 1000)) {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalRallyPointStorage provides InMemoryRallyPointStorage()) {
                        Box(Modifier.requiredSize(width.dp, 760.dp)) { PpfApp(AgirFixtures.dependencies()) }
                    }
                }
                awaitTag(ManifestationTags.HELP_BUTTON)
                onNodeWithTag(ManifestationTags.HELP_BUTTON).assert(hasClickAction())
                onNodeWithText("AIDE", useUnmergedTree = true).assertExists()
                if (width == 360) {
                    // It floats above the bottom bar, never over it.
                    val button = onNodeWithTag(ManifestationTags.HELP_BUTTON).fetchSemanticsNode().boundsInRoot
                    assertTrue(button.bottom <= top(ShellTags.BOTTOM_BAR), "above the bottom bar at $width dp")
                }
                // One gesture opens the emergency sheet, from any space.
                openSheet()
                onNodeWithTag(ManifestationTags.NUMBER + "samu").assertExists()
                onNodeWithTag(ManifestationTags.SHEET_CLOSE).performClick()
                waitForIdle()
                assertEquals(0, count(ManifestationTags.SHEET))
            }
        }
    }

    @Test
    fun theSheetOffersTheFifteenAndTheOneOneTwoFirstThenSeventeenEighteenAndTheTextToOneOneFour() = runComposeUiTest {
        val harness = HelpHarness()
        setContent { harness.Content(360) }
        awaitTag(AgirTags.HOME)
        openSheet()
        val order = listOf("samu", "europe", "police", "pompiers", "sourds").map { top(ManifestationTags.NUMBER + it) }
        assertEquals(order.sorted(), order, "15, 112, 17, 18, 114 in this order")
        awaitTag(ManifestationTags.LEGAL + "legal-team-nord")
        val lawyers = top(ManifestationTags.LEGAL + "legal-team-nord")
        assertTrue(order.last() < lawyers, "the emergency numbers before the lawyers: $order, $lawyers")
        onNodeWithText("Appeler le 15", substring = true).assertExists()
        onNodeWithText("Appeler le 112", substring = true).assertExists()

        onNodeWithTag(ManifestationTags.NUMBER + "samu").performClick()
        onNodeWithTag(ManifestationTags.NUMBER + "europe").performClick()
        onNodeWithTag(ManifestationTags.NUMBER + "sourds").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("tel:15", "tel:112", "sms:114"), harness.uris.opened)

        // What is not ready says so, and cannot be pressed.
        listOf(HelpSoon.MEDICS, HelpSoon.EXIT, HelpSoon.LAWYER_ALERT).forEach { id ->
            onNodeWithTag(ManifestationTags.SOON + id).performScrollTo().assertIsNotEnabled().assert(hasClickAction().not())
        }
        onNodeWithText("secouristes certifiés", substring = true).assertExists()
    }

    @Test
    fun theLawyersOfTheDirectoryAreCalledInOneGestureAndTheCustodyGuideIsReadInTheSheet() = runComposeUiTest {
        val harness = HelpHarness()
        setContent { harness.Content(360) }
        awaitTag(AgirTags.HOME)
        openSheet()
        awaitTag(ManifestationTags.LEGAL + "legal-team-nord")
        onNodeWithTag(ManifestationTags.LEGAL + "reseau-avocats-benevoles").assertExists()
        // Only legal teams and lawyers: no street medics, no union.
        assertEquals(0, count(ManifestationTags.LEGAL + "street-medics-marseille"))
        assertEquals(0, count(ManifestationTags.LEGAL + "union-locale-lille"))
        onNodeWithTag(ManifestationTags.LEGAL + "legal-team-nord").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("tel:0639985210"), harness.uris.opened)

        onNodeWithTag(ManifestationTags.GUIDE_LINK + ManifestationGuides.CUSTODY).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.GUIDE)
        awaitText("Garde à vue : vos droits")
        onNodeWithTag(ManifestationTags.GUIDE_BACK).performClick()
        waitForIdle()
        awaitTag(ManifestationTags.NUMBER + "samu")
    }

    @Test
    fun theRallyPointIsKeptOnTheDeviceAndErasedInOneGesture() {
        val storage = InMemoryRallyPointStorage()
        val dependencies = fileDependencies()
        runComposeUiTest {
            val harness = HelpHarness(dependencies, storage)
            setContent { harness.Content(1000) }
            awaitTag(AgirTags.HOME)
            openSheet()
            onNodeWithTag(ManifestationTags.RALLY_ADD).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(ManifestationTags.RALLY_PLACE).performTextInput("[EXEMPLE] Devant la fontaine")
            onNodeWithTag(ManifestationTags.RALLY_DETAILS).performTextInput("À 18 h")
            onNodeWithTag(ManifestationTags.RALLY_SAVE).performScrollTo().performClick()
            waitForIdle()
            awaitTag(ManifestationTags.RALLY_VALUE)
            assertEquals(RallyPoint("[EXEMPLE] Devant la fontaine", "À 18 h"), RallyPoint.decode(storage.load()))
        }
        // A new run of the application reads it back from the device.
        runComposeUiTest {
            val harness = HelpHarness(dependencies, storage)
            setContent { harness.Content(1000) }
            awaitTag(AgirTags.HOME)
            openSheet()
            onNodeWithTag(ManifestationTags.RALLY_VALUE).performScrollTo()
            onNodeWithText("[EXEMPLE] Devant la fontaine").assertExists()
            onNodeWithTag(ManifestationTags.RALLY_CLEAR).performScrollTo().performClick()
            waitForIdle()
            assertNull(storage.load())
            assertEquals(0, count(ManifestationTags.RALLY_VALUE))
        }
    }

    @Test
    fun theProtestModeReplacesTheCardInPreparationAndPreparesEverythingOffline() = runComposeUiTest {
        val harness = HelpHarness()
        setContent { harness.Content(700) }
        awaitTag(AgirTags.HOME)
        assertEquals(0, count(AgirTags.SOON + AgirRubrics.MANIFESTATION.id))
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.MANIFESTATION.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SCREEN)
        onNodeWithText("[EXEMPLE] Textes à relire par un avocat").assertExists()

        // The checklist is ticked in memory.
        onNodeWithTag(ManifestationTags.CHECK + 1).performScrollTo().assertIsOff().performClick()
        waitForIdle()
        onNodeWithTag(ManifestationTags.CHECK + 1).assertIsOn()

        // The legal teams of the typed territory first; the territory stays in memory.
        onNodeWithTag(ManifestationTags.TERRITORY).performScrollTo().performTextInput("59000")
        waitForIdle()
        awaitTag(ManifestationTags.LEGAL + "legal-team-nord")
        assertTrue(top(ManifestationTags.LEGAL + "legal-team-nord") < top(ManifestationTags.LEGAL + "reseau-avocats-benevoles"))
        assertEquals("59000", harness.help.territory)

        // The useful numbers, the 15 first, then the listening lines.
        assertTrue(onAllNodesWithText("Appeler le 15", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(ManifestationTags.NUMBER + "enfant-disparu").performScrollTo()

        // "Après une blessure" opens its guide here, and comes back.
        onNodeWithTag(ManifestationTags.GUIDE_LINK + ManifestationGuides.INJURY).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.GUIDE)
        awaitText("Après une blessure en manifestation")
        onNodeWithText("Défenseur des droits", substring = true).assertExists()
        onNodeWithTag(ManifestationTags.GUIDE_BACK).performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SCREEN)

        // The protest mode opens the AIDE sheet too, and leads back to the home of Agir.
        onNodeWithTag(ManifestationTags.OPEN_HELP).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SHEET)
        onNodeWithTag(ManifestationTags.SHEET_CLOSE).performClick()
        waitForIdle()
        onNodeWithTag(MANIFESTATION_BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(AgirTags.HOME)
    }

    @Test
    fun nothingAboutTheUserLeavesTheDevice() = runComposeUiTest {
        val server = WatchingServer()
        val harness = HelpHarness(fileDependencies(server))
        setContent { harness.Content(360) }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.MANIFESTATION.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SCREEN)
        onNodeWithTag(ManifestationTags.TERRITORY).performScrollTo().performTextInput("Roubaix")
        onNodeWithTag(ManifestationTags.RALLY_ADD).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManifestationTags.RALLY_PLACE).performTextInput("Kiosque secret")
        onNodeWithTag(ManifestationTags.RALLY_SAVE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManifestationTags.NUMBER + "samu").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ManifestationTags.OPEN_HELP).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SHEET)
        // "Roubaix" carries no department number: only the national network is near.
        awaitTag(ManifestationTags.LEGAL + "reseau-avocats-benevoles")
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertTrue(server.published.isEmpty(), "nothing is published")
        assertTrue(server.requests.none { it.contains("Roubaix") || it.contains("Kiosque") }, "neither the territory nor the rally point is sent: ${server.requests}")
        assertEquals(listOf("tel:15"), harness.uris.opened)
    }

    @Test
    fun theDarkThemeShowsTheSameSheet() = runComposeUiTest {
        val harness = HelpHarness()
        setContent { harness.Content(360, dark = true) }
        awaitTag(AgirTags.HOME)
        openSheet()
        onNodeWithTag(ManifestationTags.NUMBER + "samu").assertExists()
        onNodeWithTag(ManifestationTags.RALLY).performScrollTo()
    }

    @Test
    fun aComputerKeepsTheRallyPointInAPrivateFileReplacedInOneMove() {
        val directory = Files.createTempDirectory("ppf-rally")
        val file = directory.resolve(FileRallyPointStorage.FILE_NAME)
        val storage = FileRallyPointStorage(file)
        assertNull(storage.load())
        storage.save(RallyPoint("Place du marché").encode())
        assertEquals(RallyPoint("Place du marché"), RallyPoint.decode(FileRallyPointStorage(file).load()))
        assertTrue(Files.list(directory).use { files -> files.toList().size == 1 }, "no temporary file left")
        storage.clear()
        assertNull(storage.load())
    }
}
