package org.politisationpopulairefrancaise.ui.agir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the UX review of « Agir » on a phone. With PPF_SCREENSHOTS_DIR set, walks through the
 * home and its main rubrics (agenda, calls, kits, Fédérer, S'engager), several screens high each. Without the
 * variable it does nothing. A step that cannot be reached is skipped, never failed.
 */
@OptIn(ExperimentalTestApi::class)
class AgirTourScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
        waitForIdle()
    }

    /** Saves [pages] screens of the current view, scrolling down between them. */
    private fun ComposeUiTest.pages(name: String, pages: Int = 3) {
        repeat(pages) { index ->
            save("$name-$index")
            runCatching { onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + bottom * 0.15f) } }
            settle()
        }
    }

    private fun ComposeUiTest.home() {
        runCatching { onNodeWithTag(AgirTags.BACK).performClick() }
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
        settle()
    }

    private fun ComposeUiTest.open(rubric: String, name: String, pages: Int = 3) {
        home()
        val found = runCatching { onNodeWithTag(AgirTags.RUBRIC + rubric).performScrollTo().performClick() }.isSuccess
        if (!found) return
        settle()
        pages(name, pages)
    }

    @Test
    fun phoneTour() = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val dependencies = AgirFixtures.dependencies()
        setContent { Box(Modifier.requiredSize(360.dp, 760.dp)) { PpfTheme(darkTheme = false) { AdaptiveShell(dependencies) } } }
        settle()
        home()
        pages("tour-accueil", 5)
        open(AgirRubrics.AGENDA.id, "tour-agenda")
        open(AgirRubrics.CALLS.id, "tour-appels")
        open(AgirRubrics.KITS.id, "tour-kits")
        open(AgirRubrics.FEDERER.id, "tour-federer")
        home()
        if (runCatching { onNodeWithTag(org.politisationpopulairefrancaise.ui.agir.engagement.EngagementFeaturedTags.OPEN).performScrollTo().performClick() }.isSuccess) {
            settle()
            pages("tour-engager")
        }
        open(AgirRubrics.GATHER.id, "tour-se-reunir", 2)
        open(AgirRubrics.SOLIDARITY.id, "tour-solidarite", 2)
    }
}
