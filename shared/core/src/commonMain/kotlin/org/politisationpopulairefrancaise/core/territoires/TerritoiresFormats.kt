package org.politisationpopulairefrancaise.core.territoires

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
 * The referential of territories (D-090, docs/integration/territoires.md): official codes, names,
 * parents, populations and registered voters of the communes, intercommunalities, departments,
 * collectivities and regions. Public statistics only: nothing here is about a person.
 */

/** The kinds of territories, with the prefix of their code ([TerritoryCode.tag], tag `ppf-territory-code`). */
enum class TerritoryType(val key: String) {
    FRANCE("fr"),
    REGION("reg"),
    DEPARTEMENT("dep"),

    /** Collectivity with the powers of a department (COG "CTCD"): `01D`... `69D`, and the special statuses `20R`, `69M`, `6AE`, `75C`, `972R`, `973R`, `976R`. */
    COLLECTIVITE("ctcd"),

    /** Overseas collectivity (article 74 of the Constitution) or Nouvelle-Calédonie: `975`, `977`, `978`, `986`, `987`, `988`. */
    OUTRE_MER("comer"),

    /** EPCI à fiscalité propre, by SIREN. */
    EPCI("epci"),
    COMMUNE("com"),

    /** Municipal district of Paris, Lyon or Marseille. */
    ARRONDISSEMENT("arm"),
    ;

    companion object {
        fun fromKey(key: String): TerritoryType? = entries.firstOrNull { it.key == key.trim().lowercase() }
    }
}

/**
 * A stable code of the referential: `fr`, `reg:84`, `dep:2A`, `ctcd:69M`, `comer:987`,
 * `epci:200040590`, `com:69123`, `arm:69381`. Built only by [parse] and the factories, which check
 * the shape of the official code (never its existence: [Territoires.find] does).
 */
class TerritoryCode private constructor(val type: TerritoryType, val code: String) {
    /** The text of the tag `ppf-territory-code` and of the JSON. */
    val tag: String get() = if (type == TerritoryType.FRANCE) FRANCE_TAG else "${type.key}:$code"

    override fun equals(other: Any?): Boolean = other is TerritoryCode && other.type == type && other.code == code

    override fun hashCode(): Int = type.hashCode() * 31 + code.hashCode()

    override fun toString(): String = tag

    companion object {
        private const val FRANCE_TAG = "fr"

        val FRANCE: TerritoryCode = TerritoryCode(TerritoryType.FRANCE, "FR")

        private val SHAPES: Map<TerritoryType, Regex> = mapOf(
            TerritoryType.REGION to Regex("^[0-9]{2}$"),
            TerritoryType.DEPARTEMENT to Regex("^(0[1-9]|1[0-9]|2[1-9AB]|[3-8][0-9]|9[0-5]|97[1-6])$"),
            TerritoryType.COLLECTIVITE to Regex("^([0-9]{2}D|97[14]D|20R|69M|6AE|75C|972R|973R|976R)$"),
            TerritoryType.OUTRE_MER to Regex("^9[78][0-9]$"),
            TerritoryType.EPCI to Regex("^[0-9]{9}$"),
            TerritoryType.COMMUNE to Regex("^([0-9]{5}|2[AB][0-9]{3})$"),
            TerritoryType.ARRONDISSEMENT to Regex("^[0-9]{5}$"),
        )

        /** A code of [type] when [code] has its shape (upper-cased, trimmed); null otherwise. */
        fun of(type: TerritoryType, code: String): TerritoryCode? {
            if (type == TerritoryType.FRANCE) return FRANCE
            val clean = code.trim().uppercase()
            return if (SHAPES.getValue(type).matches(clean)) TerritoryCode(type, clean) else null
        }

        /** `com:69123`, `COM:69123`, ` dep:2a ` (trimmed, prefix case ignored, code upper-cased), `fr`; null for anything else. */
        fun parse(text: String?): TerritoryCode? {
            val value = text?.trim() ?: return null
            if (value.length > 32) return null
            if (value.equals(FRANCE_TAG, ignoreCase = true)) return FRANCE
            val colon = value.indexOf(':')
            if (colon <= 0) return null
            val type = TerritoryType.fromKey(value.substring(0, colon)) ?: return null
            if (type == TerritoryType.FRANCE) return null
            return of(type, value.substring(colon + 1))
        }

        fun commune(insee: String): TerritoryCode? = of(TerritoryType.COMMUNE, insee)
        fun departement(code: String): TerritoryCode? = of(TerritoryType.DEPARTEMENT, code)
        fun region(code: String): TerritoryCode? = of(TerritoryType.REGION, code)
        fun epci(siren: String): TerritoryCode? = of(TerritoryType.EPCI, siren)
        fun collectivite(code: String): TerritoryCode? = of(TerritoryType.COLLECTIVITE, code)
        fun outreMer(code: String): TerritoryCode? = of(TerritoryType.OUTRE_MER, code)
        fun arrondissement(code: String): TerritoryCode? = of(TerritoryType.ARRONDISSEMENT, code)
    }
}

