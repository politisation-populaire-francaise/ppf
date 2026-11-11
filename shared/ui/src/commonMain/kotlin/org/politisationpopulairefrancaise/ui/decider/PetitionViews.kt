package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.Petition
import org.politisationpopulairefrancaise.core.decider.PetitionPlatform
import org.politisationpopulairefrancaise.core.decider.PetitionProgress
import org.politisationpopulairefrancaise.core.decider.PetitionRules
import org.politisationpopulairefrancaise.core.decider.PlatformRules
import org.politisationpopulairefrancaise.core.decider.SignerRule
import org.politisationpopulairefrancaise.core.decider.Threshold
import org.politisationpopulairefrancaise.core.decider.ThresholdEffect
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.CorrectionsSection
import org.politisationpopulairefrancaise.ui.voir.FollowButton
import org.politisationpopulairefrancaise.ui.voir.LinkRow
import org.politisationpopulairefrancaise.ui.voir.NextStepsSection
import org.politisationpopulairefrancaise.ui.voir.OriginLine
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.SourcesSection
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.hostOf
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl

internal fun signatures(value: Long): String = FrenchNumbers.format(value.toDouble())

/** The card of a petition: its platform, its counter, its progress towards the next threshold. */
@Composable
internal fun PetitionCard(petition: Petition, today: CivilDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val progress = PetitionRules.progress(petition)
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(petition.article.slug))) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PpfBadge(stringResource(petition.platform.shortLabel), BadgeStyle.BRAND)
            if (!petition.isOpen(today)) PpfBadge(stringResource(Res.string.decider_petition_closed), BadgeStyle.NEUTRAL)
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(petition.title, maxLines = 4)
        Spacer(Modifier.height(10.dp))
        progress.count?.let {
            Text(stringResource(Res.string.decider_petition_count, signatures(it.count)), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(progress)
        Spacer(Modifier.height(6.dp))
        Text(nextThresholdLine(progress), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        if (petition.isOpen(today)) {
            petition.daysLeft(today)?.let { days ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Clock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(daysLeftText(days), style = MaterialTheme.typography.bodySmall, color = palette.ink)
                }
            }
        }
    }
}

@Composable
private fun daysLeftText(days: Long): String = when (days) {
    0L -> stringResource(Res.string.decider_petition_last_day)
    1L -> stringResource(Res.string.decider_petition_one_day_left)
    else -> stringResource(Res.string.decider_petition_days_left, days.toInt())
}

@Composable
private fun nextThresholdLine(progress: PetitionProgress): String {
    val next = progress.next
    return when {
        progress.thresholds.isEmpty() -> stringResource(Res.string.decider_petition_threshold_unknown)
        next == null -> stringResource(Res.string.decider_petition_all_reached)
        progress.count == null -> stringResource(if (next.moreThan) Res.string.decider_petition_next_threshold_more_than else Res.string.decider_petition_next_threshold, signatures(next.signatures))
        else -> stringResource(if (next.moreThan) Res.string.decider_petition_remaining_more_than else Res.string.decider_petition_remaining, signatures(progress.remaining ?: 0), signatures(next.signatures))
    }
}

/** A plain bar, announced to screen readers as a progress with its percentage. */
@Composable
private fun ProgressBar(progress: PetitionProgress) {
    val palette = PpfTheme.palette
    val percent = (progress.fraction * 100).toInt()
    val description = stringResource(Res.string.decider_petition_progress_description, percent)
    Box(
        Modifier.fillMaxWidth().height(10.dp).background(palette.surface1, MaterialTheme.shapes.extraSmall)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f)
                stateDescription = description
            }
            .testTag(DeciderTags.PROGRESS),
    ) {
        Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(if (progress.next == null) palette.success else palette.brand, MaterialTheme.shapes.extraSmall))
    }
}

/**
 * The page of a petition: the counter and the next threshold, what the thresholds really do, the
 * way to sign on the official site (never here), the share kit, and what became of it.
 */
