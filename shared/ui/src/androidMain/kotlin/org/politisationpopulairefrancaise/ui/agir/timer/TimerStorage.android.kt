package org.politisationpopulairefrancaise.ui.agir.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.politisationpopulairefrancaise.core.agir.timer.TimerStorage

/**
 * On Android, the timer (AG-55) is a private preference of the application (`MODE_PRIVATE`: no
 * other application can read it), like the rally point and the settings of the lock. The manifest
 * forbids system backups (`android:allowBackup="false"`), so it never leaves the phone. The secret
 * of the timer is never in it in clear: it is hidden by the key of the « je vais bien » code,
 * itself mixed with the Keystore key of the lock (D-088).
 */
@Composable
actual fun rememberPlatformTimerStorage(): TimerStorage? {
    val context = LocalContext.current.applicationContext
    return remember(context) { PreferencesTimerStorage(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)) }
}

private class PreferencesTimerStorage(private val preferences: SharedPreferences) : TimerStorage {
    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(value: String) {
        check(preferences.edit().putString(KEY, value).commit()) { "the timer could not be written" }
    }

    override fun clear() {
        check(preferences.edit().remove(KEY).commit()) { "the timer could not be erased" }
    }
}

private const val FILE = "ppf-minuteur"
private const val KEY = "data"
