package org.politisationpopulairefrancaise.ui.agir.solidarity

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.solidarity.FundManager
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTabs
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the solidarity rubric. */
object SolidarityTags {
    const val SCREEN = "solidarity-screen"
    const val LIST = "solidarity-list"
    const val BACK = "solidarity-back"
    const val TAB = "solidarity-tab-" // followed by the tab id
    const val STATUS = "solidarity-status"
    const val REFRESH = "solidarity-refresh"
    const val SELECT_HINT = "solidarity-select-hint"

    // Funds
    const val FUND_SEARCH = "solidarity-fund-search"
    const val NEAR = "solidarity-near"
    const val NEAR_PRIVATE = "solidarity-near-private"
    const val FUND_TYPE = "solidarity-fund-type-" // followed by the type, or "all"
    const val TERRITORY = "solidarity-territory-" // followed by the folded territory with dashes, or "all"
    const val INCLUDE_CLOSED_FUNDS = "solidarity-include-closed-funds"
    const val FUND_COUNT = "solidarity-fund-count"
    const val FUND_CARD = "solidarity-fund-card-" // followed by the slug
    const val FUND_STATUS = "solidarity-fund-status-" // followed by the slug
    const val NEAR_SECTION = "solidarity-near-section"
    const val NATIONAL_SECTION = "solidarity-national-section"
    const val FUNDS_NONE = "solidarity-funds-none"
    const val FUNDS_CLEAR = "solidarity-funds-clear"
    const val FUNDS_ABOUT = "solidarity-funds-about"

    /** AG-60: the entry of the strike simulator, under the funds, and its button. */
    const val STRIKE_SIMULATOR = "solidarity-strike-simulator"
    const val OPEN_STRIKE_SIMULATOR = "solidarity-open-strike-simulator"

    // Missions
    const val MISSIONS_INTRO = "solidarity-missions-intro"
    const val SKILL = "solidarity-skill-" // followed by the skill, or "all"
    const val SKILLS_PRIVATE = "solidarity-skills-private"
    const val MISSION_SEARCH = "solidarity-mission-search"
    const val REMOTE_ONLY = "solidarity-remote-only"
    const val INCLUDE_CLOSED_MISSIONS = "solidarity-include-closed-missions"
    const val MISSION_COUNT = "solidarity-mission-count"
    const val MISSION_CARD = "solidarity-mission-card-" // followed by the slug
    const val MISSIONS_NONE = "solidarity-missions-none"
    const val MISSIONS_CLEAR = "solidarity-missions-clear"
    const val OPEN_CALLS = "solidarity-open-calls"

    // Mutual aid
    const val MUTUAL_AID = "solidarity-mutual-aid"

    /** The tag of a territory chip. */
    fun territory(territory: String): String = TERRITORY + FrenchText.fold(territory).replace(' ', '-')
}

/**
 * The solidarity rubric of "Agir" (M35): the directory of solidarity funds (AG-10), the volunteer
 * missions of "Je veux aider" (AG-12) and, announced honestly, the mutual aid in preparation. One
 * pane on phones and tablets (the list, then the page of a fund or a mission with a way back), two
 * panes side by side on wide windows. [onBack] leads back to the home of "Agir".
 *
 * Whoever manages a fund is looked up in the [directory] of organisations the device holds;
 * [onOpenOrganisation] opens its page there. [onOpenCalls] leads to the calls, where organisations
 * and citizens look for volunteers too. [onOpenStrikeSimulator], when given, adds the entry of the
 * strike simulator (AG-60) at the end of the funds. Nothing the user types or picks here leaves the
 * device.
 */
