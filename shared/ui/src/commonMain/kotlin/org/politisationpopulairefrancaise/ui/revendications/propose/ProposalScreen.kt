package org.politisationpopulairefrancaise.ui.revendications.propose

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.resources.propose_details_hide
import org.politisationpopulairefrancaise.ui.resources.propose_details_show
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.revendications.checks.CheckKind
import org.politisationpopulairefrancaise.core.revendications.checks.CheckState
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.propose_blocked_charter
import org.politisationpopulairefrancaise.ui.resources.propose_blocked_issues
import org.politisationpopulairefrancaise.ui.resources.propose_charter_accept
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed
import org.politisationpopulairefrancaise.ui.resources.propose_charter_allowed_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden
import org.politisationpopulairefrancaise.ui.resources.propose_charter_forbidden_text
import org.politisationpopulairefrancaise.ui.resources.propose_charter_title
import org.politisationpopulairefrancaise.ui.resources.propose_check_after_sending
import org.politisationpopulairefrancaise.ui.resources.propose_check_rules_done
import org.politisationpopulairefrancaise.ui.resources.propose_check_similarity_done
import org.politisationpopulairefrancaise.ui.resources.propose_checks_empty
import org.politisationpopulairefrancaise.ui.resources.propose_checks_title
import org.politisationpopulairefrancaise.ui.resources.propose_checks_waiting
import org.politisationpopulairefrancaise.ui.resources.propose_close
import org.politisationpopulairefrancaise.ui.resources.propose_counter
import org.politisationpopulairefrancaise.ui.resources.propose_create_account
import org.politisationpopulairefrancaise.ui.resources.propose_field_asks
import org.politisationpopulairefrancaise.ui.resources.propose_field_asks_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_field_existing
import org.politisationpopulairefrancaise.ui.resources.propose_field_existing_help
import org.politisationpopulairefrancaise.ui.resources.propose_field_existing_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_field_summary
import org.politisationpopulairefrancaise.ui.resources.propose_field_summary_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_field_territory
import org.politisationpopulairefrancaise.ui.resources.propose_field_territory_help
import org.politisationpopulairefrancaise.ui.resources.propose_field_territory_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_field_themes
import org.politisationpopulairefrancaise.ui.resources.propose_field_title
import org.politisationpopulairefrancaise.ui.resources.propose_field_title_help
import org.politisationpopulairefrancaise.ui.resources.propose_field_title_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_field_why
import org.politisationpopulairefrancaise.ui.resources.propose_field_why_placeholder
import org.politisationpopulairefrancaise.ui.resources.propose_intro
import org.politisationpopulairefrancaise.ui.resources.propose_need_account
import org.politisationpopulairefrancaise.ui.resources.propose_result_again
import org.politisationpopulairefrancaise.ui.resources.propose_result_back
import org.politisationpopulairefrancaise.ui.resources.propose_result_failed_text
import org.politisationpopulairefrancaise.ui.resources.propose_result_failed_title
import org.politisationpopulairefrancaise.ui.resources.propose_result_published_text
import org.politisationpopulairefrancaise.ui.resources.propose_result_published_title
import org.politisationpopulairefrancaise.ui.resources.propose_result_refused_text
import org.politisationpopulairefrancaise.ui.resources.propose_result_refused_title
import org.politisationpopulairefrancaise.ui.resources.propose_result_review_text
import org.politisationpopulairefrancaise.ui.resources.propose_result_review_title
import org.politisationpopulairefrancaise.ui.resources.propose_result_waiting_text
import org.politisationpopulairefrancaise.ui.resources.propose_result_waiting_title
import org.politisationpopulairefrancaise.ui.resources.propose_send
import org.politisationpopulairefrancaise.ui.resources.propose_sending
import org.politisationpopulairefrancaise.ui.resources.propose_similar_title
import org.politisationpopulairefrancaise.ui.resources.propose_title
import org.politisationpopulairefrancaise.ui.revendications.themeLabel
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ProposalTags {
    /** « Ajouter des précisions (facultatif) »: unfolds the summary, why, what is asked and what exists. */
    const val DETAILS = "proposal-details"
    const val SCREEN = "proposal-screen"
    const val CLOSE = "proposal-close"
    const val TITLE = "proposal-title"
    const val SUMMARY = "proposal-summary"
    const val WHY = "proposal-why"
    const val ASKS = "proposal-asks"
    const val EXISTING = "proposal-existing"
    const val TERRITORY = "proposal-territory"
    const val THEME = "proposal-theme-" // followed by the theme id
    const val SIMILAR = "proposal-similar"
    const val CHECKS = "proposal-checks"
    const val CHARTER = "proposal-charter"
    const val SEND = "proposal-send"
    const val CREATE_ACCOUNT = "proposal-create-account"
    const val RESULT = "proposal-result"
    const val RESULT_BACK = "proposal-result-back"
    const val RESULT_AGAIN = "proposal-result-again"
}

