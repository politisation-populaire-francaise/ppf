package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.Hosting
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationStatus
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_announced
import org.politisationpopulairefrancaise.ui.resources.agenda_announced_inline
import org.politisationpopulairefrancaise.ui.resources.agenda_announced_note
import org.politisationpopulairefrancaise.ui.resources.agenda_badge_editorial
import org.politisationpopulairefrancaise.ui.resources.agenda_badge_verified
import org.politisationpopulairefrancaise.ui.resources.agenda_hosts_label
import org.politisationpopulairefrancaise.ui.resources.agenda_org_co_organise
import org.politisationpopulairefrancaise.ui.resources.agenda_org_done_failed
import org.politisationpopulairefrancaise.ui.resources.agenda_org_done_refused
import org.politisationpopulairefrancaise.ui.resources.agenda_org_done_shown
import org.politisationpopulairefrancaise.ui.resources.agenda_org_done_waiting
import org.politisationpopulairefrancaise.ui.resources.agenda_org_done_withdrawn
import org.politisationpopulairefrancaise.ui.resources.agenda_org_note
import org.politisationpopulairefrancaise.ui.resources.agenda_org_private
import org.politisationpopulairefrancaise.ui.resources.agenda_org_show
import org.politisationpopulairefrancaise.ui.resources.agenda_org_state_co
import org.politisationpopulairefrancaise.ui.resources.agenda_org_state_private
import org.politisationpopulairefrancaise.ui.resources.agenda_org_state_public
import org.politisationpopulairefrancaise.ui.resources.agenda_org_title
import org.politisationpopulairefrancaise.ui.resources.agenda_org_waiting
import org.politisationpopulairefrancaise.ui.resources.agenda_org_withdraw
import org.politisationpopulairefrancaise.ui.resources.agenda_org_withdraw_private
import org.politisationpopulairefrancaise.ui.resources.agenda_organized_by
import org.politisationpopulairefrancaise.ui.resources.agenda_participants_count
import org.politisationpopulairefrancaise.ui.resources.agenda_participants_note
import org.politisationpopulairefrancaise.ui.resources.agenda_participants_title
import org.politisationpopulairefrancaise.ui.resources.agenda_response_interested
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the organisers, the participating organisations and the public answer of an organisation. */
object ParticipationTags {
    const val HOST = "agenda-host-" // followed by the key
    const val ANNOUNCED = "agenda-host-announced-" // followed by the key
    const val PARTICIPANTS = "agenda-participants"
    const val PARTICIPANT = "agenda-participant-" // followed by the key
    const val ORG_RESPONSE = "agenda-org-response"
    const val ORG_STATE = "agenda-org-state"
    const val ORG_SHOW = "agenda-org-show"
    const val ORG_PRIVATE = "agenda-org-private"
    const val ORG_CO_ORGANISE = "agenda-org-co-organise"
    const val ORG_WITHDRAW = "agenda-org-withdraw"
    const val ORG_WITHDRAW_PRIVATE = "agenda-org-withdraw-private"
    const val ORG_MESSAGE = "agenda-org-message"
    const val CARD_PARTICIPANTS = "agenda-card-participants"
}

/** The name of a key that counts: the directory's, PPF's, or the short key, starting with a capital in a list. */
@Composable
internal fun partyName(names: AgendaNames, key: String): String = AgendaFormat.capitalized(names.author(key, editorialTeamLabel()))

/** "Vérifiée" for a verified organisation, "PPF" for an editorial key, nothing otherwise. */
@Composable
internal fun PartyBadge(names: AgendaNames, key: String, modifier: Modifier = Modifier) {
    when {
        names.isVerified(key) -> PpfBadge(stringResource(Res.string.agenda_badge_verified), BadgeStyle.SUCCESS, modifier)
        names.isEditorialKey(key) -> PpfBadge(stringResource(Res.string.agenda_badge_editorial), BadgeStyle.BRAND, modifier)
    }
}

/**
 * The line "Organisé par" of a card: the author when it is a verified organisation, the named
 * organisations that confirmed, then those only announced, said so (D-044). Null when nobody is to name.
 */
