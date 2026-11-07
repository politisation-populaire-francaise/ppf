package org.politisationpopulairefrancaise.core.voir.parlement

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate

/*
 * The packages of parliamentary open data (contract docs/integration/voir-donnees-ouvertes.md,
 * D-064): who sits in the Assemblée nationale, the Sénat and the European Parliament (elected in
 * France), their groups, and the public ballots with the position of each member. They are built
 * by the separate import tool from the open data of these institutions, served by the server as
 * plain files, and checked on the device: the index is a NIP-01 event signed by a dedicated import
 * key, and lists the SHA-256 of every package. Nothing personal beyond the mandate is ever kept
 * (no birth date, profession, sex, address, photograph).
 */

/** Shared constants and helpers of the packages. */
object ParliamentFormat {
    const val FORMAT: Int = 1

    /** The index is an application-data event (NIP-78), addressable by this `d`. */
    const val INDEX_KIND: Int = EventKinds.APP_DATA
    const val INDEX_D: String = "ppf:parlement-index"

    /** On a public person or a bill of "Voir": its identifier in the open data (`an:PA…`, `senat:…`, `pe:…`, `an-dossier:…`). */
    const val TAG_OFFICIAL_ID: String = "ppf-official-id"

    const val MEMBERS_PACKAGE: String = "membres"
    fun ballotsPackage(chamber: Chamber, year: Int, month: Int): String =
        "${chamber.ballotsPrefix}-$year-${month.toString().padStart(2, '0')}"

    /** After this many days without a new index, the device says the data may be out of date. */
    const val STALE_AFTER_DAYS: Int = 14

    /** An import that loses more than this share of members or ballots is refused (truncated file). */
    const val MAX_SHRINK: Double = 0.20

    /** Package names: lowercase letters, digits and dashes, so that a name is also a safe file name. */
    private val PACKAGE_NAME = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    fun isPackageName(name: String): Boolean = name.length in 1..64 && PACKAGE_NAME.matches(name)

    /** The JSON of the packages: unknown fields ignored (newer formats), absent optional fields allowed. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    fun sha256Hex(bytes: ByteArray): String = Crypto.sha256(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** The unsigned index event: `content` is the exact text of the index, `created_at` the moment of the import. */
    fun indexEvent(importKey: PublicKey, index: ParliamentIndex, createdAt: Long): UnsignedEvent =
        UnsignedEvent.create(importKey, INDEX_KIND, createdAt, listOf(listOf("d", INDEX_D)), json.encodeToString(ParliamentIndex.serializer(), index))
}

/** Which assembly. */
@Serializable
enum class Chamber(val ballotsPrefix: String) {
    @SerialName("an") AN("scrutins-an"),
    @SerialName("senat") SENAT("scrutins-senat"),
    @SerialName("pe") PE("votes-pe"),
}

/** Where a package comes from, as the reader sees it everywhere: name, address, licence, last update. */
@Serializable
data class DataSource(
    /** `an`, `senat`, `pe`, `hatvp`. */
    val id: String,
    val name: String,
    val url: String,
    val licence: String,
    /** AAAA-MM-JJ. */
    val updatedOn: String,
)

@Serializable
data class PackageRef(val name: String, val sha256: String, val bytes: Long)

/** The index of the packages, the `content` of the signed index event. */
@Serializable
data class ParliamentIndex(
    val format: Int = ParliamentFormat.FORMAT,
    /** True for the fictional data used in development, tests and preproduction. */
    val example: Boolean,
    /** AAAA-MM-JJ. */
    val importedOn: String,
    val sources: List<DataSource>,
    val packages: List<PackageRef>,
) {
    fun source(id: String): DataSource? = sources.firstOrNull { it.id == id }
    fun packageRef(name: String): PackageRef? = packages.firstOrNull { it.name == name }
    val importDate: CivilDate? get() = CivilDate.parseIso(importedOn)
}

