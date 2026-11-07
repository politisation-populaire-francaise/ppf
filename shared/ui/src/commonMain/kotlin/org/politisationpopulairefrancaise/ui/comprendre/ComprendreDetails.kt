package org.politisationpopulairefrancaise.ui.comprendre

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.comprendre.ContentBlocks
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.comprendre.Decryptage
import org.politisationpopulairefrancaise.core.comprendre.HistoryStory
import org.politisationpopulairefrancaise.core.comprendre.QuiDecide
import org.politisationpopulairefrancaise.core.comprendre.Verdict
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.PublicRole
import org.politisationpopulairefrancaise.ui.comprendre.elus.BoxLevelOfficials
import org.politisationpopulairefrancaise.ui.comprendre.elus.ElusBoxFooter
import org.politisationpopulairefrancaise.ui.comprendre.elus.ElusLibrary
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.content.FeedPost
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_claim_context
import org.politisationpopulairefrancaise.ui.resources.comprendre_claim_label
import org.politisationpopulairefrancaise.ui.resources.comprendre_elus_title
import org.politisationpopulairefrancaise.ui.resources.comprendre_history_claims
import org.politisationpopulairefrancaise.ui.resources.comprendre_history_results
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_history
import org.politisationpopulairefrancaise.ui.resources.comprendre_kind_who
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_commune
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_departement
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_etat
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_intercommunalite
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_region
import org.politisationpopulairefrancaise.ui.resources.comprendre_level_union_europeenne
import org.politisationpopulairefrancaise.ui.resources.comprendre_method_link
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_autre
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_depute
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_elu_local
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_eurodepute
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_maire
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_ministre
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_president
import org.politisationpopulairefrancaise.ui.resources.comprendre_role_senateur
import org.politisationpopulairefrancaise.ui.resources.comprendre_sources
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_contradicted
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_exact
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_label
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_misleading
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_mostly_exact
import org.politisationpopulairefrancaise.ui.resources.comprendre_verdict_unverifiable
import org.politisationpopulairefrancaise.ui.resources.comprendre_who_levels
import org.politisationpopulairefrancaise.ui.resources.content_back
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.content_type_decryptage
import org.politisationpopulairefrancaise.ui.resources.content_updated
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

object ComprendreDetailTags {
    const val DECRYPTAGE = "decryptage-detail"
    const val HISTORY = "history-detail"
    const val WHO = "who-decides-detail"
    const val VERDICT = "decryptage-verdict"
    const val CLAIM = "decryptage-claim"
    const val METHOD = "decryptage-method"
    const val ELUS = "who-decides-elus"
    const val BACK = "comprendre-detail-back"

    fun level(level: DecisionLevel): String = "who-decides-level-${level.key}"
}

// ---------------------------------------------------------------------------------------------
// Fact-checks

/** The label of a verdict, the same everywhere. */
@Composable
fun verdictLabel(verdict: Verdict): String = stringResource(
    when (verdict) {
        Verdict.EXACT -> Res.string.comprendre_verdict_exact
        Verdict.MOSTLY_EXACT -> Res.string.comprendre_verdict_mostly_exact
        Verdict.MISLEADING -> Res.string.comprendre_verdict_misleading
        Verdict.CONTRADICTED -> Res.string.comprendre_verdict_contradicted
        Verdict.UNVERIFIABLE -> Res.string.comprendre_verdict_unverifiable
    },
)

/** Colour and icon of a verdict; the word is always written, colour is never the only sign. */
@Composable
private fun verdictStyle(verdict: Verdict): Pair<Color, ImageVector> {
    val palette = PpfTheme.palette
    return when (verdict) {
        Verdict.EXACT -> palette.success to Lucide.CircleCheck
        Verdict.MOSTLY_EXACT -> palette.success to Lucide.Check
        Verdict.MISLEADING -> palette.warning to Lucide.TriangleExclamationPoint
        Verdict.CONTRADICTED -> palette.accentStrong to Lucide.X
        Verdict.UNVERIFIABLE -> palette.inkMuted to Lucide.Search
    }
}

/** The verdict as an outlined label with its icon. */
@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier, large: Boolean = false) {
    val (color, icon) = verdictStyle(verdict)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = PpfTheme.palette.surface2,
        border = BorderStroke(if (large) 2.dp else 1.dp, color),
    ) {
        Row(Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 8.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (large) 20.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (large) stringResource(Res.string.comprendre_verdict_label, verdictLabel(verdict)) else verdictLabel(verdict),
                style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = PpfTheme.palette.ink,
            )
        }
    }
}

