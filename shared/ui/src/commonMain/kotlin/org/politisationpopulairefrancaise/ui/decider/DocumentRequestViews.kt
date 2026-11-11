package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.decider.DocumentAccessMode
import org.politisationpopulairefrancaise.core.decider.DocumentRequestNote
import org.politisationpopulairefrancaise.core.decider.DocumentRequestReminders
import org.politisationpopulairefrancaise.core.decider.DocumentRequestStage
import org.politisationpopulairefrancaise.core.decider.DocumentRequestStore
import org.politisationpopulairefrancaise.core.decider.DocumentRequestTimeline
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_document_administration
import org.politisationpopulairefrancaise.ui.resources.decider_document_administration_unknown
import org.politisationpopulairefrancaise.ui.resources.decider_document_advice
import org.politisationpopulairefrancaise.ui.resources.decider_document_bad_date
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_copy
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_refusal_silence
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_refusal_written
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_site
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_text
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_join_both
import org.politisationpopulairefrancaise.ui.resources.decider_document_cada_join_request
import org.politisationpopulairefrancaise.ui.resources.decider_document_copied
import org.politisationpopulairefrancaise.ui.resources.decider_document_copy
import org.politisationpopulairefrancaise.ui.resources.decider_document_dates_note
import org.politisationpopulairefrancaise.ui.resources.decider_document_document
import org.politisationpopulairefrancaise.ui.resources.decider_document_document_unknown
import org.politisationpopulairefrancaise.ui.resources.decider_document_intro
import org.politisationpopulairefrancaise.ui.resources.decider_document_letter
import org.politisationpopulairefrancaise.ui.resources.decider_document_madada
import org.politisationpopulairefrancaise.ui.resources.decider_document_madada_text
import org.politisationpopulairefrancaise.ui.resources.decider_document_mark_answered
import org.politisationpopulairefrancaise.ui.resources.decider_document_mark_cada
import org.politisationpopulairefrancaise.ui.resources.decider_document_mark_refused
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_consultation
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_consultation_sentence
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_copy
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_copy_sentence
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_email
import org.politisationpopulairefrancaise.ui.resources.decider_document_mode_email_sentence
import org.politisationpopulairefrancaise.ui.resources.decider_document_need_fields
import org.politisationpopulairefrancaise.ui.resources.decider_document_note
import org.politisationpopulairefrancaise.ui.resources.decider_document_prepare_cada
import org.politisationpopulairefrancaise.ui.resources.decider_document_received_line
import org.politisationpopulairefrancaise.ui.resources.decider_document_received_on
import org.politisationpopulairefrancaise.ui.resources.decider_document_remind
import org.politisationpopulairefrancaise.ui.resources.decider_document_remind_stop
import org.politisationpopulairefrancaise.ui.resources.decider_document_remove
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_answered
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_refused_after_cada
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_refused_silence
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_refused_written
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_too_late
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_waiting
import org.politisationpopulairefrancaise.ui.resources.decider_document_stage_with_cada
import org.politisationpopulairefrancaise.ui.resources.decider_document_text
import org.politisationpopulairefrancaise.ui.resources.decider_document_unmark_answered
import org.politisationpopulairefrancaise.ui.resources.decider_document_unmark_cada
import org.politisationpopulairefrancaise.ui.resources.decider_document_unmark_refused
import org.politisationpopulairefrancaise.ui.resources.decider_documents_mine
import org.politisationpopulairefrancaise.ui.resources.decider_documents_mine_private
import org.politisationpopulairefrancaise.ui.resources.decider_documents_section
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl

/** Test tags of « Demander un document administratif » (DE-31). */
object DocumentRequestTags {
    const val PAGE = "decider-documents"
    const val ADMINISTRATION = "decider-document-administration"
    const val DOCUMENT = "decider-document-document"
    const val TEXT = "decider-document-text"
    const val COPY = "decider-document-copy"
    const val COPIED = "decider-document-copied"
    const val MADADA = "decider-document-madada"
    const val RECEIVED_ON = "decider-document-received-on"
    const val NOTE = "decider-document-note"
    const val ERROR = "decider-document-error"
    const val MINE = "decider-documents-mine"
    fun mode(mode: DocumentAccessMode) = "decider-document-mode-${mode.key}"
    fun request(index: Int) = "decider-document-request-$index"
    fun stage(index: Int) = "decider-document-stage-$index"
    fun answered(index: Int) = "decider-document-answered-$index"
    fun refused(index: Int) = "decider-document-refused-$index"
    fun cada(index: Int) = "decider-document-cada-$index"
    fun prepareCada(index: Int) = "decider-document-prepare-cada-$index"
    fun cadaText(index: Int) = "decider-document-cada-text-$index"
    fun remind(index: Int) = "decider-document-remind-$index"
    fun remove(index: Int) = "decider-document-remove-$index"
}

