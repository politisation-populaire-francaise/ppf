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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationPageTags
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsTags
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for "Agir", like ScreenshotTest. With PPF_SCREENSHOTS_DIR set, renders the
 * home of "Agir", the directory, a filtered directory, the page of an organisation and the warning
 * before a web site, on a phone (light and dark) and a desktop. Without the variable it does nothing.
 */
@OptIn(ExperimentalTestApi::class)
class AgirScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    /** Writes the topmost root: the screen itself, or the dialog layer when one is open. */
    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val dependencies = AgirFixtures.dependencies()
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) } } }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            settle()
            onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
            mainClock.advanceTimeBy(2_000)
            settle()
        } else {
            onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
            settle()
        }
        awaitTag(AgirTags.HOME)
        save("$prefix-agir")
        onNodeWithTag(AgirTags.SOON + AgirRubrics.inPreparation.last().id).performScrollTo()
        settle()
        save("$prefix-agir-en-preparation")

        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.ORGANISATIONS.id).performScrollTo().performClick()
        settle()
        awaitCard(AgirFixtures.EXAMPLES.first())
        scrollListTo(OrganisationsTags.SEARCH)
        settle()
        save("$prefix-annuaire")

        scrollListTo(OrganisationsTags.NEAR)
        onNodeWithTag(OrganisationsTags.NEAR).performTextInput("59000")
        settle()
        scrollListTo(OrganisationsTags.COUNT)
        settle()
        save("$prefix-annuaire-pres-de-chez-moi")
        harnessClear()

        awaitCard(AgirFixtures.LANTERNE)
        onNodeWithTag(OrganisationsTags.CARD + AgirFixtures.LANTERNE).performClick()
        settle()
        awaitTag(OrganisationPageTags.PAGE)
        save("$prefix-organisation")
        onNodeWithTag(OrganisationPageTags.WEBSITE + 0).performScrollTo()
        settle()
        save("$prefix-organisation-contacts")
        onNodeWithTag(OrganisationPageTags.WEBSITE + 0).performClick()
        settle()
        awaitTag(OrganisationPageTags.LEAVE_DIALOG)
        save("$prefix-organisation-quitter")
    }

    /** Empties the territory typed above, so that every example is listed again. */
    private fun ComposeUiTest.harnessClear() {
        scrollListTo(OrganisationsTags.NEAR)
        onNodeWithTag(OrganisationsTags.NEAR).performTextReplacement("")
        settle()
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "ordinateur-clair")
}
