package org.politisationpopulairefrancaise.server.export

import java.math.BigDecimal

/**
 * The CSV file of a dataset (RFC 4180): UTF-8 without byte order mark, comma, CRLF at the end of each
 * line, a first line of headers. A cell is quoted when it holds a comma, a quote, a carriage return or a
 * line feed, its quotes doubled. Numbers are written with a point, without exponent nor grouping; a whole
 * value without « .0 ».
 *
 * A text cell that a spreadsheet would read as a formula (it starts with `=`, `+`, `-`, `@`, a tab or a
 * carriage return) gets a leading apostrophe (OWASP, « CSV injection »): the JSON file keeps the exact value.
 */
object Csv {
    fun of(columns: List<Column>, rows: List<List<Any?>>): String = buildString {
        line(columns.map { it.name })
        rows.forEach { row ->
            require(row.size == columns.size) { "A row has ${row.size} values for ${columns.size} columns" }
            line(row.mapIndexed { i, value -> cell(columns[i].type, value) })
        }
    }

    private fun StringBuilder.line(cells: List<String>) {
        cells.forEachIndexed { i, cell ->
            if (i > 0) append(',')
            append(quoted(cell))
        }
        append("\r\n")
    }

    internal fun cell(type: ColumnType, value: Any?): String = when {
        value == null -> ""
        type == ColumnType.INTEGER || type == ColumnType.NUMBER -> number(value)
        else -> neutralized(value.toString())
    }

    private fun number(value: Any): String = when (value) {
        is Int, is Long -> value.toString()
        is Double -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        // An amount keeps its scale (« 1210345.60 »), as in the JSON file.
        is BigDecimal -> value.toPlainString()
        else -> error("Not a number: ${value::class.simpleName}")
    }

    private fun neutralized(text: String): String =
        if (text.isNotEmpty() && text[0] in FORMULA_STARTS) "'$text" else text

    private fun quoted(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
