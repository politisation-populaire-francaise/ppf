package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate

/** A field of the "Proposer un événement" form. */
enum class DraftField { TYPE, TITLE, START_DATE, START_TIME, END_DATE, END_TIME, LOCATION, TERRITORY, SUMMARY, DESCRIPTION, LINK }

/** What is wrong with a field, and the server's code for it when there is one. */
enum class DraftProblem(val field: DraftField, val error: MobilizationError?, val missing: Boolean = false) {
    TYPE_MISSING(DraftField.TYPE, MobilizationError.BAD_EVENT_TYPE, missing = true),
    TITLE_MISSING(DraftField.TITLE, MobilizationError.MISSING_TITLE, missing = true),
    TITLE_TOO_LONG(DraftField.TITLE, MobilizationError.TOO_LONG),
    START_DATE_MISSING(DraftField.START_DATE, MobilizationError.MISSING_START, missing = true),
    START_DATE_INVALID(DraftField.START_DATE, MobilizationError.MISSING_START),
    START_TIME_MISSING(DraftField.START_TIME, MobilizationError.MISSING_START, missing = true),
    START_TIME_INVALID(DraftField.START_TIME, MobilizationError.MISSING_START),

    /** The device does not send an event that has already begun (device rule). */
    START_IN_PAST(DraftField.START_DATE, null),
    END_DATE_INVALID(DraftField.END_DATE, MobilizationError.BAD_END),
    END_TIME_MISSING(DraftField.END_TIME, MobilizationError.BAD_END),
    END_TIME_INVALID(DraftField.END_TIME, MobilizationError.BAD_END),
    END_BEFORE_START(DraftField.END_TIME, MobilizationError.BAD_END),
    LOCATION_MISSING(DraftField.LOCATION, MobilizationError.MISSING_LOCATION, missing = true),
    LOCATION_TOO_LONG(DraftField.LOCATION, MobilizationError.TOO_LONG),
    TERRITORY_MISSING(DraftField.TERRITORY, MobilizationError.MISSING_TERRITORY, missing = true),
    TERRITORY_TOO_LONG(DraftField.TERRITORY, MobilizationError.TOO_LONG),
    SUMMARY_TOO_LONG(DraftField.SUMMARY, MobilizationError.TOO_LONG),
    DESCRIPTION_TOO_LONG(DraftField.DESCRIPTION, MobilizationError.TOO_LONG),
    LINK_INVALID(DraftField.LINK, MobilizationError.MISSING_FIELD),
}

/**
 * The draft of a mobilization proposed by a contributor (AG-02), as typed in the form: dates and
 * hours are text, read in the local time of mainland France ("14/11/2026", "14 h 30"). [problems]
 * says what blocks the sending, field by field, with the same codes as the server; the charter is
 * accepted apart, by the form. The draft stays in memory until it is sent.
 */
