package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFixtures.NOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParliamentRepositoryTest {

    private val keys = ParliamentFixtures.Keys()
    private val packages = ParliamentFixtures.packages()

    private suspend fun server(index: ParliamentIndex = ParliamentFixtures.index(packages), createdAt: Long = NOW) =
        ParliamentFixtures.Server(keys.sign(index, createdAt), packages)

    private fun repository(server: ParliamentFixtures.Server?, storage: ParliamentStorage = InMemoryParliamentStorage(), now: Long = NOW, bundled: suspend (String) -> ByteArray? = { null }) =
        ParliamentRepository(server, storage, bundled, keys.trust, clock = { now })

    @Test
    fun theIndexComesFromTheServerAndNothingIsDownloadedWithoutAGesture() = runTest {
        val server = server()
        val repository = repository(server)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(DataOrigin.SERVER, snapshot.origin)
        assertTrue(snapshot.example)
        assertTrue(snapshot.held.isEmpty())
        assertTrue(snapshot.catalog.isEmpty)
        assertEquals(listOf<String?>(null), server.requests)
        assertEquals(packages.getValue("membres").size.toLong(), snapshot.membersRef?.bytes)

        assertEquals(PackageOutcome.Done, repository.download("membres"))
        assertEquals(PackageOutcome.Done, repository.download("scrutins-an-2026-10"))
        val after = repository.snapshot!!
        assertEquals(setOf("membres", "scrutins-an-2026-10"), after.held)
        assertEquals(5, after.catalog.members().size)
        assertEquals(2, after.catalog.ballots().size)
        assertEquals(listOf(null, "membres", "scrutins-an-2026-10"), server.requests)
        assertEquals(PackageOutcome.Unknown("scrutins-an-2030-01"), repository.download("scrutins-an-2030-01"))
    }

    @Test
    fun aBadSignatureIsRefusedAndTheCopyOfTheDeviceStays() = runTest {
        val storage = InMemoryParliamentStorage()
        val server = server()
        repository(server, storage).open()
        val good = server.indexEvent!!
        server.indexEvent = good.copy(content = good.content.replace("2026-11-07", "2026-11-09"), createdAt = NOW + 10)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository(server, storage).open()).snapshot
        assertEquals(IndexProblem.NOT_AUTHENTIC, snapshot.refused)
        assertEquals(DataOrigin.DEVICE, snapshot.origin)
        assertEquals("2026-11-07", snapshot.index.importedOn)
    }

    @Test
    fun anIndexSignedByAnUntrustedKeyIsRefused() = runTest {
        val stranger = ParliamentFixtures.Keys()
        val server = ParliamentFixtures.Server(stranger.sign(ParliamentFixtures.index(packages)), packages)
        val opening = assertIs<ParliamentOpening.Nothing>(repository(server).open())
        assertEquals(IndexProblem.UNTRUSTED_KEY, opening.refused)
    }

    @Test
    fun anOlderIndexNeverReplacesTheOneOnTheDevice() = runTest {
        val storage = InMemoryParliamentStorage()
        val server = server(ParliamentFixtures.index(packages, importedOn = "2026-11-07"), createdAt = NOW)
        repository(server, storage).open()
        server.indexEvent = keys.sign(ParliamentFixtures.index(packages, importedOn = "2026-11-01"), createdAt = NOW - 86_400 * 6)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository(server, storage).open()).snapshot
        assertEquals(IndexProblem.OLDER_THAN_CURRENT, snapshot.refused)
        assertEquals("2026-11-07", snapshot.index.importedOn)
        // A newer one is accepted.
        server.indexEvent = keys.sign(ParliamentFixtures.index(packages, importedOn = "2026-11-08"), createdAt = NOW + 86_400)
        val newer = assertIs<ParliamentOpening.Ready>(repository(server, storage).open()).snapshot
        assertNull(newer.refused)
        assertEquals("2026-11-08", newer.index.importedOn)
    }

    @Test
    fun aPackageWhoseFingerprintDoesNotMatchIsNotKept() = runTest {
        val storage = InMemoryParliamentStorage()
        val server = server()
        val repository = repository(server, storage)
        repository.open()
        server.packages = packages + ("membres" to ParliamentFixtures.json(ParliamentFixtures.members.copy(members = ParliamentFixtures.members.members.drop(1))))
        assertEquals(PackageOutcome.Refused("membres"), repository.download("membres"))
        assertNull(storage.storedPackage("membres"))
        // A package that matches but announces another month is refused too.
        val wrongMonth = ParliamentFixtures.json(ParliamentFixtures.octobre.copy(month = "2026-09"))
        server.indexEvent = keys.sign(ParliamentFixtures.index(packages + ("scrutins-an-2026-10" to wrongMonth)), createdAt = NOW + 1)
        server.packages = packages + ("scrutins-an-2026-10" to wrongMonth)
        repository.open()
        assertEquals(PackageOutcome.Refused("scrutins-an-2026-10"), repository.download("scrutins-an-2026-10"))
    }

    @Test
    fun dataOlderThanTwoWeeksAreStale() = runTest {
        val repository = repository(server(), now = NOW + 86_400L * 15)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertTrue(snapshot.isStale(repository.today))
        val fresh = repository(server())
        assertFalse(assertIs<ParliamentOpening.Ready>(fresh.open()).snapshot.isStale(fresh.today))
    }

    @Test
    fun withoutServerTheCopyOfTheDeviceIsShown() = runTest {
        val storage = InMemoryParliamentStorage()
        val server = server()
        val first = repository(server, storage)
        first.open()
        first.download("membres")
        server.online = false
        val snapshot = assertIs<ParliamentOpening.Ready>(repository(server, storage).open()).snapshot
        assertEquals(DataOrigin.DEVICE, snapshot.origin)
        assertEquals(5, snapshot.catalog.members().size)
        // An altered copy on the device is ignored.
        val stored = storage.storedPackage("membres")!!
        storage.savePackage(stored.copy(json = stored.json.replace("Zoé", "Zoe")))
        assertTrue(assertIs<ParliamentOpening.Ready>(repository(server, storage).open()).snapshot.catalog.isEmpty)
    }

    @Test
    fun aNewIndexMarksOlderPackagesAndDropsUnlistedOnes() = runTest {
        val storage = InMemoryParliamentStorage()
        val server = server()
        val repository = repository(server, storage)
        repository.open()
        repository.download("membres")
        repository.download("scrutins-an-2026-10")
        val newMembers = ParliamentFixtures.json(ParliamentFixtures.members.copy(members = ParliamentFixtures.members.members.dropLast(1)))
        val next = mapOf("membres" to newMembers, "scrutins-an-2026-11" to packages.getValue("scrutins-an-2026-11"))
        server.indexEvent = keys.sign(ParliamentFixtures.index(next, importedOn = "2026-11-08"), createdAt = NOW + 86_400)
        server.packages = next
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(setOf("membres"), snapshot.outdated)
        assertEquals(listOf("membres"), storage.packageNames())
        assertEquals(PackageOutcome.Done, repository.download("membres"))
        assertTrue(repository.snapshot!!.outdated.isEmpty())
        assertEquals(4, repository.snapshot!!.catalog.members().size)
    }

    @Test
    fun withoutServerTheBundledExamplesAreReadAndCheckedButNeverStored() = runTest {
        val storage = InMemoryParliamentStorage()
        val index = keys.sign(ParliamentFixtures.index(packages)).toJson().encodeToByteArray()
        // One bundled package is altered: it is left out.
        val files = packages + ("index" to index) + ("scrutins-an-2026-11" to "{}".encodeToByteArray())
        val repository = repository(null, storage, bundled = { files[it] })
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(DataOrigin.BUNDLED, snapshot.origin)
        assertEquals(setOf("membres", "scrutins-an-2026-10"), snapshot.held)
        assertEquals(PackageOutcome.Done, repository.download("membres"))
        assertNull(storage.index())
        assertTrue(storage.packageNames().isEmpty())
        // Nothing bundled, no server: nothing to show.
        assertIs<ParliamentOpening.Nothing>(repository(null).open())
    }

    @Test
    fun anUnreachableServerFallsBackToTheBundledExamples() = runTest {
        val server = server().also { it.online = false }
        val files = packages + ("index" to keys.sign(ParliamentFixtures.index(packages)).toJson().encodeToByteArray())
        val snapshot = assertIs<ParliamentOpening.Ready>(repository(server, bundled = { files[it] }).open()).snapshot
        assertEquals(DataOrigin.BUNDLED, snapshot.origin)
        assertEquals(3, snapshot.held.size)
    }

    private fun history(chamber: Chamber, period: Int, title: String = "[EXEMPLE] titre") = HistoryPackage(
        example = true,
        chamber = chamber,
        period = period,
        coverage = HistoryCoverage("2014-03-12", "2014-03-12"),
        parties = listOf(PartyRef("an:PO1", "[EXEMPLE] Groupe", "G", PartyBasis.GROUP, ParliamentHistory.GROUP_BASIS_LABEL)),
        ballots = listOf(
            HistoryBallot(
                id = "an:$period:1", date = "2014-03-12", title = title,
                counts = Counts(inFavour = 1, against = 0, abstention = 0),
                parties = listOf(PartyDistribution("an:PO1", PartyBasis.GROUP, inFavour = 1, against = 0, abstention = 0, notVoting = 0, members = 2)),
            ),
        ),
    )

    private fun json(pkg: HistoryPackage): ByteArray = ParliamentFormat.json.encodeToString(HistoryPackage.serializer(), pkg).encodeToByteArray()

    @Test
    fun theHistoryIsDownloadedWholeOnOneGestureAndReadAgainstTheIndex() = runTest {
        val all = packages + mapOf(
            "historique-an-14" to json(history(Chamber.AN, 14)),
            "historique-an-15" to json(history(Chamber.AN, 15)),
        )
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(all)), all)
        val storage = InMemoryParliamentStorage()
        val repository = repository(server, storage)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(listOf("historique-an-14", "historique-an-15"), snapshot.history.map { it.name })
        assertEquals(2, snapshot.history(Chamber.AN).size)
        assertTrue(snapshot.history(Chamber.SENAT).isEmpty())
        assertEquals(all.getValue("historique-an-14").size.toLong() + all.getValue("historique-an-15").size, snapshot.historyBytesToDownload)
        assertNull(repository.history("historique-an-14"))

        val outcomes = repository.downloadHistory()
        assertEquals(mapOf("historique-an-14" to PackageOutcome.Done, "historique-an-15" to PackageOutcome.Done), outcomes)
        // Every party at once: the requests name the packages, never a party.
        assertEquals(listOf(null, "historique-an-14", "historique-an-15"), server.requests)
        val after = repository.snapshot!!
        assertTrue(after.historyToDownload.isEmpty())
        assertTrue(after.catalog.isEmpty)
        val read = repository.history("historique-an-15")!!
        assertEquals(15, read.period)
        assertEquals("an:15:1", read.ballots.single().id)
        assertEquals(PartyBasis.GROUP, read.ballots.single().parties.single().basis)
        assertNull(repository.history("membres"))

        // An altered copy on the device is never read.
        val stored = storage.storedPackage("historique-an-14")!!
        storage.savePackage(stored.copy(json = stored.json.replace("[EXEMPLE] titre", "[EXEMPLE] autre")))
        assertNull(repository.history("historique-an-14"))
    }

    @Test
    fun aHistoryPackageOfAnotherPeriodIsRefused() = runTest {
        val all = packages + mapOf("historique-an-14" to json(history(Chamber.AN, 15)))
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(all)), all)
        val repository = repository(server)
        repository.open()
        assertEquals(PackageOutcome.Refused("historique-an-14"), repository.download("historique-an-14"))
        assertNull(repository.history("historique-an-14"))
    }
}