/** The CADA's site, where a referral is made online (opened after « Vous quittez PPF »). */
internal const val CADA_URL: String = "https://www.cada.fr"

/** Ma Dada (OpenKnowledge France), an associative site that helps to word requests and publishes every exchange. */
internal const val MADADA_URL: String = "https://madada.fr"

private val DocumentAccessMode.label: StringResource get() = when (this) {
    DocumentAccessMode.EMAIL -> Res.string.decider_document_mode_email
    DocumentAccessMode.COPY -> Res.string.decider_document_mode_copy
    DocumentAccessMode.CONSULTATION -> Res.string.decider_document_mode_consultation
}

private val DocumentAccessMode.sentence: StringResource get() = when (this) {
    DocumentAccessMode.EMAIL -> Res.string.decider_document_mode_email_sentence
    DocumentAccessMode.COPY -> Res.string.decider_document_mode_copy_sentence
    DocumentAccessMode.CONSULTATION -> Res.string.decider_document_mode_consultation_sentence
}

/** A French day `JJ/MM/AAAA` (or `J/M/AAAA`), not after [today]; null otherwise. */
internal fun parseFrenchDay(text: String, today: CivilDate): CivilDate? {
    val match = Regex("""^\s*(\d{1,2})/(\d{1,2})/(\d{4})\s*$""").find(text) ?: return null
    val (day, month, year) = match.destructured.toList().map { it.toInt() }
    if (!ParisTime.isValidDate(year, month, day)) return null
    return CivilDate(year, month, day).takeIf { it <= today }
}

internal fun CivilDate.toFrenchDigits(): String = "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"

/**
 * « Demander un document administratif » (DE-31): the request written on the device from three answers (which
 * administration, which document, how), copied and sent by the person herself or himself, or through Ma Dada; then the
 * requests noted on this device with their deadlines (one month to answer, two months to ask the CADA) and, on request,
 * reminders. PPF sends nothing and keeps nothing but what the person notes, on the device ([store]).
 */
