package org.politisationpopulairefrancaise.ui.components.qr

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.qr_camera_allow
import org.politisationpopulairefrancaise.ui.resources.qr_camera_needed
import java.util.concurrent.Executors

actual fun encodeQr(text: String): QrMatrix? = AndroidQr.encode(text)

actual val canScanQr: Boolean = true

@Composable
actual fun QrScanner(onRead: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    // Asked when the scanner is shown, never before: the reason is on the screen.
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(modifier.testTag(QrTags.SCANNER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.qr_camera_needed), style = MaterialTheme.typography.bodyMedium)
            PpfPrimaryButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }, text = stringResource(Res.string.qr_camera_allow))
        }
        return
    }
    val owner = remember(context) { context.lifecycleOwner() }
    val read by rememberUpdatedState(onRead)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val provider = remember { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(Unit) {
        onDispose {
            if (provider.isDone) runCatching { provider.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(1f).testTag(QrTags.SCANNER),
        factory = { viewContext ->
            PreviewView(viewContext).also { view ->
                provider.addListener({
                    val cameras = provider.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            // The luminance plane of the camera's image, read here and dropped: nothing is kept.
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            AndroidQr.decode(bytes, plane.rowStride, image.height, image.width, image.height)?.let { text ->
                                ContextCompat.getMainExecutor(viewContext).execute { read(text) }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    if (owner != null) {
                        cameras.unbindAll()
                        cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(viewContext))
            }
        },
    )
}

/** The activity behind a context, as the owner of the camera's lifecycle. */
private fun Context.lifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

/** ZXing on Android: the same calls as on the computer, plus the reading of the camera's images. */
internal object AndroidQr {
    fun encode(text: String): QrMatrix? = try {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M, hints).matrix
        QrMatrix(matrix.width, BooleanArray(matrix.width * matrix.height) { i -> matrix.get(i % matrix.width, i / matrix.width) == ONE })
    } catch (e: WriterException) {
        null
    }

    /** The text of the QR code in the luminance plane [data] ([dataWidth] bytes per row), of [width] by [height] pixels. */
    fun decode(data: ByteArray, dataWidth: Int, dataHeight: Int, width: Int, height: Int): String? = try {
        val source = PlanarYUVLuminanceSource(data, dataWidth, dataHeight, 0, 0, minOf(width, dataWidth), minOf(height, dataHeight), false)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    } catch (e: ReaderException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private const val ONE: Byte = 1
}
