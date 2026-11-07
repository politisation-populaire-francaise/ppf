package org.politisationpopulairefrancaise.server.territoires

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.server.elus.ElusDownloadException
import org.politisationpopulairefrancaise.server.elus.ElusFormatException
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import javax.sql.DataSource
import kotlin.math.abs

/**
 * Settings of the referential of territories, read from the environment only:
 *
 * - PPF_TERRITOIRES_SOURCE: `example` (default: real public figures for a few departments,
 *   embedded in the server, which contacts nobody) or `official` (the Insee, docs/integration/territoires.md).
 *   `official` is honoured in production, and in preproduction only with
 *   PPF_TERRITOIRES_REAL_IN_PREPRODUCTION=true (the owner's decision); elsewhere the example set is served.
 * - PPF_TERRITOIRES_REFRESH_DAYS: days between two imports (default 30). 0 disables every automatic import (tests).
 */
data class TerritoiresConfig(
    val source: String = TerritoiresSources.EXAMPLE,
    val refreshDays: Int = DEFAULT_REFRESH_DAYS,
    /** True when `official` was asked where it is not allowed and replaced by `example`. */
    val officialRefused: Boolean = false,
) {
    init {
        require(source == TerritoiresSources.EXAMPLE || source == TerritoiresSources.OFFICIAL) { "Unknown source of the referential" }
        require(refreshDays >= 0) { "The refresh period cannot be negative" }
    }

    companion object {
        const val SOURCE_VARIABLE: String = "PPF_TERRITOIRES_SOURCE"
        const val REFRESH_VARIABLE: String = "PPF_TERRITOIRES_REFRESH_DAYS"
        const val PREPRODUCTION_VARIABLE: String = "PPF_TERRITOIRES_REAL_IN_PREPRODUCTION"
        const val DEFAULT_REFRESH_DAYS: Int = 30

        fun fromEnvironment(env: (String) -> String? = System::getenv): TerritoiresConfig {
            val asked = env(SOURCE_VARIABLE)?.trim()?.lowercase() == TerritoiresSources.OFFICIAL
            val environment = env("PPF_ENVIRONMENT")?.trim()
            val allowed = environment == "production" ||
                (environment == "preproduction" && env(PREPRODUCTION_VARIABLE)?.trim()?.lowercase() == "true")
            return TerritoiresConfig(
                source = if (asked && allowed) TerritoiresSources.OFFICIAL else TerritoiresSources.EXAMPLE,
                refreshDays = env(REFRESH_VARIABLE)?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: DEFAULT_REFRESH_DAYS,
                officialRefused = asked && !allowed,
            )
        }
    }
}

class TerritoiresSetup(
    val config: TerritoiresConfig = TerritoiresConfig(),
    val clock: Clock = Clock.systemUTC(),
    private val sources: (TerritoiresConfig) -> TerritoiresSource = {
        if (it.source == TerritoiresSources.OFFICIAL) OfficialTerritoiresSource() else ExampleTerritoiresSource()
    },
) {
    fun newSource(): TerritoiresSource = sources(config)

    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): TerritoiresSetup = TerritoiresSetup(TerritoiresConfig.fromEnvironment(env))
    }
}

sealed interface TerritoiresImportOutcome {
    data class Imported(val packages: Int) : TerritoiresImportOutcome

    /** The import was refused and nothing changed; [reason] is a short code. */
    data class Refused(val reason: String) : TerritoiresImportOutcome

    /** Another import holds the lock. */
    data object Busy : TerritoiresImportOutcome
}

/**
 * The referential on the server: serves the last successful import and runs the next ones on
 * schedule. Logs only counts and codes.
 */
