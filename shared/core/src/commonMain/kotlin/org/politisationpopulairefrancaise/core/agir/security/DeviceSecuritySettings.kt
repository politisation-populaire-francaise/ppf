package org.politisationpopulairefrancaise.core.agir.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The slow, salted hash of the unlock code (S-43): never the code itself, never its length.
 * Deliberately not a data class: it must never be printed.
 */
@Serializable
class PinRecord(
    val algorithm: String = ALGORITHM,
    val iterations: Int,
    val saltHex: String,
    val hashHex: String,
) {
    override fun toString(): String = "PinRecord($algorithm)"

    companion object {
        const val ALGORITHM: String = "pbkdf2-hmac-sha256"
    }
}

/**
 * The security settings of this device (M33: S-43 and S-45), kept on the device only and never
 * sent. [lock] null means the lock is off. [failedAttempts] and [lastFailureEpochSeconds] survive a
 * restart, so that closing the application does not reset the growing delay between wrong codes.
 */
@Serializable
data class DeviceSecuritySettings(
    val version: Int = 1,
    val lock: PinRecord? = null,
    /** How long the application may stay in the background before it locks again; 0: at once. */
    val lockAfterSeconds: Int = 0,
    /**
     * Hides the content from the system's list of recent applications, from screenshots and from
     * screen recordings (Android FLAG_SECURE). On by default: the most protective (principle 10).
     */
    val hideContent: Boolean = true,
    val failedAttempts: Int = 0,
    val lastFailureEpochSeconds: Long = 0,
) {
    val lockEnabled: Boolean get() = lock != null

    fun encode(): String = json.encodeToString(serializer(), this)

    override fun toString(): String = "DeviceSecuritySettings(lock=${lock != null}, after=$lockAfterSeconds, hide=$hideContent, failed=$failedAttempts)"

    companion object {
        /** The choices of « Verrouiller après » : at once, 1, 5 or 15 minutes. */
        val LOCK_AFTER_CHOICES: List<Int> = listOf(0, 60, 300, 900)

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The settings written by [encode]; the defaults when nothing was written or it cannot be read. */
        fun decode(text: String?): DeviceSecuritySettings =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: DeviceSecuritySettings()
    }
}

/**
 * Where the device keeps its [DeviceSecuritySettings]: a private preference on Android (system
 * backups refused by the manifest), a private file on a computer. Nothing is ever sent.
 */
interface DeviceSecurityStorage {
    fun load(): String?

    fun save(value: String)

    fun clear()

    /**
     * Mixes the PBKDF2 output with a secret the platform keeps apart from the stored hash, when it
     * has one: on Android an HMAC key of the Android Keystore, which cannot be read out of the phone.
     * A copy of the settings file is then useless to try every code on another machine. The default
     * returns [derived] unchanged (computer, tests).
     */
    fun pepper(derived: ByteArray): ByteArray = derived
}

/** For tests and platforms without storage: forgotten with the process. */
class InMemoryDeviceSecurityStorage(initial: String? = null) : DeviceSecurityStorage {
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
