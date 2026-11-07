package org.politisationpopulairefrancaise.server.agir

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.signing.EventReplacement
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The iCalendar feed of the agenda (RFC 5545), written by hand: a few properties, no dependency.
 * Isolated on purpose: the device has its own export in shared/core (agent "agenda"), and the main
 * session will keep only one of them.
 *
 * - one VEVENT per mobilization: `UID` = its stable address (`31923:<key>:<d>`), so that a calendar
 *   updates the event in place when it is corrected or cancelled; `DTSTAMP` = `created_at` of the
 *   version (its last revision; no METHOD is set), `DTSTART` and `DTEND` in UTC with `Z`, `SUMMARY`
 *   (title), `LOCATION`, `DESCRIPTION` (the text, or the summary when there is no text), `URL` (the
 *   first `r` link, web addresses only), `STATUS:CANCELLED` for a cancelled event;
 * - TEXT values escaped (`\`, `;`, `,`, line breaks as `\n`; other control characters dropped);
 * - lines folded at 75 octets of UTF-8, never inside a character, continued after a space; every line
 *   ends with CRLF.
 */
object AgendaCalendar {
    const val CONTENT_TYPE: String = "text/calendar; charset=utf-8"
    const val DEFAULT_NAME: String = "PPF : agenda des mobilisations"
    const val PRODUCT_ID: String = "-//Politisation Populaire Française//Agenda des mobilisations//FR"

    /** Longest line, line break excluded (RFC 5545, section 3.1). */
    const val MAX_LINE_OCTETS: Int = 75

    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** The calendar of [events] (mobilizations; anything else is skipped), in the order given. */
    fun render(events: List<SignedEvent>, name: String = DEFAULT_NAME): String = buildString {
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:" + escapeText(PRODUCT_ID))
        line("CALSCALE:GREGORIAN")
        line("X-WR-CALNAME:" + escapeText(name))
        for (event in events) appendEvent(event)
        line("END:VCALENDAR")
    }

    private fun StringBuilder.appendEvent(event: SignedEvent) {
        val entry = AgendaIndex.entryOf(event) ?: return
        val uid = EventReplacement.replacementKey(event) ?: return
        line("BEGIN:VEVENT")
        line("UID:" + escapeText(uid))
        line("DTSTAMP:" + utc(event.createdAt))
        line("DTSTART:" + utc(entry.startsAt))
        entry.endsAt?.let { line("DTEND:" + utc(it)) }
        event.firstTagValue(AgirFormats.TAG_TITLE)?.takeIf { it.isNotBlank() }?.let { line("SUMMARY:" + escapeText(it)) }
        event.firstTagValue(AgirFormats.TAG_LOCATION)?.takeIf { it.isNotBlank() }?.let { line("LOCATION:" + escapeText(it)) }
        val description = event.content.takeIf { it.isNotBlank() } ?: event.firstTagValue(AgirFormats.TAG_SUMMARY)?.takeIf { it.isNotBlank() }
        description?.let { line("DESCRIPTION:" + escapeText(it)) }
        event.tagValues(AgirFormats.TAG_LINK).firstOrNull { AgirRules.isWebAddress(it) && it.none(Char::isISOControl) }?.let { line("URL:$it") }
        if (entry.status == STATUS_CANCELLED) line("STATUS:CANCELLED")
        line("END:VEVENT")
    }

    private const val STATUS_CANCELLED = "annule"

    fun utc(seconds: Long): String = UTC.format(Instant.ofEpochSecond(seconds))

    /** A TEXT value (RFC 5545, section 3.3.11): `\`, `;` and `,` escaped, line breaks as `\n`, other control characters dropped. */
    fun escapeText(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '\\' -> append("\\\\")
                char == ';' -> append("\\;")
                char == ',' -> append("\\,")
                char == '\r' -> {
                    append("\\n")
                    if (index + 1 < value.length && value[index + 1] == '\n') index++
                }
                char == '\n' -> append("\\n")
                char == '\t' -> append(char)
                char.isISOControl() -> Unit
                else -> append(char)
            }
            index++
        }
    }

    /**
     * [content] as one or more lines of at most [MAX_LINE_OCTETS] octets of UTF-8 each, line break
     * excluded: a continuation line starts with a space, which counts. A character is never cut.
     */
    fun fold(content: String): String {
        val out = StringBuilder(content.length + content.length / MAX_LINE_OCTETS * 3 + 2)
        var octets = 0
        var index = 0
        while (index < content.length) {
            val codePoint = content.codePointAt(index)
            val count = Character.charCount(codePoint)
            val size = utf8Size(codePoint)
            if (octets + size > MAX_LINE_OCTETS) {
                out.append("\r\n ")
                octets = 1
            }
            out.append(content, index, index + count)
            octets += size
            index += count
        }
        return out.append("\r\n").toString()
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun StringBuilder.line(content: String) {
        append(fold(content))
    }
}
