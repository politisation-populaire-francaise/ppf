package org.politisationpopulairefrancaise.core.urne

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * Proof of work (D-110, section 5 of the design), self-hosted: no third party, no CAPTCHA.
 *
 * - On events, NIP-13: the tag `["nonce", "<counter>", "<target>"]`, the difficulty being the number
 *   of leading zero bits of the event id; the announced target must be at least the required one.
 * - On anonymous ballots, the same count on a SHA-256 of the ballot ([Ballot.powDigest]).
 */
object ProofOfWork {

    /** Number of leading zero bits of [hash]. */
    fun leadingZeroBits(hash: ByteArray): Int {
        var count = 0
        for (byte in hash) {
            val value = byte.toInt() and 0xff
            if (value == 0) {
                count += 8
                continue
            }
            count += value.countLeadingZeroBits() - 24
            break
        }
        return count
    }

    /** The difficulty an event really has, and the one it announces (NIP-13). */
    fun difficultyOf(event: SignedEvent): Int = runCatching { leadingZeroBits(event.id.hexToByteArray()) }.getOrDefault(0)

    fun announcedTarget(event: SignedEvent): Int? =
        event.tags.firstOrNull { it.size >= 3 && it[0] == NONCE_TAG }?.get(2)?.toIntOrNull()

    /** The event reaches [required] bits and says so in its nonce tag. Its signature is checked elsewhere. */
    fun meets(event: SignedEvent, required: Int): Boolean {
        if (required <= 0) return true
        val target = announcedTarget(event) ?: return false
        return target >= required && difficultyOf(event) >= required
    }

    /**
     * Adds a nonce tag and counts from [start] until the id reaches [difficulty] bits (then the event
     * is signed). With a random [start], two events otherwise identical never get the same id.
     */
    fun mine(event: UnsignedEvent, difficulty: Int, start: Long = 0): UnsignedEvent {
        val others = event.tags.filterNot { it.isNotEmpty() && it[0] == NONCE_TAG }
        var counter = start
        while (true) {
            val candidate = event.copy(tags = others + listOf(listOf(NONCE_TAG, counter.toString(), difficulty.toString())))
            if (difficulty <= 0 || leadingZeroBits(candidate.id()) >= difficulty) return candidate
            counter++
        }
    }

    /** Smallest nonce giving [digestOf] at least [difficulty] leading zero bits. */
    fun mineNonce(difficulty: Int, digestOf: (Long) -> ByteArray): Long {
        var nonce = 0L
        while (difficulty > 0 && leadingZeroBits(digestOf(nonce)) < difficulty) nonce++
        return nonce
    }

    internal fun sha256(bytes: ByteArray): ByteArray = Crypto.sha256(bytes)

    const val NONCE_TAG: String = "nonce"
}
