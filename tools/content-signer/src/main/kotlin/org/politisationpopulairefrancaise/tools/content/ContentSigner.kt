package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.revendications.MovementDemands
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ComprendreSchemas
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
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
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDocument
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
 * become demands (revendications.json), those of `parcours` become the courses of "Comprendre en
 * 5 minutes" (parcours.json, refused if a step, a quiz, the « Et maintenant ? » block or the
 * sources are missing or malformed), those of `fil` become short notes for the feed (fil.json),
 * those of `voir` (and its sub-directories) become the building blocks of "Voir" (voir.json, D-034),
 * those of `organisations` become entries of the directory of organisations (organisations.json),
 * those of `appels` become calls of the Agir space (appels.json), and those of `glossaire` become
 * the entries of the glossary (glossaire.json, refused if a definition, a form or the sources are
 * missing, if a form belongs to two entries or if a link names an unknown entry or content).
 * The solidarity funds and volunteer missions of `solidarite` go to solidarite.json (SolidarityContent.kt).
 * The follow-ups of the mobilizations, in `suites`, go to suites.json (FollowUpContent.kt, AG-62).
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

    val bundles = prepareBundles(content, keys.publicKey)
    bundles.forEach { bundle -> write(output.resolve(bundle.file), bundle.events.map { signer.signChecked(it) }) }
    signAgenda(content, output, keys) // content/agenda -> agenda.json (AgendaContent.kt)
    signDecider(content, output, keys, bundles.single { it.file == VOIR_BUNDLE }.events) // content/decider -> decider.json (DeciderContent.kt)
    signSolidarity(content, output, keys) // content/solidarite -> solidarite.json (SolidarityContent.kt)
    signFollowUps(content, output, keys) // content/suites -> suites.json (FollowUpContent.kt, AG-62)

    checkUniqueAddresses(output)
    println("author public key (hex): ${keys.publicKey.hex}")
    println("author public key (npub): ${keys.publicKey.npub}")
}

/** The events of one bundled JSON file, before signature, in the order of the file. */
internal class Bundle(val file: String, val events: List<UnsignedEvent>)

internal const val VOIR_BUNDLE: String = "voir.json"

/**
 * Every bundle built by this file (all but agenda.json and decider.json), checked and unsigned: building
 * an event needs only the public key of the author, so the no-regression test runs without any secret.
 */
