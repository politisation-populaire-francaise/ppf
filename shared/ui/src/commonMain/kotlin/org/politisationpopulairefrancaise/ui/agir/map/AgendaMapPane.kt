package org.politisationpopulairefrancaise.ui.agir.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.map.AgendaMapPoints
import org.politisationpopulairefrancaise.core.agir.map.MapArea
import org.politisationpopulairefrancaise.core.agir.map.MapPoint
import org.politisationpopulairefrancaise.core.agir.map.MapPointState
import org.politisationpopulairefrancaise.core.agir.map.MapStyle
import org.politisationpopulairefrancaise.ui.agir.agenda.AgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.agenda.MetaLine
import org.politisationpopulairefrancaise.ui.agir.agenda.TypeMark
import org.politisationpopulairefrancaise.ui.agir.agenda.dayHeader
import org.politisationpopulairefrancaise.ui.agir.agenda.typeLabel
import org.politisationpopulairefrancaise.ui.agir.agenda.whenText
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.PpfToolbarButton
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_filters
import org.politisationpopulairefrancaise.ui.resources.agenda_filters_active
import org.politisationpopulairefrancaise.ui.resources.agenda_map_soon_title
import org.politisationpopulairefrancaise.ui.resources.agenda_ongoing
import org.politisationpopulairefrancaise.ui.resources.agenda_type_assemblee_generale
import org.politisationpopulairefrancaise.ui.resources.agenda_type_autre
import org.politisationpopulairefrancaise.ui.resources.agenda_type_blocage
import org.politisationpopulairefrancaise.ui.resources.agenda_type_greve
import org.politisationpopulairefrancaise.ui.resources.agenda_type_manifestation
import org.politisationpopulairefrancaise.ui.resources.agenda_type_rassemblement
import org.politisationpopulairefrancaise.ui.resources.agenda_type_reunion
import org.politisationpopulairefrancaise.ui.resources.carte_attribution
import org.politisationpopulairefrancaise.ui.resources.carte_close
import org.politisationpopulairefrancaise.ui.resources.carte_count
import org.politisationpopulairefrancaise.ui.resources.carte_description
import org.politisationpopulairefrancaise.ui.resources.carte_empty
import org.politisationpopulairefrancaise.ui.resources.carte_here
import org.politisationpopulairefrancaise.ui.resources.carte_label
import org.politisationpopulairefrancaise.ui.resources.carte_label_cancelled
import org.politisationpopulairefrancaise.ui.resources.carte_label_postponed
import org.politisationpopulairefrancaise.ui.resources.carte_open
import org.politisationpopulairefrancaise.ui.resources.carte_recenter
import org.politisationpopulairefrancaise.ui.resources.carte_show_list
import org.politisationpopulairefrancaise.ui.resources.carte_sources
import org.politisationpopulairefrancaise.ui.resources.carte_sources_bundled_only
import org.politisationpopulairefrancaise.ui.resources.carte_sources_text
import org.politisationpopulairefrancaise.ui.resources.carte_unavailable_text
import org.politisationpopulairefrancaise.ui.resources.carte_view_choice
import org.politisationpopulairefrancaise.ui.resources.carte_view_list
import org.politisationpopulairefrancaise.ui.resources.carte_view_map
import org.politisationpopulairefrancaise.ui.resources.carte_without_position
import org.politisationpopulairefrancaise.ui.resources.carte_zoom_in
import org.politisationpopulairefrancaise.ui.resources.carte_zoom_out
import org.politisationpopulairefrancaise.ui.theme.DarkPalette
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the map view, also useful for accessibility tooling. */
object AgendaMapTags {
    const val VIEW_CHOICE = "carte-view-choice"
    const val VIEW = "carte-view-" // followed by "list" or "map"
    const val PANE = "carte-pane"
    const val MAP = "carte-map"
    const val COUNT = "carte-count"
    const val WITHOUT_POSITION = "carte-without-position"
    const val SHOW_LIST = "carte-show-list"
    const val EMPTY = "carte-empty"
    const val ZOOM_IN = "carte-zoom-in"
    const val ZOOM_OUT = "carte-zoom-out"
    const val RECENTER = "carte-recenter"
    const val ATTRIBUTION = "carte-attribution"
    const val SOURCES = "carte-sources"
    const val SOURCES_DIALOG = "carte-sources-dialog"
    const val SELECTION = "carte-selection"
    const val SELECTION_CLOSE = "carte-selection-close"
    const val SELECTED = "carte-selected-" // followed by the identifier of the mobilization
    const val OPEN = "carte-open-" // followed by the identifier of the mobilization
    const val FILTERS = "carte-filters"
    const val UNAVAILABLE = "agenda-map-soon" // the tag of the former "coming soon" box, kept for the agenda tests
}

