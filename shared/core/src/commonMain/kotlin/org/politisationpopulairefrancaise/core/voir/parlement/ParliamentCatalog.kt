package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/** A ballot with the assembly it belongs to. */
data class BallotEntry(val ballot: Ballot, val chamber: Chamber)

/**
 * How one member voted in one ballot, as recorded: the position, cast by delegation or not, the
 * correction declared afterwards (shown beside the recorded vote, never instead of it) and the
 * reason of a non-vote when the source gives one.
 */
data class MemberVote(
    val entry: BallotEntry,
    val position: Position,
    val delegated: Boolean,
    val correction: VoteCorrection?,
    val notVotingCause: String?,
) {
    val ballot: Ballot get() = entry.ballot
}

/**
 * Participation as PPF defines it, on every sheet with its definition: the share of the public
 * ballots of the mandate (among those on the device) where the member voted (for, against or
 * abstention). It does not measure the work in committee nor any other activity, and members are
 * never ranked by it.
 */
data class Participation(
    val voted: Int,
    val total: Int,
    /** First and last day of the ballots counted. */
    val from: CivilDate?,
    val to: CivilDate?,
) {
    /** Rounded to the unit. */
    val percent: Int get() = if (total == 0) 0 else ((voted * 100.0) / total + 0.5).toInt()
}

/** One month of ballots of one assembly announced by the index, on the device or not. */
data class BallotMonth(val chamber: Chamber, val month: String, val ref: PackageRef) {
    val name: String get() = ref.name
    val date: CivilDate? get() = CivilDate.parseIso("$month-01")
}

/**
 * Everything the device knows of the assemblies, read from the packages it holds: members, groups,
 * ballots, each member's votes and participation. Every list is sorted by name or by date, never
 * by any measure of the members (règle « Traitement identique »).
 */
