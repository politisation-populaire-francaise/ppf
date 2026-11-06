package org.politisationpopulairefrancaise.core.messaging

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * An identity able to take part in encrypted conversations: it signs events like any [EventSigner]
 * and encrypts or decrypts NIP-44 payloads with another key, which needs the secret key. On a
 * device both come from the [IdentityKeys] held in memory ([LocalMessagingSigner]). The interface
 * exposes encryption rather than keys, like the Nostr signer protocols (NIP-07, NIP-46), so that a
 * remote signer could implement it later without the messaging code noticing.
 */
interface MessagingSigner : EventSigner {
    /** NIP-44 encryption of [plaintext] for [recipient]. */
    fun encrypt(recipient: PublicKey, plaintext: String): String

    /** NIP-44 decryption of a payload written by [sender] for this key. Throws [Nip44Exception] on anything invalid. */
    fun decrypt(sender: PublicKey, payload: String): String
}

/** Signs and encrypts with keys held in memory on the device. */
class LocalMessagingSigner(private val keys: IdentityKeys) : MessagingSigner {
    private val signer = LocalEventSigner(keys)

    override val publicKey: PublicKey get() = keys.publicKey

    override suspend fun sign(event: UnsignedEvent): SignedEvent = signer.sign(event)

    /** The NIP-44 conversation key shared with [other]; the caller wipes it after use. */
    fun conversationKey(other: PublicKey): ByteArray = Nip44.conversationKey(keys, other)

    override fun encrypt(recipient: PublicKey, plaintext: String): String {
        val key = conversationKey(recipient)
        try {
            return Nip44.encrypt(plaintext, key)
        } finally {
            key.fill(0)
        }
    }

    override fun decrypt(sender: PublicKey, payload: String): String {
        val key = conversationKey(sender)
        try {
            return Nip44.decrypt(payload, key)
        } finally {
            key.fill(0)
        }
    }
}
