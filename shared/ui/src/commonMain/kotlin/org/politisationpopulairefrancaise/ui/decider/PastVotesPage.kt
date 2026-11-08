package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.decider.CandidateVotes
import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.decider.PartyBallot
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.voir.ErrorReport
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_votes_all
import org.politisationpopulairefrancaise.ui.resources.decider_votes_all_rule
import org.politisationpopulairefrancaise.ui.resources.decider_votes_cancel
import org.politisationpopulairefrancaise.ui.resources.decider_votes_count
import org.politisationpopulairefrancaise.ui.resources.decider_votes_download
import org.politisationpopulairefrancaise.ui.resources.decider_votes_download_text
import org.politisationpopulairefrancaise.ui.resources.decider_votes_downloading
import org.politisationpopulairefrancaise.ui.resources.decider_votes_empty
import org.politisationpopulairefrancaise.ui.resources.decider_votes_intro
import org.politisationpopulairefrancaise.ui.resources.decider_votes_loading
import org.politisationpopulairefrancaise.ui.resources.decider_votes_more
import org.politisationpopulairefrancaise.ui.resources.decider_votes_no_party
import org.politisationpopulairefrancaise.ui.resources.decider_votes_official
import org.politisationpopulairefrancaise.ui.resources.decider_votes_others
import org.politisationpopulairefrancaise.ui.resources.decider_votes_others_rule
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_done
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_failed
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_field
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_send
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_subject
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_text
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_title
import org.politisationpopulairefrancaise.ui.resources.decider_votes_report_wrong
import org.politisationpopulairefrancaise.ui.resources.decider_votes_result_adopted
import org.politisationpopulairefrancaise.ui.resources.decider_votes_result_rejected
import org.politisationpopulairefrancaise.ui.resources.decider_votes_search
import org.politisationpopulairefrancaise.ui.resources.decider_votes_split
import org.politisationpopulairefrancaise.ui.resources.decider_votes_theme_rule
import org.politisationpopulairefrancaise.ui.resources.decider_votes_title
import org.politisationpopulairefrancaise.ui.resources.decider_votes_unavailable
import org.politisationpopulairefrancaise.ui.resources.decider_votes_version
import org.politisationpopulairefrancaise.ui.resources.decider_votes_words
import org.politisationpopulairefrancaise.ui.revendications.themeLabel
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.RequestOutcome
import org.politisationpopulairefrancaise.ui.voir.VoirRequestSession
import org.politisationpopulairefrancaise.ui.voir.parlement.count
import org.politisationpopulairefrancaise.ui.voir.parlement.label
import org.politisationpopulairefrancaise.ui.voir.rememberOpenUrl
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** Test tags of the past votes (DE-16, D-117). */
object PastVotesTags {
    const val PAGE = "decider-past-votes"
    const val DOWNLOAD = "decider-past-votes-download"
    const val SEARCH = "decider-past-votes-search"
    const val RULE = "decider-past-votes-rule"
    const val ALL = "decider-past-votes-all"
    const val OTHERS = "decider-past-votes-others"
    const val MORE = "decider-past-votes-more"
    const val REPORT_DIALOG = "decider-past-votes-report-dialog"
    const val REPORT_SEND = "decider-past-votes-report-send"
    fun theme(id: String) = "decider-past-votes-theme-$id"
    fun ballot(id: String) = "decider-past-votes-ballot-$id"
    fun report(id: String) = "decider-past-votes-report-$id"
}

/** What the list shows: every vote of the party, one theme, or the ballots no theme takes. */
private sealed interface VotesView {
    data object All : VotesView
    data class Theme(val id: String) : VotesView
    data object Others : VotesView
}

private const val PAGE_SIZE = 20

