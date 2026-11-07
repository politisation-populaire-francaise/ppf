package org.politisationpopulairefrancaise.server.seed

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.StoreOutcome
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** What one loading of the example contents did; counts only, never a content. */
data class ExampleSeedReport(
    /** JSON files read and decoded. */
    val files: Int = 0,
    /** Names of the JSON files skipped (unreadable, not an array of signed events, too large). */
    val skippedFiles: List<String> = emptyList(),
    /** Authentic events found in the files read. */
    val authentic: Int = 0,
    /** Events whose id or signature is wrong: ignored. */
    val invalid: Int = 0,
    /** Authentic events newly stored. */
    val stored: Int = 0,
    /** Authentic events the server already held (a restart): nothing stored twice. */
    val alreadyStored: Int = 0,
    /** Older versions of a content the server holds in a newer version: dropped. */
    val superseded: Int = 0,
    /** Authentic events the database refused for another reason. */
    val failed: Int = 0,
)

/**
 * The example contents of the preproduction: the signed events bundled with the applications (written
 * by tools/content-signer, all marked [EXEMPLE]), stored at startup from the directory named by
 * PPF_EXAMPLES_DIR, so that the preproduction serves what the applications show.
 *
 * - Every `*.json` file of the directory itself (not of its subdirectories), in name order, holds a
 *   JSON array of signed events. A file that cannot be read or decoded is skipped; the log names the
 *   file only, never what it holds (a decoding error quotes its input, which is why the message of the
 *   exception is never logged, as in the ErrorShield of the application).
 * - Only authentic events are kept ([SignedEvent.verify]). They are stored as they are, as the
 *   development server does, without the rules of the API. Their authors are not made editors: the
 *   preproduction lists its editor keys in PPF_EDITOR_KEYS.
 * - Idempotent: an event the server already holds is a [StoreOutcome.DUPLICATE], an older version of a
 *   content updated since is a [StoreOutcome.SUPERSEDED]. Restarting the server stores nothing twice.
 * - Never in production: example data must never reach it, so the server refuses to start when
 *   PPF_EXAMPLES_DIR is set there ([fromEnvironment]).
 */
object ExampleSeed {
    const val DIRECTORY_VARIABLE: String = "PPF_EXAMPLES_DIR"
    const val ENVIRONMENT_VARIABLE: String = "PPF_ENVIRONMENT"
    const val PRODUCTION: String = "production"

    /** Larger files are skipped: the bundled contents weigh a few hundred kilobytes, and the whole platform must fit in 4 GB. */
    const val MAX_FILE_BYTES: Long = 16L * 1024 * 1024

    private val logger: Logger = LoggerFactory.getLogger(ExampleSeed::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The directory of example contents named by PPF_EXAMPLES_DIR, or null when it is not set (or blank).
     *
     * @throws IllegalStateException when PPF_EXAMPLES_DIR is set while PPF_ENVIRONMENT is "production":
     *   the server must not start with example data in production.
     */
    fun fromEnvironment(env: (String) -> String? = System::getenv): Path? {
        val directory = env(DIRECTORY_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        check(env(ENVIRONMENT_VARIABLE)?.trim()?.equals(PRODUCTION, ignoreCase = true) != true) {
            "$DIRECTORY_VARIABLE is set while $ENVIRONMENT_VARIABLE is $PRODUCTION: example contents must never reach " +
                "production. Remove $DIRECTORY_VARIABLE from the production settings."
        }
        return Path.of(directory)
    }

    /**
     * Stores the authentic events of every `*.json` file of [directory] with [repository] and logs one
     * summary line. A missing directory loads nothing (logged as a warning); nothing here throws for
     * a bad file or a bad event.
     */
    fun load(
        directory: Path,
        repository: EventRepository,
        info: (String) -> Unit = logger::info,
        warn: (String) -> Unit = logger::warn,
    ): ExampleSeedReport {
        if (!Files.isDirectory(directory)) {
            warn("Example contents: $DIRECTORY_VARIABLE ($directory) is not a readable directory, no example content loaded.")
            return ExampleSeedReport()
        }
        val files = Files.list(directory).use { entries ->
            entries.filter { Files.isRegularFile(it) && it.name.endsWith(".json", ignoreCase = true) }.sorted().toList()
        }
        var report = ExampleSeedReport()
        for (file in files) {
            val events = read(file, warn)
            if (events == null) {
                report = report.copy(skippedFiles = report.skippedFiles + file.name)
                continue
            }
            val (authentic, forged) = events.partition { it.verify() }
            report = report.copy(files = report.files + 1, authentic = report.authentic + authentic.size, invalid = report.invalid + forged.size)
            var failures = 0
            var firstFailure: String? = null
            for (event in authentic) {
                val outcome = try {
                    repository.store(event)
                } catch (e: Exception) {
                    // The message of a database error can quote the row: only the class is reported.
                    failures++
                    firstFailure = firstFailure ?: e::class.qualifiedName
                    continue
                }
                report = when (outcome) {
                    StoreOutcome.STORED -> report.copy(stored = report.stored + 1)
                    StoreOutcome.DUPLICATE -> report.copy(alreadyStored = report.alreadyStored + 1)
                    StoreOutcome.SUPERSEDED -> report.copy(superseded = report.superseded + 1)
                }
            }
            if (failures > 0) {
                warn("Example contents: $failures event(s) of ${file.name} could not be stored ($firstFailure).")
                report = report.copy(failed = report.failed + failures)
            }
        }
        info(
            "Example contents: ${report.files} file(s) read from $directory, ${report.authentic} authentic event(s): " +
                "${report.stored} stored, ${report.alreadyStored} already there, ${report.superseded} superseded by a newer version" +
                (if (report.failed > 0) ", ${report.failed} refused by the database" else "") +
                "; ${report.invalid} event(s) with a bad signature ignored, ${report.skippedFiles.size} file(s) skipped.",
        )
        return report
    }

    /** The events of [file], or null (with a warning naming the file only) when it cannot be used. */
    private fun read(file: Path, warn: (String) -> Unit): List<SignedEvent>? = try {
        if (Files.size(file) > MAX_FILE_BYTES) {
            warn("Example contents: ${file.name} skipped, larger than ${MAX_FILE_BYTES / (1024 * 1024)} MiB.")
            null
        } else {
            json.decodeFromString(ListSerializer(SignedEvent.serializer()), Files.readString(file))
        }
    } catch (e: Exception) {
        warn("Example contents: ${file.name} skipped, it is not a readable JSON array of signed events (${e::class.qualifiedName}).")
        null
    }
}
