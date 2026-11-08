package org.politisationpopulairefrancaise.server.accounts

import kotlinx.serialization.Serializable
import org.politisationpopulairefrancaise.core.identity.AccountRecovery
import org.politisationpopulairefrancaise.core.identity.NoticeCheck
import org.politisationpopulairefrancaise.core.identity.NoticeProblem
import org.politisationpopulairefrancaise.core.identity.RecoveryCommitment
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Stamps a stored commitment (kind 1776) with OpenTimestamps, to prove it existed before any
 * compromise. Hook for Décider's `ArchiveService.stamp(sha256)` (server/archive, not in main yet,
 * docs/integration/compte-compromis.md, section 6 bis): until it is plugged, [NONE] does nothing
 * and "the first commitment" is the first one received. Called after the commit; must return quickly.
 */
fun interface CommitmentStamper {
    fun stamp(commitment: SignedEvent)

    companion object {
        val NONE: CommitmentStamper = CommitmentStamper { }
    }
}

/** `GET /api/v1/accounts/{pubkey}/status`: what anyone may know of an account's recovery. */
@Serializable
data class AccountStatus(
    val compromised: Boolean,
    /** "Compromised since", seconds; the events of the key dated after it are suspect. */
    val since: Long? = null,
    /** The new account named by the notice: a claim, never a proof of identity. */
    val successor: String? = null,
    /** The signed notice (kind 1777), for the device to check itself. */
    val notice: SignedEvent? = null,
    /** Id of the first commitment (kind 1776) of the key: null while the account has no recovery key. */
    val commitment: String? = null,
)

/**
 * The recovery keys and compromised accounts (S-146, D-134, docs/integration/compte-compromis.md).
 * Only public keys and event ids are kept; the events themselves are public and stay in `events`.
 *
 * - The first commitment (kind 1776) of a key is recorded; a second one is refused (`409 recovery_already_set`).
 * - A notice (kind 1777) is accepted once per key, only if signed by the recovery key of that first
 *   commitment and dated within its bounds ([AccountRecovery.checkNotice]); a successor names one
 *   predecessor at most (`409 successor_taken`).
 * - From then on, every event of the compromised key is refused ([refusal], `403 account_compromised`),
 *   its mailbox push subscriptions are deleted and its room roles removed. Its events dated after
 *   "since" stay served; the device asks [status] to mark them.
 *
 * The check and the storage of both kinds happen in one transaction under a lock per account, so
 * that two commitments or two notices arriving at once cannot both count.
 */
