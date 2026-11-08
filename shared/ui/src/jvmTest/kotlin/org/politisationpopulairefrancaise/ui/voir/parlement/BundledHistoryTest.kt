package org.politisationpopulairefrancaise.ui.voir.parlement

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.DataOrigin
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataKeys
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentHistory
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The example history packages bundled with the application (comparator of « Décider », D-066)
 * are listed by the signed example index, match their sha256 and read as the package their name
 * announces, with the party at the date of the ballot and no name of any person.
 */
class BundledHistoryTest {

    @Test
    fun theBundledExampleHistoryLoadsAndVerifies() = runBlocking {
        val repository = ParliamentRepository(null, InMemoryParliamentStorage(), ::bundledParliamentFile, OpenDataKeys.bundledExamples())
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(DataOrigin.BUNDLED, snapshot.origin)
        val names = snapshot.history.map { it.name }
        assertTrue(names.containsAll(listOf("historique-an-14", "historique-an-17", "historique-senat-2006", "historique-pe-9", "historique-pe-10")), names.toString())
        assertTrue(snapshot.historyToDownload.isEmpty())
        for (name in names) {
            val pkg = assertNotNull(repository.history(name), name)
            val (chamber, period) = ParliamentHistory.parse(name)!!
            assertEquals(chamber, pkg.chamber)
            assertEquals(period, pkg.period)
            assertTrue(pkg.example)
            val known = pkg.parties.map { it.id }.toSet()
            for (ballot in pkg.ballots) assertTrue(ballot.parties.all { it.party in known }, ballot.id)
        }
        val an14 = repository.history("historique-an-14")!!
        assertTrue(an14.ballots.isNotEmpty())
        // The Sénat publishes no party: always the group.
        for (name in snapshot.history(Chamber.SENAT).map { it.name }) {
            assertTrue(repository.history(name)!!.parties.all { it.basis == PartyBasis.GROUP && it.basisLabel == ParliamentHistory.GROUP_BASIS_LABEL })
        }
    }
}
