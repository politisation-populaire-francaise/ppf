package org.politisationpopulairefrancaise.core.agir.solidarity

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The searches of the solidarity rubric, computed on the device: funds by words, types, territory
 * and "près de chez moi" (local ones first, then national ones), closed funds only on demand;
 * missions by the user's skills, remote only, the nearest deadline first.
 */
class SolidarityQueryTest {

    private val fixtures = SolidarityFixtures()

    private suspend fun funds(): List<SolidarityFund> = listOf(
        fixtures.fund("greve-lille", name = "[EXEMPLE] Caisse des transports de Lille", territory = "Lille (59)", sector = "Transports", beneficiary = "Grévistes des bus"),
        fixtures.fund("juridique-nord", name = "[EXEMPLE] Solidarité juridique du Nord", type = SolidarityFormats.FUND_LEGAL, territory = "Nord (59)", beneficiary = "Personnes poursuivies"),
        fixtures.fund("livreurs", name = "[EXEMPLE] Caisse des livreurs", territory = "France", strike = "Grève des livreurs à vélo"),
        fixtures.fund("entraide-marseille", name = "[EXEMPLE] Entraide des familles", type = SolidarityFormats.FUND_OTHER, territory = "Marseille (13)"),
        fixtures.fund("cantines-rennes", name = "[EXEMPLE] Ancienne caisse des cantines", territory = "Rennes (35)", status = SolidarityFormats.FUND_CLOSED, donationLink = null),
    ).map(fixtures::readFund)

    @Test
    fun openFundsComeFirstByNameAndClosedOnesOnlyOnDemand() = runTest {
        val all = funds()
        assertEquals(listOf("livreurs", "greve-lille", "entraide-marseille", "juridique-nord"), FundQuery().apply(all).map { it.slug })
        val withClosed = FundQuery(includeClosed = true).apply(all).map { it.slug }
        assertEquals("cantines-rennes", withClosed.last(), "closed funds after the open ones, even when their name comes first")
        assertEquals(5, withClosed.size)
        assertTrue(FundQuery().isUnconstrained)
        assertEquals(1, FundQuery(includeClosed = true).constraintCount)
    }

    @Test
    fun typesTerritoryAndWordsNarrowTheFunds() = runTest {
        val all = funds()
        assertEquals(listOf("juridique-nord"), FundQuery(types = setOf(SolidarityFormats.FUND_LEGAL)).apply(all).map { it.slug })
        assertEquals(listOf("greve-lille"), FundQuery(territory = "lille (59)").apply(all).map { it.slug }, "the territory is compared folded")
        // Words are looked for in the beneficiaries, the sector and the strike too.
        assertEquals(listOf("juridique-nord"), FundQuery(text = "poursuivies").apply(all).map { it.slug })
        assertEquals(listOf("greve-lille"), FundQuery(text = "transports").apply(all).map { it.slug })
        assertEquals(listOf("livreurs"), FundQuery(text = "velo").apply(all).map { it.slug }, "accents ignored")
        val query = FundQuery(text = "caisse", types = setOf(SolidarityFormats.FUND_STRIKE), near = "59000")
        assertEquals(3, query.constraintCount)
    }

    @Test
    fun nearMeKeepsTheLocalFundsFirstThenTheNationalOnes() = runTest {
        val all = funds()
        val near = FundQuery(near = "59000").apply(all).map { it.slug }
        assertEquals(listOf("greve-lille", "juridique-nord", "livreurs"), near, "Lille and the Nord by the postal code, then France")
        assertEquals(listOf("entraide-marseille", "livreurs"), FundQuery(near = "Marseille").apply(all).map { it.slug })
    }

    private suspend fun missions(): List<VolunteerMission> = listOf(
        fixtures.mission("traduction", skills = listOf("traduction"), deadline = 1_797_000_000, publishedAt = 1_793_000_000),
        fixtures.mission("juristes", skills = listOf("juriste"), deadline = 1_796_000_000, publishedAt = 1_793_000_100),
        fixtures.mission("application", skills = listOf("developpement", "graphisme"), publishedAt = 1_793_000_200),
        fixtures.mission("accueil-lyon", skills = listOf("accueil"), territory = "Lyon (69)", remote = false, location = "Stand fictif", publishedAt = 1_793_000_300),
        fixtures.mission("moderation", skills = listOf("moderation"), status = SolidarityFormats.MISSION_FILLED, deadline = 1_795_000_000),
    ).map(fixtures::readMission)

    @Test
    fun openMissionsComeFirstNearestDeadlineFirstThenTheNewest() = runTest {
        val all = missions()
        assertEquals(listOf("juristes", "traduction", "accueil-lyon", "application"), MissionQuery().apply(all).map { it.slug })
        assertEquals("moderation", MissionQuery(includeClosed = true).apply(all).last().slug)
    }

    @Test
    fun theUsersSkillsKeepTheMissionsThatAskForAnyOfThem() = runTest {
        val all = missions()
        assertEquals(listOf("traduction"), MissionQuery(skills = setOf("traduction")).apply(all).map { it.slug })
        assertEquals(listOf("traduction", "application"), MissionQuery(skills = setOf("traduction", "graphisme")).apply(all).map { it.slug })
        assertEquals(emptyList(), MissionQuery(skills = setOf("moderation")).apply(all).map { it.slug }, "a filled mission only on demand")
        assertEquals(listOf("moderation"), MissionQuery(skills = setOf("moderation"), includeClosed = true).apply(all).map { it.slug })
        assertEquals(listOf("juristes", "traduction", "application"), MissionQuery(remoteOnly = true).apply(all).map { it.slug })
        assertEquals(listOf("accueil-lyon"), MissionQuery(text = "stand").apply(all).map { it.slug })
        assertEquals(4, MissionQuery(text = "a", skills = setOf("juriste", "accueil"), remoteOnly = true).constraintCount)
    }
}
