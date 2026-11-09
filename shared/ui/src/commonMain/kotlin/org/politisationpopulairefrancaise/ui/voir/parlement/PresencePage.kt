package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.GroupPresence
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.PresenceAndDelegations
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Présence et délégations ». */
object PresenceTags {
    const val ENTRY = "presence-entry"
    const val PAGE = "presence-page"
    const val NONE = "presence-none"
    const val DEFINITION = "presence-definition"
    const val TABLE = "presence-table"
    fun row(groupId: String) = "presence-row-$groupId"
}

/** The card on the list of the Parliament that opens « Présence et délégations ». */
@Composable
internal fun PresenceEntry(state: ParliamentState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(PresenceTags.ENTRY), onClick = { state.show(ParliamentPage.Presence) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.UsersRound, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.presence_entry_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.presence_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * « Présence et délégations » (VO-35), a page of the parliamentary screen: the definition first, then one line
 * per group of the Assemblée nationale over its solemn ballots on the device (votes cast by the members
 * themselves, by delegation, not voting, no vote recorded), in the alphabetical order of the groups, with the
 * limits of the figures. Never member by member, never ranked, neutral colours only.
 */
@Composable
internal fun PresencePage(snapshot: ParliamentSnapshot, padding: Dp) {
    val palette = PpfTheme.palette
    val catalog = snapshot.catalog
    val presence = remember(catalog) { PresenceAndDelegations.of(catalog, Chamber.AN) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(PresenceTags.PAGE)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.presence_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            InfoBox(Lucide.Info, Modifier.testTag(PresenceTags.DEFINITION)) {
                Text(stringResource(Res.string.presence_definition_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.size(4.dp))
                Text(stringResource(Res.string.presence_definition), style = MaterialTheme.typography.bodyMedium)
            }
            if (presence == null) {
                Text(stringResource(Res.string.presence_none), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(PresenceTags.NONE))
            } else {
                Text(
                    stringResource(Res.string.presence_chamber, stringResource(Chamber.AN.label), presence.ballots.size, presence.from?.toFrench().orEmpty(), presence.to?.toFrench().orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
                PresenceTable(presence.groups)
            }
            Text(stringResource(Res.string.presence_limits), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            Text(stringResource(Res.string.presence_no_ranking), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            Text(stringResource(Res.string.presence_other_chambers), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            Text(stringResource(Res.string.presence_sources), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

/** The table, readable by screen readers one sentence per group (the header row is hidden from them). */
@Composable
private fun PresenceTable(groups: List<GroupPresence>) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(PresenceTags.TABLE)) {
        val headers = listOf(
            stringResource(Res.string.presence_column_group),
            stringResource(Res.string.presence_column_personal),
            stringResource(Res.string.presence_column_delegated),
            stringResource(Res.string.presence_column_not_voting),
            stringResource(Res.string.presence_column_without_vote),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics { }) {
            headers.forEachIndexed { index, header ->
                Text(header, style = MaterialTheme.typography.labelMedium, color = palette.inkMuted, modifier = Modifier.weight(if (index == 0) 2f else 1f))
            }
        }
        HorizontalDivider(color = palette.border)
        groups.forEach { row ->
            val name = groupName(row.group.label, row.group.abbreviation)
            val sentence = stringResource(
                Res.string.presence_row,
                name,
                row.members,
                row.percent(row.personal),
                row.percent(row.delegated),
                row.percent(row.notVoting),
                row.percent(row.withoutVote),
                row.expected,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).testTag(PresenceTags.row(row.group.id)).clearAndSetSemantics { contentDescription = sentence },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(2f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.presence_members, row.members, row.expected), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                listOf(row.personal, row.delegated, row.notVoting, row.withoutVote).forEach { value ->
                    Text(stringResource(Res.string.presence_cell, row.percent(value), value), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = palette.border)
        }
    }
}
