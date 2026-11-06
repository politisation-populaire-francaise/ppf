package org.politisationpopulairefrancaise.ui.agir.organisations

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.components.Avatar
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agir_back
import org.politisationpopulairefrancaise.ui.resources.organisation_national
import org.politisationpopulairefrancaise.ui.resources.organisation_verified
import org.politisationpopulairefrancaise.ui.resources.organisations_about_directory
import org.politisationpopulairefrancaise.ui.resources.organisations_clear_filters
import org.politisationpopulairefrancaise.ui.resources.organisations_count
import org.politisationpopulairefrancaise.ui.resources.organisations_description
import org.politisationpopulairefrancaise.ui.resources.organisations_empty
import org.politisationpopulairefrancaise.ui.resources.organisations_national_section
import org.politisationpopulairefrancaise.ui.resources.organisations_near_hint
import org.politisationpopulairefrancaise.ui.resources.organisations_near_label
import org.politisationpopulairefrancaise.ui.resources.organisations_near_private
import org.politisationpopulairefrancaise.ui.resources.organisations_near_section
import org.politisationpopulairefrancaise.ui.resources.organisations_none
import org.politisationpopulairefrancaise.ui.resources.organisations_refresh
import org.politisationpopulairefrancaise.ui.resources.organisations_search_hint
import org.politisationpopulairefrancaise.ui.resources.organisations_select_hint
import org.politisationpopulairefrancaise.ui.resources.organisations_status_loading
import org.politisationpopulairefrancaise.ui.resources.organisations_status_offline
import org.politisationpopulairefrancaise.ui.resources.organisations_status_synced
import org.politisationpopulairefrancaise.ui.resources.organisations_status_unavailable
import org.politisationpopulairefrancaise.ui.resources.organisations_status_unreachable
import org.politisationpopulairefrancaise.ui.resources.organisations_territories_label
import org.politisationpopulairefrancaise.ui.resources.organisations_territory_all
import org.politisationpopulairefrancaise.ui.resources.organisations_title
import org.politisationpopulairefrancaise.ui.resources.organisations_type_all
import org.politisationpopulairefrancaise.ui.resources.organisations_types_label
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the directory. */
object OrganisationsTags {
    const val SCREEN = "organisations-screen"
    const val LIST = "organisations-list"
    const val BACK = "organisations-back"
    const val SEARCH = "organisations-search"
    const val NEAR = "organisations-near"
    const val NEAR_PRIVATE = "organisations-near-private"
    const val TYPE = "organisations-type-" // followed by the type, or "all"
    const val TERRITORY = "organisations-territory-" // followed by the folded territory with dashes, or "all"
    const val STATUS = "organisations-status"
    const val REFRESH = "organisations-refresh"
    const val COUNT = "organisations-count"
    const val CARD = "organisations-card-" // followed by the slug
    const val NEAR_SECTION = "organisations-near-section"
    const val NATIONAL_SECTION = "organisations-national-section"
    const val NONE = "organisations-none"
    const val CLEAR = "organisations-clear"
    const val SELECT_HINT = "organisations-select-hint"
    const val ABOUT = "organisations-about"

    /** The tag of a territory chip. */
    fun territory(territory: String): String = TERRITORY + FrenchText.fold(territory).replace(' ', '-')
}

/**
 * The directory of organisations (M3, AG-03): search by name and words, filters by type and
 * territory, "près de chez moi" computed on the device, then the page of an organisation. One
 * pane on phones and tablets (the list, then the page with a way back), two panes side by side on
 * wide windows. [onBack] leads back to the home of "Agir".
 *
 * Following an organisation goes through [isFollowed] and [onToggleFollow], the list of followed
 * accounts of the feed ([org.politisationpopulairefrancaise.ui.content.ContentLibrary]), kept on
 * the device. [notesKey] changes when the device received new notes, so that the open page shows
 * them.
 */
