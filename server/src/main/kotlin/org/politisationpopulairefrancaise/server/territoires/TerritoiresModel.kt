package org.politisationpopulairefrancaise.server.territoires

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The documents of the referential of territories (D-090, docs/FORMATS.md, section 4 nonies):
 * the national level (GET /api/v1/territoires), the package of a department or overseas
 * collectivity (GET /api/v1/territoires/{code}) and the index of the names of the communes
 * (GET /api/v1/territoires/communes). Public statistics only: nothing about a person.
 */

const val TERRITOIRES_FORMAT: Int = 1

/** The code of the index of the names of every commune. */
const val COMMUNES_PACKAGE: String = "communes"

/**
 * Compact, fields in the order of the classes, every list written, an unknown value (null) left
 * out. Never pretty-printed: the same data give the same bytes.
 */
val TerritoiresJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

@Serializable
data class SourceJson(
    val id: String,
    val kind: String,
    val name: String,
    val url: String? = null,
    val licence: String? = null,
    val vintage: Int? = null,
    val date: String? = null,
)

/** Which source an entry's figure comes from, when it is not the first source of its kind. */
@Serializable
data class SourceOverride(val population: String? = null, val electorate: String? = null)

@Serializable
data class FranceJson(
    val name: String,
    val population: Long? = null,
    val populationTotale: Long? = null,
    val populationPerimeter: String? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val inscritsPerimeter: String? = null,
)

@Serializable
data class RegionJson(
    val code: String,
    val name: String,
    val of: String,
    val population: Long? = null,
    val populationTotale: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val heldBy: String? = null,
    val sources: SourceOverride? = null,
)

@Serializable
data class DepartementJson(
    val code: String,
    val name: String,
    val of: String,
    val region: String? = null,
    val collectivites: List<String> = emptyList(),
    val population: Long? = null,
    val populationTotale: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val spread: String,
    val sources: SourceOverride? = null,
)

@Serializable
data class CollectiviteJson(
    val code: String,
    val name: String,
    val of: String,
    val special: Boolean,
    val region: String? = null,
    val departements: List<String> = emptyList(),
    val population: Long? = null,
    val populationTotale: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val computed: List<String> = emptyList(),
)

@Serializable
data class OutreMerJson(
    val code: String,
    val name: String,
    val of: String,
    val population: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val spread: String,
    val sources: SourceOverride? = null,
)

@Serializable
data class PackageEntryJson(
    val code: String,
    val name: String,
    val sha256: String,
    val bytes: Long,
    val postalCodes: List<String>,
)

@Serializable
data class NationalJson(
    val format: Int,
    val example: Boolean,
    val partial: Boolean,
    val geography: String,
    val builtOn: String,
    val sources: List<SourceJson>,
    val france: FranceJson,
    val regions: List<RegionJson>,
    val departements: List<DepartementJson>,
    val collectivites: List<CollectiviteJson>,
    val outreMer: List<OutreMerJson>,
    val packages: List<PackageEntryJson>,
)

@Serializable
data class CommuneJson(
    val code: String,
    val name: String,
    val of: String,
    val postalCodes: List<String>,
    val epci: String? = null,
    val collectivite: String? = null,
    val population: Long? = null,
    val populationTotale: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val computed: List<String> = emptyList(),
    val sources: SourceOverride? = null,
    val formerCodes: List<String> = emptyList(),
)

@Serializable
data class ArrondissementJson(
    val code: String,
    val name: String,
    val of: String,
    val commune: String,
    val postalCodes: List<String>,
    val population: Long? = null,
    val populationTotale: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
)

@Serializable
data class EpciJson(
    val code: String,
    val name: String,
    val of: String,
    val nature: String,
    val departements: List<String>,
    val communes: List<String>,
    val population: Long? = null,
    val inscrits: Long? = null,
    val inscritsComplementaires: Long? = null,
    val computed: List<String> = emptyList(),
)

@Serializable
data class PackageJson(
    val format: Int,
    val example: Boolean,
    val code: String,
    val name: String,
    val outreMer: Boolean,
    val region: String? = null,
    val geography: String,
    val sources: List<SourceJson>,
    val communes: List<CommuneJson>,
    val arrondissements: List<ArrondissementJson>,
    val epcis: List<EpciJson>,
)

@Serializable
data class CommunesIndexJson(
    val format: Int,
    val example: Boolean,
    val code: String,
    val geography: String,
    val communes: List<List<String>>,
)
