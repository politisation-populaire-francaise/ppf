package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.parlement.BallotEntry
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Member
import org.politisationpopulairefrancaise.core.voir.parlement.MemberVote
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentCatalog
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.SectionHeader
import org.politisationpopulairefrancaise.ui.voir.VoirTitle

/** How many latest votes a member's page and the list show. */
private const val LATEST = 20

/**
 * « Parlementaires » (D-065): every member of the Assemblée nationale, the Sénat and the European
 * Parliament elected in France, from the open data of these assemblies, verified on the device.
 * Its own stack of pages: the back button (or the system's) closes the page on top, then the
 * screen. Identical treatment: the same sections on every member's page, lists sorted by name,
 * positions in words and in neutral colours, never a ranking by participation.
 *
 * [voirSheetFor] gives, for an official identifier, the action that opens the member's sheet in
 * "Voir" when there is one.
 */
@Composable
fun ParliamentScreen(
    state: ParliamentState,
    modifier: Modifier = Modifier,
    voirSheetFor: (String) -> (() -> Unit)? = { null },
) {
    SystemBack(enabled = state.isOpen) { state.back() }
    val palette = PpfTheme.palette
    BoxWithConstraints(modifier.fillMaxSize().background(palette.surface1).testTag(ParliamentTags.SCREEN)) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 960.dp -> 2
            else -> 3
        }
        val padding = if (maxWidth < 600.dp) 16.dp else 24.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = padding - 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.parl_back), onClick = { state.back() }, modifier = Modifier.testTag(ParliamentTags.BACK), tint = palette.ink)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.parl_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            HorizontalDivider(color = palette.border)
            val snapshot = state.snapshot
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    snapshot == null && (state.loading || state.opening == null) -> Text(
                        stringResource(Res.string.parl_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.inkMuted,
                        modifier = Modifier.padding(padding).testTag(ParliamentTags.LOADING),
                    )
                    snapshot == null -> NothingPage(state, padding)
                    else -> when (val page = state.current) {
                        null -> ListPage(state, snapshot, columns, padding)
                        is ParliamentPage.OfMember -> key(page) { MemberPage(state, snapshot, page.id, padding, voirSheetFor) }
                        is ParliamentPage.OfBallot -> key(page) { BallotPage(state, snapshot, page.id, padding) }
                        ParliamentPage.Week -> key(page) { ParliamentWeekPage(state, padding) }
                        ParliamentPage.MyVotes -> key(page) { MyVotesPage(state, snapshot, padding) }
                        ParliamentPage.Presence -> key(page) { PresencePage(snapshot, padding) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NothingPage(state: ParliamentState, padding: Dp) {
    Column(Modifier.padding(padding).widthIn(max = 720.dp).testTag(ParliamentTags.NOTHING), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.parl_nothing), style = MaterialTheme.typography.bodyLarge)
        PpfSecondaryButton(onClick = { state.load() }, text = stringResource(Res.string.parl_retry), icon = Lucide.RefreshCw)
        if ((state.opening as? ParliamentOpening.Nothing)?.refused != null) {
            Text(stringResource(Res.string.parl_refused), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted, modifier = Modifier.testTag(ParliamentTags.REFUSED))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List of the members

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListPage(state: ParliamentState, snapshot: ParliamentSnapshot, columns: Int, padding: Dp) {
    val catalog = snapshot.catalog
    val palette = PpfTheme.palette
    val membersRef = snapshot.membersRef
    val membersHeld = snapshot.isHeld(ParliamentFormat.MEMBERS_PACKAGE)
    val members = catalog.members(state.query, state.chamberFilter, state.groupFilter)
    LazyColumn(Modifier.fillMaxSize().testTag(ParliamentTags.LIST)) {
        item(key = "provenance") {
            Column(Modifier.padding(start = padding, end = padding, top = padding).widthIn(max = 1100.dp)) {
                ProvenanceBlock(snapshot, state.today, chambers = null)
                DownloadProblem(state.lastOutcome)
            }
        }
        state.week?.let { week -> item(key = "week") { ParliamentWeekEntry(week, Modifier.padding(horizontal = padding, vertical = 12.dp).widthIn(max = 720.dp)) } }
        item(key = "my-votes") { MyVotesEntry(state, Modifier.padding(horizontal = padding).padding(bottom = 12.dp).widthIn(max = 720.dp)) }
        item(key = "presence") { PresenceEntry(state, Modifier.padding(horizontal = padding).padding(bottom = 12.dp).widthIn(max = 720.dp)) }
        if (!membersHeld || membersRef != null && membersRef.name in snapshot.outdated) {
            item(key = "members-download") {
                Column(Modifier.padding(horizontal = padding, vertical = 12.dp).widthIn(max = 720.dp)) {
                    if (!membersHeld) {
                        Text(stringResource(Res.string.parl_members_needed_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(Res.string.parl_members_needed_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (membersRef != null) {
                        val size = fileSize(membersRef.bytes)
                        PpfPrimaryButton(
                            onClick = { state.download(membersRef.name) },
                            text = if (state.downloading == membersRef.name) stringResource(Res.string.parl_downloading) else stringResource(if (membersHeld) Res.string.parl_update_members else Res.string.parl_download_members, size),
                            enabled = state.downloading == null,
                            icon = Lucide.RefreshCw,
                            modifier = Modifier.testTag(ParliamentTags.DOWNLOAD_MEMBERS),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(Res.string.parl_download_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    }
                }
            }
        }
        if (membersHeld) {
            item(key = "filters") {
                Column(Modifier.padding(horizontal = padding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(state.query, onChange = { state.query = it }, modifier = Modifier.widthIn(max = 720.dp))
                    Text(stringResource(Res.string.parl_filter_chamber), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PpfSelectableChip(stringResource(Res.string.parl_filter_all), selected = state.chamberFilter == null, onClick = { state.selectChamber(null) }, modifier = Modifier.testTag(ParliamentTags.chamber("all")))
                        catalog.chambers().forEach { chamber ->
                            PpfSelectableChip(stringResource(chamber.label), selected = state.chamberFilter == chamber, onClick = { state.selectChamber(chamber) }, modifier = Modifier.testTag(ParliamentTags.chamber(ParliamentCatalog.sourceId(chamber))))
                        }
                    }
                    val chamber = state.chamberFilter
                    if (chamber != null) {
                        Text(stringResource(Res.string.parl_filter_group), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PpfSelectableChip(stringResource(Res.string.parl_filter_all_groups), selected = state.groupFilter == null, onClick = { state.groupFilter = null }, modifier = Modifier.testTag(ParliamentTags.group("all")))
                            catalog.groups(chamber).forEach { group ->
                                PpfSelectableChip(group.label.removePrefix(ParliamentCatalog.EXAMPLE_MARKER).trim(), selected = state.groupFilter == group.id, onClick = { state.groupFilter = group.id }, modifier = Modifier.testTag(ParliamentTags.group(group.id)))
                            }
                        }
                    }
                    Text(
                        if (members.isEmpty()) stringResource(Res.string.parl_members_none) else stringResource(Res.string.parl_members_count, count(members.size)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                    )
                }
            }
            val rows = members.chunked(columns)
            items(rows.size, key = { "row-" + rows[it].first().id }) { index ->
                Row(
                    Modifier.padding(horizontal = padding, vertical = 6.dp).widthIn(max = 1100.dp).fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rows[index].forEach { member ->
                        MemberCard(member, catalog, onOpen = { state.show(ParliamentPage.OfMember(member.id)) }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - rows[index].size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item(key = "ballots") {
            BallotsSection(state, snapshot, padding)
        }
    }
}

@Composable
private fun DownloadProblem(outcome: PackageOutcome?) {
    val text = when (outcome) {
        is PackageOutcome.Refused -> stringResource(Res.string.parl_download_refused)
        is PackageOutcome.Unavailable -> stringResource(Res.string.parl_download_unavailable)
        is PackageOutcome.Unknown -> stringResource(Res.string.parl_download_unknown)
        PackageOutcome.Done, null -> return
    }
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.ink)
}

@Composable
private fun MemberCard(member: Member, catalog: ParliamentCatalog, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val group = catalog.group(member.group)
    PpfCard(modifier = modifier.testTag(ParliamentTags.member(member.id)), onClick = onOpen) {
        VoirTitle(member.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(member.chamber.role), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        if (group != null) Text(groupName(group.label, group.abbreviation), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(member.territoryName ?: member.territory, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun groupName(label: String, abbreviation: String?): String {
    val clean = label.removePrefix(ParliamentCatalog.EXAMPLE_MARKER).trim()
    return if (abbreviation.isNullOrBlank()) clean else "$clean ($abbreviation)"
}

/** The months of ballots, downloaded on a gesture with their size shown, and the latest ballots on the device. */
@Composable
private fun BallotsSection(state: ParliamentState, snapshot: ParliamentSnapshot, padding: Dp) {
    val palette = PpfTheme.palette
    val months = Chamber.entries.flatMap { snapshot.months(it) }
    val ballots = snapshot.catalog.ballots()
    Column(Modifier.padding(padding).widthIn(max = 760.dp).testTag(ParliamentTags.section("ballots")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.parl_ballots_title))
        Text(stringResource(Res.string.parl_ballots_intro), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        months.forEach { month ->
            val label = stringResource(month.chamber.label) + ", " + monthLabel(month.month)
            val held = snapshot.isHeld(month.name)
            val outdated = month.name in snapshot.outdated
            if (held && !outdated) {
                Text(stringResource(Res.string.parl_month_held, label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ParliamentTags.month(month.name)))
            } else {
                PpfSecondaryButton(
                    onClick = { state.download(month.name) },
                    text = if (state.downloading == month.name) stringResource(Res.string.parl_downloading) else stringResource(if (outdated) Res.string.parl_month_update else Res.string.parl_month_download, label, fileSize(month.ref.bytes)),
                    enabled = state.downloading == null,
                    modifier = Modifier.testTag(ParliamentTags.month(month.name)),
                )
            }
        }
        if (months.isNotEmpty()) Text(stringResource(Res.string.parl_download_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        if (ballots.isEmpty()) {
            Text(stringResource(Res.string.parl_ballots_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        } else {
            ballots.take(LATEST).forEach { entry ->
                BallotRow(ParliamentTags.ballot(entry.ballot.id), entry.ballot.title, entry.ballot.number, entry.ballot.date) { state.show(ParliamentPage.OfBallot(entry.ballot.id)) }
            }
            if (ballots.size > LATEST) Text(stringResource(Res.string.parl_ballots_more, count(ballots.size - LATEST)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

/** The search field of the list: every word must match, accents and case ignored. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val label = stringResource(Res.string.parl_search_label)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
        cursorBrush = SolidColor(palette.brand),
        modifier = modifier.fillMaxWidth().testTag(ParliamentTags.SEARCH).semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(palette.surface2, MaterialTheme.shapes.small).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(stringResource(Res.string.parl_search_hint), style = MaterialTheme.typography.bodyLarge, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) {
                    PpfIconButton(Lucide.X, stringResource(Res.string.parl_search_clear), onClick = { onChange("") }, modifier = Modifier.testTag(ParliamentTags.SEARCH_CLEAR))
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Page of a member

@Composable
private fun MemberPage(state: ParliamentState, snapshot: ParliamentSnapshot, id: String, padding: Dp, voirSheetFor: (String) -> (() -> Unit)?) {
    val palette = PpfTheme.palette
    val catalog = snapshot.catalog
    val member = catalog.member(id)
    val leave = rememberLeave()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(ParliamentTags.MEMBER_PAGE)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (member == null) {
                Text(stringResource(Res.string.parl_unknown_member, id), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                ProvenanceBlock(snapshot, state.today, chambers = null)
                return@Column
            }
            Column {
                VoirTitle(member.name, style = MaterialTheme.typography.headlineSmall, heading = true)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(member.chamber.role), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
            }
            Column(Modifier.testTag(ParliamentTags.section("identity")), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Fact(stringResource(Res.string.parl_member_chamber, stringResource(member.chamber.label)))
                val group = catalog.group(member.group)
                Fact(if (group != null) stringResource(Res.string.parl_member_group, groupName(group.label, group.abbreviation)) else stringResource(Res.string.parl_member_no_group))
                val territory = member.territoryName?.let { "$it (${member.territory})" } ?: member.territory
                Fact(stringResource(Res.string.parl_member_territory, territory))
                member.since?.let { Fact(stringResource(Res.string.parl_member_since, frenchDate(it))) }
                member.nationalParty?.let { Fact(stringResource(Res.string.parl_member_party, it.removePrefix(ParliamentCatalog.EXAMPLE_MARKER).trim())) }
            }
            ParticipationSection(catalog, member)
            VotesSection(state, catalog, member)
            Column(Modifier.testTag(ParliamentTags.section("links")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(Res.string.parl_links_title))
                voirSheetFor(member.id)?.let { open -> LinkLine(stringResource(Res.string.parl_voir_sheet), Lucide.User, Modifier.testTag(ParliamentTags.VOIR_SHEET_LINK), onClick = open) }
                LinkLine(stringResource(Res.string.parl_official_page), Lucide.ExternalLink, Modifier.testTag(ParliamentTags.OFFICIAL_LINK)) { leave(member.url) }
                val interests = member.interestsUrl
                if (interests != null) {
                    LinkLine(stringResource(Res.string.parl_interests), Lucide.ExternalLink, Modifier.testTag(ParliamentTags.INTERESTS_LINK)) { leave(interests) }
                } else {
                    Text(stringResource(Res.string.parl_interests_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }
            ProvenanceBlock(snapshot, state.today, chambers = listOf(member.chamber))
        }
    }
}

@Composable
private fun Fact(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** On every page, with its definition; the Sénat's votes are not published in a reusable form yet. */
@Composable
private fun ParticipationSection(catalog: ParliamentCatalog, member: Member) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(ParliamentTags.PARTICIPATION), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(Res.string.parl_participation_title))
        val participation = if (member.chamber == Chamber.SENAT) null else catalog.participation(member.id)
        when {
            member.chamber == Chamber.SENAT -> Text(stringResource(Res.string.parl_senat_soon), style = MaterialTheme.typography.titleSmall)
            participation == null -> Text(stringResource(Res.string.parl_participation_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            else -> {
                Text(stringResource(Res.string.parl_participation_value, participation.percent.toString()), style = MaterialTheme.typography.headlineSmall, color = palette.ink)
                Text(
                    stringResource(
                        Res.string.parl_participation_detail,
                        count(participation.voted),
                        count(participation.total),
                        participation.from?.toFrench().orEmpty(),
                        participation.to?.toFrench().orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(stringResource(Res.string.parl_participation_definition), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(ParliamentTags.PARTICIPATION_DEFINITION))
        Text(stringResource(Res.string.parl_participation_no_ranking), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun VotesSection(state: ParliamentState, catalog: ParliamentCatalog, member: Member) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(ParliamentTags.section("votes")), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.parl_votes_title))
        if (member.chamber == Chamber.SENAT) {
            Text(stringResource(Res.string.parl_senat_soon), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.parl_senat_soon_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            return@Column
        }
        val votes = catalog.votesOf(member.id)
        if (votes.isEmpty()) Text(stringResource(Res.string.parl_votes_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        votes.take(LATEST).forEach { vote ->
            BallotRow(ParliamentTags.vote(vote.ballot.id), vote.ballot.title, vote.ballot.number, vote.ballot.date, extra = { VoteDetail(vote) }) {
                state.show(ParliamentPage.OfBallot(vote.ballot.id))
            }
        }
    }
}

/** The recorded position in words, then what the source adds: delegation, reason, correction. Neutral colours only. */
@Composable
internal fun VoteDetail(vote: MemberVote, showPosition: Boolean = true) {
    val palette = PpfTheme.palette
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPosition) Text(stringResource(Res.string.parl_vote_position, stringResource(vote.position.label)), style = MaterialTheme.typography.labelLarge, color = palette.ink)
        if (vote.delegated) {
            if (showPosition) Spacer(Modifier.width(8.dp))
            PpfBadge(stringResource(Res.string.parl_vote_delegated), BadgeStyle.NEUTRAL)
        }
    }
    vote.notVotingCause?.let { Text(stringResource(Res.string.parl_vote_cause, causeLabel(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
    vote.correction?.let { correction ->
        Text(stringResource(correction.kind.text, stringResource(correction.intended.label)), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        correction.date?.let { Text(stringResource(Res.string.parl_correction_date, frenchDate(it)), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
    }
}

// ---------------------------------------------------------------------------------------------
// Page of a ballot

@Composable
private fun BallotPage(state: ParliamentState, snapshot: ParliamentSnapshot, id: String, padding: Dp) {
    val palette = PpfTheme.palette
    val catalog = snapshot.catalog
    val entry = catalog.ballot(id)
    val leave = rememberLeave()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).testTag(ParliamentTags.BALLOT_PAGE)) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (entry == null) {
                Text(stringResource(Res.string.parl_ballots_none), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
                return@Column
            }
            val ballot = entry.ballot
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VoirTitle(ballot.title, style = MaterialTheme.typography.headlineSmall, heading = true)
                val line = listOfNotNull(stringResource(entry.chamber.label), kindLabel(ballot.kind), stringResource(Res.string.parl_ballot_line, ballot.number.toString(), frenchDate(ballot.date))).joinToString(" · ")
                Text(line, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                val result = when (ballot.adopted) {
                    true -> Res.string.parl_result_adopted
                    false -> Res.string.parl_result_rejected
                    null -> Res.string.parl_result_unknown
                }
                Text(stringResource(result), style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.testTag(ParliamentTags.section("result")))
            }
            CountsSection(entry)
            GroupsTable(entry, catalog)
            PositionsSection(state, entry, catalog)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ballot.url?.let { url -> LinkLine(stringResource(Res.string.parl_ballot_official), Lucide.ExternalLink, Modifier.testTag(ParliamentTags.OFFICIAL_LINK)) { leave(url) } }
                ballot.dossierUrl?.let { url -> LinkLine(stringResource(Res.string.parl_ballot_dossier), Lucide.ScrollText, Modifier.testTag(ParliamentTags.DOSSIER_LINK)) { leave(url) } }
            }
            ProvenanceBlock(snapshot, state.today, chambers = listOf(entry.chamber))
        }
    }
}

@Composable
private fun CountsSection(entry: BallotEntry) {
    val counts = entry.ballot.counts
    Column(Modifier.testTag(ParliamentTags.section("counts")), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionHeader(stringResource(Res.string.parl_counts_title))
        counts.voters?.let { Fact(stringResource(Res.string.parl_count_voters, count(it))) }
        counts.expressed?.let { Fact(stringResource(Res.string.parl_count_expressed, count(it))) }
        Fact(stringResource(Res.string.parl_count_for, count(counts.inFavour)))
        Fact(stringResource(Res.string.parl_count_against, count(counts.against)))
        Fact(stringResource(Res.string.parl_count_abstention, count(counts.abstention)))
        counts.notVoting?.let { Fact(stringResource(Res.string.parl_count_not_voting, count(it))) }
        if (entry.chamber == Chamber.PE) Text(stringResource(Res.string.parl_pe_scope), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkMuted)
    }
}

/** The count by group as a table; each row is read as one sentence by screen readers. */
@Composable
private fun GroupsTable(entry: BallotEntry, catalog: ParliamentCatalog) {
    val rows = entry.ballot.groups
    if (rows.isEmpty()) return
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(ParliamentTags.GROUP_TABLE)) {
        SectionHeader(stringResource(Res.string.parl_groups_title))
        val headers = listOf(stringResource(Res.string.parl_groups_column)) + Position.entries.map { stringResource(it.label) }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics { }) {
            headers.forEachIndexed { index, header ->
                Text(header, style = MaterialTheme.typography.labelMedium, color = palette.inkMuted, modifier = Modifier.weight(if (index == 0) 2f else 1f))
            }
        }
        HorizontalDivider(color = palette.border)
        rows.sortedBy { ParliamentCatalog.sortKey(catalog.group(it.group)?.label ?: it.group) }.forEach { row ->
            val group = catalog.group(row.group)
            val name = group?.let { groupName(it.label, it.abbreviation) } ?: row.group
            val sentence = stringResource(Res.string.parl_group_row, name, count(row.inFavour), count(row.against), count(row.abstention), count(row.notVoting))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp).testTag(ParliamentTags.groupRow(row.group)).clearAndSetSemantics { contentDescription = sentence },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
                listOf(row.inFavour, row.against, row.abstention, row.notVoting).forEach { Text(count(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
            }
            HorizontalDivider(color = palette.border)
        }
    }
}

@Composable
private fun PositionsSection(state: ParliamentState, entry: BallotEntry, catalog: ParliamentCatalog) {
    val palette = PpfTheme.palette
    val positions = entry.ballot.positions
    Column(Modifier.fillMaxWidth().testTag(ParliamentTags.section("positions")), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(Res.string.parl_by_position_title))
        catalog.membersByPosition(entry.ballot).forEach { (position, members) ->
            Text(
                stringResource(Res.string.parl_position_count, stringResource(position.label), count(members.size)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 6.dp).semantics { heading() }.testTag(ParliamentTags.position(position)),
            )
            if (members.isEmpty()) Text(stringResource(Res.string.parl_position_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            members.forEach { item ->
                val notes = listOfNotNull(
                    stringResource(Res.string.parl_vote_delegated).takeIf { item.id in positions.delegated },
                    stringResource(Res.string.parl_with_correction).takeIf { positions.corrections.any { c -> c.member == item.id } },
                    positions.notVotingCauses.firstOrNull { it.member == item.id }?.let { causeLabel(it.cause) },
                )
                val name = item.member?.name?.removePrefix(ParliamentCatalog.EXAMPLE_MARKER)?.trim() ?: stringResource(Res.string.parl_unknown_member, item.id)
                val text = (listOf(name) + notes).joinToString(" · ")
                if (item.member != null) {
                    Surface(
                        onClick = { state.show(ParliamentPage.OfMember(item.id)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(ParliamentTags.member(item.id)),
                        shape = MaterialTheme.shapes.small,
                        color = palette.surface1,
                        contentColor = palette.ink,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }
        }
    }
}
