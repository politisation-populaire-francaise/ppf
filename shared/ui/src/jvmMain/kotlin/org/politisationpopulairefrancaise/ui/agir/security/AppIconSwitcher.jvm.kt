package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.runtime.Composable

/** A computer has no launcher icon that the application could change: not offered. */
@Composable
actual fun rememberPlatformAppIconSwitcher(): AppIconSwitcher? = null

/** No task list of the system to rename on a computer. */
@Composable
actual fun KeepTaskLikeLauncher() {
    // Nothing to do on a computer.
}