/** What a source describes. */
enum class SourceKind(val key: String) {
    POPULATION("population"),
    ELECTORATE("electorate"),
    GEOGRAPHY("geography"),
    ;

    companion object {
        fun fromKey(key: String?): SourceKind? = entries.firstOrNull { it.key == key }
    }
}

/** Where a figure comes from, shown next to it. */
data class TerritorySource(
    /** Short id used in the packages: `popref-2023`, `reu-2026-02-11`, `mayotte-2017`. */
    val id: String,
    val kind: SourceKind,
    val name: String,
    val url: String?,
    val licence: String?,
    /** Populations: the vintage (2023). Null for registered voters. */
    val vintage: Int?,
    /** Populations: the day they came into force (2026-01-01). Registered voters: the extraction (2026-02-11). */
    val date: CivilDate?,
)

data class Population(
    val municipale: Long,
    /** Null for a sum (it would count some people twice) or when the source gives none. */
    val totale: Long?,
    val source: TerritorySource?,
    /** True when the server added up communes (EPCI, `ctcd:69D`, commune merged since the vintage). */
    val computed: Boolean,
)

data class Electorate(
    /** Liste principale: French voters. */
    val principale: Long,
    /** Listes complémentaires municipales: citizens of the European Union. Null when unknown. */
    val complementaires: Long?,
    val source: TerritorySource?,
    val computed: Boolean,
)

/** Whether the residents of a department or overseas collectivity count among the 30 (Règlement de l'Assemblée nationale, article 148, alinéa 6). */
enum class SpreadRule(val key: String) {
    COUNTS("counts"),
    TO_CONFIRM("to-confirm"),
    EXCLUDED("excluded"),
    ;

    companion object {
        fun fromKey(key: String?): SpreadRule? = entries.firstOrNull { it.key == key }
    }
}

data class Territory(
    val code: TerritoryCode,
    val name: String,
    /** « du Rhône », « de la Réunion », « des Bouches-du-Rhône » (TNCC of the COG). */
    val ofName: String,
    /** Nearest first, ending with [TerritoryCode.FRANCE]. Empty for France. */
    val parents: List<TerritoryCode>,
    val population: Population?,
    val electorate: Electorate?,
    /** Departments and overseas collectivities only; null for the other types. */
    val spread: SpreadRule? = null,
    /** A collectivity whose code does not end with D, or a region whose powers it holds. */
    val specialStatus: Boolean = false,
    /** Regions only: the collectivity that holds its powers (`ctcd:20R` for Corse), null when it has its own regional council. */
    val heldBy: TerritoryCode? = null,
    /** EPCI and collectivities: the departments they cover (an EPCI may cover two); departments: their collectivities. */
    val departements: List<TerritoryCode> = emptyList(),
    /** Departments: their departmental collectivities (`ctcd:69D`, `ctcd:69M`). */
    val collectivites: List<TerritoryCode> = emptyList(),
    /** EPCI: all their communes (also those of another department). */
    val members: List<TerritoryCode> = emptyList(),
    /** EPCI: `CC`, `CA`, `CU` or `METRO`. */
    val nature: String? = null,
    /** Communes: their postal codes (those of their districts for Paris, Lyon and Marseille). */
    val postalCodes: List<String> = emptyList(),
    /** Communes: the codes they replaced (mergers), to read older tags. */
    val formerCodes: List<String> = emptyList(),
    /** The package that holds the details: a department or overseas collectivity; null for the national level. */
    val packageCode: String? = null,
) {
    fun parent(type: TerritoryType): TerritoryCode? = parents.firstOrNull { it.type == type }
    val departement: TerritoryCode? get() = parent(TerritoryType.DEPARTEMENT)
    val region: TerritoryCode? get() = parent(TerritoryType.REGION)
    val epci: TerritoryCode? get() = parent(TerritoryType.EPCI)

    /** The departmental collectivity or the overseas collectivity of a commune. */
    val collectivite: TerritoryCode? get() = parent(TerritoryType.COLLECTIVITE) ?: parent(TerritoryType.OUTRE_MER)
}

