package org.politisationpopulairefrancaise.tools.content

import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** The bundle of the solidarity rubric of "Agir", next to organisations.json. */
internal const val SOLIDARITY_BUNDLE: String = "solidarite.json"

/**
 * The solidarity block of the signer (M35, docs/integration/agir-solidarite.md): the Markdown files
 * of `content/solidarite/caisses` become entries of the directory of solidarity funds
 * (`ppf-type` = `caisse`) and those of `content/solidarite/missions` volunteer missions
 * (`ppf-type` = `mission`), written to `solidarite.json`.
 *
 * Kept outside [prepareBundles], like the agenda: the no-regression test of the bundles is not
 * touched until `solidarite.json` is first signed. Every content is built by the model of the
 * application and read back by it; the tool fails on an unknown field, on a fund without the
 * statement that it serves neither a candidate nor a party (`no_party: oui`), on a manager that is
 * not an entry of `content/organisations`, or on anything the application would leave out.
 */
internal fun signSolidarity(content: Path, output: Path, keys: IdentityKeys) {
    val signer = LocalEventSigner(keys)
    write(output.resolve(SOLIDARITY_BUNDLE), solidarityEvents(content, keys.publicKey).map { signer.signChecked(it) })
}

/** The funds, then the missions, checked and unsigned: building them needs only the public key of the author. */
internal fun solidarityEvents(content: Path, author: PublicKey): List<UnsignedEvent> {
    val directory = content.resolve("solidarite")
    val directoryEntries = solidarityDocuments(content.resolve("organisations"), log = false).map { it.require("slug") }.toSet()
    val funds = solidarityDocuments(directory.resolve("caisses")).map { fundOf(author, it, directoryEntries) }
    val missions = solidarityDocuments(directory.resolve("missions")).map { missionOf(author, it) }
    val slugs = (funds + missions).map { event -> event.tags.first { it.firstOrNull() == "d" }[1] }
    check(slugs.size == slugs.toSet().size) { "solidarite: the same slug is used twice" }
    return funds + missions
}

private val FUND_FIELDS = setOf(
    "slug", "title", "summary", "fund_type", "territory", "beneficiary", "verified", "no_party", "manager", "manager_entry",
    "donation_link", "sector", "strike", "status", "origin", "geohash", "published_at", "updated_at",
)

private val MISSION_FIELDS = setOf(
    "slug", "title", "summary", "skills", "territory", "remote", "location", "commitment", "people", "deadline", "status",
    "origin", "published_at", "updated_at",
)

private fun solidarityDocuments(directory: Path, log: Boolean = true): List<Document> {
    if (!directory.isDirectory()) return emptyList()
    return directory.listDirectoryEntries("*.md").sortedBy { it.name }.map { file ->
        parseDocument(file.name, file.readText()).also { if (log) println("prepared solidarite/${directory.name}/${file.name}") }
    }
}

private fun fundOf(author: PublicKey, document: Document, directoryEntries: Set<String>): UnsignedEvent {
    (document.metadata.keys - FUND_FIELDS).firstOrNull()?.let { error("${document.name}: unknown field: $it") }
    check(document.require("no_party") == SolidarityFormats.NO_PARTY_YES) {
        "${document.name}: a fund is listed only once the team checked it serves neither a candidate nor a party (no_party: oui)"
    }
    val managerEntry = document.metadata["manager_entry"]?.let { slug ->
        check(slug in directoryEntries) { "${document.name}: manager_entry names no entry of content/organisations: $slug" }
        // The entries of the directory are signed by the same key as the funds: their address is known here.
        ContentAddress(Organisation.KIND, author.hex, slug)
    }
    val unsigned = runCatching {
        SolidarityFund.build(
            editor = author,
            slug = document.require("slug"),
            name = document.require("title"),
            summary = document.require("summary"),
            type = document.require("fund_type"),
            territory = document.require("territory"),
            beneficiary = document.require("beneficiary"),
            verifiedOn = document.require("verified"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            presentation = document.body,
            managerName = document.metadata["manager"],
            managerEntry = managerEntry,
            donationLink = document.metadata["donation_link"],
            sector = document.metadata["sector"],
            strike = document.metadata["strike"],
            status = document.metadata["status"] ?: SolidarityFormats.FUND_OPEN,
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("unknown origin"),
            geohash = document.metadata["geohash"],
        )
    }.getOrElse { error("${document.name}: ${it.message}") }
    checkNotNull(SolidarityFund.parse(readable(unsigned))) { "${document.name}: the application would not read this fund" }
    return unsigned
}

private fun missionOf(author: PublicKey, document: Document): UnsignedEvent {
    (document.metadata.keys - MISSION_FIELDS).firstOrNull()?.let { error("${document.name}: unknown field: $it") }
    val remote = when (val value = document.metadata["remote"]) {
        null -> false
        SolidarityFormats.REMOTE_YES -> true
        else -> error("${document.name}: remote is \"oui\" or absent, not \"$value\"")
    }
    val unsigned = runCatching {
        VolunteerMission.build(
            editor = author,
            slug = document.require("slug"),
            title = document.require("title"),
            summary = document.require("summary"),
            skills = document.require("skills").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            territory = document.require("territory"),
            publishedAt = document.require("published_at").dateToEpochSeconds(),
            updatedAt = document.require("updated_at").dateToEpochSeconds(),
            description = document.body,
            remote = remote,
            location = document.metadata["location"],
            commitment = document.metadata["commitment"],
            people = document.metadata["people"]?.let { it.toIntOrNull() ?: error("people is a number: $it") },
            deadline = document.metadata["deadline"]?.let(::endOfParisDay),
            status = document.metadata["status"] ?: SolidarityFormats.MISSION_OPEN,
            origin = ContentOrigin.fromTag(document.require("origin")) ?: error("unknown origin"),
        )
    }.getOrElse { error("${document.name}: ${it.message}") }
    checkNotNull(VolunteerMission.parse(readable(unsigned))) { "${document.name}: the application would not read this mission" }
    return unsigned
}

/** A deadline `AAAA-MM-JJ` lasts the whole day, in Paris time like the calls (independent of the machine). */
private fun endOfParisDay(text: String): Long {
    val date = LocalDate.parse(text)
    return ParisTime.toEpochSeconds(CivilDate(date.year, date.monthValue, date.dayOfMonth), 23, 59)
}

/** The event as the readers of the model see it, without a signature (they do not check it). */
private fun readable(unsigned: UnsignedEvent): SignedEvent =
    SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
