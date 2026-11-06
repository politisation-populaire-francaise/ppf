package org.politisationpopulairefrancaise.core.agir.agenda

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The iCalendar export (RFC 5545), checked on exact outputs. */
class ICalendarTest {
    private val fixtures = AgendaFixtures()

    @Test
    fun oneMobilizationGivesThisExactFile() = runTest {
        val m = fixtures.mobilization(
            identifier = "marche-hopital",
            title = "[EXEMPLE] Marche pour l'hôpital; tous, ensemble",
            location = "Place de la Gare, Villefictive",
            summary = "[EXEMPLE] Résumé",
            description = "Ligne 1\nLigne 2 \\ fin",
            links = listOf("https://exemple.invalid/marche"),
        )
        val pk = fixtures.pubkey
        val expected = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Politisation Populaire Francaise//Agenda des mobilisations//FR",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "BEGIN:VEVENT",
            // 75 octets, then the rest on a continuation line.
            "UID:31923:$pk:\r\n marche-hopital",
            "DTSTAMP:20261105T120000Z",
            "DTSTART:20261114T130000Z",
            "DTEND:20261114T160000Z",
            "SUMMARY:[EXEMPLE] Marche pour l'hôpital\\; tous\\, ensemble",
            "LOCATION:Place de la Gare\\, Villefictive",
            "DESCRIPTION:[EXEMPLE] Résumé\\n\\nLigne 1\\nLigne 2 \\\\ fin",
            "URL:https://exemple.invalid/marche",
            "STATUS:CONFIRMED",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("") { it + "\r\n" }
        assertEquals(expected, ICalendar.export(m))
        assertEquals("marche-hopital.ics", ICalendar.fileName(m))
    }

    @Test
    fun aCancelledOrPostponedEventSaysItAndAnEventWithoutEndHasNoEnd() = runTest {
        val cancelled = ICalendar.export(fixtures.mobilization(status = MobilizationStatus.ANNULE, end = null, description = ""))
        assertTrue("STATUS:CANCELLED\r\n" in cancelled)
        assertFalse("DTEND" in cancelled)
        assertFalse("DESCRIPTION" in cancelled, "no empty description")
        assertFalse("URL" in cancelled)
        assertTrue("STATUS:TENTATIVE\r\n" in ICalendar.export(fixtures.mobilization(status = MobilizationStatus.REPORTE)))
    }

    @Test
    fun severalMobilizationsShareOneCalendar() = runTest {
        val file = ICalendar.export(listOf(fixtures.mobilization("un"), fixtures.mobilization("deux")))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(file).count())
        assertEquals(1, Regex("BEGIN:VCALENDAR").findAll(file).count())
        assertTrue(file.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun textValuesAreEscaped() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf\\ng", ICalendar.escape("a\\b;c,d\ne\r\nf\rg"))
        assertEquals("tab\tkept, bell removed".replace(",", "\\,"), ICalendar.escape("tab\tkept, bell\u0007 removed"))
        assertEquals("deux-points: et guillemets \"ok\"", ICalendar.escape("deux-points: et guillemets \"ok\""))
    }

    @Test
    fun longLinesAreFoldedAt75OctetsWithoutCuttingACharacter() {
        // "SUMMARY:" is 8 octets and each "é" 2: 33 of them fill 74 octets, the 34th would pass 75.
        val accented = "SUMMARY:" + "é".repeat(40)
        assertEquals("SUMMARY:" + "é".repeat(33) + "\r\n " + "é".repeat(7), ICalendar.fold(accented))
        // A character outside the basic plane (4 octets, two UTF-16 units) is never split.
        val emoji = "x".repeat(74) + "😀"
        assertEquals("x".repeat(74) + "\r\n 😀", ICalendar.fold(emoji))
        // Exactly 75 octets: no fold. 76: one fold, the space counting on the next line.
        assertEquals("y".repeat(75), ICalendar.fold("y".repeat(75)))
        assertEquals("y".repeat(75) + "\r\n y", ICalendar.fold("y".repeat(76)))
        val long = "z".repeat(75 + 74 + 10)
        assertEquals("z".repeat(75) + "\r\n " + "z".repeat(74) + "\r\n " + "z".repeat(10), ICalendar.fold(long))
    }

    @Test
    fun everyPhysicalLineFitsAndEndsWithCrlf() = runTest {
        val m = fixtures.mobilization(
            title = "[EXEMPLE] " + "Très longue mobilisation, avec des virgules; ".repeat(3).take(130),
            description = "[EXEMPLE] Œuvre collective : ".repeat(40) + "😀",
        )
        val file = ICalendar.export(m)
        assertTrue(file.endsWith("\r\n"))
        val lines = file.removeSuffix("\r\n").split("\r\n")
        assertTrue(lines.none { '\n' in it || '\r' in it })
        assertTrue(lines.all { it.encodeToByteArray().size <= 75 }, lines.maxOf { it.encodeToByteArray().size }.toString())
        // Unfolding gives back the logical lines.
        val unfolded = file.replace("\r\n ", "")
        assertTrue("DESCRIPTION:[EXEMPLE] Œuvre collective : [EXEMPLE]" in unfolded)
        assertTrue(unfolded.contains("😀"))
    }

    @Test
    fun datesAreWrittenInUtc() {
        assertEquals("20261114T130000Z", ICalendar.utc(AgendaTimes.SATURDAY_14H))
        assertEquals("19700101T000000Z", ICalendar.utc(0))
        assertEquals("20261231T235959Z", ICalendar.utc(1_798_761_599L))
    }
}
