package org.politisationpopulairefrancaise.server.elus

import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate

/**
 * One mandate in progress found in the directory served (docs/integration/elus-serveur.md, section
 * ElusLookup): only the public information of the directory, nothing else.
 */
data class ElusMatch(
    /** Stable identifier of the mandate, `rne1:` and 32 lowercase hexadecimal characters ([ElusLookup.mandateId]). */
    val id: String,
    val name: String,
    val mandate: Mandate,
    val function: String?,
    /** Code of the territory, as in the packages (INSEE code of the commune, SIREN, canton...). */
    val territory: String,
    val territoryName: String,
    /** Start of the mandate, `AAAA-MM-JJ`. */
    val since: String,
    /** Last update of the source (the RNE), as data.gouv.fr gives it. */
    val sourceUpdatedOn: LocalDate,
)

/**
 * What the directory says of a mandate. The RNE gives neither an end date nor an identifier: the only
 * answer is « in progress according to the source updated on ... » or « absent from it ».
 */
sealed interface MandateStatus {
    /** The mandate is in the import served, whose source was updated on [sourceUpdatedOn]. */
    data class Current(val sourceUpdatedOn: LocalDate) : MandateStatus

    /** The mandate is absent from the import served (ended, corrected, or never there). */
    data class NotFound(val sourceUpdatedOn: LocalDate) : MandateStatus

    /** No import is served yet (first start): nothing can be said; never treat it as [NotFound]. */
    data object Unavailable : MandateStatus
}

/**
 * Read-only lookup in the directory of elected officials, for the queue `elus.certifications` of the
 * Décider space (contract docs/integration/back-office.md, section 7).
 *
 * Works on the packages of the import served (V6__elus.sql): the staging is emptied after each
 * import. Nothing is logged, least of all the names looked up; nothing is stored beyond a compact
 * set of identifiers of the import served (16 bytes per mandate, for [mandateStatus]).
 */
class ElusLookup(private val service: ElusService) {

    /** The identifiers of one import, sorted by their two halves, for a binary search. */
    private class IdIndex(val importId: Long, val sourceUpdatedOn: LocalDate, val high: LongArray, val low: LongArray) {
        fun contains(h: Long, l: Long): Boolean {
            var from = 0
            var to = high.size - 1
            while (from <= to) {
                val middle = (from + to) ushr 1
                val c = compareValues(high[middle], low[middle], h, l)
                when {
                    c < 0 -> from = middle + 1
                    c > 0 -> to = middle - 1
                    else -> return true
                }
            }
            return false
        }
    }

    @Volatile
    private var index: IdIndex? = null

    /**
     * The mandates in progress whose name holds every word of [name] (accents, case, hyphens and
     * apostrophes aside), of [mandate] and in [territory] (its code) when given, [limit] at most, sorted
     * as the packages. A query without any word of two letters or more finds nothing.
     */
    fun find(name: String, mandate: Mandate? = null, territory: String? = null, limit: Int = DEFAULT_LIMIT): List<ElusMatch> {
        val words = words(name).filter { it.length >= 2 }.toSet()
        if (words.isEmpty() || limit <= 0) return emptyList()
        val served = service.current()?.served ?: return emptyList()
        val wantedTerritory = territory?.trim()?.takeIf { it.isNotEmpty() }
        val codes = packagesFor(served.packages.keys, mandate, wantedTerritory)
        val seen = HashSet<String>()
        val matches = ArrayList<ElusMatch>()
        for (code in codes) {
            val pack = packageOf(code) ?: continue
            val updatedOn = LocalDate.parse(pack.sourceUpdatedOn)
            for (official in pack.officials) {
                val officialMandate = Mandate.entries.firstOrNull { it.code == official.mandate } ?: continue
                if (mandate != null && officialMandate != mandate) continue
                if (wantedTerritory != null && official.territory != wantedTerritory) continue
                if (!words(official.name).containsAll(words)) continue
                val id = mandateId(officialMandate, official.territory, official.name, official.since)
                // An official may be in several packages (a regional president, an intercommunality across two departments).
                if (!seen.add(id)) continue
                matches += ElusMatch(id, official.name, officialMandate, official.function, official.territory, territoryName(pack, officialMandate, official.territory), official.since, updatedOn)
                if (matches.size >= limit) return matches
            }
        }
        return matches
    }

