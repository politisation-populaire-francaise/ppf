package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.menu_collapse
import org.politisationpopulairefrancaise.ui.resources.menu_expand
import org.politisationpopulairefrancaise.ui.resources.menu_soon
import org.politisationpopulairefrancaise.ui.resources.menu_state_collapsed
import org.politisationpopulairefrancaise.ui.resources.menu_state_expanded
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** What the shell does with an entry of the menu; provided by [AdaptiveShell], nothing in previews. */
val LocalMenuTargetHandler = staticCompositionLocalOf<(MenuTarget) -> Unit> { {} }

/** Test tag of the head of a rubric. */
fun rubricTag(rubric: MenuRubric): String = "nav-rubric-${rubric.id}"

/** The spaces kept out of the bottom bar: their entry in the menu keeps the tag of the space (tests, deep links). */
private val menuOnlySpaces = setOf(Space.VOIR, Space.COMPRENDRE, Space.DECIDER, Space.REVENDICATIONS)

/** The entry that carries the tag of a menu-only space: the first one of its own rubric leading to its home. */
private val spaceTagHolders: Map<Space, String> = menuOnlySpaces.mapNotNull { space ->
    val leadsHome: (MenuEntry) -> Boolean = { entry ->
        when (val target = entry.target) {
            is MenuTarget.OpenSpace -> target.space == space
            is MenuTarget.Decider -> target.highlight == null && space == Space.DECIDER
            is MenuTarget.Revendications -> target.viewId == "tout" && space == Space.REVENDICATIONS
            else -> false
        }
    }
    val entry = rubricFor(space)?.entries?.firstOrNull(leadsHome) ?: MenuRubric.entries.flatMap { it.entries }.firstOrNull(leadsHome)
    entry?.let { space to it.id }
}.toMap()

/**
 * The seven rubrics of the menu (D-240), each with its sub-rubrics as an accordion: touching a
 * rubric unfolds it (a chevron says it can), one rubric open at a time, the rubric of the current
 * screen open at first. Used by the menu of phones and by the side bar of wide windows.
 */
@Composable
fun RubricMenu(selected: Space) {
    val onTarget = LocalMenuTargetHandler.current
    var open by rememberSaveable { mutableStateOf(rubricFor(selected)?.id) }
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember { MenuRubric.entries.flatMap { it.entries } }
    // Another way in (bottom bar, tutorial, a link): the rubric of the new space unfolds, unless the open one already leads there.
    LaunchedEffect(selected) {
        val openRubric = MenuRubric.entries.firstOrNull { it.id == open }
        if (openRubric?.entries?.any { it.space == selected } != true) rubricFor(selected)?.let { open = it.id }
    }
    // The current entry: the one last touched when it still matches the screen, else the first leading to the space.
    val chosen = all.firstOrNull { it.id == current }?.takeIf { it.space == selected }
    val shown = chosen ?: all.firstOrNull { it.space == selected }
    Column {
        MenuRubric.entries.forEach { rubric ->
            val expanded = open == rubric.id
            val holdsCurrent = rubric.entries.any { it.space == selected }
            RubricHead(rubric, expanded, holdsCurrent) { open = if (expanded) null else rubric.id }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    rubric.entries.forEach { entry ->
                        val soon = entry.target == MenuTarget.Soon
                        val isCurrent = entry == shown
                        val tag = spaceTagHolders.entries.firstOrNull { it.value == entry.id }?.key?.let(ShellTags::navItem) ?: ShellTags.subItem(entry.id)
                        SubRubricRow(stringResource(entry.label), isCurrent, soon, Modifier.testTag(tag)) {
                            current = entry.id
                            onTarget(entry.target)
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun RubricHead(rubric: MenuRubric, expanded: Boolean, holdsCurrent: Boolean, onToggle: () -> Unit) {
    val palette = PpfTheme.palette
    val title = stringResource(rubric.title)
    val action = stringResource(if (expanded) Res.string.menu_collapse else Res.string.menu_expand, title)
    val state = stringResource(if (expanded) Res.string.menu_state_expanded else Res.string.menu_state_collapsed)
    val tint = if (holdsCurrent) palette.brand else palette.ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (expanded) palette.brandTint else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
            .semantics { stateDescription = state }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp)
            .testTag(rubricTag(rubric)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(rubric.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = tint, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(
            if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
            contentDescription = null,
            tint = palette.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SubRubricRow(label: String, current: Boolean, soon: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = PpfTheme.palette
    val soonLabel = stringResource(Res.string.menu_soon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (soon) Modifier.semantics { contentDescription = "$label, $soonLabel" } else Modifier.selectable(selected = current, role = Role.Tab, onClick = onClick))
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(22.dp).background(if (current) palette.brand else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = when {
                current -> palette.ink
                soon -> palette.inkSubtle
                else -> palette.inkMuted
            },
            modifier = Modifier.weight(1f),
        )
        if (soon) {
            PpfBadge(soonLabel, BadgeStyle.NEUTRAL)
            Spacer(Modifier.width(8.dp))
        }
    }
}
