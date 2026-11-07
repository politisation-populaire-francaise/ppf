package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Cette semaine au Parlement » on the device: the package, its reading, and the loader next to the repository. */
class ParliamentWeekTest {

    private val keys = ParliamentFixtures.Keys()

    private fun item(id: String, chamber: Chamber, start: String, end: String? = null, dossier: String? = null) =
        WeekItem(id, chamber, start, end, SittingKind.PLENARY, title = "[EXEMPLE] $id", dossier = dossier, status = AgendaStatus.CONFIRMED)

    private val week = ParliamentWeekPackage(
        example = true,
        from = "2026-11-07",
        to = "2026-11-21",
        items = listOf(
            item("an:RU1:1", Chamber.AN, "2026-11-10T15:00", "2026-11-10T20:00", dossier = "an-dossier:DLR5L17N90001"),
            item("senat:x:202611101430", Chamber.SENAT, "2026-11-10T14:30", "2026-11-10T20:00"),
            item("an:RU2", Chamber.AN, "2026-11-06T09:00"),
            item("pe:MTG-PL-2026-11-18:1", Chamber.PE, "2026-11-18T00:00"),
            item("an:RU3:1", Chamber.AN, "2026-11-19T21:30", "2026-11-20T00:30"),
        ),
    )

    private fun bytes(pkg: ParliamentWeekPackage = week) = ParliamentWeekFormat.write(pkg).encodeToByteArray()

    @Test
    fun thePackageIsReadStrictlyOnItsFormatAndTolerantOnUnknownFields() {
        val text = ParliamentWeekFormat.write(week)
        assertEquals(week, ParliamentWeekFormat.read(text))
        assertEquals(week, ParliamentWeekFormat.read(text.replaceFirst("{", "{\"later\":true,")))
        assertNull(ParliamentWeekFormat.read(text.replaceFirst("{", "{\"format\":2,")))
        assertNull(ParliamentWeekFormat.read(text.replace("2026-11-10T15:00", "10 novembre")))
        assertNull(ParliamentWeekFormat.read("not json"))
        assertTrue(ParliamentFormat.isPackageName(ParliamentWeekFormat.PACKAGE))
    }

    @Test
    fun daysFromTodayInOrderAndTimesOfTheSameDay() {
        val view = ParliamentWeek(week)
        val today = CivilDate(2026, 11, 7)
        val days = view.days(today)
        assertEquals(listOf("2026-11-10", "2026-11-18", "2026-11-19"), days.map { it.date.toIso() })
        // The Sénat at 14:30 before the Assemblée at 15:00.
        assertEquals(listOf("senat:x:202611101430", "an:RU1:1"), days[0].items.map { it.id })
        assertEquals(listOf(Chamber.AN, Chamber.SENAT, Chamber.PE), view.chambers())
        assertEquals(listOf("2026-11-10"), view.days(today, Chamber.SENAT).map { it.date.toIso() })
        assertEquals(listOf("an:RU1:1"), view.ofDossier("an-dossier:DLR5L17N90001", today).map { it.id })
        assertTrue(view.ofDossier("an-dossier:DLR5L17N90001", CivilDate(2026, 11, 11)).isEmpty())
        val first = view.item("an:RU1:1")!!
        assertEquals("15:00", first.startTime)
        assertEquals("20:00", first.endTime)
        // An end on the next day is not shown as an hour of the same day.
        assertNull(view.item("an:RU3:1")!!.endTime)
        assertEquals(2, ParliamentWeek.weekday(CivilDate(2026, 11, 10)))
        assertEquals(6, ParliamentWeek.weekday(CivilDate(2026, 11, 7)))
        assertEquals(7, ParliamentWeek.weekday(CivilDate(2026, 11, 8)))
    }

