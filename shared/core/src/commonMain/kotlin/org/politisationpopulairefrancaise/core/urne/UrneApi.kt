package org.politisationpopulairefrancaise.core.urne

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The JSON exchanged with the ballot-box API of the server (`docs/FORMATS.md`, section 4 decies),
 * shared by the applications and the server. Bytes are lowercase hexadecimal; times are seconds
 * since 1970; levels are [VoteLevel.code].
 */

/** One ballot box, as everyone sees it: the same public keys for every device (D-110, 6.2). */
@Serializable
data class UrneInfo(
    /** Order of opening; the cursor of token delivery follows it. */
    val number: Long,
    /** Stable identifier, part of the key derivation. */
    val id: String,
    /** Address of what is voted on (a demand, a measure, an amendment, a question). */
    val subject: String,
    /** Allowed choices of a ballot (besides [WITHDRAWN]). */
    val choices: List<String>,
    val opensAt: Long,
    /** Null for a ballot box without a natural end (a demand open to votes). */
    val closesAt: Long?,
    /** Version of the seed of its keys (D-110, renewal). */
    val seedVersion: Int,
    /** Public key of each level, hexadecimal ristretto255 element. */
    val keys: Map<String, String>,
    /** Totals are shown while it is open (demands) or only after closing (questions). */
    val liveResults: Boolean,
    val closed: Boolean,
    /**
     * The results, when they are shown ([liveResults] or [closed]); null otherwise. They come with
     * the list of every box, so that reading them says nothing of what one looks at.
     */
    val results: UrneResults? = null,
) {
    companion object {
        /** The choice that withdraws a vote. */
        const val WITHDRAWN: String = "retire"
    }
}

/** The proofs of work the server asks for, in bits: the device must reach them (D-110, section 5). */
@Serializable
data class Difficulties(val register: Int, val tokens: Int, val ballot: Int)

@Serializable
data class UrneList(val urnes: List<UrneInfo>, val difficulties: Difficulties? = null)

/** What an account learns about itself (authenticated): its level and the ballot boxes still to serve. */
@Serializable
data class VoterStatus(
    val level: String,
    /** Monday (AAAA-MM-JJ) from which [level] counts. */
    val levelWeek: String,
    /** Numbers of the open ballot boxes for which this account has no token yet, in order. */
    val pending: List<Long>,
    /** The level before [levelWeek], for the boxes opened before it. */
    val previousLevel: String? = null,
    /** Monday (AAAA-MM-JJ) of the registration: a box opened before it is voted « non vérifié ». */
    val registeredWeek: String? = null,
) {
    /**
     * The level a token of a box opened at [opensAt] must carry (D-110, 6.4: the level of the week
     * of opening), as the server computes it; null when this status does not say. The device refuses
     * any other level: a level made for one device would recognise its ballots.
     */
    fun levelFor(opensAt: Long): VoteLevel? {
        val week = mondayOf(opensAt)
        val registered = registeredWeek ?: return null
        val previous = previousLevel?.let(VoteLevel::of) ?: return null
        return when {
            registered > week -> VoteLevel.NON_VERIFIE
            levelWeek <= week -> VoteLevel.of(level)
            else -> previous
        }
    }

    companion object {
        /** The Monday (AAAA-MM-JJ, UTC) of the week of [epochSeconds]; ISO dates compare as text. */
        fun mondayOf(epochSeconds: Long): String {
            val days = epochSeconds.floorDiv(86_400L)
            // 1 January 1970 was a Thursday: three days after a Monday.
            return CivilDate.fromEpochDays(days - (days + 3).mod(7L)).toIso()
        }
    }
}

@Serializable
data class BlindedRequest(val urne: Long, val blinded: String)

/** Tokens for a prefix of [VoterStatus.pending], every one of them: never a chosen subset (D-110, 6.3). */
@Serializable
data class TokenRequest(val requests: List<BlindedRequest>)

@Serializable
data class IssuedToken(val urne: Long, val level: String, val evaluated: String, val proof: String)

@Serializable
data class TokenResponse(val tokens: List<IssuedToken>, val upTo: Long)

/**
 * An anonymous ballot (D-110, 6.5): no account, no signature, no authentication header. [y] is
 * the secret identifier of the ballot chosen by the device, [token] its VOPRF output. Sending it
 * again with another [choice] replaces it until the closing.
 */
