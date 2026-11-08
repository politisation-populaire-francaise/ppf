package org.politisationpopulairefrancaise.core.identity

import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * The commitment of an account to its recovery key (kind 1776), as accepted by [AccountRecovery].
 * Only the first one received by the server for an account counts (D-134).
 */
data class RecoveryCommitment(
    /** Event id of the commitment. */
    val id: String,
    /** The account key that signed it, lowercase hex. */
    val account: String,
    /** The public recovery key it names, lowercase hex. */
    val recovery: String,
    val createdAt: Long,
)

/** A notice that an account is compromised (kind 1777), signed by its recovery key. */
data class CompromiseNotice(
    /** Event id of the notice. */
    val id: String,
    /** The recovery key that signed it, lowercase hex. */
    val recovery: String,
    /** The compromised account key, lowercase hex. */
    val compromised: String,
    /** Id of the commitment (kind 1776) that named [recovery]. */
    val commitmentId: String,
    /** Since when the account is no longer in its owner's hands, seconds since 1970. */
    val since: Long,
    val createdAt: Long,
    /** Id of the OpenTimestamps event (kind 1040) of the commitment, when known. */
    val proof: String?,
    /** The new account named by its owner: a claim, never a proof of identity. */
    val successor: String?,
)

/** Why a notice was refused; the same reasons on the device and on the server. */
enum class NoticeProblem {
    /** Not a notice, a tag missing, repeated or malformed. */
    MALFORMED,

    /** The id or the signature does not match the content. */
    BAD_SIGNATURE,

    /** The commitment given is not a valid commitment, or not the one the notice names. */
    NOT_THE_COMMITMENT,

    /** The notice is not signed by the recovery key named by the commitment of the account. */
    WRONG_RECOVERY_KEY,

    /** "Compromised since" more than 30 days before the notice, or after it (D-134). */
    SINCE_OUT_OF_BOUNDS,

    /** The notice is dated in the future. */
    FROM_THE_FUTURE,
}

/** The answer of [AccountRecovery.checkNotice]. */
sealed interface NoticeCheck {
    data class Valid(val notice: CompromiseNotice) : NoticeCheck
    data class Invalid(val problem: NoticeProblem) : NoticeCheck
}

/**
 * "Déclarer mon compte compromis" (S-146, D-134, docs/integration/compte-compromis.md): the
 * builders, parsers and checks of the two events, shared by the devices and the server.
 *
 * - **Commitment** (kind 1776, the format of the NIP-41 draft): signed by the account key at its
 *   creation, it names the recovery key derived from the phrase ([RecoveryPhrase.deriveRecoveryKeys]):
 *   one `p` tag, `["alt","pubkey whitelisting event"]`, `["ppf-recovery","1"]`, empty content.
 * - **Notice** (kind 1777): signed by the recovery key, `p` = compromised key, `e` = id of the
 *   commitment, `proof` = id of the OpenTimestamps event (kind 1040) of the commitment when known,
 *   `["alt","pubkey migration event"]`, `["ppf-compromised-since", <seconds>]` (at most 30 days
 *   before the notice) and optionally `["ppf-successor", <hex>]`.
 *
 * Which commitment is the first of an account is not decided here: the server records it (and will
 * stamp it, OpenTimestamps), and the device asks the server.
 */
object AccountRecovery {
    const val COMMITMENT_KIND: Int = 1776
    const val NOTICE_KIND: Int = 1777

    /** NIP-03 OpenTimestamps attestation, named by the `proof` tag of a notice. */
    const val TIMESTAMP_KIND: Int = 1040

    const val ALT_TAG: String = "alt"
    const val COMMITMENT_ALT: String = "pubkey whitelisting event"
    const val NOTICE_ALT: String = "pubkey migration event"
    const val RECOVERY_TAG: String = "ppf-recovery"
    const val RECOVERY_VERSION: String = "1"
    const val PROOF_TAG: String = "proof"
    const val SINCE_TAG: String = "ppf-compromised-since"
    const val SUCCESSOR_TAG: String = "ppf-successor"

    /** "Compromised since" may go back at most 30 days before the notice (D-134, decision 2). */
    const val MAX_BACKDATING_SECONDS: Long = 30L * 24 * 60 * 60

    /** Clock difference tolerated between a device and the server for the date of a notice. */
    const val MAX_CLOCK_SKEW_SECONDS: Long = 10L * 60

    /** The unsigned commitment of [account] to [recovery]. */
    fun commitment(account: PublicKey, recovery: PublicKey, createdAt: Long): UnsignedEvent {
        require(account != recovery) { "The recovery key must differ from the account key" }
        return UnsignedEvent.create(
            account,
            COMMITMENT_KIND,
            createdAt,
            tags = listOf(
                listOf("p", recovery.hex),
                listOf(ALT_TAG, COMMITMENT_ALT),
                listOf(RECOVERY_TAG, RECOVERY_VERSION),
            ),
        )
    }

    /** Signs the commitment with the account key held by [account]. */
    suspend fun signCommitment(account: EventSigner, recovery: PublicKey, createdAt: Long = currentEpochSeconds()): SignedEvent =
        account.sign(commitment(account.publicKey, recovery, createdAt))