/** The issues that point at existing demands: shown right under the demand, as the author types. */
private val SIMILARITY_KINDS = setOf(IssueKind.DUPLICATE, IssueKind.SIMILAR, IssueKind.ALREADY_ADVANCED)

/**
 * The guided form of DE-18: the demand in one or two sentences, why, what is asked, what already
 * exists, one to three themes, the territory, the charter. The automatic checks run as the author
 * types: the demands that say the same thing appear under the demand itself, with the way to read
 * them; the rest of the report sits beside the form on wide windows, before the button on phones.
 * Writing needs no account; sending does (the demand is signed by its author).
 */
@Composable
fun ProposalScreen(
    composer: ProposalComposer,
    existing: List<DemandText>,
    signer: EventSigner?,
    windowClass: WindowClass,
    onClose: () -> Unit,
    onOpenDemand: (address: String) -> Unit,
    onCreateAccount: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val draft = composer.draft
    val report = remember(draft, existing) { composer.report(existing) }
    val scope = rememberCoroutineScope()
    val send: () -> Unit = {
        if (signer != null) scope.launch { composer.send(signer, existing); onSent() }
    }

    // The caller's modifier may carry its own test tag (the first one wins): the screen's goes on an inner box.
    Box(modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.surface1).testTag(ProposalTags.SCREEN)) {
        val sideBySide = maxWidth >= 1000.dp
        val outcome = composer.outcome
        val padding = if (compact) 16.dp else 24.dp
        if (outcome != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                ResultCard(
                    outcome = outcome,
                    onBack = {
                        composer.reset()
                        onClose()
                    },
                    onAgain = composer::reset,
                    onCorrect = composer::dismissOutcome,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
            return@BoxWithConstraints
        }
        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.widthIn(max = 680.dp).weight(1f, fill = false).fillMaxHeight().verticalScroll(rememberScrollState()).padding(padding)) {
                    Form(composer, report, signer, onClose, onOpenDemand, onCreateAccount, send, withChecks = false)
                }
                Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = padding, end = padding, bottom = padding)) {
                    ChecksPanel(report, draft.title.isBlank(), onOpenDemand, includeSimilarity = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Form(composer, report, signer, onClose, onOpenDemand, onCreateAccount, send, withChecks = true)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Form(
    composer: ProposalComposer,
    report: ProposalReport,
    signer: EventSigner?,
    onClose: () -> Unit,
    onOpenDemand: (String) -> Unit,
    onCreateAccount: () -> Unit,
    onSend: () -> Unit,
    withChecks: Boolean,
) {
    val palette = PpfTheme.palette
    val draft = composer.draft
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.propose_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        PpfIconButton(Lucide.X, stringResource(Res.string.propose_close), onClose, Modifier.testTag(ProposalTags.CLOSE), tint = palette.ink)
    }
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.ScrollText) { Text(stringResource(Res.string.propose_intro), style = MaterialTheme.typography.bodyMedium) }

    Field(
        label = stringResource(Res.string.propose_field_title),
        help = stringResource(Res.string.propose_field_title_help),
        counter = stringResource(Res.string.propose_counter, draft.title.trim().length, DraftRules.TITLE_MAX),
    ) {
        PpfTextField(
            value = draft.title,
            onValueChange = { composer.draft = draft.copy(title = it) },
            placeholder = stringResource(Res.string.propose_field_title_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(ProposalTags.TITLE),
            minLines = 2,
        )
    }
    val similarity = report.issues.filter { it.kind in SIMILARITY_KINDS }
    if (withChecks && similarity.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.propose_similar_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        IssueList(similarity, onOpenDemand, Modifier.testTag(ProposalTags.SIMILAR))
    }

    Field(stringResource(Res.string.propose_field_themes)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RevendicationThemes.ALL.forEach { theme ->
                PpfSelectableChip(
                    label = themeLabel(theme.id),
                    selected = theme.id in draft.themes,
                    onClick = { composer.toggleTheme(theme.id) },
                    modifier = Modifier.testTag(ProposalTags.THEME + theme.id),
                )
            }
        }
    }
    Field(stringResource(Res.string.propose_field_territory), help = stringResource(Res.string.propose_field_territory_help)) {
        PpfTextField(
            value = draft.territory,
            onValueChange = { composer.draft = draft.copy(territory = it) },
            placeholder = stringResource(Res.string.propose_field_territory_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(ProposalTags.TERRITORY),
            singleLine = true,
        )
    }

    // MVP 1: a short path. The demand, its themes and its territory are enough; the rest is optional, folded.
    var detailsOpen by rememberSaveable { mutableStateOf(draft.summary.isNotBlank() || draft.why.isNotBlank() || draft.asks.isNotBlank() || draft.existing.isNotBlank()) }
    Spacer(Modifier.height(12.dp))
    PpfTextButton(
        onClick = { detailsOpen = !detailsOpen },
        text = stringResource(if (detailsOpen) Res.string.propose_details_hide else Res.string.propose_details_show),
        modifier = Modifier.testTag(ProposalTags.DETAILS),
    )
    if (detailsOpen) {
        Field(stringResource(Res.string.propose_field_summary)) {
            PpfTextField(draft.summary, { composer.draft = draft.copy(summary = it) }, stringResource(Res.string.propose_field_summary_placeholder), Modifier.fillMaxWidth().testTag(ProposalTags.SUMMARY))
        }
        Field(stringResource(Res.string.propose_field_why)) {
            PpfTextField(draft.why, { composer.draft = draft.copy(why = it) }, stringResource(Res.string.propose_field_why_placeholder), Modifier.fillMaxWidth().testTag(ProposalTags.WHY), minLines = 3)
        }
        Field(stringResource(Res.string.propose_field_asks)) {
            PpfTextField(draft.asks, { composer.draft = draft.copy(asks = it) }, stringResource(Res.string.propose_field_asks_placeholder), Modifier.fillMaxWidth().testTag(ProposalTags.ASKS), minLines = 3)
        }
        Field(stringResource(Res.string.propose_field_existing), help = stringResource(Res.string.propose_field_existing_help)) {
            PpfTextField(draft.existing, { composer.draft = draft.copy(existing = it) }, stringResource(Res.string.propose_field_existing_placeholder), Modifier.fillMaxWidth().testTag(ProposalTags.EXISTING), minLines = 2)
        }
    }

    if (withChecks) {
        Spacer(Modifier.height(20.dp))
        ChecksPanel(report, draft.title.isBlank(), onOpenDemand, includeSimilarity = false)
    }

    Spacer(Modifier.height(20.dp))
    Charter(composer.charterAccepted, onToggle = { composer.charterAccepted = !composer.charterAccepted })

    Spacer(Modifier.height(20.dp))
    if (signer == null) {
        InfoBox(Lucide.KeyRound) {
            Text(stringResource(Res.string.propose_need_account), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            PpfPrimaryButton(onCreateAccount, stringResource(Res.string.propose_create_account), Modifier.testTag(ProposalTags.CREATE_ACCOUNT))
        }
    } else {
        // An account declared compromised publishes nothing more (S-146): the server refuses it anyway.
        PublishBlockedNotice(Modifier.padding(bottom = 8.dp))
        PpfPrimaryButton(
            onClick = onSend,
            text = stringResource(if (composer.sending) Res.string.propose_sending else Res.string.propose_send),
            enabled = LocalCanPublish.current && composer.ready(report),
            icon = Lucide.Send,
            modifier = Modifier.testTag(ProposalTags.SEND),
        )
        val reason = when {
            !report.canSubmit -> Res.string.propose_blocked_issues
            !composer.charterAccepted -> Res.string.propose_blocked_charter
            else -> null
        }
        if (reason != null && draft.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** A labelled block of the form: bold label, optional help line and counter, then the control. */
@Composable
private fun Field(label: String, help: String? = null, counter: String? = null, content: @Composable () -> Unit) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        if (counter != null) Text(counter, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
    if (help != null) {
        Spacer(Modifier.height(4.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun ChecksPanel(report: ProposalReport, waiting: Boolean, onOpenDemand: (String) -> Unit, includeSimilarity: Boolean) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(ProposalTags.CHECKS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.propose_checks_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(10.dp))
        if (waiting) {
            Text(stringResource(Res.string.propose_checks_waiting), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@PpfCard
        }
        val shown: List<ProposalIssue> = if (includeSimilarity) report.issues else report.issues.filter { it.kind !in SIMILARITY_KINDS }
        if (shown.isEmpty()) {
            Text(stringResource(Res.string.propose_checks_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            IssueList(shown, onOpenDemand)
        }
        Spacer(Modifier.height(12.dp))
        CheckLine(stringResource(Res.string.propose_check_rules_done), done = true)
        val compared = report.checks.firstOrNull { it.kind == CheckKind.SIMILARITY }?.compared ?: 0
        CheckLine(stringResource(Res.string.propose_check_similarity_done, compared), done = true)
        if (report.state(CheckKind.AI) == CheckState.AFTER_SENDING || report.state(CheckKind.LEGAL) == CheckState.AFTER_SENDING) {
            CheckLine(stringResource(Res.string.propose_check_after_sending), done = false)
        }
    }
}

@Composable
private fun CheckLine(text: String, done: Boolean) {
    val palette = PpfTheme.palette
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (done) Lucide.CircleCheck else Lucide.Clock,
            contentDescription = null,
            tint = if (done) palette.success else palette.inkSubtle,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

@Composable
private fun Charter(accepted: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    Text(stringResource(Res.string.propose_charter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.LEGAL, title = stringResource(Res.string.propose_charter_allowed)) {
        Text(stringResource(Res.string.propose_charter_allowed_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
    Callout(CalloutType.ILLEGAL, title = stringResource(Res.string.propose_charter_forbidden)) {
        Text(stringResource(Res.string.propose_charter_forbidden_text), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp)
            .testTag(ProposalTags.CHARTER),
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
        Text(stringResource(Res.string.propose_charter_accept), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultCard(outcome: SendOutcome, onBack: () -> Unit, onAgain: () -> Unit, onCorrect: () -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    val (title, text) = when (outcome) {
        SendOutcome.SENT_FOR_REVIEW -> Res.string.propose_result_review_title to Res.string.propose_result_review_text
        SendOutcome.PUBLISHED -> Res.string.propose_result_published_title to Res.string.propose_result_published_text
        SendOutcome.WAITING_FOR_SERVER -> Res.string.propose_result_waiting_title to Res.string.propose_result_waiting_text
        SendOutcome.REFUSED -> Res.string.propose_result_refused_title to Res.string.propose_result_refused_text
        SendOutcome.FAILED -> Res.string.propose_result_failed_title to Res.string.propose_result_failed_text
    }
    val success = outcome == SendOutcome.SENT_FOR_REVIEW || outcome == SendOutcome.PUBLISHED || outcome == SendOutcome.WAITING_FOR_SERVER
    PpfCard(modifier.fillMaxWidth().testTag(ProposalTags.RESULT)) {
        Box(
            Modifier.size(48.dp).background(if (success) palette.successTint else palette.accentTint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (success) Lucide.CircleCheck else Lucide.TriangleExclamationPoint,
                contentDescription = null,
                tint = if (success) palette.success else palette.accentInk,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(16.dp))
        FlowRowButtons {
            if (success) {
                PpfPrimaryButton(onBack, stringResource(Res.string.propose_result_back), Modifier.testTag(ProposalTags.RESULT_BACK))
                PpfSecondaryButton(onAgain, stringResource(Res.string.propose_result_again), Modifier.testTag(ProposalTags.RESULT_AGAIN))
            } else {
                PpfPrimaryButton(onCorrect, stringResource(Res.string.propose_title), Modifier.testTag(ProposalTags.RESULT_AGAIN))
                PpfSecondaryButton(onBack, stringResource(Res.string.propose_result_back), Modifier.testTag(ProposalTags.RESULT_BACK))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