/**
 * The choice "Liste" / "Carte" of the agenda, shown only where a map can be drawn: a group of two
 * radio buttons (read as such by screen readers), the list first since it stays the main path.
 */
@Composable
fun AgendaViewChoice(state: AgendaMapState, modifier: Modifier = Modifier) {
    if (LocalAgendaMapRenderer.current == null) return
    val palette = PpfTheme.palette
    val label = stringResource(Res.string.carte_view_choice)
    Row(
        modifier = modifier.selectableGroup().semantics { contentDescription = label }.testTag(AgendaMapTags.VIEW_CHOICE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((view, text, icon) in listOf(
            Triple(AgendaView.LIST, stringResource(Res.string.carte_view_list), Lucide.List),
            Triple(AgendaView.MAP, stringResource(Res.string.carte_view_map), Lucide.Map),
        )) {
            val selected = state.view == view
            val shape = MaterialTheme.shapes.extraSmall
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { state.view = view })
                    .background(if (selected) palette.brandTint else palette.surface2, shape)
                    .border(1.dp, if (selected) palette.brand else palette.borderStrong, shape)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp)
                    .testTag(AgendaMapTags.VIEW + view.name.lowercase()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) palette.brand else palette.inkMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.brand else palette.ink)
            }
        }
    }
}

/**
 * Where the platform cannot draw the map (the computer today, with Java 21; iOS until it is
 * compiled): an honest sentence above the list, which shows every mobilization.
 */
