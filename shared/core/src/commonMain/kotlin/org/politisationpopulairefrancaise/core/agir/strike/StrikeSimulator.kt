package org.politisationpopulairefrancaise.core.agir.strike

/**
 * What the person typed in the strike simulator (AG-60), for one calendar month. Nothing of it is
 * ever stored or sent: the interface keeps it in memory only (docs/integration/agir-simulateur-greve.md).
 */
data class StrikeSimulation(
    val status: StrikeStatus,
    /** A sector that does not fit [status] is read as [StrikeSector.GENERAL]. */
    val sector: StrikeSector = StrikeSector.GENERAL,
    /**
     * The gross monthly pay, in cents. Private sector: the salary with the bonuses and allowances paid
     * every month. Civil services: the salary, the residence allowance and the bonuses (annual ones as
     * a monthly equivalent), without the family supplement nor the refunds of expenses.
     */
    val monthlyPayCents: Long,
    /** The days of strike of the month, 1 to [StrikeSimulator.MAX_DAYS]. */
    val days: List<StrikeDay>,
    /**
     * Civil services: the days without service (weekend, public holiday, rest) between two days of
     * strike, when the person did not go back to work in between. Ignored with fewer than two days.
     */
    val daysWithoutService: Int = 0,
    /** Private sector: the working time of a week, in minutes (35 hours by default). */
    val weeklyMinutes: Int = StrikeSimulator.LEGAL_WEEKLY_MINUTES,
    /** Private sector: the hours the person had to work on a day of strike, in minutes (7 hours by default). */
    val dayMinutes: Int = StrikeSimulator.DEFAULT_DAY_MINUTES,
) {
    /** The sector the rules read. */
    val effectiveSector: StrikeSector get() = sector.takeIf { it.fits(status) } ?: StrikeSector.GENERAL
}

/** What one day of strike weighs in the estimate. */
data class StrikeDayShare(
    val day: StrikeDay,
    /** Private sector: the minutes not worked that day; null in the civil services. */
    val minutes: Int?,
    /** Territorial and hospital civil services: hours above the share of a whole day, held to one thirtieth. */
    val capped: Boolean,
)

/**
 * The estimate of the simulator: the [rule] applied, the retention (null when the rule is not
 * simulated), its share of the monthly pay, the detail of each day, then what the law asks before
 * the strike ([obligations]), what the person should also know ([notes]) and every official source
 * they rest on ([sources], without duplicates, in the order of the screen).
 */
data class StrikeEstimate(
    val rule: StrikeRule,
    val retentionCents: Long?,
    /** The share of the monthly pay, in hundredths of a percent (1333 = 13,33 %); null when not simulated. */
    val shareBasisPoints: Long?,
    val days: List<StrikeDayShare>,
    /** State: the thirtieths counted (days of strike and days without service), at most 30. */
    val thirtieths: Int?,
    /** Civil services: the days without service between two days of strike that were counted. */
    val daysWithoutServiceCounted: Int,
    /** Private sector: the monthly hours, in hundredths of an hour (15167 for 35 hours a week). */
    val monthlyHoursHundredths: Long?,
    /** The strike weighs more than the month: the retention is held to the whole monthly pay. */
    val cappedAtMonthlyPay: Boolean,
    val obligations: List<StrikeObligation>,
    val notes: List<StrikeNote>,
    val sources: List<StrikeSource>,
)

/**
 * The calculator of the strike simulator (AG-60), pure and exact: every share is a fraction of
 * whole numbers, and the amount is rounded once, at the end, to the nearest cent (half a cent
 * upwards). The rules and their sources: docs/integration/agir-simulateur-greve.md, section 2.
 *
 * - Private sector: pay × hours not worked ÷ monthly hours, with monthly hours = weekly hours × 52 ÷ 12
 *   (code du travail, L3242-1; 151,67 hours for 35 hours). A whole day weighs the hours the person had
 *   to work that day, half a day half of them.
 * - State: pay × thirtieths ÷ 30, one thirtieth for each day of strike, however short, and one for each
 *   day without service between two days of strike (CGFP, L711-3; Conseil d'État, 7 juillet 1978).
 * - Territorial and hospital: 1/30 a whole day, 1/60 half a day, hours ÷ 151,67 (held to 1/30 for the
 *   day), and 1/30 for each day without service between two days of strike (fiche F499).
 * - A company in charge of a public service outside land transport: not simulated (to be confirmed).
 *
 * The retention never exceeds the monthly pay.
 */
