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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ContentLicenses
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.voir.ArchivedCopy
import org.politisationpopulairefrancaise.core.voir.Correction
import org.politisationpopulairefrancaise.core.voir.Fact
import org.politisationpopulairefrancaise.core.voir.FactKind
import org.politisationpopulairefrancaise.core.voir.FactStatus
import org.politisationpopulairefrancaise.core.voir.NextStep
import org.politisationpopulairefrancaise.core.voir.NextTarget
import org.politisationpopulairefrancaise.core.voir.ProcedureStage
import org.politisationpopulairefrancaise.core.voir.PromiseStatus
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.Reliability
import org.politisationpopulairefrancaise.core.voir.RightOfReply
import org.politisationpopulairefrancaise.core.voir.Source
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.parties
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.Dot
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of "Voir", stable for the interface tests. */
object VoirTags {
    const val SCREEN = "voir-screen"
    const val SEARCH = "voir-search"
    const val SEARCH_CLEAR = "voir-search-clear"
    const val SEARCH_RESULTS = "voir-search-results"
    const val BACK = "voir-back"
    const val GLOSSARY = "voir-glossary"
    const val GLOSSARY_OPEN = "voir-glossary-open"
    const val CORRECTIONS_OPEN = "voir-corrections-open"
    const val FOLLOW = "voir-follow"
    const val FOLLOWED = "voir-followed"
    const val COPY = "voir-copy"
    const val CITE = "voir-cite"
    const val VERIFY_OPEN = "voir-verify-open"
    const val VERIFY_INPUT = "voir-verify-input"
    const val VERIFY_SUBMIT = "voir-verify-submit"
    const val VERIFY_RESULT = "voir-verify-result"
    const val VERIFY_OPEN_CONTENT = "voir-verify-open-content"
    const val CHART = "voir-chart"
    const val CHART_TOGGLE = "voir-chart-toggle"
    const val TABLE = "voir-table"
    const val REPLY_OPEN = "voir-reply-open"
    const val PRESUMPTION = "voir-presumption"
    const val LINK_DIALOG = "voir-link-dialog"
    const val LINK_OPEN = "voir-link-open"
    const val NEXT = "voir-next-" // followed by the index
    const val PARTY_CHIP = "voir-party-chip"
    fun tab(index: Int) = "voir-tab-$index"
    fun page(index: Int) = "voir-page-$index"
    fun card(slug: String) = "voir-card-$slug"
    fun seeAll(section: String) = "voir-see-all-$section"
    fun detail(slug: String) = "voir-detail-$slug"
    fun filter(id: String) = "voir-filter-$id"
    fun section(id: String) = "voir-section-$id"
}

internal fun String.isExample(): Boolean = startsWith(VoirCatalog.EXAMPLE_MARKER)
internal fun String.withoutExample(): String = removePrefix(VoirCatalog.EXAMPLE_MARKER).trim()

/**
 * A title without its [EXEMPLE] marker; the badge, when it had one, sits on its own line above, so
 * that narrow cards keep their whole width for the words.
 */
@Composable
internal fun VoirTitle(
    title: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
    maxLines: Int = Int.MAX_VALUE,
    heading: Boolean = false,
) {
    Column(modifier) {
        if (title.isExample()) {
            ExampleBadge()
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = title.withoutExample(),
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = if (heading) Modifier.semantics { heading() } else Modifier,
        )
    }
}

/** A title of a section, with an optional "Tout voir" on the right. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, actionTag: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) {
            PpfTextButton(onClick = onAction, text = action, modifier = if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
        }
    }
}

/** A badge that explains itself: touching it opens the glossary. */
@Composable
internal fun ExplainedBadge(text: String, style: BadgeStyle, onExplain: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onExplain == null) {
        PpfBadge(text, style, modifier)
        return
    }
    val explain = stringResource(Res.string.voir_explain)
    Surface(
        onClick = onExplain,
        modifier = modifier.minimumInteractiveComponentSize().semantics { contentDescription = "$text. $explain"; role = Role.Button },
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PpfBadge(text, style)
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.Info, contentDescription = null, tint = PpfTheme.palette.inkSubtle, modifier = Modifier.size(14.dp))
        }
    }
}

internal val FactStatus.badgeStyle: BadgeStyle get() = when (this) {
    FactStatus.ESTABLISHED -> BadgeStyle.SUCCESS
    FactStatus.ALLEGED -> BadgeStyle.WARNING
    FactStatus.UNDER_INVESTIGATION -> BadgeStyle.BRAND
}

