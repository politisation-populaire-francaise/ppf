package org.politisationpopulairefrancaise.server.agir.testimonies

import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.media.StampOutcome
import org.politisationpopulairefrancaise.core.media.Timestamping
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.time.Duration

/** Fabricated material for the testimony tests: no real photo, no real person. */
object TestimonyFixtures {
    const val SECRET_GPS = "GPS 45.7640N 4.8357E [EXEMPLE]"
    const val SECRET_MODEL = "[EXEMPLE] Appareil Modele-X"

    fun latin1(text: String): ByteArray = ByteArray(text.length) { text[it].code.toByte() }

    fun contains(haystack: ByteArray, needle: String): Boolean {
        val bytes = latin1(needle)
        return (0..haystack.size - bytes.size).any { i -> bytes.indices.all { haystack[i + it] == bytes[it] } }
    }

    /** A real JPEG drawn by Java, with an EXIF block (planted GPS position and camera model) and a comment. */
    fun dirtyJpeg(): ByteArray {
        val image = BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(43, 58, 143)
            fillRect(0, 0, 48, 32)
            dispose()
        }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        fun segment(marker: Int, payload: ByteArray): ByteArray {
            val length = payload.size + 2
            return byteArrayOf(0xFF.toByte(), marker.toByte(), (length ushr 8).toByte(), length.toByte()) + payload
        }
        val tiff = latin1("MM\u0000*\u0000\u0000\u0000\u0008\u0000\u0000\u0000\u0000\u0000\u0000") + latin1("$SECRET_MODEL $SECRET_GPS")
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + segment(0xE1, latin1("Exif\u0000\u0000") + tiff) + segment(0xFE, latin1(SECRET_GPS)) +
            jpeg.copyOfRange(2, jpeg.size)
    }

    fun temporaryDirectory(): Path = Files.createTempDirectory("ppf-testimonies-test").also { it.toFile().deleteOnExit() }

    fun config(directory: Path? = temporaryDirectory(), maxFileBytes: Long = TestimonyConfig.DEFAULT_MAX_FILE_BYTES) = TestimonyConfig(
        directory = directory,
        recipients = TestimonyRecipients.EXAMPLE,
        maxFileBytes = maxFileBytes,
        cleanupInterval = Duration.ZERO,
    )

    /** Records the digests the server asks to timestamp. */
    class RecordingTimestamping : Timestamping {
        val digests = mutableListOf<String>()
        override suspend fun stamp(digest: ByteArray): StampOutcome {
            digests += digest.toHexString()
            return StampOutcome.QUEUED
        }
    }

    /** A clock the tests move by hand. */
    class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
}
