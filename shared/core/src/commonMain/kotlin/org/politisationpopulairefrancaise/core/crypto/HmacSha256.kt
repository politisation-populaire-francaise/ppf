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
        val hashedKey = if (key.size > BLOCK_SIZE) Crypto.sha256(key) else null
        val normalizedKey = hashedKey ?: key
        val innerPad = ByteArray(BLOCK_SIZE)
        val outerInput = ByteArray(BLOCK_SIZE + OUTPUT_SIZE)
        for (i in 0 until BLOCK_SIZE) {
            val k = if (i < normalizedKey.size) normalizedKey[i].toInt() else 0
            innerPad[i] = (k xor 0x36).toByte()
            outerInput[i] = (k xor 0x5c).toByte()
        }
        hashedKey?.fill(0)
        val innerInput = ByteArray(BLOCK_SIZE + messages.sumOf { it.size })
        innerPad.copyInto(innerInput)
        var offset = BLOCK_SIZE
        for (message in messages) {
            message.copyInto(innerInput, offset)
            offset += message.size
        }
        val innerHash = Crypto.sha256(innerInput)
        innerHash.copyInto(outerInput, BLOCK_SIZE)
        val result = Crypto.sha256(outerInput)
        innerPad.fill(0)
        innerInput.fill(0)
        innerHash.fill(0)
        outerInput.fill(0)
        return result
    }
}
