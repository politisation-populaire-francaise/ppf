package org.politisationpopulairefrancaise.core.messaging

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagingServiceTest {

    /** The server as the application sees it: an in-memory store that can be switched off. */
    private class FakeServer : RemoteEvents {
        private val port = InMemoryPublicationPort()
        var online = true
        val tagFilters = mutableListOf<List<TagFilter>>()
        val events: List<SignedEvent> get() = port.events

        override suspend fun publish(event: SignedEvent): PublicationResult =
            if (online) port.publish(event) else PublicationResult.Unavailable()

        override suspend fun fetch(filter: RemoteFilter): FetchResult =
            if (online) port.fetch(filter) else FetchResult.Unavailable()

        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
            tagFilters += tags
            return if (online) port.fetchByTags(tags, filter) else FetchResult.Unavailable()
        }
    }

    private val server = FakeServer()
    private var now = 1_700_000_000L

    private inner class Participant {
        val keys = IdentityKeys.generate()
        val signer = LocalMessagingSigner(keys)
        val store = MessageStore(JvmDatabase.inMemory(), clock = { now })
        val service = MessagingService(signer, store, server, clock = { now })
        val hex get() = keys.publicKey.hex
    }

    private val alice = Participant()
    private val bob = Participant()
    private val carol = Participant()

    @Test
    fun aMessageTravelsFromAliceToBobAndBack() = runTest {
        val sent = alice.service.send("[EXEMPLE] Bonjour Bob", listOf(bob.keys.publicKey))
        assertEquals(SendReport(sent.message, delivered = 2), sent)
        assertEquals(0, alice.store.outgoingCount())
        assertEquals(listOf(sent.message), alice.store.messages(sent.message.conversationId))
        assertEquals(0, alice.store.unreadCount())

        // The server only holds gift wraps: random authors, the recipient tag, nothing readable.
        assertEquals(2, server.events.size)
        assertTrue(server.events.all { it.kind == EventKinds.GIFT_WRAP })
        assertTrue(server.events.none { it.pubkey == alice.hex || it.pubkey == bob.hex })
        assertEquals(setOf(alice.hex, bob.hex), server.events.map { it.firstTagValue("p") }.toSet())
        assertTrue(server.events.none { it.content.contains("Bonjour") })

        now += 10
        val pulled = bob.service.pull()
        assertEquals(PullReport(fetched = 1, received = ReceiveReport(stored = 1)), pulled)
        assertEquals(listOf(listOf(TagFilter("p", bob.hex))), server.tagFilters)
        val conversation = bob.store.conversations().single()
        assertEquals(sent.message.conversationId, conversation.id)
        assertEquals(1, conversation.unreadCount)
        assertEquals(listOf(sent.message), bob.store.messages(conversation.id))

        // Pulling again stores nothing twice; the cursor asks from before the two-day jitter window.
        assertEquals(PullReport(fetched = 1, received = ReceiveReport(duplicates = 1)), bob.service.pull())

        now += 10
        val reply = bob.service.send("[EXEMPLE] Salut Alice", listOf(alice.keys.publicKey), replyTo = sent.message.id)
        // Alice fetches her own copy of the first message (already stored) and Bob's reply.
        assertEquals(PullReport(fetched = 2, received = ReceiveReport(stored = 1, duplicates = 1)), alice.service.pull())
        assertEquals(listOf(sent.message, reply.message), alice.store.messages(conversation.id))
        assertEquals(sent.message.id, alice.store.messages(conversation.id).last().replyTo)
        assertEquals(1, alice.store.conversation(conversation.id)!!.unreadCount)
    }

    @Test
    fun aGroupOfThreeSharesOneConversation() = runTest {
        val sent = alice.service.send("[EXEMPLE] Réunion demain", listOf(bob.keys.publicKey, carol.keys.publicKey), subject = "Réunion")
        assertEquals(3, sent.delivered)
        assertEquals(3, server.events.size)

        for (member in listOf(bob, carol)) {
            assertEquals(ReceiveReport(stored = 1), member.service.pull().received)
            val conversation = member.store.conversations().single()
            assertEquals(sent.message.conversationId, conversation.id)
            assertEquals(listOf(alice.hex, bob.hex, carol.hex).sorted(), conversation.participants)
            assertEquals("Réunion", conversation.subject)
        }
        // Carol answers everyone; Alice and Bob both receive it in the same conversation.
        val answer = carol.service.send("[EXEMPLE] Je serai là", listOf(alice.keys.publicKey, bob.keys.publicKey))
        assertEquals(sent.message.conversationId, answer.message.conversationId)
        alice.service.pull()
        bob.service.pull()
        assertEquals(listOf(sent.message, answer.message), alice.store.messages(sent.message.conversationId))
        assertEquals(listOf(sent.message, answer.message), bob.store.messages(sent.message.conversationId))
        // Alice does not see a message Carol sends to Bob alone.
        carol.service.send("[EXEMPLE] Entre nous", listOf(bob.keys.publicKey))
        assertEquals(ReceiveReport(duplicates = 2), alice.service.pull().received)
        assertEquals(1, alice.store.conversations().size)
    }

    @Test
    fun alteredOrForeignWrapsAreIgnoredWithoutException() = runTest {
        val sent = alice.service.send("[EXEMPLE] secret de polichinelle", listOf(bob.keys.publicKey))
        val forBob = server.events.single { it.firstTagValue("p") == bob.hex }
        val forAlice = server.events.single { it.firstTagValue("p") == alice.hex }

        val tampered = forBob.copy(content = forBob.content.dropLast(1) + "A")
        val readdressed = forAlice.copy(tags = listOf(listOf("p", bob.hex)))
        val wrongKind = forBob.copy(kind = EventKinds.TEXT_NOTE)
        val note = alice.signer.sign(UnsignedEvent.create(alice.keys.publicKey, EventKinds.TEXT_NOTE, now, emptyList(), "public"))

        val report = bob.service.receive(listOf(tampered, readdressed, wrongKind, note, forAlice, forBob, forBob))
        assertEquals(ReceiveReport(stored = 1, duplicates = 1, ignored = 5), report)
        assertEquals(listOf(sent.message), bob.store.messages(sent.message.conversationId))
    }

    @Test
    fun aRumorThatDoesNotNameTheRecipientIsIgnored() = runTest {
        // Mallory wraps for Bob a message whose rumor only names Carol.
        val rumor = ChatMessage.compose(alice.keys.publicKey, listOf(carol.keys.publicKey), "[EXEMPLE] pas pour toi", now).toRumor()
        val wrap = GiftWrap.wrapFor(rumor, alice.signer, bob.keys.publicKey, now)
        assertEquals(ReceiveReport(ignored = 1), bob.service.receive(listOf(wrap)))
        assertEquals(0, bob.store.count())
    }

    @Test
    fun wrapsWaitInTheOutboxWhileTheServerIsDown() = runTest {
        server.online = false
        val sent = alice.service.send("[EXEMPLE] hors ligne", listOf(bob.keys.publicKey))
        assertEquals(SendReport(sent.message, queued = 2), sent)
        assertEquals(2, alice.store.outgoingCount())
        assertEquals(listOf(sent.message), alice.store.messages(sent.message.conversationId))
        assertEquals(PullReport(reachable = false, complete = false), bob.service.pull())
        assertEquals(FlushReport(remaining = 2, reachable = false), alice.service.flushOutgoing())

        server.online = true
        assertEquals(FlushReport(delivered = 2), alice.service.flushOutgoing())
        assertEquals(0, alice.store.outgoingCount())
        assertEquals(ReceiveReport(stored = 1), bob.service.pull().received)
        assertEquals(FlushReport(), alice.service.flushOutgoing())
    }

    @Test
    fun pullPagesThroughTheServerLimit() = runTest {
        repeat(5) { now += 1; alice.service.send("[EXEMPLE] message $it", listOf(bob.keys.publicKey)) }
        val report = bob.service.pull(pageSize = 2)
        assertEquals(5, report.received.stored)
        assertTrue(report.fetched >= 5)
        assertEquals(5, bob.store.messages(bob.store.conversations().single().id).size)
    }
}
