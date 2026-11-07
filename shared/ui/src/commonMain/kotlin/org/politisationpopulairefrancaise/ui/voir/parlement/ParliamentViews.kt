package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.CorrectionKind
import org.politisationpopulairefrancaise.core.voir.parlement.DataOrigin
import org.politisationpopulairefrancaise.core.voir.parlement.DataSource
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentCatalog
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.leavingHost
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirTitle

/** Test tags of the parliamentary screens, stable for the interface tests. */
object ParliamentTags {
    const val CARD = "parl-card"
    const val SCREEN = "parl-screen"
    const val BACK = "parl-back"
    const val LIST = "parl-list"
    const val SEARCH = "parl-search"
    const val SEARCH_CLEAR = "parl-search-clear"
    const val EXAMPLE = "parl-example"
    const val DATA_DATE = "parl-data-date"
    const val STALE = "parl-stale"
    const val REFUSED = "parl-refused"
    const val ATTRIBUTION = "parl-attribution"
    const val NOTHING = "parl-nothing"
    const val LOADING = "parl-loading"
    const val DOWNLOAD_MEMBERS = "parl-download-members"
    const val MEMBER_PAGE = "parl-member-page"
    const val BALLOT_PAGE = "parl-ballot-page"
    const val PARTICIPATION = "parl-participation"
    const val PARTICIPATION_DEFINITION = "parl-participation-definition"
    const val GROUP_TABLE = "parl-group-table"
    const val OFFICIAL_LINK = "parl-official-link"
    const val INTERESTS_LINK = "parl-interests-link"
    const val VOIR_SHEET_LINK = "parl-voir-sheet-link"
    const val DOSSIER_LINK = "parl-dossier-link"
    const val PERSON_LINK = "parl-person-link"
    const val BILL_VOTES = "parl-bill-votes"
    const val BILL_OPEN = "parl-bill-open"
    val LEAVE = LeaveDialogTags(dialog = "parl-leave", stay = "parl-leave-stay", open = "parl-leave-open")
    fun member(id: String) = "parl-member-$id"
    fun ballot(id: String) = "parl-ballot-$id"
    fun vote(ballotId: String) = "parl-vote-$ballotId"
    fun month(name: String) = "parl-month-$name"
    fun chamber(id: String) = "parl-chamber-$id"
    fun group(id: String) = "parl-group-$id"
    fun groupRow(id: String) = "parl-group-row-$id"
    fun position(position: Position) = "parl-position-${position.name.lowercase()}"
    fun section(id: String) = "parl-section-$id"
}

/**
 * The entry of the parliamentary open data on the axis « Le pouvoir » of "Voir", right after the
 * section « Responsables publics »: one card, one touch.
 */
@Composable
fun OfficialParliamentCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.fillMaxWidth().testTag(ParliamentTags.CARD), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.parl_card_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.parl_card_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.parl_card_open), style = MaterialTheme.typography.labelLarge, color = palette.brand)
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * On the sheet of a public person of "Voir": a link to the member's parliamentary page, read from
 * the tag `ppf-official-id` of the sheet itself; nothing when the tag is missing.
 */
@Composable
fun PersonOfficialVotesLink(person: PublicPerson, parliament: ParliamentState, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val id = ParliamentLinks.memberId(person.article.event) ?: return
    LinkLine(stringResource(Res.string.parl_person_link), Lucide.Vote, modifier.testTag(ParliamentTags.PERSON_LINK)) {
        parliament.openMember(id)
        onOpen()
    }
}

/**
 * On the sheet of a bill of "Voir": the public ballots of its legislative file found on the device,
 * read from the tag `ppf-official-id` (`an-dossier:<uid>`) of the sheet itself; nothing when the
 * tag is missing.
 */
@Composable
fun BillOfficialVotes(text: LegislativeText, parliament: ParliamentState, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val dossier = ParliamentLinks.dossierId(text.article.event) ?: return
    val palette = PpfTheme.palette
    val entries = parliament.snapshot?.catalog?.ballotsOfDossier(dossier).orEmpty()
    Column(modifier.fillMaxWidth().testTag(ParliamentTags.BILL_VOTES), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.parl_bill_title))
        if (entries.isEmpty()) {
            Text(stringResource(Res.string.parl_bill_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            PpfSecondaryButton(
                onClick = {
                    parliament.open()
                    onOpen()
                },
                text = stringResource(Res.string.parl_bill_open),
                modifier = Modifier.testTag(ParliamentTags.BILL_OPEN),
            )
        } else {
            entries.forEach { entry ->
                BallotRow(ParliamentTags.ballot(entry.ballot.id), entry.ballot.title, entry.ballot.number, entry.ballot.date) {
                    if (!parliament.isOpen) parliament.open()
                    parliament.show(ParliamentPage.OfBallot(entry.ballot.id))
                    onOpen()
                }
            }
        }
    }
}

