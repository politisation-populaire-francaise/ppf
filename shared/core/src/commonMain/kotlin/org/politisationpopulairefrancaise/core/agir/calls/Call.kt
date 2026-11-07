package org.politisationpopulairefrancaise.core.agir.calls

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import kotlin.random.Random

/**
 * A call (AP-01 to AP-10) read from its signed event: kind 30023, `ppf-type` = `appel`, addressable
 * by `30023:<key>:<d>` (contract of the Agir space, section 3). Everything a person may want to
 * propose to others: meet, debate, demonstrate, sign, help, look for witnesses...
 *
 * The answers of other people are never part of it: they stay on their devices (section 6).
 */
data class Call(
    val event: SignedEvent,
    val address: ContentAddress,
    val type: CallType,
    val title: String,
    val summary: String?,
    /** The text of the call (plain text, 8000 characters at most). */
    val text: String,
    val territory: String,
    val status: CallStatus,
    /** The figure aimed at (signatures, people, euros...), when the author gave one. */
    val goal: Long?,
    /** Seconds since 1970. */
    val deadline: Long?,
    /** Start of what the call announces or of the facts looked into (witnesses), seconds since 1970. */
    val start: Long?,
    val end: Long?,
    val location: String?,
    /** The `r` links, in order. */
    val links: List<String>,
    /** The report of a closed call (AP-09). */
    val report: String?,
    /** First publication, seconds since 1970. */
    val publishedAt: Long?,
    /** The specific tags of the type (AP-10) and their first value, in the order of the event. */
    val fields: Map<String, String>,
) {
    val slug: String get() = address.identifier
    val author: String get() = event.pubkey
    val updatedAt: Long get() = event.createdAt
    val isOpen: Boolean get() = status == CallStatus.OPEN

    /** Example contents carry the [EXEMPLE] marker in the title. */
    val isExample: Boolean get() = title.startsWith(EXAMPLE_MARKER)

    /** The title without the example marker, which the screens show as a badge. */
    val displayTitle: String get() = title.removePrefix(EXAMPLE_MARKER).trim()

    /**
     * The date that matters for the filters and the countdown: the deadline, else the start (a
     * meeting or a demonstration is due when it begins). Null for a call without either.
     */
    val dueAt: Long? get() = deadline ?: start

    /**
     * When the call stops calling for answers: its closing (the date of the closed version), its
     * deadline, or else the end, or the start, of what it announces, whichever comes first. A call
     * for witnesses dates facts already past: only its deadline and its closing count. Null for an
     * open call without any date: its answers stay until the user withdraws them.
     */
    val answersEndAt: Long?
        get() {
            val closedAt = if (isOpen) null else updatedAt
            val due = deadline ?: if (type == CallType.TEMOINS) null else (end ?: start)
            return listOfNotNull(closedAt, due).minOrNull()
        }

    /** The tags of AP-10 this call lacks (a call published by an older or modified application). */
    fun missingRequirements(): List<CallRequirement> = type.requirements.filter { requirement ->
        requirement.alternatives.none { tag -> event.firstTagValue(tag)?.isNotBlank() == true }
    }

    /**
     * The new version of this call that closes it with its [report] (AP-09): same address, same
     * tags, `ppf-call-status` = `clos` and `ppf-report`. Dated [now], or one second after this
     * version if the clock is behind, so that it replaces it everywhere.
     */
    fun closedVersion(report: String, now: Long): UnsignedEvent {
        val text = report.trim()
        require(text.length <= CallRules.REPORT_MAX) { "A report has at most ${CallRules.REPORT_MAX} characters" }
        val kept = event.tags.filter { it.isNotEmpty() && it[0] != AgirFormats.TAG_CALL_STATUS && it[0] != AgirFormats.TAG_REPORT }
        val tags = kept + listOf(listOf(AgirFormats.TAG_CALL_STATUS, CallStatus.CLOSED.tagValue)) +
            (if (text.isNotEmpty()) listOf(listOf(AgirFormats.TAG_REPORT, text)) else emptyList())
        return UnsignedEvent(event.pubkey, maxOf(now, event.createdAt + 1), event.kind, tags, event.content)
    }

    companion object {
        const val KIND: Int = EventKinds.LONG_FORM_ARTICLE
        const val EXAMPLE_MARKER: String = "[EXEMPLE]"

        /** True when [event] says it is a call (whatever its shape). */
        fun isCall(event: SignedEvent): Boolean = event.kind == KIND && event.firstTagValue(AgirFormats.TAG_TYPE) == AgirFormats.TYPE_CALL

        /** Reads a call; null when the event is not a well-formed call (no title, unknown type, no territory). */
        fun from(event: SignedEvent): Call? {
            if (!isCall(event)) return null
            val address = ContentAddress.of(event) ?: return null
            val title = event.firstTagValue(AgirFormats.TAG_TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val type = event.firstTagValue(AgirFormats.TAG_CALL_TYPE)?.let(CallType::fromTag) ?: return null
            val territory = event.firstTagValue(AgirFormats.TAG_TERRITORY)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val fields = LinkedHashMap<String, String>()
            event.tags.forEach { tag ->
                if (tag.size >= 2 && tag[0] in CallFields.SPECIFIC_TAGS && tag[0] !in fields && tag[1].isNotBlank()) fields[tag[0]] = tag[1].trim()
            }
            return Call(
                event = event,
                address = address,
                type = type,
                title = title,
                summary = event.firstTagValue(AgirFormats.TAG_SUMMARY)?.trim()?.takeIf { it.isNotEmpty() },
                text = event.content.trim(),
                territory = territory,
                status = CallStatus.fromTag(event.firstTagValue(AgirFormats.TAG_CALL_STATUS)),
                goal = event.firstTagValue(AgirFormats.TAG_GOAL)?.toLongOrNull()?.takeIf { it > 0 },
                deadline = event.firstTagValue(AgirFormats.TAG_DEADLINE)?.toLongOrNull(),
                start = event.firstTagValue(AgirFormats.TAG_START)?.toLongOrNull(),
                end = event.firstTagValue(AgirFormats.TAG_END)?.toLongOrNull(),
                location = event.firstTagValue(AgirFormats.TAG_LOCATION)?.trim()?.takeIf { it.isNotEmpty() },
                links = event.tagValues(AgirFormats.TAG_LINK).map { it.trim() }.filter { it.isNotEmpty() },
                report = event.firstTagValue(AgirFormats.TAG_REPORT)?.trim()?.takeIf { it.isNotEmpty() },
                publishedAt = event.firstTagValue(AgirFormats.TAG_PUBLISHED_AT)?.toLongOrNull(),
                fields = fields,
            )
        }
    }
}

/** A date and an hour as the author typed them, in Paris time ("14/11/2026", "18:30"). */
data class DateTimeText(val date: String = "", val time: String = "") {
    val isBlank: Boolean get() = date.isBlank() && time.isBlank()

    /**
     * The instant, or null when the date or the hour cannot be read. Without an hour, [defaultTime]
     * applies when given (a deadline without an hour lasts the whole day); otherwise the hour is required.
     */
    fun toEpochSeconds(defaultTime: Pair<Int, Int>? = null): Long? {
        val (year, month, day) = ParisTime.parseDate(date) ?: return null
        val (hour, minute) = if (time.isBlank()) defaultTime ?: return null else ParisTime.parseTime(time) ?: return null
        val moment = ParisTime.moment(year, month, day, hour, minute) ?: return null
        return ParisTime.toEpochSeconds(moment)
    }

    companion object {
        /** The text of an instant, for a form opened on an existing value. */
        fun of(epochSeconds: Long): DateTimeText {
            val moment = ParisTime.of(epochSeconds)
            return DateTimeText(ParisTime.formatDate(moment), ParisTime.formatTime(moment))
        }
    }
}

/**
 * A call being written (AP-01). Nothing leaves the device before the author sends it; the draft
 * itself lives in memory only, like a message being typed.
 */
data class CallDraft(
    val type: CallType? = null,
    val title: String = "",
    val text: String = "",
    val summary: String = "",
    val territory: String = "",
    /** The goal as typed (digits). */
    val goal: String = "",
    val location: String = "",
    val link: String = "",
    /** The dates as typed, by tag: `ppf-deadline`, `start`, `end`. */
    val dates: Map<String, DateTimeText> = emptyMap(),
    /** The specific fields as typed, by tag (AP-10). */
    val fields: Map<String, String> = emptyMap(),
    /** Fundraising only: the author pledges that it is not for a candidate nor a party (AP-08). */
    val notForCandidateOrParty: Boolean = false,
) {
    fun date(tag: String): DateTimeText = dates[tag] ?: DateTimeText()

    fun field(tag: String): String = fields[tag].orEmpty()

    fun withDate(tag: String, value: DateTimeText): CallDraft = copy(dates = dates + (tag to value))

    fun withField(tag: String, value: String): CallDraft = copy(fields = fields + (tag to value))

    /** The instant of a date field; a deadline without an hour lasts until 23:59. */
    fun instant(tag: String): Long? {
        val text = dates[tag] ?: return null
        if (text.isBlank) return null
        return text.toEpochSeconds(if (tag == AgirFormats.TAG_DEADLINE) END_OF_DAY else null)
    }

    /** True when the author filled the tag [tag] (common or specific, dates read). */
    fun has(tag: String): Boolean = when (tag) {
        AgirFormats.TAG_LOCATION -> location.isNotBlank()
        AgirFormats.TAG_LINK -> link.isNotBlank()
        AgirFormats.TAG_GOAL -> goal.isNotBlank()
        in CallFields.DATE_TAGS -> instant(tag) != null
        CallFields.REMOTE -> field(tag) == CallFields.YES
        else -> field(tag).isNotBlank()
    }

    /** Every free text of the draft, by field name (the tag, or "title", "text", "summary", "territory"), for the checks. */
    fun texts(): Map<String, String> = buildMap {
        put(FIELD_TITLE, title)
        put(FIELD_TEXT, text)
        put(FIELD_SUMMARY, summary)
        put(FIELD_TERRITORY, territory)
        put(AgirFormats.TAG_LOCATION, location)
        put(AgirFormats.TAG_LINK, link)
        type?.let { CallFields.specific(it) }.orEmpty()
            .filter { it.kind == CallFieldKind.TEXT || it.kind == CallFieldKind.LONG_TEXT }
            .forEach { put(it.tag, field(it.tag)) }
    }

    /**
     * The event to sign. Only the fields of the chosen type are written; the status is "ouvert".
     * [slug] defaults to the folded first words of the title and a random suffix, so that two
     * calls of the same author never share an address.
     */
    fun toUnsignedEvent(author: PublicKey, now: Long, slug: String = slugFor(title)): UnsignedEvent {
        val type = requireNotNull(type) { "A call needs a type" }
        require(title.isNotBlank()) { "A call needs a title" }
        // Start and end belong to the types that ask for them; a date typed under another type is dropped.
        val start = if (type.requires(AgirFormats.TAG_START)) instant(AgirFormats.TAG_START) else null
        val end = if (type.requires(AgirFormats.TAG_END)) instant(AgirFormats.TAG_END) else null
        val tags = buildList {
            add(listOf(CallFields.TAG_D, slug))
            add(listOf(AgirFormats.TAG_TITLE, title.trim()))
            add(listOf(AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL))
            add(listOf(AgirFormats.TAG_CALL_TYPE, type.tagValue))
            add(listOf(AgirFormats.TAG_TERRITORY, territory.trim()))
            add(listOf(AgirFormats.TAG_CHARTER, AgirFormats.CHARTER_VERSION))
            add(listOf(CallFields.TAG_ORIGIN, CallFields.ORIGIN_HUMAN))
            add(listOf(AgirFormats.TAG_PUBLISHED_AT, now.toString()))
            add(listOf(AgirFormats.TAG_CALL_STATUS, CallStatus.OPEN.tagValue))
            summary.trim().takeIf { it.isNotEmpty() }?.let { add(listOf(AgirFormats.TAG_SUMMARY, it)) }
            goal.trim().takeIf { it.isNotEmpty() }?.let { add(listOf(AgirFormats.TAG_GOAL, it)) }
            instant(AgirFormats.TAG_DEADLINE)?.let { add(listOf(AgirFormats.TAG_DEADLINE, it.toString())) }
            if (start != null) {
                add(listOf(AgirFormats.TAG_START, start.toString()))
                end?.let { add(listOf(AgirFormats.TAG_END, it.toString())) }
                add(listOf(AgirFormats.TAG_START_TZID, AgirFormats.TIME_ZONE))
            }
            location.trim().takeIf { it.isNotEmpty() }?.let { add(listOf(AgirFormats.TAG_LOCATION, it)) }
            link.trim().takeIf { it.isNotEmpty() }?.let { add(listOf(AgirFormats.TAG_LINK, it)) }
            CallFields.specific(type).filter { it.kind != CallFieldKind.DATE_TIME }.forEach { spec ->
                val value = fields[spec.tag].orEmpty().trim()
                if (value.isNotEmpty()) add(listOf(spec.tag, value))
            }
        }
        return UnsignedEvent.create(author, Call.KIND, now, tags, text.trim())
    }

    companion object {
        const val FIELD_TYPE: String = "type"
        const val FIELD_TITLE: String = "title"
        const val FIELD_TEXT: String = "text"
        const val FIELD_SUMMARY: String = "summary"
        const val FIELD_TERRITORY: String = "territory"
        const val FIELD_PLEDGE: String = "pledge"

        /** A deadline typed without an hour ends with the day. */
        val END_OF_DAY: Pair<Int, Int> = 23 to 59

        /** "Assemblée de quartier sur les loyers" gives something like "assemblee-quartier-loyers-3f9a" (64 characters at most). */
        fun slugFor(title: String, random: Random = Random.Default): String {
            val words = FrenchText.words(title.removePrefix(Call.EXAMPLE_MARKER)).filter { it !in FrenchText.STOPWORDS }.take(6)
            val base = words.joinToString("-").filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(55).trimEnd('-').ifEmpty { "appel" }
            val suffix = random.nextInt(0x10000).toString(16).padStart(4, '0')
            return "$base-$suffix"
        }
    }
}
