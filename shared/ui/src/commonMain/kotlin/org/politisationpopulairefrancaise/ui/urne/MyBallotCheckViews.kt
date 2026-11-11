package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.urne.MyBallotCheck
import org.politisationpopulairefrancaise.core.urne.MyBallotFinding
import org.politisationpopulairefrancaise.core.urne.RecountResult
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.urne_mine_altered
import org.politisationpopulairefrancaise.ui.resources.urne_mine_button
import org.politisationpopulairefrancaise.ui.resources.urne_mine_counted
import org.politisationpopulairefrancaise.ui.resources.urne_mine_earlier
import org.politisationpopulairefrancaise.ui.resources.urne_mine_explain
import org.politisationpopulairefrancaise.ui.resources.urne_mine_missing
import org.politisationpopulairefrancaise.ui.resources.urne_mine_no_receipt
import org.politisationpopulairefrancaise.ui.resources.urne_mine_not_sent
import org.politisationpopulairefrancaise.ui.resources.urne_mine_not_voted
import org.politisationpopulairefrancaise.ui.resources.urne_mine_recount_correct
import org.politisationpopulairefrancaise.ui.resources.urne_mine_recount_wrong
import org.politisationpopulairefrancaise.ui.resources.urne_mine_running
import org.politisationpopulairefrancaise.ui.resources.urne_mine_unavailable
import org.politisationpopulairefrancaise.ui.resources.urne_mine_withdrawn
import org.politisationpopulairefrancaise.ui.resources.urne_recount_duplicate
import org.politisationpopulairefrancaise.ui.resources.urne_recount_invalid_token
import org.politisationpopulairefrancaise.ui.resources.urne_recount_key
import org.politisationpopulairefrancaise.ui.resources.urne_recount_too_many
import org.politisationpopulairefrancaise.ui.resources.urne_recount_totals
import org.politisationpopulairefrancaise.ui.resources.urne_recount_unknown
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Mon bulletin a été compté » (DE-29). */
object MyBallotTags {
    const val SECTION = "urne-mine"
    const val BUTTON = "urne-mine-button"
    const val FINDING = "urne-mine-finding"
    const val RECOUNT = "urne-mine-recount"
    const val UNAVAILABLE = "urne-mine-unavailable"
}

private sealed interface CheckState {
    data object Idle : CheckState
    data object Running : CheckState
    data object Unavailable : CheckState
    data class Done(val check: MyBallotCheck) : CheckState
}

/**
 * « Recompter et vérifier mon bulletin » under a closed ballot box (DE-29): the same button for everyone, whether
 * or not this device voted there, so that asking for the published list says nothing of a vote. The answer is
 * shown here only, never kept, never shareable: it names no choice and shows no identifier, so it proves nothing
 * to anyone else (« le reçu n'est jamais montrable à un tiers »).
 */
@Composable
fun MyBallotCheckSection(check: suspend () -> MyBallotCheck?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    Column(modifier.fillMaxWidth().testTag(MyBallotTags.SECTION), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.urne_mine_explain), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        PpfSecondaryButton(
            onClick = {
                state = CheckState.Running
                scope.launch { state = check()?.let { CheckState.Done(it) } ?: CheckState.Unavailable }
            },
            text = stringResource(Res.string.urne_mine_button),
            enabled = state != CheckState.Running,
            small = true,
            icon = Lucide.ShieldCheck,
            modifier = Modifier.testTag(MyBallotTags.BUTTON),
        )
        val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        when (val current = state) {
            CheckState.Idle -> Unit
            CheckState.Running -> Text(stringResource(Res.string.urne_mine_running), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = live)
            CheckState.Unavailable -> Text(
                stringResource(Res.string.urne_mine_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                modifier = live.testTag(MyBallotTags.UNAVAILABLE),
            )
            is CheckState.Done -> {
                val finding = current.check.finding
                val findingAlarming = finding == MyBallotFinding.MISSING || finding == MyBallotFinding.ALTERED
                Text(
                    stringResource(findingText(finding)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (findingAlarming) palette.warning else palette.ink,
                    modifier = live.testTag(MyBallotTags.FINDING),
                )
                val recount = current.check.recount
                Text(
                    if (recount == RecountResult.CORRECT) {
                        stringResource(Res.string.urne_mine_recount_correct)
                    } else {
                        stringResource(Res.string.urne_mine_recount_wrong, stringResource(recountReason(recount)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recount == RecountResult.CORRECT) palette.inkMuted else palette.warning,
                    modifier = Modifier.testTag(MyBallotTags.RECOUNT),
                )
                Text(stringResource(Res.string.urne_mine_no_receipt), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }
    }
}

private fun findingText(finding: MyBallotFinding): StringResource = when (finding) {
    MyBallotFinding.COUNTED -> Res.string.urne_mine_counted
    MyBallotFinding.WITHDRAWN -> Res.string.urne_mine_withdrawn
    MyBallotFinding.COUNTED_EARLIER_CHOICE -> Res.string.urne_mine_earlier
    MyBallotFinding.NOT_SENT -> Res.string.urne_mine_not_sent
    MyBallotFinding.NOT_VOTED -> Res.string.urne_mine_not_voted
    MyBallotFinding.MISSING -> Res.string.urne_mine_missing
    MyBallotFinding.ALTERED -> Res.string.urne_mine_altered
}

private fun recountReason(result: RecountResult): StringResource = when (result) {
    RecountResult.CORRECT, RecountResult.WRONG_TOTALS -> Res.string.urne_recount_totals
    RecountResult.KEY_MISMATCH -> Res.string.urne_recount_key
    RecountResult.INVALID_TOKEN -> Res.string.urne_recount_invalid_token
    RecountResult.DUPLICATE_BALLOT -> Res.string.urne_recount_duplicate
    RecountResult.MORE_BALLOTS_THAN_TOKENS -> Res.string.urne_recount_too_many
    RecountResult.UNKNOWN_VALUE -> Res.string.urne_recount_unknown
}
