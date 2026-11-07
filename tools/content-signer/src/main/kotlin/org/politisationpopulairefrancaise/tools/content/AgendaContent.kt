package org.politisationpopulairefrancaise.tools.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.agenda.DeclarationState
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationRules
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The agenda block of the signer: every Markdown file of `content/agenda` becomes a mobilization
 * (NIP-52 calendar event, kind 31923, contract of the Agir space), written to `agenda.json` next
 * to `fiches.json`. Front matter: `slug`, `title`, `type`, `start` and optional `end`
 * (`AAAA-MM-JJTHH:MM`, local time of mainland France), `location`, `territory`, optional `status`,
 * `declaration`, `summary`, `link`, `geohash` (the point of the map view), and `created_at` (`AAAA-MM-JJTHH:MM`, UTC, so that the event
 * id never depends on the machine). The body is the description. Each event is checked with the
 * rules of the server before being written; a broken file stops the tool.
 */
internal fun signAgenda(content: Path, output: Path, keys: IdentityKeys) {
    val directory = content.resolve("agenda")
    val signer = LocalEventSigner(keys)
    val events: List<SignedEvent> = if (!directory.isDirectory()) {
        emptyList()
    } else {
        directory.listDirectoryEntries("*.md").sortedBy { it.name }.map { file ->
            val (metadata, body) = frontMatter(file.name, file.readText())
            fun require(key: String): String = metadata[key] ?: error("${file.name}: missing front matter field: $key")
            val unsigned = Mobilization.build(
                author = keys.publicKey,
                identifier = require("slug"),
                createdAt = LocalDateTime.parse(require("created_at")).toEpochSecond(ZoneOffset.UTC),
                title = require("title"),
                start = parisTime(require("start")),
                end = metadata["end"]?.let(::parisTime),
                location = require("location"),
                type = MobilizationType.fromTag(require("type")) ?: error("${file.name}: unknown type"),
                territory = require("territory"),
                status = metadata["status"]?.let { MobilizationStatus.fromTag(it) ?: error("${file.name}: unknown status") } ?: MobilizationStatus.PREVU,
                declaration = metadata["declaration"]?.let { DeclarationState.fromTag(it) ?: error("${file.name}: unknown declaration") },
                links = listOfNotNull(metadata["link"]),
                // Keys of fictional organisations of content/organisations (their `pubkey`), separated by commas.
                organizers = metadata["organizers"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                summary = metadata["summary"],
                description = body,
                // The point on the map (contract, section 2): a town centre or a public place, never a person's address.
                geohash = metadata["geohash"]?.also { require(Mobilization.isValidGeohash(it)) { "${file.name}: invalid geohash" } },
            )
            val issues = MobilizationRules.check(unsigned)
            check(issues.isEmpty()) { "${file.name}: ${issues.joinToString { "${it.error.code} (${it.field})" }}" }
            val event = runBlocking { signer.sign(unsigned) }
            check(event.verify()) { "signature check failed for ${event.id}" }
            println("prepared ${file.name}")
            event
        }
    }
    val json = Json { prettyPrint = true }
    val file = output.resolve("agenda.json")
    Files.writeString(file, json.encodeToString(ListSerializer(SignedEvent.serializer()), events) + "\n")
    println("${events.size} event(s) written to $file")
}

/** `2026-11-14T14:00`, read in the local time of mainland France. */
private fun parisTime(text: String): Long {
    val local = LocalDateTime.parse(text)
    return ParisTime.toEpochSeconds(CivilDate(local.year, local.monthValue, local.dayOfMonth), local.hour, local.minute)
}

private fun frontMatter(name: String, text: String): Pair<Map<String, String>, String> {
    val lines = text.lines()
    require(lines.firstOrNull()?.trim() == "---") { "$name: the file must start with a --- front matter" }
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    require(end >= 0) { "$name: unterminated front matter" }
    val metadata = lines.subList(1, 1 + end).filter { it.isNotBlank() }.associate { line ->
        val separator = line.indexOf(':')
        require(separator > 0) { "$name: invalid front matter line: $line" }
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    return metadata to lines.drop(2 + end).joinToString("\n").trim()
}
