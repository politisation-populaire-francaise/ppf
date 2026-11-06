package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.SimilarityDetector
import org.politisationpopulairefrancaise.core.text.FrenchText

/** A mobilization close to a draft: on the same day, with a close title, a close place, or both. */
data class CloseMobilization(
    val mobilization: Mobilization,
    val closeTitle: Boolean,
    val closePlace: Boolean,
)

/**
 * AG-21: an event carried by several organisations appears only once, with all its organisers.
 * Before a new event is sent, the form shows the ones that look like it, so that the author joins
 * the existing one instead of adding a copy: same local day, and a close title (the word
 * comparison of the demands wall, [SimilarityDetector], which also lowers the score of two
 * different territories) or a close place (most of the meaningful words of the place in common).
 * It is a hint, never a refusal: two gatherings the same day in the same square may well differ.
 */
object MobilizationDuplicates {

    /** Share of the meaningful words of two places they must have in common to be "close". */
    const val PLACE_THRESHOLD: Double = 0.5

    fun find(
        title: String,
        location: String,
        territory: String,
        start: Long,
        candidates: List<Mobilization>,
        ownAddress: String? = null,
        limit: Int = 5,
    ): List<CloseMobilization> {
        val day = ParisTime.epochDay(start)
        val sameDay = candidates
            .filter { it.address.toString() != ownAddress && !it.cancelled }
            .filter { day in ParisTime.epochDay(it.start)..ParisTime.epochDay(it.end ?: it.start) }
            .distinctBy { it.address.toString() }
        if (sameDay.isEmpty()) return emptyList()
        val detector = SimilarityDetector(sameDay.map { DemandText(address = it.address.toString(), title = it.displayTitle, territory = it.territory) })
        val titleScores = detector.rank(title.removePrefix(Mobilization.EXAMPLE_MARKER).trim(), territory = territory.takeIf { it.isNotBlank() })
            .associate { it.demand.address to it.score }
        val place = placeWords(location)
        return sameDay.asSequence()
            .map { candidate ->
                val score = titleScores[candidate.address.toString()] ?: 0.0
                CloseMobilization(
                    mobilization = candidate,
                    closeTitle = score >= SimilarityDetector.SIMILAR_THRESHOLD,
                    closePlace = closePlaces(place, location, candidate.location),
                ) to score
            }
            .filter { (close, _) -> close.closeTitle || close.closePlace }
            .sortedWith(
                compareByDescending<Pair<CloseMobilization, Double>> { (close, _) -> (if (close.closeTitle) 2 else 0) + (if (close.closePlace) 1 else 0) }
                    .thenByDescending { it.second }
                    .thenBy { it.first.mobilization.start },
            )
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun placeWords(location: String): Set<String> = FrenchText.keywords(location).toSet()

    private fun closePlaces(words: Set<String>, location: String, other: String): Boolean {
        if (location.isBlank()) return false
        if (FrenchText.fold(location) == FrenchText.fold(other)) return true
        val otherWords = placeWords(other)
        if (words.isEmpty() || otherWords.isEmpty()) return false
        val common = words.intersect(otherWords).size.toDouble()
        return common / (words.size + otherWords.size - common) >= PLACE_THRESHOLD
    }
}
