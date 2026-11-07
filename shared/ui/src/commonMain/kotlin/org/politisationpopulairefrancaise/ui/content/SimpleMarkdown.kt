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

    /** Bold and links; everything else verbatim. */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        var rest = text.replace(LINK) { match -> "${match.groupValues[1]} (${match.groupValues[2]})" }
        while (true) {
            val match = BOLD.find(rest) ?: break
            append(rest.substring(0, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            rest = rest.substring(match.range.last + 1)
        }
        append(rest)
    }

    private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
    private val BULLET = Regex("^[-*]\\s+(.*)$")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val CALLOUT_START = Regex("^:::\\s*([a-z-]+)\\s*$")
    private val CALLOUT_END = Regex("^:::\\s*$")
}
