package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.ElectionCalendar
import org.politisationpopulairefrancaise.core.decider.ElectionReminderKind
import org.politisationpopulairefrancaise.core.decider.ElectionReminders
import org.politisationpopulairefrancaise.core.decider.PlannedElectionReminder
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.reminders.Reminder
import org.politisationpopulairefrancaise.core.reminders.ReminderStore
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.agir.security.ToggleRow
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.notifications.NotificationsSettingsState
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_eve_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_eve_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_proxy_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_proxy_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_registration_detail
import org.politisationpopulairefrancaise.ui.resources.decider_reminder_registration_title
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_ask
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_at
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_note
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_off
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_category_on
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_computer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_eve
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_eve_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_proxy
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_proxy_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_registration
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_kind_registration_hint
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_none
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_offer
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_permission_refused
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_remove
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_stop
import org.politisationpopulairefrancaise.ui.resources.decider_reminders_title
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the reminders of an election (DE-28, step 3). */
object ElectionReminderTags {
    const val SECTION = "decider-reminders"
    const val ASK = "decider-reminders-ask"
    const val STOP = "decider-reminders-stop"
    const val CATEGORY_OFF = "decider-reminders-category-off"
    const val CATEGORY_ON = "decider-reminders-category-on"
    const val NONE = "decider-reminders-none"
    fun kind(kind: ElectionReminderKind) = "decider-reminders-kind-${kind.key}"
    fun reminder(key: String) = "decider-reminders-item-$key"
    fun remove(key: String) = "decider-reminders-remove-$key"
}

/** The words of one reminder, written on the device when it is programmed. */
data class ReminderText(val title: String, val detail: String?)

/**
 * The reminders of the elections (DE-28, step 3, `docs/integration/rappels.md`): off by default (principle 10),
 * asked on the page of an election, programmed and kept on the device only ([ReminderStore]). They reach the
 * notification centre in the category « Votes et pétitions », which asking for them turns on, through the system's
 * permission, as Mon espace does ([settings]); the user can turn it off there, and the page then says the reminders
 * will not show. Planned again each time the contents of « Décider » are read, so a decree that moves a date moves
 * the reminders with it.
 */
class ElectionRemindersState(
    private val store: ReminderStore,
    private val settings: NotificationsSettingsState?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
    private val texts: suspend (ElectionCalendar, PlannedElectionReminder) -> ReminderText = ::electionReminderText,
) {
    /** Bumped at each change of the store, so the page reads it again. */
    var version: Int by mutableStateOf(0)
        private set

    /** Whether the category of the reminders shows anything; true without notifications to manage (tests). */
    val categoryOn: Boolean get() = settings?.settings?.isEnabled(CATEGORY) ?: true

    /** The system refused the notifications: the reminders reach the centre of the application only. */
    val permissionRefused: Boolean get() = settings?.permissionRefused == true

    /** The kinds of reminders asked for [election], or null when the user did not ask for any. */
    fun kindsOf(election: ElectionCalendar): Set<ElectionReminderKind>? =
        store.plan(ElectionReminders.owner(election))?.kinds?.mapNotNull { ElectionReminderKind.fromKey(it) }?.toSet()

    /** The reminders of [election] still to come, the nearest first. */
    fun upcoming(election: ElectionCalendar): List<Reminder> = store.upcomingOf(ElectionReminders.owner(election), clock())

    /** « Me rappeler ces dates »: every kind of reminder, and the category turned on if it was off. */
    fun ask(election: ElectionCalendar) {
        store.setPlan(ElectionReminders.owner(election), ElectionReminders.DEFAULT_KINDS.mapTo(mutableSetOf()) { it.key })
        turnCategoryOn()
        replanNow(election)
    }

    fun setKind(election: ElectionCalendar, kind: ElectionReminderKind, on: Boolean) {
        val kinds = kindsOf(election) ?: return
        val next = if (on) kinds + kind else kinds - kind
        store.setPlan(ElectionReminders.owner(election), next.mapTo(mutableSetOf()) { it.key })
        replanNow(election)
    }

    /** « Ne plus me rappeler cette élection »: the plan and every reminder of the election are forgotten. */
    fun stop(election: ElectionCalendar) {
        store.dropPlan(ElectionReminders.owner(election))
        version++
    }

    /** Removes one reminder; a new planning does not bring it back. */
    fun remove(id: String) {
        store.remove(id)
        version++
    }

    /** Turns on « Votes et pétitions », after the system's permission, as its switch in Mon espace does. */
    fun turnCategoryOn() {
        val settings = settings ?: return
        if (!settings.settings.isEnabled(CATEGORY)) settings.toggleCategory(CATEGORY)
    }

    /** Plans again the reminders of every election the user asked for, from the contents just read. */
    suspend fun replanAll(catalog: DeciderCatalog) {
        val plans = store.plans(ElectionReminders.OWNER_PREFIX)
        if (plans.isEmpty()) return
        plans.forEach { plan ->
            // An election absent from the contents read (not synchronized yet) keeps its reminders as they are.
            catalog.elections.firstOrNull { ElectionReminders.owner(it) == plan.owner }?.let { replan(it) }
        }
        version++
    }

    private fun replanNow(election: ElectionCalendar) {
        version++
        scope.launch {
            replan(election)
            version++
        }
    }

    private suspend fun replan(election: ElectionCalendar) {
        val owner = ElectionReminders.owner(election)
        val kinds = kindsOf(election) ?: return
        val now = clock()
        val planned = ElectionReminders.plan(election, kinds, now).map { p ->
            val text = texts(election, p)
            Reminder(
                id = ElectionReminders.reminderId(election, p.key),
                owner = owner,
                category = CATEGORY,
                title = text.title,
                detail = text.detail,
                target = TARGET_PREFIX + election.election.id,
                dueAt = p.dueAt,
                createdAt = now,
            )
        }
        store.replaceFuture(owner, planned, now)
    }

    companion object {
        /** The reminders of the elections are notified with the votes and the petitions. */
        val CATEGORY: NotificationCategory = NotificationCategory.VOTES

        /** Where a reminder of an election leads: `ppf:decider/election/<id of the election>`. */
        const val TARGET_PREFIX: String = "ppf:decider/election/"
    }
}

