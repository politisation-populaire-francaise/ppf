package org.politisationpopulairefrancaise.core.agir.camera

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.media.ExifOrientation
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSamples
import org.politisationpopulairefrancaise.core.media.MetadataStripper
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.media.RemovedMetadata
import org.politisationpopulairefrancaise.core.media.StripResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TAKEN_AT = 1_794_200_000L

/** The orientation a clean JPEG says (1 when it says none). */
private fun orientationOf(jpeg: ByteArray): Int = assertIs<StripResult.Clean>(MetadataStripper.strip(jpeg, keepOrientation = true)).image.orientation

/** The chain of a shot of the protected camera before the vault (AG-58, D-089), and the vault in memory of the tests. */
class ProtectedCaptureTest {
    /** What a camera hands over: a JPEG carrying a device model, a date, a serial number, a position (all fabricated). */
    private fun cameraJpeg(orientation: Int = 6): ByteArray = MediaSamples.withMetadata(MediaSamples.minimalJpeg(), orientation)

    @Test
    fun everyMetadataGoesBeforeTheVaultAndTheOrientationOfTheCameraStays() {
        val jpeg = cameraJpeg(orientation = 6)
        val ready = assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(jpeg, rotationDegrees = 90, takenAt = TAKEN_AT)))
        for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(ready.bytes, secret), "still in the kept copy: $secret")
        assertTrue(MetadataStripper.isClean(ready.bytes))
        assertEquals(6, orientationOf(ready.bytes))
        // The digest of the camera's bytes, as they came; the kept copy is another file.
        assertEquals(MediaDigest.sha256Hex(jpeg), ready.meta.cameraSha256)
        assertEquals(TAKEN_AT, ready.meta.takenAt)
        assertEquals(ProtectedPhoto.JPEG, ready.meta.mimeType)
        assertTrue(ready.removed.containsAll(listOf(RemovedMetadata.EXIF, RemovedMetadata.XMP, RemovedMetadata.IPTC, RemovedMetadata.ICC_PROFILE, RemovedMetadata.COMMENT, RemovedMetadata.EMBEDDED_PICTURES)))
    }

    @Test
    fun theRotationOfTheCameraIsWhatCountsEvenWithoutExif() {
        val bare = MediaSamples.minimalJpeg()
        assertEquals(8, orientationOf(assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(bare, 270, TAKEN_AT))).bytes))
        assertEquals(3, orientationOf(assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(bare, 180, TAKEN_AT))).bytes))
        // Upright for the camera: no orientation at all, even when its EXIF said otherwise.
        val upright = assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(cameraJpeg(orientation = 6), 0, TAKEN_AT))).bytes
        assertEquals(ExifOrientation.NORMAL, orientationOf(upright))
        assertTrue(MetadataStripper.isClean(upright))
    }

    @Test
    fun rotationsBecomeExifOrientations() {
        assertEquals(1, ProtectedCapture.orientationOf(0))
        assertEquals(6, ProtectedCapture.orientationOf(90))
        assertEquals(3, ProtectedCapture.orientationOf(180))
        assertEquals(8, ProtectedCapture.orientationOf(270))
        assertEquals(8, ProtectedCapture.orientationOf(-90))
        assertEquals(6, ProtectedCapture.orientationOf(450))
        assertEquals(1, ProtectedCapture.orientationOf(45))
    }

    @Test
    fun whatIsNotACleanableJpegIsNeverKept() {
        assertEquals(CaptureResult.Refused(CaptureRefusal.NOT_JPEG), ProtectedCapture.prepare(CameraShot(MediaSamples.minimalPng(), 0, TAKEN_AT)))
        val broken = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0x00, 0x01)
        assertEquals(CaptureResult.Refused(CaptureRefusal.MALFORMED), ProtectedCapture.prepare(CameraShot(broken, 0, TAKEN_AT)))
        val huge = ByteArray(PhotoPipeline.MAX_ORIGINAL_BYTES + 1).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }
        assertEquals(CaptureResult.Refused(CaptureRefusal.TOO_LARGE), ProtectedCapture.prepare(CameraShot(huge, 0, TAKEN_AT)))
    }

    @Test
    fun theVaultOfTheTestsKeepsListsOpensDeletesAndErases() = runTest {
        val vault = InMemoryProtectedVault()
        val first = assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(cameraJpeg(), 90, TAKEN_AT)))
        val second = assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(MediaSamples.minimalJpeg(), 0, TAKEN_AT + 60)))
        val older = vault.store(first.bytes, first.meta)
        val newer = vault.store(second.bytes, second.meta)
        assertTrue(ProtectedIds.isValid(older.id) && ProtectedIds.isValid(newer.id))
        assertEquals(listOf(newer.id, older.id), vault.list().map { it.id })
        assertEquals(MediaDigest.sha256Hex(first.bytes), older.sha256)
        assertEquals(first.bytes.size.toLong(), older.sizeBytes)
        assertContentEquals(first.bytes, vault.open(older.id))
        assertNull(vault.open("0".repeat(32)))
        vault.delete(older.id)
        assertEquals(listOf(newer.id), vault.list().map { it.id })
        assertNull(vault.open(older.id))
        vault.eraseAll()
        assertTrue(vault.list().isEmpty())
        assertNull(vault.open(newer.id))
        vault.eraseAll()
        assertEquals(2, vault.erasures)
    }

    @Test
    fun identifiersSayNothingAndAreChecked() {
        val ids = (1..50).map { ProtectedIds.next() }
        assertEquals(50, ids.toSet().size)
        assertTrue(ids.all(ProtectedIds::isValid))
        assertFalse(ProtectedIds.isValid("../index"))
        assertFalse(ProtectedIds.isValid("A".repeat(32)))
        assertFalse(ProtectedIds.isValid("0".repeat(31)))
    }
}
