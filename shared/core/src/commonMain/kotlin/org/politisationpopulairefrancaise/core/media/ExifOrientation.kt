package org.politisationpopulairefrancaise.core.media

/**
 * The orientation of a photo (EXIF tag 0x0112, values 1 to 8): phones store the picture as the
 * sensor saw it and write in the metadata how to turn it. When the metadata go, the orientation is
 * either kept alone in a minimal EXIF block (lossless cleaning, [MinimalExif]) or applied to the
 * pixels themselves (re-encoding, [ImageTransforms.applyOrientation]).
 */
object ExifOrientation {
    const val NORMAL: Int = 1
    const val TAG: Int = 0x0112

    fun isValid(value: Int): Boolean = value in 1..8

    /**
     * Reads the orientation from a TIFF structure (the body of an EXIF block after "Exif\0\0", or
     * the body of a PNG `eXIf` chunk). Never throws: anything unreadable answers [NORMAL].
     */
    fun fromTiff(tiff: ByteArray, offset: Int = 0, length: Int = tiff.size - offset): Int {
        val end = offset + length
        if (length < 8 || end > tiff.size) return NORMAL
        val littleEndian = when (tiff.ascii(offset, 2)) {
            "II" -> true
            "MM" -> false
            else -> return NORMAL
        }
        fun u16(at: Int): Int = if (at < offset || at + 2 > end) -1 else if (littleEndian) {
            (tiff[at].toInt() and 0xFF) or ((tiff[at + 1].toInt() and 0xFF) shl 8)
        } else {
            ((tiff[at].toInt() and 0xFF) shl 8) or (tiff[at + 1].toInt() and 0xFF)
        }
        fun u32(at: Int): Long {
            val high = u16(if (littleEndian) at + 2 else at)
            val low = u16(if (littleEndian) at else at + 2)
            return if (high < 0 || low < 0) -1 else (high.toLong() shl 16) or low.toLong()
        }
        if (u16(offset + 2) != 42) return NORMAL
        val ifd = u32(offset + 4)
        if (ifd < 8 || ifd > length - 2) return NORMAL
        val start = offset + ifd.toInt()
        val count = u16(start)
        if (count <= 0) return NORMAL
        for (index in 0 until minOf(count, MAX_ENTRIES)) {
            val entry = start + 2 + index * 12
            if (entry + 12 > end) return NORMAL
            if (u16(entry) == TAG && u16(entry + 2) == TYPE_SHORT) {
                val value = u16(entry + 8)
                return if (isValid(value)) value else NORMAL
            }
        }
        return NORMAL
    }

    internal const val TYPE_SHORT: Int = 3
    private const val MAX_ENTRIES = 512
}

/** The smallest TIFF structure that says only how to turn the picture: one entry, nothing else. */
internal object MinimalExif {
    /** "Exif\0\0", the identifier of an EXIF block in JPEG. */
    val JPEG_IDENTIFIER: ByteArray = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)

    /** Big-endian TIFF header, one IFD with the orientation as its only entry, no next IFD. */
    fun tiff(orientation: Int): ByteArray {
        require(ExifOrientation.isValid(orientation)) { "Orientation must be 1 to 8" }
        return byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // "MM", 42, IFD at offset 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, ExifOrientation.TYPE_SHORT.toByte(), 0x00, 0x00, 0x00, 0x01, // tag 0x0112, SHORT, count 1
            0x00, orientation.toByte(), 0x00, 0x00, // value, padded to four bytes
            0x00, 0x00, 0x00, 0x00, // no next IFD
        )
    }
}
