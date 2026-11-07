package org.politisationpopulairefrancaise.ui.agir.testimonies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.testimonies.MyTestimony
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyProblem
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyProgress
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.media.BlurEditor
import org.politisationpopulairefrancaise.ui.media.ImagePicker
import org.politisationpopulairefrancaise.ui.media.PickResult
import org.politisationpopulairefrancaise.ui.media.RemovedMetadataList
import org.politisationpopulairefrancaise.ui.media.rememberImagePicker
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.media_blur_unavailable
import org.politisationpopulairefrancaise.ui.resources.media_failed
import org.politisationpopulairefrancaise.ui.resources.media_malformed
import org.politisationpopulairefrancaise.ui.resources.media_preparing
import org.politisationpopulairefrancaise.ui.resources.media_reencoded_note
import org.politisationpopulairefrancaise.ui.resources.media_too_large
import org.politisationpopulairefrancaise.ui.resources.media_unsupported_audio
import org.politisationpopulairefrancaise.ui.resources.media_unsupported_heif
import org.politisationpopulairefrancaise.ui.resources.media_unsupported_other
import org.politisationpopulairefrancaise.ui.resources.media_unsupported_video
import org.politisationpopulairefrancaise.ui.resources.temoignages_account_create
import org.politisationpopulairefrancaise.ui.resources.temoignages_account_needed
import org.politisationpopulairefrancaise.ui.resources.temoignages_account_needed_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_answering
import org.politisationpopulairefrancaise.ui.resources.temoignages_answering_remove
import org.politisationpopulairefrancaise.ui.resources.temoignages_back
import org.politisationpopulairefrancaise.ui.resources.temoignages_blur_unavailable
import org.politisationpopulairefrancaise.ui.resources.temoignages_charter
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_call
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_charter
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_control
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_date
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_future
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_place
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_text_long
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_text_short
import org.politisationpopulairefrancaise.ui.resources.temoignages_error_time
import org.politisationpopulairefrancaise.ui.resources.temoignages_example_key
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_date
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_place
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_place_help
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_text
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_text_help
import org.politisationpopulairefrancaise.ui.resources.temoignages_field_time
import org.politisationpopulairefrancaise.ui.resources.temoignages_intro
import org.politisationpopulairefrancaise.ui.resources.temoignages_metadata_notice
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_call
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_empty
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_forget
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_forget_confirm
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_forget_text
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_forget_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_item
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_original
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_photo
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_private
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_refresh
import org.politisationpopulairefrancaise.ui.resources.temoignages_mine_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_new
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_cancel
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_confirm
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_text
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_warning
import org.politisationpopulairefrancaise.ui.resources.temoignages_original_warning_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_add
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_change
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_remove
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_title
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_unavailable
import org.politisationpopulairefrancaise.ui.resources.temoignages_photo_video_note
import org.politisationpopulairefrancaise.ui.resources.temoignages_queued
import org.politisationpopulairefrancaise.ui.resources.temoignages_refused
import org.politisationpopulairefrancaise.ui.resources.temoignages_review_notice
import org.politisationpopulairefrancaise.ui.resources.temoignages_send
import org.politisationpopulairefrancaise.ui.resources.temoignages_sending
import org.politisationpopulairefrancaise.ui.resources.temoignages_sent
import org.politisationpopulairefrancaise.ui.resources.temoignages_state_pending
import org.politisationpopulairefrancaise.ui.resources.temoignages_state_published
import org.politisationpopulairefrancaise.ui.resources.temoignages_state_reason
import org.politisationpopulairefrancaise.ui.resources.temoignages_state_rejected
import org.politisationpopulairefrancaise.ui.resources.temoignages_state_unsent
import org.politisationpopulairefrancaise.ui.resources.temoignages_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of "Témoigner". */
object TestimonyTags {
    const val SCREEN = "temoignages-screen"
    const val BACK = "temoignages-back"
    const val TEXT = "temoignages-text"
    const val DATE = "temoignages-date"
    const val TIME = "temoignages-time"
    const val PLACE = "temoignages-place"
    const val ADD_PHOTO = "temoignages-add-photo"
    const val REMOVE_PHOTO = "temoignages-remove-photo"
    const val PHOTO_REFUSAL = "temoignages-photo-refusal"
    const val ORIGINAL = "temoignages-original"
    const val ORIGINAL_CONFIRM = "temoignages-original-confirm"
    const val ORIGINAL_CANCEL = "temoignages-original-cancel"
    const val CHARTER = "temoignages-charter"
    const val SEND = "temoignages-send"
    const val STATE = "temoignages-state"
    const val ACCOUNT_NEEDED = "temoignages-account-needed"
    const val CALL = "temoignages-call"
    const val CALL_REMOVE = "temoignages-call-remove"
    const val EXAMPLE_KEY = "temoignages-example-key"
    const val ERROR = "temoignages-error-" // followed by the problem, lowercase
    const val MINE = "temoignages-mine"
    const val MINE_ITEM = "temoignages-mine-item-" // followed by the id
    const val MINE_FORGET = "temoignages-mine-forget"
    const val NEW = "temoignages-new"
}

