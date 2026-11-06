package org.politisationpopulairefrancaise.core.messaging

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChatMessageTest {

    private val alice = IdentityKeys.generate().publicKey
    private val bob = IdentityKeys.generate().publicKey
    private val carol = IdentityKeys.generate().publicKey
    private val now = 1_700_000_000L

    @Test
    fun composeBuildsTheRumorOfNip17() {
        val message = ChatMessage.compose(alice, listOf(bob, carol, alice), "[EXEMPLE] Bonjour à tous", now, subject = "Réunion", replyTo = "a".repeat(64))
        assertEquals(alice.hex, message.author)
        assertEquals(listOf(alice.hex, bob.hex, carol.hex).sorted(), message.participants)
        assertEquals(listOf(bob.hex, carol.hex).sorted(), message.recipients)
        assertEquals(message.participants.joinToString(","), message.conversationId)

        val rumor = message.toRumor()
        assertEquals(message.id, rumor.id)
        assertEquals(EventKinds.CHAT_MESSAGE, rumor.kind)
        assertEquals(alice.hex, rumor.pubkey)
        assertEquals(now, rumor.createdAt)
        assertEquals("[EXEMPLE] Bonjour à tous", rumor.content)
        assertEquals(message.recipients, rumor.tagValues("p"))
        assertEquals("a".repeat(64), rumor.firstTagValue("e"))
        assertEquals("Réunion", rumor.firstTagValue("subject"))
        assertEquals(message, ChatMessage.fromRumor(rumor))
    }

    @Test
    fun everyParticipantDerivesTheSameConversation() {
        val fromAlice = ChatMessage.compose(alice, listOf(bob, carol), "a", now)
        val fromCarol = ChatMessage.compose(carol, listOf(alice, bob), "b", now)
        assertEquals(fromAlice.conversationId, fromCarol.conversationId)
        assertEquals(fromAlice.participants, fromCarol.participants)
        val pair = ChatMessage.compose(bob, listOf(alice), "c", now)
        assertEquals(Conversation.idOf(listOf(alice.hex, bob.hex)), pair.conversationId)
        assertEquals(pair.participants, Conversation.participantsOf(pair.conversationId))
    }

    @Test
    fun composeRefusesNonsense() {
        assertFailsWith<IllegalArgumentException> { ChatMessage.compose(alice, listOf(bob), "", now) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.compose(alice, emptyList(), "x", now) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.compose(alice, listOf(alice), "x", now) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.compose(alice, listOf(bob), "x", now, replyTo = "nope") }
        assertNull(ChatMessage.compose(alice, listOf(bob), "x", now, subject = "  ").subject)
    }

    @Test
    fun receivedRumorsAreCheckedBeforeBeingTrusted() {
        val relayHinted = Rumor.of(UnsignedEvent(bob.hex, now, EventKinds.CHAT_MESSAGE, listOf(listOf("p", alice.hex, "wss://relay.example"), listOf("e", "b".repeat(64), "", "reply")), "salut"))
        val message = ChatMessage.fromRumor(relayHinted)
        assertEquals(listOf(alice.hex, bob.hex).sorted(), message.participants)
        assertEquals("b".repeat(64), message.replyTo)
        assertEquals("salut", message.text)

        assertFailsWith<IllegalArgumentException> { ChatMessage.fromRumor(relayHinted.copy(kind = EventKinds.TEXT_NOTE)) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.fromRumor(relayHinted.copy(tags = emptyList())) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.fromRumor(relayHinted.copy(tags = listOf(listOf("p", "not-a-key")))) }
        assertFailsWith<IllegalArgumentException> { ChatMessage.fromRumor(relayHinted.copy(tags = listOf(listOf("p")))) }
        // A malformed reply reference is ignored rather than rejected: the text is still readable.
        assertNull(ChatMessage.fromRumor(relayHinted.copy(tags = listOf(listOf("p", alice.hex), listOf("e", "x")))).replyTo)
    }

    @Test
    fun conversationIdsAreSortedUniqueHexKeys() {
        assertEquals("${alice.hex},${bob.hex}".split(",").sorted().joinToString(","), Conversation.idOf(listOf(bob.hex, alice.hex, bob.hex)))
        assertFailsWith<IllegalArgumentException> { Conversation.idOf(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Conversation.idOf(listOf(alice.hex.uppercase())) }
    }
}