internal val ProcedureStage.badgeStyle: BadgeStyle get() = when {
    this == ProcedureStage.FINAL_CONVICTION -> BadgeStyle.ACCENT
    endedWithoutConviction -> BadgeStyle.SUCCESS
    else -> BadgeStyle.WARNING
}

internal val PromiseStatus.badgeStyle: BadgeStyle get() = when (this) {
    PromiseStatus.KEPT -> BadgeStyle.SUCCESS
    PromiseStatus.IN_PROGRESS -> BadgeStyle.BRAND
    PromiseStatus.ABANDONED -> BadgeStyle.WARNING
    PromiseStatus.CONTRARY -> BadgeStyle.ACCENT
}

/** The reliability of a value: a coloured dot and its words (never the colour alone). */
@Composable
internal fun ReliabilityLabel(reliability: Reliability, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val color = when (reliability) {
        Reliability.HIGH -> palette.success
        Reliability.MEDIUM -> palette.warning
        Reliability.LOW -> palette.accent
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color, size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(reliability.label), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}

/**
 * Opens a web address in the browser after the reader confirms it: leaving the application shows
 * the reader's IP address to that site, so nothing opens without warning (as in "Comprendre",
 * D-033). Nothing happens if no browser can open it (never a crash).
 */
@Composable
internal fun rememberOpenUrl(): (String) -> Unit {
    val handler = LocalUriHandler.current
    var pending by remember { mutableStateOf<String?>(null) }
    pending?.let { url ->
        val palette = PpfTheme.palette
        AlertDialog(
            onDismissRequest = { pending = null },
            modifier = Modifier.testTag(VoirTags.LINK_DIALOG),
            containerColor = palette.surface2,
            titleContentColor = palette.ink,
            textContentColor = palette.inkMuted,
            shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.voir_link_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(Res.string.voir_link_text, hostOf(url)), style = MaterialTheme.typography.bodyMedium) },
            // The safe choice first.
            dismissButton = { PpfTextButton(onClick = { pending = null }, text = stringResource(Res.string.voir_link_cancel), color = palette.inkMuted) },
            confirmButton = {
                PpfTextButton(
                    onClick = {
                        pending = null
                        runCatching { handler.openUri(url) }
                    },
                    text = stringResource(Res.string.voir_link_open),
                    modifier = Modifier.testTag(VoirTags.LINK_OPEN),
                )
            },
        )
    }
    return { url -> pending = url }
}

/** The name of the site of a web address: "https://www.example.org/a/b" gives "www.example.org". */
internal fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').ifEmpty { url }

/** One source: its kind, its name, its date, and a link when it has an address. */
@Composable
internal fun SourceLine(source: Source, modifier: Modifier = Modifier, reliability: Reliability? = null) {
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    val url = source.url
    val describe = stringResource(Res.string.voir_open_source_named, source.name)
    val rowModifier = if (url != null) {
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button; contentDescription = describe }
    } else {
        modifier.fillMaxWidth()
    }
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(source.kind.label), style = MaterialTheme.typography.labelMedium, color = palette.brand)
                Text(source.name, style = MaterialTheme.typography.bodyMedium)
                val date = source.date?.toFrenchShort()
                if (date != null || reliability != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (date != null) Text(date, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                        if (date != null && reliability != null) Spacer(Modifier.width(12.dp))
                        if (reliability != null) ReliabilityLabel(reliability)
                    }
                }
            }
            if (url != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.Globe, contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 4.dp).size(18.dp))
            }
        }
    }
    if (url != null) {
        Surface(onClick = { open(url) }, modifier = rowModifier.heightIn(min = 48.dp), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = palette.ink) { content() }
    } else {
        Column(rowModifier) { content() }
    }
}

/** The sources of a content, one line each, separated by thin rules. */
@Composable
internal fun SourcesSection(sources: List<Source>, modifier: Modifier = Modifier, title: String = stringResource(Res.string.voir_sources)) {
    if (sources.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag(VoirTags.section("sources"))) {
        SectionHeader(title)
        sources.forEachIndexed { index, source ->
            if (index > 0) HorizontalDivider(color = PpfTheme.palette.border)
            SourceLine(source)
        }
    }
}