/** A member of an assembly, as far as the mandate goes. */
@Serializable
data class Member(
    /** Prefixed official identifier: `an:PA721210`, `senat:19591F`, `pe:22858`. */
    val id: String,
    /** As the source writes it (first name and last name). */
    val name: String,
    val chamber: Chamber,
    /** The group identifier in [MembersPackage.groups], null when the member sits in none. */
    val group: String? = null,
    /**
     * A deputy: the constituency (department code followed by the number on two digits); a
     * senator: the department code; a member of the European Parliament: `FR`.
     */
    val territory: String,
    val territoryName: String? = null,
    /** Start of the current mandate, AAAA-MM-JJ. */
    val since: String? = null,
    /** The member's page on the institution's site. */
    val url: String,
    /** European Parliament only: the national party, as the source names it. */
    val nationalParty: String? = null,
    /** The page of the member's declaration of interests on the site of the HATVP, when there is one (link only). */
    val interestsUrl: String? = null,
)

/** A political group of an assembly. */
@Serializable
data class Group(val id: String, val chamber: Chamber, val label: String, val abbreviation: String? = null)

/** Every current member and every group. */
@Serializable
data class MembersPackage(
    val format: Int = ParliamentFormat.FORMAT,
    val example: Boolean,
    val members: List<Member>,
    val groups: List<Group>,
)

/** A position in a public ballot. */
@Serializable
enum class Position {
    @SerialName("pour") FOR,
    @SerialName("contre") AGAINST,
    @SerialName("abstention") ABSTENTION,
    @SerialName("nonVotant") NOT_VOTING,
}

@Serializable
data class Counts(
    val voters: Int? = null,
    val expressed: Int? = null,
    @SerialName("pour") val inFavour: Int,
    @SerialName("contre") val against: Int,
    val abstention: Int,
    @SerialName("nonVotant") val notVoting: Int? = null,
)

@Serializable
data class GroupCount(
    val group: String,
    @SerialName("pour") val inFavour: Int,
    @SerialName("contre") val against: Int,
    val abstention: Int,
    @SerialName("nonVotant") val notVoting: Int = 0,
)

/** Why a vote was corrected after the ballot. */
@Serializable
enum class CorrectionKind {
    /** « Mise au point »: the member declared they meant to vote otherwise. */
    @SerialName("mise-au-point") STATEMENT,
    /** « Dysfonctionnement »: the voting system failed for this member. */
    @SerialName("dysfonctionnement") MALFUNCTION,
}

/**
 * A correction after the ballot. The recorded vote never changes; the interface shows both, with
 * the date when the source gives one (the Assemblée's file does not: the date of the ballot is shown).
 */
@Serializable
data class VoteCorrection(
    val member: String,
    val intended: Position,
    val kind: CorrectionKind = CorrectionKind.STATEMENT,
    val date: String? = null,
)

/** Why a member did not vote, as the source codes it (Assemblée: `MG` member of the Government, `PAN` President of the Assemblée, `PSE` chairing the sitting). */
@Serializable
data class NotVotingCause(val member: String, val cause: String)

@Serializable
data class Positions(
    @SerialName("pour") val inFavour: List<String> = emptyList(),
    @SerialName("contre") val against: List<String> = emptyList(),
    val abstention: List<String> = emptyList(),
    @SerialName("nonVotant") val notVoting: List<String> = emptyList(),
    /** Members whose vote was cast by delegation, when the source says so. */
    val delegated: List<String> = emptyList(),
    val corrections: List<VoteCorrection> = emptyList(),
    val notVotingCauses: List<NotVotingCause> = emptyList(),
) {
    fun of(member: String): Position? = when (member) {
        in inFavour -> Position.FOR
        in against -> Position.AGAINST
        in abstention -> Position.ABSTENTION
        in notVoting -> Position.NOT_VOTING
        else -> null
    }
}

/** One public ballot. */
@Serializable
data class Ballot(
    /** `an:17:1234`, `pe:2026-10-21:12`. */
    val id: String,
    val number: Int,
    /** AAAA-MM-JJ. */
    val date: String,
    /** The official title, as the source writes it. */
    val title: String,
    /** As the source codes it (Assemblée: `SPO` ordinary public ballot, `SPS` solemn, `MOC` motion of censure). */
    val kind: String? = null,
    /** Null when the source gives no result. */
    val adopted: Boolean? = null,
    val counts: Counts,
    val groups: List<GroupCount> = emptyList(),
    val positions: Positions = Positions(),
    val url: String? = null,
    /** The legislative file the ballot belongs to (`an-dossier:<uid>`), and its page. */
    val dossier: String? = null,
    val dossierUrl: String? = null,
)

