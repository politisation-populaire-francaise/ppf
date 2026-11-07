package org.politisationpopulairefrancaise.core.media

/** An image cleaned of its metadata on the device, ready to be encrypted and sent. */
class CleanImage(
    val bytes: ByteArray,
    val format: ImageFormat,
    /** The orientation read from the original (1 to 8); kept in the clean file when [MetadataStripper.strip] was asked to. */
    val orientation: Int,
    /** What was removed, for tests and for the screen ("position GPS, appareil, date..."): see [RemovedMetadata]. */
    val removed: Set<RemovedMetadata>,
)

/** The families of metadata the cleaning removes; their names are stable (tests, screen). */
enum class RemovedMetadata {
    /** JPEG APP1 "Exif" or PNG eXIf or WebP EXIF: position GPS, date, make and model, settings, owner, serial numbers, thumbnail. */
    EXIF,

    /** XMP (JPEG APP1, PNG iTXt, WebP "XMP "): the same as EXIF and the editing history. */
    XMP,

    /** IPTC and Photoshop blocks (JPEG APP13): author, place, captions. */
    IPTC,

    /** Colour profiles (JPEG APP2, PNG iCCP, WebP ICCP): they may name the device or the software. */
    ICC_PROFILE,

    /** Text comments (JPEG COM, PNG tEXt, zTXt, iTXt). */
    COMMENT,

    /** Thumbnails and extra pictures (JFIF thumbnail, JFXX, MPF, pictures after the end of the image: motion photos, depth maps, HDR gain maps). */
    EMBEDDED_PICTURES,

    /** Other application blocks (JPEG APP3 to APP15 except Adobe's colour transform, PNG tIME, pHYs and other ancillary chunks, WebP unknown chunks). */
    OTHER,
}

sealed interface StripResult {
    data class Clean(val image: CleanImage) : StripResult

    /** Not an image the device can clean: [kind] explains what it is. */
    data class Unsupported(val kind: MediaKind) : StripResult

    /** Looks like a supported image but its structure is broken: nothing is sent. */
    data class Malformed(val reason: String) : StripResult
}

/**
 * Removes the metadata of a JPEG, PNG or WebP image on the device, before anything is sent
 * (CLAUDE.md, section 7; cahier des charges, section 5.1), without decoding the picture: the
 * compressed image data are copied unchanged, so nothing is lost. Pure Kotlin, the same on every
 * platform and on the server (which checks again what it is asked to publish, [findMetadata]).
 *
 * What is kept is a whitelist of the structural parts each format needs to be displayed; anything
 * else goes, including unknown blocks and everything after the end of the image. The orientation
 * alone may be kept in a minimal EXIF block that says nothing else ([MinimalExif]): otherwise a
 * photo taken upright would show turned. The compressed data can still betray the encoder (JPEG
 * quantization tables) and the sensor (its noise): re-encoding from the pixels, then reducing the
 * size ([ImageTransforms]) weakens both; the chain of testimonies does it whenever the platform can.
 */
object MetadataStripper {
    /** Largest image accepted, in bytes (the server refuses bigger encrypted files anyway). */
    const val MAX_INPUT_BYTES: Int = 40 * 1024 * 1024

    fun strip(bytes: ByteArray, keepOrientation: Boolean = true): StripResult {
        if (bytes.size > MAX_INPUT_BYTES) return StripResult.Malformed("too_large")
        return when (val kind = MediaSniffer.kindOf(bytes)) {
            is MediaKind.Image -> try {
                when (kind.format) {
                    ImageFormat.JPEG -> JpegCleaner.clean(bytes, keepOrientation)
                    ImageFormat.PNG -> PngCleaner.clean(bytes, keepOrientation)
                    ImageFormat.WEBP -> WebpCleaner.clean(bytes)
                }.let(StripResult::Clean)
            } catch (e: MalformedImageException) {
                StripResult.Malformed(e.message ?: "malformed")
            }
            else -> StripResult.Unsupported(kind)
        }
    }

    /**
     * The metadata [bytes] still carry, empty for a clean image (the orientation alone in a minimal
     * block does not count). The server calls it before publishing a photo; a broken or unsupported
     * file answers [RemovedMetadata.OTHER].
     */
    fun findMetadata(bytes: ByteArray): Set<RemovedMetadata> = when (val result = strip(bytes, keepOrientation = true)) {
        is StripResult.Clean -> result.image.removed
        else -> setOf(RemovedMetadata.OTHER)
    }

    /** True when [bytes] are a supported image that carries no metadata at all: cleaning it again would remove nothing. */
    fun isClean(bytes: ByteArray): Boolean = findMetadata(bytes).isEmpty()
}

internal class MalformedImageException(reason: String) : Exception(reason)

/** A growing array of bytes (no java.io in common code). */
internal class ByteSink(initialCapacity: Int) {
    private var buffer = ByteArray(maxOf(16, initialCapacity))
    var size: Int = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity = capacity * 2
        buffer = buffer.copyOf(capacity)
    }

    fun write(byte: Int) {
        ensure(1)
        buffer[size++] = byte.toByte()
    }

    fun write(bytes: ByteArray, from: Int = 0, to: Int = bytes.size) {
        ensure(to - from)
        bytes.copyInto(buffer, size, from, to)
        size += to - from
    }

    fun writeU16BigEndian(value: Int) {
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }

    fun writeU32BigEndian(value: Long) {
        write((value ushr 24).toInt() and 0xFF)
        write((value ushr 16).toInt() and 0xFF)
        write((value ushr 8).toInt() and 0xFF)
        write(value.toInt() and 0xFF)
    }

    fun writeU32LittleEndian(value: Long) {
        write(value.toInt() and 0xFF)
        write((value ushr 8).toInt() and 0xFF)
        write((value ushr 16).toInt() and 0xFF)
        write((value ushr 24).toInt() and 0xFF)
    }

    /** Overwrites one byte already written (the flags of a WebP VP8X chunk). */
    operator fun set(index: Int, value: Int) {
        require(index in 0 until size) { "Only written bytes can be overwritten" }
        buffer[index] = value.toByte()
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.u16BigEndian(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.u32BigEndian(at: Int): Long =
    (u8(at).toLong() shl 24) or (u8(at + 1).toLong() shl 16) or (u8(at + 2).toLong() shl 8) or u8(at + 3).toLong()

internal fun ByteArray.u32LittleEndian(at: Int): Long =
    u8(at).toLong() or (u8(at + 1).toLong() shl 8) or (u8(at + 2).toLong() shl 16) or (u8(at + 3).toLong() shl 24)

internal fun ByteArray.startsWithAt(offset: Int, prefix: ByteArray): Boolean =
    offset >= 0 && offset + prefix.size <= size && prefix.indices.all { this[offset + it] == prefix[it] }

internal fun String.latin1(): ByteArray = ByteArray(length) { this[it].code.toByte() }
