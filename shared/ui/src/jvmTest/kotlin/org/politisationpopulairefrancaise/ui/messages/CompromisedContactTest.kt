package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.AccountRecoveryClient
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.RecoveryPhrase
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.social.ContactVerificationStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A contact declared compromised (S-146, D-134): the conversation says so with the dates, marks the
 * messages dated after "compromis depuis", no longer writes to the key, withdraws the in-person
 * check, and only proposes the successor.
 */
@OptIn(ExperimentalTestApi::class)
class CompromisedContactTest {

    private val aliceKeys = IdentityKeys.generate()
    private val alice = aliceKeys.publicKey.hex
    private val bobPhrase = RecoveryPhrase.generate()
    private val bobKeys = bobPhrase.deriveKeys()
    private val bob = bobKeys.publicKey.hex
    private val successor = IdentityKeys.generate().publicKey.hex
    private val withBob = Conversation.idOf(listOf(alice, bob))
    private val database = JvmDatabase.inMemory()
    private val store = MessageStore(database)
    private val now = currentEpochSeconds()
    private val before = ChatMessage.compose(bobKeys.publicKey, listOf(aliceKeys.publicKey), "[EXEMPLE] Avant", createdAt = now - 3 * 3600)
    private val after = ChatMessage.compose(bobKeys.publicKey, listOf(aliceKeys.publicKey), "[EXEMPLE] Après", createdAt = now - 600)

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openConversationWithBob(server: InMemoryPublicationPort) {
        store.store(before)
        store.store(after)
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(StoredIdentity.of(aliceKeys, null)), database, remote = server)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { has(PrivateTags.row(withBob, alice)) }
        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { has(ConversationTags.SCREEN) }
    }

    private fun declareBob(server: InMemoryPublicationPort, withSuccessor: Boolean) = runBlocking {
        val client = AccountRecoveryClient(server)
        client.ensureCommitment(LocalEventSigner(bobKeys), bobPhrase.deriveRecoveryKeys().publicKey)
        client.declareCompromised(bobPhrase, now - 3600, if (withSuccessor) org.politisationpopulairefrancaise.core.identity.PublicKey.fromHex(successor) else null)
    }

    @Test
    fun theConversationWarnsMarksTheLaterMessagesAndStopsWriting() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        declareBob(server, withSuccessor = true)
        ContactVerificationStore(database).markVerified(bob)
        openConversationWithBob(server)

        waitUntil(timeoutMillis = 10_000) { has(ConversationTags.COMPROMISED + bob.take(8)) }
        assertTrue(onAllNodesWithText("déclaré compromis le", substring = true).fetchSemanticsNodes().isNotEmpty())
        // Only the message dated after "compromis depuis" is marked.
        assertTrue(has(ConversationTags.SUSPECT + after.id.take(8)))
        assertFalse(has(ConversationTags.SUSPECT + before.id.take(8)))
        // Writing to the key is blocked, with the reason.
        onNodeWithTag(ConversationTags.COMPOSER).performTextInput("[EXEMPLE] Tu es là ?")
        onNodeWithTag(ConversationTags.SEND).assertIsNotEnabled()
        assertTrue(onAllNodesWithText("aucun message ne peut lui être envoyé", substring = true).fetchSemanticsNodes().isNotEmpty())
        // The in-person check no longer proves anything.
        waitUntil(timeoutMillis = 5_000) { !ContactVerificationStore(database).isVerified(bob) }
        // The notice and its commitment are kept on the device.
        assertTrue(LocalEventStore(database).byTag(listOf(AccountRecovery.NOTICE_KIND), "p", bob).isNotEmpty())

        // The successor is only proposed: the user opens it.
        onNodeWithTag(ConversationTags.WRITE_SUCCESSOR + bob.take(8)).performClick()
        waitForIdle()
        assertFalse(has(ConversationTags.COMPROMISED + bob.take(8)))
        onNodeWithTag(ConversationTags.COMPOSER).performTextInput("[EXEMPLE] Bonjour")
        waitForIdle()
        assertTrue(onNodeWithTag(ConversationTags.SEND).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not())
    }

    @Test
    fun aContactWhoIsNotCompromisedShowsNothing() = runComposeUiTest {
        val server = InMemoryPublicationPort()
        openConversationWithBob(server)
        waitForIdle()
        assertFalse(has(ConversationTags.COMPROMISED + bob.take(8)))
        assertFalse(has(ConversationTags.SUSPECT + after.id.take(8)))
    }
}
