package org.politisationpopulairefrancaise.ui.agir.harassment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.harassment.DsaElement
import org.politisationpopulairefrancaise.core.agir.harassment.Evidence
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceFormat
import org.politisationpopulairefrancaise.core.agir.harassment.EvidenceProblem
import org.politisationpopulairefrancaise.core.agir.harassment.HarassmentLinks
import org.politisationpopulairefrancaise.core.agir.harassment.HarassmentReports
import org.politisationpopulairefrancaise.core.agir.harassment.Offence
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.ui.agir.camera.KeepPhotosHidden
import org.politisationpopulairefrancaise.ui.agir.camera.ProtectedCameraScreen
import org.politisationpopulairefrancaise.ui.agir.camera.ProtectedCameraState
import org.politisationpopulairefrancaise.ui.agir.camera.ProtectedCameraTestimonyEntry
import org.politisationpopulairefrancaise.ui.agir.manifestation.EmergencyNumbersBlock
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.agir.security.LocalDeviceSecurity
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.components.leavingHost
import org.politisationpopulairefrancaise.ui.crash.rememberTextSharer
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.media.ImagePicker
import org.politisationpopulairefrancaise.ui.media.PickResult
import org.politisationpopulairefrancaise.ui.media.rememberImagePicker
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.harcelement_add
import org.politisationpopulairefrancaise.ui.resources.harcelement_back
import org.politisationpopulairefrancaise.ui.resources.harcelement_camera_keep
import org.politisationpopulairefrancaise.ui.resources.harcelement_cancel
import org.politisationpopulairefrancaise.ui.resources.harcelement_circle_soon
import org.politisationpopulairefrancaise.ui.resources.harcelement_clipboard_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_complaint_model
import org.politisationpopulairefrancaise.ui.resources.harcelement_complaint_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_copied
import org.politisationpopulairefrancaise.ui.resources.harcelement_copy
import org.politisationpopulairefrancaise.ui.resources.harcelement_danger_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_danger_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_delete
import org.politisationpopulairefrancaise.ui.resources.harcelement_delete_confirm
import org.politisationpopulairefrancaise.ui.resources.harcelement_delete_failed
import org.politisationpopulairefrancaise.ui.resources.harcelement_delete_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_delete_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_deleted
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_camera_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_close
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_digest
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_image_loading
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_kept
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_kept_digest
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_original_digest
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_detail_url
import org.politisationpopulairefrancaise.ui.resources.harcelement_digest_short
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_android_only
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_count
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_empty
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_included
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_loading
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_evidence_unreadable
import org.politisationpopulairefrancaise.ui.resources.harcelement_fiche
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_author
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_author_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_city
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_city_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_date
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_date_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_email
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_explanation
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_explanation_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_name
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_note_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_platform
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_platform_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_text_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_text_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_time
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_time_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_url
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_url_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_field_url_hint
import org.politisationpopulairefrancaise.ui.resources.harcelement_form_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_good_faith
import org.politisationpopulairefrancaise.ui.resources.harcelement_good_faith_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_identity_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_attached
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_failed
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_malformed
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_pick
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_preview
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_remove
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_too_large
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_unavailable
import org.politisationpopulairefrancaise.ui.resources.harcelement_image_unsupported
import org.politisationpopulairefrancaise.ui.resources.harcelement_intro
import org.politisationpopulairefrancaise.ui.resources.harcelement_item_label
import org.politisationpopulairefrancaise.ui.resources.harcelement_keep
import org.politisationpopulairefrancaise.ui.resources.harcelement_keep_failed
import org.politisationpopulairefrancaise.ui.resources.harcelement_kept
import org.politisationpopulairefrancaise.ui.resources.harcelement_leave_go
import org.politisationpopulairefrancaise.ui.resources.harcelement_leave_stay
import org.politisationpopulairefrancaise.ui.resources.harcelement_leave_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_leave_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_limits
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_explanation
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_good_faith
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_identity
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_location
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_missing_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_no_platform
import org.politisationpopulairefrancaise.ui.resources.harcelement_numbers_source
import org.politisationpopulairefrancaise.ui.resources.harcelement_offence_doxxing
import org.politisationpopulairefrancaise.ui.resources.harcelement_offence_doxxing_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_offence_harassment
import org.politisationpopulairefrancaise.ui.resources.harcelement_offence_harassment_help
import org.politisationpopulairefrancaise.ui.resources.harcelement_offences_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_open_failed
import org.politisationpopulairefrancaise.ui.resources.harcelement_pharos_open
import org.politisationpopulairefrancaise.ui.resources.harcelement_pharos_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_platform_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_police_chat
import org.politisationpopulairefrancaise.ui.resources.harcelement_police_chat_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_preview_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_date
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_future
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_nothing
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_platform
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_time
import org.politisationpopulairefrancaise.ui.resources.harcelement_problem_url
import org.politisationpopulairefrancaise.ui.resources.harcelement_reports_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_reports_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_review_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_saving
import org.politisationpopulairefrancaise.ui.resources.harcelement_seen
import org.politisationpopulairefrancaise.ui.resources.harcelement_share
import org.politisationpopulairefrancaise.ui.resources.harcelement_share_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_support_source
import org.politisationpopulairefrancaise.ui.resources.harcelement_support_text
import org.politisationpopulairefrancaise.ui.resources.harcelement_support_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_tab_complaint
import org.politisationpopulairefrancaise.ui.resources.harcelement_tab_pharos
import org.politisationpopulairefrancaise.ui.resources.harcelement_tab_platform
import org.politisationpopulairefrancaise.ui.resources.harcelement_timestamp_note
import org.politisationpopulairefrancaise.ui.resources.harcelement_title
import org.politisationpopulairefrancaise.ui.resources.harcelement_with_image
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the harassment kit. */
object HarassmentTags {
    const val HOST = "harcelement-host"
    const val ENTRY = "harcelement-entry"
    const val ENTRY_OPEN = "harcelement-entry-open"
    const val SCREEN = "harcelement-screen"
    const val BACK = "harcelement-back"
    const val DANGER = "harcelement-danger"
    const val EVIDENCE = "harcelement-evidence"
    const val UNAVAILABLE = "harcelement-unavailable"
    const val EMPTY = "harcelement-empty"
    const val ADD = "harcelement-add"
    const val FORM = "harcelement-form"
    const val FIELD = "harcelement-field-" // followed by the name of the field
    const val FIELD_ERROR = "harcelement-field-error-" // followed by the name of the field
    const val PICK = "harcelement-pick"
    const val IMAGE = "harcelement-image"
    const val IMAGE_REMOVE = "harcelement-image-remove"
    const val KEEP = "harcelement-keep"
    const val CANCEL = "harcelement-cancel"
    const val SAVING = "harcelement-saving"
    const val NOTICE = "harcelement-notice"
    const val PROBLEM = "harcelement-problem"
    const val ITEM = "harcelement-item-" // followed by the id of the evidence
    const val DIGEST = "harcelement-digest-" // followed by the id of the evidence
    const val DETAIL = "harcelement-detail"
    const val DETAIL_CLOSE = "harcelement-detail-close"
    const val DETAIL_DIGEST = "harcelement-detail-digest"
    const val DETAIL_IMAGE = "harcelement-detail-image"
    const val DELETE = "harcelement-delete"
    const val DELETE_CONFIRM = "harcelement-delete-confirm"
    const val DELETE_CANCEL = "harcelement-delete-cancel"
    const val REPORTS = "harcelement-reports"
    const val TAB = "harcelement-tab-" // followed by platform, pharos or complaint
    const val OFFENCE = "harcelement-offence-" // followed by harassment or doxxing
    const val GOOD_FAITH = "harcelement-good-faith"
    const val INCLUDE = "harcelement-include-" // followed by the id of the evidence
    const val MISSING = "harcelement-missing"
    const val PREVIEW = "harcelement-preview"
    const val COPY = "harcelement-copy"
    const val SHARE = "harcelement-share"
    const val OFFICIAL_LINK = "harcelement-official-link"
    const val LEAVE_DIALOG = "harcelement-leave"
    const val LEAVE_STAY = "harcelement-leave-stay"
    const val LEAVE_GO = "harcelement-leave-go"
    const val SUPPORT = "harcelement-support"
    const val POLICE_CHAT = "harcelement-police-chat"
    const val CIRCLE_SOON = "harcelement-circle-soon"
    const val FICHE = "harcelement-fiche"

