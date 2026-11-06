package org.politisationpopulairefrancaise.core.messaging

import fr.acinq.secp256k1.Secp256k1
import org.politisationpopulairefrancaise.core.crypto.ChaCha20
import org.politisationpopulairefrancaise.core.crypto.HkdfSha256
import org.politisationpopulairefrancaise.core.crypto.HmacSha256
import org.politisationpopulairefrancaise.core.crypto.constantTimeEquals
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import kotlin.io.encoding.Base64

/** Anything wrong with a NIP-44 payload or key: the message is simply not readable. */
class Nip44Exception(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * NIP-44 version 2 encrypted payloads (https://github.com/nostr-protocol/nips/blob/master/44.md):
 * secp256k1 ECDH, HKDF-SHA-256, length-hiding padding, ChaCha20, HMAC-SHA-256, base64.
 *
 * This is the encryption layer of private messages (decision D-027). It gives confidentiality and
 * integrity between two keys but, as the NIP itself says, neither forward secrecy nor deniability:
 * the move to MLS planned behind the messaging interface will bring those later.
 *
 * Plaintext size: the NIP allows up to 2^32 - 1 bytes and lets implementations enforce their own
 * maximum. Ours is [MAX_PLAINTEXT_BYTES] (64 KiB - 1) by default: chat messages are small, the
 * server refuses larger events anyway, and a bounded size protects memory on phones. Payloads
 * larger than the limit are rejected before being decoded.
 */
object Nip44 {
    const val VERSION: Int = 2
    const val NONCE_SIZE: Int = 32
    const val CONVERSATION_KEY_SIZE: Int = 32
    const val MIN_PLAINTEXT_BYTES: Int = 1
    const val MAX_PLAINTEXT_BYTES: Int = 65535

    private const val MAC_SIZE = 32
    private const val MIN_PAYLOAD_CHARS = 132
    private const val MIN_PAYLOAD_BYTES = 99
    private const val EXTENDED_PREFIX_THRESHOLD = 65536L
    private const val SPEC_MAX_PLAINTEXT_BYTES = 4294967295L
    private val SALT = "nip44-v2".encodeToByteArray()

    /**
     * The long-term key shared by two identities: HKDF-extract over the x coordinate of the ECDH
     * point `secretKey * publicKey`, which is the same from either side. The secret stays inside
     * [keys]; the result must be treated like a secret too.
     */
    fun conversationKey(keys: IdentityKeys, other: PublicKey): ByteArray {
        val secret = keys.secretKey()
        try {
            return conversationKey(secret, other.bytes)
        } finally {
            secret.fill(0)
        }
    }

    /** Same as above from raw keys: a 32-byte secret key and the 32-byte x-only public key of the other party. */
    fun conversationKey(secretKey: ByteArray, publicKey: ByteArray): ByteArray {
        if (secretKey.size != 32 || !Secp256k1.secKeyVerify(secretKey)) throw Nip44Exception("Invalid secret key")
        if (publicKey.size != 32) throw Nip44Exception("Public key must be 32 bytes")
        // x-only keys are even-y points by convention (BIP-340); only the x coordinate of the result is used.
        val compressed = ByteArray(33).also { it[0] = 0x02; publicKey.copyInto(it, 1) }
        val shared = try {
            Secp256k1.pubKeyTweakMul(compressed, secretKey)
        } catch (e: Exception) {
            throw Nip44Exception("Public key is not a valid secp256k1 point", e)
        }
        if (shared.size != 33 && shared.size != 65) throw Nip44Exception("Unexpected ECDH result")
        val sharedX = shared.copyOfRange(1, 33)
        val key = HkdfSha256.extract(SALT, sharedX)
        sharedX.fill(0)
        shared.fill(0)
        return key
    }

    /**
     * Encrypts [plaintext] for a conversation. The nonce must be fresh randomness for every message;
     * it is only a parameter so that the official test vectors can be replayed.
     */
    fun encrypt(
        plaintext: String,
        conversationKey: ByteArray,
        nonce: ByteArray = secureRandomBytes(NONCE_SIZE),
        maxPlaintextBytes: Int = MAX_PLAINTEXT_BYTES,
    ): String {
        val keys = messageKeys(conversationKey, nonce)
        try {
            val padded = pad(plaintext.encodeToByteArray(), maxPlaintextBytes)
            val ciphertext = ChaCha20.process(keys.chachaKey, keys.chachaNonce, padded)
            padded.fill(0)
            val mac = HmacSha256.compute(keys.hmacKey, nonce, ciphertext)
            val payload = ByteArray(1 + NONCE_SIZE + ciphertext.size + MAC_SIZE)
            payload[0] = VERSION.toByte()
            nonce.copyInto(payload, 1)
            ciphertext.copyInto(payload, 1 + NONCE_SIZE)
            mac.copyInto(payload, 1 + NONCE_SIZE + ciphertext.size)
            return Base64.encode(payload)
        } finally {
            keys.wipe()
        }
    }

    /**
     * Decrypts a payload, checking in order: version, sizes, authentication code (constant time),
     * padding. Any failure is a [Nip44Exception]; nothing partial is ever returned.
     */
    fun decrypt(payload: String, conversationKey: ByteArray, maxPlaintextBytes: Int = MAX_PLAINTEXT_BYTES): String {
        val (nonce, ciphertext, mac) = decodePayload(payload, maxPlaintextBytes)
        val keys = messageKeys(conversationKey, nonce)
        try {
            val expectedMac = HmacSha256.compute(keys.hmacKey, nonce, ciphertext)
            if (!constantTimeEquals(expectedMac, mac)) throw Nip44Exception("Invalid MAC")
            val padded = ChaCha20.process(keys.chachaKey, keys.chachaNonce, ciphertext)
            val plaintext = unpad(padded, maxPlaintextBytes)
            padded.fill(0)
            return try {
                plaintext.decodeToString(throwOnInvalidSequence = true)
            } catch (e: CharacterCodingException) {
                throw Nip44Exception("Plaintext is not valid UTF-8", e)
            }
        } finally {
            keys.wipe()
        }
    }

    internal class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray) {
        fun wipe() {
            chachaKey.fill(0)
            chachaNonce.fill(0)
            hmacKey.fill(0)
        }
    }

    /** HKDF-expand(conversation key, info = nonce, 76 bytes) sliced into the three per-message keys. */
    internal fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        if (conversationKey.size != CONVERSATION_KEY_SIZE) throw Nip44Exception("Conversation key must be 32 bytes")
        if (nonce.size != NONCE_SIZE) throw Nip44Exception("Nonce must be 32 bytes")
        val material = HkdfSha256.expand(conversationKey, nonce, 76)
        val keys = MessageKeys(material.copyOfRange(0, 32), material.copyOfRange(32, 44), material.copyOfRange(44, 76))
        material.fill(0)
        return keys
    }

    /** Padded length of a plaintext of [unpaddedLength] bytes: 32 up to 32 bytes, then power-of-two chunks. */
    internal fun calcPaddedLength(unpaddedLength: Long): Long {
        if (unpaddedLength < MIN_PLAINTEXT_BYTES) throw Nip44Exception("Invalid plaintext length")
        if (unpaddedLength <= 32) return 32
        val nextPower = 1L shl (63 - (unpaddedLength - 1).countLeadingZeroBits() + 1)
        val chunk = if (nextPower <= 256) 32L else nextPower / 8
        return chunk * ((unpaddedLength - 1) / chunk + 1)
    }

    private fun prefixLength(unpaddedLength: Long): Int = if (unpaddedLength >= EXTENDED_PREFIX_THRESHOLD) 6 else 2

    internal fun pad(plaintext: ByteArray, maxPlaintextBytes: Int = MAX_PLAINTEXT_BYTES): ByteArray {
        val length = plaintext.size.toLong()
        if (length < MIN_PLAINTEXT_BYTES || length > maxPlaintextBytes || length > SPEC_MAX_PLAINTEXT_BYTES) {
            throw Nip44Exception("Invalid plaintext length")
        }
        val prefix = prefixLength(length)
        val padded = ByteArray(prefix + calcPaddedLength(length).toInt())
        if (prefix == 2) {
            padded[0] = (length ushr 8).toByte()
            padded[1] = length.toByte()
        } else {
            padded[2] = (length ushr 24).toByte()
            padded[3] = (length ushr 16).toByte()
            padded[4] = (length ushr 8).toByte()
            padded[5] = length.toByte()
        }
        plaintext.copyInto(padded, prefix)
        return padded
    }

    internal fun unpad(padded: ByteArray, maxPlaintextBytes: Int = MAX_PLAINTEXT_BYTES): ByteArray {
        if (padded.size < 2) throw Nip44Exception("Invalid padding")
        val firstTwo = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        val length: Long
        val prefix: Int
        if (firstTwo == 0) {
            if (padded.size < 6) throw Nip44Exception("Invalid padding")
            length = ((padded[2].toLong() and 0xff) shl 24) or ((padded[3].toLong() and 0xff) shl 16) or
                ((padded[4].toLong() and 0xff) shl 8) or (padded[5].toLong() and 0xff)
            if (length < EXTENDED_PREFIX_THRESHOLD) throw Nip44Exception("Invalid padding")
            prefix = 6
        } else {
            length = firstTwo.toLong()
            prefix = 2
        }
        if (length > maxPlaintextBytes) throw Nip44Exception("Plaintext larger than the accepted maximum")
        if (padded.size.toLong() != prefix + calcPaddedLength(length)) throw Nip44Exception("Invalid padding")
        return padded.copyOfRange(prefix, prefix + length.toInt())
    }

    /** Largest base64 payload that can carry [maxPlaintextBytes] bytes; anything longer is refused unread. */
    internal fun maxPayloadChars(maxPlaintextBytes: Int): Int {
        val max = maxPlaintextBytes.toLong()
        val rawSize = 1 + NONCE_SIZE + prefixLength(max) + calcPaddedLength(max) + MAC_SIZE
        return ((rawSize + 2) / 3 * 4).toInt()
    }

    internal fun decodePayload(payload: String, maxPlaintextBytes: Int = MAX_PLAINTEXT_BYTES): Triple<ByteArray, ByteArray, ByteArray> {
        if (payload.isEmpty() || payload[0] == '#') throw Nip44Exception("Unsupported encryption version")
        if (payload.length < MIN_PAYLOAD_CHARS) throw Nip44Exception("Invalid payload size")
        if (payload.length > maxPayloadChars(maxPlaintextBytes)) throw Nip44Exception("Payload larger than the accepted maximum")
        val data = try {
            Base64.decode(payload)
        } catch (e: IllegalArgumentException) {
            throw Nip44Exception("Invalid base64", e)
        }
        if (data.size < MIN_PAYLOAD_BYTES) throw Nip44Exception("Invalid payload size")
        if (data[0].toInt() != VERSION) throw Nip44Exception("Unsupported encryption version ${data[0].toInt() and 0xff}")
        val nonce = data.copyOfRange(1, 1 + NONCE_SIZE)
        val ciphertext = data.copyOfRange(1 + NONCE_SIZE, data.size - MAC_SIZE)
        val mac = data.copyOfRange(data.size - MAC_SIZE, data.size)
        return Triple(nonce, ciphertext, mac)
    }
}
