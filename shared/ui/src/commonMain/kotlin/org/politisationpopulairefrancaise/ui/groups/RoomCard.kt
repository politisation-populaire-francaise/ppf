package org.politisationpopulairefrancaise.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.groups_closed_badge
import org.politisationpopulairefrancaise.ui.resources.groups_join
import org.politisationpopulairefrancaise.ui.resources.groups_join_named
import org.politisationpopulairefrancaise.ui.resources.groups_joined
import org.politisationpopulairefrancaise.ui.resources.groups_territory_unknown
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * A room in a list: its mark and name, its territory and badges, the start of its description,
 * then the way to join it. Everything stacks under the name so that a narrow list (a phone, the
 * left column of a desktop) never cuts the name short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomCard(
    room: Group,
    joined: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PpfTheme.palette
    PpfCard(
        modifier = modifier.widthIn(max = 720.dp).fillMaxWidth().testTag(GroupsTags.ROOM + room.id),
        onClick = onOpen,
        selected = selected,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(seed = room.id, letters = roomLetters(room.displayName), size = 40.dp, rounded = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(room.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                // The territory first; the badges follow, on the next line when the card is narrow.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            room.territory ?: stringResource(Res.string.groups_territory_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (joined) PpfBadge(stringResource(Res.string.groups_joined), BadgeStyle.SUCCESS, Modifier.testTag(GroupsTags.JOINED + room.id))
                    if (room.example) ExampleBadge()
                    if (!room.open) PpfBadge(stringResource(Res.string.groups_closed_badge))
                }
                room.about?.let { about ->
                    Spacer(Modifier.height(4.dp))
                    Text(about, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!joined) {
                    Spacer(Modifier.height(8.dp))
                    val label = stringResource(Res.string.groups_join_named, room.displayName)
                    PpfSecondaryButton(
                        onClick = onJoin,
                        text = stringResource(Res.string.groups_join),
                        small = true,
                        icon = Lucide.Plus,
                        modifier = Modifier.testTag(GroupsTags.JOIN + room.id).semantics { contentDescription = label },
                    )
                }
            }
        }
    }
}
