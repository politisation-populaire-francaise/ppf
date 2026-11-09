package org.politisationpopulairefrancaise.core.agir.strike

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The calculator of the strike simulator (AG-60) against the examples computed by hand in
 * docs/integration/agir-simulateur-greve.md, section 3 (numbers in the names), then the rounding,
 * the holds, the sectors, the obligations and the sources.
 */
class StrikeSimulatorTest {

    private fun hours(h: Int, m: Int = 0): StrikeDay = StrikeDay.Hours(h * 60 + m)

    private fun estimate(
        status: StrikeStatus,
        pay: Long,
        days: List<StrikeDay>,
        sector: StrikeSector = StrikeSector.GENERAL,
        between: Int = 0,
        weekly: Int = StrikeSimulator.LEGAL_WEEKLY_MINUTES,
        day: Int = StrikeSimulator.DEFAULT_DAY_MINUTES,
    ): StrikeEstimate = StrikeSimulator.estimate(StrikeSimulation(status, sector, pay, days, between, weekly, day))

    // State: the indivisible thirtieth

    @Test
    fun example1StateFridayAndMondayCountFourThirtieths() {
        val result = estimate(StrikeStatus.STATE, 240_000, listOf(hours(1), StrikeDay.FullDay), between = 2)
        assertEquals(StrikeRule.STATE_THIRTIETH, result.rule)
        assertEquals(4, result.thirtieths)
        assertEquals(2, result.daysWithoutServiceCounted)
        assertEquals(32_000L, result.retentionCents, "2 400 × 4 ÷ 30 = 320,00 €")
        assertEquals(1_333L, result.shareBasisPoints, "13,33 %")
        assertFalse(result.cappedAtMonthlyPay)
    }

    @Test
    fun example2StateOneHourCostsAWholeThirtieth() {
        val result = estimate(StrikeStatus.STATE, 195_000, listOf(hours(1)))
        assertEquals(1, result.thirtieths)
        assertEquals(6_500L, result.retentionCents, "1 950 ÷ 30 = 65,00 €")
        // A full day or a half-day weighs the same.
        assertEquals(6_500L, estimate(StrikeStatus.STATE, 195_000, listOf(StrikeDay.FullDay)).retentionCents)
        assertEquals(6_500L, estimate(StrikeStatus.STATE, 195_000, listOf(StrikeDay.HalfDay)).retentionCents)
    }

    @Test
    fun example10StateIsHeldToTheWholeMonth() {
        val result = estimate(StrikeStatus.STATE, 180_000, List(25) { StrikeDay.FullDay }, between = 6)
        assertEquals(30, result.thirtieths, "31 thirtieths, held to 30")
        assertTrue(result.cappedAtMonthlyPay)
        assertEquals(180_000L, result.retentionCents)
        assertEquals(10_000L, result.shareBasisPoints)
    }

    @Test
    fun example11HalfACentIsRoundedUpwards() {
        // 200 025 cents ÷ 30 = 6 667,5 cents
        assertEquals(6_668L, estimate(StrikeStatus.STATE, 200_025, listOf(StrikeDay.FullDay)).retentionCents)
        // 100 000 ÷ 30 = 3 333,33… cents
        assertEquals(3_333L, estimate(StrikeStatus.STATE, 100_000, listOf(StrikeDay.FullDay)).retentionCents)
    }

