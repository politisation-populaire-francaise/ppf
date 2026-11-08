package org.politisationpopulairefrancaise.core.contact

import org.politisationpopulairefrancaise.core.time.CivilDate

/** A deadline, in seconds since 1970, and whether the law sets it or it is an internal goal. */
data class Deadline(val at: Long, val kind: DeadlineKind)

/**
 * The internal goals of the team, in days (docs/integration/contact-reclamations.md, section 2): not legal,
 * shown as goals, never as guarantees. These are the defaults of the contract; the server may override them
 * through its private configuration. Null: no deadline at all.
 */
data class CaseGoals(
    val contactDays: Int? = 15,
    val bugDays: Int? = null,
    val suggestionDays: Int? = null,
    /** Not set by the contract: a report also goes to the moderation queue, which has its own rules. */
    val reportDays: Int? = null,
    /** The goal of a contested moderation decision (the law only says « en temps opportun »). */
    val moderationDecisionDays: Int? = 15,
    val fundsDays: Int? = 30,
    val teamMemberDays: Int? = 30,
    val otherClaimDays: Int? = 30,
    /** A request of verification to the fact-checking team of Comprendre (CO-18, D-143). */
    val verificationDays: Int? = 15,
) {
    init {
        listOf(contactDays, bugDays, suggestionDays, reportDays, moderationDecisionDays, fundsDays, teamMemberDays, otherClaimDays, verificationDays)
            .forEach { require(it == null || it in 1..MAX_GOAL_DAYS) { "A goal is between 1 and $MAX_GOAL_DAYS days" } }
    }

    companion object {
        const val MAX_GOAL_DAYS: Int = 365
        val DEFAULT: CaseGoals = CaseGoals()
    }
}

/**
 * Deadlines of the cases, computed the same way by the app (before sending: « réponse au plus tard le … »)
 * and by the server (which stores them). Calendar arithmetic is done in the time of Paris, where the law
 * applies, keeping the hour of the reception (an earlier moment rather than the end of the day: the team
 * sees the strictest reading).
 *
 * Legal deadlines (contract, section 2):
 * - rights on one's data: one month after the reception (RGPD, article 12, paragraph 3), extended by two more
 *   months only when the extension is announced and motivated within the first month;
 * - right of reply: three days after the reception (LCEN, article 1-1, III);
 * - a contested moderation decision is accepted until six months after the decision (DSA, article 20,
 *   paragraph 1, applied by choice); its handling has an internal goal only.
 */
object CaseDeadlines {
    const val DATA_RIGHTS_MONTHS: Int = 1

    /** One month, plus the two months of an extension. */
    const val DATA_RIGHTS_EXTENDED_MONTHS: Int = 3
    const val RIGHT_OF_REPLY_DAYS: Int = 3
    const val MODERATION_CLAIM_MONTHS: Int = 6

    /** The decision of a contested moderation may be dated a little after the reception (clocks differ). */
    const val DECISION_CLOCK_SKEW_SECONDS: Long = 10 * 60

    /** The deadline of a case of [nature] received at [receivedAt]; null when there is none (a bug, a suggestion, a report). */
    fun of(nature: CaseNature, receivedAt: Long, goals: CaseGoals = CaseGoals.DEFAULT): Deadline? = when (nature) {
        is CaseNature.Ticket -> goal(receivedAt, when (nature.subtype) {
            TicketSubtype.BUG -> goals.bugDays
            TicketSubtype.SUGGESTION -> goals.suggestionDays
            TicketSubtype.REPORT -> goals.reportDays
            TicketSubtype.VERIFICATION -> goals.verificationDays
        })
        is CaseNature.Contact -> goal(receivedAt, goals.contactDays)
        is CaseNature.Claim -> when (nature.motif) {
            ClaimMotif.DATA_RIGHTS -> Deadline(ParisTime.plusMonths(receivedAt, DATA_RIGHTS_MONTHS), DeadlineKind.LEGAL)
            ClaimMotif.RIGHT_OF_REPLY -> Deadline(ParisTime.plusDays(receivedAt, RIGHT_OF_REPLY_DAYS), DeadlineKind.LEGAL)
            ClaimMotif.MODERATION_DECISION -> goal(receivedAt, goals.moderationDecisionDays)
            ClaimMotif.FUNDS -> goal(receivedAt, goals.fundsDays)
            ClaimMotif.TEAM_MEMBER -> goal(receivedAt, goals.teamMemberDays)
            ClaimMotif.OTHER -> goal(receivedAt, goals.otherClaimDays)
        }
    }

