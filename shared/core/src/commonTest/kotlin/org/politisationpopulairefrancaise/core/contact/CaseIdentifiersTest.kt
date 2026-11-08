package org.politisationpopulairefrancaise.core.contact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseIdentifiersTest {

    private val display = Regex("^PPF-[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$")

    @Test
    fun theAlphabetIsCrockfordsAndReadingIsForgiving() {
        assertEquals(32, Crockford32.ALPHABET.length)
        assertEquals(32, Crockford32.ALPHABET.toSet().size)
        listOf('I', 'L', 'O', 'U').forEach { assertFalse(it in Crockford32.ALPHABET, "$it is not a symbol") }
        assertEquals(0, Crockford32.valueOf('o'))
        assertEquals(0, Crockford32.valueOf('O'))
        assertEquals(1, Crockford32.valueOf('i'))
        assertEquals(1, Crockford32.valueOf('L'))
        assertEquals(31, Crockford32.valueOf('z'))
        assertEquals(-1, Crockford32.valueOf('U'))
        assertEquals(-1, Crockford32.valueOf('é'))
        assertEquals("01AB", Crockford32.canonical("o-I a–b"))
        assertNull(Crockford32.canonical("AB_CD"))
    }

    @Test
    fun aNumberIsTenRandomSymbolsShownWithItsPrefix() {
        val numbers = List(200) { CaseNumber.generate() }
        numbers.forEach { number ->
            assertTrue(display.matches(number.display), number.display)
            assertEquals(CaseNumber.SYMBOLS, number.symbols.length)
            assertEquals(number.display, number.toString())
            assertEquals(number, CaseNumber.fromDisplay(number.display))
            assertEquals(number, CaseNumber.parse(number.display.lowercase()))
        }
        // 50 random bits: 200 draws never collide in practice.
        assertEquals(200, numbers.toSet().size)
        // Every symbol of the alphabet shows up across the draws, none other.
        assertEquals(Crockford32.ALPHABET.toSet(), numbers.flatMap { it.symbols.toList() }.toSet())
    }

    @Test
    fun aTypedNumberIsReadWithTheEquivalencesWithOrWithoutPrefix() {
        val expected = CaseNumber.fromDisplay("PPF-AB01C-D2E3F")!!
        listOf("PPF-AB01C-D2E3F", "ppf ab01c d2e3f", "ppf-abOIc-d2e3f", "ABolc D2E3F", "abolcd2e3f", " PPF‑AB01C‑D2E3F ").forEach {
            assertEquals(expected, CaseNumber.parse(it), it)
        }
        // A number may start with the letters of the prefix: the length tells them apart.
        assertEquals("PPFAB12345", CaseNumber.parse("PPFAB12345")!!.symbols)
        assertEquals("AB12345678", CaseNumber.parse("PPF-AB123-45678")!!.symbols)
        listOf("", "PPF-AB01C", "PPF-AB01C-D2E3FG", "XYZ-AB01C-D2E3F", "PPF-AB01C-D2E3U", "PPF-AB01C-D2E3?").forEach {
            assertNull(CaseNumber.parse(it), it)
        }
    }

    @Test
    fun theDisplayFormIsReadStrictly() {
        assertEquals("AB01CD2E3F", CaseNumber.fromDisplay("PPF-AB01C-D2E3F")!!.symbols)
        listOf("ppf-ab01c-d2e3f", "PPF-AB0IC-D2E3F", "PPF AB01C D2E3F", "PPF-AB01CD2E3F", "PPF-AB01C-D2E3F ", "PPF-AB01C-D2E3").forEach {
            assertNull(CaseNumber.fromDisplay(it), it)
        }
    }

    @Test
    fun aCodeIsTwentySymbolsInFiveGroupsAndNeverPrinted() {
        val code = CaseCode.generate()
        assertEquals(CaseCode.SYMBOLS, code.normalized().length)
        assertTrue(Crockford32.isCanonical(code.normalized()))
        assertTrue(Regex("^([0-9A-HJKMNP-TV-Z]{4}-){4}[0-9A-HJKMNP-TV-Z]{4}$").matches(code.display()), code.display())
        assertFalse(code.toString().contains(code.normalized()))
        assertNotEquals(code, CaseCode.generate())
        assertEquals(code, CaseCode.parse(code.display().lowercase().replace('-', ' ')))
    }

    @Test
    fun aTypedCodeIgnoresCaseSpacesAndDashes() {
        val expected = CaseCode.parse("0123456789ABCDEFGHJK")!!
        listOf("0123-4567-89AB-CDEF-GHJK", "o123 4567 89ab cdef ghjk", "OI23-4567-89AB-CDEF-GHJK", "0l23456789abcdefghjk").forEach {
            assertEquals(expected, CaseCode.parse(it), it)
        }
        assertEquals("0123-4567-89AB-CDEF-GHJK", expected.display())
        listOf("0123-4567-89AB-CDEF-GHJ", "0123-4567-89AB-CDEF-GHJKM", "0123-4567-89AB-CDEF-GHJU").forEach { assertNull(CaseCode.parse(it), it) }
    }
}
