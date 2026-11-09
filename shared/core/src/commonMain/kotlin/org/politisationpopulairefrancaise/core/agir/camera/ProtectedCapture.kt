package org.politisationpopulairefrancaise.core.agir.camera

import org.politisationpopulairefrancaise.core.media.ExifOrientation
import org.politisationpopulairefrancaise.core.media.ImageFormat
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSniffer
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.MinimalExif
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.RemovedMetadata
import org.politisationpopulairefrancaise.core.media.StripResult

/**
 * A photo as the camera of the device handed it over, in memory (CameraX `ImageCapture` with an
 * in-memory callback on Android): it is never written anywhere as it is.
 */
class CameraShot(
    /** The JPEG bytes of the camera, metadata included (device model, date, settings). */
    val jpeg: ByteArray,
    /** How many degrees clockwise the picture must turn to be upright (CameraX `ImageInfo.rotationDegrees`). */
    val rotationDegrees: Int,
    /** When it was taken, in seconds. */
    val takenAt: Long,
) {
    override fun toString(): String = "CameraShot(${jpeg.size} bytes)"
}

/** Why a shot is not kept. */
enum class CaptureRefusal {
    /** Not a JPEG (the camera is asked for JPEG only). */
    NOT_JPEG,

    /** Larger than [PhotoPipeline.MAX_ORIGINAL_BYTES]. */
    TOO_LARGE,

    /** A JPEG whose structure is broken: nothing is kept. */
    MALFORMED,

    /** The cleaned copy still carries metadata (never expected): nothing is kept. */
    NOT_CLEAN,
}

sealed interface CaptureResult {
    /** Ready for the vault: [bytes] carry no metadata but the orientation alone. */
    class Ready(val bytes: ByteArray, val meta: ProtectedPhotoMeta, val removed: Set<RemovedMetadata>) : CaptureResult {
        override fun toString(): String = "Ready(${bytes.size} bytes)"
    }

    data class Refused(val reason: CaptureRefusal) : CaptureResult
}

/**
 * What a shot of the protected camera goes through on the device before it is encrypted (AG-58,
 * D-089), with the chain of media of the testimonies (core/media), nothing duplicated:
 * 1. the SHA-256 of the camera's bytes, as they came ([MediaDigest]);
 * 2. the metadata removed by the whitelist of [MetadataStripper], without decoding the picture: the
 *    pixels stay exactly the camera's, at full size (EXIF with the device model, the date and the
 *    settings, XMP, thumbnails, maker blocks, everything after the end of the image);
 * 3. the orientation said by the camera ([CameraShot.rotationDegrees], which CameraX reads from the
 *    EXIF block when it can be trusted and takes from the request otherwise) written alone in the
 *    minimal EXIF block of the chain ([MinimalExif]); the orientation of the camera's EXIF is not kept;
 * 4. checked clean ([MetadataStripper.isClean]) before it is handed to the vault.
 * No position is ever asked of the device: the camera's JPEG has none, and none could survive step 2.
 */
object ProtectedCapture {
    fun prepare(shot: CameraShot): CaptureResult {
        val original = shot.jpeg
        if (original.size > PhotoPipeline.MAX_ORIGINAL_BYTES) return CaptureResult.Refused(CaptureRefusal.TOO_LARGE)
        if (MediaSniffer.imageFormatOf(original) != ImageFormat.JPEG) return CaptureResult.Refused(CaptureRefusal.NOT_JPEG)
        val cameraSha256 = MediaDigest.sha256Hex(original)
        val clean = when (val strip = MetadataStripper.strip(original, keepOrientation = false)) {
            is StripResult.Clean -> strip.image
            else -> return CaptureResult.Refused(CaptureRefusal.MALFORMED)
        }
        val orientation = orientationOf(shot.rotationDegrees)
        val bytes = if (orientation == ExifOrientation.NORMAL) clean.bytes else withOrientation(clean.bytes, orientation) ?: return CaptureResult.Refused(CaptureRefusal.MALFORMED)
        if (!MetadataStripper.isClean(bytes)) return CaptureResult.Refused(CaptureRefusal.NOT_CLEAN)
        return CaptureResult.Ready(bytes, ProtectedPhotoMeta(takenAt = shot.takenAt, cameraSha256 = cameraSha256), clean.removed)
    }

    /** The EXIF orientation (1 to 8) of a clockwise rotation in degrees; anything but 90, 180 and 270 is upright. */
    fun orientationOf(rotationDegrees: Int): Int = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> 6
        180 -> 3
        270 -> 8
        else -> ExifOrientation.NORMAL
    }

    /**
     * [jpeg], already clean, with [orientation] alone in the minimal EXIF block the cleaner itself
     * keeps; the cleaner then puts the blocks back in their order (JFIF first) and reads it back.
     * Null when the result does not say [orientation].
     */
    internal fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray? {
        if (jpeg.size < 4) return null
        val block = MinimalExif.JPEG_IDENTIFIER + MinimalExif.tiff(orientation)
        val length = block.size + 2
        val segment = byteArrayOf(0xFF.toByte(), APP1.toByte(), (length ushr 8).toByte(), length.toByte()) + block
        val inserted = jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
        val clean = (MetadataStripper.strip(inserted, keepOrientation = true) as? StripResult.Clean)?.image ?: return null
        return if (clean.orientation == orientation) clean.bytes else null
    }

    private const val APP1 = 0xE1
}