    /**
     * The unsigned notice that [commitment]'s account is compromised since [since], signed by its
     * recovery key [recovery]. [since] defaults to the notice date; it may go back 30 days at most.
     */
    fun notice(
        recovery: PublicKey,
        commitment: RecoveryCommitment,
        createdAt: Long,
        since: Long = createdAt,
        proof: String? = null,
        successor: PublicKey? = null,
    ): UnsignedEvent {
        require(recovery.hex == commitment.recovery) { "The notice must be signed by the recovery key of the commitment" }
        require(since in (createdAt - MAX_BACKDATING_SECONDS)..createdAt) { "\"Compromised since\" goes back 30 days at most" }
        require(proof == null || isHex64(proof)) { "proof must be an event id" }
        require(successor == null || (successor.hex != commitment.account && successor != recovery)) {
            "The successor must be a new key"
        }
        val tags = buildList {
            add(listOf("p", commitment.account))
            add(listOf("e", commitment.id))
            proof?.let { add(listOf(PROOF_TAG, it)) }
            add(listOf(ALT_TAG, NOTICE_ALT))
            add(listOf(SINCE_TAG, since.toString()))
            successor?.let { add(listOf(SUCCESSOR_TAG, it.hex)) }
        }
        return UnsignedEvent.create(recovery, NOTICE_KIND, createdAt, tags)
    }

    /** Signs the notice with the recovery key held by [recovery] (derived from the phrase typed again). */
    suspend fun signNotice(
        recovery: EventSigner,
        commitment: RecoveryCommitment,
        createdAt: Long = currentEpochSeconds(),
        since: Long = createdAt,
        proof: String? = null,
        successor: PublicKey? = null,
    ): SignedEvent = recovery.sign(notice(recovery.publicKey, commitment, createdAt, since, proof, successor))

    /**
     * The commitment carried by [event], or null when it is not an authentic commitment of our
     * form: kind 1776, valid signature, exactly one `p` naming another key, `ppf-recovery` = 1,
     * empty content.
     */
    fun parseCommitment(event: SignedEvent): RecoveryCommitment? {
        if (event.kind != COMMITMENT_KIND || event.content.isNotEmpty()) return null
        val recovery = event.tagValues("p").singleOrNull()?.takeIf(::isHex64) ?: return null
        if (recovery == event.pubkey) return null
        if (event.tagValues(RECOVERY_TAG).singleOrNull() != RECOVERY_VERSION) return null
        if (!event.verify()) return null
        return RecoveryCommitment(event.id, event.pubkey, recovery, event.createdAt)
    }

    /**
     * Reads the form of a notice without its commitment: tags, signature and the 30-day bound of
     * "compromised since". The link to the commitment is checked by [checkNotice].
     */
    fun parseNotice(event: SignedEvent): NoticeCheck {
        if (event.kind != NOTICE_KIND) return NoticeCheck.Invalid(NoticeProblem.MALFORMED)
        val compromised = event.tagValues("p").singleOrNull()?.takeIf(::isHex64)
        val commitmentId = event.tagValues("e").singleOrNull()?.takeIf(::isHex64)
        val sinceValues = event.tagValues(SINCE_TAG)
        val since = sinceValues.singleOrNull()?.takeIf { it.isNotEmpty() && it.length <= 12 && it.all(Char::isDigit) }?.toLong()
        val proofs = event.tagValues(PROOF_TAG)
        val successors = event.tagValues(SUCCESSOR_TAG)
        if (compromised == null || commitmentId == null || since == null || compromised == event.pubkey ||
            proofs.size > 1 || proofs.any { !isHex64(it) } ||
            successors.size > 1 || successors.any { !isHex64(it) || it == compromised || it == event.pubkey }
        ) {
            return NoticeCheck.Invalid(NoticeProblem.MALFORMED)
        }
        if (!event.verify()) return NoticeCheck.Invalid(NoticeProblem.BAD_SIGNATURE)
        if (since > event.createdAt || since < event.createdAt - MAX_BACKDATING_SECONDS) {
            return NoticeCheck.Invalid(NoticeProblem.SINCE_OUT_OF_BOUNDS)
        }
        return NoticeCheck.Valid(
            CompromiseNotice(
                id = event.id,
                recovery = event.pubkey,
                compromised = compromised,
                commitmentId = commitmentId,
                since = since,
                createdAt = event.createdAt,
                proof = proofs.singleOrNull(),
                successor = successors.singleOrNull(),
            ),
        )
    }

    /**
     * Checks [notice] against [commitment], the first commitment of the account it names (as the
     * server recorded it): the form ([parseNotice]), then that the notice names this commitment and
     * is signed by its recovery key. With [now], a notice dated more than
     * [MAX_CLOCK_SKEW_SECONDS] in the future is refused (it would stretch "compromised since").
     */
    fun checkNotice(notice: SignedEvent, commitment: SignedEvent, now: Long? = null): NoticeCheck {
        val parsed = when (val check = parseNotice(notice)) {
            is NoticeCheck.Invalid -> return check
            is NoticeCheck.Valid -> check.notice
        }
        if (now != null && parsed.createdAt > now + MAX_CLOCK_SKEW_SECONDS) return NoticeCheck.Invalid(NoticeProblem.FROM_THE_FUTURE)
        val committed = parseCommitment(commitment) ?: return NoticeCheck.Invalid(NoticeProblem.NOT_THE_COMMITMENT)
        return checkNotice(parsed, committed)
    }

    /** The link of an already parsed [notice] to the [commitment] of its account. */
    fun checkNotice(notice: CompromiseNotice, commitment: RecoveryCommitment): NoticeCheck = when {
        commitment.id != notice.commitmentId || commitment.account != notice.compromised ->
            NoticeCheck.Invalid(NoticeProblem.NOT_THE_COMMITMENT)
        commitment.recovery != notice.recovery -> NoticeCheck.Invalid(NoticeProblem.WRONG_RECOVERY_KEY)
        else -> NoticeCheck.Valid(notice)
    }

    private fun isHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
