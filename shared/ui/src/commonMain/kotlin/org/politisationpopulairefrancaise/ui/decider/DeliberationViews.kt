package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.Amendment
import org.politisationpopulairefrancaise.core.decider.DeliberationBoard
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.decider.DeliberationPhase
import org.politisationpopulairefrancaise.core.decider.DeliberationRules
import org.politisationpopulairefrancaise.core.decider.PropositionEntry
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.revendications.propose.SendOutcome
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.urne.LocalVoting
import org.politisationpopulairefrancaise.ui.urne.VoteControl
import org.politisationpopulairefrancaise.ui.urne.VoteUiState
import org.politisationpopulairefrancaise.ui.voir.withoutExample

object DeliberationTags {
    const val CARD = "deliberation-card"
    const val PAGE = "deliberation-page"
    const val PHASES = "deliberation-phases"
    const val PROPOSE = "deliberation-propose"
    const val FORM = "deliberation-form"
    const val FORM_TITLE = "deliberation-form-title"
    const val FORM_TEXT = "deliberation-form-text"
    const val FORM_JUSTIFICATION = "deliberation-form-justification"
    const val FORM_CHARTER = "deliberation-form-charter"
    const val FORM_SEND = "deliberation-form-send"
    const val FORM_OUTCOME = "deliberation-form-outcome"
    fun proposition(slug: String) = "deliberation-proposition-$slug"
    fun amendment(slug: String) = "deliberation-amendment-$slug"
    fun amend(slug: String) = "deliberation-amend-$slug"
    fun support(slug: String) = "deliberation-support-$slug"
}

private const val FOR = "pour"
private const val AGAINST = "contre"
private const val BLANK = "blanc"

private fun day(seconds: Long): String = CivilDate.fromEpochSeconds(seconds).toFrench()

@Composable
private fun phaseLabel(phase: DeliberationPhase): String = stringResource(
    when (phase) {
        DeliberationPhase.PROPOSITIONS -> Res.string.deliberation_phase_propositions
        DeliberationPhase.AMENDMENTS -> Res.string.deliberation_phase_amendments
        DeliberationPhase.VOTE -> Res.string.deliberation_phase_vote
    },
)

/** Where the deliberation stands at [now], in one line. */
@Composable
private fun stateLine(board: DeliberationBoard, now: Long): String {
    val deliberation = board.deliberation
    val phase = board.phaseAt(now)
    return when {
        phase != null -> stringResource(Res.string.deliberation_state_open, phaseLabel(phase), day(deliberation.dates(phase).end))
        deliberation.isOver(now) -> stringResource(Res.string.deliberation_state_over)
        else -> stringResource(Res.string.deliberation_state_soon, day(deliberation.dates(DeliberationPhase.PROPOSITIONS).start))
    }
}

/** The deliberation on the home of "Décider", under the pact it prepares. */
@Composable
fun DeliberationCard(board: DeliberationBoard, now: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeliberationTags.CARD)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Vote, contentDescription = null, tint = palette.brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(board.deliberation.title.withoutExample(), style = MaterialTheme.typography.titleSmall)
                Text(stateLine(board, now), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                Text(stringResource(Res.string.deliberation_card_count, board.entries.size), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The page of a deliberation (DE-25, MVP 1): the dated steps, then each proposition with its text, its
 * justification and, by phase, the support (propositions), the amendments and their vote (amendments), the
 * final vote (vote). Proposing and amending open the form below; PPF adopts nothing, the votes choose.
 */
@Composable
fun DeliberationPage(
    board: DeliberationBoard,
    now: Long,
    composer: DeliberationComposer?,
    signer: EventSigner?,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val deliberation = board.deliberation
    val phase = board.phaseAt(now)
    Column(modifier.widthIn(max = 760.dp).testTag(DeliberationTags.PAGE), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(deliberation.title.withoutExample(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        deliberation.subject?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted) }
        Text(stateLine(board, now), style = MaterialTheme.typography.titleSmall, color = palette.brand)
        Phases(board, now)
        InfoBox(Lucide.Info) {
            Text(stringResource(Res.string.deliberation_rules, deliberation.minMeasures, deliberation.maxMeasures), style = MaterialTheme.typography.bodyMedium)
        }
        if (phase == DeliberationPhase.PROPOSITIONS && composer != null) {
            PpfPrimaryButton(
                onClick = { composer.start(ContributionTarget.NewProposition(board.address)) },
                text = stringResource(Res.string.deliberation_propose),
                icon = Lucide.Plus,
                modifier = Modifier.testTag(DeliberationTags.PROPOSE),
            )
        }
        if (composer?.target != null) ContributionForm(composer, signer)
        if (board.entries.isEmpty()) Text(stringResource(Res.string.deliberation_no_proposition), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        board.entries.forEach { entry -> PropositionCard(entry, phase, composer) }
    }
}

@Composable
private fun Phases(board: DeliberationBoard, now: Long) {
    val palette = PpfTheme.palette
    val current = board.phaseAt(now)
    Column(Modifier.testTag(DeliberationTags.PHASES), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeliberationPhase.entries.forEachIndexed { index, phase ->
            val dates = board.deliberation.dates(phase)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, color = if (phase == current) palette.brand else palette.inkSubtle)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.deliberation_phase_dates, phaseLabel(phase), day(dates.start), day(dates.end)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (phase == current) palette.ink else palette.inkMuted,
                )
                if (phase == current) {
                    Spacer(Modifier.width(8.dp))
                    PpfBadge(stringResource(Res.string.deliberation_phase_now), BadgeStyle.BRAND)
                }
            }
        }
    }
}

