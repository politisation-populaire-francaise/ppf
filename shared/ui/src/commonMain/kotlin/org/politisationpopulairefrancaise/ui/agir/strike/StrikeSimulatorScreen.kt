package org.politisationpopulairefrancaise.ui.agir.strike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.WindowWidthClass
import org.politisationpopulairefrancaise.core.agir.solidarity.FundQuery
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.strike.STRIKE_RULES_VERIFIED_ON
import org.politisationpopulairefrancaise.core.agir.strike.StrikeDay
import org.politisationpopulairefrancaise.core.agir.strike.StrikeEstimate
import org.politisationpopulairefrancaise.core.agir.strike.StrikeNote
import org.politisationpopulairefrancaise.core.agir.strike.StrikeNumbers
import org.politisationpopulairefrancaise.core.agir.strike.StrikeObligation
import org.politisationpopulairefrancaise.core.agir.strike.StrikeRule
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSector
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSimulator
import org.politisationpopulairefrancaise.core.agir.strike.StrikeSource
import org.politisationpopulairefrancaise.core.agir.strike.StrikeStatus
import org.politisationpopulairefrancaise.ui.agir.solidarity.PrivateLine
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityLibrary
import org.politisationpopulairefrancaise.ui.agir.solidarity.SolidarityStatus
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.InfoBox
import org.politisationpopulairefrancaise.ui.components.LeaveDialogTags
import org.politisationpopulairefrancaise.ui.components.LeavePpfDialog
import org.politisationpopulairefrancaise.ui.components.PageHeader
import org.politisationpopulairefrancaise.ui.components.PpfCard
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.leavingHost
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.greve_ai_line
import org.politisationpopulairefrancaise.ui.resources.greve_back
import org.politisationpopulairefrancaise.ui.resources.greve_before_title
import org.politisationpopulairefrancaise.ui.resources.greve_between_help
import org.politisationpopulairefrancaise.ui.resources.greve_between_label
import org.politisationpopulairefrancaise.ui.resources.greve_between_less
import org.politisationpopulairefrancaise.ui.resources.greve_between_more
import org.politisationpopulairefrancaise.ui.resources.greve_between_value
import org.politisationpopulairefrancaise.ui.resources.greve_day_add
import org.politisationpopulairefrancaise.ui.resources.greve_day_hours_field
import org.politisationpopulairefrancaise.ui.resources.greve_day_hours_label
import org.politisationpopulairefrancaise.ui.resources.greve_day_label
import org.politisationpopulairefrancaise.ui.resources.greve_day_remove
import org.politisationpopulairefrancaise.ui.resources.greve_days_help_local
import org.politisationpopulairefrancaise.ui.resources.greve_days_help_private
import org.politisationpopulairefrancaise.ui.resources.greve_days_help_state
import org.politisationpopulairefrancaise.ui.resources.greve_days_max
import org.politisationpopulairefrancaise.ui.resources.greve_days_title
import org.politisationpopulairefrancaise.ui.resources.greve_description
import org.politisationpopulairefrancaise.ui.resources.greve_detail_between
import org.politisationpopulairefrancaise.ui.resources.greve_detail_day
import org.politisationpopulairefrancaise.ui.resources.greve_detail_full
import org.politisationpopulairefrancaise.ui.resources.greve_detail_half
import org.politisationpopulairefrancaise.ui.resources.greve_detail_hourly
import org.politisationpopulairefrancaise.ui.resources.greve_detail_hourly_capped
import org.politisationpopulairefrancaise.ui.resources.greve_detail_not_worked
import org.politisationpopulairefrancaise.ui.resources.greve_detail_private_total
import org.politisationpopulairefrancaise.ui.resources.greve_detail_sixtieth
import org.politisationpopulairefrancaise.ui.resources.greve_detail_thirtieth
import org.politisationpopulairefrancaise.ui.resources.greve_detail_thirtieths
import org.politisationpopulairefrancaise.ui.resources.greve_detail_title
import org.politisationpopulairefrancaise.ui.resources.greve_disclaimer
import org.politisationpopulairefrancaise.ui.resources.greve_funds_all
import org.politisationpopulairefrancaise.ui.resources.greve_funds_loading
import org.politisationpopulairefrancaise.ui.resources.greve_funds_near_hint
import org.politisationpopulairefrancaise.ui.resources.greve_funds_near_label
import org.politisationpopulairefrancaise.ui.resources.greve_funds_near_private
import org.politisationpopulairefrancaise.ui.resources.greve_funds_none
import org.politisationpopulairefrancaise.ui.resources.greve_funds_text
import org.politisationpopulairefrancaise.ui.resources.greve_funds_title
import org.politisationpopulairefrancaise.ui.resources.greve_hours_error
import org.politisationpopulairefrancaise.ui.resources.greve_kind_full
import org.politisationpopulairefrancaise.ui.resources.greve_kind_half
import org.politisationpopulairefrancaise.ui.resources.greve_kind_hours
import org.politisationpopulairefrancaise.ui.resources.greve_leave_open
import org.politisationpopulairefrancaise.ui.resources.greve_leave_stay
import org.politisationpopulairefrancaise.ui.resources.greve_leave_text
import org.politisationpopulairefrancaise.ui.resources.greve_leave_title
import org.politisationpopulairefrancaise.ui.resources.greve_notes_title
import org.politisationpopulairefrancaise.ui.resources.greve_pay_error
import org.politisationpopulairefrancaise.ui.resources.greve_pay_help_private
import org.politisationpopulairefrancaise.ui.resources.greve_pay_help_public
import org.politisationpopulairefrancaise.ui.resources.greve_pay_hint
import org.politisationpopulairefrancaise.ui.resources.greve_pay_label
import org.politisationpopulairefrancaise.ui.resources.greve_pay_title
import org.politisationpopulairefrancaise.ui.resources.greve_private
import org.politisationpopulairefrancaise.ui.resources.greve_result_amount
import org.politisationpopulairefrancaise.ui.resources.greve_result_capped
import org.politisationpopulairefrancaise.ui.resources.greve_result_gross_private
import org.politisationpopulairefrancaise.ui.resources.greve_result_gross_public
import org.politisationpopulairefrancaise.ui.resources.greve_result_incomplete
import org.politisationpopulairefrancaise.ui.resources.greve_result_not_simulated
import org.politisationpopulairefrancaise.ui.resources.greve_result_share
import org.politisationpopulairefrancaise.ui.resources.greve_result_title
import org.politisationpopulairefrancaise.ui.resources.greve_rule_title
import org.politisationpopulairefrancaise.ui.resources.greve_sector_text
import org.politisationpopulairefrancaise.ui.resources.greve_sector_title
import org.politisationpopulairefrancaise.ui.resources.greve_sources_inline
import org.politisationpopulairefrancaise.ui.resources.greve_sources_text
import org.politisationpopulairefrancaise.ui.resources.greve_sources_title
import org.politisationpopulairefrancaise.ui.resources.greve_status_title
import org.politisationpopulairefrancaise.ui.resources.greve_title
import org.politisationpopulairefrancaise.ui.resources.greve_weekly_error
import org.politisationpopulairefrancaise.ui.resources.greve_weekly_hint
import org.politisationpopulairefrancaise.ui.resources.greve_weekly_label
import org.politisationpopulairefrancaise.ui.resources.greve_day_hours_hint
import org.politisationpopulairefrancaise.ui.resources.solidarite_national
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the strike simulator. */
object StrikeTags {
    const val SCREEN = "strike-screen"
    const val INPUTS = "strike-inputs"
    const val RESULTS = "strike-results"
    const val BACK = "strike-back"
    const val STATUS = "strike-status-" // followed by the status id
    const val SECTOR = "strike-sector-" // followed by the sector id
    const val PAY = "strike-pay"
    const val PAY_ERROR = "strike-pay-error"
    const val WEEKLY = "strike-weekly"
    const val DAY_HOURS = "strike-day-hours"
    const val DAY = "strike-day-" // followed by the index
    const val KIND = "strike-kind-" // followed by the index, a dash and the kind id
    const val HOURS = "strike-hours-" // followed by the index
    const val REMOVE = "strike-remove-" // followed by the index
    const val ADD_DAY = "strike-add-day"
    const val BETWEEN = "strike-between"
    const val BETWEEN_VALUE = "strike-between-value"
    const val BETWEEN_LESS = "strike-between-less"
    const val BETWEEN_MORE = "strike-between-more"
    const val DISCLAIMER = "strike-disclaimer"
    const val INCOMPLETE = "strike-incomplete"
    const val NOT_SIMULATED = "strike-not-simulated"
    const val RESULT = "strike-result"
    const val AMOUNT = "strike-amount"
    const val SHARE = "strike-share"
    const val RULE = "strike-rule"
    const val AI_LINE = "strike-ai-line"
    const val OBLIGATION = "strike-obligation-" // followed by the obligation name, in lowercase
    const val NOTE = "strike-note-" // followed by the note name, in lowercase
    const val FUNDS = "strike-funds"
    const val NEAR = "strike-near"
    const val FUND = "strike-fund-" // followed by the slug
    const val FUNDS_NONE = "strike-funds-none"
    const val FUNDS_ALL = "strike-funds-all"
    const val SOURCE = "strike-source-" // followed by the source name, in lowercase
    const val LEAVE_DIALOG = "strike-leave-dialog"
    const val LEAVE_STAY = "strike-leave-stay"
    const val LEAVE_OPEN = "strike-leave-open"