    @Test
    fun theLoaderKeepsThePackageOnlyWhenItMatchesTheIndex() = runTest {
        val packages = ParliamentFixtures.packages() + (ParliamentWeekFormat.PACKAGE to bytes())
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(packages)), packages)
        val storage = InMemoryParliamentStorage()
        val repository = ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentFixtures.NOW })
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        // The repository itself ignores the package it does not know.
        assertFalse(snapshot.isHeld(ParliamentWeekFormat.PACKAGE))
        val loader = ParliamentWeekLoader(server, storage, clock = { ParliamentFixtures.NOW })
        assertEquals(bytes().size.toLong(), loader.ref(snapshot)?.bytes)
        assertNull(loader.held(snapshot))
        assertEquals(listOf<String?>(null), server.requests)

        assertEquals(PackageOutcome.Done, loader.download(snapshot))
        val held = assertNotNull(loader.held(snapshot))
        assertFalse(held.outdated)
        assertEquals(week, held.week.pkg)
        assertEquals("2026-11-07", storage.storedPackage(ParliamentWeekFormat.PACKAGE)?.sourceUpdatedOn)

        // Altered bytes on the server are refused; nothing changes on the device.
        server.packages = packages + (ParliamentWeekFormat.PACKAGE to bytes(week.copy(items = emptyList())))
        assertEquals(PackageOutcome.Refused(ParliamentWeekFormat.PACKAGE), loader.download(snapshot))
        assertEquals(week, loader.held(snapshot)?.week?.pkg)
    }

    @Test
    fun aNewerIndexMakesTheHeldPackageOutdatedAndAnIndexWithoutItSaysSo() = runTest {
        val storage = InMemoryParliamentStorage()
        val first = ParliamentFixtures.packages() + (ParliamentWeekFormat.PACKAGE to bytes())
        val server = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(first)), first)
        val repository = ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentFixtures.NOW })
        val loader = ParliamentWeekLoader(server, storage)
        loader.download(assertIs<ParliamentOpening.Ready>(repository.open()).snapshot)

        val newer = week.copy(from = "2026-11-08")
        val second = ParliamentFixtures.packages() + (ParliamentWeekFormat.PACKAGE to bytes(newer))
        server.indexEvent = keys.sign(ParliamentFixtures.index(second, importedOn = "2026-11-08"), ParliamentFixtures.NOW + 86_400)
        server.packages = second
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        val held = assertNotNull(loader.held(snapshot))
        assertTrue(held.outdated)
        assertEquals(PackageOutcome.Done, loader.download(snapshot))
        assertEquals("2026-11-08", loader.held(snapshot)?.week?.pkg?.from)

        val without = ParliamentFixtures.packages()
        val none = ParliamentFixtures.Server(keys.sign(ParliamentFixtures.index(without)), without)
        val other = ParliamentRepository(none, InMemoryParliamentStorage(), trust = keys.trust)
        val snapshotWithout = assertIs<ParliamentOpening.Ready>(other.open()).snapshot
        val loaderWithout = ParliamentWeekLoader(none, InMemoryParliamentStorage())
        assertNull(loaderWithout.ref(snapshotWithout))
        assertEquals(PackageOutcome.Unknown(ParliamentWeekFormat.PACKAGE), loaderWithout.download(snapshotWithout))
    }

    @Test
    fun theBundledExamplesAreCheckedAgainstTheirIndex() = runTest {
        val packages = ParliamentFixtures.packages() + (ParliamentWeekFormat.PACKAGE to bytes())
        val indexJson = keys.sign(ParliamentFixtures.index(packages)).toJson().encodeToByteArray()
        val bundled: suspend (String) -> ByteArray? = { name -> if (name == ParliamentRepository.INDEX_NAME) indexJson else packages[name] }
        val repository = ParliamentRepository(null, InMemoryParliamentStorage(), bundled, keys.trust)
        val snapshot = assertIs<ParliamentOpening.Ready>(repository.open()).snapshot
        assertEquals(DataOrigin.BUNDLED, snapshot.origin)
        val loader = ParliamentWeekLoader(null, InMemoryParliamentStorage(), bundled)
        assertEquals(week, loader.held(snapshot)?.week?.pkg)
        assertEquals(PackageOutcome.Done, loader.download(snapshot))
        // A bundled package that does not match its fingerprint is ignored.
        val tampered = ParliamentWeekLoader(null, InMemoryParliamentStorage()) { name -> if (name == ParliamentWeekFormat.PACKAGE) bytes(week.copy(items = emptyList())) else null }
        assertNull(tampered.held(snapshot))
    }
}