@Composable
private fun PropositionCard(entry: PropositionEntry, phase: DeliberationPhase?, composer: DeliberationComposer?) {
    val palette = PpfTheme.palette
    val proposition = entry.proposition
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(DeliberationTags.proposition(proposition.address.identifier))) {
        if (proposition.isStart) {
            PpfBadge(stringResource(Res.string.deliberation_start_badge), BadgeStyle.WARNING)
            Spacer(Modifier.height(8.dp))
        }
        Text(proposition.title.withoutExample(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(proposition.text, style = MaterialTheme.typography.bodyMedium)
        proposition.justification?.let {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.deliberation_justification, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(8.dp))
        when (phase) {
            DeliberationPhase.PROPOSITIONS -> SupportButton(DeliberationRules.supportUrneId(proposition.address), DeliberationTags.support(proposition.address.identifier))
            DeliberationPhase.AMENDMENTS -> if (composer != null) {
                PpfSecondaryButton(
                    onClick = { composer.start(ContributionTarget.NewAmendment(proposition.address, proposition.text)) },
                    text = stringResource(Res.string.deliberation_amend),
                    modifier = Modifier.testTag(DeliberationTags.amend(proposition.address.identifier)),
                )
            }
            DeliberationPhase.VOTE -> BallotBox(DeliberationRules.finalUrneId(proposition.address))
            null -> Unit
        }
        if (entry.amendments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = palette.border)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.deliberation_amendments, entry.amendments.size), style = MaterialTheme.typography.labelLarge, color = palette.brand)
            entry.amendments.forEach { amendment -> AmendmentBlock(amendment, voting = phase == DeliberationPhase.AMENDMENTS) }
        }
    }
}

