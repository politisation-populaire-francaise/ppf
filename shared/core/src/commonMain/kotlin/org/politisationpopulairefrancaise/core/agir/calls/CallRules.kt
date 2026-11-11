package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.text.FrenchText

/** What a rule found in a draft call. */
enum class CallIssueKind {
    /** No type chosen yet. */
    MISSING_TYPE,

    /** A field required by the type (AP-10) is empty; [CallIssue.field] is the requirement ("location|r"). */
    MISSING_FIELD,
    TOO_SHORT,
    TOO_LONG,

    /** The territory is empty. */
    MISSING_TERRITORY,

    /** Not a positive whole number (goal, number of people). */
    BAD_NUMBER,

    /** A date or an hour that cannot be read ("31/02/2027", "25h"). */
    BAD_DATE,

    /** The date has already passed (a meeting, a deadline). */
    PAST_DATE,

    /** The end is not after the start. */
    END_BEFORE_START,

    /** A link that is not a full `https://` address. */
    BAD_LINK,

    /** A choice outside the list (platform of a petition, declaration of a demonstration). */
    BAD_CHOICE,

    /** An e-mail address, a phone number, an IBAN or a social security number in a text. */
    PERSONAL_DATA,

    /** A fundraising by an account that is not a verified organisation (AP-08). */
    FUNDRAISING_NOT_ALLOWED,

    /** A fundraising without the pledge that it is for no candidate and no party (AP-08). */
    FUNDRAISING_PLEDGE,
}

/** One finding: [field] is the field to correct (a tag, or "title", "text", "summary", "territory", "type", "pledge"). */
data class CallIssue(val kind: CallIssueKind, val field: String)

/**
 * The rules of the form of a call (AP-10): the fields each type requires, lengths, dates, links,
 * the territory, no personal data in the texts (the same patterns as the demands, [DraftRules]),
 * and the limits of fundraising (AP-08, point of attention 12). Only these rules may block the
 * sending: the similar calls found while typing are shown, never blocking. The server applies its
 * own checks again (contract, section 5), so a modified application cannot skip them.
 */
object CallRules {
    const val TITLE_MIN: Int = 10
    const val TITLE_MAX: Int = AgirFormats.MAX_TITLE
    const val TEXT_MIN: Int = 20
    const val TEXT_MAX: Int = AgirFormats.MAX_TEXT
    const val SUMMARY_MAX: Int = 300
    const val TERRITORY_MAX: Int = 80
    const val FIELD_MAX: Int = 300
    // The server refuses a tag value longer than AgirFormats.MAX_TAG_VALUE: the form stops at the same length.
    const val LONG_FIELD_MAX: Int = AgirFormats.MAX_TAG_VALUE
    const val LINK_MAX: Int = AgirFormats.MAX_TAG_VALUE
    const val REPORT_MAX: Int = AgirFormats.MAX_REPORT

    /** Goals and numbers of people: at most nine digits. */
    const val NUMBER_MAX: Long = 999_999_999

    /** A date typed a little in the past (the minute being typed) is not refused. */
    const val PAST_TOLERANCE_SECONDS: Long = 3_600

    /**
     * Checks [draft] at [now]. [canFundraise] is true only for a verified organisation; it decides
     * whether a fundraising may be sent at all.
     */
    fun check(draft: CallDraft, canFundraise: Boolean, now: Long): List<CallIssue> = buildList {
        val type = draft.type
        if (type == null) add(CallIssue(CallIssueKind.MISSING_TYPE, CallDraft.FIELD_TYPE))

        length(draft.title, CallDraft.FIELD_TITLE, TITLE_MIN, TITLE_MAX)
        length(draft.text, CallDraft.FIELD_TEXT, TEXT_MIN, TEXT_MAX)
        if (draft.summary.trim().length > SUMMARY_MAX) add(CallIssue(CallIssueKind.TOO_LONG, CallDraft.FIELD_SUMMARY))
        val territory = draft.territory.trim()
        if (FrenchText.fold(territory).isEmpty()) add(CallIssue(CallIssueKind.MISSING_TERRITORY, CallDraft.FIELD_TERRITORY))
        if (territory.length > TERRITORY_MAX) add(CallIssue(CallIssueKind.TOO_LONG, CallDraft.FIELD_TERRITORY))
        if (draft.location.trim().length > FIELD_MAX) add(CallIssue(CallIssueKind.TOO_LONG, AgirFormats.TAG_LOCATION))

        number(draft.goal, AgirFormats.TAG_GOAL)
        link(draft.link, AgirFormats.TAG_LINK)
        dates(draft, type, now)

        if (type != null) {
            CallFields.specific(type).forEach { field ->
                val value = draft.field(field.tag).trim()
                when (field.kind) {
                    CallFieldKind.TEXT, CallFieldKind.LONG_TEXT -> if (value.length > field.maxLength) add(CallIssue(CallIssueKind.TOO_LONG, field.tag))
                    CallFieldKind.NUMBER -> number(value, field.tag)
                    CallFieldKind.CHOICE -> if (value.isNotEmpty() && value !in field.choices) add(CallIssue(CallIssueKind.BAD_CHOICE, field.tag))
                    CallFieldKind.FLAG, CallFieldKind.DATE_TIME -> Unit
                }
            }
            type.requirements.filter { requirement -> requirement.alternatives.none(draft::has) }.forEach { requirement ->
                // A date typed but unreadable is reported once, as such.
                if (none { it.kind == CallIssueKind.BAD_DATE && it.field in requirement.alternatives }) {
                    add(CallIssue(CallIssueKind.MISSING_FIELD, requirement.key))
                }
            }
            if (type.isFundraising) {
                if (!canFundraise) add(CallIssue(CallIssueKind.FUNDRAISING_NOT_ALLOWED, CallDraft.FIELD_TYPE))
                if (!draft.notForCandidateOrParty) add(CallIssue(CallIssueKind.FUNDRAISING_PLEDGE, CallDraft.FIELD_PLEDGE))
            }
        }

        if (type in CallSlots.TYPES) slots(draft, now)

        // The match itself is never kept nor reported: only the field to correct.
        draft.texts().entries.firstOrNull { (_, text) -> text.length <= TEXT_MAX * 2 && DraftRules.containsPersonalData(text) }?.let { (field, _) ->
            add(CallIssue(CallIssueKind.PERSONAL_DATA, field))
        }
    }

