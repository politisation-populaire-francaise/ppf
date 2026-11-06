package org.politisationpopulairefrancaise.ui.feed

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.time_days_ago
import org.politisationpopulairefrancaise.ui.resources.time_hours_ago
import org.politisationpopulairefrancaise.ui.resources.time_minutes_ago
import org.politisationpopulairefrancaise.ui.resources.time_now
import org.politisationpopulairefrancaise.ui.resources.time_yesterday

/** How long ago something happened, coarse on purpose. */
sealed interface RelativeTime {
    data object Now : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val count: Int) : RelativeTime
    data class On(val date: CivilDate) : RelativeTime

    companion object {
        fun between(then: Long, now: Long): RelativeTime {
            val seconds = now - then
            return when {
                seconds < 60 -> Now
                seconds < 3_600 -> Minutes((seconds / 60).toInt())
                seconds < 86_400 -> Hours((seconds / 3_600).toInt())
                seconds < 2 * 86_400 -> Yesterday
                seconds < 14 * 86_400 -> Days((seconds / 86_400).toInt())
                else -> On(CivilDate.fromEpochSeconds(then))
            }
        }
    }
}

@Composable
fun RelativeTime.label(): String = when (this) {
    RelativeTime.Now -> stringResource(Res.string.time_now)
    is RelativeTime.Minutes -> stringResource(Res.string.time_minutes_ago, count)
    is RelativeTime.Hours -> stringResource(Res.string.time_hours_ago, count)
    RelativeTime.Yesterday -> stringResource(Res.string.time_yesterday)
    is RelativeTime.Days -> stringResource(Res.string.time_days_ago, count)
    is RelativeTime.On -> date.toFrench()
}
