package org.politisationpopulairefrancaise.ui.endirect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirDestination
import org.politisationpopulairefrancaise.ui.voir.VoirState
import org.politisationpopulairefrancaise.ui.voir.VoirTags
import org.politisationpopulairefrancaise.ui.voir.rememberVoirState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.politisationpopulairefrancaise.core.endirect.LiveBlock
import org.politisationpopulairefrancaise.core.endirect.LiveBoard
import org.politisationpopulairefrancaise.core.endirect.LivePreset
import kotlin.test.assertNotNull

/** "En direct", from the contents bundled with the application, on a fixed day (7 November 2026). */
@OptIn(ExperimentalTestApi::class)
class EnDirectScreenTest {

    private val noon7November: Long = CivilDate(2026, 11, 7).toEpochDays() * 86_400 + 43_200

    private fun dependencies() = AppDependencies(
        identityStore = InMemoryIdentityStore(),
        contentStore = LocalEventStore(JvmDatabase.inMemory()),
        editorialTrust = EditorialTrust.bundledExamples(),
    )

    private class Seen {
        var library: ContentLibrary? = null
        var voir: VoirState? = null
        val navigations = mutableListOf<Space>()
    }

    private fun ComposeUiTest.show(width: Int, upcoming: Boolean = false, dark: Boolean = false, store: LiveBoardStore? = null): Seen {
        val seen = Seen()
        val dependencies = dependencies()
        setContent {
            PpfTheme(darkTheme = dark) {
                Box(Modifier.requiredSize(width.dp, 740.dp)) {
                    val library = rememberContentLibrary(dependencies)
                    val voir = rememberVoirState(dependencies)
                    seen.library = library
                    seen.voir = voir
                    EnDirectScreen(
                        library,
                        voir,
                        onNavigate = { seen.navigations += it },
                        upcoming = if (upcoming) ({ modifier -> Text("[EXEMPLE] Manifestation à venir", modifier.testTag("agenda-slot")) }) else null,
                        now = { noon7November },
                        board = rememberLiveBoardState(store),
                    )
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
        return seen
    }

    @Test
    fun theComingVoteIsAnAlertThatOpensTheBillInVoir() = runComposeUiTest {
        val seen = show(400)
        // The motion of censure on the budget is voted on 12 November: five days later.
        val first = onNodeWithTag(EnDirectTags.alert(0))
        first.assert(hasText("Vote dans 5 jours"))
        first.assert(hasText("Projet de loi de finances pour 2027"))
        onNodeWithTag(EnDirectTags.alert(1)).assert(hasText("Projet de loi relatif à l'ordre public lors des manifestations"))
        first.performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(Space.VOIR), seen.navigations)
        val current = assertNotNull(seen.voir!!.current) as VoirDestination.Item
        assertEquals("plf-2027", current.address.identifier)
    }

    @Test
    fun keyFiguresOfVoirOpenTheirPage() = runComposeUiTest {
        val seen = show(400)
        onNodeWithTag(EnDirectTags.FIGURES).assert(hasAnyDescendant(hasText("Manifestants lors des journées nationales")))
        onNodeWithTag(VoirTags.card("manifestants-journees")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(Space.VOIR), seen.navigations)
        assertEquals("manifestants-journees", (seen.voir!!.current as VoirDestination.Item).address.identifier)
    }

    @Test
    fun theDemandOfTheMomentOpensInTheWall() = runComposeUiTest {
        val seen = show(400)
        onNodeWithTag(EnDirectTags.HEADLINE).performScrollTo()
        onNodeWithText("Lire la revendication").performClick()
        waitForIdle()
        assertEquals(listOf(Space.REVENDICATIONS), seen.navigations)
        assertNotNull(seen.library!!.selectedRevendication)
    }

    @Test
    fun liveNewsChronologyAndTheAgendaPlace() = runComposeUiTest {
        show(1000, upcoming = true)
        onNodeWithTag(EnDirectTags.LIVE).assertExists()
        onNodeWithTag(EnDirectTags.NEWS).performScrollTo().assertExists()
        onNodeWithTag(EnDirectTags.CHRONOLOGY).performScrollTo().assert(hasAnyDescendant(hasText("Quatrième journée nationale, la plus suivie depuis septembre")))
        onNodeWithTag("agenda-slot").performScrollTo().assertExists()
    }

    @Test
    fun theViewsAndTheBlocksAreChosenAndKeptOnTheDevice() = runComposeUiTest {
        val store = InMemoryLiveBoardStore()
        show(400, store = store)
        // « Politique » opens on the alerts, without the key figures.
        onNodeWithTag(LiveBoardTags.view(LivePreset.POLITIQUE.id)).performClick()
        waitForIdle()
        onNodeWithTag(EnDirectTags.FIGURES).assertDoesNotExist()
        onNodeWithTag(LiveBoardTags.CUSTOMIZE).performClick()
        onNodeWithTag(LiveBoardTags.remove(LiveBlock.ALERTS)).performScrollTo().performClick()
        onNodeWithTag(LiveBoardTags.add(LiveBlock.FIGURES)).performScrollTo().performClick()
        onNodeWithTag(LiveBoardTags.DONE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(LiveBoardTags.EDITOR).assertDoesNotExist()
        onNodeWithTag(EnDirectTags.ALERTS).assertDoesNotExist()
        onNodeWithTag(EnDirectTags.FIGURES).performScrollTo().assertExists()
        // Kept on the device, to be found again at the next opening.
        val kept = LiveBoard.decode(store.load())
        assertEquals(LivePreset.POLITIQUE.id, kept.selected.id)
        assertTrue(LiveBlock.FIGURES in kept.selected.blocks && LiveBlock.ALERTS !in kept.selected.blocks)
    }

    @Test
    fun withoutTheAgendaALineSaysItIsComing() = runComposeUiTest {
        show(400)
        onNodeWithText("Les prochaines mobilisations s’afficheront ici", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun theShellOpensEnDirectFromItsEntry() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies()) } }
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        waitForIdle()
        onNodeWithTag(EnDirectTags.SCREEN).assertExists()
    }

    /** Not a check: writes captures when PPF_SCREENSHOTS_DIR is set (see ScreenshotTest). */
    @Test
    fun captures() {
        val directory = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        listOf(Triple(360, false, "telephone-clair"), Triple(360, true, "telephone-sombre"), Triple(1000, false, "ordinateur-clair")).forEach { (width, dark, prefix) ->
            runComposeUiTest {
                show(width, dark = dark)
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "$prefix-en-direct.png"))
                if (width < 600) {
                    onNodeWithTag(EnDirectTags.UPCOMING).performScrollTo()
                    waitForIdle()
                    ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "$prefix-en-direct-suite.png"))
                }
            }
        }
    }
}
