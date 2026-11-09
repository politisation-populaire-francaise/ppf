package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.application.ApplicationCatalog
import org.politisationpopulairefrancaise.core.voir.application.ApplicationFormat
import org.politisationpopulairefrancaise.core.voir.application.ApplicationMeasure
import org.politisationpopulairefrancaise.core.voir.application.LawApplication
import org.politisationpopulairefrancaise.core.voir.application.MeasureState
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirTitle

/** Test tags of the « Applicomètre ». */
object ApplicationTags {
    const val ENTRY = "appl-entry"
    const val PAGE = "appl-page"
    const val NONE_YET = "appl-none-yet"
    const val SEARCH = "appl-search"
    const val PENDING_ONLY = "appl-pending-only"
    const val LAW_PAGE = "appl-law-page"
    const val FIGURES = "appl-figures"
    const val LEGIFRANCE = "appl-legifrance"
    const val DOSSIER = "appl-dossier"
    const val VOIR_SHEET = "appl-voir-sheet"
    const val BILL = "appl-bill"
    const val BILL_OPEN = "appl-bill-open"
    fun download(legislature: Int) = "appl-download-$legislature"
    fun legislature(value: Int?) = "appl-legislature-${value ?: "all"}"
    fun law(id: String) = "appl-law-$id"
    fun measure(order: Int) = "appl-measure-$order"
}

