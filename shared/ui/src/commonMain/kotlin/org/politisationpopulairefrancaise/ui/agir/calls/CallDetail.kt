package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallFieldKind
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallResponse
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.Inset
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.leavingHost
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.shortNpub
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import org.politisationpopulairefrancaise.ui.agir.testimonies.LocalTestify
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The detail of a call: what, where and when; the goal and the countdown (AP-04); the answer, kept
 * on the device (AP-03); the report of a closed call (AP-09); the text; the practical information
 * of its type (AP-10); the links, behind the "Vous quittez PPF" warning; what makes it grow (AP-07);
 * the discussion, not yet available (AP-06); and, for its author, the closing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallDetail(
    call: Call,
    library: CallsLibrary,
    accountKey: String?,
    compact: Boolean,
    onBack: (() -> Unit)?,
    verified: Boolean,
    onCreateEvent: ((Call) -> Unit)?,
    onCreateDemand: ((Call) -> Unit)?,
    onOpenLink: (url: String, fundraising: Boolean) -> Unit,
    onClose: (Call) -> Unit,
    closeOutcome: CallSendOutcome?,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val now = library.now()
    val own = library.ownCall(call)
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp).testTag(CallTags.DETAIL)) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.appels_back), onBack, Modifier.testTag(CallTags.DETAIL_BACK), tint = palette.ink)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeBadge(call.type)
                PpfBadge(stringResource(if (call.isOpen) Res.string.appels_status_open else Res.string.appels_status_closed), if (call.isOpen) BadgeStyle.SUCCESS else BadgeStyle.NEUTRAL)
                if (own != null) OwnStateBadge(own.state)
                if (verified) PpfBadge(stringResource(Res.string.appels_verified_org), BadgeStyle.BRAND)
                if (call.isExample) ExampleBadge()
            }
            Text(call.displayTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            call.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaItem(Lucide.MapPin, call.territory)
                MetaItem(Lucide.User, stringResource(Res.string.appels_launched_by, shortNpub(call.author)))
            }
            own?.reason?.takeIf { own.state == OwnDemandState.NOT_PUBLISHED }?.let { reason ->
                InfoBox(Lucide.Info) { Text(stringResource(Res.string.appels_own_reason, reason), style = MaterialTheme.typography.bodyMedium) }
            }
            closeOutcome?.let { CloseOutcomeBox(it) }

            WhenAndWhere(call)
            GoalAndCountdown(call, now)
            call.report?.let { report ->
                Callout(CalloutType.EN_BREF, title = stringResource(Res.string.appels_section_report), modifier = Modifier.testTag(CallTags.REPORT)) {
                    SelectionContainer { Text(report, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            AnswerCard(call, library)
            if (call.type == CallType.TEMOINS) {
                InfoBox(Lucide.ShieldCheck) { Text(stringResource(Res.string.appels_witnesses_info), style = MaterialTheme.typography.bodyMedium) }
            }

            SectionTitle(stringResource(Res.string.appels_section_text))
            SelectionContainer { Text(call.text, style = MaterialTheme.typography.bodyLarge) }

            PracticalInformation(call, onOpenLink)

            if (call.type.isFundraising) {
                InfoBox(Lucide.Heart) { Text(stringResource(Res.string.appels_fundraising_info), style = MaterialTheme.typography.bodyMedium) }
            }
            if (call.links.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    call.links.forEachIndexed { index, link ->
                        val label = when (call.type) {
                            CallType.PETITION -> Res.string.appels_open_petition
                            CallType.COLLECTE -> Res.string.appels_open_fundraising
                            else -> Res.string.appels_open_link
                        }
                        PpfSecondaryButton(
                            onClick = { onOpenLink(link, call.type.isFundraising) },
                            text = stringResource(label),
                            icon = Lucide.Globe,
                            modifier = Modifier.testTag(CallTags.LINK + index),
                        )
                    }
                }
            }

            Grow(call, onCreateEvent, onCreateDemand)

            // The discussion room of a call (AP-06) waits for its specification.
            PpfCard(Modifier.fillMaxWidth()) {
                PpfSecondaryButton(onClick = {}, text = stringResource(Res.string.appels_discuss), enabled = false, icon = Lucide.MessagesSquare, modifier = Modifier.testTag(CallTags.DISCUSS))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.appels_discuss_soon), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }

            // Only a published call is closed: the server publishes its closing at once (decision of the owner, 2026-11-07).
            if (accountKey != null && call.author == accountKey && call.isOpen && own?.state == OwnDemandState.PUBLISHED) {
                PpfSecondaryButton(onClick = { onClose(call) }, text = stringResource(Res.string.appels_close), icon = Lucide.Flag, modifier = Modifier.testTag(CallTags.CLOSE))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = PpfTheme.palette.brand, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** The place and the dates of a call (start and end, deadline). */
@Composable
private fun WhenAndWhere(call: Call) {
    val lines = buildList {
        call.location?.let { add(Lucide.MapPin to it) }
        val start = call.start
        val end = call.end
        if (start != null) add(Lucide.Calendar to if (end != null) stringResource(Res.string.appels_period, parisDateTime(start), parisDateTime(end)) else stringResource(Res.string.appels_starts_on, parisDateTime(start)))
        call.deadline?.let { add(Lucide.Clock to stringResource(Res.string.appels_due_on, parisDateTime(it))) }
    }
    if (lines.isEmpty()) return
    Inset(Modifier.fillMaxWidth()) {
        lines.forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = PpfTheme.palette.inkMuted, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** AP-04: the goal and the countdown, without a progress bar while no counter exists. */
@Composable
private fun GoalAndCountdown(call: Call, now: Long) {
    val goal = call.goal
    val due = call.dueAt?.takeIf { call.isOpen }
    if (goal == null && due == null) return
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.appels_section_goal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        if (goal != null) Text(stringResource(Res.string.appels_goal, frenchNumber(goal)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(CallTags.GOAL))
        if (due != null) Text(countdown(due, now), style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.accentInk, modifier = Modifier.testTag(CallTags.COUNTDOWN))
        if (goal != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(Res.string.appels_no_progress), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
        }
    }
}

/** AP-03: the answer adapted to the type, kept on this device only. */
@Composable
private fun AnswerCard(call: Call, library: CallsLibrary) {
    val palette = PpfTheme.palette
    val responded = library.isResponded(call)
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.appels_section_answer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        val testify = call.type.response == CallResponse.TESTIFY
        // "Je témoigne" opens "Témoigner" linked to this call (AG-07), where Agir plugs it in.
        val testifyHere = LocalTestify.current
        when {
            testify -> PpfPrimaryButton(
                { testifyHere?.invoke(call.address.toString(), call.displayTitle) },
                stringResource(responseResource(call.type.response)),
                enabled = testifyHere != null && call.isOpen,
                icon = Lucide.Eye,
                modifier = Modifier.testTag(CallTags.ANSWER),
            )
            !call.isOpen && !responded -> PpfPrimaryButton({}, stringResource(responseResource(call.type.response)), enabled = false, modifier = Modifier.testTag(CallTags.ANSWER))
            responded -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(CallTags.ANSWER_DONE)) {
                    Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.appels_answer_done), style = MaterialTheme.typography.bodyLarge, color = palette.success)
                }
                PpfTextButton({ library.toggleResponse(call) }, stringResource(Res.string.appels_answer_withdraw), Modifier.testTag(CallTags.ANSWER_WITHDRAW))
            }
            else -> PpfPrimaryButton({ library.toggleResponse(call) }, stringResource(responseResource(call.type.response)), icon = Lucide.Check, modifier = Modifier.testTag(CallTags.ANSWER))
        }
        Spacer(Modifier.height(6.dp))
        val note = when {
            testify && testifyHere != null -> Res.string.temoignages_call_note
            testify -> Res.string.appels_answer_testify_soon
            !call.isOpen -> Res.string.appels_answer_closed
            else -> Res.string.appels_answer_private
        }
        Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, modifier = Modifier.testTag(CallTags.ANSWER_NOTE))
    }
}

