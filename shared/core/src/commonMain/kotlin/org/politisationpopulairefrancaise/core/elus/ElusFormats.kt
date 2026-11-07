package org.politisationpopulairefrancaise.core.elus

import fr.acinq.bitcoin.Crypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.politisationpopulairefrancaise.core.time.CivilDate

/*
 * The official directory of elected officials on the device (D-047; contract of the third wave of
 * "Comprendre", section 1.4): the index of the server (`GET /api/v1/elus`) and the package of one
 * department (`GET /api/v1/elus/{code}`), read from their JSON.
 *
 * Only what the contract keeps is read: name, mandate, function, territory, start dates. Nothing
 * else of a person exists here (no birth date, no sex, no profession): the labels of the
 * application are neutral and need none of it.
 */

/** The mandates of the directory, with the territory each one is attached to (contract, 1.4). */
enum class Mandate(val key: String) {
    /** Member of a municipal council; territory: the INSEE code of the commune. */
    MUNICIPAL("municipal"),

    /** Member of the council of an intercommunality; territory: its SIREN, `commune` the commune represented. */
    COMMUNAUTAIRE("communautaire"),

    /** Member of a departmental council; territory: the code of the canton. */
    DEPARTEMENTAL("departemental"),

    /** Member of a regional council; territory: the code of the region. */
    REGIONAL("regional"),

    /** Member of the assembly of a collectivity with a special status (Corse, Martinique, Guyane...). */
    COLLECTIVITE("collectivite"),

    /** Member of the Assemblée nationale; territory: the code of the constituency. */
    DEPUTE("depute"),

    /** Member of the Sénat; territory: the code of the department. */
    SENATEUR("senateur"),

    /** Member of the European Parliament elected in France; territory: `FR`, national package only. */
    EUROPEEN("europeen"),
    ;

    companion object {
        fun fromKey(key: String?): Mandate? = entries.firstOrNull { it.key == key?.trim() }
    }
}

/** A territory named in a package: an intercommunality, a canton, a constituency, a region. */
data class Territory(val code: String, val name: String)

/** Where the directory comes from, shown with the list (licence and date of the source). */
data class ElusSource(val name: String, val url: String?, val licence: String?, val updatedOn: CivilDate?)

/** One package announced by the index: its code, its name, the exact size and sha256 of its body, its postal codes. */
data class ElusPackageEntry(
    val code: String,
    val name: String,
    /** Lowercase hex SHA-256 of the exact bytes of the package (UTF-8 JSON, not compressed). */
    val sha256: String,
    val bytes: Long,
    val postalCodes: List<String>,
) {
    val isNational: Boolean get() = code == ElusReader.NATIONAL_CODE
}

/** The index of the server: every package, to find the department of a postal code without sending it. */
data class ElusIndex(
    val format: Int,
    /** True while the server serves the fictitious data set ([EXEMPLE]). */
    val example: Boolean,
    val source: ElusSource?,
    val importedOn: CivilDate?,
    val packages: List<ElusPackageEntry>,
) {
    fun entry(code: String): ElusPackageEntry? = packages.firstOrNull { it.code == code }

    /** The package of the members of the European Parliament elected in France, when the server has one. */
    val national: ElusPackageEntry? get() = entry(ElusReader.NATIONAL_CODE)

    /** The departments covering [postalCode] (rarely more than one, when a postal code straddles a border). */
    fun departmentsFor(postalCode: String): List<ElusPackageEntry> {
        val code = ElusReader.normalizePostalCode(postalCode) ?: return emptyList()
        return packages.filter { !it.isNational && code in it.postalCodes }
    }
}

/** A commune of a package: its codes and the territories it belongs to. */
data class ElusCommune(
    /** INSEE code. */
    val code: String,
    val name: String,
    val postalCodes: List<String>,
    /** SIREN of its intercommunality; null when it belongs to none. */
    val epci: String?,
    /** Codes of its cantons (several when the commune is split). */
    val cantons: List<String>,
    /** Codes of its legislative constituencies (several when the commune is split). */
    val circonscriptions: List<String>,
    /**
     * The collectivity whose assembly replaces the departmental council for this commune (`69M`
     * Métropole de Lyon, `94` Corse, `972`, `973`, overseas collectivities); null otherwise.
     */
    val collectivite: String? = null,
)

