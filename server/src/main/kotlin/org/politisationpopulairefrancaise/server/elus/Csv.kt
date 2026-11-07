package org.politisationpopulairefrancaise.server.elus

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * A file of the directory could not be read as expected. The message names the file, the columns
 * or the line number, never a value read from the file (they hold personal data).
 */
class ElusFormatException(message: String) : Exception(message)

/**
 * Streaming reader of delimited text (RFC 4180): one record at a time, never the whole file in
 * memory. Tolerant of what the official files do: a byte order mark, CRLF or LF line ends, fields
 * between double quotes (with `""` for a quote, separators and line breaks inside), a stray quote in
 * the middle of an unquoted field (kept as it is), empty lines (skipped).
 */
class CsvReader(
    private val input: Reader,
    private val separator: Char,
    private val maxRecordChars: Int = DEFAULT_MAX_RECORD_CHARS,
) : Closeable {
    private val buffer = CharArray(BUFFER_SIZE)
    private var length = 0
    private var position = 0
    private var started = false

    /** Number of the line where the last record returned started (1 for the first line). */
    var lineNumber: Long = 0
        private set
    private var currentLine: Long = 1

    private fun read(): Int {
        if (position == length) {
            length = input.read(buffer, 0, buffer.size)
            position = 0
            if (length <= 0) {
                length = 0
                return -1
            }
        }
        return buffer[position++].code
    }

    private fun peek(): Int {
        if (position == length) {
            length = input.read(buffer, 0, buffer.size)
            position = 0
            if (length <= 0) {
                length = 0
                return -1
            }
        }
        return buffer[position].code
    }

    /** The next non-empty record, or null at the end of the input. */
    fun next(): List<String>? {
        while (true) {
            val record = readRecord() ?: return null
            if (record.size == 1 && record[0].isEmpty()) continue
            return record
        }
    }

    private fun readRecord(): List<String>? {
        if (!started) {
            started = true
            if (peek() == BOM) read()
        }
        if (peek() == -1) return null
        lineNumber = currentLine
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var fieldStart = true
        var total = 0
        while (true) {
            val c = read()
            if (++total > maxRecordChars) throw ElusFormatException("Line $lineNumber is longer than $maxRecordChars characters.")
            if (quoted) {
                when (c) {
                    -1 -> throw ElusFormatException("Unterminated quoted field starting on line $lineNumber.")
                    QUOTE -> if (peek() == QUOTE) {
                        read()
                        field.append('"')
                    } else {
                        quoted = false
                    }
                    else -> {
                        if (c == LF) currentLine++
                        field.append(c.toChar())
                    }
                }
                continue
            }
            when {
                c == -1 -> {
                    fields += field.toString()
                    return fields
                }
                c == QUOTE && fieldStart -> {
                    quoted = true
                    fieldStart = false
                }
                c == separator.code -> {
                    fields += field.toString()
                    field.setLength(0)
                    fieldStart = true
                }
                c == CR -> {
                    if (peek() == LF) read()
                    currentLine++
                    fields += field.toString()
                    return fields
                }
                c == LF -> {
                    currentLine++
                    fields += field.toString()
                    return fields
                }
                else -> {
                    field.append(c.toChar())
                    fieldStart = false
                }
            }
        }
    }

    override fun close() = input.close()

    companion object {
        const val DEFAULT_MAX_RECORD_CHARS: Int = 1 shl 20
        private const val BUFFER_SIZE = 1 shl 16
        private const val BOM = 0xFEFF
        private const val QUOTE = '"'.code
        private const val CR = '\r'.code
        private const val LF = '\n'.code
    }
}

/** Opens the bytes of an official file as text, in the encoding it really uses. */
object CsvInput {
    /** Bytes looked at to tell UTF-8 from Windows-1252. */
    const val SNIFF_BYTES: Int = 1 shl 16

