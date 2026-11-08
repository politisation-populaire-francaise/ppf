package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.politisationpopulairefrancaise.core.decider.ComparatorPhotos
import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.media.ArgbImage
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.MediaDigest
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.media.platformImageCodec
import org.politisationpopulairefrancaise.ui.media.toImageBitmap

/**
 * The official photos of a comparator (DE-16, Q3), all or nothing: they are asked together for every candidate of
 * the sheet (never one alone, which would tell the server which candidate is looked at), each checked against the
 * fingerprint written in the signed sheet, and shown only when every one of them arrived and could be read.
 */
class ComparatorPhotosState(
    private val remote: RemoteEvents?,
    private val codec: ImageCodec? = platformImageCodec(),
    /** Draws decoded pixels; replaceable in the tests, which have no screen. */
    private val draw: (ArgbImage) -> ImageBitmap = { it.toImageBitmap() },
) {
    private val shown = mutableStateMapOf<String, Map<String, ImageBitmap>>()

    /** The photos of [sheet] by candidate identifier, or null while none is to be shown. */
    fun photos(sheet: ComparatorSheet): Map<String, ImageBitmap>? = shown[sheet.article.event.id]

    suspend fun load(sheet: ComparatorSheet) {
        val key = sheet.article.event.id
        if (key in shown || !sheet.photosShown) return
        val source = remote ?: return
        val decoder = codec ?: return
        val photos = sheet.candidates.mapNotNull { sheet.photoOf(it) }
        val images = coroutineScope {
            photos.map { photo ->
                async(Dispatchers.Default) {
                    val bytes = source.fetchComparatorPhoto(photo.sha256) ?: return@async null
                    if (bytes.size > ComparatorPhotos.MAX_BYTES || MediaDigest.sha256Hex(bytes) != photo.sha256) return@async null
                    val pixels = decoder.decode(bytes, PHOTO_SIDE) ?: return@async null
                    photo.candidate to draw(pixels)
                }
            }.awaitAll()
        }
        // One missing or wrong photo, and none is shown: the same treatment for every candidate.
        if (images.any { it == null }) return
        shown[key] = images.filterNotNull().toMap()
    }

    companion object {
        /** Decoded near this side, in pixels: enough for a square of 64 to 96 dp. */
        const val PHOTO_SIDE: Int = 256
    }
}

@Composable
fun rememberComparatorPhotosState(dependencies: AppDependencies): ComparatorPhotosState =
    remember(dependencies) { ComparatorPhotosState(dependencies.remote) }
