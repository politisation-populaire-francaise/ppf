package org.politisationpopulairefrancaise.core.agir.timer

import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.security.PinRecord
import org.politisationpopulairefrancaise.core.agir.security.PinRules
import org.politisationpopulairefrancaise.core.agir.security.pbkdf2Sha256
import org.politisationpopulairefrancaise.core.crypto.HkdfSha256
import org.politisationpopulairefrancaise.core.crypto.constantTimeEquals
import org.politisationpopulairefrancaise.core.crypto.secureRandomBytes

/** Why the codes chosen for a timer are refused. */
enum class TimerCodeProblem {
    /** The « je vais bien » code is not 4 to 8 digits. */
    CHECK_IN_FORMAT,

    /** The « je vais bien » code is too easy to guess (1234, 0000, 1212). */
    CHECK_IN_SIMPLE,

    /** The two copies of the « je vais bien » code differ. */
    CHECK_IN_MISMATCH,

    /** The duress code is not 4 to 8 digits. */
    DURESS_FORMAT,

    /** The duress code is the « je vais bien » code. */
    DURESS_SAME,
}

/**
 * The two codes of a timer (D-088): the « je vais bien » code, which cancels for real, and the
 * optional duress code, which shows exactly the same « Minuteur annulé » but sends nothing. Digits
 * only, 4 to 8 of them, the duress code different from the other. Shorter than the lock code of
 * the application (6 to 16, S-43): it is typed under stress, sometimes in front of someone, and a
 * wrong code costs a growing wait, as on the lock screen.
 */
object TimerCodeRules {
    const val MIN_LENGTH: Int = 4
    const val MAX_LENGTH: Int = 8

    fun isWellFormed(code: String): Boolean = code.length in MIN_LENGTH..MAX_LENGTH && code.all { it in '0'..'9' }

    /** [duress] null or empty: no duress code (allowed, but strongly suggested by the screen). */
    fun check(checkIn: String, confirmation: String, duress: String?): TimerCodeProblem? {
        val other = duress?.takeIf { it.isNotEmpty() }
        return when {
            !isWellFormed(checkIn) -> TimerCodeProblem.CHECK_IN_FORMAT
            PinRules.isTrivial(checkIn) -> TimerCodeProblem.CHECK_IN_SIMPLE
            checkIn != confirmation -> TimerCodeProblem.CHECK_IN_MISMATCH
            other != null && !isWellFormed(other) -> TimerCodeProblem.DURESS_FORMAT
            other != null && other == checkIn -> TimerCodeProblem.DURESS_SAME
            else -> null
        }
    }
}

/** Which code was typed. */
internal sealed interface CodeMatch {
    /** The « je vais bien » code: the current secret of the timer and the key that boxes it (wipe both after use). */
    class CheckIn(val secret: ByteArray, val key: ByteArray) : CodeMatch {
        fun wipe() {
            secret.fill(0)
            key.fill(0)
        }
    }

    data object Duress : CodeMatch

    data object Wrong : CodeMatch
}

/**
 * How the codes of a timer are kept (D-088): never the codes themselves, only what a slow
 * derivation of them gives (PBKDF2-HMAC-SHA-256, [AppLock.ITERATIONS] iterations as for the lock of
 * the application, D-082, peppered by the platform when it can: an HMAC key of the Android
 * Keystore, through [pepper]).
 *
 * - The « je vais bien » code is never stored even as a hash: it derives the key that hides the
 *   current secret of the timer ([box]). A copy of the device's files therefore cannot cancel the
 *   alert without the code, and checking a code means opening the box and comparing the SHA-256 of
 *   what comes out with the announced hash.
 * - The duress code is kept as a slow hash ([PinRecord]).
 *
 * Checking a code always runs both derivations, whichever code is typed, so that the time taken
 * does not tell the two codes apart. [match] is slow (a fraction of a second, twice): call it off
 * the main thread.
 */
