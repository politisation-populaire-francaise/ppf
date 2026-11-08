package org.politisationpopulairefrancaise.core.agir.security

import org.politisationpopulairefrancaise.core.crypto.constantTimeEquals
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import kotlin.time.TimeSource

/** Why a new unlock code is refused. */
enum class PinProblem { TOO_SHORT, TOO_LONG, NOT_DIGITS, TOO_SIMPLE, MISMATCH }

/** The rules of the unlock code (S-43): digits only, 6 to 16 of them, not a trivial sequence. */
object PinRules {
    const val MIN_LENGTH: Int = 6
    const val MAX_LENGTH: Int = 16

    fun check(pin: String, confirmation: String = pin): PinProblem? = when {
        pin.any { it !in '0'..'9' } -> PinProblem.NOT_DIGITS
        pin.length < MIN_LENGTH -> PinProblem.TOO_SHORT
        pin.length > MAX_LENGTH -> PinProblem.TOO_LONG
        isTrivial(pin) -> PinProblem.TOO_SIMPLE
        pin != confirmation -> PinProblem.MISMATCH
        else -> null
    }

    /** The same digit repeated, a straight run up or down (123456, 987654), or a pattern repeated (121212, 123123). */
    fun isTrivial(pin: String): Boolean {
        if (pin.isEmpty()) return true
        val digits = pin.map { it - '0' }
        val steps = digits.zipWithNext { a, b -> (b - a + 10) % 10 }.toSet()
        if (steps.size == 1 && steps.first() in setOf(0, 1, 9)) return true
        for (period in 1..pin.length / 2) {
            if (pin.length % period == 0 && pin == pin.take(period).repeat(pin.length / period)) return true
        }
        return false
    }
}

/** What happened when a code was typed on the lock screen. */
sealed interface UnlockResult {
    data object Unlocked : UnlockResult

    /** Wrong code; [waitSeconds] before the next try (0: at once). */
    data class Wrong(val failedAttempts: Int, val waitSeconds: Long) : UnlockResult

    /** Too early after wrong codes: nothing was checked. */
    data class TooEarly(val waitSeconds: Long) : UnlockResult
}

/**
 * The growing delay between wrong codes: the first [FREE_ATTEMPTS] are free, then 30 seconds,
 * 1, 5, 15 minutes, then one hour for every further try. The counter goes back to zero only after
 * the right code. No automatic erasure after many tries: a child or a stranger could then erase
 * the user's data, and an erasure under constraint needs a lawyer's opinion first (S-44).
 */
object UnlockDelays {
    const val FREE_ATTEMPTS: Int = 5
    private val steps = listOf(30L, 60L, 300L, 900L)
    const val MAX_SECONDS: Long = 3600

    fun afterFailures(failures: Int): Long = when {
        failures < FREE_ATTEMPTS -> 0
        else -> steps.getOrNull(failures - FREE_ATTEMPTS) ?: MAX_SECONDS
    }
}

/** The clocks of the lock: the wall clock (survives a restart) and a monotonic one (immune to a changed clock). */
interface LockClock {
    fun epochSeconds(): Long

    fun monotonicMillis(): Long

    companion object {
        private val start = TimeSource.Monotonic.markNow()

        val System: LockClock = object : LockClock {
            override fun epochSeconds(): Long = currentEpochSeconds()

            override fun monotonicMillis(): Long = start.elapsedNow().inWholeMilliseconds
        }
    }
}

/**
 * The lock of the application (S-43), without any interface: the code is checked on the device
 * against its slow, salted hash ([PinRecord], PBKDF2-HMAC-SHA-256, [ITERATIONS] iterations as
 * advised by OWASP in 2023, peppered by the platform when it can, [DeviceSecurityStorage.pepper]).
 * Nothing is ever sent to the server.
 *
 * The application starts locked when the lock is on; it locks again when it comes back after
 * [DeviceSecuritySettings.lockAfterSeconds] in the background (measured on both clocks, so that a
 * phone asleep or a changed clock does not stretch the delay).
 *
 * [hash] and [verify] are slow (a fraction of a second): call them off the main thread.
 */
