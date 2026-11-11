package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.SupportThresholds
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.components.ActionButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.revendication_debate
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_against
import org.politisationpopulairefrancaise.ui.resources.revendication_vote_for
import org.politisationpopulairefrancaise.ui.resources.urne_closed
import org.politisationpopulairefrancaise.ui.resources.urne_compromised_tokens
import org.politisationpopulairefrancaise.ui.resources.urne_demand_not_open
import org.politisationpopulairefrancaise.ui.resources.urne_demand_opening
import org.politisationpopulairefrancaise.ui.resources.urne_elected_line
import org.politisationpopulairefrancaise.ui.resources.urne_elected_public
import org.politisationpopulairefrancaise.ui.resources.urne_elected_recorded
import org.politisationpopulairefrancaise.ui.resources.urne_elected_too_few
import org.politisationpopulairefrancaise.ui.resources.urne_results_too_few
import org.politisationpopulairefrancaise.ui.resources.urne_need_account
import org.politisationpopulairefrancaise.ui.resources.urne_no_token
import org.politisationpopulairefrancaise.ui.resources.urne_not_voted
import org.politisationpopulairefrancaise.ui.resources.urne_offline
import org.politisationpopulairefrancaise.ui.resources.urne_privacy
import org.politisationpopulairefrancaise.ui.resources.urne_recorded
import org.politisationpopulairefrancaise.ui.resources.urne_results_line
import org.politisationpopulairefrancaise.ui.resources.urne_sending
import org.politisationpopulairefrancaise.ui.resources.urne_stage_carry
import org.politisationpopulairefrancaise.ui.resources.urne_stage_petition
import org.politisationpopulairefrancaise.ui.resources.urne_supports_line
import org.politisationpopulairefrancaise.ui.resources.urne_unavailable
import org.politisationpopulairefrancaise.ui.resources.urne_vote_blank
import org.politisationpopulairefrancaise.ui.resources.urne_your_vote
import org.politisationpopulairefrancaise.ui.resources.urne_withdraw
import org.politisationpopulairefrancaise.ui.resources.urne_withdrawn
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** The voting of the application, provided once by the shell; null in previews and tests without it. */
val LocalVoting = staticCompositionLocalOf<VotingState?> { null }

/** Test tags of the vote of a demand. */
object DemandVoteTags {
    const val FOR = "revendication-vote-for"
    const val AGAINST = "revendication-vote-against"
    const val BLANK = "revendication-vote-blank"
    const val YOUR_VOTE = "revendication-your-vote"
    const val STATUS = "revendication-vote-status"
    const val RESULTS = "revendication-vote-results"
    const val WITHDRAW = "revendication-vote-withdraw"
    const val SUPPORTS = "revendication-vote-supports"
    const val STAGE = "revendication-vote-stage"
    const val COMPROMISED = "revendication-vote-compromised"
}

