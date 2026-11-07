package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent

/**
 * The error codes of the server for a mobilization (contract of the Agir space, section 5): the
 * device uses the same ones, so that the form explains exactly what the server would refuse.
 */
enum class MobilizationError(val code: String) {
    /** A required or invalid tag that has no code of its own (the identifier, an invalid optional value); [MobilizationIssue.field] names it. */
    MISSING_FIELD("missing_field"),
    MISSING_TITLE("missing_title"),
    MISSING_START("missing_start"),
    BAD_END("bad_end"),
    MISSING_LOCATION("missing_location"),
    BAD_EVENT_TYPE("bad_event_type"),
    MISSING_TERRITORY("missing_territory"),
    MISSING_CHARTER("missing_charter"),
    TOO_LONG("too_long"),
    ;

    companion object {
        fun fromCode(code: String?): MobilizationError? = entries.firstOrNull { it.code == code }
    }
}

/** One thing that breaks the rules, and the tag it concerns ("title", "content"...). */
data class MobilizationIssue(val error: MobilizationError, val field: String)

/**
 * The rules a mobilization (kind 31923) must follow to be published, shared by the device (before
 * sending) and the server (before storing). [check] lists every issue, in a fixed order; the
 * server answers with the first one ([firstIssue]):
 *
 * 1. `d`: one identifier of 1 to 64 characters `a-z0-9._~-` (`missing_field`, field `d`);
 * 2. `title`: 1 to 140 characters (`missing_title`, then `too_long`);
 * 3. `start`: seconds since 1970 (`missing_start`);
 * 4. `end`: when present, seconds strictly after `start` (`bad_end`);
 * 5. `location`: not blank, 200 characters at most (`missing_location`, then `too_long`);
 * 6. `ppf-event-type`: one of [AgirFormats.EVENT_TYPES] (`bad_event_type`);
 * 7. `ppf-territory`: not blank, 100 characters at most (`missing_territory`, then `too_long`);
 * 8. `ppf-charter` = `1.0` (`missing_charter`);
 * 9. optional values, when present: `ppf-event-status` among [AgirFormats.EVENT_STATUSES],
 *    `ppf-declaration` among [AgirFormats.DECLARATION_STATES], `g` a geohash of 9 characters at
 *    most, `p` a 64-character lowercase hexadecimal key, `r` an http or https link
 *    (`missing_field` with the tag name);
 * 10. `summary` 280 characters at most, `content` 8000 characters at most (`too_long`).
 *
 * The limits of the place (200) and of the summary (280) are device rules proposed to the server
 * in the integration note of the agenda; the others come from the contract.
 */
object MobilizationRules {

    fun check(event: SignedEvent): List<MobilizationIssue> = check(event.kind, event.tags, event.content)

    fun check(event: UnsignedEvent): List<MobilizationIssue> = check(event.kind, event.tags, event.content)

    fun firstIssue(event: SignedEvent): MobilizationIssue? = check(event).firstOrNull()

    fun check(kind: Int, tags: List<List<String>>, content: String): List<MobilizationIssue> {
        require(kind == Mobilization.KIND) { "Only mobilizations (kind ${Mobilization.KIND}) follow these rules" }
        fun first(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)
        fun all(name: String): List<List<String>> = tags.filter { it.size >= 2 && it[0] == name }
        val issues = mutableListOf<MobilizationIssue>()
        fun add(error: MobilizationError, field: String) {
            issues += MobilizationIssue(error, field)
        }

        val identifier = first(Mobilization.TAG_IDENTIFIER)
        if (identifier == null || !Mobilization.isValidIdentifier(identifier)) add(MobilizationError.MISSING_FIELD, Mobilization.TAG_IDENTIFIER)

        val title = first(AgirFormats.TAG_TITLE)?.trim().orEmpty()
        when {
            title.isEmpty() -> add(MobilizationError.MISSING_TITLE, AgirFormats.TAG_TITLE)
            title.length > AgirFormats.MAX_TITLE -> add(MobilizationError.TOO_LONG, AgirFormats.TAG_TITLE)
        }

        val start = first(AgirFormats.TAG_START)?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        if (start == null) add(MobilizationError.MISSING_START, AgirFormats.TAG_START)
        val endText = first(AgirFormats.TAG_END)
        if (endText != null && start != null) {
            val end = endText.trim().toLongOrNull()
            if (end == null || end <= start) add(MobilizationError.BAD_END, AgirFormats.TAG_END)
        }

        val location = first(AgirFormats.TAG_LOCATION)?.trim().orEmpty()
        when {
            location.isEmpty() -> add(MobilizationError.MISSING_LOCATION, AgirFormats.TAG_LOCATION)
            location.length > Mobilization.MAX_LOCATION -> add(MobilizationError.TOO_LONG, AgirFormats.TAG_LOCATION)
        }

        if (first(AgirFormats.TAG_EVENT_TYPE) !in AgirFormats.EVENT_TYPES) add(MobilizationError.BAD_EVENT_TYPE, AgirFormats.TAG_EVENT_TYPE)

        val territory = first(AgirFormats.TAG_TERRITORY)?.trim().orEmpty()
        when {
            territory.isEmpty() -> add(MobilizationError.MISSING_TERRITORY, AgirFormats.TAG_TERRITORY)
            territory.length > Mobilization.MAX_TERRITORY -> add(MobilizationError.TOO_LONG, AgirFormats.TAG_TERRITORY)
        }

        if (first(AgirFormats.TAG_CHARTER) != AgirFormats.CHARTER_VERSION) add(MobilizationError.MISSING_CHARTER, AgirFormats.TAG_CHARTER)

        first(AgirFormats.TAG_EVENT_STATUS)?.let { if (it !in AgirFormats.EVENT_STATUSES) add(MobilizationError.MISSING_FIELD, AgirFormats.TAG_EVENT_STATUS) }
        first(AgirFormats.TAG_DECLARATION)?.let { if (it !in AgirFormats.DECLARATION_STATES) add(MobilizationError.MISSING_FIELD, AgirFormats.TAG_DECLARATION) }
        first(AgirFormats.TAG_GEOHASH)?.let { if (!Mobilization.isValidGeohash(it)) add(MobilizationError.MISSING_FIELD, AgirFormats.TAG_GEOHASH) }
        if (all(AgirFormats.TAG_PUBKEY).any { !Mobilization.isValidKey(it[1]) }) add(MobilizationError.MISSING_FIELD, AgirFormats.TAG_PUBKEY)
        if (all(AgirFormats.TAG_LINK).any { !Mobilization.isValidLink(it[1]) }) add(MobilizationError.MISSING_FIELD, AgirFormats.TAG_LINK)

        if ((first(AgirFormats.TAG_SUMMARY)?.trim()?.length ?: 0) > Mobilization.MAX_SUMMARY) add(MobilizationError.TOO_LONG, AgirFormats.TAG_SUMMARY)
        if (content.length > AgirFormats.MAX_TEXT) add(MobilizationError.TOO_LONG, "content")
        return issues
    }
}
