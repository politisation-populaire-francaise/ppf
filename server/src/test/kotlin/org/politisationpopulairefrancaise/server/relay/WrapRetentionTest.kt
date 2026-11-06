package org.politisationpopulairefrancaise.server.relay

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class WrapRetentionTest {

    private val repository = EventRepository(TestDatabase.dataSource)
    private val sender = LocalEventSigner(IdentityKeys.generate())
    private val recipientKeys = LocalEventSigner(IdentityKeys.generate())
    private val recipient = recipientKeys.publicKey.hex
    private val now = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    /** Minimum and default 90 days, maximum 365 days: the server defaults of D-028. */
    private val retention = WrapRetention(repository, 90.days, 365.days, clock)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun wrap(content: String, to: String = recipient): SignedEvent = runBlocking {
        // NIP-59 randomises created_at: the retention must not depend on it.
        sender.sign(kind = RelayKinds.GIFT_WRAP, content = content, tags = listOf(listOf("p", to)), createdAt = 1)
    }

    /** A gift wrap received [daysAgo] days before [now], for [to]. */
    private fun wrapReceived(daysAgo: Long, to: String = recipient): SignedEvent =
        wrap("[EXEMPLE] enveloppe reçue il y a $daysAgo jours pour ${to.take(8)}", to).also {
            repository.store(it)
            backdate(it, now.minusSeconds(daysAgo * 86_400))
        }

    private fun mailboxSettings(signer: LocalEventSigner, retentionDays: String, createdAt: Long = 1_000, identifier: String = MailboxSettings.IDENTIFIER): SignedEvent =
        runBlocking {
            signer.sign(
                kind = MailboxSettings.KIND,
                tags = listOf(listOf("d", identifier), listOf(MailboxSettings.RETENTION_DAYS_TAG, retentionDays)),
                createdAt = createdAt,
            )
        }

    private fun backdate(event: SignedEvent, receivedAt: Instant) {
        TestDatabase.dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE events SET received_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, receivedAt.atOffset(ZoneOffset.UTC))
                statement.setString(2, event.id)
                assertEquals(1, statement.executeUpdate())
            }
        }
    }

    private fun mailbox(of: String = recipient): Set<String> =
        repository.query(EventFilter(kinds = listOf(RelayKinds.GIFT_WRAP), tags = mapOf("p" to listOf(of)))).map { it.id }.toSet()

    @Test
    fun withoutSettingTheDefaultRetentionApplies() {
        val fresh = wrapReceived(89)
        val limit = wrapReceived(90)
        val stale = wrapReceived(91)
        val oldNote = runBlocking { sender.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] note ancienne", createdAt = 1) }
        repository.store(oldNote)
        backdate(oldNote, now.minusSeconds(400 * 86_400))

        assertEquals(1, retention.purgeOnce())
        assertNotNull(repository.find(fresh.id))
        assertNotNull(repository.find(limit.id), "received exactly at the cutoff is kept")
        assertNull(repository.find(stale.id))
        assertNotNull(repository.find(oldNote.id), "only envelopes expire")
        assertEquals(setOf(fresh.id, limit.id), mailbox(), "the tag rows went with the envelope")
        assertEquals(0, retention.purgeOnce())
        assertEquals(now.minusSeconds(90 * 86_400), retention.defaultCutoff())
    }

    @Test
    fun theRecipientsOwnSettingLengthensTheRetentionOfTheirMailboxOnly() {
        repository.store(mailboxSettings(recipientKeys, "200"))
        val other = LocalEventSigner(IdentityKeys.generate())
        val kept = wrapReceived(150)
        val expired = wrapReceived(201)
        val othersExpired = wrapReceived(150, to = other.publicKey.hex)
        val othersKept = wrapReceived(89, to = other.publicKey.hex)

        assertEquals(2, retention.purgeOnce())
        assertEquals(setOf(kept.id), mailbox())
        assertEquals(setOf(othersKept.id), mailbox(other.publicKey.hex), "another mailbox keeps the default")
        assertNull(repository.find(expired.id))
        assertNull(repository.find(othersExpired.id))
    }

    @Test
    fun theSettingIsBoundedBetweenTheMinimumAndTheMaximum() {
        val greedy = LocalEventSigner(IdentityKeys.generate())
        val hasty = LocalEventSigner(IdentityKeys.generate())
        repository.store(mailboxSettings(greedy, "100000"))
        repository.store(mailboxSettings(hasty, "7"))
        val greedyKept = wrapReceived(364, to = greedy.publicKey.hex)
        val greedyExpired = wrapReceived(366, to = greedy.publicKey.hex)
        val hastyKept = wrapReceived(89, to = hasty.publicKey.hex)
        val hastyExpired = wrapReceived(91, to = hasty.publicKey.hex)

        assertEquals(2, retention.purgeOnce())
        assertEquals(setOf(greedyKept.id), mailbox(greedy.publicKey.hex), "capped at 365 days")
        assertEquals(setOf(hastyKept.id), mailbox(hasty.publicKey.hex), "never below the 90-day minimum")
        assertNull(repository.find(greedyExpired.id))
        assertNull(repository.find(hastyExpired.id))
    }

    @Test
    fun aNewerSettingReplacesTheOlderOne() {
        repository.store(mailboxSettings(recipientKeys, "300", createdAt = 1_000))
        repository.store(mailboxSettings(recipientKeys, "120", createdAt = 2_000))
        val kept = wrapReceived(100)
        val expired = wrapReceived(150)

        assertEquals(1, retention.purgeOnce())
        assertEquals(setOf(kept.id), mailbox())
        assertNull(repository.find(expired.id))

        // Lengthening again applies at the next pass (an envelope already deleted is gone for good).
        repository.store(mailboxSettings(recipientKeys, "300", createdAt = 3_000))
        val lateButKept = wrapReceived(250)
        assertEquals(0, retention.purgeOnce())
        assertEquals(setOf(kept.id, lateButKept.id), mailbox())
    }

    @Test
    fun onlyTheRecipientsOwnSettingUnderTheMailboxIdentifierCountsAndUnreadableValuesMeanTheDefault() {
        val stranger = LocalEventSigner(IdentityKeys.generate())
        val garbled = LocalEventSigner(IdentityKeys.generate())
        val elsewhere = LocalEventSigner(IdentityKeys.generate())
        // A stranger cannot set someone else's retention: their event is under their own address.
        repository.store(runBlocking {
            stranger.sign(
                kind = MailboxSettings.KIND,
                tags = listOf(listOf("d", MailboxSettings.IDENTIFIER), listOf(MailboxSettings.RETENTION_DAYS_TAG, "365"), listOf("p", recipient)),
                createdAt = 1_000,
            )
        })
        repository.store(mailboxSettings(garbled, "beaucoup"))
        repository.store(mailboxSettings(elsewhere, "365", identifier = "ppf:other"))
        val expired = wrapReceived(100)
        val garbledExpired = wrapReceived(100, to = garbled.publicKey.hex)
        val elsewhereExpired = wrapReceived(100, to = elsewhere.publicKey.hex)
        val kept = wrapReceived(80)

        assertEquals(3, retention.purgeOnce())
        assertEquals(setOf(kept.id), mailbox())
        assertNull(repository.find(expired.id))
        assertNull(repository.find(garbledExpired.id))
        assertNull(repository.find(elsewhereExpired.id))
    }

    @Test
    fun theFirstRetentionTagIsTheOneThatCounts() {
        repository.store(runBlocking {
            recipientKeys.sign(
                kind = MailboxSettings.KIND,
                tags = listOf(
                    listOf("d", MailboxSettings.IDENTIFIER),
                    listOf(MailboxSettings.RETENTION_DAYS_TAG, "200"),
                    listOf(MailboxSettings.RETENTION_DAYS_TAG, "365"),
                ),
                createdAt = 1_000,
            )
        })
        val expired = wrapReceived(250)
        val kept = wrapReceived(150)
        assertEquals(1, retention.purgeOnce())
        assertEquals(setOf(kept.id), mailbox())
        assertNull(repository.find(expired.id))
    }

    @Test
    fun configurationRejectsNonsense() {
        assertFailsWith<IllegalArgumentException> { WrapRetention(repository, 90.days, 30.days, clock) }
        assertFailsWith<IllegalArgumentException> { WrapRetention(repository, 0.days, 30.days, clock) }
        // Minimum equal to maximum: every mailbox keeps exactly that.
        val fixed = WrapRetention(repository, 10.days, 10.days, clock)
        repository.store(mailboxSettings(recipientKeys, "365"))
        val expired = wrapReceived(11)
        val kept = wrapReceived(9)
        assertEquals(1, fixed.purgeOnce())
        assertEquals(setOf(kept.id), mailbox())
        assertNull(repository.find(expired.id))
    }
}