/** One package announced by the national level. */
data class TerritoryPackageEntry(
    val code: String,
    val name: String,
    /** Lowercase hex SHA-256 of the exact bytes (UTF-8 JSON, not compressed). */
    val sha256: String,
    val bytes: Long,
    val postalCodes: List<String>,
)

/** The national level: always on the device (copy shipped with the application, then the newest copy checked from the server). */
data class NationalTerritories(
    val format: Int,
    /** True for the example set ([EXEMPLE]): real public figures for a few departments only. */
    val example: Boolean,
    /** True for a set that holds only a few department packages. */
    val partial: Boolean,
    /** Day of the geography (COG): 2026-01-01. */
    val geography: CivilDate?,
    val builtOn: CivilDate?,
    val sources: List<TerritorySource>,
    val france: Territory,
    /** What the published figures of [france] cover (they differ). */
    val francePopulationPerimeter: String?,
    val franceElectoratePerimeter: String?,
    val regions: List<Territory>,
    val departements: List<Territory>,
    val collectivites: List<Territory>,
    val outreMer: List<Territory>,
    val packages: List<TerritoryPackageEntry>,
) {
    private val byCode: Map<TerritoryCode, Territory> by lazy {
        (listOf(france) + regions + departements + collectivites + outreMer).associateBy { it.code }
    }

    /** Every territory of the national level. */
    val all: List<Territory> get() = listOf(france) + regions + departements + collectivites + outreMer

    fun find(code: TerritoryCode): Territory? = byCode[code]

    fun entry(code: String): TerritoryPackageEntry? = packages.firstOrNull { it.code == code }

    /** Departments and overseas collectivities whose residents count for the 30 ([SpreadRule.COUNTS]; with [includeToConfirm], also [SpreadRule.TO_CONFIRM]). */
    fun spreadUnits(includeToConfirm: Boolean = false): List<Territory> =
        (departements + outreMer).filter { it.spread == SpreadRule.COUNTS || (includeToConfirm && it.spread == SpreadRule.TO_CONFIRM) }

    /** The packages covering [postalCode] (several when it straddles a border), found without sending it. */
    fun packagesFor(postalCode: String): List<TerritoryPackageEntry> {
        val code = TerritoiresReader.normalizePostalCode(postalCode) ?: return emptyList()
        return packages.filter { code in it.postalCodes }
    }
}

/** The details of a department or overseas collectivity. */
data class TerritoryPackage(
    val format: Int,
    val example: Boolean,
    val code: String,
    val name: String,
    val geography: CivilDate?,
    val communes: List<Territory>,
    val arrondissements: List<Territory>,
    val epcis: List<Territory>,
) {
    private val byCode: Map<TerritoryCode, Territory> by lazy { (communes + arrondissements + epcis).associateBy { it.code } }
    private val byFormer: Map<String, Territory> by lazy { communes.flatMap { c -> c.formerCodes.map { it to c } }.toMap() }

    fun find(code: TerritoryCode): Territory? =
        byCode[code] ?: if (code.type == TerritoryType.COMMUNE) byFormer[code.code] else null
}

