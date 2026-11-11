package org.politisationpopulairefrancaise.ui.agir.kits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.kits.CitizenKit
import org.politisationpopulairefrancaise.core.agir.kits.CitizenKits
import org.politisationpopulairefrancaise.core.agir.kits.KitGoal
import org.politisationpopulairefrancaise.core.agir.kits.KitTarget
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.agirColumns
import org.politisationpopulairefrancaise.ui.agir.manifestation.GuideReader
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.kits_back
import org.politisationpopulairefrancaise.ui.resources.kits_count
import org.politisationpopulairefrancaise.ui.resources.kits_description
import org.politisationpopulairefrancaise.ui.resources.kits_empty
import org.politisationpopulairefrancaise.ui.resources.kits_filter_clear
import org.politisationpopulairefrancaise.ui.resources.kits_filter_offline
import org.politisationpopulairefrancaise.ui.resources.kits_filter_title
import org.politisationpopulairefrancaise.ui.resources.kits_goal_convaincre
import org.politisationpopulairefrancaise.ui.resources.kits_goal_greve
import org.politisationpopulairefrancaise.ui.resources.kits_goal_manifester
import org.politisationpopulairefrancaise.ui.resources.kits_goal_s_organiser
import org.politisationpopulairefrancaise.ui.resources.kits_goal_se_presenter
import org.politisationpopulairefrancaise.ui.resources.kits_goal_se_proteger
import org.politisationpopulairefrancaise.ui.resources.kits_guide_badge
import org.politisationpopulairefrancaise.ui.resources.kits_offline_badge
import org.politisationpopulairefrancaise.ui.resources.kits_poster_text
import org.politisationpopulairefrancaise.ui.resources.kits_poster_title
import org.politisationpopulairefrancaise.ui.resources.kits_title
import org.politisationpopulairefrancaise.ui.resources.kits_tool_badge
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of « Kits d'action citoyenne ». */
object KitsTags {
    const val SCREEN = "kits-screen"
    const val BACK = "kits-back"
    const val GOAL = "kits-goal-" // followed by the goal id
    const val OFFLINE = "kits-offline"
    const val CLEAR = "kits-clear"
    const val COUNT = "kits-count"
    const val EMPTY = "kits-empty"
    const val KIT = "kits-kit-" // followed by the kit id
}

/** The label of a goal, for its filter. */
internal fun goalLabel(goal: KitGoal): StringResource = when (goal) {
    KitGoal.MANIFESTER -> Res.string.kits_goal_manifester
    KitGoal.GREVE -> Res.string.kits_goal_greve
    KitGoal.ORGANISER -> Res.string.kits_goal_s_organiser
    KitGoal.CONVAINCRE -> Res.string.kits_goal_convaincre
    KitGoal.SE_PRESENTER -> Res.string.kits_goal_se_presenter
    KitGoal.SE_PROTEGER -> Res.string.kits_goal_se_proteger
}

/** A kit as the screen shows it: its title, its sentence, its icon; null for a guide not on the device yet. */
private data class ShownKit(val kit: CitizenKit, val title: String, val text: String, val icon: ImageVector, val tool: Boolean)

