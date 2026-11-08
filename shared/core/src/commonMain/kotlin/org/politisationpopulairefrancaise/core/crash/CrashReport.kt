package org.politisationpopulairefrancaise.core.crash

/**
 * One exception of a crash, as a report may show it: its class and its stack frames, never its message.
 * A message can quote anything the code had in hand (a text being written, an address, a key), so it is
 * left out on purpose (S-101, cahier des charges, section 5.9).
 */
data class CrashedException(val className: String, val frames: List<String>)

/**
 * Crash reports written on the device after a crash and offered at the next launch (S-101): the version,
 * the system, the day, and the chain of exception classes with their frames. No message, no content, no
 * key, no identifier of the person or of the device. Nothing is ever sent by the application: the person
 * reads the whole report and chooses to copy it, share it, or erase it. A report older than
 * [MAX_AGE_SECONDS] is erased without being shown.
 */
object CrashReports {
    /** A week: long enough to see it at the next launch, short enough not to keep it (data register). */
    const val MAX_AGE_SECONDS: Long = 7L * 24 * 3600

    /** Bounds of a report, so that a deep recursion does not write megabytes. */
    const val MAX_CAUSES: Int = 5
    const val MAX_FRAMES: Int = 40
    const val MAX_CHARS: Int = 16_000

    /**
     * The text of a report. [writtenAtEpochSeconds] is kept to the day only (UTC), which is enough to
     * know which version was running and says nothing about what the person was doing.
     */
    fun format(version: String, system: String, writtenAtEpochSeconds: Long, chain: List<CrashedException>): String {
        val text = buildString {
            append("Rapport de plantage PPF (rien n'est envoyé sans vous)\n")
            append("Version : ").append(clean(version)).append('\n')
            append("Système : ").append(clean(system)).append('\n')
            append("Jour (UTC) : ").append(utcDay(writtenAtEpochSeconds)).append('\n')
            chain.take(MAX_CAUSES).forEachIndexed { index, exception ->
                append(if (index == 0) "Erreur : " else "Cause : ").append(clean(exception.className)).append('\n')
                exception.frames.take(MAX_FRAMES).forEach { append("  at ").append(clean(it)).append('\n') }
                if (exception.frames.size > MAX_FRAMES) append("  … ").append(exception.frames.size - MAX_FRAMES).append(" de plus\n")
            }
            if (chain.size > MAX_CAUSES) append("… ").append(chain.size - MAX_CAUSES).append(" causes de plus\n")
        }
        return if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n…\n"
    }

    /** Whether a report written at [writtenAtEpochSeconds] is too old to be shown at [nowEpochSeconds]. */
    fun isExpired(writtenAtEpochSeconds: Long, nowEpochSeconds: Long): Boolean =
        nowEpochSeconds - writtenAtEpochSeconds > MAX_AGE_SECONDS || writtenAtEpochSeconds > nowEpochSeconds + 3600

    /**
     * Class names and frames come from the code, not from the person; still, anything outside a short,
     * printable line is dropped, and so is anything that looks like a key or a long identifier.
     */
    private fun clean(value: String): String {
        val printable = value.filter { it.code in 0x20..0x7e }.take(300)
        return LONG_HEX.replace(BECH32_KEY.replace(printable, "[clé retirée]"), "[valeur retirée]")
    }

    private val BECH32_KEY = Regex("\\b(npub|nsec|nprofile|nevent|naddr|note)1[02-9ac-hj-np-z]{20,}\\b")
    private val LONG_HEX = Regex("\\b[0-9a-fA-F]{32,}\\b")

    /** `YYYY-MM-DD` of an epoch second, in UTC, without a date library (civil-from-days algorithm). */
    internal fun utcDay(epochSeconds: Long): String {
        val days = epochSeconds.floorDiv(86_400L)
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}

/**
 * The exception chain of [this] (the error, then its causes), with classes and frames only.
 * Platform code: frames are only readable on the JVM and on Android (expect/actual).
 */
expect fun Throwable.crashChain(): List<CrashedException>
