package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * « Application discrète » (S-45): switches the entry of the launcher between the usual name and
 * icon of PPF and a neutral name and icon (« Outils »). Kept by the system itself (state of the
 * launcher entries), nothing is stored by PPF and nothing is sent.
 */
interface AppIconSwitcher {
    val isDiscreet: Boolean

    fun setDiscreet(discreet: Boolean)
}

/** Lets tests replace the switcher of the platform. */
val LocalAppIconSwitcher = staticCompositionLocalOf<AppIconSwitcher?> { null }

/**
 * The switcher of this platform: the two `activity-alias` of the Android manifest; none on a
 * computer (no launcher icon to change) nor on iOS yet (an alternate icon, `setAlternateIconName`,
 * with the iOS build).
 */
@Composable
expect fun rememberPlatformAppIconSwitcher(): AppIconSwitcher?

@Composable
fun rememberAppIconSwitcher(): AppIconSwitcher? = LocalAppIconSwitcher.current ?: rememberPlatformAppIconSwitcher()

/**
 * Gives the entry of PPF in the list of recent applications the name and icon the launcher shows
 * (« Outils » in discreet mode, also for the screen reader). Android only; nothing elsewhere.
 */
@Composable
expect fun KeepTaskLikeLauncher()
