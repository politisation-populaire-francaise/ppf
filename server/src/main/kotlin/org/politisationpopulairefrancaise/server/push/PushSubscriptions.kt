package org.politisationpopulairefrancaise.server.push

import java.security.SecureRandom
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

enum class PushScope(val wire: String) {
    PUBLIC("public"),
    MAILBOX("mailbox"),
    ;

    companion object {
        fun fromWire(value: String): PushScope? = entries.firstOrNull { it.wire == value }
    }
}

/** One stored subscription: nothing but what is needed to send a wake-up (contract, section 7). */
data class PushSubscription(
    val id: String,
    val scope: PushScope,
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val pubkey: String?,
    val createdAt: Instant,
    val renewedAt: Instant,
    val lastWakeAt: Instant?,
)

/** The `push_subscription` table (migration V9), plain JDBC like the rest of the server. */
class PushSubscriptions(private val dataSource: DataSource, private val random: SecureRandom = SecureRandom()) {

    data class Saved(val id: String, val created: Boolean)

    fun exists(endpoint: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM push_subscription WHERE endpoint = ?").use { statement ->
            statement.setString(1, endpoint)
            statement.executeQuery().next()
        }
    }

    /**
     * Stores a subscription, or renews the one of the same endpoint (same id, new keys, scope and
     * key, `renewed_at` updated).
     */
    fun save(scope: PushScope, endpoint: String, p256dh: String, auth: String, pubkey: String?, now: Instant): Saved =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO push_subscription (id, scope, endpoint, p256dh, auth, pubkey, created_at, renewed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET scope = EXCLUDED.scope, p256dh = EXCLUDED.p256dh,
                    auth = EXCLUDED.auth, pubkey = EXCLUDED.pubkey, renewed_at = EXCLUDED.renewed_at
                RETURNING id, (xmax = 0) AS inserted
                """.trimIndent(),
            ).use { statement ->
                val at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
                statement.setString(1, newId())
                statement.setString(2, scope.wire)
                statement.setString(3, endpoint)
                statement.setString(4, p256dh)
                statement.setString(5, auth)
                statement.setString(6, pubkey)
                statement.setObject(7, at)
                statement.setObject(8, at)
                statement.executeQuery().use { rows ->
                    rows.next()
                    Saved(rows.getString(1), rows.getBoolean(2))
                }
            }
        }

    fun find(id: String): PushSubscription? = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toSubscription() else null }
        }
    }

    fun delete(id: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM push_subscription WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate() > 0
        }
    }

    fun mailboxesOf(pubkey: String): List<PushSubscription> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE scope = 'mailbox' AND pubkey = ? ORDER BY id").use { statement ->
            statement.setString(1, pubkey)
            statement.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.toSubscription() else null }.toList() }
        }
    }

    /**
     * Public subscriptions to wake: created or last woken before [contentAt] (the newest public
     * content), and not woken since [notWokenSince]. At most [limit], least recently woken first.
     */
    fun publicDue(contentAt: Instant, notWokenSince: Instant, limit: Int): List<PushSubscription> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "$SELECT WHERE scope = 'public' AND COALESCE(last_wake_at, created_at) < ? " +
                    "AND (last_wake_at IS NULL OR last_wake_at <= ?) ORDER BY last_wake_at NULLS FIRST, id LIMIT ?",
            ).use { statement ->
                statement.setObject(1, OffsetDateTime.ofInstant(contentAt, ZoneOffset.UTC))
                statement.setObject(2, OffsetDateTime.ofInstant(notWokenSince, ZoneOffset.UTC))
                statement.setInt(3, limit)
                statement.executeQuery().use { rows -> generateSequence { if (rows.next()) rows.toSubscription() else null }.toList() }
            }
        }

    fun markWoken(id: String, at: Instant) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE push_subscription SET last_wake_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                statement.setString(2, id)
                statement.executeUpdate()
            }
        }
    }

    /** Deletes the subscriptions not renewed since [before]; returns how many. */
    fun deleteNotRenewedSince(before: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM push_subscription WHERE renewed_at < ?").use { statement ->
            statement.setObject(1, OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
            statement.executeUpdate()
        }
    }

    fun count(): Long = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM push_subscription").use { rows -> rows.next(); rows.getLong(1) }
        }
    }

    private fun newId(): String = Base64Url.encode(ByteArray(16).also(random::nextBytes))

    private fun ResultSet.toSubscription(): PushSubscription = PushSubscription(
        id = getString("id"),
        scope = PushScope.fromWire(getString("scope")) ?: error("Unknown push scope"),
        endpoint = getString("endpoint"),
        p256dh = getString("p256dh"),
        auth = getString("auth"),
        pubkey = getString("pubkey"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        renewedAt = getObject("renewed_at", OffsetDateTime::class.java).toInstant(),
        lastWakeAt = getObject("last_wake_at", OffsetDateTime::class.java)?.toInstant(),
    )

    private companion object {
        const val SELECT = "SELECT id, scope, endpoint, p256dh, auth, pubkey, created_at, renewed_at, last_wake_at FROM push_subscription"
    }
}
