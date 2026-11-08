package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * One round of the election of a comparator (`ppf-round` = `number | voting day | first voting day | closing`),
 * which sets the freeze of the comparator (decision of the owner, Q4 option A, `docs/integration/comparateur-2027.md`):
 * from 0 h, Paris time, the day before the first voting day (some overseas territories vote one day earlier),
 * until the last polling stations close ([closingHour]:[closingMinute], Paris time, on the voting day), nothing
 * of the comparator nor of its positions is published or updated; the application stays readable.
 */
data class ElectionRound(
    val number: Int,
    /** The voting day in mainland France. */
    val day: CivilDate,
    /** The first voting day anywhere (overseas, the day before), [day] when it is the same. */
    val firstDay: CivilDate,
    val closingHour: Int,
    val closingMinute: Int,
) {
    /** 0 h, Paris time, the day before [firstDay] (Code électoral, article L49: "à partir de la veille du scrutin à zéro heure"). */
    val freezeStart: Long get() = ParisTime.startOfDay(ParisTime.epochDayOf(firstDay) - 1)

    /** The closing of the last polling stations, Paris time. */
    val freezeEnd: Long get() = ParisTime.toEpochSeconds(day, closingHour, closingMinute)

    fun isFrozen(epochSeconds: Long): Boolean = epochSeconds >= freezeStart && epochSeconds < freezeEnd

    companion object {
        /** When the sheet does not say otherwise, the hour the polling stations of the big cities close. */
        const val DEFAULT_CLOSING_HOUR: Int = 20

        /** Overseas territories vote at most this many days before mainland France. */
        const val MAX_DAYS_EARLIER: Int = 2

        /** A round, or null when one of its fields is missing or wrong: a sheet with such a round is not read at all. */
        fun parse(fields: List<String?>): ElectionRound? {
            val number = fields.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in 1..9 } ?: return null
            val day = CivilDate.parseIso(fields.getOrNull(1)) ?: return null
            val firstText = fields.getOrNull(2)?.trim().orEmpty()
            val firstDay = if (firstText.isEmpty()) day else CivilDate.parseIso(firstText) ?: return null
            if (firstDay > day || day.toEpochDays() - firstDay.toEpochDays() > MAX_DAYS_EARLIER) return null
            val closingText = fields.getOrNull(3)?.trim().orEmpty()
            val (hour, minute) = if (closingText.isEmpty()) {
                DEFAULT_CLOSING_HOUR to 0
            } else {
                parseClock(closingText) ?: return null
            }
            return ElectionRound(number, day, firstDay, hour, minute)
        }

        private fun parseClock(text: String): Pair<Int, Int>? {
            val parts = text.split(':')
            if (parts.size != 2 || parts.any { it.length != 2 || !it.all(Char::isDigit) }) return null
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            return if (hour in 0..23 && minute in 0..59) hour to minute else null
        }
    }
}

/**
 * The request of a declared candidate to appear in the comparator (`ppf-request` = `candidate | requested on |
 * added on`), kept visible with its dates (decision of the owner, Q1 option B): anyone who declared their
 * candidacy publicly and asks is added, within the same delay for everyone ([ComparatorSheet.additionDelayDays]).
 */
data class CandidateRequest(
    /** The identifier (`d`) of the candidate's sheet in "Voir". */
    val candidate: String,
    val requestedOn: CivilDate,
    val addedOn: CivilDate,
) {
    val delayDays: Long get() = addedOn.toEpochDays() - requestedOn.toEpochDays()

    fun concerns(candidate: ContentAddress): Boolean = candidate.identifier == this.candidate

    companion object {
        fun parse(fields: List<String?>): CandidateRequest? {
            val candidate = fields.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val requested = CivilDate.parseIso(fields.getOrNull(1)) ?: return null
            val added = CivilDate.parseIso(fields.getOrNull(2)) ?: return null
            if (added < requested) return null
            return CandidateRequest(candidate, requested, added)
        }
    }
}

/** The official list of the candidates (`ppf-official-list` = `page (https) | date`): from then on, the comparator follows it. */
data class OfficialList(val url: String, val date: CivilDate)
