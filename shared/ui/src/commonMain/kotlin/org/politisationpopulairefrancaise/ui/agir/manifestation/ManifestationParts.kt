package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyChannel
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumber
import org.politisationpopulairefrancaise.core.agir.manifestation.EmergencyNumbers
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPoint
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.help_back
import org.politisationpopulairefrancaise.ui.resources.help_call
import org.politisationpopulairefrancaise.ui.resources.help_guide_missing
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_call
import org.politisationpopulairefrancaise.ui.resources.help_lawyer_none
import org.politisationpopulairefrancaise.ui.resources.help_no_dialer
import org.politisationpopulairefrancaise.ui.resources.help_number_croix_rouge
import org.politisationpopulairefrancaise.ui.resources.help_number_cyberharcelement
import org.politisationpopulairefrancaise.ui.resources.help_number_enfance
import org.politisationpopulairefrancaise.ui.resources.help_number_enfant_disparu
import org.politisationpopulairefrancaise.ui.resources.help_number_europe
import org.politisationpopulairefrancaise.ui.resources.help_number_police
import org.politisationpopulairefrancaise.ui.resources.help_number_pompiers
import org.politisationpopulairefrancaise.ui.resources.help_number_samu
import org.politisationpopulairefrancaise.ui.resources.help_number_sos_amitie
import org.politisationpopulairefrancaise.ui.resources.help_number_sourds
import org.politisationpopulairefrancaise.ui.resources.help_number_suicide
import org.politisationpopulairefrancaise.ui.resources.help_number_victimes
import org.politisationpopulairefrancaise.ui.resources.help_number_violences_femmes
import org.politisationpopulairefrancaise.ui.resources.help_numbers_source
import org.politisationpopulairefrancaise.ui.resources.help_rally_add
import org.politisationpopulairefrancaise.ui.resources.help_rally_cancel
import org.politisationpopulairefrancaise.ui.resources.help_rally_clear
import org.politisationpopulairefrancaise.ui.resources.help_rally_details
import org.politisationpopulairefrancaise.ui.resources.help_rally_edit
import org.politisationpopulairefrancaise.ui.resources.help_rally_empty
import org.politisationpopulairefrancaise.ui.resources.help_rally_place
import org.politisationpopulairefrancaise.ui.resources.help_rally_place_hint
import org.politisationpopulairefrancaise.ui.resources.help_rally_save
import org.politisationpopulairefrancaise.ui.resources.help_rally_saved
import org.politisationpopulairefrancaise.ui.resources.help_rally_text
import org.politisationpopulairefrancaise.ui.resources.help_rally_unavailable
import org.politisationpopulairefrancaise.ui.resources.help_sms
import org.politisationpopulairefrancaise.ui.resources.help_soon_badge
import org.politisationpopulairefrancaise.ui.resources.help_soon_state
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the AIDE button, its sheet and the protest mode. */
object ManifestationTags {
    const val HELP_BUTTON = "help-button"
    const val SHEET = "help-sheet"
    const val SHEET_CLOSE = "help-sheet-close"
    const val NUMBER = "help-number-" // followed by the id of the number
    const val LEGAL = "help-legal-" // followed by the slug of the organisation
    const val LEGAL_NONE = "help-legal-none"
    const val GUIDE_LINK = "help-guide-" // followed by the slug of the guide
    const val GUIDE = "help-guide-reader"
    const val GUIDE_BACK = "help-guide-back"
    const val SOON = "help-soon-" // followed by the id of the function
    const val TALK = "help-talk"
    const val RALLY = "help-rally"
    const val RALLY_ADD = "help-rally-add"
    const val RALLY_PLACE = "help-rally-place"
    const val RALLY_DETAILS = "help-rally-details"
    const val RALLY_SAVE = "help-rally-save"
    const val RALLY_CLEAR = "help-rally-clear"
    const val RALLY_VALUE = "help-rally-value"
    const val SCREEN = "manifestation-screen"
    const val CHECK = "manifestation-check-" // followed by the number of the item
    const val TERRITORY = "manifestation-territory"
    const val OPEN_HELP = "manifestation-open-help"
    const val INJURY = "manifestation-injury"
}

