package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.VoirTags.archives
import org.politisationpopulairefrancaise.core.voir.VoirTags.at
import org.politisationpopulairefrancaise.core.voir.VoirTags.corrections
import org.politisationpopulairefrancaise.core.voir.VoirTags.fields
import org.politisationpopulairefrancaise.core.voir.VoirTags.sources
import org.politisationpopulairefrancaise.core.voir.VoirTags.value

/*
 * « Transparence de PPF » (M34, PP-01, D-104; docs/integration/transparence-ppf.md): the instances of the
 * association, its important decisions, its annual accounts and its reports of moderation and claims, published
 * as signed contents of the team, with the same rubrics every year. Functions before names: a name is written
 * only with the consent of the person (principle 10), the field is empty by default.
 */

/** The rubric of a page of transparency (`ppf-rubric`), in the order of the page. */
enum class TransparencyRubric(val tagValue: String) {
    INSTANCE("instance"),
    DECISION("decision"),
    ACCOUNTS("comptes"),
    REPORT("rapport"),
    ;

    companion object {
        fun fromTag(value: String?): TransparencyRubric? = entries.firstOrNull { it.tagValue == value }
    }
}

/** The instances of the association (`ppf-instance`), in the order of the page. */
enum class PpfInstance(val tagValue: String) {
    GENERAL_ASSEMBLY("assemblee-generale"),
    BOARD("conseil-administration"),
    BUREAU("bureau"),
    EDITORIAL_TEAM("equipe-editoriale"),
    MODERATION_TEAM("equipe-moderation"),
    OTHER("autre"),
    ;

    companion object {
        fun fromTag(value: String?): PpfInstance? = entries.firstOrNull { it.tagValue == value }
    }
}

/** One seat of an instance: its function, the name only when the person agreed, since when, how it is designated. */
data class InstanceSeat(val function: String, val name: String?, val since: CivilDate?, val designation: String?)

/** The votes of a decision (`ppf-tally`: for, against, abstention). */
data class Tally(val inFavour: Int, val against: Int, val abstention: Int)

/** Whether a line of the accounts is an income or an expense. */
enum class AccountSide(val tagValue: String) {
    INCOME("produit"),
    EXPENSE("charge"),
    ;

    companion object {
        fun fromTag(value: String?): AccountSide? = entries.firstOrNull { it.tagValue == value }
    }
}

/** One line of the annual accounts, in cents. */
data class AccountLine(val side: AccountSide, val category: String, val amountCents: Long)

/** How a figure of a report is read. */
enum class FigureUnit { COUNT, HOURS, PERCENT }

/**
 * The figures of a report of moderation and claims (`ppf-figure`), a fixed list always shown in this order
 * (cahier des charges, section 9.4: articles 15 and 24 of the Digital Services Act; S-110 for the claims).
 */
enum class ReportFigure(val tagValue: String, val unit: FigureUnit = FigureUnit.COUNT) {
    AUTHORITY_ORDERS("injonctions-autorites"),
    REPORTS_RECEIVED("signalements-recus"),
    DECISIONS_ON_REPORTS("decisions-sur-signalement"),
    DECISIONS_OWN_INITIATIVE("decisions-initiative"),
    AUTOMATIC_HIDINGS("masquages-automatiques"),
    AUTOMATIC_ALERTS("alertes-automatiques"),
    MEDIAN_DELAY_HOURS("delai-median-heures", FigureUnit.HOURS),
    INTERNAL_APPEALS("recours-internes"),
    DECISIONS_CHANGED("decisions-modifiees"),
    AUTHORITY_TRANSMISSIONS("transmissions-autorites"),
    AUTOMATIC_PRECISION("precision-automatique-pourcent", FigureUnit.PERCENT),
    AUTOMATIC_ERROR_RATE("erreur-automatique-pourcent", FigureUnit.PERCENT),
    CLAIMS_RECEIVED("reclamations-recues"),
    CLAIMS_LATE("reclamations-hors-delai"),
    DATA_RIGHTS("droits-donnees"),
    REQUISITIONS("requisitions"),
    ;

    /** The figures of moderation (the rest are those of the claims). */
    val moderation: Boolean get() = ordinal <= AUTOMATIC_ERROR_RATE.ordinal

    companion object {
        fun fromTag(value: String?): ReportFigure? = entries.firstOrNull { it.tagValue == value }
    }
}

