package org.politisationpopulairefrancaise.server.urne

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.urne.CertificationAnswer
import org.politisationpopulairefrancaise.core.urne.PublicVote
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import org.politisationpopulairefrancaise.server.legal.LegalRegister
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** An account registered to vote (table vote_account of V13__urnes.sql). */
data class VoteAccount(
    val pubkey: String,
    val level: VoteLevel,
    val levelWeek: LocalDate,
    val previousLevel: VoteLevel,
    val registeredWeek: LocalDate,
    val issuedUpTo: Long,
    val lastRequest: ByteArray?,
    val lastResponse: String?,
    val lastResponseDay: LocalDate?,
) {
    /** The level this account had the week [week] (D-110, 6.4: the level is frozen at the opening). */
    fun levelAt(week: LocalDate): VoteLevel = when {
        registeredWeek > week -> VoteLevel.NON_VERIFIE
        levelWeek <= week -> level
        else -> previousLevel
    }
}

/** A ballot box (table urne). */
data class Urne(
    val number: Long,
    val id: String,
    val subject: String,
    val choices: List<String>,
    val seedVersion: Int,
    val opensAt: Instant,
    val closesAt: Instant?,
    val openedWeek: LocalDate,
    val liveResults: Boolean,
    val closed: Boolean,
    /** Drawn at the opening, part of the derivation of its keys ([UrneKeyInfo]); never published. */
    val salt: ByteArray,
) {
    fun isOpenAt(now: Instant): Boolean = !closed && !opensAt.isAfter(now) && (closesAt == null || closesAt.isAfter(now))
}

data class StoredBallot(val y: ByteArray, val token: ByteArray, val level: VoteLevel, val choice: String, val hour: Instant)

/** A certification of an elected official's account (table elected_certification). */
data class Certification(
    val pubkey: String,
    val mandate: String,
    val mandateType: String,
    val territory: String,
    /** One of the constants of [CertificationAnswer]. */
    val status: String,
    val requestedWeek: LocalDate,
    val decidedWeek: LocalDate?,
    val reason: String?,
)

/** What [UrneStore.requestCertification] did. */
enum class CertificationWrite { RECORDED, ALREADY_CERTIFIED, MANDATE_TAKEN }

/** What [UrneStore.addPendingValidation] did. */
enum class PendingValidation { ADDED, ALREADY, FULL }

/** What [UrneStore.recordPublicVote] did. */
enum class PublicVoteWrite {
    RECORDED,

    /** A vote as recent or newer is stored: nothing changed. */
    OLDER,

    /** The box closed (or is unknown) when the transaction locked it: nothing changed. */
    CLOSED,
}

/** What [UrneStore.deliver] did. */
enum class Delivery { DONE, CURSOR_MOVED }

/**
 * The SQL of the ballot boxes. Nothing here ever reads or writes anything about a request; the only
 * link between an account and a ballot box is the account's cursor, which says "served up to box n"
 * for every account alike (D-110, 6.3).
 */
