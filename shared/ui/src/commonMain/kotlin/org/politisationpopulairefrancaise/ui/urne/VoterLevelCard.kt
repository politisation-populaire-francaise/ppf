package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.urne.CertificationAnswer
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.voter_certification_ended
import org.politisationpopulairefrancaise.ui.resources.voter_certification_pending
import org.politisationpopulairefrancaise.ui.resources.voter_certification_refused
import org.politisationpopulairefrancaise.ui.resources.voter_certification_withdrawn
import org.politisationpopulairefrancaise.ui.resources.voter_explain_elu
import org.politisationpopulairefrancaise.ui.resources.voter_explain_non_verifie
import org.politisationpopulairefrancaise.ui.resources.voter_explain_verifie
import org.politisationpopulairefrancaise.ui.resources.voter_from
import org.politisationpopulairefrancaise.ui.resources.voter_level_elu
import org.politisationpopulairefrancaise.ui.resources.voter_level_non_verifie
import org.politisationpopulairefrancaise.ui.resources.voter_level_verifie
import org.politisationpopulairefrancaise.ui.resources.voter_since
import org.politisationpopulairefrancaise.ui.resources.voter_title
import org.politisationpopulairefrancaise.ui.resources.voter_validation_pending
import org.politisationpopulairefrancaise.ui.resources.voter_waiting
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the card « Mon niveau de votant » (TE-08). */
object VoterLevelTags {
    const val CARD: String = "voter-level"
    const val LEVEL: String = "voter-level-name"
    const val NOTE: String = "voter-level-note"
}

/**
 * « Mon niveau de votant » in "Mon espace" (TE-08, D-110, D-112): the level the server gives the account
 * (non vérifié, vérifié, élu), the Monday from which it counts, a validation or a certification under way,
 * and how to get verified. The result of the self-test on this device stays at the bottom of the page
 * ([VoteCheckLine]). Nothing here is sent anywhere; hidden where voting is not available.
 */
@Composable
fun VoterLevelCard(modifier: Modifier = Modifier) {
    val voting = LocalVoting.current ?: return
    if (!voting.enabled) return
    val palette = PpfTheme.palette
    PpfCard(modifier.fillMaxWidth().testTag(VoterLevelTags.CARD)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.voter_title), style = MaterialTheme.typography.titleMedium)
            val status = voting.status
            if (status == null) {
                Text(stringResource(Res.string.voter_waiting), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                return@Column
            }
            val level = VoteLevel.of(status.level) ?: VoteLevel.NON_VERIFIE
            Text(stringResource(levelName(level)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(VoterLevelTags.LEVEL))
            weekLine(status)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
            Text(stringResource(explanation(level)), style = MaterialTheme.typography.bodyMedium)
            noteOf(status)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(VoterLevelTags.NOTE)) }
        }
    }
}

private fun levelName(level: VoteLevel): StringResource = when (level) {
    VoteLevel.NON_VERIFIE -> Res.string.voter_level_non_verifie
    VoteLevel.VERIFIE -> Res.string.voter_level_verifie
    VoteLevel.ELU -> Res.string.voter_level_elu
}

private fun explanation(level: VoteLevel): StringResource = when (level) {
    VoteLevel.NON_VERIFIE -> Res.string.voter_explain_non_verifie
    VoteLevel.VERIFIE -> Res.string.voter_explain_verifie
    VoteLevel.ELU -> Res.string.voter_explain_elu
}

/** « Compte ainsi depuis le lundi … », or « à partir du lundi … » for a change still to come. */
@Composable
private fun weekLine(status: VoterStatus): String? {
    val week = CivilDate.parseIso(status.levelWeek) ?: return null
    val previous = status.previousLevel?.let(VoteLevel::of)
    val today = CivilDate.fromEpochSeconds(org.politisationpopulairefrancaise.core.time.currentEpochSeconds())
    return if (week > today && previous != null) {
        stringResource(Res.string.voter_from, week.toFrench(), stringResource(levelName(previous)))
    } else {
        stringResource(Res.string.voter_since, week.toFrench())
    }
}

/** A validation or a certification under way, or its outcome. */
@Composable
private fun noteOf(status: VoterStatus): String? = when (status.certification) {
    CertificationAnswer.PENDING -> stringResource(Res.string.voter_certification_pending)
    CertificationAnswer.REFUSED -> stringResource(Res.string.voter_certification_refused, status.certificationReason.orEmpty())
    CertificationAnswer.WITHDRAWN -> stringResource(Res.string.voter_certification_withdrawn, status.certificationReason.orEmpty())
    CertificationAnswer.ENDED -> stringResource(Res.string.voter_certification_ended)
    else -> if (status.validationPending) stringResource(Res.string.voter_validation_pending) else null
}