    /**
     * The mandate in progress [id] ([mandateId]) of [mandate] in [territory] in the import served, or null when
     * it is not there or nothing is served ([mandateStatus] tells both apart). For the queue
     * `elus.certifications` of Décider, which keeps only the identifier and these two public codes, never
     * the name. Reads only the packages that may hold it ([packagesFor]).
     */
    fun byId(id: String, mandate: Mandate, territory: String): ElusMatch? {
        if (!isValidId(id)) return null
        val served = service.current()?.served ?: return null
        for (code in packagesFor(served.packages.keys, mandate, territory)) {
            val pack = packageOf(code) ?: continue
            for (official in pack.officials) {
                if (official.mandate != mandate.code || official.territory != territory) continue
                if (mandateId(mandate, official.territory, official.name, official.since) != id) continue
                return ElusMatch(
                    id, official.name, mandate, official.function, official.territory,
                    territoryName(pack, mandate, official.territory), official.since, LocalDate.parse(pack.sourceUpdatedOn),
                )
            }
        }
        return null
    }

    /** Whether the mandate [id] ([mandateId]) is in the import served. */
    fun mandateStatus(id: String): MandateStatus {
        val served = service.current()?.served ?: return MandateStatus.Unavailable
        val current = index?.takeIf { it.importId == served.id } ?: buildIndex(served) ?: return MandateStatus.Unavailable
        val halves = parseId(id) ?: return MandateStatus.NotFound(current.sourceUpdatedOn)
        return if (current.contains(halves.first, halves.second)) MandateStatus.Current(current.sourceUpdatedOn) else MandateStatus.NotFound(current.sourceUpdatedOn)
    }

    /** Date of the source of the import served, or null when none is served. */
    fun sourceUpdatedOn(): LocalDate? {
        val served = service.current()?.served ?: return null
        val index = ElusJson.decodeFromString(ElusIndex.serializer(), served.index.toString(Charsets.UTF_8))
        return LocalDate.parse(index.source.updatedOn)
    }

    @Synchronized
    private fun buildIndex(served: ServedImport): IdIndex? {
        index?.takeIf { it.importId == served.id }?.let { return it }
        var high = LongArray(1024)
        var low = LongArray(1024)
        var size = 0
        for (code in served.packages.keys.sorted()) {
            val pack = packageOf(code) ?: return null
            for (official in pack.officials) {
                val mandate = Mandate.entries.firstOrNull { it.code == official.mandate } ?: continue
                val digest = digestOf(mandate, official.territory, official.name, official.since)
                if (size == high.size) {
                    high = high.copyOf(size * 2)
                    low = low.copyOf(size * 2)
                }
                high[size] = longAt(digest, 0)
                low[size] = longAt(digest, 8)
                size++
            }
        }
        sortPairs(high, low, size)
        // Without duplicates (an official in several packages).
        var kept = 0
        for (i in 0 until size) {
            if (kept > 0 && high[kept - 1] == high[i] && low[kept - 1] == low[i]) continue
            high[kept] = high[i]
            low[kept] = low[i]
            kept++
        }
        val updatedOn = sourceUpdatedOn() ?: return null
        val built = IdIndex(served.id, updatedOn, high.copyOf(kept), low.copyOf(kept))
        index = built
        return built
    }

    private fun packageOf(code: String): ElusPackage? =
        service.packageBody(code)?.let { ElusJson.decodeFromString(ElusPackage.serializer(), it.toString(Charsets.UTF_8)) }

