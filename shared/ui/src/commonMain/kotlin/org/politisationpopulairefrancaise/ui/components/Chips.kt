package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/*
 * Chips and choice rows of the visual identity: what filters, views and forms are made of. Each
 * piece keeps a visible size close to the mock-ups (36 dp chips) and a touch target of at least
 * 48 dp (Material's minimum interactive size), with the semantics of a checkbox, a radio button
 * or a button, so that screen readers announce the state.
 */

/**
 * A chip that is on or off (a theme, a territory, a status). Selected: brand tint, brand border and
 * a check mark; screen readers hear a checkbox and its state. [leadingIcon] replaces the check
 * mark when given. A disabled chip keeps its state visible (a criterion locked by a view).
 */
@Composable
fun PpfSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val palette = PpfTheme.palette
    val shape = MaterialTheme.shapes.extraSmall
    val color = if (selected) palette.brand else palette.inkMuted
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .background(if (selected) palette.brandTint else palette.surface2, shape)
            .border(1.dp, if (selected) palette.brand else palette.borderStrong, shape)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = leadingIcon ?: if (selected) Lucide.Check else null
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.brand else palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An active filter shown above a list: its label and a cross. The whole chip is one button that
 * removes the filter; [removeDescription] is what screen readers say ("Retirer le filtre Santé").
 */
@Composable
fun PpfRemovableChip(
    label: String,
    onRemove: () -> Unit,
    removeDescription: String,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    val shape = MaterialTheme.shapes.extraSmall
    Surface(
        onClick = onRemove,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) {
                contentDescription = removeDescription
                role = Role.Button
            },
        shape = shape,
        color = palette.brandTint,
        contentColor = palette.brand,
        border = BorderStroke(1.dp, palette.brand),
    ) {
        Row(Modifier.heightIn(min = 36.dp).padding(start = 12.dp, end = 8.dp).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * One choice among several (a sort, an author scope): a round mark and a label, 48 dp high, with
 * the semantics of a radio button. Put the rows of one question in a `selectableGroup()`.
 * [supporting] is a short explanation under the label.
 */
@Composable
fun PpfRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val palette = PpfTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioMark(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }
    }
}

/** The round mark of [PpfRadioRow]: a ring, filled with a dot when selected. */
@Composable
private fun RadioMark(selected: Boolean) {
    val palette = PpfTheme.palette
    Box(
        modifier = Modifier.size(20.dp).border(2.dp, if (selected) palette.brand else palette.borderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).background(if (selected) palette.brand else Color.Transparent, CircleShape))
    }
}

/**
 * A button of a tool bar (filters, sort, personalize): outlined, an icon, a label and, when
 * [count] is above zero, a small brand pill with the number. [contentDescription] replaces the
 * label for screen readers when the number needs words ("Filtres, 2 filtres actifs").
 */
@Composable
fun PpfToolbarButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val palette = PpfTheme.palette
    val active = count > 0
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(if (contentDescription != null) Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription } else Modifier),
        shape = MaterialTheme.shapes.small,
        color = if (active) palette.brandTint else palette.surface2,
        contentColor = if (active) palette.brand else palette.ink,
        border = BorderStroke(1.dp, if (active) palette.brand else palette.borderStrong),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp)
                .then(if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (active) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.heightIn(min = 20.dp).background(palette.brand, CircleShape).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = palette.onBrand)
                }
            }
        }
    }
}
