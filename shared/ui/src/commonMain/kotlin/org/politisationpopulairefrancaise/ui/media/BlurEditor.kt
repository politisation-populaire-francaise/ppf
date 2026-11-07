package org.politisationpopulairefrancaise.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.media.BlurZone
import org.politisationpopulairefrancaise.core.media.RemovedMetadata
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.media_blur_add
import org.politisationpopulairefrancaise.ui.resources.media_blur_bigger
import org.politisationpopulairefrancaise.ui.resources.media_blur_count
import org.politisationpopulairefrancaise.ui.resources.media_blur_down
import org.politisationpopulairefrancaise.ui.resources.media_blur_help
import org.politisationpopulairefrancaise.ui.resources.media_blur_last
import org.politisationpopulairefrancaise.ui.resources.media_blur_left
import org.politisationpopulairefrancaise.ui.resources.media_blur_remove
import org.politisationpopulairefrancaise.ui.resources.media_blur_right
import org.politisationpopulairefrancaise.ui.resources.media_blur_smaller
import org.politisationpopulairefrancaise.ui.resources.media_blur_title
import org.politisationpopulairefrancaise.ui.resources.media_blur_up
import org.politisationpopulairefrancaise.ui.resources.media_preview_description
import org.politisationpopulairefrancaise.ui.resources.media_removed_comment
import org.politisationpopulairefrancaise.ui.resources.media_removed_exif
import org.politisationpopulairefrancaise.ui.resources.media_removed_icc
import org.politisationpopulairefrancaise.ui.resources.media_removed_iptc
import org.politisationpopulairefrancaise.ui.resources.media_removed_none
import org.politisationpopulairefrancaise.ui.resources.media_removed_other
import org.politisationpopulairefrancaise.ui.resources.media_removed_pictures
import org.politisationpopulairefrancaise.ui.resources.media_removed_title
import org.politisationpopulairefrancaise.ui.resources.media_removed_xmp
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the chain of media's views. */
object MediaTags {
    const val PREVIEW = "media-preview"
    const val ZONE_COUNT = "media-zone-count"
    const val ADD_ZONE = "media-add-zone"
    const val BIGGER = "media-bigger"
    const val SMALLER = "media-smaller"
    const val LEFT = "media-left"
    const val RIGHT = "media-right"
    const val UP = "media-up"
    const val DOWN = "media-down"
    const val REMOVE_ZONE = "media-remove-zone"
    const val REMOVED = "media-removed"
}

/**
 * The tool to blur zones of a photo by hand (CLAUDE.md, section 7: "outil de floutage des visages
 * optionnel"): a touch on the preview places a zone there; buttons place, move, resize and remove
 * zones without any drag (WCAG 2.2, 2.5.7) and with the keyboard. Zones are shown as dark boxes;
 * the irreversible blur is applied to the pixels when the photo is prepared for sending
 * ([org.politisationpopulairefrancaise.core.media.ImageTransforms.blur]). No automatic detection,
 * and no service outside the device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlurEditor(
    preview: ImageBitmap,
    aspectRatio: Float,
    zones: List<BlurZone>,
    onTap: (x: Float, y: Float) -> Unit,
    onAddInCentre: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (scale: Float) -> Unit,
    onRemoveLast: () -> Unit,
    step: Float,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.media_blur_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.media_blur_help), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        val description = stringResource(Res.string.media_preview_description, zones.size)
        Box(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 560.dp).aspectRatio(aspectRatio.coerceIn(0.3f, 3f))
                .testTag(MediaTags.PREVIEW)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onTap(offset.x / size.width, offset.y / size.height) }
                },
        ) {
            Image(preview, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                for ((index, zone) in zones.withIndex()) {
                    val topLeft = Offset(zone.left * size.width, zone.top * size.height)
                    val zoneSize = Size(zone.width * size.width, zone.height * size.height)
                    drawRect(palette.scrim.copy(alpha = 0.9f), topLeft, zoneSize)
                    drawRect(if (index == zones.lastIndex) palette.accent else palette.surface2, topLeft, zoneSize, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Text(stringResource(Res.string.media_blur_count, zones.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag(MediaTags.ZONE_COUNT))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSecondaryButton(onAddInCentre, stringResource(Res.string.media_blur_add), small = true, icon = Lucide.Plus, modifier = Modifier.testTag(MediaTags.ADD_ZONE))
            if (zones.isNotEmpty()) {
                PpfSecondaryButton({ onResize(1.25f) }, stringResource(Res.string.media_blur_bigger), small = true, icon = Lucide.Plus, modifier = Modifier.testTag(MediaTags.BIGGER))
                PpfSecondaryButton({ onResize(0.8f) }, stringResource(Res.string.media_blur_smaller), small = true, icon = Lucide.Minus, modifier = Modifier.testTag(MediaTags.SMALLER))
                PpfSecondaryButton({ onMove(-step, 0f) }, stringResource(Res.string.media_blur_left), small = true, modifier = Modifier.testTag(MediaTags.LEFT))
                PpfSecondaryButton({ onMove(step, 0f) }, stringResource(Res.string.media_blur_right), small = true, modifier = Modifier.testTag(MediaTags.RIGHT))
                PpfSecondaryButton({ onMove(0f, -step) }, stringResource(Res.string.media_blur_up), small = true, icon = Lucide.ChevronUp, modifier = Modifier.testTag(MediaTags.UP))
                PpfSecondaryButton({ onMove(0f, step) }, stringResource(Res.string.media_blur_down), small = true, icon = Lucide.ChevronDown, modifier = Modifier.testTag(MediaTags.DOWN))
                PpfTextButton(onRemoveLast, stringResource(Res.string.media_blur_remove), Modifier.testTag(MediaTags.REMOVE_ZONE), color = palette.accentInk)
            }
        }
        if (zones.isNotEmpty()) Text(stringResource(Res.string.media_blur_last), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/** What the cleaning removes from a photo, said plainly (transparency first). */
@Composable
fun RemovedMetadataList(removed: Set<RemovedMetadata>, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.testTag(MediaTags.REMOVED), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (removed.isEmpty()) {
            Text(stringResource(Res.string.media_removed_none), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            return@Column
        }
        Text(stringResource(Res.string.media_removed_title), style = MaterialTheme.typography.labelLarge)
        for (family in RemovedMetadata.entries.filter { it in removed }) {
            val label = when (family) {
                RemovedMetadata.EXIF -> Res.string.media_removed_exif
                RemovedMetadata.XMP -> Res.string.media_removed_xmp
                RemovedMetadata.IPTC -> Res.string.media_removed_iptc
                RemovedMetadata.ICC_PROFILE -> Res.string.media_removed_icc
                RemovedMetadata.COMMENT -> Res.string.media_removed_comment
                RemovedMetadata.EMBEDDED_PICTURES -> Res.string.media_removed_pictures
                RemovedMetadata.OTHER -> Res.string.media_removed_other
            }
            Text("• " + stringResource(label), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}
