package org.politisationpopulairefrancaise.core.identity

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The device side of S-146: commitment once, notice from the phrase, contacts' notices checked. */
class AccountRecoveryClientTest {

    private val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")
    private val account = phrase.deriveKeys()
    private val recovery = phrase.deriveRecoveryKeys().publicKey
    private val now = 1_794_000_000L
    private val day = 24L * 60 * 60

    private fun client(remote: RemoteEvents) = AccountRecoveryClient(remote, clock = { now }, shuffle = { it })

    @Test
    fun theCommitmentIsPublishedOnceAndRecognisedAfterwards() = runTest {
        val server = InMemoryPublicationPort()
        val first = client(server).ensureCommitment(LocalEventSigner(account), recovery)
        val published = assertIs<CommitmentOutcome.Published>(first)
        val again = assertIs<CommitmentOutcome.AlreadySet>(client(server).ensureCommitment(LocalEventSigner(account), recovery))
        assertEquals(published.id, again.id)
        assertEquals(true, again.matches)
        // Another recovery key would not match the one on record.
        val other = assertIs<CommitmentOutcome.AlreadySet>(client(server).ensureCommitment(LocalEventSigner(account), IdentityKeys.generate().publicKey))
        assertEquals(false, other.matches)
        assertEquals(1, server.events.count { it.kind == AccountRecovery.COMMITMENT_KIND })
    }

    @Test
    fun withoutAServerNothingIsPublished() = runTest {
        assertEquals(CommitmentOutcome.Unavailable, client(Offline).ensureCommitment(LocalEventSigner(account), recovery))
        assertEquals(NoticeOutcome.Unavailable, client(Offline).declareCompromised(phrase, now, null))
    }

    @Test
    fun theNoticeIsSignedByTheRecoveryKeyAndThenNothingMoreIsAccepted() = runTest {
        val server = InMemoryPublicationPort()
        client(server).ensureCommitment(LocalEventSigner(account), recovery)
        val successor = IdentityKeys.generate().publicKey
        val outcome = assertIs<NoticeOutcome.Published>(client(server).declareCompromised(phrase, now - 3 * day, successor))
        assertEquals(account.publicKey.hex, outcome.notice.compromised)
        assertEquals(recovery.hex, outcome.notice.recovery)
        assertEquals(now - 3 * day, outcome.notice.since)
        assertEquals(successor.hex, outcome.notice.successor)

        val status = assertIs<AccountStatusResult.Known>(server.fetchAccountStatus(account.publicKey.hex)).status
        assertTrue(status.compromised)
        assertEquals(successor.hex, status.successor)

        // The thief's next event is refused, and the refusal is reported.
        val note = LocalEventSigner(account).sign(UnsignedEvent.create(account.publicKey, 1, now, emptyList(), "[EXEMPLE]"))
        val refused = assertIs<PublicationResult.Rejected>(server.publish(note))
        assertEquals(RejectionReason.ACCOUNT_COMPROMISED, refused.reason)
        assertTrue(account.publicKey.hex in server.refusedAsCompromised.value)
        // A second notice is refused in plain terms.
        assertIs<NoticeOutcome.AlreadyCompromised>(client(server).declareCompromised(phrase, now, null))
    }

    @Test
    fun aDateBeyondThirtyDaysIsBroughtBackWithinTheBound() = runTest {
        val server = InMemoryPublicationPort()
        client(server).ensureCommitment(LocalEventSigner(account), recovery)
        val outcome = assertIs<NoticeOutcome.Published>(client(server).declareCompromised(phrase, now - 90 * day, null))
        assertEquals(now - AccountRecovery.MAX_BACKDATING_SECONDS, outcome.notice.since)
    }

    @Test
    fun anAccountWithoutCommitmentOrAnotherPhraseIsExplained() = runTest {
        val server = InMemoryPublicationPort()
        assertEquals(NoticeOutcome.NoCommitment, client(server).declareCompromised(phrase, now, null))
        // The commitment names a recovery key that is not this phrase's.
        client(server).ensureCommitment(LocalEventSigner(account), IdentityKeys.generate().publicKey)
        assertEquals(NoticeOutcome.WrongPhrase, client(server).declareCompromised(phrase, now, null))
    }

    @Test
    fun aSuccessorServesOnlyOnce() = runTest {
        val server = InMemoryPublicationPort()
        val other = RecoveryPhrase.generate()
        val successor = IdentityKeys.generate().publicKey
        client(server).ensureCommitment(LocalEventSigner(account), recovery)
        client(server).ensureCommitment(LocalEventSigner(other.deriveKeys()), other.deriveRecoveryKeys().publicKey)
        assertIs<NoticeOutcome.Published>(client(server).declareCompromised(phrase, now, successor))
        assertEquals(NoticeOutcome.SuccessorTaken, client(server).declareCompromised(other, now, successor))
    }

    @Test
    fun theContactsNoticesAreAskedInBatchesAndCheckedOnTheDevice() = runTest {
        val server = InMemoryPublicationPort()
        client(server).ensureCommitment(LocalEventSigner(account), recovery)
        client(server).declareCompromised(phrase, now - day, null)
        val recorder = Recorder(server)
        val contacts = List(14) { IdentityKeys.generate().publicKey.hex } + account.publicKey.hex
        val found = assertNotNull(client(recorder).noticesFor(contacts, batchSize = 5))
        assertEquals(setOf(account.publicKey.hex), found.keys)
        assertEquals(now - day, found.getValue(account.publicKey.hex).notice.since)
        // Three requests of five keys at most, never the whole list at once.
        assertEquals(listOf(5, 5, 5), recorder.tagBatches)
        assertTrue(client(server).noticesFor(emptyList())!!.isEmpty())
        assertNull(client(Offline).noticesFor(contacts))
    }

    @Test
    fun aForgedNoticeIsIgnored() = runTest {
        val server = InMemoryPublicationPort()
        client(server).ensureCommitment(LocalEventSigner(account), recovery)
        val commitment = server.events.single { it.kind == AccountRecovery.COMMITMENT_KIND }
        // A notice signed by someone else's key, served by a hostile source.
        val thief = IdentityKeys.generate()
        val forged = LocalEventSigner(thief).sign(
            UnsignedEvent.create(
                thief.publicKey,
                AccountRecovery.NOTICE_KIND,
                now,
                listOf(
                    listOf("p", account.publicKey.hex),
                    listOf("e", commitment.id),
                    listOf(AccountRecovery.ALT_TAG, AccountRecovery.NOTICE_ALT),
                    listOf(AccountRecovery.SINCE_TAG, now.toString()),
                ),
            ),
        )
        val hostile = Serving(listOf(forged), listOf(commitment))
        assertTrue(client(hostile).noticesFor(listOf(account.publicKey.hex))!!.isEmpty())
        assertFalse(AccountRecovery.checkNotice(forged, commitment) is NoticeCheck.Valid)
    }

    private object Offline : RemoteEvents {
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
    }

    private class Recorder(private val inner: InMemoryPublicationPort) : RemoteEvents by inner {
        val tagBatches = mutableListOf<Int>()

        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
            tagBatches += tags.size
            return inner.fetchByTags(tags, filter)
        }
    }

    private class Serving(private val notices: List<SignedEvent>, private val commitments: List<SignedEvent>) : RemoteEvents {
        override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Events(commitments)
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult = FetchResult.Events(notices)
    }
}