internal class TimerCodes(
    private val pepper: (ByteArray) -> ByteArray,
    val iterations: Int = AppLock.ITERATIONS,
) {
    /** The key the « je vais bien » code gives with [salt]. */
    fun derive(code: String, salt: ByteArray, rounds: Int = iterations): ByteArray {
        val password = code.encodeToByteArray()
        val stretched = pbkdf2Sha256(password, salt, rounds, KEY_SIZE)
        password.fill(0)
        val peppered = pepper(stretched)
        if (peppered !== stretched) stretched.fill(0)
        return peppered
    }

    /** The slow hash of the duress code. */
    fun duressRecord(code: String): PinRecord {
        val salt = secureRandomBytes(SALT_SIZE)
        val derived = derive(code, salt)
        return PinRecord(iterations = iterations, saltHex = salt.toHexString(), hashHex = derived.toHexString()).also { derived.fill(0) }
    }

    /** Which code [code] is for [timer]. */
    fun match(timer: ArmedTimer, code: String): CodeMatch {
        val wellFormed = TimerCodeRules.isWellFormed(code)
        // The « je vais bien » code: open the box and compare the hash of what comes out.
        val salt = runCatching { timer.codeSaltHex.hexToByteArray() }.getOrNull()
        val nonce = runCatching { timer.secretNonceHex.hexToByteArray() }.getOrNull()
        val boxed = runCatching { timer.secretBoxHex.hexToByteArray() }.getOrNull()
        val key = derive(if (wellFormed) code else PLACEHOLDER, salt ?: secureRandomBytes(SALT_SIZE), timer.codeIterations)
        val secret = if (nonce != null && boxed != null && boxed.size == TimerFormats.SECRET_BYTES) {
            TimerCodes.box(boxed, key, nonce)
        } else {
            ByteArray(TimerFormats.SECRET_BYTES)
        }
        val expected = runCatching { timer.hashHex.hexToByteArray() }.getOrNull()
        val isCheckIn = wellFormed && salt != null && expected != null &&
            constantTimeEquals(TimerFormats.hashOf(secret).hexToByteArray(), expected)
        // The duress code: always derived too, even when the first code matched or there is none.
        val duress = timer.duress
        val duressSalt = duress?.let { runCatching { it.saltHex.hexToByteArray() }.getOrNull() }
        val duressExpected = duress?.let { runCatching { it.hashHex.hexToByteArray() }.getOrNull() }
        val duressDerived = derive(if (wellFormed) code else PLACEHOLDER, duressSalt ?: secureRandomBytes(SALT_SIZE), duress?.iterations ?: timer.codeIterations)
        val isDuress = wellFormed && duress != null && duress.algorithm == PinRecord.ALGORITHM && duressSalt != null && duressExpected != null &&
            constantTimeEquals(duressDerived, duressExpected)
        duressDerived.fill(0)
        return when {
            isCheckIn -> CodeMatch.CheckIn(secret, key)
            else -> {
                secret.fill(0)
                key.fill(0)
                if (isDuress) CodeMatch.Duress else CodeMatch.Wrong
            }
        }
    }

    companion object {
        const val KEY_SIZE: Int = 32
        const val SALT_SIZE: Int = 16
        const val NONCE_SIZE: Int = 16
        private const val PLACEHOLDER: String = "0000"
        private val BOX_INFO: ByteArray = "ppf-minuteur-secret-v1".encodeToByteArray()

        /**
         * Hides or reveals a secret: XOR with 32 bytes drawn from [key] and a fresh [nonce] by
         * HKDF-SHA-256 (RFC 5869). The same call boxes and opens.
         */
        fun box(secret: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
            val stream = HkdfSha256.derive(key, nonce, BOX_INFO, secret.size)
            val out = ByteArray(secret.size) { i -> (secret[i].toInt() xor stream[i].toInt()).toByte() }
            stream.fill(0)
            return out
        }
    }
}
