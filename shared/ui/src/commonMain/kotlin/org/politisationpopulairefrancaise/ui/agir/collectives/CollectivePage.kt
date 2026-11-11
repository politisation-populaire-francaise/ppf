package org.politisationpopulairefrancaise.ui.agir.collectives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.collectives.Collective
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.whenText
import org.politisationpopulairefrancaise.ui.agir.calls.CallsLibrary
import org.politisationpopulairefrancaise.ui.agir.calls.typeLabelResource as callTypeLabel
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.collectifs_back
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_calls_empty
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_calls_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_charter_hide
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_charter_show
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_charter_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_events_empty
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_events_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_join
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_joined
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_language
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_launch_call
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_leave
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_membership_note
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_open_room
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_propose_event
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_publish_text
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_publish_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_admins
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_founder
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_moderators
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_none
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_note
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_roles_title
import org.politisationpopulairefrancaise.ui.resources.collectifs_page_you
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The page of a collectif (AG-48): presentation, public roles, joining on the device, its salon,
 * its coming events and its open calls, and for its founder, administrators and moderators the two
 * composers of "Agir" in its name. The detailed layout waits for the owner's mock-ups of the
 * community interface: a simple page meanwhile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollectivePage(
    library: CollectivesLibrary,
    collective: Collective,
    session: AccountSession,
    agenda: AgendaLibrary,
    calls: CallsLibrary,
    compact: Boolean,
    onOpenMobilization: (Mobilization) -> Unit,
    onOpenCall: (Call) -> Unit,
) {
    val palette = PpfTheme.palette
    // The events and calls of the collectif are those of the agenda and of the wall: started if they were not.
    LaunchedEffect(agenda) { agenda.start() }
    LaunchedEffect(calls) { calls.start() }
    val me = (session.state as? AccountUiState.Ready)?.publicKey?.hex
    val canPublish = LocalCanPublish.current
    val joined = library.isMember(collective)
    val organiser = library.canPublishFor(collective, me)
    val events = library.eventsOf(collective, agenda.mobilizations, agenda.now)
    val openCalls = library.callsOf(collective, calls.calls)
    var charterShown by rememberSaveable(collective.id) { mutableStateOf(false) }
    val link = CollectiveLink(collective.id, collective.displayName)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(CollectivesTags.PAGE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.collectifs_back), library::back, Modifier.testTag(CollectivesTags.PAGE_BACK), tint = palette.ink)
                Spacer(Modifier.width(8.dp))
                Text(collective.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            }
            if (collective.example) {
                Spacer(Modifier.height(4.dp))
                ExampleBadge()
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PpfBadge(stringResource(typeLabel(collective.type)), BadgeStyle.BRAND)
                PpfBadge(stringResource(scopeLabel(collective.scope)))
                collective.territory?.let { PpfBadge(it) }
                PpfBadge(stringResource(Res.string.collectifs_page_language, languageLabel(collective.language)))
            }
            if (collective.themes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(collective.themes.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.bodyMedium, color = palette.brand)
            }
            collective.about?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            collective.internalCharter?.let { charter ->
                Spacer(Modifier.height(8.dp))
                PpfTextButton(
                    onClick = { charterShown = !charterShown },
                    text = stringResource(if (charterShown) Res.string.collectifs_page_charter_hide else Res.string.collectifs_page_charter_show),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.CHARTER_TOGGLE),
                )
                if (charterShown) {
                    Inset(Modifier.fillMaxWidth().testTag(CollectivesTags.CHARTER_TEXT)) {
                        Text(stringResource(Res.string.collectifs_page_charter_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                        Text(charter, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Joining stays on the device: nothing is published, the server knows no member.
            Spacer(Modifier.height(16.dp))
            if (joined) {
                Text(stringResource(Res.string.collectifs_page_joined), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (joined) {
                    PpfSecondaryButton(
                        onClick = { library.leave(collective) },
                        text = stringResource(Res.string.collectifs_page_leave),
                        modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.LEAVE),
                    )
                } else {
                    PpfPrimaryButton(
                        onClick = { library.join(collective) },
                        text = stringResource(Res.string.collectifs_page_join),
                        icon = Lucide.Users,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.JOIN),
                    )
                }
                PpfSecondaryButton(
                    onClick = { library.openRoom(collective) },
                    text = stringResource(Res.string.collectifs_page_open_room),
                    icon = Lucide.MessagesSquare,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.OPEN_ROOM),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.collectifs_page_membership_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)

            if (organiser) {
                Section(Res.string.collectifs_page_publish_title)
                Text(stringResource(Res.string.collectifs_page_publish_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(8.dp))
                // A compromised account no longer publishes (S-146): the buttons are greyed out and say why.
                PublishBlockedNotice()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PpfSecondaryButton(
                        onClick = {
                            val composer = agenda.composer
                            if (composer.draft.territory.isBlank()) composer.draft = composer.draft.copy(territory = collective.territory.orEmpty())
                            composer.collective = link
                            composer.open = true
                            library.proposeEvent(collective)
                        },
                        text = stringResource(Res.string.collectifs_page_propose_event),
                        icon = Lucide.Calendar,
                        enabled = canPublish,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.PROPOSE_EVENT),
                    )
                    PpfSecondaryButton(
                        onClick = {
                            val composer = calls.composer
                            if (composer.draft.territory.isBlank()) composer.draft = composer.draft.copy(territory = collective.territory.orEmpty())
                            composer.collective = link
                            composer.open = true
                            library.launchCall(collective)
                        },
                        text = stringResource(Res.string.collectifs_page_launch_call),
                        icon = Lucide.Megaphone,
                        enabled = canPublish,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(CollectivesTags.LAUNCH_CALL),
                    )
                }
            }

            Section(Res.string.collectifs_page_roles_title)
            Roles(library, collective, me)

            Section(Res.string.collectifs_page_events_title)
            Column(Modifier.testTag(CollectivesTags.EVENTS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (events.isEmpty()) {
                    Text(stringResource(Res.string.collectifs_page_events_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                events.forEach { mobilization ->
                    PpfCard(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(CollectivesTags.EVENT + mobilization.identifier), onClick = { onOpenMobilization(mobilization) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mobilization.displayTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (mobilization.example) ExampleBadge()
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(whenText(mobilization, full = false), style = MaterialTheme.typography.bodyMedium)
                        Text(mobilization.location, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                }
            }

            Section(Res.string.collectifs_page_calls_title)
            Column(Modifier.testTag(CollectivesTags.CALLS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (openCalls.isEmpty()) {
                    Text(stringResource(Res.string.collectifs_page_calls_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                openCalls.forEach { call ->
                    PpfCard(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(CollectivesTags.CALL + call.slug), onClick = { onOpenCall(call) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(call.displayTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (call.isExample) ExampleBadge()
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(callTypeLabel(call.type)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The public roles of the collectif's room (D-131): its founder, its administrators, its moderators; never its members. */
@Composable
private fun Roles(library: CollectivesLibrary, collective: Collective, me: String?) {
    val palette = PpfTheme.palette
    val moderation = library.rolesOf(collective)
    val admins = (moderation?.admins ?: listOf(collective.founder)).filter { it != collective.founder }
    val moderators = moderation?.moderators.orEmpty()
    val none = stringResource(Res.string.collectifs_page_roles_none)
    val myName = me?.let { stringResource(Res.string.collectifs_page_you, shortNpub(it)) }
    fun name(key: String): String = if (key == me && myName != null) myName else shortNpub(key)
    PpfCard(Modifier.fillMaxWidth().testTag(CollectivesTags.ROLES)) {
        Text(stringResource(Res.string.collectifs_page_roles_founder, name(collective.founder)), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.collectifs_page_roles_admins, admins.joinToString { name(it) }.ifEmpty { none }), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.collectifs_page_roles_moderators, moderators.joinToString { name(it) }.ifEmpty { none }), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.collectifs_page_roles_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

@Composable
private fun ColumnScope.Section(title: StringResource) {
    Spacer(Modifier.height(24.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}
