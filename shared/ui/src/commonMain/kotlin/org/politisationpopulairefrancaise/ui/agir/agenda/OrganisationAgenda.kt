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
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_organisation_none
import org.politisationpopulairefrancaise.ui.resources.agenda_organisation_open
import org.politisationpopulairefrancaise.ui.resources.agenda_upcoming_none
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the short lists of mobilizations (page of an organisation, "En direct"). */
object OrganisationAgendaTags {
    const val LIST: String = "organisation-agenda"
    const val NONE: String = "organisation-agenda-none"
    const val ROW: String = "organisation-agenda-row"
}

/**
 * The upcoming mobilizations an organisation hosts (author or named organiser, AG-19), for its page
 * in the directory (AG-17). At most [limit] of them, the soonest first; a tap opens the mobilization
 * in the agenda through [onOpen].
 */
@Composable
fun OrganisationAgenda(
    agenda: AgendaLibrary,
    organiser: String,
    modifier: Modifier = Modifier,
    limit: Int = 5,
    onOpen: (Mobilization) -> Unit,
) {
    UpcomingList(agenda, AgendaQuery(organizers = setOf(organiser)), stringResource(Res.string.agenda_organisation_none), limit, modifier, onOpen)
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
) {
    LaunchedEffect(agenda) { agenda.start() }
    // Cancelled events stay in the agenda, but they are not "coming".
    val upcoming = query.apply(agenda.known, agenda.now).filterNot { it.cancelled }.take(limit)
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
            val moment = dayHeader(ParisTime.moment(mobilization.start).epochDay, agenda.now) + ", " + whenText(mobilization, full = false)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onOpen(mobilization) }
                    .semantics { contentDescription = "$description. $moment" }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag(OrganisationAgendaTags.ROW),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(typeIcon(mobilization.type), contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mobilization.title, style = MaterialTheme.typography.titleSmall, color = palette.ink)
                    Text(moment, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    Text(mobilization.location, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
                StatusPill(mobilization.status)
            }
        }
    }
}
