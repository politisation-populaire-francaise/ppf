package org.politisationpopulairefrancaise.core.agir.timer

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.IncomingRumors
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.MessagingService
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.signing.EventKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hook of D-088 in the private messaging (Communauté's MessagingService): the timer rumors go to
 * the handler registered for their kind, never into the conversations, and a pull that meets one
 * nobody can take keeps its cursor, so that nothing is lost before the screens start.
 */
class TimerMessagingTest {
    private val server = InMemoryPublicationPort()
    private var now = 1_800_000_000L
    private val alice = IdentityKeys.generate()
    private val bob = IdentityKeys.generate()
    private val bobStore = MessageStore(JvmDatabase.inMemory(), clock = { now })
    private val registry = IncomingRumors(expected = setOf(EventKinds.TIMER))
    private val bobMessaging = MessagingService(LocalMessagingSigner(bob), bobStore, server, clock = { now }, others = registry)

    private suspend fun armForBob() {
        val service = TimerService(TimerStore(InMemoryTimerStorage()), clock = { now }, iterations = 2)
        val transport = MessagingTimerTransport(LocalMessagingSigner(alice), server, clock = { now })
        val result = service.arm(
            ArmRequest(listOf(TimerRecipient(bob.publicKey.hex)), now + 3_600, "[EXEMPLE] Alerte", null, "73914", "73914", "2580"),
            transport,
        )
        assertTrue(result is ArmResult.Armed)
    }

    @Test
    fun aTimerRumorWaitsForItsHandlerAndNeverBecomesAMessage() = runTest {
        armForBob()
        // A chat message too, to check that nothing changes for the conversations.
        MessagingService(LocalMessagingSigner(alice), MessageStore(JvmDatabase.inMemory()), server, clock = { now }, others = registry)
            .send("[EXEMPLE] Bonjour", listOf(bob.publicKey))

        // No handler yet (a wake-up before any screen): the rumor is held, the cursor does not move.
        val first = bobMessaging.pull()
        assertEquals(1, first.received.stored)
        assertEquals(1, first.received.others.size)
        assertNull(bobStore.lastPullAt())
        assertEquals(1, bobStore.conversations().size)

        // Once the handler is registered, the next pull fetches it again and hands it over.
        val inbox = TimerInbox(TimerStore(InMemoryTimerStorage()), clock = { now })
        val received = mutableListOf<Rumor>()
        val remove = registry.register(EventKinds.TIMER) { rumor, recipient ->
            received += rumor
            inbox.accept(rumor, recipient)
        }
        val second = bobMessaging.pull()
        assertEquals(1, second.received.duplicates)
        assertEquals(1, received.size)
        assertNotNull(bobStore.lastPullAt())
        assertEquals(EventKinds.TIMER, received.single().kind)
        // Still only the chat message in the conversations.
        assertEquals(1, bobStore.count())
        remove()
    }

    @Test
    fun aFailingHandlerHoldsTheRumorAndOtherKindsAreIgnoredAsBefore() = runTest {
        armForBob()
        registry.register(EventKinds.TIMER) { _, _ -> error("storage full") }
        bobMessaging.pull()
        assertNull(bobStore.lastPullAt())

        // A registry that expects nothing ignores the timer rumors, as before D-088.
        val plain = MessagingService(LocalMessagingSigner(bob), MessageStore(JvmDatabase.inMemory(), clock = { now }), server, clock = { now }, others = IncomingRumors())
        val report = plain.pull()
        assertEquals(1, report.received.ignored)
        assertTrue(report.received.others.isEmpty())
    }

    @Test
    fun chatMessagesCannotBeTakenByAHandler() {
        assertFailsWith<IllegalArgumentException> { registry.register(EventKinds.CHAT_MESSAGE) { _, _ -> } }
    }
}
