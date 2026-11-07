package org.politisationpopulairefrancaise.core.media

import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A codec on Java's ImageIO, as the desktop application has one (shared/ui, ui/media). */
private object ImageIoCodec : ImageCodec {
    override fun decode(bytes: ByteArray, maxSide: Int): ArgbImage? {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        return ArgbImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    override fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray? {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        return ByteArrayOutputStream().also { ImageIO.write(buffered, "jpg", it) }.toByteArray()
    }
}

/** A codec that reads nothing (a format the platform does not know). */
private object BlindCodec : ImageCodec {
    override fun decode(bytes: ByteArray, maxSide: Int): ArgbImage? = null
    override fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray? = null
}

class PhotoPipelineTest {
    /** A landscape picture stored sideways (as phones store it, EXIF orientation 6): 80 wide, 40 high, red on the left. */
    private fun sideways(): ByteArray {
        val image = BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, 80, 40)
        g.color = Color.RED
        g.fillRect(0, 0, 40, 40)
        g.dispose()
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        return MediaSamples.withMetadata(jpeg, orientation = 6)
    }

    private suspend fun provenance(original: ByteArray) = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT)

    @Test
    fun aDecodablePhotoIsTurnedReencodedAndClean() = runTest {
        val original = sideways()
        val photo = assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance(original), ImageIoCodec)).photo
        assertTrue(photo.reencoded)
        assertFalse(photo.blurred)
        assertEquals(ImageFormat.JPEG, photo.publishedFormat)
        // Turned upright: 40 wide, 80 high, and no orientation left to apply.
        assertEquals(40, photo.width)
        assertEquals(80, photo.height)
        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(photo.published)))
        assertEquals(40, decoded.width)
        assertTrue(MetadataStripper.isClean(photo.published))
        assertEquals(1, (MetadataStripper.strip(photo.published) as StripResult.Clean).image.orientation)
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(photo.published, secret), secret)
        assertTrue(RemovedMetadata.EXIF in photo.removed && RemovedMetadata.XMP in photo.removed)
        assertTrue(Provenance.matches(photo.provenance, photo.original))
    }

    @Test
    fun aBlurredZoneIsBlurredInThePublishedCopyOnly() = runTest {
        val original = sideways()
        // Upright, the red half is at the top: blur the whole top half.
        val zones = listOf(BlurZone(0f, 0f, 1f, 0.5f))
        val photo = assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance(original), ImageIoCodec, zones)).photo
        assertTrue(photo.blurred)
        val decoded = ImageIO.read(ByteArrayInputStream(photo.published))
        val colours = (0 until 40).flatMap { y -> (0 until 40).map { x -> decoded.getRGB(x, y) } }
        // JPEG noise aside, few distinct colours remain in the blurred half.
        assertTrue(colours.map { it and 0xF0F0F0 }.toSet().size <= 40, "${colours.toSet().size} colours")
        assertTrue(MediaSamples.contains(photo.original, MediaSamples.GPS), "the original is untouched")
    }

    @Test
    fun withoutDecoderThePhotoIsCleanedLosslesslyButCannotBeBlurred() = runTest {
        val original = sideways()
        val photo = assertIs<PhotoResult.Ready>(PhotoPipeline.prepare(original, provenance(original), BlindCodec)).photo
        assertFalse(photo.reencoded)
        assertTrue(MetadataStripper.isClean(photo.published))
        assertEquals(6, (MetadataStripper.strip(photo.published) as StripResult.Clean).image.orientation)
        assertEquals(PhotoResult.BlurUnavailable, PhotoPipeline.prepare(original, provenance(original), BlindCodec, listOf(BlurZone(0f, 0f, 0.5f, 0.5f))))
    }

    @Test
    fun videosSoundsAndHugeFilesAreRefused() = runTest {
        val video = byteArrayOf(0, 0, 0, 0x18) + MediaSamples.latin1("ftypmp42") + ByteArray(20)
        assertEquals(PhotoResult.Unsupported(MediaKind.Video), PhotoPipeline.prepare(video, provenance(video), ImageIoCodec))
        val heif = byteArrayOf(0, 0, 0, 0x18) + MediaSamples.latin1("ftypheic") + ByteArray(20)
        assertEquals(PhotoResult.Unsupported(MediaKind.Heif), PhotoPipeline.prepare(heif, provenance(heif), BlindCodec))
        val huge = ByteArray(PhotoPipeline.MAX_ORIGINAL_BYTES + 1)
        assertEquals(PhotoResult.TooLarge, PhotoPipeline.prepare(huge, provenance(video), ImageIoCodec))
    }

    @Test
    fun thePreviewIsUpright() {
        val preview = assertNotNull(PhotoPipeline.preview(sideways(), ImageIoCodec))
        assertEquals(40, preview.width)
        assertEquals(80, preview.height)
    }
}
