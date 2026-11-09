package org.politisationpopulairefrancaise.core.agir.camera

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.media.MediaSamples
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cryptography of the vault with the JDK, as the Android vault does it (AES-256-GCM, 12-byte
 * nonce, 128-bit tag), the master key held in memory standing for the Keystore's.
 */
private class JdkVaultCrypto : VaultCrypto {
    private val random = SecureRandom()
    private var master: SecretKey? = null
    var masterKeysCreated = 0
        private set

    override fun newKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    override fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    override fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }

    override fun wrap(key: ByteArray, aad: ByteArray): ByteArray {
        val current = master ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
            master = it
            masterKeysCreated++
        }
        return seal(current.encoded, key, aad)
    }

    override fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray {
        val current = master ?: throw GeneralSecurityException("[EXEMPLE] master key gone")
        return open(current.encoded, wrapped, aad)
    }

    override fun hasMasterKey(): Boolean = master != null

    override fun destroyMasterKey() {
        master = null
    }
}

/** The private directory of the vault, in memory; it may refuse to delete (a file that resists). */
private class MemoryFiles : VaultFiles {
    val map = LinkedHashMap<String, ByteArray>()
    var refuseDelete = false

    override fun read(name: String): ByteArray? = map[name]?.copyOf()

    override fun write(name: String, bytes: ByteArray) {
        map[name] = bytes.copyOf()
    }

    override fun delete(name: String) {
        if (refuseDelete && name in map) throw VaultException("[EXEMPLE] the file resists")
        map.remove(name)
    }

    override fun names(): List<String> = map.keys.toList()

    fun copy(): MemoryFiles = MemoryFiles().also { copy -> map.forEach { (name, bytes) -> copy.map[name] = bytes.copyOf() } }
}

private fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
    needle.isNotEmpty() && (0..haystack.size - needle.size).any { i -> needle.indices.all { haystack[i + it] == needle[it] } }

