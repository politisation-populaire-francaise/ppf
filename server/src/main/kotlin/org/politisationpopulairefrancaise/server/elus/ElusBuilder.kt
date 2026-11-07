package org.politisationpopulairefrancaise.server.elus

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.zip.GZIPOutputStream

/** One package ready to be stored: its JSON body (exact bytes), their SHA-256, and its postal codes. */
class BuiltPackage(val code: String, val name: String, val body: ByteArray, val postalCodes: List<String>) {
    val sha256: String = sha256Hex(body)

    fun stored(): StoredPackage = StoredPackage(code, name, sha256, body.size, postalCodes, gzip(body))

    /** The line of the index that describes this package. */
    fun entry(): ElusPackageEntry = ElusPackageEntry(code, name, sha256, body.size.toLong(), postalCodes)
}

/**
 * Builds the packages of the contract (section 1.4) from the geography and the staged officials.
 * Everything is sorted with fixed rules (no locale, no hash order): the same data give the same bytes.
 */
class ElusBuilder(
    private val geography: Geography,
    /** Commune to its intercommunality (SIREN), from the file of the community councillors. */
    private val communeEpcis: Map<String, String>,
    private val epciNames: Map<String, String>,
    private val example: Boolean,
    private val sourceUpdatedOn: String,
) {
    /**
     * The packages to build: every department of the Code officiel géographique, every overseas
     * collectivity with at least one official among [stagedAreas], and the national package.
     */
    fun packageCodes(stagedAreas: Set<String>): List<String> {
        val collectivities = geography.collectivities.keys.filter { code -> areasOf(code).any { it in stagedAreas } }
        return (geography.departments.keys + collectivities).sorted() + NATIONAL_PACKAGE
    }

    /** The areas of the RNE gathered in package [code]. */
    fun areasOf(code: String): List<String> = listOf(code) + (AREA_ALIASES[code] ?: emptyList())

    private val communesByArea: Map<String, List<GeoCommune>> by lazy { geography.communes.values.groupBy { it.area } }

    /** The communes of package [code], sorted by name (accents and case aside), then code. */
    fun communesOf(code: String): List<GeoCommune> =
        communesByArea[code].orEmpty().sortedWith(compareBy<GeoCommune>({ sortKey(it.name) }, { it.name }, { it.code }))

    /** The intercommunalities of [communes], for the query of the staged officials. */
    fun sirensOf(communes: List<GeoCommune>): Set<String> = communes.mapNotNullTo(sortedSetOf()) { communeEpcis[it.code] }

    /** The region of package [code], or null (overseas collectivities, national package). */
    fun regionOf(code: String): String? = geography.departments[code]?.region

    /** Builds package [code] from its [communes] and its [officials] (in any order). */
    fun build(code: String, communes: List<GeoCommune>, officials: List<StagedOfficial>): BuiltPackage {
        val name = if (code == NATIONAL_PACKAGE) NATIONAL_NAME else geography.area(code)?.name ?: code
        val regionCode = regionOf(code)
        val region = regionCode?.let { ElusRegion(it, geography.regions[it] ?: it) }
        val packageCommunes = communes.map { commune ->
            ElusCommune(
                code = commune.code,
                name = commune.name,
                postalCodes = geography.postalCodes[commune.code].orEmpty(),
                epci = communeEpcis[commune.code],
                cantons = geography.cantons[commune.code].orEmpty(),
                circonscriptions = geography.circonscriptions[commune.code].orEmpty(),
            )
        }
        val sorted = officials.sortedWith(OFFICIAL_ORDER)
        val epcis = (packageCommunes.mapNotNull { it.epci } + sorted.filter { it.mandate == Mandate.COMMUNAUTAIRE }.map { it.territory })
            .toSortedSet().map { ElusNamed(it, epciNames[it] ?: it) }
        val cantons = (packageCommunes.flatMap { it.cantons } + sorted.filter { it.mandate == Mandate.DEPARTEMENTAL }.map { it.territory })
            .toSortedSet().map { ElusNamed(it, geography.cantonNames[it] ?: it) }
        val circonscriptions = (packageCommunes.flatMap { it.circonscriptions } + sorted.filter { it.mandate == Mandate.DEPUTE }.map { it.territory })
            .toSortedSet().map { ElusNamed(it, geography.circonscriptionName(it)) }
        val body = ElusPackage(
            format = ELUS_FORMAT,
            example = example,
            code = code,
            name = name,
            region = region,
            sourceUpdatedOn = sourceUpdatedOn,
            communes = packageCommunes,
            epcis = epcis,
            cantons = cantons,
            circonscriptions = circonscriptions,
            officials = sorted.map { official ->
                ElusOfficial(
                    mandate = official.mandate.code,
                    territory = official.territory,
                    commune = if (official.mandate == Mandate.COMMUNAUTAIRE) official.commune else null,
                    name = official.name,
                    function = official.function,
                    since = official.since,
                    functionSince = official.functionSince,
                )
            },
        )
        val postalCodes = packageCommunes.flatMap { it.postalCodes }.toSortedSet().toList()
        return BuiltPackage(code, name, ElusJson.encodeToString(ElusPackage.serializer(), body).toByteArray(Charsets.UTF_8), postalCodes)
    }

    companion object {
        const val NATIONAL_NAME: String = "France"

        /**
         * Areas of the RNE that belong to a package besides its own code: Corsica (20R, see
         * [Rne.CORSICA]) to both Corsican departments, the Collectivité européenne d'Alsace (6AE) to
         * the Bas-Rhin and the Haut-Rhin, the Métropole de Lyon (69M) to the Rhône, and the
         * constituency shared by Saint-Barthélemy and Saint-Martin (ZX) to both.
         */
        val AREA_ALIASES: Map<String, List<String>> = mapOf(
            "2A" to listOf(Rne.CORSICA),
            "2B" to listOf(Rne.CORSICA),
            "67" to listOf("6AE"),
            "68" to listOf("6AE"),
            "69" to listOf("69M"),
            "977" to listOf("ZX"),
            "978" to listOf("ZX"),
        )

        /** By mandate, territory, order of the council, then name: the order of the contract. */
        val OFFICIAL_ORDER: Comparator<StagedOfficial> = compareBy<StagedOfficial>(
            { it.mandate.ordinal },
            { it.territory },
            { it.rank },
            { sortKey(it.name) },
            { it.name },
            { it.since },
            { it.function ?: "" },
            { it.functionSince ?: "" },
            { it.commune ?: "" },
        )

        /** A name as sorted: without accents, lowercase. */
        fun sortKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

        private val MARKS = Regex("\\p{M}+")
    }
}

/** The index of an import (GET /api/v1/elus). */
fun buildIndex(example: Boolean, sourceUpdatedOn: String, importedOn: String, packages: List<ElusPackageEntry>): ByteArray {
    val index = ElusIndex(
        format = ELUS_FORMAT,
        example = example,
        source = ElusSourceInfo(ElusSourceDescription.NAME, ElusSourceDescription.URL, ElusSourceDescription.LICENCE, sourceUpdatedOn),
        importedOn = importedOn,
        packages = packages.sortedBy { it.code },
    )
    return ElusJson.encodeToString(ElusIndex.serializer(), index).toByteArray(Charsets.UTF_8)
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun gzip(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(bytes.size / 4 + 64)
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}