    /** The names of the fields, after [FIELD]. */
    const val PLATFORM = "platform"
    const val URL = "url"
    const val DATE = "date"
    const val TIME = "time"
    const val TEXT = "text"
    const val NOTE = "note"
    const val EXPLANATION = "explanation"
    const val NAME = "name"
    const val EMAIL = "email"
    const val AUTHOR = "author"
    const val CITY = "city"
}

/** How a filled template leaves the screen: copied, or shared by the system; never sent by PPF. */
interface ReportSharing {
    fun copy(text: String)

    /** Null where the platform has no share sheet (computer): copying is offered alone. */
    val share: ((String) -> Unit)?
}

/** The clipboard of the platform and its share sheet (Android), as the crash report uses them. */
@Composable
fun rememberReportSharing(): ReportSharing {
    // The suspending LocalClipboard needs a ClipEntry built by platform code; plain text is enough here.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val sharer = rememberTextSharer(stringResource(Res.string.harcelement_share_title))
    return remember(clipboard, sharer) {
        object : ReportSharing {
            override fun copy(text: String) {
                clipboard.setText(AnnotatedString(text))
            }

            override val share: ((String) -> Unit)? = sharer
        }
    }
}

/**
 * « Harcèlement en ligne » (AG-59): what to do in danger, the evidence kept encrypted on the device
 * (Android), the three templates (the hosting platform under the DSA, PHAROS, the prosecutor), the
 * lines that help, and the fiche. Side by side on wide screens, one under the other elsewhere. An
 * opened piece of evidence, the protected camera and the fiche are drawn over it, each with its way
 * back.
 *
 * Decrypted pictures exist only while this screen is shown and PPF is unlocked (M33); the window is
 * kept out of the recent applications and screenshots meanwhile ([KeepPhotosHidden]). [picker],
 * [sharing] and [camera] replace the platform's (tests); [guide] gives the fiche of the kit if the
 * device holds it.
 */