@Composable
fun OrganisationsScreen(
    library: OrganisationsLibrary,
    windowClass: WindowClass,
    isFollowed: (String) -> Boolean,
    onToggleFollow: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    notesKey: Any? = null,
    organisationAgenda: (@Composable (pubkeyHex: String, modifier: Modifier) -> Unit)? = null,
) {
    LaunchedEffect(library) { library.ensureStarted() }
    val selected = library.selected
    LaunchedEffect(selected?.address, notesKey) { if (selected != null) library.reloadPublications() }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1).testTag(OrganisationsTags.SCREEN)) {
        val sideBySide = windowClass.width == WindowWidthClass.EXPANDED && maxWidth >= SIDE_BY_SIDE_MIN
        val listWidth = (maxWidth * 0.42f).coerceIn(320.dp, 420.dp)
        val page: @Composable (Organisation, (() -> Unit)?, Modifier) -> Unit = { organisation, back, pageModifier ->
            val key = organisation.accountKey
            OrganisationPage(
                organisation = organisation,
                publications = library.publications,
                followed = key != null && isFollowed(key),
                onToggleFollow = { if (key != null) onToggleFollow(key) },
                onBack = back,
                compact = compact,
                modifier = pageModifier,
                organisationAgenda = organisationAgenda,
            )
        }
        when {
            sideBySide -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    DirectoryList(library, onBack, contentPadding = 16.dp)
                }
                VerticalDivider(color = PpfTheme.palette.border)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) page(selected, null, Modifier.fillMaxSize()) else SelectHint()
                }
            }
            selected != null -> page(selected, library::close, Modifier.fillMaxSize())
            else -> DirectoryList(library, onBack, contentPadding = if (compact) 16.dp else 24.dp)
        }
    }
}

/** The narrowest pane that holds the list and a page side by side. */
private val SIDE_BY_SIDE_MIN = 640.dp

@Composable
private fun SelectHint() {
    val palette = PpfTheme.palette
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lucide.Building2, contentDescription = null, tint = palette.brand, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.organisations_select_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.inkMuted,
                modifier = Modifier.testTag(OrganisationsTags.SELECT_HINT),
            )
        }
    }
}

