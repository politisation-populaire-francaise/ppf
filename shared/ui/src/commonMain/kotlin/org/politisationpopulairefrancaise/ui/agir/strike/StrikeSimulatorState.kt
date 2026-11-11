package org.politisationpopulairefrancaise.ui.agir.strike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.politisationpopulairefrancaise.core.agir.strike.StrikeDay
import org.politisationpopulairefrancaise.core.agir.strike.StrikeEstimate
import org.politisationpopulairefrancaise.core.agir.strike.StrikeNumbers
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSector
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSimulation
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSimulator
import org.politisationpopulairefrancaise.core.agir.strike.StrikeStatus
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.ui.AppDependencies

/** How a day of strike is typed: the whole day, half of it, or a number of hours. */
enum class StrikeDayKind(val id: String) {
    FULL("journee"),
    HALF("demi-journee"),
    HOURS("heures"),
}

/** One day of strike as the person types it; [hoursText] counts only for [StrikeDayKind.HOURS]. */
data class StrikeDayEntry(val kind: StrikeDayKind = StrikeDayKind.FULL, val hoursText: String = "")

/**
 * What the person types in the strike simulator (AG-60), **in memory only**: the pay is sensitive,
 * so nothing here is ever written (no database, no preference, no `rememberSaveable`, whose state the
 * system may write to the disk) nor sent. It lives as long as "Agir" is shown, and the quick erase of
 * the device (S-44) forgets it ([clear], part [ERASABLE_ID]).
 */
class StrikeSimulatorState {
    var status: StrikeStatus by mutableStateOf(StrikeStatus.PRIVATE)
        private set

    var sector: StrikeSector by mutableStateOf(StrikeSector.GENERAL)
        private set

    /** The gross monthly pay, as typed. */
    var payText: String by mutableStateOf("")

    /** Private sector: the working time of a week, as typed. */
    var weeklyHoursText: String by mutableStateOf(DEFAULT_WEEKLY)

    /** Private sector: the hours planned on a day of strike, as typed. */
    var dayHoursText: String by mutableStateOf(DEFAULT_DAY)

    /** The days of strike of the month; one whole day to start with. */
    val days: MutableList<StrikeDayEntry> = mutableStateListOf(StrikeDayEntry())

    /** Civil services: the days without service between two days of strike. */
    var daysWithoutService: Int by mutableStateOf(0)
        private set

    /** The person's territory, for "Caisses de grève proches"; never stored nor sent either. */
    var near: String by mutableStateOf("")

    fun selectStatus(status: StrikeStatus) {
        this.status = status
        if (!sector.fits(status)) sector = StrikeSector.GENERAL
    }

    fun selectSector(sector: StrikeSector) {
        if (sector.fits(status)) this.sector = sector
    }

    val canAddDay: Boolean get() = days.size < StrikeSimulator.MAX_DAYS

    fun addDay() {
        if (canAddDay) days.add(StrikeDayEntry())
    }

    fun removeDay(index: Int) {
        if (index in days.indices && days.size > 1) days.removeAt(index)
        if (days.size < 2) daysWithoutService = 0
    }

    fun setKind(index: Int, kind: StrikeDayKind) {
        if (index in days.indices) days[index] = days[index].copy(kind = kind)
    }

    fun setHours(index: Int, text: String) {
        if (index in days.indices) days[index] = days[index].copy(hoursText = text.take(MAX_FIELD))
    }

    /** The field "jours sans service" makes sense between two days of strike, in the civil services. */
    val asksDaysWithoutService: Boolean get() = status.isPublic && days.size >= 2

    fun increaseDaysWithoutService() {
        if (daysWithoutService < StrikeSimulator.MAX_DAYS_WITHOUT_SERVICE) daysWithoutService++
    }

    fun decreaseDaysWithoutService() {
        if (daysWithoutService > 0) daysWithoutService--
    }

    val payCents: Long? get() = StrikeNumbers.parseEuros(payText)

    val payError: Boolean get() = payText.isNotBlank() && payCents == null

    val weeklyMinutes: Int? get() = StrikeNumbers.parseWeeklyHours(weeklyHoursText)

    val weeklyError: Boolean get() = status == StrikeStatus.PRIVATE && weeklyMinutes == null

    val dayMinutes: Int? get() = StrikeNumbers.parseHours(dayHoursText)

    val dayHoursError: Boolean get() = status == StrikeStatus.PRIVATE && dayMinutes == null

    /** The hours of a day typed by the person are not readable. */
    fun hoursError(entry: StrikeDayEntry): Boolean =
        entry.kind == StrikeDayKind.HOURS && entry.hoursText.isNotBlank() && StrikeNumbers.parseHours(entry.hoursText) == null

    /** What the calculator reads; null while something is missing or unreadable. */
    val simulation: StrikeSimulation?
        get() {
            val pay = payCents ?: return null
            val strikeDays = days.map { entry ->
                when (entry.kind) {
                    StrikeDayKind.FULL -> StrikeDay.FullDay
                    StrikeDayKind.HALF -> StrikeDay.HalfDay
                    StrikeDayKind.HOURS -> StrikeDay.Hours(StrikeNumbers.parseHours(entry.hoursText) ?: return null)
                }
            }
            if (strikeDays.isEmpty()) return null
            val weekly = if (status == StrikeStatus.PRIVATE) weeklyMinutes ?: return null else StrikeSimulator.LEGAL_WEEKLY_MINUTES
            val day = if (status == StrikeStatus.PRIVATE) dayMinutes ?: return null else StrikeSimulator.DEFAULT_DAY_MINUTES
            return StrikeSimulation(
                status = status,
                sector = sector,
                monthlyPayCents = pay,
                days = strikeDays,
                daysWithoutService = if (asksDaysWithoutService) daysWithoutService else 0,
                weeklyMinutes = weekly,
                dayMinutes = day,
            )
        }

    /** The estimate of what is typed; null while something is missing. */
    val estimate: StrikeEstimate? get() = simulation?.let(StrikeSimulator::estimate)

    /** Forgets everything typed here (quick erase, S-44): nothing of it was ever written anywhere. */
    fun clear() {
        status = StrikeStatus.PRIVATE
        sector = StrikeSector.GENERAL
        payText = ""
        weeklyHoursText = DEFAULT_WEEKLY
        dayHoursText = DEFAULT_DAY
        days.clear()
        days.add(StrikeDayEntry())
        daysWithoutService = 0
        near = ""
    }

    companion object {
        /** The part of the device the quick erase (S-44) goes through for the simulator. */
        const val ERASABLE_ID: String = "agir-strike-simulator"

        const val DEFAULT_WEEKLY: String = "35"
        const val DEFAULT_DAY: String = "7"

        /** The longest text kept for a field (an amount, a number of hours, a territory). */
        const val MAX_FIELD: Int = 40
    }
}

/**
 * The simulator for as long as its caller stays on screen ("Agir"), in memory only; it is handed to
 * the quick erase of the device while it lives.
 */
@Composable
fun rememberStrikeSimulatorState(dependencies: AppDependencies): StrikeSimulatorState {
    val state = remember(dependencies) { StrikeSimulatorState() }
    DisposableEffect(state) {
        val unregister = dependencies.erasables.register(deviceErasable(StrikeSimulatorState.ERASABLE_ID) { state.clear() })
        onDispose { unregister() }
    }
    return state
}
