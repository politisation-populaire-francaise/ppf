package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallIssue
import org.politisationpopulairefrancaise.core.agir.calls.CallIssueKind
import org.politisationpopulairefrancaise.core.agir.calls.CallResponse
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.ParisTime
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.*

/*
 * The words of the calls: one label per type, field, choice and finding, the dates in Paris time
 * and the countdown. Kept apart from the screens so that every screen says the same thing.
 */

internal fun typeLabelResource(type: CallType): StringResource = when (type) {
    CallType.SE_REUNIR -> Res.string.appels_type_se_reunir
    CallType.DEBATTRE -> Res.string.appels_type_debattre
    CallType.S_ORGANISER -> Res.string.appels_type_s_organiser
    CallType.MANIFESTER -> Res.string.appels_type_manifester
    CallType.SOUTENIR -> Res.string.appels_type_soutenir
    CallType.PETITION -> Res.string.appels_type_petition
    CallType.INTERPELLER -> Res.string.appels_type_interpeller
    CallType.BOYCOTTER -> Res.string.appels_type_boycotter
    CallType.COLLECTE -> Res.string.appels_type_collecte
    CallType.BENEVOLES -> Res.string.appels_type_benevoles
    CallType.INFORMATION -> Res.string.appels_type_information
    CallType.TEMOINS -> Res.string.appels_type_temoins
    CallType.AUTRE -> Res.string.appels_type_autre
}

internal fun typeDescriptionResource(type: CallType): StringResource = when (type) {
    CallType.SE_REUNIR -> Res.string.appels_type_se_reunir_desc
    CallType.DEBATTRE -> Res.string.appels_type_debattre_desc
    CallType.S_ORGANISER -> Res.string.appels_type_s_organiser_desc
    CallType.MANIFESTER -> Res.string.appels_type_manifester_desc
    CallType.SOUTENIR -> Res.string.appels_type_soutenir_desc
    CallType.PETITION -> Res.string.appels_type_petition_desc
    CallType.INTERPELLER -> Res.string.appels_type_interpeller_desc
    CallType.BOYCOTTER -> Res.string.appels_type_boycotter_desc
    CallType.COLLECTE -> Res.string.appels_type_collecte_desc
    CallType.BENEVOLES -> Res.string.appels_type_benevoles_desc
    CallType.INFORMATION -> Res.string.appels_type_information_desc
    CallType.TEMOINS -> Res.string.appels_type_temoins_desc
    CallType.AUTRE -> Res.string.appels_type_autre_desc
}

/** The guided template of AP-01: writing tips, then an example of title. */
internal fun typeTipsResource(type: CallType): Pair<StringResource, StringResource> = when (type) {
    CallType.SE_REUNIR -> Res.string.appels_tips_se_reunir to Res.string.appels_example_se_reunir
    CallType.DEBATTRE -> Res.string.appels_tips_debattre to Res.string.appels_example_debattre
    CallType.S_ORGANISER -> Res.string.appels_tips_s_organiser to Res.string.appels_example_s_organiser
    CallType.MANIFESTER -> Res.string.appels_tips_manifester to Res.string.appels_example_manifester
    CallType.SOUTENIR -> Res.string.appels_tips_soutenir to Res.string.appels_example_soutenir
    CallType.PETITION -> Res.string.appels_tips_petition to Res.string.appels_example_petition
    CallType.INTERPELLER -> Res.string.appels_tips_interpeller to Res.string.appels_example_interpeller
    CallType.BOYCOTTER -> Res.string.appels_tips_boycotter to Res.string.appels_example_boycotter
    CallType.COLLECTE -> Res.string.appels_tips_collecte to Res.string.appels_example_collecte
    CallType.BENEVOLES -> Res.string.appels_tips_benevoles to Res.string.appels_example_benevoles
    CallType.INFORMATION -> Res.string.appels_tips_information to Res.string.appels_example_information
    CallType.TEMOINS -> Res.string.appels_tips_temoins to Res.string.appels_example_temoins
    CallType.AUTRE -> Res.string.appels_tips_autre to Res.string.appels_example_autre
}

internal fun typeIcon(type: CallType): ImageVector = when (type) {
    CallType.SE_REUNIR, CallType.S_ORGANISER -> Lucide.Users
    CallType.DEBATTRE -> Lucide.MessagesSquare
    CallType.MANIFESTER -> Lucide.Megaphone
    CallType.SOUTENIR -> Lucide.HandFist
    CallType.PETITION -> Lucide.Pencil
    CallType.INTERPELLER -> Lucide.Landmark
    CallType.BOYCOTTER -> Lucide.Ban
    CallType.COLLECTE -> Lucide.Heart
    CallType.BENEVOLES -> Lucide.HandHelping
    CallType.INFORMATION -> Lucide.Newspaper
    CallType.TEMOINS -> Lucide.Eye
    CallType.AUTRE -> Lucide.Lightbulb
}

