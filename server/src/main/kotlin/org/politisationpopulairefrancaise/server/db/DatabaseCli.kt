package org.politisationpopulairefrancaise.server.db

import java.io.PrintStream
import javax.sql.DataSource
import kotlin.system.exitProcess

/**
 * The migration entry point of the one-shot container (D-127), run with the owner role of the database:
 *
 * ```
 * java -cp ppf-server.jar org.politisationpopulairefrancaise.server.db.DatabaseCli migrate
 * ```
 *
 * Reads DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE, PPF_ENVIRONMENT and
 * PPF_DATABASE_APP_ROLE (the restricted role of the server, granted its rights by `afterMigrate.sql`).
 * Says where it acts (environment and database host), then prints only counts.
 */
object DatabaseCli {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), System::getenv, System.out, System.err))
    }

    fun run(args: List<String>, env: (String) -> String?, out: PrintStream, err: PrintStream, dataSource: DataSource? = null): Int {
        if (args != listOf("migrate")) {
            err.println("Usage: DatabaseCli migrate")
            return 2
        }
        val config = DatabaseConfig.fromEnvironment(env)
        if (config == null && dataSource == null) {
            err.println("No database configured (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE).")
            return 2
        }
        val role = try {
            Database.appRole(env)
        } catch (e: IllegalArgumentException) {
            err.println(e.message)
            return 2
        }
        out.println("Environment: ${env("PPF_ENVIRONMENT") ?: "development"}, database host: ${config?.host ?: "(given)"}, restricted role: ${role.ifEmpty { "(none)" }}")
        val source = dataSource ?: Database.connect(config!!)
        try {
            val applied = Database.runMigrations(source, role)
            out.println("Migrations applied: $applied.")
            return 0
        } catch (e: Exception) {
            // Only the class: a database message can quote a row.
            err.println("Migration failed (${e::class.simpleName}).")
            return 1
        } finally {
            if (dataSource == null) (source as? AutoCloseable)?.close()
        }
    }
}