@Serializable
data class Ballot(
    val urne: Long,
    val level: String,
    val y: String,
    val token: String,
    val choice: String,
    /** Proof of work: [powDigest] must have the server's number of leading zero bits. */
    val nonce: Long,
    /**
     * Zeros that bring every ballot to the same size ([padded]): the length of an encrypted request
     * says nothing of the level, the choice or the box. Not part of the proof of work.
     */
    val padding: String = "",
) {
    fun powDigest(): ByteArray = digestFor(nonce)

    fun digestFor(candidate: Long): ByteArray {
        val text = listOf(POW_LABEL, urne.toString(), level, y, token, choice, candidate.toString()).joinToString(SEPARATOR)
        return Crypto.sha256(text.encodeToByteArray())
    }

    /** This ballot with [padding] such that its JSON makes [SIZE] bytes (unchanged if already longer). */
    fun padded(json: Json): Ballot {
        val bare = json.encodeToString(serializer(), copy(padding = "")).encodeToByteArray().size
        val missing = SIZE - bare - PADDING_OVERHEAD
        return if (missing <= 0) copy(padding = "") else copy(padding = "0".repeat(missing))
    }

    companion object {
        const val POW_LABEL: String = "ppf-bulletin-v1"

        /**
         * Bits of proof of work of every ballot, the same on every device: a number sent by the server
         * could differ from one device to another and recognise its ballots (review R5). The server
         * asks this many or fewer.
         */
        const val DIFFICULTY: Int = 12

        /** Size in bytes of the JSON of every ballot sent ([padded]). */
        const val SIZE: Int = 640

        /** `,"padding":""` around the zeros. */
        private const val PADDING_OVERHEAD: Int = 13
        private const val SEPARATOR: String = "\u001f"

        /** A ballot with its proof of work, mined on the device. */
        fun withProofOfWork(urne: Long, level: String, y: String, token: String, choice: String, difficulty: Int): Ballot {
            val draft = Ballot(urne, level, y, token, choice, 0)
            return draft.copy(nonce = ProofOfWork.mineNonce(difficulty) { draft.digestFor(it) })
        }
    }
}

@Serializable
data class BallotReceipt(val status: String)

/**
 * Counts by choice, for one level or one total. [hidden]: too few ballots to be shown while the box
 * is open (fewer than [UrneResults.minimum]); [counts] is then empty and [ballots] zero.
 */
@Serializable
data class ChoiceCounts(val counts: Map<String, Long>, val ballots: Long, val hidden: Boolean = false)

/**
 * The results (D-112): the citizens' vote, verified accounts first, then not verified, then the
 * citizens' total; the elected officials' vote apart, never added to the citizens' total.
 */
@Serializable
data class UrneResults(
    val verified: ChoiceCounts,
    val notVerified: ChoiceCounts,
    val citizens: ChoiceCounts,
    val electedOfficials: ChoiceCounts,
    /** When a column is hidden: the number of ballots under which a column is not shown (5 by default). */
    val minimum: Int? = null,
)

/** The public log of a ballot box, continuous (D-110, R2): a stuffing (more tokens than accounts) shows at once. */
@Serializable
data class UrneJournal(
    val number: Long,
    /** Accounts of each level now (everyone, not only those who voted). */
    val accounts: Map<String, Long>,
    /** Tokens issued for this ballot box, by level. */
    val issued: Map<String, Long>,
    /** Ballots received, by level (withdrawn ones excluded). */
    val ballots: Map<String, Long>,
    /** How many times a ballot was replaced. */
    val replacements: Long,
    /** Null while results are hidden (question still open). */
    val results: UrneResults?,
    val closed: Boolean,
)

@Serializable
data class PublishedBallot(
    val y: String,
    val token: String,
    val level: String,
    val choice: String,
    /** Start (UTC) of the day of its last reception: never finer, so that few ballots say nothing of a person. */
    val day: Long,
)

/**
 * Published at the closing (D-110, 6.7): the secret key of each level, the anonymous ballots sorted
 * by [PublishedBallot.y], the totals. Anyone can recompute every token and recount.
 */
@Serializable
data class UrneClosing(
    val number: Long,
    val id: String,
    val secretKeys: Map<String, String>,
    val publicKeys: Map<String, String>,
    val issued: Map<String, Long>,
    val ballots: List<PublishedBallot>,
    val results: UrneResults,
    /** The signed public votes of the elected officials (D-115), the latest of each; they are the elected column. */
    val publicVotes: List<org.politisationpopulairefrancaise.core.signing.SignedEvent> = emptyList(),
)