    companion object {
        const val ID_PREFIX: String = "rne1:"
        const val DEFAULT_LIMIT: Int = 50

        private val ID = Regex("^rne1:[0-9a-f]{32}$")
        private val MARKS = Regex("\\p{M}+")
        private val SPACES = Regex("\\s+")
        private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

        /**
         * The stable identifier of a mandate (docs/FORMATS.md, « Identifiant d'un mandat `rne1:` »):
         * `rne1:` followed by the first 32 hexadecimal characters (128 bits) of the SHA-256 of
         * `mandate LF territory LF name LF since` in UTF-8, where [name] is canonical: Unicode NFD
         * without combining marks, lowercase (root locale), spaces collapsed and trimmed. The name is the
         * one of the packages (« Prénom Nom »): the function is left out, so that becoming mayor or
         * deputy keeps the identifier of the mandate.
         */
        fun mandateId(mandate: Mandate, territory: String, name: String, since: String): String =
            ID_PREFIX + digestOf(mandate, territory, name, since).take(16).joinToString("") { "%02x".format(it) }

        private fun digestOf(mandate: Mandate, territory: String, name: String, since: String): ByteArray {
            val input = "${mandate.code}\n${territory.trim()}\n${canonicalName(name)}\n${since.trim()}"
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        }

        private fun longAt(bytes: ByteArray, offset: Int): Long {
            var value = 0L
            for (i in offset until offset + 8) value = (value shl 8) or (bytes[i].toLong() and 0xff)
            return value
        }

        /** Heapsort of the pairs ([high], [low]) on their first [size] places, unsigned, without boxing. */
        private fun sortPairs(high: LongArray, low: LongArray, size: Int) {
            fun swap(i: Int, j: Int) {
                val h = high[i]
                high[i] = high[j]
                high[j] = h
                val l = low[i]
                low[i] = low[j]
                low[j] = l
            }
            fun less(i: Int, j: Int): Boolean = compareValues(high[i], low[i], high[j], low[j]) < 0
            fun sift(start: Int, end: Int) {
                var root = start
                while (2 * root + 1 < end) {
                    var child = 2 * root + 1
                    if (child + 1 < end && less(child, child + 1)) child++
                    if (!less(root, child)) return
                    swap(root, child)
                    root = child
                }
            }
            for (start in size / 2 - 1 downTo 0) sift(start, size)
            for (end in size - 1 downTo 1) {
                swap(0, end)
                sift(0, end)
            }
        }

        fun isValidId(id: String): Boolean = ID.matches(id)

        internal fun canonicalName(name: String): String =
            Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase(java.util.Locale.ROOT).replace(SPACES, " ").trim()

        /** The words of a name for the search: canonical, split on anything but letters and digits. */
        internal fun words(text: String): Set<String> =
            canonicalName(text).split(NOT_WORD).filter { it.isNotEmpty() }.toSet()

        private fun parseId(id: String): Pair<Long, Long>? {
            if (!ID.matches(id)) return null
            val hex = id.substring(ID_PREFIX.length)
            return java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16) to java.lang.Long.parseUnsignedLong(hex.substring(16), 16)
        }

        private fun compareValues(h1: Long, l1: Long, h2: Long, l2: Long): Int {
            val c = java.lang.Long.compareUnsigned(h1, h2)
            return if (c != 0) c else java.lang.Long.compareUnsigned(l1, l2)
        }

        /**
         * The packages to read: the national one for the European Parliament, the department of a
         * commune or of a senator when [territory] says it, every package otherwise.
         */
        internal fun packagesFor(available: Set<String>, mandate: Mandate?, territory: String?): List<String> {
            val all = available.sorted()
            if (mandate == Mandate.EUROPEEN) return listOf(NATIONAL_PACKAGE).filter { it in available }
            if (territory == null) return all
            val guess = when (mandate) {
                Mandate.MUNICIPAL -> if (territory.startsWith("97")) territory.take(3) else territory.take(2)
                Mandate.SENATEUR -> territory
                else -> null
            }
            return if (guess != null && guess in available) listOf(guess) else all
        }

        /** The name of [territory] for [mandate], from the lists of the package. */
        internal fun territoryName(pack: ElusPackage, mandate: Mandate, territory: String): String = when (mandate) {
            Mandate.MUNICIPAL -> pack.communes.firstOrNull { it.code == territory }?.name
            Mandate.COMMUNAUTAIRE -> pack.epcis.firstOrNull { it.code == territory }?.name
            Mandate.DEPARTEMENTAL -> pack.cantons.firstOrNull { it.code == territory }?.name
            Mandate.REGIONAL -> pack.region?.takeIf { it.code == territory }?.name
            Mandate.COLLECTIVITE -> pack.collectivites.firstOrNull { it.code == territory }?.name
            Mandate.DEPUTE -> pack.circonscriptions.firstOrNull { it.code == territory }?.name
            Mandate.SENATEUR -> pack.name.takeIf { pack.code == territory }
            Mandate.EUROPEEN -> ElusBuilder.NATIONAL_NAME
        } ?: territory
    }
}
