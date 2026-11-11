package org.politisationpopulairefrancaise.core.reminders

import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.device.DeviceErasable
import org.politisationpopulairefrancaise.core.notifications.NotificationCandidate
import org.politisationpopulairefrancaise.core.notifications.NotificationCategory
import org.politisationpopulairefrancaise.core.notifications.NotificationSource
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * A personal reminder, programmed on the device only (S-98, TE-09; contract `docs/integration/rappels.md`).
 * [title] and [detail] are written by the space that planned it, in the language of the interface; [target] is an
 * internal link (`ppf:<space>/...`) opened from the notification. [owner] names what planned it (for an election of
 * Décider: `decider:election:<id>`), so the space can plan again the reminders of that content when it changes.
 */
data class Reminder(
    val id: String,
    val owner: String,
    val category: NotificationCategory,
    val title: String,
    val detail: String? = null,
    val target: String? = null,
    /** When the reminder is due, in seconds since 1970-01-01 UTC. */
    val dueAt: Long,
    val createdAt: Long,
)

/**
 * What the user asked to be reminded of for one content: the [kinds] chosen (their meaning belongs to the space,
 * for an election `inscription`, `procuration`, `veille`), and the reminders [removed] one by one, which a new
 * planning never brings back.
 */
data class ReminderPlan(val owner: String, val kinds: Set<String>, val removed: Set<String>, val createdAt: Long)

/**
 * The reminders of the device. Nothing here ever leaves it: not the reminders, not what they are about. Each due
 * reminder reaches the notification centre through [RemindersSource] (one source per category of notification), and
 * the platform wakes the device at [nextDueAfter] (an alarm of the system on Android; while it is open elsewhere).
 * Part of the quick erase (S-44) as [ERASABLE_ID].
 */