@Composable
fun AgendaMapUnavailable(modifier: Modifier = Modifier) {
    if (LocalAgendaMapRenderer.current != null) return
    InfoBox(Lucide.Map, modifier.testTag(AgendaMapTags.UNAVAILABLE)) {
        Text(stringResource(Res.string.agenda_map_soon_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.carte_unavailable_text), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The map view of the agenda (M1, AG-01): the mobilizations of the current tab, with the filters
 * of the list, as points with their type and day; touching a point shows the mobilizations of
 * that place, each opening the detail of the agenda ([AgendaLibrary.open]). Buttons zoom and
 * bring the map back to France (no gesture is needed), the attribution of OpenStreetMap is always
 * visible, and the list stays one touch away: the map is never the only way to an information.
 *
 * The background is the one bundled with the application (no network needed), and later the
 * detailed one of our server: nothing is asked of any other server.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaMapPane(
    agenda: AgendaLibrary,
    state: AgendaMapState,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalAgendaMapRenderer.current ?: return
    val palette = PpfTheme.palette
    val dark = palette == DarkPalette
    val now = agenda.now
    val shown = remember(agenda.mobilizations, agenda.query, now) { agenda.shown() }
    val selection = remember(shown, now) { AgendaMapPoints.of(shown, now) }
    val labels = pointLabels(selection.points, now)
    val selected = state.selected.filter { address -> selection.points.any { it.address == address } }
    val points = remember(selection, labels, selected) {
        AgendaMapPoints.geoJson(selection.points, selected.firstOrNull()) { labels[it.address].orEmpty() }
    }
    val colors = palette.mapColors(dark)
    val withServer = state.tilesUrl != null
    val style = remember(colors, withServer) { MapStyle.json(colors, withServer) }
    val serverAllowed = rememberServerTilesAllowed()
    val router = remember(state.tilesUrl) { BundledMap.router(state.tilesUrl, serverAllowed = serverAllowed) }
    var sources by rememberSaveable { mutableStateOf(false) }

    Column(modifier.testTag(AgendaMapTags.PANE)) {
        // What the map shows, and what it cannot: the filters, the number of points, those that are only in the list.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                val count = agenda.filters.constraintCount
                PpfToolbarButton(
                    icon = Lucide.SlidersHorizontal,
                    text = stringResource(Res.string.agenda_filters),
                    onClick = onOpenFilters,
                    count = count,
                    contentDescription = if (count > 0) pluralStringResource(Res.plurals.agenda_filters_active, count, count) else null,
                    modifier = Modifier.testTag(AgendaMapTags.FILTERS),
                )
                Text(
                    pluralStringResource(Res.plurals.carte_count, selection.points.size, selection.points.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(AgendaMapTags.COUNT),
                )
            }
            if (selection.withoutPosition.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(Res.plurals.carte_without_position, selection.withoutPosition.size, selection.withoutPosition.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        modifier = Modifier.weight(1f).testTag(AgendaMapTags.WITHOUT_POSITION),
                    )
                    PpfTextButton(onClick = { state.view = AgendaView.LIST }, text = stringResource(Res.string.carte_show_list), modifier = Modifier.testTag(AgendaMapTags.SHOW_LIST))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val description = pluralStringResource(Res.plurals.carte_description, selection.points.size, selection.points.size)
            renderer.MapView(
                request = MapViewRequest(
                    style = style,
                    points = points,
                    pointCount = selection.points.size,
                    colors = palette.pointColors(dark),
                    minZoom = MapArea.MIN_ZOOM,
                    maxZoom = if (withServer) MapArea.MAX_ZOOM_SERVER else MapArea.MAX_ZOOM_BUNDLED,
                    camera = state.camera,
                    commands = state.commands,
                    router = router,
                    description = description,
                    onPoints = { addresses ->
                        val touched = selection.points.filter { it.address in addresses }
                        state.selected = touched.firstOrNull()?.let { first -> AgendaMapPoints.samePlace(selection.points, first).map { it.address } }.orEmpty()
                    },
                    onBackground = { state.selected = emptyList() },
                    onCamera = { state.camera = it },
                ),
                modifier = Modifier.fillMaxSize().testTag(AgendaMapTags.MAP),
            )
            // Controls: zoom and France, always reachable without any gesture on the map (WCAG 2.5.7).
            Column(
                Modifier.align(Alignment.TopEnd).padding(8.dp).background(palette.surface2, MaterialTheme.shapes.small)
                    .border(1.dp, palette.border, MaterialTheme.shapes.small),
            ) {
                PpfIconButton(Lucide.Plus, stringResource(Res.string.carte_zoom_in), state::zoomIn, Modifier.testTag(AgendaMapTags.ZOOM_IN), tint = palette.ink)
                PpfIconButton(Lucide.Minus, stringResource(Res.string.carte_zoom_out), state::zoomOut, Modifier.testTag(AgendaMapTags.ZOOM_OUT), tint = palette.ink)
                PpfIconButton(Lucide.LocateFixed, stringResource(Res.string.carte_recenter), state::recenter, Modifier.testTag(AgendaMapTags.RECENTER), tint = palette.ink)
            }
            // The attribution of OpenStreetMap (ODbL), always visible; it opens the sources of the map.
            Text(
                text = stringResource(Res.string.carte_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = palette.ink,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(palette.surface2.copy(alpha = 0.92f), MaterialTheme.shapes.extraSmall)
                    .clickable(onClickLabel = stringResource(Res.string.carte_sources), role = Role.Button) { sources = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag(AgendaMapTags.ATTRIBUTION),
            )
            if (selection.points.isEmpty()) {
                InfoBox(Lucide.MapPin, Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp).testTag(AgendaMapTags.EMPTY)) {
                    Text(stringResource(Res.string.carte_empty), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    PpfSecondaryButton(onClick = { state.view = AgendaView.LIST }, text = stringResource(Res.string.carte_show_list))
                }
            }
            val chosen = selected.mapNotNull { address -> selection.points.firstOrNull { it.address == address } }
            if (chosen.isNotEmpty()) {
                Selection(
                    points = chosen,
                    now = now,
                    onOpen = { agenda.open(it.mobilization) },
                    onClose = { state.selected = emptyList() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 36.dp).widthIn(max = 520.dp),
                )
            }
        }
    }
    if (sources) {
        AlertDialog(
            onDismissRequest = { sources = false },
            title = { Text(stringResource(Res.string.carte_sources)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.carte_sources_text), style = MaterialTheme.typography.bodyMedium)
                    if (!withServer) Text(stringResource(Res.string.carte_sources_bundled_only), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { PpfTextButton(onClick = { sources = false }, text = stringResource(Res.string.carte_close)) },
            modifier = Modifier.testTag(AgendaMapTags.SOURCES_DIALOG),
        )
    }
}

/** The mobilizations of the place touched on the map, each with a button to its detail. */
@Composable
private fun Selection(points: List<MapPoint>, now: Long, onOpen: (MapPoint) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val palette = PpfTheme.palette
    Surface(
        modifier = modifier.fillMaxWidth().testTag(AgendaMapTags.SELECTION),
        shape = MaterialTheme.shapes.medium,
        color = palette.surface2,
        contentColor = palette.ink,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(Res.plurals.carte_here, points.size, points.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PpfIconButton(Lucide.X, stringResource(Res.string.carte_close), onClose, Modifier.testTag(AgendaMapTags.SELECTION_CLOSE))
            }
            points.forEach { point ->
                val m = point.mobilization
                PpfCard(modifier = Modifier.fillMaxWidth().testTag(AgendaMapTags.SELECTED + m.identifier), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        TypeMark(m)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stateText(point), style = MaterialTheme.typography.labelMedium, color = palette.inkMuted, modifier = Modifier.weight(1f, fill = false))
                                if (m.example) ExampleBadge()
                            }
                            Text(m.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            MetaLine(Lucide.Clock, whenText(m, full = true))
                            MetaLine(Lucide.MapPin, "${m.location} · ${m.territory}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PpfPrimaryButton(onClick = { onOpen(point) }, text = stringResource(Res.string.carte_open), modifier = Modifier.testTag(AgendaMapTags.OPEN + m.identifier))
                }
            }
        }
    }
}

/** The type, and the status when the event is cancelled or postponed (in words, never the colour alone). */
@Composable
private fun stateText(point: MapPoint): String {
    val type = typeLabel(point.mobilization.type)
    return when (point.state) {
        MapPointState.CANCELLED -> stringResource(Res.string.carte_label_cancelled, type)
        MapPointState.POSTPONED -> stringResource(Res.string.carte_label_postponed, type)
        else -> type
    }
}

/**
 * The label of each point, by address: its type and its day ("Rassemblement" / "Demain"), with
 * "Annulé" or "Reporté" when it applies, and "En cours" for an event under way.
 */
@Composable
private fun pointLabels(points: List<MapPoint>, now: Long): Map<String, String> {
    val labels = LinkedHashMap<String, String>()
    for (p in points) {
        val type = stringResource(typeResource(p.mobilization.type))
        val first = when (p.state) {
            MapPointState.CANCELLED -> stringResource(Res.string.carte_label_cancelled, type)
            MapPointState.POSTPONED -> stringResource(Res.string.carte_label_postponed, type)
            else -> type
        }
        val day = if (p.state == MapPointState.ONGOING) stringResource(Res.string.agenda_ongoing) else dayHeader(ParisTime.epochDay(p.mobilization.start), now)
        labels[p.address] = stringResource(Res.string.carte_label, first, day)
    }
    return labels
}

private fun typeResource(type: MobilizationType) = when (type) {
    MobilizationType.MANIFESTATION -> Res.string.agenda_type_manifestation
    MobilizationType.BLOCAGE -> Res.string.agenda_type_blocage
    MobilizationType.RASSEMBLEMENT -> Res.string.agenda_type_rassemblement
    MobilizationType.GREVE -> Res.string.agenda_type_greve
    MobilizationType.ASSEMBLEE_GENERALE -> Res.string.agenda_type_assemblee_generale
    MobilizationType.REUNION -> Res.string.agenda_type_reunion
    MobilizationType.AUTRE -> Res.string.agenda_type_autre
}

/**
 * Whether the detailed tiles of our server may be asked now: a network is there and the data
 * saving mode of the system is off. Read at each request; the bundled background needs neither.
 */
@Composable
internal expect fun rememberServerTilesAllowed(): () -> Boolean