class CompromisedAccounts(
    private val events: EventRepository,
    private val stamper: CommitmentStamper = CommitmentStamper.NONE,
    private val clock: () -> Instant = Instant::now,
) {
    private val dataSource get() = events.dataSource

    /** The answer of [admitAndStore]. */
    sealed interface Admission {
        data class Refused(val refusal: PublicationPolicy.Decision.Refused) : Admission
        data class Stored(val outcome: StoreOutcome) : Admission
    }

    /** True once a valid notice was stored for [pubkey]. Called by Décider in `PersonAccounts.refusal`. */
    fun isCompromised(pubkey: String): Boolean = dataSource.connection.use { isCompromised(it, pubkey) }

    /** The key named as successor by the notice of [old], if any (Décider: it inherits the issuance cursor). */
    fun successorOf(old: String): String? = single("SELECT successor FROM compromised_account WHERE pubkey = ?", old)

    /** The compromised key that named [new] as its successor, if any. */
    fun predecessorOf(new: String): String? = single("SELECT pubkey FROM compromised_account WHERE successor = ?", new)

    /** The stored notice of [pubkey], if it is compromised. */
    fun noticeFor(pubkey: String): SignedEvent? =
        single("SELECT notice_id FROM compromised_account WHERE pubkey = ?", pubkey)?.let(events::find)

    /** The first commitment of [pubkey], if any. */
    fun commitmentOf(pubkey: String): RecoveryCommitment? = dataSource.connection.use { commitmentOf(it, pubkey) }

    fun status(pubkey: String): AccountStatus = dataSource.connection.use { connection ->
        val commitment = commitmentOf(connection, pubkey)?.id
        connection.prepareStatement("SELECT notice_id, since, successor FROM compromised_account WHERE pubkey = ?").use { statement ->
            statement.setString(1, pubkey)
            statement.executeQuery().use { rows ->
                if (!rows.next()) {
                    AccountStatus(compromised = false, commitment = commitment)
                } else {
                    AccountStatus(
                        compromised = true,
                        since = rows.getLong("since"),
                        successor = rows.getString("successor"),
                        notice = events.find(rows.getString("notice_id")),
                        commitment = commitment,
                    )
                }
            }
        }
    }

    /**
     * How a content held for review is treated once its key is declared compromised (decision of the
     * team lead, 8 November 2026): created on or after "compromised since", it is refused; created
     * before, it stays in the queue with a mention visible to the team.
     */
    fun heldVerdict(pubkey: String, createdAt: Long): HeldVerdict {
        val since = sinceOf(pubkey) ?: return HeldVerdict.Clear
        return if (createdAt >= since) HeldVerdict.CreatedAfter(since) else HeldVerdict.CreatedBefore(since)
    }

    /** "Compromised since" of [pubkey], seconds since 1970, or null when it is not compromised. */
    fun sinceOf(pubkey: String): Long? = single("SELECT since::text FROM compromised_account WHERE pubkey = ?", pubkey)?.toLong()

    sealed interface HeldVerdict {
        data object Clear : HeldVerdict
        data class CreatedBefore(val since: Long) : HeldVerdict
        data class CreatedAfter(val since: Long) : HeldVerdict
    }

    /** `403 account_compromised` for any event of a compromised key; null when the event may go on. */
    fun refusal(event: SignedEvent): PublicationPolicy.Decision.Refused? =
        if (isCompromised(event.pubkey)) COMPROMISED else null

    /** True for the two kinds this class checks and stores itself ([admitAndStore]). */
    fun concerns(event: SignedEvent): Boolean =
        event.kind == AccountRecovery.COMMITMENT_KIND || event.kind == AccountRecovery.NOTICE_KIND

    /** Checks a commitment or a notice, then stores it, in one transaction under the lock of the account. */
    fun admitAndStore(event: SignedEvent): Admission {
        // Neither kind is ever sent to a room: the rules of the rooms do not apply to them.
        if (event.tags.any { it.firstOrNull() == "h" }) return refused(400, "unsupported_tag", "A recovery event is never sent to a room.")
        val admission = try {
            when (event.kind) {
                AccountRecovery.COMMITMENT_KIND -> admitCommitment(event)
                else -> admitNotice(event)
            }
        } catch (e: SQLException) {
            // Two notices naming the same successor at the same instant: the unique index decides.
            if (e.sqlState == UNIQUE_VIOLATION && event.kind == AccountRecovery.NOTICE_KIND) SUCCESSOR_TAKEN else throw e
        }
        if (event.kind == AccountRecovery.COMMITMENT_KIND && admission == Admission.Stored(StoreOutcome.STORED)) {
            runCatching { stamper.stamp(event) }.onFailure { log.warn("Commitment stamping failed: ${it.javaClass.simpleName}") }
        }
        return admission
    }

    private fun admitCommitment(event: SignedEvent): Admission {
        val commitment = AccountRecovery.parseCommitment(event)
            ?: return refused(400, "invalid_commitment", "A commitment names one other key (p tag), carries [\"ppf-recovery\",\"1\"] and has no content.")
        return events.transaction { connection ->
            lock(connection, commitment.account)
            if (commitmentOf(connection, commitment.account) != null) {
                Admission.Refused(RECOVERY_ALREADY_SET)
            } else {
                val outcome = events.storeIn(connection, event)
                if (outcome == StoreOutcome.STORED) {
                    connection.prepareStatement(
                        "INSERT INTO recovery_commitment (pubkey, recovery_pubkey, event_id, received_at) VALUES (?, ?, ?, ?)",
                    ).use { statement ->
                        statement.setString(1, commitment.account)
                        statement.setString(2, commitment.recovery)
                        statement.setString(3, commitment.id)
                        statement.setTimestamp(4, Timestamp.from(clock()))
                        statement.executeUpdate()
                    }
                }
                Admission.Stored(outcome)
            }
        }
    }

    private fun admitNotice(event: SignedEvent): Admission {
        val notice = when (val parsed = AccountRecovery.parseNotice(event)) {
            is NoticeCheck.Invalid -> return invalidNotice(parsed.problem)
            is NoticeCheck.Valid -> parsed.notice
        }
        if (notice.createdAt > clock().epochSecond + AccountRecovery.MAX_CLOCK_SKEW_SECONDS) return invalidNotice(NoticeProblem.FROM_THE_FUTURE)
        return events.transaction { connection ->
            lock(connection, notice.compromised)
            val commitment = commitmentOf(connection, notice.compromised)
            when {
                isCompromised(connection, notice.compromised) -> Admission.Refused(ALREADY_COMPROMISED)
                commitment == null -> invalidNotice(NoticeProblem.NOT_THE_COMMITMENT)
                else -> when (val check = AccountRecovery.checkNotice(notice, commitment)) {
                    is NoticeCheck.Invalid -> invalidNotice(check.problem)
                    is NoticeCheck.Valid -> {
                        val successor = notice.successor
                        when {
                            successor != null && isCompromised(connection, successor) ->
                                refused(400, "invalid_notice", "The successor named by the notice is itself a compromised account.")
                            successor != null && isSuccessor(connection, successor) -> SUCCESSOR_TAKEN
                            else -> {
                                val outcome = events.storeIn(connection, event)
                                if (outcome == StoreOutcome.STORED) compromise(connection, notice.compromised, notice.id, notice.since, successor)
                                Admission.Stored(outcome)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Records the compromise and cuts what the key could still do: its mailbox wake-ups (M31) and its
     * roles in the rooms (D-131). Nothing goes to the legal register: roles are not contents. The room
     * keeps its journal; a room whose only administrator was this key has none left: it enters the
     * queue groups.orphans of the team (docs/integration/salons-reprise.md).
     */
    private fun compromise(connection: Connection, pubkey: String, noticeId: String, since: Long, successor: String?) {
        connection.prepareStatement(
            "INSERT INTO compromised_account (pubkey, notice_id, since, successor, received_at) VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, pubkey)
            statement.setString(2, noticeId)
            statement.setLong(3, since)
            statement.setString(4, successor)
            statement.setTimestamp(5, Timestamp.from(clock()))
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM push_subscription WHERE scope = 'mailbox' AND pubkey = ?").use { statement ->
            statement.setString(1, pubkey)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO group_orphan (group_id, compromised_pubkey, notice_id, orphaned_at) " +
                "SELECT r.group_id, r.pubkey, ?, ? FROM group_role r WHERE r.pubkey = ? AND r.role = 'admin' " +
                "AND NOT EXISTS (SELECT 1 FROM group_role o WHERE o.group_id = r.group_id AND o.role = 'admin' AND o.pubkey <> r.pubkey) " +
                // Orphaned again after a takeover: a new item for the team.
                "ON CONFLICT (group_id) DO UPDATE SET compromised_pubkey = EXCLUDED.compromised_pubkey, notice_id = EXCLUDED.notice_id, " +
                "orphaned_at = EXCLUDED.orphaned_at, decision = NULL, decided_at = NULL",
        ).use { statement ->
            statement.setString(1, noticeId)
            statement.setTimestamp(2, Timestamp.from(clock()))
            statement.setString(3, pubkey)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM group_role WHERE pubkey = ?").use { statement ->
            statement.setString(1, pubkey)
            statement.executeUpdate()
        }
    }

    private fun commitmentOf(connection: Connection, pubkey: String): RecoveryCommitment? =
        connection.prepareStatement(
            "SELECT c.recovery_pubkey, c.event_id, e.created_at FROM recovery_commitment c JOIN events e ON e.id = c.event_id WHERE c.pubkey = ?",
        ).use { statement ->
            statement.setString(1, pubkey)
            statement.executeQuery().use { rows ->
                if (rows.next()) RecoveryCommitment(rows.getString(2), pubkey, rows.getString(1), rows.getLong(3)) else null
            }
        }

    private fun isCompromised(connection: Connection, pubkey: String): Boolean = exists(connection, "SELECT 1 FROM compromised_account WHERE pubkey = ?", pubkey)

    private fun isSuccessor(connection: Connection, pubkey: String): Boolean = exists(connection, "SELECT 1 FROM compromised_account WHERE successor = ?", pubkey)

    private fun exists(connection: Connection, sql: String, value: String): Boolean =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { it.next() }
        }

    private fun single(sql: String, value: String): String? = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun invalidNotice(problem: NoticeProblem): Admission = refused(
        400,
        "invalid_notice",
        when (problem) {
            NoticeProblem.MALFORMED -> "A notice carries one p (the compromised key), one e (its commitment) and one ppf-compromised-since tag; proof and ppf-successor at most once."
            NoticeProblem.BAD_SIGNATURE -> "The id or the signature does not match the content."
            NoticeProblem.NOT_THE_COMMITMENT -> "The notice does not name the first commitment of this account."
            NoticeProblem.WRONG_RECOVERY_KEY -> "The notice is not signed by the recovery key of this account."
            NoticeProblem.SINCE_OUT_OF_BOUNDS -> "\"Compromised since\" goes back 30 days at most before the notice, and not after it."
            NoticeProblem.FROM_THE_FUTURE -> "The notice is dated in the future."
        },
    )

    private fun refused(status: Int, error: String, message: String): Admission =
        Admission.Refused(PublicationPolicy.Decision.Refused(status, error, message))

    companion object {
        private val log = LoggerFactory.getLogger(CompromisedAccounts::class.java)

        /** Namespace of the account locks (the two-key form of the advisory locks); "PPFR" in ASCII. */
        private const val LOCK_NAMESPACE: Int = 0x50504652

        /** PostgreSQL SQLSTATE of a unique index violation. */
        private const val UNIQUE_VIOLATION = "23505"

        val COMPROMISED = PublicationPolicy.Decision.Refused(
            403,
            "account_compromised",
            "This account was declared compromised by its owner: nothing more is accepted from this key.",
        )
        private val RECOVERY_ALREADY_SET = PublicationPolicy.Decision.Refused(
            409,
            "recovery_already_set",
            "This account already has a recovery key: only its first commitment counts.",
        )
        private val ALREADY_COMPROMISED = PublicationPolicy.Decision.Refused(409, "already_compromised", "This account is already declared compromised.")
        private val SUCCESSOR_TAKEN = Admission.Refused(
            PublicationPolicy.Decision.Refused(409, "successor_taken", "This key is already the successor of another account."),
        )

        private fun lock(connection: Connection, pubkey: String) {
            connection.prepareStatement("SELECT pg_advisory_xact_lock(?, ?)").use { statement ->
                statement.setInt(1, LOCK_NAMESPACE)
                statement.setInt(2, pubkey.hashCode())
                statement.execute()
            }
        }
    }
}
