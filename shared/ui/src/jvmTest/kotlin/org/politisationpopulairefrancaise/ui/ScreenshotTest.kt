package org.politisationpopulairefrancaise.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.components.AuthorDialogTags
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.feed.FeedFilter
import org.politisationpopulairefrancaise.ui.feed.FeedTags
import org.politisationpopulairefrancaise.ui.revendications.FilterPanelTags
import org.politisationpopulairefrancaise.ui.revendications.ManageViewsTags
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import org.politisationpopulairefrancaise.ui.revendications.propose.ProposalTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.shell.openSpace
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

    /** Writes the topmost root: the screen itself, or the dialog layer when one is open. */
    private fun ComposeUiTest.save(name: String) {
        val dir = directory ?: return
        dir.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.waitForContents(store: LocalEventStore) {
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(FeedTags.LIVE).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(width: Int, height: Int, dark: Boolean, prefix: String) = runComposeUiTest {
        if (directory == null) return@runComposeUiTest
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        val store = dependencies.contentStore!!
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = dark) {
                    org.politisationpopulairefrancaise.ui.shell.AdaptiveShell(dependencies)
                }
            }
        }
        openSpace(Space.FIL)
        waitForContents(store)
        save("$prefix-fil")

        // The "Suivis" view: empty first, then the author box, then filled once the example account is followed.
        val firstPost = store.recent(kinds = listOf(1)).first().id.take(8)
        onNodeWithTag(FeedTags.TAB + FeedFilter.FOLLOWED.id).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Vous ne suivez encore aucun compte", substring = true).fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-fil-suivis-vide")
        onNodeWithTag(FeedTags.TAB + FeedFilter.ALL.id).performClick()
        waitForIdle()
        onAllNodesWithTag(FeedTags.AUTHOR + firstPost).onFirst().performScrollTo().performClick()
        waitForIdle()
        save("$prefix-fil-auteur")
        onNodeWithTag(AuthorDialogTags.FOLLOW).performClick()
        waitForIdle()
        onNodeWithTag(FeedTags.TAB + FeedFilter.FOLLOWED.id).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag(FeedTags.POST + firstPost) and hasAnyAncestor(hasTestTag(FeedTags.LIST + FeedFilter.FOLLOWED.id))).fetchSemanticsNodes().isNotEmpty()
        }
        save("$prefix-fil-suivis")

        onNodeWithTag(FeedTags.TAB + FeedFilter.REVENDICATIONS.id).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-fil-revendications")

        openSpace(Space.REVENDICATIONS)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes().isNotEmpty() }
        save("$prefix-revendications")

        // Views and filters of the wall: the filter panel, a filtered list, "Personnaliser".
        onNodeWithTag(RevendicationTags.FILTERS).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        save("$prefix-revendications-filtres")
        onNodeWithTag(FilterPanelTags.THEME + "education").performScrollTo().performClick()
        onNodeWithTag(FilterPanelTags.APPLY).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        save("$prefix-revendications-filtrees")
        onNodeWithTag(RevendicationTags.CUSTOMIZE).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        save("$prefix-revendications-vues")
        onNodeWithTag(ManageViewsTags.DONE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag(RevendicationTags.CLEAR_ALL).performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithTag(RevendicationTags.CARD + "lyon-transports-nuit").fetchSemanticsNodes()
        onNodeWithTag(RevendicationTags.CARD + "lyon-transports-nuit").performScrollTo().performClick()
        waitForIdle()
        save("$prefix-revendication-detail")

        // The proposal form, with a duplicate found while typing and the checks.
        if (onAllNodesWithTag(RevendicationTags.BACK).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag(RevendicationTags.BACK).performClick()
            waitForIdle()
        }
        onAllNodes(hasTestTag(RevendicationTags.PROPOSE)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposalTags.TITLE).performTextInput("Cantine à un euro pour les familles modestes")
        waitForIdle()
        save("$prefix-proposer")
        onNodeWithTag(ProposalTags.CHECKS).performScrollTo()
        waitForIdle()
        save("$prefix-proposer-verifications")
        onNodeWithTag(ProposalTags.CLOSE).performScrollTo().performClick()
        waitForIdle()

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
            openSpace(Space.COMPRENDRE)
            waitForIdle()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        } else {
            openSpace(Space.COMPRENDRE)
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