/**
 * Opens "Témoigner" from another screen of Agir, linked to a call for witnesses (the button
 * "Je témoigne" of the calls, AP-03): its address `30023:<key>:<d>` and its title. Null where the
 * form is not plugged in (the button then stays disabled).
 */
val LocalTestify = compositionLocalOf<((callAddress: String, callTitle: String) -> Unit)?> { null }

/** Provides [LocalTestify] to [content]. */
@Composable
fun ProvideTestify(testify: (callAddress: String, callTitle: String) -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTestify provides testify, content = content)
}

/**
 * "Témoigner" (AG-07) with "Mes témoignages": the form on the left and the list on the right on
 * wide screens, one under the other on phones. [picker] is the platform's photo chooser by default.
 */
@Composable
fun TestimonyScreen(
    library: TestimonyLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    picker: ImagePicker? = null,
) {
    LaunchedEffect(library) { library.start() }
    val chooser = picker ?: rememberImagePicker { result ->
        when (result) {
            is PickResult.Picked -> library.attach(result.file.bytes)
            PickResult.TooLarge -> library.refusePhoto(PhotoRefusal.TOO_LARGE)
            PickResult.Failed -> library.refusePhoto(PhotoRefusal.FAILED)
            PickResult.Cancelled -> Unit
        }
    }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val expanded = windowClass.width == WindowWidthClass.EXPANDED
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(TestimonyTags.SCREEN)) {
        PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.temoignages_back), onBack, Modifier.testTag(TestimonyTags.BACK), tint = PpfTheme.palette.ink)
        Spacer(Modifier.height(8.dp))
        PageHeader(stringResource(Res.string.temoignages_title), stringResource(Res.string.temoignages_intro))
        Spacer(Modifier.height(16.dp))
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(3f)) { Form(library, session, chooser, onCreateAccount) }
                Column(Modifier.weight(2f)) { Mine(library) }
            }
        } else {
            Column(Modifier.widthIn(max = 720.dp)) {
                Form(library, session, chooser, onCreateAccount)
                Spacer(Modifier.height(24.dp))
                Mine(library)
            }
        }
    }
}

