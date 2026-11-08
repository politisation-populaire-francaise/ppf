package org.politisationpopulairefrancaise.core.contact

import fr.acinq.bitcoin.Crypto
import kotlinx.coroutines.yield
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * NIP-13 proof of work (https://github.com/nostr-protocol/nips/blob/master/13.md): the difficulty of an event
 * is the number of leading zero bits of its id; the author reaches it by changing a `nonce` tag,
 * `["nonce", "<counter>", "<target>"]`, whose third entry commits to the target, so that a lucky event mined
 * for a lower target does not count.
 *
 * The server asks it for the creation of a case (`PPF_CASE_POW_BITS`, 16 by default): a self-hosted brake on
 * floods that needs no key of the person (cahier des charges, section 5.4). Mining hashes the whole event at
 * each attempt (the tags come before the content), about 2^bits times on average: run it off the thread of
 * the interface.
 */
object ProofOfWork {
    const val TAG: String = "nonce"

    /** The largest target the miner accepts (beyond, an attempt would take years). */
    const val MAX_MINED_BITS: Int = 40

    /** The counter is written on a fixed number of digits, so that only those bytes change between attempts. */
    const val NONCE_DIGITS: Int = 16

    private const val YIELD_EVERY: Long = 1024

    /** Leading zero bits of a lowercase hexadecimal id; counting stops at the first character that is not hexadecimal. */
    fun difficulty(idHex: String): Int {
        var bits = 0
        for (char in idHex) {
            val nibble = char.digitToIntOrNull(16)?.takeIf { char !in 'A'..'F' } ?: return bits
            if (nibble == 0) {
                bits += 4
            } else {
                // A nibble holds 4 of the 32 bits of an Int.
                return bits + nibble.countLeadingZeroBits() - (Int.SIZE_BITS - 4)
            }
        }
        return bits
    }

    /** Leading zero bits of a digest. */
    fun difficulty(digest: ByteArray): Int {
        var bits = 0
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            if (value == 0) {
                bits += 8
            } else {
                return bits + value.countLeadingZeroBits() - (Int.SIZE_BITS - 8)
            }
        }
        return bits
    }

    /** The target an event commits to in its single `nonce` tag; null without such a tag or with a malformed one. */
    fun committedTarget(tags: List<List<String>>): Int? {
        val nonces = tags.filter { it.firstOrNull() == TAG }
        if (nonces.size != 1) return null
        val tag = nonces.single()
        if (tag.size < 3) return null
        return tag[2].toIntOrNull()?.takeIf { it in 0..256 }
    }

    /** Whether [event] carries at least [bits] bits of work, committed in its `nonce` tag (always true for 0). */
    fun satisfies(event: SignedEvent, bits: Int): Boolean {
        if (bits <= 0) return true
        val committed = committedTarget(event.tags) ?: return false
        return committed >= bits && difficulty(event.id) >= bits
    }

    /**
     * Returns [event] with a `nonce` tag (replacing any) that gives its id at least [bits] leading zero bits.
     * Suspends now and then, so that it can be cancelled; nothing is signed here (the author signs the
     * result). With 0 bits, the event comes back without a nonce tag.
     */
    suspend fun mine(event: UnsignedEvent, bits: Int, startNonce: Long = 0): UnsignedEvent {
        require(bits in 0..MAX_MINED_BITS) { "The target is between 0 and $MAX_MINED_BITS bits" }
        require(startNonce >= 0) { "The counter starts at 0 or more" }
        val base = event.tags.filterNot { it.firstOrNull() == TAG }
        if (bits == 0) return event.copy(tags = base)
        val target = bits.toString()
        val template = withNonce(event, base, nonceText(0), target)
        val canonical = template.canonicalJson()
        // The tags come before the content: the first occurrence of the nonce tag is the tag itself.
        val marker = "[\"$TAG\",\"${nonceText(0)}\",\"$target\"]"
        val markerAt = canonical.indexOf(marker)
        check(markerAt > 0) { "The nonce tag is in the serialization" }
        val digitsAt = canonical.substring(0, markerAt + "[\"$TAG\",\"".length).encodeToByteArray().size
        val bytes = canonical.encodeToByteArray()
        var nonce = startNonce
        while (true) {
            writeDigits(bytes, digitsAt, nonce)
            if (difficulty(Crypto.sha256(bytes)) >= bits) {
                val mined = withNonce(event, base, nonceText(nonce), target)
                check(difficulty(mined.idHex()) >= bits) { "The mined event keeps its work" }
                return mined
            }
            nonce++
            if (nonce % YIELD_EVERY == 0L) yield()
        }
    }

    /** [event] with [base] tags and a nonce tag of the fixed width, as the miner writes it (to measure its size before mining). */
    fun withPlaceholder(event: UnsignedEvent, bits: Int): UnsignedEvent =
        withNonce(event, event.tags.filterNot { it.firstOrNull() == TAG }, nonceText(0), bits.toString())

    private fun withNonce(event: UnsignedEvent, base: List<List<String>>, nonce: String, target: String): UnsignedEvent =
        event.copy(tags = base + listOf(listOf(TAG, nonce, target)))

    private fun nonceText(nonce: Long): String = nonce.toString().padStart(NONCE_DIGITS, '0')

    private fun writeDigits(bytes: ByteArray, at: Int, nonce: Long) {
        var value = nonce
        for (index in NONCE_DIGITS - 1 downTo 0) {
            bytes[at + index] = ('0'.code + (value % 10).toInt()).toByte()
            value /= 10
        }
        require(value == 0L) { "The counter has more than $NONCE_DIGITS digits" }
    }
}
