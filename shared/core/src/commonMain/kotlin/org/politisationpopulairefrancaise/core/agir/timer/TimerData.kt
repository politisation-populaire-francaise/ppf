package org.politisationpopulairefrancaise.core.agir.timer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.security.PinRecord

/** Someone chosen to receive the alert of a timer: a contact of the private conversations, or a verified legal team. */
@Serializable
data class TimerRecipient(
    /** Public key, lowercase hexadecimal. */
    val key: String,
    /** A legal team of the directory of "Agir" (its verified key), rather than a contact. */
    val legalTeam: Boolean = false,
    /** The name the device shows (the legal team's name); null shows the short form of the key. */
    val label: String? = null,
)

/**
 * The timer armed on this device (D-088), as the device keeps it: never the codes, never the
 * secret in clear. [secretBoxHex] is the current secret hidden by the key of the « je vais bien »
 * code ([TimerCodes]); [hashHex] its SHA-256, which the recipients hold.
 *
 * [deadline] is the one the recipients know. [shownDeadline] is what the screen shows: the same,
 * except after « Repousser » with the duress code, which moves only the screen (nothing is sent,
 * the alert still goes at [deadline]).
 */
@Serializable
data class ArmedTimer(
    val id: String,
    val armedAt: Long,
    val deadline: Long,
    val shownDeadline: Long = deadline,
    val recipients: List<TimerRecipient>,
    val text: String,
    val place: String? = null,
    val hashHex: String,
    val secretNonceHex: String,
    val secretBoxHex: String,
    val codeSaltHex: String,
    val codeIterations: Int,
    val duress: PinRecord? = null,
    val failedAttempts: Int = 0,
    val lastFailureEpochSeconds: Long = 0,
) {
    override fun toString(): String = "ArmedTimer(recipients=${recipients.size}, duress=${duress != null})"
}

/**
 * A message of the user's timer not yet accepted by the server: the seal (kind 13, already
 * encrypted for [recipient] and signed by the account), wrapped again with fresh dates each time
 * it is tried, like the pending private messages.
 */
@Serializable
data class PendingDelivery(
    val timerId: String,
    /** [TimerFormats.ACTION_DEPOSIT], [TimerFormats.ACTION_CANCEL] or [TimerFormats.ACTION_REPORT]. */
    val action: String,
    val recipient: String,
    /** The seal, as JSON. */
    val seal: String,
    val queuedAt: Long,
) {
    override fun toString(): String = "PendingDelivery($action)"
}

/** A deposit received from a relative, kept sealed on the device until its deadline. */
@Serializable
data class ReceivedDeposit(
    val deadline: Long,
    val hashHex: String,
    val text: String,
    val place: String? = null,
    val createdAt: Long,
)

/** A cancellation or a report received, kept as it came: it is checked against the chain of hashes when the timer is read. */
@Serializable
data class ReceivedReveal(
    val action: String,
    val secretHex: String,
    /** Reports only: the new deadline and the hash of the next secret. */
    val deadline: Long? = null,
    val hashHex: String? = null,
    val createdAt: Long,
    val receivedAt: Long,
)

/**
 * Everything this device received about one timer of a relative ([author], [id]), in whatever order
 * the envelopes arrived: the deposit and the cancellations or reports. The reveals that came first
 * wait for their deposit.
 */
@Serializable
data class ReceivedTimer(
    val author: String,
    val id: String,
    val firstSeenAt: Long,
    val deposit: ReceivedDeposit? = null,
    val reveals: List<ReceivedReveal> = emptyList(),
    /** The user hid the alert (they had news by another way); it does not come back. */
    val dismissedAt: Long? = null,
)

/**
 * What the timer of AG-55 keeps on the device, in one small JSON document (no database table, so
 * no migration): the timer the user armed, its messages still waiting for the server, and the
 * timers of relatives received. [owner] is the account all of it belongs to.
 */
@Serializable
data class TimerDeviceData(
    val version: Int = 1,
    val owner: String? = null,
    val armed: ArmedTimer? = null,
    val pending: List<PendingDelivery> = emptyList(),
    val received: List<ReceivedTimer> = emptyList(),
) {
    val isEmpty: Boolean get() = armed == null && pending.isEmpty() && received.isEmpty()

    fun encode(): String = json.encodeToString(serializer(), this)

    override fun toString(): String = "TimerDeviceData(armed=${armed != null}, pending=${pending.size}, received=${received.size})"

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** What [encode] wrote; empty when nothing was written or it cannot be read (a damaged file is forgotten, not shown). */
        fun decode(text: String?): TimerDeviceData =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: TimerDeviceData()
    }
}

/**
 * Where the device keeps [TimerDeviceData]: a private preference of the Android application
 * (system backups refused by the manifest), a private file on a computer, like the rally point and
 * the settings of the lock. Nothing is ever sent.
 */
interface TimerStorage {
    fun load(): String?

    fun save(value: String)

    fun clear()
}

/** For tests and previews: forgotten when the process ends. */
class InMemoryTimerStorage(initial: String? = null) : TimerStorage {
    var value: String? = initial
        private set

    override fun load(): String? = value

    override fun save(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}

/**
 * One reader and writer of [TimerStorage] at a time: the screens of the user's timer and the
 * reception of the envelopes (on a worker of the messaging) never overwrite each other. The calls
 * read and write the platform's storage: run them off the main thread.
 */
class TimerStore(private val storage: TimerStorage) {
    private val lock = Mutex()

    suspend fun read(): TimerDeviceData = lock.withLock { load() }

    /** Applies [change] to what is stored and writes the result when it differs; returns what [change] says. */
    suspend fun <T> update(change: (TimerDeviceData) -> Pair<TimerDeviceData, T>): T = lock.withLock {
        val current = load()
        val (next, result) = change(current)
        if (next != current) storage.save(next.encode())
        result
    }

    /** Forgets everything (the quick erase, or another account on the device). */
    suspend fun clear() = lock.withLock { storage.clear() }

    private fun load(): TimerDeviceData = TimerDeviceData.decode(runCatching { storage.load() }.getOrNull())
}
