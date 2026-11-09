package org.politisationpopulairefrancaise.core.agir.timer

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.security.UnlockDelays
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The person's timer (AG-55, D-088) from end to end: deposits sealed for each recipient, the
 * « je vais bien » code, the duress code, « Repousser », the network down, and what each
 * recipient's device makes of the envelopes. Few iterations, so that the tests stay fast.
 */
class TimerServiceTest {

    /** The server as the application sees it: an in-memory store that can be switched off. */
    private class FakeServer : RemoteEvents {
        private val port = InMemoryPublicationPort()
        var online = true
        val events: List<SignedEvent> get() = port.events

        override suspend fun publish(event: SignedEvent): PublicationResult =
            if (online) port.publish(event) else PublicationResult.Unavailable()

        override suspend fun fetch(filter: RemoteFilter): FetchResult =
            if (online) port.fetch(filter) else FetchResult.Unavailable()

        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult =
            if (online) port.fetchByTags(tags, filter) else FetchResult.Unavailable()
    }

    private val server = FakeServer()
    private var now = 1_800_000_000L

    private val alice = IdentityKeys.generate()
    private val aliceStorage = InMemoryTimerStorage()
    private val aliceStore = TimerStore(aliceStorage)
    private val service = TimerService(aliceStore, clock = { now }, iterations = 3)
    private val transport = MessagingTimerTransport(LocalMessagingSigner(alice), server, clock = { now })

    /** A recipient: its keys and its own device. */
    private inner class Relative {
        val keys = IdentityKeys.generate()
        val signer = LocalMessagingSigner(keys)
        val store = TimerStore(InMemoryTimerStorage())
        val inbox = TimerInbox(store, clock = { now })
        val hex: String get() = keys.publicKey.hex

        /** The rumors of the envelopes addressed to this relative on the server, oldest delivery first. */
        fun rumors(): List<Rumor> = server.events.filter { it.firstTagValue("p") == hex }.map { GiftWrap.unwrap(it, signer) }

        /** Takes every envelope of the server (duplicates are harmless), in the order given. */
        suspend fun receive(order: (List<Rumor>) -> List<Rumor> = { it }) {
            order(rumors()).forEach { inbox.accept(it, hex) }
        }

        suspend fun timer(): RelativeTimer? = store.read().received.singleOrNull()?.let { TimerInbox.resolve(it, now) }
    }

    private val bob = Relative()
    private val carol = Relative()

    private fun request(
        recipients: List<Relative> = listOf(bob, carol),
        deadline: Long = now + 2 * 3_600,
        duress: String? = DURESS,
        text: String = "[EXEMPLE] Je n’ai pas confirmé que tout allait bien.",
    ) = ArmRequest(
        recipients = recipients.map { TimerRecipient(it.hex) },
        deadline = deadline,
        text = text,
        place = "[EXEMPLE] Cortège, place de la République",
        checkInCode = CODE,
        checkInConfirmation = CODE,
        duressCode = duress,
    )

    private fun actionsOn(relative: Relative): List<String?> = relative.rumors().map { it.firstTagValue(TimerFormats.TAG_ACTION) }

    @Test
    fun aDepositReachesEachRecipientSealedAndAlone() = runTest {
        val result = service.arm(request(), transport)
        val armed = assertIs<ArmResult.Armed>(result)
        assertEquals(DeliveryReport(delivered = 2), armed.delivery)

        // The server only holds envelopes: one-time authors, the recipient's tag, nothing readable.
        assertEquals(2, server.events.size)
        assertTrue(server.events.all { it.kind == EventKinds.GIFT_WRAP })
        assertTrue(server.events.none { it.pubkey == alice.publicKey.hex })
        assertTrue(server.events.none { it.content.contains("EXEMPLE") || it.content.contains("ppf-") })

        for (relative in listOf(bob, carol)) {
            val rumor = relative.rumors().single()
            assertEquals(EventKinds.TIMER, rumor.kind)
            assertEquals(alice.publicKey.hex, rumor.pubkey)
            // Each recipient is named alone: nobody learns who else was chosen.
            assertEquals(listOf(relative.hex), rumor.tagValues("p"))
            val deposit = assertIs<TimerMessage.Deposit>(TimerFormats.read(rumor, relative.hex))
            assertEquals(now + 2 * 3_600, deposit.deadline)
            assertEquals("[EXEMPLE] Cortège, place de la République", deposit.place)
        }

        // The device keeps neither the codes nor the secret in clear.
        val stored = aliceStorage.value!!
        assertFalse(stored.contains("\"$CODE\""))
        assertFalse(stored.contains("\"$DURESS\""))
        val timer = assertNotNull(aliceStore.read().armed)
        assertEquals(64, timer.hashHex.length)
        assertTrue(aliceStore.read().pending.isEmpty())
    }