@Composable
internal fun PetitionPage(
    petition: Petition,
    bills: List<LegislativeText>,
    today: CivilDate,
    state: DeciderState,
    actions: DeciderActions,
    onOpenBill: (LegislativeText) -> Unit,
    wide: Boolean,
    /** The reminders of « Décider »; null without a database (previews). */
    reminders: DeciderRemindersState? = null,
) {
    val palette = PpfTheme.palette
    val progress = PetitionRules.progress(petition)
    val open = petition.isOpen(today)
    val openUrl = rememberOpenUrl()
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.detail(petition.article.slug))) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PpfBadge(stringResource(petition.platform.label), BadgeStyle.BRAND)
            PpfBadge(stringResource(if (open) Res.string.decider_petition_open else Res.string.decider_petition_closed), if (open) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL)
            petition.reference?.let { PpfBadge(it, BadgeStyle.NEUTRAL) }
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(petition.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        petition.article.summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(16.dp))
        val columns = if (wide) 2 else 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                CounterCard(petition, progress, today)
                Spacer(Modifier.height(12.dp))
                if (open) {
                    PpfPrimaryButton(
                        onClick = { openUrl(petition.officialUrl) },
                        text = stringResource(Res.string.decider_petition_sign, hostOf(petition.officialUrl)),
                        icon = Lucide.ExternalLink,
                        modifier = Modifier.fillMaxWidth().testTag(DeciderTags.SIGN),
                    )
                } else {
                    PpfSecondaryButton(
                        onClick = { openUrl(petition.officialUrl) },
                        text = stringResource(Res.string.decider_petition_see_official, hostOf(petition.officialUrl)),
                        icon = Lucide.ExternalLink,
                        modifier = Modifier.fillMaxWidth().testTag(DeciderTags.SIGN),
                    )
                }
                Spacer(Modifier.height(8.dp))
                InfoBox(Lucide.Lock, Modifier.testTag(DeciderTags.PRIVACY)) {
                    Text(stringResource(signingNote(petition.platform)), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                FollowButton(state.isFollowed(petition.address), petition.title, onToggle = { state.toggleFollow(petition.address) })
            }
            if (columns == 2) {
                Column(Modifier.weight(1f)) { ThresholdLadder(petition.platform, progress, onExplain = { state.open(DeciderDestination.Thresholds(petition.platform)) }) }
            }
        }
        if (columns == 1) {
            Spacer(Modifier.height(16.dp))
            ThresholdLadder(petition.platform, progress, onExplain = { state.open(DeciderDestination.Thresholds(petition.platform)) })
        }
        if (petition.milestones.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Milestones(petition)
        }
        if (bills.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(Res.string.decider_petition_bills))
            bills.forEach { bill ->
                LinkRow(bill.title, Lucide.ScrollText, onClick = { onOpenBill(bill) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (open) {
            Spacer(Modifier.height(16.dp))
            ShareKit(petition, progress)
        }
        if (reminders != null && open && petition.closesOn != null) {
            Spacer(Modifier.height(16.dp))
            PetitionRemindersSection(petition, reminders)
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(petition.article.body)
        SourcesSection(petition.sources)
        CorrectionsSection(petition.corrections)
        Spacer(Modifier.height(8.dp))
        NextStepsSection(petition.nextSteps, onNext = actions.next)
        Spacer(Modifier.height(8.dp))
        OriginLine(petition.article)
    }
}

private fun signingNote(platform: PetitionPlatform): StringResource = when (platform) {
    PetitionPlatform.RIP -> Res.string.decider_petition_privacy_rip
    PetitionPlatform.ICE -> Res.string.decider_petition_privacy_ice
    else -> Res.string.decider_petition_privacy
}

@Composable
private fun CounterCard(petition: Petition, progress: PetitionProgress, today: CivilDate) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth()) {
        val count = progress.count
        if (count == null) {
            Text(stringResource(Res.string.decider_petition_no_count), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        } else {
            Text(stringResource(Res.string.decider_petition_count, signatures(count.count)), style = MaterialTheme.typography.headlineSmall)
            Text(
                count.source?.let { stringResource(Res.string.decider_petition_read_on_source, count.readOn.toFrench(), it) }
                    ?: stringResource(Res.string.decider_petition_read_on, count.readOn.toFrench()),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(progress)
        Spacer(Modifier.height(6.dp))
        Text(nextThresholdLine(progress), style = MaterialTheme.typography.bodyMedium)
        petition.departments?.let { reading ->
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.decider_petition_departments, reading.count, reading.readOn.toFrenchShort()), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        progress.departmentsMissing?.takeIf { it > 0 }?.let {
            Text(stringResource(Res.string.decider_petition_departments_missing, it), style = MaterialTheme.typography.bodySmall, color = palette.warning)
        }
        Spacer(Modifier.height(6.dp))
        val dates = buildList {
            petition.openedOn?.let { add(stringResource(Res.string.decider_petition_opened_on, it.toFrench())) }
            petition.closesOn?.let { add(stringResource(if (petition.isOpen(today)) Res.string.decider_petition_closes_on else Res.string.decider_petition_closed_on, it.toFrench())) }
        }
        if (dates.isNotEmpty()) Text(dates.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        if (petition.isOpen(today)) petition.daysLeft(today)?.let { Text(daysLeftText(it), style = MaterialTheme.typography.labelLarge, color = palette.accentInk) }
    }
}

/** Every threshold of the platform, reached or not, with what it really does. */
@Composable
private fun ThresholdLadder(platform: PetitionPlatform, progress: PetitionProgress, onExplain: () -> Unit) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.section("thresholds"))) {
        SectionHeader(stringResource(Res.string.decider_thresholds_of_petition))
        if (progress.thresholds.isEmpty()) {
            Text(stringResource(Res.string.decider_petition_threshold_unknown), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        progress.thresholds.forEachIndexed { index, threshold ->
            val reached = threshold in progress.reached
            ThresholdLine(threshold, reached, Modifier.testTag(DeciderTags.threshold(index)))
            Spacer(Modifier.height(8.dp))
        }
        PpfTextButton(onClick = onExplain, text = stringResource(Res.string.decider_thresholds_explain, stringResource(platform.shortLabel)), modifier = Modifier.testTag(DeciderTags.THRESHOLDS_OPEN))
    }
}

@Composable
internal fun ThresholdLine(threshold: Threshold, reached: Boolean?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Inset(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (threshold.moreThan) Res.string.decider_threshold_more_than else Res.string.decider_threshold_signatures, signatures(threshold.signatures)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            when (reached) {
                true -> PpfBadge(stringResource(Res.string.decider_threshold_reached), BadgeStyle.SUCCESS)
                false -> PpfBadge(stringResource(Res.string.decider_threshold_not_reached), BadgeStyle.NEUTRAL)
                null -> Unit
            }
        }
        conditionsOf(threshold).takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(threshold.effect.happens), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning, modifier = Modifier.padding(top = 2.dp).size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(threshold.effect.limits), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}

@Composable
private fun conditionsOf(threshold: Threshold): String = buildList {
    threshold.minDepartments?.let { add(stringResource(Res.string.decider_threshold_departments, it)) }
    threshold.minMemberStates?.let { add(stringResource(Res.string.decider_threshold_member_states, it)) }
    threshold.withinMonths?.let { add(stringResource(Res.string.decider_threshold_within_months, it)) }
}.joinToString(" · ")

@Composable
private fun Milestones(petition: Petition) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.section("milestones"))) {
        SectionHeader(stringResource(Res.string.decider_petition_milestones))
        petition.milestones.forEach { milestone ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Lucide.Flag, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(milestone.date.toFrench(), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                    Text(milestone.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** M10: « kits de partage ». A ready text with the official link, copied in one touch. */
@Composable
private fun ShareKit(petition: Petition, progress: PetitionProgress) {
    val palette = PpfTheme.palette
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val platform = stringResource(petition.platform.shortLabel)
    val countLine = progress.count?.let { stringResource(Res.string.decider_share_count, signatures(it.count), it.readOn.toFrenchShort()) }
    val nextLine = progress.next?.let { stringResource(if (it.moreThan) Res.string.decider_share_next_more_than else Res.string.decider_share_next, signatures(it.signatures)) }
    val text = listOfNotNull(
        petition.title,
        stringResource(Res.string.decider_share_platform, platform),
        countLine,
        nextLine,
        stringResource(Res.string.decider_share_link, petition.officialUrl),
    ).joinToString("\n")
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.SHARE)) {
        SectionHeader(stringResource(Res.string.decider_share_title))
        Text(stringResource(Res.string.decider_share_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        Inset(Modifier.fillMaxWidth()) { SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) } }
        PpfSecondaryButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            },
            text = stringResource(if (copied) Res.string.decider_copied else Res.string.decider_share_copy),
            icon = if (copied) Lucide.Check else Lucide.Copy,
            small = true,
            modifier = Modifier.padding(top = 8.dp).testTag(DeciderTags.SHARE_COPY),
        )
    }
}

/**
 * How the official petitions work (M10: « explication claire des seuils et de leurs effets
 * réels »): for each platform, who can sign, for how long, each threshold and what it does or does
 * not guarantee, the legal text and the day it was checked.
 */
@Composable
internal fun ThresholdsPage(first: PetitionPlatform?, columns: Int) {
    val palette = PpfTheme.palette
    val platforms = PetitionPlatform.entries.let { all -> if (first == null) all else listOf(first) + (all - first) }
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.detail("seuils"))) {
        Text(stringResource(Res.string.decider_thresholds_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_thresholds_intro), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        InfoBox(Lucide.Info) { Text(stringResource(Res.string.decider_thresholds_truth), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))
        platforms.chunked(columns.coerceIn(1, 2)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { platform -> PlatformCard(PetitionRules.of(platform), Modifier.weight(1f)) }
                repeat(columns.coerceIn(1, 2) - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlatformCard(rules: PlatformRules, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val openUrl = rememberOpenUrl()
    PpfCard(modifier.testTag(DeciderTags.platform(rules.platform.tagValue))) {
        Text(stringResource(rules.platform.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(stringResource(rules.platform.whatItIs), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(signerText(rules.signers), rules.minimumAge ?: 18), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        rules.collectionMonths?.let { Text(stringResource(Res.string.decider_rules_duration, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        Spacer(Modifier.height(10.dp))
        if (rules.thresholdPerProcedure) {
            Text(stringResource(Res.string.decider_rules_rip_threshold), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(ThresholdEffect.RIP_REFERENDUM.happens), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(ThresholdEffect.RIP_REFERENDUM.limits), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        rules.thresholds.forEachIndexed { index, threshold ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            ThresholdLine(threshold, reached = null)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = palette.border)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.decider_rules_reference, rules.legalReference), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Text(stringResource(Res.string.decider_rules_verified, rules.verifiedOn.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        PpfTextButton(onClick = { openUrl(rules.sourceUrl) }, text = stringResource(Res.string.decider_rules_source, hostOf(rules.sourceUrl)))
    }
}

private fun signerText(rule: SignerRule): StringResource = when (rule) {
    SignerRule.FRANCECONNECT -> Res.string.decider_rules_signers_franceconnect
    SignerRule.REGISTERED_VOTERS -> Res.string.decider_rules_signers_voters
    SignerRule.EU_CITIZENS -> Res.string.decider_rules_signers_eu
}

/** The closed petitions: what they reached and what became of them stays visible. */
@Composable
internal fun ClosedPetitionsPage(petitions: List<Petition>, today: CivilDate, columns: Int, onOpen: (Petition) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.detail("closes"))) {
        Text(stringResource(Res.string.decider_closed_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        if (petitions.isEmpty()) Text(stringResource(Res.string.decider_closed_none), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
        PetitionGrid(petitions, today, columns, onOpen)
    }
}

@Composable
internal fun PetitionGrid(petitions: List<Petition>, today: CivilDate, columns: Int, onOpen: (Petition) -> Unit) {
    org.politisationpopulairefrancaise.ui.voir.Grid(petitions, columns) { petition, modifier ->
        PetitionCard(petition, today, onOpen = { onOpen(petition) }, modifier = modifier)
    }
}

internal val ThresholdEffect.happens: StringResource
    get() = when (this) {
        ThresholdEffect.ASSEMBLEE_HIGHLIGHT -> Res.string.decider_effect_assemblee_highlight
        ThresholdEffect.ASSEMBLEE_DEBATE -> Res.string.decider_effect_assemblee_debate
        ThresholdEffect.SENAT_CONFERENCE -> Res.string.decider_effect_senat_conference
        ThresholdEffect.CESE_OPINION -> Res.string.decider_effect_cese_opinion
        ThresholdEffect.RIP_REFERENDUM -> Res.string.decider_effect_rip_referendum
        ThresholdEffect.ICE_ANSWER -> Res.string.decider_effect_ice_answer
    }

internal val ThresholdEffect.limits: StringResource
    get() = when (this) {
        ThresholdEffect.ASSEMBLEE_HIGHLIGHT -> Res.string.decider_limit_assemblee_highlight
        ThresholdEffect.ASSEMBLEE_DEBATE -> Res.string.decider_limit_assemblee_debate
        ThresholdEffect.SENAT_CONFERENCE -> Res.string.decider_limit_senat_conference
        ThresholdEffect.CESE_OPINION -> Res.string.decider_limit_cese_opinion
        ThresholdEffect.RIP_REFERENDUM -> Res.string.decider_limit_rip_referendum
        ThresholdEffect.ICE_ANSWER -> Res.string.decider_limit_ice_answer
    }

internal val PetitionPlatform.whatItIs: StringResource
    get() = when (this) {
        PetitionPlatform.ASSEMBLEE -> Res.string.decider_platform_assemblee_what
        PetitionPlatform.SENAT -> Res.string.decider_platform_senat_what
        PetitionPlatform.CESE -> Res.string.decider_platform_cese_what
        PetitionPlatform.RIP -> Res.string.decider_platform_rip_what
        PetitionPlatform.ICE -> Res.string.decider_platform_ice_what
    }