    private fun goal(receivedAt: Long, days: Int?): Deadline? = days?.let { Deadline(ParisTime.plusDays(receivedAt, it), DeadlineKind.GOAL) }

    /** The deadline of a request on one's data once extended: three months after the reception. */
    fun extendedDataRights(receivedAt: Long): Long = ParisTime.plusMonths(receivedAt, DATA_RIGHTS_EXTENDED_MONTHS)

    /**
     * Whether a request on one's data received at [receivedAt] may still be extended at [now]: only within
     * the first month (before its first deadline), and once.
     */
    fun canExtend(nature: CaseNature, receivedAt: Long, now: Long, alreadyExtended: Boolean): Boolean =
        nature.motif == ClaimMotif.DATA_RIGHTS && !alreadyExtended && now < ParisTime.plusMonths(receivedAt, DATA_RIGHTS_MONTHS)

    /** The last moment a moderation decision taken at [decisionAt] may be contested: six months after it. */
    fun moderationClaimLimit(decisionAt: Long): Long = ParisTime.plusMonths(decisionAt, MODERATION_CLAIM_MONTHS)

    /**
     * Whether a claim against a moderation decision taken at [decisionAt] may be opened at [now]: from the
     * decision (with a few minutes of clock difference) to six months after it.
     */
    fun moderationClaimOpen(decisionAt: Long, now: Long): Boolean =
        decisionAt >= 0 && decisionAt <= now + DECISION_CLOCK_SKEW_SECONDS && now <= moderationClaimLimit(decisionAt)
}

/**
 * The legal time of metropolitan France (Europe/Paris) without a time-zone database, which common Kotlin does
 * not have: UTC+1, and UTC+2 from the last Sunday of March to the last Sunday of October at 01:00 UTC
 * (directive 2000/84/EC, unchanged since 1996).
 */
internal object ParisTime {
    private const val DAY: Long = 86_400
    private const val HOUR: Long = 3_600

    fun offsetSeconds(epochSeconds: Long): Long {
        val year = CivilDate.fromEpochSeconds(epochSeconds).year
        val start = lastSunday(year, 3) * DAY + HOUR
        val end = lastSunday(year, 10) * DAY + HOUR
        return if (epochSeconds in start until end) 2 * HOUR else HOUR
    }

    /** [epochSeconds] plus [months] calendar months in Paris, same hour; the day is clamped to the end of a shorter month. */
    fun plusMonths(epochSeconds: Long, months: Int): Long {
        val local = epochSeconds + offsetSeconds(epochSeconds)
        val date = CivilDate.fromEpochDays(local.floorDiv(DAY))
        val secondOfDay = local.mod(DAY)
        val index = date.year * 12 + (date.month - 1) + months
        val year = index.floorDiv(12)
        val month = index.mod(12) + 1
        val day = minOf(date.day, monthLength(year, month))
        return fromLocal(CivilDate(year, month, day).toEpochDays() * DAY + secondOfDay)
    }

    /** [epochSeconds] plus [days] calendar days in Paris, same hour. */
    fun plusDays(epochSeconds: Long, days: Int): Long {
        val local = epochSeconds + offsetSeconds(epochSeconds)
        return fromLocal(local + days * DAY)
    }

    /**
     * The instant of a local time of Paris (seconds since 1970 as if Paris were UTC): of the two possible
     * offsets, the one that maps back; the earlier instant when both do (the repeated hour of October) or none
     * does (the skipped hour of March).
     */
    fun fromLocal(localSeconds: Long): Long {
        val summer = localSeconds - 2 * HOUR
        val winter = localSeconds - HOUR
        return when {
            summer + offsetSeconds(summer) == localSeconds -> summer
            winter + offsetSeconds(winter) == localSeconds -> winter
            else -> summer
        }
    }

    private fun lastSunday(year: Int, month: Int): Long {
        val last = CivilDate(year, month, 31).toEpochDays()
        // 1970-01-01 was a Thursday: (days + 4) mod 7 is 0 on Sundays.
        return last - (last + 4).mod(7L)
    }

    private fun monthLength(year: Int, month: Int): Int {
        val first = CivilDate(year, month, 1).toEpochDays()
        val next = if (month == 12) CivilDate(year + 1, 1, 1) else CivilDate(year, month + 1, 1)
        return (next.toEpochDays() - first).toInt()
    }
}
