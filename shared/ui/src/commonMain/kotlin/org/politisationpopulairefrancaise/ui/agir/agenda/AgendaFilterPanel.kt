package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaPeriod
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaQuery
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationDraft
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfPrimaryButton
import org.politisationpopulairefrancaise.ui.components.PpfRadioRow
import org.politisationpopulairefrancaise.ui.components.PpfSecondaryButton
import org.politisationpopulairefrancaise.ui.components.PpfSelectableChip
import org.politisationpopulairefrancaise.ui.components.PpfTextField
import org.politisationpopulairefrancaise.ui.components.safeAreaBottomPadding
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_apply
import org.politisationpopulairefrancaise.ui.resources.agenda_clear_filters
import org.politisationpopulairefrancaise.ui.resources.agenda_organizers_none
import org.politisationpopulairefrancaise.ui.resources.agenda_panel_close
import org.politisationpopulairefrancaise.ui.resources.agenda_panel_title
import org.politisationpopulairefrancaise.ui.resources.agenda_period_any
import org.politisationpopulairefrancaise.ui.resources.agenda_period_month
import org.politisationpopulairefrancaise.ui.resources.agenda_period_range
import org.politisationpopulairefrancaise.ui.resources.agenda_period_today
import org.politisationpopulairefrancaise.ui.resources.agenda_period_week
import org.politisationpopulairefrancaise.ui.resources.agenda_private_note
import org.politisationpopulairefrancaise.ui.resources.agenda_range_from
import org.politisationpopulairefrancaise.ui.resources.agenda_range_invalid
import org.politisationpopulairefrancaise.ui.resources.agenda_range_to
import org.politisationpopulairefrancaise.ui.resources.agenda_section_organizers
import org.politisationpopulairefrancaise.ui.resources.agenda_section_period
import org.politisationpopulairefrancaise.ui.resources.agenda_section_territory
import org.politisationpopulairefrancaise.ui.resources.agenda_section_text
import org.politisationpopulairefrancaise.ui.resources.agenda_section_types
import org.politisationpopulairefrancaise.ui.resources.agenda_territory_none
import org.politisationpopulairefrancaise.ui.resources.agenda_text_placeholder
import org.politisationpopulairefrancaise.ui.revendications.AdaptivePanel
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the filter panel of the agenda. */
object AgendaPanelTags {
    const val PANEL = "agenda-panel"
    const val CLOSE = "agenda-panel-close"
    const val PERIOD = "agenda-period-" // followed by any, today, week, month or range
    const val RANGE_FROM = "agenda-range-from"
    const val RANGE_TO = "agenda-range-to"
    const val TYPE = "agenda-type-" // followed by the type tag
    const val TERRITORY = "agenda-territory-" // followed by the folded territory
    const val ORGANIZER = "agenda-organizer-" // followed by the key
    const val TEXT = "agenda-text"
    const val APPLY = "agenda-apply"
    const val CLEAR = "agenda-clear"
}

private enum class PeriodChoice(val tag: String) { ANY("any"), TODAY("today"), WEEK("week"), MONTH("month"), RANGE("range") }

