package org.politisationpopulairefrancaise.tools.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess

/**
 * Signs the editorial contents of the `content/` directory into events bundled with the app:
 * the Markdown files of `fiches` become NIP-23 articles (fiches.json), those of `revendications`
 * become demands (revendications.json), those of `fil` become short notes for the feed (fil.json).
 * See content/README.md for the front matter of each kind.
 * The signing key is read from the file named by PPF_CONTENT_KEY_FILE (default
 * ~/.ppf/content-example.nsec); it is created if missing. Dates come from the front matter, so
 * re-running on unchanged files produces identical events.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: content-signer <content directory> <output directory>")
        exitProcess(2)
    }
    val content = Path.of(args[0])
    val output = Path.of(args[1])
    val keys = loadOrCreateKeys()
    val signer = LocalEventSigner(keys)
    Files.createDirectories(output)

    val articles = signAll(content.resolve("fiches")) { document ->
        Article.build(
            author = keys.publicKey,
            slug = document.require("slug"),
            title = document.require("title"),
            summary = document.require("summary"),
            topics = document.topics(),
            type = ArticleType.fromTag(document.require("type")) ?: error("${document.name}: unknown type"),
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            body = document.body,
        )
    }.map { signer.signChecked(it) }
    write(output.resolve("fiches.json"), articles)

    val demands = signAll(content.resolve("revendications")) { document ->
        Article.build(
            author = keys.publicKey,
            slug = document.require("slug"),
            title = document.require("title"),
            summary = document.metadata["summary"],
            topics = document.topics(),
            type = ArticleType.REVENDICATION,
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            body = document.body,
            status = RevendicationStatus.fromTag(document.require("status")) ?: error("${document.name}: unknown status"),
            territory = document.metadata["territory"],
        )
    }.map { signer.signChecked(it) }
    write(output.resolve("revendications.json"), demands)

    val notes = signAll(content.resolve("fil")) { document ->
        UnsignedEvent.create(
            author = keys.publicKey,
            kind = EventKinds.TEXT_NOTE,
            createdAt = document.require("created_at").dateTimeToEpochSeconds(),
            tags = document.topics().map { listOf("t", it) },
            content = document.body.trim(),
        )
    }.map { signer.signChecked(it) }
    write(output.resolve("fil.json"), notes)

    println("author public key (hex): ${keys.publicKey.hex}")
    println("author public key (npub): ${keys.publicKey.npub}")
}

private fun signAll(directory: Path, build: (Document) -> UnsignedEvent): List<UnsignedEvent> {
    if (!directory.isDirectory()) return emptyList()
    return directory.listDirectoryEntries("*.md").sortedBy { it.name }.map { file ->
        val document = parseDocument(file.name, file.readText())
        build(document).also { println("prepared ${file.name}") }
    }
}

private fun LocalEventSigner.signChecked(unsigned: UnsignedEvent): SignedEvent {
    val event = runBlocking { sign(unsigned) }
    check(event.verify()) { "signature check failed for ${event.id}" }
    return event
}

private fun write(file: Path, events: List<SignedEvent>) {
    val json = Json { prettyPrint = true }
    Files.writeString(file, json.encodeToString(ListSerializer(SignedEvent.serializer()), events) + "\n")
    println("${events.size} event(s) written to $file")
}

private fun loadOrCreateKeys(): IdentityKeys {
    val path = System.getenv("PPF_CONTENT_KEY_FILE")?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".ppf", "content-example.nsec")
    if (path.exists()) return IdentityKeys.fromNsec(path.readText().trim())
    val keys = IdentityKeys.generate()
    path.parent?.let(Files::createDirectories)
    Files.writeString(path, keys.nsec + "\n")
    println("new signing key created in $path (keep it out of the repository)")
    return keys
}

private class Document(val name: String, val metadata: Map<String, String>, val body: String) {
    fun require(key: String): String = metadata[key] ?: error("$name: missing front matter field: $key")
    fun topics(): List<String> = metadata["topics"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

private fun parseDocument(name: String, text: String): Document {
    val lines = text.lines()
    require(lines.firstOrNull()?.trim() == "---") { "$name: the file must start with a --- front matter" }
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    require(end >= 0) { "$name: unterminated front matter" }
    val metadata = lines.subList(1, 1 + end).filter { it.isNotBlank() }.associate { line ->
        val separator = line.indexOf(':')
        require(separator > 0) { "$name: invalid front matter line: $line" }
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    val body = lines.drop(2 + end).joinToString("\n").trim() + "\n"
    return Document(name, metadata, body)
}

/** Dates are taken at noon UTC so that the event id does not depend on the machine's time zone. */
private fun String.dateToEpochSeconds(): Long = LocalDate.parse(this).atTime(LocalTime.NOON).toEpochSecond(ZoneOffset.UTC)

/** Date and time, e.g. 2026-11-05T18:00, read as UTC for the same reason. */
private fun String.dateTimeToEpochSeconds(): Long = LocalDateTime.parse(this).toEpochSecond(ZoneOffset.UTC)
