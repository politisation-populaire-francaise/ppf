package org.politisationpopulairefrancaise.core.voir.parrainages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.text.FrenchText

/**
 * The mandate under which an official presented a candidate, reduced to a neutral code: the official label
 * says « Sénateur » or « Sénatrice », the package keeps neither (minimisation, like the civility it never keeps).
 */
@Serializable
enum class SponsorMandate {
    @SerialName("maire") MAIRE,
    @SerialName("maire-delegue") MAIRE_DELEGUE,
    @SerialName("maire-arrondissement") MAIRE_ARRONDISSEMENT,
    @SerialName("depute") DEPUTE,
    @SerialName("senateur") SENATEUR,
    @SerialName("depute-europeen") DEPUTE_EUROPEEN,
    @SerialName("conseiller-departemental") CONSEILLER_DEPARTEMENTAL,
    @SerialName("conseiller-regional") CONSEILLER_REGIONAL,
    @SerialName("conseiller-paris") CONSEILLER_PARIS,
    @SerialName("conseiller-metropolitain-lyon") CONSEILLER_METROPOLITAIN_LYON,
    @SerialName("conseiller-corse") CONSEILLER_CORSE,
    @SerialName("conseiller-afe") CONSEILLER_AFE,
    @SerialName("president-intercommunalite") PRESIDENT_INTERCOMMUNALITE,
    @SerialName("autre") AUTRE,
    ;

    companion object {
        /**
         * The code of an official label of the Conseil constitutionnel (« Maire », « Députée », « Conseillère
         * départementale », « Représentante française au Parlement européen »…), whatever its gender.
         */
        fun of(label: String): SponsorMandate {
            val folded = FrenchText.fold(label)
            return when {
                folded.startsWith("maire delegue") -> MAIRE_DELEGUE
                folded.startsWith("maire d arrondissement") || folded.startsWith("maire de secteur") -> MAIRE_ARRONDISSEMENT
                folded == "maire" -> MAIRE
                folded.startsWith("depute") -> DEPUTE
                folded.startsWith("senat") -> SENATEUR
                "parlement europeen" in folded -> DEPUTE_EUROPEEN
                folded.startsWith("conseiller") && "departemental" in folded -> CONSEILLER_DEPARTEMENTAL
                folded.startsWith("conseiller") && "regional" in folded -> CONSEILLER_REGIONAL
                folded.startsWith("conseiller") && "paris" in folded -> CONSEILLER_PARIS
                folded.startsWith("conseiller") && "metropolitain" in folded -> CONSEILLER_METROPOLITAIN_LYON
                folded.startsWith("conseiller") && ("corse" in folded || "assemblee de corse" in folded) -> CONSEILLER_CORSE
                "francais de l etranger" in folded || "francais etablis hors de france" in folded -> CONSEILLER_AFE
                folded.startsWith("president") && ("communaute" in folded || "metropole" in folded || "intercommunal" in folded || "epci" in folded) -> PRESIDENT_INTERCOMMUNALITE
                else -> AUTRE
            }
        }
    }
}

