package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.comprendre.GlossaryFormat
import org.politisationpopulairefrancaise.core.comprendre.GlossarySource
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/*
 * The contents of "Comprendre", checked and built by the editorial engine of shared/core (D-092): the same
 * schemas (ComprendreSchemas) serve this tool and, later, the form and the checks of the server. The
 * no-regression test (NoRegressionTest) keeps the events identical to those signed before the engine.
 */

/** The Markdown files of a directory of content/, README aside, in the order of their names. */
private fun documents(directory: Path): List<Document> {
    if (!directory.isDirectory()) return emptyList()
    return directory.listDirectoryEntries("*.md")
        .filter { !it.name.equals("README.md", ignoreCase = true) }
        .sortedBy { it.name }
        .map { parseDocument(it.name, it.readText()) }
}

/** Checks one file against its schema (every problem, in French) and builds its unsigned event. */
internal fun engineEvent(schema: ContentSchema, document: Document, author: PublicKey, context: CheckContext): UnsignedEvent {
    val draft = ContentDraft(schema.type, document.entries, document.body)
    val problems = EditorialEngine.check(schema, draft, context)
    require(problems.isEmpty()) { "${document.name}: invalid content:\n  " + problems.joinToString("\n  ") { EditorialMessages.french(it) } }
    return EditorialEngine.build(schema, draft, author).also { println("prepared ${document.name}") }
}

/** Every file of the directory of [schema]. */
internal fun engineEvents(content: Path, schema: ContentSchema, author: PublicKey, context: CheckContext): List<UnsignedEvent> =
    documents(content.resolve(schema.sourceDirectory)).map { engineEvent(schema, it, author, context) }

/** The practical guides (`fiches`): the schema of the `type` of each file. */
internal fun guideEvents(content: Path, author: PublicKey, context: CheckContext): List<UnsignedEvent> =
    documents(content.resolve(ComprendreSchemas.FICHE_PRATIQUE.sourceDirectory)).map { document ->
        val type = ArticleType.fromTag(document.require("type")) ?: error("${document.name}: unknown type")
        val schema = EditorialSchemas.of(type)?.takeIf { it.sourceDirectory == ComprendreSchemas.FICHE_PRATIQUE.sourceDirectory }
            ?: error("${document.name}: the type ${type.tagValue} has no form in fiches")
        engineEvent(schema, document, author, context)
    }

/**
 * The contents of "Comprendre" beyond the guides and the courses (comprendre.json), in this order:
 * fact-checks, stories, « Qui décide de ça ? » sheets, the method page, the teaching sheets (CO-13, last so
 * that the events before them keep their place).
 */
internal fun comprendreEvents(content: Path, author: PublicKey, context: CheckContext): List<UnsignedEvent> =
    listOf(ComprendreSchemas.DECRYPTAGE, ComprendreSchemas.HISTOIRE, ComprendreSchemas.QUI_DECIDE, ComprendreSchemas.METHODE, ComprendreSchemas.FICHE_PEDAGOGIQUE)
        .flatMap { engineEvents(content, it, author, context) }

/**
 * The glossary (glossaire.json, D-048): first the checks of the glossary as a whole ([GlossaryFormat]: a form
 * owned by one entry only, links to known entries and contents), then each entry by the engine.
 */
internal fun glossaryEvents(content: Path, author: PublicKey, context: CheckContext): List<UnsignedEvent> {
    val schema = ComprendreSchemas.GLOSSAIRE
    val files = documents(content.resolve(schema.sourceDirectory))
    val sources = files.map { document ->
        GlossarySource(
            file = document.name,
            slug = document.require("slug"),
            title = document.require("title"),
            summary = document.metadata["summary"],
            variants = GlossaryFormat.list(document.metadata["variants"]),
            seeAlso = GlossaryFormat.list(document.metadata["see_also"]),
            related = GlossaryFormat.list(document.metadata["related"]),
            auto = document.metadata["auto"]?.trim(),
            body = document.body,
        )
    }
    val known = GlossaryFormat.RELATED_KINDS.mapValues { (_, folder) -> documents(content.resolve(folder)).mapNotNull { it.metadata["slug"] }.toSet() }
    val problems = GlossaryFormat.problems(sources, known)
    require(problems.isEmpty()) { "glossaire: invalid entries:\n  " + problems.joinToString("\n  ") }
    return files.map { engineEvent(schema, it, author, context) }.also { println("glossaire: ${it.size} entr(ies)") }
}

/** Directories whose files are not addressable contents of kind 30023 (or have no `type`), left out of the context. */
private val OUTSIDE_CONTEXT = setOf("agenda", "fil", "appels", "organisations")

/**
 * The type of every content of content/ by its slug, for the links of the engine: the `type` of the file,
 * or the type of the schema of its directory (the glossary has no `type`).
 */
internal fun contentContext(content: Path, authorHex: String): CheckContext {
    val byDirectory = ComprendreSchemas.all.associate { it.sourceDirectory to it.type } + ("revendications" to ArticleType.REVENDICATION)
    val types = HashMap<String, ArticleType>()
    if (content.isDirectory()) {
        val files = Files.walk(content).use { paths ->
            paths.filter { it.name.endsWith(".md") && !it.name.equals("README.md", ignoreCase = true) }.toList()
        }.sortedBy { content.relativize(it).toString().replace('\\', '/') }
        for (file in files) {
            val top = content.relativize(file).getName(0).toString()
            if (top in OUTSIDE_CONTEXT || file.parent == content) continue
            val document = parseDocument(file.name, file.readText())
            val slug = document.metadata["slug"] ?: continue
            val type = document.metadata["type"]?.let(ArticleType::fromTag) ?: byDirectory[top] ?: continue
            types[slug] = type
        }
    }
    return CheckContext.of(authorHex, types)
}