/** One page of « Transparence de PPF ». */
data class TransparencyEntry(
    val article: Article,
    val rubric: TransparencyRubric,
    val instance: PpfInstance?,
    val seats: List<InstanceSeat>,
    /** The day of a decision. */
    val date: CivilDate?,
    val tally: Tally?,
    /** The financial year of the accounts. */
    val year: Int?,
    val lines: List<AccountLine>,
    /** The day the general assembly approved the accounts. */
    val approvedOn: CivilDate?,
    /** The period of a report: start, end (null while it runs). */
    val periodStart: CivilDate?,
    val periodEnd: CivilDate?,
    /** The figures given, by code; a code absent is « not measured », never zero. */
    val figures: Map<ReportFigure, Double>,
    val archives: List<ArchivedCopy>,
    val sources: List<Source>,
    val corrections: List<Correction>,
) {
    val address: ContentAddress get() = article.address
    val title: String get() = article.title

    /** The total of the incomes, of the expenses, and the result (incomes minus expenses), computed, never typed. */
    val incomeCents: Long get() = lines.filter { it.side == AccountSide.INCOME }.sumOf { it.amountCents }
    val expenseCents: Long get() = lines.filter { it.side == AccountSide.EXPENSE }.sumOf { it.amountCents }
    val resultCents: Long get() = incomeCents - expenseCents

    companion object {
        fun from(article: Article): TransparencyEntry? {
            if (article.type != ArticleType.TRANSPARENCE) return null
            val event = article.event
            val rubric = TransparencyRubric.fromTag(event.value(VoirTags.RUBRIC)) ?: return null
            val period = event.fields(VoirTags.PERIOD).firstOrNull()
            val entry = TransparencyEntry(
                article = article,
                rubric = rubric,
                instance = PpfInstance.fromTag(event.value(VoirTags.INSTANCE)),
                seats = event.fields(VoirTags.SEAT).mapNotNull { f ->
                    val function = f.at(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    InstanceSeat(function, f.at(1)?.trim()?.takeIf { it.isNotEmpty() }, CivilDate.parseIso(f.at(2)), f.at(3)?.trim()?.takeIf { it.isNotEmpty() })
                },
                date = CivilDate.parseIso(event.value(VoirTags.DATE)),
                tally = event.fields(VoirTags.TALLY).firstOrNull()?.let { f ->
                    val counts = (0..2).map { f.at(it)?.trim()?.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return@let null }
                    Tally(counts[0], counts[1], counts[2])
                },
                year = event.value(VoirTags.YEAR)?.toIntOrNull()?.takeIf { it in 2026..2100 },
                lines = event.fields(VoirTags.ACCOUNT_LINE).mapNotNull { f ->
                    val side = AccountSide.fromTag(f.at(0)) ?: return@mapNotNull null
                    val category = f.at(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val amount = f.at(2)?.let(Indicator::parseNumber)?.takeIf { it >= 0 } ?: return@mapNotNull null
                    AccountLine(side, category, kotlin.math.round(amount * 100).toLong())
                },
                approvedOn = CivilDate.parseIso(event.value(VoirTags.APPROVED)),
                periodStart = CivilDate.parseIso(period?.at(0)),
                periodEnd = CivilDate.parseIso(period?.at(1)),
                figures = event.fields(VoirTags.FIGURE).mapNotNull { f ->
                    val code = ReportFigure.fromTag(f.at(0)) ?: return@mapNotNull null
                    val value = f.at(1)?.let(Indicator::parseNumber)?.takeIf { it >= 0 && (code.unit != FigureUnit.PERCENT || it <= 100) } ?: return@mapNotNull null
                    code to value
                }.distinctBy { it.first }.toMap(),
                archives = event.archives(),
                sources = event.sources(),
                corrections = event.corrections(),
            )
            // What each rubric cannot do without.
            return when (rubric) {
                TransparencyRubric.INSTANCE -> entry.takeIf { it.instance != null }
                TransparencyRubric.DECISION -> entry.takeIf { it.date != null }
                TransparencyRubric.ACCOUNTS -> entry.takeIf { it.year != null }
                TransparencyRubric.REPORT -> entry.takeIf { it.periodStart != null }
            }
        }
    }
}

/** The pages of « Transparence de PPF », each rubric in its order: the same page every year. */
class TransparencyPages(entries: List<TransparencyEntry>) {
    /** By instance, in the order of [PpfInstance], then by title. */
    val instances: List<TransparencyEntry> = entries.filter { it.rubric == TransparencyRubric.INSTANCE }
        .sortedWith(compareBy<TransparencyEntry> { it.instance?.ordinal ?: Int.MAX_VALUE }.thenBy { FrenchText.fold(it.title) })

    /** Newest first. */
    val decisions: List<TransparencyEntry> = entries.filter { it.rubric == TransparencyRubric.DECISION }
        .sortedWith(compareByDescending<TransparencyEntry> { it.date }.thenBy { FrenchText.fold(it.title) })

    /** The latest financial year first. */
    val accounts: List<TransparencyEntry> = entries.filter { it.rubric == TransparencyRubric.ACCOUNTS }.sortedByDescending { it.year }

    /** The latest period first. */
    val reports: List<TransparencyEntry> = entries.filter { it.rubric == TransparencyRubric.REPORT }.sortedByDescending { it.periodStart }

    val isEmpty: Boolean get() = instances.isEmpty() && decisions.isEmpty() && accounts.isEmpty() && reports.isEmpty()
}
