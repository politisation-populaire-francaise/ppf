package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable

/** Windows, macOS and Linux let no application refuse screenshots in a portable way: not offered on a computer. */
actual val canHideContentFromSystem: Boolean = false

@Composable
actual fun HideContentFromSystem(hide: Boolean) {
    // Nothing to do on a computer.
}
