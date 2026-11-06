package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.IdentityStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.MessagingService
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.account.AccountTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The private conversations on screen (NIP-17, D-027): reading without an account, the empty list
 * and the key to give, a new conversation with an npub written offline first, two accounts
 * exchanging through a relay, unread counts and read marks kept on the device, the thread grouped
 * by day, phone and desktop layouts, the open conversation surviving a visit elsewhere, the
 * explanation of the encryption, and signing out forgetting the conversations.
 */
@OptIn(ExperimentalTestApi::class)
class PrivateMessagesTest {

    private val aliceKeys = IdentityKeys.generate()
    private val bobKeys = IdentityKeys.generate()
    private val alice = aliceKeys.publicKey.hex
    private val bob = bobKeys.publicKey.hex
    private val withBob = Conversation.idOf(listOf(alice, bob))

    private class Device(val dependencies: AppDependencies, val store: MessageStore)

    private fun identity(keys: IdentityKeys): IdentityStore = InMemoryIdentityStore(StoredIdentity.of(keys, null))

    private fun device(identity: IdentityStore = InMemoryIdentityStore(), remote: RemoteEvents? = null): Device {
        val database = JvmDatabase.inMemory()
        return Device(AppDependencies.withDatabase(identity, database, remote = remote), MessageStore(database))
    }

