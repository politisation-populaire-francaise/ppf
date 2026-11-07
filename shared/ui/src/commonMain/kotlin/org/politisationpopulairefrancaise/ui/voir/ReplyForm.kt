package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.voir.ReplyCapacity
import org.politisationpopulairefrancaise.core.voir.ReplyRequest
import org.politisationpopulairefrancaise.core.voir.RequestProblem
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirRequests
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the requests to the editorial team. */
object VoirRequestTags {
    const val REPLY_FORM = "voir-request-reply-form"
    const val REPLY_RESPONDENT = "voir-request-reply-respondent"
    const val REPLY_REPRESENTATIVE = "voir-request-reply-representative"
    const val REPLY_TEXT = "voir-request-reply-text"
    const val REPLY_COUNTER = "voir-request-reply-counter"
    const val REPLY_STATEMENT = "voir-request-reply-statement"
    const val REPLY_SEND = "voir-request-reply-send"
    const val NO_ACCOUNT = "voir-request-no-account"
    const val CREATE_ACCOUNT = "voir-request-create-account"
    const val PROBLEMS = "voir-request-problems"
    const val OUTCOME = "voir-request-outcome"
    const val OPEN_MESSAGES = "voir-request-open-messages"
    const val BACK_TO_CONTENT = "voir-request-back"
    const val REPORT_LINK = "voir-request-report-link"
    const val REPORT_FORM = "voir-request-report-form"
    const val REPORT_WRONG = "voir-request-report-wrong"
    const val REPORT_CORRECTION = "voir-request-report-correction"
    const val REPORT_SOURCE = "voir-request-report-source"
    const val REPORT_ANONYMOUS = "voir-request-report-anonymous"
    const val REPORT_ACCOUNT = "voir-request-report-account"
    const val REPORT_WARNING = "voir-request-report-warning"
    const val REPORT_WARNING_CONFIRM = "voir-request-report-warning-confirm"
    const val REPORT_WARNING_CANCEL = "voir-request-report-warning-cancel"
    const val REPORT_SEND = "voir-request-report-send"
}

/** What the forms need from the screen around them. */
internal class RequestCallbacks(
    val createAccount: () -> Unit,
    val openMessages: () -> Unit,
    val back: () -> Unit,
)

/** The article behind any content of "Voir". */
internal fun articleOf(item: VoirItem): Article = when (item) {
    is VoirItem.OfIndicator -> item.indicator.article
    is VoirItem.OfFact -> item.fact.article
    is VoirItem.OfPerson -> item.person.article
    is VoirItem.OfText -> item.text.article
    is VoirItem.OfMovement -> item.movement.article
    is VoirItem.OfDossier -> item.dossier.article
}

/** Who the reply is probably from: the person of a sheet, or the only person a fact concerns. */
internal fun likelyRespondent(item: VoirItem, catalog: VoirCatalog): String? = when (item) {
    is VoirItem.OfPerson -> item.person.name
    is VoirItem.OfFact -> catalog.personsOf(item.fact).singleOrNull()?.name
    else -> null
}?.withoutExample()

/**
 * The form of a right of reply (VO-21): the law and the delay at the top, the name for the mention,
 * the capacity, the reply with a live counter against max(words of the content, 1000), the
 * statement on honour. It needs an account (the team must answer, the person must follow up); the
 * request leaves as an encrypted private message to the team.
 */
