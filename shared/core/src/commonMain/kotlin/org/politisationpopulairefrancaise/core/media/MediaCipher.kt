package org.politisationpopulairefrancaise.core.media

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.crypto.ChaCha20
import org.politisationpopulairefrancaise.core.crypto.HmacSha256
import org.politisationpopulairefrancaise.core.crypto.constantTimeEquals
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.messaging.Nip44
import org.politisationpopulairefrancaise.core.messaging.Nip44Exception

/** SHA-256 of files, written in lowercase hexadecimal as in the `x` and `ox` tags of NIP-94. */
object MediaDigest {
    fun sha256(bytes: ByteArray): ByteArray = Crypto.sha256(bytes)

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHexString()

    fun isHex(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}

/** A file encrypted on the device: [bytes] go to the server, [key] only inside an encrypted envelope. */
class EncryptedFile(
    /** The ciphertext, what the server stores; it names the file by its SHA-256 ([id]). */
    val bytes: ByteArray,
    /** The random key of this file alone (32 bytes); the caller wipes it once written in the envelope. */
    val key: ByteArray,
) {
    val id: String = MediaDigest.sha256Hex(bytes)
}

class MediaCipherException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Encryption of the files of a testimony (photos, original kept for the lawyers): the NIP-44
 * version 2 construction (HKDF-SHA-256, ChaCha20, HMAC-SHA-256 over nonce and ciphertext, padding
 * that hides the exact size), applied to bytes instead of text and keyed by a random key used for
 * one file only, in place of a conversation key. The key travels in the gift-wrapped envelope
 * (NIP-59) of the testimony; the server only ever holds ciphertext. The primitives are the ones
 * already checked against the official NIP-44 vectors; nothing new is invented, only the base64
 * layer and the 64 KiB limit of chat messages are left out.
 *
 * Layout: version (1 byte, 2) ‖ nonce (32) ‖ ChaCha20(padded plaintext) ‖ HMAC (32), the padding
 * being NIP-44's (length prefix of 2 bytes, or 6 bytes from 64 KiB, then zeros up to the padded size).
 */
object MediaCipher {
    const val ALGORITHM: String = "nip44-v2-bytes"
    const val KEY_SIZE: Int = 32
    private const val MAC_SIZE = 32

    /** Largest file encrypted, in bytes (40 MiB). */
    const val MAX_PLAINTEXT_BYTES: Int = 40 * 1024 * 1024

    fun encrypt(plaintext: ByteArray, key: ByteArray = secureRandomBytes(KEY_SIZE), nonce: ByteArray = secureRandomBytes(Nip44.NONCE_SIZE)): EncryptedFile {
        require(key.size == KEY_SIZE) { "The key of a file is 32 bytes" }
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES) { "Files of 1 byte to $MAX_PLAINTEXT_BYTES bytes" }
        val keys = Nip44.messageKeys(key, nonce)
        try {
            val padded = Nip44.pad(plaintext, MAX_PLAINTEXT_BYTES)
            val ciphertext = ChaCha20.process(keys.chachaKey, keys.chachaNonce, padded)
            padded.fill(0)
            val mac = HmacSha256.compute(keys.hmacKey, nonce, ciphertext)
            val out = ByteArray(1 + nonce.size + ciphertext.size + MAC_SIZE)
            out[0] = Nip44.VERSION.toByte()
            nonce.copyInto(out, 1)
            ciphertext.copyInto(out, 1 + nonce.size)
            mac.copyInto(out, 1 + nonce.size + ciphertext.size)
            return EncryptedFile(out, key)
        } finally {
            keys.wipe()
        }
    }

    /** Decrypts what [encrypt] wrote; any alteration of a single byte is refused ([MediaCipherException]). */
    fun decrypt(file: ByteArray, key: ByteArray): ByteArray {
        if (key.size != KEY_SIZE) throw MediaCipherException("The key of a file is 32 bytes")
        if (file.size < 1 + Nip44.NONCE_SIZE + 34 + MAC_SIZE) throw MediaCipherException("Encrypted file too short")
        if (file[0].toInt() != Nip44.VERSION) throw MediaCipherException("Unsupported version")
        val nonce = file.copyOfRange(1, 1 + Nip44.NONCE_SIZE)
        val ciphertext = file.copyOfRange(1 + Nip44.NONCE_SIZE, file.size - MAC_SIZE)
        val mac = file.copyOfRange(file.size - MAC_SIZE, file.size)
        val keys = Nip44.messageKeys(key, nonce)
        try {
            if (!constantTimeEquals(HmacSha256.compute(keys.hmacKey, nonce, ciphertext), mac)) throw MediaCipherException("Invalid MAC")
            val padded = ChaCha20.process(keys.chachaKey, keys.chachaNonce, ciphertext)
            try {
                return Nip44.unpad(padded, MAX_PLAINTEXT_BYTES)
            } catch (e: Nip44Exception) {
                throw MediaCipherException("Invalid padding", e)
            } finally {
                padded.fill(0)
            }
        } finally {
            keys.wipe()
        }
    }
}
