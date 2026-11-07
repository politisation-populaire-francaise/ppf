package org.politisationpopulairefrancaise.server.agir.testimonies

import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyFormats
import org.politisationpopulairefrancaise.core.agir.testimonies.TestimonyRecipients
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.media.NoTimestamping
import org.politisationpopulairefrancaise.core.media.Timestamping
import java.nio.file.Path
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the testimonies, read from the environment only:
 *
 * - PPF_TESTIMONY_DIR: the folder of the encrypted files and of the published photos. Unset: the
 *   testimony routes answer 503 (nothing is ever written elsewhere).
 * - PPF_MODERATION_KEY, PPF_EVIDENCE_KEY: the public keys (hexadecimal or npub) testimonies must be
 *   encrypted for. Unset outside production: the example keys ([TestimonyRecipients.EXAMPLE], whose
 *   secret halves are public). In production both are required and must not be the example keys,
 *   otherwise the testimony routes stay closed (503).
 * - PPF_TESTIMONY_MAX_FILE_MB (32), PPF_TESTIMONY_MAX_DISK_MB (4096): size of one encrypted file, and
 *   of all of them together (507 storage_full beyond).
 * - PPF_TESTIMONY_RETENTION_DAYS (30): how long a decided testimony and its encrypted files stay
 *   after the decision. PPF_EVIDENCE_RETENTION_DAYS (2190): how long an original kept for the
 *   lawyers stays. PPF_TESTIMONY_CLEANUP_INTERVAL_MINUTES (60; 0 disables the task, for tests).
 */
data class TestimonyConfig(
    val directory: Path?,
    val recipients: TestimonyRecipients,
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    val retention: Duration = 30.days,
    val evidenceRetention: Duration = 2190.days,
    val cleanupInterval: Duration = 1.hours,
    /** A file no envelope claims is deleted after this. */
    val orphanRetention: Duration = 24.hours,
    /** False when the routes must stay closed (no folder, or example keys in production); [closedReason] says why. */
    val closedReason: String? = null,
) {
    val open: Boolean get() = directory != null && closedReason == null

    companion object {
        const val DIRECTORY_VARIABLE: String = "PPF_TESTIMONY_DIR"
        const val MODERATION_KEY_VARIABLE: String = "PPF_MODERATION_KEY"
        const val EVIDENCE_KEY_VARIABLE: String = "PPF_EVIDENCE_KEY"
        const val DEFAULT_MAX_FILE_BYTES: Long = TestimonyFormats.MAX_FILE_BYTES.toLong()
        const val DEFAULT_MAX_DISK_BYTES: Long = 4096L * 1024 * 1024

        fun parseKey(value: String?): PublicKey? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { if (text.startsWith("npub1")) PublicKey.fromNpub(text) else PublicKey.fromHex(text.lowercase()) }
                .getOrNull()?.takeIf { it.hex.length == 64 }
        }

        fun fromEnvironment(env: (String) -> String? = System::getenv): TestimonyConfig {
            val production = env("PPF_ENVIRONMENT") == "production"
            val moderation = parseKey(env(MODERATION_KEY_VARIABLE))
            val evidence = parseKey(env(EVIDENCE_KEY_VARIABLE))
            val recipients = TestimonyRecipients(
                moderation ?: TestimonyRecipients.EXAMPLE.moderation,
                evidence ?: TestimonyRecipients.EXAMPLE.evidence,
            )
            val closed = when {
                production && (moderation == null || evidence == null) -> "the moderation and evidence keys are required in production"
                production && (recipients.moderation == TestimonyRecipients.EXAMPLE.moderation || recipients.evidence == TestimonyRecipients.EXAMPLE.evidence) ->
                    "the example keys are refused in production"
                recipients.moderation == recipients.evidence -> "the moderation and evidence keys must differ"
                else -> null
            }
            fun megabytes(name: String, default: Long) = env(name)?.trim()?.toLongOrNull()?.takeIf { it >= 1 }?.let { it * 1024 * 1024 } ?: default
            fun days(name: String, default: Duration) = env(name)?.trim()?.toIntOrNull()?.takeIf { it >= 1 }?.days ?: default
            return TestimonyConfig(
                directory = env(DIRECTORY_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { Path.of(it) },
                recipients = recipients,
                maxFileBytes = megabytes("PPF_TESTIMONY_MAX_FILE_MB", DEFAULT_MAX_FILE_BYTES),
                maxDiskBytes = megabytes("PPF_TESTIMONY_MAX_DISK_MB", DEFAULT_MAX_DISK_BYTES),
                retention = days("PPF_TESTIMONY_RETENTION_DAYS", 30.days),
                evidenceRetention = days("PPF_EVIDENCE_RETENTION_DAYS", 2190.days),
                cleanupInterval = env("PPF_TESTIMONY_CLEANUP_INTERVAL_MINUTES")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.minutes ?: 1.hours,
                closedReason = closed,
            )
        }
    }
}

/** What the testimonies need from outside: settings, clock and the timestamped archive (D-111; a stand-in until it reaches main). */
class TestimonySetup(
    val config: TestimonyConfig,
    val clock: Clock = Clock.systemUTC(),
    val timestamping: Timestamping = NoTimestamping,
) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): TestimonySetup = TestimonySetup(TestimonyConfig.fromEnvironment(env))
    }
}
