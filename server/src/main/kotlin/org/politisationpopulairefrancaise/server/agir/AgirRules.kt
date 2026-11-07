package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.revendications.checks.DraftRules
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.events.PublicationPolicy.Decision
import org.politisationpopulairefrancaise.server.groups.GroupKinds
import org.politisationpopulairefrancaise.server.groups.GroupNames
import org.politisationpopulairefrancaise.server.revendications.DemandDrafts
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** The contents of the Agir space that the server treats specially (contract, sections 2 to 4). */
enum class AgirCategory {
    /** Kind 31923 (NIP-52): a mobilization of the agenda. */
    MOBILIZATION,

    /** Kind 30023 with `ppf-type` = appel. */
    CALL,

    /** Kind 30023 with `ppf-type` = organisation: an entry of the directory, editorial keys only. */
    ORGANISATION,

    /**
     * Kind 31925 (NIP-52 calendar event RSVP): the public response of a key to a mobilization
     * (contract, version 2, D-044). Published directly, whoever signs it: it holds no text shown by
     * the application, and the devices show only those of verified organisations and editorial keys.
     */
    PARTICIPATION,
}

/**
 * The shape of the contents of the Agir space (docs/integration/agir-contrat.md, sections 2 to 4),
 * checked without the database, for every key (editors included): the rules of who may publish
 * what live in [AgirSpace]. Every check is linear in the size of the event.
 *
 * Errors (`400`): `missing_title`, `missing_start`, `bad_end`, `missing_location`, `bad_event_type`,
 * `bad_call_type`, `missing_territory`, `missing_charter`, `too_long` and `missing_field`, as the
 * contract names them. For `missing_field` and `too_long`, the message is exactly the name of the tag
 * at fault (`ppf-object`, `location|r`, `content`...), so that a device can point at the field;
 * `missing_field` covers a tag that is absent and one whose value cannot be read (a date that is not
 * a number, a value outside its list). A control character anywhere is `malformed_event`.
 */
object AgirRules {
    /** Longest value of a tag not bounded otherwise, in characters. */
    const val MAX_TAG_VALUE: Int = AgirFormats.MAX_TAG_VALUE

    /** Longest report of a closed call (AP-09). */
    const val MAX_REPORT: Int = AgirFormats.MAX_REPORT

    /** Longest territory: the bound of the rooms, whose territories are the same. */
    const val MAX_TERRITORY: Int = GroupKinds.MAX_TERRITORY_LENGTH

    /** Longest `d` of a mobilization (contract, section 2). */
    const val MAX_MOBILIZATION_ID: Int = 64

    /** Longest `d` of a call or a directory entry: the bound of the demands, whose form they share. */
    const val MAX_ARTICLE_ID: Int = DemandDrafts.MAX_IDENTIFIER_LENGTH

    private val IDENTIFIER = Regex("[a-z0-9._~-]+")

    /** The `d` of a response: the device writes the address of the mobilization, other clients a UUID. */
    private val RESPONSE_IDENTIFIER = Regex("[A-Za-z0-9._~:-]+")
    private val SECONDS = Regex("[0-9]{1,12}")
    private val POSITIVE = Regex("[1-9][0-9]{0,11}")
    private val GEOHASH = Regex("[0123456789bcdefghjkmnpqrstuvwxyz]{1,9}")
    private val DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val HEX_KEY = Regex("[0-9a-f]{64}")

    fun categoryOf(event: SignedEvent): AgirCategory? = when {
        event.kind == AgirFormats.KIND_MOBILIZATION -> AgirCategory.MOBILIZATION
        event.kind == AgirFormats.KIND_PARTICIPATION -> AgirCategory.PARTICIPATION
        event.kind != Article.KIND -> null
        else -> when (event.firstTagValue(AgirFormats.TAG_TYPE)) {
            AgirFormats.TYPE_CALL -> AgirCategory.CALL
            AgirFormats.TYPE_ORGANISATION -> AgirCategory.ORGANISATION
            else -> null
        }
    }

    fun isCall(event: SignedEvent): Boolean = categoryOf(event) == AgirCategory.CALL

    /** The `ppf-call-type` of a call, null for anything else. */
    fun callTypeOf(event: SignedEvent): String? = if (isCall(event)) event.firstTagValue(AgirFormats.TAG_CALL_TYPE) else null

    /** The first shape error of an Agir content; null when it is well-formed or not an Agir content. */
    fun check(event: SignedEvent): Decision.Refused? = when (categoryOf(event)) {
        AgirCategory.MOBILIZATION -> checkMobilization(event)
        AgirCategory.CALL -> checkCall(event)
        AgirCategory.ORGANISATION -> checkOrganisation(event)
        AgirCategory.PARTICIPATION -> checkParticipation(event)
        null -> null
    }