@Composable
fun SolidarityScreen(
    library: SolidarityLibrary,
    directory: OrganisationsLibrary,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenOrganisation: ((Organisation) -> Unit)? = null,
    onOpenCalls: (() -> Unit)? = null,
    onOpenStrikeSimulator: (() -> Unit)? = null,
) {
    LaunchedEffect(library) { library.ensureStarted() }
    // The managers of the funds are entries of the directory: it loads once, shared with the rest of "Agir".
    LaunchedEffect(directory) { directory.ensureStarted() }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val organisations = directory.organisations
    val fund = library.selectedFund
    val mission = library.selectedMission
    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1).testTag(SolidarityTags.SCREEN)) {
        val sideBySide = windowClass.width == WindowWidthClass.EXPANDED && maxWidth >= SIDE_BY_SIDE_MIN
        val listWidth = (maxWidth * 0.42f).coerceIn(320.dp, 440.dp)
        val page: @Composable (Modifier, (() -> Unit)?) -> Unit = { pageModifier, back ->
            when {
                fund != null -> FundPage(
                    fund = fund,
                    manager = fund.managerIn(organisations),
                    onBack = back,
                    compact = compact,
                    modifier = pageModifier,
                    onOpenOrganisation = onOpenOrganisation,
                )
                mission != null -> MissionPage(mission = mission, onBack = back, compact = compact, modifier = pageModifier)
                else -> SelectHint()
            }
        }
        when {
            sideBySide -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    SolidarityList(library, organisations, onBack, onOpenCalls, contentPadding = 16.dp, onOpenStrikeSimulator = onOpenStrikeSimulator)
                }
                VerticalDivider(color = PpfTheme.palette.border)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    page(Modifier.fillMaxSize(), null)
                }
            }
            fund != null || mission != null -> page(Modifier.fillMaxSize(), library::closeSelection)
            else -> SolidarityList(library, organisations, onBack, onOpenCalls, contentPadding = if (compact) 16.dp else 24.dp, onOpenStrikeSimulator = onOpenStrikeSimulator)
        }
    }
}

/** The narrowest pane that holds the list and a page side by side. */
private val SIDE_BY_SIDE_MIN = 640.dp

/** The widest a column of text and cards grows, for readable lines. */
private val READABLE = 720.dp

@Composable
private fun SelectHint() {
    val palette = PpfTheme.palette
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lucide.HandHelping, contentDescription = null, tint = palette.brand, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.solidarite_select_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.inkMuted,
                modifier = Modifier.testTag(SolidarityTags.SELECT_HINT),
            )
        }
    }
}

private fun tabLabel(tab: SolidarityTab): StringResource = when (tab) {
    SolidarityTab.FUNDS -> Res.string.solidarite_tab_funds
    SolidarityTab.MISSIONS -> Res.string.solidarite_tab_missions
    SolidarityTab.MUTUAL_AID -> Res.string.solidarite_tab_mutual_aid
}

@Composable
private fun SolidarityList(
    library: SolidarityLibrary,
    organisations: List<Organisation>,
    onBack: (() -> Unit)?,
    onOpenCalls: (() -> Unit)?,
    contentPadding: Dp,
    onOpenStrikeSimulator: (() -> Unit)? = null,
) {
    val palette = PpfTheme.palette
    val tab = library.tab
    val tabs = SolidarityTab.entries
    val tabLabels = tabs.map { stringResource(tabLabel(it)) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SolidarityTags.LIST),
        contentPadding = PaddingValues(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = READABLE)) {
                if (onBack != null) {
                    PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.agir_back), onBack, Modifier.testTag(SolidarityTags.BACK), tint = palette.ink)
                    Spacer(Modifier.height(8.dp))
                }
                PageHeader(stringResource(Res.string.solidarite_title), stringResource(Res.string.solidarite_description))
            }
        }
        item(key = "tabs") {
            // Scrolls sideways when large text makes the three tabs wider than the screen.
            Box(Modifier.widthIn(max = READABLE).fillMaxWidth().horizontalScroll(rememberScrollState())) {
                PpfTabs(
                    labels = tabLabels,
                    selected = tabs.indexOf(tab),
                    onSelect = { index -> library.selectTab(tabs[index]) },
                    tagFor = { index -> SolidarityTags.TAB + tabs[index].id },
                )
            }
        }
        when (tab) {
            SolidarityTab.FUNDS -> fundItems(library, organisations, onOpenStrikeSimulator)
            SolidarityTab.MISSIONS -> missionItems(library, onOpenCalls)
            SolidarityTab.MUTUAL_AID -> mutualAidItems()
        }
    }
}