internal fun responseResource(response: CallResponse): StringResource = when (response) {
    CallResponse.ATTEND -> Res.string.appels_answer_attend
    CallResponse.SUPPORT -> Res.string.appels_answer_support
    CallResponse.SIGNED -> Res.string.appels_answer_signed
    CallResponse.HELP -> Res.string.appels_answer_help
    CallResponse.TESTIFY -> Res.string.appels_answer_testify
}

/** The label of a field of a call, by tag or form field name; witnesses speak of the facts. */
internal fun fieldLabelResource(field: String, type: CallType? = null): StringResource = when (field) {
    CallDraft.FIELD_TITLE -> Res.string.appels_field_title
    CallDraft.FIELD_TEXT -> Res.string.appels_field_text
    CallDraft.FIELD_SUMMARY -> Res.string.appels_field_summary
    CallDraft.FIELD_TERRITORY -> Res.string.appels_field_territory
    CallDraft.FIELD_TYPE -> Res.string.appels_field_type
    CallDraft.FIELD_PLEDGE -> Res.string.appels_field_pledge
    AgirFormats.TAG_GOAL -> Res.string.appels_field_goal
    AgirFormats.TAG_DEADLINE -> Res.string.appels_field_deadline
    AgirFormats.TAG_LOCATION -> Res.string.appels_field_location
    AgirFormats.TAG_LINK -> Res.string.appels_field_link
    AgirFormats.TAG_START -> if (type == CallType.TEMOINS) Res.string.appels_field_start_witnesses else Res.string.appels_field_start
    AgirFormats.TAG_END -> if (type == CallType.TEMOINS) Res.string.appels_field_end_witnesses else Res.string.appels_field_end
    AgirFormats.TAG_REPORT -> Res.string.appels_field_report
    AgirFormats.TAG_DECLARATION -> Res.string.appels_field_declaration
    CallFields.OBJECT -> Res.string.appels_field_object
    CallFields.ORGANIZER -> Res.string.appels_field_organizer
    CallFields.ACCESSIBILITY -> Res.string.appels_field_accessibility
    CallFields.CAUSE -> Res.string.appels_field_cause
    CallFields.ACTION -> Res.string.appels_field_action
    CallFields.PLATFORM -> Res.string.appels_field_platform
    CallFields.TARGETS -> Res.string.appels_field_targets
    CallFields.REQUEST -> Res.string.appels_field_request
    CallFields.COMPANY -> Res.string.appels_field_company
    CallFields.SOURCE -> Res.string.appels_field_source
    CallFields.ALTERNATIVES -> Res.string.appels_field_alternatives
    CallFields.DURATION -> Res.string.appels_field_duration
    CallFields.BENEFICIARY -> Res.string.appels_field_beneficiary
    CallFields.USE -> Res.string.appels_field_use
    CallFields.MISSION -> Res.string.appels_field_mission
    CallFields.SKILLS -> Res.string.appels_field_skills
    CallFields.PEOPLE -> Res.string.appels_field_people
    CallFields.REMOTE -> Res.string.appels_field_remote
    CallFields.PERIOD -> Res.string.appels_field_period
    CallFields.EVIDENCE -> Res.string.appels_field_evidence
    CallFields.RECIPIENT -> Res.string.appels_field_recipient
    else -> Res.string.appels_field_value_placeholder
}

/** A short help line under some fields. */
internal fun fieldHelpResource(tag: String): StringResource? = when (tag) {
    CallFields.ORGANIZER -> Res.string.appels_field_organizer_help
    CallFields.RECIPIENT -> Res.string.appels_field_recipient_help
    CallFields.SOURCE -> Res.string.appels_field_source_help
    CallFields.EVIDENCE -> Res.string.appels_field_evidence_help
    // Decision of the owner (D-044): the three values are offered and shown; the author is told it is public.
    AgirFormats.TAG_DECLARATION -> Res.string.appels_field_declaration_help
    else -> null
}

internal fun choiceLabelResource(tag: String, value: String): StringResource? = when (tag) {
    CallFields.PLATFORM -> when (value) {
        "assemblee-nationale" -> Res.string.appels_platform_assemblee_nationale
        "senat" -> Res.string.appels_platform_senat
        "initiative-citoyenne-europeenne" -> Res.string.appels_platform_initiative_citoyenne_europeenne
        "autre" -> Res.string.appels_platform_autre
        else -> null
    }
    AgirFormats.TAG_DECLARATION -> when (value) {
        "faite" -> Res.string.appels_declaration_faite
        "en-cours" -> Res.string.appels_declaration_en_cours
        "non-faite" -> Res.string.appels_declaration_non_faite
        else -> null
    }
    else -> null
}

