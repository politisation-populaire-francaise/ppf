package org.politisationpopulairefrancaise.ui.content

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.Objection
import org.politisationpopulairefrancaise.core.comprendre.TimelineEntry
import org.politisationpopulairefrancaise.ui.theme.CalloutType

/** The small subset of Markdown used by editorial contents (content/README.md). */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullets(val items: List<String>) : MarkdownBlock

    /** A highlighted box (`::: en-bref` … `:::`) holding its own blocks. */
    data class Callout(val type: CalloutType, val blocks: List<MarkdownBlock>) : MarkdownBlock

    /** Objections and their sourced answers (`::: objections`, pluralism). */
    data class Objections(val items: List<Objection>) : MarkdownBlock

    /** A chronology (`::: chronologie`): dated lines. */
    data class Timeline(val entries: List<TimelineEntry>) : MarkdownBlock
}

/**
 * A deliberately small Markdown reader: headings (`#` to `###`), paragraphs separated by blank lines,
 * bullet lists (`-` or `*`), bold (`**`), links (`[text](url)`, shown as "text (url)" because the
 * application never opens an external site without warning) and highlighted boxes opened by
 * `::: en-bref`, `::: legal`, `::: illegal`, `::: attention` or `::: conseil` and closed by `:::`,
 * objections with their answers (`::: objections`) and chronologies (`::: chronologie`).
 * Anything else is shown as plain text.
 */
object SimpleMarkdown {

    fun parse(markdown: String): List<MarkdownBlock> = parseLines(markdown.lines())

    private fun parseLines(lines: List<String>): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = StringBuilder()
        val bullets = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.toString().trim())
                paragraph.clear()
            }
        }

        fun flushBullets() {
            if (bullets.isNotEmpty()) {
                blocks += MarkdownBlock.Bullets(bullets.toList())
                bullets.clear()
            }
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index].trimEnd()
            val heading = HEADING.matchEntire(line)
            val calloutStart = CALLOUT_START.matchEntire(line)
            when {
                calloutStart != null -> {
                    flushParagraph()
                    flushBullets()
                    val type = CalloutType.fromKey(calloutStart.groupValues[1])
                    val inner = mutableListOf<String>()
                    index++
                    while (index < lines.size && !CALLOUT_END.matches(lines[index].trimEnd())) {
                        inner += lines[index]
                        index++
                    }
                    val key = calloutStart.groupValues[1]
                    val objections = if (key == ContentBlocks.OBJECTIONS) ContentBlocks.parseObjections(inner).first else emptyList()
                    val timeline = if (key == ContentBlocks.TIMELINE) ContentBlocks.parseTimeline(inner).first else emptyList()
                    when {
                        objections.isNotEmpty() -> blocks += MarkdownBlock.Objections(objections)
                        timeline.isNotEmpty() -> blocks += MarkdownBlock.Timeline(timeline)
                        type != null -> blocks += MarkdownBlock.Callout(type, parseLines(inner))
                        else -> blocks += parseLines(inner)
                    }
                }
                line.isBlank() -> {
                    flushParagraph()
                    flushBullets()
                }
                heading != null -> {
                    flushParagraph()
                    flushBullets()
                    blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                }
                BULLET.matches(line) -> {
                    flushParagraph()
                    bullets += BULLET.matchEntire(line)!!.groupValues[1].trim()
                }
                bullets.isNotEmpty() && line.startsWith("  ") -> bullets[bullets.lastIndex] = bullets.last() + " " + line.trim()
                else -> {
                    flushBullets()
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            index++
        }
        flushParagraph()
        flushBullets()
        return blocks
    }

    /**
     * Bold and links; everything else verbatim. A link is shown as "text (url)" and carries a string
     * annotation [LINK_TAG] over that whole span, so that the glossary never spots a word inside it.
     */
    fun inline(text: String): AnnotatedString {
        // Links first, remembering where each one lands in the intermediate text.
        val linked = StringBuilder(text.length)
        val links = mutableListOf<Pair<Int, Int>>()
        var last = 0
        for (match in LINK.findAll(text)) {
            linked.appendRange(text, last, match.range.first)
            val start = linked.length
            linked.append(match.groupValues[1]).append(" (").append(match.groupValues[2]).append(')')
            links += start to linked.length
            last = match.range.last + 1
        }
        linked.appendRange(text, last, text.length)
        val source = linked.toString()
        // Then bold: [kept] maps each character of the intermediate text to its place in the result.
        val kept = IntArray(source.length + 1)
        return buildAnnotatedString {
            var cursor = 0
            fun copy(until: Int) {
                while (cursor < until) {
                    kept[cursor] = length
                    append(source[cursor])
                    cursor++
                }
            }
            while (true) {
                val match = BOLD.find(source, cursor) ?: break
                copy(match.range.first)
                // The opening marks vanish: they point to where the bold text starts.
                kept[cursor] = length
                kept[cursor + 1] = length
                cursor += 2
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { copy(match.range.last - 1) }
                kept[cursor] = length
                kept[cursor + 1] = length
                cursor += 2
            }
            copy(source.length)
            kept[source.length] = length
            links.forEach { (start, end) ->
                val from = kept[start]
                val to = kept[end]
                if (to > from) addStringAnnotation(LINK_TAG, "", from, to)
            }
        }
    }

    /** The string annotation of the links of [inline]. */
    const val LINK_TAG: String = "ppf-link"

    private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
    private val BULLET = Regex("^[-*]\\s+(.*)$")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val CALLOUT_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val CALLOUT_END = Regex("^:::\\s*$")
}