/** One mandate of one person, as the directory gives it. */
data class ElectedOfficial(
    val mandate: Mandate,
    val territory: String,
    /** The commune represented in an intercommunality, when the source gives it. */
    val commune: String?,
    val name: String,
    /** The label of the function in the source ("Maire", "1er adjoint"...), null when there is none. */
    val function: String?,
    val since: CivilDate?,
    val functionSince: CivilDate?,
) {
    /** Fictitious data are named "[EXEMPLE] Prénom Nom". */
    val isExample: Boolean get() = name.startsWith(ElusReader.EXAMPLE_MARKER)

    /** The name without its [EXEMPLE] marker (the badge says it instead). */
    val displayName: String get() = name.removePrefix(ElusReader.EXAMPLE_MARKER).trim()
}

/** The package of a department (or the national package `FR`). */
data class ElusPackage(
    val format: Int,
    val example: Boolean,
    val code: String,
    val name: String,
    val region: Territory?,
    val sourceUpdatedOn: CivilDate?,
    val communes: List<ElusCommune>,
    val epcis: List<Territory>,
    val cantons: List<Territory>,
    val circonscriptions: List<Territory>,
    val officials: List<ElectedOfficial>,
    /** Names of the collectivities of [ElusCommune.collectivite]; empty in packages written before this field. */
    val collectivites: List<Territory> = emptyList(),
) {
    /** True when the package says which communes belong to a collectivity (field added on 7 November 2026). */
    val knowsCollectivities: Boolean get() = communes.any { it.collectivite != null }

    fun collectiviteName(code: String): String? = collectivites.firstOrNull { it.code == code }?.name

    fun commune(code: String): ElusCommune? = communes.firstOrNull { it.code == code }

    /** The communes of [postalCode], by name. */
    fun communesFor(postalCode: String): List<ElusCommune> {
        val code = ElusReader.normalizePostalCode(postalCode) ?: return emptyList()
        return communes.filter { code in it.postalCodes }.sortedBy { it.name.lowercase() }
    }

    fun epciName(code: String): String? = epcis.firstOrNull { it.code == code }?.name
    fun cantonName(code: String): String? = cantons.firstOrNull { it.code == code }?.name
    fun circonscriptionName(code: String): String? = circonscriptions.firstOrNull { it.code == code }?.name
}

/**
 * Reads the index and the packages, strictly and tolerantly: the document itself must be what the
 * contract describes (format 1, a valid code, the expected fields), otherwise it is ignored (null,
 * never an exception); inside it, one entry that cannot be read (unknown mandate, missing name,
 * invalid code) is left out and the others are kept. Unknown fields are ignored.
 */
object ElusReader {
    /** The only version of the format this application reads. */
    const val FORMAT: Int = 1

    /** The code of the national package (members of the European Parliament elected in France). */
    const val NATIONAL_CODE: String = "FR"

    const val EXAMPLE_MARKER: String = "[EXEMPLE]"

    /** A package larger than this is refused before it is downloaded (the largest department weighs a few megabytes). */
    const val MAX_PACKAGE_BYTES: Long = 32L * 1024 * 1024

    private const val MAX_TEXT = 200

    /** `01` to `95`, `2A`, `2B`, `971` to `989` (overseas departments and collectivities), or `FR`. */
    private val PACKAGE_CODE = Regex("^([0-9]{2}|2[AB]|9[78][0-9]|FR)$")
    private val POSTAL_CODE = Regex("^[0-9]{5}$")
    private val INSEE_CODE = Regex("^([0-9]{5}|2[AB][0-9]{3})$")
    private val TERRITORY_CODE = Regex("^[0-9A-Za-z]{1,20}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    private val json = Json { ignoreUnknownKeys = true }

    fun isPackageCode(code: String): Boolean = PACKAGE_CODE.matches(code)

    /** "69 001" or " 69001 " as "69001"; null when it is not five digits. */
    fun normalizePostalCode(text: String): String? {
        val code = text.filterNot { it.isWhitespace() }
        return code.takeIf { POSTAL_CODE.matches(it) }
    }

    /** The index, or null when it is not an index of format 1. */
    fun readIndex(text: String): ElusIndex? = runCatching {
        val root = parse(text) ?: return null
        if (root.int("format") != FORMAT) return null
        val packages = root.array("packages")?.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val code = entry.text("code")?.takeIf(::isPackageCode) ?: return@mapNotNull null
            val name = entry.text("name") ?: return@mapNotNull null
            val sha = entry.text("sha256")?.lowercase()?.takeIf { SHA256.matches(it) } ?: return@mapNotNull null
            val bytes = entry.long("bytes")?.takeIf { it in 1..MAX_PACKAGE_BYTES } ?: return@mapNotNull null
            ElusPackageEntry(code, name, sha, bytes, entry.texts("postalCodes").filter { POSTAL_CODE.matches(it) }.distinct())
        } ?: return null
        val source = (root["source"] as? JsonObject)?.let { source ->
            source.text("name")?.let { ElusSource(it, source.text("url"), source.text("licence"), CivilDate.parseIso(source.text("updatedOn"))) }
        }
        ElusIndex(
            format = FORMAT,
            example = root.boolean("example") ?: false,
            source = source,
            importedOn = CivilDate.parseIso(root.text("importedOn")),
            packages = packages.distinctBy { it.code },
        )
    }.getOrNull()

