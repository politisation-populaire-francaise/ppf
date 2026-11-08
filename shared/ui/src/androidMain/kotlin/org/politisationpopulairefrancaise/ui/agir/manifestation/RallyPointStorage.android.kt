package org.politisationpopulairefrancaise.ui.agir.manifestation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPointStorage

/**
 * On Android, the rally point is a private preference of the application (`MODE_PRIVATE`: no other
 * application can read it). The manifest forbids system backups (`android:allowBackup="false"`), so
 * it never leaves the phone. Uninstalling the application, or erasing its data, forgets it.
 */
@Composable
actual fun rememberPlatformRallyPointStorage(): RallyPointStorage? {
    val context = LocalContext.current.applicationContext
    return remember(context) { PreferencesRallyPointStorage(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)) }
}

private class PreferencesRallyPointStorage(private val preferences: SharedPreferences) : RallyPointStorage {
    override fun load(): String? = preferences.getString(KEY, null)

    override fun save(value: String) {
        check(preferences.edit().putString(KEY, value).commit()) { "the rally point could not be written" }
    }

    override fun clear() {
        preferences.edit().remove(KEY).commit()
    }
}

private const val FILE = "ppf-manifestation"
private const val KEY = "rally-point"
