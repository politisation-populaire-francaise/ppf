package org.politisationpopulairefrancaise.core.crypto

/**
 * HKDF with SHA-256 (RFC 5869): derives keys from a shared secret. NIP-44 uses [extract] for the
 * long-term conversation key and [expand] for the per-message keys.
 */
object HkdfSha256 {
    private const val HASH_SIZE = HmacSha256.OUTPUT_SIZE
    const val MAX_OUTPUT_SIZE: Int = 255 * HASH_SIZE

    /** PRK = HMAC(salt, IKM); an empty salt means a string of 32 zero bytes, as the RFC says. */
    fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray =
        HmacSha256.compute(if (salt.isEmpty()) ByteArray(HASH_SIZE) else salt, inputKeyMaterial)

    /** OKM of [length] bytes (at most 8160) from a pseudo-random key and optional context [info]. */
    fun expand(pseudoRandomKey: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..MAX_OUTPUT_SIZE) { "HKDF output length must be between 1 and $MAX_OUTPUT_SIZE bytes" }
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < length) {
            previous = HmacSha256.compute(pseudoRandomKey, previous, info, byteArrayOf(counter.toByte()))
            val take = minOf(HASH_SIZE, length - filled)
            previous.copyInto(output, filled, 0, take)
            filled += take
            counter++
        }
        previous.fill(0)
        return output
    }

    /** Extract then expand in one call. */
    fun derive(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, inputKeyMaterial)
        val result = expand(prk, info, length)
        prk.fill(0)
        return result
    }
}
