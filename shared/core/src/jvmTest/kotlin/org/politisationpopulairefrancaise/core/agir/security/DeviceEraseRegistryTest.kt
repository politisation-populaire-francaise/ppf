package org.politisationpopulairefrancaise.core.agir.security

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaStore
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalRecord
import org.politisationpopulairefrancaise.core.agir.agenda.ProposalReview
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceEraseRegistryTest {

    @Test
    fun everyPartIsErasedAndAFailingPartNeverStopsTheOthers() = runBlocking {
        val registry = DeviceEraseRegistry()
        val done = mutableListOf<String>()
        registry.register(deviceErasable("core-identity") { done += "core-identity" })
        registry.register(deviceErasable("voir-data") { error("[EXEMPLE] refused") })
        val remove = registry.register(deviceErasable("agir-data") { done += "agir-data" })
        // The same id again replaces the previous part.
        registry.register(deviceErasable("core-identity") { done += "core-identity-2" })
        assertEquals(listOf("voir-data", "agir-data", "core-identity"), registry.ids)

        val report = registry.eraseAll()
        assertEquals(listOf("agir-data", "core-identity-2"), done)
        assertEquals(listOf("voir-data"), report.failed)
        assertFalse(report.complete)

        remove()
        assertEquals(listOf("voir-data", "core-identity"), registry.ids)
    }

    @Test
    fun theDataOfAgirAreErasedFromTheLocalDatabase() = runBlocking {
        val database = JvmDatabase.inMemory()
        val agenda = AgendaStore(database)
        agenda.respond("31923:cle:manif-exemple", AgendaResponse.entries.first(), 1_900_000_000)
        agenda.recordProposal("evenement-exemple", ProposalRecord(ProposalReview.entries.first(), null))
        val calls = CallResponseStore(database)
        calls.respond("31924:cle:appel-exemple")

        AgirDeviceData(database).eraseFromDevice()

        assertEquals(0, agenda.countResponses())
        assertEquals(0, agenda.countProposals())
        assertEquals(0, calls.count())
        // Idempotent: a retry after a failure of another part changes nothing.
        AgirDeviceData(database).eraseFromDevice()
    }

    @Test
    fun theSettingsFileIsReplacedInOneMoveAndForgotten() {
        val directory = Files.createTempDirectory("ppf-security")
        val storage = FileDeviceSecurityStorage(directory.resolve(FileDeviceSecurityStorage.FILE_NAME))
        val lock = AppLock(storage, iterations = 10)
        lock.enable("471905", "471905")
        assertTrue(AppLock(storage, iterations = 10).isLocked)
        lock.forget()
        assertFalse(Files.exists(directory.resolve(FileDeviceSecurityStorage.FILE_NAME)))
    }
}
