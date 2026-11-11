package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault

/*
 * The computer keeps no evidence in step 1 (AG-59, same limits as the protected camera, D-089):
 * no key store of the system is used here. The templates and the numbers of the kit stay available.
 */

@Composable
actual fun rememberPlatformEvidenceVault(): ProtectedVault? = null
