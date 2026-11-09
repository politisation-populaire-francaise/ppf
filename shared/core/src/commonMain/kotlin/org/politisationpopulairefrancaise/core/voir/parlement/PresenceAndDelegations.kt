package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * How the members of one group took part in the major ballots (VO-35), counted in member-ballots: for each
 * member of the group today and each major ballot of their mandate on the device, one of four outcomes. The
 * four always add up to [expected].
 */
data class GroupPresence(
    val group: Group,
    /** Members of the group today, in the members package. */
    val members: Int,
    /** Member-ballots of their mandates: what the four counts share. */
    val expected: Int,
    /** Voted for, against or abstained, themselves. */
    val personal: Int,
    /** Voted by delegation (another member voted for them, as the Constitution allows once per member). */
    val delegated: Int,
    /** Recorded as not voting (the president of the sitting, a member of the Government…), with the published cause. */
    val notVoting: Int,
    /** No vote recorded: absent, or present without voting; the open data do not tell which. */
    val withoutVote: Int,
) {
    fun percent(count: Int): Int = if (expected == 0) 0 else ((count * 100.0) / expected + 0.5).toInt()
}

/** Presence and delegations of one assembly over the major ballots on the device. */
data class ChamberPresence(
    val chamber: Chamber,
    /** The major ballots counted, most recent first. */
    val ballots: List<BallotEntry>,
    /** One line per group, in the alphabetical order of their names: never sorted by any of the counts. */
    val groups: List<GroupPresence>,
    val from: CivilDate?,
    val to: CivilDate?,
)

/**
 * « Présence et délégations » (VO-35, cahier fonctionnel M20), computed on the device from the packages it
 * holds, by group and by assembly, never member by member and never ranked (règle « Traitement identique »).
 *
 * A major ballot is a « scrutin public solennel » of the Assemblée nationale (kind `SPS`): the Conférence des
 * présidents decides it, usually for the vote on the whole of a major text, at a time chosen so that as many
 * deputies as possible are there (Règlement de l'Assemblée nationale, article 65-1; fiche de synthèse n° 56,
 * « Les votes à l'Assemblée nationale »). Motions of censure are left out: only the votes in favour are
 * recorded (Constitution, article 49). The Sénat and the European Parliament have no such kind in the open
 * data imported so far: nothing is shown for them rather than a guess.
 *
 * The groups are those of today; a member is counted only for the ballots since the start of their mandate,
 * and members who left the assembly are no longer in the package: the page says so beside the figures.
 */
object PresenceAndDelegations {
    /** The kind of the « scrutins publics solennels » in the open data of the Assemblée nationale. */
    const val SOLEMN_KIND: String = "SPS"

    /** The assemblies for which major ballots are defined in the data imported so far. */
    val CHAMBERS: Set<Chamber> = setOf(Chamber.AN)

    fun isMajor(entry: BallotEntry): Boolean = entry.chamber == Chamber.AN && entry.ballot.kind?.uppercase() == SOLEMN_KIND

    /** Presence and delegations of [chamber] over its major ballots on the device; null when there is none or no members package. */
    fun of(catalog: ParliamentCatalog, chamber: Chamber): ChamberPresence? {
        if (chamber !in CHAMBERS || catalog.membersPackage == null) return null
        val ballots = catalog.ballots(chamber).filter(::isMajor)
        if (ballots.isEmpty()) return null
        val groups = catalog.groups(chamber).mapNotNull { group ->
            val members = catalog.members(chamber = chamber, group = group.id)
            if (members.isEmpty()) return@mapNotNull null
            var expected = 0
            var personal = 0
            var delegated = 0
            var notVoting = 0
            var withoutVote = 0
            members.forEach { member ->
                ballots.filter { member.since == null || it.ballot.date >= member.since }.forEach { entry ->
                    expected++
                    val positions = entry.ballot.positions
                    when (positions.of(member.id)) {
                        Position.FOR, Position.AGAINST, Position.ABSTENTION -> if (member.id in positions.delegated) delegated++ else personal++
                        Position.NOT_VOTING -> notVoting++
                        null -> withoutVote++
                    }
                }
            }
            GroupPresence(group, members.size, expected, personal, delegated, notVoting, withoutVote)
        }.sortedBy { ParliamentCatalog.sortKey(it.group.label) }
        val dates = ballots.mapNotNull { CivilDate.parseIso(it.ballot.date) }
        return ChamberPresence(chamber, ballots, groups, dates.minOrNull(), dates.maxOrNull())
    }
}
