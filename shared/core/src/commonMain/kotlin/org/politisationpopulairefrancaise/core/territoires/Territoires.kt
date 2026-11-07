package org.politisationpopulairefrancaise.core.territoires

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.ElusDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** What some packages need: those missing or stale on the device, and their size to announce before downloading. */
data class TerritoiresPlan(val missing: List<TerritoryPackageEntry>, val bytes: Long) {
    val isEmpty: Boolean get() = missing.isEmpty()
}

sealed interface TerritoiresDownload {
    data class Done(val codes: List<String>) : TerritoiresDownload

    /** The bytes did not match the sha256 of the national level, or were not the package asked for: nothing kept. */
    data class Refused(val code: String) : TerritoiresDownload

    data object NoServer : TerritoiresDownload

    data object Unreachable : TerritoiresDownload
}

sealed interface TerritoiresUpdate {
    data class Updated(val national: Boolean, val packages: List<String>) : TerritoiresUpdate

    data object UpToDate : TerritoiresUpdate

    data object NoServer : TerritoiresUpdate

    data object Unreachable : TerritoiresUpdate

    /** The server answered a document this application cannot read (a newer format): nothing changed. */
    data object Unreadable : TerritoiresUpdate
}

/**
 * The referential on the device (D-090). Nothing it reads is ever sent: searches and choices stay
 * here; the server only sees which package is downloaded, after a gesture of the user ([download],
 * [update]). The national level is always available offline, from the copy shipped with the
 * application ([bundled]) or a newer checked copy.
 */
