package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.blockQuote
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.ul

/**
 * The small part of Markdown the editorial contents use (docs/FORMATS.md), rendered on the server
 * with kotlinx.html, which escapes every character of text: headings (`##`, `###`), bullet and
 * numbered lists, quotes (`>`), paragraphs, `**bold**`, `*italic*`, links `[text](https://...)`
 * (web addresses only, marked as leaving the site) and the boxes `::: en-bref` ... `:::`.
 * Anything else stays plain text: no HTML of the content is ever written as HTML.
 */
internal fun FlowContent.markdown(source: String) {
    renderBlocks(source.replace("\r\n", "\n").lines())
}

private fun FlowContent.renderBlocks(lines: List<String>) {
    var i = 0
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            val text = paragraph.joinToString(" ")
            p { inline(text) }
            paragraph.clear()
        }
    }
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val trimmed = line.trimStart()
        when {
            trimmed.isEmpty() -> {
                flush()
                i++
            }
            trimmed.startsWith(":::") && trimmed.length > 3 -> {
                flush()
                val name = trimmed.removePrefix(":::").trim()
                val inner = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim() != ":::") inner += lines[i++]
                i++ // the closing fence
                if (name == "en-bref") {
                    aside(classes = "md-box") {
                        attributes["aria-label"] = "En bref"
                        p(classes = "md-box-title") { +"En bref" }
                        renderBlocks(inner)
                    }
                } else {
                    div { renderBlocks(inner) }
                }
            }
            trimmed.startsWith("### ") -> {
                flush()
                h3 { inline(trimmed.removePrefix("### ").trim()) }
                i++
            }
            trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                flush()
                h2 { inline(trimmed.substringAfter(' ').trim()) }
                i++
            }
            isBullet(trimmed) -> {
                flush()
                ul {
                    while (i < lines.size && isBullet(lines[i].trimStart())) {
                        val item = lines[i].trimStart().drop(2).trim()
                        li { inline(item) }
                        i++
                    }
                }
            }
            NUMBERED.matches(trimmed) -> {
                flush()
                ol {
                    while (i < lines.size && NUMBERED.matches(lines[i].trimStart())) {
                        li { inline(lines[i].trimStart().substringAfter(". ").trim()) }
                        i++
                    }
                }
            }
            trimmed.startsWith(">") -> {
                flush()
                val quoted = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) quoted += lines[i++].trimStart().removePrefix(">").trim()
                blockQuote { renderBlocks(quoted) }
            }
            else -> {
                paragraph += trimmed
                i++
            }
        }
    }
    flush()
}

private val NUMBERED = Regex("""\d{1,3}\. .*""")

private fun isBullet(line: String): Boolean = line.startsWith("- ") || line.startsWith("* ")

/** Bold, italic and links inside a line; everything else is text. */
internal fun FlowContent.inline(text: String) {
    var rest = text
    while (rest.isNotEmpty()) {
        val bold = rest.indexOf("**")
        val link = LINK.find(rest)
        val italic = ITALIC.find(rest)
        val next = listOfNotNull(
            bold.takeIf { it >= 0 && rest.indexOf("**", it + 2) > it + 2 }?.let { it to 'b' },
            link?.let { it.range.first to 'l' },
            italic?.let { it.range.first to 'i' },
        ).minByOrNull { it.first }
        if (next == null) {
            +rest
            return
        }
        val (start, kind) = next
        if (start > 0) +rest.substring(0, start)
        when (kind) {
            'b' -> {
                val end = rest.indexOf("**", start + 2)
                strong { inline(rest.substring(start + 2, end)) }
                rest = rest.substring(end + 2)
            }
            'l' -> {
                val match = link!!
                val label = match.groupValues[1]
                val target = match.groupValues[2]
                if (isWebAddress(target)) {
                    a(href = target) {
                        attributes["rel"] = EXTERNAL_REL
                        +label
                        externalMark()
                    }
                } else {
                    +label
                }
                rest = rest.substring(match.range.last + 1)
            }
            else -> {
                val match = italic!!
                em { +match.groupValues[1] }
                rest = rest.substring(match.range.last + 1)
            }
        }
    }
}

private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
private val ITALIC = Regex("""(?<![*\w])\*([^*\s][^*]*?)\*(?![*\w])""")
