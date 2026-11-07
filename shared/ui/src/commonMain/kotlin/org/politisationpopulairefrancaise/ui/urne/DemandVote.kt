package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.components.ActionButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.revendication_debate
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_against
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_for
import org.politisationpopulairefrancaise.ui.resources.urne_closed
import org.politisationpopulairefrancaise.ui.resources.urne_demand_not_open
import org.politisationpopulairefrancaise.ui.resources.urne_demand_opening
import org.politisationpopulairefrancaise.ui.resources.urne_elected_line
import org.politisationpopulairefrancaise.ui.resources.urne_need_account
import org.politisationpopulairefrancaise.ui.resources.urne_no_token
import org.politisationpopulairefrancaise.ui.resources.urne_not_voted
import org.politisationpopulairefrancaise.ui.resources.urne_offline
import org.politisationpopulairefrancaise.ui.resources.urne_privacy
import org.politisationpopulairefrancaise.ui.resources.urne_recorded
import org.politisationpopulairefrancaise.ui.resources.urne_results_line
import org.politisationpopulairefrancaise.ui.resources.urne_sending
import org.politisationpopulairefrancaise.ui.resources.urne_unavailable
import org.politisationpopulairefrancaise.ui.resources.urne_withdraw
import org.politisationpopulairefrancaise.ui.resources.urne_withdrawn
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The voting of the application, provided once by the shell; null in previews and tests without it. */
val LocalVoting = staticCompositionLocalOf<VotingState?> { null }

/** Test tags of the vote of a demand. */
object DemandVoteTags {
    const val FOR = "revendication-vote-for"
    const val AGAINST = "revendication-vote-against"
    const val STATUS = "revendication-vote-status"
    const val RESULTS = "revendication-vote-results"
    const val WITHDRAW = "revendication-vote-withdraw"
}

/**
 * Pour and Contre on a demand (DE-01, D-110): enabled when its ballot box is open and the device
 * holds its token; the chosen one shown active; the citizens' results and the elected officials'
 * apart (D-112); one line that says where the vote stands. [inDebate]: the team put the demand in
 * debate (its box opens within minutes). [detailed] adds what anonymity means.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemandVoteRow(subject: String, inDebate: Boolean, modifier: Modifier = Modifier, detailed: Boolean = false) {
    val voting = LocalVoting.current
    val box = voting?.box(subject)
    val state = voting?.stateFor(subject)
    // In data saver mode, the tokens wait for a vote to be shown (D-110, 6.3).
    val hasAccount = voting?.hasAccount == true
    LaunchedEffect(voting, box?.number, state?.hasToken, hasAccount) {
        if (box != null || inDebate) voting?.shown()
        // Put in debate but its box not seen yet, or a box without its token: the next pass comes sooner.
        if ((box == null && inDebate) || (box != null && !box.closed && hasAccount && state?.hasToken != true)) voting?.awaiting()
    }
    val canVote = state != null && state.available && state.hasToken && !state.closed && hasAccount
    val palette = PpfTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(
                Lucide.ThumbsUp,
                stringResource(Res.string.revendication_vote_for),
                onClick = { voting?.choose(subject, FOR) },
                enabled = canVote,
                active = state?.choice == FOR,
                modifier = Modifier.testTag(DemandVoteTags.FOR),
            )
            ActionButton(
                Lucide.ThumbsDown,
                stringResource(Res.string.revendication_vote_against),
                onClick = { voting?.choose(subject, AGAINST) },
                enabled = canVote,
                active = state?.choice == AGAINST,
                modifier = Modifier.testTag(DemandVoteTags.AGAINST),
            )
            // The debate itself (arguments for and against) comes later.
            ActionButton(Lucide.MessageCircle, stringResource(Res.string.revendication_debate), onClick = { }, enabled = false)
        }
        box?.results?.let { results ->
            Text(
                stringResource(Res.string.urne_results_line, count(results.citizens, FOR), count(results.citizens, AGAINST)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(DemandVoteTags.RESULTS),
            )
            if (results.electedOfficials.ballots > 0) {
                Text(
                    stringResource(Res.string.urne_elected_line, count(results.electedOfficials, FOR), count(results.electedOfficials, AGAINST)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            }
        }
        val status = when {
            voting == null || !voting.enabled -> stringResource(Res.string.urne_offline)
            voting.closedHere -> stringResource(Res.string.urne_unavailable)
            box == null -> stringResource(if (inDebate) Res.string.urne_demand_opening else Res.string.urne_demand_not_open)
            box.closed -> stringResource(Res.string.urne_closed)
            !hasAccount -> stringResource(Res.string.urne_need_account)
            state == null || !state.hasToken -> stringResource(Res.string.urne_no_token)
            state.choice == null -> stringResource(Res.string.urne_not_voted)
            state.choice == UrneInfo.WITHDRAWN -> stringResource(Res.string.urne_withdrawn)
            !state.sent -> stringResource(Res.string.urne_sending)
            else -> stringResource(Res.string.urne_recorded)
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(DemandVoteTags.STATUS))
        if (canVote && state?.choice != null && state.choice != UrneInfo.WITHDRAWN) {
            PpfTextButton(
                onClick = { voting?.choose(subject, UrneInfo.WITHDRAWN) },
                text = stringResource(Res.string.urne_withdraw),
                modifier = Modifier.testTag(DemandVoteTags.WITHDRAW),
            )
        }
        if (detailed && box != null) {
            Text(stringResource(Res.string.urne_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}

private const val FOR = "pour"
private const val AGAINST = "contre"

private fun count(counts: ChoiceCounts, choice: String): String = FrenchNumbers.format((counts.counts[choice] ?: 0L).toDouble())
