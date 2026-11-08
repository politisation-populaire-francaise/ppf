package org.politisationpopulairefrancaise.core.contact

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Nip44
import org.politisationpopulairefrancaise.core.messaging.Nip44Exception
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** Why an envelope of a case is refused (stable [code] of the API, English [message]). */
enum class EnvelopeProblem(val code: String, val message: String) {
    NOT_A_GIFT_WRAP("bad_envelope", "A message of a case is a gift wrap (kind 1059)."),
    BAD_TAGS("bad_envelope", "An envelope has exactly one p tag, and at most one nonce tag, nothing else."),
    WRONG_RECIPIENT("wrong_recipient", "The envelope is not addressed to the expected key."),
    TOO_LARGE("too_large", "An envelope is at most ${CaseEnvelopes.MAX_ENVELOPE_BYTES} bytes."),
    BAD_DATE("bad_date", "The date of the envelope is more than 3 days in the past or 10 minutes in the future."),
    NOT_ENOUGH_WORK("not_enough_work", "The envelope lacks the proof of work (NIP-13) this server asks."),
    INVALID_SIGNATURE("invalid_signature", "The id or the signature does not match the content."),
}

/** Why an envelope could not be built (the message is too large for one envelope, for example). */
class CaseEnvelopeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** A message of a case once opened: the message, the key that wrote it (the seal's) and when (the rumor's date). */
data class OpenedCaseMessage(val message: CaseMessage, val author: PublicKey, val writtenAt: Long)

/**
 * The envelopes of the cases (docs/integration/contact-reclamations.md, section 5): each message is a NIP-17
 * rumor (kind 14) whose text is a [CaseMessage], sealed (kind 13, signed by the key of the case or, when the
 * person links it, of their account), gift wrapped (kind 1059, one-time author, random date within the two
 * past days, NIP-44), addressed by one `p` tag to the inbox of the team (messages of the person) or to the key
 * of the case (replies of the team). The server checks the outside ([check]) and reads nothing inside.
 */
object CaseEnvelopes {
    /** 96 KiB of JSON. */
    const val MAX_ENVELOPE_BYTES: Int = 96 * 1024
    const val MAX_AGE_SECONDS: Long = 3 * 24 * 60 * 60
    const val MAX_FUTURE_SECONDS: Long = 10 * 60

    /** The proof of work asked for the creation of a case unless the server says otherwise. */
    const val DEFAULT_WORK_BITS: Int = 16

    /** Size of the JSON form of [envelope], as stored. */
    fun sizeOf(envelope: SignedEvent): Int = envelope.toJson().encodeToByteArray().size

    /**
     * The checks of the server on an envelope addressed to [recipientHex] (the inbox of the team for the
     * messages of the person): gift wrap, a single `p` tag equal to [recipientHex] and at most a `nonce` tag,
     * size, date between 3 days in the past and 10 minutes in the future around [now], at least [work] bits of
     * proof of work (creation only), then id and signature. Null when everything holds. The cheap checks come
     * first: a forged envelope costs no signature verification.
     */
    fun check(envelope: SignedEvent, recipientHex: String, now: Long, work: Int = 0): EnvelopeProblem? {
        if (envelope.kind != EventKinds.GIFT_WRAP) return EnvelopeProblem.NOT_A_GIFT_WRAP
        val recipients = envelope.tags.filter { it.firstOrNull() == "p" }
        val nonces = envelope.tags.filter { it.firstOrNull() == ProofOfWork.TAG }
        if (recipients.size != 1 || recipients.single().size < 2 || nonces.size > 1 || envelope.tags.size != recipients.size + nonces.size) {
            return EnvelopeProblem.BAD_TAGS
        }
        if (recipients.single()[1] != recipientHex) return EnvelopeProblem.WRONG_RECIPIENT
        if (sizeOf(envelope) > MAX_ENVELOPE_BYTES) return EnvelopeProblem.TOO_LARGE
        if (envelope.createdAt < now - MAX_AGE_SECONDS || envelope.createdAt > now + MAX_FUTURE_SECONDS) return EnvelopeProblem.BAD_DATE
        if (!ProofOfWork.satisfies(envelope, work)) return EnvelopeProblem.NOT_ENOUGH_WORK
        if (!envelope.verify()) return EnvelopeProblem.INVALID_SIGNATURE
        return null
    }

    /**
     * Seals [message], written by [sender], for [recipient], and wraps it with a one-time key and [work] bits
     * of proof of work. Throws [CaseFormatException] for an invalid message and [CaseEnvelopeException] when
     * the result would be larger than [MAX_ENVELOPE_BYTES] (checked before mining).
     */
    suspend fun build(message: CaseMessage, sender: MessagingSigner, recipient: PublicKey, work: Int = 0, now: Long = currentEpochSeconds()): SignedEvent {
        require(work in 0..ProofOfWork.MAX_MINED_BITS) { "The proof of work is between 0 and ${ProofOfWork.MAX_MINED_BITS} bits" }
        val rumor = Rumor.of(UnsignedEvent.create(sender.publicKey, EventKinds.CHAT_MESSAGE, now, listOf(listOf("p", recipient.hex)), message.toJson()))
        val seal = try {
            GiftWrap.seal(rumor, sender, recipient, GiftWrap.randomPastTimestamp(now))
        } catch (e: Nip44Exception) {
            throw CaseEnvelopeException("The message is too large for one envelope", e)
        }
        return wrap(seal, recipient, work, GiftWrap.randomPastTimestamp(now))
    }

