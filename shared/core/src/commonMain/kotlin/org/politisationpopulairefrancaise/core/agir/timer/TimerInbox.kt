package org.politisationpopulairefrancaise.core.agir.timer

import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** Where the timer of a relative stands on this device, at a given moment. */
enum class RelativeTimerPhase {
    /** Before the deadline, nothing confirmed yet: a discreet line only. */
    WAITING,

    /** A valid cancellation arrived (before or after the deadline). */
    CANCELLED,

    /** The deadline passed without a valid cancellation: the alert shows by itself. */
    ALERT,

    /** The alert was hidden by the user. */
    DISMISSED,
}

/** The timer of a relative as the screens show it. */
data class RelativeTimer(
    val author: String,
    val id: String,
    val phase: RelativeTimerPhase,
    /** The deadline after the last valid report. */
    val deadline: Long,
    val text: String,
    val place: String?,
    /** When the relative confirmed (their own date, brought back to now at most), for [RelativeTimerPhase.CANCELLED]. */
    val cancelledAt: Long? = null,
)

/**
 * The timers of relatives received by this device (AG-55, D-088): the deposits stay sealed here,
 * and nothing needs the network at the deadline, so the alert shows even if the relative's phone
 * was seized or switched off.
 *
 * Envelopes arrive in any order (their dates are random): [accept] keeps whatever comes, and
 * [resolve] follows the chain of hashes from the deposit (a cancellation or a report counts only
 * when its secret hashes to the current `h` of the same timer from the same author), so that a
 * cancellation received before its deposit still counts, and a forged one never does.
 */
class TimerInbox(
    private val store: TimerStore,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /**
     * Takes a rumor handed over by the messaging ([org.politisationpopulairefrancaise.core.messaging.IncomingRumors]),
     * addressed to [recipient] (the account of this device). Returns true when it changed what the
     * device holds. Malformed, foreign or stale messages are dropped without an exception.
     */
    suspend fun accept(rumor: Rumor, recipient: String): Boolean {
        val message = TimerFormats.read(rumor, recipient) ?: return false
        val now = clock()
        return store.update { data ->
            // What belonged to another account must never become readable by this one.
            val base = if (data.owner != null && data.owner != recipient) TimerDeviceData(owner = recipient) else data.copy(owner = recipient)
            val next = base.copy(received = merge(prune(base.received, now), message, now))
            Pair(next, next.received != data.received)
        }
    }

    /** Hides an alert: the user had news by another way. It does not come back, even if its envelopes are fetched again. */
    suspend fun dismiss(author: String, id: String) {
        val now = clock()
        store.update { data ->
            Pair(data.copy(received = data.received.map { if (it.author == author && it.id == id && it.dismissedAt == null) it.copy(dismissedAt = now) else it }), Unit)
        }
    }

    /** Forgets the timers that ended long enough ago ([KEEP_SECONDS]). */
    suspend fun prune() {
        val now = clock()
        store.update { data -> Pair(data.copy(received = prune(data.received, now)), Unit) }
    }

    private fun merge(received: List<ReceivedTimer>, message: TimerMessage, now: Long): List<ReceivedTimer> {
        val index = received.indexOfFirst { it.author == message.author && it.id == message.timerId }
        if (index < 0 && received.size >= MAX_TIMERS) return received
        val existing = if (index >= 0) received[index] else ReceivedTimer(message.author, message.timerId, firstSeenAt = now)
        val updated = when (message) {
            is TimerMessage.Deposit -> when {
                existing.deposit != null -> existing
                // A deposit whose deadline is long past (envelopes fetched again after a restore) is not an alert any more.
                now - message.deadline > KEEP_SECONDS -> existing
                else -> existing.copy(deposit = ReceivedDeposit(message.deadline, message.hashHex, message.text, message.place, message.createdAt))
            }
            is TimerMessage.Cancel -> existing.withReveal(ReceivedReveal(TimerFormats.ACTION_CANCEL, message.secretHex, createdAt = message.createdAt, receivedAt = now))
            is TimerMessage.Report -> existing.withReveal(
                ReceivedReveal(TimerFormats.ACTION_REPORT, message.secretHex, message.deadline, message.hashHex, message.createdAt, now),
            )
        }
        if (updated == existing && index >= 0) return received
        if (updated.deposit == null && updated.reveals.isEmpty()) return received
        return if (index >= 0) received.toMutableList().also { it[index] = updated } else received + updated
    }

    private fun ReceivedTimer.withReveal(reveal: ReceivedReveal): ReceivedTimer = when {
        reveals.any { it.action == reveal.action && it.secretHex == reveal.secretHex } -> this
        reveals.size >= MAX_REVEALS -> this
        else -> copy(reveals = reveals + reveal)
    }

    companion object {
        /** A timer is forgotten this long after its deadline (or its cancellation): longer than the window in which its envelopes can be fetched again. */
        const val KEEP_SECONDS: Long = 7 * 24 * 3_600L

        /** Cancellations or reports received before their deposit wait this long for it. */
        const val ORPHAN_SECONDS: Long = 3 * 24 * 3_600L

        /** At most this many timers of relatives on the device; at most this many reveals per timer. */
        const val MAX_TIMERS: Int = 50
        const val MAX_REVEALS: Int = 50

        /** Where [timer] stands at [now]; null while its deposit has not arrived. */
        fun resolve(timer: ReceivedTimer, now: Long): RelativeTimer? {
            val deposit = timer.deposit ?: return null
            var hash = deposit.hashHex
            var deadline = deposit.deadline
            var cancelledAt: Long? = null
            val left = timer.reveals.toMutableList()
            while (cancelledAt == null) {
                val next = left.firstOrNull { TimerFormats.hashOf(it.secretHex) == hash } ?: break
                left.remove(next)
                if (next.action == TimerFormats.ACTION_CANCEL) {
                    cancelledAt = minOf(next.createdAt, next.receivedAt)
                } else {
                    hash = next.hashHex ?: break
                    deadline = next.deadline ?: break
                }
            }
            val phase = when {
                cancelledAt != null -> RelativeTimerPhase.CANCELLED
                now < deadline -> RelativeTimerPhase.WAITING
                timer.dismissedAt != null -> RelativeTimerPhase.DISMISSED
                else -> RelativeTimerPhase.ALERT
            }
            return RelativeTimer(timer.author, timer.id, phase, deadline, deposit.text, deposit.place, cancelledAt)
        }

        /** The timers still worth keeping at [now]. */
        fun prune(received: List<ReceivedTimer>, now: Long): List<ReceivedTimer> = received.filter { timer ->
            val state = resolve(timer, now)
            when {
                state == null -> now - timer.firstSeenAt <= ORPHAN_SECONDS
                state.phase == RelativeTimerPhase.CANCELLED -> now - maxOf(state.deadline, state.cancelledAt ?: 0L) <= KEEP_SECONDS
                else -> now - state.deadline <= KEEP_SECONDS
            }
        }
    }
}
