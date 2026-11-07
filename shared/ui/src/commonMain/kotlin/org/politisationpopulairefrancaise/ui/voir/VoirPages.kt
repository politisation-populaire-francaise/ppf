package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.voir.CorrectionEntry
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.ImpactDirection
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirDossier
import org.politisationpopulairefrancaise.core.voir.WorldMovement
import org.politisationpopulairefrancaise.ui.components.Avatar
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
 * The pages of "Voir" other than the indicators: a sourced fact, the sheet of a public person, a
 * bill, a movement of the world, a dossier, and the lists (chronology, freedoms, persons, bills,
 * world, corrections). Every page ends with its sources, its corrections and "Et maintenant ?".
 */

/** The callbacks every page needs. */
internal class VoirActions(
    val open: (org.politisationpopulairefrancaise.core.signing.ContentAddress) -> Unit,
    val next: (NextTarget) -> Unit,
    val explain: () -> Unit,
    /** Opens the form of a right of reply to the content at this address. */
    val reply: (org.politisationpopulairefrancaise.core.signing.ContentAddress) -> Unit,
)

// ---------------------------------------------------------------------------------------------
// A sourced fact

@Composable
internal fun FactPage(fact: Fact, catalog: VoirCatalog, state: VoirState, actions: VoirActions) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(fact.article.slug))) {
        Text(
            listOfNotNull(stringResource(fact.kind.label), fact.date.toFrench(), fact.place).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = palette.inkSubtle,
        )
        Spacer(Modifier.height(6.dp))
        VoirTitle(fact.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        Spacer(Modifier.height(10.dp))
        FactBadges(fact, actions.explain)
        val procedure = fact.procedure
        if (fact.kind == FactKind.CASE && procedure != null && procedure.presumedInnocent) {
            Spacer(Modifier.height(12.dp))
            InfoBox(Lucide.Info, Modifier.testTag(VoirTags.PRESUMPTION)) {
                Text(stringResource(Res.string.voir_presumption), style = MaterialTheme.typography.bodyMedium)
            }
        }
        fact.quote?.let { quote ->
            Spacer(Modifier.height(16.dp))
            QuoteBlock(quote)
        }
        fact.article.summary?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        if (fact.sentence != null || fact.sentenceServed != null) {
            Spacer(Modifier.height(12.dp))
            fact.sentence?.let { Text(stringResource(Res.string.voir_sentence, it), style = MaterialTheme.typography.bodyMedium) }
            fact.sentenceServed?.let { Text(stringResource(Res.string.voir_sentence_served, it), style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(fact.article.body)
        val persons = catalog.personsOf(fact)
        val texts = catalog.textsOf(fact)
        if (persons.isNotEmpty() || texts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(Res.string.voir_concerns))
            PersonsRow(persons, onOpen = { actions.open(it.address) })
            texts.forEach { text ->
                Spacer(Modifier.height(8.dp))
                LinkRow(text.title, Lucide.ScrollText, onClick = { actions.open(text.address) })
            }
        }
        Spacer(Modifier.height(16.dp))
        SourcesSection(fact.sources)
        ArchivesSection(fact.archives)
        CorrectionsSection(fact.corrections)
        val replies = catalog.repliesTo(fact.address)
        replies.forEach { reply ->
            Spacer(Modifier.height(12.dp))
            ReplyCard(reply)
        }
        if (persons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PpfSecondaryButton(onClick = { actions.reply(fact.address) }, text = stringResource(Res.string.voir_reply_button), small = true, icon = Lucide.MessageSquare, modifier = Modifier.testTag(VoirTags.REPLY_OPEN))
        }
        if (fact.nextSteps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            NextStepsSection(fact.nextSteps, actions.next)
        }
        Spacer(Modifier.height(12.dp))
        OriginLine(fact.article)
        FollowRow(state, fact.address, fact.title)
    }
}

@Composable
private fun FollowRow(state: VoirState, address: org.politisationpopulairefrancaise.core.signing.ContentAddress, title: String) {
    Spacer(Modifier.height(12.dp))
    FollowButton(state.isFollowed(address), title, onToggle = { state.toggleFollow(address) })
}

/** The exact words of a statement, set apart. */
@Composable
internal fun QuoteBlock(quote: String) {
    val palette = PpfTheme.palette
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(palette.accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(Res.string.voir_quote), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle)
            Text("« $quote »", style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
        }
    }
}

/** A tappable line leading to another content (a bill from a vote, a person from a fact). */
@Composable
internal fun LinkRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = palette.surface1, contentColor = palette.ink) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            VoirTitle(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2)
            Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Public persons