object StrikeSimulator {
    const val MAX_DAYS: Int = 31
    const val MAX_DAY_MINUTES: Int = 24 * 60
    const val MAX_DAYS_WITHOUT_SERVICE: Int = 30

    /** 1 000 000 € a month: anything above is a typing error. */
    const val MAX_PAY_CENTS: Long = 100_000_000L

    /** The legal working time, 35 hours a week (code du travail, L3121-27). */
    const val LEGAL_WEEKLY_MINUTES: Int = 35 * 60
    const val DEFAULT_DAY_MINUTES: Int = 7 * 60
    const val MIN_WEEKLY_MINUTES: Int = 60
    const val MAX_WEEKLY_MINUTES: Int = 60 * 60

    /** The thirtieth of the civil services. */
    const val THIRTY: Long = 30L

    /** 151,67 hours, in hundredths of an hour: the hourly fraction of the territorial and hospital civil services (fiche F499). */
    const val PUBLIC_MONTHLY_HOURS_HUNDREDTHS: Long = 15_167L

    fun estimate(simulation: StrikeSimulation): StrikeEstimate {
        require(simulation.monthlyPayCents in 1..MAX_PAY_CENTS) { "monthlyPayCents: 1 to $MAX_PAY_CENTS" }
        require(simulation.days.size in 1..MAX_DAYS) { "days: 1 to $MAX_DAYS" }
        require(simulation.daysWithoutService in 0..MAX_DAYS_WITHOUT_SERVICE) { "daysWithoutService: 0 to $MAX_DAYS_WITHOUT_SERVICE" }
        require(simulation.weeklyMinutes in MIN_WEEKLY_MINUTES..MAX_WEEKLY_MINUTES) { "weeklyMinutes: $MIN_WEEKLY_MINUTES to $MAX_WEEKLY_MINUTES" }
        require(simulation.dayMinutes in 1..MAX_DAY_MINUTES) { "dayMinutes: 1 to $MAX_DAY_MINUTES" }
        val status = simulation.status
        val sector = simulation.effectiveSector
        val rule = ruleFor(status, sector)
        val betweenDays = if (status.isPublic && simulation.days.size >= 2) simulation.daysWithoutService else 0
        val computed = when (rule) {
            StrikeRule.STATE_THIRTIETH -> stateThirtieths(simulation, betweenDays)
            StrikeRule.PUBLIC_PROPORTIONAL -> publicProportional(simulation, betweenDays)
            StrikeRule.PRIVATE_PROPORTIONAL, StrikeRule.TRANSPORT_PROPORTIONAL -> privateProportional(simulation)
            StrikeRule.NOT_SIMULATED -> Computed(
                shares = simulation.days.map { StrikeDayShare(it, minutes = null, capped = false) },
                raw = null,
            )
        }
        val raw = computed.raw
        val capped = raw != null && raw > Fraction.ONE
        val share = raw?.let { if (capped) Fraction.ONE else it }
        val shares = computed.shares
        val thirtieths = computed.thirtieths
        val monthlyHours = computed.monthlyHoursHundredths
        val obligations = obligationsFor(status, sector)
        val notes = notesFor(status, sector)
        val sources = sourcesFor(status, sector)
        return StrikeEstimate(
            rule = rule,
            retentionCents = share?.let { roundHalfUp(simulation.monthlyPayCents * it.numerator, it.denominator) },
            shareBasisPoints = share?.let { roundHalfUp(it.numerator * 10_000, it.denominator) },
            days = shares,
            thirtieths = thirtieths,
            daysWithoutServiceCounted = betweenDays,
            monthlyHoursHundredths = monthlyHours,
            cappedAtMonthlyPay = capped,
            obligations = obligations,
            notes = notes,
            sources = sources,
        )
    }

    /**
     * Every official source behind the rule, the obligations and the notes of [status] in [sector],
     * once each, in the order of the screen (the rule's first). Known before any amount is typed.
     */
    fun sourcesFor(status: StrikeStatus, sector: StrikeSector): List<StrikeSource> =
        (ruleFor(status, sector).sources + obligationsFor(status, sector).flatMap { it.sources } + notesFor(status, sector).flatMap { it.sources }).distinct()

