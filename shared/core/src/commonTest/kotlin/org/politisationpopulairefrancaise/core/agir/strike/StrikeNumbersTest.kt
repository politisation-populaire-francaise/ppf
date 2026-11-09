package org.politisationpopulairefrancaise.core.agir.strike

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reading the amounts and hours typed in the strike simulator, and writing them the French way. */
class StrikeNumbersTest {
    private val nbsp = StrikeNumbers.NBSP

    @Test
    fun amountsAreReadWithACommaOrAPointAndSpaces() {
        assertEquals(240_000L, StrikeNumbers.parseEuros("2400"))
        assertEquals(240_050L, StrikeNumbers.parseEuros("2 400,50"))
        assertEquals(240_050L, StrikeNumbers.parseEuros("2${nbsp}400,5 €"))
        assertEquals(240_005L, StrikeNumbers.parseEuros("2400.05"))
        assertEquals(1L, StrikeNumbers.parseEuros("0,01"))
    }

    @Test
    fun wrongAmountsAreRefused() {
        assertNull(StrikeNumbers.parseEuros(""))
        assertNull(StrikeNumbers.parseEuros("0"))
        assertNull(StrikeNumbers.parseEuros("abc"))
        assertNull(StrikeNumbers.parseEuros("2.400,50"), "two separators")
        assertNull(StrikeNumbers.parseEuros("2400,505"), "three decimals")
        assertNull(StrikeNumbers.parseEuros("-100"))
        assertNull(StrikeNumbers.parseEuros(",50"))
        assertNull(StrikeNumbers.parseEuros("1000001"), "more than 1 000 000 €")
    }

    @Test
    fun hoursAreReadInDecimalsOrWithAnH() {
        assertEquals(420, StrikeNumbers.parseHours("7"))
        assertEquals(450, StrikeNumbers.parseHours("7,5"))
        assertEquals(435, StrikeNumbers.parseHours("7.25"))
        assertEquals(440, StrikeNumbers.parseHours("7,33"), "439,8 minutes, to the nearest minute")
        assertEquals(450, StrikeNumbers.parseHours("7h30"))
        assertEquals(450, StrikeNumbers.parseHours("7 h 30"))
        assertEquals(180, StrikeNumbers.parseHours("3H"))
        assertEquals(30, StrikeNumbers.parseHours("0,5"))
        assertEquals(24 * 60, StrikeNumbers.parseHours("24"))
    }

    @Test
    fun wrongHoursAreRefused() {
        assertNull(StrikeNumbers.parseHours(""))
        assertNull(StrikeNumbers.parseHours("0"))
        assertNull(StrikeNumbers.parseHours("25"))
        assertNull(StrikeNumbers.parseHours("7h75"))
        assertNull(StrikeNumbers.parseHours("h30"))
        assertNull(StrikeNumbers.parseHours("sept"))
        assertNull(StrikeNumbers.parseHours("7,5,5"))
    }

    @Test
    fun resultsAreWrittenTheFrenchWay() {
        assertEquals("320,00${nbsp}€", StrikeNumbers.formatEuros(32_000))
        assertEquals("2${nbsp}400,05${nbsp}€", StrikeNumbers.formatEuros(240_005))
        assertEquals("1${nbsp}000${nbsp}000,00${nbsp}€", StrikeNumbers.formatEuros(100_000_000))
        assertEquals("0,07${nbsp}€", StrikeNumbers.formatEuros(7))
        assertEquals("13,33${nbsp}%", StrikeNumbers.formatPercent(1_333))
        assertEquals("100,00${nbsp}%", StrikeNumbers.formatPercent(10_000))
        assertEquals("7${nbsp}h", StrikeNumbers.formatHours(420))
        assertEquals("7${nbsp}h${nbsp}30", StrikeNumbers.formatHours(450))
        assertEquals("0${nbsp}h${nbsp}05", StrikeNumbers.formatHours(5))
        assertEquals("151,67", StrikeNumbers.formatHundredths(15_167))
        assertEquals("169", StrikeNumbers.formatHundredths(16_900))
        assertEquals("7", StrikeNumbers.hoursInput(420))
        assertEquals("7,5", StrikeNumbers.hoursInput(450))
        assertEquals("7h48", StrikeNumbers.hoursInput(468))
    }
}