class Territoires(
    private val storage: TerritoiresStorage,
    /** Null without a server (the current APK). */
    private val server: RemoteEvents?,
    /** The national JSON shipped with the application (composeResources of shared/ui), read by the host. */
    private val bundled: suspend () -> String?,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    val hasServer: Boolean get() = server != null

    private var national: Pair<String, NationalTerritories>? = null
    private var nationalKey: String? = null
    private var nationalLoaded = false
    private val packages = HashMap<String, Pair<String, TerritoryPackage>>()
    private var communesIndex: Pair<String, CommunesIndex>? = null

    /** The newest valid copy between the shipped one and the stored one; null only if neither can be read. */
    suspend fun national(): NationalTerritories? {
        val stored = storage.load(TerritoiresReader.NATIONAL_CODE)?.takeIf { valid(it) }
        val key = stored?.sha256 ?: ""
        if (nationalLoaded && nationalKey == key) return national?.second
        val fromStorage = stored?.let { s -> TerritoiresReader.readNational(s.json)?.let { s.sha256 to it } }
        val shipped = bundled()?.let { text -> TerritoiresReader.readNational(text)?.let { TerritoiresDigest.sha256Hex(text.encodeToByteArray()) to it } }
        val chosen = listOfNotNull(fromStorage, shipped).maxWithOrNull(
            compareBy<Pair<String, NationalTerritories>>({ it.second.builtOn?.toIso() ?: "" }, { if (it === fromStorage) 1 else 0 }),
        )
        national = chosen
        nationalKey = key
        nationalLoaded = true
        return chosen?.second
    }

    /** A package on the device, read once per version; null when missing or damaged. */
    fun storedPackage(code: String): TerritoryPackage? {
        val stored = storage.load(code)?.takeIf { valid(it) } ?: return null
        packages[code]?.let { (sha, value) -> if (sha == stored.sha256) return value }
        val read = TerritoiresReader.readPackage(stored.json)?.takeIf { it.code == code } ?: return null
        packages[code] = stored.sha256 to read
        return read
    }

    /** The index of the names of every commune, when it was downloaded. */
    fun storedCommunesIndex(): CommunesIndex? {
        val stored = storage.load(TerritoiresReader.COMMUNES_CODE)?.takeIf { valid(it) } ?: return null
        communesIndex?.let { (sha, value) -> if (sha == stored.sha256) return value }
        val read = TerritoiresReader.readCommunesIndex(stored.json) ?: return null
        communesIndex = stored.sha256 to read
        return read
    }

    /** Codes of the department packages on the device. */
    fun storedPackageCodes(): List<String> =
        storage.codes().filter { it != TerritoiresReader.NATIONAL_CODE && it != TerritoiresReader.COMMUNES_CODE }

    /**
     * One territory. France, regions, departments, collectivities and overseas collectivities:
     * always. Communes, districts and EPCI: when their package is on the device. Follows the codes
     * of merged communes ([Territory.formerCodes]).
     */
    suspend fun find(code: TerritoryCode): Territory? {
        national()?.find(code)?.let { return it }
        val candidates = when (code.type) {
            TerritoryType.COMMUNE, TerritoryType.ARRONDISSEMENT -> listOf(packageOfCommune(code.code)) + storedPackageCodes()
            else -> storedPackageCodes()
        }
        for (pkg in candidates.distinct()) {
            storedPackage(pkg)?.find(code)?.let { return it }
        }
        return null
    }

    /** The value of a `ppf-territory-code` tag, read tolerantly; null when invalid or unknown (the caller then uses the label). */
    suspend fun resolve(tagValue: String?): Territory? = TerritoryCode.parse(tagValue)?.let { find(it) }

    /**
     * On the device only: names folded with [FrenchText.fold] (accents, case, hyphens; "St" and
     * "Ste" read as "Saint" and "Sainte"), five-digit postal codes, official codes ("69", "2A",
     * "69123", a SIREN, or a tag such as `dep:69`). National types always; communes among the
     * packages on the device, and every commune once the index of the names is downloaded (without
     * figures until its package is). Best matches first, at most [limit].
     */
    suspend fun search(text: String, types: Set<TerritoryType> = TerritoryType.entries.toSet(), limit: Int = 20): List<Territory> {
        val nationalLevel = national() ?: return emptyList()
        val query = text.trim()
        if (query.isEmpty() || limit <= 0) return emptyList()
        val local = storedPackageCodes().mapNotNull(::storedPackage)
        val pool = LinkedHashMap<TerritoryCode, Territory>()
        nationalLevel.all.forEach { pool[it.code] = it }
        local.forEach { pkg -> (pkg.communes + pkg.arrondissements + pkg.epcis).forEach { pool.putIfAbsent(it.code, it) } }
        storedCommunesIndex()?.communes?.forEach { line ->
            val code = TerritoryCode.commune(line.code) ?: return@forEach
            if (code !in pool) pool[code] = indexTerritory(line, nationalLevel)
        }
        val candidates = pool.values.filter { it.code.type in types }

        // An official code first (a commune code has five digits, like a postal code: both are tried).
        val guesses = listOfNotNull(TerritoryCode.parse(query)) + codeGuesses(query)
        val exact = candidates.filter { territory ->
            guesses.any { guess -> territory.code == guess || (guess.type == TerritoryType.COMMUNE && guess.code in territory.formerCodes) }
        }.sortedWith(RANK)
        TerritoiresReader.normalizePostalCode(query)?.let { postalCode ->
            val communes = candidates.filter { postalCode in it.postalCodes }
            val areas = nationalLevel.packagesFor(postalCode).mapNotNull { entry ->
                (TerritoryCode.departement(entry.code) ?: TerritoryCode.outreMer(entry.code))?.let { nationalLevel.find(it) }
            }.filter { it.code.type in types }
            return (exact + communes.sortedWith(RANK) + areas).distinctBy { it.code }.take(limit)
        }
        val words = queryWords(query)
        if (words.isEmpty()) return exact.take(limit)
        val scored = candidates.mapNotNull { territory ->
            val score = nameScore(words, territory.name) ?: return@mapNotNull null
            score to territory
        }.sortedWith(compareBy<Pair<Int, Territory>> { it.first }.thenComparator { a, b -> RANK.compare(a.second, b.second) }).map { it.second }
        return (exact + scored).distinctBy { it.code }.take(limit)
    }

    /**
     * The assemblies a resident of [commune] can petition, nearest first: the commune, its EPCI,
     * its departmental collectivity, its region (or the collectivity that holds its powers), or its
     * overseas collectivity. Null when the package of the commune is not on the device.
     */
    suspend fun assembliesFor(commune: TerritoryCode): List<Territory>? {
        if (commune.type != TerritoryType.COMMUNE) return null
        val place = find(commune) ?: return null
        val nationalLevel = national()
        val result = mutableListOf(place)
        place.epci?.let { epci -> storedPackage(place.packageCode ?: "")?.find(epci)?.let(result::add) }
        place.collectivite?.let { c -> nationalLevel?.find(c)?.let(result::add) }
        place.region?.let { r ->
            val region = nationalLevel?.find(r)
            val holder = region?.heldBy
            when {
                holder == null -> region?.let(result::add)
                result.none { it.code == holder } -> nationalLevel.find(holder)?.let(result::add)
            }
        }
        return result.distinctBy { it.code }
    }

    /** Communes of a department, overseas collectivity, collectivity or EPCI, by name; null when a needed package is missing. */
    suspend fun communesOf(code: TerritoryCode): List<Territory>? {
        val nationalLevel = national() ?: return null
        val packageCodes = when (code.type) {
            TerritoryType.DEPARTEMENT, TerritoryType.OUTRE_MER -> listOf(code.code)
            TerritoryType.COLLECTIVITE -> nationalLevel.find(code)?.departements?.map { it.code } ?: return null
            TerritoryType.EPCI -> {
                val epci = find(code) ?: return null
                val members = epci.members.map { it.code }.toSet()
                val pkgs = members.map(::packageOfCommune).distinct()
                val communes = pkgs.map { storedPackage(it) ?: return null }.flatMap { it.communes }.filter { it.code.code in members }
                return communes.sortedWith(BY_NAME)
            }
            else -> return null
        }
        val communes = packageCodes.map { storedPackage(it) ?: return null }.flatMap { it.communes }
        val filtered = if (code.type == TerritoryType.COLLECTIVITE) communes.filter { it.collectivite == code } else communes
        return filtered.sortedWith(BY_NAME)
    }

    /** What [postalCode] needs: the packages that cover it and are missing or stale on the device. */
    suspend fun plan(postalCode: String): TerritoiresPlan {
        val nationalLevel = national() ?: return TerritoiresPlan(emptyList(), 0)
        return planEntries(nationalLevel.packagesFor(postalCode))
    }

    /** What the packages [codes] need (a department, an overseas collectivity, or [TerritoiresReader.COMMUNES_CODE]). */
    suspend fun planPackages(codes: List<String>): TerritoiresPlan {
        val nationalLevel = national() ?: return TerritoiresPlan(emptyList(), 0)
        return planEntries(codes.distinct().mapNotNull(nationalLevel::entry))
    }

    private fun planEntries(entries: List<TerritoryPackageEntry>): TerritoiresPlan {
        val missing = entries.filter { storage.load(it.code)?.sha256 != it.sha256 }
        return TerritoiresPlan(missing, missing.sumOf { it.bytes })
    }

    /** Downloads the packages of [plan]; each one is kept only if its bytes match the national level and it reads as the package asked for. */
    suspend fun download(plan: TerritoiresPlan): TerritoiresDownload {
        val remote = server ?: return TerritoiresDownload.NoServer
        val done = mutableListOf<String>()
        for (entry in plan.missing) {
            val answer = fetch(remote, entry.code, null)
            val bytes = (answer as? ElusDownload.Body)?.bytes ?: return TerritoiresDownload.Unreachable
            if (!accept(entry, bytes)) return TerritoiresDownload.Refused(entry.code)
            done += entry.code
        }
        return TerritoiresDownload.Done(done)
    }

    /** "Rechercher une mise à jour": the national level, then the packages on the device whose sha256 changed. */
    suspend fun update(): TerritoiresUpdate {
        val remote = server ?: return TerritoiresUpdate.NoServer
        national()
        val known = national?.first
        var nationalChanged = false
        when (val answer = fetch(remote, null, known)) {
            is ElusDownload.Body -> {
                if (answer.bytes.size > TerritoiresReader.MAX_NATIONAL_BYTES) return TerritoiresUpdate.Unreadable
                val text = utf8(answer.bytes) ?: return TerritoiresUpdate.Unreadable
                val read = TerritoiresReader.readNational(text) ?: return TerritoiresUpdate.Unreadable
                val sha = TerritoiresDigest.sha256Hex(answer.bytes)
                if (sha != known) {
                    storage.save(StoredTerritoires(TerritoiresReader.NATIONAL_CODE, sha, text, read.builtOn?.toIso(), clock()))
                    national = sha to read
                    nationalKey = sha
                    nationalLoaded = true
                    nationalChanged = true
                }
            }
            ElusDownload.NotModified -> Unit
            else -> return TerritoiresUpdate.Unreachable
        }
        val nationalLevel = national?.second ?: return TerritoiresUpdate.Unreadable
        val updated = mutableListOf<String>()
        for (code in storage.codes().filter { it != TerritoiresReader.NATIONAL_CODE }) {
            val entry = nationalLevel.entry(code)
            if (entry == null) {
                // The server no longer serves this package: the device keeps no orphan.
                storage.delete(code)
                packages.remove(code)
                continue
            }
            if (storage.load(code)?.sha256 == entry.sha256) continue
            val bytes = (fetch(remote, code, null) as? ElusDownload.Body)?.bytes ?: return TerritoiresUpdate.Unreachable
            if (accept(entry, bytes)) updated += code
        }
        return if (nationalChanged || updated.isNotEmpty()) TerritoiresUpdate.Updated(nationalChanged, updated) else TerritoiresUpdate.UpToDate
    }

    /** Erases the downloaded documents; the shipped national copy remains. */
    fun forget() {
        storage.deleteAll()
        national = null
        nationalLoaded = false
        packages.clear()
        communesIndex = null
    }

    private suspend fun fetch(remote: RemoteEvents, code: String?, etag: String?): ElusDownload = try {
        remote.fetchTerritoires(code, etag)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ElusDownload.Unavailable(e)
    }

    private fun accept(entry: TerritoryPackageEntry, bytes: ByteArray): Boolean {
        if (bytes.size > TerritoiresReader.MAX_PACKAGE_BYTES || !TerritoiresDigest.matches(bytes, entry.sha256)) return false
        val text = utf8(bytes) ?: return false
        val builtOn: String? = if (entry.code == TerritoiresReader.COMMUNES_CODE) {
            val index = TerritoiresReader.readCommunesIndex(text) ?: return false
            communesIndex = entry.sha256 to index
            index.geography?.toIso()
        } else {
            val pkg = TerritoiresReader.readPackage(text)?.takeIf { it.code == entry.code } ?: return false
            packages[entry.code] = entry.sha256 to pkg
            pkg.geography?.toIso()
        }
        storage.save(StoredTerritoires(entry.code, entry.sha256, text, builtOn, clock()))
        return true
    }

    private fun valid(stored: StoredTerritoires): Boolean = TerritoiresDigest.matches(stored.json.encodeToByteArray(), stored.sha256)

    private fun utf8(bytes: ByteArray): String? = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    private fun indexTerritory(line: CommuneName, nationalLevel: NationalTerritories): Territory {
        val area = TerritoryCode.departement(line.packageCode)?.takeIf { !TerritoiresReader.isOutreMer(line.packageCode) }
            ?: TerritoryCode.outreMer(line.packageCode)
        val region = area?.let { nationalLevel.find(it) }?.region
        return Territory(
            code = TerritoryCode.commune(line.code) ?: TerritoryCode.FRANCE,
            name = line.name,
            ofName = "de ${line.name}",
            parents = listOfNotNull(area, region, TerritoryCode.FRANCE),
            population = null,
            electorate = null,
            packageCode = line.packageCode,
        )
    }

    companion object {
        /** The package of a commune by its code: `2A004` in 2A, `97411` in 974, `69123` in 69. */
        fun packageOfCommune(code: String): String = if (code.startsWith("97") || code.startsWith("98")) code.take(3) else code.take(2)

        /** Types first in this order, then larger population, then name. */
        private val TYPE_ORDER = listOf(
            TerritoryType.FRANCE, TerritoryType.REGION, TerritoryType.DEPARTEMENT, TerritoryType.OUTRE_MER,
            TerritoryType.COLLECTIVITE, TerritoryType.COMMUNE, TerritoryType.ARRONDISSEMENT, TerritoryType.EPCI,
        )

        private val BY_NAME: Comparator<Territory> = compareBy<Territory>({ FrenchText.fold(it.name) }, { it.code.tag })

        private val RANK: Comparator<Territory> = compareBy<Territory>(
            { TYPE_ORDER.indexOf(it.code.type) },
            { -(it.population?.municipale ?: -1) },
            { FrenchText.fold(it.name) },
            { it.code.tag },
        )

        private val ABBREVIATIONS = mapOf("st" to "saint", "ste" to "sainte", "sts" to "saints", "stes" to "saintes")

        internal fun queryWords(text: String): List<String> = FrenchText.words(text).map { ABBREVIATIONS[it] ?: it }

        /** Codes a bare query may mean: a department (`69`, `2a`, `974`), a commune (`69123`), an EPCI (nine digits). */
        private fun codeGuesses(text: String): List<TerritoryCode> {
            val value = text.trim()
            return listOfNotNull(
                TerritoryCode.departement(value),
                TerritoryCode.outreMer(value),
                TerritoryCode.region(value),
                TerritoryCode.commune(value),
                TerritoryCode.epci(value),
                TerritoryCode.collectivite(value),
            )
        }

        /**
         * 0 when the folded name equals the query, 1 when it starts with it, 2 when every word of
         * the query starts a word of the name; null otherwise.
         */
        internal fun nameScore(words: List<String>, name: String): Int? {
            val nameWords = queryWords(name)
            val folded = nameWords.joinToString(" ")
            val query = words.joinToString(" ")
            return when {
                folded == query -> 0
                folded.startsWith(query) -> 1
                words.all { w -> nameWords.any { it.startsWith(w) } } -> 2
                else -> null
            }
        }
    }
}