@Composable
private fun Form(library: TestimonyLibrary, session: AccountSession, picker: ImagePicker, onCreateAccount: () -> Unit) {
    val palette = PpfTheme.palette
    val state = library.sendState
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoBox(Lucide.Lock) {
            Text(stringResource(Res.string.temoignages_review_notice), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.temoignages_metadata_notice), style = MaterialTheme.typography.bodyMedium)
        }
        if (library.recipients.isExample) {
            PpfCard(Modifier.fillMaxWidth().testTag(TestimonyTags.EXAMPLE_KEY)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PpfBadge("[EXEMPLE]", BadgeStyle.EXAMPLE)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.temoignages_example_key), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
        when (state) {
            is SendState.Sent, is SendState.Queued -> {
                PpfCard(Modifier.fillMaxWidth().testTag(TestimonyTags.STATE)) {
                    Text(
                        stringResource(if (state is SendState.Sent) Res.string.temoignages_sent else Res.string.temoignages_queued),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state is SendState.Sent) palette.success else palette.ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    PpfSecondaryButton(library::newTestimony, stringResource(Res.string.temoignages_new), Modifier.testTag(TestimonyTags.NEW))
                }
                return@Column
            }
            else -> Unit
        }
        library.call?.let { (_, title) ->
            PpfCard(Modifier.fillMaxWidth().testTag(TestimonyTags.CALL)) {
                Text(stringResource(Res.string.temoignages_answering, title), style = MaterialTheme.typography.bodyLarge)
                PpfTextButton(library::detachCall, stringResource(Res.string.temoignages_answering_remove), Modifier.testTag(TestimonyTags.CALL_REMOVE))
            }
        }
        val problems = if (library.showErrors) library.problems() else emptyList()
        Field(Res.string.temoignages_field_text, Res.string.temoignages_field_text_help, problems, listOf(TestimonyProblem.TEXT_TOO_SHORT, TestimonyProblem.TEXT_TOO_LONG, TestimonyProblem.CONTROL_CHARACTERS)) {
            PpfTextField(library.text, { library.text = it }, stringResource(Res.string.temoignages_field_text), Modifier.fillMaxWidth().testTag(TestimonyTags.TEXT), minLines = 6)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Field(Res.string.temoignages_field_date, null, problems, listOf(TestimonyProblem.BAD_DATE, TestimonyProblem.FUTURE_DATE)) {
                    PpfTextField(library.factDate, { library.factDate = it }, stringResource(Res.string.temoignages_field_date), Modifier.fillMaxWidth().testTag(TestimonyTags.DATE), singleLine = true)
                }
            }
            Column(Modifier.weight(1f)) {
                Field(Res.string.temoignages_field_time, null, problems, listOf(TestimonyProblem.BAD_TIME)) {
                    PpfTextField(library.factTime, { library.factTime = it }, stringResource(Res.string.temoignages_field_time), Modifier.fillMaxWidth().testTag(TestimonyTags.TIME), singleLine = true)
                }
            }
        }
        Field(Res.string.temoignages_field_place, Res.string.temoignages_field_place_help, problems, listOf(TestimonyProblem.PLACE_TOO_LONG)) {
            PpfTextField(library.place, { library.place = it }, stringResource(Res.string.temoignages_field_place), Modifier.fillMaxWidth().testTag(TestimonyTags.PLACE), singleLine = true)
        }
        PhotoSection(library, picker)
        Row(
            Modifier.fillMaxWidth().toggleable(library.charterAccepted, role = Role.Checkbox) { library.charterAccepted = it }.testTag(TestimonyTags.CHARTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(library.charterAccepted, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.temoignages_charter), style = MaterialTheme.typography.bodyMedium)
        }
        Errors(problems, listOf(TestimonyProblem.CHARTER_NOT_ACCEPTED, TestimonyProblem.BAD_CALL))
        val account = session.messagingSigner
        if (account == null) {
            PpfCard(Modifier.fillMaxWidth().testTag(TestimonyTags.ACCOUNT_NEEDED)) {
                Text(stringResource(Res.string.temoignages_account_needed_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.temoignages_account_needed), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfPrimaryButton(onCreateAccount, stringResource(Res.string.temoignages_account_create))
            }
        } else {
            val sending = state == SendState.Sending || library.preparingPhoto
            PpfPrimaryButton(
                { library.send(account) },
                stringResource(if (sending) Res.string.temoignages_sending else Res.string.temoignages_send),
                Modifier.testTag(TestimonyTags.SEND),
                enabled = !sending,
                icon = Lucide.Send,
            )
        }
        when (state) {
            is SendState.Refused -> Text(stringResource(Res.string.temoignages_refused, state.code), color = palette.accentInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(TestimonyTags.STATE))
            SendState.BlurUnavailable -> Text(stringResource(Res.string.temoignages_blur_unavailable), color = palette.accentInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(TestimonyTags.STATE))
            else -> Unit
        }
    }
}