@Composable
internal fun DocumentRequestPage(store: DocumentRequestStore?, reminders: DeciderRemindersState?, today: CivilDate) {
    val palette = PpfTheme.palette
    val openUrl = rememberOpenUrl()
    val clipboard = LocalClipboardManager.current
    var administration by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DocumentAccessMode.EMAIL) }
    var edited by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val generated = stringResource(
        Res.string.decider_document_letter,
        administration.trim().ifEmpty { stringResource(Res.string.decider_document_administration_unknown) },
        document.trim().ifEmpty { stringResource(Res.string.decider_document_document_unknown) },
        stringResource(mode.sentence),
        today.toFrench(),
    )
    val text = edited ?: generated
    Column(Modifier.fillMaxWidth().testTag(DocumentRequestTags.PAGE)) {
        VoirTitle(stringResource(Res.string.decider_documents_section), style = MaterialTheme.typography.headlineMedium, heading = true)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.decider_document_intro), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(16.dp))
        PpfTextField(
            value = administration,
            onValueChange = {
                administration = it.take(DocumentRequestStore.MAX_ADMINISTRATION)
                edited = null
                copied = false
            },
            placeholder = stringResource(Res.string.decider_document_administration),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.ADMINISTRATION),
        )
        Spacer(Modifier.height(8.dp))
        PpfTextField(
            value = document,
            onValueChange = {
                document = it.take(DocumentRequestStore.MAX_DOCUMENT)
                edited = null
                copied = false
            },
            placeholder = stringResource(Res.string.decider_document_document),
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.DOCUMENT),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.decider_document_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            DocumentAccessMode.entries.forEach { option ->
                PpfRadioRow(
                    label = stringResource(option.label),
                    selected = option == mode,
                    onClick = {
                        mode = option
                        edited = null
                        copied = false
                    },
                    modifier = Modifier.testTag(DocumentRequestTags.mode(option)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PpfTextField(
            value = text,
            onValueChange = {
                edited = it
                copied = false
            },
            placeholder = stringResource(Res.string.decider_document_text),
            minLines = 10,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.TEXT),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_document_advice), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        PpfPrimaryButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            },
            text = stringResource(Res.string.decider_document_copy),
            icon = Lucide.Copy,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.COPY),
        )
        if (copied) {
            Text(stringResource(Res.string.decider_document_copied), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(DocumentRequestTags.COPIED))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_document_madada_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        PpfSecondaryButton(
            onClick = { openUrl(MADADA_URL) },
            text = stringResource(Res.string.decider_document_madada),
            icon = Lucide.ExternalLink,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.MADADA),
        )
        if (store != null) {
            Spacer(Modifier.height(20.dp))
            MyDocumentRequests(store, reminders, today, administration, document, mode)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.decider_document_dates_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/** « Mes demandes »: note the request just sent, then each request with where it stands and what can be done next. */
@Composable
private fun MyDocumentRequests(
    store: DocumentRequestStore,
    reminders: DeciderRemindersState?,
    today: CivilDate,
    administration: String,
    document: String,
    mode: DocumentAccessMode,
) {
    val palette = PpfTheme.palette
    var version by remember { mutableStateOf(0) }
    val requests = remember(version) { store.all() }
    var receivedText by remember(today) { mutableStateOf(today.toFrenchDigits()) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    Column(Modifier.fillMaxWidth().testTag(DocumentRequestTags.MINE)) {
        Text(stringResource(Res.string.decider_documents_mine), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.decider_documents_mine_private), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        PpfTextField(
            value = receivedText,
            onValueChange = {
                receivedText = it.take(10)
                error = null
            },
            placeholder = stringResource(Res.string.decider_document_received_on),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.RECEIVED_ON),
        )
        Text(stringResource(Res.string.decider_document_received_on), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        PpfSecondaryButton(
            onClick = {
                val day = parseFrenchDay(receivedText, today)
                error = when {
                    administration.isBlank() || document.isBlank() -> Res.string.decider_document_need_fields
                    day == null -> Res.string.decider_document_bad_date
                    else -> null
                }
                if (error == null && day != null) {
                    store.add(administration, document, mode, day)
                    version++
                }
            },
            text = stringResource(Res.string.decider_document_note),
            icon = Lucide.Check,
            modifier = Modifier.fillMaxWidth().testTag(DocumentRequestTags.NOTE),
        )
        error?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = palette.warning, modifier = Modifier.testTag(DocumentRequestTags.ERROR))
        }
        requests.forEachIndexed { index, request ->
            Spacer(Modifier.height(8.dp))
            if (index > 0) HorizontalDivider(color = palette.border)
            DocumentRequestRow(index, request, store, reminders, today, onChanged = { version++ })
        }
    }
}

