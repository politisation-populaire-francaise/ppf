package org.politisationpopulairefrancaise.core.elus

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** The elected officials of the chosen commune, with what the page says of the source. */
data class MyElus(
    val chosen: ChosenCommune,
    val officials: CommuneOfficials,
    /** The source named by the index (name, licence, date), when the index is on the device. */
    val source: ElusSource?,
    /** True when the server serves the fictitious data set ([EXEMPLE]). */
    val example: Boolean,
    /** When the package of the department was downloaded, in seconds since 1970. */
    val downloadedAt: Long,
)

/** Where the index comes from when the user starts choosing a commune. */
sealed interface IndexOutcome {
    /** [fresh] is false when the server did not answer and the copy of the device is used. */
    data class Ready(val index: ElusIndex, val fresh: Boolean) : IndexOutcome

    /** No server is configured (the APK without server): only the official directories can help. */
    data object NoServer : IndexOutcome

    /** The server did not answer and the device holds no index. */
    data object Unreachable : IndexOutcome

    /** The server answered something this application cannot read (a newer format, for example). */
    data object Unreadable : IndexOutcome
}

/**
 * What finding the officials of a postal code needs: the packages of its departments (and the
 * national one, optional), [missing] those that are not on the device in their current version.
 */
data class DownloadPlan(
    /** The postal code typed, null for the update of the packages of the chosen commune. */
    val postalCode: String?,
    val departments: List<ElusPackageEntry>,
    val missing: List<ElusPackageEntry>,
) {
    /** The size to announce before downloading, in bytes. */
    val bytes: Long get() = missing.sumOf { it.bytes }
}

sealed interface DownloadOutcome {
    data object Done : DownloadOutcome

    /** A package did not match the fingerprint of the index, or could not be read: it was not kept. */
    data class Refused(val code: String) : DownloadOutcome

    /** The server did not answer (or no longer has the package). */
    data class Unavailable(val code: String) : DownloadOutcome
}

/** One commune offered for a postal code, with its department. */
data class CommuneChoice(val commune: ElusCommune, val department: Territory)

/**
 * The directory of elected officials on the device (D-047; contract of the third wave of
 * "Comprendre", 1.6). The user types a postal code; the device finds its department in the index
 * (which holds nothing personal), downloads the package of that department after showing its size,
 * checks its sha256 against the index and offers the communes of the postal code. The commune stays
 * on the device: the server only learns that a device downloaded the list of a department. Nothing
 * is downloaded without a gesture of the user: only [index] and [download] use the network.
 *
 * Not thread-safe: callers run one operation at a time.
 */