@Composable
fun HarassmentKitScreen(
    state: HarassmentKitState,
    windowClass: WindowClass,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    picker: ImagePicker? = null,
    sharing: ReportSharing? = null,
    camera: ProtectedCameraState? = null,
    guide: (String) -> Article? = { null },
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val expanded = windowClass.width == WindowWidthClass.EXPANDED
    val security = LocalDeviceSecurity.current
    val locked = security?.isLocked == true
    LaunchedEffect(state, locked) { if (locked) state.dropDecrypted() else state.load() }
    DisposableEffect(state) { onDispose { state.dropDecrypted() } }
    if (state.available) KeepPhotosHidden(security?.settings?.hideContent ?: false)
    val chooser = picker ?: rememberImagePicker { result ->
        when (result) {
            is PickResult.Picked -> state.attachImage(result.file.bytes, EvidenceFormat.SOURCE_PICKER)
            PickResult.TooLarge -> state.imageFailed(KitProblem.IMAGE_TOO_LARGE)
            PickResult.Failed -> state.imageFailed(KitProblem.IMAGE_FAILED)
            PickResult.Cancelled -> Unit
        }
    }
    val share = sharing ?: rememberReportSharing()
    var cameraOpen by remember { mutableStateOf(false) }
    var ficheOpen by remember { mutableStateOf(false) }
    if (cameraOpen && camera != null) {
        SystemBack(enabled = true) { cameraOpen = false }
        ProtectedCameraScreen(
            camera, windowClass, onBack = { cameraOpen = false }, modifier = modifier,
            // The photo is handed to the form, cleaned again by the chain of the kit; it also stays in the camera.
            onTestify = { bytes, _ ->
                state.attachImage(bytes, EvidenceFormat.SOURCE_CAMERA)
                cameraOpen = false
            },
            testifyLabel = Res.string.harcelement_camera_keep,
        )
        return
    }
    val viewing = state.viewing
    if (viewing != null) {
        SystemBack(enabled = true) { state.closeView() }
        EvidenceDetail(state, viewing, windowClass, modifier)
        return
    }
    if (ficheOpen) {
        SystemBack(enabled = true) { ficheOpen = false }
        Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
            GuideReader(guide(HarassmentLinks.FICHE_SLUG), onBack = { ficheOpen = false })
        }
        return
    }
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(HarassmentTags.SCREEN)) {
        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.harcelement_back), onBack, Modifier.testTag(HarassmentTags.BACK), tint = palette.ink)
        Spacer(Modifier.height(8.dp))
        PageHeader(stringResource(Res.string.harcelement_title), stringResource(Res.string.harcelement_intro))
        Spacer(Modifier.height(16.dp))
        val openCamera: (() -> Unit)? = camera?.takeIf { it.available }?.let { { cameraOpen = true } }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DangerBlock()
                    EvidenceBlock(state, chooser, camera, openCamera)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReportsBlock(state, share)
                    SupportBlock(state) { ficheOpen = true }
                }
            }
        } else {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DangerBlock()
                EvidenceBlock(state, chooser, camera, openCamera)
                ReportsBlock(state, share)
                SupportBlock(state) { ficheOpen = true }
            }
        }
    }
}

/** The 17, the 112 and the 114 first: PPF never replaces the emergency services. */
@Composable
private fun DangerBlock() {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(HarassmentTags.DANGER)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.harcelement_danger_title), style = MaterialTheme.typography.titleMedium, color = palette.accentInk, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.harcelement_danger_text), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        EmergencyNumbersBlock(EmergencyNumbers.harassmentDanger, source = Res.string.harcelement_numbers_source)
    }
}