/** The ballots of one month in one assembly. */
@Serializable
data class BallotsPackage(
    val format: Int = ParliamentFormat.FORMAT,
    val example: Boolean,
    val chamber: Chamber,
    /** AAAA-MM. */
    val month: String,
    val ballots: List<Ballot>,
)

/**
 * Which keys may sign the index of the packages. They are trusted for that only: an import key
 * never publishes a content, an editorial key never signs a package. Several keys may be listed
 * (rotation); a key is revoked by an update of the application.
 */
class OpenDataTrust(private val keys: Set<String>) {
    fun isTrusted(pubkeyHex: String): Boolean = pubkeyHex.lowercase() in keys

    companion object {
        // The key of the fictional packages (development, tests, preproduction) is added here by the
        // import block, with its private key kept out of the repository like the editorial example key.
        fun of(vararg pubkeys: String): OpenDataTrust = OpenDataTrust(pubkeys.map { it.lowercase() }.toSet())
    }
}

/** Why an index was not accepted. */
enum class IndexProblem { NOT_AUTHENTIC, UNTRUSTED_KEY, NOT_AN_INDEX, UNREADABLE, OLDER_THAN_CURRENT }

/** The outcome of reading an index event. */
sealed interface IndexReading {
    data class Accepted(val event: SignedEvent, val index: ParliamentIndex) : IndexReading
    data class Refused(val problem: IndexProblem) : IndexReading
}

object ParliamentIndexReader {
    /**
     * Checks an index event: authentic signature, trusted import key, the right kind and `d`,
     * readable content, and never older than the one already on the device (no rollback, by the
     * moment of the event and by the day of the import).
     */
    fun read(event: SignedEvent, trust: OpenDataTrust, current: SignedEvent? = null): IndexReading {
        if (!event.verify()) return IndexReading.Refused(IndexProblem.NOT_AUTHENTIC)
        if (!trust.isTrusted(event.pubkey)) return IndexReading.Refused(IndexProblem.UNTRUSTED_KEY)
        if (event.kind != ParliamentFormat.INDEX_KIND || event.firstTagValue("d") != ParliamentFormat.INDEX_D) return IndexReading.Refused(IndexProblem.NOT_AN_INDEX)
        val index = runCatching { ParliamentFormat.json.decodeFromString(ParliamentIndex.serializer(), event.content) }.getOrNull()
            ?.takeIf { it.format == ParliamentFormat.FORMAT && it.importDate != null && it.packages.all { p -> ParliamentFormat.isPackageName(p.name) && p.sha256.length == 64 } }
            ?: return IndexReading.Refused(IndexProblem.UNREADABLE)
        if (current != null) {
            val currentIndex = runCatching { ParliamentFormat.json.decodeFromString(ParliamentIndex.serializer(), current.content) }.getOrNull()
            val olderEvent = event.createdAt < current.createdAt
            val olderImport = currentIndex?.importDate?.let { known -> index.importDate!! < known } ?: false
            if (olderEvent || olderImport) return IndexReading.Refused(IndexProblem.OLDER_THAN_CURRENT)
        }
        return IndexReading.Accepted(event, index)
    }

    /** The index is older than [ParliamentFormat.STALE_AFTER_DAYS] days on [today]. */
    fun isStale(index: ParliamentIndex, today: CivilDate): Boolean {
        val imported = index.importDate ?: return true
        return today.toEpochDays() - imported.toEpochDays() > ParliamentFormat.STALE_AFTER_DAYS
    }

    /** A package's bytes match the fingerprint of the index. */
    fun matches(ref: PackageRef, bytes: ByteArray): Boolean = ref.bytes == bytes.size.toLong() && ref.sha256 == ParliamentFormat.sha256Hex(bytes)
}

/** The import refuses a new set that lost too much against the previous one. */
object ImportGuard {
    fun accepts(previousCount: Int?, newCount: Int): Boolean {
        if (previousCount == null || previousCount == 0) return newCount >= 0
        return newCount >= previousCount * (1 - ParliamentFormat.MAX_SHRINK)
    }
}