/** The specific fields of the type, labelled; a source that is a link opens behind the warning. */
@Composable
private fun PracticalInformation(call: Call, onOpenLink: (String, Boolean) -> Unit) {
    val specific = CallFields.specific(call.type).filter { it.kind != CallFieldKind.DATE_TIME }
    val shown = specific.mapNotNull { field -> call.fields[field.tag]?.let { field to it } }
    if (shown.isEmpty()) return
    SectionTitle(stringResource(Res.string.appels_section_details))
    Inset(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, (field, value) ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Text(stringResource(fieldLabelResource(field.tag, call.type)), style = MaterialTheme.typography.labelLarge, color = PpfTheme.palette.inkMuted)
            val text = when (field.kind) {
                CallFieldKind.CHOICE -> choiceLabelResource(field.tag, value)?.let { stringResource(it) } ?: value
                CallFieldKind.FLAG -> stringResource(Res.string.appels_remote_yes)
                CallFieldKind.NUMBER -> value.toLongOrNull()?.let(::frenchNumber) ?: value
                else -> value
            }
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
            if (field.tag == CallFields.SOURCE && CallRules.isValidLink(value)) {
                PpfTextButton({ onOpenLink(value, false) }, stringResource(Res.string.appels_open_source), Modifier.testTag(CallTags.SOURCE_LINK))
            }
        }
    }
}

