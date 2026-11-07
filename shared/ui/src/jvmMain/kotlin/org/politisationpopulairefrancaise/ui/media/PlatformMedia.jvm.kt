package org.politisationpopulairefrancaise.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.media_pick_title
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.coroutines.resume

/** Java's ImageIO: JPEG, PNG (and others, but WebP and HEIF are not read: the lossless cleaning then applies). */
private object ImageIoCodec : ImageCodec {
    override fun decode(bytes: ByteArray, maxSide: Int): ArgbImage? {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        if (image.width.toLong() * image.height > MAX_PIXELS) return null
        return ArgbImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    override fun encodeJpeg(image: ArgbImage, quality: Int): ByteArray? = runCatching {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val parameters = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality.coerceIn(1, 100) / 100f
                }
                writer.write(null, IIOImage(buffered, null, null), parameters)
            }
        } finally {
            writer.dispose()
        }
        out.toByteArray()
    }.getOrNull()

    /** 60 megapixels: beyond, a computer may run out of memory decoding it. */
    private const val MAX_PIXELS = 60_000_000L
}

actual fun platformImageCodec(): ImageCodec? = ImageIoCodec

actual fun ArgbImage.toImageBitmap(): ImageBitmap {
    val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    buffered.setRGB(0, 0, width, height, pixels, 0, width)
    return buffered.toComposeImageBitmap()
}

/** On computers, the system's file dialog, limited to the image files the chain knows. */
@Composable
actual fun rememberImagePicker(onResult: (PickResult) -> Unit): ImagePicker {
    val title = stringResource(Res.string.media_pick_title)
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    return remember(title) {
        object : ImagePicker {
            override val available: Boolean = true
            override fun pick() {
                scope.launch {
                    val file = choose(title) ?: return@launch callback(PickResult.Cancelled)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (file.length() > PhotoPipeline.MAX_ORIGINAL_BYTES) PickResult.TooLarge else PickResult.Picked(PickedFile(file.readBytes()))
                        }.getOrDefault(PickResult.Failed)
                    }
                    callback(result)
                }
            }
        }
    }
}

private suspend fun choose(title: String): File? =
    if (EventQueue.isDispatchThread()) {
        open(title)
    } else {
        suspendCancellableCoroutine { continuation ->
            EventQueue.invokeLater { continuation.resume(runCatching { open(title) }.getOrNull()) }
        }
    }

private fun open(title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name)
}