    private fun ComposeUiTest.launch(device: Device, width: Int = 1000, height: Int = 740) {
        setContent { Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(device.dependencies) } }
        waitForIdle()
    }

    private fun ComposeUiTest.has(tag: String, unmerged: Boolean = false): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.sees(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openMessages() {
        onNodeWithTag(ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
    }

    /** A message from Bob to Alice stored directly on Alice's device, as if received earlier. */
    private fun fromBob(text: String, at: Long): ChatMessage = ChatMessage.compose(bobKeys.publicKey, listOf(aliceKeys.publicKey), text, createdAt = at)

    @Test
    fun readingWithoutAnAccountInvitesToCreateOne() = runComposeUiTest {
        launch(device())
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.INVITATION) }
        assertTrue(sees("Tout se lit sans compte"))
        onNodeWithTag(MessagesTags.NEW).assertIsNotEnabled()
        onNodeWithTag(PrivateTags.CREATE_ACCOUNT).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(AccountTags.CREATE) }
    }

    @Test
    fun withAnAccountTheEmptyListShowsTheKeyToGiveAndTheLimitsOfTheEncryption() = runComposeUiTest {
        launch(device(identity(aliceKeys)))
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.EMPTY) }
        onNodeWithTag(MessagesTags.NEW).assertIsEnabled()
        // Wide window: the list and, next to it, the place of the open conversation.
        assertTrue(has(PrivateTags.SELECT_HINT))
        onNodeWithTag(PrivateTags.STATUS).assert(hasText("Pas de serveur", substring = true))
        onNodeWithTag(PrivateTags.LIST).performScrollToNode(hasTestTag(PrivateTags.MY_KEY))
        onNodeWithTag(PrivateTags.MY_KEY).assertTextEquals(aliceKeys.publicKey.npub)
        onNodeWithTag(PrivateTags.LIST).performScrollToNode(hasTestTag(PrivateTags.ENCRYPTION))
        assertTrue(sees("Limites connues"))
    }

    @Test
    fun aNewConversationWithAnNpubIsWrittenOfflineFirst() = runComposeUiTest {
        val device = device(identity(aliceKeys))
        launch(device)
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.EMPTY) }
        onNodeWithTag(MessagesTags.NEW).performClick()
        waitForIdle()

        // A mistyped key is caught before anything starts; one's own key too.
        onNodeWithTag(NewConversationTags.KEY).performTextInput("npub1nimportequoi")
        onNodeWithTag(NewConversationTags.CONFIRM).performClick()
        waitForIdle()
        onNodeWithTag(NewConversationTags.ERROR).assert(hasText("pas valide", substring = true))
        onNodeWithTag(NewConversationTags.KEY).performTextClearance()
        onNodeWithTag(NewConversationTags.KEY).performTextInput(aliceKeys.publicKey.npub)
        onNodeWithTag(NewConversationTags.CONFIRM).performClick()
        waitForIdle()
        onNodeWithTag(NewConversationTags.ERROR).assert(hasText("votre propre clé", substring = true))

        onNodeWithTag(NewConversationTags.KEY).performTextClearance()
        onNodeWithTag(NewConversationTags.KEY).performTextInput(bobKeys.publicKey.npub)
        onNodeWithTag(NewConversationTags.CONFIRM).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.EMPTY) }
        onNodeWithTag(NewConversationTags.DIALOG).assertDoesNotExist()
        // Nothing exists yet anywhere: a conversation starts with its first message.
        assertEquals(0, device.store.count())

        onNodeWithTag(ConversationTags.COMPOSER).performTextInput("[EXEMPLE] Premier message")
        onNodeWithTag(ConversationTags.SEND).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { device.store.count() == 1L }
        val message = device.store.messages(withBob).single()
        val suffix = message.id.take(8)
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.MESSAGE + suffix) }
        // Without a server it waits on the device, with a visible clock, one delivery per participant.
        assertTrue(has(ConversationTags.PENDING + suffix, unmerged = true))
        onNodeWithTag(ConversationTags.STATUS).assert(hasText("Pas de serveur", substring = true))
        assertEquals(2, device.store.outgoingCount())
        assertTrue(has(PrivateTags.row(withBob, alice)))
        onNodeWithTag(ConversationTags.SEND).assertIsNotEnabled()
    }

    @Test
    fun twoAccountsExchangeAMessageEndToEndOnPhones() = runComposeUiTest {
        val relay = FakeRelay()
        val aliceDevice = device(identity(aliceKeys), relay)
        val bobDevice = device(identity(bobKeys), relay)
        setContent {
            Row {
                Box(Modifier.requiredSize(500.dp, 740.dp).testTag("alice")) { PpfApp(aliceDevice.dependencies) }
                Box(Modifier.requiredSize(500.dp, 740.dp).testTag("bob")) { PpfApp(bobDevice.dependencies) }
            }
        }
        waitForIdle()
        fun on(app: String, tag: String, unmerged: Boolean = false) =
            onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(app)), useUnmergedTree = unmerged)
        fun hasIn(app: String, tag: String, unmerged: Boolean = false) =
            onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(app)), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
        fun back(app: String, description: String) {
            onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(app))).performClick()
            waitForIdle()
        }

        on("alice", ShellTags.navItem(Space.MESSAGES)).performClick()
        on("bob", ShellTags.navItem(Space.MESSAGES)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { hasIn("alice", PrivateTags.EMPTY) && hasIn("bob", PrivateTags.EMPTY) }

        // Alice writes to Bob by his npub; on a phone the conversation takes the whole screen.
        on("alice", MessagesTags.NEW).performClick()
        waitForIdle()
        onNodeWithTag(NewConversationTags.KEY).performTextInput(bobKeys.publicKey.npub)
        onNodeWithTag(NewConversationTags.CONFIRM).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { hasIn("alice", ConversationTags.COMPOSER) }
        assertTrue(!hasIn("alice", MessagesTags.TAB + MessagesView.PRIVATE.id))
        on("alice", ConversationTags.COMPOSER).performTextInput("[EXEMPLE] Bonjour Bob")
        on("alice", ConversationTags.SEND).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { relay.events.size == 2 }
        val sent = aliceDevice.store.messages(withBob).single()
        waitUntil(timeoutMillis = 5_000) { hasIn("alice", ConversationTags.MESSAGE + sent.id.take(8)) }
        assertTrue(!hasIn("alice", ConversationTags.PENDING + sent.id.take(8), unmerged = true))

        // Bob asks the server: the conversation appears with one unread message.
        on("bob", PrivateTags.REFRESH).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { hasIn("bob", PrivateTags.unread(withBob, bob), unmerged = true) }
        on("bob", PrivateTags.unread(withBob, bob), unmerged = true).assert(hasContentDescription("1 message non lu"))
        assertEquals(1, bobDevice.store.unreadCount())
        on("bob", PrivateTags.row(withBob, bob)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { hasIn("bob", ConversationTags.MESSAGE + sent.id.take(8)) }
        waitUntil(timeoutMillis = 5_000) { bobDevice.store.unreadCount() == 0L }

        // Bob answers; Alice goes back to her list and asks the server.
        on("bob", ConversationTags.COMPOSER).performTextInput("[EXEMPLE] Bonjour Alice")
        on("bob", ConversationTags.SEND).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { relay.events.size == 4 }
        back("alice", "Retour aux conversations")
        waitUntil(timeoutMillis = 5_000) { hasIn("alice", PrivateTags.REFRESH) }
        on("alice", PrivateTags.REFRESH).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { hasIn("alice", PrivateTags.unread(withBob, alice), unmerged = true) }
        on("alice", PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        val reply = aliceDevice.store.messages(withBob).single { it.author == bob }
        waitUntil(timeoutMillis = 5_000) { hasIn("alice", ConversationTags.MESSAGE + reply.id.take(8)) }
        waitUntil(timeoutMillis = 5_000) { aliceDevice.store.unreadCount() == 0L }

        // The relay only ever held gift wraps from one-time keys: no read receipt, no clear author.
        assertEquals(4, relay.events.size)
        assertTrue(relay.events.all { it.kind == 1059 && it.pubkey != alice && it.pubkey != bob })
    }

    @Test
    fun unreadMessagesAreCountedThenReadOnTheDeviceOnly() = runComposeUiTest {
        val relay = FakeRelay()
        runBlocking {
            val bobService = MessagingService(LocalMessagingSigner(bobKeys), MessageStore(JvmDatabase.inMemory()), relay)
            bobService.send("[EXEMPLE] Un", listOf(aliceKeys.publicKey))
            bobService.send("[EXEMPLE] Deux", listOf(aliceKeys.publicKey))
        }
        val device = device(identity(aliceKeys), relay)
        launch(device)
        openMessages()
        // Opening the screen asks the server at once.
        waitUntil(timeoutMillis = 10_000) { has(PrivateTags.unread(withBob, alice), unmerged = true) }
        onNodeWithTag(PrivateTags.unread(withBob, alice), useUnmergedTree = true).assert(hasContentDescription("2 messages non lus"))
        assertEquals(2, device.store.unreadCount())

        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.LIST) }
        waitUntil(timeoutMillis = 5_000) { device.store.unreadCount() == 0L }
        waitUntil(timeoutMillis = 5_000) { !has(PrivateTags.unread(withBob, alice), unmerged = true) }
        // Desktop: the list stays next to the open conversation, its row selected.
        assertTrue(has(PrivateTags.LIST))
        // Reading sent nothing.
        assertEquals(4, relay.events.size)
    }

    @Test
    fun theThreadIsGroupedByDayWithTheLocalHour() = runComposeUiTest {
        val device = device(identity(aliceKeys))
        val now = currentEpochSeconds()
        val older = now - 10 * 86_400
        val yesterday = now - 86_400
        val today = now - 60
        listOf(fromBob("[EXEMPLE] Il y a dix jours", older), fromBob("[EXEMPLE] Hier", yesterday), fromBob("[EXEMPLE] Tout à l’heure", today))
            .forEach { device.store.store(it, read = true) }
        launch(device)
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.row(withBob, alice)) }
        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.LIST) }
        for (at in listOf(older, yesterday, today)) {
            assertTrue(has(ConversationTags.DAY + LocalMoment.of(at).day), "a separator for the day of $at")
        }
        assertTrue(sees("Aujourd’hui"))
        assertTrue(sees("Hier"))
        assertTrue(sees(LocalMoment.of(older).date.toFrench()))
        assertTrue(sees(LocalMoment.of(today).hourLabel))
    }

    @Test
    fun onAPhoneTheConversationTakesTheWholeScreenWithABackButton() = runComposeUiTest {
        val device = device(identity(aliceKeys))
        device.store.store(fromBob("[EXEMPLE] Salut", currentEpochSeconds() - 120))
        launch(device, width = 360, height = 740)
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.row(withBob, alice)) }
        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.SCREEN) }
        assertTrue(!has(MessagesTags.TAB + MessagesView.PRIVATE.id))
        assertTrue(!has(PrivateTags.LIST))
        onNode(hasContentDescription("Retour aux conversations")).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.LIST) }
        assertTrue(has(MessagesTags.TAB + MessagesView.PRIVATE.id))
        assertTrue(!has(ConversationTags.SCREEN))
    }

    @Test
    fun theOpenConversationSurvivesAVisitToAnotherSpaceAndExplainsItsEncryption() = runComposeUiTest {
        val device = device(identity(aliceKeys))
        device.store.store(fromBob("[EXEMPLE] Salut", currentEpochSeconds() - 120))
        launch(device)
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.row(withBob, alice)) }
        onNodeWithTag(PrivateTags.row(withBob, alice)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.SCREEN) }

        onNodeWithTag(ShellTags.navItem(Space.FIL)).performClick()
        waitForIdle()
        assertTrue(!has(ConversationTags.SCREEN))
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(ConversationTags.SCREEN) }

        onNodeWithTag(ConversationTags.INFO).performClick()
        waitForIdle()
        onNodeWithTag(ConversationTags.ENCRYPTION_DIALOG).assertExists()
        assertTrue(sees("Limites connues"))
        assertTrue(sees("QR code"))
        onNodeWithTag(ConversationTags.ENCRYPTION_CLOSE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag(ConversationTags.ENCRYPTION_DIALOG).assertDoesNotExist()
    }

    @Test
    fun theScreenAsksTheServerAgainWhileItIsShown() = runComposeUiTest {
        val relay = FakeRelay()
        launch(device(identity(aliceKeys), relay))
        openMessages()
        waitUntil(timeoutMillis = 10_000) { relay.calls.count { it == "tags" } >= 1 && has(PrivateTags.EMPTY) }
        val before = relay.calls.count { it == "tags" }
        mainClock.advanceTimeBy(MessagingSession.REFRESH_PERIOD_SECONDS * 1_000L + 1_000)
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { relay.calls.count { it == "tags" } > before }
    }

    @Test
    fun signingOutForgetsTheConversationsOnTheDevice() = runComposeUiTest {
        val device = device(identity(aliceKeys))
        device.store.store(fromBob("[EXEMPLE] À oublier", currentEpochSeconds() - 120))
        launch(device)
        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.row(withBob, alice)) }

        onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
        waitForIdle()
        onNodeWithTag(AccountTags.SIGN_OUT).performScrollTo().performClick()
        waitForIdle()
        assertTrue(sees("vos conversations privées enregistrées sur l’appareil"))
        onNodeWithTag(AccountTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { has(AccountTags.CREATE) }
        waitUntil(timeoutMillis = 5_000) { device.store.count() == 0L }

        openMessages()
        waitUntil(timeoutMillis = 5_000) { has(PrivateTags.INVITATION) }
        assertTrue(!has(PrivateTags.row(withBob, alice)))
        assertTrue(!sees("À oublier"))
    }
}
