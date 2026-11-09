package org.politisationpopulairefrancaise.ui.agir.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.camera.AndroidProtectedVault
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.camera_permission_allow
import org.politisationpopulairefrancaise.ui.resources.camera_permission_needed
import org.politisationpopulairefrancaise.ui.resources.camera_preview_description
import org.politisationpopulairefrancaise.ui.resources.camera_shutter

/** The encrypted vault of the app's no-backup directory, its master key in the Android Keystore (D-089). */
@Composable
actual fun rememberPlatformProtectedVault(): ProtectedVault? {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidProtectedVault.create(context) }
}

/**
 * CameraX, as the QR scanner of « Décider » uses it (permission asked when shown, preview bound to
 * the lifecycle, unbound when it leaves), with an `ImageCapture` taken **in memory**: the JPEG of
 * the camera arrives in an `ImageProxy`, is copied to a byte array and closed; no file, no
 * MediaStore entry, nothing in the phone's gallery. The flash is off; PPF plays no shutter sound
 * of its own.
 */
@Composable
actual fun ProtectedCameraCapture(onShot: (jpeg: ByteArray, rotationDegrees: Int) -> Unit, onFailed: () -> Unit, busy: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    // Asked when the protected camera is opened, never before: the reason is on the screen.
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(modifier.testTag(ProtectedCameraTags.PERMISSION), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.camera_permission_needed), style = MaterialTheme.typography.bodyMedium)
            PpfPrimaryButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }, text = stringResource(Res.string.camera_permission_allow))
        }
        return
    }
    val owner = LocalLifecycleOwner.current
    val shot by rememberUpdatedState(onShot)
    val failed by rememberUpdatedState(onFailed)
    val provider = remember { ProcessCameraProvider.getInstance(context) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }
    DisposableEffect(Unit) {
        onDispose {
            if (provider.isDone) runCatching { provider.get().unbindAll() }
        }
    }
    val description = stringResource(Res.string.camera_preview_description)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(3f / 4f).semantics { contentDescription = description }.testTag(ProtectedCameraTags.PREVIEW),
            factory = { viewContext ->
                PreviewView(viewContext).also { view ->
                    // Drawn in the window (TextureView) rather than over it: right inside a scrolling page, and covered by FLAG_SECURE.
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    provider.addListener({
                        try {
                            val cameras = provider.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            cameras.unbindAll()
                            cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        } catch (e: Exception) {
                            failed()
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                }
            },
        )
        PpfPrimaryButton(
            onClick = {
                // The callback runs on the main thread: the bytes are copied, the image closed at once.
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val taken = try {
                                if (image.format == android.graphics.ImageFormat.JPEG) {
                                    val buffer = image.planes[0].buffer
                                    buffer.rewind()
                                    ByteArray(buffer.remaining()).also { buffer.get(it) } to image.imageInfo.rotationDegrees
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            } catch (e: OutOfMemoryError) {
                                null
                            } finally {
                                image.close()
                            }
                            if (taken != null) shot(taken.first, taken.second) else failed()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            failed()
                        }
                    },
                )
            },
            text = stringResource(Res.string.camera_shutter),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(ProtectedCameraTags.SHUTTER),
            enabled = !busy,
            icon = Lucide.Camera,
        )
    }
}

/**
 * Android 10 and later: MediaStore, in « Pictures » (no folder named after PPF), no permission
 * needed for the application's own entries; the entry is written while pending, then published.
 * Android 8 and 9 (writing to MediaStore would need the storage permission): the system's document
 * dialog, where the person chooses the folder.
 */
@Composable
actual fun rememberGalleryExporter(onResult: (GalleryExport) -> Unit): GalleryExporter {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val pending = remember { PendingCopy() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ProtectedPhoto.JPEG)) { uri ->
        val bytes = pending.bytes
        pending.bytes = null
        if (bytes == null) {
            callback(GalleryExport.FAILED)
        } else if (uri == null) {
            bytes.fill(0)
            callback(GalleryExport.CANCELLED)
        } else {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                bytes.fill(0)
                callback(if (written) GalleryExport.SAVED else GalleryExport.FAILED)
            }
        }
    }
    return remember(launcher) {
        object : GalleryExporter {
            override val available: Boolean = true

            override fun save(bytes: ByteArray, name: String) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { saveWithMediaStore(context, bytes, name) }
                        bytes.fill(0)
                        callback(if (saved) GalleryExport.SAVED else GalleryExport.FAILED)
                    }
                } else {
                    pending.bytes?.fill(0)
                    pending.bytes = bytes
                    launcher.launch(name)
                }
            }
        }
    }
}

/** The decrypted copy waiting for the document dialog of Android 8 and 9; wiped once written or cancelled. */
private class PendingCopy {
    var bytes: ByteArray? = null
}

/** A pending MediaStore entry in « Pictures », written, then published; removed if anything fails. */
private fun saveWithMediaStore(context: Context, bytes: ByteArray, name: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, ProtectedPhoto.JPEG)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return false
    return try {
        val written = resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        if (written) {
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
        } else {
            resolver.delete(uri, null, null)
        }
        written
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

/**
 * `FLAG_SECURE` on the window while the protected camera or its gallery is shown: no thumbnail in
 * the recent applications, no screenshot, no recording. When it leaves, the window goes back to the
 * setting « Masquer le contenu » of M33 ([settingHides]), which keeps the flag when it is on.
 */
@Composable
actual fun KeepPhotosHidden(settingHides: Boolean) {
    val context = LocalContext.current
    val setting by rememberUpdatedState(settingHides)
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!setting) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
