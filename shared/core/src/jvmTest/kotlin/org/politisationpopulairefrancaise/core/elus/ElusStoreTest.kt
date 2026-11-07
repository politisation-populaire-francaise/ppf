package org.politisationpopulairefrancaise.core.elus

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The commune and the packages in the local database: one row for the commune, everything erased in one gesture. */
class ElusStoreTest {

    @Test
    fun theCommuneIsASingleRowAndForgettingErasesEverything() {
        val store = ElusStore(JvmDatabase.inMemory())
        assertNull(store.commune())
        store.saveCommune(ChosenCommune("69123", "69"))
        store.saveCommune(ChosenCommune("69143", "69"))
        assertEquals(ChosenCommune("69143", "69"), store.commune())

        store.savePackage(StoredPackage("69", "a".repeat(64), "{}", "2026-10-01", 10))
        store.savePackage(StoredPackage("69", "b".repeat(64), "{\"v\":2}", null, 11))
        store.savePackage(StoredPackage(ElusStorage.INDEX_CODE, "c".repeat(64), "{}", null, 12))
        assertEquals(StoredPackage("69", "b".repeat(64), "{\"v\":2}", null, 11), store.storedPackage("69"))
        assertEquals(listOf("69", "index"), store.packageCodes())
        store.deletePackage("69")
        assertEquals(1L, store.countPackages())

        store.forget()
        assertNull(store.commune())
        assertEquals(0L, store.countPackages())
    }

    @Test
    fun theChosenCommuneSurvivesARestartOfTheApplication() = runTest {
        val database = JvmDatabase.inMemory()
        val server = ElusFixtures.server()
        val first = ElusDirectory(server, ElusStore(database))
        val index = assertIs<IndexOutcome.Ready>(first.index()).index
        val plan = assertNotNull(first.plan(index, "69001"))
        first.download(plan)
        first.choose(first.communesFor(plan).single())

        // A new directory on the same database, without network: the officials are found on the device.
        server.online = false
        val restarted = ElusDirectory(server, ElusStore(database))
        val mine = assertNotNull(restarted.mine())
        assertEquals("Lyon", mine.officials.commune.name)
        assertEquals("Noa Maire", mine.officials.level(DecisionLevel.COMMUNE).groups.single().officials.first().displayName)
        assertEquals(listOf<String?>(null, "69", "FR"), server.requests)
    }
}