/** A requirement in words: "Lieu ou Lien". */
@Composable
internal fun requirementLabel(key: String, type: CallType?): String {
    val labels = key.split('|').map { stringResource(fieldLabelResource(it, type)) }
    return labels.reduce { a, b -> stringResource(Res.string.appels_or, a, b) }
}

/** What a finding of the rules says, with the field it points at. */
@Composable
internal fun issueText(issue: CallIssue, type: CallType?): String {
    val field = if (issue.kind == CallIssueKind.MISSING_FIELD) requirementLabel(issue.field, type) else stringResource(fieldLabelResource(issue.field, type))
    return when (issue.kind) {
        CallIssueKind.MISSING_TYPE -> stringResource(Res.string.appels_issue_missing_type)
        CallIssueKind.MISSING_FIELD -> stringResource(Res.string.appels_issue_missing_field, field)
        CallIssueKind.TOO_SHORT -> stringResource(if (issue.field == CallDraft.FIELD_TITLE) Res.string.appels_issue_too_short_title else Res.string.appels_issue_too_short_text)
        CallIssueKind.TOO_LONG -> stringResource(Res.string.appels_issue_too_long, field)
        CallIssueKind.MISSING_TERRITORY -> stringResource(Res.string.appels_issue_missing_territory)
        CallIssueKind.BAD_NUMBER -> stringResource(Res.string.appels_issue_bad_number, field)
        CallIssueKind.BAD_DATE -> stringResource(Res.string.appels_issue_bad_date, field)
        CallIssueKind.PAST_DATE -> stringResource(Res.string.appels_issue_past_date, field)
        CallIssueKind.END_BEFORE_START -> stringResource(Res.string.appels_issue_end_before_start)
        CallIssueKind.BAD_LINK -> stringResource(Res.string.appels_issue_bad_link, field)
        CallIssueKind.BAD_CHOICE -> stringResource(Res.string.appels_issue_bad_choice, field)
        CallIssueKind.PERSONAL_DATA -> stringResource(Res.string.appels_issue_personal_data, field)
        CallIssueKind.FUNDRAISING_NOT_ALLOWED -> stringResource(Res.string.appels_issue_fundraising_not_allowed)
        CallIssueKind.FUNDRAISING_PLEDGE -> stringResource(Res.string.appels_issue_fundraising_pledge)
    }
}

/** "14 novembre 2026", in Paris time. */
internal fun parisDate(epochSeconds: Long): String {
    val moment = ParisTime.of(epochSeconds)
    return CivilDate(moment.year, moment.month, moment.day).toFrench()
}

/** "18 h 30", "9 h", in Paris time (French typography, no-break spaces so that the hour never splits). */
internal fun parisHour(epochSeconds: Long): String {
    val moment = ParisTime.of(epochSeconds)
    return if (moment.minute == 0) "${moment.hour} h" else "${moment.hour} h ${moment.minute.toString().padStart(2, '0')}"
}

/** "14 novembre 2026 à 18 h 30"; a deadline lasting the whole day (23:59) shows its date only. */
@Composable
internal fun parisDateTime(epochSeconds: Long): String {
    val moment = ParisTime.of(epochSeconds)
    if (moment.hour == CallDraft.END_OF_DAY.first && moment.minute == CallDraft.END_OF_DAY.second) return parisDate(epochSeconds)
    return stringResource(Res.string.appels_date_time, parisDate(epochSeconds), parisHour(epochSeconds))
}

/** "1 000", "12 500": thousands separated by a narrow no-break space, as in French. */
internal fun frenchNumber(value: Long): String {
    val digits = value.toString()
    return digits.reversed().chunked(3).joinToString(" ").reversed()
}

/** "Dans 8 jours", "Demain", "Aujourd’hui", "Échéance passée", counted in Paris calendar days. */
@Composable
internal fun countdown(due: Long, now: Long): String {
    if (due < now) return stringResource(Res.string.appels_countdown_passed)
    val days = ParisTime.calendarDaysBetween(now, due).toInt()
    return when (days) {
        0 -> stringResource(Res.string.appels_countdown_today)
        1 -> stringResource(Res.string.appels_countdown_tomorrow)
        else -> pluralStringResource(Res.plurals.appels_countdown_days, days, days)
    }
}

/** When a call is due, in words: the deadline, or the start (with its end for witnesses). */
@Composable
internal fun dueLabel(deadline: Long?, start: Long?): String? = when {
    deadline != null -> stringResource(Res.string.appels_due_on, parisDateTime(deadline))
    start != null -> stringResource(Res.string.appels_starts_on, parisDateTime(start))
    else -> null
}
