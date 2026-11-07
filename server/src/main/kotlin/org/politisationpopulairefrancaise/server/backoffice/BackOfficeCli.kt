package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.db.DatabaseConfig
import java.io.PrintStream
import javax.sql.DataSource
import kotlin.system.exitProcess

/**
 * The command line of the back-office, run on the server with the same environment as the server
 * (database, PPF_BACKOFFICE_ORIGIN, PPF_ENVIRONMENT):
 *
 * ```
 * java -cp ppf-server.jar org.politisationpopulairefrancaise.server.backoffice.BackOfficeCli bootstrap <pseudonyme>
 * ```
 *
 * (`ppf-backoffice bootstrap <pseudonyme>` once infra/ wraps it). `bootstrap` creates the invitation of a
 * super administrator and prints its link once; it works while fewer than two super administrators exist
 * (counting the bootstrap invitations still valid), so that the first two can be created and access can
 * be recovered if their keys are lost. Whoever runs it has the server in hand anyway: the line it leaves
 * in the journal says "système".
 */
object BackOfficeCli {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), System::getenv, System.out, System.err))
    }

    fun run(args: List<String>, env: (String) -> String?, out: PrintStream, err: PrintStream, dataSource: DataSource? = null): Int {
        if (args.size != 2 || args[0] != "bootstrap") {
            err.println("Usage: BackOfficeCli bootstrap <pseudonyme>")
            return 2
        }
        val environment = env("PPF_ENVIRONMENT") ?: "development"
        val publicPort = env("PORT")?.toIntOrNull() ?: 8080
        val config = try {
            BackOfficeConfig.fromEnvironment(environment, publicPort, env)
        } catch (e: IllegalArgumentException) {
            err.println("Invalid back-office settings: ${e.message}")
            return 2
        } ?: run {
            err.println("${BackOfficeConfig.ORIGIN_VARIABLE} is not set: the back-office is off.")
            return 2
        }
        val source = dataSource ?: DatabaseConfig.fromEnvironment(env)?.let { Database.connect(it).also(Database::migrate) } ?: run {
            err.println("No database configured (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE).")
            return 2
        }
        try {
            return when (val outcome = BackOffice(config, source).bootstrapInvitation(args[1])) {
                is Outcome.Ok -> {
                    out.println("Invitation of a super administrator for \"${outcome.value.invitation.pseudonym}\", valid 48 hours, shown only once:")
                    out.println("${config.origin}/invitation/${outcome.value.token}")
                    0
                }
                is Outcome.Denied -> {
                    err.println(
                        when (outcome.code) {
                            "bootstrap_closed" -> "Refused: two super administrators (or valid bootstrap invitations) already exist."
                            "bad_pseudonym" -> "Refused: a pseudonym has 2 to 40 letters, digits, spaces or -_.' and is never an e-mail address."
                            "pseudonym_taken" -> "Refused: an active member already uses this pseudonym."
                            else -> "Refused: ${outcome.code}."
                        },
                    )
                    1
                }
            }
        } finally {
            if (dataSource == null) (source as? AutoCloseable)?.close()
        }
    }
}
