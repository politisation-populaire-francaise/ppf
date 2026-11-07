package org.politisationpopulairefrancaise.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec

/**
 * Not available yet on iOS: the application is not built for it until the Apple Developer account
 * exists. Without a codec, photos would be cleaned without decoding (lossless) and could not be
 * blurred; without a picker, none can be chosen. The iOS build will bring PHPickerViewController and
 * a codec on UIImage or Skia.
 */
actual fun platformImageCodec(): ImageCodec? = null

actual fun ArgbImage.toImageBitmap(): ImageBitmap = ImageBitmap(width, height)

@Composable
actual fun rememberImagePicker(onResult: (PickResult) -> Unit): ImagePicker = remember {
    object : ImagePicker {
        override val available: Boolean = false
        override fun pick() = Unit
    }
}