/** What a number is for, in words (the number itself is never the only information). */
internal fun numberLabel(number: EmergencyNumber): StringResource = when (number.id) {
    EmergencyNumbers.SAMU.id -> Res.string.help_number_samu
    EmergencyNumbers.EUROPE.id -> Res.string.help_number_europe
    EmergencyNumbers.POLICE.id -> Res.string.help_number_police
    EmergencyNumbers.FIREFIGHTERS.id -> Res.string.help_number_pompiers
    EmergencyNumbers.DEAF.id -> Res.string.help_number_sourds
    EmergencyNumbers.CHILDREN.id -> Res.string.help_number_enfance
    EmergencyNumbers.MISSING_CHILD.id -> Res.string.help_number_enfant_disparu
    EmergencyNumbers.VICTIMS.id -> Res.string.help_number_victimes
    EmergencyNumbers.WOMEN.id -> Res.string.help_number_violences_femmes
    EmergencyNumbers.CYBER_HARASSMENT.id -> Res.string.help_number_cyberharcelement
    EmergencyNumbers.RED_CROSS_LISTENING.id -> Res.string.help_number_croix_rouge
    EmergencyNumbers.SOS_AMITIE.id -> Res.string.help_number_sos_amitie
    else -> Res.string.help_number_suicide
}

/**
 * Opens a `tel:`, `sms:` or `mailto:` address with the system (the dialer asks the user to confirm,
 * no permission is needed). Returns false when the platform has nothing to open it with (a computer).
 */
@Composable
internal fun rememberDialer(): (String) -> Boolean {
    val handler = LocalUriHandler.current
    return remember(handler) { { uri -> runCatching { handler.openUri(uri) }.isSuccess } }
}

@Composable
internal fun SectionHeading(text: String, icon: ImageVector, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = PpfTheme.palette.ink) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.semantics { heading() })
    }
}

/**
 * The emergency numbers: the 15 and the 112 as large accent buttons, then the 17, the 18 and the
 * 114 by text message. [onFailed] is told when the platform could not dial.
 */
