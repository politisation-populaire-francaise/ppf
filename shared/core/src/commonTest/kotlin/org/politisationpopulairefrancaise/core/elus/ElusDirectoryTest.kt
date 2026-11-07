package org.politisationpopulairefrancaise.core.elus

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Choosing a commune: index, size, download checked against the fingerprint, communes of a postal code, forgetting. */
class ElusDirectoryTest {

    private fun directory(server: FakeElusServer? = ElusFixtures.server(), storage: ElusStorage = InMemoryElusStorage()) =
        ElusDirectory(server, storage, clock = { 1_000 })

    @Test
    fun aPostalCodeLeadsToTheCommunesOfItsDepartmentWithoutSendingIt() = runTest {
        val server = ElusFixtures.server()
        val elus = directory(server)
        assertNull(elus.mine())
        assertEquals(emptyList(), server.requests, "nothing is downloaded before a gesture")

        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        val plan = assertNotNull(elus.plan(index, "69250"))
        assertEquals(listOf("69"), plan.departments.map { it.code })
        // The department and the national package, with their total size announced before downloading.
        assertEquals(listOf("69", "FR"), plan.missing.map { it.code })
        assertEquals(plan.missing.sumOf { it.bytes }, plan.bytes)
        assertEquals(DownloadOutcome.Done, elus.download(plan))
        // The server was asked for the index and two packages: never for a postal code or a commune.
        assertEquals(listOf(null, "69", "FR"), server.requests)

        val choices = elus.communesFor(plan)
        assertEquals(listOf("Albigny-sur-Saône", "Neuville-sur-Saône"), choices.map { it.commune.name })
        elus.choose(choices[1])
        val mine = assertNotNull(elus.mine())
        assertEquals(ChosenCommune("69143", "69"), mine.chosen)
        assertEquals(listOf("Charlie Neuville"), mine.officials.level(DecisionLevel.COMMUNE).groups.single().officials.map { it.displayName })
        assertEquals(listOf("Ana Europe", "Teo Europe"), mine.officials.level(DecisionLevel.UNION_EUROPEENNE).groups.single().officials.map { it.displayName })
        assertTrue(mine.example)
        assertEquals("Licence Ouverte 2.0", mine.source?.licence)
        assertEquals(1_000, mine.downloadedAt)

        // Everything is on the device now: a new plan for the same postal code needs no download.
        assertEquals(emptyList(), assertNotNull(elus.plan(index, "69250")).missing)
        assertNull(elus.update(index))
    }

    @Test
    fun aPackageThatDoesNotMatchItsFingerprintIsRefused() = runTest {
        val server = ElusFixtures.server()
        val storage = InMemoryElusStorage()
        val elus = directory(server, storage)
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        // The server (or someone on the way) serves another content than the one announced.
        server.packages["69"] = server.packages.getValue("69").replace("Noa Maire", "Noa Autre")
        val plan = assertNotNull(elus.plan(index, "69001"))
        assertEquals(DownloadOutcome.Refused("69"), elus.download(plan))
        assertNull(storage.storedPackage("69"))
        assertEquals(emptyList(), elus.communesFor(plan))
    }

    @Test
    fun aPackageThatClaimsAnotherDepartmentIsRefused() = runTest {
        val server = ElusFixtures.server()
        // The index announces the package of the Finistère under the code of the Rhône.
        val swapped = mapOf("69" to server.packages.getValue("29"), "29" to server.packages.getValue("29"), "FR" to server.packages.getValue("FR"))
        server.indexText = ElusFixtures.index(mapOf("69" to ("Rhône" to listOf("69001")), "FR" to ("France" to emptyList())), swapped)
        server.packages["69"] = swapped.getValue("69")
        val elus = directory(server)
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        assertEquals(DownloadOutcome.Refused("69"), elus.download(assertNotNull(elus.plan(index, "69001"))))
    }

