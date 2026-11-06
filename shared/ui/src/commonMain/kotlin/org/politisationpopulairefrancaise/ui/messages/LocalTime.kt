package org.politisationpopulairefrancaise.ui.messages

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.messages_day_today
import org.politisationpopulairefrancaise.ui.resources.messages_day_yesterday

/**
 * The offset of the device's time zone from UTC at [epochSeconds], in seconds (3600 for Paris in
 * winter, 7200 in summer). The only platform-specific piece of the conversation screens, which
 * show the local hour of each message and group them by local day.
 */
internal expect fun localUtcOffsetSeconds(epochSeconds: Long): Int

/** A moment as the device's clock would show it: its local day and its hour and minute. */
internal data class LocalMoment(val day: Long, val hour: Int, val minute: Int, val date: CivilDate) {
    /** "9:05", "14:32": the compact form of French chat applications. */
    val hourLabel: String get() = "$hour:${minute.toString().padStart(2, '0')}"

    /** "27 oct.", or "27 oct. 2025" in another year than [currentYear]: older rows of a list. */
    fun shortDate(currentYear: Int): String {
        val text = "${if (date.day == 1) "1er" else date.day} ${SHORT_MONTHS[date.month - 1]}"
        return if (date.year == currentYear) text else "$text ${date.year}"
    }

    companion object {
        private val SHORT_MONTHS = listOf(
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc.",
        )

        fun of(epochSeconds: Long): LocalMoment {
            val local = epochSeconds + localUtcOffsetSeconds(epochSeconds)
            val secondOfDay = local.mod(86_400L)
            return LocalMoment(
                day = local.floorDiv(86_400L),
                hour = (secondOfDay / 3_600).toInt(),
                minute = (secondOfDay % 3_600 / 60).toInt(),
                date = CivilDate.fromEpochSeconds(local),
            )
        }
    }
}

/** The label of a day between the bubbles of a thread: "Aujourd’hui", "Hier", or the full date. */
@Composable
internal fun dayLabel(moment: LocalMoment, today: LocalMoment): String = when (moment.day) {
    today.day -> stringResource(Res.string.messages_day_today)
    today.day - 1 -> stringResource(Res.string.messages_day_yesterday)
    else -> moment.date.toFrench()
}

/** The time of a row in a list of threads, like chat applications: the hour today, "Hier", then the date. */
@Composable
internal fun listTimeLabel(epochSeconds: Long, now: Long): String {
    val moment = LocalMoment.of(epochSeconds)
    val today = LocalMoment.of(now)
    return when {
        moment.day >= today.day -> moment.hourLabel
        moment.day == today.day - 1 -> stringResource(Res.string.messages_day_yesterday)
        else -> moment.shortDate(today.date.year)
    }
}