@Composable
private fun AmendmentBlock(amendment: Amendment, voting: Boolean) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag(DeliberationTags.amendment(amendment.address.identifier))) {
        Text(amendment.text, style = MaterialTheme.typography.bodyMedium)
        amendment.justification?.let { Text(stringResource(Res.string.deliberation_justification, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        val subject = DeliberationRules.amendmentUrneId(amendment.address)
        if (voting) BallotBox(subject) else ResultsLine(subject)
    }
}

/** The vote Pour, Contre, Blanc on one box, with the shared control of the ballot boxes (D-110). */
@Composable
private fun BallotBox(subject: String) {
    val voting = LocalVoting.current
    LaunchedEffect(voting, subject) { voting?.shown() }
    val state = voting?.stateFor(subject) ?: VoteUiState(available = false, hasToken = false, closed = false, choice = null, sent = false)
    VoteControl(
        choices = listOf(FOR to stringResource(Res.string.revendication_vote_for), AGAINST to stringResource(Res.string.revendication_vote_against), BLANK to stringResource(Res.string.urne_vote_blank)),
        state = state,
        onChoose = { voting?.choose(subject, it) },
    )
    ResultsLine(subject)
}

/** The one choice « soutien » of the phase of propositions, given or withdrawn on a touch. */
@Composable
private fun SupportButton(subject: String, tag: String) {
    val voting = LocalVoting.current
    LaunchedEffect(voting, subject) { voting?.shown() }
    val state = voting?.stateFor(subject)
    val supported = state?.choice == DeliberationFormats.SUPPORT
    val canVote = state != null && state.available && state.hasToken && !state.closed && voting?.hasAccount == true
    PpfSecondaryButton(
        onClick = { voting?.choose(subject, if (supported) UrneInfo.WITHDRAWN else DeliberationFormats.SUPPORT) },
        text = stringResource(if (supported) Res.string.deliberation_supported else Res.string.deliberation_support),
        enabled = canVote,
        modifier = Modifier.testTag(tag),
    )
    if (!canVote) Text(stringResource(Res.string.deliberation_support_unavailable), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
}

@Composable
private fun ResultsLine(subject: String) {
    val results = LocalVoting.current?.box(subject)?.results ?: return
    fun count(choice: String) = FrenchNumbers.format((results.citizens.counts[choice] ?: 0L).toDouble())
    Text(
        if (results.citizens.hidden) stringResource(Res.string.urne_results_too_few, results.minimum ?: 5) else stringResource(Res.string.urne_results_line, count(FOR), count(AGAINST), count(BLANK)),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The form of a proposition or of an amendment: signed by its author, reviewed by a person before publication. */
@Composable
private fun ContributionForm(composer: DeliberationComposer, signer: EventSigner?) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    val target = composer.target ?: return
    val outcome = composer.outcome
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(DeliberationTags.FORM)) {
        Text(
            stringResource(if (target is ContributionTarget.NewProposition) Res.string.deliberation_form_proposition else Res.string.deliberation_form_amendment),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        if (outcome != null && outcome != SendOutcome.FAILED && outcome != SendOutcome.REFUSED) {
            Text(stringResource(outcomeText(outcome)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(DeliberationTags.FORM_OUTCOME))
            PpfTextButton(onClick = composer::close, text = stringResource(Res.string.deliberation_form_close))
            return@PpfCard
        }
        Text(stringResource(Res.string.deliberation_form_review), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        if (target is ContributionTarget.NewProposition) {
            Text(stringResource(Res.string.deliberation_form_title_label), style = MaterialTheme.typography.labelLarge)
            PpfTextField(composer.title, { composer.title = it.take(DeliberationFormats.MAX_TITLE) }, stringResource(Res.string.deliberation_form_title_hint), Modifier.fillMaxWidth().testTag(DeliberationTags.FORM_TITLE), singleLine = true)
            Spacer(Modifier.height(8.dp))
        }
        Text(stringResource(if (target is ContributionTarget.NewProposition) Res.string.deliberation_form_text_label else Res.string.deliberation_form_wording_label), style = MaterialTheme.typography.labelLarge)
        PpfTextField(composer.text, { composer.text = it.take(DeliberationFormats.MAX_TEXT) }, stringResource(Res.string.deliberation_form_text_hint), Modifier.fillMaxWidth().testTag(DeliberationTags.FORM_TEXT), minLines = 4)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.deliberation_form_justification_label), style = MaterialTheme.typography.labelLarge)
        PpfTextField(composer.justification, { composer.justification = it.take(DeliberationFormats.MAX_JUSTIFICATION) }, stringResource(Res.string.deliberation_form_justification_hint), Modifier.fillMaxWidth().testTag(DeliberationTags.FORM_JUSTIFICATION), minLines = 2)
        Spacer(Modifier.height(8.dp))
        CharterRow(composer.charterAccepted) { composer.charterAccepted = !composer.charterAccepted }
        if (outcome == SendOutcome.REFUSED || outcome == SendOutcome.FAILED) {
            Text(stringResource(outcomeText(outcome)), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk, modifier = Modifier.testTag(DeliberationTags.FORM_OUTCOME))
        }
        if (signer == null) Text(stringResource(Res.string.deliberation_form_account), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PpfPrimaryButton(
                onClick = { if (signer != null) scope.launch { composer.send(signer) } },
                text = stringResource(Res.string.deliberation_form_send),
                enabled = composer.ready && signer != null,
                modifier = Modifier.testTag(DeliberationTags.FORM_SEND),
            )
            PpfTextButton(onClick = composer::close, text = stringResource(Res.string.deliberation_form_cancel))
        }
    }
}

private fun outcomeText(outcome: SendOutcome) = when (outcome) {
    SendOutcome.SENT_FOR_REVIEW -> Res.string.deliberation_outcome_review
    SendOutcome.PUBLISHED -> Res.string.deliberation_outcome_published
    SendOutcome.WAITING_FOR_SERVER -> Res.string.deliberation_outcome_waiting
    SendOutcome.REFUSED -> Res.string.deliberation_outcome_refused
    SendOutcome.FAILED -> Res.string.deliberation_outcome_failed
}

@Composable
private fun CharterRow(accepted: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp).padding(horizontal = 4.dp).testTag(DeliberationTags.FORM_CHARTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp)
                .background(if (accepted) palette.brand else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .border(2.dp, if (accepted) palette.brand else palette.borderStrong, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (accepted) Icon(Lucide.Check, contentDescription = null, tint = palette.onBrand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(Res.string.deliberation_form_charter), style = MaterialTheme.typography.bodyMedium)
    }
}