/** The archived copies of an official document (VO-07): date, short fingerprint, link. */
@Composable
internal fun ArchivesSection(archives: List<ArchivedCopy>, modifier: Modifier = Modifier) {
    if (archives.isEmpty()) return
    val palette = PpfTheme.palette
    val open = rememberOpenUrl()
    Column(modifier.fillMaxWidth().testTag(VoirTags.section("archives"))) {
        SectionHeader(stringResource(Res.string.voir_archives))
        archives.forEach { copy ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.ShieldCheck, contentDescription = null, tint = palette.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.voir_archive_line, copy.archivedAt?.toFrenchShort() ?: "?", copy.sha256.take(12) + "…"),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    modifier = Modifier.weight(1f),
                )
                copy.archiveUrl?.let { url -> PpfTextButton(onClick = { open(url) }, text = stringResource(Res.string.voir_archive_open)) }
            }
        }
    }
}

/** The corrections made to a content (VO-22), newest first. */
@Composable
internal fun CorrectionsSection(corrections: List<Correction>, modifier: Modifier = Modifier) {
    if (corrections.isEmpty()) return
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(VoirTags.section("corrections"))) {
        corrections.forEach { correction ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Lucide.Pencil, contentDescription = null, tint = palette.warning, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.voir_corrected_on, correction.date.toFrench(), correction.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            }
        }
    }
}

/** Who wrote the content: always shown (cahier fonctionnel, origin of each content). */
@Composable
internal fun OriginLine(article: Article, modifier: Modifier = Modifier, cite: VoirItem? = null) {
    val origin = stringResource(if (article.origin == ContentOrigin.AI_ASSISTED) Res.string.voir_origin_ai else Res.string.voir_origin_human)
    val updated = stringResource(Res.string.voir_updated_on, org.politisationpopulairefrancaise.core.time.CivilDate.fromEpochSeconds(article.updatedAt).toFrench())
    // The licence of PPF's own texts (D-103); none for a text whose rights stay with its author.
    val license = ContentLicenses.of(article.type)?.let { stringResource(Res.string.voir_license, it.shortName) }
    Text(listOfNotNull(updated, origin, license).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle, modifier = modifier)
    // « Citer ce contenu » (S-144) under the origin of every published block.
    cite?.let { CiteContent(it) }
}

