package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parrainages.MySponsors
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorMandate
import org.politisationpopulairefrancaise.core.voir.parrainages.Sponsorship
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsCatalog
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirTitle
import org.politisationpopulairefrancaise.ui.voir.withoutExample

/** Test tags of « Parrainages de la présidentielle 2027 ». */
object SponsorshipsTags {
    const val ENTRY = "spons-entry"
    const val PAGE = "spons-page"
    const val NOT_YET = "spons-not-yet"
    const val DOWNLOAD = "spons-download"
    const val MINE = "spons-mine"
    const val MINE_NONE = "spons-mine-none"
    const val DEPARTMENT = "spons-department"
    const val CANDIDATE_PAGE = "spons-candidate-page"
    fun candidate(name: String) = "spons-candidate-$name"
    fun line(index: Int) = "spons-line-$index"
}

/** The card on the list of the Parliament that opens « Parrainages de la présidentielle 2027 ». */
@Composable
internal fun SponsorshipsEntry(state: ParliamentState, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(SponsorshipsTags.ENTRY), onClick = { state.show(ParliamentPage.Sponsorships) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ScrollText, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.spons_entry_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.spons_entry_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * « Parrainages de la présidentielle 2027 » (VO-37): the rule of the law, then « Vos élus ont parrainé » (on the
 * device, from « Mes élus »), then every candidate in alphabetical order with the same three figures, and the
 * presentations of one department. No action towards any official, no verdict on any candidacy.
 */
@Composable
internal fun SponsorshipsPage(state: ParliamentState, snapshot: ParliamentSnapshot, padding: Dp) {
    val sponsorships = state.sponsorships
    val palette = PpfTheme.palette
    LaunchedEffect(snapshot) { sponsorships.refresh(snapshot); state.myVotes.load() }
    val catalog = sponsorships.holding?.catalog
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(SponsorshipsTags.PAGE)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.spons_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            InfoBox(Lucide.Landmark) {
                Text(stringResource(Res.string.spons_law), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(4.dp))
                Text(stringResource(Res.string.spons_only_cc), style = MaterialTheme.typography.bodyMedium)
            }
            val bytes = sponsorships.bytes(snapshot)
            if (bytes == null && catalog == null) {
                Text(stringResource(Res.string.spons_not_yet), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted, modifier = Modifier.testTag(SponsorshipsTags.NOT_YET))
            }
            if (bytes != null && (catalog == null || sponsorships.holding?.outdated == true)) {
                PpfPrimaryButton(
                    onClick = { sponsorships.download(snapshot) },
                    text = if (sponsorships.downloading) stringResource(Res.string.spons_downloading) else stringResource(if (catalog == null) Res.string.spons_download else Res.string.spons_update, fileSize(bytes)),
                    enabled = !sponsorships.downloading,
                    icon = Lucide.RefreshCw,
                    modifier = Modifier.testTag(SponsorshipsTags.DOWNLOAD),
                )
                Text(stringResource(Res.string.spons_download_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
            if (catalog != null) {
                Text(
                    stringResource(Res.string.spons_published_on, day(catalog.pkg.publishedOn), catalog.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
                MineSection(state, catalog)
                CandidatesSection(state, catalog)
                DepartmentSection(state, catalog)
            }
            Text(stringResource(Res.string.spons_source), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

@Composable
private fun MineSection(state: ParliamentState, catalog: SponsorshipsCatalog) {
    val mine = state.myVotes.mine ?: return
    val palette = PpfTheme.palette
    val found = remember(mine, catalog) { MySponsors.of(mine.officials, catalog) }
    Column(Modifier.fillMaxWidth().testTag(SponsorshipsTags.MINE), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.spons_mine_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (found.isEmpty()) {
            Text(stringResource(Res.string.spons_mine_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(SponsorshipsTags.MINE_NONE))
        }
        found.forEach { item ->
            Text(
                stringResource(Res.string.spons_mine_line, item.sponsorship.displayName, stringResource(item.sponsorship.mandate.label), item.sponsorship.candidate.withoutExample(), day(item.sponsorship.publishedOn)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CandidatesSection(state: ParliamentState, catalog: SponsorshipsCatalog) {
    val palette = PpfTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.spons_candidates), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        catalog.candidates().forEach { candidate ->
            PpfCard(modifier = Modifier.fillMaxWidth().testTag(SponsorshipsTags.candidate(candidate.candidate)), onClick = { state.show(ParliamentPage.SponsorshipsOf(candidate.candidate)) }) {
                VoirTitle(candidate.candidate, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.spons_candidate_figures, candidate.count, candidate.territories, candidate.mostInOneTerritory),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun DepartmentSection(state: ParliamentState, catalog: SponsorshipsCatalog) {
    val sponsorships = state.sponsorships
    val palette = PpfTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PpfTextField(
            value = sponsorships.department,
            onValueChange = { sponsorships.department = it.take(60) },
            placeholder = stringResource(Res.string.spons_department_field),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SponsorshipsTags.DEPARTMENT),
        )
        val typed = FrenchText.fold(sponsorships.department)
        if (typed.isEmpty()) return@Column
        val department = catalog.departments().firstOrNull { FrenchText.fold(it.first) == typed } ?: catalog.departments().firstOrNull { FrenchText.fold(it.first).startsWith(typed) }
        if (department == null) {
            Text(stringResource(Res.string.spons_department_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@Column
        }
        val list = catalog.inDepartment(department.first)
        Text(stringResource(Res.string.spons_department_title, department.first, list.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        list.forEachIndexed { index, item -> SponsorshipLine(item, index, withCandidate = true) }
    }
}

/** The presentations of one candidate, by department. */
@Composable
internal fun SponsorshipsOfCandidatePage(state: ParliamentState, candidate: String, padding: Dp) {
    val catalog = state.sponsorships.holding?.catalog
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(SponsorshipsTags.CANDIDATE_PAGE)) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.spons_candidate_title, candidate.withoutExample()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            val list = catalog?.of(candidate).orEmpty()
            var lastDepartment: String? = null
            list.forEachIndexed { index, item ->
                if (item.department != lastDepartment) {
                    lastDepartment = item.department
                    Text(item.department.withoutExample(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                }
                SponsorshipLine(item, index, withCandidate = false)
            }
        }
    }
}

@Composable
private fun SponsorshipLine(item: Sponsorship, index: Int, withCandidate: Boolean) {
    val palette = PpfTheme.palette
    val mandate = item.otherMandate ?: stringResource(item.mandate.label)
    val who = item.territory?.let { stringResource(Res.string.spons_line_territory, item.displayName, mandate, it) } ?: stringResource(Res.string.spons_line, item.displayName, mandate)
    Column(Modifier.fillMaxWidth().testTag(SponsorshipsTags.line(index))) {
        Text(who, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(item.candidate.withoutExample().takeIf { withCandidate }, stringResource(Res.string.spons_published, day(item.publishedOn))).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )
    }
}

private fun day(iso: String?): String = iso?.let { CivilDate.parseIso(it)?.toFrench() ?: it }.orEmpty()

internal val SponsorMandate.label: StringResource get() = when (this) {
    SponsorMandate.MAIRE -> Res.string.spons_mandate_maire
    SponsorMandate.MAIRE_DELEGUE -> Res.string.spons_mandate_maire_delegue
    SponsorMandate.MAIRE_ARRONDISSEMENT -> Res.string.spons_mandate_maire_arrondissement
    SponsorMandate.DEPUTE -> Res.string.spons_mandate_depute
    SponsorMandate.SENATEUR -> Res.string.spons_mandate_senateur
    SponsorMandate.DEPUTE_EUROPEEN -> Res.string.spons_mandate_depute_europeen
    SponsorMandate.CONSEILLER_DEPARTEMENTAL -> Res.string.spons_mandate_departemental
    SponsorMandate.CONSEILLER_REGIONAL -> Res.string.spons_mandate_regional
    SponsorMandate.CONSEILLER_PARIS -> Res.string.spons_mandate_paris
    SponsorMandate.CONSEILLER_METROPOLITAIN_LYON -> Res.string.spons_mandate_lyon
    SponsorMandate.CONSEILLER_CORSE -> Res.string.spons_mandate_corse
    SponsorMandate.CONSEILLER_AFE -> Res.string.spons_mandate_afe
    SponsorMandate.PRESIDENT_INTERCOMMUNALITE -> Res.string.spons_mandate_epci
    SponsorMandate.AUTRE -> Res.string.spons_mandate_other
}
