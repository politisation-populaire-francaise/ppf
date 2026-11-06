package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.time.CivilDate

/**
 * Export of mobilizations as an iCalendar file (RFC 5545), so that anyone can add them to the
 * calendar application of their choice (M1: "Export calendrier (iCal)"). Pure Kotlin, the same
 * on every platform, and done on the device: nothing is sent anywhere to build the file.
 *
 * Each mobilization is one `VEVENT`: `UID` = its address (`31923:<pubkey>:<d>`, the same for every
 * version, so that a calendar replaces an imported event instead of doubling it), `DTSTAMP` = the
 * date of the version, `DTSTART` and `DTEND` in UTC (suffix `Z`; no `DTEND` when the author gave
 * no end), `SUMMARY` (the title, [EXEMPLE] marker included), `LOCATION`, `DESCRIPTION` (summary
 * then description), `URL` (the first link), `STATUS` (`CONFIRMED`, `TENTATIVE` when postponed,
 * `CANCELLED` when cancelled). Text values escape `\`, `;`, `,` and line breaks; lines are folded
 * at 75 octets of UTF-8 without cutting a character; every line ends with CRLF.
 */
object ICalendar {
    const val MEDIA_TYPE: String = "text/calendar"
    const val PRODUCT_ID: String = "-//Politisation Populaire Francaise//Agenda des mobilisations//FR"
    private const val MAX_OCTETS = 75
    private const val CRLF = "\r\n"

    fun export(mobilizations: List<Mobilization>): String = buildString {
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:$PRODUCT_ID")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        mobilizations.forEach { event(it) }
        line("END:VCALENDAR")
    }

    fun export(mobilization: Mobilization): String = export(listOf(mobilization))

    /** A file name made of the event's identifier: lowercase letters, digits and dashes, ".ics". */
    fun fileName(mobilization: Mobilization): String {
        val base = mobilization.identifier.map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
            .split('-').filter { it.isNotEmpty() }.joinToString("-").take(60).trim('-')
        return (base.ifEmpty { "evenement" }) + ".ics"
    }

    private fun StringBuilder.event(m: Mobilization) {
        line("BEGIN:VEVENT")
        line("UID:" + escape(m.address.toString()))
        line("DTSTAMP:" + utc(m.updatedAt))
        line("DTSTART:" + utc(m.start))
        m.end?.let { line("DTEND:" + utc(it)) }
        line("SUMMARY:" + escape(m.title))
        line("LOCATION:" + escape(m.location))
        val description = listOfNotNull(m.summary, m.description.trim().takeIf { it.isNotEmpty() }).joinToString("\n\n")
        if (description.isNotEmpty()) line("DESCRIPTION:" + escape(description))
        m.links.firstOrNull()?.let { line("URL:$it") }
        val status = when (m.status) {
            MobilizationStatus.PREVU -> "CONFIRMED"
            MobilizationStatus.REPORTE -> "TENTATIVE"
            MobilizationStatus.ANNULE -> "CANCELLED"
        }
        line("STATUS:$status")
        line("END:VEVENT")
    }

    private fun StringBuilder.line(content: String) {
        append(fold(content)).append(CRLF)
    }

    /** `20261114T130000Z`: a UTC date and time of RFC 5545 (form 2). */
    fun utc(epochSeconds: Long): String {
        val date = CivilDate.fromEpochSeconds(epochSeconds)
        val second = epochSeconds.mod(86_400L)
        fun two(value: Long) = value.toString().padStart(2, '0')
        return date.year.toString().padStart(4, '0') + two(date.month.toLong()) + two(date.day.toLong()) +
            "T" + two(second / 3_600) + two(second % 3_600 / 60) + two(second % 60) + "Z"
    }

    /**
     * A TEXT value (RFC 5545, section 3.3.11): backslash, semicolon and comma escaped by a
     * backslash, every line break written `\n`, other control characters (except tab) removed.
     */
    fun escape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val char = text[i]
            when {
                char == '\\' -> append("\\\\")
                char == ';' -> append("\\;")
                char == ',' -> append("\\,")
                char == '\r' -> {
                    append("\\n")
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                }
                char == '\n' -> append("\\n")
                char == '\t' -> append(char)
                char.code < 0x20 || char.code == 0x7f -> Unit
                else -> append(char)
            }
            i++
        }
    }

    /**
     * Folds a content line (RFC 5545, section 3.1): at most 75 octets of UTF-8 per line, the
     * continuation lines starting with one space (which counts), never inside a character (a
     * surrogate pair stays together).
     */
    fun fold(line: String): String {
        val out = StringBuilder(line.length + 8)
        var octets = 0
        var i = 0
        while (i < line.length) {
            val pair = line[i].isHighSurrogate() && i + 1 < line.length && line[i + 1].isLowSurrogate()
            val length = if (pair) 2 else 1
            val size = if (pair) 4 else utf8Size(line[i])
            if (octets + size > MAX_OCTETS) {
                out.append(CRLF).append(' ')
                octets = 1
            }
            out.append(line, i, i + length)
            octets += size
            i += length
        }
        return out.toString()
    }

    private fun utf8Size(char: Char): Int = when {
        char.code < 0x80 -> 1
        char.code < 0x800 -> 2
        else -> 3 // a lone surrogate is written as the replacement character, 3 octets
    }
}
