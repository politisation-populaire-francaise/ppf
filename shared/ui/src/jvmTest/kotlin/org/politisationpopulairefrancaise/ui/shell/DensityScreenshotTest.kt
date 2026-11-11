package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.endirect.EnDirectTags
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a ruler for « l'application est trop grande » (11 November 2026). With
 * PPF_SCREENSHOTS_DIR set, it renders the screens most used on a phone in the windows of the
 * Galaxy phones (360, 384 and 411 dp wide: 411 is a Galaxy A34 at its usual display size), at the
 * font sizes 1.0, 1.15 and 1.3, and writes beside the captures `mesures.txt`: the share of the
 * height taken by the bars (the system's and the application's) and where the first content of
 * each screen starts. Without the variable it does nothing. Run with:
 *   PPF_SCREENSHOTS_DIR=captures gradlew :shared:ui:jvmTest --tests "*DensityScreenshotTest*"
 */
@OptIn(ExperimentalTestApi::class)
class DensityScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it, "densite") }

    private val phones = listOf(
        FakeDevice.galaxy360,
        FakeDevice.galaxy384,
        FakeDevice("411-a34", width = 411, height = 891, statusBar = 32, navigationBar = 48),
    )
    private val fontScales = listOf(1.0f, 1.15f, 1.3f)

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.await(tag: String, prefix: Boolean = false, timeoutMillis: Long = 20_000) {
        runCatching { waitUntil(timeoutMillis = timeoutMillis) { nodes(tag, prefix).isNotEmpty() } }
        waitForIdle()
    }

    private fun ComposeUiTest.nodes(tag: String, prefix: Boolean) =
        if (prefix) {
            onAllNodes(SemanticsMatcher("tag starts with $tag") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(tag) == true }).fetchSemanticsNodes()
        } else {
            onAllNodesWithTag(tag).fetchSemanticsNodes()
        }

    /** The top of the first node whose tag is [tag] (or starts with it), in dp from the top of the window. */
    private fun ComposeUiTest.topOf(tag: String, prefix: Boolean = false): String {
        val found = nodes(tag, prefix)
        if (found.isEmpty()) return "absent"
        return "%.0f dp".format(found.minOf { it.boundsInRoot.top } / density.density)
    }

    private fun ComposeUiTest.bottomOf(tag: String): Float = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom / density.density
    private fun ComposeUiTest.topEdgeOf(tag: String): Float = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top / density.density

    @Test
    fun measure() {
        val dir = directory ?: return
        dir.mkdirs()
        val report = StringBuilder("# Densité, mesurée par DensityScreenshotTest\n\n")
        report.append("| Téléphone | Police | Barres (système et application) | En direct : premier bloc | Revendications : première carte | Agir : première rubrique |\n|---|---|---|---|---|---|\n")
        for (device in phones) for (scale in fontScales) runDesktopComposeUiTest(width = device.width, height = device.height) {
            val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
            setContent {
                FakeDeviceWindow(device, safeArea = true, drawBars = true) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                        PpfTheme(darkTheme = false) { AdaptiveShell(dependencies) }
                    }
                }
            }
            val prefix = "telephone-${device.name}-police-${"%.2f".format(scale).replace(',', '.')}"
            await(EnDirectTags.SCREEN)
            // The bars: from the top of the window to the bottom of the top bar, and from the top of the bottom bar to the bottom.
            val top = topEdgeOf(ShellTags.CONTENT)
            val bottom = device.height - bottomOf(ShellTags.CONTENT)
            val share = (top + bottom) / device.height * 100
            save("$prefix-en-direct")

            openSpace(Space.REVENDICATIONS)
            await(RevendicationTags.CARD, prefix = true)
            val firstDemand = topOf(RevendicationTags.CARD, prefix = true)
            save("$prefix-revendications")

            onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
            await(AgirTags.HOME)
            val firstRubric = topOf(AgirTags.RUBRIC, prefix = true)
            save("$prefix-agir")

            report.append("| ${device.name} x ${device.height} | ${"%.2f".format(scale)} | ${"%.0f".format(top + bottom)} dp, ${"%.0f".format(share)} % | $firstDemand | $firstRubric |\n")
        }
        File(dir, "mesures.md").writeText(report.toString())
    }
}