    fun kind(index: Int, kind: StrikeDayKind): String = KIND + index + "-" + kind.id

    fun obligation(obligation: StrikeObligation): String = OBLIGATION + obligation.name.lowercase()

    /** The text of an obligation (the card itself does not merge its texts). */
    fun obligationText(obligation: StrikeObligation): String = obligation(obligation) + "-text"

    fun note(note: StrikeNote): String = NOTE + note.name.lowercase()

    fun source(source: StrikeSource): String = SOURCE + source.name.lowercase()
}

/** The narrowest pane that holds the questions and the estimate side by side. */
private val SIDE_BY_SIDE_MIN = 840.dp

/** The widest a column of text and cards grows, for readable lines. */
private val READABLE = 720.dp

/** The strike funds shown under the estimate; the rest is one tap away in the solidarity rubric. */
private const val MAX_FUNDS = 5

/**
 * The strike simulator of "Agir" (M35, AG-60): the status, the sector, the pay and the days of
 * strike, then the estimate of the retention with the rule and its official sources, the notice and
 * the declaration the sector asks for, what to know besides, and the strike funds of the solidarity
 * rubric near the person ([solidarity], AG-10). One column on phones and tablets, the questions and
 * the estimate side by side on wide windows. Everything typed stays in [state], in memory only.
 *
 * [onOpenFund] opens the page of a fund in the solidarity rubric, [onOpenFunds] its list of strike
 * funds; [onBack] leads back to the solidarity rubric. The sources open after "Vous quittez PPF".
 */
