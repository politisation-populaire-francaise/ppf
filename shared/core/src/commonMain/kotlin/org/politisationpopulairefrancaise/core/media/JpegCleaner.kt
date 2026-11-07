package org.politisationpopulairefrancaise.core.media

/**
 * Cleans a JPEG file (ITU T.81, JFIF, EXIF 2.3 and 3.0) segment by segment, without decoding it.
 *
 * Kept: the frame and its tables (SOF0 to SOF15, DHT, DAC, DQT, DRI, DNL, DHP, EXP), the scans with
 * their entropy-coded data and restart markers (all scans of a progressive image), a JFIF block
 * rewritten without its thumbnail, Adobe's colour transform block (APP14, needed to show CMYK and
 * YCCK images), and the orientation alone in a minimal EXIF block when asked.
 * Removed: every other APPn block (EXIF, XMP and extended XMP, ICC profiles, MPF, FlashPix, IPTC
 * and Photoshop, Ducky, maker blocks...), comments, unknown markers, and everything after the end
 * of the image (thumbnails of the multi-picture format, motion photos, depth maps, gain maps).
 */
internal object JpegCleaner {
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP0 = 0xE0
    private const val APP1 = 0xE1
    private const val APP2 = 0xE2
    private const val APP13 = 0xED
    private const val APP14 = 0xEE
    private const val COM = 0xFE

    private val JFIF = "JFIF\u0000".latin1()
    private val JFXX = "JFXX\u0000".latin1()
    private val XMP = "http://ns.adobe.com/xap/1.0/\u0000".latin1()
    private val XMP_EXTENDED = "http://ns.adobe.com/xmp/extension/\u0000".latin1()
    private val ICC = "ICC_PROFILE\u0000".latin1()
    private val ADOBE = "Adobe".latin1()

    /** Structural markers: the frame, tables and parameters a decoder needs. */
    private fun isStructural(marker: Int): Boolean =
        (marker in 0xC0..0xCF) || marker == 0xDB || marker == 0xDC || marker == 0xDD || marker == 0xDE || marker == 0xDF