@Composable
private fun DocumentRequestRow(
    index: Int,
    request: DocumentRequestNote,
    store: DocumentRequestStore,
    reminders: DeciderRemindersState?,
    today: CivilDate,
    onChanged: () -> Unit,
) {
    val palette = PpfTheme.palette
    val clipboard = LocalClipboardManager.current
    val openUrl = rememberOpenUrl()
    var showCada by remember(request.id) { mutableStateOf(false) }
    val timeline = DocumentRequestTimeline.of(request, today)
    val stage = DocumentRequestTimeline.stageOf(request, today)
    /** Writes a change, then plans the reminders again from the note as stored. */
    fun change(write: () -> Unit) {
        write()
        store.request(request.id)?.let { reminders?.changed(it) }
        onChanged()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(DocumentRequestTags.request(index))) {
        Text(request.administration, style = MaterialTheme.typography.titleSmall)
        Text(request.document, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        Text(
            stringResource(Res.string.decider_document_received_line, request.receivedOn.toFrench(), stringResource(request.mode.label)),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
        )
        val stageText = when (stage) {
            DocumentRequestStage.WAITING -> stringResource(Res.string.decider_document_stage_waiting, timeline.answerBy.toFrench())
            DocumentRequestStage.REFUSED -> request.refusedOn?.let {
                stringResource(Res.string.decider_document_stage_refused_written, it.toFrench(), timeline.cadaBy?.toFrench().orEmpty())
            } ?: stringResource(Res.string.decider_document_stage_refused_silence, timeline.refusal?.toFrench().orEmpty(), timeline.cadaBy?.toFrench().orEmpty())
            DocumentRequestStage.CADA_TOO_LATE -> stringResource(Res.string.decider_document_stage_too_late, timeline.cadaBy?.toFrench().orEmpty())
            DocumentRequestStage.WITH_CADA -> stringResource(
                Res.string.decider_document_stage_with_cada,
                request.cadaOn?.toFrench().orEmpty(),
                timeline.cadaOpinionBy?.toFrench().orEmpty(),
                timeline.refusedAfterCadaOn?.toFrench().orEmpty(),
            )
            DocumentRequestStage.REFUSED_AFTER_CADA -> stringResource(Res.string.decider_document_stage_refused_after_cada, timeline.refusedAfterCadaOn?.toFrench().orEmpty())
            DocumentRequestStage.ANSWERED -> stringResource(Res.string.decider_document_stage_answered, request.answeredOn?.toFrench().orEmpty())
        }
        Text(
            stageText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (stage == DocumentRequestStage.REFUSED) palette.warning else palette.ink,
            modifier = Modifier.testTag(DocumentRequestTags.stage(index)),
        )
        PpfTextButton(
            onClick = { change { store.setAnswered(request.id, if (request.answeredOn == null) today else null) } },
            text = stringResource(if (request.answeredOn == null) Res.string.decider_document_mark_answered else Res.string.decider_document_unmark_answered),
            modifier = Modifier.testTag(DocumentRequestTags.answered(index)),
        )
        if (request.answeredOn == null) {
            if (request.cadaOn == null) {
                PpfTextButton(
                    onClick = { change { store.setRefused(request.id, if (request.refusedOn == null) today else null) } },
                    text = stringResource(if (request.refusedOn == null) Res.string.decider_document_mark_refused else Res.string.decider_document_unmark_refused),
                    modifier = Modifier.testTag(DocumentRequestTags.refused(index)),
                )
            }
            if (stage == DocumentRequestStage.REFUSED || stage == DocumentRequestStage.WITH_CADA || request.refusedOn != null) {
                PpfTextButton(
                    onClick = { change { store.setCada(request.id, if (request.cadaOn == null) today else null) } },
                    text = stringResource(if (request.cadaOn == null) Res.string.decider_document_mark_cada else Res.string.decider_document_unmark_cada),
                    modifier = Modifier.testTag(DocumentRequestTags.cada(index)),
                )
            }
            if (stage == DocumentRequestStage.REFUSED) {
                PpfTextButton(
                    onClick = { showCada = !showCada },
                    text = stringResource(Res.string.decider_document_prepare_cada),
                    modifier = Modifier.testTag(DocumentRequestTags.prepareCada(index)),
                )
            }
        }
        if (showCada && stage == DocumentRequestStage.REFUSED) {
            val refusalSentence = request.refusedOn?.let { stringResource(Res.string.decider_document_cada_refusal_written, it.toFrench()) }
                ?: stringResource(Res.string.decider_document_cada_refusal_silence)
            val cadaText = stringResource(
                Res.string.decider_document_cada_text,
                request.receivedOn.toFrench(),
                request.administration,
                request.document,
                refusalSentence,
                stringResource(if (request.refusedOn != null) Res.string.decider_document_cada_join_both else Res.string.decider_document_cada_join_request),
            )
            InfoBox(Lucide.ScrollText, Modifier.testTag(DocumentRequestTags.cadaText(index))) {
                Text(cadaText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            PpfSecondaryButton(onClick = { clipboard.setText(AnnotatedString(cadaText)) }, text = stringResource(Res.string.decider_document_cada_copy), icon = Lucide.Copy, small = true)
            Spacer(Modifier.height(6.dp))
            PpfSecondaryButton(onClick = { openUrl(CADA_URL) }, text = stringResource(Res.string.decider_document_cada_site), icon = Lucide.ExternalLink, small = true)
        }
        if (reminders != null && request.answeredOn == null && request.cadaOn == null) {
            val version = reminders.version
            val asked = remember(request.id, version) { reminders.isAsked(request) }
            if (asked) {
                ReminderCategoryNotice(reminders)
                UpcomingReminders(remember(request, version) { reminders.upcoming(request) }, reminders, onStop = { reminders.stop(request) }, stopText = Res.string.decider_document_remind_stop)
            } else if (DocumentRequestReminders.plan(request, ParisTime.toEpochSeconds(today, 0, 0)).isNotEmpty()) {
                PpfTextButton(
                    onClick = { reminders.ask(request) },
                    text = stringResource(Res.string.decider_document_remind),
                    modifier = Modifier.testTag(DocumentRequestTags.remind(index)),
                )
            }
        }
        PpfTextButton(
            onClick = {
                reminders?.stop(request)
                store.remove(request.id)
                onChanged()
            },
            text = stringResource(Res.string.decider_document_remove),
            color = palette.accent,
            modifier = Modifier.testTag(DocumentRequestTags.remove(index)),
        )
    }
}
