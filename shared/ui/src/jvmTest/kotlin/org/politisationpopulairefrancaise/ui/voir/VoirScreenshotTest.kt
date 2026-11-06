package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for "Voir" (see ScreenshotTest). Writes PNG files to PPF_SCREENSHOTS_DIR
 * when the variable is set; does nothing otherwise.
 */
@OptIn(ExperimentalTestApi::class)
class VoirScreenshotTest {

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

    private fun ComposeUiTest.openVoir(width: Int) {
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Voir").performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    private fun ComposeUiTest.back() {
        onNodeWithTag(VoirTags.BACK).performClick()
        settle()
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String, full: Boolean) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfTheme(darkTheme = dark) { AdaptiveShell(dependencies) } } }
        settle()
        openVoir(width)
        save("$prefix-voir-mouvement")

        onNodeWithTag(VoirTags.card("manifestants-journees")).performClick()
        settle()
        save("$prefix-voir-indicateur")
        onNodeWithTag(VoirTags.CHART).performScrollTo()
        settle()
        save("$prefix-voir-indicateur-graphique")
        if (!full) return@runComposeUiTest
        onNodeWithTag(VoirTags.section("next")).performScrollTo()
        settle()
        save("$prefix-voir-indicateur-et-maintenant")
        back()

        onNodeWithTag(VoirTags.tab(1)).performClick()
        settle()
        save("$prefix-voir-pouvoir")
        onNodeWithTag(VoirTags.card("dominique-fictif")).performScrollTo().performClick()
        settle()
        save("$prefix-voir-fiche-personne")
        onNodeWithTag(VoirTags.card("affaire-dominique-marche-public")).performScrollTo().performClick()
        settle()
        save("$prefix-voir-affaire")
        onNodeWithText("Droit de réponse de Dominique Fictif").performScrollTo()
        settle()
        save("$prefix-voir-affaire-reponse")
        back()
        back()

        onNodeWithTag(VoirTags.card("pjl-ordre-public-manifestations")).performScrollTo().performClick()
        settle()
        save("$prefix-voir-texte")
        onNodeWithTag(VoirTags.section("impacts")).performScrollTo()
        settle()
        save("$prefix-voir-texte-qui-gagne")
        back()

        onNodeWithTag(VoirTags.tab(2)).performClick()
        settle()
        save("$prefix-voir-libertes")
        onNodeWithTag(VoirTags.GLOSSARY_OPEN).performScrollTo().performClick()
        settle()
        save("$prefix-voir-lexique")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "telephone-clair", full = true)

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "telephone-sombre", full = false)

    @Test
    fun tabletLight() = capture(800, 1100, dark = false, prefix = "tablette-clair", full = false)

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "ordinateur-clair", full = true)
}
