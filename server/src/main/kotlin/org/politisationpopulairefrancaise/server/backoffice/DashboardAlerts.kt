package org.politisationpopulairefrancaise.server.backoffice

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** An alert of the dashboard without queue (contract 1.1, section 7), for example a refused import. */
data class DashboardAlert(
    val id: UUID,
    val source: String,
    val title: String,
    val level: Int,
    val link: String?,
    val permission: String,
    val createdAt: Instant,
    val dismissedBy: UUID?,
    val dismissedAt: Instant?,
)

/** The alerts table (team_dashboard_alert); posted and dismissed through [BackOffice], which records the journal. */
class DashboardAlerts(private val dataSource: DataSource) {

    fun insert(connection: Connection, source: String, title: String, level: Int, link: String?, permission: String, now: Instant): DashboardAlert {
        val alert = DashboardAlert(UUID.randomUUID(), source, title, level, link, permission, now, null, null)
        connection.prepareStatement(
            "INSERT INTO team_dashboard_alert (id, source, title, level, link, permission, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { insert ->
            insert.setObject(1, alert.id)
            insert.setString(2, source)
            insert.setString(3, title)
            insert.setInt(4, level)
            insert.setText(5, link)
            insert.setString(6, permission)
            insert.setInstant(7, now)
            insert.executeUpdate()
        }
        return alert
    }

    /** The alerts not dismissed yet, the most important and then the newest first. */
    fun open(): List<DashboardAlert> = dataSource.connection.use { connection ->
        connection.prepareStatement("$SELECT WHERE dismissed_at IS NULL ORDER BY level DESC, created_at DESC LIMIT 200").use { statement ->
            val rows = statement.executeQuery()
            buildList { while (rows.next()) add(rows.toAlert()) }
        }
    }

    fun find(connection: Connection, id: UUID): DashboardAlert? =
        connection.prepareStatement("$SELECT WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, id)
            val rows = statement.executeQuery()
            if (rows.next()) rows.toAlert() else null
        }

    fun dismiss(connection: Connection, id: UUID, by: UUID, now: Instant): Boolean =
        connection.prepareStatement("UPDATE team_dashboard_alert SET dismissed_by = ?, dismissed_at = ? WHERE id = ? AND dismissed_at IS NULL").use { update ->
            update.setObject(1, by)
            update.setInstant(2, now)
            update.setObject(3, id)
            update.executeUpdate() == 1
        }

    fun deleteDismissedBefore(cutoff: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM team_dashboard_alert WHERE dismissed_at < ?").use { delete ->
            delete.setInstant(1, cutoff)
            delete.executeUpdate()
        }
    }

    private fun ResultSet.toAlert(): DashboardAlert = DashboardAlert(
        id = uuid("id")!!,
        source = getString("source"),
        title = getString("title"),
        level = getInt("level"),
        link = getString("link"),
        permission = getString("permission"),
        createdAt = instant("created_at")!!,
        dismissedBy = uuid("dismissed_by"),
        dismissedAt = instant("dismissed_at"),
    )

    private companion object {
        const val SELECT = "SELECT id, source, title, level, link, permission, created_at, dismissed_by, dismissed_at FROM team_dashboard_alert"
    }
}
