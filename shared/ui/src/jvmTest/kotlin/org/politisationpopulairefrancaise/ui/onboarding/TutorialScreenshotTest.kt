package org.politisationpopulairefrancaise.ui.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.FakeDevice
import org.politisationpopulairefrancaise.ui.shell.FakeDeviceWindow
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the tutorial (D-186). With PPF_SCREENSHOTS_DIR set, it renders every screen
 * of the tutorial in the window of a 360 dp Galaxy with its system bars, at the font sizes 1.0 and 1.3,
 * light and dark, in `tutoriel/`. Without the variable it does nothing. Run with:
 *   PPF_SCREENSHOTS_DIR=captures gradlew :shared:ui:jvmTest --tests "*TutorialScreenshotTest*"
 */
@OptIn(ExperimentalTestApi::class)
class TutorialScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it, "tutoriel") }

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test
    fun everyScreen() {
        if (directory == null) return
        val device = FakeDevice.galaxy360
        for ((scale, dark) in listOf(1.0f to false, 1.3f to false, 1.0f to true)) runDesktopComposeUiTest(width = device.width, height = device.height) {
            val dependencies = AppDependencies(identityStore = InMemoryIdentityStore(), tutorial = InMemoryTutorialStore())
            setContent {
                FakeDeviceWindow(device, safeArea = true, drawBars = true, dark = dark) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                        PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) }
                    }
                }
            }
            waitForIdle()
            val prefix = "tutoriel-360-police-${"%.1f".format(scale).replace(',', '.')}-${if (dark) "sombre" else "clair"}"
            TutorialStep.entries.forEachIndexed { index, _ ->
                mainClock.advanceTimeBy(500)
                waitForIdle()
                save("$prefix-ecran-${(index + 1).toString().padStart(2, '0')}")
                if (index < TutorialStep.entries.lastIndex) {
                    onNodeWithTag(TutorialTags.NEXT).performClick()
                    waitForIdle()
                }
            }
        }
    }
}
