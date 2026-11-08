package org.politisationpopulairefrancaise.core.agir.security

import org.politisationpopulairefrancaise.core.crypto.HmacSha256

/**
 * PBKDF2 with HMAC-SHA-256 (RFC 8018, section 5.2), the slow and salted hash of the unlock code of
 * the application (S-43). This portable version is built on [HmacSha256] (itself on the SHA-256 of
 * bitcoin-kmp, already used to sign every event): no new dependency. The JVM and Android use the
 * same function of their own cryptography provider, much faster ([pbkdf2Sha256]); both give the
 * same bytes (tested).
 */
object Pbkdf2Sha256 {
    fun derive(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations >= 1) { "at least one iteration" }
        require(length in 1..1024) { "length out of range" }
        val output = ByteArray(length)
        var block = 1
        var offset = 0
        while (offset < length) {
            val index = byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte())
            var u = HmacSha256.compute(password, salt, index)
            val t = u.copyOf()
            for (i in 2..iterations) {
                val next = HmacSha256.compute(password, u)
                u.fill(0)
                u = next
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            u.fill(0)
            val take = minOf(t.size, length - offset)
            t.copyInto(output, offset, 0, take)
            t.fill(0)
            offset += take
            block++
        }
        return output
    }
}

/**
 * PBKDF2-HMAC-SHA-256 by the platform's provider when it has one (`PBKDF2WithHmacSHA256` of the
 * JVM and of Android 8 and later), else [Pbkdf2Sha256]. [password] holds ASCII digits only.
 */
expect fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray
