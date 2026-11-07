package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * An entry of the glossary (D-048; contract of the third wave of "Comprendre", section 2): a term,
 * its short definition shown in the tooltip, the forms under which it is spotted in the texts, and
 * a detailed explanation (the body, « En savoir plus ») closed by its sources.
 *
 * Read from a NIP-23 article of type `glossaire`: `title` is the term, `summary` the definition,
 * one `ppf-variant` tag per form, `ppf-auto` = `non` for a term never spotted automatically, `a`
 * tags of role `voir-aussi` (other entries) and `lie` (related contents).
 */
data class GlossaryEntry(
    val article: Article,
    /** The term as displayed, without the [EXEMPLE] marker. */
    val term: String,
    /** The short definition of the tooltip, understandable on its own (200 characters at most). */
    val definition: String,
    /** The forms spotted in the texts: singular, plural, acronym and long form. */
    val variants: List<String>,
    /** False for `auto: non`: the entry is listed in the glossary but never spotted in the texts. */
    val auto: Boolean,
    /** Identifiers (slugs) of other entries of the glossary (« Voir aussi »). */
    val seeAlso: List<String>,
    /** Addresses (`30023:<clé>:<slug>`) of the related contents: courses, guides, fact-checks... */
    val related: List<String>,
) {
    val slug: String get() = article.slug

    /** Example contents carry the [EXEMPLE] marker, shown as a badge. */
    val example: Boolean get() = article.title.trimStart().startsWith(EXAMPLE_MARKER)

    /** The detailed explanation (« En savoir plus »), without its sources. */
    val explanation: String = splitSources(article.body).first

    /** The sources of the entry (the lines under `## Sources`), empty when there are none. */
    val sources: String = splitSources(article.body).second

    companion object {
        const val TAG_VARIANT: String = "ppf-variant"
        const val TAG_AUTO: String = "ppf-auto"
        const val AUTO_NO: String = "non"
        const val ROLE_SEE_ALSO: String = "voir-aussi"
        const val ROLE_RELATED: String = "lie"

        /** The longest short definition, in characters (contract, section 2.1). */
        const val MAX_DEFINITION: Int = 200
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"
        const val SOURCES_HEADING: String = "## Sources"

        /** Null when the content is not an entry of the glossary or misses its term or definition. */
        fun from(article: Article): GlossaryEntry? {
            if (article.type != ArticleType.GLOSSAIRE) return null
            val event = article.event
            val term = article.title.trim().removePrefix(EXAMPLE_MARKER).trim().takeIf { it.isNotEmpty() } ?: return null
            val definition = article.summary?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            fun links(role: String) = event.tags.filter { it.size >= 4 && it[0] == "a" && it[3] == role }.map { it[1] }.distinct()
            return GlossaryEntry(
                article = article,
                term = term,
                definition = definition,
                variants = event.tagValues(TAG_VARIANT).map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                auto = event.firstTagValue(TAG_AUTO)?.trim() != AUTO_NO,
                seeAlso = links(ROLE_SEE_ALSO).map { it.substringAfter(':').substringAfter(':') }.filter { it.isNotEmpty() && it != article.slug },
                related = links(ROLE_RELATED),
            )
        }

        /** The body before the `## Sources` line, and the lines after it. */
        fun splitSources(body: String): Pair<String, String> {
            val lines = body.lines()
            val at = lines.indexOfFirst { it.trim() == SOURCES_HEADING }
            if (at < 0) return body.trim() to ""
            return lines.subList(0, at).joinToString("\n").trim() to lines.subList(at + 1, lines.size).joinToString("\n").trim()
        }
    }
}

/** One form of the glossary found in a text: the characters `[start, end)` name the entry [slug]. */
data class GlossaryMatch(val start: Int, val end: Int, val slug: String)

/**
 * The glossary as the device uses it: every entry in alphabetical order, the full-text search of
 * the glossary page, and the spotting of the forms in any text (contract, section 2.3).
 *
 * Spotting ignores case, treats straight and curly apostrophes alike and respects accents. A form is
 * found only between word limits (the characters around it are neither letters nor digits: « loi »
 * is not found in « emploi »). At one position the longest form wins (« motion de censure » before
 * « motion ») and forms never overlap. The index is a tree of the folded forms, built once: finding
 * the forms of a text costs one walk of the text, whatever the size of the glossary.
 */
class GlossaryIndex(entries: List<GlossaryEntry>) {

    /** Every entry, once per slug (the newest version wins), ordered by term without accents or case. */
    val entries: List<GlossaryEntry> = entries
        .groupBy { it.slug }
        .map { (_, versions) -> versions.maxBy { it.article.updatedAt } }
        .sortedWith(compareBy<GlossaryEntry>({ FrenchText.fold(it.term) }, { it.term }, { it.slug }))

    private val bySlug: Map<String, GlossaryEntry> = this.entries.associateBy { it.slug }

    /** The forms compared without case. */
    private val root = Node()

    /** The acronyms (UE, QPC, EPCI...), compared with their capitals. */
    private val acronyms = Node()

    init {
        // In alphabetical order, so that a form claimed by two entries (refused by the content
        // signer) always goes to the same one.
        for (entry in this.entries) {
            if (!entry.auto) continue
            for (variant in entry.variants) {
                if (isAcronym(variant)) insert(acronyms, normalizeForm(variant, keepCase = true), entry.slug) else insert(root, normalizeForm(variant), entry.slug)
            }
        }
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    val size: Int get() = entries.size

    fun entry(slug: String): GlossaryEntry? = bySlug[slug]

    /**
     * Every form of [text], from left to right: at each position where a word starts, the longest
     * form that ends at a word limit. Characters of [excluded] (links, for example) and addresses
     * written out in the text (`http://…`, `https://…`, `www.…`, `ppf:…`) are never part of a form.
     * A form of an entry of [excludedSlugs] is passed over (its words are not searched for shorter
     * forms), but not reported.
     */
    fun find(text: String, excluded: List<IntRange> = emptyList(), excludedSlugs: Set<String> = emptySet()): List<GlossaryMatch> {
        if ((root.next.isEmpty() && acronyms.next.isEmpty()) || text.isEmpty()) return emptyList()
        val addresses = ADDRESS.findAll(text).map { it.range }.toList()
        val blocked: BooleanArray? = if (excluded.isEmpty() && addresses.isEmpty()) {
            null
        } else {
            BooleanArray(text.length).also { array ->
                (excluded + addresses).forEach { range -> for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(text.length - 1)) array[i] = true }
            }
        }
        val found = mutableListOf<GlossaryMatch>()
        var i = 0
        while (i < text.length) {
            val startsWord = (i == 0 || !text[i - 1].isLetterOrDigit()) && blocked?.get(i) != true
            val end = if (startsWord) {
                val acronym = longestAt(acronyms, keepCase = true, text, i, blocked)
                val other = longestAt(root, keepCase = false, text, i, blocked)
                // The longest wins; an acronym wins a tie (it is the more precise of the two).
                if (other != null && (acronym == null || other.first > acronym.first)) other else acronym
            } else {
                null
            }
            if (end == null) {
                i++
                continue
            }
            if (end.second !in excludedSlugs) found += GlossaryMatch(i, end.first, end.second)
            i = end.first
        }
        return found
    }

    /**
     * The first occurrence of each entry only: the forms of [text] whose entry is not already in
     * [seen], which they join. Passing the same [seen] to the pieces of one content, in reading order,
     * links each term once in the whole content.
     */
    fun firstOccurrences(
        text: String,
        seen: MutableSet<String>,
        excluded: List<IntRange> = emptyList(),
        excludedSlugs: Set<String> = emptySet(),
    ): List<GlossaryMatch> = find(text, excluded, excludedSlugs).filter { seen.add(it.slug) }

    /**
     * The entries whose term or one of its forms holds every word of [query] (each word of the query
     * starts a word of the term), then those whose definition holds them; accents and case ignored.
     * Alphabetical order, the whole glossary for an empty query.
     */
    fun search(query: String): List<GlossaryEntry> {
        if (FrenchText.words(query).isEmpty()) return entries
        return entries.filter { entry ->
            FrenchText.matchesAllWords(entry.term + " " + entry.variants.joinToString(" "), query) ||
                FrenchText.matchesAllWords(entry.definition, query)
        }
    }

    private fun insert(tree: Node, form: String, slug: String) {
        if (form.isEmpty()) return
        var node = tree
        for (char in form) node = node.next.getOrPut(char) { Node() }
        if (node.slug == null) node.slug = slug
    }