@Composable
private fun EvidenceBlock(state: HarassmentKitState, picker: ImagePicker, camera: ProtectedCameraState?, onOpenCamera: (() -> Unit)?) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(HarassmentTags.EVIDENCE)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Lock, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.harcelement_evidence_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.harcelement_evidence_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        if (!state.available) {
            InfoBox(Lucide.Info, Modifier.testTag(HarassmentTags.UNAVAILABLE)) {
                Text(stringResource(Res.string.harcelement_evidence_android_only), style = MaterialTheme.typography.bodyMedium)
            }
            return@PpfCard
        }
        Messages(state)
        when {
            state.evidence.isEmpty() && state.loading -> Text(stringResource(Res.string.harcelement_evidence_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            state.evidence.isEmpty() -> Text(
                stringResource(Res.string.harcelement_evidence_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(HarassmentTags.EMPTY),
            )
            else -> {
                Text(stringResource(Res.string.harcelement_evidence_count, state.evidence.size), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in state.evidence) EvidenceRow(item) { state.view(item.id) }
                }
            }
        }
        if (state.unreadable > 0) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.harcelement_evidence_unreadable, state.unreadable), style = MaterialTheme.typography.bodySmall, color = palette.accentInk)
        }
        Spacer(Modifier.height(12.dp))
        if (state.adding) {
            EvidenceForm(state, picker, camera, onOpenCamera)
        } else {
            PpfPrimaryButton(state::startAdding, stringResource(Res.string.harcelement_add), Modifier.testTag(HarassmentTags.ADD), icon = Lucide.Plus)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.harcelement_timestamp_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/** One piece of evidence of the list: when it was seen, where, and the start of its digest. */
@Composable
private fun EvidenceRow(item: Evidence, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    val content = item.content
    val (date, time) = EvidenceFormat.dateAndTime(content.seenAt)
    val platform = content.platform.ifBlank { stringResource(Res.string.harcelement_no_platform) }
    val label = stringResource(Res.string.harcelement_item_label, date, time, platform)
    PpfCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }.testTag(HarassmentTags.ITEM + item.id),
        onClick = onOpen,
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(stringResource(Res.string.harcelement_seen, date, time), style = MaterialTheme.typography.titleSmall)
        Text(platform, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (content.url.isNotBlank()) {
            Text(content.url, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (content.image != null) {
            Text(stringResource(Res.string.harcelement_with_image), style = MaterialTheme.typography.bodySmall, color = palette.brand)
        }
        Text(
            stringResource(Res.string.harcelement_digest_short, item.digest.take(16)),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = palette.inkSubtle,
            modifier = Modifier.testTag(HarassmentTags.DIGEST + item.id),
        )
    }
}

@Composable
private fun EvidenceForm(state: HarassmentKitState, picker: ImagePicker, camera: ProtectedCameraState?, onOpenCamera: (() -> Unit)?) {
    val palette = PpfTheme.palette
    val draft = state.draft
    val problems = state.draftProblems
    Inset(Modifier.fillMaxWidth().testTag(HarassmentTags.FORM)) {
        Text(stringResource(Res.string.harcelement_form_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        DraftField(HarassmentTags.PLATFORM, Res.string.harcelement_field_platform, draft.platform, Res.string.harcelement_field_platform_hint, problems) { value -> state.updateDraft { it.copy(platform = value) } }
        DraftField(
            HarassmentTags.URL, Res.string.harcelement_field_url, draft.url, Res.string.harcelement_field_url_hint, problems,
            help = Res.string.harcelement_field_url_help, keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true,
        ) { value -> state.updateDraft { it.copy(url = value) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                DraftField(HarassmentTags.DATE, Res.string.harcelement_field_date, draft.seenDate, Res.string.harcelement_field_date_hint, problems, singleLine = true) { value ->
                    state.updateDraft { it.copy(seenDate = value.take(16)) }
                }
            }
            Column(Modifier.weight(1f)) {
                DraftField(HarassmentTags.TIME, Res.string.harcelement_field_time, draft.seenTime, Res.string.harcelement_field_time_hint, problems, singleLine = true) { value ->
                    state.updateDraft { it.copy(seenTime = value.take(8)) }
                }
            }
        }
        DraftField(HarassmentTags.TEXT, Res.string.harcelement_field_text, draft.text, Res.string.harcelement_field_text_hint, problems, help = Res.string.harcelement_field_text_help, minLines = 3) { value ->
            state.updateDraft { it.copy(text = value) }
        }
        DraftField(HarassmentTags.NOTE, Res.string.harcelement_field_note, draft.note, Res.string.harcelement_field_note_hint, problems, minLines = 2) { value ->
            state.updateDraft { it.copy(note = value) }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.harcelement_image_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        val attached = state.draftImage
        if (attached != null) {
            Column(Modifier.testTag(HarassmentTags.IMAGE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.draftThumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(Res.string.harcelement_image_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = 160.dp),
                    )
                }
                Text(stringResource(Res.string.harcelement_image_attached), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                PpfTextButton(state::removeImage, stringResource(Res.string.harcelement_image_remove), Modifier.testTag(HarassmentTags.IMAGE_REMOVE), color = palette.accentInk)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picker.available) {
                    PpfSecondaryButton(picker::pick, stringResource(Res.string.harcelement_image_pick), Modifier.testTag(HarassmentTags.PICK), icon = Lucide.Camera)
                } else {
                    Text(stringResource(Res.string.harcelement_image_unavailable), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                if (camera != null && onOpenCamera != null) ProtectedCameraTestimonyEntry(camera, onOpenCamera)
            }
        }
        problems.firstOrNull { it == EvidenceProblem.NOTHING_TO_KEEP }?.let { problem ->
            Spacer(Modifier.height(8.dp))
            ErrorLine(problemText(problem), HarassmentTags.FIELD_ERROR + "nothing")
        }
        Spacer(Modifier.height(16.dp))
        if (state.saving) {
            Text(
                stringResource(Res.string.harcelement_saving),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(HarassmentTags.SAVING).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PpfPrimaryButton(state::keep, stringResource(Res.string.harcelement_keep), Modifier.testTag(HarassmentTags.KEEP), enabled = !state.saving, icon = Lucide.Lock)
            PpfTextButton(state::cancelAdding, stringResource(Res.string.harcelement_cancel), Modifier.testTag(HarassmentTags.CANCEL))
        }
    }
}

/** The problems of the form that belong to [field]. */
private fun problemsOf(field: String, problems: Set<EvidenceProblem>): List<EvidenceProblem> = problems.filter { problem ->
    when (problem) {
        EvidenceProblem.NOTHING_TO_KEEP -> false
        EvidenceProblem.URL_INVALID -> field == HarassmentTags.URL
        EvidenceProblem.DATE_INVALID, EvidenceProblem.IN_FUTURE -> field == HarassmentTags.DATE
        EvidenceProblem.TIME_INVALID -> field == HarassmentTags.TIME
        EvidenceProblem.PLATFORM_TOO_LONG -> field == HarassmentTags.PLATFORM
        EvidenceProblem.TEXT_TOO_LONG -> field == HarassmentTags.TEXT
        EvidenceProblem.NOTE_TOO_LONG -> field == HarassmentTags.NOTE
    }
}

private fun problemText(problem: EvidenceProblem): StringResource = when (problem) {
    EvidenceProblem.NOTHING_TO_KEEP -> Res.string.harcelement_problem_nothing
    EvidenceProblem.URL_INVALID -> Res.string.harcelement_problem_url
    EvidenceProblem.DATE_INVALID -> Res.string.harcelement_problem_date
    EvidenceProblem.TIME_INVALID -> Res.string.harcelement_problem_time
    EvidenceProblem.IN_FUTURE -> Res.string.harcelement_problem_future
    EvidenceProblem.PLATFORM_TOO_LONG -> Res.string.harcelement_problem_platform
    EvidenceProblem.TEXT_TOO_LONG -> Res.string.harcelement_problem_text
    EvidenceProblem.NOTE_TOO_LONG -> Res.string.harcelement_problem_note
}

/** A labelled field of the form, with its help line and the problems that belong to it. */
@Composable
private fun DraftField(
    field: String,
    label: StringResource,
    value: String,
    placeholder: StringResource,
    problems: Set<EvidenceProblem>,
    help: StringResource? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    LabelledField(label, help) {
        PpfTextField(
            value, onChange, stringResource(placeholder),
            Modifier.fillMaxWidth().testTag(HarassmentTags.FIELD + field),
            minLines = minLines, singleLine = singleLine, keyboardOptions = keyboard,
        )
    }
    problemsOf(field, problems).forEach { problem -> ErrorLine(problemText(problem), HarassmentTags.FIELD_ERROR + field) }
}

@Composable
private fun LabelledField(label: StringResource, help: StringResource?, content: @Composable () -> Unit) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
    if (help != null) {
        Spacer(Modifier.height(2.dp))
        Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun ErrorLine(text: StringResource, tag: String) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp).testTag(tag)) {
        Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.accentInk, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = palette.accentInk)
    }
}

/** What went right or wrong last, announced politely to the screen reader. */
@Composable
private fun Messages(state: HarassmentKitState) {
    val palette = PpfTheme.palette
    state.notice?.let { notice ->
        val text = when (notice) {
            KitNotice.KEPT -> Res.string.harcelement_kept
            KitNotice.DELETED -> Res.string.harcelement_deleted
        }
        Message(text, palette.success, HarassmentTags.NOTICE)
    }
    state.problem?.let { problem ->
        val text = when (problem) {
            KitProblem.KEEP_FAILED -> Res.string.harcelement_keep_failed
            KitProblem.OPEN_FAILED -> Res.string.harcelement_open_failed
            KitProblem.DELETE_FAILED -> Res.string.harcelement_delete_failed
            KitProblem.IMAGE_UNSUPPORTED -> Res.string.harcelement_image_unsupported
            KitProblem.IMAGE_TOO_LARGE -> Res.string.harcelement_image_too_large
            KitProblem.IMAGE_MALFORMED -> Res.string.harcelement_image_malformed
            KitProblem.IMAGE_FAILED -> Res.string.harcelement_image_failed
        }
        Message(text, palette.accentInk, HarassmentTags.PROBLEM)
    }
}

@Composable
private fun Message(text: StringResource, color: Color, tag: String) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(bottom = 8.dp).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** One piece of evidence, its picture decrypted in memory, its digests, and its deletion. */
@Composable
private fun EvidenceDetail(state: HarassmentKitState, viewing: ViewedEvidence, windowClass: WindowClass, modifier: Modifier) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val item = viewing.evidence
    val content = item.content
    var askDelete by remember(item.id) { mutableStateOf(false) }
    val (date, time) = EvidenceFormat.dateAndTime(content.seenAt)
    val (keptDate, keptTime) = EvidenceFormat.dateAndTime(content.keptAt)
    val title = stringResource(Res.string.harcelement_seen, date, time)
    Column(modifier.fillMaxSize().background(palette.surface1).verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(HarassmentTags.DETAIL)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.harcelement_detail_close), state::closeView, Modifier.testTag(HarassmentTags.DETAIL_CLOSE), tint = palette.ink)
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(content.platform.ifBlank { stringResource(Res.string.harcelement_no_platform) }, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            Text(stringResource(Res.string.harcelement_detail_kept, keptDate, keptTime), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            Messages(state)
            viewing.bitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(Res.string.harcelement_image_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag(HarassmentTags.DETAIL_IMAGE),
                )
            }
            if (viewing.loading) Text(stringResource(Res.string.harcelement_detail_image_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (content.url.isNotBlank()) DetailLine(Res.string.harcelement_detail_url, content.url)
                    if (content.text.isNotBlank()) DetailLine(Res.string.harcelement_detail_text, content.text)
                    if (content.note.isNotBlank()) DetailLine(Res.string.harcelement_detail_note, content.note)
                    DetailLine(Res.string.harcelement_detail_digest, item.digest, monospace = true, tag = HarassmentTags.DETAIL_DIGEST)
                    content.image?.let { image ->
                        DetailLine(Res.string.harcelement_detail_original_digest, image.originalSha256, monospace = true)
                        DetailLine(Res.string.harcelement_detail_kept_digest, image.sha256, monospace = true)
                    }
                }
            }
            if (content.image?.source == EvidenceFormat.SOURCE_CAMERA) {
                Text(stringResource(Res.string.harcelement_detail_camera_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            PpfTextButton({ askDelete = true }, stringResource(Res.string.harcelement_delete), Modifier.testTag(HarassmentTags.DELETE), color = palette.accentInk)
        }
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(Res.string.harcelement_delete_title)) },
            text = { Text(stringResource(Res.string.harcelement_delete_text)) },
            dismissButton = {
                PpfTextButton({ askDelete = false }, stringResource(Res.string.harcelement_cancel), Modifier.testTag(HarassmentTags.DELETE_CANCEL))
            },
            confirmButton = {
                PpfTextButton({
                    askDelete = false
                    state.delete(item.id)
                }, stringResource(Res.string.harcelement_delete_confirm), Modifier.testTag(HarassmentTags.DELETE_CONFIRM), color = palette.accentInk)
            },
        )
    }
}

@Composable
private fun DetailLine(label: StringResource, value: String, monospace: Boolean = false, tag: String? = null) {
    Column(if (tag != null) Modifier.testTag(tag) else Modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            style = if (monospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The three templates, filled from the evidence included, copied or shared by the person herself. */
@Composable
private fun ReportsBlock(state: HarassmentKitState, sharing: ReportSharing) {
    val palette = PpfTheme.palette
    var leavingFor by remember { mutableStateOf<String?>(null) }
    PpfCard(Modifier.fillMaxWidth().testTag(HarassmentTags.REPORTS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Flag, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.harcelement_reports_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.harcelement_reports_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        val kinds = ReportKind.entries
        PpfTabs(
            labels = listOf(stringResource(Res.string.harcelement_tab_platform), stringResource(Res.string.harcelement_tab_pharos), stringResource(Res.string.harcelement_tab_complaint)),
            selected = state.report.ordinal,
            onSelect = { state.report = kinds[it] },
            tagFor = { HarassmentTags.TAB + kinds[it].name.lowercase() },
        )
        Spacer(Modifier.height(12.dp))
        when (state.report) {
            ReportKind.PLATFORM -> Text(stringResource(Res.string.harcelement_platform_text), style = MaterialTheme.typography.bodyMedium)
            ReportKind.PHAROS -> {
                Text(stringResource(Res.string.harcelement_pharos_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton({ leavingFor = HarassmentLinks.PHAROS }, stringResource(Res.string.harcelement_pharos_open), Modifier.testTag(HarassmentTags.OFFICIAL_LINK), icon = Lucide.ExternalLink)
            }
            ReportKind.COMPLAINT -> {
                Text(stringResource(Res.string.harcelement_complaint_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton({ leavingFor = HarassmentLinks.COMPLAINT_MODEL }, stringResource(Res.string.harcelement_complaint_model), Modifier.testTag(HarassmentTags.OFFICIAL_LINK), icon = Lucide.ExternalLink)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.harcelement_offences_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        CheckRow(Offence.HARASSMENT in state.offences, Res.string.harcelement_offence_harassment, Res.string.harcelement_offence_harassment_help, HarassmentTags.OFFENCE + "harassment") {
            state.toggleOffence(Offence.HARASSMENT)
        }
        CheckRow(Offence.DOXXING in state.offences, Res.string.harcelement_offence_doxxing, Res.string.harcelement_offence_doxxing_help, HarassmentTags.OFFENCE + "doxxing") {
            state.toggleOffence(Offence.DOXXING)
        }
        ReportField(HarassmentTags.EXPLANATION, Res.string.harcelement_field_explanation, state.explanation, Res.string.harcelement_field_explanation_hint, minLines = 3) { state.explanation = it.take(MAX_EXPLANATION) }
        if (state.report != ReportKind.PHAROS) {
            ReportField(HarassmentTags.NAME, Res.string.harcelement_field_name, state.name, Res.string.harcelement_field_name) { state.name = it.take(MAX_SHORT) }
            ReportField(
                HarassmentTags.EMAIL, Res.string.harcelement_field_email, state.email, Res.string.harcelement_field_email,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email),
            ) { state.email = it.take(MAX_SHORT) }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.harcelement_identity_help), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        if (state.report == ReportKind.PLATFORM) {
            Spacer(Modifier.height(8.dp))
            CheckRow(state.goodFaith, Res.string.harcelement_good_faith, Res.string.harcelement_good_faith_help, HarassmentTags.GOOD_FAITH) { state.goodFaith = !state.goodFaith }
        }
        if (state.report == ReportKind.COMPLAINT) {
            ReportField(HarassmentTags.AUTHOR, Res.string.harcelement_field_author, state.author, Res.string.harcelement_field_author_hint) { state.author = it.take(MAX_SHORT) }
            ReportField(HarassmentTags.CITY, Res.string.harcelement_field_city, state.city, Res.string.harcelement_field_city_hint) { state.city = it.take(MAX_SHORT) }
        }
        if (state.evidence.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.harcelement_evidence_included), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            for (item in state.evidence) {
                val (date, time) = EvidenceFormat.dateAndTime(item.content.seenAt)
                val platform = item.content.platform.ifBlank { stringResource(Res.string.harcelement_no_platform) }
                CheckRowText(item.id !in state.excluded, stringResource(Res.string.harcelement_item_label, date, time, platform), null, HarassmentTags.INCLUDE + item.id) {
                    state.include(item.id, item.id in state.excluded)
                }
            }
        }
        if (state.report == ReportKind.PLATFORM) {
            val missing = HarassmentReports.dsaMissing(state.input())
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                InfoBox(Lucide.Info, Modifier.testTag(HarassmentTags.MISSING)) {
                    Text(stringResource(Res.string.harcelement_missing_title), style = MaterialTheme.typography.labelLarge)
                    for (element in missing) {
                        val text = when (element) {
                            DsaElement.EXPLANATION -> Res.string.harcelement_missing_explanation
                            DsaElement.LOCATION -> Res.string.harcelement_missing_location
                            DsaElement.NAME_AND_EMAIL -> Res.string.harcelement_missing_identity
                            DsaElement.GOOD_FAITH -> Res.string.harcelement_missing_good_faith
                        }
                        Text("• " + stringResource(text), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.harcelement_missing_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val text = state.text()
        Text(stringResource(Res.string.harcelement_preview_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Inset(Modifier.fillMaxWidth().testTag(HarassmentTags.PREVIEW)) {
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(8.dp))
        var copied by remember(text) { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfPrimaryButton(
                onClick = {
                    sharing.copy(text)
                    copied = true
                },
                text = stringResource(if (copied) Res.string.harcelement_copied else Res.string.harcelement_copy),
                icon = if (copied) Lucide.Check else Lucide.Copy,
                modifier = Modifier.testTag(HarassmentTags.COPY),
            )
            sharing.share?.let { share ->
                PpfSecondaryButton({ share(text) }, stringResource(Res.string.harcelement_share), Modifier.testTag(HarassmentTags.SHARE), icon = Lucide.Share2)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.harcelement_clipboard_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Text(stringResource(Res.string.harcelement_review_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    leavingFor?.let { url ->
        LeavePpfDialog(
            url = url,
            title = stringResource(Res.string.harcelement_leave_title),
            text = stringResource(Res.string.harcelement_leave_text, leavingHost(url)),
            stayLabel = stringResource(Res.string.harcelement_leave_stay),
            openLabel = stringResource(Res.string.harcelement_leave_go),
            onStay = { leavingFor = null },
            onDone = { leavingFor = null },
            tags = LeaveDialogTags(HarassmentTags.LEAVE_DIALOG, HarassmentTags.LEAVE_STAY, HarassmentTags.LEAVE_GO),
        )
    }
}

@Composable
private fun ReportField(
    field: String,
    label: StringResource,
    value: String,
    placeholder: StringResource,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    LabelledField(label, null) {
        PpfTextField(
            value, onChange, stringResource(placeholder),
            Modifier.fillMaxWidth().testTag(HarassmentTags.FIELD + field),
            minLines = minLines, singleLine = minLines == 1, keyboardOptions = keyboard,
        )
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: StringResource, help: StringResource?, tag: String, onToggle: () -> Unit) {
    CheckRowText(checked, stringResource(label), help?.let { stringResource(it) }, tag, onToggle)
}

/** A box the person ticks herself, 48 dp high, with the semantics of a checkbox. */
@Composable
private fun CheckRowText(checked: Boolean, label: String, help: String?, tag: String, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.borderStrong, checkmarkColor = palette.onBrand))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}

/** The lines that help, the police in writing, the circle (later) and the fiche. */
@Composable
private fun SupportBlock(state: HarassmentKitState, onOpenFiche: () -> Unit) {
    val palette = PpfTheme.palette
    var leavingFor by remember { mutableStateOf<String?>(null) }
    PpfCard(Modifier.fillMaxWidth().testTag(HarassmentTags.SUPPORT)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.HandHelping, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.harcelement_support_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.harcelement_support_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        EmergencyNumbersBlock(EmergencyNumbers.harassmentSupport, source = Res.string.harcelement_support_source)
        Spacer(Modifier.height(12.dp))
        SupportLink(Res.string.harcelement_police_chat, Res.string.harcelement_police_chat_text, HarassmentTags.POLICE_CHAT) { leavingFor = HarassmentLinks.POLICE_CHAT }
        Spacer(Modifier.height(12.dp))
        // « Soutien du cercle » waits for the circles of M28: one sentence, nothing sent.
        if (!state.circle.available) {
            Text(stringResource(Res.string.harcelement_circle_soon), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(HarassmentTags.CIRCLE_SOON))
            Spacer(Modifier.height(12.dp))
        }
        PpfTextButton(onOpenFiche, stringResource(Res.string.harcelement_fiche), Modifier.testTag(HarassmentTags.FICHE))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.harcelement_limits), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    leavingFor?.let { url ->
        LeavePpfDialog(
            url = url,
            title = stringResource(Res.string.harcelement_leave_title),
            text = stringResource(Res.string.harcelement_leave_text, leavingHost(url)),
            stayLabel = stringResource(Res.string.harcelement_leave_stay),
            openLabel = stringResource(Res.string.harcelement_leave_go),
            onStay = { leavingFor = null },
            onDone = { leavingFor = null },
            tags = LeaveDialogTags(HarassmentTags.LEAVE_DIALOG, HarassmentTags.LEAVE_STAY, HarassmentTags.LEAVE_GO),
        )
    }
}

@Composable
private fun SupportLink(title: StringResource, text: StringResource, tag: String, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PpfSecondaryButton(onOpen, stringResource(title), Modifier.testTag(tag), icon = Lucide.MessageSquare)
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

/** Longest explanation kept in memory, in characters. */
private const val MAX_EXPLANATION = 6_000

/** Longest name, email, author or town, in characters. */
private const val MAX_SHORT = 200
