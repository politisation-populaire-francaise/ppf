package org.politisationpopulairefrancaise.core.urne

/** Counting of ballots, the same on the server and for anyone who recounts (D-112). */
object UrneTally {

    /** Results of [ballots] (level, choice); withdrawn ballots are not counted. */
    fun results(ballots: List<Pair<VoteLevel, String>>): UrneResults {
        val counted = ballots.filter { it.second != UrneInfo.WITHDRAWN }
        fun counts(of: List<Pair<VoteLevel, String>>) =
            ChoiceCounts(of.groupingBy { it.second }.eachCount().map { it.key to it.value.toLong() }.sortedBy { it.first }.toMap(), of.size.toLong())
        return UrneResults(
            verified = counts(counted.filter { it.first == VoteLevel.VERIFIE }),
            notVerified = counts(counted.filter { it.first == VoteLevel.NON_VERIFIE }),
            citizens = counts(counted.filter { it.first.isCitizen }),
            electedOfficials = counts(counted.filter { it.first == VoteLevel.ELU }),
        )
    }

    /**
     * The results as shown while the box is open (team lead's decision on the review R5): a level
     * column with 1 to [minimum] - 1 ballots is hidden (« moins de 5 »), so that a few ballots of one
     * level never tell anyone's choice; the citizens' total is hidden with it, since the total minus
     * the other column would give it back. Everything is published at the closing (D-112, Q4).
     */
    fun hidingSmall(results: UrneResults, minimum: Int): UrneResults {
        fun small(counts: ChoiceCounts) = counts.ballots in 1 until minimum
        val hidden = ChoiceCounts(emptyMap(), 0, hidden = true)
        val hideVerified = small(results.verified)
        val hideNotVerified = small(results.notVerified)
        val hideCitizens = hideVerified || hideNotVerified || small(results.citizens)
        // The elected officials vote publicly (D-115): their column is never hidden.
        val hideElected = false
        if (!hideCitizens && !hideElected) return results
        return UrneResults(
            verified = if (hideVerified) hidden else results.verified,
            notVerified = if (hideNotVerified) hidden else results.notVerified,
            citizens = if (hideCitizens) hidden else results.citizens,
            electedOfficials = if (hideElected) hidden else results.electedOfficials,
            minimum = minimum,
        )
    }

    /** The same results from counts of ballots by (level, choice), as a database adds them up. */
    fun fromCounts(counts: Map<Pair<VoteLevel, String>, Long>): UrneResults {
        val counted = counts.filterKeys { it.second != UrneInfo.WITHDRAWN }
        fun of(levels: (VoteLevel) -> Boolean): ChoiceCounts {
            val selected = counted.filterKeys { levels(it.first) }
            val byChoice = selected.entries.groupBy({ it.key.second }, { it.value }).mapValues { it.value.sum() }
            return ChoiceCounts(byChoice.entries.sortedBy { it.key }.associate { it.key to it.value }, selected.values.sum())
        }
        return UrneResults(
            verified = of { it == VoteLevel.VERIFIE },
            notVerified = of { it == VoteLevel.NON_VERIFIE },
            citizens = of { it.isCitizen },
            electedOfficials = of { it == VoteLevel.ELU },
        )
    }
}

/** What a recount of a published closing found. */
enum class RecountResult {
    CORRECT,

    /** A published secret key does not match the public key announced at the opening. */
    KEY_MISMATCH,

    /** A ballot's token is not the one its secret identifier gives with the key of its level. */
    INVALID_TOKEN,

    /** Two ballots share a secret identifier. */
    DUPLICATE_BALLOT,

    /** More ballots than tokens issued for a level. */
    MORE_BALLOTS_THAN_TOKENS,

    /** The published totals differ from the count of the published ballots. */
    WRONG_TOTALS,

    /** A ballot uses a level or a choice that does not exist. */
    UNKNOWN_VALUE,
}

/**
 * Recounts a closed ballot box from what was published (D-110, 6.7): anyone can run it, the
 * application included. [announcedKeys] are the public keys seen while the box was open.
 */
object UrneRecount {
    fun check(closing: UrneClosing, announcedKeys: Map<String, String>, choices: List<String>, group: Ristretto255): RecountResult {
        val voprf = Voprf(group, VoprfMode.VOPRF)
        for ((level, secretHex) in closing.secretKeys) {
            val announced = announcedKeys[level] ?: return RecountResult.KEY_MISMATCH
            val publicKey = runCatching { group.multiplyBase(secretHex.hexToByteArray()) }.getOrNull() ?: return RecountResult.KEY_MISMATCH
            if (publicKey.toHexString() != announced || closing.publicKeys[level] != announced) return RecountResult.KEY_MISMATCH
        }
        val seen = HashSet<String>()
        val counted = ArrayList<Pair<VoteLevel, String>>()
        for (ballot in closing.ballots) {
            val level = VoteLevel.of(ballot.level) ?: return RecountResult.UNKNOWN_VALUE
            if (ballot.choice != UrneInfo.WITHDRAWN && ballot.choice !in choices) return RecountResult.UNKNOWN_VALUE
            if (!seen.add(ballot.y)) return RecountResult.DUPLICATE_BALLOT
            val secret = closing.secretKeys[ballot.level] ?: return RecountResult.KEY_MISMATCH
            val expected = runCatching { voprf.evaluate(secret.hexToByteArray(), ballot.y.hexToByteArray()) }.getOrNull()
                ?: return RecountResult.INVALID_TOKEN
            if (!Voprf.constantTimeEquals(expected, ballot.token.hexToByteArray())) return RecountResult.INVALID_TOKEN
            counted += level to ballot.choice
        }
        for (level in VoteLevel.entries) {
            val ballots = closing.ballots.count { it.level == level.code }.toLong()
            if (ballots > (closing.issued[level.code] ?: 0L)) return RecountResult.MORE_BALLOTS_THAN_TOKENS
        }
        // The elected officials vote publicly (D-115): every vote signed by its official, one per official.
        if (closing.ballots.any { it.level == VoteLevel.ELU.code }) return RecountResult.UNKNOWN_VALUE
        val officials = HashSet<String>()
        for (event in closing.publicVotes) {
            val vote = PublicVote.parse(event) ?: return RecountResult.UNKNOWN_VALUE
            if (vote.urne != closing.number || !event.verify()) return RecountResult.INVALID_TOKEN
            if (vote.choice != UrneInfo.WITHDRAWN && vote.choice !in choices) return RecountResult.UNKNOWN_VALUE
            if (!officials.add(vote.official)) return RecountResult.DUPLICATE_BALLOT
            counted += VoteLevel.ELU to vote.choice
        }
        if (UrneTally.results(counted) != closing.results) return RecountResult.WRONG_TOTALS
        return RecountResult.CORRECT
    }
}
