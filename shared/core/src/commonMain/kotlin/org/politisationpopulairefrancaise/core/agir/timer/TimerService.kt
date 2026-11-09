package org.politisationpopulairefrancaise.core.agir.timer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.security.UnlockDelays
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.MessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.isLowercaseHex
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/**
 * How the messages of a timer leave the device: sealed by the account (NIP-59, kind 13) for one
 * recipient, then wrapped by a one-time key and published, exactly like a private message. The
 * server only ever receives the wrap.
 */
interface TimerTransport {
    /** The account that arms the timer. */
    val author: PublicKey

    /** The seal of [rumor] for [recipient]; throws when the key is not a valid point of the curve. */
    suspend fun seal(rumor: Rumor, recipient: PublicKey): SignedEvent

    /** Wraps [seal] for [recipient] with fresh random dates and publishes the wrap. */
    suspend fun deliver(seal: SignedEvent, recipient: PublicKey): PublicationResult
}

/** The transport of the application: the account's messaging signer and the single publication port. */
class MessagingTimerTransport(
    private val signer: MessagingSigner,
    private val remote: RemoteEvents,
    private val clock: () -> Long = ::currentEpochSeconds,
) : TimerTransport {
    override val author: PublicKey get() = signer.publicKey

    override suspend fun seal(rumor: Rumor, recipient: PublicKey): SignedEvent =
        GiftWrap.seal(rumor, signer, recipient, GiftWrap.randomPastTimestamp(clock()))

    override suspend fun deliver(seal: SignedEvent, recipient: PublicKey): PublicationResult =
        remote.publish(GiftWrap.wrap(seal, recipient, GiftWrap.randomPastTimestamp(clock())))
}

/** What the person asks when arming a timer. Codes are wiped from memory by the screen once used. */
class ArmRequest(
    val recipients: List<TimerRecipient>,
    val deadline: Long,
    val text: String,
    val place: String?,
    val checkInCode: String,
    val checkInConfirmation: String,
    val duressCode: String?,
) {
    override fun toString(): String = "ArmRequest(recipients=${recipients.size})"
}

/** Why a timer cannot be armed. */
enum class ArmProblem { NO_RECIPIENT, TOO_MANY_RECIPIENTS, INVALID_RECIPIENT, DEADLINE_TOO_SOON, DEADLINE_TOO_FAR, NO_TEXT, CODES, ALREADY_ARMED }

/** What the server did with the messages of a timer that were waiting. */
data class DeliveryReport(val delivered: Int = 0, val queued: Int = 0, val rejected: Int = 0)

sealed interface ArmResult {
    /** Armed: the deposits are on the device; [delivery] says how many reached the server already. */
    data class Armed(val shownDeadline: Long, val recipients: Int, val delivery: DeliveryReport) : ArmResult

    data class Refused(val problem: ArmProblem, val codes: TimerCodeProblem? = null) : ArmResult
}

/** What « Je vais bien » gives. */
sealed interface CheckInResult {
    /**
     * The timer is over on this device. The very same answer for the « je vais bien » code (the
     * cancellations wait for [TimerService.flush]) and for the duress code (nothing is sent): the
     * screen cannot show anything different.
     */
    data object Cancelled : CheckInResult

    data class Wrong(val failedAttempts: Int, val waitSeconds: Long) : CheckInResult

    /** Too early after wrong codes: nothing was checked. */
    data class TooEarly(val waitSeconds: Long) : CheckInResult

    data object NoTimer : CheckInResult
}

/** What « Repousser » gives. */
sealed interface ReportResult {
    /** The same answer for both codes (with the duress code, only the screen moves). */
    data class Reported(val shownDeadline: Long) : ReportResult

    data class Wrong(val failedAttempts: Int, val waitSeconds: Long) : ReportResult

    data class TooEarly(val waitSeconds: Long) : ReportResult

    data object InvalidDeadline : ReportResult

    data object NoTimer : ReportResult
}

