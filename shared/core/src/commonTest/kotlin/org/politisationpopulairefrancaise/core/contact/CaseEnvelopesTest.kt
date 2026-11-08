package org.politisationpopulairefrancaise.core.contact

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.GiftWrapException
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseEnvelopesTest {

    private val now = 1_790_000_000L
    private val inboxKeys = IdentityKeys.generate()
    private val inbox = inboxKeys.publicKey
    private val team = LocalMessagingSigner(inboxKeys)
    private val number = CaseNumber.generate()
    private val secrets = CaseSecrets.derive(number, CaseCode.generate())
    private val opening = CaseMessage.opening(number, CaseNature.Contact(ContactCategory.CITIZEN), "[EXEMPLE] Une question", "[EXEMPLE] Bonjour l’équipe.")

    @Test
    fun anOpeningSignedByTheCaseReachesTheInboxWithItsWork() = runTest {
        val envelope = CaseEnvelopes.opening(opening, secrets.signer(), inbox, work = 8, now = now)
        assertEquals(EventKinds.GIFT_WRAP, envelope.kind)
        assertEquals(listOf("p", inbox.hex), envelope.tags.first())
        assertEquals(2, envelope.tags.size)
        assertTrue(envelope.createdAt in (now - 2 * 86_400)..now)
        assertTrue(envelope.pubkey != secrets.publicKey.hex && envelope.pubkey != inbox.hex, "a one-time author")
        assertTrue(!envelope.content.contains("question"))
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, now, work = 8))
        assertEquals(EnvelopeProblem.NOT_ENOUGH_WORK, CaseEnvelopes.check(envelope, inbox.hex, now, work = 9))
        val opened = CaseEnvelopes.open(envelope, team)
        assertEquals(opening, opened.message)
        assertEquals(secrets.publicKey, opened.author)
        assertEquals(now, opened.writtenAt)
        // Only the inbox opens it: not the case, not anybody else.
        assertFailsWith<GiftWrapException> { CaseEnvelopes.open(envelope, secrets.signer()) }
        assertFailsWith<GiftWrapException> { CaseEnvelopes.open(envelope, LocalMessagingSigner(IdentityKeys.generate())) }
    }

    @Test
    fun theDefaultWorkIsSixteenBits() = runTest {
        val envelope = CaseEnvelopes.opening(opening, secrets.signer(), inbox, now = now)
        assertEquals(CaseEnvelopes.DEFAULT_WORK_BITS, ProofOfWork.committedTarget(envelope.tags))
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, now, work = 16))
    }

    @Test
    fun aPersonWhoLinksTheirAccountSignsTheSealWithIt() = runTest {
        val account = IdentityKeys.generate()
        val claim = CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.DATA_RIGHTS), "[EXEMPLE] Mes données", "[EXEMPLE] Je demande une copie.")
        val envelope = CaseEnvelopes.opening(claim, LocalMessagingSigner(account), inbox, work = 4, now = now)
        assertEquals(account.publicKey, CaseEnvelopes.open(envelope, team).author)
        // The server sees no difference: the outside is the same.
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, now, work = 4))
    }

    @Test
    fun theServerChecksTheOutsideOfAnEnvelope() = runTest {
        val envelope = CaseEnvelopes.followUp(CaseMessage.followUp(number, "[EXEMPLE] Précision"), secrets.signer(), inbox, now)
        assertEquals(listOf(listOf("p", inbox.hex)), envelope.tags)
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, now))
        assertEquals(EnvelopeProblem.WRONG_RECIPIENT, CaseEnvelopes.check(envelope, secrets.publicKey.hex, now))
        assertEquals(EnvelopeProblem.NOT_ENOUGH_WORK, CaseEnvelopes.check(envelope, inbox.hex, now, work = 1))
        // Dates: at most 3 days in the past, 10 minutes in the future.
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, envelope.createdAt + CaseEnvelopes.MAX_AGE_SECONDS))
        assertEquals(EnvelopeProblem.BAD_DATE, CaseEnvelopes.check(envelope, inbox.hex, envelope.createdAt + CaseEnvelopes.MAX_AGE_SECONDS + 1))
        assertNull(CaseEnvelopes.check(envelope, inbox.hex, envelope.createdAt - 600))
        assertEquals(EnvelopeProblem.BAD_DATE, CaseEnvelopes.check(envelope, inbox.hex, envelope.createdAt - 601))
        // Tags: exactly one p, at most one nonce, nothing else (checked before the signature).
        val p = listOf("p", inbox.hex)
        listOf(
            emptyList(),
            listOf(p, p),
            listOf(p, listOf("subject", "x")),
            listOf(p, listOf("nonce", "1", "1"), listOf("nonce", "2", "1")),
            listOf(listOf("p")),
        ).forEach { tags -> assertEquals(EnvelopeProblem.BAD_TAGS, CaseEnvelopes.check(envelope.copy(tags = tags), inbox.hex, now), tags.toString()) }
        // Anything changed breaks the id or the signature.
        assertEquals(EnvelopeProblem.INVALID_SIGNATURE, CaseEnvelopes.check(envelope.copy(content = envelope.content + "A"), inbox.hex, now))
        assertEquals(EnvelopeProblem.INVALID_SIGNATURE, CaseEnvelopes.check(envelope.copy(sig = "0".repeat(128)), inbox.hex, now))
        // Not a gift wrap.
        val note = LocalEventSigner(inboxKeys).sign(UnsignedEvent.create(inbox, EventKinds.TEXT_NOTE, now, listOf(p), "[EXEMPLE]"))
        assertEquals(EnvelopeProblem.NOT_A_GIFT_WRAP, CaseEnvelopes.check(note, inbox.hex, now))
        // Size, before any signature check.
        val huge = envelope.copy(content = "A".repeat(CaseEnvelopes.MAX_ENVELOPE_BYTES))
        assertEquals(EnvelopeProblem.TOO_LARGE, CaseEnvelopes.check(huge, inbox.hex, now))
    }

    @Test
    fun aMessageTooLargeForOneEnvelopeIsRefusedBeforeAnyWork() = runTest {
        // 20 000 characters of three bytes each: within the bounds of the format, too much for one envelope.
        val heavy = CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.OTHER), "[EXEMPLE] Long", "€".repeat(CaseMessage.MAX_TEXT))
        assertFailsWith<CaseEnvelopeException> { CaseEnvelopes.opening(heavy, secrets.signer(), inbox, work = 16, now = now) }
        // A long text of ordinary French fits.
        val long = CaseMessage.opening(number, CaseNature.Claim(ClaimMotif.OTHER), "[EXEMPLE] Long", "[EXEMPLE] Réclamation détaillée. ".repeat(600).take(CaseMessage.MAX_TEXT))
        val envelope = CaseEnvelopes.opening(long, secrets.signer(), inbox, work = 2, now = now)
        assertTrue(CaseEnvelopes.sizeOf(envelope) <= CaseEnvelopes.MAX_ENVELOPE_BYTES)
        assertEquals(long, CaseEnvelopes.open(envelope, team).message)
    }

    @Test
    fun theAppAcceptsOnlyTheTeamsMessagesAboutItsCase() = runTest {
        val reply = CaseMessage.reply(number, "[EXEMPLE] Merci, nous regardons.")
        val envelope = CaseEnvelopes.fromTeam(reply, team, secrets.publicKey, now)
        assertNull(CaseEnvelopes.check(envelope, secrets.publicKey.hex, now))
        val opened = CaseEnvelopes.openFromTeam(envelope, secrets, inbox)
        assertEquals(reply, opened.message)
        assertEquals(inbox, opened.author)
        val status = CaseMessage.statusChange(number, CaseStatus.CLOSED, "[EXEMPLE] Dossier clos.")
        assertEquals(status, CaseEnvelopes.openFromTeam(CaseEnvelopes.fromTeam(status, team, secrets.publicKey, now), secrets, inbox).message)

        // A key other than the inbox built into the app (a compromised server, for instance) cannot reply.
        val impostor = LocalMessagingSigner(IdentityKeys.generate())
        assertFailsWith<CaseFormatException> { CaseEnvelopes.openFromTeam(CaseEnvelopes.fromTeam(reply, impostor, secrets.publicKey, now), secrets, inbox) }
        // A reply of another case moved into this one.
        val other = CaseMessage.reply(CaseNumber.generate(), "[EXEMPLE] Autre dossier")
        assertFailsWith<CaseFormatException> { CaseEnvelopes.openFromTeam(CaseEnvelopes.fromTeam(other, team, secrets.publicKey, now), secrets, inbox) }
        // The team never sends an opening nor a message of the person.
        val fake = CaseEnvelopes.build(CaseMessage.followUp(number, "[EXEMPLE] Faux"), team, secrets.publicKey, now = now)
        assertFailsWith<CaseFormatException> { CaseEnvelopes.openFromTeam(fake, secrets, inbox) }
        assertFailsWith<IllegalArgumentException> { CaseEnvelopes.fromTeam(opening, team, secrets.publicKey, now) }
        // Another case cannot read it.
        val stranger = CaseSecrets.derive(number, CaseCode.generate())
        assertFailsWith<GiftWrapException> { CaseEnvelopes.openFromTeam(envelope, stranger, inbox) }
    }
}
