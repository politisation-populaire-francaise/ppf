package org.politisationpopulairefrancaise.server.editorial

import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft

/** A simple line diff (longest common subsequence), enough to compare two versions of a draft. */
object LineDiff {
    enum class Kind { SAME, ADDED, REMOVED }

    data class Line(val kind: Kind, val text: String)

    /** Above this many cells (lines of one text times lines of the other), no diff is computed. */
    const val MAX_CELLS: Long = 4_000_000

    /** The lines of [before] and [after] in order, each kept, added or removed; null when too long to compare. */
    fun of(before: List<String>, after: List<String>): List<Line>? {
        val n = before.size
        val m = after.size
        if (n.toLong() * m.toLong() > MAX_CELLS) return null
        // lengths[i][j]: length of the longest common subsequence of before[i..] and after[j..].
        val lengths = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lengths[i][j] = if (before[i] == after[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val lines = ArrayList<Line>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                before[i] == after[j] -> {
                    lines += Line(Kind.SAME, before[i]); i++; j++
                }
                lengths[i + 1][j] >= lengths[i][j + 1] -> lines += Line(Kind.REMOVED, before[i++])
                else -> lines += Line(Kind.ADDED, after[j++])
            }
        }
        while (i < n) lines += Line(Kind.REMOVED, before[i++])
        while (j < m) lines += Line(Kind.ADDED, after[j++])
        return lines
    }

    /** A draft written as a file of content/: the front matter lines, a separator, then the body. */
    fun linesOf(draft: ContentDraft): List<String> =
        listOf("---") + draft.entries.map { (key, value) -> "$key: $value" } + listOf("---") + draft.body.replace("\r\n", "\n").trimEnd('\n').lines()

    fun changed(lines: List<Line>): Boolean = lines.any { it.kind != Kind.SAME }
}