@Composable
private fun DirectoryList(library: OrganisationsLibrary, onBack: (() -> Unit)?, contentPadding: Dp) {
    val palette = PpfTheme.palette
    val query = library.query
    val results = library.results
    val nearSet = !query.near.isNullOrBlank()
    val selectedAddress = library.selected?.address
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(OrganisationsTags.LIST),
        contentPadding = PaddingValues(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = 720.dp)) {
                if (onBack != null) {
                    PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agir_back), onBack, Modifier.testTag(OrganisationsTags.BACK), tint = palette.ink)
                    Spacer(Modifier.height(8.dp))
                }
                PageHeader(stringResource(Res.string.organisations_title), stringResource(Res.string.organisations_description))
            }
        }
        item(key = "search") {
            // On a card, so that the fields (page-coloured) stand out from the page.
            PpfCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                PpfTextField(
                    value = query.text,
                    onValueChange = library::search,
                    placeholder = stringResource(Res.string.organisations_search_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(OrganisationsTags.SEARCH),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.organisations_near_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
                Spacer(Modifier.height(4.dp))
                PpfTextField(
                    value = query.near.orEmpty(),
                    onValueChange = library::setNear,
                    placeholder = stringResource(Res.string.organisations_near_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(OrganisationsTags.NEAR),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.testTag(OrganisationsTags.NEAR_PRIVATE)) {
                    Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.organisations_near_private), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
            }
        }
        item(key = "types") { TypeChips(library) }
        val territories = library.territories
        if (territories.isNotEmpty()) {
            item(key = "territories") { TerritoryChips(territories, query.territory, library::selectTerritory) }
        }
        item(key = "status") {
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatusLine(library.status)
                    Text(
                        pluralStringResource(Res.plurals.organisations_count, results.size, results.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.inkMuted,
                        modifier = Modifier.testTag(OrganisationsTags.COUNT),
                    )
                }
                if (library.hasRemote) {
                    PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.organisations_refresh), library::refresh, Modifier.testTag(OrganisationsTags.REFRESH), tint = palette.brand)
                }
            }
        }
        if (nearSet) {
            val local = results.filterNot { it.isNational }
            val national = results.filter { it.isNational }
            if (local.isNotEmpty()) {
                item(key = "near-title") { SectionTitle(stringResource(Res.string.organisations_near_section), Modifier.testTag(OrganisationsTags.NEAR_SECTION)) }
                cards(local, selectedAddress, library)
            }
            if (national.isNotEmpty()) {
                item(key = "national-title") { SectionTitle(stringResource(Res.string.organisations_national_section), Modifier.testTag(OrganisationsTags.NATIONAL_SECTION)) }
                cards(national, selectedAddress, library)
            }
        } else {
            cards(results, selectedAddress, library)
        }
        if (results.isEmpty() && library.status != OrganisationsStatus.Loading && library.status != OrganisationsStatus.Idle) {
            item(key = "none") {
                Column(Modifier.widthIn(max = 720.dp)) {
                    Text(
                        stringResource(if (query.isUnconstrained) Res.string.organisations_empty else Res.string.organisations_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted,
                        modifier = Modifier.testTag(OrganisationsTags.NONE),
                    )
                    if (!query.isUnconstrained) {
                        Spacer(Modifier.height(8.dp))
                        PpfSecondaryButton(onClick = library::clearFilters, text = stringResource(Res.string.organisations_clear_filters), modifier = Modifier.testTag(OrganisationsTags.CLEAR))
                    }
                }
            }
        }
        item(key = "about") {
            InfoBox(Lucide.ShieldCheck, Modifier.widthIn(max = 720.dp).padding(top = 8.dp).testTag(OrganisationsTags.ABOUT)) {
                Text(stringResource(Res.string.organisations_about_directory), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun LazyListScope.cards(organisations: List<Organisation>, selected: Any?, library: OrganisationsLibrary) {
    items(organisations, key = { "card-" + it.address.toString() }) { organisation ->
        OrganisationCard(organisation, selected = organisation.address == selected, onOpen = { library.open(organisation) })
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
}

@Composable
private fun TypeChips(library: OrganisationsLibrary) {
    val palette = PpfTheme.palette
    val types = library.query.types
    Column {
        Text(stringResource(Res.string.organisations_types_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(
                label = stringResource(Res.string.organisations_type_all),
                selected = types.isEmpty(),
                onClick = library::clearTypes,
                modifier = Modifier.testTag(OrganisationsTags.TYPE + "all"),
            )
            OrganisationLabels.types.forEach { type ->
                PpfSelectableChip(
                    label = stringResource(OrganisationLabels.filter(type)),
                    selected = type in types,
                    onClick = { library.toggleType(type) },
                    modifier = Modifier.testTag(OrganisationsTags.TYPE + type),
                )
            }
        }
    }
}

@Composable
private fun TerritoryChips(territories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val palette = PpfTheme.palette
    Column {
        Text(stringResource(Res.string.organisations_territories_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PpfSelectableChip(
                label = stringResource(Res.string.organisations_territory_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.testTag(OrganisationsTags.TERRITORY + "all"),
            )
            territories.forEach { territory ->
                val active = selected != null && FrenchText.fold(selected) == FrenchText.fold(territory)
                PpfSelectableChip(
                    label = territory,
                    selected = active,
                    onClick = { onSelect(if (active) null else territory) },
                    leadingIcon = if (active) null else Lucide.MapPin,
                    modifier = Modifier.testTag(OrganisationsTags.territory(territory)),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(status: OrganisationsStatus) {
    val text = when (status) {
        OrganisationsStatus.Idle -> null
        OrganisationsStatus.Loading -> Res.string.organisations_status_loading
        OrganisationsStatus.Unavailable -> Res.string.organisations_status_unavailable
        OrganisationsStatus.Offline -> Res.string.organisations_status_offline
        is OrganisationsStatus.Synced -> Res.string.organisations_status_synced
        OrganisationsStatus.Unreachable -> Res.string.organisations_status_unreachable
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(OrganisationsTags.STATUS)) {
        if (status == OrganisationsStatus.Loading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = PpfTheme.palette.brand, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

/** One organisation of the list: the whole card is one button that opens its page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganisationCard(organisation: Organisation, selected: Boolean, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag(OrganisationsTags.CARD + organisation.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(
                seed = organisation.slug,
                letters = OrganisationLabels.initials(organisation.displayName),
                size = 40.dp,
                rounded = true,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(organisation.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PpfBadge(stringResource(OrganisationLabels.type(organisation.type)), BadgeStyle.NEUTRAL)
                    PpfBadge(stringResource(Res.string.organisation_verified), BadgeStyle.SUCCESS)
                    if (organisation.example) ExampleBadge()
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (organisation.isNational) stringResource(Res.string.organisation_national) else organisation.territory,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(organisation.summary, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
