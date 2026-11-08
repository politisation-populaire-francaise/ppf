package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The history of the public ballots, for the presidential comparator of "Décider" (DE-16,
 * docs/integration/voir-historique-import.md): one package per assembly and period (legislature of
 * the Assemblée nationale or of the European Parliament, session year of the Sénat), listed in the
 * same signed index as the other packages, and downloaded whole by the application for every party
 * at once (never a request per party: opinions are sensitive data, GDPR art. 9).
 *
 * A package holds, for every public ballot, its official description, its result and the
 * distribution of the votes per party at the date of the ballot. It holds no name of any person
 * and no nominal vote (these stay in the monthly packages of the current legislature), and no
 * "majority position" of a party: that would be an editorial reading of the counts.
 */

/** How the party of a member at the date of a ballot was found. */
@Serializable
enum class PartyBasis {
    /** The party itself: the party of attachment (AN, `PARPOL`) or the national party (EP). */
    @SerialName("parti") PARTY,

    /** The political group of the assembly: the source publishes no party for this member at this date. */
    @SerialName("groupe") GROUP,
}

/** A party (or a group standing for it) named by the distributions of a package. */
@Serializable
data class PartyRef(
    /** `an:PO…` (party or group of the Assemblée), `senat-grp:<code>` (group of the Sénat), `pe-org:<id>` (national party, EP). */
    val id: String,
    /** As the source writes it. */
    val label: String,
    val abbreviation: String? = null,
    val basis: PartyBasis,
    /** For a group: « groupe, rattachement au parti non publié », to show next to the label. */
    val basisLabel: String? = null,
)

/** How the members of one party voted in one ballot. */
@Serializable
data class PartyDistribution(
    /** A [PartyRef.id] of the package. */
    val party: String,
    /** Always written: `parti` or `groupe`. */
    val basis: PartyBasis,
    @SerialName("pour") val inFavour: Int,
    @SerialName("contre") val against: Int,
    val abstention: Int,
    /** Present but not voting, as the source records it (members absent from the sitting are not counted). */
    @SerialName("nonVotant") val notVoting: Int,
    /** The members of this party (or group) in the assembly at the date of the ballot. */
    val members: Int,
)

/** One public ballot of the history, without any nominal vote. */
@Serializable
data class HistoryBallot(
    /** `an:<législature>:<numéro>`, `senat:<année de session>:<numéro>`, `pe:<date>:<numéro du vote>`. */
    val id: String,
    /** AAAA-MM-JJ. */
    val date: String,
    /** The official title, as the source writes it. */
    val title: String,
    /** As the source codes it (Assemblée: `SPO`, `SPS`, `MOC`; European Parliament: `appel-nominal`). */
    val kind: String? = null,
    /** The reference of the text voted on, when the source gives it (European Parliament: `A10-0012/2025`…). */
    val text: String? = null,
    /** The legislative file (`an-dossier:<uid>`), when the source gives it. */
    val dossier: String? = null,
    /** The committee in charge, when the source gives it. */
    val committee: String? = null,
    /** Null when the source gives no result. */
    val adopted: Boolean? = null,
    /** The totals of the whole assembly. */
    val counts: Counts,
    /** Per party, sorted by [PartyDistribution.party]. European Parliament: the members elected in France only. */
    val parties: List<PartyDistribution> = emptyList(),
    /** The page of the ballot on the institution's site. */
    val url: String? = null,
)

/** The first and last day of the ballots a package holds (the official file may stop before the end of the period). */
@Serializable
data class HistoryCoverage(
    /** AAAA-MM-JJ. */
    val first: String,
    /** AAAA-MM-JJ. */
    val last: String,
)

/** The ballots of one period of one assembly. */
@Serializable
data class HistoryPackage(
    val format: Int = ParliamentFormat.FORMAT,
    val example: Boolean,
    val chamber: Chamber,
    /** The legislature (Assemblée nationale, European Parliament) or the session year (Sénat, October to September). */
    val period: Int,
    /** Null for a period without any ballot. */
    val coverage: HistoryCoverage? = null,
    /** Every party named by the distributions, sorted by identifier. */
    val parties: List<PartyRef>,
    /** Sorted by date, then by identifier. */
    val ballots: List<HistoryBallot>,
)

/** Names and checks of the history packages. */
object ParliamentHistory {
    const val PREFIX: String = "historique-"

    /** The label shown next to a group standing for a party. */
    const val GROUP_BASIS_LABEL: String = "groupe, rattachement au parti non publié"

    /** The short name of an assembly in the package names: `an`, `senat`, `pe`. */
    fun chamberCode(chamber: Chamber): String = when (chamber) {
        Chamber.AN -> "an"
        Chamber.SENAT -> "senat"
        Chamber.PE -> "pe"
    }

    fun isHistoryPackage(name: String): Boolean = parse(name) != null

    /** The assembly and period a history package name announces, null for any other name. */
    fun parse(name: String): Pair<Chamber, Int>? {
        if (!ParliamentFormat.isPackageName(name) || !name.startsWith(PREFIX)) return null
        val rest = name.removePrefix(PREFIX)
        val chamber = Chamber.entries.firstOrNull { rest.startsWith(chamberCode(it) + "-") } ?: return null
        val period = rest.removePrefix(chamberCode(chamber) + "-").takeIf { it.all(Char::isDigit) && it.length in 1..4 && !it.startsWith("0") }?.toIntOrNull() ?: return null
        return chamber to period
    }

    /**
     * Reads a history package as the one its name announces (assembly and period), strict on these,
     * tolerant on unknown fields; null when unreadable or of another format.
     */
    fun read(name: String, json: String): HistoryPackage? {
        val (chamber, period) = parse(name) ?: return null
        return runCatching { ParliamentFormat.json.decodeFromString(HistoryPackage.serializer(), json) }.getOrNull()
            ?.takeIf { it.format == ParliamentFormat.FORMAT && it.chamber == chamber && it.period == period }
    }
}

/** `historique-an-14`, `historique-senat-2025`, `historique-pe-9`. */
fun ParliamentFormat.historyPackage(chamber: Chamber, period: Int): String {
    require(period in 1..9999) { "period out of range" }
    return ParliamentHistory.PREFIX + ParliamentHistory.chamberCode(chamber) + "-" + period
}
