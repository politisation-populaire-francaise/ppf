package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.decider.DeciderTags
import org.politisationpopulairefrancaise.core.decider.Pacte
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The "Décider" block of the signer: the Markdown files under `content/decider` (petitions,
 * pacts, answers of the candidates) become NIP-23 blocks written to `decider.json`. As for
 * "Voir", every front matter key other than the common ones becomes a `ppf-<key>` tag whose
 * fields are separated by `|`; `text`, `person`, `candidate` and `pacte` name another block by
 * its slug and become `a` tags; `next` is "label | voir:<slug>", "label | decider:<slug>",
 * "label | fiche:<slug>" or "label | espace:<id>".
 *
 * An answer never states the fingerprint of the text it answers by hand: the tool computes it
 * from the pact it names, so that an answer can only be recorded against the exact text sent.
 * Every block is then read back as the application reads it, and the tool fails on a block the
 * application would leave out (a petition outside its official site, a pact without its
 * deadline), on a link to an unknown slug, or on a candidate without a sheet in "Voir".
 */
internal fun signDecider(content: Path, output: Path, keys: IdentityKeys, voirEvents: List<UnsignedEvent>) {
    val directory = content.resolve("decider")
    val documents = if (!directory.isDirectory()) emptyList() else Files.walk(directory).use { paths ->
        paths.filter { it.name.endsWith(".md") && !it.name.equals("README.md", ignoreCase = true) }.toList()
    }.sortedBy { directory.relativize(it).toString().replace('\\', '/') }
        .map { file -> parseDocument(directory.relativize(file).toString().replace('\\', '/'), file.readText()) }
    val author = keys.publicKey
    val bySlug = documents.associateBy { it.require("slug") }
    check(bySlug.size == documents.size) { "decider: the same slug is used twice" }

    // The pacts first: the answers need the fingerprint of their text.
    val pacts = documents.filter { it.require("type") == ArticleType.PACTE.tagValue }.associate { document ->
        val event = deciderEvent(document, author, emptyList())
        val pacte = Pacte.from(readBack(event, document)) ?: error("${document.name}: the application would not read this pact (check election, status, sending day, deadline, 1 to 10 measures)")
        document.require("slug") to (event to pacte)
    }
    val events = documents.map { document ->
        when (document.require("type")) {
            ArticleType.PACTE.tagValue -> pacts.getValue(document.require("slug")).first
            ArticleType.ENGAGEMENT.tagValue -> {
                val pacteSlug = document.require("pacte")
                val pacte = pacts[pacteSlug]?.second ?: error("${document.name}: unknown pact $pacteSlug")
                deciderEvent(document, author, listOf(listOf(DeciderTags.PACTE_TEXT, pacte.fingerprint)))
            }
            else -> deciderEvent(document, author, emptyList())
        }.also { println("prepared decider/${document.name}") }
    }

    val articles = events.zip(documents).map { (event, document) -> readBack(event, document) }
    val catalog = DeciderCatalog.from(articles)
    val voirArticles = voirEvents.mapNotNull { Article.from(unchecked(it)) }
    val voir = VoirCatalog.from(voirArticles)
    val known = (articles + voirArticles).map { it.address.toString() }.toSet()
    val bundledGuides = setOf("droits-en-manifestation", "filmer-les-forces-de-l-ordre", "garde-a-vue")
    articles.zip(documents).forEach { (article, document) ->
        val readable = catalog.petition(article.address) != null || catalog.pacte(article.address) != null ||
            catalog.engagements.any { it.address == article.address } ||
            catalog.comparators.any { it.address == article.address } || catalog.positions.any { it.article.address == article.address } ||
            catalog.elections.any { it.address == article.address } || catalog.consultation(article.address) != null ||
            catalog.letter(article.address) != null || catalog.localPetition(article.address) != null
        check(readable) { "${document.name}: the application would not read this block (check its fields; a petition needs its official site)" }
        article.event.tags.filter { it.size >= 4 && it[0] == VoirTags.LINK }.forEach { tag ->
            check(tag[1] in known) { "${document.name}: link to an unknown slug: ${tag[1].substringAfterLast(':')}" }
        }
        article.event.tags.filter { it.size >= 3 && it[0] == VoirTags.NEXT && !it[2].startsWith("space:") }.forEach { tag ->
            val slug = tag[2].substringAfterLast(':')
            check(tag[2] in known || slug in bundledGuides) { "${document.name}: next step to an unknown slug: $slug" }
        }
    }
    // Every candidate has one sheet in "Voir", and every answer comes from a candidate of its pact.
    catalog.pactes.forEach { pacte ->
        pacte.candidates.forEach { candidate ->
            checkNotNull(voir.person(candidate)) { "${pacte.article.slug}: candidate without a sheet in Voir: ${candidate.identifier}" }
        }
    }
    // The comparators (DE-16): every candidate has one sheet in "Voir", every position fits a comparator.
    catalog.comparators.forEach { sheet ->
        sheet.candidates.forEach { candidate ->
            checkNotNull(voir.person(candidate)) { "${sheet.article.slug}: candidate without a sheet in Voir: ${candidate.identifier}" }
        }
        // The published rule of entry and the freeze (owner's decisions Q1 B and Q4 A): never signed when broken.
        val problems = sheet.ruleProblems()
        check(problems.isEmpty()) { "${sheet.article.slug}: " + problems.joinToString("; ") }
        val grid = catalog.grid(sheet, voir)
        grid.aside.forEach { (position, reason) ->
            println("decider: ${position.article.slug} is not shown in ${sheet.article.slug} ($reason)")
        }
    }
    // The elections of the calendar (DE-28): the dates come from the sources; the tool only says what looks odd.
    catalog.elections.forEach { election ->
        election.warnings().forEach { println("decider: ${election.article.slug}: $it") }
    }
    // The official consultations (DE-27): the dates come from the official notice; the tool only says what looks odd.
    catalog.consultations.forEach { consultation ->
        consultation.warnings().forEach { println("decider: ${consultation.article.slug}: $it") }
    }
    // The model letters (DE-09): a mark the application does not fill, a recipient without its official contact.
    catalog.letters.forEach { letter ->
        letter.warnings().forEach { println("decider: ${letter.article.slug}: $it") }
    }
    // The local petitions (DE-26): the counts come from the organizers; the tool only says what looks odd.
    catalog.localPetitions.forEach { petition ->
        petition.warnings().forEach { println("decider: ${petition.article.slug}: $it") }
    }
    catalog.engagements.forEach { engagement ->
        val pacte = catalog.pacte(engagement.pacte) ?: error("${engagement.article.slug}: unknown pact")
        check(engagement.candidate in pacte.candidates) { "${engagement.article.slug}: ${engagement.candidate.identifier} is not a candidate of ${pacte.article.slug}" }
        engagement.answers.forEach { answer ->
            checkNotNull(pacte.measure(answer.measureId)) { "${engagement.article.slug}: answer to an unknown measure: ${answer.measureId}" }
        }
        check(engagement.receivedOn >= (pacte.sentOn ?: engagement.receivedOn)) { "${engagement.article.slug}: received before the pact was sent" }
    }

    val signer = LocalEventSigner(keys)
    write(output.resolve("decider.json"), events.map { signer.signChecked(it) })
    println(
        "decider: ${catalog.petitions.size} petition(s), ${catalog.pactes.size} pact(s), ${catalog.engagements.size} answer(s), " +
            "${catalog.comparators.size} comparator(s), ${catalog.positions.size} position(s), ${catalog.elections.size} election(s), " +
            "${catalog.consultations.size} consultation(s), ${catalog.letters.size} letter(s), ${catalog.localPetitions.size} local petition(s)",
    )
}