    @Test
    fun theNationalPackageIsOptional() = runTest {
        val server = ElusFixtures.server()
        server.packages.remove("FR")
        val elus = directory(server)
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        val plan = assertNotNull(elus.plan(index, "29990"))
        assertEquals(DownloadOutcome.Done, elus.download(plan))
        elus.choose(elus.communesFor(plan).single())
        val mine = assertNotNull(elus.mine())
        assertEquals("Île-de-Sein", mine.officials.commune.name)
        assertTrue(mine.officials.level(DecisionLevel.UNION_EUROPEENNE).groups.isEmpty())
    }

    @Test
    fun withoutServerOrAnswerTheIndexIsUnavailable() = runTest {
        assertEquals(IndexOutcome.NoServer, directory(server = null).index())
        val server = ElusFixtures.server()
        server.online = false
        assertEquals(IndexOutcome.Unreachable, directory(server).index())
        server.indexText = "{\"format\":9}"
        server.online = true
        assertEquals(IndexOutcome.Unreadable, directory(server).index())
    }

    @Test
    fun theIndexOfTheDeviceIsUsedWhenTheServerDoesNotAnswer() = runTest {
        val server = ElusFixtures.server()
        val storage = InMemoryElusStorage()
        assertIs<IndexOutcome.Ready>(directory(server, storage).index())
        // The second time, the version of the device is announced and the server answers "not modified".
        val again = assertIs<IndexOutcome.Ready>(directory(server, storage).index())
        assertTrue(again.fresh)
        assertEquals(ElusDigest.sha256Hex(server.indexText.encodeToByteArray()), server.etags.last())
        server.online = false
        val offline = assertIs<IndexOutcome.Ready>(directory(server, storage).index())
        assertEquals(false, offline.fresh)
        assertEquals(listOf("69"), offline.index.departmentsFor("69400").map { it.code })
    }

    @Test
    fun aPostalCodeOutsideTheIndexHasNoPlan() = runTest {
        val elus = directory()
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        assertNull(elus.plan(index, "75001"))
        assertNull(elus.plan(index, "abc"))
    }

    @Test
    fun choosingKeepsOnlyThePackagesOfTheCommuneAndForgettingErasesEverything() = runTest {
        val server = ElusFixtures.server()
        val storage = InMemoryElusStorage()
        val elus = directory(server, storage)
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        val finistere = assertNotNull(elus.plan(index, "29990"))
        elus.download(finistere)
        val rhone = assertNotNull(elus.plan(index, "69400"))
        elus.download(rhone)
        assertEquals(listOf("29", "69", "FR", "index"), storage.packageCodes())
        elus.choose(elus.communesFor(rhone).single())
        assertEquals(listOf("69", "FR", "index"), storage.packageCodes())

        // A newer version of the department is announced: the update holds only that package.
        server.packages["69"] = server.packages.getValue("69").replace("Robin Villefranche", "Robin Nouveau")
        server.indexText = ElusFixtures.index(
            mapOf("69" to ("Rhône" to listOf("69400")), "FR" to ("France" to emptyList())),
            server.packages,
        )
        val newer = assertIs<IndexOutcome.Ready>(elus.index()).index
        val update = assertNotNull(elus.update(newer))
        assertEquals(listOf("69"), update.missing.map { it.code })
        assertEquals(DownloadOutcome.Done, elus.download(update))
        assertEquals("Robin Nouveau", assertNotNull(elus.mine()).officials.level(DecisionLevel.COMMUNE).groups.single().officials.single().displayName)

        elus.forget()
        assertNull(elus.mine())
        assertNull(storage.commune())
        assertEquals(emptyList(), storage.packageCodes())
    }

    @Test
    fun aDamagedCopyOnTheDeviceIsNotShown() = runTest {
        val storage = InMemoryElusStorage()
        val elus = directory(storage = storage)
        val index = assertIs<IndexOutcome.Ready>(elus.index()).index
        val plan = assertNotNull(elus.plan(index, "69001"))
        elus.download(plan)
        elus.choose(elus.communesFor(plan).single())
        val stored = assertNotNull(storage.storedPackage("69"))
        storage.savePackage(stored.copy(json = stored.json.replace("Noa Maire", "Noa Abime")))
        // A fresh directory (the application restarted) reads the copy of the device and refuses it.
        assertNull(directory(storage = storage).mine())
    }
}
