package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.politisationpopulairefrancaise.core.decider.ConsultationReminders
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.DocumentRequestNote
import org.politisationpopulairefrancaise.core.decider.DocumentRequestReminders
import org.politisationpopulairefrancaise.core.decider.ElectionCalendar
import org.politisationpopulairefrancaise.core.decider.ElectionReminderKind
import org.politisationpopulairefrancaise.core.decider.ElectionReminders
import org.politisationpopulairefrancaise.core.decider.PlannedElectionReminder
import org.politisationpopulairefrancaise.core.decider.PublicConsultation
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_closing_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_closing_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_document_cada_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_document_cada_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_document_no_answer_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_document_no_answer_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_eve_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_eve_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_opening_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_opening_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_proxy_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_proxy_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_registration_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_registration_title

/** The words of one reminder, written on the device when it is programmed. */
data class ReminderText(val title: String, val detail: String?)

/**
 * The reminders of « Décider » (`docs/integration/rappels.md`): those of the elections (DE-28, step 3) and of the official
 * consultations (DE-27). Off by default (principle 10), asked on the page of the election or of the consultation,
 * programmed and kept on the device only ([ReminderStore]). They reach the notification centre in the category « Votes
 * et pétitions », which asking for them turns on, through the system's permission, as Mon espace does ([settings]); the
 * user can turn it off there, and the page then says the reminders will not show. Planned again each time the contents
 * of « Décider » are read, so a decree or a notice that moves a date moves the reminders with it.
 */
