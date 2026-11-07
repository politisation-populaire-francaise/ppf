package org.politisationpopulairefrancaise.core.media

/**
 * Fabricated files for the tests of the chain of media: no real photo, no real person. The
 * metadata planted in them are recognizable strings ([SECRETS]) that must all disappear.
 */
object MediaSamples {
    const val GPS = "GPS 45.7640N 4.8357E"
    const val MODEL = "[EXEMPLE] Appareil Modele-X"
    const val DATE = "2026:11:05 14:32:10"
    const val SERIAL = "SN-EXEMPLE-0042"
    const val AUTHOR = "[EXEMPLE] Auteur fictif"
    const val COMMENT = "[EXEMPLE] commentaire secret"
    const val SOFTWARE = "LogicielRetouche 9.1"
    val SECRETS = listOf(GPS, MODEL, DATE, SERIAL, AUTHOR, COMMENT, SOFTWARE)

    fun latin1(text: String): ByteArray = ByteArray(text.length) { text[it].code.toByte() }

    fun contains(haystack: ByteArray, needle: String): Boolean {
        val bytes = latin1(needle)
        if (bytes.isEmpty() || bytes.size > haystack.size) return false
        outer@ for (i in 0..haystack.size - bytes.size) {
            for (j in bytes.indices) if (haystack[i + j] != bytes[j]) continue@outer
            return true
        }
        return false
    }

    /**
     * A TIFF structure as cameras write it: IFD0 with make and model (ASCII), the orientation, the
     * date, the software, an artist; the strings are stored after the IFD and found by their offsets.
     */
    fun exifTiff(orientation: Int = 6): ByteArray {
        val strings = listOf(0x010F to "$MODEL\u0000", 0x0132 to "$DATE\u0000", 0x0131 to "$SOFTWARE\u0000", 0x013B to "$AUTHOR\u0000", 0xA431 to "$SERIAL\u0000", 0x8825 to "$GPS\u0000")
        val entries = strings.size + 1
        val ifdSize = 2 + entries * 12 + 4
        var dataOffset = 8 + ifdSize
        val out = Bytes()
        out.ascii("MM"); out.u16(42); out.u32(8)
        out.u16(entries)
        // Orientation first, then the strings (type ASCII = 2).
        out.u16(0x0112); out.u16(3); out.u32(1); out.u16(orientation); out.u16(0)
        val payload = Bytes()
        for ((tag, value) in strings) {
            out.u16(tag); out.u16(2); out.u32(value.length.toLong()); out.u32(dataOffset.toLong())
            payload.ascii(value)
            dataOffset += value.length
        }
        out.u32(0)
        out.bytes(payload.toByteArray())
        return out.toByteArray()
    }

    /** JPEG segment: marker, length, payload. */
    fun segment(marker: Int, payload: ByteArray): ByteArray = Bytes().apply {
        u8(0xFF); u8(marker); u16(payload.size + 2); bytes(payload)
    }.toByteArray()

    fun exifSegment(orientation: Int = 6): ByteArray = segment(0xE1, latin1("Exif\u0000\u0000") + exifTiff(orientation))

    fun xmpSegment(): ByteArray = segment(0xE1, latin1("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta><tiff:Model>$MODEL</tiff:Model><exif:GPS>$GPS</exif:GPS></x:xmpmeta>"))

    fun iptcSegment(): ByteArray = segment(0xED, latin1("Photoshop 3.0\u00008BIM\u0004\u0004\u0000\u0000\u0000\u0000\u0000\u0010\u001c\u0002P\u0000\u000c$AUTHOR"))

    fun iccSegment(): ByteArray = segment(0xE2, latin1("ICC_PROFILE\u0000\u0001\u0001") + latin1("desc $MODEL profile"))

    fun commentSegment(): ByteArray = segment(0xFE, latin1(COMMENT))

    fun jfifWithThumbnail(): ByteArray = segment(0xE0, latin1("JFIF\u0000") + byteArrayOf(1, 2, 1, 0, 72, 0, 72, 2, 1) + ByteArray(6) { 0x7F })