class UrneStore(
    internal val dataSource: DataSource,
    /** The legal register (D-077): the public votes of the elected officials enter it, in their own transaction. */
    private val register: LegalRegister = LegalRegister(),
) {

    private val choicesJson = ListSerializer(String.serializer())

    // ------------------------------------------------------------------ accounts

    /** Registers [pubkey] as not verified; false when it already was. */
    fun register(pubkey: String, week: LocalDate): Boolean = dataSource.connection.use { c ->
        c.prepareStatement(
            "INSERT INTO vote_account (pubkey, level, level_week, previous_level, registered_week) VALUES (?, 'non-verifie', ?, 'non-verifie', ?) ON CONFLICT DO NOTHING",
        ).use { s ->
            s.setString(1, pubkey)
            s.setObject(2, week)
            s.setObject(3, week)
            s.executeUpdate() == 1
        }
    }

    fun account(pubkey: String): VoteAccount? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM vote_account WHERE pubkey = ?").use { s ->
            s.setString(1, pubkey)
            s.executeQuery().use { r -> if (r.next()) r.account() else null }
        }
    }

    /**
     * A new level from [week] on (validation, certification, demotion; step 5 of the design), decided
     * during the week [currentWeek]. A change that has not taken effect yet is replaced, and the level
     * before it stays the previous one: the boxes opened meanwhile keep the level in force (review R5).
     */
    fun setLevel(pubkey: String, level: VoteLevel, week: LocalDate, currentWeek: LocalDate): Boolean =
        dataSource.connection.use { c -> setLevel(c, pubkey, level, week, currentWeek) }

    /** The same inside the transaction of [c]. */
    fun setLevel(c: Connection, pubkey: String, level: VoteLevel, week: LocalDate, currentWeek: LocalDate): Boolean =
        c.prepareStatement(
            "UPDATE vote_account SET previous_level = CASE WHEN level_week <= ? THEN level ELSE previous_level END, level = ?, level_week = ? WHERE pubkey = ?",
        ).use { s ->
            s.setObject(1, currentWeek)
            s.setString(2, level.code)
            s.setObject(3, week)
            s.setString(4, pubkey)
            s.executeUpdate() == 1
        }

    fun accountsByLevel(): Map<String, Long> = dataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT level, count(*) FROM vote_account GROUP BY level").use { r ->
                val counts = VoteLevel.entries.associate { it.code to 0L }.toMutableMap()
                while (r.next()) counts[r.getString(1)] = r.getLong(2)
                counts
            }
        }
    }

    /**
     * Moves the cursor of [pubkey] from [from] to [to] and counts one token per (box, level) of
     * [issued], atomically. If the cursor moved meanwhile (two requests at once), nothing is done.
     */
    fun deliver(pubkey: String, from: Long, to: Long, issued: List<Pair<Long, VoteLevel>>, requestHash: ByteArray, response: String, day: LocalDate): Delivery =
        transaction { c ->
            val moved = c.prepareStatement(
                "UPDATE vote_account SET issued_up_to = ?, last_request = ?, last_response = ?, last_response_day = ? WHERE pubkey = ? AND issued_up_to = ?",
            ).use { s ->
                s.setLong(1, to)
                s.setBytes(2, requestHash)
                s.setString(3, response)
                s.setObject(4, day)
                s.setString(5, pubkey)
                s.setLong(6, from)
                s.executeUpdate()
            }
            if (moved != 1) {
                c.rollback()
                return@transaction Delivery.CURSOR_MOVED
            }
            c.prepareStatement(
                "INSERT INTO urne_issued (urne_number, level, issued) VALUES (?, ?, 1) ON CONFLICT (urne_number, level) DO UPDATE SET issued = urne_issued.issued + 1",
            ).use { s ->
                issued.forEach { (number, level) ->
                    s.setLong(1, number)
                    s.setString(2, level.code)
                    s.addBatch()
                }
                s.executeBatch()
            }
            Delivery.DONE
        }

    // ------------------------------------------------------------------ ballot boxes

    /** Opens a ballot box with the next number; returns it. */
    fun open(id: String, subject: String, choices: List<String>, seedVersion: Int, opensAt: Instant, closesAt: Instant?, openedWeek: LocalDate, liveResults: Boolean, salt: ByteArray): Urne =
        transaction { c ->
            // One opening at a time: numbers are consecutive and never reused.
            c.createStatement().use { it.execute("LOCK TABLE urne IN SHARE ROW EXCLUSIVE MODE") }
            val number = c.createStatement().use { s -> s.executeQuery("SELECT coalesce(max(number), 0) + 1 FROM urne").use { r -> r.next(); r.getLong(1) } }
            c.prepareStatement(
                "INSERT INTO urne (number, id, subject, choices, seed_version, opens_at, closes_at, opened_week, live_results, salt) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)",
            ).use { s ->
                s.setLong(1, number)
                s.setString(2, id)
                s.setString(3, subject)
                s.setString(4, Json.encodeToString(choicesJson, choices))
                s.setInt(5, seedVersion)
                s.setTimestamp(6, Timestamp.from(opensAt))
                s.setTimestamp(7, closesAt?.let(Timestamp::from))
                s.setObject(8, openedWeek)
                s.setBoolean(9, liveResults)
                s.setBytes(10, salt)
                s.executeUpdate()
            }
            Urne(number, id, subject, choices, seedVersion, opensAt, closesAt, openedWeek, liveResults, closed = false, salt = salt)
        }

    fun urne(number: Long): Urne? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM urne WHERE number = ?").use { s ->
            s.setLong(1, number)
            s.executeQuery().use { r -> if (r.next()) r.urne() else null }
        }
    }

    fun urneById(id: String): Urne? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM urne WHERE id = ?").use { s ->
            s.setString(1, id)
            s.executeQuery().use { r -> if (r.next()) r.urne() else null }
        }
    }

    fun urnes(after: Long, limit: Int): List<Urne> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM urne WHERE number > ? ORDER BY number LIMIT ?").use { s ->
            s.setLong(1, after)
            s.setInt(2, limit)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.urne() else null }.toList() }
        }
    }

    /** Ballot boxes open at [now] numbered after [after], in order. */
    fun openAfter(after: Long, now: Instant, limit: Int): List<Urne> = dataSource.connection.use { c ->
        c.prepareStatement(
            "SELECT * FROM urne WHERE number > ? AND NOT closed AND opens_at <= ? AND (closes_at IS NULL OR closes_at > ?) ORDER BY number LIMIT ?",
        ).use { s ->
            s.setLong(1, after)
            s.setTimestamp(2, Timestamp.from(now))
            s.setTimestamp(3, Timestamp.from(now))
            s.setInt(4, limit)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.urne() else null }.toList() }
        }
    }

    /**
     * Brings the end of the open ballot box [id] forward to [at]: only when it has no end or a later
     * one, so that an end is never pushed back. Returns whether the end changed.
     */
    fun bringEndForward(id: String, at: Instant): Boolean = dataSource.connection.use { c ->
        c.prepareStatement("UPDATE urne SET closes_at = ? WHERE id = ? AND NOT closed AND (closes_at IS NULL OR closes_at > ?)").use { s ->
            s.setTimestamp(1, Timestamp.from(at))
            s.setString(2, id)
            s.setTimestamp(3, Timestamp.from(at))
            s.executeUpdate() == 1
        }
    }

    /** The open ballot boxes whose identifier starts with [prefix] (the boxes of one use, for one pole). */
    fun openWithPrefix(prefix: String): List<Urne> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM urne WHERE NOT closed AND starts_with(id, ?) ORDER BY number").use { s ->
            s.setString(1, prefix)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.urne() else null }.toList() }
        }
    }

    /** Closes every ballot box whose end has come; returns their numbers. */
    fun closeDue(now: Instant): List<Long> = dataSource.connection.use { c ->
        c.prepareStatement("UPDATE urne SET closed = true WHERE NOT closed AND closes_at IS NOT NULL AND closes_at <= ? RETURNING number").use { s ->
            s.setTimestamp(1, Timestamp.from(now))
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.getLong(1) else null }.toList() }
        }
    }

    // ------------------------------------------------------------------ closing statements (decider.urn_closure)

    /** Closed boxes whose closing statement is neither signed nor refused for good, oldest first. */
    fun closuresToSign(limit: Int): List<Long> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT number FROM urne WHERE closed AND closure_statement IS NULL AND closure_refused IS NULL ORDER BY number LIMIT ?").use { s ->
            s.setInt(1, limit)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.getLong(1) else null }.toList() }
        }
    }

    /** Signed closing statements not yet published, as (number, signed event JSON). */
    fun closuresToPublish(limit: Int): List<Pair<Long, String>> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT number, closure_statement FROM urne WHERE closure_statement IS NOT NULL AND NOT closure_published ORDER BY number LIMIT ?").use { s ->
            s.setInt(1, limit)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.getLong(1) to r.getString(2) else null }.toList() }
        }
    }

    /** Keeps the signed closing statement of [number] (once: a statement is never replaced). */
    fun recordClosureStatement(number: Long, signedJson: String): Boolean = update(
        "UPDATE urne SET closure_statement = ? WHERE number = ? AND closed AND closure_statement IS NULL",
    ) { s ->
        s.setString(1, signedJson)
        s.setLong(2, number)
    }

    fun markClosurePublished(number: Long): Boolean = update("UPDATE urne SET closure_published = true WHERE number = ? AND closure_statement IS NOT NULL") { s -> s.setLong(1, number) }

    fun markClosureRefused(number: Long, code: String): Boolean = update("UPDATE urne SET closure_refused = ? WHERE number = ? AND closure_statement IS NULL") { s ->
        s.setString(1, code.take(64))
        s.setLong(2, number)
    }

    /** The boxes whose closing statement was refused for good (an alert for the team). */
    fun refusedClosures(): List<Pair<Long, String>> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT number, closure_refused FROM urne WHERE closure_refused IS NOT NULL ORDER BY number").use { s ->
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.getLong(1) to r.getString(2) else null }.toList() }
        }
    }

    private fun update(sql: String, bind: (java.sql.PreparedStatement) -> Unit): Boolean = dataSource.connection.use { c ->
        c.prepareStatement(sql).use { s ->
            bind(s)
            s.executeUpdate() == 1
        }
    }

    /** Ballots of each box of [numbers], summed by (level, choice): the live results of a whole list at once. */
    fun countsByBox(numbers: List<Long>): Map<Long, Map<Pair<VoteLevel, String>, Long>> {
        if (numbers.isEmpty()) return emptyMap()
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT urne_number, level, choice, count(*) FROM ballot WHERE urne_number = ANY(?) GROUP BY urne_number, level, choice").use { s ->
                s.setArray(1, c.createArrayOf("bigint", numbers.toTypedArray()))
                s.executeQuery().use { r ->
                    val found = HashMap<Long, HashMap<Pair<VoteLevel, String>, Long>>()
                    while (r.next()) {
                        val level = VoteLevel.of(r.getString(2)) ?: continue
                        found.getOrPut(r.getLong(1)) { HashMap() }[level to r.getString(3)] = r.getLong(4)
                    }
                    found
                }
            }
        }
    }

    fun issuedByLevel(number: Long): Map<String, Long> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT level, issued FROM urne_issued WHERE urne_number = ?").use { s ->
            s.setLong(1, number)
            s.executeQuery().use { r ->
                val counts = VoteLevel.entries.associate { it.code to 0L }.toMutableMap()
                while (r.next()) counts[r.getString(1)] = r.getLong(2)
                counts
            }
        }
    }

    // ------------------------------------------------------------------ ballots

    /** Records or replaces (same y, same token) a ballot; true when it replaced an earlier one. */
    fun recordBallot(number: Long, y: ByteArray, token: ByteArray, level: VoteLevel, choice: String, hour: Instant): Boolean =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO ballot (urne_number, y, token, level, choice, received_hour) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (urne_number, y) DO UPDATE SET choice = excluded.choice, received_hour = excluded.received_hour,
                    replacements = ballot.replacements + 1
                WHERE ballot.token = excluded.token AND ballot.level = excluded.level
                RETURNING replacements
                """.trimIndent(),
            ).use { s ->
                s.setLong(1, number)
                s.setBytes(2, y)
                s.setBytes(3, token)
                s.setString(4, level.code)
                s.setString(5, choice)
                s.setTimestamp(6, Timestamp.from(hour))
                s.executeQuery().use { r -> r.next() && r.getInt(1) > 0 }
            }
        }

    /** The ballots of a box, sorted by their secret identifier (never by arrival, R1). */
    /**
     * Records the public vote of an elected official (D-115) and its operation in the legal register (D-077),
     * in one transaction: both or neither. The box is locked and checked open at [now] inside it (its public
     * votes are written one after the other; the anonymous ballots are not blocked). In the register: the
     * first vote is a creation, a newer one a replacement, a withdrawal (`retire`) a deletion of the vote it
     * removes; a withdrawal with no vote to remove records nothing there (no content), and a vote after a
     * withdrawal is a creation again.
     */
    fun recordPublicVote(vote: PublicVote.Read, now: Instant): PublicVoteWrite = transaction { c ->
        val open = c.prepareStatement("SELECT closed, opens_at, closes_at FROM urne WHERE number = ? FOR NO KEY UPDATE").use { s ->
            s.setLong(1, vote.urne)
            s.executeQuery().use { r ->
                r.next() && !r.getBoolean(1) && !r.getTimestamp(2).toInstant().isAfter(now) &&
                    (r.getTimestamp(3)?.toInstant()?.isAfter(now) ?: true)
            }
        }
        if (!open) {
            c.rollback()
            return@transaction PublicVoteWrite.CLOSED
        }
        val previous = c.prepareStatement("SELECT event->>'id', created_at, choice FROM public_vote WHERE urne_number = ? AND pubkey = ?").use { s ->
            s.setLong(1, vote.urne)
            s.setString(2, vote.official)
            s.executeQuery().use { r -> if (r.next()) Triple(r.getString(1), r.getLong(2), r.getString(3)) else null }
        }
        if (previous != null && previous.second >= vote.createdAt) {
            c.rollback()
            return@transaction PublicVoteWrite.OLDER
        }
        c.prepareStatement(
            """
            INSERT INTO public_vote (urne_number, pubkey, choice, created_at, event) VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (urne_number, pubkey) DO UPDATE SET choice = excluded.choice, created_at = excluded.created_at, event = excluded.event
            """.trimIndent(),
        ).use { s ->
            s.setLong(1, vote.urne)
            s.setString(2, vote.official)
            s.setString(3, vote.choice)
            s.setLong(4, vote.createdAt)
            s.setString(5, Json.encodeToString(SignedEvent.serializer(), vote.event))
            s.executeUpdate()
        }
        val removed = previous?.takeIf { it.third != UrneInfo.WITHDRAWN }?.let { LegalRegister.ReplacedVersion(it.first, it.second, vote.official) }
        when {
            vote.choice == UrneInfo.WITHDRAWN -> removed?.let { register.recordExternal(c, LegalRegister.Operation.DELETE, vote.event, it) }
            removed == null -> register.recordExternal(c, LegalRegister.Operation.CREATE, vote.event, null)
            else -> register.recordExternal(c, LegalRegister.Operation.REPLACE, vote.event, removed)
        }
        PublicVoteWrite.RECORDED
    }

    /** The signed public votes of one box (JSON of the events), by official. */
    fun publicVotes(number: Long): List<String> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT event::text FROM public_vote WHERE urne_number = ? ORDER BY pubkey").use { s ->
            s.setLong(1, number)
            s.executeQuery().use { r -> generateSequence { if (r.next()) r.getString(1) else null }.toList() }
        }
    }

    /** The public votes of the boxes of [numbers], summed by choice (withdrawn ones excluded). */
    fun publicCounts(numbers: List<Long>): Map<Long, Map<String, Long>> {
        if (numbers.isEmpty()) return emptyMap()
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT urne_number, choice, count(*) FROM public_vote WHERE urne_number = ANY(?) AND choice <> ? GROUP BY urne_number, choice").use { s ->
                s.setArray(1, c.createArrayOf("bigint", numbers.toTypedArray()))
                s.setString(2, UrneInfo.WITHDRAWN)
                s.executeQuery().use { r ->
                    val found = HashMap<Long, HashMap<String, Long>>()
                    while (r.next()) found.getOrPut(r.getLong(1)) { HashMap() }[r.getString(2)] = r.getLong(3)
                    found
                }
            }
        }
    }

    fun ballots(number: Long): List<StoredBallot> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT y, token, level, choice, received_hour FROM ballot WHERE urne_number = ? ORDER BY y").use { s ->
            s.setLong(1, number)
            s.executeQuery().use { r ->
                generateSequence {
                    if (!r.next()) {
                        null
                    } else {
                        StoredBallot(r.getBytes(1), r.getBytes(2), VoteLevel.of(r.getString(3))!!, r.getString(4), r.getTimestamp(5).toInstant())
                    }
                }.toList()
            }
        }
    }

    fun replacements(number: Long): Long = dataSource.connection.use { c ->
        c.prepareStatement("SELECT coalesce(sum(replacements), 0) FROM ballot WHERE urne_number = ?").use { s ->
            s.setLong(1, number)
            s.executeQuery().use { r -> r.next(); r.getLong(1) }
        }
    }

    // ------------------------------------------------------------------ in-person validation (D-112, 4.3; option B of the DT)

    /**
     * Adds the validation of [pubkey] to the ones waiting for next Monday, with its [sealed] link (JSON of
     * the AuditSealer), and lets [count] count it in its session, in one transaction: both or neither.
     * [PendingValidation.ALREADY] when the account already waits (nothing counted); [PendingValidation.FULL]
     * when [count] refuses (the session is full or no longer usable: nothing written).
     */
    fun addPendingValidation(pubkey: String, sealed: String, count: (Connection) -> Boolean): PendingValidation = transaction { c ->
        val added = c.prepareStatement("INSERT INTO vote_validation_pending (pubkey, sealed) VALUES (?, ?::jsonb) ON CONFLICT DO NOTHING").use { s ->
            s.setString(1, pubkey)
            s.setString(2, sealed)
            s.executeUpdate() == 1
        }
        when {
            !added -> PendingValidation.ALREADY
            !count(c) -> {
                c.rollback()
                PendingValidation.FULL
            }
            else -> PendingValidation.ADDED
        }
    }

    /** Whether [pubkey] waits for next Monday's batch. */
    fun isValidationPending(pubkey: String): Boolean = dataSource.connection.use { c ->
        c.prepareStatement("SELECT 1 FROM vote_validation_pending WHERE pubkey = ?").use { s ->
            s.setString(1, pubkey)
            s.executeQuery().use { it.next() }
        }
    }

    /**
     * The batch of the Monday [monday], at most once (null when it already ran): in ONE transaction, every
     * waiting account becomes « vérifié » from [monday] on, in the order of the keys (so that the order of
     * the rows carries nothing), registered that Monday when it was not; an elected or already verified
     * account is left as it is. The sealed links are kept in the order of their random ephemeral keys, the
     * pending rows deleted, and the links older than [keepFrom] deleted. Returns how many accounts changed.
     */
    fun applyValidations(monday: LocalDate, keepFrom: LocalDate): Int? {
        val applied = transaction { c ->
            val first = c.prepareStatement("INSERT INTO vote_validation_batch (week) VALUES (?) ON CONFLICT DO NOTHING").use { s ->
                s.setObject(1, monday)
                s.executeUpdate() == 1
            }
            if (!first) {
                c.rollback()
                return@transaction null
            }
            val changed = c.prepareStatement(
                """
                INSERT INTO vote_account (pubkey, level, level_week, previous_level, registered_week)
                SELECT pubkey, 'verifie', ?, 'non-verifie', ? FROM vote_validation_pending ORDER BY pubkey
                ON CONFLICT (pubkey) DO UPDATE SET
                    previous_level = CASE WHEN vote_account.level_week <= excluded.level_week THEN vote_account.level ELSE vote_account.previous_level END,
                    level = 'verifie', level_week = excluded.level_week
                WHERE vote_account.level = 'non-verifie'
                """.trimIndent(),
            ).use { s ->
                s.setObject(1, monday)
                s.setObject(2, monday)
                s.executeUpdate()
            }
            c.prepareStatement(
                "INSERT INTO vote_validation (week, sealed) SELECT ?, sealed FROM vote_validation_pending ORDER BY sealed->>'ephemeral_pubkey'",
            ).use { s ->
                s.setObject(1, monday)
                s.executeUpdate()
            }
            c.createStatement().use { it.executeUpdate("DELETE FROM vote_validation_pending") }
            c.prepareStatement("DELETE FROM vote_validation WHERE week < ?").use { s ->
                s.setObject(1, keepFrom)
                s.executeUpdate()
            }
            changed
        } ?: return null
        // The deleted rows leave the table's pages at once when the role may vacuum it (autovacuum otherwise).
        runCatching { dataSource.connection.use { c -> c.createStatement().use { it.execute("VACUUM vote_validation_pending") } } }
        return applied
    }

    // ------------------------------------------------------------------ certification of elected officials (DE-23, D-112)

    fun certification(pubkey: String): Certification? = dataSource.connection.use { c ->
        c.prepareStatement("SELECT * FROM elected_certification WHERE pubkey = ?").use { s ->
            s.setString(1, pubkey)
            s.executeQuery().use { r -> if (r.next()) r.certification() else null }
        }
    }

    /** The certifications of [statuses], those waiting first, then by week of request and key. */
    fun certifications(statuses: Set<String>, offset: Int, limit: Int): List<Certification> = dataSource.connection.use { c ->
        c.prepareStatement(
            "SELECT * FROM elected_certification WHERE status = ANY(?) ORDER BY (status = 'pending') DESC, requested_week, pubkey OFFSET ? LIMIT ?",
        ).use { s ->
            s.setArray(1, c.createArrayOf("text", statuses.toTypedArray()))
            s.setInt(2, offset)
            s.setInt(3, limit)
            s.executeQuery().use { r -> buildList { while (r.next()) add(r.certification()) } }
        }
    }

    fun countCertifications(status: String): Int = dataSource.connection.use { c ->
        c.prepareStatement("SELECT count(*) FROM elected_certification WHERE status = ?").use { s ->
            s.setString(1, status)
            s.executeQuery().use { r -> r.next(); r.getInt(1) }
        }
    }

    /**
     * Records the request of [pubkey] for [mandate] (a new request replaces one waiting, refused, withdrawn
     * or ended), unless the account is certified already or the mandate is certified for another account.
     */
    fun requestCertification(pubkey: String, mandate: String, mandateType: String, territory: String, week: LocalDate): CertificationWrite = transaction { c ->
        val current = c.prepareStatement("SELECT status FROM elected_certification WHERE pubkey = ? FOR UPDATE").use { s ->
            s.setString(1, pubkey)
            s.executeQuery().use { r -> if (r.next()) r.getString(1) else null }
        }
        if (current == CertificationAnswer.CERTIFIED) return@transaction CertificationWrite.ALREADY_CERTIFIED
        val taken = c.prepareStatement("SELECT 1 FROM elected_certification WHERE mandate = ? AND status = 'certified' AND pubkey <> ?").use { s ->
            s.setString(1, mandate)
            s.setString(2, pubkey)
            s.executeQuery().use { it.next() }
        }
        if (taken) return@transaction CertificationWrite.MANDATE_TAKEN
        c.prepareStatement(
            """
            INSERT INTO elected_certification (pubkey, mandate, mandate_type, territory, status, requested_week) VALUES (?, ?, ?, ?, 'pending', ?)
            ON CONFLICT (pubkey) DO UPDATE SET mandate = excluded.mandate, mandate_type = excluded.mandate_type, territory = excluded.territory,
                status = 'pending', requested_week = excluded.requested_week, decided_week = NULL, reason = NULL
            """.trimIndent(),
        ).use { s ->
            s.setString(1, pubkey)
            s.setString(2, mandate)
            s.setString(3, mandateType)
            s.setString(4, territory)
            s.setObject(5, week)
            s.executeUpdate()
        }
        CertificationWrite.RECORDED
    }

    /**
     * Moves the certification of [pubkey] from [from] to [to] with [reason], decided the week [week], and,
     * in the same transaction, gives the account [level] from next Monday. Certifying also requires the
     * account to be still dedicated to the mandate ([isDedicated]). Returns false when the certification is
     * not in [from] any more, or the account is no longer dedicated (nothing changed).
     */
    fun decideCertification(pubkey: String, from: String, to: String, reason: String?, week: LocalDate, level: VoteLevel?): Boolean = transaction { c ->
        val updated = c.prepareStatement("UPDATE elected_certification SET status = ?, reason = ?, decided_week = ? WHERE pubkey = ? AND status = ?").use { s ->
            s.setString(1, to)
            s.setString(2, reason)
            s.setObject(3, week)
            s.setString(4, pubkey)
            s.setString(5, from)
            s.executeUpdate() == 1
        }
        if (!updated || (to == CertificationAnswer.CERTIFIED && !isDedicated(c, pubkey))) {
            c.rollback()
            return@transaction false
        }
        if (level != null) setLevel(c, pubkey, level, week.plusWeeks(1), week)
        true
    }

    /** An account dedicated to a mandate: registered, never verified nor waiting for it, never served a token. */
    fun isDedicated(pubkey: String): Boolean = dataSource.connection.use { c -> isDedicated(c, pubkey) }

    private fun isDedicated(c: Connection, pubkey: String): Boolean =
        c.prepareStatement(
            """
            SELECT 1 FROM vote_account a WHERE a.pubkey = ? AND a.level = 'non-verifie' AND a.previous_level = 'non-verifie' AND a.issued_up_to = 0
                AND NOT EXISTS (SELECT 1 FROM vote_validation_pending p WHERE p.pubkey = a.pubkey)
            """.trimIndent(),
        ).use { s ->
            s.setString(1, pubkey)
            s.executeQuery().use { it.next() }
        }

    // ------------------------------------------------------------------ helpers

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { c ->
        val autoCommit = c.autoCommit
        c.autoCommit = false
        try {
            block(c).also { if (!c.autoCommit) c.commit() }
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = autoCommit
        }
    }

    private fun ResultSet.certification() = Certification(
        pubkey = getString("pubkey"),
        mandate = getString("mandate"),
        mandateType = getString("mandate_type"),
        territory = getString("territory"),
        status = getString("status"),
        requestedWeek = getObject("requested_week", LocalDate::class.java),
        decidedWeek = getObject("decided_week", LocalDate::class.java),
        reason = getString("reason"),
    )

    private fun ResultSet.account() = VoteAccount(
        pubkey = getString("pubkey"),
        level = VoteLevel.of(getString("level"))!!,
        levelWeek = getObject("level_week", LocalDate::class.java),
        previousLevel = VoteLevel.of(getString("previous_level"))!!,
        registeredWeek = getObject("registered_week", LocalDate::class.java),
        issuedUpTo = getLong("issued_up_to"),
        lastRequest = getBytes("last_request"),
        lastResponse = getString("last_response"),
        lastResponseDay = getObject("last_response_day", LocalDate::class.java),
    )

    private fun ResultSet.urne() = Urne(
        number = getLong("number"),
        id = getString("id"),
        subject = getString("subject"),
        choices = Json.decodeFromString(choicesJson, getString("choices")),
        seedVersion = getInt("seed_version"),
        opensAt = getTimestamp("opens_at").toInstant(),
        closesAt = getTimestamp("closes_at")?.toInstant(),
        openedWeek = getObject("opened_week", LocalDate::class.java),
        liveResults = getBoolean("live_results"),
        closed = getBoolean("closed"),
        salt = getBytes("salt"),
    )

}
