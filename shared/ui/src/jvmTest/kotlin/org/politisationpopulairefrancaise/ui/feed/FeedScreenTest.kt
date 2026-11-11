package org.politisationpopulairefrancaise.ui.feed

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.shell.openSpace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.revendications.RevendicationTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The feed: bundled posts, the live banner, the invitation to create an account, the swiped views, publishing and local reactions. */
@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

    /** The five bundled posts are imported at start; the newest one (the live example) is at the top of the list. */
    private fun ComposeUiTest.waitForPosts(store: LocalEventStore) {
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Rassemblement fictif en cours", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeUiTest.hasNode(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun bundledPostsAreShownNewestFirstWithoutAccount() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(AppDependencies(InMemoryIdentityStore(), contentStore = store)) } }
        // The feed, "Actu", is no longer the first screen (D-215).
        openSpace(Space.FIL)
        waitForPosts(store)
        assertEquals(5, store.recent(kinds = listOf(1)).size)
        // The [EXEMPLE] marker is shown as a badge, not in the text.
        onNodeWithText("[EXEMPLE] Rassemblement fictif en cours", substring = true).assertDoesNotExist()
        assertTrue(hasNode(FeedTags.LIVE))
        assertTrue(hasNode(FeedTags.CREATE_ACCOUNT))
        assertTrue(!hasNode(FeedTags.COMPOSER))
        onNodeWithTag(FeedTags.TAB + FeedFilter.ALL.id).assertIsSelected()
        onNodeWithTag(FeedTags.TAB + FeedFilter.MINE.id).assertDoesNotExist() // no account, no "my publications"
        // Reactions stay local: the heart toggles.
        val first = store.recent(kinds = listOf(1)).first()
        onAllNodesWithTag(FeedTags.LIKE + first.id.take(8)).onFirst().performScrollTo().performClick()
        waitForIdle()
        onAllNodesWithTag(FeedTags.LIKE + first.id.take(8)).onFirst().assertIsSelected()
    }

    @Test
    fun theDemandsViewShowsTheBundledDemands() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(AppDependencies(InMemoryIdentityStore(), contentStore = store)) } }
        // The feed, "Actu", is no longer the first screen (D-215).
        openSpace(Space.FIL)
        waitForPosts(store)
        onNodeWithTag(FeedTags.TAB + FeedFilter.REVENDICATIONS.id).performClick()
        waitForIdle()
        onNodeWithTag(FeedTags.TAB + FeedFilter.REVENDICATIONS.id).assertIsSelected()
        // Newest first, then by title: the Lyon demand leads; the third card is below the fold (lazy list).
        waitUntil(timeoutMillis = 5_000) { hasNode(RevendicationTags.CARD + "lyon-transports-nuit") }
        assertTrue(hasNode(RevendicationTags.CARD + "transparence-agenda-elus"))
        // No composer and no publication on this view, only demands.
        assertTrue(!hasNode(FeedTags.COMPOSER))
        onAllNodesWithTag(RevendicationTags.VOTE_FOR).onFirst().assertIsNotEnabled()
    }

    @Test
    fun anAccountCanPublishANoteWhichWaitsForTheServer() = runComposeUiTest {
        val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
        val identity = InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase))
        val store = LocalEventStore(JvmDatabase.inMemory())
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(AppDependencies(identity, contentStore = store)) } }
        // The feed, "Actu", is no longer the first screen (D-215).
        openSpace(Space.FIL)
        waitForPosts(store)
        waitUntil(timeoutMillis = 5_000) { hasNode(FeedTags.COMPOSER) }

        onAllNodesWithTag(FeedTags.PUBLISH).onFirst().assertIsNotEnabled()
        onAllNodesWithTag(FeedTags.COMPOSER).onFirst().performTextInput("[EXEMPLE] Mon premier message")
        onAllNodesWithTag(FeedTags.PUBLISH).onFirst().assertIsEnabled().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Mon premier message").fetchSemanticsNodes().isNotEmpty() }

        onAllNodesWithText("Vous").onFirst().assertExists()
        onAllNodesWithText("En attente d’envoi au serveur").onFirst().assertExists()
        val pending = store.pending()
        assertEquals(1, pending.size)
        assertEquals("[EXEMPLE] Mon premier message", pending.single().content)
        assertEquals(phrase.deriveKeys().publicKey.hex, pending.single().pubkey)
        assertTrue(pending.single().verify())

        // "Mes publications" only shows the user's own note; the live banner stays.
        val livePost = store.recent(kinds = listOf(1)).first { it.pubkey != pending.single().pubkey }
        assertTrue(hasNode(FeedTags.POST + livePost.id.take(8)))
        onNodeWithTag(FeedTags.TAB + FeedFilter.MINE.id).performClick()
        waitForIdle()
        onNodeWithTag(FeedTags.TAB + FeedFilter.MINE.id).assertIsSelected()
        waitUntil(timeoutMillis = 5_000) { hasNode(FeedTags.LIST + FeedFilter.MINE.id) }
        assertTrue(onAllNodesWithText("Mon premier message").fetchSemanticsNodes().isNotEmpty())
        assertTrue(hasNode(FeedTags.LIVE))
    }
}