@Composable
fun DecryptageDetail(
    check: Decryptage,
    onOpenMethod: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    DetailColumn(check.article, stringResource(Res.string.content_type_decryptage), onBack, compact, modifier.testTag(ComprendreDetailTags.DECRYPTAGE)) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(ComprendreDetailTags.CLAIM),
            shape = MaterialTheme.shapes.medium,
            color = palette.surface2,
            border = BorderStroke(1.dp, palette.border),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.comprendre_claim_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                Spacer(Modifier.height(6.dp))
                Text("« ${check.claim} »", style = MaterialTheme.typography.titleMedium)
                check.claimContext?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(Res.string.comprendre_claim_context, it), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                Spacer(Modifier.height(12.dp))
                VerdictBadge(check.verdict, Modifier.testTag(ComprendreDetailTags.VERDICT), large = true)
            }
        }
        if (onOpenMethod != null) {
            PpfTextButton(onClick = onOpenMethod, text = stringResource(Res.string.comprendre_method_link), modifier = Modifier.testTag(ComprendreDetailTags.METHOD))
        }
        Spacer(Modifier.height(12.dp))
        BodyWithActions(check.article.body, onNavigate)
    }
}

// ---------------------------------------------------------------------------------------------
// History of social movements

@Composable
fun HistoryDetail(story: HistoryStory, onBack: (() -> Unit)?, onNavigate: (Space) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    DetailColumn(story.article, stringResource(Res.string.comprendre_kind_history), onBack, compact, modifier.testTag(ComprendreDetailTags.HISTORY), subtitle = "${story.period} · ${story.country}") {
        if (story.claims.isNotEmpty() || story.results.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = palette.surface2, border = BorderStroke(1.dp, palette.border)) {
                Column(Modifier.padding(16.dp)) {
                    if (story.claims.isNotEmpty()) LabelledList(stringResource(Res.string.comprendre_history_claims), story.claims, Lucide.Megaphone, palette.brand)
                    if (story.claims.isNotEmpty() && story.results.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = palette.border)
                    }
                    if (story.results.isNotEmpty()) LabelledList(stringResource(Res.string.comprendre_history_results), story.results, Lucide.CircleCheck, palette.success)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        BodyWithActions(story.article.body, onNavigate)
    }
}

@Composable
private fun LabelledList(title: String, items: List<String>, icon: ImageVector, tint: Color) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    items.forEach { item ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(item, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// « Qui décide de ça ? »

@Composable
fun levelLabel(level: DecisionLevel): String = stringResource(
    when (level) {
        DecisionLevel.COMMUNE -> Res.string.comprendre_level_commune
        DecisionLevel.INTERCOMMUNALITE -> Res.string.comprendre_level_intercommunalite
        DecisionLevel.DEPARTEMENT -> Res.string.comprendre_level_departement
        DecisionLevel.REGION -> Res.string.comprendre_level_region
        DecisionLevel.ETAT -> Res.string.comprendre_level_etat
        DecisionLevel.UNION_EUROPEENNE -> Res.string.comprendre_level_union_europeenne
    },
)

private fun roleResource(role: PublicRole): StringResource = when (role) {
    PublicRole.MAYOR -> Res.string.comprendre_role_maire
    PublicRole.LOCAL_ELECTED -> Res.string.comprendre_role_elu_local
    PublicRole.DEPUTY -> Res.string.comprendre_role_depute
    PublicRole.SENATOR -> Res.string.comprendre_role_senateur
    PublicRole.MEP -> Res.string.comprendre_role_eurodepute
    PublicRole.MINISTER -> Res.string.comprendre_role_ministre
    PublicRole.PRESIDENT -> Res.string.comprendre_role_president
    PublicRole.OTHER -> Res.string.comprendre_role_autre
}

/** « Niveaux : Commune · Région · État », for the cards. */
@Composable
fun levelsLine(sheet: QuiDecide): String = stringResource(Res.string.comprendre_who_levels, sheet.steps.map { levelLabel(it.level) }.joinToString(" · "))

@Composable
fun QuiDecideDetail(
    sheet: QuiDecide,
    persons: Map<String, PublicPerson>,
    onBack: (() -> Unit)?,
    onNavigate: (Space) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    elus: ElusLibrary? = null,
    onOpenElus: (() -> Unit)? = null,
) {
    LaunchedEffect(elus) { elus?.load() }
    DetailColumn(sheet.article, stringResource(Res.string.comprendre_kind_who), onBack, compact, modifier.testTag(ComprendreDetailTags.WHO)) {
        ElusBox(sheet, sheet.persons.mapNotNull { persons[it] }, elus, onOpenElus)
        Spacer(Modifier.height(16.dp))
        BodyWithActions(sheet.body, onNavigate)
    }
}

/**
 * The box at the start of a « Qui décide de ça ? » sheet (owner's decision of 7 November 2026): each
 * level of the chain of decision, what it decides, the elected officials concerned and, under the
 * levels whose roles they hold, the public persons the editorial team linked to the sheet, then
 * the officials of the reader's commune once it is chosen (D-047: the directory of elected
 * officials, found on the device); until then, a button to indicate the commune, or without server
 * the way to the official directories.
 */
@Composable
private fun ElusBox(sheet: QuiDecide, linked: List<PublicPerson>, elus: ElusLibrary?, onOpenElus: (() -> Unit)?) {
    val mine = elus?.mine
    val palette = PpfTheme.palette
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(ComprendreDetailTags.ELUS),
        shape = MaterialTheme.shapes.medium,
        color = palette.brandTint,
        contentColor = palette.ink,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Landmark, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.comprendre_elus_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            sheet.steps.forEachIndexed { index, step ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = palette.border) else Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().testTag(ComprendreDetailTags.level(step.level))) {
                    Text(levelLabel(step.level), style = MaterialTheme.typography.labelLarge, color = palette.brand)
                    Spacer(Modifier.height(2.dp))
                    Text(step.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(step.roles.map { stringResource(roleResource(it)) }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    linked.filter { person -> person.roles.any { it in step.roles } }.forEach { person ->
                        Spacer(Modifier.height(6.dp))
                        PersonLine(person)
                    }
                    if (mine != null) {
                        Spacer(Modifier.height(6.dp))
                        BoxLevelOfficials(mine.officials.level(step.level), mine.officials)
                    }
                }
            }
            ElusBoxFooter(elus, hasLocalLevel = sheet.steps.any { it.level.local }, onOpenPage = onOpenElus)
        }
    }
}

private val DecisionLevel.local: Boolean get() = this != DecisionLevel.ETAT && this != DecisionLevel.UNION_EUROPEENNE

@Composable
private fun PersonLine(person: PublicPerson) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.User, contentDescription = null, tint = palette.brand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall)
            person.function?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted) }
        }
        if (person.name.startsWith(FeedPost.EXAMPLE_MARKER)) {
            Spacer(Modifier.width(8.dp))
            ExampleBadge()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces

/** Badge, title, meta line (type, update, origin), then [content], in a readable column. */
@Composable
private fun DetailColumn(
    article: Article,
    kind: String,
    onBack: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val palette = PpfTheme.palette
    Column(modifier.verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 24.dp)) {
        Column(Modifier.widthIn(max = 720.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.content_back), onBack, Modifier.testTag(ComprendreDetailTags.BACK), tint = palette.ink)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PpfBadge(kind, BadgeStyle.BRAND)
                if (article.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                    Spacer(Modifier.width(8.dp))
                    ExampleBadge()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(article.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.titleSmall, color = palette.brand)
            }
            Spacer(Modifier.height(6.dp))
            Text(detailMeta(article, kind), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun detailMeta(article: Article, kind: String): String {
    val origin = stringResource(if (article.origin == ContentOrigin.AI_ASSISTED) Res.string.content_origin_ai else Res.string.content_origin_human)
    val updated = stringResource(Res.string.content_updated, CivilDate.fromEpochSeconds(article.updatedAt).toFrench())
    return "$kind · $updated · $origin"
}

/**
 * The body of a content with its « Et maintenant ? » block turned into buttons and placed just
 * before the sources, which come last.
 */
@Composable
private fun BodyWithActions(body: String, onNavigate: (Space) -> Unit) {
    val actions = mutableListOf<List<String>>()
    val lines = ContentBlocks.extract(body.lines(), NEXT_ACTIONS_BLOCK, actions)
    val sourcesAt = lines.indexOfFirst { it.trim() == "## Sources" }
    val main = (if (sourcesAt >= 0) lines.subList(0, sourcesAt) else lines).joinToString("\n").trim()
    val sources = if (sourcesAt >= 0) lines.subList(sourcesAt + 1, lines.size).joinToString("\n").trim() else ""
    SelectionContainer { MarkdownText(main) }
    actions.firstOrNull()?.joinToString("\n")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        Spacer(Modifier.height(8.dp))
        NextActionsCard(it, onNavigate)
    }
    if (sources.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text(stringResource(Res.string.comprendre_sources), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        SelectionContainer { MarkdownText(sources) }
    }
}

private const val NEXT_ACTIONS_BLOCK = "et-maintenant"

// ---------------------------------------------------------------------------------------------
// Cards of the home

@Composable
fun DecryptageCard(check: Decryptage, onOpen: () -> Unit, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictBadge(check.verdict)
            Spacer(Modifier.weight(1f))
            if (check.article.title.startsWith(FeedPost.EXAMPLE_MARKER)) ExampleBadge()
        }
        Spacer(Modifier.height(8.dp))
        Text("« ${check.claim} »", style = MaterialTheme.typography.titleSmall)
        check.article.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
    }
}

@Composable
fun HistoryCard(story: HistoryStory, onOpen: () -> Unit, selected: Boolean, modifier: Modifier = Modifier) {
    SimpleCard(story.article, "${story.period} · ${story.country}", onOpen, selected, modifier)
}

@Composable
fun QuiDecideCard(sheet: QuiDecide, onOpen: () -> Unit, selected: Boolean, modifier: Modifier = Modifier) {
    SimpleCard(sheet.article, levelsLine(sheet), onOpen, selected, modifier)
}

@Composable
private fun SimpleCard(article: Article, line: String, onOpen: () -> Unit, selected: Boolean, modifier: Modifier) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(article.title.removePrefix(FeedPost.EXAMPLE_MARKER).trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (article.title.startsWith(FeedPost.EXAMPLE_MARKER)) {
                Spacer(Modifier.width(8.dp))
                ExampleBadge()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(line, style = MaterialTheme.typography.labelLarge, color = palette.brand)
        article.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
    }
}
