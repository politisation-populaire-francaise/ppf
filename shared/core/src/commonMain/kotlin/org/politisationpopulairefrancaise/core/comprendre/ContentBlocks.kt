package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * An objection and its sourced answer (pluralism, owner's decision of 7 November 2026: the
 * contents of "Comprendre" present the objections and answer them with sources).
 */
data class Objection(val objection: String, val answer: String)

/** A dated line of a chronology. */
data class TimelineEntry(
    /** The date as written: `AAAA`, `AAAA-MM` or `AAAA-MM-JJ`. */
    val date: String,
    val text: String,
) {
    /** The date in words: « 7 juin 1936 », « juin 1936 » or « 1936 ». */
    val frenchDate: String get() = ContentBlocks.frenchDate(date) ?: date
}

/**
 * The blocks that editorial contents of "Comprendre" add to the Markdown of content/README.md:
 *
 * - `::: objections` … `:::` : pairs of one `? objection` line and one or more `> answer` lines;
 * - `::: chronologie` … `:::` : lines `- DATE : text`, DATE being `AAAA`, `AAAA-MM` or `AAAA-MM-JJ`.
 *
 * Each reader returns what it understood and the problems found, so that the content signer can
 * refuse a malformed block while the application shows what it can.
 */
object ContentBlocks {
    const val OBJECTIONS: String = "objections"
    const val TIMELINE: String = "chronologie"

    fun parseObjections(lines: List<String>): Pair<List<Objection>, List<String>> {
        val problems = mutableListOf<String>()
        val items = mutableListOf<Objection>()
        var question: String? = null
        val answer = mutableListOf<String>()

        fun flush() {
            val q = question ?: return
            val a = answer.filter { it.isNotEmpty() }.joinToString(" ")
            if (a.isEmpty()) problems += "objection without an answer: \"$q\"" else items += Objection(q, a)
            question = null
            answer.clear()
        }

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("?") -> {
                    flush()
                    question = line.removePrefix("?").trim().ifEmpty { null }
                    if (question == null) problems += "empty objection"
                }
                line.startsWith(">") -> if (question == null) problems += "answer without an objection: \"$line\"" else answer += line.removePrefix(">").trim()
                else -> problems += "unexpected line in objections: \"$line\""
            }
        }
        flush()
        if (items.isEmpty() && problems.isEmpty()) problems += "an objections block needs at least one \"? objection\" with its \"> answer\""
        return items to problems
    }

    fun parseTimeline(lines: List<String>): Pair<List<TimelineEntry>, List<String>> {
        val problems = mutableListOf<String>()
        val entries = mutableListOf<TimelineEntry>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = TIMELINE_LINE.matchEntire(line)
            if (match == null || frenchDate(match.groupValues[1]) == null) {
                problems += "chronology line must be \"- AAAA-MM-JJ : text\": \"$line\""
                continue
            }
            entries += TimelineEntry(match.groupValues[1], match.groupValues[2].trim())
        }
        if (entries.isEmpty() && problems.isEmpty()) problems += "a chronology needs at least one line"
        if (entries.zipWithNext().any { (a, b) -> a.date > b.date }) problems += "chronology lines must be in date order"
        return entries to problems
    }

    /** « 7 juin 1936 », « juin 1936 », « 1936 », or null for a malformed date. */
    fun frenchDate(date: String): String? {
        val parts = date.split('-')
        val year = parts[0].takeIf { it.length == 4 }?.toIntOrNull() ?: return null
        return when (parts.size) {
            1 -> year.toString()
            2 -> {
                val month = parts[1].takeIf { it.length == 2 }?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
                CivilDate(year, month, 1).monthYearFrench()
            }
            3 -> CivilDate.parseIso(date)?.toFrench()
            else -> null
        }
    }

    /**
     * Removes the `::: [name]` blocks from [lines] and returns the remaining lines; the inner lines
     * of each block are added to [found]. Blocks do not nest.
     */
    fun extract(lines: List<String>, name: String, found: MutableList<List<String>>): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val start = BLOCK_START.matchEntire(lines[index].trimEnd())
            if (start != null && start.groupValues[1] == name) {
                val inner = mutableListOf<String>()
                index++
                while (index < lines.size && !BLOCK_END.matches(lines[index].trimEnd())) {
                    inner += lines[index]
                    index++
                }
                found += inner
                index++
                continue
            }
            kept += lines[index]
            index++
        }
        return kept
    }

    /** The problems of every `objections` and `chronologie` block of a body, for the content signer. */
    fun problems(markdown: String): List<String> {
        val objections = mutableListOf<List<String>>()
        val timelines = mutableListOf<List<String>>()
        extract(extract(markdown.lines(), OBJECTIONS, objections), TIMELINE, timelines)
        return objections.flatMap { parseObjections(it).second } + timelines.flatMap { parseTimeline(it).second }
    }

    /** True when the body has at least one well-formed objections block. */
    fun hasObjections(markdown: String): Boolean {
        val found = mutableListOf<List<String>>()
        extract(markdown.lines(), OBJECTIONS, found)
        return found.any { parseObjections(it).first.isNotEmpty() }
    }

    private val TIMELINE_LINE = Regex("^[-*]\\s+(\\d{4}(?:-\\d{2}){0,2})\\s*:\\s*(.+)$")
    private val BLOCK_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val BLOCK_END = Regex("^:::\\s*$")
}
