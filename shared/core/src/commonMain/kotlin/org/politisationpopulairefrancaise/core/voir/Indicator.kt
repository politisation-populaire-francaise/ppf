package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.isWebAddress
import org.politisationpopulairefrancaise.core.voir.VoirTags.nextSteps
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * One value of an indicator (M2: "valeur, date, lieu, type de source, lien vers la source,
 * méthode, niveau de fiabilité"). Measures of the same [occasion] (a day of mobilisation, a
 * week...) given by different sources are shown side by side, never merged.
 */
data class Measure(
    /** Stable within its indicator, so that a correction replaces the right value. */
    val id: String,
    val date: CivilDate,
    val value: Double,
    val source: Source,
    val reliability: Reliability,
    /** What the value counts: "Journée du 18 septembre"; the date when absent. */
    val occasion: String?,
    val place: String?,
    /** The part named by the indicator's breakdown ("dont mineurs"), when known. */
    val part: Double?,
) {
    val occasionKey: String get() = occasion ?: date.toIso()
}

/** The values of one occasion, one per source, in the order of the sources' kinds. */
data class Reading(val occasion: String, val date: CivilDate, val place: String?, val measures: List<Measure>) {
    val low: Double get() = measures.minOf { it.value }
    val high: Double get() = measures.maxOf { it.value }

    /** At least two sources whose values differ by a quarter or more: the gap itself is information. */
    val diverges: Boolean get() = measures.size >= 2 && high > 0 && (high - low) / high >= DIVERGENCE

    companion object {
        const val DIVERGENCE: Double = 0.25
    }
}

/** The values of one kind of source over time, for the chart. */
data class Series(val kind: SourceKind, val points: List<Pair<CivilDate, Double>>)

/**
 * An indicator of the observatory (M2), created by the team without code: definition (summary),
 * unit, method (body, Markdown), accepted sources, measures. See docs/FORMATS.md.
 */
data class Indicator(
    val article: Article,
    val axis: VoirAxis,
    val unit: String,
    /** Order among the key figures; lower first, absent last. */
    val rank: Int?,
    /** Collected from the users' declarations (shadowban), always flagged as such. */
    val declarative: Boolean,
    /** The label of the part each measure may carry ("dont mineurs"). */
    val breakdown: String?,
    val acceptedSources: List<SourceKind>,
    val measures: List<Measure>,
    /** General sources of the method (other than each measure's own). */
    val sources: List<Source>,
    val corrections: List<Correction>,
    val nextSteps: List<NextStep>,
) {
    val address get() = article.address
    val title: String get() = article.title
    val definition: String? get() = article.summary
    val method: String get() = article.body

    /** The occasions, oldest first, each with its values side by side. */
    val readings: List<Reading> by lazy {
        measures.groupBy { it.occasionKey }.map { (key, values) ->
            val sorted = values.sortedWith(compareBy<Measure> { it.source.kind.ordinal }.thenByDescending { it.value })
            Reading(occasion = values.first().occasion ?: key, date = values.maxOf { it.date }, place = values.firstNotNullOfOrNull { it.place }, measures = sorted)
        }.sortedWith(compareBy<Reading> { it.date }.thenBy { it.occasion })
    }

    /** The most recent occasion, the one shown on the card. */
    val latest: Reading? get() = readings.lastOrNull()

    /** The previous occasion, to say whether the figure rises or falls. */
    val previous: Reading? get() = readings.getOrNull(readings.size - 2)

    /** One line per kind of source, oldest first; a kind with a single value still gets its point. */
    val series: List<Series> by lazy {
        measures.groupBy { it.source.kind }.entries.sortedBy { it.key.ordinal }.map { (kind, values) ->
            Series(kind, values.groupBy { it.date }.map { (date, sameDay) -> date to sameDay.maxOf { it.value } }.sortedBy { it.first })
        }
    }

    /** The date of the most recent measure, or of the content when there is none. */
    val lastUpdate: CivilDate get() = measures.maxOfOrNull { it.date } ?: CivilDate.fromEpochSeconds(article.updatedAt)

    companion object {
        /** Reads an indicator; null when the article is not one or has no unit. */
        fun from(article: Article): Indicator? {
            if (article.type != ArticleType.INDICATEUR) return null
            val event = article.event
            val unit = event.value(VoirTags.UNIT) ?: return null
            val measures = event.fields(VoirTags.MEASURE).mapNotNull { f -> measureOf(f) }.distinctBy { it.id }
            return Indicator(
                article = article,
                axis = VoirAxis.fromTag(event.value(VoirTags.AXIS)) ?: VoirAxis.MOUVEMENT,
                unit = unit,
                rank = event.value(VoirTags.RANK)?.toIntOrNull(),
                declarative = event.value(VoirTags.DECLARATIVE) == "true",
                breakdown = event.value(VoirTags.BREAKDOWN),
                acceptedSources = event.fields(VoirTags.ACCEPTED_SOURCE).mapNotNull { SourceKind.fromTag(it.at(0)) }.distinct(),
                measures = measures,
                sources = event.sources(),
                corrections = event.corrections(),
                nextSteps = event.nextSteps(),
            )
        }

        /** `["ppf-measure", id, date, value, source kind, source name, source url, reliability, occasion, place, part]`. */
        private fun measureOf(f: List<String?>): Measure? {
            val id = f.at(0) ?: return null
            val date = CivilDate.parseIso(f.at(1)) ?: return null
            val value = f.at(2)?.let(::parseNumber)?.takeIf { it >= 0 } ?: return null
            val kind = SourceKind.fromTag(f.at(3)) ?: return null
            val name = f.at(4) ?: return null
            val reliability = Reliability.fromTag(f.at(6)) ?: return null
            return Measure(
                id = id,
                date = date,
                value = value,
                source = Source(kind, name, f.at(5)?.takeIf(::isWebAddress), date),
                reliability = reliability,
                occasion = f.at(7),
                place = f.at(8),
                part = f.at(9)?.let(::parseNumber)?.takeIf { it in 0.0..value },
            )
        }

        /** "45000", "45 000", "2,5", "2.5" all read; anything else is not a number. */
        fun parseNumber(text: String): Double? {
            val cleaned = text.replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.')
            if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
            return cleaned.toDoubleOrNull()
        }
    }
}
