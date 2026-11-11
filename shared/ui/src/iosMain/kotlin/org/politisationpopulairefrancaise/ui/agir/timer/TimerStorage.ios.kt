package org.politisationpopulairefrancaise.ui.agir.timer

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.timer.TimerStorage

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists. The iOS build will keep the timer in a file of the application's private container,
 * excluded from iCloud backups (`NSURLIsExcludedFromBackupKey`). Until then the screen says that
 * the timer is not available on this device: a timer kept in memory only would be lost at the
 * next start, with its secret, and could no longer be cancelled.
 */
@Composable
actual fun rememberPlatformTimerStorage(): TimerStorage? = null