internal fun prepareBundles(content: Path, author: PublicKey): List<Bundle> {
    // The contents of Comprendre are checked and built by the editorial engine (ComprendreContent.kt, D-092).
    val context = contentContext(content, author.hex)
    val articles = guideEvents(content, author, context)

    val demands = signAll(content.resolve("revendications")) { document ->
        Article.build(
            author = author,
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
            // A demand already carried by the movement (MVP 1): `from: mouvement` and who carries it,
            // `carried_by: <a> ; <b>`, with its sources in the body (core.revendications.MovementDemands).
            extraTags = when (document.metadata["from"]) {
                null -> emptyList()
                MovementDemands.FROM_MOVEMENT -> {
                    require(document.body.contains("## Sources")) { "${document.name}: a demand of the movement lists its sources" }
                    MovementDemands.tags(document.require("carried_by").split(" ; "))
                }
                else -> error("${document.name}: unknown from: ${document.metadata["from"]}")
            } + document.metadata["supported_by"].orEmpty().let { supporters ->
                // Any demand may name who else publicly supports it: `supported_by: <a> ; <b>` (D-152).
                if (supporters.isBlank()) {
                    emptyList()
                } else {
                    require(document.body.contains("## Sources")) { "${document.name}: the public supporters of a demand are sourced" }
                    MovementDemands.supporterTags(supporters.split(" ; "))
                }
            },
        )
    }

    val courses = engineEvents(content, ComprendreSchemas.PARCOURS, author, context)

    val comprendre = comprendreEvents(content, author, context)
    val glossary = glossaryEvents(content, author, context)

    val notes = signAll(content.resolve("fil")) { document ->
        UnsignedEvent.create(
            author = author,
            kind = EventKinds.TEXT_NOTE,
            createdAt = document.require("created_at").dateTimeToEpochSeconds(),
            tags = document.topics().map { listOf("t", it) },
            content = document.body.trim(),
        )
    }

    // The building blocks of Voir are checked and built by the editorial engine (VoirContent.kt, D-092).
    val voir = voirEvents(content, author, context)
    // Directory of organisations (contract of the Agir space, section 4): organisations.json.
    val organisations = signAll(content.resolve("organisations")) { document ->
        Organisation.build(
            editor = author,
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
    }

    val calls = signAll(content.resolve("appels")) { document -> callOf(author, document) }

    return listOf(
        Bundle("fiches.json", articles),
        Bundle("revendications.json", demands),
        Bundle("parcours.json", courses),
        Bundle("comprendre.json", comprendre),
        Bundle("glossaire.json", glossary),
        Bundle("fil.json", notes),
        Bundle(VOIR_BUNDLE, voir),
        Bundle("organisations.json", organisations),
        Bundle("appels.json", calls),
    )
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

internal fun LocalEventSigner.signChecked(unsigned: UnsignedEvent): SignedEvent {
    val event = runBlocking { sign(unsigned) }
    check(event.verify()) { "signature check failed for ${event.id}" }
    return event
}

internal fun write(file: Path, events: List<SignedEvent>) {
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

internal class Document(val name: String, val entries: List<Pair<String, String>>, val body: String) {
    /** The last value of each key; repeated keys (measures, sources...) are read through [entries]. */
    val metadata: Map<String, String> = entries.toMap()
    fun require(key: String): String = metadata[key] ?: error("$name: missing front matter field: $key")
    fun topics(): List<String> = metadata["topics"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** The common reading of a file of content/ (shared/core, ContentDocument), kept under its old name for the other spaces. */
internal fun parseDocument(name: String, text: String): Document =
    ContentDocument.parse(name, text).let { Document(it.name, it.entries, it.body) }

/** Dates are taken at noon UTC so that the event id does not depend on the machine's time zone. */
internal fun String.dateToEpochSeconds(): Long = LocalDate.parse(this).atTime(LocalTime.NOON).toEpochSecond(ZoneOffset.UTC)

/** Date and time, e.g. 2026-11-05T18:00, read as UTC for the same reason. */
private fun String.dateTimeToEpochSeconds(): Long = LocalDateTime.parse(this).toEpochSecond(ZoneOffset.UTC)

/** Date and time of a call, e.g. 2026-11-05T18:00, read in Paris time like the form ([ParisTime], independent of the machine). */
private fun String.parisToEpochSeconds(): Long {
    val time = LocalDateTime.parse(this)
    return ParisTime.toEpochSeconds(ParisDateTime(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute))
}

/**
 * Two addressable events of the same kind and author with the same `d` tag are one content for
 * every reader: the newer replaces the other on the device. Refuses such a collision between any
 * two bundled files (a glossary entry and a practical guide once shared the slug `garde-a-vue`).
 */
private fun checkUniqueAddresses(output: Path) {
    val json = Json { ignoreUnknownKeys = true }
    val owners = HashMap<String, String>()
    val collisions = mutableListOf<String>()
    output.listDirectoryEntries("*.json").sortedBy { it.name }.forEach { file ->
        json.decodeFromString(ListSerializer(SignedEvent.serializer()), file.readText()).filter { it.kind in 30000..39999 }.forEach { event ->
            val address = "${event.kind}:${event.pubkey}:${event.firstTagValue("d").orEmpty()}"
            val previous = owners.put(address, file.name)
            if (previous != null) collisions += "${address.substringAfterLast(':')} (${previous} and ${file.name})"
        }
    }
    check(collisions.isEmpty()) { "the same address is used by two contents, the newer would replace the other: " + collisions.joinToString() }
}
