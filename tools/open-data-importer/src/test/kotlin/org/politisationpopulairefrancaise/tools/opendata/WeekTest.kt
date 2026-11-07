package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.AgendaStatus
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.IndexReading
import org.politisationpopulairefrancaise.core.voir.parlement.OpenDataTrust
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndexReader
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekFormat
import org.politisationpopulairefrancaise.core.voir.parlement.SittingKind
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Cette semaine au Parlement »: the three parsers and the package, on the example files; never the network. */
class WeekTest {
    private val window = WeekWindow.of("2026-11-07")

    @Test
    fun theWindowCoversTheDayOfTheImportAndFourteenDaysAfter() {
        assertEquals("2026-11-21", window.to.toString())
        assertTrue(window.contains(java.time.LocalDateTime.parse("2026-11-07T00:00")))
        assertTrue(window.contains(java.time.LocalDateTime.parse("2026-11-21T23:30")))
        assertFalse(window.contains(java.time.LocalDateTime.parse("2026-11-22T00:00")))
        assertFalse(window.contains(java.time.LocalDateTime.parse("2026-11-06T23:59")))
    }

    @Test
    fun theAgendaOfTheAssembleeKeepsSittingsAndCommitteesOnALegislativeFile() {
        val week = ExampleWeekSources()
        val meetings = AnAgenda.meetings(week.anAgenda(), window)
        // Past sitting, committee without legislative point, friendship group: left out.
        assertEquals(listOf("RUANR5L17S2026IDS99001", "RUANR5L17S2026IDS99002", "RUANR5L17S2026IDC99003", "RUANR5L17S2026IDS99006"), meetings.map { it.uid })
        val items = AnAgenda.items(meetings, AnAgenda.organNames(week.anOrgans(), setOf("PO900010")))
        val plenary = items.filter { it.id.startsWith("an:RUANR5L17S2026IDS99001:") }
        assertEquals(2, plenary.size)
        val bill = plenary[1]
        assertEquals(Chamber.AN, bill.chamber)
        assertEquals(SittingKind.PLENARY, bill.kind)
        assertEquals("2026-11-10T15:00", bill.start)
        assertEquals("2026-11-10T20:00", bill.end)
        assertEquals("Suite de la discussion", bill.pointKind)
        assertEquals("an-dossier:DLR5L17N90001", bill.dossier)
        assertEquals("https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N90001", bill.dossierUrl)
        assertEquals(AgendaStatus.CONFIRMED, bill.status)
        assertEquals(AnAgenda.PLENARY_URL, bill.url)
        assertNull(plenary[0].dossier)

        val committee = items.single { it.kind == SittingKind.COMMITTEE }
        assertEquals("an:RUANR5L17S2026IDC99003:1", committee.id)
        assertEquals("[EXEMPLE] Commission des affaires fictives", committee.body)
        assertEquals("an-dossier:DLR5L17N90002", committee.dossier)

        val noPoints = items.single { it.id == "an:RUANR5L17S2026IDS99002" }
        assertEquals(AgendaStatus.PROVISIONAL, noPoints.status)
        assertEquals("[EXEMPLE] Suite de l'ordre du jour de la veille.", noPoints.title)
        assertEquals(AgendaStatus.CANCELLED, items.single { it.id.startsWith("an:RUANR5L17S2026IDS99006") }.status)
    }

    @Test
    fun timesAreConvertedToTheTimeOfParis() {
        assertEquals("2026-11-10T15:00", AnAgenda.dateTime("2026-11-10T14:00:00.000Z")!!.minutes())
        assertEquals("2026-07-01T14:00", AnAgenda.dateTime("2026-07-01T12:00:00+00:00")!!.minutes())
        assertEquals("2026-11-10T15:00", SenatICalendar.dateTime("20261110T140000Z", emptyMap())!!.minutes())
        assertEquals("2026-11-10T00:00", SenatICalendar.dateTime("20261110", mapOf("VALUE" to "DATE"))!!.minutes())
        assertEquals("2026-11-10T09:00", SenatICalendar.dateTime("20261110T090000", mapOf("TZID" to "Europe/Paris"))!!.minutes())
    }

    @Test
    fun theCalendarOfTheSenatExpandsItsRepetitionsAndKeepsNoPersonalData() {
        val events = SenatICalendar.events(ExampleWeekSources().senatCalendar())
        assertEquals(4, events.size)
        val items = SenatICalendar.items(events, window)
        assertEquals(
            listOf("2026-11-10T14:30", "2026-11-11T15:00", "2026-11-13T10:30", "2026-11-16T14:30", "2026-11-18T15:00"),
            items.map { it.start }.sorted(),
        )
        val first = items.single { it.start == "2026-11-10T14:30" }
        assertEquals("senat:exemple-seance-99001@senat.fr:202611101430", first.id)
        assertEquals("2026-11-10T20:00", first.end)
        assertEquals("[EXEMPLE] Projet de loi fictif sur les acacias", first.title)
        assertEquals("[EXEMPLE] Discussion générale, puis examen des articles.\nTexte n° 9002 (2026-2027).", first.detail)
        assertEquals(AgendaStatus.CONFIRMED, first.status)
        assertEquals(SittingKind.PLENARY, first.kind)
        // A link outside www.senat.fr is replaced by the Sénat's agenda page.
        assertTrue(items.filter { it.title!!.contains("Questions") }.all { it.url == SenatICalendar.AGENDA_URL && it.end!!.endsWith("16:30") && it.status == null })
        assertTrue(items.filter { it.title!!.contains("Suite") }.all { it.status == AgendaStatus.PROVISIONAL })
        val all = ParliamentWeekFormat.write(org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekPackage(example = true, from = "2026-11-07", to = "2026-11-21", items = items))
        listOf("mailto", "Autre Personne", "01 00 00 00 00", "Hémicycle", "Prénom Nom").forEach { assertFalse(it in all, it) }
    }