/** One line of the index of the names of every commune. */
data class CommuneName(val code: String, val name: String, val packageCode: String)

data class CommunesIndex(val format: Int, val example: Boolean, val geography: CivilDate?, val communes: List<CommuneName>)

/**
 * Reads the documents of the referential, strictly and tolerantly: the document itself must be of
 * format 1 with its expected fields, otherwise it is ignored (null, never an exception); inside it,
 * an entry that cannot be read is left out and the others are kept. Unknown fields are ignored.
 */
object TerritoiresReader {
    const val FORMAT: Int = 1

    /** The code of the index of the names of every commune. */
    const val COMMUNES_CODE: String = "communes"

    /** The code under which the device keeps the national level. */
    const val NATIONAL_CODE: String = "national"

    const val MAX_NATIONAL_BYTES: Long = 2L * 1024 * 1024
    const val MAX_PACKAGE_BYTES: Long = 8L * 1024 * 1024

    private const val MAX_TEXT = 200
    private const val MAX_FIGURE = 200_000_000L

    private val PACKAGE_CODE = Regex("^([0-9]{2}|2[AB]|9[78][0-9])$")
    private val POSTAL_CODE = Regex("^[0-9]{5}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    private val json = Json { ignoreUnknownKeys = true }

    /** A department or overseas collectivity package code, or [COMMUNES_CODE]. */
    fun isPackageCode(code: String): Boolean = code == COMMUNES_CODE || PACKAGE_CODE.matches(code)

    /** An overseas collectivity package (975, 977, 978, 98x), not a department (971 to 976 except 975). */
    fun isOutreMer(code: String): Boolean = code in setOf("975", "977", "978") || (code.length == 3 && code.startsWith("98"))

    /** "69 001" or " 69001 " as "69001"; null when it is not five digits. */
    fun normalizePostalCode(text: String): String? = text.filterNot { it.isWhitespace() }.takeIf { POSTAL_CODE.matches(it) }

    fun readNational(text: String): NationalTerritories? = runCatching {
        val root = parse(text) ?: return null
        if (root.int("format") != FORMAT) return null
        val sources = sources(root)
        val francePart = root["france"] as? JsonObject ?: return null
        val france = Territory(
            code = TerritoryCode.FRANCE,
            name = francePart.text("name") ?: "France",
            ofName = "de la France",
            parents = emptyList(),
            population = population(francePart, sources),
            electorate = electorate(francePart, sources),
        )
        val fr = listOf(TerritoryCode.FRANCE)
        val regions = root.objects("regions").mapNotNull { entry ->
            val code = entry.text("code")?.let(TerritoryCode::region) ?: return@mapNotNull null
            val heldBy = entry.text("heldBy")?.let(TerritoryCode::collectivite)
            territory(entry, code, fr, sources)?.copy(heldBy = heldBy, specialStatus = heldBy != null)
        }.distinctBy { it.code }
        val departements = root.objects("departements").mapNotNull { entry ->
            val code = entry.text("code")?.let(TerritoryCode::departement) ?: return@mapNotNull null
            val region = entry.text("region")?.let(TerritoryCode::region)
            territory(entry, code, listOfNotNull(region) + fr, sources)?.copy(
                spread = SpreadRule.fromKey(entry.text("spread")),
                collectivites = entry.texts("collectivites").mapNotNull(TerritoryCode::collectivite),
                packageCode = code.code,
            )
        }.distinctBy { it.code }
        val collectivites = root.objects("collectivites").mapNotNull { entry ->
            val code = entry.text("code")?.let(TerritoryCode::collectivite) ?: return@mapNotNull null
            val region = entry.text("region")?.let(TerritoryCode::region)
            territory(entry, code, listOfNotNull(region) + fr, sources)?.copy(
                specialStatus = entry.boolean("special") ?: !code.code.endsWith("D"),
                departements = entry.texts("departements").mapNotNull(TerritoryCode::departement),
            )
        }.distinctBy { it.code }
        val outreMer = root.objects("outreMer").mapNotNull { entry ->
            val code = entry.text("code")?.let(TerritoryCode::outreMer) ?: return@mapNotNull null
            territory(entry, code, fr, sources)?.copy(spread = SpreadRule.fromKey(entry.text("spread")), packageCode = code.code)
        }.distinctBy { it.code }
        val packages = root.objects("packages").mapNotNull { entry ->
            val code = entry.text("code")?.takeIf(::isPackageCode) ?: return@mapNotNull null
            val sha = entry.text("sha256")?.lowercase()?.takeIf { SHA256.matches(it) } ?: return@mapNotNull null
            val bytes = entry.long("bytes")?.takeIf { it in 1..MAX_PACKAGE_BYTES } ?: return@mapNotNull null
            TerritoryPackageEntry(code, entry.text("name") ?: code, sha, bytes, entry.texts("postalCodes").filter { POSTAL_CODE.matches(it) }.distinct())
        }.distinctBy { it.code }
        NationalTerritories(
            format = FORMAT,
            example = root.boolean("example") ?: false,
            partial = root.boolean("partial") ?: false,
            geography = CivilDate.parseIso(root.text("geography")),
            builtOn = CivilDate.parseIso(root.text("builtOn")),
            sources = sources,
            france = france,
            francePopulationPerimeter = francePart.text("populationPerimeter"),
            franceElectoratePerimeter = francePart.text("inscritsPerimeter"),
            regions = regions,
            departements = departements,
            collectivites = collectivites,
            outreMer = outreMer,
            packages = packages,
        )
    }.getOrNull()

    fun readPackage(text: String): TerritoryPackage? = runCatching {
        val root = parse(text) ?: return null
        if (root.int("format") != FORMAT) return null
        val code = root.text("code")?.takeIf { it != COMMUNES_CODE && isPackageCode(it) } ?: return null
        val name = root.text("name") ?: return null
        val sources = sources(root)
        val outreMer = root.boolean("outreMer") ?: isOutreMer(code)
        val region = root.text("region")?.let(TerritoryCode::region)
        val area = if (outreMer) TerritoryCode.outreMer(code) else TerritoryCode.departement(code)
        val above = listOfNotNull(if (outreMer) null else area, region, TerritoryCode.FRANCE)
        val communes = root.objects("communes").mapNotNull { entry ->
            val commune = entry.text("code")?.let(TerritoryCode::commune) ?: return@mapNotNull null
            val epci = entry.text("epci")?.let(TerritoryCode::epci)
            val collectivite = entry.text("collectivite")?.let { if (outreMer) TerritoryCode.outreMer(it) else TerritoryCode.collectivite(it) }
            territory(entry, commune, listOfNotNull(epci, collectivite) + above, sources)?.copy(
                postalCodes = entry.texts("postalCodes").filter { POSTAL_CODE.matches(it) }.distinct(),
                formerCodes = entry.texts("formerCodes").filter { TerritoryCode.commune(it) != null }.distinct(),
                packageCode = code,
            )
        }.distinctBy { it.code }
        val communesByCode = communes.associateBy { it.code.code }
        val arrondissements = root.objects("arrondissements").mapNotNull { entry ->
            val arm = entry.text("code")?.let(TerritoryCode::arrondissement) ?: return@mapNotNull null
            val parent = communesByCode[entry.text("commune")] ?: return@mapNotNull null
            territory(entry, arm, listOf(parent.code) + parent.parents, sources)?.copy(postalCodes = entry.texts("postalCodes").filter { POSTAL_CODE.matches(it) }, packageCode = code)
        }.distinctBy { it.code }
        val epcis = root.objects("epcis").mapNotNull { entry ->
            val epci = entry.text("code")?.let(TerritoryCode::epci) ?: return@mapNotNull null
            territory(entry, epci, listOf(TerritoryCode.FRANCE), sources)?.copy(
                nature = entry.text("nature"),
                departements = entry.texts("departements").mapNotNull(TerritoryCode::departement),
                members = entry.texts("communes").mapNotNull(TerritoryCode::commune),
                packageCode = code,
            )
        }.distinctBy { it.code }
        TerritoryPackage(FORMAT, root.boolean("example") ?: false, code, name, CivilDate.parseIso(root.text("geography")), communes, arrondissements, epcis)
    }.getOrNull()

    fun readCommunesIndex(text: String): CommunesIndex? = runCatching {
        val root = parse(text) ?: return null
        if (root.int("format") != FORMAT || root.text("code") != COMMUNES_CODE) return null
        val communes = (root["communes"] as? JsonArray).orEmpty().mapNotNull { element ->
            val row = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() } ?: return@mapNotNull null
            if (row.size < 3) return@mapNotNull null
            val code = row[0]?.takeIf { TerritoryCode.commune(it) != null } ?: return@mapNotNull null
            val name = row[1]?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT } ?: return@mapNotNull null
            val pkg = row[2]?.takeIf { PACKAGE_CODE.matches(it) } ?: return@mapNotNull null
            CommuneName(code, name, pkg)
        }.distinctBy { it.code }
        CommunesIndex(FORMAT, root.boolean("example") ?: false, CivilDate.parseIso(root.text("geography")), communes)
    }.getOrNull()

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text.removePrefix("﻿")) }.getOrNull() as? JsonObject

    private fun sources(root: JsonObject): List<TerritorySource> = root.objects("sources").mapNotNull { entry ->
        TerritorySource(
            id = entry.text("id") ?: return@mapNotNull null,
            kind = SourceKind.fromKey(entry.text("kind")) ?: return@mapNotNull null,
            name = entry.text("name") ?: return@mapNotNull null,
            url = entry.text("url")?.takeIf { it.startsWith("https://") },
            licence = entry.text("licence"),
            vintage = entry.int("vintage"),
            date = CivilDate.parseIso(entry.text("date")),
        )
    }.distinctBy { it.id }

    /** A territory with its name and figures; null without a name. */
    private fun territory(entry: JsonObject, code: TerritoryCode, parents: List<TerritoryCode>, sources: List<TerritorySource>): Territory? {
        val name = entry.text("name") ?: return null
        return Territory(
            code = code,
            name = name,
            ofName = entry.text("of") ?: "de $name",
            parents = parents,
            population = population(entry, sources),
            electorate = electorate(entry, sources),
        )
    }

    private fun source(entry: JsonObject, kind: SourceKind, sources: List<TerritorySource>): TerritorySource? {
        val id = (entry["sources"] as? JsonObject)?.text(kind.key)
        return (if (id != null) sources.firstOrNull { it.id == id } else null) ?: sources.firstOrNull { it.kind == kind }
    }

    private fun population(entry: JsonObject, sources: List<TerritorySource>): Population? {
        val municipale = entry.figure("population") ?: return null
        val computed = "population" in entry.texts("computed")
        return Population(municipale, if (computed) null else entry.figure("populationTotale"), source(entry, SourceKind.POPULATION, sources), computed)
    }

    private fun electorate(entry: JsonObject, sources: List<TerritorySource>): Electorate? {
        val principale = entry.figure("inscrits") ?: return null
        return Electorate(principale, entry.figure("inscritsComplementaires"), source(entry, SourceKind.ELECTORATE, sources), "inscrits" in entry.texts("computed"))
    }

    /** A non-negative whole number below 200 million; null for anything else. */
    private fun JsonObject.figure(key: String): Long? = long(key)?.takeIf { it in 0..MAX_FIGURE }

    /** A non-blank string of at most 200 characters, trimmed; null for anything else (numbers included). */
    private fun JsonObject.text(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (value is JsonNull || !value.isString) return null
        return value.content.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
    }

    private fun JsonObject.texts(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content?.trim()?.takeIf { value -> value.isNotEmpty() } }

    private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}

/** The fingerprint of a document: the SHA-256 of its exact bytes, the one the national level announces. */
object TerritoiresDigest {
    fun sha256Hex(bytes: ByteArray): String = Crypto.sha256(bytes).toHexString()

    fun matches(bytes: ByteArray, expected: String): Boolean = sha256Hex(bytes) == expected.trim().lowercase()
}
