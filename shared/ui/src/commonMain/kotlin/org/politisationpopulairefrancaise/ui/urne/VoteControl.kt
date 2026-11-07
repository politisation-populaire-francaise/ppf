package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.urne_ballots
import org.politisationpopulairefrancaise.ui.resources.urne_citizens_title
import org.politisationpopulairefrancaise.ui.resources.urne_closed
import org.politisationpopulairefrancaise.ui.resources.urne_elected_apart
import org.politisationpopulairefrancaise.ui.resources.urne_elected_title
import org.politisationpopulairefrancaise.ui.resources.urne_hidden_until_closing
import org.politisationpopulairefrancaise.ui.resources.urne_issued
import org.politisationpopulairefrancaise.ui.resources.urne_level_not_verified
import org.politisationpopulairefrancaise.ui.resources.urne_level_total
import org.politisationpopulairefrancaise.ui.resources.urne_level_verified
import org.politisationpopulairefrancaise.ui.resources.urne_no_token
import org.politisationpopulairefrancaise.ui.resources.urne_privacy
import org.politisationpopulairefrancaise.ui.resources.urne_recorded
import org.politisationpopulairefrancaise.ui.resources.urne_sending
import org.politisationpopulairefrancaise.ui.resources.urne_unavailable
import org.politisationpopulairefrancaise.ui.resources.urne_withdraw
import org.politisationpopulairefrancaise.ui.resources.urne_withdrawn
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the voting components. */
object UrneTags {
    const val CONTROL = "urne-control"
    const val STATUS = "urne-status"
    const val WITHDRAW = "urne-withdraw"
    const val RESULTS = "urne-results"
    fun choice(code: String) = "urne-choice-$code"
}

/** What the device knows about its vote in one ballot box. */
data class VoteUiState(
    /** Voting cannot work here (self-test failed, no server): the reason is shown, nothing else. */
    val available: Boolean,
    /** A token for this box is on the device. */
    val hasToken: Boolean,
    val closed: Boolean,
    /** The choice made on this device, null before any vote. */
    val choice: String?,
    /** The server has this very choice (false while the ballot waits to leave). */
    val sent: Boolean,
)

/**
 * The vote in one ballot box, the same component everywhere (D-110, contract of `core/urne`):
 * one choice among [choices] (code to label), the state of the ballot, and what anonymity means.
 * The ballot leaves by itself a few minutes later, without the account (D-112): the component
 * says so, it never offers to send at once.
 */
@Composable
fun VoteControl(
    choices: List<Pair<String, String>>,
    state: VoteUiState,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(UrneTags.CONTROL)) {
        when {
            !state.available -> {
                InfoBox(Lucide.Info) { Text(stringResource(Res.string.urne_unavailable), style = MaterialTheme.typography.bodyMedium) }
                return@Column
            }
            state.closed -> {
                Text(stringResource(Res.string.urne_closed), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(UrneTags.STATUS))
                return@Column
            }
            !state.hasToken -> {
                Text(stringResource(Res.string.urne_no_token), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(UrneTags.STATUS))
                return@Column
            }
        }
        Column(Modifier.selectableGroup()) {
            choices.forEach { (code, label) ->
                PpfRadioRow(label = label, selected = state.choice == code, onClick = { onChoose(code) }, modifier = Modifier.testTag(UrneTags.choice(code)))
            }
        }
        val status = when {
            state.choice == null -> null
            state.choice == UrneInfo.WITHDRAWN && state.sent -> stringResource(Res.string.urne_withdrawn)
            state.sent -> stringResource(Res.string.urne_recorded)
            else -> stringResource(Res.string.urne_sending)
        }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(UrneTags.STATUS))
        }
        if (state.choice != null && state.choice != UrneInfo.WITHDRAWN) {
            PpfTextButton(onClick = { onChoose(UrneInfo.WITHDRAWN) }, text = stringResource(Res.string.urne_withdraw), modifier = Modifier.testTag(UrneTags.WITHDRAW))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.urne_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

/**
 * The results of a ballot box (D-112): the citizens' vote, verified accounts first, then not
 * verified, then their total; the elected officials' vote apart, never added. Words before colours.
 */
@Composable
fun UrneResultsView(journal: UrneJournal, labels: Map<String, String>, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(UrneTags.RESULTS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val results = journal.results
        if (results == null) {
            Text(stringResource(Res.string.urne_hidden_until_closing), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        } else {
            Text(stringResource(Res.string.urne_citizens_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            CountsBlock(stringResource(Res.string.urne_level_verified), results.verified, labels, journal.issued["verifie"])
            CountsBlock(stringResource(Res.string.urne_level_not_verified), results.notVerified, labels, journal.issued["non-verifie"])
            CountsBlock(stringResource(Res.string.urne_level_total), results.citizens, labels, null)
            HorizontalDivider(color = palette.border)
            Text(stringResource(Res.string.urne_elected_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            CountsBlock(null, results.electedOfficials, labels, journal.issued["elu"])
            Text(stringResource(Res.string.urne_elected_apart), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

@Composable
private fun CountsBlock(title: String?, counts: ChoiceCounts, labels: Map<String, String>, issued: Long?) {
    val palette = PpfTheme.palette
    Inset(Modifier.fillMaxWidth()) {
        title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        labels.forEach { (code, label) ->
            Text("$label : ${counts.counts[code] ?: 0}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(Res.string.urne_ballots, counts.ballots), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        issued?.let { Text(stringResource(Res.string.urne_issued, it), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
    }
}
