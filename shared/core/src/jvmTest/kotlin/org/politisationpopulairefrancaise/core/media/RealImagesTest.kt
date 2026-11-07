package org.politisationpopulairefrancaise.core.media

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The cleaned files stay real images: a JPEG and a PNG drawn and encoded by Java itself, given
 * every metadata a phone writes (fabricated, see MediaSamples), are cleaned, then decoded again by
 * Java: same size, same pixels as before the metadata were added, and none of the planted strings.
 */
class RealImagesTest {
    private fun picture(): BufferedImage = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB).also { image ->
        val g = image.createGraphics()
        g.color = Color(30, 60, 140)
        g.fillRect(0, 0, 64, 48)
        g.color = Color(200, 30, 60)
        g.fillOval(10, 8, 30, 30)
        g.dispose()
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { assert(ImageIO.write(image, format, it)) }.toByteArray()

    private fun decode(bytes: ByteArray): BufferedImage = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)), "the image must stay readable")

    private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

    @Test
    fun aRealJpegStaysReadableWithTheSamePixels() {
        val jpeg = encode(picture(), "jpg")
        val dirty = MediaSamples.withMetadata(jpeg)
        val reference = decode(jpeg)
        val cleaned = assertIs<StripResult.Clean>(MetadataStripper.strip(dirty)).image
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(cleaned.bytes, secret), secret)
        val decoded = decode(cleaned.bytes)
        assertEquals(64, decoded.width)
        assertEquals(48, decoded.height)
        assertContentEquals(pixels(reference), pixels(decoded))
        assertEquals(6, cleaned.orientation)
    }

    @Test
    fun aRealPngStaysReadableWithTheSamePixels() {
        val png = encode(picture(), "png")
        val dirty = MediaSamples.pngWithMetadata(png)
        val cleaned = assertIs<StripResult.Clean>(MetadataStripper.strip(dirty)).image
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(cleaned.bytes, secret), secret)
        assertContentEquals(pixels(decode(png)), pixels(decode(cleaned.bytes)))
    }

    @Test
    fun aProgressiveJpegKeepsAllItsScans() {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam.apply { progressiveMode = javax.imageio.ImageWriteParam.MODE_DEFAULT }
            writer.write(null, javax.imageio.IIOImage(picture(), null, null), parameters)
        }
        writer.dispose()
        val progressive = out.toByteArray()
        val cleaned = assertIs<StripResult.Clean>(MetadataStripper.strip(MediaSamples.withMetadata(progressive))).image
        assertContentEquals(pixels(decode(progressive)), pixels(decode(cleaned.bytes)))
    }
}
