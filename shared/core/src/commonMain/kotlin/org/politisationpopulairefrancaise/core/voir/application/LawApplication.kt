package org.politisationpopulairefrancaise.core.voir.application

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.text.FrenchText

/*
 * « Applicomètre » (VO-36; docs/integration/voir-applicometre.md): for each law, the measures of application
 * it expects (decrees, orders), those published, those still awaited and the delays, from the data of the
 * baromètre de l'application des lois of the Assemblée nationale (LexImpact, data.gouv.fr, Licence Ouverte 2.0),
 * itself made of the schedules (échéanciers) of Légifrance. One package per legislature, the same for everyone.
 */

/** The state of a measure, as written by the source (« appliqué », « sans objet », « en attente d'application »). */
@Serializable
enum class MeasureState {
    @SerialName("applique") APPLIED,
    @SerialName("sans-objet") NOT_NEEDED,
    @SerialName("en-attente") PENDING,
    ;

    companion object {
        fun of(label: String): MeasureState? = when (FrenchText.fold(label)) {
            "applique", "appliquee" -> APPLIED
            "sans objet" -> NOT_NEEDED
            "en attente d application", "en attente" -> PENDING
            else -> null
        }
    }
}

/** One measure of application of a law: one line of the schedule of Légifrance. */
@Serializable
data class ApplicationMeasure(
    /** Its order in the schedule. */
    val order: Int,
    /** The articles of the law it applies (« Article 53, I »). */
    val articles: String? = null,
    /** The provision changed by the article (« Article 15-3, code de procédure pénale »). */
    val legalBasis: String? = null,
    /** What the measure is about, as written in the schedule. */
    val subject: String? = null,
    /** The observations of the DILA: the text published, or the announced date (« Publication envisagée en mars 2027 »). */
    val observation: String? = null,
    /** The Légifrance identifier of the text published (`JORFTEXT…`). */
    val decree: String? = null,
    val state: MeasureState,
    /** Days between the law and the text published, computed by LexImpact. */
    val delayDays: Int? = null,
    /** The month announced by the government for a measure awaited, `AAAA-MM`, read from [observation] or the planned date. */
    val plannedMonth: String? = null,
)

/** One law promulgated, with its measures of application. */
@Serializable
data class LawApplication(
    /** The Légifrance identifier of the law, `JORFTEXT…`: the key of the source. */
    val id: String,
    val title: String,
    val publishedOn: String,
    val legislature: Int,
    /** The legislative file of the Assemblée, `an-dossier:<uid>` (since 2012), the same as on the ballots and on the bills of "Voir". */
    val dossier: String? = null,
    /** The path of the file in the address of its page on the site of the Assemblée. */
    val dossierPath: String? = null,
    /** The committee of the Assemblée in charge of the text. */
    val committee: String? = null,
    /** Directly applicable (no measure expected). */
    val directApplication: Boolean = false,
    /** Authorises the ratification of an international agreement. */
    val ratification: Boolean = false,
    /** Empowers the government to legislate by ordinances. */
    val enabling: Boolean = false,
    /** Ratifies ordinances. */
    val ordinanceRatification: Boolean = false,
    /** Légifrance publishes no schedule for it. */
    val withoutSchedule: Boolean = false,
    /** The official figures of the source: measures to take, measures taken. */
    val toApply: Int = 0,
    val applied: Int = 0,
    val measures: List<ApplicationMeasure> = emptyList(),
) {
    val legifranceUrl: String get() = ApplicationFormat.legifranceUrl(id)
    val dossierUrl: String? get() = dossierPath?.let { ApplicationFormat.dossierUrl(legislature, it) }

    /** The awaited measures. */
    val pending: List<ApplicationMeasure> get() = measures.filter { it.state == MeasureState.PENDING }

    /** The share of the measures taken, rounded down (never 100 % while one is awaited); null when none is expected. */
    fun percent(): Int? = if (toApply <= 0) null else (applied.coerceIn(0, toApply) * 100) / toApply
}

/** The package `application-lois-<législature>`. */
@Serializable
data class ApplicationPackage(
    val format: Int = ApplicationFormat.FORMAT,
    val example: Boolean,
    val legislature: Int,
    /** The day of the import. */
    val updatedOn: String? = null,
    /** Newest first. */
    val laws: List<LawApplication>,
)

object ApplicationFormat {
    const val FORMAT: Int = 1

    /** The legislatures imported: the current one and the two before (the presidential terms since 2017). */
    val LEGISLATURES: List<Int> = listOf(17, 16, 15)

    const val PACKAGE_PREFIX: String = "application-lois-"
    fun packageName(legislature: Int): String = "$PACKAGE_PREFIX$legislature"

    /** The identifier of the source in the index of the open data. */
    const val SOURCE_ID: String = "an-barometre"

    private val LEGIFRANCE_ID = Regex("^JORFTEXT[0-9]{12}$")
    private val DOSSIER_PATH = Regex("^[A-Za-z0-9_-]{1,160}$")

    fun isLegifranceId(text: String?): Boolean = text != null && LEGIFRANCE_ID.matches(text)

    fun legifranceUrl(id: String): String = "https://www.legifrance.gouv.fr/jorf/id/$id"

    fun dossierUrl(legislature: Int, path: String): String? =
        if (DOSSIER_PATH.matches(path)) "https://www.assemblee-nationale.fr/dyn/$legislature/dossiers/$path" else null

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    fun read(text: String): ApplicationPackage? = runCatching { json.decodeFromString(ApplicationPackage.serializer(), text) }.getOrNull()
        ?.takeIf { it.format == FORMAT }

    fun write(pkg: ApplicationPackage): String = json.encodeToString(ApplicationPackage.serializer(), pkg)

    private val MONTHS = listOf("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")
    private val ANNOUNCED = Regex("\\b(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre) ((?:19|20)[0-9]{2})\\b")
    private val ISO_DAY = Regex("^((?:19|20)[0-9]{2})-([0-9]{2})-[0-9]{2}")
    private val FRENCH_DAY = Regex("^[0-9]{1,2}/([0-9]{1,2})/((?:19|20)[0-9]{2})$")

    /**
     * The month announced for an awaited measure, `AAAA-MM`: the planned date when the source gives one, otherwise
     * « Publication envisagée en mars 2027 » in the observations. Null for anything else (a semester, a vague
     * wording): the observation is then shown as written, never guessed.
     */
    fun plannedMonth(plannedDate: String?, observation: String?): String? {
        plannedDate?.trim()?.takeIf { it.isNotEmpty() }?.let { date ->
            ISO_DAY.find(date)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}" }
            FRENCH_DAY.find(date)?.let { return "${it.groupValues[2]}-${it.groupValues[1].padStart(2, '0')}" }
        }
        val folded = observation?.let(FrenchText::fold) ?: return null
        if ("envisagee" !in folded && "prevue" !in folded) return null
        val match = ANNOUNCED.find(folded) ?: return null
        val month = MONTHS.indexOf(match.groupValues[1]) + 1
        return "${match.groupValues[2]}-${month.toString().padStart(2, '0')}"
    }
}
