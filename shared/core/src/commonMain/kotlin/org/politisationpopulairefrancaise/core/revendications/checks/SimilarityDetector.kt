package org.politisationpopulairefrancaise.core.revendications.checks

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.text.FrenchText
import kotlin.math.ln

/** What the comparison needs to know about an existing demand (published, or waiting for review on the server). */
data class DemandText(
    val address: String,
    val title: String,
    val summary: String? = null,
    val territory: String? = null,
    val status: RevendicationStatus? = null,
) {
    companion object {
        /** The [EXEMPLE] marker of example contents is not part of what they say. */
        fun of(article: Article): DemandText = DemandText(
            address = article.address.toString(),
            title = article.title.removePrefix(EXAMPLE_MARKER).trim(),
            summary = article.summary,
            territory = article.territory,
            status = article.status,
        )

        private const val EXAMPLE_MARKER = "[EXEMPLE]"
    }
}

/**
 * Finds the existing demands that say the same thing as a draft (DE-18: shown while typing, with
 * the offer to support the existing one; DE-19: duplicates proposed for merging to the team).
 *
 * The comparison works on words: the keywords of the demand (title, then title and summary) are
 * weighted by how rare they are among the compared demands, and two measures are mixed, the share
 * of all the words both texts use and the share of the shorter text found in the other. Then:
 * - two local demands of different territories are rarely the same demand: their score is lowered;
 * - a national demand and a local one may both exist: they are at most "similar".
 *
 * It finds rephrasings that keep the words ("médecins de garde le soir" / "un médecin de garde
 * joignable le soir"), not those that change them, and it cannot see that "supprimer X" is the
 * opposite of "créer X": the meaning belongs to the AI model (CheckKind.AI), on the server.
 */
class SimilarityDetector(private val candidates: List<DemandText>) {

    private data class Prepared(val text: DemandText, val title: Set<String>, val full: Set<String>)

    private val prepared: List<Prepared> = candidates.map { candidate ->
        val title = FrenchText.keywords(candidate.title).toSet()
        Prepared(candidate, title, title + FrenchText.keywords(candidate.summary.orEmpty()))
    }

    /** How many documents use each keyword, the draft not counted yet. */
    private val documentFrequency: Map<String, Int> = buildMap {
        prepared.forEach { doc -> doc.full.forEach { put(it, (get(it) ?: 0) + 1) } }
    }

    val size: Int get() = candidates.size

    /**
     * The demands closest to the draft, closest first, at most [limit], only those whose score
     * reaches [SIMILAR_THRESHOLD]. A demand with the same address as the draft (its own earlier
     * version) is ignored.
     */
    fun compare(title: String, summary: String = "", territory: String? = null, ownAddress: String? = null, limit: Int = 5): List<Match> =
        rank(title, summary, territory, ownAddress).filter { it.level != null }.take(limit)

    /** Same, for a whole draft. */
    fun compare(draft: RevendicationDraft, ownAddress: String? = null, limit: Int = 5): List<Match> =
        compare(draft.title, draft.summary, draft.territory, ownAddress, limit)

    /**
     * Every demand sharing at least one keyword with the draft, closest first, whatever its score:
     * the server gives the closest ones to the AI model, which may see a duplicate the words miss.
     */
    fun rank(title: String, summary: String = "", territory: String? = null, ownAddress: String? = null): List<Match> {
        val draftTitle = FrenchText.keywords(title).toSet()
        if (draftTitle.isEmpty()) return emptyList()
        val draftFull = draftTitle + FrenchText.keywords(summary)
        val documents = prepared.size + 1
        val weight: (String) -> Double = { word ->
            val df = (documentFrequency[word] ?: 0) + (if (word in draftFull) 1 else 0)
            ln(1.0 + documents.toDouble() / df.coerceAtLeast(1))
        }
        return prepared.asSequence()
            .filter { it.text.address != ownAddress }
            .map { doc ->
                val raw = maxOf(similarity(draftTitle, doc.title, weight), similarity(draftFull, doc.full, weight) * SUMMARY_FACTOR)
                val (score, cappedToSimilar) = territorial(raw, territory, doc.text.territory)
                Match(doc.text, score, levelOf(score, cappedToSimilar))
            }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.demand.title })
            .toList()
    }

    data class Match(val demand: DemandText, val score: Double, val level: Level?) {
        fun toRelated(): RelatedDemand = RelatedDemand(
            address = demand.address,
            title = demand.title,
            score = (score * 100).toInt() / 100.0,
            statusTag = demand.status?.tagValue,
            territory = demand.territory,
        )
    }

    enum class Level { DUPLICATE, SIMILAR }

    private fun similarity(a: Set<String>, b: Set<String>, weight: (String) -> Double): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.intersect(b)
        if (common.isEmpty()) return 0.0
        val wa = a.sumOf(weight)
        val wb = b.sumOf(weight)
        val wCommon = common.sumOf(weight)
        val union = wa + wb - wCommon
        val jaccard = wCommon / union
        val containment = wCommon / minOf(wa, wb)
        return 0.5 * jaccard + 0.5 * containment
    }

    private fun territorial(score: Double, draft: String?, other: String?): Pair<Double, Boolean> {
        val a = FrenchText.fold(draft.orEmpty())
        val b = FrenchText.fold(other.orEmpty())
        if (a.isEmpty() || b.isEmpty() || a == b) return score to false
        val aNational = a == NATIONAL
        val bNational = b == NATIONAL
        return when {
            aNational || bNational -> score to true
            // "Lyon" and "Métropole de Lyon": the same place, written differently.
            a in b || b in a -> score to false
            else -> score * OTHER_TERRITORY_FACTOR to true
        }
    }

    private fun levelOf(score: Double, cappedToSimilar: Boolean): Level? = when {
        score >= DUPLICATE_THRESHOLD && !cappedToSimilar -> Level.DUPLICATE
        score >= SIMILAR_THRESHOLD -> Level.SIMILAR
        else -> null
    }

    companion object {
        /** At or above: "semble dire la même chose". Tuned on the cases of SimilarityDetectorTest. */
        const val DUPLICATE_THRESHOLD: Double = 0.6

        /** At or above: shown as a close demand to read. */
        const val SIMILAR_THRESHOLD: Double = 0.3

        /** The summary adds words that dilute the comparison: it counts a little less than the title. */
        const val SUMMARY_FACTOR: Double = 0.9

        /** Two local demands of different places. */
        const val OTHER_TERRITORY_FACTOR: Double = 0.6

        private const val NATIONAL = "france"
    }
}
