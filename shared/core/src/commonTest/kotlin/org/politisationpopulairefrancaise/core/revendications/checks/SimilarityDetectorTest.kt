package org.politisationpopulairefrancaise.core.revendications.checks

import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.ALL
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.ATELIERS
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.CANTINE
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.LOGEMENTS
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.LYON
import org.politisationpopulairefrancaise.core.revendications.checks.CheckFixtures.MEDECIN
import org.politisationpopulairefrancaise.core.revendications.checks.SimilarityDetector.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cases the thresholds were tuned on: rephrasings that keep the words are duplicates,
 * neighbours on the same subject are similar, other subjects are not shown.
 */
class SimilarityDetectorTest {
    private val detector = SimilarityDetector(ALL)

    private fun best(title: String, territory: String = "France", summary: String = "") =
        detector.compare(title, summary, territory).firstOrNull()

    @Test
    fun aShorterWordingOfTheSameDemandIsADuplicate() {
        val match = best("Cantine à un euro pour les familles modestes")
        assertEquals(CANTINE, match?.demand)
        assertEquals(Level.DUPLICATE, match?.level)
    }

    @Test
    fun anotherSentenceWithTheSameWordsIsADuplicate() {
        assertEquals(Level.DUPLICATE, best("Des médecins de garde le soir et le week-end partout")?.level)
        assertEquals(MEDECIN, best("Des médecins de garde le soir et le week-end partout")?.demand)
        assertEquals(ATELIERS, best("Ateliers numériques gratuits dans les mairies")?.demand)
        assertEquals(Level.DUPLICATE, best("Ateliers numériques gratuits dans les mairies")?.level)
    }

    @Test
    fun aNeighbourOnTheSameSubjectIsOnlySimilar() {
        val match = best("Rendre gratuits les transports en commun de la métropole de Lyon", territory = "Métropole de Lyon")
        assertEquals(LYON, match?.demand)
        assertEquals(Level.SIMILAR, match?.level)
    }

    @Test
    fun anotherSubjectIsNotShown() {
        assertTrue(detector.compare("Interdire la publicité lumineuse la nuit en ville", "", "France").isEmpty())
        assertTrue(detector.compare("Augmenter le nombre de juges pour raccourcir les délais", "", "France").isEmpty())
    }

    @Test
    fun theSameLocalDemandElsewhereIsNotADuplicate() {
        val match = best("Des transports en commun toute la nuit le week-end à Marseille", territory = "Marseille")
        assertEquals(LYON, match?.demand)
        assertEquals(Level.SIMILAR, match?.level, "Lyon and Marseille are two different demands")
    }

    @Test
    fun theSameDemandWrittenForTheSamePlaceWithAnotherNameStaysADuplicate() {
        val match = best("Des transports en commun la nuit pendant le week-end à Lyon", territory = "Lyon")
        assertEquals(LYON, match?.demand)
        assertEquals(Level.DUPLICATE, match?.level)
    }

    @Test
    fun aNationalDemandCloseToALocalOneIsAtMostSimilar() {
        val match = best("Des logements étudiants à loyer modéré près des campus", territory = "France")
        assertEquals(LOGEMENTS, match?.demand)
        assertEquals(Level.SIMILAR, match?.level)
    }

    @Test
    fun itsOwnEarlierVersionIsIgnoredAndTheExampleMarkerDoesNotCount() {
        assertTrue(detector.compare(CANTINE.title, territory = "France", ownAddress = CANTINE.address).none { it.demand == CANTINE })
        val withMarker = SimilarityDetector(listOf(CANTINE.copy(title = "[EXEMPLE] " + CANTINE.title)))
        assertEquals(Level.DUPLICATE, withMarker.compare(CANTINE.title, territory = "France").single().level)
    }

    @Test
    fun anEmptyDraftOrAnEmptyWallFindsNothing() {
        assertTrue(detector.compare("", "", "France").isEmpty())
        assertTrue(detector.compare("Le la les de des", "", "France").isEmpty())
        assertTrue(SimilarityDetector(emptyList()).compare("Cantine à un euro", "", "France").isEmpty())
    }

    @Test
    fun rankKeepsEveryDemandSharingAWordClosestFirst() {
        val ranked = detector.rank("Des transports de nuit et des médecins de garde", "", "France")
        assertTrue(ranked.size >= 2)
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
