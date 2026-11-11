package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault

/*
 * Not available yet on iOS (not built until the Apple Developer account exists). The iOS build will
 * bring the vault of the protected camera (a file of the private container excluded from iCloud
 * backups, a master key of the Keychain `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`), a
 * directory and a key of its own for the evidence. Until then the kit keeps nothing and says so.
 */

@Composable
actual fun rememberPlatformEvidenceVault(): ProtectedVault? = null