/** AP-07: the share kit, and the ways to turn the call into an event or a demand when offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Grow(call: Call, onCreateEvent: ((Call) -> Unit)?, onCreateDemand: ((Call) -> Unit)?) {
    val palette = PpfTheme.palette
    // The suspending LocalClipboard needs a ClipEntry built by platform code; plain text is enough here.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(call.address) { mutableStateOf(false) }
    val share = shareText(call)
    SectionTitle(stringResource(Res.string.appels_section_grow))
    PpfCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.appels_share_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.appels_share_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Spacer(Modifier.height(8.dp))
        Inset(Modifier.fillMaxWidth().testTag(CallTags.SHARE_TEXT)) {
            SelectionContainer { Text(share, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(8.dp))
        PpfSecondaryButton(
            onClick = {
                clipboard.setText(AnnotatedString(share))
                copied = true
            },
            text = stringResource(if (copied) Res.string.appels_share_copied else Res.string.appels_share_copy),
            icon = if (copied) Lucide.Check else Lucide.Copy,
            modifier = Modifier.testTag(CallTags.SHARE_COPY),
        )
        if (onCreateEvent != null || onCreateDemand != null) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCreateEvent != null) PpfSecondaryButton({ onCreateEvent(call) }, stringResource(Res.string.appels_make_event), icon = Lucide.Calendar, modifier = Modifier.testTag(CallTags.MAKE_EVENT))
                if (onCreateDemand != null) PpfSecondaryButton({ onCreateDemand(call) }, stringResource(Res.string.appels_make_demand), icon = Lucide.ScrollText, modifier = Modifier.testTag(CallTags.MAKE_DEMAND))
            }
        }
    }
}

/** The text of the share kit: title, summary, territory, date, and where to find the call. No link to a third party. */
@Composable
private fun shareText(call: Call): String {
    val lines = buildList {
        add(call.displayTitle)
        call.summary?.let { add(it) }
        add(stringResource(Res.string.appels_share_territory, call.territory))
        dueLabel(call.deadline, call.start)?.let { add(it) }
        call.location?.let { add(it) }
        add(stringResource(Res.string.appels_share_footer))
    }
    return lines.joinToString("\n")
}