@Composable
private fun Field(label: StringResource, help: StringResource?, problems: List<TestimonyProblem>, mine: List<TestimonyProblem>, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        content()
        help?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle) }
        Errors(problems, mine)
    }
}

@Composable
private fun Errors(problems: List<TestimonyProblem>, mine: List<TestimonyProblem>) {
    for (problem in problems.filter { it in mine }) {
        val text = when (problem) {
            TestimonyProblem.TEXT_TOO_SHORT -> Res.string.temoignages_error_text_short
            TestimonyProblem.TEXT_TOO_LONG -> Res.string.temoignages_error_text_long
            TestimonyProblem.BAD_DATE -> Res.string.temoignages_error_date
            TestimonyProblem.FUTURE_DATE -> Res.string.temoignages_error_future
            TestimonyProblem.BAD_TIME -> Res.string.temoignages_error_time
            TestimonyProblem.PLACE_TOO_LONG -> Res.string.temoignages_error_place
            TestimonyProblem.BAD_CALL -> Res.string.temoignages_error_call
            TestimonyProblem.CHARTER_NOT_ACCEPTED -> Res.string.temoignages_error_charter
            TestimonyProblem.CONTROL_CHARACTERS -> Res.string.temoignages_error_control
        }
        Text(stringResource(text), color = PpfTheme.palette.accentInk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(TestimonyTags.ERROR + problem.name.lowercase()))
    }
}

