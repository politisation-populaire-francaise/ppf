package org.politisationpopulairefrancaise.core.media

import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * Decoding and encoding of pictures, given by the platform (Android's BitmapFactory and Bitmap,
 * Java's ImageIO on computers: shared/ui, ui/media). Both may fail (format the platform does not
 * read, not enough memory): they answer null, and the chain falls back to the lossless cleaning.
 */
interface ImageCodec {
    /**
     * The pixels of [bytes], as stored (the EXIF orientation is not applied), reduced on the way if
     * the platform can so that the longer side stays near [maxSide] (never below it).
     */
    fun decode(bytes: ByteArray, maxSide: Int): ArgbImage?

    /** A baseline JPEG of [image] at [quality] (1 to 100), with no metadata of its own beyond JFIF. */
    fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray?
}

/** A photo ready to go: the untouched original, the cleaned copy to publish and the provenance of the original. */
class PreparedPhoto(
    /** Exactly the bytes chosen or taken, metadata included: sent only to the evidence key, and only if the witness says so. */
    val original: ByteArray,
    val originalKind: MediaKind,
    /** What the cleaning removed from the original (shown to the witness). */
    val removed: Set<RemovedMetadata>,
    /** The copy that may be published: no metadata, turned upright, reduced and blurred when re-encoded. */
    val published: ByteArray,
    val publishedFormat: ImageFormat,
    /** Dimensions of [published], when known (always known once re-encoded). */
    val width: Int?,
    val height: Int?,
    val blurred: Boolean,
    /** True when [published] was drawn again from the pixels (quantization tables and sensor noise weakened). */
    val reencoded: Boolean,
    /** Signed on the device before any change (AG-54, [Provenance]). */
    val provenance: SignedEvent,
) {
    val originalSha256: String = MediaDigest.sha256Hex(original)
    val publishedSha256: String = MediaDigest.sha256Hex(published)
}

sealed interface PhotoResult {
    data class Ready(val photo: PreparedPhoto) : PhotoResult

    /** Not a picture the device can clean: a video, a sound, an unknown file, or HEIF that the platform cannot decode. */
    data class Unsupported(val kind: MediaKind) : PhotoResult

    data object TooLarge : PhotoResult

    data object Malformed : PhotoResult

    /** Zones to blur were drawn but the platform cannot decode this picture: nothing is sent unblurred. */
    data object BlurUnavailable : PhotoResult
}

/**
 * The chain a photo goes through on the device before it may leave (CLAUDE.md, section 7):
 * 1. its provenance is signed as it was chosen or taken (done by the caller, [Provenance]);
 * 2. when the platform decodes it: turned upright, reduced to [ImageTransforms.MAX_PUBLISHED_SIDE],
 *    blurred where the witness drew zones, encoded again as JPEG, then cleaned once more;
 * 3. otherwise (and only without zones to blur): cleaned without decoding, orientation kept alone.
 * The result is checked clean ([MetadataStripper.isClean]) before it is returned.
 */
object PhotoPipeline {
    const val JPEG_QUALITY: Int = 85

    /** The pixels to show while the witness draws the zones: upright, small enough for any phone. */
    fun preview(original: ByteArray, codec: ImageCodec?, maxSide: Int = 1600): ArgbImage? {
        codec ?: return null
        val orientation = (MetadataStripper.strip(original, keepOrientation = true) as? StripResult.Clean)?.image?.orientation ?: ExifOrientation.NORMAL
        val decoded = runCatching { codec.decode(original, maxSide) }.getOrNull() ?: return null
        return ImageTransforms.downscale(ImageTransforms.applyOrientation(decoded, orientation), maxSide)
    }

    fun prepare(original: ByteArray, provenance: SignedEvent, codec: ImageCodec?, zones: List<BlurZone> = emptyList()): PhotoResult {
        if (original.size > MAX_ORIGINAL_BYTES) return PhotoResult.TooLarge
        val kind = MediaSniffer.kindOf(original)
        val strip = when (kind) {
            is MediaKind.Image -> MetadataStripper.strip(original, keepOrientation = true)
            MediaKind.Heif -> null
            else -> return PhotoResult.Unsupported(kind)
        }
        if (strip is StripResult.Malformed) return PhotoResult.Malformed
        val clean = (strip as? StripResult.Clean)?.image
        val orientation = clean?.orientation ?: ExifOrientation.NORMAL
        val removed = clean?.removed ?: setOf(RemovedMetadata.EXIF, RemovedMetadata.OTHER) // HEIF always carries EXIF; all of it goes
        val decoded = codec?.let { runCatching { it.decode(original, ImageTransforms.MAX_PUBLISHED_SIDE) }.getOrNull() }
        if (decoded != null) {
            val upright = ImageTransforms.downscale(ImageTransforms.applyOrientation(decoded, orientation))
            val blurred = ImageTransforms.blur(upright, zones)
            val encoded = runCatching { codec.encodeJpeg(blurred, JPEG_QUALITY) }.getOrNull()
            val cleaned = encoded?.let { MetadataStripper.strip(it, keepOrientation = false) as? StripResult.Clean }?.image
            if (cleaned != null && MetadataStripper.isClean(cleaned.bytes)) {
                return PhotoResult.Ready(
                    PreparedPhoto(original, kind, removed, cleaned.bytes, ImageFormat.JPEG, blurred.width, blurred.height, zones.isNotEmpty(), reencoded = true, provenance = provenance),
                )
            }
        }
        if (zones.isNotEmpty()) return PhotoResult.BlurUnavailable
        clean ?: return PhotoResult.Unsupported(kind)
        if (!MetadataStripper.isClean(clean.bytes)) return PhotoResult.Malformed
        return PhotoResult.Ready(PreparedPhoto(original, kind, removed, clean.bytes, clean.format, null, null, blurred = false, reencoded = false, provenance = provenance))
    }

    const val MAX_ORIGINAL_BYTES: Int = 25 * 1024 * 1024
}
