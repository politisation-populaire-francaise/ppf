package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirCatalog

/**
 * Everything "Décider" shows, assembled on the device from the trusted editorial contents: the
 * official petitions relayed (M10), the democratic pacts (M13.5) and the answers of the candidates.
 * The candidates are the public persons of "Voir", so that a candidate has one sheet everywhere.
 */
class DeciderCatalog(
    val petitions: List<Petition>,
    val pactes: List<Pacte>,
    val engagements: List<Engagement>,
) {
    val isEmpty: Boolean get() = petitions.isEmpty() && pactes.isEmpty()

    fun petition(address: ContentAddress): Petition? = petitions.firstOrNull { it.address == address }
    fun pacte(address: ContentAddress): Pacte? = pactes.firstOrNull { it.address == address }

    /** The open petitions: the nearest last day first, then those without one, by title. */
    fun openPetitions(today: CivilDate): List<Petition> = petitions.filter { it.isOpen(today) }
        .sortedWith(compareBy<Petition>({ it.closesOn ?: FAR_AWAY }, { sortKey(it.title) }))

    /** The closed petitions, the most recently opened first: what they obtained stays visible. */
    fun closedPetitions(today: CivilDate): List<Petition> = petitions.filterNot { it.isOpen(today) }
        .sortedWith(compareByDescending<Petition> { it.openedOn ?: FAR_PAST }.thenBy { sortKey(it.title) })

    /** The petitions about a bill of "Voir". */
    fun petitionsOn(text: ContentAddress): List<Petition> = petitions.filter { text in it.texts }

    /** The pact to show first: the one sent and still open for answers, else the latest one. */
    fun currentPacte(): Pacte? = pactes.firstOrNull { it.status == PacteStatus.SENT }
        ?: pactes.firstOrNull { it.status == PacteStatus.PROPOSED }
        ?: pactes.firstOrNull()

    /** The public table of a pact, with the names and the follow-up facts of "Voir". */
    fun board(pacte: Pacte, voir: VoirCatalog, today: CivilDate): PacteBoard = PacteBoard.build(
        pacte = pacte,
        engagements = engagements,
        nameOf = { voir.person(it)?.name },
        followUps = voir.facts.mapNotNull(PacteFollowUps::of),
        today = today,
    )

    /** The pacts a candidate was sent, with their line in the table. */
    fun pactesOf(candidate: ContentAddress): List<Pacte> = pactes.filter { candidate in it.candidates }

    /**
     * Every petition whose title, summary or reference contains every word of [query] (accents and
     * case ignored), then the pacts.
     */
    fun searchPetitions(query: String): List<Petition> {
        if (FrenchText.words(query).isEmpty()) return emptyList()
        return petitions.filter { FrenchText.matchesAllWords(listOfNotNull(it.title, it.article.summary, it.reference).joinToString(" "), query) }
            .sortedBy { sortKey(it.title) }
    }

    companion object {
        private val FAR_AWAY = CivilDate(9999, 12, 31)
        private val FAR_PAST = CivilDate(1, 1, 1)

        val EMPTY: DeciderCatalog = DeciderCatalog(emptyList(), emptyList(), emptyList())

        /** The editorial types read here, to ask the local copy for them. */
        val TYPES: List<ArticleType> = listOf(ArticleType.PETITION, ArticleType.PACTE, ArticleType.ENGAGEMENT)

        private fun sortKey(title: String): String = FrenchText.fold(title.removePrefix(VoirCatalog.EXAMPLE_MARKER).trim())

        /**
         * Builds the catalog from articles already checked (signature, trusted author). Malformed
         * blocks are left out; for a given address only the most recent version counts.
         */
        fun from(articles: List<Article>): DeciderCatalog {
            val latest = articles.groupBy { it.address }.values.map { versions -> versions.maxBy { it.updatedAt } }
            return DeciderCatalog(
                petitions = latest.mapNotNull(Petition::from),
                // The pact being answered first, then the starting lists, then the closed ones, newest first.
                pactes = latest.mapNotNull(Pacte::from).sortedWith(
                    compareBy<Pacte> { listOf(PacteStatus.SENT, PacteStatus.PROPOSED, PacteStatus.CLOSED).indexOf(it.status) }
                        .thenByDescending { it.article.updatedAt },
                ),
                engagements = latest.mapNotNull(Engagement::from),
            )
        }
    }
}
