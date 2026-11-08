package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable

/**
 * Not yet on iOS (not built until the Apple Developer account exists): iOS has no FLAG_SECURE;
 * the usual way is to cover the window when the application resigns active (snapshot of the app
 * switcher). To do with the iOS build.
 */
actual val canHideContentFromSystem: Boolean = false

@Composable
actual fun HideContentFromSystem(hide: Boolean) {
    // Nothing yet on iOS.
}
