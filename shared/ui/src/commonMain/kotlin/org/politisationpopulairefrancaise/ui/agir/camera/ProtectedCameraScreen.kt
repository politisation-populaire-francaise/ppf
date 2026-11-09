package org.politisationpopulairefrancaise.ui.agir.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.camera.ProtectedPhoto
import org.politisationpopulairefrancaise.ui.agir.security.LocalDeviceSecurity
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.camera_android_only
import org.politisationpopulairefrancaise.ui.resources.camera_back
import org.politisationpopulairefrancaise.ui.resources.camera_cancel
import org.politisationpopulairefrancaise.ui.resources.camera_capture_failed
import org.politisationpopulairefrancaise.ui.resources.camera_delete
import org.politisationpopulairefrancaise.ui.resources.camera_delete_confirm
import org.politisationpopulairefrancaise.ui.resources.camera_delete_failed
import org.politisationpopulairefrancaise.ui.resources.camera_delete_text
import org.politisationpopulairefrancaise.ui.resources.camera_delete_title
import org.politisationpopulairefrancaise.ui.resources.camera_deleted
import org.politisationpopulairefrancaise.ui.resources.camera_entry_count
import org.politisationpopulairefrancaise.ui.resources.camera_entry_open
import org.politisationpopulairefrancaise.ui.resources.camera_entry_text
import org.politisationpopulairefrancaise.ui.resources.camera_gallery_empty
import org.politisationpopulairefrancaise.ui.resources.camera_gallery_loading
import org.politisationpopulairefrancaise.ui.resources.camera_gallery_private
import org.politisationpopulairefrancaise.ui.resources.camera_gallery_title
import org.politisationpopulairefrancaise.ui.resources.camera_how_1
import org.politisationpopulairefrancaise.ui.resources.camera_how_2
import org.politisationpopulairefrancaise.ui.resources.camera_how_3
import org.politisationpopulairefrancaise.ui.resources.camera_how_4
import org.politisationpopulairefrancaise.ui.resources.camera_how_title
import org.politisationpopulairefrancaise.ui.resources.camera_intro
import org.politisationpopulairefrancaise.ui.resources.camera_limits
import org.politisationpopulairefrancaise.ui.resources.camera_not_protected
import org.politisationpopulairefrancaise.ui.resources.camera_open_failed
import org.politisationpopulairefrancaise.ui.resources.camera_photo_label
import org.politisationpopulairefrancaise.ui.resources.camera_save_confirm
import org.politisationpopulairefrancaise.ui.resources.camera_save_failed
import org.politisationpopulairefrancaise.ui.resources.camera_save_gallery
import org.politisationpopulairefrancaise.ui.resources.camera_save_text
import org.politisationpopulairefrancaise.ui.resources.camera_save_title
import org.politisationpopulairefrancaise.ui.resources.camera_saved
import org.politisationpopulairefrancaise.ui.resources.camera_saved_gallery
import org.politisationpopulairefrancaise.ui.resources.camera_saving
import org.politisationpopulairefrancaise.ui.resources.camera_take_title
import org.politisationpopulairefrancaise.ui.resources.camera_testify
import org.politisationpopulairefrancaise.ui.resources.camera_testimony_open
import org.politisationpopulairefrancaise.ui.resources.camera_title
import org.politisationpopulairefrancaise.ui.resources.camera_view_close
import org.politisationpopulairefrancaise.ui.resources.camera_view_loading
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the protected camera. */
object ProtectedCameraTags {
    const val ENTRY = "camera-entry"
    const val ENTRY_OPEN = "camera-entry-open"
    const val UNAVAILABLE = "camera-unavailable"
    const val SCREEN = "camera-screen"
    const val BACK = "camera-back"
    const val PERMISSION = "camera-permission"
    const val PREVIEW = "camera-preview"
    const val SHUTTER = "camera-shutter"
    const val SAVING = "camera-saving"
    const val NOTICE = "camera-notice"
    const val PROBLEM = "camera-problem"
    const val GALLERY = "camera-gallery"
    const val EMPTY = "camera-gallery-empty"
    const val THUMBNAIL = "camera-thumbnail-" // followed by the id of the photo
    const val VIEWER = "camera-viewer"
    const val VIEW_CLOSE = "camera-viewer-close"
    const val PHOTO = "camera-viewer-photo"
    const val TESTIFY = "camera-testify"
    const val SAVE = "camera-save"
    const val SAVE_CONFIRM = "camera-save-confirm"
    const val SAVE_CANCEL = "camera-save-cancel"
    const val DELETE = "camera-delete"
    const val DELETE_CONFIRM = "camera-delete-confirm"
    const val DELETE_CANCEL = "camera-delete-cancel"
}

/** Side of a thumbnail of the gallery: well above the 48 dp of a target. */
private val THUMBNAIL_SIZE = 96.dp

/**
 * The entry of the protected camera in the protest mode: what it does, and the way in; where the
 * platform has none (computer, iOS), a line says it is on the Android application.
 */
