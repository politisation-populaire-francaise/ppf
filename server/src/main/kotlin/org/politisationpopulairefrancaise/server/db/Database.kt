package org.politisationpopulairefrancaise.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.io.File
import javax.sql.DataSource

/**
 * Database settings, read from the environment only (never from the repository):
 *
 * - DATABASE_URL: JDBC URL, e.g. jdbc:postgresql://db:5432/ppf
 * - DATABASE_USER
 * - DATABASE_PASSWORD, or DATABASE_PASSWORD_FILE (path of a file holding only the password,
 *   the form used by Docker secrets in infra/)
 *
 * Without them the server still starts (health check and web pages) but the API answers 503.
 */
data class DatabaseConfig(val url: String, val user: String, val password: String) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): DatabaseConfig? {
            val url = env("DATABASE_URL") ?: return null
            val user = env("DATABASE_USER") ?: return null
            val password = env("DATABASE_PASSWORD")
                ?: env("DATABASE_PASSWORD_FILE")?.let { File(it).readText().trim() }
                ?: return null
            return DatabaseConfig(url, user, password)
        }
    }
}

object Database {
    /** A small pool: the whole platform must fit in 4 GB of memory, database included. */
    fun connect(config: DatabaseConfig): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 4
            minimumIdle = 1
            connectionTimeout = 10_000
            poolName = "ppf"
        },
    )

    /** Applies the SQL migrations of src/main/resources/db/migration, in order, exactly once each. */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
