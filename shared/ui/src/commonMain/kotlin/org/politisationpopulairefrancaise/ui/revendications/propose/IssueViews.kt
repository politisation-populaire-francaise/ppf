package org.politisationpopulairefrancaise.ui.revendications.propose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.revendications.checks.DraftField
import org.politisationpopulairefrancaise.core.revendications.checks.IssueKind
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSeverity
import org.politisationpopulairefrancaise.core.revendications.checks.IssueSource
import org.politisationpopulairefrancaise.core.revendications.checks.LegalReference
import org.politisationpopulairefrancaise.core.revendications.checks.LegalStatus
import org.politisationpopulairefrancaise.core.revendications.checks.ModerationAlert
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalIssue
import org.politisationpopulairefrancaise.core.revendications.checks.RelatedDemand
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.issue_all_caps
import org.politisationpopulairefrancaise.ui.resources.issue_already_advanced
import org.politisationpopulairefrancaise.ui.resources.issue_charter
import org.politisationpopulairefrancaise.ui.resources.issue_contradiction
import org.politisationpopulairefrancaise.ui.resources.issue_duplicate
import org.politisationpopulairefrancaise.ui.resources.issue_existing_law
import org.politisationpopulairefrancaise.ui.resources.issue_missing_territory
import org.politisationpopulairefrancaise.ui.resources.issue_missing_theme
import org.politisationpopulairefrancaise.ui.resources.issue_out_of_scope
import org.politisationpopulairefrancaise.ui.resources.issue_pending_bill
import org.politisationpopulairefrancaise.ui.resources.issue_personal_data
import org.politisationpopulairefrancaise.ui.resources.issue_personal_data_ai
import org.politisationpopulairefrancaise.ui.resources.issue_several_ideas
import org.politisationpopulairefrancaise.ui.resources.issue_similar
import org.politisationpopulairefrancaise.ui.resources.issue_targets_person
import org.politisationpopulairefrancaise.ui.resources.issue_too_long_field
import org.politisationpopulairefrancaise.ui.resources.issue_too_long_sentences
import org.politisationpopulairefrancaise.ui.resources.issue_too_long_title
import org.politisationpopulairefrancaise.ui.resources.issue_too_many_themes
import org.politisationpopulairefrancaise.ui.resources.issue_too_short
import org.politisationpopulairefrancaise.ui.resources.issue_unclear
import org.politisationpopulairefrancaise.ui.resources.propose_alert_orange
import org.politisationpopulairefrancaise.ui.resources.propose_alert_red
import org.politisationpopulairefrancaise.ui.resources.propose_field_asks
import org.politisationpopulairefrancaise.ui.resources.propose_field_existing
import org.politisationpopulairefrancaise.ui.resources.propose_field_summary
import org.politisationpopulairefrancaise.ui.resources.propose_field_territory
import org.politisationpopulairefrancaise.ui.resources.propose_field_why
import org.politisationpopulairefrancaise.ui.resources.propose_reference_status_a_venir
import org.politisationpopulairefrancaise.ui.resources.propose_reference_status_abroge
import org.politisationpopulairefrancaise.ui.resources.propose_reference_status_en_discussion
import org.politisationpopulairefrancaise.ui.resources.propose_reference_status_en_vigueur
import org.politisationpopulairefrancaise.ui.resources.propose_reference_unverified
import org.politisationpopulairefrancaise.ui.resources.propose_reference_verified
import org.politisationpopulairefrancaise.ui.resources.propose_related_open
import org.politisationpopulairefrancaise.ui.resources.propose_severity_blocking
import org.politisationpopulairefrancaise.ui.resources.propose_severity_info
import org.politisationpopulairefrancaise.ui.resources.propose_severity_warning
import org.politisationpopulairefrancaise.ui.resources.propose_source_ai
import org.politisationpopulairefrancaise.ui.resources.propose_source_automatic
import org.politisationpopulairefrancaise.ui.revendications.StatusBadge
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object IssueTags {
    /** Followed by the serial name of the kind ("duplicate", "personal_data"...). */
    const val ISSUE = "issue-"

    /** Followed by the content address of the related demand. */
    const val RELATED = "issue-related-"
}

/** The issues of a report, most serious first, each with what it refers to and who raised it. */
@Composable
fun IssueList(issues: List<ProposalIssue>, onOpenDemand: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        issues.forEach { IssueRow(it, onOpenDemand) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueRow(issue: ProposalIssue, onOpenDemand: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val (icon, color, label) = severityStyle(issue.severity)
    Inset(modifier.fillMaxWidth().testTag(IssueTags.ISSUE + issue.kind.name.lowercase())) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color)
                    issue.alert?.let { alert ->
                        PpfBadge(stringResource(if (alert == ModerationAlert.RED) Res.string.propose_alert_red else Res.string.propose_alert_orange), BadgeStyle.EXAMPLE)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(issueMessage(issue), style = MaterialTheme.typography.bodyMedium)
                issue.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
                issue.related.forEach { related ->
                    Spacer(Modifier.height(4.dp))
                    RelatedRow(related, onOpenDemand)
                }
                issue.references.forEach { reference ->
                    Spacer(Modifier.height(4.dp))
                    ReferenceRow(reference)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(if (issue.source == IssueSource.AI) Res.string.propose_source_ai else Res.string.propose_source_automatic),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                )
            }
        }
    }
}