@Composable
fun ProtectedCameraEntry(state: ProtectedCameraState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.testTag(ProtectedCameraTags.ENTRY), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.camera_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (state.available) {
            PpfSecondaryButton(onOpen, stringResource(Res.string.camera_entry_open), Modifier.testTag(ProtectedCameraTags.ENTRY_OPEN), icon = Lucide.Camera)
        } else {
            Text(stringResource(Res.string.camera_android_only), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ProtectedCameraTags.UNAVAILABLE))
        }
    }
}

/** The smaller entry of the testimony form, next to the choice of a photo already on the phone. */
@Composable
fun ProtectedCameraTestimonyEntry(state: ProtectedCameraState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (state.available) {
        PpfSecondaryButton(onOpen, stringResource(Res.string.camera_testimony_open), modifier.testTag(ProtectedCameraTags.ENTRY_OPEN), icon = Lucide.Camera)
    } else {
        Text(
            stringResource(Res.string.camera_android_only),
            style = MaterialTheme.typography.bodySmall,
            color = PpfTheme.palette.inkSubtle,
            modifier = modifier.testTag(ProtectedCameraTags.UNAVAILABLE),
        )
    }
}

/**
 * « Caméra protégée » (AG-58, D-089): the camera, then the gallery of PPF, side by side on wide
 * screens, one under the other on phones and tablets. A photo opens over it, with « Témoigner avec
 * cette photo » ([onTestify], where a testimony can receive it), the copy to the phone's gallery
 * after a warning, and the deletion after a confirmation.
 *
 * Decrypted pixels exist only while this screen is shown and PPF is unlocked (M33); the window is
 * kept out of the recent applications and screenshots meanwhile ([KeepPhotosHidden]). [exporter]
 * replaces the platform's copy to the gallery (tests).
 */
@Composable
fun ProtectedCameraScreen(
    state: ProtectedCameraState,
    windowClass: WindowClass,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTestify: ((bytes: ByteArray, takenAt: Long) -> Unit)? = null,
    exporter: GalleryExporter? = null,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val expanded = windowClass.width == WindowWidthClass.EXPANDED
    val security = LocalDeviceSecurity.current
    val locked = security?.isLocked == true
    // Thumbnails are decrypted while the gallery is shown and PPF unlocked; they leave memory otherwise.
    LaunchedEffect(state, locked) { if (locked) state.dropDecrypted() else state.load() }
    DisposableEffect(state) { onDispose { state.dropDecrypted() } }
    if (state.available) KeepPhotosHidden(security?.settings?.hideContent ?: false)
    val saver = exporter ?: rememberGalleryExporter { result -> state.exportFinished(result) }
    val viewing = state.viewing
    if (viewing != null) {
        SystemBack(enabled = true) { state.closeView() }
        PhotoViewer(state, viewing, windowClass, onTestify, saver, modifier)
        return
    }
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ProtectedCameraTags.SCREEN)) {
        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.camera_back), onBack, Modifier.testTag(ProtectedCameraTags.BACK), tint = palette.ink)
        Spacer(Modifier.height(8.dp))
        PageHeader(stringResource(Res.string.camera_title), stringResource(Res.string.camera_intro))
        Spacer(Modifier.height(16.dp))
        if (!state.available) {
            InfoBox(Lucide.Info, Modifier.widthIn(max = 760.dp).testTag(ProtectedCameraTags.UNAVAILABLE)) {
                Text(stringResource(Res.string.camera_android_only), style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TakeBlock(state)
                    HowBlock()
                }
                Column(Modifier.weight(1f)) { GalleryBlock(state) }
            }
        } else {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TakeBlock(state)
                GalleryBlock(state)
                HowBlock()
            }
        }
    }
}

@Composable
private fun TakeBlock(state: ProtectedCameraState) {
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.camera_take_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        ProtectedCameraCapture(onShot = state::protect, onFailed = state::captureFailed, busy = state.saving)
        Spacer(Modifier.height(8.dp))
        if (state.saving) {
            Text(
                stringResource(Res.string.camera_saving),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ProtectedCameraTags.SAVING).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Messages(state)
    }
}

