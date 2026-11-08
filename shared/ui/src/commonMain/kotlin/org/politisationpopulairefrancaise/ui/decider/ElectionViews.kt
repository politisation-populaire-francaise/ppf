package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.DeadlineKind
import org.politisationpopulairefrancaise.core.decider.ElectionCalendar
import org.politisationpopulairefrancaise.core.decider.ElectionStatus
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_election_announced
import org.politisationpopulairefrancaise.ui.resources.decider_election_card_deadline
import org.politisationpopulairefrancaise.ui.resources.decider_election_card_round
import org.politisationpopulairefrancaise.ui.resources.decider_election_convened
import org.politisationpopulairefrancaise.ui.resources.decider_election_deadline_candidacies
import org.politisationpopulairefrancaise.ui.resources.decider_election_deadline_online
import org.politisationpopulairefrancaise.ui.resources.decider_election_deadline_proxy
import org.politisationpopulairefrancaise.ui.resources.decider_election_deadline_town_hall
import org.politisationpopulairefrancaise.ui.resources.decider_election_deadlines
import org.politisationpopulairefrancaise.ui.resources.decider_election_decree
import org.politisationpopulairefrancaise.ui.resources.decider_election_overseas
import org.politisationpopulairefrancaise.ui.resources.decider_election_passed
import org.politisationpopulairefrancaise.ui.resources.decider_election_privacy
import org.politisationpopulairefrancaise.ui.resources.decider_election_round
import org.politisationpopulairefrancaise.ui.resources.decider_election_rounds
import org.politisationpopulairefrancaise.ui.resources.decider_election_source
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** Test tags of the personal electoral calendar (DE-28). */
object ElectionTags {
    const val STATUS = "decider-election-status"
    const val PRIVACY = "decider-election-privacy"
    fun page(slug: String) = "decider-election-$slug"
    fun round(number: Int) = "decider-election-round-$number"
    fun deadline(kind: String) = "decider-election-deadline-$kind"
}

internal val DeadlineKind.label: StringResource get() = when (this) {
    DeadlineKind.REGISTRATION_TOWN_HALL -> Res.string.decider_election_deadline_town_hall
    DeadlineKind.REGISTRATION_ONLINE -> Res.string.decider_election_deadline_online
    DeadlineKind.PROXY -> Res.string.decider_election_deadline_proxy
    DeadlineKind.CANDIDACIES -> Res.string.decider_election_deadline_candidacies
}

/** The card of the home: the election, its first round still to come, and the nearest deadline. */
@Composable
internal fun ElectionCard(election: ElectionCalendar, today: CivilDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(election.article.slug))) {
        Text(election.election.label.withoutExample(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        election.rounds.firstOrNull { it.day >= today }?.let { round ->
            Text(stringResource(Res.string.decider_election_card_round, round.number, round.day.toFrench()), style = MaterialTheme.typography.bodyMedium)
        }
        election.deadlinesAhead(today).firstOrNull()?.let { next ->
            Text(
                stringResource(Res.string.decider_election_card_deadline, stringResource(next.kind.label), next.date.toFrench()),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
            )
        }
        Text(statusText(election), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun statusText(election: ElectionCalendar): String = when (election.status) {
    ElectionStatus.ANNOUNCED -> stringResource(Res.string.decider_election_announced)
    ElectionStatus.CONVENED -> stringResource(Res.string.decider_election_convened, election.decree?.date?.toFrench().orEmpty())
}

/**
 * One election of the calendar: whether its dates are set by the decree or only announced, each round with the
 * closing of the polling stations, each deadline with its official source. Nothing personal leaves the device.
 */
@Composable
internal fun ElectionPage(election: ElectionCalendar, today: CivilDate) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    Column(Modifier.fillMaxWidth().testTag(ElectionTags.page(election.article.slug))) {
        PageHeader(election.election.label.withoutExample(), election.article.summary?.withoutExample())
        Spacer(Modifier.height(12.dp))
        InfoBox(if (election.status == ElectionStatus.CONVENED) Lucide.BadgeCheck else Lucide.Clock, Modifier.testTag(ElectionTags.STATUS)) {
            Text(statusText(election), style = MaterialTheme.typography.bodyMedium)
            election.decree?.let { decree ->
                PpfTextButton(onClick = { open(decree.url) }, text = stringResource(Res.string.decider_election_decree, decree.number))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.decider_election_rounds), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        election.rounds.forEachIndexed { index, round ->
            if (index > 0) HorizontalDivider(color = palette.border)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(ElectionTags.round(round.number))) {
                Text(
                    stringResource(Res.string.decider_election_round, round.number, round.day.toFrench(), frenchClock(round.closingHour, round.closingMinute)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (round.firstDay != round.day) {
                    Text(stringResource(Res.string.decider_election_overseas, round.firstDay.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
            }
        }
        if (election.deadlines.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.decider_election_deadlines), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            election.deadlines.forEachIndexed { index, deadline ->
                if (index > 0) HorizontalDivider(color = palette.border)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(ElectionTags.deadline(deadline.kind.tagValue))) {
                    Text(stringResource(deadline.kind.label), style = MaterialTheme.typography.titleSmall)
                    Text(deadline.date.toFrench(), style = MaterialTheme.typography.bodyLarge)
                    if (deadline.date < today) Text(stringResource(Res.string.decider_election_passed), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    PpfTextButton(onClick = { open(deadline.source) }, text = stringResource(Res.string.decider_election_source))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.decider_election_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(ElectionTags.PRIVACY))
    }
}
