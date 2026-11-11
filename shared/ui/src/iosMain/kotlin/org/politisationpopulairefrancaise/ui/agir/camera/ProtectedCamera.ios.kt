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
 * Not available yet on iOS (not built until the Apple Developer account exists, D-089). The iOS
 * build will bring the system camera (AVFoundation, capture in memory), the vault in a file of the
 * private container excluded from iCloud backups (`NSURLIsExcludedFromBackupKey`) with a master key
 * of the Keychain (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`), and the cover of the window in
 * the app switcher. Until then the screens say the protected camera is on Android.
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
    // Nothing yet on iOS (see SecureWindow.ios.kt).
}
