package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.decider.DeciderTags
import org.politisationpopulairefrancaise.ui.endirect.EnDirectTags
import org.politisationpopulairefrancaise.ui.feed.FeedTags
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the safe area (D-180), like ScreenshotTest. With PPF_SCREENSHOTS_DIR
 * set, it renders the main screens in the windows of the usual Galaxy phones (360, 384 and 412 dp
 * wide, 20:9 screens), light and dark, with the system bars drawn over them, in two folders:
 * `avant`, where the application is not told about the bars and draws under them (what a phone
 * showed before D-180, and what a regression would show again), and `apres`, where it is told.
 * A phone on its side and the keyboard over a form complete the set. Without the variable it does
 * nothing. Run with:
 *   PPF_SCREENSHOTS_DIR=captures gradlew :shared:ui:jvmTest --tests "*SystemBarsScreenshotTest*"
 */
@OptIn(ExperimentalTestApi::class)
class SystemBarsScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(folder: String, name: String) {
        val dir = File(directory ?: return, folder)
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    /** Waits for a part of a screen; a camera goes on without it rather than fail. */
    private fun ComposeUiTest.await(tag: String, timeoutMillis: Long = 30_000) {
        runCatching { waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        waitForIdle()
    }

    private fun ComposeUiTest.show(device: FakeDevice, dark: Boolean, safeArea: Boolean) {
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        setContent {
            FakeDeviceWindow(device, safeArea = safeArea, drawBars = true, dark = dark) {
                PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) }
            }
        }
        openSpace(Space.FIL)
        await(FeedTags.LIVE)
    }

    /** A space: from the bars when it is there, otherwise from the menu (phones, phones on their side). */
    private fun ComposeUiTest.openPillar(@Suppress("UNUSED_PARAMETER") device: FakeDevice, space: Space) {
        openSpace(space)
        settle()
    }

    private fun mainScreens(device: FakeDevice, dark: Boolean, safeArea: Boolean) = runDesktopComposeUiTest(width = device.width, height = device.height) {
        if (directory == null) return@runDesktopComposeUiTest
        val folder = if (safeArea) "apres" else "avant"
        val prefix = "telephone-${device.name}-${if (dark) "sombre" else "clair"}"
        show(device, dark, safeArea)
        save(folder, "$prefix-fil")

        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        await(EnDirectTags.SCREEN)
        save(folder, "$prefix-en-direct")

        openSpace(Space.REVENDICATIONS)
        await(RevendicationTags.FILTERS)
        await(RevendicationTags.CARD + "lyon-transports-nuit", timeoutMillis = 10_000)
        save(folder, "$prefix-revendications")

        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        settle()
        save(folder, "$prefix-mon-espace")

        onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
        settle()
        save(folder, "$prefix-menu")
        // The menu closes on its scrim; Agir is in the bottom bar (D-215).
        onNodeWithTag(ShellTags.navItem(Space.DECIDER)).performClick()
        settle()
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
        settle()
        await(AgirTags.HOME)
        save(folder, "$prefix-agir")

        onNodeWithTag(ShellTags.navItem(Space.APPELS)).performClick()
        settle()
        save(folder, "$prefix-appels")

        openPillar(device, Space.DECIDER)
        await(DeciderTags.SCREEN)
        save(folder, "$prefix-decider")

        openPillar(device, Space.COMPRENDRE)
        await(ContentTags.GUIDE_CARD + "garde-a-vue", timeoutMillis = 10_000)
        save(folder, "$prefix-comprendre")
    }

    private fun beforeAndAfter(device: FakeDevice, dark: Boolean) {
        mainScreens(device, dark, safeArea = false)
        mainScreens(device, dark, safeArea = true)
    }

    @Test
    fun galaxy360Light() = beforeAndAfter(FakeDevice.galaxy360, dark = false)

    @Test
    fun galaxy360Dark() = beforeAndAfter(FakeDevice.galaxy360, dark = true)

    @Test
    fun galaxy384Light() = beforeAndAfter(FakeDevice.galaxy384, dark = false)

    @Test
    fun galaxy384Dark() = beforeAndAfter(FakeDevice.galaxy384, dark = true)

    @Test
    fun galaxy412Light() = beforeAndAfter(FakeDevice.galaxy412Gestures, dark = false)

    @Test
    fun galaxy412Dark() = beforeAndAfter(FakeDevice.galaxy412Gestures, dark = true)

    /**
     * A phone on its side, the camera hole on the left: the 360 dp one (800 dp wide: the rail) with its three
     * buttons on the right, the 412 dp one (915 dp wide: the side bar) with the gesture bar below.
     */
    @Test
    fun landscape() {
        if (directory == null) return
        for ((device, dark) in listOf(FakeDevice.landscapeButtons to false, FakeDevice.landscapeGestures to true)) {
            for (safeArea in listOf(false, true)) {
                runDesktopComposeUiTest(width = device.width, height = device.height) {
                    val folder = if (safeArea) "apres" else "avant"
                    val prefix = "${device.name}-${if (dark) "sombre" else "clair"}"
                    show(device, dark, safeArea)
                    save(folder, "$prefix-fil")
                    openPillar(device, Space.AGIR)
                    await(AgirTags.HOME)
                    save(folder, "$prefix-agir")
                }
            }
        }
    }

    /** The keyboard over the form of a demand: the field being typed and the end of the form. */
    @Test
    fun keyboard() {
        if (directory == null) return
        val device = FakeDevice.galaxy384.copy(keyboard = 330)
        for (safeArea in listOf(false, true)) {
            runDesktopComposeUiTest(width = device.width, height = device.height) {
                val folder = if (safeArea) "apres" else "avant"
                show(device, dark = false, safeArea = safeArea)
                openSpace(Space.REVENDICATIONS)
                await(RevendicationTags.PROPOSE)
                onAllNodesWithTag(RevendicationTags.PROPOSE).onFirst().performScrollTo().performClick()
                await(ProposalTags.TITLE)
                onNodeWithTag(ProposalTags.TITLE).performTextInput("Cantine à un euro pour les familles modestes")
                waitForIdle()
                save(folder, "telephone-384-clair-clavier-proposer")
                onNodeWithTag(ProposalTags.CHARTER).performScrollTo()
                waitForIdle()
                save(folder, "telephone-384-clair-clavier-proposer-fin")
            }
        }
    }
}
