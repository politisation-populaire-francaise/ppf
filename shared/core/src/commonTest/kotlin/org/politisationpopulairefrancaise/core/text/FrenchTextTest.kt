package org.politisationpopulairefrancaise.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FrenchTextTest {

    @Test
    fun foldRemovesAccentsCaseAndPunctuation() {
        assertEquals("oeuvres d ete lyon 3e", FrenchText.fold("Œuvres  d'Été - Lyon 3e"))
        assertEquals("hopitaux publics", FrenchText.fold("  Hôpitaux, publics !  "))
        assertEquals("", FrenchText.fold("… !? —"))
        assertEquals("ca", FrenchText.fold("ÇA"))
    }

    @Test
    fun wordsAreFoldedAndInOrder() {
        assertEquals(listOf("la", "cantine", "a", "1", "euro"), FrenchText.words("La cantine à 1 euro"))
    }

    @Test
    fun stemJoinsSingularPluralFeminineAndCommonEndings() {
        assertEquals(FrenchText.stem("cantine"), FrenchText.stem("cantines"))
        assertEquals(FrenchText.stem("gratuit"), FrenchText.stem("gratuite"))
        assertEquals(FrenchText.stem("hopital"), FrenchText.stem("hopitaux"))
        assertEquals(FrenchText.stem("bureau"), FrenchText.stem("bureaux"))
        assertEquals(FrenchText.stem("education"), FrenchText.stem("educatif"))
        assertEquals(FrenchText.stem("transport"), FrenchText.stem("transports"))
        assertNotEquals(FrenchText.stem("transparence"), FrenchText.stem("transports"))
        assertEquals("bus", FrenchText.stem("bus"))
        assertEquals("2027", FrenchText.stem("2027"))
    }

    @Test
    fun keywordsDropSmallWordsAndTheVocabularyOfDemands() {
        assertEquals(
            listOf("tarif", "cantin", "euro", "famill"),
            FrenchText.keywords("Nous demandons un tarif de cantine à un euro pour les familles"),
        )
        assertEquals(emptyList(), FrenchText.keywords("Nous exigeons que la revendication soit là"))
    }

    @Test
    fun searchMatchesEveryWordAsAPrefix() {
        val text = "Des transports en commun toute la nuit dans la métropole de Lyon"
        assertTrue(FrenchText.matchesAllWords(text, "metro lyon"))
        assertTrue(FrenchText.matchesAllWords(text, "Métropole"))
        assertTrue(FrenchText.matchesAllWords(text, "  "))
        assertFalse(FrenchText.matchesAllWords(text, "lyon marseille"))
    }
}
