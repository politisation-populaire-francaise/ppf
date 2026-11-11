package org.politisationpopulairefrancaise.ui.agir.followups

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpKind
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpLinkKind
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.SafeChoiceDialog
import org.politisationpopulairefrancaise.ui.agir.agenda.hostOf
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_open
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_stay
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_text
import org.politisationpopulairefrancaise.ui.resources.agenda_leave_title
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.suites_date
import org.politisationpopulairefrancaise.ui.resources.suites_empty
import org.politisationpopulairefrancaise.ui.resources.suites_intro
import org.politisationpopulairefrancaise.ui.resources.suites_kind_annonce
import org.politisationpopulairefrancaise.ui.resources.suites_kind_autre
import org.politisationpopulairefrancaise.ui.resources.suites_kind_decision_justice
import org.politisationpopulairefrancaise.ui.resources.suites_kind_recul
import org.politisationpopulairefrancaise.ui.resources.suites_kind_reponse_elu
import org.politisationpopulairefrancaise.ui.resources.suites_kind_texte_depose
import org.politisationpopulairefrancaise.ui.resources.suites_link_call
import org.politisationpopulairefrancaise.ui.resources.suites_link_chronology
import org.politisationpopulairefrancaise.ui.resources.suites_link_demand
import org.politisationpopulairefrancaise.ui.resources.suites_link_person
import org.politisationpopulairefrancaise.ui.resources.suites_loading
import org.politisationpopulairefrancaise.ui.resources.suites_reply_note
import org.politisationpopulairefrancaise.ui.resources.suites_reply_title
import org.politisationpopulairefrancaise.ui.resources.suites_source
import org.politisationpopulairefrancaise.ui.resources.suites_source_dated
import org.politisationpopulairefrancaise.ui.resources.suites_sources
import org.politisationpopulairefrancaise.ui.resources.suites_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.badgeStyle
import org.politisationpopulairefrancaise.ui.voir.label

/** The test tags of « Et après ? ». The ids are stable (tests). */
object FollowUpTags {
    const val SECTION = "suites-section"
    const val LOADING = "suites-loading"
    const val EMPTY = "suites-empty"
    const val ITEM = "suite-item-" // followed by the identifier of the follow-up
    const val KIND = "suite-kind-" // followed by the identifier
    const val STATUS = "suite-status-" // followed by the identifier
    const val DATE = "suite-date-" // followed by the identifier
    const val LINK = "suite-link-" // followed by the identifier, "-" and the position of the link
    const val SOURCE = "suite-source-" // followed by the identifier, "-" and the position of the source
    const val REPLY = "suite-reply-" // followed by the identifier
    const val REPLY_NOTE = "suite-reply-note-" // followed by the identifier
    const val LEAVE_DIALOG = "suites-leave-dialog"
    const val LEAVE_STAY = "suites-leave-stay"
    const val LEAVE_OPEN = "suites-leave-open"
}

/**
 * AG-62 « Et après ? » on the detail of a mobilization: nothing while it is still to come; once it is over, its
 * sourced follow-ups by date, or a short sentence when none is known. Read-only (step 1). Called with one line
 * from MobilizationDetail.kt.
 */
@Composable
internal fun MobilizationFollowUps(mobilization: Mobilization, agenda: AgendaLibrary, onOpenLink: (String) -> Unit) {
    if (!mobilization.isPast(agenda.now)) return
    FollowUpsSection(mobilization.address, agenda.followUps, onOpenLink)
}

/**
 * The section itself, for one mobilization: the title, what it is about, then each follow-up (kind, date, status in
 * words, title, text, origin, links known on the device, rights of reply, sources). A source opens only after the
 * « Vous quittez PPF » warning of the agenda, the safe choice first.
 */
