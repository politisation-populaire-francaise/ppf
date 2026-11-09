package org.politisationpopulairefrancaise.ui.agir.proches

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.politisationpopulairefrancaise.core.agir.proches.ProchesStorage

/**
 * On Android, the list of « Mes proches à mobiliser » (AG-63) is a private preference of the
 * application (`MODE_PRIVATE`: no other application can read it), like the timer and the rally point.
 * The manifest forbids system backups (`android:allowBackup="false"`), so it never leaves the phone.
 */
@Composable
actual fun rememberPlatformProchesStorage(): ProchesStorage? {
    val context = LocalContext.current.applicationContext
    return remember(context) { PreferencesProchesStorage(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)) }
}

private class PreferencesProchesStorage(private val preferences: SharedPreferences) : ProchesStorage {
    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(value: String) {
        check(preferences.edit().putString(KEY, value).commit()) { "the list of relatives could not be written" }
    }

    override fun clear() {
        check(preferences.edit().remove(KEY).commit()) { "the list of relatives could not be erased" }
    }
}

private const val FILE = "ppf-proches"
private const val KEY = "data"
