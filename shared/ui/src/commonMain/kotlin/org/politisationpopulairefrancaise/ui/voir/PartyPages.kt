package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.Chamber
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.PoliticalParty
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * The sheets of the political parties (axis of power; cahier fonctionnel M17; owner's decision of
 * 7 November 2026). Every party sheet shows the same sections in the same order, whatever the
 * party (règle « Traitement identique »). A member's facts stay on the member's own sheet: the
 * party sheet only shows the facts that link the party itself (no guilt by association).
 */

/** The fact sections of every party sheet, in this order, each with its title. */
internal val PARTY_SECTIONS: List<Pair<FactKind, StringResource>> = listOf(
    FactKind.PROMISE to Res.string.voir_party_promises,
    FactKind.VOTE to Res.string.voir_party_votes,
    FactKind.CASE to Res.string.voir_party_cases,
    FactKind.PUBLIC_MONEY to Res.string.voir_party_money,
)

internal val Chamber.label: StringResource get() = when (this) {
    Chamber.ASSEMBLY -> Res.string.voir_chamber_assembly
    Chamber.SENATE -> Res.string.voir_chamber_senate
    Chamber.EUROPEAN -> Res.string.voir_chamber_european
    Chamber.OTHER -> Res.string.voir_chamber_other
}

/** The short name of a party for a badge or a chip: its acronym, or its name. */
internal fun PoliticalParty.shortName(): String = acronym ?: name.withoutExample()

/** "Assemblée nationale : 120 · Sénat : 40", from the reference count of each assembly. */
@Composable
private fun seatsSummary(party: PoliticalParty): String =
    party.chambers.mapNotNull { chamber -> party.referenceSeats(chamber)?.let { stringResource(Res.string.voir_party_seats_line, stringResource(chamber.label), it.count) } }
        .joinToString(" · ")

@Composable
internal fun PartyCard(party: PoliticalParty, catalog: VoirCatalog, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(party.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            party.acronym?.let { PpfBadge(it, BadgeStyle.BRAND) }
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(party.name, maxLines = 2)
        party.article.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val seats = seatsSummary(party)
        if (seats.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(seats, style = MaterialTheme.typography.labelMedium, color = palette.ink)
        }
        val members = catalog.membersOf(party.address).size
        if (members > 0) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.voir_party_members_count, members), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
        }
    }
}

