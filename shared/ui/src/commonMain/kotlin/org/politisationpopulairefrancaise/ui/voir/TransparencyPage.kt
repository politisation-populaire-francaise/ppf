package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.FigureUnit
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.PpfInstance
import org.politisationpopulairefrancaise.core.voir.ReportFigure
import org.politisationpopulairefrancaise.core.voir.TransparencyEntry
import org.politisationpopulairefrancaise.core.voir.TransparencyPages
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Transparence de PPF ». */
object TransparencyTags {
    const val OPEN = "transp-open"
    const val PAGE = "transp-page"
    const val EXAMPLE = "transp-example"
    fun section(name: String) = "transp-section-$name"
    fun entry(slug: String) = "transp-entry-$slug"
}

/**
 * « Transparence de PPF » (M34, PP-01, D-104): the instances, the important decisions, the accounts and the reports,
 * always in this order and with the same rubrics every year; a rubric with nothing published says so.
 */
@Composable
internal fun TransparencyPage(pages: TransparencyPages, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().widthIn(max = 860.dp).testTag(TransparencyTags.PAGE), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.transp_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.transp_intro), style = MaterialTheme.typography.bodyMedium)
        val all = pages.instances + pages.decisions + pages.accounts + pages.reports
        if (all.any { it.title.startsWith(VoirCatalog.EXAMPLE_MARKER) }) {
            Callout(CalloutType.ATTENTION, modifier = Modifier.testTag(TransparencyTags.EXAMPLE)) {
                Text(stringResource(Res.string.transp_example), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Rubric("instances", Res.string.transp_instances, pages.instances) { InstanceCard(it) }
        if (pages.instances.isNotEmpty()) Text(stringResource(Res.string.transp_names_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Rubric("decisions", Res.string.transp_decisions, pages.decisions) { DecisionCard(it) }
        Rubric("accounts", Res.string.transp_accounts, pages.accounts) { AccountsCard(it) }
        Rubric("reports", Res.string.transp_reports, pages.reports) { ReportCard(it) }
    }
}

@Composable
private fun Rubric(name: String, title: StringResource, entries: List<TransparencyEntry>, card: @Composable (TransparencyEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(TransparencyTags.section(name)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(title))
        if (entries.isEmpty()) {
            Text(stringResource(Res.string.transp_empty), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
        }
        entries.forEach { card(it) }
    }
}

/** What every page ends with: its text, its archived documents, its sources and its corrections. */
@Composable
private fun EntryFooter(entry: TransparencyEntry) {
    if (entry.article.body.isNotBlank()) MarkdownText(entry.article.body, glossary = false)
    ArchivesSection(entry.archives)
    SourcesSection(entry.sources)
    CorrectionsSection(entry.corrections)
}

@Composable
private fun InstanceCard(entry: TransparencyEntry) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(TransparencyTags.entry(entry.article.slug))) {
        VoirTitle(entry.title, style = MaterialTheme.typography.titleMedium)
        entry.instance?.let { Text(stringResource(it.label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted) }
        entry.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        entry.seats.forEach { seat ->
            val who = seat.name?.let { stringResource(Res.string.transp_seat_named, seat.function, it.withoutExample()) } ?: stringResource(Res.string.transp_seat, seat.function)
            val details = listOfNotNull(seat.since?.let { stringResource(Res.string.transp_seat_since, it.toFrench()) }, seat.designation).joinToString(" · ")
            Column(Modifier.padding(top = 6.dp)) {
                Text(who, style = MaterialTheme.typography.bodyMedium)
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }
        EntryFooter(entry)
    }
}

@Composable
private fun DecisionCard(entry: TransparencyEntry) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(TransparencyTags.entry(entry.article.slug))) {
        VoirTitle(entry.title, style = MaterialTheme.typography.titleMedium)
        val date = entry.date?.toFrench().orEmpty()
        Text(
            entry.instance?.let { stringResource(Res.string.transp_decision_meta, date, stringResource(it.label)) } ?: date,
            style = MaterialTheme.typography.labelLarge,
            color = palette.inkMuted,
        )
        entry.tally?.let { Text(stringResource(Res.string.transp_tally, it.inFavour, it.against, it.abstention), style = MaterialTheme.typography.bodyMedium) }
        entry.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        EntryFooter(entry)
    }
}

@Composable
private fun AccountsCard(entry: TransparencyEntry) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(TransparencyTags.entry(entry.article.slug))) {
        VoirTitle(entry.title, style = MaterialTheme.typography.titleMedium)
        entry.year?.let { Text(stringResource(Res.string.transp_accounts_year, it), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted) }
        entry.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(
            entry.approvedOn?.let { stringResource(Res.string.transp_accounts_approved, it.toFrench()) } ?: stringResource(Res.string.transp_accounts_not_approved),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )
        // The totals and the result, computed from the lines, never typed.
        Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {}) {
            Text(stringResource(Res.string.transp_income, euros(entry.incomeCents)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.transp_expense, euros(entry.expenseCents)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.transp_result, signedEuros(entry.resultCents)), style = MaterialTheme.typography.titleSmall)
        }
        entry.lines.forEach { line ->
            Text(stringResource(Res.string.transp_line, line.category, euros(line.amountCents)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        EntryFooter(entry)
    }
}

@Composable
private fun ReportCard(entry: TransparencyEntry) {
    val palette = PpfTheme.palette
    PpfCard(modifier = Modifier.fillMaxWidth().testTag(TransparencyTags.entry(entry.article.slug))) {
        VoirTitle(entry.title, style = MaterialTheme.typography.titleMedium)
        val start = entry.periodStart?.toFrench().orEmpty()
        Text(
            entry.periodEnd?.let { stringResource(Res.string.transp_report_period, start, it.toFrench()) } ?: stringResource(Res.string.transp_report_running, start),
            style = MaterialTheme.typography.labelLarge,
            color = palette.inkMuted,
        )
        entry.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        listOf(true to Res.string.transp_moderation, false to Res.string.transp_claims).forEach { (moderation, title) ->
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            // Every figure of the list, always in this order: one absent is « not measured », never zero.
            ReportFigure.entries.filter { it.moderation == moderation }.forEach { figure ->
                val value = entry.figures[figure]?.let { valueText(figure, it) } ?: stringResource(Res.string.transp_not_measured)
                Text(stringResource(Res.string.transp_figure_line, stringResource(figure.label), value), style = MaterialTheme.typography.bodySmall)
            }
        }
        EntryFooter(entry)
    }
}

@Composable
private fun valueText(figure: ReportFigure, value: Double): String {
    val number = FrenchNumbers.format(value)
    return when (figure.unit) {
        FigureUnit.COUNT -> number
        FigureUnit.HOURS -> stringResource(Res.string.transp_hours, number)
        FigureUnit.PERCENT -> stringResource(Res.string.transp_percent, number)
    }
}

/** A result may be a deficit: « -1 234,56 € ». */
private fun signedEuros(cents: Long): String = if (cents < 0) "-" + euros(-cents) else euros(cents)

internal val PpfInstance.label: StringResource get() = when (this) {
    PpfInstance.GENERAL_ASSEMBLY -> Res.string.transp_instance_assemblee_generale
    PpfInstance.BOARD -> Res.string.transp_instance_conseil_administration
    PpfInstance.BUREAU -> Res.string.transp_instance_bureau
    PpfInstance.EDITORIAL_TEAM -> Res.string.transp_instance_equipe_editoriale
    PpfInstance.MODERATION_TEAM -> Res.string.transp_instance_equipe_moderation
    PpfInstance.OTHER -> Res.string.transp_instance_autre
}

internal val ReportFigure.label: StringResource get() = when (this) {
    ReportFigure.AUTHORITY_ORDERS -> Res.string.transp_fig_injonctions_autorites
    ReportFigure.REPORTS_RECEIVED -> Res.string.transp_fig_signalements_recus
    ReportFigure.DECISIONS_ON_REPORTS -> Res.string.transp_fig_decisions_sur_signalement
    ReportFigure.DECISIONS_OWN_INITIATIVE -> Res.string.transp_fig_decisions_initiative
    ReportFigure.AUTOMATIC_HIDINGS -> Res.string.transp_fig_masquages_automatiques
    ReportFigure.AUTOMATIC_ALERTS -> Res.string.transp_fig_alertes_automatiques
    ReportFigure.MEDIAN_DELAY_HOURS -> Res.string.transp_fig_delai_median_heures
    ReportFigure.INTERNAL_APPEALS -> Res.string.transp_fig_recours_internes
    ReportFigure.DECISIONS_CHANGED -> Res.string.transp_fig_decisions_modifiees
    ReportFigure.AUTHORITY_TRANSMISSIONS -> Res.string.transp_fig_transmissions_autorites
    ReportFigure.AUTOMATIC_PRECISION -> Res.string.transp_fig_precision_automatique_pourcent
    ReportFigure.AUTOMATIC_ERROR_RATE -> Res.string.transp_fig_erreur_automatique_pourcent
    ReportFigure.CLAIMS_RECEIVED -> Res.string.transp_fig_reclamations_recues
    ReportFigure.CLAIMS_LATE -> Res.string.transp_fig_reclamations_hors_delai
    ReportFigure.DATA_RIGHTS -> Res.string.transp_fig_droits_donnees
    ReportFigure.REQUISITIONS -> Res.string.transp_fig_requisitions
}
