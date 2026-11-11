package org.politisationpopulairefrancaise.ui.agir.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.camera.CameraShot
import org.politisationpopulairefrancaise.core.agir.camera.CaptureResult
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedCapture
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedVault
import org.politisationpopulairefrancaise.core.media.ImageCodec
import org.politisationpopulairefrancaise.core.media.PhotoPipeline
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.media.platformImageCodec
import org.politisationpopulairefrancaise.ui.media.toImageBitmap

/** What went wrong last, said under the camera or the photo. */
enum class CameraProblem { CAPTURE_FAILED, NOT_PROTECTED, OPEN_FAILED, DELETE_FAILED, EXPORT_FAILED }

/** What went right last, said the same way. */
enum class CameraNotice { PROTECTED, DELETED, EXPORTED }

/** The photo open in the gallery of PPF: its line, and its pixels decrypted in memory once ready (null while they come, or if they cannot). */
data class ViewedPhoto(val photo: ProtectedPhoto, val bitmap: ImageBitmap?, val loading: Boolean)

/**
 * The protected camera (AG-58, D-089) for the whole run of the shell: the vault of the platform
 * ([vault], null where there is none: computer, iOS), the shots protected one after the other, and
 * the gallery of PPF. Decrypted pixels (thumbnails, the open photo) live in memory only, while the
 * gallery is shown and PPF is unlocked ([dropDecrypted]); never on the disk. Nothing here talks to
 * a server.
 *
 * The quick erase part `agir-camera` ([eraseFromDevice]) is registered by the shell.
 */
