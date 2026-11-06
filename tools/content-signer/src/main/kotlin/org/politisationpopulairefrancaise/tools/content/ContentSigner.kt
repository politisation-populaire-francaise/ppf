package org.politisationpopulairefrancaise.tools.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallFieldKind
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallRules
import org.politisationpopulairefrancaise.core.agir.calls.CallStatus
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.DateTimeText
import org.politisationpopulairefrancaise.core.agir.calls.ParisDateTime
import org.politisationpopulairefrancaise.core.agir.calls.ParisTime
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
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
 * become demands (revendications.json), those of `fil` become short notes for the feed (fil.json),
 * those of `appels` become calls of the Agir space (appels.json).
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

    val calls = signAll(content.resolve("appels")) { document -> callOf(keys.publicKey, document) }.map { signer.signChecked(it) }
    write(output.resolve("appels.json"), calls)

    println("author public key (hex): ${keys.publicKey.hex}")
    println("author public key (npub): ${keys.publicKey.npub}")
}

/**
 * A call of the `appels` directory (contract of the Agir space, section 3), built and checked exactly like
 * the form of the application ([CallDraft], [CallRules] at the date of publication). Dates are Paris
 * time (`2026-11-14T18:30`, or `2026-12-15` for a deadline lasting the whole day); the specific
 * fields are written under their tag name (`ppf-organizer: ...`). A closed call (`status: clos`)
 * is the closing version of the open one, with its `report`. No fundraising among the examples:
 * only verified organisations may launch one.
 */
private fun callOf(author: PublicKey, document: Document): UnsignedEvent {
    val type = CallType.fromTag(document.require("call_type")) ?: error("${document.name}: unknown call_type")
    val status = document.require("status")
    require(status in AgirFormats.CALL_STATUSES) { "${document.name}: unknown status: $status" }
    val publishedAt = document.require("published_at").parisToEpochSeconds()
    val updatedAt = document.require("updated_at").parisToEpochSeconds()
    val known = setOf("slug", "title", "summary", "call_type", "territory", "status", "report", "published_at", "updated_at", "deadline", "start", "end", "goal", "location", "r")
    val specific = CallFields.specific(type).filter { it.kind != CallFieldKind.DATE_TIME }.map { it.tag }.toSet()
    (document.metadata.keys - known - specific).firstOrNull()?.let { error("${document.name}: field not used by the type ${type.tagValue}: $it") }

    var draft = CallDraft(
        type = type,
        title = document.require("title"),
        text = document.body,
        summary = document.metadata["summary"].orEmpty(),
        territory = document.require("territory"),
        goal = document.metadata["goal"].orEmpty(),
        location = document.metadata["location"].orEmpty(),
        link = document.metadata["r"].orEmpty(),
    )
    listOf("deadline" to AgirFormats.TAG_DEADLINE, "start" to AgirFormats.TAG_START, "end" to AgirFormats.TAG_END).forEach { (key, tag) ->
        document.metadata[key]?.let { value -> draft = draft.withDate(tag, DateTimeText(value.substringBefore('T'), value.substringAfter('T', ""))) }
    }
    specific.forEach { tag -> document.metadata[tag]?.let { draft = draft.withField(tag, it) } }
    val issues = CallRules.check(draft, canFundraise = false, now = publishedAt)
    check(issues.isEmpty()) { "${document.name}: the call breaks the rules of the form: $issues" }

    val opened = draft.toUnsignedEvent(author, publishedAt, slug = document.require("slug"))
    if (status == CallStatus.OPEN.tagValue) return opened.copy(createdAt = updatedAt)
    // Read back without a signature (the model does not check it) to build the closing version.
    val call = Call.from(SignedEvent(opened.idHex(), opened.pubkey, opened.createdAt, opened.kind, opened.tags, opened.content, "0".repeat(128)))
        ?: error("${document.name}: unreadable call")
    val report = document.require("report")
    check(CallRules.checkReport(report).isEmpty()) { "${document.name}: the report breaks the rules" }
    return call.closedVersion(report, updatedAt)
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

/** Date and time of a call, e.g. 2026-11-05T18:00, read in Paris time like the form ([ParisTime], independent of the machine). */
private fun String.parisToEpochSeconds(): Long {
    val time = LocalDateTime.parse(this)
    return ParisTime.toEpochSeconds(ParisDateTime(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute))
}
