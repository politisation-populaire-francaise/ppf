package org.politisationpopulairefrancaise.core.archive

import fr.acinq.bitcoin.crypto.Digest

/**
 * One operation of an OpenTimestamps proof (python-opentimestamps, `opentimestamps/core/op.py`).
 * Each one turns a message into the next; a proof is a tree of them ending in attestations.
 */
sealed class OtsOperation(val tag: Int) {
    /** The result, or null when this platform cannot compute it (Keccak-256, only on Ethereum branches). */
    abstract fun apply(message: ByteArray): ByteArray?

    /** `f0`: the argument appended after the message. */
    class Append(val argument: ByteArray) : OtsOperation(0xf0) {
        override fun apply(message: ByteArray): ByteArray = checkResult(message + argument)
    }

    /** `f1`: the argument put before the message. */
    class Prepend(val argument: ByteArray) : OtsOperation(0xf1) {
        override fun apply(message: ByteArray): ByteArray = checkResult(argument + message)
    }

    /** `f2`: the message reversed (deprecated, still readable). */
    data object Reverse : OtsOperation(0xf2) {
        override fun apply(message: ByteArray): ByteArray = message.reversedArray()
    }

    /** `f3`: the lowercase hexadecimal text of the message (at most 2 048 bytes in). */
    data object Hexlify : OtsOperation(0xf3) {
        override fun apply(message: ByteArray): ByteArray {
            if (message.size > MAX_HEXLIFY_INPUT) throw OtsFormatError("hexlify input too long")
            return message.toHexString().encodeToByteArray()
        }
    }

    /** `08`: SHA-256, the operation of every recent proof. */
    data object Sha256 : OtsOperation(0x08), Hashing {
        override val digestLength: Int = 32
        override fun apply(message: ByteArray): ByteArray = Digest.sha256().hash(message)
    }

    /** `03`: RIPEMD-160, in proofs of 2015 and 2016. */
    data object Ripemd160 : OtsOperation(0x03), Hashing {
        override val digestLength: Int = 20
        override fun apply(message: ByteArray): ByteArray = Digest.ripemd160().hash(message)
    }

    /** `02`: SHA-1, only as the hash of an old file. */
    data object Sha1 : OtsOperation(0x02), Hashing {
        override val digestLength: Int = 20
        override fun apply(message: ByteArray): ByteArray = Digest.sha1().hash(message)
    }

    /** `67`: Keccak-256, used on Ethereum branches only; kept, never computed here. */
    data object Keccak256 : OtsOperation(0x67), Hashing {
        override val digestLength: Int = 32
        override fun apply(message: ByteArray): ByteArray? = null
    }

    /** The hashing operations, the only ones allowed as the hash of the timestamped file. */
    sealed interface Hashing {
        val digestLength: Int
    }

    /** Same operation and same argument (an operation appears at most once under a node). */
    fun sameAs(other: OtsOperation): Boolean = tag == other.tag && argumentOrNull().contentEquals(other.argumentOrNull())

    internal fun argumentOrNull(): ByteArray? = when (this) {
        is Append -> argument
        is Prepend -> argument
        else -> null
    }

    companion object {
        const val MAX_MESSAGE_LENGTH: Int = 4096
        const val MAX_RESULT_LENGTH: Int = 4096
        private const val MAX_HEXLIFY_INPUT: Int = 2048

        private fun checkResult(result: ByteArray): ByteArray {
            if (result.size > MAX_RESULT_LENGTH) throw OtsFormatError("result too long")
            return result
        }

        /** The operation of a tag, reading its argument when it has one. */
        internal fun read(tag: Int, reader: OtsReader): OtsOperation = when (tag) {
            0xf0 -> Append(reader.readVarBytes(MAX_MESSAGE_LENGTH, minLength = 1))
            0xf1 -> Prepend(reader.readVarBytes(MAX_MESSAGE_LENGTH, minLength = 1))
            0xf2 -> Reverse
            0xf3 -> Hexlify
            0x08 -> Sha256
            0x03 -> Ripemd160
            0x02 -> Sha1
            0x67 -> Keccak256
            else -> throw OtsFormatError("unknown operation 0x${tag.toString(16)}")
        }

        internal fun hashingOf(tag: Int): OtsOperation = when (tag) {
            0x08 -> Sha256
            0x03 -> Ripemd160
            0x02 -> Sha1
            0x67 -> Keccak256
            else -> throw OtsFormatError("not a hash operation: 0x${tag.toString(16)}")
        }
    }
}

/** A proof that cannot be read: bad header, unknown operation, too long, too deep, bytes left over. */
class OtsFormatError(message: String) : Exception(message)