@Composable
fun StrikeSimulatorScreen(
    state: StrikeSimulatorState,
    solidarity: SolidarityLibrary,
    windowClass: WindowClass,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenFund: ((SolidarityFund) -> Unit)? = null,
    onOpenFunds: (() -> Unit)? = null,
) {
    // The strike funds come from the directory of the solidarity rubric: it loads once, shared with it.
    LaunchedEffect(solidarity) { solidarity.ensureStarted() }
    var leaving by remember { mutableStateOf<String?>(null) }
    val compact = windowClass.width == WindowWidthClass.COMPACT
    val padding = if (compact) 16.dp else 24.dp
    BoxWithConstraints(modifier.fillMaxSize().background(PpfTheme.palette.surface1).testTag(StrikeTags.SCREEN)) {
        val sideBySide = windowClass.width == WindowWidthClass.EXPANDED && maxWidth >= SIDE_BY_SIDE_MIN
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(padding).testTag(StrikeTags.INPUTS),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Header(onBack)
                    Questions(state)
                }
                VerticalDivider(color = PpfTheme.palette.border)
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(padding).testTag(StrikeTags.RESULTS),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Answers(state, solidarity, onOpenFund, onOpenFunds, onOpenSource = { leaving = it })
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
                Column(Modifier.widthIn(max = READABLE).testTag(StrikeTags.INPUTS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header(onBack)
                    Questions(state)
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = READABLE).testTag(StrikeTags.RESULTS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Answers(state, solidarity, onOpenFund, onOpenFunds, onOpenSource = { leaving = it })
                }
            }
        }
    }
    leaving?.let { url ->
        LeavePpfDialog(
            url = url,
            title = stringResource(Res.string.greve_leave_title),
            text = stringResource(Res.string.greve_leave_text, leavingHost(url)),
            stayLabel = stringResource(Res.string.greve_leave_stay),
            openLabel = stringResource(Res.string.greve_leave_open),
            onStay = { leaving = null },
            onDone = { leaving = null },
            tags = LeaveDialogTags(StrikeTags.LEAVE_DIALOG, StrikeTags.LEAVE_STAY, StrikeTags.LEAVE_OPEN),
        )
    }
}

