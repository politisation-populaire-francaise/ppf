package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.editorial.ArticleType

/**
 * A Markdown file of content/ (content/README.md): a `---` front matter of `key: value` lines (repeated keys
 * kept in order, lines starting with `#` are comments for the editors), then the body, trimmed and ended by
 * one line feed. Read the same way by tools/content-signer and, later, by the import of the server.
 */
data class ContentDocument(val name: String, val entries: List<Pair<String, String>>, val body: String) {
    fun toDraft(type: ArticleType): ContentDraft = ContentDraft(type, entries, body)

    companion object {
        /** Throws IllegalArgumentException, naming the file, on a malformed front matter. */
        fun parse(name: String, text: String): ContentDocument {
            val lines = text.lines()
            require(lines.firstOrNull()?.trim() == "---") { "$name: the file must start with a --- front matter" }
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            require(end >= 0) { "$name: unterminated front matter" }
            val entries = lines.subList(1, 1 + end).filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.map { line ->
                val separator = line.indexOf(':')
                require(separator > 0) { "$name: invalid front matter line: $line" }
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            val body = lines.drop(2 + end).joinToString("\n").trim() + "\n"
            return ContentDocument(name, entries, body)
        }
    }
}