    /**
     * Adds metadata to a real baseline JPEG (from a platform encoder, or [minimalJpeg]): EXIF with
     * orientation 6, XMP, IPTC, an ICC profile, a JFIF thumbnail, a comment, and a second picture
     * after the end (as motion photos and the multi-picture format do).
     */
    fun withMetadata(jpeg: ByteArray, orientation: Int = 6): ByteArray {
        require(jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        var rest = jpeg.copyOfRange(2, jpeg.size)
        // Skip the encoder's own APP0, if any, to put ours first.
        if (rest[0] == 0xFF.toByte() && rest[1] == 0xE0.toByte()) {
            val length = ((rest[2].toInt() and 0xFF) shl 8) or (rest[3].toInt() and 0xFF)
            rest = rest.copyOfRange(2 + length, rest.size)
        }
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + jfifWithThumbnail() + exifSegment(orientation) + xmpSegment() + iccSegment() +
            iptcSegment() + commentSegment() + segment(0xE9, latin1("Maker block $SERIAL")) + rest + latin1("TRAILER $GPS") + byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }

    /**
     * A structurally complete JPEG without a real picture: quantization and Huffman tables, a frame,
     * a scan whose data contain a stuffed byte and a restart marker, the end. Enough for the cleaner,
     * which never decodes.
     */
    fun minimalJpeg(): ByteArray = Bytes().apply {
        u8(0xFF); u8(0xD8)
        bytes(segment(0xDB, ByteArray(65) { if (it == 0) 0 else 1 }))
        bytes(segment(0xC0, byteArrayOf(8, 0, 16, 0, 16, 1, 1, 0x11, 0)))
        bytes(segment(0xC4, byteArrayOf(0x00) + ByteArray(16) { if (it == 0) 1 else 0 } + byteArrayOf(0)))
        bytes(segment(0xDD, byteArrayOf(0, 4)))
        bytes(segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)))
        bytes(byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56, 0xFF.toByte(), 0xD0.toByte(), 0x78, 0x9A.toByte()))
        u8(0xFF); u8(0xD9)
    }.toByteArray()

    fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = latin1(type)
        return Bytes().apply { u32(data.size.toLong()); bytes(typeBytes); bytes(data); u32(Crc32.compute(typeBytes, data)) }.toByteArray()
    }

    val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Adds text, eXIf (orientation 6), tIME, iCCP, pHYs and a private chunk to a real PNG, and data after IEND. */
    fun pngWithMetadata(png: ByteArray, orientation: Int = 6): ByteArray {
        require(png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
        val ihdrLength = 8 + 4 + 4 + 13 + 4
        val header = png.copyOfRange(0, ihdrLength)
        val rest = png.copyOfRange(ihdrLength, png.size)
        return header +
            pngChunk("tEXt", latin1("Author\u0000$AUTHOR")) +
            pngChunk("iTXt", latin1("XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<x:xmpmeta>$GPS $MODEL</x:xmpmeta>")) +
            pngChunk("zTXt", latin1("Comment\u0000\u0000") + byteArrayOf(0x78, 0x9C.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x01)) +
            pngChunk("eXIf", exifTiff(orientation)) +
            pngChunk("tIME", byteArrayOf(0x07, 0xEA.toByte(), 11, 5, 14, 32, 10)) +
            pngChunk("iCCP", latin1("$MODEL\u0000\u0000") + byteArrayOf(0x78, 0x9C.toByte())) +
            pngChunk("pHYs", byteArrayOf(0, 0, 0x0B, 0x13, 0, 0, 0x0B, 0x13, 1)) +
            pngChunk("prVt", latin1(SERIAL)) +
            rest + latin1("TRAILER $COMMENT")
    }

    /** A minimal PNG of 1 × 1 pixel (no decoding needed by the cleaner: the IDAT is copied). */
    fun minimalPng(): ByteArray = PNG_SIGNATURE +
        pngChunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0)) +
        pngChunk("IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00, 0x03, 0x01, 0x01, 0x00)) +
        pngChunk("IEND", ByteArray(0))

    fun riffChunk(type: String, data: ByteArray): ByteArray = Bytes().apply {
        bytes(latin1(type)); u32le(data.size.toLong()); bytes(data); if (data.size % 2 == 1) u8(0)
    }.toByteArray()

    /** An extended WebP (VP8X with the ICC, EXIF and XMP flags) carrying ICCP, EXIF and XMP chunks around a lossless bitstream. */
    fun webpWithMetadata(): ByteArray {
        val vp8x = byteArrayOf(0x2C, 0, 0, 0, 0, 0, 0, 0, 0, 0) // ICC (0x20), EXIF (0x08), XMP (0x04); canvas 1 × 1
        val vp8l = byteArrayOf(0x2F, 0, 0, 0, 0, 0x07) // signature, then an arbitrary bitstream (never decoded here)
        val body = latin1("WEBP") + riffChunk("VP8X", vp8x) + riffChunk("ICCP", latin1("profile $MODEL")) +
            riffChunk("VP8L", vp8l) + riffChunk("EXIF", latin1("Exif\u0000\u0000") + exifTiff(6)) + riffChunk("XMP ", latin1("<x:xmpmeta>$GPS</x:xmpmeta>")) +
            riffChunk("ZZZZ", latin1(SERIAL))
        return Bytes().apply { bytes(latin1("RIFF")); u32le(body.size.toLong()); bytes(body) }.toByteArray() + latin1("TRAILER $AUTHOR")
    }

    class Bytes {
        private val list = ArrayList<Byte>()
        fun u8(value: Int) { list.add(value.toByte()) }
        fun u16(value: Int) { u8(value ushr 8 and 0xFF); u8(value and 0xFF) }
        fun u32(value: Long) { u16((value ushr 16).toInt() and 0xFFFF); u16(value.toInt() and 0xFFFF) }
        fun u32le(value: Long) { u8(value.toInt() and 0xFF); u8((value ushr 8).toInt() and 0xFF); u8((value ushr 16).toInt() and 0xFF); u8((value ushr 24).toInt() and 0xFF) }
        fun ascii(text: String) { bytes(latin1(text)) }
        fun bytes(value: ByteArray) { value.forEach { list.add(it) } }
        fun toByteArray(): ByteArray = list.toByteArray()
    }
}
