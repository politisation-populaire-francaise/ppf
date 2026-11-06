package org.politisationpopulairefrancaise.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.components.AuthorDialogTags
import org.politisationpopulairefrancaise.ui.components.shortNpub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Following accounts from the feed (S-120): the button of a card, the author box, the "Suivis"
 * view, and the list kept on the device across launches. The five bundled posts share one author,
 * the example editorial account.
 */
@OptIn(ExperimentalTestApi::class)
class FollowTest {

    private val editor = EditorialTrust.EXAMPLE_EDITOR

    private fun ComposeUiTest.hasNode(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.seesText(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Nodes carrying [tag] inside the list of the given view (the pager may keep a neighbouring view composed). */
    private fun ComposeUiTest.inView(filter: FeedFilter, tag: String) =
        onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(FeedTags.LIST + filter.id)))

    private fun ComposeUiTest.viewHasText(filter: FeedFilter, text: String): Boolean =
        onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(FeedTags.LIST + filter.id))).fetchSemanticsNodes().isNotEmpty()

    /** Starts the application on [database] (contents and followed accounts share it) and waits for the bundled posts. */
    private fun ComposeUiTest.launch(database: PpfDatabase, identity: IdentityStore = InMemoryIdentityStore()): LocalEventStore {
        val dependencies = AppDependencies.withDatabase(identity, database)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        val store = dependencies.contentStore!!
        waitUntil(timeoutMillis = 10_000) { store.recent(kinds = listOf(1)).size == 5 }
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { seesText("Rassemblement fictif en cours") }
        return store
    }

    private fun ComposeUiTest.openView(filter: FeedFilter) {
        onNodeWithTag(FeedTags.TAB + filter.id).performClick()
        waitForIdle()
        onNodeWithTag(FeedTags.TAB + filter.id).assertIsSelected()
    }

    @Test
    fun followingFromACardFillsTheFollowedViewAndUnfollowingEmptiesIt() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val store = launch(database)
        val suffix = store.recent(kinds = listOf(1)).first().id.take(8)

        // The view sits right after "Tout" and, with nothing followed, explains how to follow.
        openView(FeedFilter.SUIVIS)
        waitUntil(timeoutMillis = 5_000) { viewHasText(FeedFilter.SUIVIS, "Vous ne suivez encore aucun compte") }
        assertTrue(viewHasText(FeedFilter.SUIVIS, "reste sur votre appareil"))
        inView(FeedFilter.SUIVIS, FeedTags.POST + suffix).assertCountEquals(0)

        // Follow from the first card of "Tout", without any account.
        openView(FeedFilter.ALL)
        assertTrue(!hasNode(FeedTags.COMPOSER))
        val button = onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst()
        button.performScrollTo().assertIsEnabled().assert(hasText("Suivre")).performClick()
        waitForIdle()
        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().assert(hasText("Suivi"))
        waitUntil(timeoutMillis = 5_000) { follows.all() == listOf(editor) }

        // The author's publications now fill the view.
        openView(FeedFilter.SUIVIS)
        waitUntil(timeoutMillis = 5_000) { inView(FeedFilter.SUIVIS, FeedTags.POST + suffix).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(viewHasText(FeedFilter.SUIVIS, "Vous ne suivez encore aucun compte"))

        // Unfollowing from a card of the view empties it again.
        inView(FeedFilter.SUIVIS, FeedTags.FOLLOW + suffix).onFirst().performScrollTo().assert(hasText("Suivi")).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { viewHasText(FeedFilter.SUIVIS, "Vous ne suivez encore aucun compte") }
        inView(FeedFilter.SUIVIS, FeedTags.POST + suffix).assertCountEquals(0)
        waitUntil(timeoutMillis = 5_000) { follows.all().isEmpty() }
    }

    @Test
    fun theAuthorBoxShowsTheFullKeyAndFollowsOrUnfollows() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        val follows = FollowStore(database)
        val store = launch(database)
        val suffix = store.recent(kinds = listOf(1)).first().id.take(8)

        onAllNodesWithTag(FeedTags.AUTHOR + suffix).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AuthorDialogTags.KEY).assertTextEquals(PublicKey.fromHex(editor).npub)
        onAllNodesWithText(shortNpub(editor)).onFirst().assertExists()
        onNodeWithTag(AuthorDialogTags.FOLLOW).assert(hasText("Suivre")).performClick()
        waitForIdle()
        // The box closes and the card reflects the new state.
        onNodeWithTag(AuthorDialogTags.KEY).assertDoesNotExist()
        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().assert(hasText("Suivi"))
        waitUntil(timeoutMillis = 5_000) { follows.isFollowed(editor) }

        // Opened again, the box offers to unfollow; closing it changes nothing.
        onAllNodesWithTag(FeedTags.AUTHOR + suffix).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AuthorDialogTags.FOLLOW).assert(hasText("Ne plus suivre"))
        onNodeWithTag(AuthorDialogTags.CLOSE).performClick()
        waitForIdle()
        onNodeWithTag(AuthorDialogTags.KEY).assertDoesNotExist()
        assertTrue(follows.isFollowed(editor))

        onAllNodesWithTag(FeedTags.AUTHOR + suffix).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AuthorDialogTags.FOLLOW).performClick()
        waitForIdle()
        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().assert(hasText("Suivre"))
        waitUntil(timeoutMillis = 5_000) { !follows.isFollowed(editor) }
    }

    @Test
    fun theListSurvivesARelaunchOnTheSameDatabase() = runComposeUiTest {
        val database = JvmDatabase.inMemory()
        // Followed during a previous session of the application.
        FollowStore(database).follow(editor)
        val store = launch(database)
        val suffix = store.recent(kinds = listOf(1)).first().id.take(8)

        onAllNodesWithTag(FeedTags.FOLLOW + suffix).onFirst().performScrollTo().assert(hasText("Suivi"))
        openView(FeedFilter.SUIVIS)
        waitUntil(timeoutMillis = 5_000) { inView(FeedFilter.SUIVIS, FeedTags.POST + suffix).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(viewHasText(FeedFilter.SUIVIS, "Vous ne suivez encore aucun compte"))
    }

    @Test
    fun ownPublicationsOfferNoFollowButton() = runComposeUiTest {
        val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
        val identity = InMemoryIdentityStore(StoredIdentity.of(phrase.deriveKeys(), phrase))
        val database = JvmDatabase.inMemory()
        val store = launch(database, identity)
        waitUntil(timeoutMillis = 5_000) { hasNode(FeedTags.COMPOSER) }
        onAllNodesWithTag(FeedTags.COMPOSER).onFirst().performTextInput("[EXEMPLE] Ma note")
        onAllNodesWithTag(FeedTags.PUBLISH).onFirst().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { seesText("Ma note") }
        val mine = store.pending().single()
        val suffix = mine.id.take(8)

        onAllNodesWithTag(FeedTags.FOLLOW + suffix).assertCountEquals(0)
        // The box still opens on one's own name, with the key but without the follow action.
        onAllNodesWithTag(FeedTags.AUTHOR + suffix).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AuthorDialogTags.KEY).assertTextEquals(phrase.deriveKeys().publicKey.npub)
        onNodeWithTag(AuthorDialogTags.FOLLOW).assertDoesNotExist()
        onNodeWithTag(AuthorDialogTags.CLOSE).performClick()
        waitForIdle()
        assertEquals(0, FollowStore(database).count())
    }
}
