package org.politisationpopulairefrancaise.core.media

/**
 * Cleans a WebP file (RFC 9649) chunk by chunk, without decoding it.
 *
 * Kept: VP8X with its ICC, EXIF and XMP flags cleared, ANIM, ANMF, ALPH, VP8 and VP8L. Removed:
 * ICCP, EXIF, "XMP " and every unknown chunk, then the RIFF size is written again. The orientation
 * is not kept: WebP decoders do not apply it; it is reported in [CleanImage.orientation] so that a
 * re-encoding can turn the pixels.
 */
internal object WebpCleaner {
    private val KEPT = setOf("VP8X", "ANIM", "ANMF", "ALPH", "VP8 ", "VP8L")
    private const val ICC_FLAG = 0x20
    private const val EXIF_FLAG = 0x08
    private const val XMP_FLAG = 0x04

    fun clean(bytes: ByteArray): CleanImage {
        if (bytes.size < 20 || bytes.ascii(0, 4) != "RIFF" || bytes.ascii(8, 4) != "WEBP") throw MalformedImageException("not_webp")
        val declared = bytes.u32LittleEndian(4)
        val riffEnd = 8L + declared
        if (riffEnd > bytes.size || declared < 4) throw MalformedImageException("bad_length")
        val removed = mutableSetOf<RemovedMetadata>()
        var orientation = ExifOrientation.NORMAL
        val chunks = ByteSink(bytes.size)
        var position = 12
        var sawImage = false
        while (position + 8 <= riffEnd) {
            val type = bytes.ascii(position, 4)
            val size = bytes.u32LittleEndian(position + 4)
            val dataStart = position + 8
            val padded = size + (size and 1L)
            if (dataStart + size > riffEnd) throw MalformedImageException("bad_chunk_length")
            val end = minOf(dataStart + padded, riffEnd).toInt()
            when (type) {
                in KEPT -> {
                    if (type == "VP8 " || type == "VP8L" || type == "ANMF") sawImage = true
                    val start = chunks.size
                    chunks.write(bytes, position, end)
                    if (end - position < 8 + padded.toInt()) chunks.write(0) // a final odd chunk without its padding byte
                    if (type == "VP8X" && size >= 1) {
                        val flags = bytes.u8(dataStart)
                        val cleared = flags and (ICC_FLAG or EXIF_FLAG or XMP_FLAG).inv()
                        chunks[start + 8] = cleared
                    }
                }
                "EXIF" -> {
                    val skip = if (bytes.startsWithAt(dataStart, MinimalExif.JPEG_IDENTIFIER)) MinimalExif.JPEG_IDENTIFIER.size else 0
                    val value = ExifOrientation.fromTiff(bytes, dataStart + skip, size.toInt() - skip)
                    if (orientation == ExifOrientation.NORMAL) orientation = value
                    removed += RemovedMetadata.EXIF
                }
                "XMP " -> removed += RemovedMetadata.XMP
                "ICCP" -> removed += RemovedMetadata.ICC_PROFILE
                else -> removed += RemovedMetadata.OTHER
            }
            position = end
        }
        if (!sawImage) throw MalformedImageException("no_image")
        if (riffEnd < bytes.size) removed += RemovedMetadata.EMBEDDED_PICTURES
        val body = chunks.toByteArray()
        val out = ByteSink(body.size + 12)
        out.write("RIFF".latin1())
        out.writeU32LittleEndian(4L + body.size)
        out.write("WEBP".latin1())
        out.write(body)
        return CleanImage(out.toByteArray(), ImageFormat.WEBP, orientation, removed)
    }
}
