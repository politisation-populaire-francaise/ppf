package org.politisationpopulairefrancaise.core.archive

/**
 * The byte encodings of OpenTimestamps (python-opentimestamps, `opentimestamps/core/serialize.py`):
 * unsigned integers in LEB128 (7 bits per byte, least significant first, `0x80` = more follows),
 * variable-length bytes as their length then the bytes.
 */
internal class OtsReader(private val bytes: ByteArray) {
    private var position = 0

    val remaining: Int get() = bytes.size - position

    fun readByte(): Int {
        if (position >= bytes.size) throw OtsFormatError("unexpected end of the proof")
        return bytes[position++].toInt() and 0xff
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || count > remaining) throw OtsFormatError("unexpected end of the proof")
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readVarUInt(): Long {
        var value = 0L
        var shift = 0
        for (i in 0 until MAX_VARUINT_BYTES) {
            val b = readByte()
            value = value or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return value
            shift += 7
        }
        throw OtsFormatError("integer too long")
    }

    fun readVarBytes(maxLength: Int, minLength: Int = 0): ByteArray {
        val length = readVarUInt()
        if (length < minLength || length > maxLength) throw OtsFormatError("length out of range")
        return readBytes(length.toInt())
    }

    fun requireEnd() {
        if (remaining != 0) throw OtsFormatError("bytes left after the proof")
    }

    private companion object {
        /** The official library reads at most 9 bytes for one integer. */
        const val MAX_VARUINT_BYTES = 9
    }
}

internal class OtsWriter {
    private val out = ArrayList<Byte>()

    fun writeByte(value: Int) {
        out += value.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        bytes.forEach { out += it }
    }

    fun writeVarUInt(value: Long) {
        require(value >= 0) { "unsigned integers only" }
        var rest = value
        while (true) {
            val low = (rest and 0x7f).toInt()
            rest = rest ushr 7
            if (rest == 0L) {
                writeByte(low)
                return
            }
            writeByte(low or 0x80)
        }
    }

    fun writeVarBytes(bytes: ByteArray) {
        writeVarUInt(bytes.size.toLong())
        writeBytes(bytes)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}
