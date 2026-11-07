package org.politisationpopulairefrancaise.server.backoffice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.Nip44

/**
 * A text only the "audit" key can read: NIP-44 version 2 from a key pair made for this text alone.
 * [ephemeralPubkey] is needed, with the audit secret key, to decrypt [payload].
 */
@Serializable
data class SealedForAudit(
    @SerialName("ephemeral_pubkey") val ephemeralPubkey: String,
    val payload: String,
)

/**
 * Encrypts towards the audit key (`PPF_AUDIT_PUBLIC_KEY`, contract section 8), whose secret stays offline
 * with the owner: for example the link between a citizen's account and the session that validated it.
 * The server can write these texts and never read them again. Reuses the NIP-44 code of the messaging
 * (shared/core), never a second implementation.
 */
class AuditSealer(private val auditKey: PublicKey) {
    init {
        // A key that is not a point of the curve is refused at startup rather than at the first use.
        seal("ppf")
    }

    fun seal(plaintext: String): SealedForAudit {
        val ephemeral = IdentityKeys.generate()
        try {
            val secret = ephemeral.secretKey()
            val conversationKey = try {
                Nip44.conversationKey(secret, auditKey.bytes)
            } finally {
                secret.fill(0)
            }
            try {
                return SealedForAudit(ephemeral.publicKey.hex, Nip44.encrypt(plaintext, conversationKey))
            } finally {
                conversationKey.fill(0)
            }
        } finally {
            ephemeral.destroy()
        }
    }
}
