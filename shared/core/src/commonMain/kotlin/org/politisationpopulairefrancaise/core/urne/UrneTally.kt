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
        if (UrneTally.results(counted) != closing.results) return RecountResult.WRONG_TOTALS
        return RecountResult.CORRECT
    }
}
