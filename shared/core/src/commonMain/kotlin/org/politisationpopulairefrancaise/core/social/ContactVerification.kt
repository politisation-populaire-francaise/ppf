package org.politisationpopulairefrancaise.core.social

import fr.acinq.bitcoin.Crypto
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The safety number of a conversation between two accounts (S-128): twelve groups of five digits
 * that both devices compute alike from the two public keys, whatever the order. If the two people
 * read the same digits to each other, face to face or on a call they trust, each has the real key
 * of the other, and nobody slipped in between (an impostor's key would give other digits).
 *
 * On Nostr the public key is the identity: it never "changes" for a given account. A changed key
 * means another account, and the application shows it as an unverified contact.
 */
object SafetyNumber {
    const val GROUPS: Int = 12
    private const val DOMAIN = "ppf-safety-number-v1"

    /** The groups of digits for the pair (order-independent); both keys are 64-character lowercase hex. */
    fun of(firstHex: String, secondHex: String): List<String> {
        require(isLowercaseHex(firstHex, 64) && isLowercaseHex(secondHex, 64)) { "Keys are 64-character lowercase hexadecimal public keys" }
        val (low, high) = listOf(firstHex, secondHex).sorted()
        // 64 bytes of digest (two SHA-256 with a counter), of which 12 groups of 3 bytes are used:
        // 24 bits per group reduced to 5 digits, a negligible bias for a comparison made by humans.
        val material = digest(0, low, high) + digest(1, low, high)
        return (0 until GROUPS).map { group ->
            val offset = group * 3
            val value = ((material[offset].toInt() and 0xff) shl 16) or ((material[offset + 1].toInt() and 0xff) shl 8) or (material[offset + 2].toInt() and 0xff)
            (value % 100_000).toString().padStart(5, '0')
        }
    }

    /** The safety number as people read it: groups separated by spaces. */
    fun display(firstHex: String, secondHex: String): String = of(firstHex, secondHex).joinToString(" ")

    private fun digest(counter: Int, low: String, high: String): ByteArray =
        Crypto.sha256("$DOMAIN|$counter|$low|$high".encodeToByteArray())
}

/** The contacts the user checked in person, on the device only. */
class ContactVerificationStore(private val database: PpfDatabase, private val clock: () -> Long = ::currentEpochSeconds) {

    private val queries get() = database.contactsQueries

    fun markVerified(pubkeyHex: String) {
        require(FollowStore.isValidKey(pubkeyHex)) { "A contact key must be 64 lowercase hexadecimal characters" }
        queries.markVerified(pubkeyHex, clock())
    }

    fun unmarkVerified(pubkeyHex: String) = queries.unmarkVerified(pubkeyHex)

    fun isVerified(pubkeyHex: String): Boolean = queries.verifiedAt(pubkeyHex).executeAsOneOrNull() != null

    fun verifiedAt(pubkeyHex: String): Long? = queries.verifiedAt(pubkeyHex).executeAsOneOrNull()

    fun all(): List<String> = queries.selectVerified().executeAsList()

    /** Forgets every verification: called when the keys are erased from the device. */
    fun clear() = queries.deleteAllVerifications()
}
