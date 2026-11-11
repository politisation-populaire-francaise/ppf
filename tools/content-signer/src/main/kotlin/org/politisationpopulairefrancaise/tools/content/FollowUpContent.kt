package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.followups.FollowUpSchema
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.FactKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** The bundle of the follow-ups of the mobilizations of "Agir" (AG-62), next to agenda.json. */
internal const val FOLLOW_UPS_BUNDLE: String = FollowUpSchema.BUNDLE

/**
 * The follow-ups block of the signer (AG-62 « Et après ? », docs/integration/agir-et-apres.md): every Markdown
 * file of `content/suites` becomes a follow-up of a mobilization (NIP-23, `ppf-type` = `suite`), checked and built
 * by the editorial engine with [FollowUpSchema.SUITE], written to `suites.json`.
 *
 * Kept outside [prepareBundles], like the agenda and the solidarity: the no-regression test of the bundles is not
 * touched until `suites.json` is first signed. In content/suites, a link names a file of content/ by its slug
 * with a prefix, and the tool writes the full address under its own key (the key that signs the agenda and the
 * other examples): `mobilization: agenda:<slug>`, `demand: revendication:<slug>`, `call: appel:<slug>`,
 * `chronology: voir:<slug>` (a fact of the chronology) and `person: voir:<slug>` (a public person). The tool refuses
 * a link to a slug that does not exist or of the wrong kind, a follow-up dated before a mobilization it follows,
 * a title without [EXEMPLE] (the agenda keeps examples only, so their follow-ups are examples too) and a source
 * outside the domains reserved for examples.
 */
internal fun signFollowUps(content: Path, output: Path, keys: IdentityKeys) {
    val signer = LocalEventSigner(keys)
    write(output.resolve(FOLLOW_UPS_BUNDLE), followUpEvents(content, keys.publicKey).map { signer.signChecked(it) })
}

/** What a link of content/suites may name: its prefix, the kind of the address written, the slugs that exist. */
private class LinkTarget(val prefix: String, val kind: Int, val description: String, val known: Set<String>)

/** The follow-ups of content/suites, checked and unsigned: building them needs only the public key of the author. */
internal fun followUpEvents(content: Path, author: PublicKey): List<UnsignedEvent> {
    val documents = markdownFiles(content.resolve(FollowUpSchema.SOURCE_DIRECTORY)).map { parseDocument(it.name, it.readText()) }
    if (documents.isEmpty()) return emptyList()
    val duplicates = documents.groupBy { it.require("slug") }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "suites: the same slug is used twice: ${duplicates.joinToString()}" }

    // The start date (Paris time, AAAA-MM-JJ) of each mobilization of content/agenda, by slug.
    val agenda = markdownFiles(content.resolve("agenda")).map { parseDocument(it.name, it.readText()) }
        .associate { it.require("slug") to it.require("start").substringBefore('T') }
    val voir = voirFiles(content.resolve("voir")).map { parseDocument(it.name, it.readText()) }
    val chronology = voir.filter { it.metadata["type"] == "fait" && FactKind.fromTag(it.metadata["fact"])?.inChronology == true }.mapNotNull { it.metadata["slug"] }.toSet()
    val persons = voir.filter { it.metadata["type"] == "personne" }.mapNotNull { it.metadata["slug"] }.toSet()
    val targets = mapOf(
        FollowUpSchema.KEY_MOBILIZATION to LinkTarget("agenda:", Mobilization.KIND, "mobilization of content/agenda", agenda.keys),
        FollowUpSchema.KEY_DEMAND to LinkTarget("revendication:", Article.KIND, "demand of content/revendications", slugsOf(content.resolve("revendications"))),
        FollowUpSchema.KEY_CALL to LinkTarget("appel:", Article.KIND, "call of content/appels", slugsOf(content.resolve("appels"))),
        FollowUpSchema.KEY_CHRONOLOGY to LinkTarget("voir:", Article.KIND, "fact of the chronology in content/voir", chronology),
        FollowUpSchema.KEY_PERSON to LinkTarget("voir:", Article.KIND, "public person in content/voir", persons),
    )
    val context = contentContext(content, author.hex)

    val events = documents.map { document ->
        require(document.require("title").trimStart().startsWith(Mobilization.EXAMPLE_MARKER)) {
            "${document.name}: the agenda holds examples only, so a follow-up is an example too: its title starts with [EXEMPLE]"
        }
        val entries = document.entries.map { (key, value) ->
            val target = targets[key] ?: return@map key to value
            key to resolve(document, key, value, target, author)
        }
        val date = document.require(FollowUpSchema.KEY_DATE)
        document.entries.filter { it.first == FollowUpSchema.KEY_MOBILIZATION }.forEach { (_, value) ->
            val start = agenda[value.trim().removePrefix("agenda:")]
            require(start == null || date >= start) { "${document.name}: the follow-up ($date) is dated before the mobilization it follows ($start)" }
        }
        document.entries.filter { it.first == FollowUpSchema.KEY_SOURCE }.forEach { (_, value) ->
            val url = value.split('|').getOrNull(2)?.trim().orEmpty()
            require(url.isEmpty() || isExampleAddress(url)) {
                "${document.name}: a source of an example uses a domain reserved for examples (example.org, .invalid), not $url"
            }
        }
        engineEvent(FollowUpSchema.SUITE, Document(document.name, entries, document.body), author, context)
    }
    println("suites: ${events.size} follow-up(s), author ${author.hex}")
    return events
}

/** « agenda:<slug> » becomes `31923:<author>:<slug>`, « voir:<slug> » `30023:<author>:<slug>`, once the slug is known. */
private fun resolve(document: Document, key: String, value: String, target: LinkTarget, author: PublicKey): String {
    val text = value.trim()
    require(text.startsWith(target.prefix)) {
        "${document.name}: in content/suites, $key is written ${target.prefix}<slug> (the tool writes the address), not \"$text\""
    }
    val slug = text.removePrefix(target.prefix).trim()
    require(slug in target.known) { "${document.name}: $key names no ${target.description}: $slug" }
    return "${target.kind}:${author.hex}:$slug"
}

/** The hosts reserved for examples and documentation (RFC 2606): example.org, example.com, example.net, and .invalid. */
private fun isExampleAddress(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
    return listOf("example.org", "example.com", "example.net").any { host == it || host.endsWith(".$it") } || host.endsWith(".invalid")
}

/** The slugs of the Markdown files of [directory]. */
private fun slugsOf(directory: Path): Set<String> = markdownFiles(directory).mapNotNull { parseDocument(it.name, it.readText()).metadata["slug"] }.toSet()

/** The Markdown files of [directory], README aside, in the order of their names. */
private fun markdownFiles(directory: Path): List<Path> {
    if (!directory.isDirectory()) return emptyList()
    return directory.listDirectoryEntries("*.md").filter { !it.name.equals("README.md", ignoreCase = true) }.sortedBy { it.name }
}

/** The Markdown files under content/voir, sub-directories included, in a stable order. */
private fun voirFiles(directory: Path): List<Path> {
    if (!directory.isDirectory()) return emptyList()
    return Files.walk(directory).use { paths ->
        paths.filter { it.name.endsWith(".md") && !it.name.equals("README.md", ignoreCase = true) }.toList()
    }.sortedBy { directory.relativize(it).toString().replace('\\', '/') }
}
