package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: a camera for the agenda, like ScreenshotTest. With PPF_SCREENSHOTS_DIR set, renders
 * the list, the filters, a detail, the export and link warnings, a cancelled event, the proposal
 * form with a close event, its result and "Mes propositions", on a phone (light and dark) and a
 * desktop, on Friday 13 November 2026. Without the variable it does nothing.
 */
@OptIn(ExperimentalTestApi::class)
class AgendaScreenshotTest {

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

    private fun ComposeUiTest.backIfAny() {
        if (onAllNodesWithTag(AgendaTags.BACK).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
            settle()
        }
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        setContent { TestAgenda(device, width, height, dark = dark) }
        waitUntil(timeoutMillis = 20_000) { cardCount() == 7 }
        settle()
        save("$prefix-agenda")

        onNodeWithTag(AgendaTags.FILTERS).performClick()
        settle()
        save("$prefix-agenda-filtres")
        onNodeWithTag(AgendaPanelTags.TYPE + "manifestation").performScrollTo().performClick()
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        settle()
        save("$prefix-agenda-filtre")
        onNodeWithTag(AgendaTags.CLEAR_ALL).performClick()
        settle()

        onNodeWithTag(AgendaTags.CARD + "manifestation-hopital-marseille-2026-11-21").performScrollTo().performClick()
        settle()
        save("$prefix-agenda-detail")
        onNodeWithTag(AgendaTags.RESPONSE + "going").performScrollTo().performClick()
        settle()
        save("$prefix-agenda-detail-reponse")
        onNodeWithTag(AgendaTags.EXPORT).performScrollTo().performClick()
        settle()
        save("$prefix-agenda-export")
        onNodeWithTag(AgendaTags.EXPORT_CANCEL).performClick()
        settle()
        onNodeWithTag(AgendaTags.LINK).performScrollTo().performClick()
        settle()
        save("$prefix-agenda-lien")
        onNodeWithTag(AgendaTags.LEAVE_STAY).performClick()
        settle()
        backIfAny()

        onNodeWithTag(AgendaTags.CARD + "rassemblement-maternite-seine-saint-denis-2026-12-05").performScrollTo().performClick()
        settle()
        save("$prefix-agenda-annule")
        backIfAny()

        onNodeWithTag(AgendaTags.PROPOSE).performScrollTo().performClick()
        settle()
        save("$prefix-agenda-proposer")
        onNodeWithTag(ProposeTags.TYPE + "rassemblement").performScrollTo().performClick()
        onNodeWithTag(ProposeTags.TITLE).performScrollTo().performTextInput("[EXEMPLE] Rassemblement pour défendre les services publics")
        onNodeWithTag(ProposeTags.START_DATE).performScrollTo().performTextInput("14/11/2026")
        onNodeWithTag(ProposeTags.START_TIME).performScrollTo().performTextInput("15h")
        onNodeWithTag(ProposeTags.END_TIME).performScrollTo().performTextInput("13h")
        onNodeWithTag(ProposeTags.LOCATION).performScrollTo().performTextInput("Place de la République, Paris")
        settle()
        onNodeWithTag(ProposeTags.END_TIME).performScrollTo()
        settle()
        save("$prefix-agenda-proposer-erreur")
        onNodeWithTag(ProposeTags.SIMILAR).performScrollTo()
        settle()
        save("$prefix-agenda-proposer-proches")

        onNodeWithTag(ProposeTags.END_TIME).performScrollTo().performTextReplacement("17h")
        onNodeWithTag(ProposeTags.TERRITORY).performScrollTo().performTextInput("Paris")
        onNodeWithTag(ProposeTags.CHARTER).performScrollTo().performClick()
        settle()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ProposeTags.RESULT + "-sent_for_review").fetchSemanticsNodes().isNotEmpty() }
        settle()
        save("$prefix-agenda-proposer-envoye")
        onNodeWithTag(ProposeTags.RESULT_BACK).performClick()
        settle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(AgendaTags.OWN_STATE + "in_review", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        settle()
        save("$prefix-agenda-mes-propositions")
    }

    @Test
    fun phoneLight() = capture(360, 760, dark = false, prefix = "agenda-telephone-clair")

    @Test
    fun phoneDark() = capture(360, 760, dark = true, prefix = "agenda-telephone-sombre")

    @Test
    fun desktopLight() = capture(1000, 740, dark = false, prefix = "agenda-ordinateur-clair")
}
