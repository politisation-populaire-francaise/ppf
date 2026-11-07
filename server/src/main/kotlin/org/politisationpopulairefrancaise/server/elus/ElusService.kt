package org.politisationpopulairefrancaise.server.elus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import javax.sql.DataSource

/**
 * Settings of the directory of elected officials, read from the environment only:
 *
 * - PPF_RNE_SOURCE: `example` (default: the fictitious set embedded in the server, which contacts
 *   nobody) or `official` (the real RNE from data.gouv.fr). `official` is honoured when
 *   PPF_ENVIRONMENT is `production` or `preproduction` (owner's decision of 7 November 2026: real
 *   information in preproduction too); development and tests keep the fictitious set.
 * - PPF_RNE_REFRESH_DAYS: days between two imports (default 7). An import also runs at start when
 *   nothing is served yet or when the source changed. 0 disables every automatic import (tests).
 */
data class ElusConfig(
    val source: String = ElusSources.EXAMPLE,
    val refreshDays: Int = DEFAULT_REFRESH_DAYS,
    /** True when `official` was asked outside production and preproduction and replaced by `example`. */
    val officialRefused: Boolean = false,
) {
    init {
        require(source == ElusSources.EXAMPLE || source == ElusSources.OFFICIAL) { "Unknown source of the directory" }
        require(refreshDays >= 0) { "The refresh period cannot be negative" }
    }

    companion object {
        const val SOURCE_VARIABLE: String = "PPF_RNE_SOURCE"
        const val REFRESH_VARIABLE: String = "PPF_RNE_REFRESH_DAYS"
        const val DEFAULT_REFRESH_DAYS: Int = 7

        /** Where the real directory may be served (owner's decision of 7 November 2026). */
        val REAL_DATA_ENVIRONMENTS: Set<String> = setOf("production", "preproduction")

        fun fromEnvironment(env: (String) -> String? = System::getenv): ElusConfig {
            val asked = env(SOURCE_VARIABLE)?.trim()?.lowercase()
            val production = env("PPF_ENVIRONMENT")?.trim() in REAL_DATA_ENVIRONMENTS
            val official = asked == ElusSources.OFFICIAL && production
            return ElusConfig(
                source = if (official) ElusSources.OFFICIAL else ElusSources.EXAMPLE,
                refreshDays = env(REFRESH_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: DEFAULT_REFRESH_DAYS,
                officialRefused = asked == ElusSources.OFFICIAL && !production,
            )
        }
    }
}

/** What the directory needs from outside: its settings, where its files come from, the clock. */
class ElusSetup(
    val config: ElusConfig = ElusConfig(),
    val clock: Clock = Clock.systemUTC(),
    private val sources: (ElusConfig) -> ElusSource = { if (it.source == ElusSources.OFFICIAL) OfficialSource() else ExampleSource() },
) {
    fun newSource(): ElusSource = sources(config)

    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): ElusSetup = ElusSetup(ElusConfig.fromEnvironment(env))
    }
}

/** The import served, as the routes answer it: index ready in both encodings, package ETags. */
class ServedDirectory(val served: ServedImport) {
    val indexGzip: ByteArray = gzip(served.index)
}

/**
 * The directory on the server: serves the last successful import and runs the next ones on
 * schedule (contract, section 1.5). Logs only counts, codes and dates.
 */
class ElusService(
    private val store: ElusStore,
    private val setup: ElusSetup,
    private val log: Logger? = null,
) {
    @Volatile
    private var current: ServedDirectory? = null

    /** The import served, or null before the first one. */
    fun current(): ServedDirectory? = current

    /** Reads the import served from the database. */
    fun reload(): ServedDirectory? {
        current = store.served()?.let(::ServedDirectory)
        return current
    }

    /** Runs an import now, from a new source of the configured kind, and serves it if it succeeds. */
    fun importNow(source: ElusSource = setup.newSource()): ImportOutcome {
        val outcome = ElusImporter(store, setup.clock, log).run(source)
        if (outcome is ImportOutcome.Imported) reload()
        return outcome
    }

    /** The exact JSON body of package [code] of the import served, uncompressed, or null. */
    fun packageBody(code: String): ByteArray? = packageGzip(code)?.let { GZIPInputStream(it.inputStream()).use { input -> input.readAllBytes() } }

    /** The compressed body of package [code] of the import served, or null. */
    fun packageGzip(code: String): ByteArray? {
        val served = current?.served ?: return null
        if (code !in served.packages) return null
        return store.packageGzip(served.id, code)
    }

    /**
     * How long to wait before the next import: none when nothing is served for the configured
     * source; otherwise until the refresh period has passed since it was served; after a failed or
     * interrupted attempt, [RETRY] (at most the period) after that attempt.
     */
    fun untilNextImport(now: Instant): Duration {
        val period = Duration.ofDays(setup.config.refreshDays.toLong())
        val retry = minOf(RETRY, period)
        val served = current?.served?.takeIf { it.source == setup.config.source }
        val attempt = store.lastAttempt()
        val due = when {
            attempt != null && attempt.status != "ready" && (served == null || attempt.at >= served.finishedAt) -> attempt.at.plus(retry)
            served != null -> served.finishedAt.plus(period)
            else -> now
        }
        return Duration.between(now, due).takeIf { !it.isNegative } ?: Duration.ZERO
    }

    /** The schedule: imports when due, until [scope] is cancelled (the application's scope). */
    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            val wait = try {
                untilNextImport(setup.clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.warn("Directory of elected officials: schedule unavailable (${e::class.simpleName}).")
                RETRY
            }
            if (!wait.isZero) {
                delay(minOf(wait, CHECK_AT_LEAST_EVERY).toMillis())
                continue
            }
            try {
                when (val outcome = importNow()) {
                    is ImportOutcome.Refused -> log?.warn("Directory of elected officials: import refused (${outcome.reason}); the previous import stays served.")
                    ImportOutcome.Busy -> delay(RETRY.toMillis())
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.warn("Directory of elected officials: import failed (${e::class.simpleName}).")
                delay(RETRY.toMillis())
            }
        }
    }

    companion object {
        /** Delay before trying again after a failed import. */
        val RETRY: Duration = Duration.ofHours(6)

        /** The schedule is checked at least this often (a clock change, a new import by another process). */
        val CHECK_AT_LEAST_EVERY: Duration = Duration.ofHours(1)

        /**
         * The directory on [dataSource]: the import served is read at once; with a positive refresh
         * period, the schedule runs in [scope] (the application).
         */
        fun start(scope: CoroutineScope, dataSource: DataSource, setup: ElusSetup, log: Logger?): ElusService {
            val service = ElusService(ElusStore(dataSource), setup, log)
            if (setup.config.officialRefused) {
                log?.warn("Directory of elected officials: ${ElusConfig.SOURCE_VARIABLE}=official is only honoured in production and preproduction; the example set is served.")
            }
            service.reload()
            if (setup.config.refreshDays > 0) service.start(scope)
            return service
        }
    }
}
