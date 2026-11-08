package org.politisationpopulairefrancaise.core.agir.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
