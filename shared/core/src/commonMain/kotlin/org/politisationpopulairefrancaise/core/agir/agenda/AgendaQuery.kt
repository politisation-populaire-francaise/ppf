package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/** Which part of the agenda a query shows (M1: "passés et à venir"). */
enum class AgendaTiming {
    /** Under way or still to come, soonest first. */
    UPCOMING,

    /** Over, most recent first. */
    PAST,

    /** Everything, by start date. */
    ALL,
}

/** The dates a query keeps, in the local time of mainland France ([ParisTime]). */
sealed interface AgendaPeriod {
    /** No constraint. */
    data object Anytime : AgendaPeriod

    data object Today : AgendaPeriod

    /** Monday to Sunday of the current week. */
    data object ThisWeek : AgendaPeriod

    /** The current calendar month. */
    data object ThisMonth : AgendaPeriod

    /** From [from] to [to], both included; a missing bound is open. */
    data class Range(val from: CivilDate?, val to: CivilDate?) : AgendaPeriod

    /**
     * The local days covered, as `epochDay` numbers (both included), for a query run at [now];
     * null for [Anytime]. Exposed so that the interface can say which dates a period means.
     */
    fun days(now: Long): LongRange? {
        val today = ParisTime.epochDay(now)
        return when (this) {
            Anytime -> null
            Today -> today..today
            ThisWeek -> {
                val monday = today - (ParisTime.dayOfWeek(today) - 1)
                monday..(monday + 6)
            }
            ThisMonth -> {
                val date = ParisTime.dateOf(today)
                val first = ParisTime.epochDayOf(CivilDate(date.year, date.month, 1))
                first..(first + ParisTime.daysInMonth(date.year, date.month) - 1)
            }
            is Range -> (from?.let(ParisTime::epochDayOf) ?: Long.MIN_VALUE)..(to?.let(ParisTime::epochDayOf) ?: Long.MAX_VALUE)
        }
    }
}

/**
 * A query on the agenda (M1, AG-01: "filtres par date, lieu, type, organisation"). Every criterion
 * is a constraint only when set: an empty set, [AgendaPeriod.Anytime] and a blank [text] mean "no
 * constraint"; sets match when the mobilization has ANY of their values. It runs on the device,
 * on the local copy of the agenda: the server never learns which territory, type or organisation
 * the user looks at (contract of the Agir space, section 6).
 */
data class AgendaQuery(
    val timing: AgendaTiming = AgendaTiming.UPCOMING,
    val period: AgendaPeriod = AgendaPeriod.Anytime,
    /** Territories, folded with [FrenchText.fold] ([territoryKey]). */
    val territories: Set<String> = emptySet(),
    val types: Set<MobilizationType> = emptySet(),
    /** Keys (lowercase hex) of organisers: author or named organiser ([Mobilization.isHostedBy]). */
    val organizers: Set<String> = emptySet(),
    /** Words that must all start a word of the title, summary, description, place or territory. */
    val text: String = "",
) {
    /** How many removable filters this query holds (the timing is a tab, not a filter). */
    val constraintCount: Int
        get() = (if (period != AgendaPeriod.Anytime) 1 else 0) + territories.size + types.size + organizers.size + (if (text.isNotBlank()) 1 else 0)

    /** The same query without any filter, keeping the timing. */
    fun cleared(): AgendaQuery = AgendaQuery(timing = timing)

    fun matches(mobilization: Mobilization, now: Long): Boolean {
        when (timing) {
            AgendaTiming.UPCOMING -> if (mobilization.isPast(now)) return false
            AgendaTiming.PAST -> if (!mobilization.isPast(now)) return false
            AgendaTiming.ALL -> Unit
        }
        period.days(now)?.let { days ->
            val first = ParisTime.epochDay(mobilization.start)
            val last = ParisTime.epochDay(mobilization.end ?: mobilization.start)
            if (last < days.first || first > days.last) return false
        }
        if (territories.isNotEmpty() && territoryKey(mobilization.territory) !in territories) return false
        if (types.isNotEmpty() && mobilization.type !in types) return false
        if (organizers.isNotEmpty() && organizers.none(mobilization::isHostedBy)) return false
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(mobilization), text)) return false
        return true
    }

    /** The mobilizations this query keeps, sorted by start date (most recent first for the past ones). */
    fun apply(mobilizations: List<Mobilization>, now: Long): List<Mobilization> =
        sort(mobilizations.filter { matches(it, now) }, timing)

    companion object {
        /** A territory as stored in [territories]. */
        fun territoryKey(territory: String): String = FrenchText.fold(territory)

        fun searchableText(mobilization: Mobilization): String = buildString {
            append(mobilization.title).append(' ')
            mobilization.summary?.let { append(it).append(' ') }
            append(mobilization.description).append(' ')
            append(mobilization.location).append(' ')
            append(mobilization.territory)
        }

        /** By start date, then title; the past ones most recent first. */
        fun sort(mobilizations: List<Mobilization>, timing: AgendaTiming): List<Mobilization> {
            val byStart = compareBy<Mobilization> { it.start }.thenBy { FrenchText.fold(it.displayTitle) }.thenBy { it.address.toString() }
            return mobilizations.sortedWith(if (timing == AgendaTiming.PAST) compareByDescending<Mobilization> { it.start }.thenBy { FrenchText.fold(it.displayTitle) }.thenBy { it.address.toString() } else byStart)
        }
    }
}