/** The texts of a reminder, in the language of the interface, named after the title of the election (an example keeps its [EXEMPLE]). */
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

/**
 * The reminders of the elections for the whole run of the application, planned again after each reading of the
 * contents of « Décider ». Null without a database on the device (previews).
 */
@Composable
fun rememberElectionReminders(
    dependencies: AppDependencies,
    settings: NotificationsSettingsState?,
    catalog: DeciderCatalog,
): ElectionRemindersState? {
    val database = dependencies.database ?: return null
    val scope = rememberCoroutineScope()
    val state = remember(database, settings) { ElectionRemindersState(ReminderStore(database), settings, scope) }
    LaunchedEffect(state, catalog) { runCatching { state.replanAll(catalog) } }
    return state
}

/**
 * The section « Rappels » of the page of an election: the offer while nothing is asked (« Me rappeler ces dates »),
 * then a switch per kind of reminder, the reminders to come with their date, each one removable, and the way to
 * stop them all.
 */
@Composable
internal fun ElectionRemindersSection(election: ElectionCalendar, reminders: ElectionRemindersState) {
    val palette = PpfTheme.palette
    // Read again after each change of the store.
    val version = reminders.version
    val kinds = remember(election, version) { reminders.kindsOf(election) }
    val upcoming = remember(election, version) { reminders.upcoming(election) }
    Column(Modifier.fillMaxWidth().testTag(ElectionReminderTags.SECTION)) {
        Text(stringResource(Res.string.decider_reminders_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (kinds == null) {
            Text(stringResource(Res.string.decider_reminders_offer), style = MaterialTheme.typography.bodyMedium)
            if (!reminders.categoryOn) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.decider_reminders_category_note), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            PpfPrimaryButton(onClick = { reminders.ask(election) }, text = stringResource(Res.string.decider_reminders_ask), icon = Lucide.Bell, modifier = Modifier.testTag(ElectionReminderTags.ASK))
        } else {
            ChosenReminders(election, reminders, kinds, upcoming)
        }
    }
}

/** What the user asked for an election: the category, a switch per kind, the reminders to come, the way to stop. */
@Composable
private fun ChosenReminders(election: ElectionCalendar, reminders: ElectionRemindersState, kinds: Set<ElectionReminderKind>, upcoming: List<Reminder>) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth()) {
        if (!reminders.categoryOn) {
            InfoBox(Lucide.Bell, Modifier.testTag(ElectionReminderTags.CATEGORY_OFF)) {
                Text(stringResource(Res.string.decider_reminders_category_off), style = MaterialTheme.typography.bodyMedium)
                PpfTextButton(onClick = reminders::turnCategoryOn, text = stringResource(Res.string.decider_reminders_category_on), modifier = Modifier.testTag(ElectionReminderTags.CATEGORY_ON))
            }
            Spacer(Modifier.height(8.dp))
        } else if (reminders.permissionRefused) {
            Text(stringResource(Res.string.decider_reminders_permission_refused), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            Spacer(Modifier.height(8.dp))
        }
        ElectionReminderKind.entries.forEach { kind ->
            ToggleRow(
                label = stringResource(kind.label),
                text = stringResource(kind.hint),
                checked = kind in kinds,
                onChange = { on -> reminders.setKind(election, kind, on) },
                modifier = Modifier.testTag(ElectionReminderTags.kind(kind)),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (upcoming.isEmpty()) {
            Text(stringResource(Res.string.decider_reminders_none), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.testTag(ElectionReminderTags.NONE))
        }
        upcoming.forEachIndexed { index, reminder ->
            if (index > 0) HorizontalDivider(color = palette.border)
            val key = reminder.id.substringAfterLast('/')
            val moment = ParisTime.moment(reminder.dueAt)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(ElectionReminderTags.reminder(key)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.decider_reminders_at, moment.date.toFrench(), frenchClock(moment.hour, moment.minute)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
                Spacer(Modifier.width(8.dp))
                PpfIconButton(Lucide.X, stringResource(Res.string.decider_reminders_remove), onClick = { reminders.remove(reminder.id) }, modifier = Modifier.testTag(ElectionReminderTags.remove(key)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.decider_reminders_computer), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        PpfTextButton(onClick = { reminders.stop(election) }, text = stringResource(Res.string.decider_reminders_stop), modifier = Modifier.testTag(ElectionReminderTags.STOP))
    }
}

private val ElectionReminderKind.label get() = when (this) {
    ElectionReminderKind.REGISTRATION -> Res.string.decider_reminders_kind_registration
    ElectionReminderKind.PROXY -> Res.string.decider_reminders_kind_proxy
    ElectionReminderKind.EVE -> Res.string.decider_reminders_kind_eve
}

private val ElectionReminderKind.hint get() = when (this) {
    ElectionReminderKind.REGISTRATION -> Res.string.decider_reminders_kind_registration_hint
    ElectionReminderKind.PROXY -> Res.string.decider_reminders_kind_proxy_hint
    ElectionReminderKind.EVE -> Res.string.decider_reminders_kind_eve_hint
}
