package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.DeclarationState
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisMoment
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.ui.components.BadgeStyle
import org.politisationpopulairefrancaise.ui.components.PpfBadge
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.agenda_date_range
import org.politisationpopulairefrancaise.ui.resources.agenda_date_time
import org.politisationpopulairefrancaise.ui.resources.agenda_day_today
import org.politisationpopulairefrancaise.ui.resources.agenda_day_tomorrow
import org.politisationpopulairefrancaise.ui.resources.agenda_day_yesterday
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_en_cours
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_faite
import org.politisationpopulairefrancaise.ui.resources.agenda_declaration_non_faite
import org.politisationpopulairefrancaise.ui.resources.agenda_response_going
import org.politisationpopulairefrancaise.ui.resources.agenda_response_interested
import org.politisationpopulairefrancaise.ui.resources.agenda_state_published
import org.politisationpopulairefrancaise.ui.resources.agenda_state_rejected
import org.politisationpopulairefrancaise.ui.resources.agenda_state_review
import org.politisationpopulairefrancaise.ui.resources.agenda_state_waiting
import org.politisationpopulairefrancaise.ui.resources.agenda_status_cancelled
import org.politisationpopulairefrancaise.ui.resources.agenda_status_postponed
import org.politisationpopulairefrancaise.ui.resources.agenda_time_range
import org.politisationpopulairefrancaise.ui.resources.agenda_type_assemblee_generale
import org.politisationpopulairefrancaise.ui.resources.agenda_type_autre
import org.politisationpopulairefrancaise.ui.resources.agenda_type_blocage
import org.politisationpopulairefrancaise.ui.resources.agenda_type_greve
import org.politisationpopulairefrancaise.ui.resources.agenda_type_manifestation
import org.politisationpopulairefrancaise.ui.resources.agenda_type_rassemblement
import org.politisationpopulairefrancaise.ui.resources.agenda_type_reunion
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * French dates and hours of the agenda, in the local time of mainland France: "samedi 14 novembre
 * 2026", "14 h", "14 h 30" (a no-break space keeps the number with its "h", French typography).
 */
internal object AgendaFormat {
    private val WEEKDAYS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    private val MONTHS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )
    private const val NBSP = ' '

    fun hour(moment: ParisMoment): String =
        if (moment.minute == 0) "${moment.hour}${NBSP}h" else "${moment.hour}${NBSP}h$NBSP${moment.minute.toString().padStart(2, '0')}"

    /** "samedi 14 novembre", with the year when [withYear]. */
    fun date(moment: ParisMoment, withYear: Boolean): String {
        val day = if (moment.date.day == 1) "1er" else moment.date.day.toString()
        val text = "${WEEKDAYS[moment.dayOfWeek - 1]} $day ${MONTHS[moment.date.month - 1]}"
        return if (withYear) "$text ${moment.date.year}" else text
    }

    /** "14/11/2026", the form of the date fields. */
    fun numeric(moment: ParisMoment): String =
        "${moment.date.day.toString().padStart(2, '0')}/${moment.date.month.toString().padStart(2, '0')}/${moment.date.year}"

    fun capitalized(text: String): String = text.replaceFirstChar { it.uppercaseChar() }
}

@Composable
internal fun typeLabel(type: MobilizationType): String = stringResource(typeLabelResource(type))

internal fun typeLabelResource(type: MobilizationType): StringResource = when (type) {
    MobilizationType.MANIFESTATION -> Res.string.agenda_type_manifestation
    MobilizationType.BLOCAGE -> Res.string.agenda_type_blocage
    MobilizationType.RASSEMBLEMENT -> Res.string.agenda_type_rassemblement
    MobilizationType.GREVE -> Res.string.agenda_type_greve
    MobilizationType.ASSEMBLEE_GENERALE -> Res.string.agenda_type_assemblee_generale
    MobilizationType.REUNION -> Res.string.agenda_type_reunion
    MobilizationType.AUTRE -> Res.string.agenda_type_autre
}

/** The icon of each type (Lucide set of the visual identity). */
internal fun typeIcon(type: MobilizationType): ImageVector = when (type) {
    MobilizationType.MANIFESTATION -> Lucide.Megaphone
    MobilizationType.BLOCAGE -> Lucide.Ban
    MobilizationType.RASSEMBLEMENT -> Lucide.UsersRound
    MobilizationType.GREVE -> Lucide.HandFist
    MobilizationType.ASSEMBLEE_GENERALE -> Lucide.Vote
    MobilizationType.REUNION -> Lucide.MessagesSquare
    MobilizationType.AUTRE -> Lucide.Calendar
}