@Composable
internal fun ReplyFormPage(item: VoirItem, catalog: VoirCatalog, session: VoirRequestSession, callbacks: RequestCallbacks) {
    val palette = PpfTheme.palette
    remember(item.address) { session.startReply(item.address, likelyRespondent(item, catalog)) }
    val article = articleOf(item)
    val contentWords = remember(article) { VoirRequests.contentWords(article) }
    val maxWords = VoirRequests.maxReplyWords(contentWords)
    var outcome by remember { mutableStateOf<RequestOutcome?>(null) }
    var tried by remember { mutableStateOf(false) }
    val draft = session.replyDraft
    val subject = stringResource(Res.string.voir_req_reply_subject, item.title.withoutExample())

    Column(Modifier.fillMaxWidth().testTag(VoirRequestTags.REPLY_FORM)) {
        Text(stringResource(Res.string.voir_req_reply_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.voir_req_content, item.title.withoutExample()), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        Spacer(Modifier.height(12.dp))
        InfoBox(Lucide.Landmark) { Text(stringResource(Res.string.voir_req_reply_law), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))

        val done = outcome
        if (done != null && done != RequestOutcome.FAILED) {
            OutcomeBox(
                title = if (done == RequestOutcome.SENT) Res.string.voir_req_reply_sent_title else Res.string.voir_req_pending_title,
                text = if (done == RequestOutcome.SENT) Res.string.voir_req_reply_sent_text else Res.string.voir_req_reply_pending_text,
                pending = done == RequestOutcome.PENDING,
                callbacks = callbacks,
                showMessages = true,
            )
            return@Column
        }

        Text(stringResource(Res.string.voir_req_reply_why_account), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (!session.hasAccount) {
            InfoBox(Lucide.KeyRound, Modifier.testTag(VoirRequestTags.NO_ACCOUNT)) {
                Text(stringResource(Res.string.voir_req_reply_no_account), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfPrimaryButton(
                    onClick = callbacks.createAccount,
                    text = stringResource(Res.string.voir_req_create_account),
                    small = true,
                    icon = Lucide.CircleUserRound,
                    modifier = Modifier.testTag(VoirRequestTags.CREATE_ACCOUNT),
                )
            }
            Spacer(Modifier.height(16.dp))
        } else if (!session.canWriteMessages) {
            InfoBox(Lucide.Info) { Text(stringResource(Res.string.voir_req_no_database), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(16.dp))
        }

        FieldLabel(stringResource(Res.string.voir_req_respondent))
        PpfTextField(
            value = draft.respondent,
            onValueChange = { session.replyDraft = draft.copy(respondent = it.take(VoirRequests.MAX_NAME_CHARS + 20)) },
            placeholder = stringResource(Res.string.voir_req_respondent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(VoirRequestTags.REPLY_RESPONDENT),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(Res.string.voir_req_capacity))
        Column(Modifier.selectableGroup()) {
            PpfRadioRow(
                label = stringResource(Res.string.voir_req_capacity_person),
                supporting = stringResource(Res.string.voir_req_capacity_person_hint),
                selected = draft.capacity == ReplyCapacity.PERSON_NAMED,
                onClick = { session.replyDraft = draft.copy(capacity = ReplyCapacity.PERSON_NAMED) },
            )
            PpfRadioRow(
                label = stringResource(Res.string.voir_req_capacity_representative),
                supporting = stringResource(Res.string.voir_req_capacity_representative_hint),
                selected = draft.capacity == ReplyCapacity.LEGAL_REPRESENTATIVE,
                onClick = { session.replyDraft = draft.copy(capacity = ReplyCapacity.LEGAL_REPRESENTATIVE) },
                modifier = Modifier.testTag(VoirRequestTags.REPLY_REPRESENTATIVE),
            )
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(Res.string.voir_req_reply_text))
        Text(stringResource(Res.string.voir_req_reply_limit, contentWords, maxWords), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(6.dp))
        PpfTextField(
            value = draft.text,
            onValueChange = { session.replyDraft = draft.copy(text = it) },
            placeholder = stringResource(Res.string.voir_req_reply_text),
            minLines = 8,
            modifier = Modifier.fillMaxWidth().testTag(VoirRequestTags.REPLY_TEXT),
        )
        val words = VoirRequests.countWords(draft.text)
        val over = words > maxWords
        Text(
            if (over) stringResource(Res.string.voir_req_counter_over, words, words - maxWords, maxWords) else stringResource(Res.string.voir_req_counter, words, maxWords),
            style = MaterialTheme.typography.labelMedium,
            color = if (over) palette.accent else palette.inkSubtle,
            modifier = Modifier.padding(top = 4.dp).testTag(VoirRequestTags.REPLY_COUNTER).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        CheckRow(stringResource(Res.string.voir_req_statement), draft.statement, onToggle = { session.replyDraft = draft.copy(statement = !draft.statement) }, tag = VoirRequestTags.REPLY_STATEMENT)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.voir_req_identity), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.voir_req_encrypted), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)

        val request = ReplyRequest(
            content = item.address.toString(),
            contentTitle = item.title,
            respondent = draft.respondent.trim(),
            capacity = draft.capacity,
            reply = draft.text.trim(),
            contentWords = contentWords,
            statementOfTruth = draft.statement,
        )
        val problems = remember(request) { request.problems() }
        if (tried && problems.isNotEmpty()) ProblemsBox(problems)
        if (done == RequestOutcome.FAILED) FailedBox()
        Spacer(Modifier.height(16.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            PpfPrimaryButton(
                onClick = {
                    tried = true
                    if (problems.isEmpty()) session.sendReply(request, subject) { outcome = it }
                },
                text = stringResource(if (session.sending) Res.string.voir_req_sending else Res.string.voir_req_reply_send),
                enabled = session.canWriteMessages && !session.sending,
                icon = Lucide.Send,
                modifier = Modifier.testTag(VoirRequestTags.REPLY_SEND),
            )
            PpfTextButton(onClick = callbacks.back, text = stringResource(Res.string.voir_req_cancel))
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp).semantics { heading() })
}

/** What prevents sending, in words, announced to screen readers. */
@Composable
internal fun ProblemsBox(problems: List<RequestProblem>) {
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.TriangleExclamationPoint, Modifier.testTag(VoirRequestTags.PROBLEMS).semantics { liveRegion = LiveRegionMode.Polite }) {
        problems.forEach { Text(stringResource(it.message), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun FailedBox() {
    Spacer(Modifier.height(12.dp))
    InfoBox(Lucide.TriangleExclamationPoint, Modifier.testTag(VoirRequestTags.OUTCOME)) {
        Text(stringResource(Res.string.voir_req_failed_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.voir_req_failed_text), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The clear confirmation once a request left, or waits on the device. */
@Composable
internal fun OutcomeBox(title: StringResource, text: StringResource, pending: Boolean, callbacks: RequestCallbacks, showMessages: Boolean) {
    InfoBox(if (pending) Lucide.Clock else Lucide.CircleCheck, Modifier.testTag(VoirRequestTags.OUTCOME).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showMessages) {
                PpfSecondaryButton(onClick = callbacks.openMessages, text = stringResource(Res.string.voir_req_open_messages), small = true, icon = Lucide.MessageSquare, modifier = Modifier.testTag(VoirRequestTags.OPEN_MESSAGES))
            }
            PpfTextButton(onClick = callbacks.back, text = stringResource(Res.string.voir_req_back_to_content), modifier = Modifier.testTag(VoirRequestTags.BACK_TO_CONTENT))
        }
    }
}

/** A checkbox and its label, 48 dp high, announced as a checkbox (same look as the other forms). */
@Composable
internal fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit, tag: String) {
    val palette = PpfTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp)
                .background(if (checked) palette.brand else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .border(2.dp, if (checked) palette.brand else palette.borderStrong, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, tint = palette.onBrand, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

internal val RequestProblem.message: StringResource
    get() = when (this) {
        RequestProblem.CONTENT_ADDRESS -> Res.string.voir_req_problem_content
        RequestProblem.RESPONDENT_MISSING -> Res.string.voir_req_problem_respondent
        RequestProblem.RESPONDENT_TOO_LONG -> Res.string.voir_req_problem_respondent_long
        RequestProblem.REPLY_MISSING -> Res.string.voir_req_problem_reply
        RequestProblem.REPLY_TOO_LONG -> Res.string.voir_req_problem_reply_long
        RequestProblem.STATEMENT_MISSING -> Res.string.voir_req_problem_statement
        RequestProblem.WRONG_MISSING -> Res.string.voir_req_problem_wrong
        RequestProblem.FIELD_TOO_LONG -> Res.string.voir_req_problem_field_long
        RequestProblem.SOURCE_INVALID -> Res.string.voir_req_problem_source
        RequestProblem.MESSAGE_TOO_LARGE -> Res.string.voir_req_problem_too_large
    }
