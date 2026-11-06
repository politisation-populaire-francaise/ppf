package org.politisationpopulairefrancaise.ui.messages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The state holder of the private conversations, on a real local database: what it shows without
 * a database or an account, sending offline then delivering when the relay answers, two accounts
 * exchanging through one relay, read marks kept on the device, and forgetting everything when the
 * keys are erased.
 */
class MessagingSessionTest {

    private val alice = IdentityKeys.generate()
    private val bob = IdentityKeys.generate()
    private val carol = IdentityKeys.generate()

    private fun CoroutineScope.session(store: MessageStore? = MessageStore(JvmDatabase.inMemory()), remote: RemoteEvents? = FakeRelay()) =
        MessagingSession(store, remote, this)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    private suspend fun MessagingSession.awaitSettled() = awaitUntil { status != MessagingStatus.Loading && !refreshing }

    @Test
    fun withoutDatabaseNothingIsAvailable() = runBlocking {
        val session = session(store = null)
        assertEquals(MessagingStatus.Unavailable, session.status)
        session.setAccount(LocalMessagingSigner(alice))
        assertEquals(MessagingStatus.Unavailable, session.status)
        assertFalse(session.canWrite)
    }

    @Test
    fun withoutAccountItWaitsForOneAndAsksNothing() = runBlocking {
        val relay = FakeRelay()
        val session = session(remote = relay)
        session.setAccount(null)
        session.refresh()
        assertEquals(MessagingStatus.NoAccount, session.status)
        assertFalse(session.canWrite)
        session.openWith(listOf(bob.publicKey.hex))
        assertNull(session.open)
        assertTrue(relay.calls.isEmpty())
    }

    @Test
    fun aMessageWrittenOfflineWaitsOnTheDeviceThenLeavesWhenTheRelayAnswers() = runBlocking {
        val relay = FakeRelay().apply { online = false }
        val store = MessageStore(JvmDatabase.inMemory())
        val session = session(store, relay)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        assertEquals(MessagingStatus.Unreachable, session.status)

        session.openWith(listOf(bob.publicKey.hex))
        assertTrue(session.send("[EXEMPLE] Bonjour, écrit hors ligne"))
        val message = session.messages.single()
        assertEquals(setOf(message.id), session.pending)
        assertTrue(session.conversations.single().pending)
        assertEquals(2, store.outgoingCount())
        assertTrue(relay.events.isEmpty())

        // The relay answers again: the pending deliveries leave first, then the pull runs.
        relay.online = true
        relay.calls.clear()
        session.refresh()
        awaitUntil { session.pending.isEmpty() && !session.refreshing }
        assertEquals(listOf("publish", "publish"), relay.calls.take(2))
        assertEquals("tags", relay.calls.last())
        assertTrue(session.status is MessagingStatus.Synced)
        assertFalse(session.conversations.single().pending)
        assertEquals(0, store.outgoingCount())
        // One gift wrap per participant, sender included; the relay only ever sees kind 1059 from one-time keys.
        assertEquals(2, relay.events.size)
        assertTrue(relay.events.all { it.kind == EventKinds.GIFT_WRAP && it.pubkey != alice.publicKey.hex })
        assertEquals(setOf(alice.publicKey.hex, bob.publicKey.hex), relay.events.map { it.tagValues("p").single() }.toSet())
    }

    @Test
    fun withoutServerEveryMessageStaysOnTheDevice() = runBlocking {
        val store = MessageStore(JvmDatabase.inMemory())
        val session = session(store, remote = null)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        assertEquals(MessagingStatus.Offline, session.status)
        assertFalse(session.hasServer)
        session.openWith(listOf(bob.publicKey.hex))
        assertTrue(session.send("[EXEMPLE] Gardé ici"))
        session.refresh()
        awaitUntil { session.conversations.isNotEmpty() }
        assertEquals(1, session.pending.size)
        assertEquals(2, store.outgoingCount())
    }

    @Test
    fun theDeviceAsksOnlyForTheWrapsAddressedToTheUser() = runBlocking {
        val relay = FakeRelay()
        val session = session(remote = relay)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        val (tags, filter) = relay.tagRequests.single()
        assertEquals(listOf(TagFilter("p", alice.publicKey.hex)), tags)
        assertEquals(listOf(EventKinds.GIFT_WRAP), filter.kinds)
        assertTrue(filter.authors.isEmpty())
        // The first pull of a device fetches the whole history.
        assertNull(filter.since)
    }

    @Test
    fun twoAccountsExchangeThroughTheRelayAndReadMarksStayOnTheDevice() = runBlocking {
        val relay = FakeRelay()
        val aliceStore = MessageStore(JvmDatabase.inMemory())
        val bobStore = MessageStore(JvmDatabase.inMemory())
        val aliceSide = session(aliceStore, relay)
        val bobSide = session(bobStore, relay)
        aliceSide.setAccount(LocalMessagingSigner(alice))
        bobSide.setAccount(LocalMessagingSigner(bob))
        aliceSide.awaitSettled()
        bobSide.awaitSettled()

        aliceSide.openWith(listOf(bob.publicKey.hex), subject = "[EXEMPLE] Réunion")
        assertTrue(aliceSide.send("[EXEMPLE] Bonjour Bob"))
        awaitUntil { aliceSide.pending.isEmpty() }

        bobSide.refresh()
        awaitUntil { bobSide.conversations.isNotEmpty() && !bobSide.refreshing }
        val received = bobSide.conversations.single()
        assertEquals(1, received.conversation.unreadCount)
        assertEquals(1, bobSide.unreadTotal)
        assertEquals("[EXEMPLE] Bonjour Bob", received.lastMessage?.text)
        assertEquals("[EXEMPLE] Réunion", received.conversation.subject)
        assertEquals(listOf(alice.publicKey.hex, bob.publicKey.hex).sorted(), received.conversation.participants)

        // Opening shows the thread; what the screen shows is marked read, on Bob's device only.
        val wrapsBefore = relay.events.size
        bobSide.open(received.conversation)
        awaitUntil { bobSide.messages.size == 1 }
        assertEquals("[EXEMPLE] Réunion", bobSide.open?.subject)
        bobSide.markOpenRead()
        awaitUntil { bobSide.unreadTotal == 0L }
        assertEquals(0, bobStore.unreadCount())
        assertEquals(wrapsBefore, relay.events.size)

        assertTrue(bobSide.send("[EXEMPLE] Bonjour Alice"))
        awaitUntil { bobSide.pending.isEmpty() }
        aliceSide.refresh()
        awaitUntil { aliceSide.messages.size == 2 && !aliceSide.refreshing }
        // Both were written within the same second here: their order in the thread is not the point.
        assertEquals(setOf("[EXEMPLE] Bonjour Bob", "[EXEMPLE] Bonjour Alice"), aliceSide.messages.map { it.text }.toSet())
        assertEquals(1, aliceSide.unreadTotal)
        // Alice's own copy came back from the relay too, already read; nothing was stored twice.
        assertEquals(2, aliceStore.count())
    }

