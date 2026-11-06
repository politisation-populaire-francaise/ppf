package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.feed.FeedTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Flux" view of the messages: the followed accounts read as one-way conversations, assembled
 * on the device, without any account. The five bundled posts share one author, the example
 * editorial account.
 */
@OptIn(ExperimentalTestApi::class)
class FlowsTest {

    private val editor = EditorialTrust.EXAMPLE_EDITOR

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.sees(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.launch(dependencies: AppDependencies, width: Int = 1000, height: Int = 740): LocalEventStore {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) } }
        val store = dependencies.contentStore!!
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { sees("Rassemblement fictif en cours") }
        return store
    }

    private fun ComposeUiTest.openFlows() {
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + MessagesView.FLOWS.id).performClick()
        waitForIdle()
        onNodeWithTag(MessagesTags.TAB + MessagesView.FLOWS.id).assertIsSelected()
    }

    @Test
    fun followingTheExampleAccountMakesItAOneWayConversation() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val store = launch(AppDependencies.withDatabase(InMemoryIdentityStore(), database))
        val posts = store.recent(kinds = listOf(1))

        // Nothing followed yet: the view says how to fill it, and that the list stays on the device.
        openFlows()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.EMPTY) }
        assertTrue(sees("reste sur votre appareil"))

        // Follow the example account from a card of the feed, without any account.
        onNodeWithTag(ShellTags.navItem(Space.FIL)).performClick()
        waitForIdle()
        val suffix = posts.first().id.take(8)
        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().performScrollTo().assert(hasText("Suivre")).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { follows.isFollowed(editor) }

        openFlows()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.row(editor)) }
        onNodeWithTag(FlowsTags.row(editor)).assert(hasText("Politisation Populaire Française"))
        onNodeWithTag(FlowsTags.row(editor)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.SCREEN) }
        // The newest publication sits at the bottom of the thread, marked as an example, with no way to answer.
        assertTrue(has(FlowsTags.POST + posts.first().id.take(8)))
        assertTrue(sees("[EXEMPLE]"))
        onNodeWithTag(FlowsTags.PUBLIC).assert(hasText("vous ne répondez pas ici", substring = true))
        assertTrue(!has(ConversationTags.COMPOSER))
        // Desktop: the list stays next to the open flow.
        assertTrue(has(FlowsTags.LIST))

        // Unfollowing from the flow closes it and empties the view.
        onNodeWithTag(FlowsTags.UNFOLLOW).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.EMPTY) }
        assertTrue(!has(FlowsTags.SCREEN))
        waitUntil(timeoutMillis = 5_000) { follows.count() == 0L }
    }

    @Test
    fun onAPhoneAFlowTakesTheWholeScreenWithABackButton() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        FollowStore(database).follow(editor)
        launch(AppDependencies.withDatabase(InMemoryIdentityStore(), database), width = 360, height = 740)
        openFlows()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.row(editor)) }
        onNodeWithTag(FlowsTags.row(editor)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.SCREEN) }
        assertTrue(!has(MessagesTags.TAB + MessagesView.FLOWS.id))
        onNode(hasContentDescription("Retour aux flux")).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(FlowsTags.LIST) }
        assertEquals(1, onAllNodesWithTag(FlowsTags.row(editor)).fetchSemanticsNodes().size)
    }
}
