package org.politisationpopulairefrancaise.core.media

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaChainTest {
    @Test
    fun encryptedFilesComeBackIdenticalAndRefuseAnyChange() {
        for (size in listOf(1, 31, 32, 33, 1000, 70_000, 300_000)) {
            val plain = ByteArray(size) { (it * 7 + 3).toByte() }
            val file = MediaCipher.encrypt(plain)
            assertEquals(MediaDigest.sha256Hex(file.bytes), file.id)
            assertContentEquals(plain, MediaCipher.decrypt(file.bytes, file.key))
            // The ciphertext hides the exact size (NIP-44 padding).
            if (size in 2..32) assertEquals(MediaCipher.encrypt(ByteArray(1)).bytes.size, file.bytes.size)
            val tampered = file.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            assertFailsWith<MediaCipherException> { MediaCipher.decrypt(tampered, file.key) }
            val otherKey = ByteArray(32) { 1 }
            assertFailsWith<MediaCipherException> { MediaCipher.decrypt(file.bytes, otherKey) }
        }
    }

    @Test
    fun twoEncryptionsOfTheSameFileDifferEntirely() {
        val plain = ByteArray(500) { 9 }
        val a = MediaCipher.encrypt(plain)
        val b = MediaCipher.encrypt(plain)
        assertNotEquals(a.id, b.id)
        assertFalse(a.key.contentEquals(b.key))
    }

    @Test
    fun theProvenanceNamesTheOriginalAndNoAccount() = runTest {
        val original = MediaSamples.withMetadata(MediaSamples.minimalJpeg())
        val statement = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT, capturedAt = 1_793_966_400)
        assertTrue(statement.verify())
        assertEquals(Provenance.KIND, statement.kind)
        assertEquals(MediaDigest.sha256Hex(original), statement.firstTagValue("x"))
        assertEquals("image/jpeg", statement.firstTagValue("m"))
        assertEquals(original.size.toString(), statement.firstTagValue("size"))
        assertEquals("import", statement.firstTagValue("ppf-capture"))
        assertEquals("1793966400", statement.firstTagValue("ppf-captured-at"))
        assertEquals(1_793_966_400, statement.createdAt)
        assertTrue(Provenance.matches(statement, original))
        assertFalse(Provenance.matches(statement, original.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }))
        // A key for this file alone: two statements never share their author.
        val again = Provenance.signWithOneTimeKey(original, "image/jpeg", CaptureSource.IMPORT)
        assertNotEquals(statement.pubkey, again.pubkey)
        // The account can sign instead, if the witness chooses to.
        val account = LocalEventSigner(IdentityKeys.generate())
        assertEquals(account.publicKey.hex, Provenance.sign(account, original, "image/jpeg", CaptureSource.CAMERA).pubkey)
        assertEquals(StampOutcome.UNAVAILABLE, NoTimestamping.stamp(MediaDigest.sha256(original)))
    }

    private fun image(width: Int, height: Int) = ArgbImage(width, height, IntArray(width * height) { (0xFF shl 24) or it })

    @Test
    fun everyOrientationTurnsThePixelsUpright() {
        // A 3 × 2 picture stored as the sensor saw it; each case is checked on its corners.
        val stored = image(3, 2) // pixels 0..5, row after row
        fun shown(o: Int) = ImageTransforms.applyOrientation(stored, o)
        fun ArgbImage.values() = pixels.map { it and 0xFFFFFF }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), shown(1).values())
        assertEquals(listOf(2, 1, 0, 5, 4, 3), shown(2).values())
        assertEquals(listOf(5, 4, 3, 2, 1, 0), shown(3).values())
        assertEquals(listOf(3, 4, 5, 0, 1, 2), shown(4).values())
        // Turned a quarter: 2 wide, 3 high.
        assertEquals(2, shown(6).width)
        assertEquals(listOf(0, 3, 1, 4, 2, 5), shown(5).values())
        assertEquals(listOf(3, 0, 4, 1, 5, 2), shown(6).values())
        assertEquals(listOf(5, 2, 4, 1, 3, 0), shown(7).values())
        assertEquals(listOf(2, 5, 1, 4, 0, 3), shown(8).values())
    }

    @Test
    fun aLargePictureIsReducedAndASmallOneKept() {
        val big = image(4000, 3000)
        val small = ImageTransforms.downscale(big)
        assertEquals(2048, small.width)
        assertEquals(1536, small.height)
        val tiny = image(10, 10)
        assertTrue(ImageTransforms.downscale(tiny) === tiny)
    }

    @Test
    fun aBlurredZoneKeepsOnlyAFewAveragesAndTheRestIsUntouched() {
        val width = 120
        val height = 80
        // A detailed picture: every pixel different.
        val source = ArgbImage(width, height, IntArray(width * height) { (0xFF shl 24) or ((it * 2654435761L).toInt() and 0xFFFFFF) })
        val zone = BlurZone(0.25f, 0.25f, 0.75f, 0.75f)
        val blurred = ImageTransforms.blur(source, listOf(zone))
        val inside = mutableSetOf<Int>()
        for (y in 0 until height) for (x in 0 until width) {
            val within = x in 30 until 90 && y in 20 until 60
            if (within) inside += blurred[x, y] else assertEquals(source[x, y], blurred[x, y], "outside the zone at $x,$y")
        }
        // 60 × 40 pixels become at most 6 × 4 blocks of one colour.
        assertTrue(inside.size <= ImageTransforms.BLOCKS_PER_ZONE * ImageTransforms.BLOCKS_PER_ZONE, "${inside.size} colours left")
        // The original is not modified.
        assertNotEquals(source[45, 30], source[46, 30])
    }

    @Test
    fun zonesStayInsideThePicture() {
        val zone = BlurZone.around(0.98f, 0.02f, side = 0.2f, aspectRatio = 1f)
        assertTrue(zone.right <= 1f && zone.top >= 0f)
        assertEquals(0.2f, zone.width, 0.0001f)
        val wide = BlurZone.around(0.5f, 0.5f, side = 0.2f, aspectRatio = 2f)
        assertEquals(0.1f, wide.width, 0.0001f)
        assertEquals(0.2f, wide.height, 0.0001f)
        val grown = zone.moved(scale = 100f)
        assertEquals(1f, grown.width, 0.0001f)
        val shrunk = zone.moved(scale = 0.001f)
        assertEquals(BlurZone.MIN_SIDE, shrunk.width, 0.0001f)
        assertFailsWith<IllegalArgumentException> { BlurZone(0.5f, 0.5f, 0.4f, 0.6f) }
    }
}