/** A ballot on one line: its title, its number and its date. */
@Composable
internal fun BallotRow(tag: String, title: String, number: Int, date: String, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        shape = MaterialTheme.shapes.small,
        color = palette.surface2,
        contentColor = palette.ink,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Vote, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                VoirTitle(title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                Text(stringResource(Res.string.parl_ballot_line, number.toString(), frenchDate(date)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                extra()
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/** A tappable line with an icon, to a page of the screen or outside PPF. */
@Composable
internal fun LinkLine(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.Button }, shape = MaterialTheme.shapes.small, color = palette.surface1, contentColor = palette.ink) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Where the data come from, on every page: the [EXEMPLE] banner when the index says so, the day of
 * the data and the warning after two weeks, a refused update, then for each assembly concerned the
 * attribution its licence asks for, never presented as official.
 */
@Composable
internal fun ProvenanceBlock(snapshot: ParliamentSnapshot, today: CivilDate, chambers: List<Chamber>?, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshot.example) {
            Surface(Modifier.fillMaxWidth().testTag(ParliamentTags.EXAMPLE), shape = MaterialTheme.shapes.small, color = palette.warningTint, contentColor = palette.ink) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.TriangleExclamationPoint, contentDescription = null, tint = palette.warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(Res.string.parl_example_banner), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val date = snapshot.importDate?.toFrench() ?: snapshot.index.importedOn
        Text(stringResource(Res.string.parl_data_of, date), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.testTag(ParliamentTags.DATA_DATE))
        if (snapshot.isStale(today)) {
            InfoBox(Lucide.Clock, Modifier.testTag(ParliamentTags.STALE)) { Text(stringResource(Res.string.parl_stale, date), style = MaterialTheme.typography.bodyMedium) }
        }
        if (snapshot.refused != null) {
            InfoBox(Lucide.ShieldCheck, Modifier.testTag(ParliamentTags.REFUSED)) { Text(stringResource(Res.string.parl_refused), style = MaterialTheme.typography.bodyMedium) }
        }
        when (snapshot.origin) {
            DataOrigin.DEVICE -> Text(stringResource(Res.string.parl_offline), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            DataOrigin.BUNDLED -> Text(stringResource(Res.string.parl_bundled), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            DataOrigin.SERVER -> Unit
        }
        val ids = chambers?.map(ParliamentCatalog::sourceId)
        val sources = snapshot.index.sources.filter { ids == null || it.id in ids }
        Column(Modifier.fillMaxWidth().testTag(ParliamentTags.ATTRIBUTION), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sources.forEach { source -> Text(attribution(source), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        }
    }
}

@Composable
internal fun attribution(source: DataSource): String {
    val updated = CivilDate.parseIso(source.updatedOn)?.toFrench() ?: source.updatedOn
    val of = when (source.id) {
        "an" -> stringResource(Res.string.parl_of_an)
        "senat" -> stringResource(Res.string.parl_of_senat)
        "pe" -> stringResource(Res.string.parl_of_pe)
        else -> null
    }
    return if (of != null) stringResource(Res.string.parl_attribution, of, updated, source.licence) else stringResource(Res.string.parl_attribution_other, source.name, updated, source.licence)
}

/** "Vous quittez PPF" before any page outside: returns the function that asks. */
@Composable
internal fun rememberLeave(): (String) -> Unit {
    var pending by remember { mutableStateOf<String?>(null) }
    pending?.let { url ->
        LeavePpfDialog(
            url = url,
            title = stringResource(Res.string.parl_leave_title),
            text = stringResource(Res.string.parl_leave_text, leavingHost(url)),
            stayLabel = stringResource(Res.string.parl_leave_stay),
            openLabel = stringResource(Res.string.parl_leave_open),
            onStay = { pending = null },
            onDone = { pending = null },
            tags = ParliamentTags.LEAVE,
        )
    }
    return { url -> pending = url }
}

@Composable
internal fun fileSize(bytes: Long): String = if (bytes < 1_000_000) {
    stringResource(Res.string.parl_size_ko, ((bytes + 999) / 1_000).coerceAtLeast(1).toString())
} else {
    stringResource(Res.string.parl_size_mo, FrenchNumbers.format(bytes / 1_000_000.0))
}

internal fun frenchDate(iso: String): String = CivilDate.parseIso(iso)?.toFrench() ?: iso

internal fun monthLabel(month: String): String = CivilDate.parseIso("$month-01")?.monthYearFrench() ?: month

internal fun count(value: Int): String = FrenchNumbers.format(value.toDouble())

internal val Chamber.label: StringResource get() = when (this) {
    Chamber.AN -> Res.string.parl_chamber_an
    Chamber.SENAT -> Res.string.parl_chamber_senat
    Chamber.PE -> Res.string.parl_chamber_pe
}

internal val Chamber.role: StringResource get() = when (this) {
    Chamber.AN -> Res.string.parl_role_an
    Chamber.SENAT -> Res.string.parl_role_senat
    Chamber.PE -> Res.string.parl_role_pe
}

internal val Position.label: StringResource get() = when (this) {
    Position.FOR -> Res.string.parl_position_for
    Position.AGAINST -> Res.string.parl_position_against
    Position.ABSTENTION -> Res.string.parl_position_abstention
    Position.NOT_VOTING -> Res.string.parl_position_not_voting
}

internal val CorrectionKind.text: StringResource get() = when (this) {
    CorrectionKind.STATEMENT -> Res.string.parl_correction_statement
    CorrectionKind.MALFUNCTION -> Res.string.parl_correction_malfunction
}

/** The reason of a non-vote in words; the code itself when it is unknown. */
@Composable
internal fun causeLabel(code: String): String = when (code.uppercase()) {
    "MG" -> stringResource(Res.string.parl_cause_mg)
    "PAN" -> stringResource(Res.string.parl_cause_pan)
    "PSE" -> stringResource(Res.string.parl_cause_pse)
    else -> code
}

/** The kind of a ballot in words; the code itself when it is unknown. */
@Composable
internal fun kindLabel(code: String?): String? = when (code?.uppercase()) {
    null -> null
    "SPS" -> stringResource(Res.string.parl_ballot_kind_sps)
    "SPO" -> stringResource(Res.string.parl_ballot_kind_spo)
    "MOC" -> stringResource(Res.string.parl_ballot_kind_moc)
    else -> code
}
