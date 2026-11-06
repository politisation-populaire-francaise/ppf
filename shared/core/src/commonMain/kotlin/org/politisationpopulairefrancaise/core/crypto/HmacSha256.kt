package org.politisationpopulairefrancaise.core.crypto

import fr.acinq.bitcoin.Crypto

/**
 * HMAC-SHA-256 (RFC 2104), built on the SHA-256 of bitcoin-kmp that already signs every event.
 * Used by HKDF and by the NIP-44 message authentication code.
 */
object HmacSha256 {
    const val BLOCK_SIZE: Int = 64
    const val OUTPUT_SIZE: Int = 32

    /** HMAC of the concatenation of [messages] under [key] (any key length; longer keys are hashed first). */
    fun compute(key: ByteArray, vararg messages: ByteArray): ByteArray {
        val normalizedKey = if (key.size > BLOCK_SIZE) Crypto.sha256(key) else key
        val innerPad = ByteArray(BLOCK_SIZE)
        val outerPad = ByteArray(BLOCK_SIZE)
        for (i in 0 until BLOCK_SIZE) {
            val k = if (i < normalizedKey.size) normalizedKey[i].toInt() else 0
            innerPad[i] = (k xor 0x36).toByte()
            outerPad[i] = (k xor 0x5c).toByte()
        }
        val innerInput = ByteArray(BLOCK_SIZE + messages.sumOf { it.size })
        innerPad.copyInto(innerInput)
        var offset = BLOCK_SIZE
        for (message in messages) {
            message.copyInto(innerInput, offset)
            offset += message.size
        }
        val innerHash = Crypto.sha256(innerInput)
        val result = Crypto.sha256(outerPad + innerHash)
        innerPad.fill(0)
        outerPad.fill(0)
        innerInput.fill(0)
        return result
    }
}
