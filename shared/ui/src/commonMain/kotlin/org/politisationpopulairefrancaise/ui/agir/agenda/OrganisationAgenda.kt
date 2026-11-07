package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_organisation_none
import org.politisationpopulairefrancaise.ui.resources.agenda_organisation_open
import org.politisationpopulairefrancaise.ui.resources.agenda_role_announced
import org.politisationpopulairefrancaise.ui.resources.agenda_role_co_organises
import org.politisationpopulairefrancaise.ui.resources.agenda_role_organises
import org.politisationpopulairefrancaise.ui.resources.agenda_role_participates
import org.politisationpopulairefrancaise.ui.resources.agenda_upcoming_none
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the short lists of mobilizations (page of an organisation, "En direct"). */
object OrganisationAgendaTags {
    const val LIST: String = "organisation-agenda"
    const val NONE: String = "organisation-agenda-none"
    const val ROW: String = "organisation-agenda-row"
}

/**
 * The upcoming mobilizations of an organisation, for its page in the directory (AG-17, AG-19): those
 * it organises (its own), those it co-organises or takes part in, as it said with its own key (D-044),
 * and those that name it without its confirmation, said so ("Annoncée, non confirmée"). Each row
 * says which. At most [limit] of them, the soonest first; a tap opens the mobilization in the agenda
 * through [onOpen]. The key comes from a directory entry signed by the editorial team.
 */
@Composable
fun OrganisationAgenda(
    agenda: AgendaLibrary,
    organiser: String,
    modifier: Modifier = Modifier,
    limit: Int = 5,
    onOpen: (Mobilization) -> Unit,
) {
    val participations = agenda.participations
    val accepted = remember(participations, organiser) { participations.acceptedBy(organiser) }
    UpcomingList(
        agenda, AgendaQuery(), stringResource(Res.string.agenda_organisation_none), limit, modifier, onOpen,
        include = { it.isHostedBy(organiser) || it.address.toString() in accepted },
        role = { m ->
            val answer = participations.of(organiser, m.address.toString())?.takeIf { it.accepted }
            stringResource(
                when {
                    m.author == organiser -> Res.string.agenda_role_organises
                    organiser in m.organizers && answer?.role == ParticipationRole.CO_ORGANISER -> Res.string.agenda_role_co_organises
                    answer != null -> Res.string.agenda_role_participates
                    else -> Res.string.agenda_role_announced
                },
            )
        },
    )
}

/**
 * The next mobilizations of the agenda, the soonest first, for "En direct" (MA-02): at most [limit]
 * of them; a tap opens one through [onOpen] (the shell shows it in "Carte").
 */
@Composable
fun UpcomingMobilizations(
    agenda: AgendaLibrary,
    modifier: Modifier = Modifier,
    limit: Int = 3,
    onOpen: (Mobilization) -> Unit,
) {
    UpcomingList(agenda, AgendaQuery(), stringResource(Res.string.agenda_upcoming_none), limit, modifier, onOpen)
}

@Composable
private fun UpcomingList(
    agenda: AgendaLibrary,
    query: AgendaQuery,
    emptyText: String,
    limit: Int,
    modifier: Modifier,
    onOpen: (Mobilization) -> Unit,
    include: (Mobilization) -> Boolean = { true },
    role: (@Composable (Mobilization) -> String)? = null,
) {
    LaunchedEffect(agenda) { agenda.start() }
    // Cancelled events stay in the agenda, but they are not "coming".
    val upcoming = query.apply(agenda.known, agenda.now).filter { !it.cancelled && include(it) }.take(limit)
    val palette = PpfTheme.palette
    Column(modifier.testTag(OrganisationAgendaTags.LIST), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (upcoming.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(OrganisationAgendaTags.NONE),
            )
        }
        upcoming.forEach { mobilization ->
            val description = stringResource(Res.string.agenda_organisation_open, mobilization.title)
            val label = role?.invoke(mobilization)
            val moment = dayHeader(ParisTime.moment(mobilization.start).epochDay, agenda.now) + ", " + whenText(mobilization, full = false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onOpen(mobilization) }
                    .semantics { contentDescription = listOfNotNull(description, label, moment).joinToString(". ") }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag(OrganisationAgendaTags.ROW),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(typeIcon(mobilization.type), contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mobilization.title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                    label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = palette.brand) }
                    Text(moment, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    Text(mobilization.location, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
                StatusPill(mobilization.status)
            }
        }
    }
}