/** "Et maintenant ?": the next step in another space (rule of the cahier fonctionnel, section 3). */
@Composable
internal fun NextStepsSection(steps: List<NextStep>, onNext: (NextTarget) -> Unit, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val palette = PpfTheme.palette
    Surface(modifier = modifier.fillMaxWidth().testTag(VoirTags.section("next")), shape = MaterialTheme.shapes.medium, color = palette.brandTint, contentColor = palette.ink) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.voir_next_title), style = MaterialTheme.typography.titleMedium, color = palette.brand, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { index, step ->
                Surface(
                    onClick = { onNext(step.target) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(VoirTags.NEXT + index),
                    shape = MaterialTheme.shapes.small,
                    color = palette.surface2,
                    contentColor = palette.ink,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(step.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(Lucide.ChevronRight, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                    }
                }
                if (index < steps.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** "Suivre": keeps a content in the user's follows, on the device only. */
@Composable
internal fun FollowButton(followed: Boolean, title: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (followed) Res.string.voir_unfollow_description else Res.string.voir_follow_description, title.withoutExample())
    val label = stringResource(if (followed) Res.string.voir_following else Res.string.voir_follow)
    PpfSecondaryButton(
        onClick = onToggle,
        text = label,
        small = true,
        icon = if (followed) Lucide.Check else Lucide.Bookmark,
        modifier = modifier.testTag(if (followed) VoirTags.FOLLOWED else VoirTags.FOLLOW).semantics { contentDescription = description },
    )
}

/** The persons a fact concerns, as small buttons that open their sheets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonsRow(persons: List<PublicPerson>, onOpen: (PublicPerson) -> Unit, modifier: Modifier = Modifier) {
    if (persons.isEmpty()) return
    val palette = PpfTheme.palette
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        persons.forEach { person ->
            Surface(
                onClick = { onOpen(person) },
                modifier = Modifier.heightIn(min = 44.dp),
                shape = MaterialTheme.shapes.small,
                color = palette.surface1,
                contentColor = palette.ink,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.address.identifier, initials(person.name), size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(person.name.withoutExample(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Two initials of a name, for the avatar of a public person (no photograph: no image rights to manage). */
internal fun initials(name: String): String =
    name.withoutExample().split(' ', '-').filter { it.isNotBlank() }.let { parts ->
        (parts.firstOrNull()?.take(1).orEmpty() + (parts.drop(1).lastOrNull()?.take(1).orEmpty())).uppercase()
    }

/**
 * A sourced fact in a list: its kind and date, its title, its status, the line that matters for
 * its kind (stage of the procedure, fate of the promise, vote, appeals), its summary.
 */
@Composable
internal fun FactCard(fact: Fact, onOpen: () -> Unit, modifier: Modifier = Modifier, catalog: VoirCatalog? = null) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag(VoirTags.card(fact.article.slug))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(fact.kind.label)} · ${fact.date.toFrenchShort()}" + (fact.place?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        VoirTitle(fact.title, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        // Inside a card the badges are plain: touching anywhere opens the fact, which explains them.
        FactBadges(fact, onExplain = null)
        fact.article.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val names = catalog?.let { c -> c.personsOf(fact).map { it.name } + fact.parties.mapNotNull(c::party).map { it.name } }.orEmpty()
        if (names.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                names.joinToString(", ") { it.withoutExample() },
                style = MaterialTheme.typography.labelMedium,
                color = palette.brand,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The status of a fact and the badge proper to its kind, side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FactBadges(fact: Fact, onExplain: (() -> Unit)?, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        ExplainedBadge(stringResource(fact.status.label), fact.status.badgeStyle, onExplain)
        fact.procedure?.let { ExplainedBadge(stringResource(it.label), it.badgeStyle, onExplain) }
        fact.promise?.let { PpfBadge(stringResource(it.label), it.badgeStyle) }
        if (fact.kind == FactKind.VOTE || fact.kind == FactKind.STATEMENT_VS_VOTE) {
            fact.vote?.let { PpfBadge(stringResource(Res.string.voir_vote_position, stringResource(it.label)), BadgeStyle.NEUTRAL) }
        }
        fact.liberty?.let { PpfBadge(stringResource(it.label), BadgeStyle.NEUTRAL) }
        fact.recourse?.let { PpfBadge(stringResource(Res.string.voir_recourse, stringResource(it.label)), BadgeStyle.BRAND) }
    }
}

/** A right of reply, under the content it answers: "Droit de réponse de [nom]". */
@Composable
internal fun ReplyCard(reply: RightOfReply, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = palette.surface1, contentColor = palette.ink) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.voir_reply_title, reply.respondent.withoutExample()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(Res.string.voir_reply_published, reply.publishedOn.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            Spacer(Modifier.height(8.dp))
            Text(reply.text.trim(), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
    }
}

/** What the labels of "Voir" mean: status of a fact, reliability, stages of a procedure, sources side by side. */
@Composable
internal fun GlossaryDialog(onDismiss: () -> Unit) {
    val palette = PpfTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(VoirTags.GLOSSARY),
        containerColor = palette.surface2,
        titleContentColor = palette.ink,
        textContentColor = palette.ink,
        shape = MaterialTheme.shapes.medium,
        title = { Text(stringResource(Res.string.voir_glossary_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GlossaryBlock(stringResource(Res.string.voir_glossary_status)) {
                    GlossaryEntry(stringResource(FactStatus.ESTABLISHED.label), FactStatus.ESTABLISHED.badgeStyle, stringResource(Res.string.voir_glossary_established))
                    GlossaryEntry(stringResource(FactStatus.ALLEGED.label), FactStatus.ALLEGED.badgeStyle, stringResource(Res.string.voir_glossary_alleged))
                    GlossaryEntry(stringResource(FactStatus.UNDER_INVESTIGATION.label), FactStatus.UNDER_INVESTIGATION.badgeStyle, stringResource(Res.string.voir_glossary_investigation))
                }
                GlossaryBlock(stringResource(Res.string.voir_glossary_reliability)) {
                    Reliability.entries.forEach { level ->
                        ReliabilityLabel(level)
                        Text(
                            stringResource(
                                when (level) {
                                    Reliability.HIGH -> Res.string.voir_glossary_high
                                    Reliability.MEDIUM -> Res.string.voir_glossary_medium
                                    Reliability.LOW -> Res.string.voir_glossary_low
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                GlossaryBlock(stringResource(Res.string.voir_glossary_procedure)) {
                    Text(stringResource(Res.string.voir_glossary_presumption), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    ProcedureStage.entries.forEach { stage ->
                        GlossaryEntry(stringResource(stage.label), stage.badgeStyle, stringResource(stage.explanation))
                    }
                }
                GlossaryBlock(stringResource(Res.string.voir_glossary_sources)) {
                    Text(stringResource(Res.string.voir_glossary_sources_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
                }
            }
        },
        confirmButton = { PpfTextButton(onClick = onDismiss, text = stringResource(Res.string.voir_close)) },
    )
}

@Composable
private fun GlossaryBlock(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = PpfTheme.palette.brand, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp).semantics { heading() })
    content()
}

@Composable
private fun GlossaryEntry(label: String, style: BadgeStyle, text: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        PpfBadge(label, style)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted)
    }
}
