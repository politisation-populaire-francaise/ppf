package org.politisationpopulairefrancaise.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec

/**
 * The platform's side of the chain of media (core/media): decoding and encoding pictures, showing
 * them, and letting the user choose one. Everything that touches metadata, blur or encryption is
 * common code; only what the system must do lives here.
 */

/** The codec of the platform: BitmapFactory and Bitmap on Android, ImageIO on computers; null where none exists yet (iOS). */
expect fun platformImageCodec(): ImageCodec?

/** Shows decoded pixels in Compose. */
expect fun ArgbImage.toImageBitmap(): ImageBitmap

/** A file the user chose: its bytes exactly as stored (metadata included), never sent as they are. */
class PickedFile(val bytes: ByteArray)

/** How choosing a file ended. */
sealed interface PickResult {
    data class Picked(val file: PickedFile) : PickResult
    data object Cancelled : PickResult
    /** Larger than [org.politisationpopulairefrancaise.core.media.PhotoPipeline.MAX_ORIGINAL_BYTES]: not even read. */
    data object TooLarge : PickResult
    data object Failed : PickResult
}

/** Opens the system's chooser for a photo; [available] is false where the platform has none yet (iOS). */
interface ImagePicker {
    val available: Boolean
    fun pick()
}

/**
 * The photo chooser of the platform: the system photo picker on Android (no permission: the user
 * chooses one photo, the application sees nothing else), the system's file dialog on computers.
 * [onResult] is called on the main thread.
 */
@Composable
expect fun rememberImagePicker(onResult: (PickResult) -> Unit): ImagePicker
