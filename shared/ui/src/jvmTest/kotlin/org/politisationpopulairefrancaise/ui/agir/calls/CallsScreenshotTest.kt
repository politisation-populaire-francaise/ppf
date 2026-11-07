package org.politisationpopulairefrancaise.ui.agir.calls

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
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera, like ScreenshotTest. When PPF_SCREENSHOTS_DIR names a directory, renders
 * the screens of the calls at phone size (light and dark) and desktop size and writes one PNG per
 * screen there. Without the variable it does nothing. Run with:
 *   PPF_SCREENSHOTS_DIR=captures gradlew :shared:ui:jvmTest --tests "*CallsScreenshotTest*"
 */
@OptIn(ExperimentalTestApi::class)
class CallsScreenshotTest {

    private val directory: File? = System.getenv("PPF_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val harness = showCalls(width = width, height = height, dark = dark, keys = IdentityKeys.generate(), onBack = {}, onCreateEvent = {}, onCreateDemand = {})
        save("$prefix-appels")

        onNodeWithTag(CallTags.FILTERS).performClick()
        save("$prefix-appels-filtres")
        onNodeWithTag(CallTags.FILTER_DUE + "this_month").performScrollTo().performClick()
        onNodeWithTag(CallTags.FILTERS).performScrollTo().performClick()
        save("$prefix-appels-filtres-actifs")
        harness.calls.query = harness.calls.query.cleared()

        onNodeWithTag(CallTags.CARD + Examples.PETITION).performScrollTo().performClick()
        save("$prefix-appel-detail")
        onNodeWithTag(CallTags.ANSWER).performScrollTo().performClick()
        save("$prefix-appel-repondu")
        onNodeWithTag(CallTags.LINK + 0).performScrollTo().performClick()
        save("$prefix-appel-quitter-ppf")
        onNodeWithTag(CallTags.LEAVE_STAY).performClick()

        harness.calls.open(harness.call(Examples.CLOSED))
        save("$prefix-appel-clos")
        harness.calls.open(harness.call(Examples.WITNESSES))
        save("$prefix-appel-temoins")
        harness.calls.close()

        onNodeWithTag(CallTags.TAB + "answered").performClick()
        save("$prefix-appels-mes-reponses")
        onNodeWithTag(CallTags.TAB + "all").performClick()

        onNodeWithTag(CallTags.LAUNCH).performScrollTo().performClick()
        save("$prefix-lancer-type")
        onNodeWithTag(CallComposerTags.TYPE + CallType.MANIFESTER.tagValue).performScrollTo().performClick()
        onNodeWithTag(CallComposerTags.FIELD + "title").performScrollTo().performTextInput("Marche pour garder les urgences de nuit")
        save("$prefix-lancer-formulaire")
        onNodeWithTag(CallComposerTags.ISSUES).performScrollTo()
        save("$prefix-lancer-verifications")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "ordinateur-clair")
}