@Composable
internal fun EmergencyNumbersBlock(numbers: List<EmergencyNumber>, modifier: Modifier = Modifier, source: StringResource = Res.string.help_numbers_source) {
    val dial = rememberDialer()
    var failed by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.forEach { number ->
            NumberButton(number, onClick = { failed = !dial(number.uri) })
        }
        if (failed) {
            Text(stringResource(Res.string.help_no_dialer), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        }
        Text(stringResource(source), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
private fun NumberButton(number: EmergencyNumber, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    val action = stringResource(if (number.channel == EmergencyChannel.SMS) Res.string.help_sms else Res.string.help_call, number.number)
    val label = stringResource(numberLabel(number))
    val vital = number.vital
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = if (vital) {
            ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
        } else {
            ButtonDefaults.buttonColors(containerColor = palette.surface1, contentColor = palette.ink)
        },
        border = if (vital) null else androidx.compose.foundation.BorderStroke(1.dp, palette.borderStrong),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (vital) 64.dp else 52.dp)
            .semantics { contentDescription = "$action. $label" }
            .testTag(ManifestationTags.NUMBER + number.id),
    ) {
        Icon(if (number.channel == EmergencyChannel.SMS) Lucide.MessageSquare else Lucide.Phone, contentDescription = null, modifier = Modifier.size(if (vital) 24.dp else 20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(action, style = if (vital) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The legal teams and volunteer lawyers near the user, each called in one gesture. */
@Composable
internal fun LegalContactsBlock(help: HelpState, modifier: Modifier = Modifier) {
    val dial = rememberDialer()
    var failed by remember { mutableStateOf(false) }
    val contacts = help.legalContacts
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (contacts.isEmpty()) {
            Text(stringResource(Res.string.help_lawyer_none), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted, modifier = Modifier.testTag(ManifestationTags.LEGAL_NONE))
        }
        contacts.forEach { organisation ->
            val phone = organisation.phone ?: return@forEach
            val uri = organisation.phoneUri ?: return@forEach
            val spoken = lawyerCallLabel(organisation.displayName, phone)
            Inset(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(organisation.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                            if (organisation.example) {
                                Spacer(Modifier.width(6.dp))
                                ExampleBadge()
                            }
                        }
                        Text(organisation.territory, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    PpfSecondaryButton(
                        onClick = { failed = !dial(uri) },
                        text = phone,
                        icon = Lucide.Phone,
                        small = true,
                        modifier = Modifier
                            .semantics { contentDescription = spoken }
                            .testTag(ManifestationTags.LEGAL + organisation.slug),
                    )
                }
            }
        }
        if (failed) {
            Text(stringResource(Res.string.help_no_dialer), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        }
    }
}

/** The spoken name of a lawyer's button: "Appeler <name> au <phone>". */
@Composable
internal fun lawyerCallLabel(name: String, phone: String): String = stringResource(Res.string.help_lawyer_call, name, phone)

/** A link to a guide read inside the sheet or the protest mode; the guide may not be on the device yet. */
@Composable
internal fun GuideLink(label: String, slug: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    PpfSecondaryButton(
        onClick = { onOpen(slug) },
        text = label,
        icon = Lucide.BookOpen,
        modifier = modifier.fillMaxWidth().testTag(ManifestationTags.GUIDE_LINK + slug),
    )
}

/** A function announced but not ready: its name, "Bientôt", one honest sentence; disabled. */
@Composable
internal fun SoonItem(id: String, title: String, text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val state = stringResource(Res.string.help_soon_state)
    Inset(
        modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {
                disabled()
                stateDescription = state
            }
            .testTag(ManifestationTags.SOON + id),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = palette.ink, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    PpfBadge(stringResource(Res.string.help_soon_badge), BadgeStyle.NEUTRAL)
                }
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
    }
}

/** The rally point kept on the device (AG-36): shown, noted, changed or erased. */
@Composable
internal fun RallyPointBlock(help: HelpState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val point = help.rallyPoint
    var editing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    Column(modifier.testTag(ManifestationTags.RALLY), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.help_rally_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        if (!help.canKeepRallyPoint) {
            Text(stringResource(Res.string.help_rally_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (editing) {
            PpfTextField(
                value = place,
                onValueChange = { place = it.take(RallyPoint.MAX_PLACE) },
                placeholder = stringResource(Res.string.help_rally_place) + " : " + stringResource(Res.string.help_rally_place_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(ManifestationTags.RALLY_PLACE),
            )
            PpfTextField(
                value = details,
                onValueChange = { details = it.take(RallyPoint.MAX_DETAILS) },
                placeholder = stringResource(Res.string.help_rally_details),
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag(ManifestationTags.RALLY_DETAILS),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PpfPrimaryButton(
                    onClick = {
                        val typed = RallyPoint.of(place, details)
                        if (typed != null && help.saveRallyPoint(typed)) {
                            editing = false
                            saved = true
                        }
                    },
                    text = stringResource(Res.string.help_rally_save),
                    enabled = place.isNotBlank(),
                    modifier = Modifier.testTag(ManifestationTags.RALLY_SAVE),
                )
                PpfTextButton(onClick = { editing = false }, text = stringResource(Res.string.help_rally_cancel))
            }
        } else if (point == null) {
            Text(stringResource(Res.string.help_rally_empty), style = MaterialTheme.typography.bodyMedium)
            PpfSecondaryButton(
                onClick = {
                    place = ""
                    details = ""
                    saved = false
                    editing = true
                },
                text = stringResource(Res.string.help_rally_add),
                icon = Lucide.MapPin,
                modifier = Modifier.testTag(ManifestationTags.RALLY_ADD),
            )
        } else {
            Inset(Modifier.fillMaxWidth().testTag(ManifestationTags.RALLY_VALUE)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Lucide.MapPin, contentDescription = null, tint = palette.accent, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    SelectionContainer {
                        Column {
                            Text(point.place, style = MaterialTheme.typography.titleMedium)
                            if (point.details.isNotEmpty()) Text(point.details, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (saved) Text(stringResource(Res.string.help_rally_saved), style = MaterialTheme.typography.bodySmall, color = palette.success)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PpfSecondaryButton(
                    onClick = {
                        place = point.place
                        details = point.details
                        saved = false
                        editing = true
                    },
                    text = stringResource(Res.string.help_rally_edit),
                    icon = Lucide.Pencil,
                    small = true,
                    modifier = Modifier.testTag(ManifestationTags.RALLY_ADD),
                )
                PpfTextButton(
                    onClick = {
                        help.clearRallyPoint()
                        saved = false
                    },
                    text = stringResource(Res.string.help_rally_clear),
                    color = palette.accentInk,
                    modifier = Modifier.testTag(ManifestationTags.RALLY_CLEAR),
                )
            }
        }
    }
}

/**
 * A practical guide read inside the sheet or the protest mode, as "Comprendre" shows it (its
 * [EXEMPLE] marker, its text), with a way back. Null [article]: not on the device yet.
 */
@Composable
internal fun GuideReader(article: Article?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag(ManifestationTags.GUIDE)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.help_back), onBack, Modifier.testTag(ManifestationTags.GUIDE_BACK), tint = PpfTheme.palette.ink)
            Spacer(Modifier.height(8.dp))
            if (article == null) {
                Text(stringResource(Res.string.help_guide_missing), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            if (article.title.trimStart().startsWith(EXAMPLE_MARKER)) {
                ExampleBadge()
                Spacer(Modifier.height(8.dp))
            }
            Text(
                article.title.trimStart().removePrefix(EXAMPLE_MARKER).trim(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            SelectionContainer { MarkdownText(article.body) }
        }
    }
}

private const val EXAMPLE_MARKER = "[EXEMPLE]"