/**
 * Pour, Contre and Blanc on a demand (DE-01, D-110; the blank vote is counted and shown, owner's
 * decision of 7 November 2026): enabled when its ballot box is open and the device
 * holds its token; the chosen one shown active; the citizens' results and the elected officials'
 * apart (D-112); one line that says where the vote stands. [inDebate]: the team put the demand in
 * debate (its box opens within minutes). [detailed] adds what anonymity means.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemandVoteRow(
    subject: String,
    inDebate: Boolean,
    modifier: Modifier = Modifier,
    detailed: Boolean = false,
    /** The demand's `ppf-territory-code` (D-090): its thresholds of supports (D-115); France without one. */
    territoryCode: String? = null,
) {
    val voting = LocalVoting.current
    val box = voting?.box(subject)
    val state = voting?.stateFor(subject)
    // In data saver mode, the tokens wait for a vote to be shown (D-110, 6.3).
    val hasAccount = voting?.hasAccount == true
    val thresholds by produceState<SupportThresholds.Thresholds?>(null, voting, territoryCode) { value = voting?.thresholdsFor(territoryCode) }
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
            ActionButton(
                Lucide.Minus,
                stringResource(Res.string.urne_vote_blank),
                onClick = { voting?.choose(subject, BLANK) },
                enabled = canVote,
                active = state?.choice == BLANK,
                modifier = Modifier.testTag(DemandVoteTags.BLANK),
            )
            // The debate itself (arguments for and against) comes later.
            ActionButton(Lucide.MessageCircle, stringResource(Res.string.revendication_debate), onClick = { }, enabled = false)
        }
        box?.results?.let { results ->
            Text(
                if (results.citizens.hidden) {
                    stringResource(Res.string.urne_results_too_few, results.minimum ?: 5)
                } else {
                    stringResource(Res.string.urne_results_line, count(results.citizens, FOR), count(results.citizens, AGAINST), count(results.citizens, BLANK))
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(DemandVoteTags.RESULTS),
            )
            val supports = SupportThresholds.supports(results)
            val levels = thresholds
            if (supports != null && levels != null) {
                Text(
                    stringResource(Res.string.urne_supports_line, count(supports), count(levels.carry), count(levels.petition)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(DemandVoteTags.SUPPORTS),
                )
                when (SupportThresholds.stage(results, levels)) {
                    SupportThresholds.Stage.CARRY -> Text(stringResource(Res.string.urne_stage_carry), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(DemandVoteTags.STAGE))
                    SupportThresholds.Stage.PETITION -> Text(stringResource(Res.string.urne_stage_petition), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(DemandVoteTags.STAGE))
                    SupportThresholds.Stage.BELOW -> Unit
                }
            }
            if (results.electedOfficials.hidden) {
                Text(stringResource(Res.string.urne_elected_too_few, results.minimum ?: 5), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            } else if (results.electedOfficials.ballots > 0) {
                Text(
                    stringResource(Res.string.urne_elected_line, count(results.electedOfficials, FOR), count(results.electedOfficials, AGAINST), count(results.electedOfficials, BLANK)),
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
            voting.elected && state?.choice == null -> stringResource(Res.string.urne_elected_public)
            voting.elected -> stringResource(Res.string.urne_elected_recorded)
            state == null || !state.hasToken -> stringResource(Res.string.urne_no_token)
            state.choice == null -> stringResource(Res.string.urne_not_voted)
            state.choice == UrneInfo.WITHDRAWN -> stringResource(Res.string.urne_withdrawn)
            !state.sent -> stringResource(Res.string.urne_sending)
            else -> stringResource(Res.string.urne_recorded)
        }
        // After a vote, the choice made on this device, in words (the ballot itself leaves later, anonymous).
        val chosen = state?.choice?.takeIf { it != UrneInfo.WITHDRAWN && voting?.elected != true }
        if (chosen != null) {
            val label = stringResource(
                when (chosen) {
                    FOR -> Res.string.revendication_vote_for
                    AGAINST -> Res.string.revendication_vote_against
                    else -> Res.string.urne_vote_blank
                },
            )
            Text(stringResource(Res.string.urne_your_vote, label), style = MaterialTheme.typography.labelLarge, color = palette.brand, modifier = Modifier.testTag(DemandVoteTags.YOUR_VOTE))
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(DemandVoteTags.STATUS))
        // An account declared compromised gets no new token (S-146), but the tokens already on this device stay the
        // person's (blinded, never linked to the account): the buttons stay, and this line says why.
        if (!LocalCanPublish.current && box != null && !box.closed) {
            Text(stringResource(Res.string.urne_compromised_tokens), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(DemandVoteTags.COMPROMISED))
        }
        if (canVote && state?.choice != null && state.choice != UrneInfo.WITHDRAWN) {
            PpfTextButton(
                onClick = { voting?.choose(subject, UrneInfo.WITHDRAWN) },
                text = stringResource(Res.string.urne_withdraw),
                modifier = Modifier.testTag(DemandVoteTags.WITHDRAW),
            )
        }
        if (detailed && box != null && voting?.elected != true) {
            Text(stringResource(Res.string.urne_privacy), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        // DE-29: after the closing, the same button for everyone (voted here or not).
        if (detailed && box != null && box.closed && voting != null && voting.enabled) {
            MyBallotCheckSection(check = { voting.checkClosed(subject) })
        }
    }
}

private const val FOR = "pour"
private const val AGAINST = "contre"
private const val BLANK = "blanc"

private fun count(counts: ChoiceCounts, choice: String): String = FrenchNumbers.format((counts.counts[choice] ?: 0L).toDouble())

private fun count(value: Long): String = FrenchNumbers.format(value.toDouble())