class DeciderRemindersState(
    private val store: ReminderStore,
    private val settings: NotificationsSettingsState?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
    private val electionTexts: suspend (ElectionCalendar, PlannedElectionReminder) -> ReminderText = ::electionReminderText,
    private val consultationTexts: suspend (PublicConsultation, String) -> ReminderText = ::consultationReminderText,
    private val documentTexts: suspend (DocumentRequestNote, String) -> ReminderText = ::documentReminderText,
) {
    /** Bumped at each change of the store, so the pages read it again. */
    var version: Int by mutableStateOf(0)
        private set

    /** Whether the category of the reminders shows anything; true without notifications to manage (tests). */
    val categoryOn: Boolean get() = settings?.settings?.isEnabled(CATEGORY) ?: true

    /** The system refused the notifications: the reminders reach the centre of the application only. */
    val permissionRefused: Boolean get() = settings?.permissionRefused == true

    /** The reminders of [owner] still to come, the nearest first. */
    fun upcomingOf(owner: String): List<Reminder> = store.upcomingOf(owner, clock())

    /** Removes one reminder; a new planning does not bring it back. */
    fun remove(id: String) {
        store.remove(id)
        version++
    }

    /** Forgets the plan of [owner] and all its reminders (« Ne plus me rappeler »). */
    fun stop(owner: String) {
        store.dropPlan(owner)
        version++
    }

    /** Turns on « Votes et pétitions », after the system's permission, as its switch in Mon espace does. */
    fun turnCategoryOn() {
        val settings = settings ?: return
        if (!settings.settings.isEnabled(CATEGORY)) settings.toggleCategory(CATEGORY)
    }

    // The elections (DE-28).

    /** The kinds of reminders asked for [election], or null when the user did not ask for any. */
    fun kindsOf(election: ElectionCalendar): Set<ElectionReminderKind>? =
        store.plan(ElectionReminders.owner(election))?.kinds?.mapNotNull { ElectionReminderKind.fromKey(it) }?.toSet()

    fun upcoming(election: ElectionCalendar): List<Reminder> = upcomingOf(ElectionReminders.owner(election))

    /** « Me rappeler ces dates »: every kind of reminder, and the category turned on if it was off. */
    fun ask(election: ElectionCalendar) {
        store.setPlan(ElectionReminders.owner(election), ElectionReminders.DEFAULT_KINDS.mapTo(mutableSetOf()) { it.key })
        turnCategoryOn()
        replanNow { replan(election) }
    }

    fun setKind(election: ElectionCalendar, kind: ElectionReminderKind, on: Boolean) {
        val kinds = kindsOf(election) ?: return
        val next = if (on) kinds + kind else kinds - kind
        store.setPlan(ElectionReminders.owner(election), next.mapTo(mutableSetOf()) { it.key })
        replanNow { replan(election) }
    }

    fun stop(election: ElectionCalendar) = stop(ElectionReminders.owner(election))

    // The official consultations (DE-27).

    /** Whether the user asked for the reminders of [consultation]. */
    fun isAsked(consultation: PublicConsultation): Boolean = store.plan(ConsultationReminders.owner(consultation)) != null

    fun upcoming(consultation: PublicConsultation): List<Reminder> = upcomingOf(ConsultationReminders.owner(consultation))

    /** « Me rappeler cette consultation »: its opening while it is to come, and three days before its last day. */
    fun ask(consultation: PublicConsultation) {
        store.setPlan(ConsultationReminders.owner(consultation), setOf(ConsultationReminders.OPENING, ConsultationReminders.CLOSING))
        turnCategoryOn()
        replanNow { replan(consultation) }
    }

    fun stop(consultation: PublicConsultation) = stop(ConsultationReminders.owner(consultation))

    // The requests for administrative documents (DE-31): planned from the days the person noted, not from the contents.

    /** Whether the user asked for the reminders of the deadlines of [note]. */
    fun isAsked(note: DocumentRequestNote): Boolean = store.plan(DocumentRequestReminders.owner(note)) != null

    fun upcoming(note: DocumentRequestNote): List<Reminder> = upcomingOf(DocumentRequestReminders.owner(note))

    /** « Me rappeler les délais »: the day after a month of silence, and seven days before the last day for the CADA. */
    fun ask(note: DocumentRequestNote) {
        store.setPlan(DocumentRequestReminders.owner(note), setOf(DocumentRequestReminders.NO_ANSWER, DocumentRequestReminders.CADA))
        turnCategoryOn()
        replanNow { replan(note) }
    }

    fun stop(note: DocumentRequestNote) = stop(DocumentRequestReminders.owner(note))

    /** The days of [note] changed (document received, written refusal, CADA asked): its reminders follow, when asked. */
    fun changed(note: DocumentRequestNote) {
        if (isAsked(note)) replanNow { replan(note) }
    }

    /** Plans again the reminders of every election and consultation the user asked for, from the contents just read. */
    suspend fun replanAll(catalog: DeciderCatalog) {
        val elections = store.plans(ElectionReminders.OWNER_PREFIX)
        val consultations = store.plans(ConsultationReminders.OWNER_PREFIX)
        if (elections.isEmpty() && consultations.isEmpty()) return
        // A content absent from those read (not synchronized yet) keeps its reminders as they are.
        elections.forEach { plan -> catalog.elections.firstOrNull { ElectionReminders.owner(it) == plan.owner }?.let { replan(it) } }
        consultations.forEach { plan -> catalog.consultations.firstOrNull { ConsultationReminders.owner(it) == plan.owner }?.let { replan(it) } }
        version++
    }

    private fun replanNow(replan: suspend () -> Unit) {
        version++
        scope.launch {
            replan()
            version++
        }
    }

    private suspend fun replan(election: ElectionCalendar) {
        val owner = ElectionReminders.owner(election)
        val kinds = kindsOf(election) ?: return
        val now = clock()
        val planned = ElectionReminders.plan(election, kinds, now).map { p ->
            val text = electionTexts(election, p)
            reminder(ElectionReminders.reminderId(election, p.key), owner, text, ELECTION_TARGET + election.election.id, p.dueAt, now)
        }
        store.replaceFuture(owner, planned, now)
    }

    private suspend fun replan(consultation: PublicConsultation) {
        val owner = ConsultationReminders.owner(consultation)
        val kinds = store.plan(owner)?.kinds ?: return
        val now = clock()
        val planned = ConsultationReminders.plan(consultation, now).filter { (key, _) -> key in kinds }.map { (key, dueAt) ->
            val text = consultationTexts(consultation, key)
            reminder(ConsultationReminders.reminderId(consultation, key), owner, text, CONSULTATION_TARGET + consultation.article.slug, dueAt, now)
        }
        store.replaceFuture(owner, planned, now)
    }

    private suspend fun replan(note: DocumentRequestNote) {
        val owner = DocumentRequestReminders.owner(note)
        val kinds = store.plan(owner)?.kinds ?: return
        val now = clock()
        val planned = DocumentRequestReminders.plan(note, now).filter { (key, _) -> key in kinds }.map { (key, dueAt) ->
            reminder(DocumentRequestReminders.reminderId(note, key), owner, documentTexts(note, key), DOCUMENT_TARGET, dueAt, now)
        }
        store.replaceFuture(owner, planned, now)
    }

    private fun reminder(id: String, owner: String, text: ReminderText, target: String, dueAt: Long, now: Long) =
        Reminder(id = id, owner = owner, category = CATEGORY, title = text.title, detail = text.detail, target = target, dueAt = dueAt, createdAt = now)

    companion object {
        /** The reminders of « Décider » are notified with the votes, the petitions and the consultations. */
        val CATEGORY: NotificationCategory = NotificationCategory.VOTES

        /** Where a reminder of an election leads: `ppf:decider/election/<id of the election>`. */
        const val ELECTION_TARGET: String = "ppf:decider/election/"

        /** Where a reminder of a consultation leads: `ppf:decider/consultation/<slug>`. */
        const val CONSULTATION_TARGET: String = "ppf:decider/consultation/"

        /** Where a reminder of a request for a document leads: the page of the requests (DE-31). */
        const val DOCUMENT_TARGET: String = "ppf:decider/documents"
    }
}

