package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The iCalendar feed (RFC 5545): exact output, escaping, folding at 75 octets. */
class AgendaCalendarTest {
    private val author = "a".repeat(64)

    /** A mobilization with fixed values (the feed does not check signatures: the events table holds verified ones). */
    private fun event(
        tags: List<List<String>>,
        content: String = "",
        createdAt: Long = 1_700_000_000,
    ) = SignedEvent(id = "b".repeat(64), pubkey = author, createdAt = createdAt, kind = AgirFormats.KIND_MOBILIZATION, tags = tags, content = content, sig = "c".repeat(128))

    @Test
    fun writesTheExactCalendar() {
        val cancelled = event(
            tags = listOf(
                listOf("d", "manif-test"),
                listOf("title", "[EXEMPLE] Manif, rassemblement; AG"),
                listOf("start", "1800000000"),
                listOf("end", "1800007200"),
                listOf("location", "Place Bellecour\\Lyon"),
                listOf("ppf-event-type", "manifestation"),
                listOf("ppf-event-status", "annule"),
                listOf("ppf-territory", "Lyon"),
                listOf("r", "javascript:alert(1)"),
                listOf("r", "https://exemple.invalid/manif"),
            ),
            content = "Ligne 1\nLigne 2\r\nLigne 3",
        )
        val planned = event(
            tags = listOf(listOf("d", "ag"), listOf("start", "1800000000"), listOf("summary", "[EXEMPLE] Résumé seul")),
        )
        val expected = "BEGIN:VCALENDAR\r\n" +
            "VERSION:2.0\r\n" +
            "PRODID:-//Politisation Populaire Française//Agenda des mobilisations//FR\r\n" +
            "CALSCALE:GREGORIAN\r\n" +
            "X-WR-CALNAME:PPF : agenda des mobilisations\r\n" +
            "BEGIN:VEVENT\r\n" +
            // 85 octets: folded after the 75th, the rest after a space.
            "UID:31923:$author:\r\n manif-test\r\n" +
            "DTSTAMP:20231114T221320Z\r\n" +
            "DTSTART:20270115T080000Z\r\n" +
            "DTEND:20270115T100000Z\r\n" +
            "SUMMARY:[EXEMPLE] Manif\\, rassemblement\\; AG\r\n" +
            "LOCATION:Place Bellecour\\\\Lyon\r\n" +
            "DESCRIPTION:Ligne 1\\nLigne 2\\nLigne 3\r\n" +
            "URL:https://exemple.invalid/manif\r\n" +
            "STATUS:CANCELLED\r\n" +
            "END:VEVENT\r\n" +
            "BEGIN:VEVENT\r\n" +
            "UID:31923:$author:\r\n ag\r\n" +
            "DTSTAMP:20231114T221320Z\r\n" +
            "DTSTART:20270115T080000Z\r\n" +
            "DESCRIPTION:[EXEMPLE] Résumé seul\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        assertEquals(expected, AgendaCalendar.render(listOf(cancelled, planned)))
    }

    @Test
    fun skipsWhatIsNotAMobilizationWithADate() {
        val note = event(listOf(listOf("d", "x"), listOf("start", "1"))).copy(kind = 1)
        val undated = event(listOf(listOf("d", "x")))
        val calendar = AgendaCalendar.render(listOf(note, undated))
        assertFalse(calendar.contains("BEGIN:VEVENT"))
        assertTrue(calendar.endsWith("X-WR-CALNAME:PPF : agenda des mobilisations\r\nEND:VCALENDAR\r\n"))
    }

    @Test
    fun escapesTextValues() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf\\ng\th", AgendaCalendar.escapeText("a\\b;c,d\ne\r\nf\rg\th"))
        // Other control characters are dropped; colons and quotes stay as they are.
        assertEquals("ab: \"c\"", AgendaCalendar.escapeText("a\u0000b\u0007: \"c\""))
    }

    @Test
    fun foldsAt75OctetsWithoutCuttingACharacter() {
        val samples = listOf(
            "x".repeat(75),
            "x".repeat(76),
            "SUMMARY:" + "é".repeat(200),
            "DESCRIPTION:" + "😀".repeat(40) + "fin",
            "LOCATION:" + "a€😀é".repeat(30),
            "",
        )
        for (sample in samples) {
            val folded = AgendaCalendar.fold(sample)
            assertTrue(folded.endsWith("\r\n"), sample)
            val lines = folded.removeSuffix("\r\n").split("\r\n")
            lines.forEachIndexed { index, line ->
                assertTrue(line.encodeToByteArray().size <= AgendaCalendar.MAX_LINE_OCTETS, "line of ${line.encodeToByteArray().size} octets")
                if (index > 0) assertTrue(line.startsWith(" "))
                // No half character: every line is valid UTF-8 on its own.
                assertEquals(line, line.encodeToByteArray().decodeToString())
                assertFalse(line.codePoints().anyMatch { it in 0xD800..0xDFFF })
            }
            // Unfolding gives the line back.
            assertEquals(sample, lines.first() + lines.drop(1).joinToString("") { it.removePrefix(" ") })
        }
        assertEquals("x".repeat(75) + "\r\n", AgendaCalendar.fold("x".repeat(75)))
        assertEquals("x".repeat(75) + "\r\n x\r\n", AgendaCalendar.fold("x".repeat(76)))
        // 37 two-octet characters fill a line (74 octets), the 38th would make 76.
        val accents = AgendaCalendar.fold("é".repeat(80)).removeSuffix("\r\n").split("\r\n")
        assertEquals(listOf(37, 37, 6), accents.map { it.removePrefix(" ").length })
    }

    @Test
    fun writesDatesInUtc() {
        assertEquals("19700101T000000Z", AgendaCalendar.utc(0))
        assertEquals("20261106T120000Z", AgendaCalendar.utc(java.time.Instant.parse("2026-11-06T12:00:00Z").epochSecond))
    }
}