    @Test
    fun theRightCodeCancelsAndTheRecipientsAcceptIt() = runTest {
        service.arm(request(), transport)
        bob.receive()
        assertEquals(RelativeTimerPhase.WAITING, bob.timer()?.phase)

        now += 600
        assertEquals(CheckInResult.Cancelled, service.checkIn(CODE, transport))
        assertNull(aliceStore.read().armed)
        assertEquals(DeliveryReport(delivered = 2), service.flush(transport))
        assertEquals(listOf(TimerFormats.ACTION_DEPOSIT, TimerFormats.ACTION_CANCEL), actionsOn(bob))

        bob.receive()
        assertEquals(RelativeTimerPhase.CANCELLED, bob.timer()?.phase)
        // Long after the deadline, still no alert.
        now += 5 * 3_600
        assertEquals(RelativeTimerPhase.CANCELLED, bob.timer()?.phase)
    }

    @Test
    fun aCancellationWithAnotherSecretAnotherTimerOrAnotherAuthorIsRefused() = runTest {
        service.arm(request(), transport)
        bob.receive()
        val timerId = bob.store.read().received.single().id
        // The real secret, as carol receives it.
        service.checkIn(CODE, transport)
        service.flush(transport)
        val secret = assertIs<TimerMessage.Cancel>(TimerFormats.read(carol.rumors().last(), carol.hex)).secretHex

        val stranger = IdentityKeys.generate().publicKey.hex
        val randomSecret = IdentityKeys.generate().publicKey.hex
        val otherTimer = IdentityKeys.generate().publicKey.hex
        // A wrong secret, the right secret for another timer, the right secret from another author.
        bob.inbox.accept(TimerFormats.cancel(alice.publicKey.hex, bob.hex, timerId, now, randomSecret), bob.hex)
        bob.inbox.accept(TimerFormats.cancel(alice.publicKey.hex, bob.hex, otherTimer, now, secret), bob.hex)
        bob.inbox.accept(TimerFormats.cancel(stranger, bob.hex, timerId, now, secret), bob.hex)

        val own = bob.store.read().received.first { it.author == alice.publicKey.hex && it.id == timerId }
        assertEquals(RelativeTimerPhase.WAITING, TimerInbox.resolve(own, now)?.phase)
        now += 3 * 3_600
        assertEquals(RelativeTimerPhase.ALERT, TimerInbox.resolve(own, now)?.phase)
    }

    @Test
    fun aReportMovesTheDeadlineWithANewHashAndTheChainStillCancels() = runTest {
        service.arm(request(), transport)
        val before = assertNotNull(aliceStore.read().armed)

        now += 3_600
        val reported = service.report(CODE, now + 4 * 3_600, transport)
        assertEquals(ReportResult.Reported(now + 4 * 3_600), reported)
        service.flush(transport)
        val after = assertNotNull(aliceStore.read().armed)
        assertEquals(now + 4 * 3_600, after.deadline)
        assertEquals(now + 4 * 3_600, after.shownDeadline)
        assertTrue(after.hashHex != before.hashHex)

        val report = assertIs<TimerMessage.Report>(TimerFormats.read(bob.rumors().last(), bob.hex))
        // The report reveals the secret of the deposit and announces the hash of the next one.
        assertEquals(before.hashHex, TimerFormats.hashOf(report.secretHex))
        assertEquals(after.hashHex, report.hashHex)

        bob.receive()
        now += 2 * 3_600 // past the first deadline, before the new one
        assertEquals(RelativeTimerPhase.WAITING, bob.timer()?.phase)
        assertEquals(after.deadline, bob.timer()?.deadline)

        service.checkIn(CODE, transport)
        service.flush(transport)
        bob.receive()
        assertEquals(RelativeTimerPhase.CANCELLED, bob.timer()?.phase)
    }