@Composable
internal fun hostsLine(mobilization: Mobilization, hosting: Hosting, names: AgendaNames): String? {
    val parts = buildList {
        if (names.isVerified(mobilization.author)) add(names.organisation(mobilization.author))
        hosting.confirmed.forEach { add(names.organisation(it)) }
    } + hosting.announced.map { stringResource(Res.string.agenda_announced_inline, names.organisation(it)) }
    return if (parts.isEmpty()) null else stringResource(Res.string.agenda_organized_by, parts.joinToString(", "))
}

/** The number of participating organisations on a card, when there are some. */
@Composable
internal fun CardParticipants(hosting: Hosting) {
    val count = hosting.participants.size
    if (count == 0) return
    MetaLine(Lucide.Building2, pluralStringResource(Res.plurals.agenda_participants_count, count, count), Modifier.testTag(ParticipationTags.CARD_PARTICIPANTS))
}

/**
 * "Organisé par" in the detail: the author first (with its badge when it is a verified organisation
 * or PPF), the named organisations that confirmed with their own key ("Vérifiée"), then those the
 * event names without their confirmation, "annoncée, non confirmée".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HostsBlock(mobilization: Mobilization, hosting: Hosting, names: AgendaNames, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Icon(Lucide.Users, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.agenda_hosts_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
            (listOf(mobilization.author) + hosting.confirmed).distinct().forEach { key ->
                PartyRow(names, key, Modifier.testTag(ParticipationTags.HOST + key))
            }
            hosting.announced.forEach { key ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.testTag(ParticipationTags.ANNOUNCED + key),
                ) {
                    Text(AgendaFormat.capitalized(names.organisation(key)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
                    PpfBadge(stringResource(Res.string.agenda_announced), BadgeStyle.NEUTRAL, Modifier.align(Alignment.CenterVertically))
                }
            }
            if (hosting.announced.isNotEmpty()) {
                Text(stringResource(Res.string.agenda_announced_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyRow(names: AgendaNames, key: String, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(partyName(names, key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
        PartyBadge(names, key, Modifier.align(Alignment.CenterVertically))
    }
}

/**
 * "Organisations participantes (N)": the verified organisations and PPF that published, with their
 * own key, that they take part (D-044: "la transparence passe avant tout"). Nothing when none did.
 * The answer of any other key is never shown here.
 */
