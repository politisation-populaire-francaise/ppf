package org.politisationpopulairefrancaise.core.territoires

import org.politisationpopulairefrancaise.core.db.PpfDatabase

/** A document as kept on the device: the exact JSON, checked against [sha256] before it was kept. */
data class StoredTerritoires(
    val code: String,
    val sha256: String,
    val json: String,
    /** The day the document was built (`AAAA-MM-JJ`), when it gives one. */
    val builtOn: String?,
    /** When it was downloaded, in seconds since 1970. */
    val downloadedAt: Long,
)

/** What the device keeps of the referential: checked documents, by code. No personal data, never sent. */
interface TerritoiresStorage {
    fun load(code: String): StoredTerritoires?

    fun save(stored: StoredTerritoires)

    fun codes(): List<String>

    fun delete(code: String)

    fun deleteAll()
}

/** The referential in the local database (Territoires.sq). */
class TerritoiresStore(private val database: PpfDatabase) : TerritoiresStorage {
    private val queries get() = database.territoiresQueries

    override fun load(code: String): StoredTerritoires? =
        queries.selectPackage(code).executeAsOneOrNull()?.let { StoredTerritoires(it.code, it.sha256, it.json, it.built_on, it.downloaded_at) }

    override fun save(stored: StoredTerritoires) {
        queries.savePackage(stored.code, stored.sha256, stored.json, stored.builtOn, stored.downloadedAt)
    }

    override fun codes(): List<String> = queries.selectPackageCodes().executeAsList()

    override fun delete(code: String) {
        queries.deletePackage(code)
    }

    override fun deleteAll() {
        queries.deleteAllPackages()
    }
}

/** The same in memory (tests, previews, a device without database). */
class InMemoryTerritoiresStorage : TerritoiresStorage {
    private val documents = LinkedHashMap<String, StoredTerritoires>()

    override fun load(code: String): StoredTerritoires? = documents[code]

    override fun save(stored: StoredTerritoires) {
        documents[stored.code] = stored
    }

    override fun codes(): List<String> = documents.keys.sorted()

    override fun delete(code: String) {
        documents.remove(code)
    }

    override fun deleteAll() {
        documents.clear()
    }
}