    /** The end and the entry of the longest form of [tree] starting at [start], or null. */
    private fun longestAt(tree: Node, keepCase: Boolean, text: String, start: Int, blocked: BooleanArray?): Pair<Int, String>? {
        if (tree.next.isEmpty()) return null
        var node = tree
        var j = start
        var previousSpace = false
        var best: Pair<Int, String>? = null
        while (j < text.length) {
            if (blocked?.get(j) == true) break
            val char = normalizeChar(text[j], keepCase)
            // Several spaces in a row in the text match the single space of a form.
            if (char == ' ' && previousSpace) {
                j++
                continue
            }
            node = node.next[char] ?: break
            j++
            previousSpace = char == ' '
            val slug = node.slug
            if (slug != null && (j == text.length || !text[j].isLetterOrDigit())) best = j to slug
        }
        return best
    }

    private class Node {
        val next = HashMap<Char, Node>()
        var slug: String? = null
    }

    companion object {
        val EMPTY: GlossaryIndex = GlossaryIndex(emptyList())

        /** An address written out in a text: never searched for forms. */
        private val ADDRESS = Regex("(?:https?://|www\\.|ppf:)\\S+", RegexOption.IGNORE_CASE)

        /**
         * An acronym: only capital letters and digits, at least two letters (UE, RIP, QPC, EPCI,
         * CMP). It is compared with its capitals, so that « UE » is not found in « ue »; every other
         * form is compared without case.
         */
        fun isAcronym(form: String): Boolean {
            val trimmed = form.trim()
            return trimmed.isNotEmpty() && trimmed.all { it.isDigit() || (it.isLetter() && it.isUpperCase()) } && trimmed.count { it.isLetter() } >= 2
        }

        /**
         * One character as the spotting compares it, without changing the length of the text:
         * lowercase (unless [keepCase], for the acronyms), every apostrophe as `'`, every space as
         * ` `, the non-breaking hyphen as `-`.
         */
        fun normalizeChar(char: Char, keepCase: Boolean = false): Char = when (char) {
            '’', '‘', 'ʼ', '′', '´', '`' -> '\''
            ' ', ' ', ' ', ' ', '\t', '\n', '\r' -> ' '
            '‐', '‑' -> '-'
            else -> if (keepCase) char else char.lowercaseChar()
        }

        /** A form as written by the editors, normalized for the comparison, inner spaces collapsed. */
        fun normalizeForm(form: String, keepCase: Boolean = false): String {
            val out = StringBuilder(form.length)
            for (char in form.trim()) {
                val normalized = normalizeChar(char, keepCase)
                if (normalized == ' ' && (out.isEmpty() || out.last() == ' ')) continue
                out.append(normalized)
            }
            return out.toString().trimEnd()
        }

        /** The heading of the entry in the alphabetical list: its first letter, or « 0-9 » for a number. */
        fun letterOf(entry: GlossaryEntry): String {
            val first = FrenchText.fold(entry.term).firstOrNull() ?: return "#"
            return if (first.isDigit()) "0-9" else first.uppercaseChar().toString()
        }
    }
}

/** One file of `content/glossaire/`, as the content signer reads it (contract, section 2.1). */
data class GlossarySource(
    /** The file name, for the messages. */
    val file: String,
    val slug: String,
    val title: String,
    val summary: String?,
    val variants: List<String>,
    val seeAlso: List<String>,
    /** `parcours:<slug>`, `fiche:<slug>`, `decryptage:<slug>`, `qui-decide:<slug>` or `histoire:<slug>`. */
    val related: List<String>,
    /** `non`, `oui` or null. */
    val auto: String?,
    val body: String,
)

/**
 * The checks and the tags of the glossary for the content signer (contract, section 2.2): it refuses
 * a missing or too long definition, an entry without forms, a form used by two entries, missing
 * sources and a link to an unknown entry or content.
 */
object GlossaryFormat {
    /** The prefixes of a `related` link and the directory of `content/` whose slugs they name. */
    val RELATED_KINDS: Map<String, String> = mapOf(
        "parcours" to "parcours",
        "fiche" to "fiches",
        "decryptage" to "decryptages",
        "qui-decide" to "qui-decide",
        "histoire" to "histoire",
    )

