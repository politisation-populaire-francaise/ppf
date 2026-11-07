package org.politisationpopulairefrancaise.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import java.io.ByteArrayOutputStream

/**
 * Android's BitmapFactory and Bitmap. Decoding is reduced by powers of two on the way
 * (`inSampleSize`) so that a 50-megapixel photo does not exhaust the memory of an old phone; it
 * reads JPEG, PNG, WebP and, from Android 9, HEIF. Neither applies the EXIF orientation: the common
 * code does.
 */
private object AndroidCodec : ImageCodec {
    override fun decode(bytes: ByteArray, maxSide: Int): ArgbImage? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { bitmap ->
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    ArgbImage(bitmap.width, bitmap.height, pixels)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    override fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray? = try {
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }
}

actual fun platformImageCodec(): ImageCodec? = AndroidCodec

actual fun ArgbImage.toImageBitmap(): ImageBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/**
 * The system photo picker (`PickVisualMedia`, images only): no permission is asked, the user
 * chooses one photo and the application reads that one only. On phones without the picker, the
 * system falls back to its document chooser.
 */
@Composable
actual fun rememberImagePicker(onResult: (PickResult) -> Unit): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            callback(PickResult.Cancelled)
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            // Read at most one byte past the limit: a larger file is refused without being read entirely.
                            val bytes = input.readNBytesCompat(PhotoPipeline.MAX_ORIGINAL_BYTES + 1)
                            if (bytes.size > PhotoPipeline.MAX_ORIGINAL_BYTES) PickResult.TooLarge else PickResult.Picked(PickedFile(bytes))
                        } ?: PickResult.Failed
                    } catch (e: OutOfMemoryError) {
                        PickResult.Failed
                    } catch (e: Exception) {
                        PickResult.Failed
                    }
                }
                callback(result)
            }
        }
    }
    return remember(launcher) {
        object : ImagePicker {
            override val available: Boolean = true
            override fun pick() {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

/** InputStream.readNBytes exists from Android 13 only. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
