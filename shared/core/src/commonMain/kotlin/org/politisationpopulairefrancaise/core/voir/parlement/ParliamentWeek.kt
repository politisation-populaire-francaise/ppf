package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.time.CivilDate

/*
 * « Cette semaine au Parlement » (cahier fonctionnel 2.0, M20, VO-31): the coming sittings of the
 * Assemblée nationale, the Sénat and the European Parliament, from today to the fourteenth day
 * after, with their order of the day. Built by the import tool (package `semaine`, listed by the
 * signed index like the other packages) from the agenda of the Assemblée (`Agenda.json.zip`), the
 * iCalendar feed of the Sénat's sitting and the foreseen activities of the European Parliament.
 * Public agenda of the institutions only: no participant, no organiser, no contact is ever kept.
 */

/** Constants and reading of the package `semaine`. */
object ParliamentWeekFormat {
    const val PACKAGE: String = "semaine"

    /** The agenda covers the day of the import and the fourteen days after. */
    const val DAYS_AFTER: Int = 14

    /** The longest text kept for a title or a description (the Sénat's descriptions can be long). */
    const val MAX_TEXT: Int = 2_000

    /**
     * Reads the package, strict on its format and tolerant on unknown fields; null when it cannot
     * be read (the package is then ignored, like any other unreadable package).
     */
    fun read(json: String): ParliamentWeekPackage? =
        runCatching { ParliamentFormat.json.decodeFromString(ParliamentWeekPackage.serializer(), json) }.getOrNull()
            ?.takeIf { pkg -> pkg.format == ParliamentFormat.FORMAT && CivilDate.parseIso(pkg.from) != null && pkg.items.all { WeekItem.isDateTime(it.start) } }

    fun write(pkg: ParliamentWeekPackage): String = ParliamentFormat.json.encodeToString(ParliamentWeekPackage.serializer(), pkg)
}

/** What kind of meeting. */
@Serializable
enum class SittingKind {
    /** The plenary sitting of the assembly. */
    @SerialName("seance-publique") PLENARY,

    /** A meeting of a committee, kept when it examines a legislative file. */
    @SerialName("commission") COMMITTEE,

    @SerialName("autre") OTHER,
}

/** What the source says of the planned meeting; null when it says nothing. */
@Serializable
enum class AgendaStatus {
    @SerialName("confirme") CONFIRMED,

    /** Planned, not confirmed yet (the European Parliament's foreseen activities, a meeting « éventuelle »). */
    @SerialName("previsionnel") PROVISIONAL,

    @SerialName("annule") CANCELLED,
}

/** One point of the agenda: a sitting, or one point of the order of the day of a sitting. */
@Serializable
data class WeekItem(
    /** Stable: `an:<uid of the meeting>:<rank of the point>`, `senat:<uid>:<AAAAMMJJHHMM>`, `pe:<sitting>:<order>`. */
    val id: String,
    val chamber: Chamber,
    /** Local time of Paris, `AAAA-MM-JJTHH:MM`. */
    val start: String,
    val end: String? = null,
    val kind: SittingKind,
    /** The committee, as the source names it. */
    val body: String? = null,
    /** The object of the point, as the source writes it; null when the order of the day is not published. */
    val title: String? = null,
    /** The kind of point as the source writes it (« Suite de la discussion », « Débat », « Votes »). */
    val pointKind: String? = null,
    /** More text of the source (the Sénat's description). */
    val detail: String? = null,
    /** The legislative file of the Assemblée, `an-dossier:<uid>`, when the source gives it. */
    val dossier: String? = null,
    val dossierUrl: String? = null,
    val status: AgendaStatus? = null,
    /** The page of the institution where the agenda is published. */
    val url: String? = null,
) {
    val day: CivilDate? get() = CivilDate.parseIso(start.take(10))

    /** `HH:MM` of the start, null for an item without hour. */
    val startTime: String? get() = start.substringAfter('T', "").take(5).takeIf { it.length == 5 }
    val endTime: String? get() = end?.takeIf { it.take(10) == start.take(10) }?.substringAfter('T', "")?.take(5)?.takeIf { it.length == 5 }

    companion object {
        private val DATE_TIME = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$")
        fun isDateTime(value: String): Boolean = DATE_TIME.matches(value)
    }
}

/** The package `semaine`. */
@Serializable
data class ParliamentWeekPackage(
    val format: Int = ParliamentFormat.FORMAT,
    val example: Boolean,
    /** The first day covered, AAAA-MM-JJ (the day of the import). */
    val from: String,
    /** The last day covered, AAAA-MM-JJ. */
    val to: String,
    /** Sorted by start, then chamber, then identifier. */
    val items: List<WeekItem>,
)

/** One day of the agenda, its items in order. */
data class WeekDay(val date: CivilDate, val items: List<WeekItem>)

/** What the screens show of the package: days from [today], optionally one assembly. */
class ParliamentWeek(val pkg: ParliamentWeekPackage) {
    val example: Boolean get() = pkg.example

    /** The assemblies present in the package, in their usual order. */
    fun chambers(): List<Chamber> = Chamber.entries.filter { c -> pkg.items.any { it.chamber == c } }

    /** The items from [today] on (an earlier day is over), of [chamber] when given. */
    fun upcoming(today: CivilDate, chamber: Chamber? = null): List<WeekItem> =
        pkg.items.filter { item -> (chamber == null || item.chamber == chamber) && (item.day?.let { it >= today } ?: false) }
            .sortedWith(ORDER)

    fun days(today: CivilDate, chamber: Chamber? = null): List<WeekDay> =
        upcoming(today, chamber).groupBy { it.day!! }.map { (date, items) -> WeekDay(date, items) }.sortedBy { it.date }

    /** The coming items of a legislative file. */
    fun ofDossier(dossier: String, today: CivilDate): List<WeekItem> = upcoming(today).filter { it.dossier == dossier }

    fun item(id: String): WeekItem? = pkg.items.firstOrNull { it.id == id }

    companion object {
        val ORDER: Comparator<WeekItem> = compareBy<WeekItem>({ it.start }, { it.chamber.ordinal }, { it.id })

        /** The weekday of [date], 1 for Monday to 7 for Sunday (1 January 1970 was a Thursday). */
        fun weekday(date: CivilDate): Int = ((date.toEpochDays() + 3).mod(7L) + 1).toInt()
    }
}