class TerritoiresService(
    private val store: TerritoiresStore,
    private val setup: TerritoiresSetup,
    private val log: Logger? = null,
) {
    @Volatile
    private var current: ServedTerritoires? = null

    fun current(): ServedTerritoires? = current

    fun reload(): ServedTerritoires? {
        current = store.served()
        return current
    }

    /** Runs an import now, from a new source of the configured kind, and serves it if it succeeds. */
    fun importNow(source: TerritoiresSource = setup.newSource()): TerritoiresImportOutcome {
        val started = setup.clock.instant()
        val outcome = try {
            source.use { s ->
                val dates = s.prepare()
                val built = TerritoiresBuilder(s.example, dates) { s.open(it) }.build()
                guard(store.servedCounts().takeIf { current?.source == s.kind }, built.counts)?.let { return@use TerritoiresImportOutcome.Refused(it) }
                if (!store.save(s.kind, started, setup.clock.instant(), built)) return@use TerritoiresImportOutcome.Busy
                TerritoiresImportOutcome.Imported(built.packages.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ElusFormatException) {
            TerritoiresImportOutcome.Refused("format")
        } catch (e: ElusDownloadException) {
            TerritoiresImportOutcome.Refused("download")
        } catch (e: java.io.IOException) {
            TerritoiresImportOutcome.Refused("io")
        }
        when (outcome) {
            is TerritoiresImportOutcome.Imported -> {
                reload()
                log?.info("Territories: import of ${source.kind} served (${outcome.packages} documents).")
            }
            is TerritoiresImportOutcome.Refused -> store.fail(source.kind, started, setup.clock.instant(), outcome.reason)
            TerritoiresImportOutcome.Busy -> Unit
        }
        return outcome
    }

    /** The compressed body of package [code] of the import served, or null. */
    fun packageGzip(code: String): ByteArray? {
        val served = current ?: return null
        if (code !in served.packages) return null
        return store.packageGzip(served.id, code)
    }

    fun packageBody(code: String): ByteArray? = packageGzip(code)?.let { GZIPInputStream(it.inputStream()).use { input -> input.readAllBytes() } }

    /** How long to wait before the next import (same rules as the directory of elected officials). */
    fun untilNextImport(now: Instant): Duration {
        val period = Duration.ofDays(setup.config.refreshDays.toLong())
        val retry = minOf(RETRY, period)
        val served = current?.takeIf { it.source == setup.config.source }
        val attempt = store.lastAttempt()
        val due = when {
            attempt != null && attempt.status != "ready" && (served == null || attempt.at >= served.finishedAt) -> attempt.at.plus(retry)
            served != null -> served.finishedAt.plus(period)
            else -> now
        }
        return Duration.between(now, due).takeIf { !it.isNegative } ?: Duration.ZERO
    }

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            val wait = try {
                untilNextImport(setup.clock.instant())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.warn("Territories: schedule unavailable (${e::class.simpleName}).")
                RETRY
            }
            if (!wait.isZero) {
                delay(minOf(wait, CHECK_AT_LEAST_EVERY).toMillis())
                continue
            }
            try {
                when (val outcome = importNow()) {
                    is TerritoiresImportOutcome.Refused -> log?.warn("Territories: import refused (${outcome.reason}); the previous import stays served.")
                    TerritoiresImportOutcome.Busy -> delay(RETRY.toMillis())
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.warn("Territories: import failed (${e::class.simpleName}).")
                delay(RETRY.toMillis())
            }
        }
    }

    companion object {
        val RETRY: Duration = Duration.ofHours(6)
        val CHECK_AT_LEAST_EVERY: Duration = Duration.ofHours(1)

        /** The guard against a truncated source: refused when the communes move by more than 2 % or the population of France by more than 3 %. */
        fun guard(previous: Map<String, Long>?, next: Map<String, Long>): String? {
            if (previous.isNullOrEmpty()) return null
            fun moved(key: String, limit: Double): Boolean {
                val before = previous[key] ?: return false
                val after = next[key] ?: return true
                if (before == 0L) return false
                return abs(after - before).toDouble() / before > limit
            }
            return when {
                moved("communes", 0.02) -> "drop:communes:${previous["communes"]}:${next["communes"]}"
                moved("population", 0.03) -> "drop:population"
                else -> null
            }
        }

        fun start(scope: CoroutineScope, dataSource: DataSource, setup: TerritoiresSetup, log: Logger?): TerritoiresService {
            val service = TerritoiresService(TerritoiresStore(dataSource), setup, log)
            if (setup.config.officialRefused) {
                log?.warn("Territories: ${TerritoiresConfig.SOURCE_VARIABLE}=official is honoured only in production (or preproduction with ${TerritoiresConfig.PREPRODUCTION_VARIABLE}=true); the example set is served.")
            }
            service.reload()
            if (setup.config.refreshDays > 0) service.start(scope)
            return service
        }
    }
}
