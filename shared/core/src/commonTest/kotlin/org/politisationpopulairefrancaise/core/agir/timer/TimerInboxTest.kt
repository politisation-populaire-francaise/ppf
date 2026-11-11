package org.politisationpopulairefrancaise.core.agir.timer

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.security.PinRecord
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The timers of relatives on a recipient's device, the formats and what the device keeps (AG-55, D-088). */
class TimerInboxTest {
    private var now = 1_800_000_000L
    private val store = TimerStore(InMemoryTimerStorage())
    private val inbox = TimerInbox(store, clock = { now })
    private val me = IdentityKeys.generate().publicKey.hex
    private val relative = IdentityKeys.generate().publicKey.hex
    private val timerId = IdentityKeys.generate().publicKey.hex
    private val secret = IdentityKeys.generate().publicKey.hex
    private val hash = TimerFormats.hashOf(secret)!!

    private fun deposit(deadline: Long = now + 3_600, author: String = relative, recipient: String = me): Rumor =
        TimerFormats.deposit(author, recipient, timerId, now, deadline, hash, "[EXEMPLE] Sans nouvelles, appelez-moi.", "[EXEMPLE] Gare du Nord")

    private suspend fun state(): RelativeTimer? = store.read().received.singleOrNull()?.let { TimerInbox.resolve(it, now) }

    @Test
    fun theLineBeforeTheDeadlineThenTheAlertByItself() = runTest {
        val first = deposit()
        assertTrue(inbox.accept(first, me))
        assertEquals(RelativeTimerPhase.WAITING, state()?.phase)
        assertEquals("[EXEMPLE] Gare du Nord", state()?.place)

        // No network is needed at the deadline: the deposit already on the device is enough.
        now += 3_600
        assertEquals(RelativeTimerPhase.ALERT, state()?.phase)

        inbox.dismiss(relative, timerId)
        assertEquals(RelativeTimerPhase.DISMISSED, state()?.phase)
        // The same envelope fetched again does not bring the alert back.
        assertFalse(inbox.accept(first, me))
        assertEquals(RelativeTimerPhase.DISMISSED, state()?.phase)
    }

    @Test
    fun aLateCancellationStillResolvesTheAlert() = runTest {
        inbox.accept(deposit(), me)
        now += 2 * 3_600
        assertEquals(RelativeTimerPhase.ALERT, state()?.phase)
        // The relative confirms after the deadline (offline until then): the alert turns into a confirmation.
        inbox.accept(TimerFormats.cancel(relative, me, timerId, now, secret), me)
        val resolved = state()
        assertEquals(RelativeTimerPhase.CANCELLED, resolved?.phase)
        assertEquals(now, resolved?.cancelledAt)
    }

    @Test
    fun onlyWellFormedTimerMessagesForThisDeviceAreRead() = runTest {
        val stranger = IdentityKeys.generate().publicKey.hex
        // Another recipient, the user's own rumor, too far a deadline, another kind, a malformed secret.
        assertNull(TimerFormats.read(deposit(recipient = stranger), me))
        assertNull(TimerFormats.read(deposit(author = me), me))
        assertNull(TimerFormats.read(deposit(deadline = now + 3 * 24 * 3_600), me))
        val otherKind = deposit().let { Rumor.of(UnsignedEvent(it.pubkey, it.createdAt, 14, it.tags, it.content)) }
        assertNull(TimerFormats.read(otherKind, me))
        assertNull(TimerFormats.read(TimerFormats.cancel(relative, me, timerId, now, "zz" + secret.drop(2)), me))
        assertFalse(inbox.accept(deposit(recipient = stranger), me))
        assertTrue(store.read().received.isEmpty())
    }

    @Test
    fun oldTimersAreForgottenAndStaleDepositsAreNotAlerts() = runTest {
        inbox.accept(deposit(), me)
        now += 3_600 + TimerInbox.KEEP_SECONDS + 1
        inbox.prune()
        assertTrue(store.read().received.isEmpty())

        // A deposit whose deadline passed more than a week ago (fetched again after a restore) is not an alert any more.
        val old = TimerFormats.deposit(relative, me, timerId, now - TimerInbox.KEEP_SECONDS - 7_200, now - TimerInbox.KEEP_SECONDS - 3_600, hash, "[EXEMPLE] Ancien", null)
        assertFalse(inbox.accept(old, me))
        assertTrue(store.read().received.isEmpty())
    }