@Composable
private fun CloseOutcomeBox(outcome: CallSendOutcome) {
    val text = when (outcome) {
        CallSendOutcome.PUBLISHED -> Res.string.appels_close_done
        CallSendOutcome.SENT_FOR_REVIEW -> Res.string.appels_close_review
        CallSendOutcome.WAITING_FOR_SERVER -> Res.string.appels_close_waiting
        CallSendOutcome.REFUSED -> Res.string.appels_close_refused
        CallSendOutcome.FAILED -> Res.string.appels_close_failed
    }
    InfoBox(Lucide.Info, Modifier.testTag(CallTags.CLOSE_OUTCOME)) { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium) }
}

/**
 * "Vous quittez PPF" (AP-08, point of attention 12): the host of the page, the safe choice first
 * and most visible ("Rester sur PPF"), then "Ouvrir la page". For a fundraising, a word on fake ones.
 */
@Composable
internal fun LeaveDialog(link: LeavingLink, onStay: () -> Unit, onDone: () -> Unit) {
    LeavePpfDialog(
        url = link.url,
        title = stringResource(Res.string.appels_leave_title),
        text = stringResource(Res.string.appels_leave_text, leavingHost(link.url)),
        stayLabel = stringResource(Res.string.appels_leave_stay),
        openLabel = stringResource(Res.string.appels_leave_open),
        onStay = onStay,
        onDone = onDone,
        tags = LeaveDialogTags(CallTags.LEAVE_DIALOG, CallTags.LEAVE_STAY, CallTags.LEAVE_OPEN),
        note = if (link.fundraising) stringResource(Res.string.appels_leave_fundraising) else null,
    )
}

/** AP-09: the report of a closing, then "Garder l'appel ouvert" first and "Clore l'appel" second. */
@Composable
internal fun CloseDialog(call: Call, signer: EventSigner?, library: CallsLibrary, onKeep: () -> Unit, onClosed: (CallSendOutcome) -> Unit) {
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var report by remember(call.address) { mutableStateOf("") }
    var working by remember(call.address) { mutableStateOf(false) }
    val issues = CallRules.checkReport(report)
    AlertDialog(
        onDismissRequest = { if (!working) onKeep() },
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.testTag(CallTags.CLOSE_DIALOG),
        title = { Text(stringResource(Res.string.appels_close_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.appels_close_text), style = MaterialTheme.typography.bodyMedium)
                PpfTextField(
                    value = report,
                    onValueChange = { report = it },
                    placeholder = stringResource(Res.string.appels_close_placeholder),
                    modifier = Modifier.fillMaxWidth().testTag(CallTags.CLOSE_REPORT),
                    minLines = 4,
                )
                Text(stringResource(Res.string.appels_counter, report.trim().length, CallRules.REPORT_MAX), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                issues.forEach { Text(issueText(it, call.type), style = MaterialTheme.typography.bodySmall, color = palette.accentInk) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = onKeep, text = stringResource(Res.string.appels_close_keep), enabled = !working, modifier = Modifier.fillMaxWidth().testTag(CallTags.CLOSE_KEEP))
                PpfTextButton(
                    onClick = {
                        val author = signer ?: return@PpfTextButton
                        working = true
                        scope.launch {
                            val outcome = library.closeCall(author, call, report)
                            working = false
                            onClosed(outcome)
                        }
                    },
                    text = stringResource(Res.string.appels_close_confirm),
                    enabled = signer != null && issues.isEmpty() && !working,
                    color = palette.accentInk,
                    modifier = Modifier.testTag(CallTags.CLOSE_CONFIRM),
                )
            }
        },
    )
}

/** A warning before a change that cannot be undone, the safe choice first and most visible. */
@Composable
internal fun SafeDialog(title: String, text: String, keepLabel: String, confirmLabel: String, onKeep: () -> Unit, onConfirm: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.inkMuted,
        shape = MaterialTheme.shapes.medium,
        icon = { Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PpfPrimaryButton(onClick = onKeep, text = keepLabel, modifier = Modifier.fillMaxWidth().testTag(CallTags.KEEP))
                PpfTextButton(onClick = onConfirm, text = confirmLabel, color = palette.accentInk, modifier = Modifier.testTag(CallTags.CONFIRM))
            }
        },
    )
}
