package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.account.MailboxTags
import org.politisationpopulairefrancaise.ui.messages.MessagesTags
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the rooms, like ScreenshotTest. With PPF_SCREENSHOTS_DIR set, renders
 * the "Collectifs" view, a room, the creation form and the retention setting, on a phone (light and
 * dark) and a desktop. Without the variable it does nothing.
 */
@OptIn(ExperimentalTestApi::class)
class GroupsScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

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
        val server = RoomsFixtures.server()
        val dependencies = RoomsFixtures.withAccount(server)
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) } } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        settle()
        onNodeWithTag(MessagesTags.TAB + "collectifs").performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithTag(GroupsTags.LIST).performScrollToNode(hasTestTag(GroupsTags.JOIN + RoomsFixtures.LIVREURS)) }.isSuccess
        }
        onNodeWithTag(GroupsTags.JOIN + RoomsFixtures.LIVREURS, useUnmergedTree = true).performClick()
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        settle()
        save("$prefix-salons")

        onNodeWithTag(GroupsTags.LIST).performScrollToNode(hasTestTag(GroupsTags.ROOM + RoomsFixtures.SANTE))
        onNodeWithTag(GroupsTags.ROOM + RoomsFixtures.SANTE).performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(GroupsTags.JOIN_TO_WRITE).fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-salon-lecture")
        onNodeWithTag(GroupsTags.JOIN_TO_WRITE).performClick()
        settle()
        onNodeWithTag(GroupsTags.INPUT).performTextInput("[EXEMPLE] Je viendrai jeudi.")
        onNodeWithTag(GroupsTags.SEND).performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { server.published.isNotEmpty() }
        settle()
        save("$prefix-salon")

        if (onAllNodesWithTag(GroupsTags.BACK).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag(GroupsTags.BACK).performClick()
            settle()
        }
        onNodeWithTag(GroupsTags.LIST).performScrollToIndex(0)
        settle()
        onNodeWithTag(GroupsTags.CREATE).performClick()
        settle()
        onNodeWithTag(GroupsTags.FORM_NAME).performTextInput("[EXEMPLE] Collectif santé")
        onNodeWithTag(GroupsTags.FORM_TERRITORY).performTextInput("Lyon")
        settle()
        save("$prefix-salon-creation")
        onNodeWithTag(GroupsTags.FORM_CHARTER).performScrollTo().performClick()
        settle()
        onNodeWithTag(GroupsTags.FORM_SUBMIT).performScrollTo().performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(GroupsTags.FORM_EXISTS).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(GroupsTags.FORM_EXISTS).performScrollTo()
        settle()
        save("$prefix-salon-existe")
        onNodeWithTag(GroupsTags.FORM_CLOSE).performScrollTo().performClick()
        settle()

        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(MailboxTags.CARD).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(MailboxTags.CARD).performScrollTo()
        settle()
        save("$prefix-conservation")
        onNodeWithTag(MailboxTags.OPTION + 180).performScrollTo().performClick()
        settle()
        save("$prefix-conservation-avertissement")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "salons-telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "salons-telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "salons-ordinateur-clair")
}