    @Test
    fun daysWithoutServiceCountOnlyBetweenTwoDaysOfStrike() {
        val alone = estimate(StrikeStatus.STATE, 300_000, listOf(StrikeDay.FullDay), between = 2)
        assertEquals(0, alone.daysWithoutServiceCounted, "nothing lies between a single day")
        assertEquals(1, alone.thirtieths)
        assertEquals(10_000L, alone.retentionCents)
        // In the private sector, a day without work is never withheld.
        val privateDays = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay, StrikeDay.FullDay), between = 2)
        assertEquals(0, privateDays.daysWithoutServiceCounted)
        assertEquals(18_462L, privateDays.retentionCents, "2 000 × 14 × 12 ÷ 1 820 = 184,6154")
    }

    // Territorial and hospital: proportional

    @Test
    fun example3TerritorialAddsADayAHalfDayAndHours() {
        val result = estimate(StrikeStatus.TERRITORIAL, 210_000, listOf(StrikeDay.FullDay, StrikeDay.HalfDay, hours(2)))
        assertEquals(StrikeRule.PUBLIC_PROPORTIONAL, result.rule)
        assertEquals(13_269L, result.retentionCents, "70 + 35 + 27,6917 = 132,69 €")
        assertNull(result.thirtieths)
        assertTrue(result.days.none { it.capped })
    }

    @Test
    fun example4HoursAreHeldToOneThirtiethADay() {
        val result = estimate(StrikeStatus.TERRITORIAL, 210_000, listOf(hours(7)))
        assertEquals(7_000L, result.retentionCents, "7 ÷ 151,67 is more than 1/30: 2 100 ÷ 30")
        assertTrue(result.days.single().capped)
        // Five hours stay below the thirtieth: 2 100 × 5 ÷ 151,67 = 69,2292
        val five = estimate(StrikeStatus.TERRITORIAL, 210_000, listOf(hours(5)))
        assertEquals(6_923L, five.retentionCents)
        assertFalse(five.days.single().capped)
    }

    @Test
    fun example5HospitalTwoDaysInARow() {
        val result = estimate(StrikeStatus.HOSPITAL, 230_000, listOf(StrikeDay.FullDay, StrikeDay.FullDay))
        assertEquals(StrikeRule.PUBLIC_PROPORTIONAL, result.rule)
        assertEquals(15_333L, result.retentionCents, "2 300 × 2 ÷ 30 = 153,33 €")
    }

    @Test
    fun territorialDaysWithoutServiceAddAThirtiethEach() {
        val result = estimate(StrikeStatus.TERRITORIAL, 300_000, listOf(StrikeDay.FullDay, StrikeDay.FullDay), between = 2)
        assertEquals(2, result.daysWithoutServiceCounted)
        assertEquals(40_000L, result.retentionCents, "3 000 × 4 ÷ 30")
    }

    @Test
    fun anHourInTheTerritorialServiceIsDividedBy15167Hundredths() {
        // 1 516,70 € ÷ 151,67 = 10,00 € an hour
        assertEquals(1_000L, estimate(StrikeStatus.HOSPITAL, 151_670, listOf(hours(1))).retentionCents)
        assertEquals(1_500L, estimate(StrikeStatus.HOSPITAL, 151_670, listOf(hours(1, 30))).retentionCents)
    }

    // Private sector: proportional to the hours not worked

    @Test
    fun example6PrivateOneDayOfSevenHours() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay))
        assertEquals(StrikeRule.PRIVATE_PROPORTIONAL, result.rule)
        assertEquals(9_231L, result.retentionCents, "168 000 ÷ 1 820 = 92,3077")
        assertEquals(15_167L, result.monthlyHoursHundredths, "35 × 52 ÷ 12 = 151,67 h")
        assertEquals(420, result.days.single().minutes)
    }

    @Test
    fun example7PrivateADayAndTwoHours() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay, hours(2)))
        assertEquals(11_868L, result.retentionCents, "2 000 × 9 × 12 ÷ 1 820 = 118,6813")
        assertEquals(listOf(420, 120), result.days.map { it.minutes })
    }

    @Test
    fun example8PrivateThirtyNineHoursAWeek() {
        val result = estimate(StrikeStatus.PRIVATE, 250_000, listOf(hours(7, 48)), weekly = 39 * 60, day = 7 * 60 + 48)
        assertEquals(16_900L, result.monthlyHoursHundredths, "39 × 52 ÷ 12 = 169 h")
        assertEquals(11_538L, result.retentionCents, "2 500 × 7,8 ÷ 169 = 115,3846")
        // The same day typed as a whole day of 7 h 48.
        assertEquals(11_538L, estimate(StrikeStatus.PRIVATE, 250_000, listOf(StrikeDay.FullDay), weekly = 39 * 60, day = 7 * 60 + 48).retentionCents)
    }

    @Test
    fun example9PrivateIsHeldToTheWholeMonth() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, List(23) { StrikeDay.FullDay })
        assertTrue(result.cappedAtMonthlyPay, "161 h is more than 151,67 h")
        assertEquals(200_000L, result.retentionCents)
    }

    @Test
    fun example13PrivateHalfDay() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.HalfDay))
        assertEquals(4_615L, result.retentionCents, "2 000 × 3,5 × 12 ÷ 1 820 = 46,1538")
        assertEquals(210, result.days.single().minutes)
    }

    @Test
    fun landTransportFollowsItsOwnTextWithTheSameFormula() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay), sector = StrikeSector.LAND_TRANSPORT)
        assertEquals(StrikeRule.TRANSPORT_PROPORTIONAL, result.rule)
        assertEquals(9_231L, result.retentionCents)
        assertTrue(StrikeSource.TRANSPORT_CODE_L1324_11 in result.sources)
    }

    @Test
    fun example12ACompanyInChargeOfAPublicServiceIsNotSimulated() {
        val result = estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay), sector = StrikeSector.PRIVATE_PUBLIC_SERVICE)
        assertEquals(StrikeRule.NOT_SIMULATED, result.rule)
        assertNull(result.retentionCents)
        assertNull(result.shareBasisPoints)
        assertFalse(result.cappedAtMonthlyPay)
        assertEquals(StrikeObligation.NOTICE_PUBLIC_SERVICE, result.obligations.first())
        assertTrue(StrikeSource.LABOUR_CODE_L2512 in result.sources)
    }

    // The status changes the result

    @Test
    fun theSameStrikeCostsDifferentlyByStatus() {
        val days = listOf(hours(2))
        val byStatus = StrikeStatus.entries.associateWith { estimate(it, 300_000, days).retentionCents }
        assertEquals(10_000L, byStatus[StrikeStatus.STATE], "a thirtieth")
        assertEquals(3_956L, byStatus[StrikeStatus.TERRITORIAL], "3 000 × 2 ÷ 151,67 = 39,5595")
        assertEquals(3_956L, byStatus[StrikeStatus.HOSPITAL])
        assertEquals(3_956L, byStatus[StrikeStatus.PRIVATE], "3 000 × 2 × 12 ÷ 1 820 = 39,5604")
    }

    // Sectors and obligations

    @Test
    fun aSectorThatDoesNotFitTheStatusIsReadAsGeneral() {
        val simulation = StrikeSimulation(StrikeStatus.HOSPITAL, StrikeSector.PRIMARY_SCHOOL, 200_000, listOf(StrikeDay.FullDay))
        assertEquals(StrikeSector.GENERAL, simulation.effectiveSector)
        assertEquals(
            listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NO_KNOWN_DECLARATION),
            StrikeSimulator.estimate(simulation).obligations,
        )
        assertEquals(listOf(StrikeSector.GENERAL, StrikeSector.PRIMARY_SCHOOL, StrikeSector.AIR_NAVIGATION), StrikeSector.forStatus(StrikeStatus.STATE))
        assertEquals(listOf(StrikeSector.GENERAL), StrikeSector.forStatus(StrikeStatus.HOSPITAL))
    }

    @Test
    fun eachSectorBringsItsNoticeAndItsDeclaration() {
        fun obligations(status: StrikeStatus, sector: StrikeSector) = StrikeSimulator.obligationsFor(status, sector)
        assertEquals(listOf(StrikeObligation.NO_NOTICE_PRIVATE, StrikeObligation.NO_KNOWN_DECLARATION), obligations(StrikeStatus.PRIVATE, StrikeSector.GENERAL))
        assertEquals(listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.DECLARATION_LAND_TRANSPORT), obligations(StrikeStatus.PRIVATE, StrikeSector.LAND_TRANSPORT))
        assertEquals(listOf(StrikeObligation.NO_NOTICE_PRIVATE, StrikeObligation.DECLARATION_AIR_TRANSPORT), obligations(StrikeStatus.PRIVATE, StrikeSector.AIR_TRANSPORT))
        assertEquals(listOf(StrikeObligation.NOTICE_PRIMARY_SCHOOL, StrikeObligation.DECLARATION_PRIMARY_SCHOOL), obligations(StrikeStatus.STATE, StrikeSector.PRIMARY_SCHOOL))
        assertEquals(listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.DECLARATION_AIR_NAVIGATION), obligations(StrikeStatus.STATE, StrikeSector.AIR_NAVIGATION))
        assertEquals(listOf(StrikeObligation.NOTICE_TERRITORIAL, StrikeObligation.NO_KNOWN_DECLARATION), obligations(StrikeStatus.TERRITORIAL, StrikeSector.GENERAL))
        assertEquals(listOf(StrikeObligation.NOTICE_TERRITORIAL, StrikeObligation.DECLARATION_TERRITORIAL), obligations(StrikeStatus.TERRITORIAL, StrikeSector.TERRITORIAL_CONTINUITY))
        assertEquals(listOf(StrikeObligation.NOTICE_PUBLIC_SERVICE, StrikeObligation.NO_KNOWN_DECLARATION), obligations(StrikeStatus.HOSPITAL, StrikeSector.GENERAL))
    }

    @Test
    fun theNotesFollowTheStatus() {
        val privateNotes = StrikeSimulator.notesFor(StrikeStatus.PRIVATE, StrikeSector.GENERAL)
        assertTrue(StrikeNote.BONUSES in privateNotes && StrikeNote.PAYSLIP_SILENT in privateNotes)
        assertFalse(StrikeNote.NO_ROTATING_STRIKES in privateNotes, "rotating strikes are forbidden only in public services")
        assertTrue(StrikeNote.NO_ROTATING_STRIKES in StrikeSimulator.notesFor(StrikeStatus.PRIVATE, StrikeSector.LAND_TRANSPORT))
        val stateNotes = StrikeSimulator.notesFor(StrikeStatus.STATE, StrikeSector.GENERAL)
        assertTrue(StrikeNote.LEAVE_BEFORE_NOTICE in stateNotes && StrikeNote.RETIREMENT_STATE in stateNotes)
        val localNotes = StrikeSimulator.notesFor(StrikeStatus.TERRITORIAL, StrikeSector.GENERAL)
        assertTrue(StrikeNote.GUARDS_NOT_SIMULATED in localNotes && StrikeNote.RETIREMENT_LOCAL in localNotes)
        assertFalse(StrikeNote.LEAVE_BEFORE_NOTICE in localNotes)
    }

    @Test
    fun everySourceIsListedOnceAndIsAnOfficialHttpsAddress() {
        val result = estimate(StrikeStatus.STATE, 200_000, listOf(StrikeDay.FullDay), sector = StrikeSector.PRIMARY_SCHOOL)
        assertEquals(result.sources.distinct(), result.sources)
        assertEquals(StrikeRule.STATE_THIRTIETH.sources.first(), result.sources.first(), "the rule's sources come first")
        assertTrue(StrikeSource.EDUCATION_CODE_L133_4 in result.sources)
        val official = setOf("www.legifrance.gouv.fr", "www.service-public.gouv.fr", "www.senat.fr")
        StrikeSource.entries.forEach { source ->
            assertTrue(source.url.startsWith("https://"), source.name)
            assertTrue(source.url.removePrefix("https://").substringBefore('/') in official, "${source.name}: ${source.url}")
        }
        assertEquals(2026, STRIKE_RULES_VERIFIED_ON.year)
        assertEquals(11, STRIKE_RULES_VERIFIED_ON.month)
        assertEquals(9, STRIKE_RULES_VERIFIED_ON.day)
    }

    // Inputs

    @Test
    fun invalidInputsAreRefused() {
        assertFailsWith<IllegalArgumentException> { estimate(StrikeStatus.STATE, 0, listOf(StrikeDay.FullDay)) }
        assertFailsWith<IllegalArgumentException> { estimate(StrikeStatus.STATE, StrikeSimulator.MAX_PAY_CENTS + 1, listOf(StrikeDay.FullDay)) }
        assertFailsWith<IllegalArgumentException> { estimate(StrikeStatus.STATE, 200_000, emptyList()) }
        assertFailsWith<IllegalArgumentException> { estimate(StrikeStatus.STATE, 200_000, List(32) { StrikeDay.FullDay }) }
        assertFailsWith<IllegalArgumentException> { estimate(StrikeStatus.PRIVATE, 200_000, listOf(StrikeDay.FullDay), weekly = 0) }
        assertFailsWith<IllegalArgumentException> { StrikeDay.Hours(0) }
        assertFailsWith<IllegalArgumentException> { StrikeDay.Hours(24 * 60 + 1) }
    }

    @Test
    fun roundingIsHalfUpOnWholeCents() {
        assertEquals(3L, StrikeSimulator.roundHalfUp(5, 2), "2,5 becomes 3")
        assertEquals(2L, StrikeSimulator.roundHalfUp(9, 4), "2,25 becomes 2")
        assertEquals(0L, StrikeSimulator.roundHalfUp(0, 7))
    }
}