class ReminderStore(
    private val database: PpfDatabase,
    private val clock: () -> Long = ::currentEpochSeconds,
) : DeviceErasable {

    private val queries get() = database.remindersQueries

    override val erasableId: String = ERASABLE_ID

    /** The plan of [owner], or null when the user did not ask to be reminded of it. */
    fun plan(owner: String): ReminderPlan? = queries.selectPlan(owner).executeAsOneOrNull()?.let { row ->
        ReminderPlan(row.owner, row.kinds.toKeySet(), row.removed.toKeySet(), row.created_at)
    }

    /** The plans whose owner starts with [prefix] (every followed election of Décider: `decider:election:`). */
    fun plans(prefix: String): List<ReminderPlan> =
        queries.selectPlans(prefix.likePrefix()).executeAsList()
            // LIKE ignores the case of ASCII letters: the exact prefix is checked again here.
            .filter { it.owner.startsWith(prefix) }
            .map { row -> ReminderPlan(row.owner, row.kinds.toKeySet(), row.removed.toKeySet(), row.created_at) }

    /**
     * Chooses the kinds of reminders of [owner] (an empty set is still a plan: the user follows the content without
     * any reminder). The reminders removed one by one stay removed.
     */
    fun setPlan(owner: String, kinds: Set<String>) {
        val before = plan(owner)
        queries.upsertPlan(owner, kinds.toKeyText(), before?.removed.orEmpty().toKeyText(), before?.createdAt ?: clock())
    }

    /** Forgets the plan of [owner] and all its reminders, past and future. */
    fun dropPlan(owner: String) = database.transaction {
        queries.deletePlan(owner)
        queries.deleteAllOf(owner)
    }

    /**
     * Replaces the future reminders of [owner] by [planned] (a space calls it each time it reads its content again):
     * those already due stay as they are, those the user removed are not brought back, and those not due after
     * [now] are not written. Old reminders, due more than [KEEP_PAST_SECONDS] ago, are dropped at the same time.
     */
    fun replaceFuture(owner: String, planned: List<Reminder>, now: Long = clock()) = database.transaction {
        val removed = plan(owner)?.removed.orEmpty()
        queries.deleteFutureOf(owner, now)
        planned.filter { it.owner == owner && it.dueAt > now && it.id !in removed }.forEach(::write)
        queries.deleteRemindersBefore(now - KEEP_PAST_SECONDS)
    }

    /** Programs one reminder that no planning manages (a reminder of an event, of a deadline chosen by the user). */
    fun schedule(reminder: Reminder) = write(reminder)

    /** Removes one reminder; when it belongs to a plan, a new planning will not bring it back. */
    fun remove(id: String) = database.transaction {
        val reminder = queries.selectReminder(id).executeAsOneOrNull()
        queries.deleteReminder(id)
        val owner = reminder?.owner ?: return@transaction
        val plan = plan(owner) ?: return@transaction
        queries.upsertPlan(owner, plan.kinds.toKeyText(), (plan.removed + id).toKeyText(), plan.createdAt)
    }

    /** The reminders still to come, the nearest first ("Mes rappels", TE-09). */
    fun upcoming(now: Long = clock(), limit: Long = MAX_LISTED): List<Reminder> =
        queries.selectUpcoming(now, limit).executeAsList().mapNotNull { it.toReminder() }

    /** The reminders of [owner] still to come, the nearest first. */
    fun upcomingOf(owner: String, now: Long = clock()): List<Reminder> =
        queries.selectUpcomingOf(owner, now).executeAsList().mapNotNull { it.toReminder() }

    /** The reminders of [category] due after [after] (exclusive) and up to [until]. */
    fun due(category: NotificationCategory, after: Long, until: Long, limit: Long = MAX_PER_ROUND): List<Reminder> =
        queries.selectDue(category.key, after, until, limit).executeAsList().mapNotNull { it.toReminder() }

    /** When the device must wake for the next reminder after [now], or null when none is programmed. */
    fun nextDueAfter(now: Long = clock()): Long? = queries.nextDueAfter(now).executeAsOneOrNull()?.MIN

    /** Erases every reminder and every plan of the device. */
    fun clear() = database.transaction {
        queries.deleteAllReminders()
        queries.deleteAllPlans()
    }

    override suspend fun eraseFromDevice() {
        clear()
        check(queries.nextDueAfter(Long.MIN_VALUE).executeAsOneOrNull()?.MIN == null) { "reminders left on the device" }
    }

    private fun write(reminder: Reminder) = queries.insertReminder(
        reminder.id,
        reminder.owner,
        reminder.category.key,
        reminder.title,
        reminder.detail,
        reminder.target,
        reminder.dueAt,
        reminder.createdAt,
    )

    private fun org.politisationpopulairefrancaise.core.db.Reminder.toReminder(): Reminder? {
        val category = NotificationCategory.fromKey(category) ?: return null
        return Reminder(id, owner, category, title, detail, target, due_at, created_at)
    }

    companion object {
        /** The part of the quick erase (S-44) that empties the reminders. */
        const val ERASABLE_ID: String = "core-reminders"

        /** How long a reminder already due is kept, so a late round of the device still notifies it. */
        const val KEEP_PAST_SECONDS: Long = 30L * 24 * 3600

        /** At most this many reminders are listed at once. */
        const val MAX_LISTED: Long = 200

        /** At most this many reminders of one category are notified in one round. */
        const val MAX_PER_ROUND: Long = 50

        private fun String.toKeySet(): Set<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        private fun Set<String>.toKeyText(): String {
            require(none { ',' in it }) { "A key of reminder holds no comma" }
            return sorted().joinToString(",")
        }

        /** A LIKE pattern for the values starting with this text, its own % and _ taken literally. */
        private fun String.likePrefix(): String {
            require('%' !in this && '_' !in this && '\\' !in this) { "A prefix of owner holds no %, _ nor \\" }
            return "$this%"
        }

        /**
         * The sources to give to the notifications of the device (`Notifications(extraSources = ...)`): one per
         * category a reminder may belong to. A reminder reaches the centre and the system once its time has come,
         * if its category is enabled; the source reads the device only.
         */
        fun notificationSources(database: PpfDatabase, clock: () -> Long = ::currentEpochSeconds): List<Pair<NotificationCategory, NotificationSource>> {
            val store = ReminderStore(database, clock)
            return NotificationCategory.entries.filterNot { it.alwaysInCentre }.map { it to RemindersSource(store, it, clock) }
        }
    }
}

/**
 * The reminders of one [category] whose time has come, for the notification centre (M31). The id of a candidate
 * holds the due time: a reminder moved to another day (a new decree) is notified again on its new day.
 */
class RemindersSource(
    private val store: ReminderStore,
    private val category: NotificationCategory,
    private val clock: () -> Long = ::currentEpochSeconds,
) : NotificationSource {

    override val name: String = "reminders-${category.key}"

    override suspend fun collect(after: Long?): List<NotificationCandidate> =
        store.due(category, after ?: Long.MIN_VALUE, clock()).map { reminder ->
            NotificationCandidate(
                id = "reminder:${reminder.id}:${reminder.dueAt}",
                category = reminder.category,
                title = reminder.title,
                detail = reminder.detail,
                target = reminder.target,
                createdAt = reminder.dueAt,
            )
        }
}
