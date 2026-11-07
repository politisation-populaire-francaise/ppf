package org.politisationpopulairefrancaise.server.elus

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The CSV reader of the directory: tolerant of what the official files do, strict on the columns. */
class CsvTest {
    private fun records(text: String, separator: Char = ';'): List<List<String>> {
        val reader = CsvReader(StringReader(text), separator)
        return generateSequence { reader.next() }.toList()
    }

    @Test
    fun aByteOrderMarkCrlfAndEmptyLinesAreIgnored() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "")),
            records("﻿a;b\r\n1;2\r\n\r\n\n3;\n"),
        )
    }

    @Test
    fun quotedFieldsKeepSeparatorsQuotesAndLineBreaks() {
        assertEquals(
            listOf(listOf("200067544", "Cc \"Creuse Confluence\"", "x;y", "deux\nlignes")),
            records("200067544;\"Cc \"\"Creuse Confluence\"\"\";\"x;y\";\"deux\nlignes\""),
        )
    }

    @Test
    fun aStrayQuoteInsideAnUnquotedFieldIsKept() {
        assertEquals(listOf(listOf("L'Abergement", "a\"b")), records("L'Abergement;a\"b"))
    }

    @Test
    fun anUnterminatedQuoteFailsWithTheLineNumberOnly() {
        val error = assertFailsWith<ElusFormatException> { records("a;b\n\"secret value;2\n") }
        assertEquals("Unterminated quoted field starting on line 2.", error.message)
    }

    @Test
    fun theCommaSeparatedCodeOfficielGeographiqueIsRead() {
        assertEquals(listOf(listOf("COM", "01001", "L'Abergement-Clémenciat")), records("\"COM\",\"01001\",\"L'Abergement-Clémenciat\"\r\n", ','))
    }

    @Test
    fun columnsAreFoundByNameWhateverTheirOrderAccentsCaseOrSpaces() {
        val table = CsvTable.open("test.csv", StringReader("Prénom de l'élu;Libellé du  département;#Code_postal;Ignored\nCamille;Rhône;69001;x\n"), ';', listOf("Code_postal", "Libellé du département", "Prenom de l'elu"))
        val rows = ArrayList<Triple<String, String, String>>()
        table.use { it.forEach { row -> rows += Triple(row["Prenom de l'elu"], row["Libellé du département"], row["Code_postal"]) } }
        assertEquals(listOf(Triple("Camille", "Rhône", "69001")), rows)
    }

    @Test
    fun aMissingColumnIsRefusedByName() {
        val error = assertFailsWith<ElusFormatException> {
            CsvTable.open("elus-deputes-dep.csv", StringReader("Code du département;Nom de l'élu\n69;SECRET\n"), ';', listOf("Code du département", "Date de début du mandat"))
        }
        assertEquals("elus-deputes-dep.csv lacks the column(s) \"Date de début du mandat\".", error.message)
        assertFalse(error.message.orEmpty().contains("SECRET"))
    }

    @Test
    fun recordsTooShortAreSkippedAndCounted() {
        val table = CsvTable.open("t.csv", StringReader("a;b;c\n1;2;3\n4;5\n6;7;8\n"), ';', listOf("a", "c"))
        val values = ArrayList<String>()
        table.use { t ->
            t.forEach { values += it["c"] }
            assertEquals(1, t.skipped)
        }
        assertEquals(listOf("3", "8"), values)
    }

    @Test
    fun emptyValuesReadAsNullWithOptional() {
        val table = CsvTable.open("t.csv", StringReader("a;b\n 1 ;  \n"), ';', listOf("a", "b"))
        table.use { t ->
            t.forEach {
                assertEquals("1", it["a"])
                assertNull(it.optional("b"))
            }
        }
    }

    @Test
    fun theEncodingIsDetectedFromTheFirstBytes() {
        val windows = "#Code_commune_INSEE;Libellé_d_acheminement\n01001;L ABERGEMENT\n".toByteArray(CsvInput.WINDOWS_1252)
        val utf8 = "﻿Code;Libellé\n1;Réunion\n".toByteArray(Charsets.UTF_8)
        assertFalse(CsvInput.isUtf8(windows, complete = true))
        assertTrue(CsvInput.isUtf8(utf8, complete = true))
        assertEquals("#Code_commune_INSEE;Libellé_d_acheminement", CsvInput.reader(windows.inputStream()).readText().lines().first())
        val reader = CsvReader(CsvInput.reader(utf8.inputStream()), ';')
        assertEquals(listOf("Code", "Libellé"), reader.next())
        assertEquals(listOf("1", "Réunion"), reader.next())
    }

    @Test
    fun aUtf8SequenceCutAtTheEndOfTheSniffedBytesIsStillUtf8() {
        val bytes = "é".repeat(10).toByteArray(Charsets.UTF_8)
        assertTrue(CsvInput.isUtf8(bytes.copyOf(bytes.size - 1), complete = false))
        assertFalse(CsvInput.isUtf8(bytes.copyOf(bytes.size - 1), complete = true))
    }

    @Test
    fun aBrokenUtf8SequenceFurtherInTheFileFailsInsteadOfBeingReplaced() {
        val head = "a;b\n".toByteArray() + ByteArray(CsvInput.SNIFF_BYTES) { 'x'.code.toByte() }
        val broken = head + byteArrayOf(0xC3.toByte(), '\n'.code.toByte(), 0xE9.toByte(), 0x41)
        val reader = CsvReader(CsvInput.reader(broken.inputStream()), ';')
        assertFailsWith<java.nio.charset.MalformedInputException> { generateSequence { reader.next() }.toList() }
    }

    @Test
    fun aRecordLongerThanTheLimitIsRefused() {
        assertFailsWith<ElusFormatException> { CsvReader(StringReader("x".repeat(100)), ';', maxRecordChars = 50).next() }
    }
}