@Composable
private fun Header(onBack: (() -> Unit)?) {
    Column {
        if (onBack != null) {
            PpfIconButton(Lucide.ArrowLeft, stringResource(Res.string.greve_back), onBack, Modifier.testTag(StrikeTags.BACK), tint = PpfTheme.palette.ink)
            Spacer(Modifier.height(8.dp))
        }
        PageHeader(stringResource(Res.string.greve_title), stringResource(Res.string.greve_description))
        Spacer(Modifier.height(8.dp))
        PrivateLine(stringResource(Res.string.greve_private))
    }
}

// The questions

@Composable
private fun Questions(state: StrikeSimulatorState) {
    Section {
        SectionTitle(stringResource(Res.string.greve_status_title))
        Column(Modifier.selectableGroup()) {
            StrikeStatus.entries.forEach { status ->
                PpfRadioRow(
                    label = stringResource(StrikeLabels.status(status)),
                    selected = state.status == status,
                    onClick = { state.selectStatus(status) },
                    supporting = stringResource(StrikeLabels.statusText(status)),
                    modifier = Modifier.testTag(StrikeTags.STATUS + status.id),
                )
            }
        }
    }
    val sectors = StrikeSector.forStatus(state.status)
    if (sectors.size > 1) {
        Section {
            SectionTitle(stringResource(Res.string.greve_sector_title))
            Help(stringResource(Res.string.greve_sector_text))
            Column(Modifier.selectableGroup()) {
                sectors.forEach { sector ->
                    PpfRadioRow(
                        label = stringResource(StrikeLabels.sector(sector)),
                        selected = state.sector == sector,
                        onClick = { state.selectSector(sector) },
                        supporting = stringResource(StrikeLabels.sectorText(sector)),
                        modifier = Modifier.testTag(StrikeTags.SECTOR + sector.id),
                    )
                }
            }
        }
    }
    Section {
        SectionTitle(stringResource(Res.string.greve_pay_title))
        NumberField(
            label = stringResource(Res.string.greve_pay_label),
            value = state.payText,
            onValueChange = { typed -> state.payText = typed.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }.take(StrikeSimulatorState.MAX_FIELD) },
            tag = StrikeTags.PAY,
        )
        Help(
            stringResource(if (state.status == StrikeStatus.PRIVATE) Res.string.greve_pay_help_private else Res.string.greve_pay_help_public) +
                " " + stringResource(Res.string.greve_pay_hint) + ".",
        )
        if (state.payError) ErrorLine(stringResource(Res.string.greve_pay_error), StrikeTags.PAY_ERROR)
        if (state.status == StrikeStatus.PRIVATE) {
            Spacer(Modifier.height(4.dp))
            NumberField(
                label = stringResource(Res.string.greve_weekly_label),
                value = state.weeklyHoursText,
                onValueChange = { state.weeklyHoursText = it.take(StrikeSimulatorState.MAX_FIELD) },
                tag = StrikeTags.WEEKLY,
            )
            Help(stringResource(Res.string.greve_weekly_hint) + ".")
            if (state.weeklyError) ErrorLine(stringResource(Res.string.greve_weekly_error), StrikeTags.WEEKLY + "-error")
            Spacer(Modifier.height(4.dp))
            NumberField(
                label = stringResource(Res.string.greve_day_hours_label),
                value = state.dayHoursText,
                onValueChange = { state.dayHoursText = it.take(StrikeSimulatorState.MAX_FIELD) },
                tag = StrikeTags.DAY_HOURS,
            )
            Help(stringResource(Res.string.greve_day_hours_hint) + ".")
            if (state.dayHoursError) ErrorLine(stringResource(Res.string.greve_hours_error), StrikeTags.DAY_HOURS + "-error")
        }
    }
    Section {
        SectionTitle(stringResource(Res.string.greve_days_title))
        Help(
            stringResource(
                when (state.status) {
                    StrikeStatus.PRIVATE -> Res.string.greve_days_help_private
                    StrikeStatus.STATE -> Res.string.greve_days_help_state
                    StrikeStatus.TERRITORIAL, StrikeStatus.HOSPITAL -> Res.string.greve_days_help_local
                },
            ),
        )
        state.days.forEachIndexed { index, entry -> DayRow(state, index, entry) }
        if (state.canAddDay) {
            PpfSecondaryButton(
                onClick = state::addDay,
                text = stringResource(Res.string.greve_day_add),
                icon = Lucide.Plus,
                modifier = Modifier.testTag(StrikeTags.ADD_DAY),
            )
        } else {
            Help(stringResource(Res.string.greve_days_max))
        }
        if (state.asksDaysWithoutService) DaysWithoutService(state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayRow(state: StrikeSimulatorState, index: Int, entry: StrikeDayEntry) {
    val number = index + 1
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(StrikeTags.DAY + index)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.greve_day_label, number),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.days.size > 1) {
                PpfIconButton(
                    Lucide.X,
                    stringResource(Res.string.greve_day_remove, number),
                    { state.removeDay(index) },
                    Modifier.minimumInteractiveComponentSize().testTag(StrikeTags.REMOVE + index),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StrikeDayKind.entries.forEach { kind ->
                PpfSelectableChip(
                    label = stringResource(kindLabel(kind)),
                    selected = entry.kind == kind,
                    onClick = { state.setKind(index, kind) },
                    modifier = Modifier.testTag(StrikeTags.kind(index, kind)),
                )
            }
        }
        if (entry.kind == StrikeDayKind.HOURS) {
            Spacer(Modifier.height(6.dp))
            NumberField(
                label = stringResource(Res.string.greve_day_hours_field, number),
                value = entry.hoursText,
                onValueChange = { state.setHours(index, it) },
                tag = StrikeTags.HOURS + index,
                showLabel = false,
            )
            if (state.hoursError(entry)) ErrorLine(stringResource(Res.string.greve_hours_error), StrikeTags.HOURS + index + "-error")
        }
    }
}

