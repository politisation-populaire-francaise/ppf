package org.politisationpopulairefrancaise.core.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MetadataStripperTest {
    private fun clean(bytes: ByteArray, keepOrientation: Boolean = true): CleanImage =
        assertIs<StripResult.Clean>(MetadataStripper.strip(bytes, keepOrientation)).image

    private fun assertNoSecret(bytes: ByteArray) {
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(bytes, secret), "\"$secret\" is still in the file")
        assertFalse(MediaSamples.contains(bytes, "TRAILER"), "data after the end of the image are still there")
    }

    /** The markers of a JPEG, in order (segments and standalone markers, not the scan data). */
    private fun markers(jpeg: ByteArray): List<Int> {
        val found = mutableListOf<Int>()
        var i = 2
        found += 0xD8
        while (i + 1 < jpeg.size) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            found += marker
            if (marker == 0xD9) break
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            i += 2 + length
            if (marker == 0xDA) {
                while (i + 1 < jpeg.size && !((jpeg[i].toInt() and 0xFF) == 0xFF && (jpeg[i + 1].toInt() and 0xFF) !in setOf(0x00, 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7))) i++
            }
        }
        return found
    }

    @Test
    fun aJpegLosesEveryMetadataAndKeepsItsPicture() {
        val original = MediaSamples.minimalJpeg()
        val dirty = MediaSamples.withMetadata(original)
        for (secret in MediaSamples.SECRETS) assertTrue(MediaSamples.contains(dirty, secret), "the sample must carry \"$secret\"")
        val image = clean(dirty)
        assertNoSecret(image.bytes)
        assertEquals(ImageFormat.JPEG, image.format)
        assertEquals(6, image.orientation)
        assertEquals(
            setOf(RemovedMetadata.EXIF, RemovedMetadata.XMP, RemovedMetadata.IPTC, RemovedMetadata.ICC_PROFILE, RemovedMetadata.COMMENT, RemovedMetadata.EMBEDDED_PICTURES, RemovedMetadata.OTHER),
            image.removed,
        )
        // SOI, JFIF without thumbnail, the minimal orientation block, then exactly the structure of the original.
        assertEquals(listOf(0xD8, 0xE0, 0xE1) + markers(original).drop(1), markers(image.bytes))
        // The JFIF block lost its thumbnail.
        assertEquals(16, ((image.bytes[4].toInt() and 0xFF) shl 8) or (image.bytes[5].toInt() and 0xFF))
        // The orientation block is exactly the minimal one: one entry, nothing else.
        val app1Start = 2 + 2 + 16
        val exif = MinimalExif.JPEG_IDENTIFIER + MinimalExif.tiff(6)
        assertContentEquals(MediaSamples.segment(0xE1, exif), image.bytes.copyOfRange(app1Start, app1Start + exif.size + 4))
        // The scan data (with the stuffed byte and the restart marker) are copied unchanged.
        val scan = byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56, 0xFF.toByte(), 0xD0.toByte(), 0x78, 0x9A.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertContentEquals(scan, image.bytes.copyOfRange(image.bytes.size - scan.size, image.bytes.size))
    }

    @Test
    fun cleaningIsIdempotentAndTheServerFindsNothingLeft() {
        val once = clean(MediaSamples.withMetadata(MediaSamples.minimalJpeg())).bytes
        val twice = clean(once)
        assertContentEquals(once, twice.bytes)
        assertTrue(twice.removed.isEmpty(), twice.removed.toString())
        assertTrue(MetadataStripper.isClean(once))
        assertFalse(MetadataStripper.isClean(MediaSamples.withMetadata(MediaSamples.minimalJpeg())))
    }

    @Test
    fun theOrientationCanGoToo() {
        val image = clean(MediaSamples.withMetadata(MediaSamples.minimalJpeg(), orientation = 8), keepOrientation = false)
        assertEquals(8, image.orientation)
        assertFalse(markers(image.bytes).contains(0xE1))
        assertNoSecret(image.bytes)
    }

    @Test
    fun anUprightPhotoGetsNoExifBlockAtAll() {
        val image = clean(MediaSamples.withMetadata(MediaSamples.minimalJpeg(), orientation = 1))
        assertEquals(1, image.orientation)
        assertFalse(markers(image.bytes).contains(0xE1))
    }

    @Test
    fun theAdobeColourTransformIsKeptAlone() {
        val adobe = MediaSamples.segment(0xEE, MediaSamples.latin1("Adobe") + byteArrayOf(0x00, 0x64, 0x40, 0x00, 0x00, 0x00, 0x02))
        val jpeg = MediaSamples.minimalJpeg().let { byteArrayOf(it[0], it[1]) + adobe + it.copyOfRange(2, it.size) }
        val image = clean(jpeg)
        val kept = MediaSamples.segment(0xEE, MediaSamples.latin1("Adobe") + byteArrayOf(0x00, 0x64, 0x00, 0x00, 0x00, 0x00, 0x02))
        assertContentEquals(kept, image.bytes.copyOfRange(2, 2 + kept.size))
    }

    @Test
    fun brokenJpegsAreRefused() {
        val jpeg = MediaSamples.minimalJpeg()
        assertIs<StripResult.Malformed>(MetadataStripper.strip(jpeg.copyOfRange(0, 30)))
        // A segment length past the end of the file.
        val bad = jpeg.copyOf().also { it[4] = 0x7F }
        assertIs<StripResult.Malformed>(MetadataStripper.strip(bad))
        // No frame at all.
        assertIs<StripResult.Malformed>(MetadataStripper.strip(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
    }

    @Test
    fun aJpegCutAfterItsScanGetsItsEnd() {
        val jpeg = MediaSamples.minimalJpeg()
        val cut = jpeg.copyOfRange(0, jpeg.size - 2)
        val image = clean(cut)
        assertEquals(0xD9, image.bytes.last().toInt() and 0xFF)
    }

    @Test
    fun aPngLosesItsTextExifTimeAndProfileAndKeepsItsPixels() {
        val original = MediaSamples.minimalPng()
        val dirty = MediaSamples.pngWithMetadata(original)
        for (secret in listOf(MediaSamples.GPS, MediaSamples.MODEL, MediaSamples.AUTHOR, MediaSamples.SERIAL)) assertTrue(MediaSamples.contains(dirty, secret))
        val image = clean(dirty)
        assertNoSecret(image.bytes)
        assertEquals(6, image.orientation)
        assertEquals(
            setOf(RemovedMetadata.EXIF, RemovedMetadata.XMP, RemovedMetadata.COMMENT, RemovedMetadata.ICC_PROFILE, RemovedMetadata.OTHER, RemovedMetadata.EMBEDDED_PICTURES),
            image.removed,
        )
        val expected = original.copyOfRange(0, 33) + MediaSamples.pngChunk("eXIf", MinimalExif.tiff(6)) + original.copyOfRange(33, original.size)
        assertContentEquals(expected, image.bytes)
        assertTrue(MetadataStripper.isClean(image.bytes))
        assertContentEquals(original, clean(dirty, keepOrientation = false).bytes)
    }

    @Test
    fun anUnknownCriticalPngChunkIsRefused() {
        val png = MediaSamples.minimalPng()
        val withCritical = png.copyOfRange(0, 33) + MediaSamples.pngChunk("ABCD", byteArrayOf(1)) + png.copyOfRange(33, png.size)
        assertIs<StripResult.Malformed>(MetadataStripper.strip(withCritical))
    }

    @Test
    fun aWebpLosesItsChunksAndItsFlags() {
        val dirty = MediaSamples.webpWithMetadata()
        val image = clean(dirty)
        assertNoSecret(image.bytes)
        assertEquals(6, image.orientation)
        assertEquals(setOf(RemovedMetadata.EXIF, RemovedMetadata.XMP, RemovedMetadata.ICC_PROFILE, RemovedMetadata.OTHER, RemovedMetadata.EMBEDDED_PICTURES), image.removed)
        val bytes = image.bytes
        assertEquals("RIFF", bytes.ascii(0, 4))
        assertEquals(bytes.size - 8L, bytes.u32LittleEndian(4))
        assertEquals("VP8X", bytes.ascii(12, 4))
        assertEquals(0, bytes[20].toInt() and 0x2C, "the ICC, EXIF and XMP flags are cleared")
        assertEquals("VP8L", bytes.ascii(30, 4))
        assertEquals(bytes.size, 30 + 8 + 6)
        assertTrue(MetadataStripper.isClean(bytes))
    }

    @Test
    fun videosSoundsAndHeifAreRecognizedAndRefused() {
        fun ftyp(brand: String) = byteArrayOf(0, 0, 0, 0x18) + MediaSamples.latin1("ftyp$brand") + ByteArray(12)
        assertEquals(MediaKind.Video, MediaSniffer.kindOf(ftyp("isom")))
        assertEquals(MediaKind.Video, MediaSniffer.kindOf(ftyp("qt  ")))
        assertEquals(MediaKind.Heif, MediaSniffer.kindOf(ftyp("heic")))
        assertEquals(MediaKind.Audio, MediaSniffer.kindOf(ftyp("M4A ")))
        assertEquals(MediaKind.Audio, MediaSniffer.kindOf(MediaSamples.latin1("ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000")))
        assertEquals(MediaKind.Audio, MediaSniffer.kindOf(MediaSamples.latin1("OggS\u0000\u0002")))
        assertEquals(MediaKind.Video, MediaSniffer.kindOf(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0, 0)))
        assertEquals(MediaKind.Unknown, MediaSniffer.kindOf(MediaSamples.latin1("%PDF-1.7")))
        assertEquals(StripResult.Unsupported(MediaKind.Video), MetadataStripper.strip(ftyp("mp42")))
        assertEquals(StripResult.Unsupported(MediaKind.Heif), MetadataStripper.strip(ftyp("mif1")))
    }

    @Test
    fun orientationIsReadFromBothByteOrders() {
        assertEquals(6, ExifOrientation.fromTiff(MediaSamples.exifTiff(6)))
        val little = byteArrayOf(0x49, 0x49, 0x2A, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(3, ExifOrientation.fromTiff(little))
        assertEquals(1, ExifOrientation.fromTiff(byteArrayOf(1, 2, 3)))
        assertEquals(1, ExifOrientation.fromTiff(MinimalExif.tiff(1).also { it[19] = 42 }))
    }
}