@Composable
internal fun FollowUpsSection(mobilization: ContentAddress, library: FollowUpLibrary, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val entries by produceState<List<FollowUpEntry>?>(null, library, mobilization) {
        value = try {
            library.entriesOf(mobilization)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken local copy never breaks the detail: the section then says that nothing is known.
            emptyList()
        }
    }
    var leaving by remember(mobilization) { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth().testTag(FollowUpTags.SECTION)) {
        Spacer(Modifier.height(20.dp))
        Text(stringResource(Res.string.suites_title), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.suites_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        val shown = entries
        when {
            shown == null -> Text(
                stringResource(Res.string.suites_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSubtle,
                modifier = Modifier.testTag(FollowUpTags.LOADING),
            )
            shown.isEmpty() -> Text(
                stringResource(Res.string.suites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(FollowUpTags.EMPTY),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { entry -> FollowUpCard(entry, onSource = { leaving = it }) }
            }
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
                onOpenLink(link)
            },
            tags = Triple(FollowUpTags.LEAVE_DIALOG, FollowUpTags.LEAVE_STAY, FollowUpTags.LEAVE_OPEN),
            safeIsPrimary = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowUpCard(entry: FollowUpEntry, onSource: (String) -> Unit) {
    val palette = PpfTheme.palette
    val followUp = entry.followUp
    val id = followUp.address.identifier
    PpfCard(Modifier.fillMaxWidth().testTag(FollowUpTags.ITEM + id)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(followUp.kind.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(followUp.kind.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.inkMuted,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag(FollowUpTags.KIND + id),
                )
                if (followUp.example) ExampleBadge(Modifier.align(Alignment.CenterVertically))
                // The status in words (established, alleged, under investigation), as in « Voir »: never a colour alone.
                Box(Modifier.align(Alignment.CenterVertically).semantics(mergeDescendants = true) { }.testTag(FollowUpTags.STATUS + id)) {
                    PpfBadge(stringResource(followUp.status.label), followUp.status.badgeStyle)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.suites_date, followUp.date.toFrench()),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
            modifier = Modifier.testTag(FollowUpTags.DATE + id),
        )
        Spacer(Modifier.height(4.dp))
        Text(followUp.displayTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        followUp.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (followUp.text.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(followUp.text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (followUp.origin == ContentOrigin.AI_ASSISTED) Res.string.content_origin_ai else Res.string.content_origin_human),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )

        if (entry.links.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.links.forEachIndexed { index, known ->
                    // Read as one sentence: « Revendication : … ».
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.testTag(FollowUpTags.LINK + id + "-" + index), verticalAlignment = Alignment.Top) {
                        Icon(known.link.kind.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(known.link.kind.label, known.title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // The right of reply (M17, VO-21): published under the follow-up, or how to ask for it when a person is named.
        entry.replies.forEach { reply ->
            Spacer(Modifier.height(10.dp))
            Inset(Modifier.fillMaxWidth().testTag(FollowUpTags.REPLY + id)) {
                Text(stringResource(Res.string.suites_reply_title, reply.respondent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(reply.text.trim(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (followUp.persons.isNotEmpty() && entry.replies.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.Info, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.suites_reply_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.weight(1f).testTag(FollowUpTags.REPLY_NOTE + id),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(Res.string.suites_sources), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.semantics { heading() })
        followUp.sources.forEachIndexed { index, source ->
            val url = source.url ?: return@forEachIndexed
            PpfTextButton(
                onClick = { onSource(url) },
                text = sourceText(source) + " · " + hostOf(url),
                modifier = Modifier.testTag(FollowUpTags.SOURCE + id + "-" + index),
            )
        }
    }
}

/** « Lettre publique (exemple) (Déclaration publique, 6 novembre 2026) ». */
@Composable
private fun sourceText(source: Source): String {
    val kind = stringResource(source.kind.label)
    val date = source.date
    return if (date == null) {
        stringResource(Res.string.suites_source, source.name, kind)
    } else {
        stringResource(Res.string.suites_source_dated, source.name, kind, date.toFrench())
    }
}

private val FollowUpKind.label: StringResource get() = when (this) {
    FollowUpKind.ELECTED_REPLY -> Res.string.suites_kind_reponse_elu
    FollowUpKind.ANNOUNCEMENT -> Res.string.suites_kind_annonce
    FollowUpKind.RETREAT -> Res.string.suites_kind_recul
    FollowUpKind.BILL_FILED -> Res.string.suites_kind_texte_depose
    FollowUpKind.COURT_DECISION -> Res.string.suites_kind_decision_justice
    FollowUpKind.OTHER -> Res.string.suites_kind_autre
}

/** Decorative: the kind is always said in words next to it. */
private val FollowUpKind.icon: ImageVector get() = when (this) {
    FollowUpKind.ELECTED_REPLY -> Lucide.MessageSquare
    FollowUpKind.ANNOUNCEMENT -> Lucide.Megaphone
    FollowUpKind.RETREAT -> Lucide.RefreshCw
    FollowUpKind.BILL_FILED -> Lucide.ScrollText
    FollowUpKind.COURT_DECISION -> Lucide.Landmark
    FollowUpKind.OTHER -> Lucide.Info
}

private val FollowUpLinkKind.label: StringResource get() = when (this) {
    FollowUpLinkKind.PERSON -> Res.string.suites_link_person
    FollowUpLinkKind.DEMAND -> Res.string.suites_link_demand
    FollowUpLinkKind.CALL -> Res.string.suites_link_call
    FollowUpLinkKind.CHRONOLOGY -> Res.string.suites_link_chronology
}

private val FollowUpLinkKind.icon: ImageVector get() = when (this) {
    FollowUpLinkKind.PERSON -> Lucide.User
    FollowUpLinkKind.DEMAND -> Lucide.Flag
    FollowUpLinkKind.CALL -> Lucide.Bell
    FollowUpLinkKind.CHRONOLOGY -> Lucide.Clock
}