    @Test
    fun theDuressCodeShowsTheSameAnswerSendsNothingAndTheAlertStillGoes() = runTest {
        service.arm(request(), transport)
        bob.receive()
        val sent = server.events.size

        now += 600
        val result = service.checkIn(DURESS, transport)
        // Exactly the same answer as the « je vais bien » code: the screen cannot differ.
        assertSame(CheckInResult.Cancelled, result)
        assertEquals(DeliveryReport(), service.flush(transport))
        assertEquals(sent, server.events.size)
        // Nothing on this device says that the timer still runs.
        assertTrue(aliceStore.read().isEmpty)

        now += 2 * 3_600
        bob.receive()
        assertEquals(RelativeTimerPhase.ALERT, bob.timer()?.phase)
    }

    @Test
    fun aDuressReportMovesOnlyTheScreen() = runTest {
        service.arm(request(), transport)
        val deadline = assertNotNull(aliceStore.read().armed).deadline
        val sent = server.events.size

        assertEquals(ReportResult.Reported(now + 5 * 3_600), service.report(DURESS, now + 5 * 3_600, transport))
        service.flush(transport)
        assertEquals(sent, server.events.size)
        val timer = assertNotNull(aliceStore.read().armed)
        assertEquals(deadline, timer.deadline)
        assertEquals(now + 5 * 3_600, timer.shownDeadline)

        // The secret did not change: the real code still cancels for the recipients.
        service.checkIn(CODE, transport)
        service.flush(transport)
        bob.receive()
        assertEquals(RelativeTimerPhase.CANCELLED, bob.timer()?.phase)
    }

    @Test
    fun wrongCodesWaitLikeTheLockScreen() = runTest {
        service.arm(request(), transport)
        repeat(UnlockDelays.FREE_ATTEMPTS - 1) { attempt ->
            assertEquals(CheckInResult.Wrong(attempt + 1, 0), service.checkIn("1357", transport))
        }
        assertEquals(CheckInResult.Wrong(UnlockDelays.FREE_ATTEMPTS, 30), service.checkIn("1357", transport))
        assertEquals(CheckInResult.TooEarly(30), service.checkIn(CODE, transport))
        now += 30
        assertEquals(CheckInResult.Cancelled, service.checkIn(CODE, transport))
    }

    @Test
    fun offlineTheDepositsWaitAndTheyLeaveBeforeTheCancellation() = runTest {
        server.online = false
        val armed = assertIs<ArmResult.Armed>(service.arm(request(), transport))
        assertEquals(DeliveryReport(queued = 2), armed.delivery)
        assertEquals(2, aliceStore.read().pending.size)
        assertEquals(0, server.events.size)

        // Cancelled while still offline: the cancellations queue behind the deposits.
        assertEquals(CheckInResult.Cancelled, service.checkIn(CODE, transport))
        assertEquals(4, aliceStore.read().pending.size)

        server.online = true
        assertEquals(DeliveryReport(delivered = 4), service.flush(transport))
        assertTrue(aliceStore.read().pending.isEmpty())
        assertEquals(listOf(TimerFormats.ACTION_DEPOSIT, TimerFormats.ACTION_CANCEL), actionsOn(bob))

        // Envelopes arrive in any order: the cancellation first still counts once its deposit is there.
        bob.receive { it.reversed() }
        assertEquals(RelativeTimerPhase.CANCELLED, bob.timer()?.phase)
    }

    @Test
    fun armingRefusesWhatCannotWork() = runTest {
        assertEquals(ArmResult.Refused(ArmProblem.NO_RECIPIENT), service.arm(request(recipients = emptyList()), transport))
        assertEquals(ArmResult.Refused(ArmProblem.DEADLINE_TOO_SOON), service.arm(request(deadline = now + 60), transport))
        assertEquals(ArmResult.Refused(ArmProblem.DEADLINE_TOO_FAR), service.arm(request(deadline = now + 3 * 24 * 3_600), transport))
        assertEquals(ArmResult.Refused(ArmProblem.NO_TEXT), service.arm(request(text = "  "), transport))
        assertEquals(ArmResult.Refused(ArmProblem.CODES, TimerCodeProblem.DURESS_SAME), service.arm(request(duress = CODE), transport))
        assertEquals(0, server.events.size)
        assertIs<ArmResult.Armed>(service.arm(request(duress = null), transport))
        assertEquals(ArmResult.Refused(ArmProblem.ALREADY_ARMED), service.arm(request(), transport))
    }

    private companion object {
        const val CODE = "73914"
        const val DURESS = "2580"
    }
}