class ProtectedCameraState(
    private val vault: ProtectedVault?,
    private val scope: CoroutineScope,
    private val codec: ImageCodec? = platformImageCodec(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** False where this platform has no protected camera: the screens say it is on Android. */
    val available: Boolean get() = vault != null

    /** The photos kept, newest first (their lines only: dates and digests). */
    var photos: List<ProtectedPhoto> by mutableStateOf(emptyList())
        private set

    /** Thumbnails decrypted in memory, by identifier, while the gallery is shown. */
    var thumbnails: Map<String, ImageBitmap> by mutableStateOf(emptyMap())
        private set

    var loading: Boolean by mutableStateOf(false)
        private set

    /** True while a shot goes through the chain and into the vault: the shutter waits. */
    var saving: Boolean by mutableStateOf(false)
        private set

    var viewing: ViewedPhoto? by mutableStateOf(null)
        private set

    var problem: CameraProblem? by mutableStateOf(null)
        private set

    var notice: CameraNotice? by mutableStateOf(null)
        private set

    /** Moved by [dropDecrypted] and [eraseFromDevice]: what was being decrypted before is dropped when it arrives. */
    private var generation = 0
    private var loadJob: Job? = null

    /** Lists the photos and decrypts their thumbnails, one after the other. */
    fun load() {
        val vault = vault ?: return
        loadJob?.cancel()
        loading = true
        val token = generation
        loadJob = scope.launch {
            try {
                val listed = withContext(io) { vault.list() }
                if (token != generation) return@launch
                photos = listed
                for (photo in listed) {
                    if (photo.id in thumbnails) continue
                    // One photo that does not open leaves the others shown (its tile says it is locked).
                    val thumbnail = try {
                        withContext(io) { decrypted(vault, photo.id, THUMBNAIL_SIDE) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (token != generation) return@launch
                    if (thumbnail != null) thumbnails = thumbnails + (photo.id to thumbnail)
                }
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) {
                    loading = false
                    problem = CameraProblem.OPEN_FAILED
                }
            }
        }
    }

    /**
     * A shot of the camera, in memory: through the chain of media ([ProtectedCapture]), then into
     * the vault, encrypted. The camera's bytes are wiped once cleaned.
     */
    fun protect(jpeg: ByteArray, rotationDegrees: Int) {
        val vault = vault ?: return
        saving = true
        problem = null
        notice = null
        val token = generation
        scope.launch {
            try {
                val result = withContext(io) { ProtectedCapture.prepare(CameraShot(jpeg, rotationDegrees, clock())) }
                jpeg.fill(0)
                when (result) {
                    is CaptureResult.Ready -> {
                        val stored = withContext(io) { vault.store(result.bytes, result.meta) }
                        val thumbnail = withContext(io) { thumbnailOf(result.bytes) }
                        result.bytes.fill(0)
                        photos = listOf(stored) + photos.filter { it.id != stored.id }
                        if (thumbnail != null && token == generation) thumbnails = thumbnails + (stored.id to thumbnail)
                        notice = CameraNotice.PROTECTED
                    }
                    is CaptureResult.Refused -> problem = CameraProblem.NOT_PROTECTED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = CameraProblem.NOT_PROTECTED
            } finally {
                saving = false
            }
        }
    }

    /** The camera could not take the photo (the platform said so). */
    fun captureFailed() {
        notice = null
        problem = CameraProblem.CAPTURE_FAILED
    }

    /** Opens the photo [id] in the gallery of PPF, decrypted in memory. */
    fun view(id: String) {
        val vault = vault ?: return
        val photo = photos.firstOrNull { it.id == id } ?: return
        problem = null
        notice = null
        viewing = ViewedPhoto(photo, thumbnails[id], loading = true)
        val token = generation
        scope.launch {
            val bitmap = try {
                withContext(io) { decrypted(vault, id, VIEW_SIDE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (token != generation || viewing?.photo?.id != id) return@launch
            if (bitmap == null) problem = CameraProblem.OPEN_FAILED
            viewing = ViewedPhoto(photo, bitmap, loading = false)
        }
    }

    fun closeView() {
        viewing = null
        problem = null
    }

    /** Deletes the photo [id] (after the confirmation of the screen). */
    fun delete(id: String) {
        val vault = vault ?: return
        scope.launch {
            try {
                withContext(io) { vault.delete(id) }
                photos = photos.filter { it.id != id }
                thumbnails = thumbnails - id
                if (viewing?.photo?.id == id) viewing = null
                problem = null
                notice = CameraNotice.DELETED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = CameraProblem.DELETE_FAILED
            }
        }
    }

    /**
     * Hands the decrypted bytes of the photo [id] to [use], in memory (the testimony, the copy to the
     * phone's gallery); nothing is written by this.
     */
    fun withBytes(id: String, use: (bytes: ByteArray, photo: ProtectedPhoto) -> Unit) {
        val vault = vault ?: return
        val photo = photos.firstOrNull { it.id == id } ?: return
        scope.launch {
            val bytes = try {
                withContext(io) { vault.open(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                problem = CameraProblem.OPEN_FAILED
                return@launch
            }
            use(bytes, photo)
        }
    }

    /** Saves a copy of the photo [id] with [exporter], once the person confirmed the warning (principle 10). */
    fun saveToGallery(id: String, exporter: GalleryExporter) {
        if (!exporter.available) return
        problem = null
        notice = null
        withBytes(id) { bytes, photo -> exporter.save(bytes, galleryName(photo.takenAt)) }
    }

    /** What the exporter said. */
    fun exportFinished(result: GalleryExport) {
        when (result) {
            GalleryExport.SAVED -> notice = CameraNotice.EXPORTED
            GalleryExport.FAILED -> problem = CameraProblem.EXPORT_FAILED
            GalleryExport.CANCELLED -> Unit
        }
    }

    /** The decrypted pixels leave memory: the gallery is no longer shown, or PPF locked itself. */
    fun dropDecrypted() {
        generation++
        loadJob?.cancel()
        loadJob = null
        thumbnails = emptyMap()
        viewing = null
        loading = false
    }

    /**
     * The part `agir-camera` of the quick erase (S-44): the vault destroys its master key, then its
     * files ([ProtectedVault.eraseAll]); the screens forget everything. Waits for the end, throws
     * when something is left, may be called again.
     */
    suspend fun eraseFromDevice() {
        dropDecrypted()
        photos = emptyList()
        problem = null
        notice = null
        val vault = vault ?: return
        withContext(io) { vault.eraseAll() }
    }

    /** The pixels of the photo [id], reduced to [side], decrypted in memory; its bytes are wiped once drawn. */
    private suspend fun decrypted(vault: ProtectedVault, id: String, side: Int): ImageBitmap? {
        val bytes = vault.open(id) ?: return null
        try {
            return thumbnailOf(bytes, side)
        } finally {
            bytes.fill(0)
        }
    }

    private fun thumbnailOf(bytes: ByteArray, side: Int = THUMBNAIL_SIDE): ImageBitmap? =
        runCatching { PhotoPipeline.preview(bytes, codec, maxSide = side)?.toImageBitmap() }.getOrNull()

    companion object {
        /** Longest side of a thumbnail, in pixels. */
        const val THUMBNAIL_SIDE: Int = 320

        /** Longest side of the open photo, in pixels. */
        const val VIEW_SIDE: Int = 1600

        /** A neutral name for the copy in the phone's gallery, like the ones of camera apps (nothing names PPF). */
        fun galleryName(takenAt: Long): String {
            val moment = ParisTime.moment(takenAt)
            val date = moment.date
            return "IMG_" + date.year.toString().padStart(4, '0') + two(date.month) + two(date.day) + "_" + two(moment.hour) + two(moment.minute) + ".jpg"
        }

        /** "9 novembre 2026" and "14:30", Paris time like the agenda, for the label of a photo. */
        fun dateAndTime(takenAt: Long): Pair<String, String> {
            val moment = ParisTime.moment(takenAt)
            return moment.date.toFrench() to (two(moment.hour) + ":" + two(moment.minute))
        }

        private fun two(value: Int): String = value.toString().padStart(2, '0')
    }
}

/** Lets tests and previews replace the vault; null uses the platform's. */
val LocalProtectedVault = staticCompositionLocalOf<ProtectedVault?> { null }

/** The protected camera for as long as its caller stays on screen (the shell keeps it for the whole run). */
@Composable
fun rememberProtectedCameraState(): ProtectedCameraState {
    val provided = LocalProtectedVault.current
    val platform = rememberPlatformProtectedVault()
    val vault = provided ?: platform
    val scope = rememberCoroutineScope()
    return remember(vault) { ProtectedCameraState(vault, scope) }
}