class AppLock(
    private val storage: DeviceSecurityStorage,
    private val clock: LockClock = LockClock.System,
    private val iterations: Int = ITERATIONS,
) {
    var settings: DeviceSecuritySettings = DeviceSecuritySettings.decode(runCatching { storage.load() }.getOrNull())
        private set

    var isLocked: Boolean = settings.lockEnabled
        private set

    private var backgroundSince: Pair<Long, Long>? = null

    /** The application went to the background (another application, the screen turned off, the window minimised). */
    fun onBackground() {
        if (backgroundSince == null) backgroundSince = clock.monotonicMillis() to clock.epochSeconds()
    }

    /** The application is shown again: locks when it stayed away long enough. */
    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (!settings.lockEnabled) return
        val awayMillis = maxOf(clock.monotonicMillis() - since.first, (clock.epochSeconds() - since.second) * 1000)
        if (awayMillis >= settings.lockAfterSeconds * 1000L) isLocked = true
    }

    /** Locks now (the « Verrouiller maintenant » button). */
    fun lockNow() {
        if (settings.lockEnabled) isLocked = true
    }

    /** Seconds to wait before the next try; 0 when a code can be typed. */
    fun waitSeconds(): Long {
        val delay = UnlockDelays.afterFailures(settings.failedAttempts)
        if (delay == 0L) return 0
        val now = clock.epochSeconds()
        // A clock set back in time does not stretch the wait beyond the delay itself.
        val elapsed = (now - settings.lastFailureEpochSeconds).coerceAtLeast(0)
        return (delay - elapsed).coerceIn(0, delay)
    }

    fun unlock(pin: String): UnlockResult {
        val record = settings.lock ?: return UnlockResult.Unlocked.also { isLocked = false }
        val wait = waitSeconds()
        if (wait > 0) return UnlockResult.TooEarly(wait)
        if (verify(record, pin)) {
            isLocked = false
            if (settings.failedAttempts != 0) update(settings.copy(failedAttempts = 0, lastFailureEpochSeconds = 0))
            return UnlockResult.Unlocked
        }
        val failures = settings.failedAttempts + 1
        update(settings.copy(failedAttempts = failures, lastFailureEpochSeconds = clock.epochSeconds()))
        return UnlockResult.Wrong(failures, UnlockDelays.afterFailures(failures))
    }

    /** Turns the lock on with [pin] (typed twice); the application stays unlocked until it is left. */
    fun enable(pin: String, confirmation: String): PinProblem? {
        PinRules.check(pin, confirmation)?.let { return it }
        update(settings.copy(lock = hash(pin), failedAttempts = 0, lastFailureEpochSeconds = 0))
        isLocked = false
        return null
    }

    /** Changes the code; false when [current] is wrong (counted as a wrong try). */
    fun change(current: String, pin: String, confirmation: String): ChangeResult {
        val record = settings.lock ?: return ChangeResult.Refused(null)
        if (waitSeconds() > 0) return ChangeResult.WrongCurrent
        PinRules.check(pin, confirmation)?.let { return ChangeResult.Refused(it) }
        if (!verify(record, current)) {
            update(settings.copy(failedAttempts = settings.failedAttempts + 1, lastFailureEpochSeconds = clock.epochSeconds()))
            return ChangeResult.WrongCurrent
        }
        update(settings.copy(lock = hash(pin), failedAttempts = 0, lastFailureEpochSeconds = 0))
        return ChangeResult.Changed
    }

    /** Turns the lock off; false when [current] is wrong (counted as a wrong try). */
    fun disable(current: String): Boolean {
        val record = settings.lock ?: return true
        if (waitSeconds() > 0) return false
        if (!verify(record, current)) {
            update(settings.copy(failedAttempts = settings.failedAttempts + 1, lastFailureEpochSeconds = clock.epochSeconds()))
            return false
        }
        update(settings.copy(lock = null, failedAttempts = 0, lastFailureEpochSeconds = 0))
        isLocked = false
        return true
    }

    fun setLockAfter(seconds: Int) {
        require(seconds in DeviceSecuritySettings.LOCK_AFTER_CHOICES) { "not a proposed delay" }
        update(settings.copy(lockAfterSeconds = seconds))
    }

    fun setHideContent(hide: Boolean) {
        update(settings.copy(hideContent = hide))
    }

    /** Forgets every setting (part of the quick erase, S-44): back to the defaults, unlocked. */
    fun forget() {
        runCatching { storage.clear() }
        settings = DeviceSecuritySettings()
        isLocked = false
        backgroundSince = null
    }

    private fun update(next: DeviceSecuritySettings) {
        storage.save(next.encode())
        settings = next
    }

    private fun hash(pin: String): PinRecord {
        val salt = secureRandomBytes(SALT_SIZE)
        val derived = derive(pin, salt, iterations)
        return PinRecord(iterations = iterations, saltHex = salt.toHexString(), hashHex = derived.toHexString()).also { derived.fill(0) }
    }

    private fun verify(record: PinRecord, pin: String): Boolean {
        if (record.algorithm != PinRecord.ALGORITHM) return false
        if (pin.isEmpty() || pin.length > PinRules.MAX_LENGTH || pin.any { it !in '0'..'9' }) return false
        val salt = runCatching { record.saltHex.hexToByteArray() }.getOrNull() ?: return false
        val expected = runCatching { record.hashHex.hexToByteArray() }.getOrNull() ?: return false
        val derived = derive(pin, salt, record.iterations)
        return constantTimeEquals(derived, expected).also { derived.fill(0) }
    }

    private fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val password = pin.encodeToByteArray()
        val stretched = pbkdf2Sha256(password, salt, rounds, HASH_SIZE)
        password.fill(0)
        val peppered = storage.pepper(stretched)
        if (peppered !== stretched) stretched.fill(0)
        return peppered
    }

    companion object {
        /** OWASP Password Storage Cheat Sheet (2023): 600 000 iterations for PBKDF2-HMAC-SHA-256. */
        const val ITERATIONS: Int = 600_000
        const val SALT_SIZE: Int = 16
        const val HASH_SIZE: Int = 32
    }
}

sealed interface ChangeResult {
    data object Changed : ChangeResult

    data object WrongCurrent : ChangeResult

    data class Refused(val problem: PinProblem?) : ChangeResult
}
