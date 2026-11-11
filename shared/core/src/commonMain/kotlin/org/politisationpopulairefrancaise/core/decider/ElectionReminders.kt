package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The reminders a person may ask for an election (DE-28, `docs/integration/calendrier-electoral.md`, section 3):
 * off by default (principle 10), proposed on the page of the election, programmed and kept on the device only.
 * [key] is stored in the plan of the reminders and must not change.
 */
enum class ElectionReminderKind(val key: String) {
    /** [ElectionReminders.REGISTRATION_DAYS_BEFORE] days before each deadline of registration on the electoral rolls. */
    REGISTRATION("inscription"),

    /** [ElectionReminders.PROXY_DAYS_BEFORE] days before each round: the time to establish a proxy. */
    PROXY("procuration"),

    /** The day before each round. */
    EVE("veille"),
    ;

    companion object {
        fun fromKey(key: String): ElectionReminderKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One reminder of an election, as the device programs it: [key] is unique within the election (`inscription-mairie`,
 * `procuration-1`, `veille-2`), [date] is the day it is about (the deadline, the round), [dueAt] when it is shown.
 */
data class PlannedElectionReminder(
    val key: String,
    val kind: ElectionReminderKind,
    val date: CivilDate,
    val dueAt: Long,
    /** The round it is about ([ElectionReminderKind.PROXY], [ElectionReminderKind.EVE]). */
    val round: Int? = null,
    /** The deadline it is about ([ElectionReminderKind.REGISTRATION]). */
    val deadline: DeadlineKind? = null,
)

/**
 * Computes the reminders of an election from its current dates, so that a new decree that moves a date moves the
 * reminders with it. PPF never sets a legal date: the dates are those the team wrote from the official sources; only
 * the delays of the reminders are chosen here, and they are the same for everyone.
 */
object ElectionReminders {
    /** The owner of the reminders of [election] in the store of the device. */
    fun owner(election: ElectionCalendar): String = OWNER_PREFIX + election.election.id

    /** The reminders of every election start with this owner. */
    const val OWNER_PREFIX: String = "decider:election:"

    /** The registration reminder comes a week before the deadline: the time to gather the documents asked. */
    const val REGISTRATION_DAYS_BEFORE: Int = 7

    /** The proxy reminder comes ten days before the round, so the proxy reaches the polling station in time. */
    const val PROXY_DAYS_BEFORE: Int = 10

    /** Every reminder is shown at this hour, Paris time: in the day, never at night. */
    const val HOUR: Int = 10

    /** What the page proposes when the user asks for the reminders: all of them, each one removable. */
    val DEFAULT_KINDS: Set<ElectionReminderKind> = ElectionReminderKind.entries.toSet()

    /** The reminders of [election] for [kinds], those due after [now] only, the nearest first. */
    fun plan(election: ElectionCalendar, kinds: Set<ElectionReminderKind>, now: Long): List<PlannedElectionReminder> = buildList {
        if (ElectionReminderKind.REGISTRATION in kinds) {
            election.deadlines
                .filter { it.kind == DeadlineKind.REGISTRATION_TOWN_HALL || it.kind == DeadlineKind.REGISTRATION_ONLINE }
                .forEach { d ->
                    // The key is the deadline's own: inscription-mairie, inscription-en-ligne.
                    add(PlannedElectionReminder(d.kind.tagValue, ElectionReminderKind.REGISTRATION, d.date, at(d.date, REGISTRATION_DAYS_BEFORE), deadline = d.kind))
                }
        }
        election.rounds.forEach { round ->
            if (ElectionReminderKind.PROXY in kinds) {
                add(PlannedElectionReminder("procuration-${round.number}", ElectionReminderKind.PROXY, round.day, at(round.day, PROXY_DAYS_BEFORE), round = round.number))
            }
            if (ElectionReminderKind.EVE in kinds) {
                add(PlannedElectionReminder("veille-${round.number}", ElectionReminderKind.EVE, round.day, at(round.day, 1), round = round.number))
            }
        }
    }.filter { it.dueAt > now }.sortedWith(compareBy({ it.dueAt }, { it.key }))

    /** The id of a reminder of [election] in the store of the device. */
    fun reminderId(election: ElectionCalendar, key: String): String = owner(election) + "/" + key

    private fun at(day: CivilDate, daysBefore: Int): Long =
        ParisTime.toEpochSeconds(CivilDate.fromEpochDays(day.toEpochDays() - daysBefore), HOUR, 0)
}
