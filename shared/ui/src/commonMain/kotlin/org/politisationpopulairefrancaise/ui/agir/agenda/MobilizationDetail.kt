package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.ICalendar
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.agir.followups.MobilizationFollowUps
import org.politisationpopulairefrancaise.ui.agir.poster.PosterTags
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.affiche_make
import org.politisationpopulairefrancaise.ui.resources.agenda_add_calendar
import org.politisationpopulairefrancaise.ui.resources.agenda_back
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_confirm
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_done
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_event
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_failed
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_held
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_keep
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_refused
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_text
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_title
import org.politisationpopulairefrancaise.ui.resources.agenda_cancel_waiting
import org.politisationpopulairefrancaise.ui.resources.agenda_cancelled_note
import org.politisationpopulairefrancaise.ui.resources.agenda_description
import org.politisationpopulairefrancaise.ui.resources.agenda_export_cancel
import org.politisationpopulairefrancaise.ui.resources.agenda_export_confirm
import org.politisationpopulairefrancaise.ui.resources.agenda_export_done
import org.politisationpopulairefrancaise.ui.resources.agenda_export_failed
import org.politisationpopulairefrancaise.ui.resources.agenda_export_text
import org.politisationpopulairefrancaise.ui.resources.agenda_export_title
import org.politisationpopulairefrancaise.ui.resources.agenda_export_unsupported
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_open
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_stay
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_text
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_title
import org.politisationpopulairefrancaise.ui.resources.agenda_link
import org.politisationpopulairefrancaise.ui.resources.agenda_link_host
import org.politisationpopulairefrancaise.ui.resources.agenda_postponed_note
import org.politisationpopulairefrancaise.ui.resources.agenda_proposed_by
import org.politisationpopulairefrancaise.ui.resources.agenda_response_going
import org.politisationpopulairefrancaise.ui.resources.agenda_response_interested
import org.politisationpopulairefrancaise.ui.resources.agenda_response_private
import org.politisationpopulairefrancaise.ui.resources.agenda_response_title
import org.politisationpopulairefrancaise.ui.resources.agenda_state_reason
import org.politisationpopulairefrancaise.ui.resources.agenda_territory
import org.politisationpopulairefrancaise.ui.resources.agenda_timezone_note
import org.politisationpopulairefrancaise.ui.resources.agenda_updated
import org.politisationpopulairefrancaise.ui.resources.agenda_when
import org.politisationpopulairefrancaise.ui.resources.agenda_where
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The detail of a mobilization: what, when, where, who organises it, its description; the link
 * opened only after the "Vous quittez PPF" warning (the safe choice first); "Ajouter à mon
 * agenda", which builds the iCalendar file on the device after a short warning about synced
 * calendars; and the answers "J'y serai" / "Ça m'intéresse", recorded on the device only. The
 * organisers and the participating organisations follow D-044: an organisation shows as such only
 * when it said so with its own key; an account that is one may answer publicly ([OrganisationResponses]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MobilizationDetail(
    mobilization: Mobilization,
    agenda: AgendaLibrary,
    names: AgendaNames,
    compact: Boolean,
    exporter: CalendarExporter,
    onOpenLink: ((String) -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier,
    signer: EventSigner? = null,
    onMakePoster: ((Mobilization) -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = onOpenLink ?: { url -> runCatching { uriHandler.openUri(url) } }
    val scope = rememberCoroutineScope()
    val address = mobilization.address.toString()
    var leaving by remember(address) { mutableStateOf<String?>(null) }
    var exporting by remember(address) { mutableStateOf(false) }
    var exportResult by remember(address) { mutableStateOf<CalendarExportResult?>(null) }
    var cancelling by remember(address) { mutableStateOf(false) }
    var cancelResult by remember(address) { mutableStateOf<CancelOutcome?>(null) }
    val own = agenda.ownProposal(mobilization)
    val now = agenda.now
    val hosting = agenda.participations.hosting(mobilization, names::counts)
    val account = signer?.publicKey?.hex

    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(AgendaTags.DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agenda_back), onBack, Modifier.testTag(AgendaTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeMark(mobilization)
                Spacer(Modifier.width(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(typeLabel(mobilization.type), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.align(Alignment.CenterVertically))
                    if (mobilization.example) ExampleBadge(Modifier.align(Alignment.CenterVertically))
                    StatusPill(mobilization.status, Modifier.align(Alignment.CenterVertically))
                    if (own != null && own.state != OwnProposalState.PUBLISHED) OwnStatePill(own.state, Modifier.align(Alignment.CenterVertically))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(mobilization.displayTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            when (mobilization.status) {
                MobilizationStatus.ANNULE -> Notice(Lucide.Ban, stringResource(Res.string.agenda_cancelled_note), cancelled = true)
                MobilizationStatus.REPORTE -> Notice(Lucide.Clock, stringResource(Res.string.agenda_postponed_note), cancelled = false)
                MobilizationStatus.PREVU -> Unit
            }
            mobilization.summary?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            if (own?.state == OwnProposalState.NOT_PUBLISHED && own.reason != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.agenda_state_reason, own.reason), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            Inset(Modifier.fillMaxWidth()) {
                InfoRow(Lucide.Clock, stringResource(Res.string.agenda_when), whenText(mobilization, full = true), Modifier.testTag(AgendaTags.WHEN))
                if (mobilization.timeZone != null && mobilization.timeZone != AgirFormats.TIME_ZONE) {
                    Text(stringResource(Res.string.agenda_timezone_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.padding(start = 28.dp))
                }
                Spacer(Modifier.height(10.dp))
                InfoRow(
                    Lucide.MapPin,
                    stringResource(Res.string.agenda_where),
                    mobilization.location + "\n" + stringResource(Res.string.agenda_territory, mobilization.territory),
                    Modifier.testTag(AgendaTags.WHERE),
                )
                Spacer(Modifier.height(10.dp))
                HostsBlock(mobilization, hosting, names, Modifier.testTag(AgendaTags.ORGANIZERS))
                val declaration = mobilization.declaration
                if (mobilization.type == MobilizationType.MANIFESTATION && declaration != null) {
                    Spacer(Modifier.height(10.dp))
                    InfoRow(Lucide.Landmark, null, declarationLabel(declaration), Modifier.testTag(AgendaTags.DECLARATION))
                }
            }
            if (hosting.participants.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ParticipantsSection(hosting, names)
            }

            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!mobilization.isPast(now) && !mobilization.cancelled) {
                    PpfPrimaryButton(onClick = { exporting = true }, text = stringResource(Res.string.agenda_add_calendar), icon = Lucide.Calendar, modifier = Modifier.testTag(AgendaTags.EXPORT))
                }
                mobilization.links.firstOrNull()?.let { link ->
                    PpfSecondaryButton(onClick = { leaving = link }, text = stringResource(Res.string.agenda_link), icon = Lucide.Globe, modifier = Modifier.testTag(AgendaTags.LINK))
                }
                // AG-61: a poster of a published event to come; it publishes nothing.
                if (onMakePoster != null && !mobilization.isPast(now) && !mobilization.cancelled && (own == null || own.state == OwnProposalState.PUBLISHED)) {
                    PpfSecondaryButton(onClick = { onMakePoster(mobilization) }, text = stringResource(Res.string.affiche_make), icon = Lucide.Printer, modifier = Modifier.testTag(PosterTags.MAKE))
                }
            }
            mobilization.links.firstOrNull()?.let { link ->
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.agenda_link_host, hostOf(link)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            exportResult?.let { result ->
                val text = when (result) {
                    CalendarExportResult.DONE -> stringResource(Res.string.agenda_export_done)
                    CalendarExportResult.UNSUPPORTED -> stringResource(Res.string.agenda_export_unsupported)
                    CalendarExportResult.FAILED -> stringResource(Res.string.agenda_export_failed)
                    CalendarExportResult.CANCELLED -> null
                }
                if (text != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(AgendaTags.EXPORT_MESSAGE))
                }
            }

            // The author cancels without review (decision of the owner of 2026-11-07), after a confirmation.
            if (signer != null && agenda.canCancel(mobilization)) {
                Spacer(Modifier.height(8.dp))
                // A cancellation is a publication too: a compromised account no longer cancels (S-146).
                PpfTextButton(
                    onClick = { cancelling = true },
                    text = stringResource(Res.string.agenda_cancel_event),
                    enabled = LocalCanPublish.current,
                    color = palette.accentInk,
                    modifier = Modifier.testTag(AgendaTags.CANCEL_EVENT),
                )
                PublishBlockedNotice()
            }
            cancelResult?.let { result ->
                val text = stringResource(
                    when (result) {
                        CancelOutcome.CANCELLED -> Res.string.agenda_cancel_done
                        CancelOutcome.HELD -> Res.string.agenda_cancel_held
                        CancelOutcome.WAITING_FOR_SERVER -> Res.string.agenda_cancel_waiting
                        CancelOutcome.REFUSED -> Res.string.agenda_cancel_refused
                        CancelOutcome.FAILED -> Res.string.agenda_cancel_failed
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(AgendaTags.CANCEL_MESSAGE))
            }

            if (!mobilization.isPast(now) && !mobilization.cancelled) {
                Spacer(Modifier.height(16.dp))
                if (signer != null && account != null && names.counts(account)) {
                    // A verified organisation or PPF: "J'y serai" becomes the choice to show it publicly or not (D-044).
                    OrganisationResponses(mobilization, agenda, names, account, signer, hosting)
                } else {
                    Responses(agenda.responseTo(mobilization), onRespond = { agenda.respond(mobilization, it) })
                }
            }

            if (mobilization.description.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(Res.string.agenda_description), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(mobilization.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(AgendaTags.DESCRIPTION))
                }
            }

            MobilizationFollowUps(mobilization, agenda, openLink) // AG-62 « Et après ? », past mobilizations only (FollowUpsSection.kt)

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.agenda_proposed_by, names.author(mobilization.author, editorialTeamLabel())) + " · " +
                    stringResource(Res.string.agenda_updated, ParisTime.moment(mobilization.updatedAt).date.toFrench()),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    leaving?.let { link ->
        SafeChoiceDialog(
            title = stringResource(Res.string.agenda_leave_title),
            text = stringResource(Res.string.agenda_leave_text, hostOf(link)),
            safeLabel = stringResource(Res.string.agenda_leave_stay),
            riskyLabel = stringResource(Res.string.agenda_leave_open),
            onSafe = { leaving = null },
            onRisky = {
                leaving = null
                openLink(link)
            },
            tags = Triple(AgendaTags.LEAVE_DIALOG, AgendaTags.LEAVE_STAY, AgendaTags.LEAVE_OPEN),
            safeIsPrimary = true,
        )
    }
    if (cancelling && signer != null) {
        SafeChoiceDialog(
            title = stringResource(Res.string.agenda_cancel_title),
            text = stringResource(Res.string.agenda_cancel_text),
            safeLabel = stringResource(Res.string.agenda_cancel_keep),
            riskyLabel = stringResource(Res.string.agenda_cancel_confirm),
            onSafe = { cancelling = false },
            onRisky = {
                cancelling = false
                scope.launch { cancelResult = agenda.cancel(mobilization, signer) }
            },
            tags = Triple(AgendaTags.CANCEL_DIALOG, AgendaTags.CANCEL_KEEP, AgendaTags.CANCEL_CONFIRM),
            safeIsPrimary = true,
        )
    }
    if (exporting) {
        SafeChoiceDialog(
            title = stringResource(Res.string.agenda_export_title),
            text = stringResource(Res.string.agenda_export_text),
            safeLabel = stringResource(Res.string.agenda_export_cancel),
            riskyLabel = stringResource(Res.string.agenda_export_confirm),
            onSafe = { exporting = false },
            onRisky = {
                exporting = false
                scope.launch { exportResult = exporter.export(ICalendar.fileName(mobilization), ICalendar.export(mobilization), mobilization) }
            },
            tags = Triple(AgendaTags.EXPORT_DIALOG, AgendaTags.EXPORT_CANCEL, AgendaTags.EXPORT_CONFIRM),
            safeIsPrimary = false,
        )
    }
}

/** The host of a link, shown before it is opened ("exemple.invalid"). */
internal fun hostOf(link: String): String =
    link.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@').substringBefore(':').ifEmpty { link }

@Composable
private fun InfoRow(icon: ImageVector, label: String?, value: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    // Read as one sentence by screen readers: "Quand, samedi 14 novembre 2026, 14 h à 17 h".
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A cancelled or postponed event says it in words, with an icon, on a tinted band. */
@Composable
private fun Notice(icon: ImageVector, text: String, cancelled: Boolean) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (cancelled) palette.accentTint else palette.warningTint,
        contentColor = if (cancelled) palette.accentInk else palette.warning,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) { }.testTag(AgendaTags.NOTICE).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** AG-49: two private answers, on the device only, with what that means. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Responses(current: AgendaResponse?, onRespond: (AgendaResponse) -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.agenda_response_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(
                label = stringResource(Res.string.agenda_response_going),
                selected = current == AgendaResponse.GOING,
                onClick = { onRespond(AgendaResponse.GOING) },
                modifier = Modifier.testTag(AgendaTags.RESPONSE + AgendaResponse.GOING.value),
            )
            PpfSelectableChip(
                label = stringResource(Res.string.agenda_response_interested),
                selected = current == AgendaResponse.INTERESTED,
                onClick = { onRespond(AgendaResponse.INTERESTED) },
                leadingIcon = if (current == AgendaResponse.INTERESTED) null else Lucide.Bookmark,
                modifier = Modifier.testTag(AgendaTags.RESPONSE + AgendaResponse.INTERESTED.value),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.agenda_response_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(AgendaTags.RESPONSE_NOTE))
        }
    }
}

/**
 * A warning before something that loosens the protection of the user (principle 10 of the cahier
 * des charges): a title, a plain explanation, the safe choice first, then the other one.
 * [safeIsPrimary] makes the safe choice the highlighted button (leaving PPF); otherwise both keep
 * the order but the action the user asked for stays the main one (exporting a file).
 */
@Composable
internal fun SafeChoiceDialog(
    title: String,
    text: String,
    safeLabel: String,
    riskyLabel: String,
    onSafe: () -> Unit,
    onRisky: () -> Unit,
    tags: Triple<String, String, String>,
    safeIsPrimary: Boolean,
) {
    val palette = PpfTheme.palette
    Dialog(onDismissRequest = onSafe, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag(tags.first),
                shape = MaterialTheme.shapes.medium,
                color = palette.surface2,
                contentColor = palette.ink,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                    Spacer(Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (safeIsPrimary) {
                            PpfPrimaryButton(onClick = onSafe, text = safeLabel, modifier = Modifier.fillMaxWidth().testTag(tags.second))
                            PpfTextButton(onClick = onRisky, text = riskyLabel, color = palette.inkMuted, modifier = Modifier.fillMaxWidth().testTag(tags.third))
                        } else {
                            PpfSecondaryButton(onClick = onSafe, text = safeLabel, modifier = Modifier.fillMaxWidth().testTag(tags.second))
                            PpfPrimaryButton(onClick = onRisky, text = riskyLabel, modifier = Modifier.fillMaxWidth().testTag(tags.third))
                        }
                    }
                }
            }
        }
    }
}
