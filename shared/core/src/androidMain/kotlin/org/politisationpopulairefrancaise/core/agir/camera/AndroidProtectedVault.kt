package org.politisationpopulairefrancaise.core.agir.camera

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The vault of the protected camera on Android (AG-58, D-089): [EncryptedPhotoVault] in the
 * directory `ppf-camera` of the app's no-backup directory (`Context.getNoBackupFilesDir`: never in
 * an Android backup, and the manifest refuses backups anyway), each photo sealed by its own
 * AES-256-GCM key, every key wrapped by a master key generated inside the Android Keystore
 * (non-exportable, hardware-backed when the phone has it). Nothing goes to MediaStore nor to the
 * shared storage.
 */
object AndroidProtectedVault {
    const val DIRECTORY: String = "ppf-camera"

    fun create(context: Context): ProtectedVault =
        EncryptedPhotoVault(DirectoryVaultFiles(File(context.applicationContext.noBackupFilesDir, DIRECTORY)), KeystoreVaultCrypto())
}

/**
 * AES-256-GCM from `javax.crypto` for the keys of the photos (random 12-byte nonce, 128-bit tag),
 * and a master AES-256-GCM key of the Android Keystore for wrapping them: the Keystore chooses the
 * nonce of each wrap itself (randomized encryption, the default), and the key never leaves it.
 */
internal class KeystoreVaultCrypto : VaultCrypto {
    private val random = SecureRandom()

    override fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    override fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    override fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw GeneralSecurityException("sealed content too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    override fun wrap(key: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        cipher.updateAAD(aad)
        val wrapped = cipher.doFinal(key)
        val nonce = cipher.iv
        if (nonce == null || nonce.size != NONCE_BYTES) throw GeneralSecurityException("unexpected nonce from the Keystore")
        return nonce + wrapped
    }

    override fun unwrap(wrapped: ByteArray, aad: ByteArray): ByteArray {
        if (wrapped.size < NONCE_BYTES + TAG_BITS / 8) throw GeneralSecurityException("wrapped key too short")
        val master = existingMasterKey() ?: throw GeneralSecurityException("the master key of the protected camera is gone")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
    }

    override fun hasMasterKey(): Boolean = keyStore().containsAlias(MASTER_ALIAS)

    override fun destroyMasterKey() {
        val store = keyStore()
        if (store.containsAlias(MASTER_ALIAS)) store.deleteEntry(MASTER_ALIAS)
        if (keyStore().containsAlias(MASTER_ALIAS)) throw GeneralSecurityException("the master key of the protected camera could not be destroyed")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingMasterKey(): SecretKey? = (keyStore().getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun masterKey(): SecretKey {
        existingMasterKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "ppf-camera-master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * A private directory of the application: each file replaced in one move (a temporary file renamed,
 * atomic on Android's file systems), so that a file is never half written.
 */
internal class DirectoryVaultFiles(private val directory: File) : VaultFiles {
    override fun read(name: String): ByteArray? {
        val file = file(name)
        return if (file.isFile) file.readBytes() else null
    }

    override fun write(name: String, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw VaultException("the directory of the protected camera could not be created")
        val temporary = file("$name.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file(name))) {
            temporary.delete()
            throw VaultException("a file of the protected camera could not be written")
        }
    }

    override fun delete(name: String) {
        val file = file(name)
        if (file.exists() && !file.delete() && file.exists()) throw VaultException("a file of the protected camera could not be deleted")
    }

    override fun names(): List<String> = directory.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    private fun file(name: String): File {
        require(SAFE_NAME.matches(name)) { "a plain file name" }
        return File(directory, name)
    }

    private companion object {
        val SAFE_NAME = Regex("[a-z0-9]+(\\.[a-z]+){1,2}")
    }
}
