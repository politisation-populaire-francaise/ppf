package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.social.ContactVerificationStore
import org.politisationpopulairefrancaise.core.social.SafetyNumber
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The actions of a private conversation (M14): checking the contact in person with the safety
 * number (S-128), kept on the device and undone at will, and deleting the conversation from the
 * device after a confirmation whose safe choice comes first.
 */
@OptIn(ExperimentalTestApi::class)
class ConversationActionsTest {

    private val aliceKeys = IdentityKeys.generate()
    private val bobKeys = IdentityKeys.generate()
    private val alice = aliceKeys.publicKey.hex
    private val bob = bobKeys.publicKey.hex
    private val withBob = Conversation.idOf(listOf(alice, bob))
    private val database = JvmDatabase.inMemory()
    private val store = MessageStore(database)

    private fun ComposeUiTest.openConversationWithBob() {
        store.store(ChatMessage.compose(bobKeys.publicKey, listOf(aliceKeys.publicKey), "[EXEMPLE] Salut", createdAt = currentEpochSeconds() - 120))
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(StoredIdentity.of(aliceKeys, null)), database)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.row(withBob, alice)) }
        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.SCREEN) }
    }

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.sees(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openMenuItem(tag: String) {
        onNodeWithTag(ConversationActionTags.MORE).performClick()
        waitForIdle()
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun theContactIsCheckedWithTheSafetyNumberAndTheCheckCanBeUndone() = runComposeUiTest {
        openConversationWithBob()
        assertFalse(has(ConversationActionTags.VERIFIED_BADGE))

        openMenuItem(ConversationActionTags.VERIFY)
        onNodeWithTag(ConversationActionTags.VERIFY_DIALOG).assertExists()
        // The very digits Bob's phone shows for this conversation.
        SafetyNumber.of(alice, bob).chunked(4).forEach { line -> assertTrue(sees(line.joinToString("  "))) }
        assertTrue(sees("pas encore vérifié"))
        onNodeWithTag(ConversationActionTags.VERIFY_DONE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertTrue(ContactVerificationStore(database).isVerified(bob))
        assertTrue(has(ConversationActionTags.VERIFIED_BADGE))

        openMenuItem(ConversationActionTags.VERIFY)
        assertTrue(sees("déjà vérifié"))
        onNodeWithTag(ConversationActionTags.VERIFY_UNDO).performClick()
        waitForIdle()
        onNodeWithTag(ConversationActionTags.VERIFY_CLOSE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertFalse(ContactVerificationStore(database).isVerified(bob))
        assertFalse(has(ConversationActionTags.VERIFIED_BADGE))
    }

    @Test
    fun deletingAsksFirstAndKeepingIsTheSafeChoice() = runComposeUiTest {
        openConversationWithBob()
        openMenuItem(ConversationActionTags.DELETE)
        onNodeWithTag(ConversationActionTags.DELETE_DIALOG).assertExists()
        assertTrue(sees("sans retour possible"))
        onNodeWithTag(ConversationActionTags.DELETE_CANCEL).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(1, store.count())
        assertTrue(has(ConversationTags.SCREEN))

        openMenuItem(ConversationActionTags.DELETE)
        onNodeWithTag(ConversationActionTags.DELETE_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { store.count() == 0L }
        assertNull(store.conversation(withBob))
        waitUntil(timeoutMillis = 5_000) { !has(PrivateTags.row(withBob, alice)) }
    }
}