data class MobilizationDraft(
    val type: MobilizationType? = null,
    val title: String = "",
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val location: String = "",
    val territory: String = "",
    val summary: String = "",
    val description: String = "",
    val link: String = "",
    /** Kept only for a demonstration. */
    val declaration: DeclarationState? = null,
    /** Keys (lowercase hex) of the organisations named as organisers. */
    val organizers: List<String> = emptyList(),
) {
    val isBlank: Boolean
        get() = type == null && listOf(title, startDate, startTime, endDate, endTime, location, territory, summary, description, link).all { it.isBlank() }

    /** The start, when the date and the hour can be read. */
    fun start(today: CivilDate): Long? {
        val date = parseDate(startDate, today) ?: return null
        val (hour, minute) = parseTime(startTime) ?: return null
        return ParisTime.toEpochSeconds(date, hour, minute)
    }

    /** The end, when an hour of end is given and can be read (same day as the start unless a date of end is given). */
    fun end(today: CivilDate): Long? {
        if (endTime.isBlank()) return null
        val date = (if (endDate.isBlank()) parseDate(startDate, today) else parseDate(endDate, today)) ?: return null
        val (hour, minute) = parseTime(endTime) ?: return null
        return ParisTime.toEpochSeconds(date, hour, minute)
    }

    /** The link to publish: the text with `https://` added when no scheme is given ("asso.example" works). */
    val normalizedLink: String?
        get() {
            val text = link.trim()
            if (text.isEmpty()) return null
            return if (text.contains("://")) text else "https://$text"
        }

    /** Everything that blocks the sending at [now], in the order of the form. */
    fun problems(now: Long): List<DraftProblem> {
        val today = ParisTime.moment(now).date
        val problems = mutableListOf<DraftProblem>()
        if (type == null) problems += DraftProblem.TYPE_MISSING
        val cleanTitle = title.trim()
        when {
            cleanTitle.isEmpty() -> problems += DraftProblem.TITLE_MISSING
            cleanTitle.length > AgirFormats.MAX_TITLE -> problems += DraftProblem.TITLE_TOO_LONG
        }
        val date = parseDate(startDate, today)
        when {
            startDate.isBlank() -> problems += DraftProblem.START_DATE_MISSING
            date == null -> problems += DraftProblem.START_DATE_INVALID
        }
        val time = parseTime(startTime)
        when {
            startTime.isBlank() -> problems += DraftProblem.START_TIME_MISSING
            time == null -> problems += DraftProblem.START_TIME_INVALID
        }
        val start = if (date != null && time != null) ParisTime.toEpochSeconds(date, time.first, time.second) else null
        if (start != null && start < now) problems += DraftProblem.START_IN_PAST
        if (endDate.isNotBlank() && parseDate(endDate, today) == null) problems += DraftProblem.END_DATE_INVALID
        when {
            endTime.isBlank() -> if (endDate.isNotBlank()) problems += DraftProblem.END_TIME_MISSING
            parseTime(endTime) == null -> problems += DraftProblem.END_TIME_INVALID
            else -> {
                val end = end(today)
                if (start != null && end != null && end <= start) problems += DraftProblem.END_BEFORE_START
            }
        }
        val cleanLocation = location.trim()
        when {
            cleanLocation.isEmpty() -> problems += DraftProblem.LOCATION_MISSING
            cleanLocation.length > Mobilization.MAX_LOCATION -> problems += DraftProblem.LOCATION_TOO_LONG
        }
        val cleanTerritory = territory.trim()
        when {
            cleanTerritory.isEmpty() -> problems += DraftProblem.TERRITORY_MISSING
            cleanTerritory.length > Mobilization.MAX_TERRITORY -> problems += DraftProblem.TERRITORY_TOO_LONG
        }
        if (summary.trim().length > Mobilization.MAX_SUMMARY) problems += DraftProblem.SUMMARY_TOO_LONG
        if (description.trim().length > AgirFormats.MAX_TEXT) problems += DraftProblem.DESCRIPTION_TOO_LONG
        normalizedLink?.let { if (!Mobilization.isValidLink(it)) problems += DraftProblem.LINK_INVALID }
        return problems
    }

    /**
     * The unsigned event of this draft, or null while [problems] is not empty. [identifier] is the
     * stable `d` of the new event ([Mobilization.newIdentifier]).
     */
    fun toUnsignedEvent(author: PublicKey, now: Long, identifier: String): UnsignedEvent? {
        if (problems(now).isNotEmpty()) return null
        val today = ParisTime.moment(now).date
        val type = type ?: return null
        return Mobilization.build(
            author = author,
            identifier = identifier,
            createdAt = now,
            title = title,
            start = start(today) ?: return null,
            end = end(today),
            location = location,
            type = type,
            territory = territory,
            declaration = declaration.takeIf { type == MobilizationType.MANIFESTATION },
            organizers = organizers.filter(Mobilization::isValidKey),
            links = listOfNotNull(normalizedLink),
            summary = summary,
            description = description,
        )
    }

    companion object {
        /**
         * A date typed in French order: "14/11/2026", "14-11-2026", "14.11.2026", "14/11/26",
         * "14 11 2026", the ISO form "2026-11-14", or "14/11" (this year, or the next one when
         * the date has passed). Null when it is not a real date.
         */
        fun parseDate(text: String, today: CivilDate): CivilDate? {
            val parts = text.trim().split('/', '-', '.', ' ').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.size > 3 || parts.any { part -> part.any { !it.isDigit() } || part.length > 4 }) return null
            val numbers = parts.map { it.toInt() }
            val (year, month, day) = when {
                parts.size == 3 && parts[0].length == 4 -> Triple(numbers[0], numbers[1], numbers[2])
                parts.size == 3 -> Triple(if (parts[2].length <= 2) 2000 + numbers[2] else numbers[2], numbers[1], numbers[0])
                parts.size == 2 -> {
                    val guess = Triple(today.year, numbers[1], numbers[0])
                    val passed = ParisTime.isValidDate(guess.first, guess.second, guess.third) &&
                        ParisTime.epochDayOf(CivilDate(guess.first, guess.second, guess.third)) < ParisTime.epochDayOf(today)
                    if (passed) Triple(today.year + 1, numbers[1], numbers[0]) else guess
                }
                else -> return null
            }
            if (!ParisTime.isValidDate(year, month, day)) return null
            return CivilDate(year, month, day)
        }

        /** An hour: "14:30", "14h30", "14 h 30", "14h", "14", "9.05". Null when it is not a time of day. */
        fun parseTime(text: String): Pair<Int, Int>? {
            val clean = text.trim().lowercase().replace(" ", "").replace(" ", "")
            if (clean.isEmpty()) return null
            val parts = clean.split(':', 'h', '.')
            if (parts.size > 2 || parts[0].isEmpty()) return null
            if (parts.any { part -> part.any { !it.isDigit() } || part.length > 2 }) return null
            val hour = parts[0].toInt()
            val minute = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { if (it.length == 2) it.toInt() else return null } ?: 0
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }
    }
}