    @Test
    fun aSmallGroupReachesEveryParticipant() = runBlocking {
        val relay = FakeRelay()
        val aliceSide = session(remote = relay)
        val bobSide = session(remote = relay)
        val carolSide = session(remote = relay)
        aliceSide.setAccount(LocalMessagingSigner(alice))
        bobSide.setAccount(LocalMessagingSigner(bob))
        carolSide.setAccount(LocalMessagingSigner(carol))
        listOf(aliceSide, bobSide, carolSide).forEach { it.awaitSettled() }

        aliceSide.openWith(listOf(bob.publicKey.hex, carol.publicKey.hex))
        assertTrue(aliceSide.send("[EXEMPLE] Bonjour à vous deux"))
        awaitUntil { aliceSide.pending.isEmpty() }
        assertEquals(3, relay.events.size)
        for (side in listOf(bobSide, carolSide)) {
            side.refresh()
            awaitUntil { side.conversations.isNotEmpty() && !side.refreshing }
            assertEquals(3, side.conversations.single().conversation.participants.size)
        }
    }

    @Test
    fun aKeyOutsideTheCurveOrOnesOwnIsRefusedBeforeAnythingStarts() = runBlocking {
        val session = session()
        session.setAccount(LocalMessagingSigner(alice))
        assertTrue(session.canEncryptFor(bob.publicKey.hex))
        assertFalse(session.canEncryptFor(alice.publicKey.hex))
        // Larger than the field prime: no point of secp256k1 has this x coordinate.
        assertFalse(session.canEncryptFor("f".repeat(64)))
        assertFalse(session.canEncryptFor("not a key"))
    }

    @Test
    fun aTextTooLongIsRefusedWithItsReason() = runBlocking {
        val store = MessageStore(JvmDatabase.inMemory())
        val session = session(store)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        session.openWith(listOf(bob.publicKey.hex))
        assertFalse(session.send("a".repeat(ChatMessage.MAX_TEXT_BYTES + 1)))
        assertEquals(SendError.TOO_LONG, session.sendError)
        assertEquals(0, store.count())
    }

    @Test
    fun forgettingErasesTheConversationsAndTheCursorAndTheServerGivesThemBack() = runBlocking {
        val relay = FakeRelay()
        val store = MessageStore(JvmDatabase.inMemory())
        val session = session(store, relay)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        session.openWith(listOf(bob.publicKey.hex))
        assertTrue(session.send("[EXEMPLE] À garder sur le serveur"))
        session.refresh()
        awaitUntil { session.pending.isEmpty() && !session.refreshing && store.lastPullAt() != null }

        session.forgetAll()
        assertNull(session.myKey)
        assertNull(session.open)
        assertTrue(session.conversations.isEmpty())
        assertEquals(MessagingStatus.NoAccount, session.status)
        awaitUntil { store.count() == 0L }
        assertNull(store.lastPullAt())
        assertEquals(0, store.outgoingCount())

        // The same account restored: its history comes back from the relay's gift wraps.
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        awaitUntil { session.conversations.size == 1 }
        assertEquals("[EXEMPLE] À garder sur le serveur", session.conversations.single().lastMessage?.text)
    }

    @Test
    fun anotherAccountNeverSeesThePreviousOnesConversations() = runBlocking {
        val store = MessageStore(JvmDatabase.inMemory())
        store.store(ChatMessage.compose(alice.publicKey, listOf(bob.publicKey), "[EXEMPLE] Entre Alice et Bob", createdAt = 1_700_000_000), read = true)
        store.setLastPullAt(1_700_000_000)
        val relay = FakeRelay()
        val session = session(store, relay)
        session.setAccount(LocalMessagingSigner(carol))
        session.awaitSettled()
        assertTrue(session.conversations.isEmpty())
        assertEquals(0, store.count())
        // The cursor left by the other identity went too: Carol's first pull asks for her whole history.
        assertNull(relay.tagRequests.single().second.since)
    }

    @Test
    fun theSameKeyAgainChangesNothing() = runBlocking {
        val relay = FakeRelay()
        val session = session(remote = relay)
        session.setAccount(LocalMessagingSigner(alice))
        session.awaitSettled()
        session.openWith(listOf(bob.publicKey.hex))
        val requests = relay.calls.size
        session.setAccount(LocalMessagingSigner(alice))
        assertEquals(bob.publicKey.hex, session.open?.others(session.myKey)?.single())
        assertEquals(requests, relay.calls.size)
    }
}
