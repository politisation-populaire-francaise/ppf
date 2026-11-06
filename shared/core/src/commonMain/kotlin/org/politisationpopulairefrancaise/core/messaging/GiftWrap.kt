package org.politisationpopulairefrancaise.core.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * A rumor (NIP-59): an event with its id but without signature. It only ever travels inside a
 * seal, so nobody can prove who wrote it if it leaks (deniability); its id still identifies it.
 */
@Serializable
data class Rumor(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
) {
    fun unsigned(): UnsignedEvent = UnsignedEvent(pubkey, createdAt, kind, tags, content)

    /** True only if [id] really is the digest of this content (NIP-01 serialization). Never throws. */
    fun isConsistent(): Boolean =
        isLowercaseHex(id, 64) && runCatching { unsigned().idHex() }.getOrNull() == id

    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    fun firstTagValue(name: String): String? = tagValues(name).firstOrNull()

    fun toJson(): String = RumorJson.encodeToString(serializer(), this)

    companion object {
        /** The rumor of an unsigned event: its id is computed, nothing is signed. */
        fun of(event: UnsignedEvent): Rumor = Rumor(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content)

        fun fromJson(json: String): Rumor = RumorJson.decodeFromString(serializer(), json)
    }
}

/** Why a gift wrap could not be opened; the message is dropped, never shown. */
class GiftWrapException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * NIP-59 gift wrapping (https://github.com/nostr-protocol/nips/blob/master/59.md): a rumor is
 * encrypted for one recipient and signed by its author (the seal, kind 13), then the seal is
 * encrypted again and signed by a random one-time key (the gift wrap, kind 1059). The server only
 * ever sees the wrap: a random author, the recipient's `p` tag, a random date and an opaque content.
 */
object GiftWrap {
    /** NIP-17 and NIP-59 randomize the dates of seals and wraps up to two days in the past. */
    const val TIMESTAMP_JITTER_SECONDS: Long = 2 * 24 * 60 * 60

    /**
     * Seals [rumor] for [recipient]: encrypted with the conversation key of its author and the
     * recipient, signed by the author, no tag. The rumor must be authored by [sender].
     */
    suspend fun seal(rumor: Rumor, sender: MessagingSigner, recipient: PublicKey, createdAt: Long = randomPastTimestamp()): SignedEvent {
        require(rumor.pubkey == sender.publicKey.hex) { "A seal is signed by the author of the rumor" }
        val key = sender.conversationKey(recipient)
        try {
            val content = Nip44.encrypt(rumor.toJson(), key)
            return sender.sign(UnsignedEvent.create(sender.publicKey, EventKinds.SEAL, createdAt, emptyList(), content))
        } finally {
            key.fill(0)
        }
    }

    /**
     * Wraps [seal] for [recipient] with a brand new throwaway key, destroyed right after signing;
     * the only tag is the recipient's `p`, which the server uses to deliver the wrap.
     */
    suspend fun wrap(seal: SignedEvent, recipient: PublicKey, createdAt: Long = randomPastTimestamp()): SignedEvent {
        require(seal.kind == EventKinds.SEAL) { "Only seals are gift wrapped" }
        val oneTimeKeys = IdentityKeys.generate()
        try {
            val key = Nip44.conversationKey(oneTimeKeys, recipient)
            val content = try {
                Nip44.encrypt(seal.toJson(), key)
            } finally {
                key.fill(0)
            }
            val event = UnsignedEvent.create(oneTimeKeys.publicKey, EventKinds.GIFT_WRAP, createdAt, listOf(listOf("p", recipient.hex)), content)
            return LocalEventSigner(oneTimeKeys).sign(event)
        } finally {
            oneTimeKeys.destroy()
        }
    }

    /** Seal then wrap [rumor] for [recipient], each layer with its own random past date. */
    suspend fun wrapFor(rumor: Rumor, sender: MessagingSigner, recipient: PublicKey, now: Long = currentEpochSeconds()): SignedEvent =
        wrap(seal(rumor, sender, recipient, randomPastTimestamp(now)), recipient, randomPastTimestamp(now))

    /**
     * Opens a wrap addressed to [recipient] and returns the rumor inside, after checking: kind and
     * signature of the wrap, recipient among its `p` tags, kind and signature of the seal, author of
     * the seal identical to the author of the rumor (otherwise anyone could impersonate anyone),
     * and id of the rumor. Every failure is a [GiftWrapException].
     */
    fun unwrap(wrap: SignedEvent, recipient: MessagingSigner): Rumor {
        if (wrap.kind != EventKinds.GIFT_WRAP) throw GiftWrapException("Not a gift wrap (kind ${wrap.kind})")
        if (!wrap.verify()) throw GiftWrapException("The gift wrap is not authentic")
        if (recipient.publicKey.hex !in wrap.tagValues("p")) throw GiftWrapException("The gift wrap is not addressed to this key")
        val seal = SignedEvent.fromJsonOrThrow(decryptFrom(wrap.pubkey, wrap.content, recipient, "gift wrap"), "seal")
        if (seal.kind != EventKinds.SEAL) throw GiftWrapException("Not a seal (kind ${seal.kind})")
        if (!seal.verify()) throw GiftWrapException("The seal is not authentic")
        val rumor = try {
            Rumor.fromJson(decryptFrom(seal.pubkey, seal.content, recipient, "seal"))
        } catch (e: GiftWrapException) {
            throw e
        } catch (e: Exception) {
            throw GiftWrapException("The rumor is malformed", e)
        }
        if (rumor.pubkey != seal.pubkey) throw GiftWrapException("The rumor is not from the author of the seal")
        if (!rumor.isConsistent()) throw GiftWrapException("The rumor id does not match its content")
        return rumor
    }

    private fun decryptFrom(authorHex: String, payload: String, recipient: MessagingSigner, layer: String): String {
        val author = runCatching { PublicKey.fromHex(authorHex) }.getOrElse { throw GiftWrapException("Malformed $layer author", it) }
        val key = try {
            recipient.conversationKey(author)
        } catch (e: Nip44Exception) {
            throw GiftWrapException("Cannot derive the $layer key", e)
        }
        try {
            return Nip44.decrypt(payload, key)
        } catch (e: Nip44Exception) {
            throw GiftWrapException("Cannot decrypt the $layer", e)
        } finally {
            key.fill(0)
        }
    }

    private fun SignedEvent.Companion.fromJsonOrThrow(json: String, layer: String): SignedEvent =
        runCatching { fromJson(json) }.getOrElse { throw GiftWrapException("The $layer is malformed", it) }

    /**
     * A date between [now] and two days before, drawn from the secure random generator so that the
     * dates of seals and wraps reveal nothing about when the message was really written.
     */
    fun randomPastTimestamp(now: Long = currentEpochSeconds()): Long {
        val bytes = secureRandomBytes(4)
        val value = ((bytes[0].toLong() and 0xff) shl 24) or ((bytes[1].toLong() and 0xff) shl 16) or
            ((bytes[2].toLong() and 0xff) shl 8) or (bytes[3].toLong() and 0xff)
        return now - value % (TIMESTAMP_JITTER_SECONDS + 1)
    }
}

private val RumorJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
