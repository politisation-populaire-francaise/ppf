package org.politisationpopulairefrancaise.core.archive

import fr.acinq.bitcoin.crypto.Digest

/**
 * An 80-byte Bitcoin block header, as an explorer returns it (`GET /block/<hash>/header` of the
 * Esplora API, in hexadecimal). What an OpenTimestamps proof needs from it: the Merkle root
 * (bytes 36 to 67, in the stored order, which is the order of the proof), the block time and the
 * proof of work that ties the header to its hash.
 */
class BitcoinHeader(raw: ByteArray) {
    private val bytes: ByteArray = raw.copyOf()

    init {
        if (bytes.size != SIZE) throw OtsFormatError("a block header is $SIZE bytes")
    }

    /** The Merkle root exactly as stored: what the last message of a proof must equal. */
    val merkleRoot: ByteArray get() = bytes.copyOfRange(36, 68)

    /** Block time, in seconds since 1970 (accurate to an hour or two, like every block time). */
    val time: Long get() = littleEndian(68)

    /** Compact difficulty target (`nBits`). */
    val bits: Long get() = littleEndian(72)

    /** Double SHA-256 of the header, in the internal byte order. */
    val hash: ByteArray get() = Digest.sha256().hash(Digest.sha256().hash(bytes))

    /** The block hash as explorers write it (bytes reversed, hexadecimal). */
    val hashHex: String get() = hash.reversedArray().toHexString()

    /** The header's hash is below the target its `nBits` announces (it really was mined). */
    fun meetsItsTarget(): Boolean {
        val exponent = (bits ushr 24).toInt()
        val mantissa = bits and 0x00ffffffL
        // A negative, zero or overflowing target is never valid.
        if (mantissa == 0L || mantissa and 0x00800000L != 0L || exponent < 3 || exponent > 32) return false
        val target = ByteArray(32)
        val start = 32 - exponent
        target[start] = (mantissa ushr 16).toByte()
        target[start + 1] = (mantissa ushr 8).toByte()
        target[start + 2] = mantissa.toByte()
        val value = hash.reversedArray() // big-endian
        for (i in 0 until 32) {
            val a = value[i].toInt() and 0xff
            val b = target[i].toInt() and 0xff
            if (a != b) return a < b
        }
        return true
    }

    fun toByteArray(): ByteArray = bytes.copyOf()

    private fun littleEndian(offset: Int): Long =
        (0 until 4).fold(0L) { acc, i -> acc or ((bytes[offset + i].toLong() and 0xff) shl (8 * i)) }

    companion object {
        const val SIZE: Int = 80
    }
}

/** What the check of one Bitcoin attestation found. */
enum class BitcoinCheck {
    /** The header matches the announced block hash, was mined, and holds the proof's message. */
    VERIFIED,

    /** The two explorers did not give the same header: nothing is concluded, the team is told. */
    SOURCES_DISAGREE,

    /** The header does not hash to the block hash given for this height. */
    WRONG_BLOCK,

    /** The header's hash is not below its target. */
    NOT_MINED,

    /** The block is genuine but its Merkle root is not the proof's message: the proof is false. */
    WRONG_MERKLE_ROOT,
}

/**
 * Checks of an OpenTimestamps proof against Bitcoin (D-111). The device recomputes the path of
 * the proof ([OtsTimestamp.allAttestations]); the server fetches each header from two independent
 * explorers and calls [check]; anyone can redo it with the official client and their own node.
 */
object OtsBitcoinVerifier {

    /** The heights to look up, with the message each one must hold (uncomputable branches left out). */
    fun bitcoinAttestations(timestamp: OtsTimestamp): List<Pair<Long, ByteArray>> =
        timestamp.allAttestations().mapNotNull { (message, attestation) ->
            if (attestation is OtsAttestation.BitcoinBlockHeader && message != null) attestation.height to message else null
        }

    /**
     * [message] attested at a height whose block hash is [blockHashHex] (as explorers write it);
     * [first] and [second] are that block's header from two independent explorers.
     */
    fun check(message: ByteArray, blockHashHex: String, first: BitcoinHeader, second: BitcoinHeader): BitcoinCheck = when {
        !first.toByteArray().contentEquals(second.toByteArray()) -> BitcoinCheck.SOURCES_DISAGREE
        first.hashHex != blockHashHex.lowercase() -> BitcoinCheck.WRONG_BLOCK
        !first.meetsItsTarget() -> BitcoinCheck.NOT_MINED
        !first.merkleRoot.contentEquals(message) -> BitcoinCheck.WRONG_MERKLE_ROOT
        else -> BitcoinCheck.VERIFIED
    }
}