private fun kindLabel(kind: StrikeDayKind): StringResource = when (kind) {
    StrikeDayKind.FULL -> Res.string.greve_kind_full
    StrikeDayKind.HALF -> Res.string.greve_kind_half
    StrikeDayKind.HOURS -> Res.string.greve_kind_hours
}

@Composable
private fun DaysWithoutService(state: StrikeSimulatorState) {
    val count = state.daysWithoutService
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag(StrikeTags.BETWEEN)) {
        Text(stringResource(Res.string.greve_between_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            PpfIconButton(
                Lucide.Minus,
                stringResource(Res.string.greve_between_less),
                state::decreaseDaysWithoutService,
                Modifier.minimumInteractiveComponentSize().testTag(StrikeTags.BETWEEN_LESS),
                enabled = count > 0,
                tint = PpfTheme.palette.brand,
            )
            Text(
                pluralStringResource(Res.plurals.greve_between_value, count, count),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp).testTag(StrikeTags.BETWEEN_VALUE).semantics { liveRegion = LiveRegionMode.Polite },
            )
            PpfIconButton(
                Lucide.Plus,
                stringResource(Res.string.greve_between_more),
                state::increaseDaysWithoutService,
                Modifier.minimumInteractiveComponentSize().testTag(StrikeTags.BETWEEN_MORE),
                enabled = count < StrikeSimulator.MAX_DAYS_WITHOUT_SERVICE,
                tint = PpfTheme.palette.brand,
            )
        }
        Help(stringResource(Res.string.greve_between_help))
    }
}

