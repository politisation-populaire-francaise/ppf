package org.politisationpopulairefrancaise.core.media

/** The image formats whose metadata the device knows how to remove before anything is sent. */
enum class ImageFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    ;

    companion object {
        fun ofMimeType(mimeType: String): ImageFormat? = entries.firstOrNull { it.mimeType == mimeType }
    }
}

/**
 * What a file is, read from its first bytes (never from its name or from what the system says):
 * the chain of media only sends the images it can clean ([ImageFormat]); the other kinds are named
 * so that the refusal can be explained honestly.
 */
sealed interface MediaKind {
    data class Image(val format: ImageFormat) : MediaKind

    /** HEIF or HEIC photos (default format of some phones): not cleaned yet, converted when the platform can decode them. */
    data object Heif : MediaKind

    /** MP4, QuickTime, 3GP, WebM, Matroska, AVI: refused for now (no reliable removal of their metadata without a heavy library). */
    data object Video : MediaKind

    /** MP3, AAC or M4A, Ogg, FLAC, WAV: refused for now, for the same reason. */
    data object Audio : MediaKind

    data object Unknown : MediaKind
}

/** Recognizes a file by its signature (magic numbers). */
object MediaSniffer {
    fun kindOf(bytes: ByteArray): MediaKind {
        imageFormatOf(bytes)?.let { return MediaKind.Image(it) }
        if (bytes.size >= 12 && bytes.ascii(4, 4) == "ftyp") {
            val brand = bytes.ascii(8, 4)
            return when {
                brand in HEIF_BRANDS -> MediaKind.Heif
                brand.startsWith("M4A") || brand.startsWith("M4B") -> MediaKind.Audio
                else -> MediaKind.Video
            }
        }
        if (bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF") {
            return when (bytes.ascii(8, 4)) {
                "WAVE" -> MediaKind.Audio
                "AVI " -> MediaKind.Video
                else -> MediaKind.Unknown
            }
        }
        if (bytes.startsWith(0x1A, 0x45, 0xDF, 0xA3)) return MediaKind.Video // WebM, Matroska
        if (bytes.size >= 4 && (bytes.ascii(0, 4) == "OggS" || bytes.ascii(0, 4) == "fLaC")) return MediaKind.Audio
        if (bytes.size >= 3 && bytes.ascii(0, 3) == "ID3") return MediaKind.Audio
        // MPEG audio frame or ADTS: eleven or twelve set bits of synchronization.
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0) return MediaKind.Audio
        return MediaKind.Unknown
    }

    fun imageFormatOf(bytes: ByteArray): ImageFormat? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> ImageFormat.JPEG
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageFormat.PNG
        bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" -> ImageFormat.WEBP
        else -> null
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "avis")
}

internal fun ByteArray.startsWith(vararg values: Int): Boolean =
    size >= values.size && values.indices.all { (this[it].toInt() and 0xFF) == values[it] }

internal fun ByteArray.ascii(offset: Int, length: Int): String {
    if (offset < 0 || offset + length > size) return ""
    return CharArray(length) { (this[offset + it].toInt() and 0xFF).toChar() }.concatToString()
}