    @Test
    fun anotherAccountNeverReadsWhatThisOneReceived() = runTest {
        inbox.accept(deposit(), me)
        val other = IdentityKeys.generate().publicKey.hex
        inbox.accept(TimerFormats.deposit(relative, other, timerId, now, now + 3_600, hash, "[EXEMPLE] Pour l’autre compte", null), other)
        val data = store.read()
        assertEquals(other, data.owner)
        assertEquals(1, data.received.size)
        assertEquals("[EXEMPLE] Pour l’autre compte", TimerInbox.resolve(data.received.single(), now)?.text)
    }

    @Test
    fun whatTheDeviceKeepsSurvivesAJsonRoundTrip() {
        val data = TimerDeviceData(
            owner = me,
            armed = ArmedTimer(
                id = timerId,
                armedAt = now,
                deadline = now + 7_200,
                shownDeadline = now + 9_000,
                recipients = listOf(TimerRecipient(relative), TimerRecipient(secret, legalTeam = true, label = "[EXEMPLE] Legal team")),
                text = "[EXEMPLE] Texte",
                place = null,
                hashHex = hash,
                secretNonceHex = "00".repeat(16),
                secretBoxHex = "11".repeat(32),
                codeSaltHex = "22".repeat(16),
                codeIterations = 600_000,
                duress = PinRecord(iterations = 600_000, saltHex = "33".repeat(16), hashHex = "44".repeat(32)),
                failedAttempts = 2,
                lastFailureEpochSeconds = now - 10,
            ),
            pending = listOf(PendingDelivery(timerId, TimerFormats.ACTION_DEPOSIT, relative, "{\"kind\":13}", now)),
            received = listOf(
                ReceivedTimer(
                    author = relative,
                    id = timerId,
                    firstSeenAt = now,
                    deposit = ReceivedDeposit(now + 3_600, hash, "[EXEMPLE] Alerte", "[EXEMPLE] Lieu", now),
                    reveals = listOf(ReceivedReveal(TimerFormats.ACTION_REPORT, secret, now + 7_200, hash, now, now)),
                ),
            ),
        )
        val encoded = data.encode()
        val decoded = TimerDeviceData.decode(encoded)
        assertEquals(encoded, decoded.encode())
        assertEquals(data.received, decoded.received)
        assertEquals(data.pending, decoded.pending)
        assertEquals(now + 9_000, decoded.armed?.shownDeadline)
        assertEquals("44".repeat(32), decoded.armed?.duress?.hashHex)
        // Nothing readable is printed by mistake.
        assertFalse(data.toString().contains(hash))
        assertFalse(data.armed.toString().contains("11".repeat(32)))
        // A damaged file is forgotten, not shown.
        assertTrue(TimerDeviceData.decode("{pas du json").isEmpty)
        assertTrue(TimerDeviceData.decode(null).isEmpty)
    }

    @Test
    fun theCodesOfATimer() {
        assertNull(TimerCodeRules.check("73914", "73914", "2580"))
        assertNull(TimerCodeRules.check("73914", "73914", null))
        assertNull(TimerCodeRules.check("73914", "73914", ""))
        assertEquals(TimerCodeProblem.CHECK_IN_FORMAT, TimerCodeRules.check("739", "739", null))
        assertEquals(TimerCodeProblem.CHECK_IN_FORMAT, TimerCodeRules.check("739145821", "739145821", null))
        assertEquals(TimerCodeProblem.CHECK_IN_FORMAT, TimerCodeRules.check("73a14", "73a14", null))
        assertEquals(TimerCodeProblem.CHECK_IN_SIMPLE, TimerCodeRules.check("1234", "1234", null))
        assertEquals(TimerCodeProblem.CHECK_IN_SIMPLE, TimerCodeRules.check("0000", "0000", null))
        assertEquals(TimerCodeProblem.CHECK_IN_MISMATCH, TimerCodeRules.check("73914", "73915", null))
        assertEquals(TimerCodeProblem.DURESS_FORMAT, TimerCodeRules.check("73914", "73914", "25"))
        assertEquals(TimerCodeProblem.DURESS_SAME, TimerCodeRules.check("73914", "73914", "73914"))
    }
}