// The answers

@Composable
private fun Answers(
    state: StrikeSimulatorState,
    solidarity: SolidarityLibrary,
    onOpenFund: ((SolidarityFund) -> Unit)?,
    onOpenFunds: (() -> Unit)?,
    onOpenSource: (String) -> Unit,
) {
    val status = state.status
    val sector = state.sector
    val estimate = state.estimate
    Result(state, estimate)
    Rule(state, StrikeSimulator.ruleFor(status, sector))
    Obligations(StrikeSimulator.obligationsFor(status, sector))
    Notes(StrikeSimulator.notesFor(status, sector))
    StrikeFunds(state, solidarity, onOpenFund, onOpenFunds)
    Sources(StrikeSimulator.sourcesFor(status, sector), onOpenSource)
}

@Composable
private fun Result(state: StrikeSimulatorState, estimate: StrikeEstimate?) {
    val palette = PpfTheme.palette
    SectionTitle(stringResource(Res.string.greve_result_title))
    InfoBox(Lucide.TriangleExclamationPoint, Modifier.testTag(StrikeTags.DISCLAIMER)) {
        Text(stringResource(Res.string.greve_disclaimer), style = MaterialTheme.typography.bodyMedium)
    }
    val cents = estimate?.retentionCents
    when {
        estimate == null -> Text(
            stringResource(Res.string.greve_result_incomplete),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkMuted,
            modifier = Modifier.testTag(StrikeTags.INCOMPLETE),
        )
        cents == null -> Text(
            stringResource(Res.string.greve_result_not_simulated),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(StrikeTags.NOT_SIMULATED),
        )
        else -> PpfCard(Modifier.fillMaxWidth().testTag(StrikeTags.RESULT)) {
            Text(
                stringResource(Res.string.greve_result_amount, StrikeNumbers.formatEuros(cents)),
                style = MaterialTheme.typography.headlineSmall,
                color = palette.ink,
                modifier = Modifier.testTag(StrikeTags.AMOUNT).semantics { liveRegion = LiveRegionMode.Polite },
            )
            estimate.shareBasisPoints?.let { share ->
                Text(
                    stringResource(Res.string.greve_result_share, StrikeNumbers.formatPercent(share)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.inkMuted,
                    modifier = Modifier.testTag(StrikeTags.SHARE),
                )
            }
            if (estimate.cappedAtMonthlyPay) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.greve_result_capped), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (state.status == StrikeStatus.PRIVATE) Res.string.greve_result_gross_private else Res.string.greve_result_gross_public),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSubtle,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.greve_detail_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Detail(estimate)
        }
    }
}

@Composable
private fun Detail(estimate: StrikeEstimate) {
    val style = MaterialTheme.typography.bodyMedium
    estimate.days.forEachIndexed { index, share ->
        val what = when (val day = share.day) {
            StrikeDay.FullDay -> stringResource(Res.string.greve_detail_full)
            StrikeDay.HalfDay -> stringResource(Res.string.greve_detail_half)
            is StrikeDay.Hours -> StrikeNumbers.formatHours(day.minutes)
        }
        val text = when (estimate.rule) {
            StrikeRule.STATE_THIRTIETH -> stringResource(Res.string.greve_detail_thirtieth, what)
            StrikeRule.PUBLIC_PROPORTIONAL -> when {
                share.day == StrikeDay.FullDay -> stringResource(Res.string.greve_detail_thirtieth, what)
                share.day == StrikeDay.HalfDay -> stringResource(Res.string.greve_detail_sixtieth, what)
                share.capped -> stringResource(Res.string.greve_detail_hourly_capped, what)
                else -> stringResource(Res.string.greve_detail_hourly, what)
            }
            StrikeRule.PRIVATE_PROPORTIONAL, StrikeRule.TRANSPORT_PROPORTIONAL ->
                stringResource(Res.string.greve_detail_not_worked, StrikeNumbers.formatHours(share.minutes ?: 0))
            StrikeRule.NOT_SIMULATED -> what
        }
        Text(stringResource(Res.string.greve_detail_day, index + 1, text), style = style)
    }
    val between = estimate.daysWithoutServiceCounted
    if (between > 0) Text(pluralStringResource(Res.plurals.greve_detail_between, between, between), style = style)
    estimate.thirtieths?.let { count ->
        Text(pluralStringResource(Res.plurals.greve_detail_thirtieths, count, count), style = style)
    }
    estimate.monthlyHoursHundredths?.let { monthly ->
        val minutes = estimate.days.sumOf { it.minutes ?: 0 }
        Text(
            stringResource(Res.string.greve_detail_private_total, StrikeNumbers.formatHours(minutes), StrikeNumbers.formatHundredths(monthly)),
            style = style,
        )
    }
}

