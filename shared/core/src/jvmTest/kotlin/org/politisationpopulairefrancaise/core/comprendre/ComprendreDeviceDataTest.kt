package org.politisationpopulairefrancaise.core.comprendre

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.elus.ChosenCommune
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.elus.StoredPackage
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.territoires.StoredTerritoires
import org.politisationpopulairefrancaise.core.territoires.TerritoiresReader
import org.politisationpopulairefrancaise.core.territoires.TerritoiresStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The quick erase (S-44) of what « Comprendre » keeps on the device. */
class ComprendreDeviceDataTest {
    private val database = JvmDatabase.inMemory()
    private val progress = LearningProgressStore(database)
    private val elus = ElusStore(database)
    private val territoires = TerritoiresStore(database)

    private fun fill() {
        progress.reachStep("30023:abc:comment-une-loi-est-votee", 2, now = 1L)
        progress.answer("30023:abc:comment-une-loi-est-votee", 0, choice = 1, correct = true, now = 1L)
        elus.saveCommune(ChosenCommune(communeCode = "75056", departmentCode = "75"))
        elus.savePackage(StoredPackage(code = "75", sha256 = "a".repeat(64), json = "{}", sourceUpdatedOn = null, downloadedAt = 1L))
        elus.savePackage(StoredPackage(code = "index", sha256 = "b".repeat(64), json = "{}", sourceUpdatedOn = null, downloadedAt = 1L))
        for (code in listOf(TerritoiresReader.NATIONAL_CODE, TerritoiresReader.COMMUNES_CODE, "75", "2A")) {
            territoires.save(StoredTerritoires(code = code, sha256 = "c".repeat(64), json = "{}", builtOn = null, downloadedAt = 1L))
        }
    }

    @Test
    fun everyPartOfComprendreIsRegisteredUnderItsOwnName() {
        val registry = DeviceEraseRegistry()
        ComprendreDeviceData.erasables(database).forEach { registry.register(it) }
        assertEquals(listOf("comprendre-progression", "comprendre-elus", "comprendre-territoires"), registry.ids)
    }

    @Test
    fun theQuickEraseForgetsWhatCouldRevealInterestsOrWhereThePersonLives() = runBlocking {
        fill()
        val registry = DeviceEraseRegistry()
        ComprendreDeviceData.erasables(database).forEach { registry.register(it) }
        val report = registry.eraseAll()
        assertTrue(report.complete, "every part erased: ${report.failed}")
        // The progress of the courses and their answers.
        assertEquals(0L, progress.count())
        assertTrue(progress.all().isEmpty())
        // The chosen commune and every package of the directory.
        assertNull(elus.commune())
        assertTrue(elus.packageCodes().isEmpty())
        // The department packages go; the national package and the index of the names of the communes stay.
        assertEquals(listOf(TerritoiresReader.COMMUNES_CODE, TerritoiresReader.NATIONAL_CODE).sorted(), territoires.codes().sorted())
    }

    @Test
    fun theEraseCanBeRunAgain() = runBlocking {
        fill()
        val parts = ComprendreDeviceData.erasables(database)
        parts.forEach { it.eraseFromDevice() }
        // A retry after a failure elsewhere: nothing left to erase, no error.
        parts.forEach { it.eraseFromDevice() }
        assertNull(elus.commune())
        assertEquals(2, territoires.codes().size)
    }
}
