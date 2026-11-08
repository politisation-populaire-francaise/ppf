package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import java.io.Reader

/**
 * Reads a JSON document of any size as a stream and hands over, one at a time, the objects found
 * as the value of a key of [objectKeys], or as an element of an array that is the value of a key of
 * [arrayKeys]. Everything else is skipped without being kept. The file of the ballots of the 14th
 * legislature is a single document (`Scrutins_XIV.json`, a list `scrutins` of 1,354 ballots with
 * every nominal vote): read whole, its tree would not fit in the 256 MB of the importer.
 *
 * Each object handed over is at most [maxChars] characters; the document must be well formed
 * (any error is an [UnreadableSourceException] naming [name], never a value of the data).
 */
internal class JsonObjectStream(
    private val reader: Reader,
    private val name: String,
    private val objectKeys: Set<String>,
    private val arrayKeys: Set<String>,
    private val maxChars: Int = SourceEntry.MAX_ENTRY_BYTES.toInt(),
) {
    private var peeked = -2

    private fun fail(what: String): Nothing = throw UnreadableSourceException("$name: $what")

    private fun peek(): Int {
        if (peeked == -2) peeked = reader.read()
        return peeked
    }

    private fun next(): Int = peek().also { peeked = -2 }

    private fun skipSpaces() {
        while (true) {
            val c = peek()
            if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) next() else return
        }
    }

    private fun expect(c: Char) {
        skipSpaces()
        if (next() != c.code) fail("malformed JSON")
    }

    fun forEach(action: (JsonElement) -> Unit) {
        // A byte order mark is not JSON.
        if (peek() == 0xFEFF) next()
        value(key = null, arrayKey = null, depth = 0, action = action)
        skipSpaces()
        if (peek() != -1) fail("trailing content")
    }

    private fun value(key: String?, arrayKey: String?, depth: Int, action: (JsonElement) -> Unit) {
        if (depth > MAX_DEPTH) fail("too deep")
        skipSpaces()
        when (peek()) {
            '{'.code -> if ((arrayKey == null && key in objectKeys) || (arrayKey != null && arrayKey in arrayKeys)) {
                val text = capture()
                val element = try {
                    lenientJson.parseToJsonElement(text)
                } catch (e: Exception) {
                    fail("not a JSON document")
                }
                action(element)
            } else {
                next()
                skipSpaces()
                if (peek() == '}'.code) {
                    next()
                    return
                }
                while (true) {
                    skipSpaces()
                    if (peek() != '"'.code) fail("malformed JSON")
                    val child = string(keep = true)
                    expect(':')
                    value(child, null, depth + 1, action)
                    skipSpaces()
                    when (next()) {
                        ','.code -> continue
                        '}'.code -> return
                        else -> fail("malformed JSON")
                    }
                }
            }
            '['.code -> {
                next()
                skipSpaces()
                if (peek() == ']'.code) {
                    next()
                    return
                }
                while (true) {
                    value(null, key, depth + 1, action)
                    skipSpaces()
                    when (next()) {
                        ','.code -> continue
                        ']'.code -> return
                        else -> fail("malformed JSON")
                    }
                }
            }
            '"'.code -> string(keep = false)
            -1 -> fail("truncated JSON")
            else -> {
                // A number, true, false or null.
                var any = false
                while (true) {
                    val c = peek()
                    if (c == -1 || c == ','.code || c == '}'.code || c == ']'.code || c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) break
                    next()
                    any = true
                }
                if (!any) fail("malformed JSON")
            }
        }
    }

    /** A string; its text when [keep] (keys only, a few characters). */
    private fun string(keep: Boolean): String? {
        if (next() != '"'.code) fail("malformed JSON")
        val out = if (keep) StringBuilder() else null
        while (true) {
            val c = next()
            when (c) {
                -1 -> fail("truncated JSON")
                '"'.code -> return out?.toString()
                '\\'.code -> {
                    val escaped = next()
                    if (escaped == -1) fail("truncated JSON")
                    out?.append('\\')?.append(escaped.toChar())
                }
                else -> out?.append(c.toChar())
            }
            if (out != null && out.length > 1024) fail("key too long")
        }
    }

    /** The raw text of the object starting here, up to its closing brace. */
    private fun capture(): String {
        val out = StringBuilder()
        var depth = 0
        var inString = false
        while (true) {
            val c = next()
            if (c == -1) fail("truncated JSON")
            out.append(c.toChar())
            if (out.length > maxChars) fail("object larger than $maxChars characters")
            if (inString) {
                if (c == '\\'.code) {
                    val escaped = next()
                    if (escaped == -1) fail("truncated JSON")
                    out.append(escaped.toChar())
                } else if (c == '"'.code) {
                    inString = false
                }
                continue
            }
            when (c) {
                '"'.code -> inString = true
                '{'.code, '['.code -> depth++
                '}'.code, ']'.code -> {
                    depth--
                    if (depth == 0) return out.toString()
                }
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 64
    }
}