/**
 * The filters of the agenda (M1: date, place, type, organisation, and words): a bottom sheet on
 * phones, a dialog elsewhere (the container of the demands wall). The territories and organisers
 * offered are those of the mobilizations on the device. The choices are a draft until "Afficher N
 * mobilisations", whose number is counted live; everything is computed on the device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgendaFilterPanel(
    agenda: AgendaLibrary,
    names: AgendaNames,
    compact: Boolean,
    onDismiss: () -> Unit,
    onApply: (AgendaQuery) -> Unit,
) {
    val palette = PpfTheme.palette
    val initial = agenda.filters
    var draft by remember { mutableStateOf(initial) }
    var choice by remember {
        mutableStateOf(
            when (initial.period) {
                AgendaPeriod.Anytime -> PeriodChoice.ANY
                AgendaPeriod.Today -> PeriodChoice.TODAY
                AgendaPeriod.ThisWeek -> PeriodChoice.WEEK
                AgendaPeriod.ThisMonth -> PeriodChoice.MONTH
                is AgendaPeriod.Range -> PeriodChoice.RANGE
            },
        )
    }
    val range = initial.period as? AgendaPeriod.Range
    var fromText by remember { mutableStateOf(range?.from?.let(::numeric).orEmpty()) }
    var toText by remember { mutableStateOf(range?.to?.let(::numeric).orEmpty()) }
    val today = ParisTime.moment(agenda.now).date
    val from = MobilizationDraft.parseDate(fromText, today)
    val to = MobilizationDraft.parseDate(toText, today)
    val period = when (choice) {
        PeriodChoice.ANY -> AgendaPeriod.Anytime
        PeriodChoice.TODAY -> AgendaPeriod.Today
        PeriodChoice.WEEK -> AgendaPeriod.ThisWeek
        PeriodChoice.MONTH -> AgendaPeriod.ThisMonth
        PeriodChoice.RANGE -> if (from == null && to == null) AgendaPeriod.Anytime else AgendaPeriod.Range(from, to)
    }
    val effective = draft.copy(period = period)
    val timing = agenda.query.timing
    val count = remember(agenda.mobilizations, effective, timing, agenda.now) { effective.copy(timing = timing).apply(agenda.mobilizations, agenda.now).size }
    val territories = remember(agenda.mobilizations) { territoryLabels(agenda.mobilizations) }
    val organizers = remember(agenda.mobilizations) { agenda.mobilizations.flatMap { it.organizers }.distinct() + draft.organizers.filter { key -> agenda.mobilizations.none { key in it.organizers } } }

    AdaptivePanel(compact = compact, onDismiss = onDismiss, modifier = Modifier.testTag(AgendaPanelTags.PANEL)) { close ->
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = if (compact) 0.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.agenda_panel_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            PpfIconButton(Lucide.X, stringResource(Res.string.agenda_panel_close), onClick = close, modifier = Modifier.testTag(AgendaPanelTags.CLOSE), tint = palette.ink)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = palette.border)

        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(Res.string.agenda_section_period)) {
                Column(Modifier.selectableGroup()) {
                    PeriodChoice.entries.forEach { option ->
                        PpfRadioRow(
                            label = stringResource(
                                when (option) {
                                    PeriodChoice.ANY -> Res.string.agenda_period_any
                                    PeriodChoice.TODAY -> Res.string.agenda_period_today
                                    PeriodChoice.WEEK -> Res.string.agenda_period_week
                                    PeriodChoice.MONTH -> Res.string.agenda_period_month
                                    PeriodChoice.RANGE -> Res.string.agenda_period_range
                                },
                            ),
                            selected = choice == option,
                            onClick = { choice = option },
                            modifier = Modifier.testTag(AgendaPanelTags.PERIOD + option.tag),
                        )
                    }
                }
                if (choice == PeriodChoice.RANGE) {
                    DateField(fromText, { fromText = it.take(10) }, stringResource(Res.string.agenda_range_from), invalid = fromText.isNotBlank() && from == null, tag = AgendaPanelTags.RANGE_FROM)
                    DateField(toText, { toText = it.take(10) }, stringResource(Res.string.agenda_range_to), invalid = toText.isNotBlank() && to == null, tag = AgendaPanelTags.RANGE_TO)
                }
            }

            Section(stringResource(Res.string.agenda_section_types)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobilizationType.entries.forEach { type ->
                        PpfSelectableChip(
                            label = typeLabel(type),
                            selected = type in draft.types,
                            onClick = { draft = draft.copy(types = draft.types.toggle(type)) },
                            leadingIcon = if (type in draft.types) null else typeIcon(type),
                            modifier = Modifier.testTag(AgendaPanelTags.TYPE + type.tagValue),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.agenda_section_territory)) {
                val keys = territories.keys.toList() + draft.territories.filter { it !in territories }
                if (keys.isEmpty()) Text(stringResource(Res.string.agenda_territory_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        PpfSelectableChip(
                            label = territories[key] ?: key,
                            selected = key in draft.territories,
                            onClick = { draft = draft.copy(territories = draft.territories.toggle(key)) },
                            leadingIcon = if (key in draft.territories) null else Lucide.MapPin,
                            modifier = Modifier.testTag(AgendaPanelTags.TERRITORY + key),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.agenda_section_organizers)) {
                if (organizers.isEmpty()) Text(stringResource(Res.string.agenda_organizers_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkSubtle)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    organizers.forEach { key ->
                        PpfSelectableChip(
                            label = names.organisation(key),
                            selected = key in draft.organizers,
                            onClick = { draft = draft.copy(organizers = draft.organizers.toggle(key)) },
                            modifier = Modifier.testTag(AgendaPanelTags.ORGANIZER + key),
                        )
                    }
                }
            }

            Section(stringResource(Res.string.agenda_section_text)) {
                PpfTextField(
                    value = draft.text,
                    onValueChange = { draft = draft.copy(text = it.take(MAX_TEXT_LENGTH)) },
                    placeholder = stringResource(Res.string.agenda_text_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(AgendaPanelTags.TEXT),
                )
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Lucide.Lock, contentDescription = null, tint = palette.inkSubtle, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.agenda_private_note), style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
            }
        }

        HorizontalDivider(color = palette.border)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).then(if (compact) Modifier.safeAreaBottomPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PpfPrimaryButton(
                onClick = { onApply(effective) },
                text = pluralStringResource(Res.plurals.agenda_apply, count, count),
                modifier = Modifier.fillMaxWidth().testTag(AgendaPanelTags.APPLY),
            )
            PpfSecondaryButton(
                onClick = {
                    draft = initial.cleared()
                    choice = PeriodChoice.ANY
                    fromText = ""
                    toText = ""
                },
                text = stringResource(Res.string.agenda_clear_filters),
                enabled = effective.constraintCount > 0,
                modifier = Modifier.testTag(AgendaPanelTags.CLEAR),
            )
        }
    }
}

private const val MAX_TEXT_LENGTH = 80

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun numeric(date: CivilDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.toString().padStart(2, '0')}/${date.year}"

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = PpfTheme.palette.brand, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, placeholder: String, invalid: Boolean, tag: String) {
    val message = stringResource(Res.string.agenda_range_invalid)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PpfTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag).then(if (invalid) Modifier.semantics { error(message) } else Modifier),
        )
        if (invalid) Text(message, style = MaterialTheme.typography.bodySmall, color = PpfTheme.palette.accentInk)
    }
}
