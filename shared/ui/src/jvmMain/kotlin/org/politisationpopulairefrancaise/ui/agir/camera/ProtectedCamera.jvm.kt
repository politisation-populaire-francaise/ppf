package org.politisationpopulairefrancaise.ui.agir.camera

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.camera_android_only

/*
 * The computer has no protected camera in step 1 (D-089): no camera is used here, no photo is
 * kept, and the screens say the protected camera is on the Android application.
 */

@Composable
actual fun rememberPlatformProtectedVault(): ProtectedVault? = null

@Composable
actual fun ProtectedCameraCapture(onShot: (jpeg: ByteArray, rotationDegrees: Int) -> Unit, onFailed: () -> Unit, busy: Boolean, modifier: Modifier) {
    Text(stringResource(Res.string.camera_android_only), style = MaterialTheme.typography.bodyMedium, modifier = modifier.testTag(ProtectedCameraTags.UNAVAILABLE))
}

@Composable
actual fun rememberGalleryExporter(onResult: (GalleryExport) -> Unit): GalleryExporter = remember {
    object : GalleryExporter {
        override val available: Boolean = false

        override fun save(bytes: ByteArray, name: String) {
            bytes.fill(0)
        }
    }
}

@Composable
actual fun KeepPhotosHidden(settingHides: Boolean) {
    // Windows, macOS and Linux let no application refuse screenshots in a portable way (see SecureWindow).
}
