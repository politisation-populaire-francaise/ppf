package org.politisationpopulairefrancaise.core.elus

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** The commune the user chose: its INSEE code and the package of its department. */
data class ChosenCommune(val communeCode: String, val departmentCode: String)

/** A package as kept on the device: the exact JSON, checked against [sha256] before it was kept. */
data class StoredPackage(
    val code: String,
    val sha256: String,
    val json: String,
    /** The date of the source (`AAAA-MM-JJ`), when the package gives one. */
    val sourceUpdatedOn: String?,
    /** When it was downloaded, in seconds since 1970. */
    val downloadedAt: Long,
)

/**
 * What the device keeps of the directory of elected officials: the chosen commune (one row) and
 * the downloaded packages. The commune is never sent anywhere; [forget] erases everything.
 */
interface ElusStorage {
    fun commune(): ChosenCommune?

    fun saveCommune(commune: ChosenCommune)

    fun storedPackage(code: String): StoredPackage?

    fun savePackage(stored: StoredPackage)

    /** Codes of the packages on the device. */
    fun packageCodes(): List<String>

    fun deletePackage(code: String)

    /** Erases the commune and every package: nothing of the directory stays on the device. */
    fun forget()

    companion object {
        /** The code under which the index of the server is kept. */
        const val INDEX_CODE: String = "index"
    }
}

/** The directory of elected officials in the local database (Elus.sq). */
class ElusStore(private val database: PpfDatabase) : ElusStorage {

    private val queries get() = database.elusQueries

    override fun commune(): ChosenCommune? =
        queries.selectCommune().executeAsOneOrNull()?.let { ChosenCommune(it.commune_code, it.department_code) }

    override fun saveCommune(commune: ChosenCommune) {
        queries.saveCommune(commune.communeCode, commune.departmentCode)
    }

    override fun storedPackage(code: String): StoredPackage? =
        queries.selectPackage(code).executeAsOneOrNull()?.let { StoredPackage(it.code, it.sha256, it.json, it.source_updated_on, it.downloaded_at) }

    override fun savePackage(stored: StoredPackage) {
        queries.savePackage(stored.code, stored.sha256, stored.json, stored.sourceUpdatedOn, stored.downloadedAt)
    }

    override fun packageCodes(): List<String> = queries.selectPackageCodes().executeAsList()

    override fun deletePackage(code: String) {
        queries.deletePackage(code)
    }

    override fun forget() = database.transaction {
        queries.deleteCommune()
        queries.deleteAllPackages()
    }

    fun countPackages(): Long = queries.countPackages().executeAsOne()
}

/** The same in memory, for the life of the process (tests, previews, a device without database). */
class InMemoryElusStorage : ElusStorage {
    private var chosen: ChosenCommune? = null
    private val packages = LinkedHashMap<String, StoredPackage>()

    override fun commune(): ChosenCommune? = chosen

    override fun saveCommune(commune: ChosenCommune) {
        chosen = commune
    }

    override fun storedPackage(code: String): StoredPackage? = packages[code]

    override fun savePackage(stored: StoredPackage) {
        packages[stored.code] = stored
    }

    override fun packageCodes(): List<String> = packages.keys.sorted()

    override fun deletePackage(code: String) {
        packages.remove(code)
    }

    override fun forget() {
        chosen = null
        packages.clear()
    }
}
