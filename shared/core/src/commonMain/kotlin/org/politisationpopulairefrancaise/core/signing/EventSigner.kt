package org.politisationpopulairefrancaise.core.signing

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * Something able to sign events for one public key. On a device it holds the user's keys
 * ([LocalEventSigner]); on the server it will call the separate signing service that holds the
 * platform key, which is never stored in clear on the main server (cahier des charges, 3.2).
 */
interface EventSigner {
    val publicKey: PublicKey

    /** Signs [event], whose `pubkey` must be this signer's key. */
    suspend fun sign(event: UnsignedEvent): SignedEvent
}

/** Signs with keys held in memory on the device. */
class LocalEventSigner(private val keys: IdentityKeys) : EventSigner {
    override val publicKey: PublicKey get() = keys.publicKey

    override suspend fun sign(event: UnsignedEvent): SignedEvent {
        require(event.pubkey == publicKey.hex) { "The event is not authored by this signer" }
        val id = event.id()
        val signature = keys.sign(id)
        return SignedEvent(
            id = id.toHexString(),
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
            sig = signature.toHexString(),
        )
    }
}

/** Convenience: builds and signs an event authored by this signer, dated now unless specified. */
suspend fun EventSigner.sign(
    kind: Int,
    content: String = "",
    tags: List<List<String>> = emptyList(),
    createdAt: Long = currentEpochSeconds(),
): SignedEvent = sign(UnsignedEvent.create(publicKey, kind, createdAt, tags, content))