@Composable
private fun RelatedRow(related: RelatedDemand, onOpenDemand: (String) -> Unit) {
    val palette = PpfTheme.palette
    val description = stringResource(Res.string.propose_related_open, related.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button) { onOpenDemand(related.address) }
            .heightIn(min = 44.dp)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description }
            .testTag(IssueTags.RELATED + related.address),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.ScrollText, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(related.title, style = MaterialTheme.typography.titleSmall, color = palette.brand)
            related.status?.let {
                Spacer(Modifier.height(4.dp))
                StatusBadge(it)
            }
        }
        Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceRow(reference: LegalReference) {
    val palette = PpfTheme.palette
    val uriHandler = LocalUriHandler.current
    val url = reference.url?.takeIf(LegalReference::isOfficialUrl)
    val rowModifier = if (url != null) {
        Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(role = Role.Button) { runCatching { uriHandler.openUri(url) } }.heightIn(min = 44.dp)
    } else {
        Modifier
    }
    Row(rowModifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Lucide.Landmark, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(reference.title, style = MaterialTheme.typography.titleSmall, color = if (url != null) palette.brand else palette.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                legalStatusLabel(reference.status)?.let { PpfBadge(stringResource(it), BadgeStyle.NEUTRAL) }
                PpfBadge(
                    stringResource(if (reference.verified) Res.string.propose_reference_verified else Res.string.propose_reference_unverified),
                    if (reference.verified) BadgeStyle.SUCCESS else BadgeStyle.EXAMPLE,
                )
            }
            reference.excerpt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

private fun legalStatusLabel(status: LegalStatus): StringResource? = when (status) {
    LegalStatus.EN_VIGUEUR -> Res.string.propose_reference_status_en_vigueur
    LegalStatus.A_VENIR -> Res.string.propose_reference_status_a_venir
    LegalStatus.ABROGE -> Res.string.propose_reference_status_abroge
    LegalStatus.EN_DISCUSSION -> Res.string.propose_reference_status_en_discussion
    LegalStatus.INCONNU -> null
}

@Composable
private fun severityStyle(severity: IssueSeverity): Triple<ImageVector, Color, StringResource> {
    val palette = PpfTheme.palette
    return when (severity) {
        IssueSeverity.BLOCKING -> Triple(Lucide.TriangleExclamationPoint, palette.accentInk, Res.string.propose_severity_blocking)
        IssueSeverity.WARNING -> Triple(Lucide.Info, palette.warning, Res.string.propose_severity_warning)
        IssueSeverity.INFO -> Triple(Lucide.Lightbulb, palette.brand, Res.string.propose_severity_info)
    }
}

/** The sentence shown for an issue, in the words of the interface (the model's own words go in [ProposalIssue.detail]). */
@Composable
fun issueMessage(issue: ProposalIssue): String = when (issue.kind) {
    IssueKind.DUPLICATE -> stringResource(Res.string.issue_duplicate)
    IssueKind.SIMILAR -> stringResource(Res.string.issue_similar)
    IssueKind.ALREADY_ADVANCED -> stringResource(Res.string.issue_already_advanced)
    IssueKind.EXISTING_LAW -> stringResource(Res.string.issue_existing_law)
    IssueKind.PENDING_BILL -> stringResource(Res.string.issue_pending_bill)
    IssueKind.CONTRADICTION -> stringResource(Res.string.issue_contradiction)
    IssueKind.SEVERAL_IDEAS -> stringResource(Res.string.issue_several_ideas)
    IssueKind.TOO_SHORT -> stringResource(Res.string.issue_too_short)
    IssueKind.TOO_LONG -> when {
        issue.field == DraftField.TITLE && issue.severity == IssueSeverity.BLOCKING -> stringResource(Res.string.issue_too_long_title)
        issue.field == DraftField.TITLE -> stringResource(Res.string.issue_too_long_sentences)
        else -> stringResource(Res.string.issue_too_long_field, fieldLabel(issue.field))
    }
    IssueKind.ALL_CAPS -> stringResource(Res.string.issue_all_caps)
    IssueKind.MISSING_THEME -> stringResource(Res.string.issue_missing_theme)
    IssueKind.TOO_MANY_THEMES -> stringResource(Res.string.issue_too_many_themes)
    IssueKind.MISSING_TERRITORY -> stringResource(Res.string.issue_missing_territory)
    IssueKind.PERSONAL_DATA -> stringResource(if (issue.source == IssueSource.AI) Res.string.issue_personal_data_ai else Res.string.issue_personal_data)
    IssueKind.TARGETS_PERSON -> stringResource(Res.string.issue_targets_person)
    IssueKind.CHARTER -> stringResource(Res.string.issue_charter)
    IssueKind.OUT_OF_SCOPE -> stringResource(Res.string.issue_out_of_scope)
    IssueKind.UNCLEAR -> stringResource(Res.string.issue_unclear)
}

@Composable
private fun fieldLabel(field: DraftField?): String = when (field) {
    DraftField.SUMMARY -> stringResource(Res.string.propose_field_summary)
    DraftField.WHY -> stringResource(Res.string.propose_field_why)
    DraftField.ASKS -> stringResource(Res.string.propose_field_asks)
    DraftField.EXISTING -> stringResource(Res.string.propose_field_existing)
    DraftField.TERRITORY -> stringResource(Res.string.propose_field_territory)
    else -> ""
}.lowercase()