/**
 * « Kits d'action citoyenne » (D-232, demand of the owner): the tools of « Agir » and the practical guides gathered
 * in one place and filtered by goal (« Je veux… ») and by use without network. A tool opens its rubric
 * ([onOpenRubric] with its id), a guide opens inside « Agir » with its sources. A guide not on the device yet is
 * not listed. Filters and reading stay in memory: nothing is sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsScreen(
    library: ContentLibrary,
    windowClass: WindowClass,
    onOpenRubric: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val compact = windowClass.width == WindowWidthClass.COMPACT
    var goalId by rememberSaveable { mutableStateOf<String?>(null) }
    var offlineOnly by rememberSaveable { mutableStateOf(false) }
    var readingSlug by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(readingSlug) { scroll.scrollTo(0) }
    SystemBack(enabled = readingSlug != null) { readingSlug = null }
    val guideOf: (String) -> Article? = { slug -> library.guides.firstOrNull { it.slug == slug } }
    val goal = KitGoal.entries.firstOrNull { it.id == goalId }

    Column(
        modifier.fillMaxSize().background(palette.surface1).verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp).testTag(KitsTags.SCREEN),
    ) {
        readingSlug?.let { slug ->
            GuideReader(guideOf(slug), onBack = { readingSlug = null })
            return@Column
        }
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBack != null) {
                PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.kits_back), onBack, Modifier.testTag(KitsTags.BACK), tint = palette.ink)
            }
            PageHeader(stringResource(Res.string.kits_title), stringResource(Res.string.kits_description), Modifier.widthIn(max = 720.dp))

            // « Je veux… »: one goal at a time, and the use without network.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.kits_filter_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitGoal.entries.forEach { candidate ->
                        PpfSelectableChip(
                            stringResource(goalLabel(candidate)),
                            goal == candidate,
                            { goalId = if (goal == candidate) null else candidate.id },
                            Modifier.testTag(KitsTags.GOAL + candidate.id),
                        )
                    }
                    PpfSelectableChip(
                        label = stringResource(Res.string.kits_filter_offline),
                        selected = offlineOnly,
                        onClick = { offlineOnly = !offlineOnly },
                        modifier = Modifier.testTag(KitsTags.OFFLINE),
                    )
                }
                if (goal != null || offlineOnly) {
                    PpfTextButton(onClick = { goalId = null; offlineOnly = false }, text = stringResource(Res.string.kits_filter_clear), modifier = Modifier.testTag(KitsTags.CLEAR))
                }
            }

            val shown = CitizenKits.filter(goal, offlineOnly).mapNotNull { kit -> shownKit(kit, guideOf) }
            Text(
                pluralStringResource(Res.plurals.kits_count, shown.size, shown.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(KitsTags.COUNT).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (shown.isEmpty()) {
                Text(stringResource(Res.string.kits_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(KitsTags.EMPTY))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = agirColumns(maxWidth)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { item ->
                                KitCard(item, compact, Modifier.weight(1f).fillMaxHeight()) {
                                    when (val target = item.kit.target) {
                                        is KitTarget.Rubric -> onOpenRubric(target.id)
                                        is KitTarget.Guide -> readingSlug = target.slug
                                    }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** The title and sentence of a kit: its rubric's for a tool, the guide's for a guide (null when not on the device). */
@Composable
private fun shownKit(kit: CitizenKit, guideOf: (String) -> Article?): ShownKit? = when (val target = kit.target) {
    is KitTarget.Guide -> guideOf(target.slug)?.let { article ->
        ShownKit(kit, article.title.removePrefix("[EXEMPLE]").trim(), article.summary.orEmpty(), Lucide.BookOpen, tool = false)
    }
    is KitTarget.Rubric -> if (kit.id == CitizenKits.POSTER) {
        ShownKit(kit, stringResource(Res.string.kits_poster_title), stringResource(Res.string.kits_poster_text), Lucide.Printer, tool = true)
    } else {
        AgirRubrics.available.firstOrNull { it.id == target.id }?.let { rubric ->
            ShownKit(kit, stringResource(rubric.title), stringResource(rubric.text), rubric.icon, tool = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitCard(item: ShownKit, compact: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(modifier = modifier.semantics { role = Role.Button }.testTag(KitsTags.KIT + item.kit.id), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(item.icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                if (item.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        maxLines = if (compact) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PpfBadge(stringResource(if (item.tool) Res.string.kits_tool_badge else Res.string.kits_guide_badge), BadgeStyle.BRAND)
                    if (item.kit.offline) PpfBadge(stringResource(Res.string.kits_offline_badge), BadgeStyle.NEUTRAL)
                }
            }
        }
    }
}
