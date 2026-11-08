package org.politisationpopulairefrancaise.core.identity

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recovery key and the two events of "Déclarer mon compte compromis" (S-146, D-134). */
class AccountRecoveryTest {

    // NIP-06 test vector (https://github.com/nostr-protocol/nips/blob/master/06.md); the recovery key
    // was computed independently with a plain BIP-32 implementation at m/44'/1237'/0'/1/0.
    private val phrase = RecoveryPhrase.parse("leader monkey parrot ring guide accident before fence cannon height naive bean")

    private val now = 1_794_000_000L
    private val day = 24L * 60 * 60

    @Test
    fun theRecoveryKeyIsTheOtherBranchOfThePhrase() {
        val account = phrase.deriveKeys()
        val recovery = phrase.deriveRecoveryKeys()
        // The account key is still NIP-06 account 0.
        assertEquals("7f7ff03d123792d6ac594bfa67bf6d0c0ab55b6b1fdb6249303fe861f1ccba9a", account.secretKey().toHexString())
        assertEquals("17162c921dc4d2518f9a101db33695df1afb56ab82f5ff3e5da6eec3ca5cd917", account.publicKey.hex)
        assertEquals("025a7e31db8135d9e8040f931e74ddc2dfe5957553f8b2a80dc93d3a779dd48a", recovery.secretKey().toHexString())
        assertEquals("bfbb7b7fa954b8e5468bebf7b899696f5aab822412013053d891ed7602fbc24e", recovery.publicKey.hex)
        assertNotEquals(account.publicKey, recovery.publicKey)
        // Deterministic: the same phrase typed again gives the same recovery key.
        assertEquals(recovery.publicKey, RecoveryPhrase.parse(phrase.text).deriveRecoveryKeys().publicKey)
        // A generated French phrase also gives two different keys.
        val french = RecoveryPhrase.generate()
        assertNotEquals(french.deriveKeys().publicKey, french.deriveRecoveryKeys().publicKey)
    }

    @Test
    fun aCommitmentNamesTheRecoveryKey() = runTest {
        val account = LocalEventSigner(phrase.deriveKeys())
        val recovery = phrase.deriveRecoveryKeys().publicKey
        val event = AccountRecovery.signCommitment(account, recovery, createdAt = now)
        assertEquals(1776, event.kind)
        assertEquals(
            listOf(listOf("p", recovery.hex), listOf("alt", "pubkey whitelisting event"), listOf("ppf-recovery", "1")),
            event.tags,
        )
        assertEquals("", event.content)
        val parsed = AccountRecovery.parseCommitment(event)!!
        assertEquals(RecoveryCommitment(event.id, account.publicKey.hex, recovery.hex, now), parsed)

        // Anything else is not a commitment.
        assertNull(AccountRecovery.parseCommitment(event.copy(sig = "0".repeat(128))))
        assertNull(AccountRecovery.parseCommitment(account.sign(kind = 1776, tags = listOf(listOf("p", recovery.hex)), createdAt = now)))
        assertNull(AccountRecovery.parseCommitment(account.sign(kind = 1776, tags = event.tags + listOf(listOf("p", recovery.hex)), createdAt = now)))
        assertNull(AccountRecovery.parseCommitment(account.sign(kind = 1776, tags = event.tags, content = "x", createdAt = now)))
        assertNull(AccountRecovery.parseCommitment(account.sign(kind = 1, tags = event.tags, createdAt = now)))
        assertFailsWith<IllegalArgumentException> { AccountRecovery.commitment(account.publicKey, account.publicKey, now) }
    }

    @Test
    fun aNoticeSignedByTheRecoveryKeyIsValid() = runTest {
        val (commitmentEvent, commitment, recovery) = commitment()
        val successor = IdentityKeys.generate().publicKey
        val proof = "ab".repeat(32)
        val notice = AccountRecovery.signNotice(recovery, commitment, createdAt = now, since = now - 3 * day, proof = proof, successor = successor)
        assertEquals(1777, notice.kind)
        assertEquals(
            listOf(
                listOf("p", commitment.account),
                listOf("e", commitment.id),
                listOf("proof", proof),
                listOf("alt", "pubkey migration event"),
                listOf("ppf-compromised-since", (now - 3 * day).toString()),
                listOf("ppf-successor", successor.hex),
            ),
            notice.tags,
        )
        val check = AccountRecovery.checkNotice(notice, commitmentEvent, now = now)
        assertEquals(
            NoticeCheck.Valid(CompromiseNotice(notice.id, recovery.publicKey.hex, commitment.account, commitment.id, now - 3 * day, now, proof, successor.hex)),
            check,
        )
        // The proof and the successor are optional; "since" defaults to the notice date.
        val plain = AccountRecovery.signNotice(recovery, commitment, createdAt = now)
        val valid = AccountRecovery.checkNotice(plain, commitmentEvent) as NoticeCheck.Valid
        assertEquals(now, valid.notice.since)
        assertNull(valid.notice.proof)
        assertNull(valid.notice.successor)
    }

