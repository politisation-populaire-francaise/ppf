package org.politisationpopulairefrancaise.server.groups

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupNamesTest {

    @Test
    fun lowercasesRemovesAccentsAndTurnsPunctuationIntoSingleSpaces() {
        assertEquals("collectif education lyon 3e", GroupNames.normalize("Collectif  Éducation - Lyon 3e"))
        assertEquals("collectif education", GroupNames.normalize("  COLLECTIF ÉDUCATION  "))
        assertEquals("saint etienne", GroupNames.normalize("Saint-Étienne"))
        assertEquals("l ile d yeu", GroupNames.normalize("L’Île-d'Yeu"))
        assertEquals("c est ca", GroupNames.normalize("Ç’est ça…"))
        assertEquals("nantes metropole", GroupNames.normalize("Nantes🌊Métropole"))
        assertEquals("exemple collectif", GroupNames.normalize("[EXEMPLE] Collectif"))
        assertEquals("a b", GroupNames.normalize("a\t\n b"))
    }

    @Test
    fun textsMadeOfPunctuationOnlyNormalizeToNothing() {
        assertEquals("", GroupNames.normalize(""))
        assertEquals("", GroupNames.normalize("   "))
        assertEquals("", GroupNames.normalize("!!! ... ---"))
        assertEquals("", GroupNames.normalize("🌊"))
    }

    @Test
    fun twoSpellingsOfTheSameNameAreEqualOnceNormalized() {
        val spellings = listOf("Collectif Éducation Lyon", "collectif education lyon", "COLLECTIF  ÉDUCATION, LYON !", "Collectif-Éducation_Lyon")
        assertEquals(1, spellings.map(GroupNames::normalize).toSet().size, spellings.map(GroupNames::normalize).toString())
        assertEquals("collectif education lyon", GroupNames.normalize(spellings[0]))
    }
}
