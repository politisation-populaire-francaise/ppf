package org.politisationpopulairefrancaise.server.decider

import org.politisationpopulairefrancaise.core.decider.AnswerStatus
import org.politisationpopulairefrancaise.core.decider.DeciderCatalog
import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * The figures of « Décider » in the « Tableau de bord fédérateur » (PP-07, M34; design of « Voir »,
 * `docs/integration/tableau-de-bord.md`): what each month (`AAAA-MM`) added, from what PPF already publishes. Never a
 * person. The votes come from the ballot boxes ([org.politisationpopulairefrancaise.server.urne.UrneStore.votesByMonth]);
 * these two from the editorial contents of « Décider » (signed by the team).
 */
object DeciderDashboardFigures {

    /**
     * « Signatures »: the official readings of the petitions PPF relays, as the team wrote them with their date and
     * source. For each petition, a month adds what its last reading of the month goes beyond the highest reading
     * before (a lower reading, after a correction of the official site, adds nothing): the months add up to the
     * highest official count of each petition.
     */
    fun signatures(catalog: DeciderCatalog): Map<String, Long> =
        sum(catalog.petitions.map { petition -> monthlyIncrements(petition.counts.map { it.readOn to it.count }) })

    /**
     * « Engagements de candidats »: each candidate who signed at least one measure of a pact, once per pact, in the
     * month their first answer with a signed measure was received.
     */
    fun engagements(catalog: DeciderCatalog): Map<String, Long> {
        val firstSigned = catalog.engagements
            .filter { engagement -> engagement.answers.any { it.status == AnswerStatus.SIGNED } }
            .groupBy { it.pacte to it.candidate }
            .mapValues { (_, answers) -> answers.minOf { it.receivedOn } }
        return firstSigned.values.groupingBy(::monthOf).eachCount().mapValues { it.value.toLong() }.toSortedMap()
    }

    /** What each month added to a counter read on several days: the last reading of the month above the highest before. */
    internal fun monthlyIncrements(readings: List<Pair<CivilDate, Long>>): Map<String, Long> {
        val lastOfMonth = readings.sortedBy { it.first }.groupBy { monthOf(it.first) }.mapValues { (_, month) -> month.last().second }
        var highest = 0L
        val added = LinkedHashMap<String, Long>()
        lastOfMonth.toSortedMap().forEach { (month, reading) ->
            if (reading > highest) {
                added[month] = reading - highest
                highest = reading
            }
        }
        return added
    }

    private fun sum(parts: List<Map<String, Long>>): Map<String, Long> {
        val total = sortedMapOf<String, Long>()
        parts.forEach { part -> part.forEach { (month, n) -> total[month] = (total[month] ?: 0L) + n } }
        return total
    }

    private fun monthOf(date: CivilDate): String = date.toIso().take(7)
}