    /** The field of the [index]th slot in the issues (AP-11). */
    fun slotField(index: Int): String = "slot-$index"

    private fun MutableList<CallIssue>.slots(draft: CallDraft, now: Long) {
        if (draft.slots.size > CallSlots.MAX_SLOTS) add(CallIssue(CallIssueKind.TOO_LONG, FIELD_SLOTS))
        draft.slots.take(CallSlots.MAX_SLOTS).forEachIndexed { index, slot ->
            val field = slotField(index)
            val label = slot.label.trim()
            val start = slot.start()
            val end = slot.end()
            val places = slot.places.trim().toIntOrNull()
            when {
                label.isEmpty() -> add(CallIssue(CallIssueKind.MISSING_FIELD, field))
                label.length > CallSlots.MAX_LABEL -> add(CallIssue(CallIssueKind.TOO_LONG, field))
                DraftRules.containsPersonalData(label) -> add(CallIssue(CallIssueKind.PERSONAL_DATA, field))
                start == null || end == null -> add(CallIssue(CallIssueKind.BAD_DATE, field))
                start < now - PAST_TOLERANCE_SECONDS -> add(CallIssue(CallIssueKind.PAST_DATE, field))
                places == null || places !in 1..CallSlots.MAX_PLACES -> add(CallIssue(CallIssueKind.BAD_NUMBER, field))
            }
        }
    }

    /** The field of the list of slots, when there are too many. */
    const val FIELD_SLOTS: String = "slots"

    /** The report of a closing (AP-09): optional, 2000 characters at most, no personal data. */
    fun checkReport(report: String): List<CallIssue> = buildList {
        val text = report.trim()
        if (text.length > REPORT_MAX) add(CallIssue(CallIssueKind.TOO_LONG, AgirFormats.TAG_REPORT))
        if (text.length <= REPORT_MAX * 2 && DraftRules.containsPersonalData(text)) add(CallIssue(CallIssueKind.PERSONAL_DATA, AgirFormats.TAG_REPORT))
    }

    /** True when nothing blocks the sending. */
    fun canSubmit(issues: List<CallIssue>): Boolean = issues.isEmpty()

    /** True for a full `https://` address with a host, without spaces. */
    fun isValidLink(value: String): Boolean {
        val link = value.trim()
        if (link.length > LINK_MAX || link.any { it.isWhitespace() }) return false
        if (!link.startsWith("https://", ignoreCase = true)) return false
        val host = link.substring(8).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.') && host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' }
    }

    /** The host of a link, to tell the reader where it leads ("www.helloasso.com"); null when it cannot be read. */
    fun hostOf(value: String): String? {
        if (!isValidLink(value)) return null
        return value.trim().substring(8).substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':').lowercase()
    }

    private fun MutableList<CallIssue>.length(value: String, field: String, min: Int, max: Int) {
        val length = value.trim().length
        when {
            length < min -> add(CallIssue(CallIssueKind.TOO_SHORT, field))
            length > max -> add(CallIssue(CallIssueKind.TOO_LONG, field))
        }
    }

    private fun MutableList<CallIssue>.number(value: String, field: String) {
        val text = value.trim()
        if (text.isEmpty()) return
        val number = text.takeIf { it.length <= 9 && it.all(Char::isDigit) }?.toLongOrNull()
        if (number == null || number < 1 || number > NUMBER_MAX) add(CallIssue(CallIssueKind.BAD_NUMBER, field))
    }

    private fun MutableList<CallIssue>.link(value: String, field: String) {
        if (value.isBlank()) return
        if (!isValidLink(value)) add(CallIssue(CallIssueKind.BAD_LINK, field))
    }

    private fun MutableList<CallIssue>.dates(draft: CallDraft, type: CallType?, now: Long) {
        // Only the dates the form shows count: the deadline always, start and end for the types that ask for them.
        val shown = buildList {
            add(AgirFormats.TAG_DEADLINE)
            if (type != null && type.requires(AgirFormats.TAG_START)) add(AgirFormats.TAG_START)
            if (type != null && type.requires(AgirFormats.TAG_END)) add(AgirFormats.TAG_END)
        }
        shown.forEach { tag ->
            val text = draft.date(tag)
            if (!text.isBlank && draft.instant(tag) == null) add(CallIssue(CallIssueKind.BAD_DATE, tag))
        }
        val witnesses = type?.isWitnesses == true
        // The facts looked into by witnesses are in the past; what a call announces is in the future.
        if (!witnesses) {
            listOf(AgirFormats.TAG_DEADLINE, AgirFormats.TAG_START).filter { it in shown }.forEach { tag ->
                val instant = draft.instant(tag)
                if (instant != null && instant < now - PAST_TOLERANCE_SECONDS) add(CallIssue(CallIssueKind.PAST_DATE, tag))
            }
        }
        val start = draft.instant(AgirFormats.TAG_START)
        val end = draft.instant(AgirFormats.TAG_END)
        if (AgirFormats.TAG_END in shown && start != null && end != null && end <= start) add(CallIssue(CallIssueKind.END_BEFORE_START, AgirFormats.TAG_END))
    }
}