/** The order of the sections of every sheet, whoever the person is (règle « Traitement identique »). */
internal val PERSON_SECTIONS: List<FactKind> = listOf(
    FactKind.VOTE,
    FactKind.PROMISE,
    FactKind.STATEMENT_VS_VOTE,
    FactKind.CASE,
    FactKind.CONTRADICTED_STATEMENT,
    FactKind.INTERESTS,
    FactKind.LOBBYING,
    FactKind.PUBLIC_MONEY,
)

@Composable
internal fun PersonCard(person: PublicPerson, catalog: VoirCatalog, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(person.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.address.identifier, initials(person.name), size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                VoirTitle(person.name, maxLines = 2)
                person.function?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                person.group?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        val facts = catalog.factsAbout(person.address)
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PERSON_SECTIONS.forEach { kind ->
                    val count = facts.count { it.kind == kind }
                    if (count > 0) PpfBadge("${stringResource(kind.section)} : $count", if (kind == FactKind.CASE) BadgeStyle.WARNING else BadgeStyle.NEUTRAL)
                }
            }
        }
    }
}

@Composable
internal fun PersonPage(person: PublicPerson, catalog: VoirCatalog, state: VoirState, actions: VoirActions, wide: Boolean) {
    val palette = PpfTheme.palette
    val facts = catalog.factsAbout(person.address)
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(person.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.address.identifier, initials(person.name), size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                VoirTitle(person.name, style = MaterialTheme.typography.headlineMedium, heading = true)
                person.function?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted) }
                person.group?.let { Text(stringResource(Res.string.voir_group, it), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle) }
            }
        }
        Spacer(Modifier.height(12.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FollowButton(state.isFollowed(person.address), person.name, onToggle = { state.toggleFollow(person.address) })
            PpfSecondaryButton(onClick = { actions.reply(person.address) }, text = stringResource(Res.string.voir_reply_button), small = true, icon = Lucide.MessageSquare, modifier = Modifier.testTag(VoirTags.REPLY_OPEN))
        }
        Spacer(Modifier.height(16.dp))
        InfoBox(Lucide.Info) { Text(stringResource(Res.string.voir_people_intro), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))
        PpfCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(Res.string.voir_person_mandates))
            person.mandates.forEachIndexed { index, mandate ->
                if (index > 0) HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mandate.title, style = MaterialTheme.typography.titleSmall)
                        mandate.territory?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted) }
                        val period = when {
                            mandate.start != null && mandate.end != null -> stringResource(Res.string.voir_from_to, mandate.start!!.toFrench(), mandate.end!!.toFrench())
                            mandate.start != null -> stringResource(Res.string.voir_since, mandate.start!!.toFrench())
                            else -> null
                        }
                        period?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle) }
                    }
                    if (mandate.current) PpfBadge(stringResource(Res.string.voir_world_ongoing), BadgeStyle.SUCCESS)
                }
            }
        }
        val sections = PERSON_SECTIONS.map { kind -> kind to facts.filter { it.kind == kind } }
        val columns = if (wide) 2 else 1
        sections.chunked(columns).forEach { row ->
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (kind, list) ->
                    Column(Modifier.weight(1f).testTag(VoirTags.section(kind.tagValue))) {
                        SectionHeader("${stringResource(kind.section)} (${list.size})")
                        if (kind == FactKind.CASE && list.any { it.procedure?.presumedInnocent == true }) {
                            InfoBox(Lucide.Info, Modifier.padding(bottom = 8.dp).testTag(VoirTags.PRESUMPTION)) {
                                Text(stringResource(Res.string.voir_presumption), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (list.isEmpty()) {
                            Text(stringResource(Res.string.voir_section_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                        }
                        list.forEach { fact ->
                            FactCard(fact, onOpen = { actions.open(fact.address) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(person.article.body)
        SourcesSection(person.sources)
        CorrectionsSection(person.corrections)
        Spacer(Modifier.height(8.dp))
        OriginLine(person.article)
    }
}

// ---------------------------------------------------------------------------------------------
// Bills (législomètre)

@Composable
internal fun BillCard(text: LegislativeText, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(text.article.slug))) {
        VoirTitle(text.title, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        StageProgress(text, compact = true)
        text.nextVote?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Calendar, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.voir_next_vote, it.date.toFrenchShort(), it.label), style = MaterialTheme.typography.bodySmall, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (text.uses493.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PpfBadge(stringResource(Res.string.voir_493_count, text.uses493.size), BadgeStyle.ACCENT)
        }
    }
}

/** Where the bill stands on its usual path: a bar of steps, and the same in words for screen readers. */
@Composable
internal fun StageProgress(text: LegislativeText, compact: Boolean) {
    val palette = PpfTheme.palette
    val path = TextStage.path
    val reached = path.indexOfLast { it.order <= text.stage.order }.coerceAtLeast(0)
    val words = stringResource(Res.string.voir_stage_progress, reached + 1, path.size, stringResource(text.stage.label))
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = words }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            path.forEachIndexed { index, _ ->
                Box(
                    Modifier.weight(1f).height(6.dp).background(
                        if (index <= reached) palette.brand else palette.border,
                        RoundedCornerShape(3.dp),
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(words, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            path.forEachIndexed { index, stage ->
                val date = text.history.lastOrNull { it.stage == stage }?.date
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(if (index <= reached) palette.brand else palette.border, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (date != null) stringResource(Res.string.voir_stage_reached_on, stringResource(stage.label), date.toFrenchShort()) else stringResource(stage.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index <= reached) palette.ink else palette.inkSubtle,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BillPage(text: LegislativeText, catalog: VoirCatalog, state: VoirState, actions: VoirActions, wide: Boolean) {
    val palette = PpfTheme.palette
    val votes = catalog.factsOn(text.address, FactKind.VOTE)
    val lobbying = catalog.factsOn(text.address, FactKind.LOBBYING)
    val others = catalog.factsOn(text.address).filter { it.kind != FactKind.VOTE && it.kind != FactKind.LOBBYING }
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(text.article.slug))) {
        text.textKind?.let { kind ->
            Text(
                stringResource(if (kind == "proposition") Res.string.voir_text_proposition else Res.string.voir_text_projet),
                style = MaterialTheme.typography.labelLarge,
                color = palette.inkSubtle,
            )
            Spacer(Modifier.height(6.dp))
        }
        VoirTitle(text.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        text.article.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(12.dp))
        FollowButton(state.isFollowed(text.address), text.title, onToggle = { state.toggleFollow(text.address) })
        text.nextVote?.let {
            Spacer(Modifier.height(16.dp))
            InfoBox(Lucide.Calendar) { Text(stringResource(Res.string.voir_next_vote, it.date.toFrench(), it.label), style = MaterialTheme.typography.titleSmall) }
        }
        val main: @Composable () -> Unit = {
            PpfCard(Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(Res.string.voir_text_stage))
                StageProgress(text, compact = false)
                if (text.uses493.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        text.uses493.forEach { PpfBadge(stringResource(Res.string.voir_493_on, it.toFrenchShort()), BadgeStyle.ACCENT) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PpfCard(Modifier.fillMaxWidth().testTag(VoirTags.section("impacts"))) {
                SectionHeader(stringResource(Res.string.voir_impacts))
                if (text.impacts.isEmpty()) Text(stringResource(Res.string.voir_impacts_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                text.impacts.forEachIndexed { index, impact ->
                    if (index > 0) HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(impact.group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        PpfBadge(
                            stringResource(impact.direction.label),
                            when (impact.direction) {
                                ImpactDirection.GAINS -> BadgeStyle.SUCCESS
                                ImpactDirection.LOSES -> BadgeStyle.ACCENT
                                ImpactDirection.MIXED -> BadgeStyle.WARNING
                            },
                        )
                    }
                    Text(impact.description, style = MaterialTheme.typography.bodyMedium)
                    impact.source?.let { SourceLine(it) }
                }
            }
            Spacer(Modifier.height(16.dp))
            MarkdownText(text.article.body)
        }
        val aside: @Composable () -> Unit = {
            FactsBlock(stringResource(Res.string.voir_text_votes), votes, catalog, actions, "votes")
            FactsBlock(stringResource(Res.string.voir_text_lobbying), lobbying, catalog, actions, "lobbying")
            if (others.isNotEmpty()) FactsBlock(stringResource(Res.string.voir_text_other_facts), others, catalog, actions, "others")
            SourcesSection(text.sources)
            CorrectionsSection(text.corrections)
            if (text.nextSteps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                NextStepsSection(text.nextSteps, actions.next)
            }
            Spacer(Modifier.height(12.dp))
            OriginLine(text.article)
        }
        Spacer(Modifier.height(16.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.4f)) { main() }
                Column(Modifier.weight(1f)) { aside() }
            }
        } else {
            main()
            Spacer(Modifier.height(8.dp))
            aside()
        }
    }
}

@Composable
private fun FactsBlock(title: String, facts: List<Fact>, catalog: VoirCatalog, actions: VoirActions, id: String) {
    Column(Modifier.fillMaxWidth().testTag(VoirTags.section(id))) {
        SectionHeader("$title (${facts.size})")
        if (facts.isEmpty()) Text(stringResource(Res.string.voir_section_empty), style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkSubtle)
        facts.forEach { fact ->
            FactCard(fact, onOpen = { actions.open(fact.address) }, catalog = catalog)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Movements of the world and dossiers

@Composable
internal fun MovementCard(movement: WorldMovement, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(movement.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Globe, contentDescription = null, tint = palette.brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(movement.country + " · " + periodText(movement), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        VoirTitle(movement.title, maxLines = 3)
        movement.results.firstOrNull()?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.CircleCheck, contentDescription = null, tint = palette.success, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun periodText(movement: WorldMovement): String {
    val start = movement.start
    val end = movement.end
    return when {
        start != null && end != null -> stringResource(Res.string.voir_world_period, start.toFrenchShort(), end.toFrenchShort())
        start != null -> stringResource(Res.string.voir_world_since, start.toFrenchShort())
        else -> stringResource(Res.string.voir_world_ongoing)
    }
}

@Composable
internal fun MovementPage(movement: WorldMovement) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(movement.article.slug))) {
        Text(movement.country + " · " + periodText(movement), style = MaterialTheme.typography.labelLarge, color = palette.inkSubtle)
        Spacer(Modifier.height(6.dp))
        VoirTitle(movement.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        movement.article.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(16.dp))
        if (movement.claims.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.voir_world_claims))
            movement.claims.forEach { Bullet(it, Lucide.Megaphone, palette.brand) }
            Spacer(Modifier.height(12.dp))
        }
        if (movement.results.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.voir_world_results))
            movement.results.forEach { Bullet(it, Lucide.CircleCheck, palette.success) }
            Spacer(Modifier.height(12.dp))
        }
        MarkdownText(movement.article.body)
        SourcesSection(movement.sources)
        Spacer(Modifier.height(8.dp))
        OriginLine(movement.article)
    }
}

@Composable
private fun Bullet(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun DossierCard(dossier: VoirDossier, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(dossier.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.BookOpen, contentDescription = null, tint = palette.brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.content_type_dossier), style = MaterialTheme.typography.labelMedium, color = palette.inkSubtle)
        }
        Spacer(Modifier.height(4.dp))
        VoirTitle(dossier.title, maxLines = 3)
        dossier.article.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DossierPage(dossier: VoirDossier) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(VoirTags.detail(dossier.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PpfBadge(stringResource(Res.string.content_type_dossier), BadgeStyle.BRAND)
            Spacer(Modifier.width(8.dp))
            PpfBadge(stringResource(dossier.axis.label), BadgeStyle.NEUTRAL)
        }
        Spacer(Modifier.height(8.dp))
        VoirTitle(dossier.title, style = MaterialTheme.typography.headlineMedium, heading = true)
        dossier.article.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
        }
        Spacer(Modifier.height(16.dp))
        MarkdownText(dossier.article.body)
        Spacer(Modifier.height(8.dp))
        OriginLine(dossier.article)
    }
}

// ---------------------------------------------------------------------------------------------
// Lists

@Composable
internal fun ListIntro(title: String, intro: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(intro, style = MaterialTheme.typography.bodyLarge, color = PpfTheme.palette.inkMuted)
    Spacer(Modifier.height(12.dp))
}

/** Filter chips: "Tout" then one chip per value; selecting the selected one goes back to "Tout". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> FilterChips(values: List<T>, selected: T?, label: @Composable (T) -> String, id: (T) -> String, onSelect: (T?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        PpfSelectableChip(stringResource(Res.string.voir_all_filter), selected == null, onClick = { onSelect(null) }, modifier = Modifier.testTag(VoirTags.filter("all")))
        values.forEach { value ->
            PpfSelectableChip(label(value), selected == value, onClick = { onSelect(if (selected == value) null else value) }, modifier = Modifier.testTag(VoirTags.filter(id(value))))
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun ChronologyPage(catalog: VoirCatalog, state: VoirState, actions: VoirActions) {
    ListIntro(stringResource(Res.string.voir_chronology), stringResource(Res.string.voir_chronology_intro))
    val kinds = FactKind.chronology.filter { kind -> catalog.chronology().any { it.kind == kind } }
    FilterChips(kinds, state.chronologyFilter, label = { stringResource(it.label) }, id = { it.tagValue }, onSelect = { state.chronologyFilter = it })
    val facts = catalog.chronology(state.chronologyFilter?.let { setOf(it) } ?: FactKind.chronology.toSet())
    var month: String? = null
    facts.forEach { fact ->
        val current = fact.date.monthYearFrench()
        if (current != month) {
            month = current
            Text(
                current.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = PpfTheme.palette.brand,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        TimelineItem(fact, onOpen = { actions.open(fact.address) })
    }
}

/** A fact of the chronology: a dot on a vertical line, then its card. */
@Composable
private fun TimelineItem(fact: Fact, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(20.dp).fillMaxHeight()) {
            Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(palette.border))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(12.dp).background(if (fact.kind == FactKind.MOBILISATION) palette.accent else palette.brand, CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
            FactCard(fact, onOpen = onOpen)
        }
    }
}

@Composable
internal fun LibertiesPage(catalog: VoirCatalog, state: VoirState, actions: VoirActions) {
    ListIntro(stringResource(Res.string.voir_liberties), stringResource(Res.string.voir_liberties_intro))
    FilterChips(catalog.libertyCategoriesInUse(), state.libertyFilter, label = { stringResource(it.label) }, id = { it.tagValue }, onSelect = { state.libertyFilter = it })
    catalog.liberties(state.libertyFilter).forEach { fact ->
        FactCard(fact, onOpen = { actions.open(fact.address) })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun PeoplePage(catalog: VoirCatalog, state: VoirState, actions: VoirActions, columns: Int) {
    ListIntro(stringResource(Res.string.voir_people), stringResource(Res.string.voir_people_intro))
    FilterChips(catalog.rolesInUse(), state.roleFilter, label = { stringResource(it.label) }, id = { it.tagValue }, onSelect = { state.roleFilter = it })
    Grid(catalog.persons(state.roleFilter), columns) { person, modifier -> PersonCard(person, catalog, onOpen = { actions.open(person.address) }, modifier = modifier) }
}

@Composable
internal fun BillsPage(catalog: VoirCatalog, actions: VoirActions, columns: Int) {
    ListIntro(stringResource(Res.string.voir_bills), stringResource(Res.string.voir_bills_intro))
    Grid(catalog.bills(), columns) { text, modifier -> BillCard(text, onOpen = { actions.open(text.address) }, modifier = modifier) }
}

@Composable
internal fun WorldPage(catalog: VoirCatalog, actions: VoirActions, columns: Int) {
    ListIntro(stringResource(Res.string.voir_world), stringResource(Res.string.voir_world_intro))
    Grid(catalog.movements, columns) { movement, modifier -> MovementCard(movement, onOpen = { actions.open(movement.address) }, modifier = modifier) }
}

@Composable
internal fun KeyFiguresPage(catalog: VoirCatalog, axis: VoirAxis, actions: VoirActions, columns: Int) {
    ListIntro(stringResource(if (axis == VoirAxis.LIBERTES) Res.string.voir_counter_powers else Res.string.voir_key_figures), stringResource(Res.string.voir_method_text))
    KeyFigureGrid(catalog.keyFigures(axis), columns, onOpen = { actions.open(it.address) })
}

@Composable
internal fun CorrectionsPage(catalog: VoirCatalog, actions: VoirActions) {
    val palette = PpfTheme.palette
    ListIntro(stringResource(Res.string.voir_corrections), stringResource(Res.string.voir_corrections_intro))
    val entries: List<CorrectionEntry> = catalog.corrections()
    if (entries.isEmpty()) Text(stringResource(Res.string.voir_corrections_none), style = MaterialTheme.typography.bodyLarge, color = palette.inkMuted)
    entries.forEach { entry ->
        PpfCard(onClick = { actions.open(entry.item.address) }, modifier = Modifier.fillMaxWidth().testTag(VoirTags.card("correction-" + entry.item.address.identifier))) {
            Text(entry.correction.date.toFrench(), style = MaterialTheme.typography.labelMedium, color = palette.warning)
            Spacer(Modifier.height(4.dp))
            VoirTitle(entry.item.title, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(entry.correction.reason, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Items laid out in [columns] columns of equal width and equal height per row. */
@Composable
internal fun <T> Grid(items: List<T>, columns: Int, item: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

