package org.politisationpopulairefrancaise.ui.messages

import org.politisationpopulairefrancaise.ui.account.PublishBlockedNotice
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.groups.GroupCreationForm
import org.politisationpopulairefrancaise.ui.groups.GroupScreen
import org.politisationpopulairefrancaise.ui.groups.GroupsLibrary
import org.politisationpopulairefrancaise.ui.groups.GroupsStatus
import org.politisationpopulairefrancaise.ui.groups.GroupsTags
import org.politisationpopulairefrancaise.ui.groups.RoomCard
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.groups_create
import org.politisationpopulairefrancaise.ui.resources.groups_create_named
import org.politisationpopulairefrancaise.ui.resources.groups_membership_private
import org.politisationpopulairefrancaise.ui.resources.groups_mine_empty
import org.politisationpopulairefrancaise.ui.resources.groups_mine_title
import org.politisationpopulairefrancaise.ui.resources.groups_near_title
import org.politisationpopulairefrancaise.ui.resources.groups_none
import org.politisationpopulairefrancaise.ui.resources.groups_none_for_query
import org.politisationpopulairefrancaise.ui.resources.groups_none_for_territory
import org.politisationpopulairefrancaise.ui.resources.groups_not_found_hint
import org.politisationpopulairefrancaise.ui.resources.groups_others_title
import org.politisationpopulairefrancaise.ui.resources.groups_refresh
import org.politisationpopulairefrancaise.ui.resources.groups_search_hint
import org.politisationpopulairefrancaise.ui.resources.groups_select_hint
import org.politisationpopulairefrancaise.ui.resources.groups_status_loading
import org.politisationpopulairefrancaise.ui.resources.groups_status_offline
import org.politisationpopulairefrancaise.ui.resources.groups_status_synced
import org.politisationpopulairefrancaise.ui.resources.groups_status_unavailable
import org.politisationpopulairefrancaise.ui.resources.groups_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.groups_suggestions_title
import org.politisationpopulairefrancaise.ui.resources.groups_territories_label
import org.politisationpopulairefrancaise.ui.resources.groups_territory_all
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * The "Collectifs" view of the messages: the rooms of collectives, unions and associations
 * (NIP-29, decisions D-027 and D-028). The joined rooms come first, then the search, the
 * suggestions by territory and the creation of a room. Reading needs no account and works offline
 * from the device's copy; joining stays on the device.
 *
 * On wide windows the open room or the creation form sits beside the list; elsewhere they take the
 * whole messages screen ([CollectivesFullScreen]).
 */
@Composable
fun CollectivesPage(
    groups: GroupsLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    onNavigate: (Space) -> Unit,
) {
    LaunchedEffect(groups) { groups.ensureStarted() }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val room = groups.selected
    BoxWithConstraints(Modifier.fillMaxSize().testTag(GroupsTags.PAGE)) {
        val wide = windowClass.width == WindowWidthClass.EXPANDED
        val sideBySide = wide && maxWidth >= SIDE_BY_SIDE_MIN
        val listWidth = (maxWidth * 0.42f).coerceIn(300.dp, 400.dp)
        when {
            // Wide enough for both: the list on the left, the room or the form beside it.
            sideBySide -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    RoomList(groups, contentPadding = 16.dp)
                }
                VerticalDivider(color = PpfTheme.palette.border)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        groups.creating -> GroupCreationForm(groups, session, onClose = groups::cancelCreation, onNavigate = onNavigate, compact = false)
                        room != null -> GroupScreen(groups, room, session, onBack = null, onNavigate = onNavigate, compact = false)
                        else -> SelectHint()
                    }
                }
            }
            // A wide window whose page is narrow: the room or the form in place of the list.
            wide && groups.creating -> GroupCreationForm(groups, session, onClose = groups::cancelCreation, onNavigate = onNavigate, compact = false)
            wide && room != null -> GroupScreen(groups, room, session, onBack = groups::close, onNavigate = onNavigate, compact = false)
            else -> RoomList(groups, contentPadding = if (compact) 16.dp else 24.dp)
        }
    }
}

/** The narrowest page that holds the list and a room side by side. */
private val SIDE_BY_SIDE_MIN = 640.dp

/** Whether the open room or the creation form replaces the whole messages screen (phones, tablets). */
fun collectivesTakeTheScreen(groups: GroupsLibrary, windowClass: WindowClass): Boolean =
    windowClass.width != WindowWidthClass.EXPANDED && (groups.creating || groups.selected != null)

/** The open room or the creation form over the whole messages screen, with a way back to the list. */
@Composable
fun CollectivesFullScreen(
    groups: GroupsLibrary,
    session: AccountSession,
    windowClass: WindowClass,
    onNavigate: (Space) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val room = groups.selected
    when {
        groups.creating -> GroupCreationForm(groups, session, onClose = groups::cancelCreation, onNavigate = onNavigate, compact = compact, modifier = modifier)
        room != null -> GroupScreen(groups, room, session, onBack = groups::close, onNavigate = onNavigate, compact = compact, modifier = modifier)
    }
}

@Composable
private fun SelectHint() {
    val palette = PpfTheme.palette
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lucide.Users, contentDescription = null, tint = palette.brand, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.groups_select_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.inkMuted,
                modifier = Modifier.testTag(GroupsTags.SELECT_HINT),
            )
        }
    }
}

