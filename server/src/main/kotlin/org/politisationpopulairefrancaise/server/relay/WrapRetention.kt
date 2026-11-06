package org.politisationpopulairefrancaise.server.relay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.RecipientRetention
import org.slf4j.Logger
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * The mailbox settings of a recipient (D-028): an application-specific event (NIP-78, kind 30078)
 * signed by the recipient, addressable by `d` = [IDENTIFIER], whose tag [RETENTION_DAYS_TAG]
 * carries the number of days their envelopes stay on the server. Any key may publish one (the
 * publication policy accepts kind 30078 from everyone); only the one signed by the recipient
 * counts for their mailbox, since the server reads it by `kind:recipient:d`. Being addressable,
 * a newer version replaces the older one.
 *
 * The value is bounded by the server between [RelayConfig.wrapRetention] (also the default) and
 * [RelayConfig.wrapRetentionMax]; a missing or unreadable value means the default.
 */
object MailboxSettings {
    const val KIND: Int = RelayKinds.APPLICATION_DATA
    const val IDENTIFIER: String = "ppf:mailbox"
    const val RETENTION_DAYS_TAG: String = "retention_days"
}

/**
 * Deletes encrypted envelopes (kind 1059) once they have been on the server longer than their
 * recipient's retention: the recipient's own setting ([MailboxSettings]) bounded between [minimum]
 * and [maximum], or [minimum] without a setting. The age is counted from the reception by the
 * server, not from the `created_at` written by the sender, which NIP-59 deliberately randomises.
 *
 * The server never opens an envelope: this task only knows kinds, recipient keys and reception dates.
 */
class WrapRetention(
    private val repository: EventRepository,
    private val minimum: Duration,
    private val maximum: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    constructor(repository: EventRepository, config: RelayConfig, clock: Clock = Clock.systemUTC()) :
        this(repository, config.wrapRetention, config.wrapRetentionMax, clock)

    init {
        require(minimum.isPositive()) { "The minimum retention must be positive" }
        require(maximum >= minimum) { "The maximum retention cannot be shorter than the minimum" }
    }

    private val rule = RecipientRetention(
        defaultDays = minimum.inWholeDays.toInt(),
        minDays = minimum.inWholeDays.toInt(),
        maxDays = maximum.inWholeDays.toInt(),
        preferenceKind = MailboxSettings.KIND,
        preferenceIdentifier = MailboxSettings.IDENTIFIER,
        daysTag = MailboxSettings.RETENTION_DAYS_TAG,
    )

    /** Deletes the expired envelopes once and returns how many were removed. */
    fun purgeOnce(): Int = repository.deleteExpiredByRecipient(RelayKinds.GIFT_WRAP, RECIPIENT_TAG, rule, clock.instant())

    /** The instant before which an envelope of a recipient without setting has expired. */
    fun defaultCutoff(): Instant = clock.instant().minus(minimum.toJavaDuration())

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

    private companion object {
        /** The tag naming the recipient of an envelope (NIP-59). */
        const val RECIPIENT_TAG = "p"
    }
}
