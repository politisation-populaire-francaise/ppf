package org.politisationpopulairefrancaise.server.legal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The daily purge of the legal register (D-077), living and dying with the application like WrapRetention:
 * [LegalRegister.purge] now and then every [interval]. The retention itself (one year) is fixed in the
 * database function, which the server cannot shorten nor lengthen. Only counts are logged, never a key.
 */
class LegalRegisterPurge(private val dataSource: DataSource) {
    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val report = LegalRegister.purge(dataSource)
                if (report.operations + report.pseudonyms > 0) {
                    log.info("Legal register: ${report.operations} operation(s) and ${report.pseudonyms} pseudonym(s) older than one year deleted.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Legal register purge failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }

    companion object {
        val DEFAULT_INTERVAL: Duration = 24.hours
    }
}