@Composable
internal fun ParticipantsSection(hosting: Hosting, names: AgendaNames, modifier: Modifier = Modifier) {
    val count = hosting.participants.size
    if (count == 0) return
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(ParticipationTags.PARTICIPANTS), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            pluralStringResource(Res.plurals.agenda_participants_title, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = palette.brand,
            modifier = Modifier.semantics { heading() },
        )
        hosting.participants.forEach { key ->
            Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Building2, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                PartyRow(names, key, Modifier.testTag(ParticipationTags.PARTICIPANT + key))
            }
        }
        Text(stringResource(Res.string.agenda_participants_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/**
 * "J'y serai" for an account whose answer counts (a verified organisation or PPF, D-044): a clear
 * choice between showing the participation of the organisation publicly, first and highlighted (the
 * owner's choice: an organisation is not a natural person), and taking part without showing it, kept
 * on the device like a citizen's answer. An organisation the event names may also confirm that it
 * co-organises it. Withdrawing a public answer publishes a newer version, `declined`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganisationResponses(
    mobilization: Mobilization,
    agenda: AgendaLibrary,
    names: AgendaNames,
    account: String,
    signer: EventSigner,
    hosting: Hosting,
) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    val address = mobilization.address.toString()
    var outcome by remember(address) { mutableStateOf<ParticipationOutcome?>(null) }
    var sending by remember(address) { mutableStateOf(false) }
    val name = names.author(account, editorialTeamLabel())
    val public = agenda.publicAnswerOf(mobilization)?.takeIf { it.status == ParticipationStatus.ACCEPTED }
    val coOrganising = public != null && account in hosting.confirmed
    val local = agenda.responseTo(mobilization)
    val named = account in mobilization.organizers && account != mobilization.author
    val send: (ParticipationStatus, ParticipationRole) -> Unit = { status, role ->
        sending = true
        scope.launch {
            outcome = agenda.answerPublicly(mobilization, signer, status, role)
            sending = false
        }
    }
    PpfCard(Modifier.fillMaxWidth().testTag(ParticipationTags.ORG_RESPONSE)) {
        Text(stringResource(Res.string.agenda_org_title, name), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val state = when {
            coOrganising -> Lucide.CircleCheck to stringResource(Res.string.agenda_org_state_co, name)
            public != null -> Lucide.CircleCheck to stringResource(Res.string.agenda_org_state_public, name)
            local == AgendaResponse.GOING -> Lucide.Lock to stringResource(Res.string.agenda_org_state_private, name)
            else -> null
        }
        state?.let { (icon, text) ->
            Spacer(Modifier.height(8.dp))
            StateLine(icon, text, Modifier.testTag(ParticipationTags.ORG_STATE))
        }
        if (agenda.publicAnswerWaiting(mobilization)) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.agenda_org_waiting), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (public == null) {
                // The owner's choice: showing the participation of the organisation comes first.
                PpfPrimaryButton(
                    onClick = { send(ParticipationStatus.ACCEPTED, ParticipationRole.PARTICIPANT) },
                    text = stringResource(Res.string.agenda_org_show, name),
                    enabled = !sending,
                    icon = Lucide.Eye,
                    modifier = Modifier.fillMaxWidth().testTag(ParticipationTags.ORG_SHOW),
                )
                if (local != AgendaResponse.GOING) {
                    PpfSecondaryButton(
                        onClick = { agenda.respond(mobilization, AgendaResponse.GOING) },
                        text = stringResource(Res.string.agenda_org_private),
                        icon = Lucide.EyeOff,
                        modifier = Modifier.fillMaxWidth().testTag(ParticipationTags.ORG_PRIVATE),
                    )
                }
            }
            if (named && !coOrganising) {
                PpfSecondaryButton(
                    onClick = { send(ParticipationStatus.ACCEPTED, ParticipationRole.CO_ORGANISER) },
                    text = stringResource(Res.string.agenda_org_co_organise, name),
                    enabled = !sending,
                    icon = Lucide.BadgeCheck,
                    modifier = Modifier.fillMaxWidth().testTag(ParticipationTags.ORG_CO_ORGANISE),
                )
            }
            if (public != null) {
                PpfTextButton(
                    onClick = { send(ParticipationStatus.DECLINED, ParticipationRole.PARTICIPANT) },
                    text = stringResource(Res.string.agenda_org_withdraw),
                    enabled = !sending,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(ParticipationTags.ORG_WITHDRAW),
                )
            } else if (local == AgendaResponse.GOING) {
                PpfTextButton(
                    onClick = { agenda.respond(mobilization, AgendaResponse.GOING) },
                    text = stringResource(Res.string.agenda_org_withdraw_private),
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(ParticipationTags.ORG_WITHDRAW_PRIVATE),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(
                label = stringResource(Res.string.agenda_response_interested),
                selected = local == AgendaResponse.INTERESTED,
                onClick = { agenda.respond(mobilization, AgendaResponse.INTERESTED) },
                leadingIcon = if (local == AgendaResponse.INTERESTED) null else Lucide.Bookmark,
                modifier = Modifier.testTag(AgendaTags.RESPONSE + AgendaResponse.INTERESTED.value),
            )
        }
        outcome?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (result) {
                        ParticipationOutcome.SHOWN -> Res.string.agenda_org_done_shown
                        ParticipationOutcome.WITHDRAWN -> Res.string.agenda_org_done_withdrawn
                        ParticipationOutcome.WAITING_FOR_SERVER -> Res.string.agenda_org_done_waiting
                        ParticipationOutcome.REFUSED -> Res.string.agenda_org_done_refused
                        ParticipationOutcome.FAILED -> Res.string.agenda_org_done_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(ParticipationTags.ORG_MESSAGE),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.Info, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.agenda_org_note, name), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(AgendaTags.RESPONSE_NOTE))
        }
    }
}

@Composable
private fun StateLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = palette.success, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