    /**
     * The opening envelope of a new case, for the inbox of the team: [sender] is the signer of the case
     * ([CaseSecrets.signer]) or, when the person links their account, the signer of the account.
     */
    suspend fun opening(message: CaseMessage, sender: MessagingSigner, inbox: PublicKey, work: Int = DEFAULT_WORK_BITS, now: Long = currentEpochSeconds()): SignedEvent {
        require(message.kind == CaseMessageKind.OPENING) { "An opening envelope holds an opening" }
        return build(message, sender, inbox, work, now)
    }

    /** A follow-up message of the person for the inbox of the team (no proof of work is asked). */
    suspend fun followUp(message: CaseMessage, sender: MessagingSigner, inbox: PublicKey, now: Long = currentEpochSeconds()): SignedEvent {
        require(message.kind == CaseMessageKind.MESSAGE) { "A follow-up envelope holds a follow-up message" }
        return build(message, sender, inbox, 0, now)
    }

    /** A reply or a change of status of the team, signed by the inbox key, for the key of the case (team client, step 3). */
    suspend fun fromTeam(message: CaseMessage, inbox: MessagingSigner, casePublicKey: PublicKey, now: Long = currentEpochSeconds()): SignedEvent {
        require(message.kind == CaseMessageKind.REPLY || message.kind == CaseMessageKind.STATUS) { "The team sends replies and changes of status" }
        return build(message, inbox, casePublicKey, 0, now)
    }

    /**
     * Opens an envelope addressed to [recipient]: checks of [GiftWrap.unwrap] (authentic layers, the seal's
     * author is the rumor's), a private message (kind 14) holding a valid [CaseMessage]. Throws
     * [org.politisationpopulairefrancaise.core.messaging.GiftWrapException] or [CaseFormatException].
     */
    fun open(envelope: SignedEvent, recipient: MessagingSigner): OpenedCaseMessage {
        val rumor = GiftWrap.unwrap(envelope, recipient)
        if (rumor.kind != EventKinds.CHAT_MESSAGE) throw CaseFormatException("Not a private message (kind ${rumor.kind})")
        val message = CaseMessage.fromJson(rumor.content)
        return OpenedCaseMessage(message, PublicKey.fromHex(rumor.pubkey), rumor.createdAt)
    }

    /**
     * For the app: opens a message of the team with the key of the case, and accepts it only when it was
     * written by the [inbox] key built into the app (never a key the server gives: a compromised server
     * cannot forge a reply), about this very case, as a reply or a change of status.
     */
    fun openFromTeam(envelope: SignedEvent, secrets: CaseSecrets, inbox: PublicKey): OpenedCaseMessage {
        val opened = open(envelope, secrets.signer())
        if (opened.author != inbox) throw CaseFormatException("Not written by the inbox of the team")
        if (opened.message.caseNumber != secrets.number.display) throw CaseFormatException("A message of another case")
        if (opened.message.kind != CaseMessageKind.REPLY && opened.message.kind != CaseMessageKind.STATUS) throw CaseFormatException("Not a message of the team")
        return opened
    }

    /** The gift wrap of [GiftWrap.wrap], with a proof of work mined before the one-time key signs. */
    private suspend fun wrap(seal: SignedEvent, recipient: PublicKey, work: Int, createdAt: Long): SignedEvent {
        val oneTimeKeys = IdentityKeys.generate()
        try {
            val key = Nip44.conversationKey(oneTimeKeys, recipient)
            val content = try {
                Nip44.encrypt(seal.toJson(), key)
            } catch (e: Nip44Exception) {
                throw CaseEnvelopeException("The message is too large for one envelope", e)
            } finally {
                key.fill(0)
            }
            val unsigned = UnsignedEvent.create(oneTimeKeys.publicKey, EventKinds.GIFT_WRAP, createdAt, listOf(listOf("p", recipient.hex)), content)
            // The counter has a fixed width: the size measured now is the size of the mined envelope.
            val measured = if (work > 0) ProofOfWork.withPlaceholder(unsigned, work) else unsigned
            if (sizeOf(placeholderSigned(measured)) > MAX_ENVELOPE_BYTES) throw CaseEnvelopeException("The message is too large for one envelope")
            val mined = ProofOfWork.mine(unsigned, work)
            return LocalEventSigner(oneTimeKeys).sign(mined)
        } finally {
            oneTimeKeys.destroy()
        }
    }

    /** [event] with an id and a signature of the right lengths, only to measure its JSON. */
    private fun placeholderSigned(event: UnsignedEvent): SignedEvent =
        SignedEvent("0".repeat(64), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))
}