@Composable
private fun HowBlock() {
    InfoBox(Lucide.ShieldCheck) {
        Text(stringResource(Res.string.camera_how_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        listOf(Res.string.camera_how_1, Res.string.camera_how_2, Res.string.camera_how_3, Res.string.camera_how_4).forEach { line ->
            Text("• " + stringResource(line), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.camera_limits), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryBlock(state: ProtectedCameraState) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(ProtectedCameraTags.GALLERY)) {
        Text(stringResource(Res.string.camera_gallery_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.camera_gallery_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        when {
            state.photos.isEmpty() && state.loading -> Text(stringResource(Res.string.camera_gallery_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            state.photos.isEmpty() -> Text(
                stringResource(Res.string.camera_gallery_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(ProtectedCameraTags.EMPTY),
            )
            else -> {
                Text(stringResource(Res.string.camera_entry_count, state.photos.size), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (photo in state.photos) Thumbnail(photo, state.thumbnails[photo.id]) { state.view(photo.id) }
                }
            }
        }
    }
}

/** One photo of the gallery: its thumbnail, decrypted in memory (a lock while it comes), named for the screen reader by its date. */
@Composable
private fun Thumbnail(photo: ProtectedPhoto, bitmap: ImageBitmap?, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    val label = photoLabel(photo)
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(THUMBNAIL_SIZE)
            .clip(shape)
            .background(palette.surface1)
            .border(1.dp, palette.border, shape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = label }
            .testTag(ProtectedCameraTags.THUMBNAIL + photo.id),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(24.dp))
        }
    }
}

/** "Photo du 9 novembre 2026 à 14:30". */
@Composable
private fun photoLabel(photo: ProtectedPhoto): String {
    val (date, time) = ProtectedCameraState.dateAndTime(photo.takenAt)
    return stringResource(Res.string.camera_photo_label, date, time)
}

/** What went right or wrong last, announced politely to the screen reader. */
@Composable
private fun Messages(state: ProtectedCameraState) {
    val palette = PpfTheme.palette
    state.notice?.let { notice ->
        val text = when (notice) {
            CameraNotice.PROTECTED -> Res.string.camera_saved
            CameraNotice.DELETED -> Res.string.camera_deleted
            CameraNotice.EXPORTED -> Res.string.camera_saved_gallery
        }
        Message(text, palette.success, ProtectedCameraTags.NOTICE)
    }
    state.problem?.let { problem ->
        val text = when (problem) {
            CameraProblem.CAPTURE_FAILED -> Res.string.camera_capture_failed
            CameraProblem.NOT_PROTECTED -> Res.string.camera_not_protected
            CameraProblem.OPEN_FAILED -> Res.string.camera_open_failed
            CameraProblem.DELETE_FAILED -> Res.string.camera_delete_failed
            CameraProblem.EXPORT_FAILED -> Res.string.camera_save_failed
        }
        Message(text, palette.accentInk, ProtectedCameraTags.PROBLEM)
    }
}

@Composable
private fun Message(text: StringResource, color: androidx.compose.ui.graphics.Color, tag: String) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(top = 4.dp).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** One photo, decrypted in memory, with what can be done with it. */
@Composable
private fun PhotoViewer(
    state: ProtectedCameraState,
    viewing: ViewedPhoto,
    windowClass: WindowClass,
    onTestify: ((bytes: ByteArray, takenAt: Long) -> Unit)?,
    exporter: GalleryExporter,
    modifier: Modifier,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val id = viewing.photo.id
    var askDelete by remember(id) { mutableStateOf(false) }
    var askSave by remember(id) { mutableStateOf(false) }
    val label = photoLabel(viewing.photo)
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(ProtectedCameraTags.VIEWER)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.camera_view_close), state::closeView, Modifier.testTag(ProtectedCameraTags.VIEW_CLOSE), tint = palette.ink)
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            val bitmap = viewing.bitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag(ProtectedCameraTags.PHOTO),
                )
            }
            if (viewing.loading) Text(stringResource(Res.string.camera_view_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Messages(state)
            if (onTestify != null) {
                PpfPrimaryButton(
                    onClick = { state.withBytes(id) { bytes, photo -> onTestify(bytes, photo.takenAt) } },
                    text = stringResource(Res.string.camera_testify),
                    modifier = Modifier.testTag(ProtectedCameraTags.TESTIFY),
                    icon = Lucide.Send,
                )
            }
            if (exporter.available) {
                PpfSecondaryButton({ askSave = true }, stringResource(Res.string.camera_save_gallery), Modifier.testTag(ProtectedCameraTags.SAVE))
            }
            PpfTextButton({ askDelete = true }, stringResource(Res.string.camera_delete), Modifier.testTag(ProtectedCameraTags.DELETE), color = palette.accentInk)
        }
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(Res.string.camera_delete_title)) },
            text = { Text(stringResource(Res.string.camera_delete_text)) },
            dismissButton = {
                PpfTextButton({ askDelete = false }, stringResource(Res.string.camera_cancel), Modifier.testTag(ProtectedCameraTags.DELETE_CANCEL))
            },
            confirmButton = {
                PpfTextButton({
                    askDelete = false
                    state.delete(id)
                }, stringResource(Res.string.camera_delete_confirm), Modifier.testTag(ProtectedCameraTags.DELETE_CONFIRM), color = palette.accentInk)
            },
        )
    }
    if (askSave) {
        // Loosening a protection on the person's own photo: a plain warning first, cancel offered first (principle 10).
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text(stringResource(Res.string.camera_save_title)) },
            text = { Text(stringResource(Res.string.camera_save_text)) },
            dismissButton = {
                PpfTextButton({ askSave = false }, stringResource(Res.string.camera_cancel), Modifier.testTag(ProtectedCameraTags.SAVE_CANCEL))
            },
            confirmButton = {
                PpfTextButton({
                    askSave = false
                    state.saveToGallery(id, exporter)
                }, stringResource(Res.string.camera_save_confirm), Modifier.testTag(ProtectedCameraTags.SAVE_CONFIRM), color = palette.accentInk)
            },
        )
    }
}
