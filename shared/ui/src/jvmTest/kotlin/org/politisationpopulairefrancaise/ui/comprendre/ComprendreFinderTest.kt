package org.politisationpopulairefrancaise.ui.comprendre

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComprendreFinderTest {
    @Test
    fun searchIgnoresAccentsCaseAndApostrophes() {
        val title = "Contester une décision de l’administration"
        assertTrue(ComprendreFinder.matches(title, null, listOf("droits"), null, "DECISION administration"))
        assertTrue(ComprendreFinder.matches(title, "Le référé-liberté en 48 heures", emptyList(), null, "refere"))
        assertTrue(ComprendreFinder.matches(title, null, emptyList(), null, "l'administration"))
        assertFalse(ComprendreFinder.matches(title, null, emptyList(), null, "chômage"))
        assertTrue(ComprendreFinder.matches(title, null, emptyList(), null, "  "), "an empty search shows everything")
    }

    @Test
    fun themesGatherTheTopicsOfTheContents() {
        assertTrue(ComprendreFinder.isInTheme(listOf("droits", "recours"), ComprendreTheme.RIGHTS))
        assertTrue(ComprendreFinder.isInTheme(listOf("libertés"), ComprendreTheme.RIGHTS), "topics are compared without accents")
        assertTrue(ComprendreFinder.isInTheme(listOf("budget"), ComprendreTheme.MONEY))
        assertFalse(ComprendreFinder.isInTheme(listOf("budget"), ComprendreTheme.WORK))
        assertFalse(ComprendreFinder.matches("Le budget", null, listOf("budget"), ComprendreTheme.WORK, ""))
        assertTrue(ComprendreFinder.matches("Le budget", null, listOf("budget"), ComprendreTheme.MONEY, "budget"))
    }

    @Test
    fun readingTimeIsRoundedUpAndAtLeastOneMinute() {
        assertEquals(1, ComprendreFinder.readingMinutes(""))
        assertEquals(1, ComprendreFinder.readingMinutes("mot ".repeat(200)))
        assertEquals(2, ComprendreFinder.readingMinutes("mot ".repeat(201)))
    }
}
