package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.proches.ProchesStorage

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists. The iOS build will keep the list in a file of the application's private container,
 * excluded from iCloud backups (`NSURLIsExcludedFromBackupKey`), like the timer. Until then the
 * screen says that the list is not available on this device rather than keeping it in memory only,
 * where it would be lost at the next start.
 */
@Composable
actual fun rememberPlatformProchesStorage(): ProchesStorage? = null
