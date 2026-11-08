package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPointStorage

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists. The iOS build will keep the rally point in a file of the application's private container,
 * excluded from iCloud backups (`NSURLIsExcludedFromBackupKey`). Until then the sheet says that the
 * rally point cannot be kept and invites the user to write it down.
 */
@Composable
actual fun rememberPlatformRallyPointStorage(): RallyPointStorage? = null