/**
 * The past votes of the party of one candidate (DE-16, decision D-117): every public ballot of the history of
 * "Voir" where the members of the party voted, in numbers, never a computed position. The themes help to read and
 * never filter what can be seen: « Tous les votes de ce parti » and the free search are always there; under each
 * list, the published words of the rule, its version, and a way to report a ballot put in the wrong theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PastVotesPage(
    sheet: ComparatorSheet,
    candidate: ContentAddress,
    name: String,
    state: PastVotesState,
    reports: VoirRequestSession?,
) {
    val palette = PpfTheme.palette
    LaunchedEffect(state) { state.start() }
    var view by remember(candidate) { mutableStateOf<VotesView>(VotesView.All) }
    var query by rememberSaveable(candidate.toString()) { mutableStateOf("") }
    var shown by remember(candidate, view, query) { mutableStateOf(PAGE_SIZE) }
    var reporting by remember { mutableStateOf<PartyBallot?>(null) }

    Column(Modifier.fillMaxWidth().testTag(PastVotesTags.PAGE)) {
        PageHeader(stringResource(Res.string.decider_votes_title, name.withoutExample()), sheet.election.label)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.decider_votes_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (sheet.parties.none { it.concerns(candidate) }) {
            InfoBox(Lucide.Info) { Text(stringResource(Res.string.decider_votes_no_party), style = MaterialTheme.typography.bodyMedium) }
            return@Column
        }
        when (val status = state.status) {
            PastVotesStatus.Loading -> {
                Text(stringResource(Res.string.decider_votes_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                return@Column
            }
            PastVotesStatus.Unavailable -> {
                InfoBox(Lucide.Info) { Text(stringResource(Res.string.decider_votes_unavailable), style = MaterialTheme.typography.bodyMedium) }
                return@Column
            }
            is PastVotesStatus.Ready -> if (status.bytesToDownload > 0) {
                InfoBox(Lucide.Landmark, Modifier.testTag(PastVotesTags.DOWNLOAD)) {
                    Text(stringResource(Res.string.decider_votes_download_text, megabytes(status.bytesToDownload)), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    PpfSecondaryButton(
                        onClick = { state.downloadAll() },
                        text = stringResource(if (state.downloading) Res.string.decider_votes_downloading else Res.string.decider_votes_download),
                        enabled = !state.downloading,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        LaunchedEffect(state.status, sheet.article.event.id, candidate) { state.read(sheet, candidate) }
        val votes: CandidateVotes = state.cached(sheet, candidate) ?: run {
            Text(stringResource(Res.string.decider_votes_loading), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@Column
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(stringResource(Res.string.decider_votes_all), selected = view == VotesView.All, onClick = { view = VotesView.All }, modifier = Modifier.testTag(PastVotesTags.ALL))
            sheet.themeWords.filter { it.theme in sheet.themes }.forEach { words ->
                PpfSelectableChip(
                    label = themeLabel(words.theme),
                    selected = view == VotesView.Theme(words.theme),
                    onClick = { view = VotesView.Theme(words.theme) },
                    modifier = Modifier.testTag(PastVotesTags.theme(words.theme)),
                )
            }
            if (sheet.themeWords.isNotEmpty()) {
                PpfSelectableChip(stringResource(Res.string.decider_votes_others), selected = view == VotesView.Others, onClick = { view = VotesView.Others }, modifier = Modifier.testTag(PastVotesTags.OTHERS))
            }
        }
        Spacer(Modifier.height(12.dp))
        PpfTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            placeholder = stringResource(Res.string.decider_votes_search),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PastVotesTags.SEARCH),
        )
        Spacer(Modifier.height(12.dp))

        val current = view
        val base = when (current) {
            VotesView.All -> votes.all
            is VotesView.Theme -> sheet.wordsOf(current.id)?.let(votes::inTheme).orEmpty()
            VotesView.Others -> votes.outsideEveryTheme(sheet.themeWords)
        }
        val listed = if (query.isBlank()) base else base.filter { FrenchText.matchesAllWords(it.ballot.title, query) }
        Text(stringResource(Res.string.decider_votes_count, count(listed.size)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        RuleBox(sheet, current)
        Spacer(Modifier.height(8.dp))
        if (listed.isEmpty()) {
            Text(stringResource(Res.string.decider_votes_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        }
        listed.take(shown).forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = palette.border)
            BallotLine(item, canReport = sheet.themeWords.isNotEmpty() && reports != null, onReport = { reporting = item })
        }
        if (listed.size > shown) {
            PpfTextButton(onClick = { shown += PAGE_SIZE }, text = stringResource(Res.string.decider_votes_more), modifier = Modifier.testTag(PastVotesTags.MORE))
        }
    }
    val wrong = reporting
    if (wrong != null && reports != null) {
        ReportDialog(sheet, wrong, reports, onClose = { reporting = null })
    }
}

/** The rule of the list in plain words: what it holds, the words, their version (D-117, safeguards 2 and 4). */
@Composable
private fun RuleBox(sheet: ComparatorSheet, view: VotesView) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag(PastVotesTags.RULE)) {
        when (view) {
            VotesView.All -> Text(stringResource(Res.string.decider_votes_all_rule), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            VotesView.Others -> Text(stringResource(Res.string.decider_votes_others_rule), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            is VotesView.Theme -> {
                Text(stringResource(Res.string.decider_votes_theme_rule), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                sheet.wordsOf(view.id)?.let { words ->
                    Text(stringResource(Res.string.decider_votes_words, words.words.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
            }
        }
        sheet.currentWords?.let { version ->
            if (view != VotesView.All) {
                Text(
                    stringResource(Res.string.decider_votes_version, version.version, version.date.toFrench(), version.reason.withoutExample()),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSubtle,
                )
            }
        }
    }
}

/** One ballot: date, assembly, official title, result, and how the party voted, in numbers only. */
@Composable
private fun BallotLine(item: PartyBallot, canReport: Boolean, onReport: () -> Unit) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    val ballot = item.ballot
    val split = item.distribution
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag(PastVotesTags.ballot(ballot.id))) {
        Text("${item.date.toFrenchShort()} · ${stringResource(item.chamber.label)}", style = MaterialTheme.typography.labelMedium, color = palette.inkMuted)
        Spacer(Modifier.height(2.dp))
        Text(ballot.title.withoutExample(), style = MaterialTheme.typography.bodyMedium)
        ballot.adopted?.let { adopted ->
            Text(stringResource(if (adopted) Res.string.decider_votes_result_adopted else Res.string.decider_votes_result_rejected), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(4.dp))
        val party = item.party
        val partyName = party?.let { p -> listOfNotNull(p.label.withoutExample(), p.basisLabel.takeIf { split.basis == PartyBasis.GROUP }).joinToString(", ") } ?: split.party
        Text(
            stringResource(Res.string.decider_votes_split, partyName, count(split.inFavour), count(split.against), count(split.abstention), count(split.notVoting), count(split.members)),
            style = MaterialTheme.typography.bodyMedium,
        )
        ballot.url?.let { url -> PpfTextButton(onClick = { open(url) }, text = stringResource(Res.string.decider_votes_official)) }
        if (canReport) {
            PpfTextButton(onClick = onReport, text = stringResource(Res.string.decider_votes_report), color = palette.inkMuted, modifier = Modifier.testTag(PastVotesTags.report(ballot.id)))
        }
    }
}

/**
 * Reports a ballot put in the wrong theme, without an account by default: the team corrects the rule of words,
 * never this one ballot by hand (D-117, safeguard 4). Sent like the error reports of "Voir" (D-056).
 */
@Composable
private fun ReportDialog(sheet: ComparatorSheet, item: PartyBallot, reports: VoirRequestSession, onClose: () -> Unit) {
    var text by remember(item.ballot.id) { mutableStateOf("") }
    var outcome by remember(item.ballot.id) { mutableStateOf<RequestOutcome?>(null) }
    val wrong = stringResource(Res.string.decider_votes_report_wrong, item.ballot.id, item.ballot.title)
    val subject = stringResource(Res.string.decider_votes_report_subject, sheet.article.title.withoutExample())
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag(PastVotesTags.REPORT_DIALOG),
        title = { Text(stringResource(Res.string.decider_votes_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (outcome) {
                    null -> {
                        Text(stringResource(Res.string.decider_votes_report_text), style = MaterialTheme.typography.bodyMedium)
                        Text(item.ballot.title.withoutExample(), style = MaterialTheme.typography.bodySmall)
                        PpfTextField(value = text, onValueChange = { text = it.take(1_000) }, placeholder = stringResource(Res.string.decider_votes_report_field), minLines = 3, modifier = Modifier.fillMaxWidth())
                    }
                    RequestOutcome.FAILED -> Text(stringResource(Res.string.decider_votes_report_failed), style = MaterialTheme.typography.bodyMedium)
                    else -> Text(stringResource(Res.string.decider_votes_report_done), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (outcome == null) {
                PpfPrimaryButton(
                    onClick = {
                        val report = ErrorReport(
                            content = sheet.address.toString(),
                            contentTitle = sheet.article.title,
                            wrong = wrong,
                            correction = text.trim().ifEmpty { null },
                            anonymous = true,
                        )
                        reports.sendReport(report, subject) { outcome = it }
                    },
                    text = stringResource(Res.string.decider_votes_report_send),
                    enabled = !reports.sending,
                    modifier = Modifier.testTag(PastVotesTags.REPORT_SEND),
                )
            }
        },
        dismissButton = { PpfTextButton(onClick = onClose, text = stringResource(Res.string.decider_votes_cancel)) },
    )
}

/** "3,2 Mo": the size of what is to download, rounded up to a tenth. */
internal fun megabytes(bytes: Long): String {
    val tenths = (bytes + 99_999) / 100_000
    return if (tenths % 10 == 0L) "${tenths / 10} Mo" else "${tenths / 10},${tenths % 10} Mo"
}