@Composable
private fun Rule(state: StrikeSimulatorState, rule: StrikeRule) {
    val palette = PpfTheme.palette
    SectionTitle(stringResource(Res.string.greve_rule_title))
    val text = when (rule) {
        StrikeRule.PRIVATE_PROPORTIONAL, StrikeRule.TRANSPORT_PROPORTIONAL -> {
            val weekly = state.weeklyMinutes ?: StrikeSimulator.LEGAL_WEEKLY_MINUTES
            val monthly = StrikeNumbers.formatHundredths(StrikeSimulator.monthlyHoursHundredths(weekly)) + StrikeNumbers.NBSP + "h"
            stringResource(StrikeLabels.rule(rule), monthly, StrikeNumbers.formatHours(weekly))
        }
        else -> stringResource(StrikeLabels.rule(rule))
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(StrikeTags.RULE))
    SourcesLine(rule.sources)
    Text(
        stringResource(Res.string.greve_ai_line, STRIKE_RULES_VERIFIED_ON.toFrench()),
        style = MaterialTheme.typography.bodySmall,
        color = palette.inkSubtle,
        modifier = Modifier.testTag(StrikeTags.AI_LINE),
    )
}

@Composable
private fun Obligations(obligations: List<StrikeObligation>) {
    val palette = PpfTheme.palette
    SectionTitle(stringResource(Res.string.greve_before_title))
    obligations.forEach { obligation ->
        PpfCard(Modifier.fillMaxWidth().testTag(StrikeTags.obligation(obligation))) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(obligationIcon(obligation), contentDescription = null, tint = palette.brand, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(StrikeLabels.obligationTitle(obligation)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(StrikeLabels.obligationText(obligation)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(StrikeTags.obligationText(obligation)),
                    )
                    if (obligation.sources.isNotEmpty()) SourcesLine(obligation.sources)
                }
            }
        }
    }
}

private fun obligationIcon(obligation: StrikeObligation): ImageVector = when (obligation) {
    StrikeObligation.NO_NOTICE_PRIVATE, StrikeObligation.NOTICE_PUBLIC_SERVICE,
    StrikeObligation.NOTICE_TERRITORIAL, StrikeObligation.NOTICE_PRIMARY_SCHOOL -> Lucide.Megaphone
    StrikeObligation.NO_KNOWN_DECLARATION -> Lucide.Info
    else -> Lucide.Clock
}

@Composable
private fun Notes(notes: List<StrikeNote>) {
    val palette = PpfTheme.palette
    SectionTitle(stringResource(Res.string.greve_notes_title))
    notes.forEach { note ->
        Row(Modifier.fillMaxWidth().testTag(StrikeTags.note(note)), verticalAlignment = Alignment.Top) {
            Icon(Lucide.Info, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(StrikeLabels.note(note)), style = MaterialTheme.typography.bodyMedium)
                SourcesLine(note.sources)
            }
        }
    }
}

