package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.runtime.Composable
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault

/*
 * The platform's side of the harassment kit (AG-59): only where the evidence is kept. Everything
 * else (the chain of the pictures, the record, the templates, the screen) is common code. The same
 * limits as the protected camera (D-089): Android only in step 1; the computer has no Keystore here
 * and iOS is not built, so their actuals keep nothing and the screen says so.
 */

/**
 * Where this platform keeps the evidence: on Android, the vault of the protected camera's format
 * in its own no-backup directory (`ppf-harcelement`) under its own master key of the Android
 * Keystore (`AndroidProtectedVault.createEvidence`); null elsewhere in step 1.
 */
@Composable
expect fun rememberPlatformEvidenceVault(): ProtectedVault?
