package org.politisationpopulairefrancaise.server.export

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The CSV files of the open export (RFC 4180, OWASP « CSV injection »). */
class CsvTest {
    private val columns = listOf(
        Column("text", ColumnType.STRING, "Texte."),
        Column("count", ColumnType.INTEGER, "Nombre."),
        Column("value", ColumnType.NUMBER, "Valeur.", nullable = true),
        Column("url", ColumnType.URL, "Adresse.", nullable = true),
    )

    @Test
    fun cellsAreQuotedOnlyWhenNeededAndLinesEndWithCrLf() {
        val csv = Csv.of(
            columns,
            listOf(
                listOf("simple", 1, 2.5, "https://example.org/a"),
                listOf("virgule, guillemet \" et\nretour", 2, null, null),
            ),
        )
        assertEquals(
            "text,count,value,url\r\n" +
                "simple,1,2.5,https://example.org/a\r\n" +
                "\"virgule, guillemet \"\" et\nretour\",2,,\r\n",
            csv,
        )
    }

    @Test
    fun numbersHaveAPointNoExponentAndAmountsKeepTheirCents() {
        assertEquals("312000", Csv.cell(ColumnType.NUMBER, 312_000.0))
        assertEquals("0.0001", Csv.cell(ColumnType.NUMBER, 0.0001))
        assertEquals("12345678901", Csv.cell(ColumnType.INTEGER, 12_345_678_901L))
        assertEquals("4210345.60", Csv.cell(ColumnType.NUMBER, BigDecimal.valueOf(421_034_560, 2)))
        assertEquals("-3", Csv.cell(ColumnType.NUMBER, -3), "a negative number stays a number")
    }

    @Test
    fun aTextThatASpreadsheetWouldReadAsAFormulaGetsAnApostrophe() {
        listOf("=1+1", "+33 1 23", "-2", "@SOMME(A1)", "\tonglet").forEach { assertEquals("'$it", Csv.cell(ColumnType.STRING, it), it) }
        assertEquals("Un texte = sans danger", Csv.cell(ColumnType.STRING, "Un texte = sans danger"))
    }

    @Test
    fun aLineMustHaveOneValuePerColumn() {
        assertFailsWith<IllegalArgumentException> { Csv.of(columns, listOf(listOf("un seul"))) }
    }
}
