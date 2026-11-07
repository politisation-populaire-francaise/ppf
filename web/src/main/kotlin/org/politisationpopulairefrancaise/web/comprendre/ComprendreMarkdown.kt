package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.div
import kotlinx.html.em
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.h5
import kotlinx.html.h6
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.time
import kotlinx.html.ul
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.comprendre.Objection
import org.politisationpopulairefrancaise.core.comprendre.TimelineEntry
import org.politisationpopulairefrancaise.web.voir.EXTERNAL_REL
import org.politisationpopulairefrancaise.web.voir.isWebAddress

/** The boxes of the editorial contents (content/README.md), with the same titles as the application. */
internal enum class Callout(val key: String, val title: String) {
    EN_BREF("en-bref", "En bref"),
    LEGAL("legal", "Légal"),
    ILLEGAL("illegal", "Illégal"),
    ATTENTION("attention", "Attention"),
    CONSEIL("conseil", "Conseils"),
    ;

    companion object {
        fun fromKey(key: String): Callout? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The Markdown of the contents of "Comprendre" rendered on the server, following the rules of the
 * application (`ui/content/SimpleMarkdown.kt`): headings, paragraphs, bullet and numbered lists, quotes,
 * bold, italic, links, the boxes `::: en-bref`, `::: legal`, `::: illegal`, `::: attention`,
 * `::: conseil`, the objections (`::: objections`) and the chronologies (`::: chronologie`). kotlinx.html
 * escapes every character of text: no HTML of a content is ever written as HTML.
 *
 * Links: web addresses only (`https://`, `http://`), opened without script but marked as leaving the
 * site with their host; `ppf:<space>` leads to the page of that space on the site; any other target
 * stays plain text. Addresses written out in the text become links too.
 *
 * Glossary (D-048, D-091): the first occurrence of each term of [glossary] in the whole page becomes a
 * link to the page of its entry, described (`aria-describedby`) by its short definition, which the page
 * lists at the end ([used], rendered by [glossaryTerms]). Headings, links, addresses and the sources are
 * never searched. One renderer serves one page, so that each term is linked once and every id is unique.
 */
internal class ComprendreRenderer(
    private val glossary: GlossaryIndex? = null,
    /** Entries never linked on this page (the entry of the page itself). */
    private val excludedSlugs: Set<String> = emptySet(),
) {
    private val seen = LinkedHashSet<String>()
    private var counter = 0

    /** The entries linked on the page, in reading order. */
    val used: List<GlossaryEntry> get() = seen.mapNotNull { glossary?.entry(it) }

    /** An id unique in the page. */
    fun nextId(prefix: String): String = "$prefix-${++counter}"

    /**
     * Renders [source]. [headingOffset] moves the headings down (`##` becomes h3 with 1) when the content
     * sits under a heading of the page. [withGlossary] = false leaves every word alone (sources).
     */
    fun markdown(flow: FlowContent, source: String, headingOffset: Int = 0, withGlossary: Boolean = true) {
        Blocks(flow, headingOffset, withGlossary).render(source.replace("\r\n", "\n").lines())
    }

    private inner class Blocks(private val flow: FlowContent, private val offset: Int, private var gloss: Boolean) {
        fun render(lines: List<String>) = flow.blocks(lines)

        private fun FlowContent.blocks(lines: List<String>) {
            var i = 0
            val paragraph = mutableListOf<String>()
            fun flush() {
                if (paragraph.isNotEmpty()) {
                    val text = paragraph.joinToString(" ")
                    paragraph.clear()
                    p { inline(this, text, gloss) }
                }
            }
            while (i < lines.size) {
                val line = lines[i].trimEnd()
                val trimmed = line.trimStart()
                val block = BLOCK_START.matchEntire(trimmed)
                val heading = HEADING.matchEntire(trimmed)
                when {
                    trimmed.isEmpty() -> {
                        flush()
                        i++
                    }
                    block != null -> {
                        flush()
                        val name = block.groupValues[1]
                        val inner = mutableListOf<String>()
                        i++
                        while (i < lines.size && !BLOCK_END.matches(lines[i].trim())) inner += lines[i++]
                        i++ // the closing fence, if any
                        val callout = Callout.fromKey(name)
                        when {
                            callout != null -> div(classes = "callout callout-${callout.key}") {
                                attributes["role"] = "note"
                                p(classes = "callout-title") { +callout.title }
                                blocks(inner)
                            }
                            name == ContentBlocks.OBJECTIONS -> objections(this, ContentBlocks.parseObjections(inner).first, offset)
                            name == ContentBlocks.TIMELINE -> timeline(this, ContentBlocks.parseTimeline(inner).first)
                            else -> blocks(inner)
                        }
                    }
                    heading != null -> {
                        flush()
                        val text = heading.groupValues[2].trim()
                        val level = (if (heading.groupValues[1].length <= 2) 2 else 3) + offset
                        headingOf(this, level) { inline(this, text, glossary = false) }
                        // Nothing after the sources is searched for the glossary.
                        if (text.equals(SOURCES, ignoreCase = true)) gloss = false
                        i++
                    }
                    BULLET.matches(trimmed) -> {
                        flush()
                        ul {
                            while (i < lines.size && BULLET.matches(lines[i].trimStart())) {
                                val item = StringBuilder(BULLET.matchEntire(lines[i].trimStart())!!.groupValues[1].trim())
                                i++
                                // Continuation lines of the same item, indented.
                                while (i < lines.size && lines[i].startsWith("  ") && lines[i].isNotBlank() && !BULLET.matches(lines[i].trimStart())) {
                                    item.append(' ').append(lines[i].trim())
                                    i++
                                }
                                li { inline(this, item.toString(), gloss) }
                            }
                        }
                    }
                    NUMBERED.matches(trimmed) -> {
                        flush()
                        ol {
                            while (i < lines.size && NUMBERED.matches(lines[i].trimStart())) {
                                li { inline(this, NUMBERED.matchEntire(lines[i].trimStart())!!.groupValues[1].trim(), gloss) }
                                i++
                            }
                        }
                    }
                    trimmed.startsWith(">") -> {
                        flush()
                        val quoted = mutableListOf<String>()
                        while (i < lines.size && lines[i].trimStart().startsWith(">")) quoted += lines[i++].trimStart().removePrefix(">").trim()
                        blockQuote { blocks(quoted) }
                    }
                    else -> {
                        paragraph += trimmed
                        i++
                    }
                }
            }
            flush()
        }
    }

    /** Objections and their sourced answers, under a heading of the level of the content's sections. */
    fun objections(flow: FlowContent, items: List<Objection>, headingOffset: Int = 0) {
        if (items.isEmpty()) return
        val id = nextId("objections")
        flow.section(classes = "objections") {
            attributes["aria-labelledby"] = id
            headingOf(this, 2 + headingOffset, id) { +"Objections et réponses" }
            items.forEach { item ->
                div(classes = "objection") {
                    p(classes = "objection-q") {
                        strong { +"Objection : " }
                        inline(this, item.objection, glossary = true)
                    }
                    p(classes = "objection-a") {
                        strong { +"Réponse : " }
                        inline(this, item.answer, glossary = true)
                    }
                }
            }
        }
    }

    private fun timeline(flow: FlowContent, entries: List<TimelineEntry>) {
        if (entries.isEmpty()) return
        flow.ol(classes = "timeline") {
            entries.forEach { entry ->
                li {
                    time {
                        attributes["datetime"] = entry.date
                        +entry.frenchDate
                    }
                    +" : "
                    inline(this, entry.text, glossary = true)
                }
            }
        }
    }

    /** Bold, italic, links and addresses inside a line; the words of the remaining text are searched for the glossary. */
    fun inline(flow: FlowContent, text: String, glossary: Boolean = true) {
        var rest = text
        while (rest.isNotEmpty()) {
            val bold = rest.indexOf("**").takeIf { it >= 0 && rest.indexOf("**", it + 2) > it + 2 }
            val link = LINK.find(rest)
            val url = URL.find(rest)
            val italic = ITALIC.find(rest)
            val next = listOfNotNull(
                bold?.let { it to 'b' },
                link?.let { it.range.first to 'l' },
                url?.let { it.range.first to 'u' },
                italic?.let { it.range.first to 'i' },
            ).minByOrNull { it.first }
            if (next == null) {
                plain(flow, rest, glossary)
                return
            }
            val (start, kind) = next
            if (start > 0) plain(flow, rest.substring(0, start), glossary)
            when (kind) {
                'b' -> {
                    val end = rest.indexOf("**", start + 2)
                    flow.strong { inline(this, rest.substring(start + 2, end), glossary) }
                    rest = rest.substring(end + 2)
                }
                'l' -> {
                    val match = link!!
                    linkTo(flow, match.groupValues[1], match.groupValues[2])
                    rest = rest.substring(match.range.last + 1)
                }
                'u' -> {
                    val match = url!!
                    val address = match.value.trimEnd(*TRAILING)
                    if (isWebAddress(address)) externalLink(flow, address, address, showHost = false) else flow.run { +address }
                    rest = rest.substring(match.range.first + address.length)
                }
                else -> {
                    val match = italic!!
                    flow.em { inline(this, match.groupValues[1], glossary) }
                    rest = rest.substring(match.range.last + 1)
                }
            }
        }
    }

    /** A link of a content: a web address, a space of the site (`ppf:revendications`) or a page of the site (`/comprendre/...`). */
    private fun linkTo(flow: FlowContent, label: String, target: String) {
        val space = target.removePrefix("ppf:").takeIf { target.startsWith("ppf:") }?.let(Space::fromId)
        when {
            isWebAddress(target) -> externalLink(flow, target, label, showHost = true)
            space != null -> flow.a(href = "/${space.id}") { +label }
            SITE_PATH.matches(target) -> flow.a(href = target) { +label }
            else -> flow.run { +label }
        }
    }

    /** Text, with the first occurrence of each term of the glossary as a link to its entry. */
    private fun plain(flow: FlowContent, text: String, glossary: Boolean) {
        val index = this.glossary
        if (!glossary || index == null || index.isEmpty) {
            flow.run { +text }
            return
        }
        var last = 0
        for (match in index.firstOccurrences(text, seen, excludedSlugs = excludedSlugs)) {
            if (match.start > last) flow.run { +text.substring(last, match.start) }
            flow.a(href = ComprendreLinks.glossaryPath(match.slug), classes = "gloss") {
                attributes["aria-describedby"] = definitionId(match.slug)
                +text.substring(match.start, match.end)
            }
            last = match.end
        }
        if (last < text.length) flow.run { +text.substring(last) }
    }

    companion object {
        const val SOURCES: String = "Sources"

        /** The id of the definition of an entry in the list of the terms of the page. */
        fun definitionId(slug: String): String = "def-" + slug.map { if (it.isLetterOrDigit() && it.code < 0x80) it else '-' }.joinToString("")

        private val HEADING = Regex("^(#{1,3})\\s+(.*)$")
        private val BULLET = Regex("^[-*]\\s+(.*)$")
        private val NUMBERED = Regex("^\\d{1,3}\\.\\s+(.*)$")
        private val BLOCK_START = Regex("^:::\\s*([a-z-]+)\\s*$")
        private val BLOCK_END = Regex("^:::\\s*$")
        private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
        private val URL = Regex("https?://[^\\s<>\"()\\[\\]]+")
        private val ITALIC = Regex("(?<![*\\w])\\*([^*\\s][^*]*?)\\*(?![*\\w])")
        private val SITE_PATH = Regex("^/(?!/)[A-Za-z0-9/_.~%-]*$")
        private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', '»', '\'', '’')
    }
}

/** A heading of the given level, from 2 to 6. */
internal fun headingOf(flow: FlowContent, level: Int, id: String? = null, block: FlowContent.() -> Unit) {
    when (level.coerceIn(2, 6)) {
        2 -> flow.h2 { id?.let { this.id = it }; block() }
        3 -> flow.h3 { id?.let { this.id = it }; block() }
        4 -> flow.h4 { id?.let { this.id = it }; block() }
        5 -> flow.h5 { id?.let { this.id = it }; block() }
        else -> flow.h6 { id?.let { this.id = it }; block() }
    }
}

/** The host of a web address, shown next to every link that leaves the site. */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')

/**
 * A link that leaves the site: opened without script, never sending this page's address
 * ([EXTERNAL_REL]), and saying so in words (with its host when the text does not show the address).
 */
internal fun externalLink(flow: FlowContent, url: String, text: String, showHost: Boolean = true) {
    flow.a(href = url) {
        attributes["rel"] = EXTERNAL_REL
        +text
        span(classes = "external-mark") { +(if (showHost) " (site extérieur : ${hostOf(url)})" else " (site extérieur)") }
    }
}
