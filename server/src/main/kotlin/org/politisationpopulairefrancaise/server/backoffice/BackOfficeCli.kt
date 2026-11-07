package org.politisationpopulairefrancaise.server.backoffice

import org.politisationpopulairefrancaise.server.db.Database
import org.politisationpopulairefrancaise.server.db.DatabaseConfig
import java.io.PrintStream
import javax.sql.DataSource
import kotlin.system.exitProcess

/**
 * The command line of the back-office, run on the server with the same environment as the server
 * (database, PPF_BACKOFFICE_ORIGIN, PPF_ENVIRONMENT), with the authority of the server operator:
 *
 * ```
 * java -cp ppf-server.jar org.politisationpopulairefrancaise.server.backoffice.BackOfficeCli <command>
 * ```
 *
 * - `bootstrap <pseudonyme>`: the invitation of a super administrator, its link printed once; works while
 *   fewer than two active super administrators exist (bootstrap invitations still valid included), so
 *   that the first two can be created and access recovered if their keys are lost;
 * - `cut <pseudonyme>`: emergency cut of an active member (reversible suspension);
 * - `reinstate <pseudonyme>`: reinstatement of a suspended member;
 * - `withdraw-bootstrap`: withdraws the bootstrap invitations still valid (the pages cannot).
 *
 * Each command says where it acts (environment, database host) before acting, never migrates the
 * database (DatabaseCli does, D-127) and refuses to act while a migration is pending. Its journal lines
 * say « système ».
 */
object BackOfficeCli {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), System::getenv, System.out, System.err))
    }

    private const val USAGE = "Usage: BackOfficeCli bootstrap <pseudonyme> | cut <pseudonyme> | reinstate <pseudonyme> | withdraw-bootstrap"

    fun run(args: List<String>, env: (String) -> String?, out: PrintStream, err: PrintStream, dataSource: DataSource? = null): Int {
        val command = args.firstOrNull()
        val valid = when (command) {
            "bootstrap", "cut", "reinstate" -> args.size == 2
            "withdraw-bootstrap" -> args.size == 1
            else -> false
        }
        if (!valid) {
            err.println(USAGE)
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
        val databaseConfig = DatabaseConfig.fromEnvironment(env)
        if (databaseConfig == null && dataSource == null) {
            err.println("No database configured (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD or DATABASE_PASSWORD_FILE).")
            return 2
        }
        out.println("Environment: $environment, database host: ${databaseConfig?.host ?: "(given)"}, back-office: ${config.origin}")
        val source = dataSource ?: Database.connect(databaseConfig!!)
        try {
            try {
                Database.checkMigrated(source)
            } catch (e: IllegalStateException) {
                err.println("Refused: ${e.message}")
                return 1
            }
            val backOffice = BackOffice(config, source)
            return when (command) {
                "bootstrap" -> when (val outcome = backOffice.bootstrapInvitation(args[1])) {
                    is Outcome.Ok -> {
                        out.println("Invitation of a super administrator for \"${outcome.value.invitation.pseudonym}\", valid 48 hours, shown only once:")
                        out.println("${config.origin}/invitation/${outcome.value.token}")
                        0
                    }
                    is Outcome.Denied -> refused(err, outcome.code)
                }
                "cut" -> when (val outcome = backOffice.operatorCut(args[1], REASON)) {
                    is Outcome.Ok -> {
                        val report = outcome.value
                        out.println(
                            "Account suspended: ${report.sessions} session(s) closed, ${report.grants} role(s) and ${report.keys} key(s) disabled, " +
                                "${report.validationSessions} validation session(s) revoked, ${report.requests} request(s) and ${report.invitations} invitation(s) withdrawn. " +
                                "Remove its WireGuard peers too.",
                        )
                        0
                    }
                    is Outcome.Denied -> refused(err, outcome.code)
                }
                "reinstate" -> when (val outcome = backOffice.operatorReinstate(args[1], REASON)) {
                    is Outcome.Ok -> {
                        out.println("Account reinstated: its remaining keys and roles work again.")
                        0
                    }
                    is Outcome.Denied -> refused(err, outcome.code)
                }
                else -> {
                    out.println("Bootstrap invitations withdrawn: ${backOffice.withdrawBootstrapInvitations()}.")
                    0
                }
            }
        } finally {
            if (dataSource == null) (source as? AutoCloseable)?.close()
        }
    }

    /** The reason journaled for the commands of the operator. */
    const val REASON: String = "Commande du serveur (BackOfficeCli)"

    private fun refused(err: PrintStream, code: String): Int {
        err.println(
            when (code) {
                "bootstrap_closed" -> "Refused: two active super administrators (or valid bootstrap invitations) already exist."
                "bad_pseudonym" -> "Refused: a pseudonym has 2 to 40 letters, digits, spaces or -_.' and is never an e-mail address."
                "pseudonym_taken" -> "Refused: an active member already uses this pseudonym."
                "not_found" -> "Refused: no member with this pseudonym in the expected state."
                "ambiguous" -> "Refused: several suspended members have this pseudonym."
                else -> "Refused: $code."
            },
        )
        return 1
    }
}
