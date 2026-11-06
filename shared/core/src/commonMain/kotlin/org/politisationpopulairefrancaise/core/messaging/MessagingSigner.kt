package org.politisationpopulairefrancaise.core.messaging

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * An identity able to take part in encrypted conversations: it signs events like any [EventSigner]
 * and derives NIP-44 conversation keys, which needs the secret key. On a device both come from the
 * [IdentityKeys] held in memory ([LocalMessagingSigner]); a remote signer could implement it later
 * without the messaging code noticing.
 */
interface MessagingSigner : EventSigner {
    /** The NIP-44 conversation key shared with [other]. The caller wipes it after use. */
    fun conversationKey(other: PublicKey): ByteArray
}

/** Signs and derives conversation keys from keys held in memory on the device. */
class LocalMessagingSigner(private val keys: IdentityKeys) : MessagingSigner {
    private val signer = LocalEventSigner(keys)

    override val publicKey: PublicKey get() = keys.publicKey

    override suspend fun sign(event: UnsignedEvent): SignedEvent = signer.sign(event)

    override fun conversationKey(other: PublicKey): ByteArray = Nip44.conversationKey(keys, other)
}
