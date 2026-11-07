package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.BoardCell
import org.politisationpopulairefrancaise.core.decider.CandidateRow
import org.politisationpopulairefrancaise.core.decider.CellStatus
import org.politisationpopulairefrancaise.core.decider.Pacte
import org.politisationpopulairefrancaise.core.decider.PacteBoard
import org.politisationpopulairefrancaise.core.decider.PacteStatus
import org.politisationpopulairefrancaise.core.decider.SetAsideReason
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.ArchivesSection
import org.politisationpopulairefrancaise.ui.voir.CorrectionsSection
import org.politisationpopulairefrancaise.ui.voir.FollowButton
import org.politisationpopulairefrancaise.ui.voir.NextStepsSection
import org.politisationpopulairefrancaise.ui.voir.OriginLine
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.SourcesSection
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.initials
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** The card of a pact on the home of "Décider": where it stands, how many answered. */
@Composable
internal fun PacteCard(board: PacteBoard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val pacte = board.pacte
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(DeciderTags.card(pacte.article.slug))) {
        VoirTitle(pacte.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Text(pacte.election.label, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfBadge(stringResource(pacte.status.label), pacte.status.badge)
            PpfBadge(stringResource(Res.string.decider_pacte_measures_count, pacte.measures.size), BadgeStyle.NEUTRAL)
        }
        Spacer(Modifier.height(10.dp))
        Text(pacteProgressLine(board), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.decider_pacte_open), style = MaterialTheme.typography.labelLarge, color = palette.brand)
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun pacteProgressLine(board: PacteBoard): String {
    val pacte = board.pacte
    return when {
        pacte.status == PacteStatus.PROPOSED -> stringResource(Res.string.decider_pacte_proposed_line)
        pacte.status == PacteStatus.CLOSED -> stringResource(Res.string.decider_pacte_answered, board.answeredCandidates, board.rows.size)
        board.deadlinePassed -> stringResource(Res.string.decider_pacte_answered_final, board.answeredCandidates, board.rows.size)
        else -> stringResource(Res.string.decider_pacte_answered_until, board.answeredCandidates, board.rows.size, pacte.deadline?.toFrench().orEmpty())
    }
}

/**
 * The page of a pact (M13.5): the method first (the same text, the same day, the same deadline
 * for every candidate), the measures, then the public table by candidate and by measure, the
 * archives of the answers, and what each one did after the election.
 */
@Composable
internal fun PactePage(
    board: PacteBoard,
    state: DeciderState,
    actions: DeciderActions,
    wide: Boolean,
) {
    val pacte = board.pacte
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.detail(pacte.article.slug))) {
        VoirTitle(pacte.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        Spacer(Modifier.height(4.dp))
        Text(pacte.election.label, style = MaterialTheme.typography.titleMedium, color = palette.inkMuted)
        pacte.election.firstRound?.let {
            Text(stringResource(Res.string.decider_pacte_first_round, it.toFrench()), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        }
        Spacer(Modifier.height(12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            PpfBadge(stringResource(pacte.status.label), pacte.status.badge)
            FollowButton(state.isFollowed(pacte.address), pacte.title, onToggle = { state.toggleFollow(pacte.address) })
        }
        Spacer(Modifier.height(16.dp))
        InfoBox(Lucide.ShieldCheck, Modifier.testTag(DeciderTags.PACTE_METHOD)) {
            Text(stringResource(Res.string.decider_pacte_method_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.decider_pacte_method_text), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        PacteDates(pacte, board)
        Spacer(Modifier.height(16.dp))
        MeasuresSection(pacte)
        Spacer(Modifier.height(20.dp))
        if (board.isOpenForAnswers) {
            SectionHeader(stringResource(Res.string.decider_pacte_table_title))
            Text(stringResource(Res.string.decider_pacte_table_order), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            Spacer(Modifier.height(8.dp))
            StatusLegend()
            Spacer(Modifier.height(12.dp))
            if (wide) BoardTable(board, actions) else BoardByCandidate(board, actions)
            Spacer(Modifier.height(16.dp))
            SummaryByMeasure(board)
            SetAsideSection(board)
            Spacer(Modifier.height(16.dp))
            AnswersArchives(board)
        } else {
            InfoBox(Lucide.Vote, Modifier.testTag(DeciderTags.PACTE_PROPOSED)) {
                Text(stringResource(Res.string.decider_pacte_proposed_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_pacte_proposed_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Fingerprint(pacte)
        Spacer(Modifier.height(16.dp))
        MarkdownText(pacte.article.body)
        ArchivesSection(pacte.archives)
        SourcesSection(pacte.sources)
        CorrectionsSection(pacte.corrections)
        Spacer(Modifier.height(8.dp))
        NextStepsSection(pacte.nextSteps, onNext = actions.next)
        Spacer(Modifier.height(8.dp))
        OriginLine(pacte.article)
    }
}

@Composable
private fun PacteDates(pacte: Pacte, board: PacteBoard) {
    val sent = pacte.sentOn ?: return
    val deadline = pacte.deadline ?: return
    Inset(Modifier.fillMaxWidth().testTag(DeciderTags.PACTE_DATES)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Send, contentDescription = null, tint = PpfTheme.palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.decider_pacte_sent_on, sent.toFrench(), board.rows.size), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Clock, contentDescription = null, tint = PpfTheme.palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (board.deadlinePassed) Res.string.decider_pacte_deadline_passed else Res.string.decider_pacte_deadline, deadline.toFrench()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MeasuresSection(pacte: Pacte) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.section("measures"))) {
        SectionHeader(stringResource(Res.string.decider_pacte_measures_title))
        pacte.measures.forEachIndexed { index, measure ->
            PpfCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(DeciderTags.measure(measure.id))) {
                Row(verticalAlignment = Alignment.Top) {
                    MeasureNumber(index + 1)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(measure.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                        Text(measure.commitment, style = MaterialTheme.typography.bodyMedium)
                        measure.textToChange?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(Res.string.decider_pacte_text_to_change, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureNumber(number: Int) {
    val palette = PpfTheme.palette
    Box(Modifier.size(32.dp).background(palette.brandTint, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = palette.brand)
    }
}

/** The four states, always with their words: the colours only repeat them (WCAG, 1.4.1). */
@Composable
private fun StatusLegend() {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.testTag(DeciderTags.LEGEND), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CellStatus.entries.forEach { StatusChip(it) }
    }
}

@Composable
internal fun StatusChip(status: CellStatus, modifier: Modifier = Modifier, late: Boolean = false) {
    val (icon, style) = when (status) {
        CellStatus.SIGNED -> Lucide.CircleCheck to BadgeStyle.SUCCESS
        CellStatus.REFUSED -> Lucide.X to BadgeStyle.ACCENT
        CellStatus.AWAITING -> Lucide.Clock to BadgeStyle.NEUTRAL
        CellStatus.NO_ANSWER -> Lucide.Ellipsis to BadgeStyle.WARNING
    }
    val palette = PpfTheme.palette
    val tint = when (style) {
        BadgeStyle.SUCCESS -> palette.success
        BadgeStyle.ACCENT -> palette.accentInk
        BadgeStyle.WARNING -> palette.warning
        else -> palette.inkMuted
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(status.label) + if (late) " " + stringResource(Res.string.decider_pacte_late_short) else "", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Phones and narrow windows: one card per candidate, every measure on its own line. */
@Composable
private fun BoardByCandidate(board: PacteBoard, actions: DeciderActions) {
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.BOARD), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        board.rows.forEach { row ->
            PpfCard(Modifier.fillMaxWidth().testTag(DeciderTags.row(row.candidate.identifier))) {
                CandidateHeader(row, actions)
                Spacer(Modifier.height(8.dp))
                row.cells.forEachIndexed { index, cell ->
                    if (index > 0) HorizontalDivider(color = PpfTheme.palette.border, modifier = Modifier.padding(vertical = 6.dp))
                    CellLine(index + 1, cell, row.late, actions)
                }
            }
        }
    }
}

@Composable
private fun CandidateHeader(row: CandidateRow, actions: DeciderActions) {
    val palette = PpfTheme.palette
    val describe = stringResource(Res.string.decider_pacte_open_candidate, row.name.withoutExample())
    Surface(
        onClick = { actions.openPerson(row.candidate) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = describe },
        shape = MaterialTheme.shapes.small,
        color = palette.surface2,
        contentColor = palette.ink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(row.candidate.identifier, initials(row.name.withoutExample()), size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name.withoutExample(), style = MaterialTheme.typography.titleSmall)
                Text(answerLine(row), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun answerLine(row: CandidateRow): String {
    val engagement = row.engagement ?: return stringResource(Res.string.decider_pacte_no_answer_received)
    val received = stringResource(Res.string.decider_pacte_received_on, engagement.receivedOn.toFrench())
    val late = if (row.late) " · " + stringResource(Res.string.decider_pacte_late) else ""
    return stringResource(Res.string.decider_pacte_row_counts, row.signed, row.refused, row.unanswered) + " · " + received + late
}

@Composable
private fun CellLine(number: Int, cell: BoardCell, late: Boolean, actions: DeciderActions) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.cell(cell.measure.id))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number. ${cell.measure.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatusChip(cell.status, late = late && (cell.status == CellStatus.SIGNED || cell.status == CellStatus.REFUSED))
        }
        cell.comment?.let {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(Res.string.decider_pacte_comment, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        FollowUps(cell, actions)
    }
}

@Composable
private fun FollowUps(cell: BoardCell, actions: DeciderActions) {
    if (cell.followUps.isEmpty()) return
    val palette = PpfTheme.palette
    cell.followUps.forEach { followUp ->
        PpfTextButton(
            onClick = { actions.openFact(followUp.factAddress) },
            text = stringResource(Res.string.decider_pacte_follow_up, followUp.date.toFrenchShort()),
            color = palette.brand,
        )
    }
}

/**
 * Wide windows: a real table, one line per candidate and one column per measure, scrolled
 * sideways when the measures do not fit. Every cell keeps its words.
 */
@Composable
private fun BoardTable(board: PacteBoard, actions: DeciderActions) {
    val palette = PpfTheme.palette
    val measures = board.pacte.measures
    val cellWidth = 132.dp
    val nameWidth = 220.dp
    Surface(Modifier.fillMaxWidth().testTag(DeciderTags.BOARD), shape = MaterialTheme.shapes.medium, color = palette.surface2, contentColor = palette.ink, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.height(IntrinsicSize.Max).background(palette.surface1).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.decider_pacte_candidate_column), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(nameWidth).padding(horizontal = 12.dp))
                measures.forEachIndexed { index, measure ->
                    Column(Modifier.width(cellWidth).padding(horizontal = 8.dp)) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = palette.brand)
                        Text(measure.title, style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            board.rows.forEach { row ->
                HorizontalDivider(color = palette.border)
                Row(Modifier.height(IntrinsicSize.Max).testTag(DeciderTags.row(row.candidate.identifier)), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { actions.openPerson(row.candidate) },
                        modifier = Modifier.width(nameWidth).fillMaxHeight().heightIn(min = 56.dp),
                        color = palette.surface2,
                        contentColor = palette.ink,
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                            Text(row.name.withoutExample(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                row.engagement?.let { stringResource(Res.string.decider_pacte_received_on, it.receivedOn.toFrenchShort()) + if (row.late) " · " + stringResource(Res.string.decider_pacte_late) else "" }
                                    ?: stringResource(Res.string.decider_pacte_no_answer_received),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                            )
                        }
                    }
                    row.cells.forEach { cell ->
                        Column(Modifier.width(cellWidth).padding(horizontal = 8.dp, vertical = 8.dp).testTag(DeciderTags.cell(cell.measure.id))) {
                            StatusChip(cell.status)
                            cell.comment?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            FollowUps(cell, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryByMeasure(board: PacteBoard) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.section("summary"))) {
        SectionHeader(stringResource(Res.string.decider_pacte_summary_title))
        board.summaries.forEachIndexed { index, summary ->
            if (index > 0) HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 4.dp))
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${index + 1}. ${summary.measure.title}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(Res.string.decider_pacte_summary_line, summary.signed, summary.refused, summary.unanswered),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun SetAsideSection(board: PacteBoard) {
    if (board.setAside.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.Info, Modifier.testTag(DeciderTags.SET_ASIDE)) {
        Text(stringResource(Res.string.decider_pacte_set_aside_title, board.setAside.size), style = MaterialTheme.typography.titleSmall)
        board.setAside.forEach { item ->
            Text(
                "· " + stringResource(if (item.reason == SetAsideReason.OTHER_TEXT) Res.string.decider_pacte_set_aside_other_text else Res.string.decider_pacte_set_aside_not_candidate, item.engagement.article.title.withoutExample()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Each answer received is archived: the fingerprint of the document and its timestamp proof. */
@Composable
private fun AnswersArchives(board: PacteBoard) {
    val answered = board.rows.filter { it.engagement != null }
    if (answered.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag(DeciderTags.section("answers-archives"))) {
        SectionHeader(stringResource(Res.string.decider_pacte_archives_title))
        Text(stringResource(Res.string.decider_pacte_archives_text), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
        answered.forEach { row ->
            val engagement = row.engagement!!
            Spacer(Modifier.height(8.dp))
            Text(row.name.withoutExample(), style = MaterialTheme.typography.titleSmall)
            if (engagement.archives.isEmpty()) {
                Text(stringResource(Res.string.decider_pacte_archive_missing), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.warning)
            }
            ArchivesSection(engagement.archives)
            SourcesSection(engagement.sources)
            CorrectionsSection(engagement.corrections)
        }
    }
}

/** The fingerprint of the text sent: anyone can check that every candidate answered the same words. */
@Composable
private fun Fingerprint(pacte: Pacte) {
    val palette = PpfTheme.palette
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Inset(Modifier.fillMaxWidth().testTag(DeciderTags.FINGERPRINT)) {
        Text(stringResource(Res.string.decider_pacte_fingerprint_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.decider_pacte_fingerprint_text), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(6.dp))
        SelectionContainer { Text(pacte.fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = palette.ink) }
        PpfTextButton(
            onClick = {
                clipboard.setText(AnnotatedString(pacte.fingerprint))
                copied = true
            },
            text = stringResource(if (copied) Res.string.decider_copied else Res.string.decider_pacte_fingerprint_copy),
            modifier = Modifier.testTag(DeciderTags.FINGERPRINT_COPY),
        )
    }
}

/** What "Décider" pages can ask of the shell. */
internal class DeciderActions(
    val openPerson: (ContentAddress) -> Unit,
    val openFact: (ContentAddress) -> Unit,
    val next: (org.politisationpopulairefrancaise.core.voir.NextTarget) -> Unit,
)
