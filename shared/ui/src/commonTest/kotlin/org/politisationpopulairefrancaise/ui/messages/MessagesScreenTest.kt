package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The messages screen: its three views, swiped or chosen by tab. Without a local database nor an
 * account (the default dependencies), "Nouvelle conversation" stays disabled.
 */
@OptIn(ExperimentalTestApi::class)
class MessagesScreenTest {

    @Test
    fun theThreeViewsAreReachableByTheTabs() = runComposeUiTest {
        setContent { Box(Modifier.requiredSize(360.dp, 700.dp)) { PpfApp() } }
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + MessagesView.PRIVATE.id).assertIsSelected()
        assertTrue(onAllNodesWithText("Conversations privées et de groupe").fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(MessagesTags.NEW).assertIsNotEnabled()

        onNodeWithTag(MessagesTags.TAB + MessagesView.FLOWS.id).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + MessagesView.FLOWS.id).assertIsSelected()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Médias et élus suivis").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag(MessagesTags.TAB + MessagesView.COLLECTIVES.id).performClick()
        waitForIdle()
        // The rooms of collectives (CollectivesScreenTest); without a database on this device, it says so.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Les salons ne sont pas disponibles sur cet appareil.").fetchSemanticsNodes().isNotEmpty() }
    }
}
