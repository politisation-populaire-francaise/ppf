package org.politisationpopulairefrancaise.server.elus

import org.slf4j.Logger
import java.time.Clock
import java.time.ZoneOffset

/** How an import ended. Only counts and codes: never a value read from a file. */
sealed interface ImportOutcome {
    data class Imported(val importId: Long, val counts: Map<Mandate, Int>, val packages: Int, val largestPackageBytes: Int) : ImportOutcome

    /** The numbers fell too much compared with the import served (a truncated file): nothing changed. */
    data class Refused(val reason: String) : ImportOutcome

    /** A file could not be fetched or read: nothing changed. */
    data class Failed(val reason: String) : ImportOutcome

    /** Another import is running. */
    data object Busy : ImportOutcome
}

/**
 * Imports the directory from an [ElusSource] (contract, section 1.5): geography first, then the
 * officials, file by file, streamed into the staging table; then one package per department,
 * built and stored one at a time. The import served until now stays served unless the new one
 * succeeds; an import whose officials drop by more than [maxDrop] compared with the previous one of
 * the same source, for any mandate or in total, is refused.
 */
class ElusImporter(
    private val store: ElusStore,
    private val clock: Clock = Clock.systemUTC(),
    private val log: Logger? = null,
    private val maxDrop: Double = MAX_DROP,
) {
    fun run(source: ElusSource): ImportOutcome {
        val lock = store.tryLock() ?: return ImportOutcome.Busy
        lock.use {
            source.use {
                val importId = store.begin(lock, source.kind, clock.instant())
                var counts: Map<Mandate, Int>? = null
                try {
                    val updatedOn = source.prepare()
                    val geography = Geography.read(source::open)
                    val epcis = EpciCollector()
                    val staged = HashMap<Mandate, Int>()
                    for (file in ElusFile.RNE) {
                        val result = store.stage(lock, importId) { stage ->
                            Rne.read(file, source.open(file), epcis) { official ->
                                if (source.example && !official.name.startsWith(EXAMPLE_PREFIX)) {
                                    throw ElusFormatException("${file.fileName}: a person of the example set is not marked $EXAMPLE_PREFIX.")
                                }
                                stage(official)
                            }
                        }
                        staged[Rne.mandateOf(file)] = result.staged
                        log?.info("Directory of elected officials: ${file.fileName}: ${result.staged} official(s), ${result.skipped} line(s) skipped, ${result.abroad} for the French abroad.")
                    }
                    counts = staged
                    refusal(lock, source.kind, staged)?.let { reason ->
                        store.fail(lock, importId, reason, staged, clock.instant())
                        store.clearStaging(lock, importId)
                        return ImportOutcome.Refused(reason)
                    }
                    val builder = ElusBuilder(
                        geography = geography,
                        communeEpcis = epcis.communeEpcis(),
                        epciNames = epcis.names,
                        example = source.example,
                        sourceUpdatedOn = updatedOn.toString(),
                    )
                    val built = ArrayList<ElusPackageEntry>()
                    var largest = 0
                    for (code in builder.packageCodes(store.stagedAreas(lock, importId))) {
                        val communes = builder.communesOf(code)
                        val officials = store.staged(lock, importId, builder.areasOf(code), builder.sirensOf(communes), builder.regionOf(code))
                        val pack = builder.build(code, communes, officials)
                        store.savePackage(lock, importId, pack.stored())
                        largest = maxOf(largest, pack.body.size)
                        // Only the description stays in memory: the body is in the database.
                        built += pack.entry()
                    }
                    store.clearStaging(lock, importId)
                    val now = clock.instant()
                    val index = buildIndex(source.example, updatedOn.toString(), now.atOffset(ZoneOffset.UTC).toLocalDate().toString(), built)
                    store.activate(lock, importId, index, sha256Hex(index), staged, updatedOn, now)
                    log?.info("Directory of elected officials: import $importId (${source.kind}) served: ${built.size} package(s), ${staged.values.sum()} official(s), RNE of $updatedOn.")
                    return ImportOutcome.Imported(importId, staged, built.size, largest)
                } catch (e: Exception) {
                    val reason = reasonOf(e)
                    try {
                        store.clearStaging(lock, importId)
                        store.fail(lock, importId, reason, counts, clock.instant())
                    } catch (ignored: Exception) {
                        // The served import is untouched either way.
                    }
                    log?.warn("Directory of elected officials: import failed ($reason); the previous import stays served.")
                    return ImportOutcome.Failed(reason)
                }
            }
        }
    }

    /** Why [counts] cannot replace the import served for [source], or null when they can. */
    private fun refusal(lock: ElusStore.ImportLock, source: String, counts: Map<Mandate, Int>): String? {
        if (counts.values.sum() == 0) return "empty"
        val previous = store.servedCounts(lock, source) ?: return null
        for (mandate in Mandate.entries) {
            val before = previous[mandate] ?: continue
            val after = counts[mandate] ?: 0
            if (before > 0 && after < before * (1 - maxDrop)) return "drop:${mandate.code}:$before:$after"
        }
        val before = previous.values.sum()
        val after = counts.values.sum()
        if (before > 0 && after < before * (1 - maxDrop)) return "drop:total:$before:$after"
        return null
    }

    companion object {
        /** An import may lose at most 20 % of the officials of the previous one (contract, section 1.5). */
        const val MAX_DROP: Double = 0.20

        /** How every person of the example set is named. */
        const val EXAMPLE_PREFIX: String = "[EXEMPLE] "

        /**
         * A short reason for the logs and the table: our own messages (which name files and
         * columns, never values), otherwise only the class of the exception.
         */
        fun reasonOf(e: Exception): String = when (e) {
            is ElusFormatException -> "format: ${e.message}"
            is ElusDownloadException -> "download: ${e.message}"
            else -> e::class.simpleName ?: "error"
        }
    }
}
