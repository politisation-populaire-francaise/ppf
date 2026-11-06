package org.politisationpopulairefrancaise.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.feed.FeedFilter
import org.politisationpopulairefrancaise.ui.feed.FeedTags
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera. When the environment variable PPF_SCREENSHOTS_DIR names a directory,
 * this test renders the main screens at phone and desktop sizes, in light and dark, and writes
 * one PNG per screen there, so that the rendering can be reviewed without a device. Without the
 * variable it does nothing. Run with:
 *   PPF_SCREENSHOTS_DIR=captures gradlew :shared:ui:jvmTest --tests "*ScreenshotTest*"
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.waitForContents(store: LocalEventStore) {
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(FeedTags.LIVE).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val store = LocalEventStore(JvmDatabase.inMemory())
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) {
                    org.politisationpopulairefrancaise.ui.shell.AdaptiveShell(AppDependencies(InMemoryIdentityStore(), contentStore = store))
                }
            }
        }
        waitForContents(store)
        save("$prefix-fil")

        onNodeWithTag(FeedTags.TAB + FeedFilter.REVENDICATIONS.id).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-fil-revendications")

        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-revendications")
        onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes()
        onNodeWithTag(RevendicationTags.CARD + "lyon-transports-nuit").performScrollTo().performClick()
        waitForIdle()
        save("$prefix-revendication-detail")

        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        save("$prefix-messages")

        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        save("$prefix-mon-espace")

        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
            save("$prefix-menu")
            onNodeWithTag(ShellTags.navItem(Space.COMPRENDRE)).performClick()
            waitForIdle()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        } else {
            onNodeWithTag(ShellTags.navItem(Space.COMPRENDRE)).performClick()
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-comprendre")
        onNodeWithTag(ContentTags.GUIDE_CARD + "garde-a-vue").performScrollTo().performClick()
        waitForIdle()
        save("$prefix-fiche")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "ordinateur-clair")
}