    fun clean(bytes: ByteArray, keepOrientation: Boolean): CleanImage {
        if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != SOI) throw MalformedImageException("not_jpeg")
        val removed = mutableSetOf<RemovedMetadata>()
        var orientation = ExifOrientation.NORMAL
        var jfif: ByteArray? = null
        val body = ByteSink(bytes.size)
        var position = 2
        var ended = false
        var sawFrame = false
        while (position < bytes.size) {
            if (bytes.u8(position) != 0xFF) throw MalformedImageException("expected_marker")
            // Fill bytes: any number of 0xFF may precede a marker.
            while (position < bytes.size && bytes.u8(position) == 0xFF) position++
            if (position >= bytes.size) throw MalformedImageException("truncated")
            val marker = bytes.u8(position)
            position++
            when {
                marker == EOI -> {
                    ended = true
                    break
                }
                marker in 0xD0..0xD7 || marker == 0x01 -> body.writeMarker(marker) // stray restart or TEM: no length
                else -> {
                    if (position + 2 > bytes.size) throw MalformedImageException("truncated")
                    val length = bytes.u16BigEndian(position)
                    if (length < 2 || position + length > bytes.size) throw MalformedImageException("bad_length")
                    val start = position + 2
                    val end = position + length
                    when {
                        marker == SOS -> {
                            if (!sawFrame) throw MalformedImageException("scan_before_frame")
                            body.writeSegment(marker, bytes, start, end)
                            position = copyEntropyCodedData(bytes, end, body)
                            continue
                        }
                        isStructural(marker) -> {
                            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) sawFrame = true
                            body.writeSegment(marker, bytes, start, end)
                        }
                        marker == APP0 && bytes.startsWithAt(start, JFIF) && end - start >= 14 -> {
                            // Version, density unit and densities kept; the thumbnail (Xthumbnail, Ythumbnail, pixels) dropped.
                            val minimal = bytes.copyOfRange(start, start + 14).also { it[12] = 0; it[13] = 0 }
                            if (end - start > 14 || bytes.u8(start + 12) != 0 || bytes.u8(start + 13) != 0) removed += RemovedMetadata.EMBEDDED_PICTURES
                            if (jfif == null) jfif = minimal
                        }
                        marker == APP0 && bytes.startsWithAt(start, JFXX) -> removed += RemovedMetadata.EMBEDDED_PICTURES
                        marker == APP1 && bytes.startsWithAt(start, MinimalExif.JPEG_IDENTIFIER) -> {
                            val tiffStart = start + MinimalExif.JPEG_IDENTIFIER.size
                            val value = ExifOrientation.fromTiff(bytes, tiffStart, end - tiffStart)
                            if (orientation == ExifOrientation.NORMAL) orientation = value
                            // Our own minimal block (orientation only) is not metadata.
                            if (!isMinimalOrientationBlock(bytes, tiffStart, end)) removed += RemovedMetadata.EXIF
                        }
                        marker == APP1 && (bytes.startsWithAt(start, XMP) || bytes.startsWithAt(start, XMP_EXTENDED)) -> removed += RemovedMetadata.XMP
                        marker == APP2 && bytes.startsWithAt(start, ICC) -> removed += RemovedMetadata.ICC_PROFILE
                        marker == APP2 -> removed += RemovedMetadata.EMBEDDED_PICTURES // MPF, FlashPix
                        marker == APP13 -> removed += RemovedMetadata.IPTC
                        marker == APP14 && bytes.startsWithAt(start, ADOBE) && end - start >= 12 -> {
                            // The colour transform alone: version 100, no flags, transform byte copied.
                            body.writeSegment(APP14, adobeBlock(bytes.u8(start + 11)), 0, 12)
                            if (end - start != 12 || bytes.u16BigEndian(start + 7) != 0 || bytes.u16BigEndian(start + 9) != 0) removed += RemovedMetadata.OTHER
                        }
                        marker == COM -> removed += RemovedMetadata.COMMENT
                        else -> removed += RemovedMetadata.OTHER // other APPn, JPGn and unknown markers
                    }
                    position = end
                }
            }
        }
        if (!sawFrame) throw MalformedImageException("no_frame")
        if (ended && position < bytes.size) removed += RemovedMetadata.EMBEDDED_PICTURES
        val keptOrientation = keepOrientation && orientation != ExifOrientation.NORMAL
        val out = ByteSink(body.size + 64)
        out.writeMarker(SOI)
        jfif?.let { out.writeSegment(APP0, it, 0, it.size) }
        if (keptOrientation) {
            val block = MinimalExif.JPEG_IDENTIFIER + MinimalExif.tiff(orientation)
            out.writeSegment(APP1, block, 0, block.size)
        }
        out.write(body.toByteArray())
        // A file cut after its last scan is still shown by decoders; it gets its end marker back.
        out.writeMarker(EOI)
        return CleanImage(out.toByteArray(), ImageFormat.JPEG, orientation, removed)
    }

    private fun isMinimalOrientationBlock(bytes: ByteArray, tiffStart: Int, end: Int): Boolean {
        val orientation = ExifOrientation.fromTiff(bytes, tiffStart, end - tiffStart)
        val expected = MinimalExif.tiff(orientation)
        return end - tiffStart == expected.size && bytes.startsWithAt(tiffStart, expected)
    }

    private fun adobeBlock(transform: Int): ByteArray =
        ADOBE + byteArrayOf(0x00, 0x64, 0x00, 0x00, 0x00, 0x00, (transform and 0xFF).toByte())

    /**
     * Copies the entropy-coded data that follow a scan header, up to the next marker that is not
     * part of them (0xFF00 is a stuffed byte, 0xFFD0 to 0xFFD7 are restart markers); returns its offset.
     */
    private fun copyEntropyCodedData(bytes: ByteArray, from: Int, out: ByteSink): Int {
        var index = from
        while (index < bytes.size) {
            if (bytes.u8(index) == 0xFF && index + 1 < bytes.size) {
                val next = bytes.u8(index + 1)
                if (next != 0x00 && next !in 0xD0..0xD7 && next != 0xFF) break
                if (next == 0xFF) {
                    // Fill byte before a marker: let the main loop read it.
                    out.write(bytes, from, index)
                    return index
                }
                index += 2
            } else {
                index++
            }
        }
        out.write(bytes, from, minOf(index, bytes.size))
        return index
    }

    private fun ByteSink.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteSink.writeSegment(marker: Int, source: ByteArray, from: Int, to: Int) {
        writeMarker(marker)
        writeU16BigEndian(to - from + 2)
        write(source, from, to)
    }
}