    @Test
    fun aNoticeByAnotherKeyIsRefused() = runTest {
        val (commitmentEvent, commitment, _) = commitment()
        // The thief has the account key, not the recovery key: a notice signed by any other key fails.
        val thief = LocalEventSigner(IdentityKeys.generate())
        val forged = thief.sign(AccountRecovery.notice(thief.publicKey, commitment.copy(recovery = thief.publicKey.hex), now))
        assertEquals(NoticeCheck.Invalid(NoticeProblem.WRONG_RECOVERY_KEY), AccountRecovery.checkNotice(forged, commitmentEvent))
        // The account key itself cannot sign a notice about itself.
        val account = LocalEventSigner(phrase.deriveKeys())
        val self = account.sign(kind = 1777, tags = forged.tags, createdAt = now)
        assertEquals(NoticeCheck.Invalid(NoticeProblem.MALFORMED), AccountRecovery.parseNotice(self))
        // A tampered signature.
        val (_, _, recovery) = commitment()
        val notice = AccountRecovery.signNotice(recovery, commitment, createdAt = now)
        assertEquals(NoticeCheck.Invalid(NoticeProblem.BAD_SIGNATURE), AccountRecovery.parseNotice(notice.copy(createdAt = now + 1)))
    }

    @Test
    fun aNoticeNamesTheFirstCommitment() = runTest {
        val (_, commitment, recovery) = commitment()
        val notice = AccountRecovery.signNotice(recovery, commitment, createdAt = now)
        // Another commitment of the same account (the thief's) is not the one the notice names.
        val account = LocalEventSigner(phrase.deriveKeys())
        val other = AccountRecovery.signCommitment(account, IdentityKeys.generate().publicKey, createdAt = now + 1)
        assertEquals(NoticeCheck.Invalid(NoticeProblem.NOT_THE_COMMITMENT), AccountRecovery.checkNotice(notice, other))
        // Something that is not a commitment.
        assertEquals(NoticeCheck.Invalid(NoticeProblem.NOT_THE_COMMITMENT), AccountRecovery.checkNotice(notice, notice))
    }

    @Test
    fun compromisedSinceGoesBackThirtyDaysAtMost() = runTest {
        val (commitmentEvent, commitment, recovery) = commitment()
        val limit = AccountRecovery.signNotice(recovery, commitment, createdAt = now, since = now - 30 * day)
        assertTrue(AccountRecovery.checkNotice(limit, commitmentEvent) is NoticeCheck.Valid)
        assertFailsWith<IllegalArgumentException> { AccountRecovery.notice(recovery.publicKey, commitment, now, since = now - 30 * day - 1) }
        assertFailsWith<IllegalArgumentException> { AccountRecovery.notice(recovery.publicKey, commitment, now, since = now + 1) }
        // Built by hand, past the bounds: refused.
        for (since in listOf(now - 30 * day - 1, now + 1)) {
            val tags = limit.tags.map { if (it[0] == AccountRecovery.SINCE_TAG) listOf(it[0], since.toString()) else it }
            val event = recovery.sign(kind = 1777, tags = tags, createdAt = now)
            assertEquals(NoticeCheck.Invalid(NoticeProblem.SINCE_OUT_OF_BOUNDS), AccountRecovery.checkNotice(event, commitmentEvent), "since $since")
        }
        // A notice dated in the future would stretch "since".
        val future = AccountRecovery.signNotice(recovery, commitment, createdAt = now + day)
        assertEquals(NoticeCheck.Invalid(NoticeProblem.FROM_THE_FUTURE), AccountRecovery.checkNotice(future, commitmentEvent, now = now))
    }

    @Test
    fun malformedNoticesAreRefused() = runTest {
        val (_, commitment, recovery) = commitment()
        val good = AccountRecovery.signNotice(recovery, commitment, createdAt = now, successor = IdentityKeys.generate().publicKey)
        val variants = listOf(
            good.tags.filter { it[0] != "p" },
            good.tags.filter { it[0] != "e" },
            good.tags.filter { it[0] != AccountRecovery.SINCE_TAG },
            good.tags + listOf(listOf("p", "cd".repeat(32))),
            good.tags + listOf(listOf(AccountRecovery.SINCE_TAG, now.toString())),
            good.tags + listOf(listOf(AccountRecovery.SUCCESSOR_TAG, "cd".repeat(32))),
            good.tags.map { if (it[0] == AccountRecovery.SINCE_TAG) listOf(it[0], "-5") else it },
            good.tags.map { if (it[0] == AccountRecovery.SUCCESSOR_TAG) listOf(it[0], commitment.account) else it },
            good.tags.map { if (it[0] == AccountRecovery.SUCCESSOR_TAG) listOf(it[0], recovery.publicKey.hex) else it },
            good.tags + listOf(listOf(AccountRecovery.PROOF_TAG, "not an id")),
        )
        variants.forEachIndexed { index, tags ->
            val event = recovery.sign(kind = 1777, tags = tags, createdAt = now)
            assertEquals(NoticeCheck.Invalid(NoticeProblem.MALFORMED), AccountRecovery.parseNotice(event), "variant $index")
        }
        assertEquals(NoticeCheck.Invalid(NoticeProblem.MALFORMED), AccountRecovery.parseNotice(recovery.sign(kind = 1, tags = good.tags, createdAt = now)))
    }

    /** A commitment of the test account, its parsed form and a signer holding the recovery key. */
    private suspend fun commitment(): Triple<SignedEvent, RecoveryCommitment, LocalEventSigner> {
        val recovery = LocalEventSigner(phrase.deriveRecoveryKeys())
        val event = AccountRecovery.signCommitment(LocalEventSigner(phrase.deriveKeys()), recovery.publicKey, createdAt = now - 100 * day)
        return Triple(event, AccountRecovery.parseCommitment(event)!!, recovery)
    }
}
