package org.politisationpopulairefrancaise.ui.agir.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault

/*
 * The platform's side of the protected camera (AG-58, D-089): the camera itself, the vault, the
 * copy to the phone's gallery and the window hidden from the system. Everything else (the chain of
 * media, the encryption format, the gallery of PPF) is common code. Android only in step 1: the
 * computer has no camera here and iOS is not built; their actuals say so.
 */

/** How saving a copy to the phone's gallery ended. */
enum class GalleryExport { SAVED, CANCELLED, FAILED }

/** Saves a copy of a protected photo to the phone's gallery, only after the warning of the screen (principle 10). */
interface GalleryExporter {
    /** False where the platform has no gallery to save to (computer, iOS for now): the button is not shown. */
    val available: Boolean

    /**
     * Saves [bytes] (a cleaned JPEG) as [name]; the result reaches the callback given to
     * [rememberGalleryExporter]. The exporter wipes [bytes] once written.
     */
    fun save(bytes: ByteArray, name: String)
}

/**
 * Where this platform keeps the protected photos: on Android, the encrypted vault of the app's
 * no-backup directory with its master key in the Android Keystore; null elsewhere in step 1.
 */
@Composable
expect fun rememberPlatformProtectedVault(): ProtectedVault?

/**
 * The camera of the device and its shutter. On Android: the camera permission asked when this is
 * first shown (never at install), the preview of CameraX and a capture **in memory**
 * (`ImageCapture.takePicture` with `OnImageCapturedCallback`: no file, no MediaStore, nothing in the
 * phone's gallery); [onShot] receives the camera's JPEG bytes and how many degrees they must turn,
 * on the main thread. Elsewhere, a line says the protected camera is on Android.
 */
@Composable
expect fun ProtectedCameraCapture(onShot: (jpeg: ByteArray, rotationDegrees: Int) -> Unit, onFailed: () -> Unit, busy: Boolean, modifier: Modifier = Modifier)

/**
 * The copy to the phone's gallery: on Android 10 and later, MediaStore (the « Pictures » folder,
 * under a neutral name, no permission needed); on Android 8 and 9, the system's document dialog,
 * where the person chooses the folder. Nothing elsewhere.
 */
@Composable
expect fun rememberGalleryExporter(onResult: (GalleryExport) -> Unit): GalleryExporter

/**
 * Keeps the window out of the recent applications, screenshots and screen recordings while the
 * protected camera or its gallery is shown (`FLAG_SECURE` on Android, whatever « Masquer le contenu »
 * says, D-089); back to [settingHides] (the setting of M33) when it leaves. Nothing elsewhere.
 */
@Composable
expect fun KeepPhotosHidden(settingHides: Boolean)
