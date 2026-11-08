package org.politisationpopulairefrancaise.core.agir.security

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * The security settings of the Android application: a private preference (`MODE_PRIVATE`, system
 * backups refused by the manifest). The hash of the unlock code is peppered with an HMAC-SHA-256
 * key generated inside the Android Keystore (hardware-backed when the phone has it), which never
 * leaves it: a copy of the preference alone cannot be used to try every code on a computer.
 */
class AndroidDeviceSecurityStorage(context: Context) : DeviceSecurityStorage {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(value: String) {
        check(preferences.edit().putString(KEY, value).commit()) { "the security settings could not be written" }
    }

    override fun clear() {
        preferences.edit().clear().commit()
        runCatching { keyStore().deleteEntry(PEPPER_ALIAS) }
    }

    override fun pepper(derived: ByteArray): ByteArray {
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(pepperKey())
        return mac.doFinal(derived)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun pepperKey(): SecretKey {
        (keyStore().getEntry(PEPPER_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(PEPPER_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "ppf-securite"
        const val KEY = "settings"
        const val PEPPER_ALIAS = "ppf-lock-pepper"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * The quick erase on Android (S-44): the system's own « Effacer les données » of the application
 * (`ActivityManager.clearApplicationUserData`, Android 4.4 and later). It erases every private file
 * of the application (local database of every pole, preferences, identity, files, cache, media) and
 * its Android Keystore keys, then stops it. Nothing else to do afterwards.
 */
class AndroidDeviceEraser(context: Context) : DeviceEraser {
    private val context = context.applicationContext

    override suspend fun eraseAll() {
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager?.clearApplicationUserData() == true) return
        // The system refused (rare): the same by hand, then the process is stopped by closeApplication.
        context.databaseList().forEach { context.deleteDatabase(it) }
        val data = context.dataDir
        listOf(context.filesDir, context.noBackupFilesDir, context.cacheDir, context.codeCacheDir, File(data, "shared_prefs"))
            .forEach { it.deleteRecursively() }
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { store -> store.aliases().toList().forEach(store::deleteEntry) } }
        check(File(data, "shared_prefs").listFiles().isNullOrEmpty()) { "the preferences could not be erased" }
    }

    override fun closeApplication() {
        // Normally the system has already stopped the application while it erased its data.
        Process.killProcess(Process.myPid())
    }
}