/** The list of rooms: tools, joined rooms, suggestions by territory, the way to create a room. */
@Composable
private fun RoomList(groups: GroupsLibrary, contentPadding: Dp) {
    val palette = PpfTheme.palette
    val query = groups.query
    val mine = groups.mine
    val suggestions = groups.suggestions
    val near = suggestions.filter(groups::isNearJoined)
    val others = suggestions.filterNot(groups::isNearJoined)
    val territories = groups.territories
    val selectedId = groups.selected?.id
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(GroupsTags.LIST),
        contentPadding = PaddingValues(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "tools") {
            Column(Modifier.widthIn(max = 720.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PpfTextField(
                        value = query,
                        onValueChange = groups::search,
                        placeholder = stringResource(Res.string.groups_search_hint),
                        modifier = Modifier.weight(1f).testTag(GroupsTags.SEARCH),
                        singleLine = true,
                    )
                    if (groups.hasRemote) {
                        Spacer(Modifier.width(4.dp))
                        PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.groups_refresh), groups::refresh, Modifier.testTag(GroupsTags.REFRESH))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLine(groups.status, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PpfPrimaryButton(
                        onClick = { groups.beginCreation() },
                        text = stringResource(Res.string.groups_create),
                        small = true,
                        icon = Lucide.Plus,
                        // A compromised account creates no room (S-146).
                        enabled = LocalCanPublish.current,
                        modifier = Modifier.testTag(GroupsTags.CREATE),
                    )
                }
                PublishBlockedNotice(Modifier.padding(top = 8.dp))
            }
        }
        if (territories.isNotEmpty()) {
            item(key = "territories") {
                TerritoryChips(territories, groups.territoryFilter, groups::selectTerritory)
            }
        }

        item(key = "mine-title") { SectionTitle(stringResource(Res.string.groups_mine_title), Modifier.testTag(GroupsTags.MINE)) }
        if (mine.isEmpty() && query.isBlank()) {
            item(key = "mine-empty") {
                Text(
                    stringResource(Res.string.groups_mine_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(GroupsTags.MINE_EMPTY),
                )
            }
        }
        items(mine, key = { "mine-" + it.id }) { room ->
            RoomCard(room, joined = true, selected = room.id == selectedId, onOpen = { groups.open(room) }, onJoin = { })
        }

        item(key = "suggestions-title") {
            SectionTitle(stringResource(if (near.isEmpty()) Res.string.groups_suggestions_title else Res.string.groups_near_title), Modifier.testTag(GroupsTags.SUGGESTIONS))
        }
        items(near, key = { "near-" + it.id }) { room ->
            RoomCard(room, joined = false, selected = room.id == selectedId, onOpen = { groups.open(room) }, onJoin = { groups.join(room.id) })
        }
        if (near.isNotEmpty() && others.isNotEmpty()) {
            item(key = "others-title") { SectionTitle(stringResource(Res.string.groups_others_title)) }
        }
        items(others, key = { "other-" + it.id }) { room ->
            RoomCard(room, joined = false, selected = room.id == selectedId, onOpen = { groups.open(room) }, onJoin = { groups.join(room.id) })
        }
        if (suggestions.isEmpty()) {
            item(key = "empty") {
                val text = when {
                    query.isNotBlank() -> stringResource(Res.string.groups_none_for_query, query.trim())
                    groups.territoryFilter != null -> stringResource(Res.string.groups_none_for_territory)
                    mine.isEmpty() -> stringResource(Res.string.groups_none)
                    else -> null
                }
                if (text != null) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, modifier = Modifier.testTag(GroupsTags.EMPTY))
                }
            }
        }

        item(key = "create") {
            PpfCard(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(Res.string.groups_not_found_hint), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                val named = query.trim()
                PpfSecondaryButton(
                    onClick = { groups.beginCreation(named) },
                    text = if (named.isEmpty()) stringResource(Res.string.groups_create) else stringResource(Res.string.groups_create_named, named),
                    icon = Lucide.Plus,
                    enabled = LocalCanPublish.current,
                    modifier = Modifier.testTag(GroupsTags.CREATE_FROM_QUERY),
                )
            }
        }
        item(key = "privacy") {
            InfoBox(Lucide.Lock, Modifier.widthIn(max = 720.dp)) {
                Text(stringResource(Res.string.groups_membership_private), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
private fun StatusLine(status: GroupsStatus, modifier: Modifier) {
    val text = when (status) {
        GroupsStatus.Idle -> null
        GroupsStatus.Loading -> Res.string.groups_status_loading
        GroupsStatus.Unavailable -> Res.string.groups_status_unavailable
        GroupsStatus.Offline -> Res.string.groups_status_offline
        GroupsStatus.Synced -> Res.string.groups_status_synced
        GroupsStatus.Unreachable -> Res.string.groups_status_unreachable
    }
    Box(modifier) {
        if (text != null) {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = PpfTheme.palette.inkSubtle,
                modifier = Modifier.testTag(GroupsTags.STATUS),
            )
        }
    }
}

/** The suggestions by territory: one chip per territory of the known rooms, the chosen one ticked. */
@Composable
private fun TerritoryChips(territories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val palette = PpfTheme.palette
    Column {
        Text(stringResource(Res.string.groups_territories_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(
                label = stringResource(Res.string.groups_territory_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.testTag(GroupsTags.TERRITORY + "all"),
            )
            territories.forEach { territory ->
                val active = selected != null && Group.slugify(selected) == Group.slugify(territory)
                PpfSelectableChip(
                    label = territory,
                    selected = active,
                    onClick = { onSelect(if (active) null else territory) },
                    leadingIcon = if (active) null else Lucide.MapPin,
                    modifier = Modifier.testTag(GroupsTags.TERRITORY + Group.slugify(territory)),
                )
            }
        }
    }
}