/** The texts of a reminder of a request for a document: the administration and the last day to ask the CADA. */
suspend fun documentReminderText(note: DocumentRequestNote, key: String): ReminderText {
    val cadaBy = DocumentRequestReminders.cadaBy(note).toFrench()
    return when (key) {
        DocumentRequestReminders.NO_ANSWER -> ReminderText(
            getString(Res.string.decider_reminder_document_no_answer_title),
            getString(Res.string.decider_reminder_document_no_answer_detail, note.administration, cadaBy),
        )
        else -> ReminderText(
            getString(Res.string.decider_reminder_document_cada_title),
            getString(Res.string.decider_reminder_document_cada_detail, note.administration, cadaBy),
        )
    }
}

/** The texts of a reminder of an election, named after the title of the election (an example keeps its [EXEMPLE]). */
suspend fun electionReminderText(election: ElectionCalendar, planned: PlannedElectionReminder): ReminderText {
    val label = election.article.title
    val round = election.rounds.firstOrNull { it.number == planned.round }
    return when (planned.kind) {
        ElectionReminderKind.REGISTRATION -> ReminderText(
            getString(Res.string.decider_reminder_registration_title),
            getString(Res.string.decider_reminder_registration_detail, label, getString(planned.deadline?.label ?: Res.string.decider_reminder_registration_title), planned.date.toFrench()),
        )
        ElectionReminderKind.PROXY -> ReminderText(
            getString(Res.string.decider_reminder_proxy_title),
            getString(Res.string.decider_reminder_proxy_detail, label, planned.round ?: 1, planned.date.toFrench()),
        )
        ElectionReminderKind.EVE -> ReminderText(
            getString(Res.string.decider_reminder_eve_title),
            getString(Res.string.decider_reminder_eve_detail, label, planned.round ?: 1, planned.date.toFrench(), frenchClock(round?.closingHour ?: 20, round?.closingMinute ?: 0)),
        )
    }
}

/** The texts of a reminder of a consultation, named after its title (an example keeps its [EXEMPLE]). */
suspend fun consultationReminderText(consultation: PublicConsultation, key: String): ReminderText = when (key) {
    ConsultationReminders.OPENING -> ReminderText(
        getString(Res.string.decider_reminder_opening_title),
        getString(Res.string.decider_reminder_opening_detail, consultation.title, consultation.closesOn.toFrench()),
    )
    else -> ReminderText(
        getString(Res.string.decider_reminder_closing_title),
        getString(Res.string.decider_reminder_closing_detail, consultation.title, consultation.closesOn.toFrench()),
    )
}

/**
 * The reminders of « Décider » for the whole run of the application, planned again after each reading of its contents.
 * Null without a database on the device (previews).
 */
@Composable
fun rememberDeciderReminders(
    dependencies: AppDependencies,
    settings: NotificationsSettingsState?,
    catalog: DeciderCatalog,
): DeciderRemindersState? {
    val database = dependencies.database ?: return null
    val scope = rememberCoroutineScope()
    val state = remember(database, settings) { DeciderRemindersState(ReminderStore(database), settings, scope) }
    LaunchedEffect(state, catalog) { runCatching { state.replanAll(catalog) } }
    return state
}
