package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** The demands wall: bundled example demands, their detail, and the vote that waits for its decision. */
@OptIn(ExperimentalTestApi::class)
class RevendicationsScreenTest {

    private fun dependencies() = AppDependencies(identityStore = InMemoryIdentityStore(), contentStore = LocalEventStore(JvmDatabase.inMemory()))

    private fun ComposeUiTest.openWall(dependencies: AppDependencies, width: Int, height: Int) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.REVENDICATIONS)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(RevendicationTags.CARD + "transparence-agenda-elus").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun bundledDemandsAreListedWithTheirStatusAndOpenBesideTheListOnWideWindows() = runComposeUiTest {
        val dependencies = dependencies()
        openWall(dependencies, width = 1000, height = 740)
        assertEquals(8, dependencies.contentStore!!.byTag(listOf(30023), "ppf-type", "revendication").size)
        onNodeWithTag(RevendicationTags.CARD + "cantines-tarif-social").assertExists()
        onNodeWithTag(RevendicationTags.CARD + "lyon-transports-nuit").assertExists()
        onAllNodesWithText("Très soutenue").onFirst().assertExists()
        onAllNodesWithText("En débat").onFirst().assertExists()
        onAllNodesWithTag(RevendicationTags.VOTE_FOR).onFirst().assertIsNotEnabled()

        onNodeWithTag(RevendicationTags.CARD + "lyon-transports-nuit").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.DETAIL).assertExists()
        onNodeWithTag(RevendicationTags.LIST).assertExists()
        onNodeWithTag(RevendicationTags.BACK).assertDoesNotExist()
        onNodeWithText("Argumentaire").assertExists()
        onAllNodesWithText("Métropole de Lyon").onFirst().assertExists()
    }

    @Test
    fun phoneShowsOnePaneWithABackButton() = runComposeUiTest {
        openWall(dependencies(), width = 360, height = 700)
        onNodeWithTag(RevendicationTags.CARD + "transparence-agenda-elus").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.DETAIL).assertExists()
        onNodeWithTag(RevendicationTags.LIST).assertDoesNotExist()
        onNodeWithTag(RevendicationTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(RevendicationTags.LIST).assertExists()
        onNodeWithTag(RevendicationTags.DETAIL).assertDoesNotExist()
    }
}