@Composable
private fun PhotoSection(library: TestimonyLibrary, picker: ImagePicker) {
    val palette = PpfTheme.palette
    var askOriginal by remember { mutableStateOf(false) }
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.temoignages_photo_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        val photo = library.photo
        when {
            library.preparingPhoto -> Text(stringResource(Res.string.media_preparing), style = MaterialTheme.typography.bodyMedium)
            !picker.available && photo == null -> Text(stringResource(Res.string.temoignages_photo_unavailable), style = MaterialTheme.typography.bodyMedium)
            photo == null -> PpfSecondaryButton(picker::pick, stringResource(Res.string.temoignages_photo_add), icon = Lucide.Camera, modifier = Modifier.testTag(TestimonyTags.ADD_PHOTO))
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val preview = photo.preview
                val bitmap = photo.previewBitmap
                if (preview != null && bitmap != null) {
                    BlurEditor(
                        preview = bitmap,
                        aspectRatio = preview.width.toFloat() / preview.height,
                        zones = library.zones,
                        onTap = library::addZoneAt,
                        onAddInCentre = library::addZoneInCentre,
                        onMove = library::moveLastZone,
                        onResize = library::resizeLastZone,
                        onRemoveLast = library::removeLastZone,
                        step = TestimonyLibrary.MOVE_STEP,
                    )
                    Text(stringResource(Res.string.media_reencoded_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                } else {
                    Text(stringResource(Res.string.media_blur_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
                RemovedMetadataList(photo.removed)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (picker.available) PpfSecondaryButton(picker::pick, stringResource(Res.string.temoignages_photo_change), small = true)
                    PpfTextButton(library::removePhoto, stringResource(Res.string.temoignages_photo_remove), Modifier.testTag(TestimonyTags.REMOVE_PHOTO), color = palette.accentInk)
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(library.giveOriginal, role = Role.Switch) { wanted ->
                        if (wanted) askOriginal = true else library.giveOriginal = false
                    }.testTag(TestimonyTags.ORIGINAL),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.temoignages_original_title), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(Res.string.temoignages_original_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(library.giveOriginal, onCheckedChange = null)
                }
            }
        }
        library.photoRefusal?.let { refusal ->
            Spacer(Modifier.height(8.dp))
            val text = when (refusal) {
                PhotoRefusal.VIDEO -> Res.string.media_unsupported_video
                PhotoRefusal.AUDIO -> Res.string.media_unsupported_audio
                PhotoRefusal.HEIF -> Res.string.media_unsupported_heif
                PhotoRefusal.UNKNOWN -> Res.string.media_unsupported_other
                PhotoRefusal.TOO_LARGE -> Res.string.media_too_large
                PhotoRefusal.MALFORMED -> Res.string.media_malformed
                PhotoRefusal.FAILED -> Res.string.media_failed
            }
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk, modifier = Modifier.testTag(TestimonyTags.PHOTO_REFUSAL))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.temoignages_photo_video_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    if (askOriginal) {
        // Loosening a protection: a plain warning first, cancel offered first (principle 10).
        AlertDialog(
            onDismissRequest = { askOriginal = false },
            title = { Text(stringResource(Res.string.temoignages_original_warning_title)) },
            text = { Text(stringResource(Res.string.temoignages_original_warning)) },
            dismissButton = {
                PpfTextButton({ askOriginal = false }, stringResource(Res.string.temoignages_original_cancel), Modifier.testTag(TestimonyTags.ORIGINAL_CANCEL))
            },
            confirmButton = {
                PpfTextButton({
                    library.giveOriginal = true
                    askOriginal = false
                }, stringResource(Res.string.temoignages_original_confirm), Modifier.testTag(TestimonyTags.ORIGINAL_CONFIRM), color = palette.accentInk)
            },
        )
    }
}

@Composable
private fun Mine(library: TestimonyLibrary) {
    val palette = PpfTheme.palette
    var confirmForget by remember { mutableStateOf(false) }
    Column(Modifier.testTag(TestimonyTags.MINE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.temoignages_mine_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.temoignages_mine_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        if (library.mine.isEmpty()) {
            Text(stringResource(Res.string.temoignages_mine_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        for (item in library.mine) MineItem(item)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSecondaryButton(library::refresh, stringResource(Res.string.temoignages_mine_refresh), small = true, icon = Lucide.RefreshCw)
            if (library.mine.isNotEmpty()) {
                PpfTextButton({ confirmForget = true }, stringResource(Res.string.temoignages_mine_forget), Modifier.testTag(TestimonyTags.MINE_FORGET), color = palette.accentInk)
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(Res.string.temoignages_mine_forget_title)) },
            text = { Text(stringResource(Res.string.temoignages_mine_forget_text)) },
            dismissButton = { PpfTextButton({ confirmForget = false }, stringResource(Res.string.temoignages_original_cancel)) },
            confirmButton = {
                PpfTextButton({
                    library.forgetAll()
                    confirmForget = false
                }, stringResource(Res.string.temoignages_mine_forget_confirm), color = palette.accentInk)
            },
        )
    }
}

@Composable
private fun MineItem(item: MyTestimony) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(TestimonyTags.MINE_ITEM + item.id)) {
        val date = ParisTime.moment(item.createdAt).date.toFrench()
        Text(stringResource(Res.string.temoignages_mine_item, date), style = MaterialTheme.typography.labelLarge)
        val details = listOfNotNull(
            if (item.hasPhoto) stringResource(Res.string.temoignages_mine_photo) else null,
            if (item.gaveOriginal) stringResource(Res.string.temoignages_mine_original) else null,
            if (item.callAddress != null) stringResource(Res.string.temoignages_mine_call) else null,
        )
        if (details.isNotEmpty()) Text(details.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(6.dp))
        val (label, style) = when (item.progress) {
            TestimonyProgress.UNSENT -> Res.string.temoignages_state_unsent to BadgeStyle.WARNING
            TestimonyProgress.PENDING -> Res.string.temoignages_state_pending to BadgeStyle.NEUTRAL
            TestimonyProgress.PUBLISHED -> Res.string.temoignages_state_published to BadgeStyle.SUCCESS
            TestimonyProgress.REJECTED -> Res.string.temoignages_state_rejected to BadgeStyle.ACCENT
        }
        PpfBadge(stringResource(label), style)
        item.reason?.takeIf { item.progress == TestimonyProgress.REJECTED }?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.temoignages_state_reason, it), style = MaterialTheme.typography.bodySmall)
        }
    }
}