/** One presentation, as the Conseil constitutionnel published it, without the civility. */
@Serializable
data class Sponsorship(
    val lastName: String,
    val firstName: String,
    val mandate: SponsorMandate,
    /** The official label, kept only for a mandate the code does not know ([SponsorMandate.AUTRE]). */
    val otherMandate: String? = null,
    /** « Circonscription » as published: the commune of a mayor, « 5ème circonscription », the region…; null when empty. */
    val territory: String? = null,
    /** « Département » as published (« Rhône », « Parlement européen »…). */
    val department: String,
    /** The code of the department or collectivity when its name is known (« 69 », « 2A », « 971 »). */
    val departmentCode: String? = null,
    /** The candidate as published (« NOM Prénom »). */
    val candidate: String,
    /** The day of the publication by the Conseil constitutionnel, AAAA-MM-JJ. */
    val publishedOn: String,
) {
    /** « Prénom NOM », as the page shows it. */
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

/** The package `parrainages-2027` of the open data (docs/integration/voir-parrainages.md, section 2). */
@Serializable
data class SponsorshipsPackage(
    val format: Int = SponsorshipsFormat.FORMAT,
    val example: Boolean,
    val election: String,
    /** The latest publication of the Conseil constitutionnel it holds, AAAA-MM-JJ. */
    val publishedOn: String? = null,
    val sponsorships: List<Sponsorship>,
)

object SponsorshipsFormat {
    const val FORMAT: Int = 1

    /** The election of April 2027 and its package. */
    const val ELECTION_2027: String = "presidentielle-2027"
    const val PACKAGE_2027: String = "parrainages-2027"

    /** The rule of the law, shown beside the figures (loi n° 62-1292, article 3). */
    const val REQUIRED: Int = 500
    const val MIN_DEPARTMENTS: Int = 30

    /** The identifier of the source in the index of the open data. */
    const val SOURCE_ID: String = "cc"

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun read(text: String): SponsorshipsPackage? = runCatching { json.decodeFromString(SponsorshipsPackage.serializer(), text) }.getOrNull()
        ?.takeIf { it.format == FORMAT }

    fun write(pkg: SponsorshipsPackage): String = json.encodeToString(SponsorshipsPackage.serializer(), pkg)

    /** The stable order of the package: candidate, department, last name, first name. */
    val ORDER: Comparator<Sponsorship> = compareBy<Sponsorship>({ FrenchText.fold(it.candidate) }, { FrenchText.fold(it.department) }, { FrenchText.fold(it.lastName) }, { FrenchText.fold(it.firstName) }, { it.publishedOn })
}

/** The same figures for every candidate (règle n° 9): never a verdict, never a rank. */
data class CandidateSponsors(
    val candidate: String,
    val count: Int,
    /** Distinct « Département » values of the published list (departments, collectivities, « Parlement européen »…). */
    val territories: Int,
    /** The most presentations from one of them. */
    val mostInOneTerritory: Int,
    val firstPublishedOn: String?,
    val lastPublishedOn: String?,
)

/**
 * The published presentations on the device: by candidate, in the alphabetical order of the candidates, and
 * by department. Nothing here judges a candidacy: only the Conseil constitutionnel draws up the list.
 */
class SponsorshipsCatalog(val pkg: SponsorshipsPackage) {
    private val byCandidate: Map<String, List<Sponsorship>> = pkg.sponsorships.groupBy { it.candidate }

    val example: Boolean get() = pkg.example || pkg.sponsorships.any { it.lastName.startsWith(EXAMPLE_MARKER) || it.candidate.startsWith(EXAMPLE_MARKER) }

    /** Every candidate with presentations, alphabetically (their name as published, without the [EXEMPLE] marker). */
    fun candidates(): List<CandidateSponsors> = byCandidate.map { (candidate, list) ->
        val perTerritory = list.groupingBy { FrenchText.fold(it.department) }.eachCount()
        val dates = list.map { it.publishedOn }.sorted()
        CandidateSponsors(candidate, list.size, perTerritory.size, perTerritory.values.maxOrNull() ?: 0, dates.firstOrNull(), dates.lastOrNull())
    }.sortedBy { sortKey(it.candidate) }

    /** The presentations of [candidate], by department then name. */
    fun of(candidate: String): List<Sponsorship> = byCandidate[candidate].orEmpty().sortedWith(SponsorshipsFormat.ORDER)

    /** The departments of the list, alphabetically, with their code when known. */
    fun departments(): List<Pair<String, String?>> = pkg.sponsorships.map { it.department to it.departmentCode }.distinctBy { FrenchText.fold(it.first) }.sortedBy { FrenchText.fold(it.first) }

    /** The presentations made in [department] (its published name), by candidate then name. */
    fun inDepartment(department: String): List<Sponsorship> {
        val folded = FrenchText.fold(department)
        return pkg.sponsorships.filter { FrenchText.fold(it.department) == folded }.sortedWith(SponsorshipsFormat.ORDER)
    }

    val total: Int get() = pkg.sponsorships.size

    companion object {
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        fun sortKey(name: String): String = FrenchText.fold(name.removePrefix(EXAMPLE_MARKER))
    }
}
