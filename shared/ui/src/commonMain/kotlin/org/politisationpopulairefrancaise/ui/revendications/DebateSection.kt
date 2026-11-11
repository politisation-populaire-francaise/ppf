package org.politisationpopulairefrancaise.ui.revendications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.contact.ContentReports
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.revendications.DebateComment
import org.politisationpopulairefrancaise.core.revendications.DebateStance
import org.politisationpopulairefrancaise.core.revendications.DemandDebate
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.ReportContentButton
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.debate_all
import org.politisationpopulairefrancaise.ui.resources.debate_empty
import org.politisationpopulairefrancaise.ui.resources.debate_form_hint
import org.politisationpopulairefrancaise.ui.resources.debate_form_label
import org.politisationpopulairefrancaise.ui.resources.debate_need_account
import org.politisationpopulairefrancaise.ui.resources.debate_rules
import org.politisationpopulairefrancaise.ui.resources.debate_send
import org.politisationpopulairefrancaise.ui.resources.debate_sent
import org.politisationpopulairefrancaise.ui.resources.debate_sent_later
import org.politisationpopulairefrancaise.ui.resources.debate_refused
import org.politisationpopulairefrancaise.ui.resources.debate_stance_contre
import org.politisationpopulairefrancaise.ui.resources.debate_stance_mitige
import org.politisationpopulairefrancaise.ui.resources.debate_stance_pour
import org.politisationpopulairefrancaise.ui.resources.debate_stance_question
import org.politisationpopulairefrancaise.ui.resources.debate_title
import org.politisationpopulairefrancaise.ui.resources.debate_author
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object DebateTags {
    const val SECTION = "debate-section"
    const val FILTER = "debate-filter-" // followed by "tout" or the stance
    const val STANCE = "debate-stance-" // followed by the stance
    const val TEXT = "debate-text"
    const val SEND = "debate-send"
    const val OUTCOME = "debate-outcome"
    fun comment(id: String) = "debate-comment-" + id.take(12)
}

@Composable
private fun stanceLabel(stance: DebateStance): String = stringResource(
    when (stance) {
        DebateStance.POUR -> Res.string.debate_stance_pour
        DebateStance.CONTRE -> Res.string.debate_stance_contre
        DebateStance.MITIGE -> Res.string.debate_stance_mitige
    },
)

private fun stanceStyle(stance: DebateStance): BadgeStyle = when (stance) {
    DebateStance.POUR -> BadgeStyle.SUCCESS
    DebateStance.CONTRE -> BadgeStyle.ACCENT
    DebateStance.MITIGE -> BadgeStyle.NEUTRAL
}

/**
 * The debate of a demand (DE-20, MVP 1): counts by stance that filter the list, the rules in one box, the form
 * (stance, text, send) and the comments, newest first, each with « Signaler ». A pseudonym publishes; nothing is
 * hidden before a person of the team decides on a report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebateSection(demand: Article, library: ContentLibrary, signer: EventSigner?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var comments by remember(demand.address) { mutableStateOf<List<DebateComment>>(emptyList()) }
    var reload by remember(demand.address) { mutableStateOf(0) }
    LaunchedEffect(demand.address, reload) { comments = runCatching { library.debateOf(demand) }.getOrDefault(comments) }
    var filter by rememberSaveable(demand.address) { mutableStateOf<String?>(null) }
    var stance by rememberSaveable(demand.address) { mutableStateOf<String?>(null) }
    var text by rememberSaveable(demand.address) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var outcome by remember(demand.address) { mutableStateOf<PublicationResult?>(null) }
    val counts = DemandDebate.counts(comments)
    val shown = comments.filter { filter == null || it.stance.tagValue == filter }

    Column(modifier.fillMaxWidth().testTag(DebateTags.SECTION), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.debate_title, comments.size), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PpfSelectableChip(label = stringResource(Res.string.debate_all, comments.size), selected = filter == null, onClick = { filter = null }, modifier = Modifier.testTag(DebateTags.FILTER + "tout"))
            DebateStance.entries.forEach { s ->
                PpfSelectableChip(
                    label = "${stanceLabel(s)} (${counts.getValue(s)})",
                    selected = filter == s.tagValue,
                    onClick = { filter = if (filter == s.tagValue) null else s.tagValue },
                    modifier = Modifier.testTag(DebateTags.FILTER + s.tagValue),
                )
            }
        }
        InfoBox(Lucide.Info) { Text(stringResource(Res.string.debate_rules), style = MaterialTheme.typography.bodySmall) }

        // The form: the stance first, then the text, then send.
        PpfCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.debate_stance_question), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebateStance.entries.forEach { s ->
                    PpfSelectableChip(label = stanceLabel(s), selected = stance == s.tagValue, onClick = { stance = s.tagValue }, modifier = Modifier.testTag(DebateTags.STANCE + s.tagValue))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.debate_form_label, DemandDebate.MAX_TEXT), style = MaterialTheme.typography.labelLarge)
            PpfTextField(text, { text = it.take(DemandDebate.MAX_TEXT) }, stringResource(Res.string.debate_form_hint), Modifier.fillMaxWidth().testTag(DebateTags.TEXT), minLines = 3)
            Spacer(Modifier.height(8.dp))
            if (signer == null) {
                Text(stringResource(Res.string.debate_need_account), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            val chosen = DebateStance.fromTag(stance)
            PpfPrimaryButton(
                onClick = {
                    if (signer != null && chosen != null) {
                        sending = true
                        scope.launch {
                            val result = runCatching { library.sendDebate(signer, demand, chosen, text) }.getOrNull()
                            outcome = result
                            if (result !is PublicationResult.Rejected && result != null) {
                                text = ""
                                stance = null
                            }
                            sending = false
                            reload++
                        }
                    }
                },
                text = stringResource(Res.string.debate_send),
                enabled = signer != null && chosen != null && text.isNotBlank() && !sending,
                modifier = Modifier.testTag(DebateTags.SEND),
            )
            outcome?.let { result ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        when (result) {
                            is PublicationResult.Accepted -> Res.string.debate_sent
                            is PublicationResult.Rejected -> Res.string.debate_refused
                            else -> Res.string.debate_sent_later
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(DebateTags.OUTCOME),
                )
            }
        }

        if (shown.isEmpty()) Text(stringResource(Res.string.debate_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
        shown.forEach { comment -> CommentCard(comment) }
    }
}

@Composable
private fun CommentCard(comment: DebateComment) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(DebateTags.comment(comment.id))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PpfBadge(stanceLabel(comment.stance), stanceStyle(comment.stance))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(Res.string.debate_author, comment.author.take(8), CivilDate.fromEpochSeconds(comment.createdAt).toFrench()),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        ReportContentButton(ContentReports.eventAddress(DemandDebate.KIND, comment.id))
    }
}
