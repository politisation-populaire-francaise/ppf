package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

/*
 * The building blocks of "Voir" (voir.json, D-034), checked and built by the editorial engine of shared/core
 * (D-092) with the schemas of core/voir/VoirSchemas.kt: the application must read each block without dropping
 * any line, every link must name a known content of the right type. The no-regression test (NoRegressionTest)
 * keeps the events identical to those signed before the engine.
 */

/** The Markdown files under `voir/`, sub-directories included, in a stable order (named by their relative path). */
private fun voirDocuments(directory: Path): List<Document> {
    if (!directory.isDirectory()) return emptyList()
    val files = Files.walk(directory).use { paths ->
        paths.filter { it.name.endsWith(".md") && !it.name.equals("README.md", ignoreCase = true) }.toList()
    }.sortedBy { directory.relativize(it).toString().replace('\\', '/') }
    return files.map { file -> parseDocument(directory.relativize(file).toString().replace('\\', '/'), file.readText()) }
}

/**
 * Every block of `content/voir`: the sub-directories only sort the files, the `type` of each file chooses its
 * schema (content/README.md). Refuses a type that does not belong to Voir and a slug used twice.
 */
internal fun voirEvents(content: Path, author: PublicKey, context: CheckContext): List<UnsignedEvent> {
    val documents = voirDocuments(content.resolve("voir"))
    val duplicates = documents.groupBy { it.require("slug") }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "voir: the same slug is used twice: ${duplicates.joinToString()}" }
    val events = documents.map { document ->
        val type = ArticleType.fromTag(document.require("type")) ?: error("${document.name}: unknown type")
        val schema = EditorialSchemas.of(type)?.takeIf { it.space == EditorialSpace.VOIR }
            ?: throw IllegalArgumentException("${document.name}: type ${type.tagValue} does not belong to Voir")
        engineEvent(schema, document, author, context)
    }
    val byType = documents.groupingBy { it.require("type") }.eachCount().entries.sortedBy { it.key }.joinToString { "${it.value} ${it.key}" }
    println("voir: ${events.size} block(s) ($byType), author ${author.hex}")
    return events
}
