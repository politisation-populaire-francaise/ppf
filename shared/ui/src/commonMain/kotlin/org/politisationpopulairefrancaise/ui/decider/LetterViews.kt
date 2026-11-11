package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.decider.LetterNoteStore
import org.politisationpopulairefrancaise.core.decider.LetterRecipient
import org.politisationpopulairefrancaise.core.decider.LetterTemplate
import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.elus.MyElus
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_letter_advice
import org.politisationpopulairefrancaise.ui.resources.decider_letter_choose_commune
import org.politisationpopulairefrancaise.ui.resources.decider_letter_contacts
import org.politisationpopulairefrancaise.ui.resources.decider_letter_copied
import org.politisationpopulairefrancaise.ui.resources.decider_letter_copy
import org.politisationpopulairefrancaise.ui.resources.decider_letter_for
import org.politisationpopulairefrancaise.ui.resources.decider_letter_mark_sent
import org.politisationpopulairefrancaise.ui.resources.decider_letter_no_commune
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_answered
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_mark_answered
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_no_answer
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_remove
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_sent
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_unknown
import org.politisationpopulairefrancaise.ui.resources.decider_letter_note_unmark
import org.politisationpopulairefrancaise.ui.resources.decider_letter_notes
import org.politisationpopulairefrancaise.ui.resources.decider_letter_notes_private
import org.politisationpopulairefrancaise.ui.resources.decider_letter_privacy
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_departemental
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_depute
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_europeen
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_maire
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_regional
import org.politisationpopulairefrancaise.ui.resources.decider_letter_recipient_senateur
import org.politisationpopulairefrancaise.ui.resources.decider_letter_subject
import org.politisationpopulairefrancaise.ui.resources.decider_letter_text
import org.politisationpopulairefrancaise.ui.resources.decider_letter_to
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.NextStepsSection
import org.politisationpopulairefrancaise.ui.voir.OriginLine
import org.politisationpopulairefrancaise.ui.voir.SourcesSection
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl

/** Test tags of the model letters (DE-09). */
object LetterTags {
    const val TEXT = "decider-letter-text"
    const val COPY = "decider-letter-copy"
    const val COPIED = "decider-letter-copied"
    const val NO_COMMUNE = "decider-letter-no-commune"
    const val PRIVACY = "decider-letter-privacy"
    const val NOTES = "decider-letter-notes"
    const val MARK_SENT = "decider-letter-mark-sent"
    fun note(index: Int) = "decider-letter-note-$index"
    fun answered(index: Int) = "decider-letter-note-answered-$index"
    fun removeNote(index: Int) = "decider-letter-note-remove-$index"
    fun page(slug: String) = "decider-letter-$slug"
    fun recipient(index: Int) = "decider-letter-recipient-$index"
    fun contact(recipient: LetterRecipient) = "decider-letter-contact-${recipient.key}"
}

internal val LetterRecipient.label: StringResource get() = when (this) {
    LetterRecipient.MAIRE -> Res.string.decider_letter_recipient_maire
    LetterRecipient.DEPUTE -> Res.string.decider_letter_recipient_depute
    LetterRecipient.SENATEUR -> Res.string.decider_letter_recipient_senateur
    LetterRecipient.DEPARTEMENTAL -> Res.string.decider_letter_recipient_departemental
    LetterRecipient.REGIONAL -> Res.string.decider_letter_recipient_regional
    LetterRecipient.EUROPEEN -> Res.string.decider_letter_recipient_europeen
}

@Composable
private fun recipientsText(letter: LetterTemplate): String =
    stringResource(Res.string.decider_letter_for, letter.recipients.map { stringResource(it.label) }.joinToString(", "))

/**
 * The elected officials of the commune chosen on this device in « Mes élus » (D-047), read from the device only (no
 * network); null while nothing is chosen or without a database.
 */
@Composable
internal fun rememberDeviceElus(dependencies: AppDependencies?): MyElus? {
    val deps = dependencies ?: return null
    val database = deps.database ?: return null
    var mine by remember(database) { mutableStateOf<MyElus?>(null) }
    LaunchedEffect(database) {
        mine = withContext(Dispatchers.Default) { runCatching { ElusDirectory(deps.remote, ElusStore(database)).mine() }.getOrNull() }
    }
    return mine
}

/** The card of the home: the title of the model letter and who it is for. */
@Composable
internal fun LetterCard(letter: LetterTemplate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(letter.article.slug))) {
        VoirTitle(letter.title, maxLines = 4)
        Spacer(Modifier.height(6.dp))
        Text(recipientsText(letter), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
    }
}

/**
 * One model letter (DE-09): to whom (the elected officials of the commune chosen on the device, never a guessed civility),
 * the letter filled in on the device and editable, « Copier le courrier », and where the official contacts are (after
 * « Vous quittez PPF »). Nothing is sent nor kept: the text lives in this screen only.
 */