/**
 * The timer of the person on this device (AG-55, D-088), without any interface: arming (the
 * deposits sealed for each recipient, kept on the device until the server takes them), « Je vais
 * bien » and « Repousser » with the « je vais bien » code or the duress code, and the deliveries
 * waiting for the network. Every slow step (two PBKDF2 derivations per code) runs on the caller's
 * thread: call from a worker.
 *
 * What is kept: [TimerStore]; what leaves: one gift wrap per recipient and per message, through
 * [TimerTransport]. The server never learns that a timer exists, nor its deadline.
 */
class TimerService(
    private val store: TimerStore,
    pepper: (ByteArray) -> ByteArray = { it },
    private val clock: () -> Long = ::currentEpochSeconds,
    iterations: Int = AppLock.ITERATIONS,
) {
    private val codes = TimerCodes(pepper, iterations)
    private val flushing = Mutex()

    /** Seconds to wait before the next code can be tried (wrong codes, as on the lock screen); 0 at once. */
    fun waitSeconds(timer: ArmedTimer): Long {
        val delay = UnlockDelays.afterFailures(timer.failedAttempts)
        if (delay == 0L) return 0
        val elapsed = (clock() - timer.lastFailureEpochSeconds).coerceAtLeast(0)
        return (delay - elapsed).coerceIn(0, delay)
    }

    /**
     * Arms a timer: draws its id and its secret, hides the secret behind the « je vais bien » code,
     * seals one deposit per recipient, keeps everything on the device, then tries to deliver. The
     * deposits the server cannot take now wait for [flush]; until they leave, the alert cannot go.
     */
    suspend fun arm(request: ArmRequest, transport: TimerTransport): ArmResult {
        val now = clock()
        validate(request, now)?.let { return it }
        if (store.read().armed != null) return ArmResult.Refused(ArmProblem.ALREADY_ARMED)
        val author = transport.author.hex
        val recipientKeys = try {
            request.recipients.map { PublicKey.fromHex(it.key) }
        } catch (e: IllegalArgumentException) {
            return ArmResult.Refused(ArmProblem.INVALID_RECIPIENT)
        }
        val text = TimerFormats.cleanText(request.text)
        val place = TimerFormats.cleanPlace(request.place)
        val id = secureRandomBytes(TimerFormats.SECRET_BYTES).toHexString()
        val secret = secureRandomBytes(TimerFormats.SECRET_BYTES)
        val salt = secureRandomBytes(TimerCodes.SALT_SIZE)
        val nonce = secureRandomBytes(TimerCodes.NONCE_SIZE)
        val key = codes.derive(request.checkInCode, salt)
        try {
            val hash = TimerFormats.hashOf(secret)
            val box = TimerCodes.box(secret, key, nonce)
            val duress = request.duressCode?.takeIf { it.isNotEmpty() }?.let(codes::duressRecord)
            val deliveries = try {
                request.recipients.zip(recipientKeys).map { (recipient, publicKey) ->
                    val rumor = TimerFormats.deposit(author, recipient.key, id, now, request.deadline, hash, text, place)
                    PendingDelivery(id, TimerFormats.ACTION_DEPOSIT, recipient.key, transport.seal(rumor, publicKey).toJson(), now)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ArmResult.Refused(ArmProblem.INVALID_RECIPIENT)
            }
            val timer = ArmedTimer(
                id = id,
                armedAt = now,
                deadline = request.deadline,
                recipients = request.recipients,
                text = text,
                place = place,
                hashHex = hash,
                secretNonceHex = nonce.toHexString(),
                secretBoxHex = box.toHexString(),
                codeSaltHex = salt.toHexString(),
                codeIterations = codes.iterations,
                duress = duress,
            )
            val stored = store.update { data ->
                if (data.armed != null) {
                    Pair(data, false)
                } else {
                    Pair(data.copy(owner = author, armed = timer, pending = data.pending + deliveries), true)
                }
            }
            if (!stored) return ArmResult.Refused(ArmProblem.ALREADY_ARMED)
        } finally {
            secret.fill(0)
            key.fill(0)
        }
        return ArmResult.Armed(request.deadline, request.recipients.size, flush(transport))
    }

    /**
     * « Je vais bien ». With the « je vais bien » code, the cancellations (the current secret) are
     * sealed for every recipient and queued for [flush], after whatever still waits (a deposit or a
     * report that has not left yet goes first, so that the recipients can check the cancellation).
     * With the duress code, nothing is prepared and nothing will be sent: the timer simply
     * disappears from this device, while the deposits already on the recipients' devices still show
     * the alert at the deadline (and those still waiting here still leave). Both give
     * [CheckInResult.Cancelled].
     */
    suspend fun checkIn(code: String, transport: TimerTransport): CheckInResult {
        val timer = store.read().armed ?: return CheckInResult.NoTimer
        val wait = waitSeconds(timer)
        if (wait > 0) return CheckInResult.TooEarly(wait)
        return when (val match = codes.match(timer, code)) {
            is CodeMatch.CheckIn -> try {
                val now = clock()
                val author = transport.author.hex
                val secretHex = match.secret.toHexString()
                val cancellations = timer.recipients.mapNotNull { recipient ->
                    sealOrNull(transport, TimerFormats.cancel(author, recipient.key, timer.id, now, secretHex), recipient.key)
                        ?.let { PendingDelivery(timer.id, TimerFormats.ACTION_CANCEL, recipient.key, it, now) }
                }
                store.update { data -> Pair(data.copy(armed = null, pending = data.pending + cancellations), Unit) }
                CheckInResult.Cancelled
            } finally {
                match.wipe()
            }
            CodeMatch.Duress -> {
                // Nothing is prepared, nothing will be sent: only the screen believes the timer is over.
                store.update { data -> Pair(data.copy(armed = null), Unit) }
                CheckInResult.Cancelled
            }
            CodeMatch.Wrong -> {
                val failures = recordFailure(timer)
                CheckInResult.Wrong(failures, UnlockDelays.afterFailures(failures))
            }
        }
    }

    /**
     * « Repousser » to [newDeadline]. With the « je vais bien » code, a report (the current secret,
     * the new deadline and the hash of a new secret) is sealed for every recipient and kept for
     * [flush]; the new secret replaces the old one in the box. With the duress code, only the
     * screen moves: nothing is sent, the alert still goes at the deadline the recipients know.
     */
    suspend fun report(code: String, newDeadline: Long, transport: TimerTransport): ReportResult {
        val timer = store.read().armed ?: return ReportResult.NoTimer
        val now = clock()
        if (newDeadline - now < TimerFormats.MIN_DURATION_SECONDS || newDeadline - now > TimerFormats.MAX_DURATION_SECONDS) {
            return ReportResult.InvalidDeadline
        }
        val wait = waitSeconds(timer)
        if (wait > 0) return ReportResult.TooEarly(wait)
        return when (val match = codes.match(timer, code)) {
            is CodeMatch.CheckIn -> {
                val next = secureRandomBytes(TimerFormats.SECRET_BYTES)
                try {
                    val author = transport.author.hex
                    val secretHex = match.secret.toHexString()
                    val nextHash = TimerFormats.hashOf(next)
                    val nonce = secureRandomBytes(TimerCodes.NONCE_SIZE)
                    val box = TimerCodes.box(next, match.key, nonce)
                    val reports = timer.recipients.mapNotNull { recipient ->
                        sealOrNull(transport, TimerFormats.report(author, recipient.key, timer.id, now, secretHex, newDeadline, nextHash), recipient.key)
                            ?.let { PendingDelivery(timer.id, TimerFormats.ACTION_REPORT, recipient.key, it, now) }
                    }
                    val moved = timer.copy(
                        deadline = newDeadline,
                        shownDeadline = newDeadline,
                        hashHex = nextHash,
                        secretNonceHex = nonce.toHexString(),
                        secretBoxHex = box.toHexString(),
                        failedAttempts = 0,
                        lastFailureEpochSeconds = 0,
                    )
                    store.update { data -> data.copy(armed = moved, pending = data.pending + reports) to Unit }
                    ReportResult.Reported(newDeadline)
                } finally {
                    next.fill(0)
                    match.wipe()
                }
            }
            CodeMatch.Duress -> {
                store.update { data ->
                    val current = data.armed
                    val moved = if (current != null && current.id == timer.id) current.copy(shownDeadline = newDeadline, failedAttempts = 0, lastFailureEpochSeconds = 0) else current
                    data.copy(armed = moved) to Unit
                }
                ReportResult.Reported(newDeadline)
            }
            CodeMatch.Wrong -> {
                val failures = recordFailure(timer)
                ReportResult.Wrong(failures, UnlockDelays.afterFailures(failures))
            }
        }
    }

    /**
     * Sends the messages waiting on the device, oldest first (so that a report always reaches a
     * recipient before the cancellation that follows it), each wrapped anew with fresh dates. What is
     * queued while it runs is sent by the same flush. Stops at the first network failure. One flush
     * at a time; a second call while one runs returns at once (the running one takes everything).
     */
    suspend fun flush(transport: TimerTransport): DeliveryReport {
        if (!flushing.tryLock()) return DeliveryReport(queued = store.read().pending.size)
        try {
            val author = transport.author.hex
            // Seals of another account (left behind by a former account) are never sent by this one.
            val skipped = mutableListOf<PendingDelivery>()
            var report = DeliveryReport()
            while (true) {
                val delivery = store.read().pending.firstOrNull { it !in skipped } ?: break
                val seal = runCatching { SignedEvent.fromJson(delivery.seal) }.getOrNull()
                if (seal == null || !isLowercaseHex(delivery.recipient, 64)) {
                    remove(delivery)
                    report = report.copy(rejected = report.rejected + 1)
                    continue
                }
                if (seal.pubkey != author) {
                    skipped += delivery
                    continue
                }
                val result = try {
                    transport.deliver(seal, PublicKey.fromHex(delivery.recipient))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PublicationResult.Unavailable(e)
                }
                when (result) {
                    is PublicationResult.Accepted -> {
                        remove(delivery)
                        report = report.copy(delivered = report.delivered + 1)
                    }
                    is PublicationResult.Rejected -> {
                        remove(delivery)
                        report = report.copy(rejected = report.rejected + 1)
                    }
                    is PublicationResult.Unavailable -> {
                        val left = store.read().pending.count { it !in skipped }
                        return report.copy(queued = left)
                    }
                }
            }
            return report
        } finally {
            flushing.unlock()
        }
    }

    private fun validate(request: ArmRequest, now: Long): ArmResult.Refused? {
        val keys = request.recipients.map { it.key }
        return when {
            request.recipients.isEmpty() -> ArmResult.Refused(ArmProblem.NO_RECIPIENT)
            request.recipients.size > MAX_RECIPIENTS -> ArmResult.Refused(ArmProblem.TOO_MANY_RECIPIENTS)
            keys.any { !isLowercaseHex(it, 64) } || keys.distinct().size != keys.size -> ArmResult.Refused(ArmProblem.INVALID_RECIPIENT)
            request.deadline - now < TimerFormats.MIN_DURATION_SECONDS -> ArmResult.Refused(ArmProblem.DEADLINE_TOO_SOON)
            request.deadline - now > TimerFormats.MAX_DURATION_SECONDS -> ArmResult.Refused(ArmProblem.DEADLINE_TOO_FAR)
            TimerFormats.cleanText(request.text).isEmpty() -> ArmResult.Refused(ArmProblem.NO_TEXT)
            else -> TimerCodeRules.check(request.checkInCode, request.checkInConfirmation, request.duressCode)
                ?.let { ArmResult.Refused(ArmProblem.CODES, it) }
        }
    }

    private suspend fun sealOrNull(transport: TimerTransport, rumor: Rumor, recipient: String): String? = try {
        transport.seal(rumor, PublicKey.fromHex(recipient)).toJson()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun recordFailure(timer: ArmedTimer): Int {
        val failures = timer.failedAttempts + 1
        val now = clock()
        store.update { data ->
            val current = data.armed
            val counted = if (current != null && current.id == timer.id) current.copy(failedAttempts = failures, lastFailureEpochSeconds = now) else current
            data.copy(armed = counted) to Unit
        }
        return failures
    }

    private suspend fun remove(delivery: PendingDelivery) {
        store.update { data -> data.copy(pending = data.pending - delivery) to Unit }
    }

    companion object {
        /** At most this many recipients for one timer. */
        const val MAX_RECIPIENTS: Int = 20
    }
}
