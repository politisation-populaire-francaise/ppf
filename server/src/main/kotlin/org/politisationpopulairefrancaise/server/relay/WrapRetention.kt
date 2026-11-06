package org.politisationpopulairefrancaise.server.relay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.slf4j.Logger
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Deletes encrypted envelopes (kind 1059) once they have been on the server longer than
 * [retention]. The age is counted from the reception by the server, not from the `created_at`
 * written by the sender, which NIP-59 deliberately randomises.
 *
 * The server never opens an envelope: this task only knows kinds and reception dates.
 */
class WrapRetention(
    private val repository: EventRepository,
    private val retention: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Deletes the expired envelopes once and returns how many were removed. */
    fun purgeOnce(): Int = repository.deleteReceivedBefore(RelayKinds.GIFT_WRAP, cutoff())

    fun cutoff(): Instant = clock.instant().minus(retention.toJavaDuration())

    /**
     * Runs [purgeOnce] now and then every [interval], until [scope] is cancelled (the application's
     * own scope: the task stops with the server). Only counts are logged, never contents or keys.
     */
    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val deleted = purgeOnce()
                if (deleted > 0) log.info("Envelope retention: $deleted expired envelope(s) deleted.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Envelope retention failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }
}