class ParliamentCatalog(
    val membersPackage: MembersPackage?,
    ballotsPackages: List<BallotsPackage>,
) {
    val ballotsPackages: List<BallotsPackage> = ballotsPackages.sortedWith(compareByDescending<BallotsPackage> { it.month }.thenBy { it.chamber.ordinal })

    private val membersById: Map<String, Member> = membersPackage?.members.orEmpty().associateBy { it.id }
    private val groupsById: Map<String, Group> = membersPackage?.groups.orEmpty().associateBy { it.id }
    private val allMembers: List<Member> = membersPackage?.members.orEmpty().sortedWith(compareBy({ sortKey(it.name) }, { it.id }))

    /** Every ballot, most recent first. */
    private val allBallots: List<BallotEntry> = this.ballotsPackages
        .flatMap { pkg -> pkg.ballots.map { BallotEntry(it, pkg.chamber) } }
        .sortedWith(compareByDescending<BallotEntry> { it.ballot.date }.thenByDescending { it.ballot.number }.thenBy { it.ballot.id })
    private val ballotsById: Map<String, BallotEntry> = allBallots.associateBy { it.ballot.id }

    val example: Boolean get() = (membersPackage?.example ?: false) || ballotsPackages.any { it.example }

    val isEmpty: Boolean get() = allMembers.isEmpty()

    fun member(id: String): Member? = membersById[id]

    fun group(id: String?): Group? = id?.let { groupsById[it] }

    /** The members of [chamber] (all when null) and [group], whose name, group, party or territory match every word of [query]; by name. */
    fun members(query: String = "", chamber: Chamber? = null, group: String? = null): List<Member> =
        allMembers.filter { member ->
            (chamber == null || member.chamber == chamber) &&
                (group == null || member.group == group) &&
                (query.isBlank() || FrenchText.matchesAllWords(searchText(member), query))
        }

    /** The groups of [chamber] (all when null), by label. */
    fun groups(chamber: Chamber? = null): List<Group> =
        membersPackage?.groups.orEmpty().filter { chamber == null || it.chamber == chamber }.sortedWith(compareBy({ it.chamber.ordinal }, { FrenchText.fold(it.label) }))

    /** The assemblies that have at least one member, in a fixed order. */
    fun chambers(): List<Chamber> = Chamber.entries.filter { c -> allMembers.any { it.chamber == c } }

    /** The ballots of [chamber] (all when null), most recent first. */
    fun ballots(chamber: Chamber? = null): List<BallotEntry> = allBallots.filter { chamber == null || it.chamber == chamber }

    fun ballot(id: String): BallotEntry? = ballotsById[id]

    /** The ballots of a legislative file (`an-dossier:<uid>`), most recent first. */
    fun ballotsOfDossier(dossier: String): List<BallotEntry> = allBallots.filter { it.ballot.dossier == dossier }

    /** The votes of a member in the ballots on the device, most recent first. */
    fun votesOf(memberId: String): List<MemberVote> {
        val member = membersById[memberId] ?: return emptyList()
        return mandateBallots(member).mapNotNull { entry -> voteOf(entry, memberId) }
    }

    /** How [memberId] voted in [entry]; null when the member is in none of the lists. */
    fun voteOf(entry: BallotEntry, memberId: String): MemberVote? {
        val positions = entry.ballot.positions
        val position = positions.of(memberId) ?: return null
        return MemberVote(
            entry = entry,
            position = position,
            delegated = memberId in positions.delegated,
            correction = positions.corrections.firstOrNull { it.member == memberId },
            notVotingCause = positions.notVotingCauses.firstOrNull { it.member == memberId }?.cause,
        )
    }

    /**
     * The participation of a member, computed on the device on the public ballots of the member's
     * assembly held on the device, since the start of the mandate; null when there is none.
     */
    fun participation(memberId: String): Participation? {
        val member = membersById[memberId] ?: return null
        val ballots = mandateBallots(member)
        if (ballots.isEmpty()) return null
        val voted = ballots.count { it.ballot.positions.of(memberId).let { p -> p == Position.FOR || p == Position.AGAINST || p == Position.ABSTENTION } }
        val dates = ballots.mapNotNull { CivilDate.parseIso(it.ballot.date) }
        return Participation(voted, ballots.size, dates.minOrNull(), dates.maxOrNull())
    }

    /** The members of [ballot] in each position, by name; identifiers unknown to the members package are kept as such. */
    fun membersByPosition(ballot: Ballot): Map<Position, List<MemberOrId>> {
        val positions = ballot.positions
        fun named(ids: List<String>) = ids.map { id -> membersById[id]?.let { MemberOrId(id, it) } ?: MemberOrId(id, null) }
            .sortedWith(compareBy({ sortKey(it.member?.name ?: it.id) }, { it.id }))
        return linkedMapOf(
            Position.FOR to named(positions.inFavour),
            Position.AGAINST to named(positions.against),
            Position.ABSTENTION to named(positions.abstention),
            Position.NOT_VOTING to named(positions.notVoting),
        )
    }

    private fun mandateBallots(member: Member): List<BallotEntry> {
        val since = member.since
        return allBallots.filter { it.chamber == member.chamber && (since == null || it.ballot.date >= since) }
    }

    private fun searchText(member: Member): String {
        val group = group(member.group)
        return listOfNotNull(member.name.removePrefix(EXAMPLE_MARKER), member.territoryName, member.territory, group?.label, group?.abbreviation, member.nationalParty).joinToString(" ")
    }

    companion object {
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        /** The key that sorts names: without the [EXEMPLE] marker, accents and case ignored. */
        fun sortKey(name: String): String = FrenchText.fold(name.removePrefix(EXAMPLE_MARKER))

        /** The months of ballots of [chamber] announced by [index], most recent first. */
        fun months(index: ParliamentIndex, chamber: Chamber): List<BallotMonth> {
            val prefix = chamber.ballotsPrefix + "-"
            return index.packages.mapNotNull { ref ->
                if (!ref.name.startsWith(prefix)) return@mapNotNull null
                val month = ref.name.removePrefix(prefix)
                if (!MONTH.matches(month)) return@mapNotNull null
                BallotMonth(chamber, month, ref)
            }.sortedByDescending { it.month }
        }

        /** The identifier of the source of an assembly in the index. */
        fun sourceId(chamber: Chamber): String = when (chamber) {
            Chamber.AN -> "an"
            Chamber.SENAT -> "senat"
            Chamber.PE -> "pe"
        }

        private val MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    }
}

/** A member of a ballot list, or only the identifier when the members package does not know it. */
data class MemberOrId(val id: String, val member: Member?)

/**
 * The link between a block of "Voir" and the open data: a public person or a bill may carry the tag
 * [ParliamentFormat.TAG_OFFICIAL_ID] (`an:PA…`, `senat:…`, `pe:…`, `an-dossier:<uid>`). Read here,
 * without touching the models of "Voir"; anything else is ignored.
 */
object ParliamentLinks {
    private val MEMBER_ID = Regex("^(an|senat|pe):[A-Za-z0-9]{1,32}$")
    private val DOSSIER_ID = Regex("^an-dossier:[A-Za-z0-9]{1,64}$")

    /** The official identifier of a member carried by [event], or null. */
    fun memberId(event: SignedEvent): String? = event.tagValues(ParliamentFormat.TAG_OFFICIAL_ID).map { it.trim() }.firstOrNull { MEMBER_ID.matches(it) }

    /** The legislative file carried by [event], or null. */
    fun dossierId(event: SignedEvent): String? = event.tagValues(ParliamentFormat.TAG_OFFICIAL_ID).map { it.trim() }.firstOrNull { DOSSIER_ID.matches(it) }
}