/** The vault of the protected camera on its real cryptography (AG-58, D-089): nothing in clear, one key per photo, crypto-erasure. */
class EncryptedPhotoVaultTest {
    /** A photo as the camera hands it over: real pixels drawn by Java, with the fabricated metadata of the chain's tests. */
    private fun cameraJpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(43, 58, 143)
            fillRect(0, 0, 64, 48)
            color = Color(200, 33, 58)
            fillOval(16, 8, 32, 32)
            dispose()
        }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        return MediaSamples.withMetadata(jpeg, orientation = 6)
    }

    private fun prepared(rotation: Int = 90, takenAt: Long = 1_794_200_000): CaptureResult.Ready =
        assertIs<CaptureResult.Ready>(ProtectedCapture.prepare(CameraShot(cameraJpeg(), rotation, takenAt)))

    @Test
    fun aPhotoIsKeptSealedAndOpensBackExactly() = runTest {
        val files = MemoryFiles()
        val vault = EncryptedPhotoVault(files, JdkVaultCrypto())
        val shot = prepared()
        val photo = vault.store(shot.bytes, shot.meta)
        assertEquals(setOf(EncryptedPhotoVault.INDEX, EncryptedPhotoVault.photoFile(photo.id)), files.map.keys)
        assertEquals(listOf(photo), vault.list())
        val opened = assertNotNull(vault.open(photo.id))
        assertContentEquals(shot.bytes, opened)
        assertEquals(MediaDigest.sha256Hex(shot.bytes), photo.sha256)
        assertNotNull(ImageIO.read(ByteArrayInputStream(opened)))
        // Nothing is ever written in clear: not the start nor the middle of the photo, no metadata, no digest, no date.
        val middle = shot.bytes.size / 2
        val clear = listOf(shot.bytes.copyOfRange(0, 8), shot.bytes.copyOfRange(middle, middle + 16), "JFIF".encodeToByteArray(), photo.sha256.encodeToByteArray(), "1794200000".encodeToByteArray()) +
            MediaSamples.SECRETS.map { MediaSamples.latin1(it) }
        for (bytes in files.map.values) for (needle in clear) assertFalse(contains(bytes, needle), "found in clear in a file of the vault")
    }

    @Test
    fun eachPhotoHasItsOwnKeyAndAnyChangeIsRefused() = runTest {
        val files = MemoryFiles()
        val vault = EncryptedPhotoVault(files, JdkVaultCrypto())
        val shot = prepared()
        val first = vault.store(shot.bytes, shot.meta)
        val second = vault.store(shot.bytes, shot.meta)
        val firstFile = assertNotNull(files.map[EncryptedPhotoVault.photoFile(first.id)])
        val secondFile = assertNotNull(files.map[EncryptedPhotoVault.photoFile(second.id)])
        assertFalse(firstFile.contentEquals(secondFile), "the same photo twice gives two different files")
        // A file moved under another name is refused: each key and each photo are bound to their identifier.
        files.map[EncryptedPhotoVault.photoFile(first.id)] = secondFile
        assertFails { vault.open(first.id) }
        files.map[EncryptedPhotoVault.photoFile(first.id)] = firstFile
        assertContentEquals(shot.bytes, vault.open(first.id))
        // A single byte changed is refused.
        files.map[EncryptedPhotoVault.photoFile(second.id)] = secondFile.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        assertFails { vault.open(second.id) }
        // A changed listing is refused too.
        val index = assertNotNull(files.map[EncryptedPhotoVault.INDEX])
        files.map[EncryptedPhotoVault.INDEX] = index.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFails { vault.list() }
    }

    @Test
    fun deletingAPhotoDeletesItsFileAndItsLine() = runTest {
        val files = MemoryFiles()
        val vault = EncryptedPhotoVault(files, JdkVaultCrypto())
        val older = prepared(takenAt = 1_794_200_000).let { vault.store(it.bytes, it.meta) }
        val newer = prepared(takenAt = 1_794_200_600).let { vault.store(it.bytes, it.meta) }
        assertEquals(listOf(newer.id, older.id), vault.list().map { it.id })
        vault.delete(older.id)
        assertFalse(EncryptedPhotoVault.photoFile(older.id) in files.map)
        assertEquals(listOf(newer.id), vault.list().map { it.id })
        assertNull(vault.open(older.id))
        assertFails { vault.delete("../index") }
        // A file the listing does not know (a store interrupted before its line) is never shown.
        files.map[EncryptedPhotoVault.photoFile("f".repeat(32))] = files.map.getValue(EncryptedPhotoVault.photoFile(newer.id))
        assertEquals(listOf(newer.id), vault.list().map { it.id })
    }

    @Test
    fun theQuickEraseDestroysTheKeySoThatNoCopyCanBeReadAgain() = runTest {
        val files = MemoryFiles()
        val crypto = JdkVaultCrypto()
        val vault = EncryptedPhotoVault(files, crypto)
        val photo = prepared().let { vault.store(it.bytes, it.meta) }
        // What the flash memory could keep of the files after they are deleted (wear-levelling).
        val leftOnTheChip = files.copy()
        vault.eraseAll()
        assertTrue(files.map.isEmpty())
        assertFalse(crypto.hasMasterKey())
        assertTrue(vault.list().isEmpty())
        // The old files come back: a new master key (after a new photo) cannot open them.
        prepared().let { vault.store(it.bytes, it.meta) }
        assertEquals(2, crypto.masterKeysCreated)
        val restored = EncryptedPhotoVault(leftOnTheChip, crypto)
        assertFails { restored.list() }
        assertFails { restored.open(photo.id) }
        // The erase may be run again.
        vault.eraseAll()
        vault.eraseAll()
        assertTrue(files.map.isEmpty())
    }

    @Test
    fun aFailedEraseSaysSoAfterTheKeyIsGoneAndCanBeRunAgain() = runTest {
        val files = MemoryFiles()
        val crypto = JdkVaultCrypto()
        val vault = EncryptedPhotoVault(files, crypto)
        prepared().let { vault.store(it.bytes, it.meta) }
        files.refuseDelete = true
        assertFails { vault.eraseAll() }
        // Even so, nothing can be read any more: the key went first.
        assertFalse(crypto.hasMasterKey())
        files.refuseDelete = false
        vault.eraseAll()
        assertTrue(files.map.isEmpty())
    }

    @Test
    fun filesLeftWithoutTheirKeyAreDropped() = runTest {
        val files = MemoryFiles()
        val crypto = JdkVaultCrypto()
        val vault = EncryptedPhotoVault(files, crypto)
        prepared().let { vault.store(it.bytes, it.meta) }
        // An erase interrupted right after the key (the application closed): the files are unreadable leftovers.
        crypto.destroyMasterKey()
        assertTrue(vault.list().isEmpty())
        assertTrue(files.map.isEmpty())
        // A new photo starts a new vault.
        val fresh = prepared().let { vault.store(it.bytes, it.meta) }
        assertEquals(listOf(fresh.id), vault.list().map { it.id })
    }
}
