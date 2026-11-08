package org.politisationpopulairefrancaise.core.archive

/**
 * What a branch of a proof ends with (python-opentimestamps, `opentimestamps/core/notary.py`):
 * an 8-byte tag, then the payload as variable-length bytes.
 */
sealed class OtsAttestation {
    abstract val tag: ByteArray

    /** Not yet in a blockchain: ask [calendarUri] later for the rest of the proof. */
    class Pending(val calendarUri: String) : OtsAttestation() {
        override val tag: ByteArray get() = PENDING_TAG.copyOf()
    }

    /** The message is the Merkle root of the Bitcoin block at [height]. */
    class BitcoinBlockHeader(val height: Long) : OtsAttestation() {
        override val tag: ByteArray get() = BITCOIN_TAG.copyOf()
    }

    /** Same for Litecoin; read and kept, not verified by PPF. */
    class LitecoinBlockHeader(val height: Long) : OtsAttestation() {
        override val tag: ByteArray get() = LITECOIN_TAG.copyOf()
    }

    /** Any other notary: kept byte for byte, never trusted. */
    class Unknown(private val rawTag: ByteArray, val payload: ByteArray) : OtsAttestation() {
        override val tag: ByteArray get() = rawTag.copyOf()
    }

    internal fun payload(): ByteArray = when (this) {
        is Pending -> OtsWriter().apply { writeVarBytes(calendarUri.encodeToByteArray()) }.toByteArray()
        is BitcoinBlockHeader -> OtsWriter().apply { writeVarUInt(height) }.toByteArray()
        is LitecoinBlockHeader -> OtsWriter().apply { writeVarUInt(height) }.toByteArray()
        is Unknown -> payload
    }

    companion object {
        val PENDING_TAG: ByteArray = "83dfe30d2ef90c8e".hexToByteArray()
        val BITCOIN_TAG: ByteArray = "0588960d73d71901".hexToByteArray()
        val LITECOIN_TAG: ByteArray = "06869a0d73d71b45".hexToByteArray()

        const val MAX_PAYLOAD_SIZE: Int = 8192
        const val MAX_URI_LENGTH: Int = 1000
        private const val MAX_HEIGHT: Long = 0xffffffffL

        /** Characters allowed in a calendar address, as in the official library. */
        private val URI_CHARACTERS: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '.', '_', '/', ':')).toSet()

        internal fun read(reader: OtsReader): OtsAttestation {
            val tag = reader.readBytes(8)
            val payload = reader.readVarBytes(MAX_PAYLOAD_SIZE)
            val inner = OtsReader(payload)
            val attestation = when {
                tag.contentEquals(PENDING_TAG) -> {
                    val uri = inner.readVarBytes(MAX_URI_LENGTH).decodeToString()
                    if (uri.any { it !in URI_CHARACTERS }) throw OtsFormatError("invalid calendar address")
                    Pending(uri)
                }
                tag.contentEquals(BITCOIN_TAG) -> BitcoinBlockHeader(inner.readVarUInt().also { if (it > MAX_HEIGHT) throw OtsFormatError("height too large") })
                tag.contentEquals(LITECOIN_TAG) -> LitecoinBlockHeader(inner.readVarUInt().also { if (it > MAX_HEIGHT) throw OtsFormatError("height too large") })
                else -> return Unknown(tag, payload)
            }
            inner.requireEnd()
            return attestation
        }
    }
}
