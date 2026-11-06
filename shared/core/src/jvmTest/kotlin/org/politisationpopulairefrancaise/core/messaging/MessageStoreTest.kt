package org.politisationpopulairefrancaise.core.messaging

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageStoreTest {

    private val store = MessageStore(JvmDatabase.inMemory(), clock = { 1_700_000_000 })
    private val alice = IdentityKeys.generate().publicKey
    private val bob = IdentityKeys.generate().publicKey
    private val carol = IdentityKeys.generate().publicKey

    @Test
    fun storesOnceAndCreatesTheConversation() {
        val message = ChatMessage.compose(alice, listOf(bob), "[EXEMPLE] salut", createdAt = 100)
        assertEquals(MessageStoreOutcome.STORED, store.store(message))
        assertEquals(MessageStoreOutcome.DUPLICATE, store.store(message))
        assertEquals(message, store.find(message.id))
        assertNull(store.find("0".repeat(64)))
        assertEquals(1, store.count())

        val conversation = store.conversation(message.conversationId)!!
        assertEquals(message.participants, conversation.participants)
        assertEquals(100, conversation.lastMessageAt)
        assertEquals(1, conversation.unreadCount)
        assertNull(conversation.subject)
    }

    @Test
    fun conversationsAreOrderedByActivityWithUnreadCounts() {
        val withBob1 = ChatMessage.compose(bob, listOf(alice), "1", createdAt = 100)
        val withBob2 = ChatMessage.compose(alice, listOf(bob), "2", createdAt = 300)
        val group = ChatMessage.compose(carol, listOf(alice, bob), "3", createdAt = 200)
        store.store(withBob1)
        store.store(withBob2, read = true)
        store.store(group)

        val conversations = store.conversations()
        assertEquals(listOf(withBob2.conversationId, group.conversationId), conversations.map { it.id })
        assertEquals(listOf(1L, 1L), conversations.map { it.unreadCount })
        assertEquals(300, conversations[0].lastMessageAt)
        assertEquals(2, store.unreadCount())

        store.markRead(withBob1.conversationId)
        assertEquals(0, store.conversation(withBob1.conversationId)!!.unreadCount)
        assertEquals(1, store.unreadCount())
        store.markMessageRead(group.id)
        assertEquals(0, store.unreadCount())
        assertEquals(listOf(withBob2.conversationId), store.conversations(limit = 1).map { it.id })
    }

    @Test
    fun messagesComeOldestFirstWithPaging() {
        val messages = (1..5).map { ChatMessage.compose(alice, listOf(bob), "m$it", createdAt = 100L * it) }
        messages.shuffled().forEach { store.store(it) }
        val conversationId = messages[0].conversationId
        assertEquals(messages, store.messages(conversationId))
        assertEquals(messages.takeLast(2), store.messages(conversationId, limit = 2))
        assertEquals(messages.take(3).takeLast(2), store.messages(conversationId, limit = 2, before = 400))
        assertEquals(emptyList(), store.messages("nope"))
        // An older message arriving late does not move the conversation back in time.
        store.store(ChatMessage.compose(bob, listOf(alice), "late", createdAt = 50))
        assertEquals(500, store.conversation(conversationId)!!.lastMessageAt)
    }

    @Test
    fun theNewestSubjectWins() {
        val first = ChatMessage.compose(alice, listOf(bob), "a", createdAt = 100, subject = "Premier")
        val newer = ChatMessage.compose(bob, listOf(alice), "b", createdAt = 300, subject = "Second")
        val older = ChatMessage.compose(bob, listOf(alice), "c", createdAt = 200, subject = "Ancien")
        val noSubject = ChatMessage.compose(alice, listOf(bob), "d", createdAt = 400)
        store.store(first)
        assertEquals("Premier", store.conversation(first.conversationId)!!.subject)
        store.store(newer)
        store.store(older)
        store.store(noSubject)
        assertEquals("Second", store.conversation(first.conversationId)!!.subject)
        assertEquals(400, store.conversation(first.conversationId)!!.lastMessageAt)
    }

    @Test
    fun deletingAConversationRemovesItsMessagesOnly() {
        val withBob = ChatMessage.compose(alice, listOf(bob), "a", createdAt = 100)
        val withCarol = ChatMessage.compose(alice, listOf(carol), "b", createdAt = 100)
        store.store(withBob)
        store.store(withCarol)
        store.deleteConversation(withBob.conversationId)
        assertNull(store.find(withBob.id))
        assertNull(store.conversation(withBob.conversationId))
        assertEquals(withCarol, store.find(withCarol.id))
        assertEquals(1, store.count())
    }

    @Test
    fun outgoingWrapsAndPullCursorArePersisted() = runTest {
        val sender = LocalMessagingSigner(IdentityKeys.generate())
        val message = ChatMessage.compose(sender.publicKey, listOf(bob), "x", createdAt = 100)
        val wrap1 = GiftWrap.wrapFor(message.toRumor(), sender, bob, now = 1_700_000_000)
        val wrap2 = GiftWrap.wrapFor(message.toRumor(), sender, sender.publicKey, now = 1_700_000_000)
        assertEquals(0, store.outgoingCount())
        store.queueOutgoing(wrap1, message.id)
        store.queueOutgoing(wrap2, message.id)
        // Queued in the same second: both come back, by id.
        assertEquals(setOf(OutgoingWrap(wrap1, message.id), OutgoingWrap(wrap2, message.id)), store.outgoing().toSet())
        assertEquals(store.outgoing().map { it.wrap.id }.sorted(), store.outgoing().map { it.wrap.id })
        store.removeOutgoing(wrap1.id)
        assertEquals(listOf(OutgoingWrap(wrap2, message.id)), store.outgoing())
        assertEquals(1, store.outgoingCount())

        assertNull(store.lastPullAt())
        store.setLastPullAt(123)
        store.setLastPullAt(456)
        assertEquals(456, store.lastPullAt())
    }
}