// The funds (AG-10)

private fun LazyListScope.fundItems(library: SolidarityLibrary, organisations: List<Organisation>, onOpenStrikeSimulator: (() -> Unit)? = null) {
    val query = library.fundQuery
    val results = library.fundResults
    val territories = library.fundTerritories
    val status = library.status
    item(key = "fund-search") {
        val palette = PpfTheme.palette
        // On a card, so that the fields (page-coloured) stand out from the page.
        PpfCard(Modifier.widthIn(max = READABLE).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
            PpfTextField(
                value = query.text,
                onValueChange = library::searchFunds,
                placeholder = stringResource(Res.string.solidarite_funds_search_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SolidarityTags.FUND_SEARCH),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.solidarite_near_label), style = MaterialTheme.typography.labelLarge, color = palette.inkMuted)
            Spacer(Modifier.height(4.dp))
            PpfTextField(
                value = query.near.orEmpty(),
                onValueChange = library::setNear,
                placeholder = stringResource(Res.string.solidarite_near_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SolidarityTags.NEAR),
            )
            Spacer(Modifier.height(4.dp))
            PrivateLine(stringResource(Res.string.solidarite_near_private), Modifier.testTag(SolidarityTags.NEAR_PRIVATE))
        }
    }
    item(key = "fund-types") {
        ChipGroup(stringResource(Res.string.solidarite_fund_types_label)) {
            PpfSelectableChip(
                label = stringResource(Res.string.solidarite_fund_type_all),
                selected = query.types.isEmpty(),
                onClick = library::clearFundTypes,
                modifier = Modifier.testTag(SolidarityTags.FUND_TYPE + "all"),
            )
            SolidarityLabels.fundTypes.forEach { type ->
                PpfSelectableChip(
                    label = stringResource(SolidarityLabels.fundFilter(type)),
                    selected = type in query.types,
                    onClick = { library.toggleFundType(type) },
                    modifier = Modifier.testTag(SolidarityTags.FUND_TYPE + type),
                )
            }
            if (library.hasClosedFunds) {
                PpfSelectableChip(
                    label = stringResource(Res.string.solidarite_include_closed_funds),
                    selected = query.includeClosed,
                    onClick = library::toggleIncludeClosedFunds,
                    modifier = Modifier.testTag(SolidarityTags.INCLUDE_CLOSED_FUNDS),
                )
            }
        }
    }
    if (territories.isNotEmpty()) {
        item(key = "fund-territories") {
            ChipGroup(stringResource(Res.string.solidarite_territories_label)) {
                PpfSelectableChip(
                    label = stringResource(Res.string.solidarite_territory_all),
                    selected = query.territory.isNullOrBlank(),
                    onClick = { library.selectFundTerritory(null) },
                    modifier = Modifier.testTag(SolidarityTags.TERRITORY + "all"),
                )
                territories.forEach { territory ->
                    val active = !query.territory.isNullOrBlank() && FrenchText.fold(query.territory.orEmpty()) == FrenchText.fold(territory)
                    PpfSelectableChip(
                        label = territory,
                        selected = active,
                        onClick = { library.selectFundTerritory(if (active) null else territory) },
                        leadingIcon = if (active) null else Lucide.MapPin,
                        modifier = Modifier.testTag(SolidarityTags.territory(territory)),
                    )
                }
            }
        }
    }
    item(key = "fund-status") {
        StatusRow(library, pluralStringResource(Res.plurals.solidarite_funds_count, results.size, results.size), SolidarityTags.FUND_COUNT)
    }
    val selected = library.selectedFund?.address
    if (!query.near.isNullOrBlank()) {
        val local = results.filterNot { it.isNational }
        val national = results.filter { it.isNational }
        if (local.isNotEmpty()) {
            item(key = "fund-near-title") { SectionTitle(stringResource(Res.string.solidarite_near_section), Modifier.testTag(SolidarityTags.NEAR_SECTION)) }
            fundCards(local, selected, organisations, library)
        }
        if (national.isNotEmpty()) {
            item(key = "fund-national-title") { SectionTitle(stringResource(Res.string.solidarite_national_section), Modifier.testTag(SolidarityTags.NATIONAL_SECTION)) }
            fundCards(national, selected, organisations, library)
        }
    } else {
        fundCards(results, selected, organisations, library)
    }
    if (results.isEmpty() && status != SolidarityStatus.Loading && status != SolidarityStatus.Idle) {
        item(key = "funds-none") {
            EmptyState(
                text = stringResource(if (query.isUnconstrained) Res.string.solidarite_funds_empty else Res.string.solidarite_funds_none),
                textTag = SolidarityTags.FUNDS_NONE,
                onClear = if (query.isUnconstrained) null else library::clearFundFilters,
                clearTag = SolidarityTags.FUNDS_CLEAR,
            )
        }
    }
    item(key = "funds-about") {
        InfoBox(Lucide.ShieldCheck, Modifier.widthIn(max = READABLE).padding(top = 8.dp).testTag(SolidarityTags.FUNDS_ABOUT)) {
            Text(stringResource(Res.string.solidarite_funds_about), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (onOpenStrikeSimulator != null) {
        // AG-60: the strike simulator, at the end of the funds so that nothing above it moves.
        item(key = "strike-simulator") {
            InfoBox(Lucide.ScrollText, Modifier.widthIn(max = READABLE).testTag(SolidarityTags.STRIKE_SIMULATOR)) {
                Text(
                    stringResource(Res.string.greve_entry_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.greve_entry_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(
                    onClick = onOpenStrikeSimulator,
                    text = stringResource(Res.string.greve_entry_open),
                    modifier = Modifier.testTag(SolidarityTags.OPEN_STRIKE_SIMULATOR),
                    small = true,
                )
            }
        }
    }
}

private fun LazyListScope.fundCards(funds: List<SolidarityFund>, selected: Any?, organisations: List<Organisation>, library: SolidarityLibrary) {
    items(funds, key = { "fund-" + it.address.toString() }) { fund ->
        FundCard(fund, fund.managerIn(organisations), selected = fund.address == selected, onOpen = { library.openFund(fund) })
    }
}

/** One fund of the list: the whole card is one button that opens its page; its state is written, never shown by colour alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FundCard(fund: SolidarityFund, manager: FundManager, selected: Boolean, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.widthIn(max = READABLE).fillMaxWidth().testTag(SolidarityTags.FUND_CARD + fund.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(Lucide.HandHelping)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fund.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PpfBadge(stringResource(SolidarityLabels.fundType(fund.type)), BadgeStyle.NEUTRAL)
                    FundStatusBadge(fund, Modifier.testTag(SolidarityTags.FUND_STATUS + fund.slug))
                    if (fund.example) ExampleBadge()
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (fund.isNational) stringResource(Res.string.solidarite_national) else fund.territory,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.solidarite_fund_for, fund.beneficiary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ManagerLine(manager, small = true)
                Spacer(Modifier.height(4.dp))
                Text(fund.summary, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// The missions (AG-12)

private fun LazyListScope.missionItems(library: SolidarityLibrary, onOpenCalls: (() -> Unit)?) {
    val query = library.missionQuery
    val results = library.missionResults
    val status = library.status
    item(key = "missions-intro") {
        Text(
            stringResource(Res.string.solidarite_missions_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = PpfTheme.palette.inkMuted,
            modifier = Modifier.widthIn(max = READABLE).testTag(SolidarityTags.MISSIONS_INTRO),
        )
    }
    item(key = "missions-skills") {
        Column(Modifier.widthIn(max = READABLE)) {
            ChipGroup(stringResource(Res.string.solidarite_skills_label)) {
                PpfSelectableChip(
                    label = stringResource(Res.string.solidarite_skill_all),
                    selected = query.skills.isEmpty(),
                    onClick = library::clearSkills,
                    modifier = Modifier.testTag(SolidarityTags.SKILL + "all"),
                )
                SolidarityLabels.skills.forEach { skill ->
                    PpfSelectableChip(
                        label = stringResource(SolidarityLabels.skill(skill)),
                        selected = skill in query.skills,
                        onClick = { library.toggleSkill(skill) },
                        modifier = Modifier.testTag(SolidarityTags.SKILL + skill),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PrivateLine(stringResource(Res.string.solidarite_skills_private), Modifier.testTag(SolidarityTags.SKILLS_PRIVATE))
        }
    }
    item(key = "missions-search") {
        PpfCard(Modifier.widthIn(max = READABLE).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
            PpfTextField(
                value = query.text,
                onValueChange = library::searchMissions,
                placeholder = stringResource(Res.string.solidarite_missions_search_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SolidarityTags.MISSION_SEARCH),
            )
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                PpfSelectableChip(
                    label = stringResource(Res.string.solidarite_remote_only),
                    selected = query.remoteOnly,
                    onClick = library::toggleRemoteOnly,
                    leadingIcon = if (query.remoteOnly) null else Lucide.Globe,
                    modifier = Modifier.testTag(SolidarityTags.REMOTE_ONLY),
                )
                if (library.hasClosedMissions) {
                    PpfSelectableChip(
                        label = stringResource(Res.string.solidarite_include_closed_missions),
                        selected = query.includeClosed,
                        onClick = library::toggleIncludeClosedMissions,
                        modifier = Modifier.testTag(SolidarityTags.INCLUDE_CLOSED_MISSIONS),
                    )
                }
            }
        }
    }
    item(key = "missions-status") {
        StatusRow(library, pluralStringResource(Res.plurals.solidarite_missions_count, results.size, results.size), SolidarityTags.MISSION_COUNT)
    }
    val selected = library.selectedMission?.address
    items(results, key = { "mission-" + it.address.toString() }) { mission ->
        MissionCard(mission, selected = mission.address == selected, onOpen = { library.openMission(mission) })
    }
    if (results.isEmpty() && status != SolidarityStatus.Loading && status != SolidarityStatus.Idle) {
        item(key = "missions-none") {
            EmptyState(
                text = stringResource(if (query.isUnconstrained) Res.string.solidarite_missions_empty else Res.string.solidarite_missions_none),
                textTag = SolidarityTags.MISSIONS_NONE,
                onClear = if (query.isUnconstrained) null else library::clearMissionFilters,
                clearTag = SolidarityTags.MISSIONS_CLEAR,
            )
        }
    }
    if (onOpenCalls != null) {
        item(key = "missions-calls") {
            InfoBox(Lucide.Megaphone, Modifier.widthIn(max = READABLE).padding(top = 8.dp)) {
                Text(stringResource(Res.string.solidarite_missions_calls), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                PpfSecondaryButton(
                    onClick = onOpenCalls,
                    text = stringResource(Res.string.solidarite_missions_open_calls),
                    modifier = Modifier.testTag(SolidarityTags.OPEN_CALLS),
                    small = true,
                )
            }
        }
    }
}

/** One mission of the list: one button that opens its page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionCard(mission: VolunteerMission, selected: Boolean, onOpen: () -> Unit) {
    val palette = PpfTheme.palette
    val skills = mission.skills.map { stringResource(SolidarityLabels.skill(it)) }.joinToString(", ")
    PpfCard(onClick = onOpen, selected = selected, modifier = Modifier.widthIn(max = READABLE).fillMaxWidth().testTag(SolidarityTags.MISSION_CARD + mission.slug)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(Lucide.Heart)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mission.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MissionStatusBadge(mission)
                    if (mission.remote) PpfBadge(stringResource(Res.string.solidarite_mission_remote), BadgeStyle.BRAND)
                    if (mission.example) ExampleBadge()
                }
                Spacer(Modifier.height(6.dp))
                Text(skills, style = MaterialTheme.typography.labelLarge, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (mission.location == null && mission.remote) Lucide.Globe else Lucide.MapPin, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mission.location ?: if (mission.isNational) stringResource(Res.string.solidarite_national) else mission.territory,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSubtle,
                    )
                }
                mission.deadlineDate?.let { date ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Calendar, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.solidarite_mission_deadline, date.toFrench()), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(mission.summary, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Mutual aid (AG-10), announced honestly

private fun LazyListScope.mutualAidItems() {
    item(key = "mutual-aid") {
        InfoBox(Lucide.Users, Modifier.widthIn(max = READABLE).testTag(SolidarityTags.MUTUAL_AID)) {
            Text(
                stringResource(Res.string.solidarite_mutual_aid_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.solidarite_mutual_aid_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.solidarite_mutual_aid_now), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Shared pieces of the lists

/** A label, then chips that wrap on several lines (no sideways gesture needed). */
@Composable
private fun ChipGroup(label: String, chips: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = READABLE)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = PpfTheme.palette.inkMuted)
        Spacer(Modifier.height(4.dp))
        ChipFlow(chips)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(chips: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips()
    }
}

/** A line that says what stays on the device, with a lock. */
@Composable
internal fun PrivateLine(text: String, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
}

/** The icon of a card, decorative: the card's text says everything. */
@Composable
private fun IconTile(icon: ImageVector) {
    val palette = PpfTheme.palette
    Box(
        Modifier.size(40.dp).background(palette.brandTint, MaterialTheme.shapes.small).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatusRow(library: SolidarityLibrary, count: String, countTag: String) {
    val palette = PpfTheme.palette
    Row(Modifier.widthIn(max = READABLE).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            StatusLine(library.status)
            Text(count, style = MaterialTheme.typography.labelLarge, color = palette.inkMuted, modifier = Modifier.testTag(countTag))
        }
        if (library.hasRemote) {
            PpfIconButton(Lucide.RefreshCw, stringResource(Res.string.solidarite_refresh), library::refresh, Modifier.testTag(SolidarityTags.REFRESH), tint = palette.brand)
        }
    }
}

@Composable
private fun StatusLine(status: SolidarityStatus) {
    val text = when (status) {
        SolidarityStatus.Idle -> null
        SolidarityStatus.Loading -> Res.string.solidarite_status_loading
        SolidarityStatus.Unavailable -> Res.string.solidarite_status_unavailable
        SolidarityStatus.Offline -> Res.string.solidarite_status_offline
        is SolidarityStatus.Synced -> Res.string.solidarite_status_synced
        SolidarityStatus.Unreachable -> Res.string.solidarite_status_unreachable
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(SolidarityTags.STATUS)) {
        if (status == SolidarityStatus.Loading) {
            CircularProgressIndicator(Modifier.size(14.dp), color = PpfTheme.palette.brand, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkSubtle)
    }
}

@Composable
private fun EmptyState(text: String, textTag: String, onClear: (() -> Unit)?, clearTag: String) {
    Column(Modifier.widthIn(max = READABLE)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PpfTheme.palette.inkMuted, modifier = Modifier.testTag(textTag))
        if (onClear != null) {
            Spacer(Modifier.height(8.dp))
            PpfSecondaryButton(onClick = onClear, text = stringResource(Res.string.solidarite_clear_filters), modifier = Modifier.testTag(clearTag))
        }
    }
}
