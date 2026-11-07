package org.politisationpopulairefrancaise.ui.glossaire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the glossary, like ScreenshotTest. With PPF_SCREENSHOTS_DIR set, it
 * writes a text whose terms are spotted with the tooltip of « motion de censure » open (phone, light
 * and dark), the page of the entry over the text, and the full glossary on a phone and a computer.
 */
@OptIn(ExperimentalTestApi::class)
class GlossaryScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(name: String, last: Boolean = false) {
        val dir = directory ?: return
        dir.mkdirs()
        val roots = onAllNodes(isRoot())
        ImageIO.write((if (last) roots.onLast() else roots.onFirst()).captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    /** A window the size of a phone, so that the tooltip and the page meet its edges as on a real screen. */
    private fun captureText(dark: Boolean, prefix: String) = runDesktopComposeUiTest(width = 400, height = 800) {
        if (directory == null) return@runDesktopComposeUiTest
        val state = GlossaryFixtures.state()
        setContent {
            Box(Modifier.requiredSize(400.dp, 800.dp)) {
                PpfTheme(darkTheme = dark) {
                    ProvideGlossary(state) {
                        // A screen gives its texts the ink of the theme, as the shell's surfaces do.
                        CompositionLocalProvider(LocalContentColor provides PpfTheme.palette.ink) {
                        Column(Modifier.fillMaxSize().background(PpfTheme.palette.surface1).padding(16.dp)) {
                            PpfBadge("Parcours", BadgeStyle.BRAND)
                            Spacer(Modifier.height(8.dp))
                            Text("Le 49.3 et la motion de censure", style = MaterialTheme.typography.headlineMedium, color = PpfTheme.palette.ink)
                            Spacer(Modifier.height(16.dp))
                            SelectionContainer {
                                MarkdownText(
                                    GlossaryFixtures.PARAGRAPH +
                                        "\n\n::: en-bref\n- Le Sénat ne vote pas la motion de censure.\n- Le 49.3 peut servir une fois par session, en plus des lois de finances.\n:::\n",
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
        waitForIdle()
        save("$prefix-glossaire-texte")
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).performTouchInput { click(center) }
        waitForIdle()
        save("$prefix-glossaire-infobulle")
        onNodeWithTag(GlossaryTags.MORE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        save("$prefix-glossaire-page", last = true)
    }

    @Test
    fun textOnAPhone() = captureText(dark = false, prefix = "telephone-clair")

    @Test
    fun textOnAPhoneInTheDark() = captureText(dark = true, prefix = "telephone-sombre")

    private fun captureList(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val state = GlossaryFixtures.state()
        if (width >= 840) state.select("motion-de-censure")
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) {
                    ProvideGlossary(state) {
                        GlossaryScreen(state, WindowClass.of(widthDp = width.toFloat(), heightDp = height.toFloat()), onBack = {})
                    }
                }
            }
        }
        waitForIdle()
        save("$prefix-glossaire-complet")
    }

    @Test
    fun fullGlossaryOnAPhone() = captureList(400, 800, dark = false, prefix = "telephone-clair")

    @Test
    fun fullGlossaryOnAComputerInTheDark() = captureList(1000, 740, dark = true, prefix = "ordinateur-sombre")
}
