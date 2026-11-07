package org.politisationpopulairefrancaise.tools.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.comprendre.ComprendreTags
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.ParcoursFormat
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
import org.politisationpopulairefrancaise.core.comprendre.QuiDecide
import org.politisationpopulairefrancaise.core.comprendre.Verdict
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.Indicator
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
 * those of `organisations` become entries of the directory of organisations (organisations.json),
 * and those of `appels` become calls of the Agir space (appels.json).
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

    write(output.resolve("comprendre.json"), comprendreEvents(content, keys.publicKey).map { signer.signChecked(it) })

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
    "party" to VoirTags.ROLE_PARTY,
    "leader" to VoirTags.ROLE_LEADER,
)

/**
 * A "Voir" block: the common fields as for any article, then every other key of the front matter
 * as a tag `ppf-<key>` whose fields are separated by `|` (repeat the key for several tags).
 * `person`, `text`, `indicator`, `target`, `party` and `leader` name another block by its slug and become `a` tags;
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
            val linked = ContentAddress.parse(tag[1])
            when (tag[3]) {
                VoirTags.ROLE_PARTY -> check(catalog.party(linked) != null) { "${document.name}: party: ${linked.identifier} is not a party" }
                VoirTags.ROLE_LEADER, VoirTags.ROLE_PERSON -> check(catalog.person(linked) != null) { "${document.name}: ${tag[3]}: ${linked.identifier} is not a person" }
            }
        }
        if (article.type == ArticleType.PARTI) checkParty(article, document.name)
        article.event.tags.filter { it.size >= 3 && it[0] == VoirTags.NEXT && !it[2].startsWith("space:") }.forEach { tag ->
            val slug = tag[2].substringAfterLast(':')
            check(tag[2] in known || slug in bundledGuides) { "${document.name}: next step to an unknown slug: $slug" }
        }
    }
    println(
        "voir: ${catalog.indicators.size} indicator(s), ${catalog.facts.size} fact(s), ${catalog.persons.size} person(s), " +
            "${catalog.texts.size} bill(s), ${catalog.movements.size} movement(s), ${catalog.dossiers.size} dossier(s), " +
            "${catalog.replies.size} reply(ies), ${catalog.parties.size} party(ies), author $authorHex",
    )
}

/**
 * A party is refused when a line would be silently dropped by the application: no financing
 * structure or more than one, a financial agent with a name (a private person: never stored), an
 * unreadable count of seats or amount of public aid, the same year of aid twice.
 */
private fun checkParty(article: Article, name: String) {
    val event = article.event
    val financing = event.tags.filter { it.size >= 2 && it[0] == VoirTags.FINANCING }
    check(financing.size == 1) { "$name: a party has exactly one financing (association-de-financement or mandataire-financier)" }
    val f = financing.single()
    check(f[1] in setOf("association-de-financement", "mandataire-financier")) { "$name: unknown financing kind ${f[1]}" }
    check(f[1] != "mandataire-financier" || f.getOrNull(2).isNullOrBlank()) { "$name: never write the name or reference of a financial agent (a private person)" }
    event.tags.filter { it.size >= 2 && it[0] == VoirTags.SEATS }.forEach { t ->
        check(t.getOrNull(1) in setOf("assemblee", "senat", "parlement-europeen", "autre") && t.getOrNull(2)?.toIntOrNull()?.let { it >= 0 } == true) { "$name: unreadable seats: ${t.drop(1)}" }
    }
    val years = event.tags.filter { it.size >= 2 && it[0] == VoirTags.PUBLIC_FUNDING }.map { t ->
        val year = t.getOrNull(1)?.toIntOrNull()
        check(year != null && year in 1988..2100 && t.getOrNull(2)?.let(Indicator::parseNumber) != null && !t.getOrNull(3).isNullOrBlank()) {
            "$name: public-funding is written \"year | amount in euros | decree | address\": ${t.drop(1)}"
        }
        year
    }
    check(years.size == years.toSet().size) { "$name: the same year of public funding is written twice" }
}

/** Date and time of a call, e.g. 2026-11-05T18:00, read in Paris time like the form ([ParisTime], independent of the machine). */
private fun String.parisToEpochSeconds(): Long {
    val time = LocalDateTime.parse(this)
    return ParisTime.toEpochSeconds(ParisDateTime(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute))
}

