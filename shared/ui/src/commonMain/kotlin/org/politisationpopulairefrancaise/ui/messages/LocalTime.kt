package org.politisationpopulairefrancaise.ui.messages

import org.politisationpopulairefrancaise.core.time.CivilDate

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

    companion object {
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