@Composable
internal fun declarationLabel(state: DeclarationState): String = stringResource(
    when (state) {
        DeclarationState.FAITE -> Res.string.agenda_declaration_faite
        DeclarationState.EN_COURS -> Res.string.agenda_declaration_en_cours
        DeclarationState.NON_FAITE -> Res.string.agenda_declaration_non_faite
    },
)

/** The header of a day of the list: "Aujourd’hui", "Demain", "Hier", or the date (with the year when it is another year). */
@Composable
internal fun dayHeader(epochDay: Long, now: Long): String {
    val today = ParisTime.epochDay(now)
    return when (epochDay) {
        today -> stringResource(Res.string.agenda_day_today)
        today + 1 -> stringResource(Res.string.agenda_day_tomorrow)
        today - 1 -> stringResource(Res.string.agenda_day_yesterday)
        else -> {
            val moment = ParisTime.moment(ParisTime.startOfDay(epochDay) + 12 * 3_600)
            AgendaFormat.capitalized(AgendaFormat.date(moment, withYear = moment.date.year != ParisTime.moment(now).date.year))
        }
    }
}

/**
 * When a mobilization takes place. [full] adds the date and the year ("Samedi 14 novembre 2026,
 * 14 h à 17 h"), as on the detail; otherwise the hours only for an event of one day (the day is
 * the header of the list). An event over several days always gives both dates.
 */
@Composable
internal fun whenText(mobilization: Mobilization, full: Boolean): String {
    val start = ParisTime.moment(mobilization.start)
    val end = mobilization.end?.let(ParisTime::moment)
    if (end != null && end.epochDay != start.epochDay) {
        val withYear = full || start.date.year != end.date.year
        return stringResource(
            Res.string.agenda_date_range,
            stringResource(Res.string.agenda_date_time, AgendaFormat.date(start, withYear), AgendaFormat.hour(start)),
            stringResource(Res.string.agenda_date_time, AgendaFormat.date(end, withYear), AgendaFormat.hour(end)),
        )
    }
    val hours = if (end != null) stringResource(Res.string.agenda_time_range, AgendaFormat.hour(start), AgendaFormat.hour(end)) else AgendaFormat.hour(start)
    return if (full) stringResource(Res.string.agenda_date_time, AgendaFormat.capitalized(AgendaFormat.date(start, withYear = true)), hours) else hours
}

/**
 * The status of a cancelled or postponed event, very visible: an icon and a word on a tinted pill
 * (the colour never carries the meaning alone). Nothing for an event "prévu".
 */
@Composable
internal fun StatusPill(status: MobilizationStatus, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    val (label, icon, colors) = when (status) {
        MobilizationStatus.PREVU -> return
        MobilizationStatus.ANNULE -> Triple(Res.string.agenda_status_cancelled, Lucide.Ban, palette.accentTint to palette.accentInk)
        MobilizationStatus.REPORTE -> Triple(Res.string.agenda_status_postponed, Lucide.Clock, palette.warningTint to palette.warning)
    }
    Pill(stringResource(label), icon, colors.first, colors.second, modifier.testTag(AgendaTags.STATUS + status.tagValue))
}

/** The user's own answer, shown on the card: it stays on the device. */
@Composable
internal fun ResponsePill(response: AgendaResponse, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    when (response) {
        AgendaResponse.GOING -> Pill(stringResource(Res.string.agenda_response_going), Lucide.CircleCheck, palette.successTint, palette.success, modifier)
        AgendaResponse.INTERESTED -> Pill(stringResource(Res.string.agenda_response_interested), Lucide.Bookmark, palette.brandTint, palette.brand, modifier)
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector, container: Color, content: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Where one of the user's own proposals stands. */
@Composable
internal fun OwnStatePill(state: OwnProposalState, modifier: Modifier = Modifier) {
    val (label, style) = when (state) {
        OwnProposalState.WAITING -> Res.string.agenda_state_waiting to BadgeStyle.NEUTRAL
        OwnProposalState.IN_REVIEW -> Res.string.agenda_state_review to BadgeStyle.BRAND
        OwnProposalState.PUBLISHED -> Res.string.agenda_state_published to BadgeStyle.SUCCESS
        OwnProposalState.NOT_PUBLISHED -> Res.string.agenda_state_rejected to BadgeStyle.NEUTRAL
    }
    PpfBadge(stringResource(label), style, modifier.testTag(AgendaTags.OWN_STATE + state.name.lowercase()))
}
