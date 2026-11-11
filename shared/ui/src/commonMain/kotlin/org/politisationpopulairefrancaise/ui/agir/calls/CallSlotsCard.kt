package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallSlot
import org.politisationpopulairefrancaise.core.agir.calls.CallSlots
import org.politisationpopulairefrancaise.core.agir.calls.SlotApi
import org.politisationpopulairefrancaise.core.agir.calls.SlotDraft
import org.politisationpopulairefrancaise.core.agir.calls.MySlotSignup
import org.politisationpopulairefrancaise.core.agir.calls.SlotClient
import org.politisationpopulairefrancaise.core.agir.calls.SlotOutcome
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupStore
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignups
import org.politisationpopulairefrancaise.core.agir.calls.SlotWithdrawal
import org.politisationpopulairefrancaise.core.agir.calls.slots
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the slots (AP-11). */
object SlotTags {
    const val CARD = "slots-card"
    const val SLOT = "slots-slot-" // followed by the id of the slot
    const val SIGNUP = "slots-signup-"
    const val WITHDRAW = "slots-withdraw-"
    const val DIALOG = "slots-dialog"
    const val NAME = "slots-name"
    const val SEND = "slots-send"
    const val RESULT = "slots-result"
    const val CLOSE = "slots-close"
    const val ORGANIZER = "slots-organizer"
    const val ORGANIZER_LIST = "slots-organizer-list"
    const val ORGANIZER_REMOVE = "slots-organizer-remove-"
    const val EDITOR = "slots-editor-"
    const val EDITOR_LABEL = "slots-editor-label-"
    const val EDITOR_DATE = "slots-editor-date-"
    const val EDITOR_START = "slots-editor-start-"
    const val EDITOR_END = "slots-editor-end-"
    const val EDITOR_PLACES = "slots-editor-places-"
    const val EDITOR_REMOVE = "slots-editor-remove-"
    const val EDITOR_ADD = "slots-editor-add"
}

/**
 * The slots of the calls on this device (AP-11): the signups kept here to withdraw them ([signups], none in memory
 * without a database), the server ([client]) and the reminders ([reminders]). Nothing about a signup leaves the device
 * but the envelope for the organizer and the hash of its token.
 */
class SlotsLibrary(
    private val signups: SlotSignupStore?,
    private val client: SlotClient?,
    private val reminders: ReminderStore?,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val memory = mutableListOf<MySlotSignup>()

    val connected: Boolean get() = client != null

    fun now(): Long = clock()

    fun mine(call: Call): List<MySlotSignup> = signups?.ofCall(call.address.toString()) ?: memory.filter { it.callAddress == call.address.toString() }

    suspend fun counts(call: Call): Map<String, Int>? = client?.counts(call.address.toString())

    suspend fun signUp(call: Call, slot: CallSlot, name: String, reminderTitle: String): SlotOutcome {
        val client = client ?: return SlotOutcome.Unreachable
        // The proof of work takes a moment: off the main thread.
        val prepared = withContext(Dispatchers.Default) { SlotSignups.prepare(call, slot, name) }
        val outcome = client.signUp(prepared.request)
        if (outcome is SlotOutcome.Done) {
            val mine = MySlotSignup(outcome.id, call.address.toString(), slot.id, prepared.token, slot.end)
            signups?.add(mine) ?: memory.add(mine)
            val due = slot.start - REMINDER_BEFORE
            if (due > clock()) {
                reminders?.schedule(Reminder(reminderId(outcome.id), reminderId(outcome.id), NotificationCategory.MOBILISATIONS, reminderTitle, call.displayTitle, dueAt = due, createdAt = clock()))
            }
        }
        return outcome
    }

    /** Withdraws [signup]; true once the server no longer has it (withdrawn now, or already erased). */
    suspend fun withdraw(signup: MySlotSignup): Boolean {
        val client = client ?: return false
        val outcome = client.withdraw(SlotWithdrawal(signup.id, signup.token))
        if (outcome is SlotOutcome.Done || outcome is SlotOutcome.Refused) {
            signups?.remove(signup.id) ?: memory.removeAll { it.id == signup.id }
            reminders?.remove(reminderId(signup.id))
            return true
        }
        return false
    }

    /** The signups to each slot of [call] (envelope id and name), decrypted with the key of its author; null without an answer. */
    suspend fun names(call: Call, organizer: MessagingSigner): Map<String, List<Pair<String, String>>>? {
        val envelopes = client?.signups(call.address.toString()) ?: return null
        return withContext(Dispatchers.Default) {
            envelopes.mapNotNull { SlotSignups.openWithId(it, organizer, call.address.toString()) }.groupBy({ it.second.slot }, { it.first to it.second.name })
        }
    }

    /** The organizer removes a signup (a fake one, a duplicate); true once the server no longer has it. */
    suspend fun remove(signupId: String, organizer: EventSigner): Boolean {
        val outcome = client?.remove(signupId, organizer) ?: return false
        return outcome is SlotOutcome.Done
    }

    private fun reminderId(signupId: String) = "agir:slot:$signupId"

    companion object {
        /** The reminder of a slot: two hours before it. */
        const val REMINDER_BEFORE: Long = 2 * 60 * 60
    }
}