/** Keys of a "Décider" front matter that are not turned into `ppf-` tags. */
private val DECIDER_COMMON_KEYS = setOf("slug", "title", "summary", "topics", "type", "origin", "published_at", "updated_at")

private val DECIDER_LINK_KEYS = mapOf(
    "text" to VoirTags.ROLE_TEXT,
    "person" to VoirTags.ROLE_PERSON,
    "candidate" to DeciderTags.ROLE_CANDIDATE,
    "pacte" to DeciderTags.ROLE_PACTE,
    // An official petition filed by the team for a demand of the wall (D-115).
    "revendication" to DeciderTags.ROLE_DEMAND,
)

private fun deciderEvent(document: Document, author: PublicKey, computed: List<List<String>>): UnsignedEvent {
    val type = ArticleType.fromTag(document.require("type")) ?: error("${document.name}: unknown type")
    require(type in DeciderCatalog.TYPES) { "${document.name}: type ${type.tagValue} does not belong to Décider" }
    fun address(slug: String) = "${Article.KIND}:${author.hex}:${slug.trim()}"
    val extra = document.entries.filter { (key, _) -> key !in DECIDER_COMMON_KEYS }.map { (key, value) ->
        val fields = value.split('|').map { it.trim() }
        when {
            key in DECIDER_LINK_KEYS -> listOf(VoirTags.LINK, address(value), "", DECIDER_LINK_KEYS.getValue(key))
            key == "next" -> {
                require(fields.size == 2) { "${document.name}: next is written \"label | target\"" }
                val target = fields[1]
                val resolved = when {
                    target.startsWith("fiche:") -> address(target.removePrefix("fiche:"))
                    target.startsWith("voir:") -> address(target.removePrefix("voir:"))
                    target.startsWith("decider:") -> address(target.removePrefix("decider:"))
                    target.startsWith("espace:") -> "space:" + target.removePrefix("espace:")
                    else -> error("${document.name}: unknown next target $target")
                }
                listOf(VoirTags.NEXT, fields[0], resolved)
            }
            key == "pacte-text" -> error("${document.name}: the fingerprint of the text is computed by the tool, do not write it")
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
        extraTags = extra + computed,
    )
}

/** Reads an event back without its signature (the models do not check it). */
private fun unchecked(unsigned: UnsignedEvent): SignedEvent =
    SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))

private fun readBack(unsigned: UnsignedEvent, document: Document): Article =
    Article.from(unchecked(unsigned)) ?: error("${document.name}: not a readable article")