/**
 * The contents of "Comprendre" beyond the courses (comprendre.json): fact-checks (`decryptages`),
 * stories of the history of social movements (`histoire`), « Qui décide de ça ? » sheets
 * (`qui-decide`) and the method page (`methode`). Each kind is refused when its required fields,
 * its blocks (objections, chronology, chain of decision) or its sources are missing or malformed.
 */
private fun comprendreEvents(content: Path, author: PublicKey): List<UnsignedEvent> {
    fun common(document: Document, type: ArticleType, extraTags: List<List<String>>) = Article.build(
        author = author,
        slug = document.require("slug"),
        title = document.require("title"),
        summary = document.require("summary"),
        topics = document.topics(),
        type = type,
        origin = ContentOrigin.fromTag(document.require("origin")) ?: error("${document.name}: unknown origin"),
        publishedAt = document.require("published_at").dateToEpochSeconds(),
        updatedAt = document.require("updated_at").dateToEpochSeconds(),
        body = document.body,
        extraTags = extraTags,
    )

    fun check(document: Document, problems: List<String>) {
        val all = problems + ContentBlocks.problems(document.body) +
            if (document.body.lines().any { it.trim() == "## Sources" }) emptyList() else listOf("no \"## Sources\" section")
        require(all.isEmpty()) { "${document.name}: invalid content:\n  " + all.joinToString("\n  ") }
    }

    fun list(document: Document, key: String): List<String> = document.metadata[key].orEmpty().split(" ; ", ";").map { it.trim() }.filter { it.isNotEmpty() }

    val checks = signAll(content.resolve("decryptages")) { document ->
        val verdict = Verdict.fromTag(document.require("verdict")) ?: error("${document.name}: unknown verdict (${Verdict.entries.joinToString { it.tagValue }})")
        check(document, if (ContentBlocks.hasObjections(document.body)) emptyList() else listOf("a fact-check needs a ::: objections block (pluralism)"))
        common(
            document,
            ArticleType.DECRYPTAGE,
            listOfNotNull(
                listOf(ComprendreTags.VERDICT, verdict.tagValue),
                listOf(ComprendreTags.CLAIM, document.require("claim")),
                document.metadata["claim_context"]?.takeIf { it.isNotBlank() }?.let { listOf(ComprendreTags.CLAIM_CONTEXT, it) },
            ),
        )
    }
    val stories = signAll(content.resolve("histoire")) { document ->
        val period = document.require("period").split('|').map { it.trim() }
        require(period.isNotEmpty() && period.all { it.isEmpty() || ContentBlocks.frenchDate(it) != null } && period[0].isNotEmpty()) {
            "${document.name}: period must be \"start | end\" with AAAA or AAAA-MM-JJ"
        }
        check(document, if (ContentBlocks.hasObjections(document.body)) emptyList() else listOf("a story needs a ::: objections block (pluralism)"))
        common(
            document,
            ArticleType.HISTOIRE,
            listOf(listOf(ComprendreTags.PERIOD) + period.filter { it.isNotEmpty() }, listOf(ComprendreTags.COUNTRY, document.require("country"))) +
                list(document, "claims").map { listOf(ComprendreTags.CLAIM, it) } +
                list(document, "results").map { listOf(ComprendreTags.RESULT, it) },
        )
    }
    val sheets = signAll(content.resolve("qui-decide")) { document ->
        val (_, _, problems) = QuiDecide.parse(document.body)
        val actions = if (document.body.contains("::: et-maintenant")) emptyList() else listOf("no \"::: et-maintenant\" block (« Et maintenant ? »)")
        check(document, problems + actions)
        val persons = document.metadata["persons"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        common(document, ArticleType.QUI_DECIDE, persons.map { slug -> listOf("a", "${Article.KIND}:${author.hex}:$slug", "", ComprendreTags.PERSON_LINK_ROLE) })
    }
    val method = signAll(content.resolve("methode")) { document ->
        check(document, emptyList())
        common(document, ArticleType.ARTICLE, emptyList())
    }
    return checks + stories + sheets + method
}
