package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The status the team gives a demand by a label of its own (NIP-32; review R5, Q3). */
class StatusLabelsTest {
    private val team = "e".repeat(64)
    private val citizen = "c".repeat(64)
    private val demand = ContentAddress.parse("30023:$citizen:piscine")

    private fun event(tags: List<List<String>>, kind: Int = StatusLabels.KIND, by: String = team, at: Long = 1_794_000_000) =
        SignedEvent("0".repeat(64), by, at, kind, tags, "", "0".repeat(128))

    @Test
    fun aLabelNamesOneDemandAndOneStatus() {
        val label = StatusLabels.parse(event(StatusLabels.tags(demand, RevendicationStatus.EN_DEBAT)))!!
        assertEquals(demand, label.demand)
        assertEquals(RevendicationStatus.EN_DEBAT, label.status)
        assertNull(StatusLabels.parse(event(StatusLabels.tags(demand, RevendicationStatus.EN_DEBAT), kind = 1)))
        assertNull(StatusLabels.parse(event(listOf(listOf("L", "autre"), listOf("l", "en-debat", "autre"), listOf("a", demand.toString())))))
        assertNull(StatusLabels.parse(event(listOf(listOf("L", "ppf-status"), listOf("l", "inconnu", "ppf-status"), listOf("a", demand.toString())))))
        assertNull(StatusLabels.parse(event(StatusLabels.tags(demand, RevendicationStatus.EN_DEBAT) + listOf(listOf("a", "30023:$team:autre")))))
    }

    @Test
    fun theLatestLabelOfTheTeamWinsAndACitizenOnlyWritesNouvelle() {
        val isEditor = { key: String -> key == team }
        val early = StatusLabels.Label(demand, RevendicationStatus.EN_DEBAT, 100, team)
        val late = StatusLabels.Label(demand, RevendicationStatus.RETENUE, 200, team)
        val forged = StatusLabels.Label(demand, RevendicationStatus.CAMPAGNE, 300, citizen)
        assertEquals(RevendicationStatus.RETENUE, StatusLabels.effective(citizen, RevendicationStatus.NOUVELLE, listOf(late, early, forged), isEditor))
        assertEquals(RevendicationStatus.NOUVELLE, StatusLabels.effective(citizen, RevendicationStatus.NOUVELLE, listOf(forged), isEditor))
        assertNull(StatusLabels.effective(citizen, RevendicationStatus.EN_DEBAT, emptyList(), isEditor))
        assertEquals(RevendicationStatus.EN_DEBAT, StatusLabels.effective(team, RevendicationStatus.EN_DEBAT, emptyList(), isEditor))
    }
}