    /**
     * A public response to a mobilization (contract, version 2): exactly one `d` (1 to
     * [AgirFormats.MAX_PARTICIPATION_ID] characters, letters, digits and `._~:-`, so that the address
     * of the mobilization fits), exactly one `a` naming a mobilization (`31923:<key>:<d>`), a known
     * `status`, an optional known `ppf-role`, optional `p` (the author of the mobilization named in
     * `a`), `e` (an event id) and `fb` (`free` or `busy`), a note of 280 characters at most without
     * any personal contact detail. Errors: `missing_field` with the name of the tag, `too_long`,
     * `personal_data` (`content`), `malformed_event`.
     */
    private fun checkParticipation(event: SignedEvent): Decision.Refused? {
        if (event.tags.any { tag -> tag.any { !DemandDrafts.isPlainText(it, multiline = false) } } || !DemandDrafts.isPlainText(event.content, multiline = true)) {
            return refuse("malformed_event", "A response holds plain text only: no control character.")
        }
        val identifiers = event.tagValues("d")
        if (identifiers.size != 1 || identifiers[0].length > AgirFormats.MAX_PARTICIPATION_ID || !RESPONSE_IDENTIFIER.matches(identifiers[0])) return missing("d")
        val targets = event.tagValues(AgirFormats.TAG_ADDRESS)
        val target = targets.singleOrNull()?.let { runCatching { ContentAddress.parse(it) }.getOrNull() }
        if (target == null || target.kind != AgirFormats.KIND_MOBILIZATION || target.identifier.length > MAX_MOBILIZATION_ID || !IDENTIFIER.matches(target.identifier)) {
            return missing(AgirFormats.TAG_ADDRESS)
        }
        if (event.firstTagValue(AgirFormats.TAG_STATUS) !in AgirFormats.PARTICIPATION_STATUSES) return missing(AgirFormats.TAG_STATUS)
        oneOf(event, AgirFormats.TAG_ROLE, AgirFormats.PARTICIPATION_ROLES)?.let { return it }
        oneOf(event, AgirFormats.TAG_FREE_BUSY, AgirFormats.FREE_BUSY)?.let { return it }
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                AgirFormats.TAG_PUBKEY -> if (tag[1] != target.pubkey) return missing(AgirFormats.TAG_PUBKEY)
                AgirFormats.TAG_EVENT_ID -> if (!HEX_KEY.matches(tag[1])) return missing(AgirFormats.TAG_EVENT_ID)
            }
        }
        if (event.content.length > AgirFormats.MAX_PARTICIPATION_NOTE) return tooLong("content")
        for (tag in event.tags) {
            val name = tag.firstOrNull() ?: continue
            if (tag.drop(1).any { it.length > MAX_TAG_VALUE }) return tooLong(name)
        }
        if (DraftRules.containsPersonalData(event.content)) return refuse("personal_data", "content")
        return null
    }

    private fun checkMobilization(event: SignedEvent): Decision.Refused? {
        common(event, MAX_MOBILIZATION_ID)?.let { return it }
        val start = event.firstTagValue(AgirFormats.TAG_START)?.takeIf(SECONDS::matches)?.toLong()
            ?: return refuse("missing_start", "A mobilization carries its start in a start tag, in seconds since 1970.")
        endAfter(event, start)?.let { return it }
        if (!hasText(event, AgirFormats.TAG_LOCATION)) return refuse("missing_location", "A mobilization names its place in a location tag.")
        if (event.firstTagValue(AgirFormats.TAG_EVENT_TYPE) !in AgirFormats.EVENT_TYPES) {
            return refuse("bad_event_type", "The ppf-event-type tag is one of ${AgirFormats.EVENT_TYPES.joinToString()}.")
        }
        territoryAndCharter(event)?.let { return it }
        oneOf(event, AgirFormats.TAG_EVENT_STATUS, AgirFormats.EVENT_STATUSES)?.let { return it }
        oneOf(event, AgirFormats.TAG_DECLARATION, AgirFormats.DECLARATION_STATES)?.let { return it }
        timeZone(event)?.let { return it }
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                AgirFormats.TAG_PUBKEY -> if (!HEX_KEY.matches(tag[1])) return missing(AgirFormats.TAG_PUBKEY)
                AgirFormats.TAG_ADDRESS -> if (!isCallAddress(tag[1])) return missing(AgirFormats.TAG_ADDRESS)
            }
        }
        return null
    }

    private fun checkCall(event: SignedEvent): Decision.Refused? {
        common(event, MAX_ARTICLE_ID)?.let { return it }
        val callType = event.firstTagValue(AgirFormats.TAG_CALL_TYPE)
        if (callType !in AgirFormats.CALL_TYPES) return refuse("bad_call_type", "The ppf-call-type tag is one of ${AgirFormats.CALL_TYPES.joinToString()}.")
        territoryAndCharter(event)?.let { return it }
        if (event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.let(SECONDS::matches) != true) return missing(AgirFormats.TAG_PUBLISHED_AT)
        // AP-10: the fields of the type, the first one missing named back ("a|b" means one of the two).
        for (required in AgirFormats.CALL_REQUIRED_TAGS[callType].orEmpty()) {
            if (required.split('|').none { hasText(event, it) }) return missing(required)
        }
        val start = event.firstTagValue(AgirFormats.TAG_START)?.let { value ->
            value.takeIf(SECONDS::matches)?.toLong() ?: return missing(AgirFormats.TAG_START)
        }
        if (start != null) {
            endAfter(event, start)?.let { return it }
        } else if (event.firstTagValue(AgirFormats.TAG_END)?.let(SECONDS::matches) == false) {
            return refuse("bad_end", "The end tag is a date in seconds since 1970, after the start.")
        }
        event.firstTagValue(AgirFormats.TAG_DEADLINE)?.let { if (!SECONDS.matches(it)) return missing(AgirFormats.TAG_DEADLINE) }
        event.firstTagValue(AgirFormats.TAG_GOAL)?.let { if (!POSITIVE.matches(it)) return missing(AgirFormats.TAG_GOAL) }
        oneOf(event, AgirFormats.TAG_CALL_STATUS, AgirFormats.CALL_STATUSES)?.let { return it }
        oneOf(event, AgirFormats.TAG_DECLARATION, AgirFormats.DECLARATION_STATES)?.let { return it }
        oneOf(event, TAG_PLATFORM, AgirFormats.PETITION_PLATFORMS)?.let { return it }
        return null
    }

    private fun checkOrganisation(event: SignedEvent): Decision.Refused? {
        common(event, MAX_ARTICLE_ID)?.let { return it }
        if (!hasText(event, AgirFormats.TAG_SUMMARY)) return missing(AgirFormats.TAG_SUMMARY)
        if (event.firstTagValue(AgirFormats.TAG_ORG_TYPE) !in AgirFormats.ORG_TYPES) return missing(AgirFormats.TAG_ORG_TYPE)
        if (!hasTerritory(event)) return missingTerritory()
        if (event.firstTagValue(AgirFormats.TAG_VERIFIED)?.let(::isDay) != true) return missing(AgirFormats.TAG_VERIFIED)
        if (event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.let(SECONDS::matches) != true) return missing(AgirFormats.TAG_PUBLISHED_AT)
        for (tag in event.tags) {
            if (tag.size >= 2 && tag[0] == AgirFormats.TAG_PUBKEY && !HEX_KEY.matches(tag[1])) return missing(AgirFormats.TAG_PUBKEY)
            // Contract, version 2: the status is optional, but a value the readers do not know is refused.
            if (tag.isNotEmpty() && tag[0] == AgirFormats.TAG_ORG_STATUS && (tag.size < 2 || tag[1] !in AgirFormats.ORG_STATUSES)) return missing(AgirFormats.TAG_ORG_STATUS)
        }
        return null
    }

    /**
     * What every content of the space shares: plain text, bounded lengths, exactly one valid `d`, a
     * title, a readable geohash and links that are web addresses.
     */
    private fun common(event: SignedEvent, maxIdentifier: Int): Decision.Refused? {
        // No control character (the database refuses some of them) nor broken character anywhere.
        if (event.tags.any { tag -> tag.any { !DemandDrafts.isPlainText(it, multiline = false) } } || !DemandDrafts.isPlainText(event.content, multiline = true)) {
            return refuse("malformed_event", "A content of the Agir space holds plain text only: no control character.")
        }
        val identifiers = event.tagValues("d")
        if (identifiers.size != 1 || identifiers[0].length > maxIdentifier || !IDENTIFIER.matches(identifiers[0])) return missing("d")
        if (!hasText(event, AgirFormats.TAG_TITLE)) return refuse("missing_title", "A content of the Agir space carries a title tag.")
        lengths(event)?.let { return it }
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                AgirFormats.TAG_GEOHASH -> if (!GEOHASH.matches(tag[1])) return missing(AgirFormats.TAG_GEOHASH)
                AgirFormats.TAG_LINK -> if (!isWebAddress(tag[1])) return missing(AgirFormats.TAG_LINK)
            }
        }
        personalData(event)?.let { return it }
        return null
    }

    /** Tags that never hold free text: links, keys, addresses, places on the map, dates and fixed values. */
    private val NOT_FREE_TEXT = setOf(
        "d", AgirFormats.TAG_LINK, AgirFormats.TAG_PUBKEY, AgirFormats.TAG_ADDRESS, AgirFormats.TAG_GEOHASH,
        AgirFormats.TAG_START, AgirFormats.TAG_END, AgirFormats.TAG_START_TZID, AgirFormats.TAG_PUBLISHED_AT,
        AgirFormats.TAG_DEADLINE, AgirFormats.TAG_GOAL, AgirFormats.TAG_TYPE, AgirFormats.TAG_CALL_TYPE,
        AgirFormats.TAG_CALL_STATUS, AgirFormats.TAG_CHARTER,
    )

    /**
     * No personal contact detail in a call (an e-mail address, a French phone number, an IBAN, a
     * social security number), as the form of the application checks it (CallRules, the patterns of
     * DraftRules): a verified organisation publishes without review, so the server checks again.
     * The detail found is never kept nor sent back; the message names the field.
     */
    private fun personalData(event: SignedEvent): Decision.Refused? {
        if (DraftRules.containsPersonalData(event.content)) return refuse("personal_data", "content")
        for (tag in event.tags) {
            val name = tag.firstOrNull() ?: continue
            if (name in NOT_FREE_TEXT) continue
            if (tag.drop(1).any { DraftRules.containsPersonalData(it) }) return refuse("personal_data", name)
        }
        return null
    }

    private fun lengths(event: SignedEvent): Decision.Refused? {
        if (event.content.length > AgirFormats.MAX_TEXT) return tooLong("content")
        for (tag in event.tags) {
            val name = tag.firstOrNull() ?: continue
            val limit = when (name) {
                AgirFormats.TAG_TITLE -> AgirFormats.MAX_TITLE
                AgirFormats.TAG_TERRITORY -> MAX_TERRITORY
                AgirFormats.TAG_REPORT -> MAX_REPORT
                else -> MAX_TAG_VALUE
            }
            if (tag.drop(1).any { it.length > limit }) return tooLong(name)
        }
        return null
    }

    private fun territoryAndCharter(event: SignedEvent): Decision.Refused? {
        if (!hasTerritory(event)) return missingTerritory()
        if (event.firstTagValue(AgirFormats.TAG_CHARTER) != AgirFormats.CHARTER_VERSION) {
            return refuse("missing_charter", "Publishing means accepting the charter of values: a ${AgirFormats.TAG_CHARTER} tag with the value ${AgirFormats.CHARTER_VERSION}.")
        }
        return null
    }

    private fun endAfter(event: SignedEvent, start: Long): Decision.Refused? {
        val end = event.firstTagValue(AgirFormats.TAG_END) ?: return null
        return if (SECONDS.matches(end) && end.toLong() > start) null else refuse("bad_end", "The end tag is a date in seconds since 1970, after the start.")
    }

    private fun oneOf(event: SignedEvent, tag: String, values: List<String>): Decision.Refused? {
        val value = event.firstTagValue(tag) ?: return null
        return if (value in values) null else missing(tag)
    }

    /**
     * The contract writes `Europe/Paris`; a region of the IANA database (`America/Guadeloupe`,
     * `Indian/Reunion`...) is accepted too, for the overseas departments. Only a display hint: `start`
     * is absolute.
     */
    private fun timeZone(event: SignedEvent): Decision.Refused? {
        val zone = event.firstTagValue(AgirFormats.TAG_START_TZID) ?: return null
        val valid = zone.contains('/') && try {
            ZoneId.of(zone).id == zone
        } catch (e: DateTimeException) {
            false
        }
        return if (valid) null else missing(AgirFormats.TAG_START_TZID)
    }

    private fun isCallAddress(value: String): Boolean = runCatching { ContentAddress.parse(value) }.getOrNull()?.kind == Article.KIND

    /** An absolute http(s) address, without space: the only links a page or a calendar may open. */
    fun isWebAddress(value: String): Boolean =
        (value.startsWith("https://") || value.startsWith("http://")) && value.length > "https://".length && value.none { it.isWhitespace() }

    private fun isDay(value: String): Boolean = DAY.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess

    private fun hasText(event: SignedEvent, tag: String): Boolean = event.firstTagValue(tag)?.isNotBlank() == true

    private fun hasTerritory(event: SignedEvent): Boolean =
        event.firstTagValue(AgirFormats.TAG_TERRITORY)?.let { GroupNames.normalize(it).isNotEmpty() } == true

    private fun missingTerritory() =
        refuse("missing_territory", "A content of the Agir space names its territory in a ${AgirFormats.TAG_TERRITORY} tag (a town, a department, \"France\").")

    private fun missing(tag: String) = refuse("missing_field", tag)

    private fun tooLong(field: String) = refuse("too_long", field)

    private fun refuse(error: String, message: String) = Decision.Refused(400, error, message)

    /** `ppf-platform` of a petition (AgirFormats.PETITION_PLATFORMS). */
    const val TAG_PLATFORM: String = "ppf-platform"
}
