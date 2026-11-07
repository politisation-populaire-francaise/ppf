package org.politisationpopulairefrancaise.server.elus

import org.junit.Assume.assumeTrue
import org.politisationpopulairefrancaise.server.TestDatabase
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Manual check of the real files (docs/integration/elus-serveur.md, section 5), skipped unless
 * ELUS_OFFICIAL_DIR names a directory holding the real files under the names of [ElusFile]
 * (outside the repository: they hold personal data). Writes only numbers and codes, never a name,
 * to ELUS_OFFICIAL_REPORT (or to that directory, file `report.txt`):
 *
 *     ELUS_OFFICIAL_DIR=<dir> gradlew :server:test --tests "*ElusOfficialCheck*"
 */
class ElusOfficialCheck {
    @Test
    fun theRealFilesGiveSensiblePackages() {
        val dir = System.getenv("ELUS_OFFICIAL_DIR")?.let(Path::of)
        assumeTrue("ELUS_OFFICIAL_DIR is not set", dir != null && Files.isDirectory(dir))
        dir!!
        TestDatabase.reset()
        val report = StringBuilder()
        val heap = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
        heap.forEach { it.resetPeakUsage() }
        val start = System.nanoTime()
        val service = ElusService(ElusStore(TestDatabase.dataSource), ElusSetup(ElusConfig(refreshDays = 0)))
        val outcome = service.importNow(DirectorySource(dir, LocalDate.parse(System.getenv("ELUS_OFFICIAL_DATE") ?: "2026-08-11"), example = false))
        val seconds = (System.nanoTime() - start) / 1e9
        report.appendLine("outcome: ${outcome::class.simpleName} ${(outcome as? ImportOutcome.Failed)?.reason ?: (outcome as? ImportOutcome.Refused)?.reason ?: ""}")
        val imported = assertIs<ImportOutcome.Imported>(outcome)
        report.appendLine("seconds: %.1f".format(seconds))
        report.appendLine("peak heap (sum of pools): ${heap.sumOf { it.peakUsage.used } / (1024 * 1024)} MB; max heap: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
        imported.counts.forEach { (mandate, count) -> report.appendLine("count ${mandate.code}: $count") }
        report.appendLine("packages: ${imported.packages}, largest: ${imported.largestPackageBytes} bytes")
        val served = service.current()!!.served
        val index = ElusJson.decodeFromString(ElusIndex.serializer(), served.index.toString(Charsets.UTF_8))
        report.appendLine("index: ${served.index.size} bytes, postal codes: ${index.packages.sumOf { it.postalCodes.size }}")
        var total = 0L
        var gzipTotal = 0L
        var communes = 0
        var withoutPostal = 0
        var withoutCircos = 0
        var withoutCantons = 0
        var withEpci = 0
        for (entry in index.packages) {
            val gzip = service.packageGzip(entry.code)!!
            total += entry.bytes
            gzipTotal += gzip.size
            val pack = ElusJson.decodeFromString(ElusPackage.serializer(), service.packageBody(entry.code)!!.toString(Charsets.UTF_8))
            communes += pack.communes.size
            withoutPostal += pack.communes.count { it.postalCodes.isEmpty() }
            withoutCircos += pack.communes.count { it.circonscriptions.isEmpty() }
            withoutCantons += pack.communes.count { it.cantons.isEmpty() }
            withEpci += pack.communes.count { it.epci != null }
            val byMandate = pack.officials.groupingBy { it.mandate }.eachCount()
            if (entry.code in setOf("59", "75", "69", "13", "2A", "67", "971", "976", "977", "987", "FR")) {
                report.appendLine(
                    "package ${entry.code}: ${entry.bytes} bytes (gzip ${gzip.size}), ${pack.communes.size} communes, ${pack.epcis.size} epcis, " +
                        "${pack.cantons.size} cantons, ${pack.circonscriptions.size} circonscriptions, officials $byMandate",
                )
            }
            assertTrue(pack.officials.none { it.name.startsWith("[EXEMPLE]") })
        }
        report.appendLine("all packages: $total bytes, gzip $gzipTotal bytes; communes $communes, without postal code $withoutPostal, without constituency $withoutCircos, without canton $withoutCantons, with intercommunality $withEpci")
        report.appendLine("codes: ${index.packages.joinToString(" ") { it.code }}")
        val target = System.getenv("ELUS_OFFICIAL_REPORT")?.let(Path::of) ?: dir.resolve("report.txt")
        Files.writeString(target, report.toString())
        TestDatabase.reset()
    }
}
