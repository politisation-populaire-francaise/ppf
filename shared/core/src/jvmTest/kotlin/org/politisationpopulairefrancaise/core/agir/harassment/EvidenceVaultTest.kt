package org.politisationpopulairefrancaise.core.agir.harassment

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.agir.camera.EncryptedPhotoVault
import org.politisationpopulairefrancaise.core.agir.camera.VaultCrypto
import org.politisationpopulairefrancaise.core.agir.camera.VaultFiles
import org.politisationpopulairefrancaise.core.media.MediaSamples
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 9 November 2026, 05:53 in Paris. */
private const val NOW = 1_794_200_000L

/** AES-256-GCM of the JDK, as the Android vault does it; the master key held in memory stands for the Keystore's. */
private class EvidenceJdkCrypto : VaultCrypto {
    private val random = SecureRandom()
    private var master: SecretKey? = null

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
        val current = master ?: KeyGenerator.getInstance("AES").also { it.init(256) }.generateKey().also { master = it }
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

/** The private directory of a vault, in memory. */
private class EvidenceMemoryFiles : VaultFiles {
    val map = LinkedHashMap<String, ByteArray>()

    override fun read(name: String): ByteArray? = map[name]?.copyOf()

    override fun write(name: String, bytes: ByteArray) {
        map[name] = bytes.copyOf()
    }

    override fun delete(name: String) {
        map.remove(name)
    }

    override fun names(): List<String> = map.keys.toList()

    fun copy(): EvidenceMemoryFiles = EvidenceMemoryFiles().also { copy -> map.forEach { (name, bytes) -> copy.map[name] = bytes.copyOf() } }
}

private fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
    needle.isNotEmpty() && (0..haystack.size - needle.size).any { i -> needle.indices.all { haystack[i + it] == needle[it] } }

/** AG-59: the evidence on the real format and cryptography of the protected camera's vault (D-089). */
class EvidenceVaultTest {
    private val draft = EvidenceDraft(
        platform = "[EXEMPLE] Forum-Témoin",
        url = "https://forum.example.org/fil/77-adresse-exemple",
        text = "[EXEMPLE] Voici son adresse : 12 rue Fictive",
        note = "[EXEMPLE] Note-Intime-Secrète",
        seenDate = "09/11/2026",
        seenTime = "05:00",
    )

    @Test
    fun nothingOfTheEvidenceIsWrittenInClear() = runTest {
        val files = EvidenceMemoryFiles()
        val locker = EvidenceLocker(EncryptedPhotoVault(files, EvidenceJdkCrypto(), domain = EncryptedPhotoVault.EVIDENCE_DOMAIN)) { NOW }
        val image = assertIs<EvidenceImageResult.Ready>(EvidenceImages.prepare(MediaSamples.withMetadata(MediaSamples.minimalJpeg(), 1), EvidenceFormat.SOURCE_PICKER)).image
        val picture = image.bytes.copyOf()
        val kept = assertIs<KeepResult.Kept>(locker.keep(draft, image)).evidence
        assertEquals(3, files.map.size, "the picture, the record and the listing")
        val secrets = listOf("forum.example.org", "12 rue Fictive", "Note-Intime-Secrète", "Forum-Témoin", kept.digest, assertNotNull(kept.content.image).originalSha256)
        for ((name, bytes) in files.map) {
            for (secret in secrets) assertFalse(contains(bytes, secret.encodeToByteArray()), "$secret in clear in $name")
            assertFalse(contains(bytes, picture.copyOfRange(0, 64)), "the start of the picture in clear in $name")
            for (secret in MediaSamples.SECRETS) assertFalse(MediaSamples.contains(bytes, secret), "$secret in $name")
        }
        // And it comes back whole, picture included.
        val read = locker.list().items.single()
        assertEquals(kept, read)
        assertTrue(picture.contentEquals(locker.image(read)))
    }

    @Test
    fun aFileOfTheEvidenceVaultIsRefusedByTheCameraVaultEvenUnderTheSameKey() = runTest {
        val files = EvidenceMemoryFiles()
        val crypto = EvidenceJdkCrypto()
        val locker = EvidenceLocker(EncryptedPhotoVault(files, crypto, domain = EncryptedPhotoVault.EVIDENCE_DOMAIN)) { NOW }
        assertIs<KeepResult.Kept>(locker.keep(draft, null))
        // The same files read as photos of the camera, with the same master key: the associated data differ.
        val camera = EncryptedPhotoVault(files.copy(), crypto)
        assertTrue(runCatching { camera.list() }.isFailure)
        // The camera's default domain is unchanged; an unknown domain is refused.
        assertEquals("camera", EncryptedPhotoVault.CAMERA_DOMAIN)
        assertTrue(runCatching { EncryptedPhotoVault(files, crypto, domain = "Evidence-2") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theQuickEraseDestroysTheKeyThenTheFiles() = runTest {
        val files = EvidenceMemoryFiles()
        val crypto = EvidenceJdkCrypto()
        val locker = EvidenceLocker(EncryptedPhotoVault(files, crypto, domain = EncryptedPhotoVault.EVIDENCE_DOMAIN)) { NOW }
        locker.keep(draft, null)
        val copy = files.copy()
        locker.eraseAll()
        assertTrue(files.map.isEmpty())
        assertFalse(crypto.hasMasterKey())
        // A copy of the files taken before (flash memory, a backup) can no longer be read, even by a new key.
        val again = EncryptedPhotoVault(copy, crypto, domain = EncryptedPhotoVault.EVIDENCE_DOMAIN)
        assertTrue(again.list().isEmpty(), "unreadable files are dropped, not shown")
    }
}
