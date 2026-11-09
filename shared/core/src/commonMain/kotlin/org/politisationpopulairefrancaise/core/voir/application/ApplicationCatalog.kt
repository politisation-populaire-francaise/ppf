package org.politisationpopulairefrancaise.core.voir.application

import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate

/** Where an awaited measure stands on [CivilDate] `today`, from the facts of the source only. */
data class PendingDelay(
    /** Months elapsed since the law was published (whole months). */
    val monthsSinceLaw: Int,
    /** The month announced by the government is over. */
    val announcedMonthPassed: Boolean,
)

/** The figures of one law shown everywhere the law appears, computed the same way for every law. */
data class LawFigures(
    val toApply: Int,
    val applied: Int,
    val pending: Int,
    val notNeeded: Int,
    /** Awaited measures whose announced month is over. */
    val pastAnnounced: Int,
    val percent: Int?,
)

/**
 * The packages of the device, read (VO-36). The laws are listed newest first, never ranked by their rate nor
 * grouped by government or minister: the page shows the same figures for each law, from the source.
 */
class ApplicationCatalog(packages: List<ApplicationPackage>) {
    val legislatures: List<Int> = packages.map { it.legislature }.distinct().sortedDescending()

    val laws: List<LawApplication> = packages.flatMap { it.laws }.distinctBy { it.id }
        .sortedWith(compareByDescending<LawApplication> { it.publishedOn }.thenBy { it.id })

    val example: Boolean = packages.any { it.example }

    val updatedOn: String? = packages.mapNotNull { it.updatedOn }.maxOrNull()

    private val byId = laws.associateBy { it.id }
    private val byDossier = laws.filter { it.dossier != null }.associateBy { it.dossier!! }

    fun law(id: String): LawApplication? = byId[id]

    /** The law of a legislative file of the Assemblée (`an-dossier:<uid>`), the key shared with the ballots and the bills of "Voir". */
    fun ofDossier(dossier: String): LawApplication? = byDossier[dossier]

    /** The laws whose title holds every word typed, of [legislature] when given; [pendingOnly] keeps those still awaiting a measure. */
    fun search(query: String, legislature: Int? = null, pendingOnly: Boolean = false): List<LawApplication> = laws.filter { law ->
        (legislature == null || law.legislature == legislature) &&
            (!pendingOnly || law.pending.isNotEmpty()) &&
            FrenchText.matchesAllWords(law.title, query)
    }

    companion object {
        /** The month of [today], `AAAA-MM`. */
        private fun monthOf(today: CivilDate): String = today.toIso().take(7)

        fun delay(law: LawApplication, measure: ApplicationMeasure, today: CivilDate): PendingDelay? {
            if (measure.state != MeasureState.PENDING) return null
            val published = CivilDate.parseIso(law.publishedOn) ?: return null
            var months = (today.year - published.year) * 12 + (today.month - published.month)
            if (today.day < published.day) months -= 1
            return PendingDelay(
                monthsSinceLaw = months.coerceAtLeast(0),
                announcedMonthPassed = measure.plannedMonth != null && measure.plannedMonth < monthOf(today),
            )
        }

        fun figures(law: LawApplication, today: CivilDate): LawFigures = LawFigures(
            toApply = law.toApply,
            applied = law.applied,
            pending = law.measures.count { it.state == MeasureState.PENDING },
            notNeeded = law.measures.count { it.state == MeasureState.NOT_NEEDED },
            pastAnnounced = law.measures.count { delay(law, it, today)?.announcedMonthPassed == true },
            percent = law.percent(),
        )
    }
}