@Composable
internal fun LetterPage(letter: LetterTemplate, mine: MyElus?, today: CivilDate, actions: DeciderActions, notes: LetterNoteStore? = null) {
    val palette = PpfTheme.palette
    val openUrl = rememberOpenUrl()
    val clipboard = LocalClipboardManager.current
    val officials = remember(letter, mine) { letter.recipientsAmong(mine) }
    var chosen by remember(letter, officials) { mutableStateOf(officials.firstOrNull()) }
    val commune = mine?.officials?.commune?.name
    var text by remember(letter, chosen, commune) { mutableStateOf(letter.fill(chosen, commune, today)) }
    var copied by remember(letter) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag(LetterTags.page(letter.article.slug))) {
        VoirTitle(letter.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        letter.article.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(6.dp))
        Text(recipientsText(letter), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.decider_letter_to), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (officials.isEmpty()) {
            Text(stringResource(Res.string.decider_letter_no_commune), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(LetterTags.NO_COMMUNE))
            PpfTextButton(onClick = { actions.next(NextTarget.ToSpace(Space.COMPRENDRE)) }, text = stringResource(Res.string.decider_letter_choose_commune))
        } else {
            officials.forEachIndexed { index, official ->
                PpfRadioRow(
                    label = official.displayName,
                    selected = official == chosen,
                    onClick = { chosen = official },
                    supporting = official.function ?: recipientOf(letter, official)?.let { stringResource(it.label) },
                    modifier = Modifier.testTag(LetterTags.recipient(index)),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.decider_letter_subject, letter.subject), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        PpfTextField(
            value = text,
            onValueChange = {
                text = it
                copied = false
            },
            placeholder = stringResource(Res.string.decider_letter_text),
            minLines = 10,
            modifier = Modifier.fillMaxWidth().testTag(LetterTags.TEXT),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_letter_advice), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        PpfPrimaryButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            },
            text = stringResource(Res.string.decider_letter_copy),
            icon = Lucide.Copy,
            modifier = Modifier.fillMaxWidth().testTag(LetterTags.COPY),
        )
        if (copied) {
            Text(stringResource(Res.string.decider_letter_copied), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(LetterTags.COPIED))
        }
        if (notes != null) {
            Spacer(Modifier.height(16.dp))
            LetterNotesSection(letter, notes, chosen, today)
        }
        val contacts = chosen?.let { official -> letter.contacts.filter { it.recipient.matches(official) } }?.takeIf { it.isNotEmpty() } ?: letter.contacts
        if (contacts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.decider_letter_contacts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            contacts.forEach { contact ->
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(
                    onClick = { openUrl(contact.url) },
                    text = contact.label,
                    icon = Lucide.ExternalLink,
                    modifier = Modifier.fillMaxWidth().testTag(LetterTags.contact(contact.recipient)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoBox(Lucide.Lock, Modifier.testTag(LetterTags.PRIVACY)) {
            Text(stringResource(Res.string.decider_letter_privacy), style = MaterialTheme.typography.bodySmall)
        }
        SourcesSection(letter.sources)
        Spacer(Modifier.height(8.dp))
        NextStepsSection(letter.nextSteps, onNext = actions.next)
        Spacer(Modifier.height(8.dp))
        OriginLine(letter.article)
    }
}

/**
 * « Vos envois » (DE-09, option A of D-119): « J'ai envoyé ce courrier » to the official chosen, then each note with its
 * day and « J'ai reçu une réponse ». On the device only; each note is erased with one gesture.
 */
@Composable
private fun LetterNotesSection(letter: LetterTemplate, store: LetterNoteStore, chosen: ElectedOfficial?, today: CivilDate) {
    val palette = PpfTheme.palette
    var version by remember(letter) { mutableStateOf(0) }
    val notes = remember(letter, version) { store.notesOf(letter.address) }
    Column(Modifier.fillMaxWidth().testTag(LetterTags.NOTES)) {
        Text(stringResource(Res.string.decider_letter_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.decider_letter_notes_private), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        PpfSecondaryButton(
            onClick = {
                store.markSent(letter.address, chosen, today)
                version++
            },
            text = stringResource(Res.string.decider_letter_mark_sent),
            icon = Lucide.Check,
            modifier = Modifier.fillMaxWidth().testTag(LetterTags.MARK_SENT),
        )
        notes.forEachIndexed { index, note ->
            if (index > 0) HorizontalDivider(color = palette.border)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(LetterTags.note(index)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(note.official ?: stringResource(Res.string.decider_letter_note_unknown), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.decider_letter_note_sent, note.sentOn.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    Text(
                        note.answeredOn?.let { stringResource(Res.string.decider_letter_note_answered, it.toFrench()) } ?: stringResource(Res.string.decider_letter_note_no_answer),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                    )
                    PpfTextButton(
                        onClick = {
                            store.setAnswered(note.id, if (note.answeredOn == null) today else null)
                            version++
                        },
                        text = stringResource(if (note.answeredOn == null) Res.string.decider_letter_note_mark_answered else Res.string.decider_letter_note_unmark),
                        modifier = Modifier.testTag(LetterTags.answered(index)),
                    )
                }
                PpfIconButton(
                    Lucide.X,
                    stringResource(Res.string.decider_letter_note_remove),
                    onClick = {
                        store.remove(note.id)
                        version++
                    },
                    modifier = Modifier.testTag(LetterTags.removeNote(index)),
                )
            }
        }
    }
}

private fun recipientOf(letter: LetterTemplate, official: ElectedOfficial): LetterRecipient? = letter.recipients.firstOrNull { it.matches(official) }