    /** The rule of the retention for [status] in [sector] (a sector that does not fit the status is read as general). */
    fun ruleFor(status: StrikeStatus, sector: StrikeSector): StrikeRule = when (status) {
        StrikeStatus.PRIVATE -> when (sector.takeIf { it.fits(status) } ?: StrikeSector.GENERAL) {
            StrikeSector.LAND_TRANSPORT -> StrikeRule.TRANSPORT_PROPORTIONAL
            StrikeSector.PRIVATE_PUBLIC_SERVICE -> StrikeRule.NOT_SIMULATED
            else -> StrikeRule.PRIVATE_PROPORTIONAL
        }
        StrikeStatus.STATE -> StrikeRule.STATE_THIRTIETH
        StrikeStatus.TERRITORIAL, StrikeStatus.HOSPITAL -> StrikeRule.PUBLIC_PROPORTIONAL
    }

    /** The notice first, then the individual declaration, for [status] in [sector]. */
    fun obligationsFor(status: StrikeStatus, sector: StrikeSector): List<StrikeObligation> {
        val effective = sector.takeIf { it.fits(status) } ?: StrikeSector.GENERAL
        return when (status) {
            StrikeStatus.PRIVATE -> when (effective) {
                StrikeSector.LAND_TRANSPORT -> listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.DECLARATION_LAND_TRANSPORT)
                StrikeSector.AIR_TRANSPORT -> listOf(StrikeObligation.NO_NOTICE_PRIVATE, StrikeObligation.DECLARATION_AIR_TRANSPORT)
                StrikeSector.PRIVATE_PUBLIC_SERVICE -> listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NO_KNOWN_DECLARATION)
                else -> listOf(StrikeObligation.NO_NOTICE_PRIVATE, StrikeObligation.NO_KNOWN_DECLARATION)
            }
            StrikeStatus.STATE -> when (effective) {
                StrikeSector.PRIMARY_SCHOOL -> listOf(StrikeObligation.NOTICE_PRIMARY_SCHOOL, StrikeObligation.DECLARATION_PRIMARY_SCHOOL)
                StrikeSector.AIR_NAVIGATION -> listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.DECLARATION_AIR_NAVIGATION)
                else -> listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NO_KNOWN_DECLARATION)
            }
            StrikeStatus.TERRITORIAL -> listOf(
                StrikeObligation.NOTICE_TERRITORIAL,
                if (effective == StrikeSector.TERRITORIAL_CONTINUITY) StrikeObligation.DECLARATION_TERRITORIAL else StrikeObligation.NO_KNOWN_DECLARATION,
            )
            StrikeStatus.HOSPITAL -> listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NO_KNOWN_DECLARATION)
        }
    }

    /** What the person should also know, for [status] in [sector]. */
    fun notesFor(status: StrikeStatus, sector: StrikeSector): List<StrikeNote> {
        val effective = sector.takeIf { it.fits(status) } ?: StrikeSector.GENERAL
        return when (status) {
            StrikeStatus.PRIVATE -> buildList {
                add(StrikeNote.ACCESSORIES)
                add(StrikeNote.PAYSLIP_SILENT)
                add(StrikeNote.NO_DISCRIMINATION)
                add(StrikeNote.BONUSES)
                add(StrikeNote.FIXED_DAYS_NOT_SIMULATED)
                if (effective == StrikeSector.LAND_TRANSPORT || effective == StrikeSector.PRIVATE_PUBLIC_SERVICE) add(StrikeNote.NO_ROTATING_STRIKES)
            }
            StrikeStatus.STATE -> listOf(
                StrikeNote.FAMILY_SUPPLEMENT_KEPT,
                StrikeNote.NOT_CONTRIBUTED,
                StrikeNote.SEIZABLE_CAP,
                StrikeNote.MONTH_OF_STRIKE,
                StrikeNote.LEAVE_BEFORE_NOTICE,
                StrikeNote.RETIREMENT_STATE,
                StrikeNote.NO_ROTATING_STRIKES,
            )
            StrikeStatus.TERRITORIAL, StrikeStatus.HOSPITAL -> listOf(
                StrikeNote.FAMILY_SUPPLEMENT_KEPT,
                StrikeNote.NOT_CONTRIBUTED,
                StrikeNote.SEIZABLE_CAP,
                StrikeNote.MONTH_OF_STRIKE,
                StrikeNote.GUARDS_NOT_SIMULATED,
                StrikeNote.RETIREMENT_LOCAL,
                StrikeNote.NO_ROTATING_STRIKES,
            )
        }
    }

    /** The shares before the hold to the whole month. */
    private class Computed(
        val shares: List<StrikeDayShare>,
        val raw: Fraction?,
        val thirtieths: Int? = null,
        val monthlyHoursHundredths: Long? = null,
    )

    /** State: one thirtieth for each day of strike and each day without service in between. */
    private fun stateThirtieths(simulation: StrikeSimulation, betweenDays: Int): Computed {
        val counted = simulation.days.size + betweenDays
        return Computed(
            shares = simulation.days.map { StrikeDayShare(it, minutes = null, capped = false) },
            raw = Fraction.of(counted.toLong(), THIRTY),
            thirtieths = minOf(counted, THIRTY.toInt()),
        )
    }

    /** Territorial and hospital: the share of each day, plus one thirtieth for each day without service in between. */
    private fun publicProportional(simulation: StrikeSimulation, betweenDays: Int): Computed {
        var total = Fraction.of(betweenDays.toLong(), THIRTY)
        val shares = simulation.days.map { day ->
            val (share, capped) = publicShare(day)
            total += share
            StrikeDayShare(day, minutes = null, capped = capped)
        }
        return Computed(shares = shares, raw = total)
    }

    /** Private sector: the minutes not worked over the monthly minutes (weekly × 52 ÷ 12). */
    private fun privateProportional(simulation: StrikeSimulation): Computed {
        // In half-minutes, so that half of an odd day stays exact.
        var halfMinutes = 0L
        val shares = simulation.days.map { day ->
            val dayHalfMinutes = when (day) {
                StrikeDay.FullDay -> 2L * simulation.dayMinutes
                StrikeDay.HalfDay -> simulation.dayMinutes.toLong()
                is StrikeDay.Hours -> 2L * day.minutes
            }
            halfMinutes += dayHalfMinutes
            StrikeDayShare(day, minutes = (dayHalfMinutes / 2).toInt(), capped = false)
        }
        // hours ÷ (weekly hours × 52 ÷ 12) = minutes × 12 ÷ (weekly minutes × 52)
        return Computed(
            shares = shares,
            raw = Fraction.of(halfMinutes * 12, 2L * simulation.weeklyMinutes * 52),
            monthlyHoursHundredths = roundHalfUp(simulation.weeklyMinutes.toLong() * 52 * 100, 12L * 60),
        )
    }

    /** Territorial and hospital: the share of one day of strike, and whether hours were held to one thirtieth. */
    private fun publicShare(day: StrikeDay): Pair<Fraction, Boolean> = when (day) {
        StrikeDay.FullDay -> Fraction.of(1, THIRTY) to false
        StrikeDay.HalfDay -> Fraction.of(1, 2 * THIRTY) to false
        is StrikeDay.Hours -> {
            // minutes ÷ 60 ÷ 151,67 = minutes × 100 ÷ (60 × 15 167)
            val hours = Fraction.of(day.minutes.toLong() * 100, 60 * PUBLIC_MONTHLY_HOURS_HUNDREDTHS)
            val wholeDay = Fraction.of(1, THIRTY)
            if (hours > wholeDay) wholeDay to true else hours to false
        }
    }

    /** [numerator] ÷ [denominator] rounded to the nearest whole number, halves upwards (both positive or zero). */
    internal fun roundHalfUp(numerator: Long, denominator: Long): Long = (2 * numerator + denominator) / (2 * denominator)
}

/** A non-negative fraction of whole numbers, always reduced; enough for the shares of a monthly pay. */
internal class Fraction private constructor(val numerator: Long, val denominator: Long) : Comparable<Fraction> {

    operator fun plus(other: Fraction): Fraction =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    override fun compareTo(other: Fraction): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)

    override fun equals(other: Any?): Boolean = other is Fraction && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = "$numerator/$denominator"

    companion object {
        val ONE: Fraction = Fraction(1, 1)

        fun of(numerator: Long, denominator: Long): Fraction {
            require(numerator >= 0 && denominator > 0) { "a non-negative fraction with a positive denominator" }
            if (numerator == 0L) return Fraction(0, 1)
            val divisor = gcd(numerator, denominator)
            return Fraction(numerator / divisor, denominator / divisor)
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}
