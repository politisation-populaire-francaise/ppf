package org.politisationpopulairefrancaise.tools.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ParcoursFormat
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
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
 * become demands (revendications.json), those of `parcours` become the courses of "Comprendre en
 * 5 minutes" (parcours.json, refused if a step, a quiz, the « Et maintenant ? » block or the
 * sources are missing or malformed), those of `fil` become short notes for the feed (fil.json),
 * those of `voir` (and its sub-directories) become the building blocks of "Voir" (voir.json, D-034),
 * and those of `organisations` become entries of the directory of organisations (organisations.json).
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

    val courses = signAll(content.resolve("parcours")) { document ->
        val problems = ParcoursFormat.parse(document.body).problems
        require(problems.isEmpty()) { "${document.name}: invalid course:\n  " + problems.joinToString("\n  ") }
        Article.build(
            author = keys.publicKey,
            slug = document.require("slug"),
            title = document.require("title"),
            summary = document.require("summary"),
            topics = document.topics(),
            type = ArticleType.PARCOURS,
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            body = document.body,
            durationMinutes = document.require("duration").toIntOrNull() ?: error("${document.name}: duration must be a number of minutes"),
        )
    }.map { signer.signChecked(it) }
    write(output.resolve("parcours.json"), courses)

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
    signAgenda(content, output, keys) // content/agenda -> agenda.json (AgendaContent.kt)

    val voirFiles = voirDocuments(content.resolve("voir"))
    val voir = voirFiles.map { document -> voirEvent(document, keys.publicKey) }
    checkVoir(voirFiles, voir, keys.publicKey.hex)
    write(output.resolve("voir.json"), voir.map { signer.signChecked(it) })
    // Directory of organisations (contract of the Agir space, section 4): organisations.json.
    val organisations = signAll(content.resolve("organisations")) { document ->
        Organisation.build(
            editor = keys.publicKey,
            slug = document.require("slug"),
            name = document.require("title"),
            summary = document.require("summary"),
            type = document.require("org_type"),
            territory = document.require("territory"),
            verifiedOn = document.require("verified"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            presentation = document.body,
            accountKey = document.metadata["pubkey"],
            websites = document.metadata["websites"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            phone = document.metadata["phone"],
            email = document.metadata["email"],
            postalAddress = document.metadata["address"],
            geohash = document.metadata["geohash"],
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
        )
    }.map { signer.signChecked(it) }
    write(output.resolve("organisations.json"), organisations)

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

private class Document(val name: String, val entries: List<Pair<String, String>>, val body: String) {
    /** The last value of each key; repeated keys (measures, sources...) are read through [entries]. */
    val metadata: Map<String, String> = entries.toMap()
    fun require(key: String): String = metadata[key] ?: error("$name: missing front matter field: $key")
    fun topics(): List<String> = metadata["topics"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

private fun parseDocument(name: String, text: String): Document {
    val lines = text.lines()
    require(lines.firstOrNull()?.trim() == "---") { "$name: the file must start with a --- front matter" }
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    require(end >= 0) { "$name: unterminated front matter" }
    // Lines starting with # are comments for the editors.
    val entries = lines.subList(1, 1 + end).filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.map { line ->
        val separator = line.indexOf(':')
        require(separator > 0) { "$name: invalid front matter line: $line" }
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    val body = lines.drop(2 + end).joinToString("\n").trim() + "\n"
    return Document(name, entries, body)
}

/** Dates are taken at noon UTC so that the event id does not depend on the machine's time zone. */
private fun String.dateToEpochSeconds(): Long = LocalDate.parse(this).atTime(LocalTime.NOON).toEpochSecond(ZoneOffset.UTC)

/** Date and time, e.g. 2026-11-05T18:00, read as UTC for the same reason. */
private fun String.dateTimeToEpochSeconds(): Long = LocalDateTime.parse(this).toEpochSecond(ZoneOffset.UTC)

/** The Markdown files under `voir/`, sub-directories included, in a stable order. */
private fun voirDocuments(directory: Path): List<Document> {
    if (!directory.isDirectory()) return emptyList()
    val files = Files.walk(directory).use { paths ->
        paths.filter { it.name.endsWith(".md") && !it.name.equals("README.md", ignoreCase = true) }.toList()
    }.sortedBy { directory.relativize(it).toString().replace('\\', '/') }
    return files.map { file -> parseDocument(directory.relativize(file).toString().replace('\\', '/'), file.readText()) }
}

/** Keys of a "Voir" front matter that are not turned into `ppf-` tags. */
private val COMMON_KEYS = setOf("slug", "title", "summary", "topics", "type", "origin", "published_at", "updated_at")

/** Keys that link to another block by its slug: key to the role of the `a` tag. */
private val LINK_KEYS = mapOf(
    "person" to VoirTags.ROLE_PERSON,
    "text" to VoirTags.ROLE_TEXT,
    "indicator" to VoirTags.ROLE_INDICATOR,
    "target" to VoirTags.ROLE_TARGET,
)

/**
 * A "Voir" block: the common fields as for any article, then every other key of the front matter
 * as a tag `ppf-<key>` whose fields are separated by `|` (repeat the key for several tags).
 * `person`, `text`, `indicator` and `target` name another block by its slug and become `a` tags;
 * `next` is "label | fiche:<slug>", "label | voir:<slug>" or "label | espace:<id>".
 */
private fun voirEvent(document: Document, author: PublicKey): UnsignedEvent {
    val type = ArticleType.fromTag(document.require("type")) ?: error("${document.name}: unknown type")
    require(type in VoirCatalog.TYPES) { "${document.name}: type ${type.tagValue} does not belong to Voir" }
    fun address(slug: String) = "${Article.KIND}:${author.hex}:${slug.trim()}"
    val extra = document.entries.filter { (key, _) -> key !in COMMON_KEYS }.map { (key, value) ->
        // Fields are separated by |; an empty field (two | in a row, a trailing |) means unknown.
        val fields = value.split('|').map { it.trim() }
        when {
            key in LINK_KEYS -> listOf(VoirTags.LINK, address(value), "", LINK_KEYS.getValue(key))
            key == "next" -> {
                require(fields.size == 2) { "${document.name}: next is written \"label | target\"" }
                val target = fields[1]
                val resolved = when {
                    target.startsWith("fiche:") -> address(target.removePrefix("fiche:"))
                    target.startsWith("voir:") -> address(target.removePrefix("voir:"))
                    target.startsWith("espace:") -> "space:" + target.removePrefix("espace:")
                    else -> error("${document.name}: unknown next target $target")
                }
                listOf(VoirTags.NEXT, fields[0], resolved)
            }
            else -> listOf("ppf-$key") + fields
        }
    }
    return Article.build(
        author = author,
        slug = document.require("slug"),
        title = document.require("title"),
        summary = document.metadata["summary"],
        topics = document.topics(),
        type = type,
        origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
        publishedAt = document.require("published_at").dateToEpochSeconds(),
        updatedAt = document.require("updated_at").dateToEpochSeconds(),
        body = document.body,
        extraTags = extra,
    )
}

/**
 * Reads every block back as the application will, and fails on the first one it would leave out
 * (a typo in a status, a missing unit) or on a link to a slug that does not exist.
 */
private fun checkVoir(documents: List<Document>, events: List<UnsignedEvent>, authorHex: String) {
    val articles = events.zip(documents).map { (unsigned, document) ->
        val unchecked = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        Article.from(unchecked) ?: error("${document.name}: not a readable article")
    }
    val duplicates = articles.groupBy { it.address }.filterValues { it.size > 1 }.keys
    check(duplicates.isEmpty()) { "voir: the same slug is used twice: ${duplicates.joinToString { it.identifier }}" }
    val catalog = VoirCatalog.from(articles)
    val known = articles.map { it.address.toString() }.toSet()
    val bundledGuides = setOf("droits-en-manifestation", "filmer-les-forces-de-l-ordre", "garde-a-vue")
    articles.zip(documents).forEach { (article, document) ->
        val readable = catalog.item(article.address) != null || catalog.replies.any { it.article.address == article.address }
        check(readable) { "${document.name}: the application would not read this block (check its fields)" }
        article.event.tags.filter { it.size >= 4 && it[0] == VoirTags.LINK }.forEach { tag ->
            check(tag[1] in known) { "${document.name}: link to an unknown slug: ${tag[1].substringAfterLast(':')}" }
        }
        article.event.tags.filter { it.size >= 3 && it[0] == VoirTags.NEXT && !it[2].startsWith("space:") }.forEach { tag ->
            val slug = tag[2].substringAfterLast(':')
            check(tag[2] in known || slug in bundledGuides) { "${document.name}: next step to an unknown slug: $slug" }
        }
    }
    println(
        "voir: ${catalog.indicators.size} indicator(s), ${catalog.facts.size} fact(s), ${catalog.persons.size} person(s), " +
            "${catalog.texts.size} bill(s), ${catalog.movements.size} movement(s), ${catalog.dossiers.size} dossier(s), " +
            "${catalog.replies.size} reply(ies), author $authorHex",
    )
}