    /**
     * A comma-separated list of the front matter, trimmed, without empty items. A comma inside an
     * item is written `\,` (`Conseil économique\, social et environnemental`, `article 49\, alinéa 3`).
     */
    fun list(value: String?): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        val text = value.orEmpty()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            when {
                char == '\\' && i + 1 < text.length && text[i + 1] == ',' -> {
                    current.append(',')
                    i++
                }
                char == ',' -> {
                    items += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        items += current.toString()
        return items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Every problem of the glossary, each prefixed with its file. [known] gives, for each prefix of
     * [RELATED_KINDS], the slugs of the contents that exist.
     */
    fun problems(sources: List<GlossarySource>, known: Map<String, Set<String>>): List<String> {
        val problems = mutableListOf<String>()
        val slugs = sources.map { it.slug }.toSet()
        sources.groupBy { it.slug }.filterValues { it.size > 1 }.forEach { (slug, files) ->
            problems += "${files.joinToString { it.file }}: the slug \"$slug\" is used twice"
        }
        val owners = HashMap<String, GlossarySource>()
        for (source in sources) {
            val at = source.file
            val summary = source.summary?.trim().orEmpty()
            when {
                summary.isEmpty() -> problems += "$at: no summary (the short definition of the tooltip)"
                summary.length > GlossaryEntry.MAX_DEFINITION -> problems += "$at: the summary has ${summary.length} characters, ${GlossaryEntry.MAX_DEFINITION} at most"
            }
            if (source.title.trim().removePrefix(GlossaryEntry.EXAMPLE_MARKER).isBlank()) problems += "$at: no title (the term)"
            if (source.variants.isEmpty()) problems += "$at: no variants (the forms spotted in the texts)"
            val own = HashSet<String>()
            for (variant in source.variants) {
                val form = GlossaryIndex.normalizeForm(variant)
                when {
                    form.isEmpty() -> problems += "$at: an empty variant"
                    !form.first().isLetterOrDigit() || !form.last().isLetterOrDigit() -> problems += "$at: the variant \"$variant\" must start and end with a letter or a digit"
                    !own.add(form) -> problems += "$at: the variant \"$variant\" appears twice"
                    else -> {
                        val other = owners[form]
                        if (other != null) problems += "$at: the variant \"$variant\" is already a variant of ${other.file}" else owners[form] = source
                    }
                }
            }
            if (source.body.lines().none { it.trim() == GlossaryEntry.SOURCES_HEADING }) problems += "$at: no \"${GlossaryEntry.SOURCES_HEADING}\" section"
            if (source.auto != null && source.auto != GlossaryEntry.AUTO_NO && source.auto != "oui") problems += "$at: auto is \"oui\" or \"non\", not \"${source.auto}\""
            for (slug in source.seeAlso) {
                when (slug) {
                    source.slug -> problems += "$at: see_also names the entry itself"
                    !in slugs -> problems += "$at: see_also names an unknown entry: $slug"
                }
            }
            for (link in source.related) {
                val kind = link.substringBefore(':', "")
                val slug = link.substringAfter(':', "")
                when {
                    kind !in RELATED_KINDS -> problems += "$at: related is \"<kind>:<slug>\" with a kind among ${RELATED_KINDS.keys.joinToString()}: $link"
                    slug.isEmpty() || slug !in known[kind].orEmpty() -> problems += "$at: related names an unknown content: $link"
                }
            }
        }
        return problems
    }

    /** The tags of an entry beyond the common ones of an article, in a stable order. */
    fun tags(source: GlossarySource, authorHex: String): List<List<String>> = buildList {
        source.variants.forEach { add(listOf(GlossaryEntry.TAG_VARIANT, it.trim())) }
        if (source.auto == GlossaryEntry.AUTO_NO) add(listOf(GlossaryEntry.TAG_AUTO, GlossaryEntry.AUTO_NO))
        source.seeAlso.forEach { add(listOf("a", "${Article.KIND}:$authorHex:$it", "", GlossaryEntry.ROLE_SEE_ALSO)) }
        source.related.forEach { add(listOf("a", "${Article.KIND}:$authorHex:${it.substringAfter(':')}", "", GlossaryEntry.ROLE_RELATED)) }
    }
}