@Composable
private fun StrikeFunds(
    state: StrikeSimulatorState,
    solidarity: SolidarityLibrary,
    onOpenFund: ((SolidarityFund) -> Unit)?,
    onOpenFunds: (() -> Unit)?,
) {
    val palette = PpfTheme.palette
    Column(Modifier.fillMaxWidth().testTag(StrikeTags.FUNDS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.greve_funds_title))
        Text(stringResource(Res.string.greve_funds_text), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        NumberField(
            label = stringResource(Res.string.greve_funds_near_label),
            value = state.near,
            onValueChange = { state.near = it.take(StrikeSimulatorState.MAX_FIELD) },
            tag = StrikeTags.NEAR,
            keyboardType = KeyboardType.Text,
        )
        Help(stringResource(Res.string.greve_funds_near_hint) + ".")
        PrivateLine(stringResource(Res.string.greve_funds_near_private))
        // The same search as the solidarity rubric, on the device: open strike funds, the person's territory first.
        val funds = FundQuery(types = setOf(SolidarityFormats.FUND_STRIKE), near = state.near.takeIf { it.isNotBlank() }).apply(solidarity.funds)
        val loading = solidarity.status == SolidarityStatus.Idle || solidarity.status == SolidarityStatus.Loading
        when {
            funds.isEmpty() && loading -> Help(stringResource(Res.string.greve_funds_loading))
            funds.isEmpty() -> Text(
                stringResource(Res.string.greve_funds_none),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
                modifier = Modifier.testTag(StrikeTags.FUNDS_NONE),
            )
            else -> funds.take(MAX_FUNDS).forEach { fund -> FundRow(fund, onOpenFund) }
        }
        if (onOpenFunds != null) {
            PpfSecondaryButton(
                onClick = onOpenFunds,
                text = stringResource(Res.string.greve_funds_all),
                small = true,
                modifier = Modifier.testTag(StrikeTags.FUNDS_ALL),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FundRow(fund: SolidarityFund, onOpenFund: ((SolidarityFund) -> Unit)?) {
    val palette = PpfTheme.palette
    PpfCard(
        onClick = onOpenFund?.let { open -> { open(fund) } },
        modifier = Modifier.fillMaxWidth().testTag(StrikeTags.FUND + fund.slug),
        contentPadding = PaddingValues(12.dp),
    ) {
        Text(fund.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (fund.example) {
            Spacer(Modifier.height(4.dp))
            FlowRow { ExampleBadge() }
        }
        Spacer(Modifier.height(4.dp))
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
        Text(fund.summary, style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Sources(sources: List<StrikeSource>, onOpenSource: (String) -> Unit) {
    val palette = PpfTheme.palette
    SectionTitle(stringResource(Res.string.greve_sources_title))
    Text(
        stringResource(Res.string.greve_sources_text, STRIKE_RULES_VERIFIED_ON.toFrench()),
        style = MaterialTheme.typography.bodyMedium,
        color = palette.inkMuted,
    )
    sources.forEach { source ->
        PpfCard(
            onClick = { onOpenSource(source.url) },
            modifier = Modifier.fillMaxWidth().testTag(StrikeTags.source(source)),
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(StrikeLabels.source(source)), style = MaterialTheme.typography.bodyMedium)
                    Text(leavingHost(source.url), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.ExternalLink, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Shared pieces

/** A group of questions on a card, so that the page-coloured fields stand out. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    PpfCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), content = content)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 4.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.inkMuted, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun ErrorLine(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.accentInk,
        modifier = Modifier.padding(top = 2.dp).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The sources of a block, in words; the addresses are in "Sources officielles" at the end. */
@Composable
private fun SourcesLine(sources: List<StrikeSource>) {
    val labels = sources.map { stringResource(StrikeLabels.source(it)) }
    Text(
        stringResource(Res.string.greve_sources_inline, labels.joinToString(" ; ")),
        style = MaterialTheme.typography.bodySmall,
        color = PpfTheme.palette.inkSubtle,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * A field with its label always visible above it (the label is also the field's name for screen
 * readers, so the visible copy is not read twice). [showLabel] false when the row already says it.
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    showLabel: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    if (showLabel) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = PpfTheme.palette.ink,
            modifier = Modifier.padding(bottom = 4.dp).clearAndSetSemantics { },
        )
    }
    PpfTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