    @Test
    fun aWeeklyRuleWithoutDaysRepeatsTheDayOfTheStart() {
        val calendar = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:x
            DTSTART;TZID=Europe/Paris:20261103T100000
            RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261117T235959Z
            SUMMARY:x
            END:VEVENT
            BEGIN:VEVENT
            UID:y
            DTSTART;TZID=Europe/Paris:20261109T090000
            RRULE:FREQ=DAILY;INTERVAL=2;COUNT=3
            SUMMARY:y
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val items = SenatICalendar.items(SenatICalendar.events(calendar.byteInputStream()), window)
        assertEquals(listOf("2026-11-10T10:00", "2026-11-17T10:00"), items.filter { it.title == "x" }.map { it.start })
        assertEquals(listOf("2026-11-09T09:00", "2026-11-11T09:00", "2026-11-13T09:00"), items.filter { it.title == "y" }.map { it.start })
    }

    @Test
    fun theForeseenActivitiesOfTheEuropeanParliament() {
        val items = EuroparlAgenda.items(ExampleWeekSources()::europarl, window)
        assertEquals(listOf("pe:MTG-PL-2026-11-18:1", "pe:MTG-PL-2026-11-18:2"), items.map { it.id })
        assertEquals("[EXEMPLE] Débat fictif sur les forêts européennes", items[0].title)
        assertEquals("2026-11-18T09:00", items[0].start)
        assertEquals("2026-11-18T11:50", items[0].end)
        assertEquals("Débat", items[0].pointKind)
        assertEquals("[EXEMPLE] Heure des votes", items[1].title)
        assertEquals("2026-11-18T12:00", items[1].start)
        assertEquals("Votes", items[1].pointKind)
        assertTrue(items.all { it.status == AgendaStatus.PROVISIONAL && it.chamber == Chamber.PE })
    }

    @Test
    fun theImportWritesTheWeekPackageInTheSignedIndex() {
        val keys = IdentityKeys.generate()
        val out = Files.createTempDirectory("ppf-week-test")
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}).run())
        val event = SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText())
        val index = (ParliamentIndexReader.read(event, OpenDataTrust.of(keys.publicKey.hex)) as IndexReading.Accepted).index
        val ref = assertNotNull(index.packageRef(ParliamentWeekFormat.PACKAGE))
        val bytes = Files.readAllBytes(out.resolve("semaine.json"))
        assertTrue(ParliamentIndexReader.matches(ref, bytes))
        val pkg = assertNotNull(ParliamentWeekFormat.read(bytes.decodeToString()))
        assertTrue(pkg.example)
        assertEquals("2026-11-07", pkg.from)
        assertEquals("2026-11-21", pkg.to)
        assertEquals(setOf(Chamber.AN, Chamber.SENAT, Chamber.PE), pkg.items.map { it.chamber }.toSet())
        assertEquals(pkg.items.sortedWith(org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeek.ORDER), pkg.items)
        val text = bytes.decodeToString()
        listOf("participants", "Zoé Vasseur", "dateNais", "demandeurs", "Salle fictive", "mailto").forEach { assertFalse(it in text, it) }
        // A second import gives the same bytes, and the package is listed once.
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}).run())
        assertTrue(bytes.contentEquals(Files.readAllBytes(out.resolve("semaine.json"))))
        val again = (ParliamentIndexReader.read(SignedEvent.fromJson(out.resolve(Importer.INDEX_FILE).readText()), OpenDataTrust.of(keys.publicKey.hex)) as IndexReading.Accepted).index
        assertEquals(1, again.packages.count { it.name == ParliamentWeekFormat.PACKAGE })
    }

    @Test
    fun aChamberNotRefreshedOrUnreadableKeepsItsPreviousItems() {
        val out: Path = Files.createTempDirectory("ppf-week-keep")
        val keys = IdentityKeys.generate()
        assertEquals(ExitCodes.OK, Importer(ExampleSources(), keys, out, log = {}).run())
        val before = ParliamentWeekFormat.read(out.resolve("semaine.json").readText())!!
        // The Sénat's feed broken: its items stay, the others are read again; the import still succeeds.
        val broken = object : WeekSources by ExampleWeekSources() {
            override fun senatCalendar(): InputStream = "not a calendar".byteInputStream()
        }
        val logs = ArrayList<String>()
        val pkg = WeekImport(ExampleSources(), SourceId.entries.toSet(), out, { logs += it }) { broken }.build()
        assertEquals(before.items, pkg.items)
        assertTrue(logs.any { "senat unreadable" in it })
        // Only the European Parliament refreshed: the Assemblée and the Sénat come from the previous package.
        val onlyPe = WeekImport(ExampleSources(), setOf(SourceId.PE), out, {}) { object : WeekSources by ExampleWeekSources() {
            override fun anAgenda(): EntrySource = EntrySource { throw UnreadableSourceException("must not be read") }
        } }.build()
        assertEquals(before.items, onlyPe.items)
        // No previous package and no source: an empty week, never a failure.
        val empty = WeekImport(ExampleSources(), SourceId.entries.toSet(), Files.createTempDirectory("ppf-week-empty"), {}) { null }.build()
        assertTrue(empty.items.isEmpty())
    }
}
