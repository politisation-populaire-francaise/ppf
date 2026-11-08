package org.politisationpopulairefrancaise.core.decider

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.isWebAddress
import org.politisationpopulairefrancaise.core.voir.VoirTags.links
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/**
 * The comparator of an election (DE-16, design: `docs/integration/comparateur-2027.md`): which
 * candidates, which themes, in which order. Published by the editorial team (`ppf-type` =
 * `comparateur`); the candidates are the sheets of "Voir" linked with the role `candidat`.
 */
data class ComparatorSheet(
    val article: Article,
    val election: Election,
    val candidates: List<ContentAddress>,
    /** The themes, in the order of the sheet: the same for every candidate. */
    val themes: List<String>,
    val sources: List<Source>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address

    companion object {
        /** Enough to compare, few enough to read on a phone. */
        const val MAX_THEMES: Int = 20

        fun from(article: Article): ComparatorSheet? {
            if (article.type != ArticleType.COMPARATEUR) return null
            val event = article.event
            val election = event.fields(DeciderTags.ELECTION).firstNotNullOfOrNull { f ->
                val id = f.at(0) ?: return@firstNotNullOfOrNull null
                val label = f.at(1) ?: return@firstNotNullOfOrNull null
                Election(id, label, CivilDate.parseIso(f.at(2)))
            } ?: return null
            val themes = event.fields(DeciderTags.THEME).mapNotNull { it.at(0) }
            if (themes.isEmpty() || themes.size > MAX_THEMES || themes.distinct().size != themes.size) return null
            return ComparatorSheet(
                article = article,
                election = election,
                candidates = event.links(DeciderTags.ROLE_CANDIDATE),
                themes = themes,
                sources = event.sources(),
                corrections = event.corrections(),
            )
        }
    }
}

/** A short quotation of the candidate's own words, with the page and the date it comes from. */
data class Quote(val text: String, val url: String, val date: CivilDate)

/**
 * The position of one candidate on one theme (`ppf-type` = `position`): a short summary written by
 * the editorial team ([Article.title]), a short quotation of the candidate with its source, and an
 * optional explanation (the body). The same limits for everyone.
 */
data class Position(
    val article: Article,
    val electionId: String,
    val candidate: ContentAddress,
    val theme: String,
    val summary: String,
    val quote: Quote,
) {
    companion object {
        /** The summary of a position, the same length for everyone (one or two sentences). */
        const val MAX_SUMMARY: Int = 280

        /** A short quotation only (right of quotation): never the programme itself. */
        const val MAX_QUOTE: Int = 300

        fun from(article: Article): Position? {
            if (article.type != ArticleType.POSITION) return null
            val event = article.event
            val electionId = event.fields(DeciderTags.ELECTION).firstNotNullOfOrNull { it.at(0) } ?: return null
            val candidate = event.links(DeciderTags.ROLE_CANDIDATE).singleOrNull() ?: return null
            val theme = event.value(DeciderTags.THEME) ?: return null
            val summary = article.title.trim().takeIf { it.isNotEmpty() && it.length <= MAX_SUMMARY } ?: return null
            // Without its source, a position is not shown: every cell is checkable the same way.
            val quote = event.fields(DeciderTags.QUOTE).firstNotNullOfOrNull { f ->
                val text = f.at(0)?.takeIf { it.length <= MAX_QUOTE } ?: return@firstNotNullOfOrNull null
                val url = f.at(1)?.takeIf(::isWebAddress) ?: return@firstNotNullOfOrNull null
                val date = CivilDate.parseIso(f.at(2)) ?: return@firstNotNullOfOrNull null
                Quote(text, url, date)
            } ?: return null
            return Position(article, electionId, candidate, theme, summary, quote)
        }
    }
}

/** One cell: the candidate's position on the theme, or none published (shown as such, never filled in). */
data class ComparatorCell(val theme: String, val position: Position?)

/** One candidate's line: every theme of the sheet, in its order, whatever was published. */
data class ComparatorRow(val candidate: ContentAddress, val name: String, val cells: List<ComparatorCell>) {
    fun cell(theme: String): ComparatorCell? = cells.firstOrNull { it.theme == theme }
}

/** Why a published position is not shown; listed apart, for transparency. */
enum class PositionAside {
    /** For another election than the sheet's. */
    OTHER_ELECTION,

    /** About someone who is not among the candidates of the sheet. */
    NOT_A_CANDIDATE,

    /** On a theme the sheet does not have: a theme for one candidate would be a theme for one candidate only. */
    OTHER_THEME,
}

/**
 * The grid of the comparator, candidates by themes, under the rule of identical treatment (cahier
 * des charges, 9.2; cahier fonctionnel, rule 9):
 * - every candidate of the sheet has a line, with every theme of the sheet, in the same order;
 * - the lines are in alphabetical order of the names, never in any other order;
 * - one position per candidate and theme (the latest version); none is shown as « none published »;
 * - nothing else: no score, no ranking, no match with the reader.
 */
class ComparatorGrid(
    val sheet: ComparatorSheet,
    val rows: List<ComparatorRow>,
    val aside: List<Pair<Position, PositionAside>>,
) {
    val themes: List<String> get() = sheet.themes

    /** Every candidate's cell for one theme, in the order of the lines. */
    fun column(theme: String): List<Pair<ComparatorRow, ComparatorCell>> = rows.mapNotNull { row -> row.cell(theme)?.let { row to it } }

    companion object {
        fun build(sheet: ComparatorSheet, positions: List<Position>, nameOf: (ContentAddress) -> String?): ComparatorGrid {
            val candidates = sheet.candidates.toSet()
            val themes = sheet.themes.toSet()
            val aside = positions.mapNotNull { p ->
                when {
                    p.electionId != sheet.election.id -> p to PositionAside.OTHER_ELECTION
                    p.candidate !in candidates -> p to PositionAside.NOT_A_CANDIDATE
                    p.theme !in themes -> p to PositionAside.OTHER_THEME
                    else -> null
                }
            }
            val shown = positions
                .filter { it.electionId == sheet.election.id && it.candidate in candidates && it.theme in themes }
                .groupBy { it.candidate to it.theme }
                .mapValues { (_, list) -> list.maxWith(compareBy<Position>({ it.article.updatedAt }, { it.article.event.id })) }
            val rows = sheet.candidates.distinct().map { candidate ->
                ComparatorRow(
                    candidate = candidate,
                    name = nameOf(candidate) ?: candidate.identifier,
                    cells = sheet.themes.map { theme -> ComparatorCell(theme, shown[candidate to theme]) },
                )
            }.sortedWith(compareBy<ComparatorRow>({ PacteBoard.sortKey(it.name) }, { it.candidate.toString() }))
            return ComparatorGrid(sheet, rows, aside)
        }
    }
}
