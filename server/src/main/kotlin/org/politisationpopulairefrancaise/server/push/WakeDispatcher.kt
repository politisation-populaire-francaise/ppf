package org.politisationpopulairefrancaise.server.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.relay.RelayKinds
import org.slf4j.Logger
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Turns stored events into wake-ups (contract, section 2), without ever blocking the publication:
 *
 * - an envelope (kind 1059) wakes the mailbox subscriptions of its `p` recipient, at most once a
 *   minute per subscription, after a random delay of 0 to 60 seconds so that the arrival of a
 *   message cannot be matched with the time it was sent;
 * - any other stored event that is public (not ephemeral, not application data) wakes the public
 *   subscriptions, at most once every 30 minutes per subscription.
 *
 * The server never knows what a device follows: every wake-up is the same constant, and the device
 * decides what to show after its own synchronization. Nothing is logged about a subscription.
 */
class WakeDispatcher(
    private val subscriptions: PushSubscriptions,
    private val sender: WakeSender,
    private val clock: () -> Instant = Instant::now,
    /** A delay between 0 and the given bound (inclusive); random in production, fixed in tests. */
    private val jitter: (Duration) -> Duration = ::randomJitter,
) {
    private val signals = Channel<Signal>(SIGNAL_CAPACITY)

    /** Mailbox wake-ups waiting for their time, by subscription id. */
    private val pendingMailbox = ConcurrentHashMap<String, Instant>()

    /** The last mailbox wake-ups of the past minute, by subscription id (the database is updated after sending). */
    private val lastMailboxWake = ConcurrentHashMap<String, Instant>()

    /** When the newest public content was accepted (null: none since the start of the server). */
    private val lastPublicContent = AtomicReference<Instant?>(null)

    private sealed interface Signal {
        data class Mailbox(val recipient: String) : Signal
        data object Public : Signal
    }

    /**
     * Called by the publication path right after an event is stored. Never blocks and never fails:
     * when the queue is full, the signal is dropped (a flood already wakes everyone anyway).
     */
    fun stored(event: SignedEvent) {
        signalOf(event)?.let { signals.trySend(it) }
    }

    /** Same as [stored], but handled at once (tests, and the worker). */
    suspend fun handle(event: SignedEvent) {
        signalOf(event)?.let { handle(it) }
    }

    private fun signalOf(event: SignedEvent): Signal? = when {
        event.kind == RelayKinds.GIFT_WRAP -> event.firstTagValue("p")?.takeIf(::isLowercaseHex64)?.let(Signal::Mailbox)
        isPublicKind(event.kind) -> Signal.Public
        else -> null
    }

    /** Number of mailbox wake-ups waiting (tests). */
    val pendingMailboxCount: Int get() = pendingMailbox.size

    private fun handle(signal: Signal) {
        when (signal) {
            Signal.Public -> lastPublicContent.set(clock())
            is Signal.Mailbox -> {
                val now = clock()
                for (subscription in subscriptions.mailboxesOf(signal.recipient)) {
                    // A wake-up already waiting covers this message too.
                    pendingMailbox.computeIfAbsent(subscription.id) {
                        val lastWake = listOfNotNull(subscription.lastWakeAt, lastMailboxWake[subscription.id]).maxOrNull()
                        val earliest = lastWake?.plus(MAILBOX_INTERVAL) ?: now
                        maxOf(now.plus(jitter(MAILBOX_MAX_DELAY)), earliest)
                    }
                }
            }
        }
    }

    /** Sends the mailbox wake-ups whose time has come. Returns how many were attempted. */
    suspend fun sendDueMailbox(): Int {
        val now = clock()
        lastMailboxWake.entries.removeIf { (_, at) -> at.plus(MAILBOX_INTERVAL).isBefore(now) }
        val due = pendingMailbox.filterValues { !it.isAfter(now) }
        var attempted = 0
        for ((id, dueAt) in due) {
            // Taken out of the queue before sending, and the time of the wake-up noted first: a message
            // arriving meanwhile schedules a new wake-up, one minute after this one at the earliest.
            if (!pendingMailbox.remove(id, dueAt)) continue
            val subscription = subscriptions.find(id) ?: continue
            lastMailboxWake[id] = now
            attempted++
            when (val result = sender.wake(subscription)) {
                is WakeResult.RetryAt -> {
                    lastMailboxWake.remove(id)
                    pendingMailbox.merge(id, result.at) { waiting, retry -> maxOf(waiting, retry) }
                }
                WakeResult.Gone -> {
                    lastMailboxWake.remove(id)
                    subscriptions.delete(id)
                }
                WakeResult.Delivered, WakeResult.Failed -> subscriptions.markWoken(id, now)
            }
        }
        return attempted
    }

    /**
     * Wakes the public subscriptions that have not been woken since the newest public content and
     * not in the last 30 minutes. Stops early when the push service asks to wait. Returns how many
     * were attempted.
     */
    suspend fun sendDuePublic(maxPerRound: Int = MAX_PUBLIC_PER_ROUND): Int {
        val contentAt = lastPublicContent.get() ?: return 0
        val now = clock()
        var attempted = 0
        while (attempted < maxPerRound) {
            val batch = subscriptions.publicDue(contentAt, now.minus(PUBLIC_INTERVAL), minOf(BATCH_SIZE, maxPerRound - attempted))
            if (batch.isEmpty()) break
            attempted += batch.size
            val results = coroutineScope {
                batch.chunked(PARALLEL_SENDS).flatMap { chunk ->
                    chunk.map { subscription -> async { subscription to sender.wake(subscription) } }.awaitAll()
                }
            }
            var blocked = false
            for ((subscription, result) in results) {
                when (result) {
                    WakeResult.Gone -> subscriptions.delete(subscription.id)
                    WakeResult.Delivered, WakeResult.Failed -> subscriptions.markWoken(subscription.id, now)
                    // Left as it is: the next round tries again once the host accepts.
                    is WakeResult.RetryAt -> blocked = true
                }
            }
            if (blocked) break
        }
        return attempted
    }

    /** Deletes the subscriptions not renewed for 30 days. */
    fun cleanup(): Int = subscriptions.deleteNotRenewedSince(clock().minus(RETENTION))

    /** The worker: signals, mailbox wake-ups every second, public ones every minute, cleanup every hour. */
    fun start(scope: CoroutineScope, log: Logger): Job = scope.launch(Dispatchers.IO) {
        launch {
            for (signal in signals) {
                runCatching { handle(signal) }.onFailure { log.warn("Push: a signal could not be handled (${it::class.simpleName}).") }
            }
        }
        launch {
            while (isActive) {
                delay(MAILBOX_TICK.toMillis())
                runCatching { sendDueMailbox() }.onFailure { log.warn("Push: mailbox wake-ups failed (${it::class.simpleName}).") }
            }
        }
        launch {
            while (isActive) {
                delay(PUBLIC_TICK.toMillis())
                runCatching { sendDuePublic() }.onFailure { log.warn("Push: public wake-ups failed (${it::class.simpleName}).") }
            }
        }
        launch {
            while (isActive) {
                runCatching { cleanup() }
                    .onSuccess { if (it > 0) log.info("Push: $it subscription(s) not renewed for 30 days deleted.") }
                    .onFailure { log.warn("Push: cleanup failed (${it::class.simpleName}).") }
                delay(CLEANUP_TICK.toMillis())
            }
        }
    }

    companion object {
        val MAILBOX_INTERVAL: Duration = Duration.ofMinutes(1)
        val MAILBOX_MAX_DELAY: Duration = Duration.ofSeconds(60)
        val PUBLIC_INTERVAL: Duration = Duration.ofMinutes(30)
        val RETENTION: Duration = Duration.ofDays(30)
        private val MAILBOX_TICK: Duration = Duration.ofSeconds(1)
        private val PUBLIC_TICK: Duration = Duration.ofMinutes(1)
        private val CLEANUP_TICK: Duration = Duration.ofHours(1)
        private const val SIGNAL_CAPACITY = 10_000
        private const val BATCH_SIZE = 500
        private const val PARALLEL_SENDS = 16
        const val MAX_PUBLIC_PER_ROUND: Int = 20_000

        private val secureRandom = SecureRandom()

        fun randomJitter(max: Duration): Duration = Duration.ofMillis((secureRandom.nextDouble() * (max.toMillis() + 1)).toLong().coerceAtMost(max.toMillis()))

        /** Public content: regular, replaceable or addressable kinds; not ephemeral, not envelopes, not application data (settings). */
        fun isPublicKind(kind: Int): Boolean =
            kind != RelayKinds.GIFT_WRAP && kind != RelayKinds.APPLICATION_DATA && kind !in 20_000..29_999

        private fun isLowercaseHex64(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