class ElusDirectory(
    private val remote: RemoteEvents?,
    private val storage: ElusStorage,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** False for an application without server (option C: links to the official directories). */
    val hasServer: Boolean get() = remote != null

    /** Packages read from the device, by code, with the sha256 they were read for. */
    private val parsed = HashMap<String, Pair<String, ElusPackage>>()

    /** The officials of the chosen commune, from the device only; null when no commune is chosen (or its package is gone). */
    fun mine(): MyElus? {
        val chosen = storage.commune() ?: return null
        val stored = storage.storedPackage(chosen.departmentCode) ?: return null
        val pkg = packageOnDevice(chosen.departmentCode) ?: return null
        val officials = ElusFinder.find(pkg, chosen.communeCode, packageOnDevice(ElusReader.NATIONAL_CODE)) ?: return null
        val index = storedIndex()
        return MyElus(chosen, officials, index?.source, officials.example || (index?.example ?: false), stored.downloadedAt)
    }

    /** The commune chosen, if any, without reading its package. */
    fun chosen(): ChosenCommune? = storage.commune()

    /** The index kept on the device, if any (no network). */
    fun storedIndex(): ElusIndex? = storage.storedPackage(ElusStorage.INDEX_CODE)?.let { ElusReader.readIndex(it.json) }

    /**
     * The index, asked to the server (a gesture of the user): `If-None-Match` with the version on the
     * device, which is kept and used when the server does not answer.
     */
    suspend fun index(): IndexOutcome {
        val server = remote ?: return IndexOutcome.NoServer
        val stored = storage.storedPackage(ElusStorage.INDEX_CODE)
        val storedIndex = stored?.let { ElusReader.readIndex(it.json) }
        val answer = try {
            server.fetchElus(code = null, etag = stored?.sha256?.takeIf { storedIndex != null })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ElusDownload.Unavailable(e)
        }
        return when (answer) {
            is ElusDownload.Body -> {
                val text = utf8(answer.bytes) ?: return IndexOutcome.Unreadable
                val index = ElusReader.readIndex(text) ?: return IndexOutcome.Unreadable
                storage.savePackage(StoredPackage(ElusStorage.INDEX_CODE, ElusDigest.sha256Hex(answer.bytes), text, index.source?.updatedOn?.toIso(), clock()))
                IndexOutcome.Ready(index, fresh = true)
            }
            ElusDownload.NotModified -> storedIndex?.let { IndexOutcome.Ready(it, fresh = true) } ?: IndexOutcome.Unreachable
            ElusDownload.UnknownPackage, is ElusDownload.Unavailable -> storedIndex?.let { IndexOutcome.Ready(it, fresh = false) } ?: IndexOutcome.Unreachable
        }
    }

    /** What [postalCode] needs; null when no department of the index covers it. */
    fun plan(index: ElusIndex, postalCode: String): DownloadPlan? {
        val code = ElusReader.normalizePostalCode(postalCode) ?: return null
        val departments = index.departmentsFor(code).ifEmpty { return null }
        return DownloadPlan(code, departments, missing(departments + listOfNotNull(index.national)))
    }

    /** The newer versions of the packages of the chosen commune announced by [index]; null when they are up to date. */
    fun update(index: ElusIndex): DownloadPlan? {
        val chosen = storage.commune() ?: return null
        val department = index.entry(chosen.departmentCode) ?: return null
        val missing = missing(listOf(department) + listOfNotNull(index.national))
        return if (missing.isEmpty()) null else DownloadPlan(null, listOf(department), missing)
    }

    /**
     * Downloads what [plan] misses (a gesture of the user, after the size was shown). Each package
     * is kept only when its bytes match the sha256 of the index and it reads as the package it
     * claims to be. The national package is optional: its failure does not stop the others.
     */
    suspend fun download(plan: DownloadPlan): DownloadOutcome {
        val server = remote ?: return DownloadOutcome.Unavailable(plan.missing.firstOrNull()?.code ?: "")
        for (entry in plan.missing) {
            val outcome = fetchPackage(server, entry)
            if (outcome != DownloadOutcome.Done && !entry.isNational) return outcome
        }
        return DownloadOutcome.Done
    }

    /** The communes of the postal code of [plan] found in the packages on the device, by name. */
    fun communesFor(plan: DownloadPlan): List<CommuneChoice> {
        val postalCode = plan.postalCode ?: return emptyList()
        return plan.departments.flatMap { entry ->
            val pkg = packageOnDevice(entry.code) ?: return@flatMap emptyList()
            pkg.communesFor(postalCode).map { CommuneChoice(it, Territory(pkg.code, pkg.name)) }
        }.sortedWith(compareBy({ it.commune.name.lowercase() }, { it.department.code }))
    }

    /** Keeps [choice] on the device (one row) and drops the packages of other departments. */
    fun choose(choice: CommuneChoice) {
        storage.saveCommune(ChosenCommune(choice.commune.code, choice.department.code))
        val kept = setOf(choice.department.code, ElusReader.NATIONAL_CODE, ElusStorage.INDEX_CODE)
        storage.packageCodes().filter { it !in kept }.forEach {
            storage.deletePackage(it)
            parsed.remove(it)
        }
    }

    /** « Oublier ma commune »: the commune and every package leave the device. */
    fun forget() {
        storage.forget()
        parsed.clear()
    }

    private fun missing(entries: List<ElusPackageEntry>): List<ElusPackageEntry> =
        entries.filter { storage.storedPackage(it.code)?.sha256 != it.sha256 }

    private suspend fun fetchPackage(server: RemoteEvents, entry: ElusPackageEntry): DownloadOutcome {
        val answer = try {
            server.fetchElus(code = entry.code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ElusDownload.Unavailable(e)
        }
        val bytes = (answer as? ElusDownload.Body)?.bytes ?: return DownloadOutcome.Unavailable(entry.code)
        if (bytes.size > ElusReader.MAX_PACKAGE_BYTES || !ElusDigest.matches(bytes, entry.sha256)) return DownloadOutcome.Refused(entry.code)
        val text = utf8(bytes) ?: return DownloadOutcome.Refused(entry.code)
        val pkg = ElusReader.readPackage(text)?.takeIf { it.code == entry.code } ?: return DownloadOutcome.Refused(entry.code)
        storage.savePackage(StoredPackage(entry.code, entry.sha256, text, pkg.sourceUpdatedOn?.toIso(), clock()))
        parsed[entry.code] = entry.sha256 to pkg
        return DownloadOutcome.Done
    }

    /** A package kept on the device, read once per version; null when it is absent or no longer matches its fingerprint. */
    private fun packageOnDevice(code: String): ElusPackage? {
        val stored = storage.storedPackage(code) ?: return null
        parsed[code]?.let { (sha, pkg) -> if (sha == stored.sha256) return pkg }
        if (!ElusDigest.matches(stored.json.encodeToByteArray(), stored.sha256)) return null
        val pkg = ElusReader.readPackage(stored.json)?.takeIf { it.code == code } ?: return null
        parsed[code] = stored.sha256 to pkg
        return pkg
    }

    private fun utf8(bytes: ByteArray): String? = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
}
