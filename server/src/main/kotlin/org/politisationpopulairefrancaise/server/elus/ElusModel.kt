package org.politisationpopulairefrancaise.server.elus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The mandates of the directory, in the order of the packages (contract, section 1.4). [code] is
 * the value of `mandate` in the JSON; [territory] says what `territory` holds.
 */
enum class Mandate(val code: String) {
    /** Member of a municipal council, mayor and deputies included; territory: INSEE code of the commune. */
    MUNICIPAL("municipal"),

    /** Member of the council of an intercommunality; territory: its SIREN; `commune`: the commune represented. */
    COMMUNAUTAIRE("communautaire"),

    /** Member of a departmental council; territory: code of the canton. */
    DEPARTEMENTAL("departemental"),

    /** Member of a regional council; territory: code of the region. */
    REGIONAL("regional"),

    /** Member of the assembly of a collectivity with a special status (69M, 94, 972...); territory: its code. */
    COLLECTIVITE("collectivite"),

    /** Member of the National Assembly; territory: code of the constituency. */
    DEPUTE("depute"),

    /** Member of the Senate; territory: code of the department or collectivity. */
    SENATEUR("senateur"),

    /** Member of the European Parliament elected in France; territory: `FR`. */
    EUROPEEN("europeen"),
}

/** Version of the JSON format of the index and of the packages. */
const val ELUS_FORMAT: Int = 1

/** Code of the national package: members of the European Parliament elected in France. */
const val NATIONAL_PACKAGE: String = "FR"

/**
 * The encoding of the index and of the packages: compact, fields in the order of the classes, `null`
 * written where the contract shows it (`function`, `functionSince`, `epci`, `region`), `commune`
 * left out when there is none. Never pretty-printed: the same data must give the same bytes.
 */
val ElusJson: Json = Json {
    encodeDefaults = false
    explicitNulls = true
    prettyPrint = false
}

/** GET /api/v1/elus. */
@Serializable
data class ElusIndex(
    val format: Int,
    val example: Boolean,
    val source: ElusSourceInfo,
    val importedOn: String,
    val packages: List<ElusPackageEntry>,
)

@Serializable
data class ElusSourceInfo(
    val name: String,
    val url: String,
    val licence: String,
    val updatedOn: String,
)

@Serializable
data class ElusPackageEntry(
    val code: String,
    val name: String,
    val sha256: String,
    val bytes: Long,
    val postalCodes: List<String>,
)

/** GET /api/v1/elus/{code}. */
@Serializable
data class ElusPackage(
    val format: Int,
    val example: Boolean,
    val code: String,
    val name: String,
    val region: ElusRegion?,
    val sourceUpdatedOn: String,
    val communes: List<ElusCommune>,
    val epcis: List<ElusNamed>,
    val cantons: List<ElusNamed>,
    val circonscriptions: List<ElusNamed>,
    val officials: List<ElusOfficial>,
    /** Collectivities with a special status whose assembly is the departmental level of some communes (D-047). */
    val collectivites: List<ElusNamed> = emptyList(),
)

@Serializable
data class ElusRegion(val code: String, val name: String)

@Serializable
data class ElusNamed(val code: String, val name: String)

@Serializable
data class ElusCommune(
    val code: String,
    val name: String,
    val postalCodes: List<String>,
    val epci: String?,
    val cantons: List<String>,
    val circonscriptions: List<String>,
    /**
     * The collectivity whose assembly takes the place of the departmental council for this commune
     * (`69M` Métropole de Lyon, `94` Corse, `972` Martinique, `973` Guyane, or an overseas
     * collectivity), null for a commune of an ordinary department.
     */
    val collectivite: String? = null,
)

/**
 * One elected official, with only what the contract keeps (section 1.2): never a date of birth,
 * sex, occupation or nationality, which the importer does not even read.
 */
@Serializable
data class ElusOfficial(
    val mandate: String,
    val territory: String,
    val commune: String? = null,
    val name: String,
    val function: String?,
    val since: String,
    val functionSince: String?,
)

/** The source announced by the index (the RNE); the other sources are listed in docs/integration/elus-serveur.md. */
object ElusSourceDescription {
    const val NAME: String = "Répertoire national des élus (ministère de l'Intérieur)"
    const val URL: String = "https://www.data.gouv.fr/fr/datasets/5c34c4d1634f4173183a64f1/"
    const val LICENCE: String = "Licence Ouverte 2.0"
}