/** AP-11: the slots of a call, with their places, the user's signups and, for the author, the names signed up. */
@Composable
internal fun SlotsCard(call: Call, slotsLibrary: SlotsLibrary, organizer: MessagingSigner?, organizerSigner: EventSigner? = null) {
    val slots = call.slots
    if (slots.isEmpty()) return
    val palette = PpfTheme.palette
    val scope = rememberCoroutineScope()
    var counts by remember(call.address) { mutableStateOf<Map<String, Int>?>(null) }
    var mine by remember(call.address) { mutableStateOf(slotsLibrary.mine(call)) }
    var signingUp by remember(call.address) { mutableStateOf<CallSlot?>(null) }
    var failed by remember(call.address) { mutableStateOf(false) }
    var names by remember(call.address) { mutableStateOf<Map<String, List<Pair<String, String>>>?>(null) }
    var removeFailed by remember(call.address) { mutableStateOf(false) }
    var namesFailed by remember(call.address) { mutableStateOf(false) }
    LaunchedEffect(call.address) { counts = slotsLibrary.counts(call) }
    val now = slotsLibrary.now()
    PpfCard(Modifier.fillMaxWidth().testTag(SlotTags.CARD)) {
        Text(stringResource(Res.string.slots_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.slots_intro), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        slots.forEach { slot ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().testTag(SlotTags.SLOT + slot.id), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(slot.label, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.appels_period, parisDateTime(slot.start), parisDateTime(slot.end)), style = MaterialTheme.typography.bodyMedium)
                val taken = counts?.get(slot.id) ?: counts?.let { 0 }
                Text(
                    if (taken != null) stringResource(Res.string.slots_places, taken, slot.places) else stringResource(Res.string.slots_places_unknown, slot.places),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
                val signup = mine.firstOrNull { it.slotId == slot.id }
                when {
                    signup != null -> {
                        Text(stringResource(Res.string.slots_mine), style = MaterialTheme.typography.bodyLarge, color = palette.success)
                        PpfTextButton(
                            onClick = {
                                scope.launch {
                                    failed = !slotsLibrary.withdraw(signup)
                                    mine = slotsLibrary.mine(call)
                                    counts = slotsLibrary.counts(call) ?: counts
                                }
                            },
                            text = stringResource(Res.string.slots_withdraw),
                            modifier = Modifier.testTag(SlotTags.WITHDRAW + slot.id),
                        )
                    }
                    slot.end <= now -> PpfSecondaryButton({}, stringResource(Res.string.slots_over), enabled = false, modifier = Modifier.testTag(SlotTags.SIGNUP + slot.id))
                    taken != null && taken >= slot.places -> PpfSecondaryButton({}, stringResource(Res.string.slots_full), enabled = false, modifier = Modifier.testTag(SlotTags.SIGNUP + slot.id))
                    else -> PpfPrimaryButton({ signingUp = slot }, stringResource(Res.string.slots_signup), enabled = call.isOpen, modifier = Modifier.testTag(SlotTags.SIGNUP + slot.id))
                }
                names?.let { all ->
                    val here = all[slot.id].orEmpty().sortedBy { it.second }
                    if (here.isEmpty()) {
                        Text(stringResource(Res.string.slots_organizer_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(SlotTags.ORGANIZER_LIST))
                    }
                    here.forEach { (id, name) ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.testTag(SlotTags.ORGANIZER_LIST)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (organizerSigner != null) {
                                PpfTextButton(
                                    onClick = {
                                        scope.launch {
                                            val done = slotsLibrary.remove(id, organizerSigner)
                                            removeFailed = !done
                                            if (done) {
                                                names = names?.mapValues { (_, list) -> list.filterNot { it.first == id } }
                                                counts = slotsLibrary.counts(call) ?: counts
                                            }
                                        }
                                    },
                                    text = stringResource(Res.string.slots_organizer_remove),
                                    modifier = Modifier.testTag(SlotTags.ORGANIZER_REMOVE + id),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (removeFailed) Text(stringResource(Res.string.slots_organizer_remove_failed), style = MaterialTheme.typography.bodySmall, color = palette.accentInk)
        if (failed) Text(stringResource(Res.string.slots_withdraw_failed), style = MaterialTheme.typography.bodySmall, color = palette.accentInk)
        if (organizer != null) {
            Spacer(Modifier.height(12.dp))
            PpfSecondaryButton(
                onClick = {
                    scope.launch {
                        val read = slotsLibrary.names(call, organizer)
                        namesFailed = read == null
                        names = read
                    }
                },
                text = stringResource(Res.string.slots_organizer_show),
                modifier = Modifier.testTag(SlotTags.ORGANIZER),
            )
            Text(
                stringResource(if (namesFailed) Res.string.slots_organizer_failed else Res.string.slots_organizer_note),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
        }
    }
    signingUp?.let { slot ->
        SignupDialog(call, slot, slotsLibrary, onDismiss = { signingUp = null }, onDone = {
            mine = slotsLibrary.mine(call)
            scope.launch { counts = slotsLibrary.counts(call) ?: counts }
        })
    }
}

private sealed interface SignupStep {
    data object Typing : SignupStep
    data object Sending : SignupStep
    data class Done(val outcome: SlotOutcome?) : SignupStep
}

@Composable
private fun SignupDialog(call: Call, slot: CallSlot, slotsLibrary: SlotsLibrary, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<SignupStep>(SignupStep.Typing) }
    val reminderTitle = stringResource(Res.string.slots_reminder_title, slot.label)
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = { if (step != SignupStep.Sending) onDismiss() },
        modifier = Modifier.testTag(SlotTags.DIALOG),
        title = { Text(stringResource(Res.string.slots_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val current = step) {
                    is SignupStep.Done -> Text(
                        stringResource(
                            when (current.outcome) {
                                is SlotOutcome.Done -> Res.string.slots_result_done
                                SlotOutcome.Full -> Res.string.slots_result_full
                                is SlotOutcome.Refused -> Res.string.slots_result_refused
                                SlotOutcome.Unreachable -> Res.string.slots_result_unreachable
                                null -> Res.string.slots_no_server
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(SlotTags.RESULT).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    else -> {
                        Text(slot.label, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.appels_period, parisDateTime(slot.start), parisDateTime(slot.end)), style = MaterialTheme.typography.bodyMedium)
                        PpfTextField(
                            value = name,
                            onValueChange = { name = it.take(SlotApi.MAX_NAME) },
                            placeholder = stringResource(Res.string.slots_dialog_name),
                            modifier = Modifier.fillMaxWidth().testTag(SlotTags.NAME),
                            singleLine = true,
                        )
                        Text(stringResource(Res.string.slots_dialog_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                is SignupStep.Done -> PpfPrimaryButton(onClick = onDismiss, text = stringResource(Res.string.slots_close), modifier = Modifier.testTag(SlotTags.CLOSE))
                else -> PpfPrimaryButton(
                    onClick = {
                        if (!slotsLibrary.connected) {
                            step = SignupStep.Done(null)
                            return@PpfPrimaryButton
                        }
                        step = SignupStep.Sending
                        scope.launch {
                            val outcome = try {
                                slotsLibrary.signUp(call, slot, name, reminderTitle)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                SlotOutcome.Unreachable
                            }
                            step = SignupStep.Done(outcome)
                            onDone()
                        }
                    },
                    text = stringResource(if (step == SignupStep.Sending) Res.string.slots_dialog_sending else Res.string.slots_dialog_send),
                    enabled = step == SignupStep.Typing,
                    modifier = Modifier.testTag(SlotTags.SEND),
                )
            }
        },
        dismissButton = {
            if (step == SignupStep.Typing) PpfTextButton(onClick = onDismiss, text = stringResource(Res.string.slots_cancel))
        },
    )
}

/** AP-11: the slots of the call being written, for the types that may be cut into slots. */
@Composable
internal fun SlotsEditor(composer: CallComposer) {
    val draft = composer.draft
    val palette = PpfTheme.palette
    fun update(index: Int, change: (SlotDraft) -> SlotDraft) {
        composer.draft = draft.copy(slots = draft.slots.mapIndexed { i, slot -> if (i == index) change(slot) else slot })
    }
    Text(stringResource(Res.string.slots_editor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
    Text(stringResource(Res.string.slots_editor_help), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    draft.slots.forEachIndexed { index, slot ->
        Spacer(Modifier.height(12.dp))
        PpfCard(Modifier.fillMaxWidth().testTag(SlotTags.EDITOR + index)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PpfTextField(slot.label, { value -> update(index) { it.copy(label = value.take(CallSlots.MAX_LABEL)) } }, stringResource(Res.string.slots_editor_label), Modifier.fillMaxWidth().testTag(SlotTags.EDITOR_LABEL + index), singleLine = true)
                PpfTextField(slot.date, { value -> update(index) { it.copy(date = value.take(10)) } }, stringResource(Res.string.slots_editor_date), Modifier.fillMaxWidth().testTag(SlotTags.EDITOR_DATE + index), singleLine = true)
                PpfTextField(slot.startTime, { value -> update(index) { it.copy(startTime = value.take(5)) } }, stringResource(Res.string.slots_editor_start), Modifier.fillMaxWidth().testTag(SlotTags.EDITOR_START + index), singleLine = true)
                PpfTextField(slot.endTime, { value -> update(index) { it.copy(endTime = value.take(5)) } }, stringResource(Res.string.slots_editor_end), Modifier.fillMaxWidth().testTag(SlotTags.EDITOR_END + index), singleLine = true)
                PpfTextField(slot.places, { value -> update(index) { it.copy(places = value.filter(Char::isDigit).take(3)) } }, stringResource(Res.string.slots_editor_places), Modifier.fillMaxWidth().testTag(SlotTags.EDITOR_PLACES + index), singleLine = true)
                PpfTextButton(
                    onClick = { composer.draft = draft.copy(slots = draft.slots.filterIndexed { i, _ -> i != index }) },
                    text = stringResource(Res.string.slots_editor_remove),
                    modifier = Modifier.testTag(SlotTags.EDITOR_REMOVE + index),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PpfSecondaryButton(
        onClick = {
            // A new slot starts on the day of the previous one, to type a series quickly.
            val previous = draft.slots.lastOrNull()
            composer.draft = draft.copy(slots = draft.slots + SlotDraft(date = previous?.date.orEmpty(), places = previous?.places.orEmpty()))
        },
        text = stringResource(Res.string.slots_editor_add),
        enabled = draft.slots.size < CallSlots.MAX_SLOTS,
        modifier = Modifier.testTag(SlotTags.EDITOR_ADD),
    )
}
