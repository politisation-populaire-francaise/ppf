package org.politisationpopulairefrancaise.core.platform

import fr.acinq.bitcoin.Crypto

/**
 * The challenge a team member signs with their passkey to approve the platform signature of one event
 * (contract, section 5): SHA-256 of `"ppf-approval-v1\n" + use + "\n" + hex id of the unsigned event`.
 * The domain and the use make an approval worthless for another use or another event; the back-office
 * gives it to the browser as the WebAuthn challenge, the signing service recomputes it.
 */
object ApprovalChallenge {
    const val DOMAIN: String = "ppf-approval-v1"

    fun of(use: SigningUse, unsignedEventId: String): ByteArray = of(use.id, unsignedEventId)

    fun of(use: String, unsignedEventId: String): ByteArray {
        require(unsignedEventId.length == 64 && unsignedEventId.all { it in '0'..'9' || it in 'a'..'f' }) {
            "The event id is 64 lowercase hexadecimal characters"
        }
        return Crypto.sha256("$DOMAIN\n$use\n$unsignedEventId".encodeToByteArray())
    }
}