    val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    /**
     * A reader of [stream] in UTF-8 when its first [SNIFF_BYTES] bytes are valid UTF-8, otherwise in
     * Windows-1252 (the files of La Poste and of the election results, whatever their server says).
     * UTF-8 is decoded strictly: a broken sequence further in the file fails the import instead of
     * being silently replaced. A UTF-8 byte order mark is left to [CsvReader], which skips it.
     */
    fun reader(stream: InputStream): Reader {
        val buffered = BufferedInputStream(stream, SNIFF_BYTES)
        buffered.mark(SNIFF_BYTES)
        val head = buffered.readNBytes(SNIFF_BYTES)
        buffered.reset()
        val utf8 = isUtf8(head, complete = head.size < SNIFF_BYTES)
        val decoder = if (utf8) {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        } else {
            // Five bytes have no character in Windows-1252; they never appear in these files.
            WINDOWS_1252.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
        }
        return InputStreamReader(buffered, decoder)
    }

    /** Whether [bytes] are valid UTF-8; when not [complete], a sequence cut at the end is accepted. */
    fun isUtf8(bytes: ByteArray, complete: Boolean): Boolean {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(bytes.size + 1)
        val result = decoder.decode(input, output, complete)
        if (result.isError) return false
        return if (complete) !decoder.flush(output).isError else true
    }
}

/**
 * A delimited file read by the names of its columns, never by their position: strict on the
 * columns expected (all must be present, or the file is refused), tolerant of the others (ignored,
 * never read). Header names are compared without accents, case, spaces doubled or a leading `#`.
 */
class CsvTable private constructor(
    private val reader: CsvReader,
    val file: String,
    private val indices: Map<String, Int>,
) : Closeable {
    private val needed = (indices.values.maxOrNull() ?: -1) + 1

    /** Records too short to hold every expected column: skipped and counted, never read. */
    var skipped: Int = 0
        private set

    /** Calls [action] with every record of the file, in order. */
    fun forEach(action: (CsvRow) -> Unit) {
        while (true) {
            val fields = reader.next() ?: return
            if (fields.size < needed) {
                skipped++
                continue
            }
            action(CsvRow(fields, indices))
        }
    }

    override fun close() = reader.close()

    companion object {
        /**
         * Reads the header of [reader] and checks that every column of [columns] is there.
         * [file] names the file in error messages.
         */
        fun open(file: String, reader: Reader, separator: Char, columns: Collection<String>): CsvTable {
            val csv = CsvReader(reader, separator)
            try {
                val header = csv.next() ?: throw ElusFormatException("$file is empty.")
                val positions = HashMap<String, Int>()
                header.forEachIndexed { index, name -> positions.putIfAbsent(normalizeHeader(name), index) }
                val missing = columns.filter { normalizeHeader(it) !in positions }
                if (missing.isNotEmpty()) {
                    throw ElusFormatException("$file lacks the column(s) ${missing.joinToString { "\"$it\"" }}.")
                }
                return CsvTable(csv, file, columns.associateWith { positions.getValue(normalizeHeader(it)) })
            } catch (e: Exception) {
                csv.close()
                throw e
            }
        }

        /** The name of a column as compared: no accents, lowercase, single spaces, no leading `#`. */
        fun normalizeHeader(name: String): String =
            Normalizer.normalize(name.removePrefix("﻿").trim().removePrefix("#"), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase()
                .replace(SPACES, " ")
                .trim()

        private val COMBINING_MARKS = Regex("\\p{M}+")
        private val SPACES = Regex("\\s+")
    }
}

/** One record of a [CsvTable]: only the expected columns can be read, trimmed. */
class CsvRow internal constructor(private val fields: List<String>, private val indices: Map<String, Int>) {
    operator fun get(column: String): String {
        val index = indices[column] ?: throw IllegalArgumentException("Column \"$column\" was not declared.")
        return fields[index].trim()
    }

    /** The value of [column], or null when it is empty. */
    fun optional(column: String): String? = get(column).takeIf { it.isNotEmpty() }
}