/** The party of a person, under the name on the sheet: one touch opens the party's sheet. */
@Composable
internal fun PartyChip(party: PoliticalParty, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val description = stringResource(Res.string.voir_party_of, party.name.withoutExample())
    Surface(
        onClick = onOpen,
        modifier = modifier.heightIn(min = 44.dp).testTag(VoirTags.PARTY_CHIP).semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = palette.brandTint,
        contentColor = palette.ink,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(party.name.withoutExample(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            party.acronym?.let {
                Spacer(Modifier.width(8.dp))
                PpfBadge(it, BadgeStyle.BRAND)
            }
        }
    }
}

/** The list of the parties, most seats first. */
@Composable
internal fun PartiesPage(catalog: VoirCatalog, actions: VoirActions, columns: Int) {
    ListIntro(stringResource(Res.string.voir_parties), stringResource(Res.string.voir_parties_intro))
    Grid(catalog.parties, columns) { party, modifier -> PartyCard(party, catalog, onOpen = { actions.open(party.address) }, modifier = modifier) }
}

/** The filter of the persons by party, under the filter by function. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartyFilter(catalog: VoirCatalog, state: VoirState) {
    val parties = catalog.partiesInUse()
    if (parties.isEmpty()) return
    Text(stringResource(Res.string.voir_party_filter), style = MaterialTheme.typography.labelLarge, color = PpfTheme.palette.inkMuted)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PpfSelectableChip(stringResource(Res.string.voir_party_filter_all), state.partyFilter == null, onClick = { state.partyFilter = null }, modifier = Modifier.testTag(VoirTags.filter("party-all")))
        parties.forEach { party ->
            val selected = state.partyFilter == party.address
            PpfSelectableChip(
                party.shortName(),
                selected,
                onClick = { state.partyFilter = if (selected) null else party.address },
                modifier = Modifier.testTag(VoirTags.filter("party-" + party.article.slug)),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun PartyPage(party: PoliticalParty, catalog: VoirCatalog, state: VoirState, actions: VoirActions, wide: Boolean, columns: Int) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    val facts = catalog.factsAbout(party.address)
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(party.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.voir_party_kind), style = MaterialTheme.typography.labelLarge, color = palette.inkSubtle)
            party.acronym?.let {
                Spacer(Modifier.width(8.dp))
                PpfBadge(it, BadgeStyle.BRAND)
            }
        }
        Spacer(Modifier.height(6.dp))
        VoirTitle(party.name, style = MaterialTheme.typography.headlineMedium, heading = true)
        party.article.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted) }
        party.founded?.let { Text(stringResource(Res.string.voir_party_founded, it.toFrench()), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle) }
        Spacer(Modifier.height(12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FollowButton(state.isFollowed(party.address), party.name, onToggle = { state.toggleFollow(party.address) })
            PpfSecondaryButton(onClick = actions.reply, text = stringResource(Res.string.voir_reply_button), small = true, icon = Lucide.MessageSquare, modifier = Modifier.testTag(VoirTags.REPLY_OPEN))
        }
        Spacer(Modifier.height(16.dp))
        InfoBox(Lucide.Info) { Text(stringResource(Res.string.voir_parties_intro), style = MaterialTheme.typography.bodyMedium) }

        // Presentation.
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().testTag(VoirTags.section("party-presentation"))) {
            SectionHeader(stringResource(Res.string.voir_party_presentation))
            MarkdownText(party.article.body)
            party.website?.let { url ->
                Spacer(Modifier.height(8.dp))
                LinkRow(stringResource(Res.string.voir_party_website) + " (" + hostOf(url) + ")", Lucide.ExternalLink, onClick = { open(url) })
            }
        }

        // Seats.
        Spacer(Modifier.height(16.dp))
        SeatsSection(party, wide)

        // Leaders, then the other members who have a sheet.
        val leaders = catalog.leadersOf(party)
        val members = catalog.membersOf(party.address).filter { member -> leaders.none { it.address == member.address } }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().testTag(VoirTags.section("party-leaders"))) {
            SectionHeader("${stringResource(Res.string.voir_party_leaders)} (${leaders.size})")
            if (leaders.isEmpty()) Text(stringResource(Res.string.voir_section_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            Grid(leaders, columns) { person, modifier -> PersonCard(person, catalog, onOpen = { actions.open(person.address) }, modifier = modifier, showFacts = false) }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().testTag(VoirTags.section("party-members"))) {
            SectionHeader("${stringResource(Res.string.voir_party_members)} (${members.size})")
            if (members.isEmpty()) Text(stringResource(Res.string.voir_party_members_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            Grid(members, columns) { person, modifier -> PersonCard(person, catalog, onOpen = { actions.open(person.address) }, modifier = modifier, showFacts = false) }
            Spacer(Modifier.height(8.dp))
            InfoBox(Lucide.Info) { Text(stringResource(Res.string.voir_party_members_note), style = MaterialTheme.typography.bodySmall) }
        }

        // The facts that concern the party itself, in the same sections for every party.
        val sections = PARTY_SECTIONS.map { (kind, title) -> Triple(kind, title, facts.filter { it.kind == kind }) }
        val factColumns = if (wide) 2 else 1
        sections.chunked(factColumns).forEach { row ->
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (kind, title, list) ->
                    Column(Modifier.weight(1f).testTag(VoirTags.section(kind.tagValue))) {
                        SectionHeader("${stringResource(title)} (${list.size})")
                        if (kind == FactKind.CASE && list.any { it.procedure?.presumedInnocent == true }) {
                            InfoBox(Lucide.Info, Modifier.padding(bottom = 8.dp).testTag(VoirTags.PRESUMPTION)) {
                                Text(stringResource(Res.string.voir_party_presumption), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (list.isEmpty()) Text(stringResource(Res.string.voir_section_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                        list.forEach { fact ->
                            FactCard(fact, onOpen = { actions.open(fact.address) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                repeat(factColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        SourcesSection(party.sources)
        CorrectionsSection(party.corrections)
        catalog.repliesTo(party.address).forEach { reply ->
            Spacer(Modifier.height(12.dp))
            ReplyCard(reply)
        }
        // "Et maintenant ?" on every sheet: the party's own steps, or the demands of the citizens.
        Spacer(Modifier.height(16.dp))
        val next = party.nextSteps.ifEmpty { listOf(NextStep(stringResource(Res.string.voir_party_next_default), NextTarget.ToSpace(Space.REVENDICATIONS))) }
        NextStepsSection(next, actions.next)
        Spacer(Modifier.height(12.dp))
        OriginLine(party.article)
    }
}

/** The seats of a party, assembly by assembly; several sources of the same count stay side by side. */
@Composable
private fun SeatsSection(party: PoliticalParty, wide: Boolean) {
    val palette = PpfTheme.palette
    PpfCard(Modifier.fillMaxWidth().testTag(VoirTags.section("party-seats"))) {
        SectionHeader(stringResource(Res.string.voir_party_seats))
        if (party.chambers.isEmpty()) {
            Text(stringResource(Res.string.voir_party_seats_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            return@PpfCard
        }
        Text(stringResource(Res.string.voir_party_seats_total, party.totalSeats), style = MaterialTheme.typography.titleSmall)
        party.chambers.forEach { chamber ->
            HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 8.dp))
            val reference = party.referenceSeats(chamber)
            val counts = party.seatsIn(chamber)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(chamber.label), style = MaterialTheme.typography.titleSmall)
                    reference?.date?.let { Text(stringResource(Res.string.voir_party_seats_date, it.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
                }
                reference?.let { Text(it.count.toString(), style = MaterialTheme.typography.headlineSmall, color = palette.brand) }
            }
            if (counts.size > 1) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.voir_party_seats_sources), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            // Every source of the count, side by side on wide windows, one under the other otherwise.
            val tiles: @Composable (Modifier) -> Unit = { modifier ->
                counts.forEach { count ->
                    Column(modifier) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(count.count.toString(), style = MaterialTheme.typography.titleMedium)
                            if (counts.size > 1 && count == reference) {
                                Spacer(Modifier.width(8.dp))
                                PpfBadge(stringResource(Res.string.voir_party_seats_reference), BadgeStyle.NEUTRAL)
                            }
                        }
                        count.source?.let { SourceLine(it) }
                    }
                }
            }
            if (wide && counts.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { tiles(Modifier.weight(1f)) }
            } else {
                Column(Modifier.fillMaxWidth()) { tiles(Modifier.fillMaxWidth()) }
            }
        }
    }
}
