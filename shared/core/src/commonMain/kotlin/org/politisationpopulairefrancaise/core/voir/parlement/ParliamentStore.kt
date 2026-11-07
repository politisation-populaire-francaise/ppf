package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** The index kept on the device: the exact JSON of the signed event, checked again when read. */
data class StoredIndex(
    val eventJson: String,
    /** The day of the import, AAAA-MM-JJ. */
    val importedOn: String,
    /** When it was downloaded, in seconds since 1970. */
    val downloadedAt: Long,
)

/** A package as kept on the device: the exact JSON, checked against [sha256] before it was kept. */
data class StoredParliamentPackage(
    val name: String,
    val sha256: String,
    val json: String,
    /** The date of the source (AAAA-MM-JJ), when known. */
    val sourceUpdatedOn: String?,
    /** When it was downloaded, in seconds since 1970. */
    val downloadedAt: Long,
)

/**
 * What the device keeps of the parliamentary open data: the trusted index (one row) and the
 * downloaded packages. Public data of the assemblies only; nothing about the user.
 */
interface ParliamentStorage {
    fun index(): StoredIndex?

    fun saveIndex(index: StoredIndex)

    fun storedPackage(name: String): StoredParliamentPackage?

    fun savePackage(stored: StoredParliamentPackage)

    /** Names of the packages on the device, sorted. */
    fun packageNames(): List<String>

    fun deletePackage(name: String)

    /** Erases the index and every package. */
    fun forget()
}

/** The parliamentary open data in the local database (Parliament.sq). */
class ParliamentStore(private val database: PpfDatabase) : ParliamentStorage {

    private val queries get() = database.parliamentQueries

    override fun index(): StoredIndex? =
        queries.selectIndex().executeAsOneOrNull()?.let { StoredIndex(it.event_json, it.imported_on, it.downloaded_at) }

    override fun saveIndex(index: StoredIndex) {
        queries.saveIndex(index.eventJson, index.importedOn, index.downloadedAt)
    }

    override fun storedPackage(name: String): StoredParliamentPackage? =
        queries.selectPackage(name).executeAsOneOrNull()?.let { StoredParliamentPackage(it.name, it.sha256, it.json, it.source_updated_on, it.downloaded_at) }

    override fun savePackage(stored: StoredParliamentPackage) {
        queries.savePackage(stored.name, stored.sha256, stored.json, stored.sourceUpdatedOn, stored.downloadedAt)
    }

    override fun packageNames(): List<String> = queries.selectPackageNames().executeAsList()

    override fun deletePackage(name: String) {
        queries.deletePackage(name)
    }

    override fun forget() = database.transaction {
        queries.deleteIndex()
        queries.deleteAllPackages()
    }

    fun countPackages(): Long = queries.countPackages().executeAsOne()
}

/** The same in memory, for the life of the process (tests, previews, a device without database). */
class InMemoryParliamentStorage : ParliamentStorage {
    private var stored: StoredIndex? = null
    private val packages = LinkedHashMap<String, StoredParliamentPackage>()

    override fun index(): StoredIndex? = stored

    override fun saveIndex(index: StoredIndex) {
        stored = index
    }

    override fun storedPackage(name: String): StoredParliamentPackage? = packages[name]

    override fun savePackage(stored: StoredParliamentPackage) {
        packages[stored.name] = stored
    }

    override fun packageNames(): List<String> = packages.keys.sorted()

    override fun deletePackage(name: String) {
        packages.remove(name)
    }

    override fun forget() {
        stored = null
        packages.clear()
    }
}