/** The card on the list of the Parliament that opens the « Applicomètre ». */
@Composable
internal fun ApplicationEntry(state: ParliamentState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(ApplicationTags.ENTRY), onClick = { state.show(ParliamentPage.Application) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ListChecks, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.appl_entry_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.appl_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * « Applicomètre » (VO-36): the method, the packages to download (one per legislature, size shown), then every
 * law newest first with the same figures, a search by title and a filter on the laws still awaiting a measure.
 * Never a ranking of laws, ministers or governments.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApplicationPage(state: ParliamentState, snapshot: ParliamentSnapshot, padding: Dp) {
    val application = state.application
    val palette = PpfTheme.palette
    LaunchedEffect(snapshot) { application.refresh(snapshot) }
    val holding = application.holding
    val catalog = holding?.catalog
    val available = application.available(snapshot)
    val today = state.today
    val laws = catalog?.search(application.query, application.legislature, application.pendingOnly).orEmpty()
    LazyColumn(Modifier.fillMaxSize().testTag(ApplicationTags.PAGE), contentPadding = PaddingValues(padding)) {
        item(key = "head") {
            Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.appl_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(Res.string.appl_intro), style = MaterialTheme.typography.bodyMedium)
                InfoBox(Lucide.Info) { Text(stringResource(Res.string.appl_method), style = MaterialTheme.typography.bodyMedium) }
                if (available.isEmpty() && catalog == null) {
                    Text(stringResource(Res.string.appl_none_yet), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(ApplicationTags.NONE_YET))
                }
                val toOffer = available.filter { (legislature, _) -> catalog?.legislatures?.contains(legislature) != true || legislature in holding?.outdated.orEmpty() }
                toOffer.forEachIndexed { index, (legislature, ref) ->
                    val held = catalog?.legislatures?.contains(legislature) == true
                    val text = if (application.downloading == legislature) {
                        stringResource(Res.string.appl_downloading)
                    } else {
                        stringResource(if (held) Res.string.appl_update else Res.string.appl_download, legislature, fileSize(ref.bytes))
                    }
                    val modifier = Modifier.testTag(ApplicationTags.download(legislature))
                    // The newest legislature first, as the main gesture.
                    if (index == 0 && catalog == null) {
                        PpfPrimaryButton(onClick = { application.download(snapshot, legislature) }, text = text, enabled = application.downloading == null, icon = Lucide.RefreshCw, modifier = modifier)
                    } else {
                        PpfSecondaryButton(onClick = { application.download(snapshot, legislature) }, text = text, enabled = application.downloading == null, icon = Lucide.RefreshCw, modifier = modifier)
                    }
                }
                if (toOffer.isNotEmpty()) {
                    Text(stringResource(Res.string.appl_download_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                val outcome = application.lastOutcome
                if (outcome != null && outcome != PackageOutcome.Done) {
                    Text(stringResource(Res.string.appl_failed), style = MaterialTheme.typography.bodyMedium, color = palette.accentInk)
                }
                if (catalog != null) {
                    PpfTextField(
                        value = application.query,
                        onValueChange = { application.query = it.take(80) },
                        placeholder = stringResource(Res.string.appl_search),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(ApplicationTags.SEARCH),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (catalog.legislatures.size > 1) {
                            PpfSelectableChip(stringResource(Res.string.appl_all_legislatures), selected = application.legislature == null, onClick = { application.legislature = null }, modifier = Modifier.testTag(ApplicationTags.legislature(null)))
                            catalog.legislatures.forEach { legislature ->
                                PpfSelectableChip(
                                    stringResource(Res.string.appl_legislature, legislature),
                                    selected = application.legislature == legislature,
                                    onClick = { application.legislature = legislature },
                                    modifier = Modifier.testTag(ApplicationTags.legislature(legislature)),
                                )
                            }
                        }
                        PpfSelectableChip(
                            stringResource(Res.string.appl_pending_only),
                            selected = application.pendingOnly,
                            onClick = { application.pendingOnly = !application.pendingOnly },
                            modifier = Modifier.testTag(ApplicationTags.PENDING_ONLY),
                        )
                    }
                    Text(stringResource(Res.string.appl_count, laws.size), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                    if (laws.isEmpty()) Text(stringResource(Res.string.appl_nothing_found), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }
        }
        items(laws, key = { it.id }) { law -> LawCard(state, law, today, Modifier.widthIn(max = 860.dp).padding(top = 8.dp)) }
        item(key = "source") {
            Column(Modifier.widthIn(max = 860.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.appl_source), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                catalog?.updatedOn?.let { Text(stringResource(Res.string.appl_updated, day(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
            }
        }
    }
}

@Composable
private fun LawCard(state: ParliamentState, law: LawApplication, today: CivilDate, modifier: Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(ApplicationTags.law(law.id)), onClick = { state.show(ParliamentPage.ApplicationOf(law.id)) }) {
        VoirTitle(law.title, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.appl_law_published, day(law.publishedOn), law.legislature), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        Figures(law, today)
    }
}

/** The same figures for every law, from the source: measures taken out of those expected, those awaited. */
@Composable
private fun Figures(law: LawApplication, today: CivilDate, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val figures = ApplicationCatalog.figures(law, today)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val percent = figures.percent
        if (percent == null) {
            Text(stringResource(Res.string.appl_figures_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        } else {
            Text(stringResource(Res.string.appl_figures, figures.applied, figures.toApply, percent), style = MaterialTheme.typography.bodyMedium)
        }
        if (figures.pending > 0) {
            Text(stringResource(Res.string.appl_figures_pending, figures.pending, figures.pastAnnounced), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
    }
}

/** One law: what it is, its figures, its links (Légifrance, the Assemblée, its sheet in "Voir", its ballots), then its measures. */
@Composable
internal fun LawApplicationPage(state: ParliamentState, snapshot: ParliamentSnapshot, id: String, padding: Dp, voirSheetFor: (String) -> (() -> Unit)?) {
    val application = state.application
    val palette = PpfTheme.palette
    LaunchedEffect(snapshot) { if (!application.loaded) application.refresh(snapshot) }
    val leave = rememberLeave()
    val today = state.today
    val law = application.holding?.catalog?.law(id)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(ApplicationTags.LAW_PAGE)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (law == null) {
                Text(stringResource(Res.string.appl_nothing_found), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                return@Column
            }
            VoirTitle(law.title, style = MaterialTheme.typography.headlineSmall, heading = true)
            Text(stringResource(Res.string.appl_law_published, day(law.publishedOn), law.legislature), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            law.committee?.let { Text(stringResource(Res.string.appl_committee, it), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
            listOfNotNull(
                Res.string.appl_direct.takeIf { law.directApplication },
                Res.string.appl_ratification.takeIf { law.ratification },
                Res.string.appl_enabling.takeIf { law.enabling },
                Res.string.appl_ordinance_ratification.takeIf { law.ordinanceRatification },
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            if (law.withoutSchedule) Text(stringResource(Res.string.appl_without_schedule), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            Column(Modifier.testTag(ApplicationTags.FIGURES).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Figures(law, today)
                val notNeeded = law.measures.count { it.state == MeasureState.NOT_NEEDED }
                if (notNeeded > 0) Text(stringResource(Res.string.appl_not_needed, notNeeded), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            LinkLine(stringResource(Res.string.appl_legifrance), Lucide.ExternalLink, Modifier.testTag(ApplicationTags.LEGIFRANCE)) { leave(law.legifranceUrl) }
            law.dossierUrl?.let { url -> LinkLine(stringResource(Res.string.appl_dossier), Lucide.ScrollText, Modifier.testTag(ApplicationTags.DOSSIER)) { leave(url) } }
            law.dossier?.let { dossier -> voirSheetFor(dossier) }?.let { open -> LinkLine(stringResource(Res.string.appl_voir_sheet), Lucide.BookOpen, Modifier.testTag(ApplicationTags.VOIR_SHEET), onClick = open) }

            val ballots = law.dossier?.let { snapshot.catalog.ballotsOfDossier(it) }.orEmpty()
            if (ballots.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.appl_ballots))
                ballots.forEach { entry ->
                    BallotRow(ParliamentTags.ballot(entry.ballot.id), entry.ballot.title, entry.ballot.number, entry.ballot.date) { state.show(ParliamentPage.OfBallot(entry.ballot.id)) }
                }
            }

            listOf(
                MeasureState.PENDING to Res.string.appl_section_pending,
                MeasureState.APPLIED to Res.string.appl_section_applied,
                MeasureState.NOT_NEEDED to Res.string.appl_section_not_needed,
            ).forEach { (measureState, title) ->
                val measures = law.measures.filter { it.state == measureState }
                if (measures.isNotEmpty()) {
                    SectionHeader(stringResource(title))
                    measures.forEach { MeasureItem(law, it, today, leave) }
                }
            }
        }
    }
}

@Composable
private fun MeasureItem(law: LawApplication, measure: ApplicationMeasure, today: CivilDate, leave: (String) -> Unit) {
    val palette = PpfTheme.palette
    // One measure is read as one block by a screen reader.
    Column(Modifier.fillMaxWidth().testTag(ApplicationTags.measure(measure.order)).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            measure.articles?.let { stringResource(Res.string.appl_measure_title, measure.order, it) } ?: stringResource(Res.string.appl_measure_title_alone, measure.order),
            style = MaterialTheme.typography.titleSmall,
        )
        measure.subject?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        measure.legalBasis?.let { Text(stringResource(Res.string.appl_measure_basis, it), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
        when (measure.state) {
            MeasureState.PENDING -> {
                ApplicationCatalog.delay(law, measure, today)?.let { delay ->
                    Text(
                        if (delay.monthsSinceLaw == 0) {
                            stringResource(Res.string.appl_measure_recent)
                        } else {
                            pluralStringResource(Res.plurals.appl_measure_waiting, delay.monthsSinceLaw, delay.monthsSinceLaw)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                    )
                    measure.plannedMonth?.let { month ->
                        val label = monthLabel(month)
                        Text(
                            stringResource(if (delay.announcedMonthPassed) Res.string.appl_measure_announced_passed else Res.string.appl_measure_announced, label),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delay.announcedMonthPassed) palette.accentInk else palette.inkMuted,
                        )
                    }
                }
                if (measure.plannedMonth == null) measure.observation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
            }
            MeasureState.APPLIED -> {
                measure.observation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
                measure.delayDays?.let { Text(pluralStringResource(Res.plurals.appl_measure_delay, it, it), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
                measure.decree?.let { decree -> LinkLine(stringResource(Res.string.appl_measure_decree), Lucide.ExternalLink) { leave(ApplicationFormat.legifranceUrl(decree)) } }
            }
            MeasureState.NOT_NEEDED -> measure.observation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        }
    }
}

/**
 * On the sheet of a bill of "Voir" (and on a promise that goes through it): where its measures of application
 * stand, read from the tag `ppf-official-id` (`an-dossier:<uid>`) of the sheet itself and from the packages on
 * the device only; nothing for a bill not yet promulgated that the packages do not know.
 */
@Composable
fun BillApplication(text: LegislativeText, parliament: ParliamentState, modifier: Modifier = Modifier, showTitle: Boolean = false, onOpen: () -> Unit = {}) {
    val dossier = ParliamentLinks.dossierId(text.article.event) ?: return
    val palette = PpfTheme.palette
    val snapshot = parliament.snapshot
    LaunchedEffect(snapshot) { snapshot?.let { parliament.application.refresh(it) } }
    val law = parliament.application.holding?.catalog?.ofDossier(dossier)
    if (law == null && text.stage != TextStage.PROMULGATED) return
    Column(modifier.fillMaxWidth().testTag(ApplicationTags.BILL), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.appl_bill_title))
        if (showTitle) VoirTitle(text.article.title, style = MaterialTheme.typography.titleSmall)
        if (law != null) {
            Figures(law, parliament.today)
            PpfSecondaryButton(
                onClick = {
                    if (!parliament.isOpen) parliament.open()
                    parliament.show(ParliamentPage.ApplicationOf(law.id))
                    onOpen()
                },
                text = stringResource(Res.string.appl_bill_open),
                modifier = Modifier.testTag(ApplicationTags.BILL_OPEN),
            )
        } else {
            Text(stringResource(Res.string.appl_bill_not_held), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            PpfSecondaryButton(
                onClick = {
                    if (!parliament.isOpen) parliament.open()
                    parliament.show(ParliamentPage.Application)
                    onOpen()
                },
                text = stringResource(Res.string.appl_bill_open_meter),
                modifier = Modifier.testTag(ApplicationTags.BILL_OPEN),
            )
        }
    }
}

private fun day(iso: String): String = CivilDate.parseIso(iso)?.toFrench() ?: iso

/** `2027-03` to « mars 2027 ». */
private fun monthLabel(month: String): String {
    val year = month.take(4).toIntOrNull() ?: return month
    val number = month.drop(5).toIntOrNull()?.takeIf { it in 1..12 } ?: return month
    return CivilDate(year, number, 1).monthYearFrench()
}
