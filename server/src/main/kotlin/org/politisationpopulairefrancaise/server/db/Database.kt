package org.politisationpopulairefrancaise.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.io.File
import java.net.URI
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
    /** The host of [url], to say where a command acts (never the user nor the password). */
    val host: String get() = runCatching { URI(url.removePrefix("jdbc:")).host }.getOrNull() ?: "?"

    override fun toString(): String = "DatabaseConfig(host=$host)"

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

/**
 * The database of the server: pool and migrations (D-127).
 *
 * In production and preproduction the server connects with a restricted role (`ppf_app`, infra/) that
 * may neither change the schema nor rewrite the team journal; the migrations run beforehand, with the
 * owner role, in a one-shot container (`DatabaseCli migrate`). The server then only checks that nothing
 * is pending. Settings:
 *
 * - PPF_DATABASE_MIGRATE: `true` or `false`; whether the server applies the migrations itself at startup
 *   (default `true` only when PPF_ENVIRONMENT is `development`). With `false` it refuses to start when a
 *   migration is pending or failed.
 * - PPF_DATABASE_APP_ROLE: the restricted role of the server, read by the migrations (Flyway placeholder
 *   `appRole`, callback `afterMigrate.sql`) to grant it the data rights and nothing on the journal but
 *   reading and appending; empty (development, tests) grants nothing.
 */
object Database {
    const val MIGRATE_VARIABLE: String = "PPF_DATABASE_MIGRATE"
    const val APP_ROLE_VARIABLE: String = "PPF_DATABASE_APP_ROLE"

    private val ROLE_NAME = Regex("^[a-z_][a-z0-9_]{0,62}$")

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

    /** Whether the server migrates at startup: [MIGRATE_VARIABLE], or only in development by default. */
    fun shouldMigrate(environment: String, env: (String) -> String? = System::getenv): Boolean =
        when (env(MIGRATE_VARIABLE)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }) {
            null -> environment == "development"
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("$MIGRATE_VARIABLE must be true or false")
        }

    /** The restricted role named by [APP_ROLE_VARIABLE], or "" when none (nothing is granted then). */
    fun appRole(env: (String) -> String? = System::getenv): String {
        val role = env(APP_ROLE_VARIABLE)?.trim().orEmpty()
        require(role.isEmpty() || ROLE_NAME.matches(role)) { "$APP_ROLE_VARIABLE must be a lowercase PostgreSQL role name" }
        return role
    }

    /** Applies the SQL migrations of src/main/resources/db/migration, in order, exactly once each (development, tests). */
    fun migrate(dataSource: DataSource) {
        runMigrations(dataSource, "")
    }

    /**
     * Applies the pending migrations, then the callback `afterMigrate.sql` with [appRole] (rights of the
     * restricted role); returns how many migrations were applied.
     */
    fun runMigrations(dataSource: DataSource, appRole: String): Int {
        require(appRole.isEmpty() || ROLE_NAME.matches(appRole)) { "Invalid role name" }
        return flyway(dataSource, appRole).migrate().migrationsExecuted
    }

    /**
     * Checks, without changing anything, that every migration is applied and none failed; throws
     * otherwise, so that a server started before its migrations refuses to run.
     */
    fun checkMigrated(dataSource: DataSource) {
        val flyway = flyway(dataSource, "")
        val info = flyway.info()
        val pending = info.pending().size
        val failed = info.all().count { it.state.isFailed }
        check(pending == 0 && failed == 0) { "Database not migrated: $pending pending and $failed failed migration(s). Run DatabaseCli migrate first." }
        val validation = flyway.validateWithResult()
        check(validation.validationSuccessful) { "Database migrations do not match this version of the server (${validation.invalidMigrations.size} invalid)." }
    }

    /** At startup: migrates when [migrate] is true, otherwise only checks ([checkMigrated]). */
    fun prepare(dataSource: DataSource, migrate: Boolean, appRole: String) {
        if (migrate) runMigrations(dataSource, appRole) else checkMigrated(dataSource)
    }

    private fun flyway(dataSource: DataSource, appRole: String): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .placeholders(mapOf("appRole" to appRole))
        .load()
}
