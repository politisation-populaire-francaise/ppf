package org.politisationpopulairefrancaise.core.voir

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.revendications.RevendicationViewStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationViews
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStore
import org.politisationpopulairefrancaise.core.voir.parlement.StoredIndex
import org.politisationpopulairefrancaise.core.voir.parlement.StoredParliamentPackage
import org.politisationpopulairefrancaise.core.voir.parlement.VoteAlertStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The part of "Voir" in the quick erase (S-44): everything it keeps in the local database goes. */
class VoirDeviceDataTest {

    @Test
    fun theQuickEraseLeavesNothingOfVoirAndCanBeRepeated() = runTest {
        val database = JvmDatabase.inMemory()
        val follows = ContentFollowStore(database)
        val views = RevendicationViewStore(database)
        val parliament = ParliamentStore(database)
        follows.follow(ContentAddress.parse("30023:${"a".repeat(64)}:dominique-fictif"))
        views.save(listOf(RevendicationViews.ALL, RevendicationViews.FOLLOWED))
        parliament.saveIndex(StoredIndex("{}", "2026-11-08", 1))
        parliament.savePackage(StoredParliamentPackage("scrutins-an-2026-11", "b".repeat(64), "{}", null, 2))
        val alerts = VoteAlertStore(database)
        alerts.addWord("retraites", 3)
        alerts.setEuropeans(setOf("pe:990001"))
        alerts.markSeen(listOf("an:17:1"), 3)

        val part = VoirDeviceData(database)
        assertEquals("voir-data", part.erasableId)
        part.eraseFromDevice()

        assertTrue(follows.all().isEmpty())
        assertEquals(0, views.count())
        assertNull(parliament.index())
        assertEquals(0L, parliament.countPackages())
        assertTrue(alerts.words().isEmpty() && alerts.europeans().isEmpty() && alerts.seen().isEmpty(), "the followed words never survive the erase")
        // Called again after a failure elsewhere: still nothing, no error.
        part.eraseFromDevice()
        assertTrue(follows.all().isEmpty())
    }
}
