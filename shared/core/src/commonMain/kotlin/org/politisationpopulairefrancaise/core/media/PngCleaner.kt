package org.politisationpopulairefrancaise.core.media

/**
 * Cleans a PNG file (PNG third edition, W3C 2025) chunk by chunk, without decoding it.
 *
 * Kept, verbatim with their checksum: IHDR, PLTE, IDAT, IEND and the ancillary chunks that only
 * say how to show the pixels (tRNS, cHRM, gAMA, sRGB, cICP, sBIT, bKGD), plus the orientation alone
 * in a minimal `eXIf` chunk when asked. Removed: text chunks (tEXt, zTXt, iTXt: XMP lives there),
 * eXIf, tIME, iCCP, pHYs, sPLT, hIST, the frames of an animated PNG (acTL, fcTL, fdAT: the still
 * image remains), every unknown ancillary chunk and anything after IEND. An unknown critical chunk
 * makes the file unsupported, as the specification requires of a decoder.
 */
internal object PngCleaner {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val KEPT = setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "cHRM", "gAMA", "sRGB", "cICP", "sBIT", "bKGD")
    private val TEXT = setOf("tEXt", "zTXt", "iTXt")
    private val ANIMATION = setOf("acTL", "fcTL", "fdAT")

    fun clean(bytes: ByteArray, keepOrientation: Boolean): CleanImage {
        if (!bytes.startsWithAt(0, SIGNATURE)) throw MalformedImageException("not_png")
        val removed = mutableSetOf<RemovedMetadata>()
        var orientation = ExifOrientation.NORMAL
        val out = ByteSink(bytes.size)
        out.write(SIGNATURE)
        var position = SIGNATURE.size
        var first = true
        var ended = false
        var exifWritten = false
        while (position < bytes.size) {
            if (position + 12 > bytes.size) throw MalformedImageException("truncated")
            val length = bytes.u32BigEndian(position)
            if (length > Int.MAX_VALUE || position + 12 + length > bytes.size) throw MalformedImageException("bad_length")
            val type = bytes.ascii(position + 4, 4)
            if (type.length != 4 || !type.all { it in 'A'..'Z' || it in 'a'..'z' }) throw MalformedImageException("bad_chunk_type")
            val dataStart = position + 8
            val end = dataStart + length.toInt() + 4
            if (first && type != "IHDR") throw MalformedImageException("no_header")
            first = false
            when {
                type in KEPT -> {
                    // The orientation chunk must come before the image data.
                    if (type == "IDAT" && !exifWritten && keepOrientation && orientation != ExifOrientation.NORMAL) {
                        writeChunk(out, "eXIf", MinimalExif.tiff(orientation))
                        exifWritten = true
                    }
                    out.write(bytes, position, end)
                }
                type == "eXIf" -> {
                    // Some writers wrongly keep the JPEG identifier "Exif\0\0" in front of the TIFF structure.
                    val skip = if (bytes.startsWithAt(dataStart, MinimalExif.JPEG_IDENTIFIER)) MinimalExif.JPEG_IDENTIFIER.size else 0
                    val value = ExifOrientation.fromTiff(bytes, dataStart + skip, length.toInt() - skip)
                    if (orientation == ExifOrientation.NORMAL) orientation = value
                    val minimal = MinimalExif.tiff(value)
                    if (length.toInt() != minimal.size || !bytes.startsWithAt(dataStart, minimal)) removed += RemovedMetadata.EXIF
                }
                type in TEXT -> {
                    removed += if (type == "iTXt" && bytes.ascii(dataStart, 17) == "XML:com.adobe.xmp") RemovedMetadata.XMP else RemovedMetadata.COMMENT
                }
                type == "iCCP" -> removed += RemovedMetadata.ICC_PROFILE
                type in ANIMATION -> removed += RemovedMetadata.EMBEDDED_PICTURES
                type[0].isUpperCase() -> throw MalformedImageException("unknown_critical_chunk")
                else -> removed += RemovedMetadata.OTHER // tIME, pHYs, sPLT, hIST, private chunks
            }
            position = end
            if (type == "IEND") {
                ended = true
                break
            }
        }
        if (!ended) throw MalformedImageException("no_end")
        if (position < bytes.size) removed += RemovedMetadata.EMBEDDED_PICTURES
        return CleanImage(out.toByteArray(), ImageFormat.PNG, orientation, removed)
    }

    private fun writeChunk(out: ByteSink, type: String, data: ByteArray) {
        val typeBytes = type.latin1()
        out.writeU32BigEndian(data.size.toLong())
        out.write(typeBytes)
        out.write(data)
        out.writeU32BigEndian(Crc32.compute(typeBytes, data))
    }
}

/** CRC-32 of ISO 3309 and ITU-T V.42, as PNG uses it for its chunks. */
internal object Crc32 {
    private val TABLE = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }

    fun compute(vararg parts: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (part in parts) for (byte in part) crc = TABLE[((crc xor byte.toLong()) and 0xFF).toInt()] xor (crc ushr 8)
        return crc xor 0xFFFFFFFFL
    }
}