    /** A package, or null when it is not a package of format 1 with a valid code and name. */
    fun readPackage(text: String): ElusPackage? = runCatching {
        val root = parse(text) ?: return null
        if (root.int("format") != FORMAT) return null
        val code = root.text("code")?.takeIf(::isPackageCode) ?: return null
        val name = root.text("name") ?: return null
        val communes = root.objects("communes").mapNotNull { commune ->
            val inseeCode = commune.text("code")?.takeIf { INSEE_CODE.matches(it) } ?: return@mapNotNull null
            ElusCommune(
                code = inseeCode,
                name = commune.text("name") ?: return@mapNotNull null,
                postalCodes = commune.texts("postalCodes").filter { POSTAL_CODE.matches(it) }.distinct(),
                epci = commune.text("epci")?.takeIf { TERRITORY_CODE.matches(it) },
                cantons = commune.texts("cantons").filter { TERRITORY_CODE.matches(it) }.distinct(),
                circonscriptions = commune.texts("circonscriptions").filter { TERRITORY_CODE.matches(it) }.distinct(),
                collectivite = commune.text("collectivite")?.takeIf { TERRITORY_CODE.matches(it) },
            )
        }.distinctBy { it.code }
        val officials = root.objects("officials").mapNotNull { official ->
            ElectedOfficial(
                mandate = Mandate.fromKey(official.text("mandate")) ?: return@mapNotNull null,
                territory = official.text("territory")?.takeIf { TERRITORY_CODE.matches(it) } ?: return@mapNotNull null,
                commune = official.text("commune")?.takeIf { INSEE_CODE.matches(it) },
                name = official.text("name")?.takeIf { it.removePrefix(EXAMPLE_MARKER).isNotBlank() } ?: return@mapNotNull null,
                function = official.text("function"),
                since = CivilDate.parseIso(official.text("since")),
                functionSince = CivilDate.parseIso(official.text("functionSince")),
            )
        }
        ElusPackage(
            format = FORMAT,
            example = root.boolean("example") ?: false,
            code = code,
            name = name,
            region = (root["region"] as? JsonObject)?.let(::territory),
            sourceUpdatedOn = CivilDate.parseIso(root.text("sourceUpdatedOn")),
            communes = communes,
            epcis = root.objects("epcis").mapNotNull(::territory).distinctBy { it.code },
            cantons = root.objects("cantons").mapNotNull(::territory).distinctBy { it.code },
            circonscriptions = root.objects("circonscriptions").mapNotNull(::territory).distinctBy { it.code },
            officials = officials,
            collectivites = root.objects("collectivites").mapNotNull(::territory).distinctBy { it.code },
        )
    }.getOrNull()

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text.removePrefix("﻿")) }.getOrNull() as? JsonObject

    private fun territory(element: JsonObject): Territory? {
        val code = element.text("code")?.takeIf { TERRITORY_CODE.matches(it) } ?: return null
        return Territory(code, element.text("name") ?: return null)
    }

    /** A non-blank string of at most 200 characters, trimmed; null for anything else (numbers included). */
    private fun JsonObject.text(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (value is JsonNull || !value.isString) return null
        return value.content.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
    }

    private fun JsonObject.texts(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content?.trim()?.takeIf { value -> value.isNotEmpty() } }

    private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}

/** The fingerprint of a package: the SHA-256 of its exact bytes, the one the index announces. */
object ElusDigest {
    fun sha256Hex(bytes: ByteArray): String = Crypto.sha256(bytes).toHexString()

    /** True when [bytes] are exactly the package the index announced with [expected]. */
    fun matches(bytes: ByteArray, expected: String): Boolean = sha256Hex(bytes) == expected.trim().lowercase()
}
