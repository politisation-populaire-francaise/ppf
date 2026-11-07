package org.politisationpopulairefrancaise.server.urne

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.urne.VoteLevel
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** An account registered to vote (table vote_account of V9__urnes.sql). */
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

/** What [UrneStore.deliver] did. */
enum class Delivery { DONE, CURSOR_MOVED }

/**
 * The SQL of the ballot boxes. Nothing here ever reads or writes anything about a request; the only
 * link between an account and a ballot box is the account's cursor, which says "served up to box n"
 * for every account alike (D-110, 6.3).
 */
class UrneStore(private val dataSource: DataSource) {

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
    fun setLevel(pubkey: String, level: VoteLevel, week: LocalDate, currentWeek: LocalDate): Boolean = dataSource.connection.use { c ->
        c.prepareStatement(
            "UPDATE vote_account SET previous_level = CASE WHEN level_week <= ? THEN level ELSE previous_level END, level = ?, level_week = ? WHERE pubkey = ?",
        ).use { s ->
            s.setObject(1, currentWeek)
            s.setString(2, level.code)
            s.setObject(3, week)
            s.setString(4, pubkey)
            s.executeUpdate() == 1
        }
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
