package org.politisationpopulairefrancaise.core.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the identity in the app's private storage, encrypted with an AES-256-GCM key that is
 * generated inside the Android Keystore (hardware-backed when the device has it) and never
 * leaves it. The file sits in the "no backup" directory and the app refuses backups anyway,
 * so the secret never goes to a cloud backup.
 */
class AndroidIdentityStore(context: Context) : IdentityStore {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun load(): StoredIdentity? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val blob = file.readBytes()
        check(blob.size > IV_SIZE) { "Stored identity is corrupted" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE))
        StoredIdentity.decode(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE))
    }

    override suspend fun save(identity: StoredIdentity) {
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
            val blob = cipher.iv + cipher.doFinal(identity.encode())
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeBytes(blob)
            // rename() replaces the destination atomically on Android's file systems.
            check(temp.renameTo(file)) { "Could not store the identity" }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            file.delete()
        }
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "identity.bin"
        const val KEY_ALIAS = "ppf-identity-wrap"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
