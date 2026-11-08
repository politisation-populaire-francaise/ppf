package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable

/**
 * Not yet on iOS (not built until the Apple Developer account exists). iOS can change the icon
 * (`UIApplication.setAlternateIconName`) but not the name under it, and shows a system alert at
 * each change: to study with the iOS build.
 */
@Composable
actual fun rememberPlatformAppIconSwitcher(): AppIconSwitcher? = null

/** Nothing yet on iOS (no discreet entry there). */
@Composable
actual fun KeepTaskLikeLauncher() {
    // Nothing yet on iOS.
}
