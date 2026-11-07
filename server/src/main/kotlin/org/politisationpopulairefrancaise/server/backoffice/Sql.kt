package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

/** Small JDBC helpers shared by the stores of the back-office (plain JDBC, as the rest of the server). */
internal fun <T> DataSource.transaction(block: (Connection) -> T): T = connection.use { connection ->
    connection.autoCommit = false
    try {
        val result = block(connection)
        connection.commit()
        result
    } catch (e: Throwable) {
        connection.rollback()
        throw e
    } finally {
        connection.autoCommit = true
    }
}

/** Seconds are enough for the team and the journal; nothing finer is kept. */
internal fun Clock.nowSeconds(): Instant = instant().truncatedTo(ChronoUnit.SECONDS)

internal fun Instant.toTimestamp(): OffsetDateTime = atOffset(ZoneOffset.UTC)

internal fun ResultSet.instant(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()

internal fun ResultSet.uuid(column: String): UUID? = getObject(column, UUID::class.java)

internal fun PreparedStatement.setInstant(index: Int, value: Instant?) {
    if (value == null) setNull(index, Types.TIMESTAMP_WITH_TIMEZONE) else setObject(index, value.toTimestamp())
}

internal fun PreparedStatement.setUuid(index: Int, value: UUID?) {
    if (value == null) setNull(index, Types.OTHER) else setObject(index, value)
}

internal fun PreparedStatement.setText(index: Int, value: String?) {
    if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
}

/** A lock held until the end of the transaction of [connection]. */
internal fun Connection.advisoryLock(name: String) {
    prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { lock ->
        lock.setString(1, name)
        lock.executeQuery().close()
    }
}

/** Lowercase hexadecimal key of 64 characters (a Nostr public key in the hex form of events). */
internal fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
