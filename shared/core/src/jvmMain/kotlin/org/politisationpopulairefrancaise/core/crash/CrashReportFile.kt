package org.politisationpopulairefrancaise.core.crash

import java.io.File

/**
 * The one crash report of the application, a file in its private directory (S-101). [install] writes it
 * when the application crashes, then lets the system handle the crash as before; [pending] gives it at the
 * next launch, or erases it without showing it once it is older than [CrashReports.MAX_AGE_SECONDS] (its
 * age is the file's own date: nothing else is stored). A new crash replaces the previous report.
 */
class CrashReportFile(private val file: File) {

    /** Installs the handler that writes the report; [version] and [system] are written as given. */
    fun install(version: String, system: String, nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 }) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(CrashReports.format(version, system, nowEpochSeconds(), error.crashChain())) }
            // Then the crash goes on as before (the system closes the app); without a previous handler, as the JVM would.
            if (previous != null) previous.uncaughtException(thread, error) else error.printStackTrace()
        }
    }

    /** The report to offer, or null; an expired or unreadable report is erased. */
    fun pending(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): String? {
        if (!file.isFile) return null
        val writtenAt = file.lastModified() / 1000
        if (CrashReports.isExpired(writtenAt, nowEpochSeconds) || file.length() > MAX_BYTES) {
            delete()
            return null
        }
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() } ?: run {
            delete()
            null
        }
    }

    fun delete() {
        file.delete()
        File(file.path + ".tmp").delete()
    }

    internal fun write(text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(text, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024L
    }
}
