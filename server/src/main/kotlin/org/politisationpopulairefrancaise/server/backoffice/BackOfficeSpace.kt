package org.politisationpopulairefrancaise.server.backoffice

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.server.accounts.CompromisedAccounts
import org.politisationpopulairefrancaise.server.agir.AgirSpace
import org.slf4j.Logger
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * The periodic task of the back-office, living and dying with the application (like WrapRetention):
 * [BackOffice.maintain] now and then every [interval]. Only counts are logged, never a pseudonym, a key
 * or an identifier.
 */
class BackOfficeMaintenance(private val backOffice: BackOffice) {
    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val report = backOffice.maintain()
                if (report.expiredGrants + report.expiredApprovals + report.suspendedValidators > 0) {
                    log.info(
                        "Back-office: ${report.expiredGrants} grant(s) expired, ${report.expiredApprovals} request(s) expired, " +
                            "${report.suspendedValidators} validator(s) suspended.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Back-office maintenance failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }
}

object BackOfficeSpace {
    /**
     * The back-office of [application]: the base on [dataSource], the queue `agir.held` when the Agir space
     * runs (and the verified organisations it knows, for the validators), and the periodic task. The poles
     * register their own queues on [BackOffice.queues] right after, in `Application.module`.
     */
    fun start(application: Application, dataSource: DataSource, setup: BackOfficeSetup, agir: AgirSpace?, compromised: CompromisedAccounts? = null): BackOffice {
        val backOffice = BackOffice(
            config = setup.config,
            dataSource = dataSource,
            isVerifiedOrganisation = agir?.verified?.let { verified -> verified::isVerified },
            verifiedOrganisations = agir?.verified?.let { verified -> verified::keys },
            clock = setup.clock,
        )
        // A content signed after its key was declared compromised can only be refused (S-146).
        agir?.let { backOffice.queues.register(AgirHeldQueue(it, compromised)) }
        val interval = setup.config.maintenanceInterval
        if (interval.isPositive()) BackOfficeMaintenance(backOffice).start(application, interval, application.log)
        application.log.info("Back-office: served on port ${setup.config.port} only, for ${setup.config.origin}.")
        return backOffice
    }
}
