package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.politisationpopulairefrancaise.core.agir.camera.AndroidProtectedVault
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault

/** The evidence vault of the app's no-backup directory, its own master key in the Android Keystore (AG-59). */
@Composable
actual fun rememberPlatformEvidenceVault(): ProtectedVault? {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidProtectedVault.createEvidence(context) }
}
