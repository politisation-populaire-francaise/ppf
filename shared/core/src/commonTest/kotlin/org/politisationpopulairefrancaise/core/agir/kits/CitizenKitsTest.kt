package org.politisationpopulairefrancaise.core.agir.kits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** « Kits d'action citoyenne » (D-232): every kit once, each goal served, the filters keep the order. */
class CitizenKitsTest {

    @Test
    fun everyKitIsListedOnceAndEveryGoalHasKits() {
        val ids = CitizenKits.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        KitGoal.entries.forEach { goal -> assertTrue(CitizenKits.filter(goal, offlineOnly = false).isNotEmpty(), goal.id) }
        // The kits the owner named: a party, a poster with a QR code, the protester's kit.
        assertTrue("creer-ou-rejoindre-un-parti" in ids && CitizenKits.POSTER in ids && "mode-manifestation" in ids)
    }

    @Test
    fun theFiltersCombineAGoalAndUseWithoutNetwork() {
        val strike = CitizenKits.filter(KitGoal.GREVE, offlineOnly = false).map { it.id }
        assertEquals(listOf("simulateur-greve", "solidarite", "faire-greve-vos-droits", "collecter-de-l-argent-pour-une-cause"), strike)
        val organise = CitizenKits.filter(KitGoal.ORGANISER, offlineOnly = false).map { it.id }
        assertTrue("collectifs" in organise && "se-reunir" in organise)
        val offline = CitizenKits.filter(KitGoal.ORGANISER, offlineOnly = true).map { it.id }
        assertTrue("collectifs" !in offline && "se-reunir" !in offline && "creer-une-association" in offline)
        assertEquals(CitizenKits.all, CitizenKits.filter(null, offlineOnly = false))
        // The guides are bundled: all of them work without network.
        assertTrue(CitizenKits.all.filter { it.target is KitTarget.Guide }.all { it.offline })
    }
}
