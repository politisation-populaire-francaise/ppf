package org.politisationpopulairefrancaise.core.revendications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevendicationThemesTest {

    @Test
    fun idsAreUniqueAndAliasesBelongToOneThemeOnly() {
        val ids = RevendicationThemes.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val aliases = RevendicationThemes.ALL.flatMap { it.aliases }
        assertEquals(aliases.size, aliases.toSet().size, "an alias names one theme only")
        assertTrue(aliases.none { it in ids }, "an alias is never another theme's id")
    }

    @Test
    fun topicsOfTheBundledExamplesFindTheirThemes() {
        assertEquals(listOf(RevendicationThemes.EDUCATION, RevendicationThemes.POUVOIR_D_ACHAT), RevendicationThemes.of(listOf("education", "pouvoir-d-achat")))
        assertEquals(listOf(RevendicationThemes.TRANSPORTS), RevendicationThemes.of(listOf("transports", "metropole")))
        assertEquals(listOf(RevendicationThemes.DEMOCRATIE), RevendicationThemes.of(listOf("transparence", "institutions")))
    }

    @Test
    fun topicsAreComparedWithoutAccentsCaseOrSpaces() {
        assertTrue(RevendicationThemes.SANTE.matches("Santé"))
        assertTrue(RevendicationThemes.SANTE.matches("médical"))
        assertTrue(RevendicationThemes.POUVOIR_D_ACHAT.matches("Vie chère"))
        assertEquals("vie-chere", RevendicationThemes.foldTopic("Vie  chère"))
        assertNull(RevendicationThemes.byId("inconnu"))
    }
}
