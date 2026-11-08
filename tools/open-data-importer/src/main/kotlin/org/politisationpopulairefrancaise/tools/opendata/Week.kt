package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.AgendaStatus
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndex
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeek
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekPackage
import org.politisationpopulairefrancaise.core.voir.parlement.SittingKind
import org.politisationpopulairefrancaise.core.voir.parlement.WeekItem
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.isRegularFile

/*
 * « Cette semaine au Parlement » (VO-31): the package `semaine`, built at each import from the
 * agenda of the Assemblée nationale (`Agenda.json.zip`), the iCalendar feed of the Sénat's sitting
 * and the foreseen activities of the European Parliament's plenary, from the day of the import to
 * the fourteenth day after, in the time of Paris. Only the public agenda is kept: never the
 * participants, the organisers, the contacts or the attendees of a meeting.
 *
 * Hooked into [Importer] by two lines (the package joins the files of the import, and a previous
 * `semaine` is not kept twice). A source of the week that cannot be read or downloaded never
 * blocks the import of members and ballots: its chamber keeps the items of the previous package.
 */

/** Where the agenda of each chamber is read: the example files or the institutions. */
interface WeekSources : AutoCloseable {
    /** `Agenda.json.zip`: `json/reunion/RU….json`. */
    fun anAgenda(): EntrySource

    /** The organs of the Assemblée (`json/organe/PO….json`), to name the committees; asked only when one is needed. */
    fun anOrgans(): EntrySource

    /** The iCalendar feed of the Sénat's plenary sitting. */
    fun senatCalendar(): InputStream

    /** The European Parliament's API, as [JsonEuroparlApi] reads it: a relative path to an answer, null for none. */
    fun europarl(path: String): InputStream?

    override fun close() {}
}

/** The fictional files of `example/semaine/`, in the formats of the official ones. */
class ExampleWeekSources : WeekSources {
    override fun anAgenda(): EntrySource = EntrySource { action ->
        ExampleFiles.open("semaine/an-agenda.txt").use { it.readBytes().decodeToString() }.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { name ->
            action(SourceEntry("json/reunion/$name") { ExampleFiles.open("semaine/an/reunion/$name") })
        }
    }

    override fun anOrgans(): EntrySource = ResourceEntries("an/amo10") { it.startsWith("json/organe/") }

    override fun senatCalendar(): InputStream = ExampleFiles.open("semaine/senat/ical.ics")

    override fun europarl(path: String): InputStream? {
        val base = path.substringBefore('?')
        val year = Regex("year=(\\d{4})").find(path)?.groupValues?.get(1)
        val file = "semaine/pe/" + (if (year != null) "$base-$year.json" else "$base.json")
        return if (ExampleFiles.exists(file)) ExampleFiles.open(file) else null
    }
}

/** The files of the institutions, downloaded by [Downloader] (allowed hosts only, HTTPS, no redirection). */
class OfficialWeekSources(private val http: Downloader = Downloader()) : WeekSources {
    private val folder: Path = Files.createTempDirectory("ppf-week-")

    override fun anAgenda(): EntrySource = ZipEntries(http.download(AGENDA, folder.resolve("agenda.zip")))
    override fun anOrgans(): EntrySource = ZipEntries(http.download(OfficialSources.AMO10, folder.resolve("amo10.zip")))
    override fun senatCalendar(): InputStream = Files.newInputStream(http.download(SENAT_ICAL, folder.resolve("senat.ics")))
    override fun europarl(path: String): InputStream? {
        val separator = if ('?' in path) '&' else '?'
        return http.get("${OfficialSources.EUROPARL_API}$path${separator}format=application%2Fld%2Bjson&offset=0&limit=500", throttle = true)
    }

    override fun close() {
        Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    companion object {
        const val AGENDA = "https://data.assemblee-nationale.fr/static/openData/repository/17/vp/reunions/Agenda.json.zip"
        const val SENAT_ICAL = "https://www.senat.fr/aglae/Seance/ical.ics"
    }
}

/** The days covered by the package: the day of the import and the fourteen after. */
data class WeekWindow(val from: LocalDate, val to: LocalDate) {
    fun contains(start: LocalDateTime): Boolean = !start.toLocalDate().isBefore(from) && !start.toLocalDate().isAfter(to)

    companion object {
        fun of(today: String): WeekWindow = LocalDate.parse(today).let { WeekWindow(it, it.plusDays(ParliamentWeekFormat.DAYS_AFTER.toLong())) }
    }
}

internal val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val LOCAL_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

internal fun LocalDateTime.minutes(): String = format(LOCAL_MINUTES)

/** A text of a source, trimmed, its white space folded, at most [ParliamentWeekFormat.MAX_TEXT] characters. */
internal fun cleanText(value: String?): String? {
    val text = value?.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")?.lines()?.joinToString("\n") { it.trim().replace(Regex("[ \\t]+"), " ") }?.replace(Regex("\n{3,}"), "\n\n")?.trim()
    if (text.isNullOrEmpty()) return null
    return if (text.length > ParliamentWeekFormat.MAX_TEXT) text.take(ParliamentWeekFormat.MAX_TEXT - 1).trimEnd() + "…" else text
}

// ---------------------------------------------------------------------------------------------
// Assemblée nationale

object AnAgenda {
    /** The organ of the plenary sitting. */
    const val PLENARY_ORGAN = "PO838901"
    const val PLENARY_URL = "https://www.assemblee-nationale.fr/dyn/seance-publique"
    const val AGENDAS_URL = "https://www2.assemblee-nationale.fr/agendas/les-agendas"

    private val MEETING = Regex("^RU[0-9A-Z]{1,40}$")
    private val ORGAN = Regex("^PO\\d{1,10}$")
    private val DOSSIER = Regex("^DLR5L(\\d{1,3})N\\d{1,10}$")

    /** A meeting of the agenda, before the window and the names of the committees are applied. */
    data class Meeting(val uid: String, val start: LocalDateTime, val end: LocalDateTime?, val kind: SittingKind, val organ: String?, val status: AgendaStatus?, val points: List<Point>, val summary: List<String>)

    data class Point(val title: String?, val pointKind: String?, val dossier: String?)

    /** The meetings of `Agenda.json.zip` in [window]: plenary sittings, and committee meetings with a legislative point. */
    fun meetings(entries: EntrySource, window: WeekWindow): List<Meeting> {
        val meetings = ArrayList<Meeting>()
        var read = 0
        entries.forEach { entry ->
            if (!entry.name.contains("reunion/") || !entry.name.endsWith(".json")) return@forEach
            read++
            meeting(entry.json())?.takeIf { window.contains(it.start) }?.let { meetings += it }
        }
        if (read == 0) throw UnreadableSourceException("Agenda: no meeting")
        return meetings
    }

    /** One `reunion`; null for a meeting that is neither a plenary sitting nor a committee meeting on a legislative file. */
    fun meeting(json: JsonElement): Meeting? {
        val root = json["reunion"] ?: return null
        val uid = root["uid"].text()?.takeIf { MEETING.matches(it) } ?: return null
        val start = dateTime(root["timeStampDebut"].text()) ?: return null
        val end = dateTime(root["timeStampFin"].text())?.takeIf { !it.isBefore(start) }
        val organ = root["organeReuniRef"].text()?.takeIf { ORGAN.matches(it) }
        val type = root["@xsi:type"].text().orEmpty()
        val kind = when {
            organ == PLENARY_ORGAN || type.startsWith("seance") -> SittingKind.PLENARY
            type.contains("Commission", ignoreCase = true) -> SittingKind.COMMITTEE
            else -> return null
        }
        val points = root.at("ODJ", "pointsODJ", "pointODJ").items().map { point ->
            Point(
                title = cleanText(point["objet"].text()),
                pointKind = cleanText(point["typePointODJ"].text()),
                dossier = point.at("dossiersLegislatifsRefs", "dossierRef").items().firstNotNullOfOrNull { it.text()?.takeIf(DOSSIER::matches) },
            )
        }.filter { it.title != null || it.dossier != null }
        val summary = root.at("ODJ", "resumeODJ", "item").items().mapNotNull { cleanText(it.text()) }
        val kept = if (kind == SittingKind.COMMITTEE) points.filter { it.dossier != null } else points
        if (kind == SittingKind.COMMITTEE && kept.isEmpty()) return null
        return Meeting(uid, start, end, kind, organ, status(root.at("cycleDeVie", "etat").text()), kept, summary)
    }

    /** `Confirmé`, `Éventuel`, `Annulé`, `Supprimé`… ; null when the state says nothing of the kind. */
    fun status(etat: String?): AgendaStatus? {
        val value = etat?.lowercase()?.replace('é', 'e')?.replace('É', 'e') ?: return null
        return when {
            value.startsWith("confirm") -> AgendaStatus.CONFIRMED
            value.startsWith("annul") || value.startsWith("supprim") -> AgendaStatus.CANCELLED
            value.startsWith("eventuel") || value.startsWith("prevision") -> AgendaStatus.PROVISIONAL
            else -> null
        }
    }

    /** `2026-11-10T15:00:00.000+01:00` to the local time of Paris. */
    fun dateTime(value: String?): LocalDateTime? {
        val text = value?.trim() ?: return null
        runCatching { return OffsetDateTime.parse(text).atZoneSameInstant(PARIS).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES) }
        return runCatching { LocalDateTime.parse(text).truncatedTo(ChronoUnit.MINUTES) }.getOrNull()
    }

    /** The committees named in [meetings], from the organs of the Assemblée. */
    fun organNames(entries: EntrySource, wanted: Set<String>): Map<String, String> {
        val names = HashMap<String, String>()
        if (wanted.isEmpty()) return names
        entries.forEach { entry ->
            if (!entry.name.contains("organe/") || !entry.name.endsWith(".json")) return@forEach
            if (entry.name.substringAfterLast('/').removeSuffix(".json") !in wanted) return@forEach
            val root = entry.json()["organe"] ?: return@forEach
            val uid = root["uid"].text() ?: return@forEach
            if (uid in wanted) root["libelle"].text()?.let { names[uid] = it }
        }
        return names
    }

    /** The items of the package: one per point of the order of the day (or one for a sitting without published points). */
    fun items(meetings: List<Meeting>, organs: Map<String, String>): List<WeekItem> = meetings.flatMap { m ->
        val url = if (m.kind == SittingKind.PLENARY) PLENARY_URL else AGENDAS_URL
        val body = if (m.kind == SittingKind.COMMITTEE) m.organ?.let { organs[it] } else null
        if (m.points.isEmpty()) {
            listOf(WeekItem("an:${m.uid}", Chamber.AN, m.start.minutes(), m.end?.minutes(), m.kind, body, title = m.summary.firstOrNull(), detail = cleanText(m.summary.drop(1).joinToString("\n")), status = m.status, url = url))
        } else {
            m.points.mapIndexed { index, point ->
                WeekItem(
                    id = "an:${m.uid}:${index + 1}",
                    chamber = Chamber.AN,
                    start = m.start.minutes(),
                    end = m.end?.minutes(),
                    kind = m.kind,
                    body = body,
                    title = point.title,
                    pointKind = point.pointKind,
                    dossier = point.dossier?.let { "an-dossier:$it" },
                    dossierUrl = point.dossier?.let { d -> "${Assemblee.SITE}/dyn/${DOSSIER.find(d)!!.groupValues[1]}/dossiers/$d" },
                    status = m.status,
                    url = url,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sénat

/**
 * The iCalendar feed of the Sénat (RFC 5545), read simply: folded lines, `VEVENT` blocks with
 * `DTSTART`, `DTEND`, `SUMMARY`, `DESCRIPTION`, `CATEGORIES`, `UID`, `URL`, `STATUS`, and the
 * repetitions `RRULE` (FREQ DAILY or WEEKLY, INTERVAL, COUNT, UNTIL, BYDAY), `RDATE` and `EXDATE`.
 * `ORGANIZER`, `ATTENDEE`, `CONTACT` and any other property are never read.
 */
object SenatICalendar {
    const val AGENDA_URL = "https://www.senat.fr/ordre-du-jour/ordre-du-jour.html"
    private const val MAX_BYTES: Long = 8L * 1024 * 1024
    private const val MAX_OCCURRENCES = 400

    data class Property(val name: String, val params: Map<String, String>, val value: String)

    data class Event(val properties: List<Property>) {
        fun first(name: String): Property? = properties.firstOrNull { it.name == name }
        fun all(name: String): List<Property> = properties.filter { it.name == name }
    }

    fun events(stream: InputStream): List<Event> {
        val text = stream.use { readBounded(it, MAX_BYTES, "senat ical") }.decodeToString().removePrefix("﻿")
        if (!text.trimStart().startsWith("BEGIN:VCALENDAR")) throw UnreadableSourceException("senat ical: not a calendar")
        val lines = ArrayList<String>()
        text.split("\r\n", "\n").forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] = lines.last() + line.substring(1) else lines += line
        }
        val events = ArrayList<Event>()
        var current: ArrayList<Property>? = null
        var depth = 0
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> { current = ArrayList(); depth = 0 }
                line == "END:VEVENT" -> { current?.let { events += Event(it) }; current = null }
                line.startsWith("BEGIN:") && current != null -> depth++
                line.startsWith("END:") && current != null -> depth--
                current != null && depth == 0 -> {
                    val p = property(line)
                    if (p != null) current.add(p)
                }
            }
        }
        return events
    }

    private fun property(line: String): Property? {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') } }.toMap()
        return Property(head[0].uppercase(), params, line.substring(colon + 1))
    }

    fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /** A date or date-time of the feed in the time of Paris: `…Z` is UTC, `TZID=` a zone, a bare value local time. */
    fun dateTime(value: String, params: Map<String, String>): LocalDateTime? {
        val v = value.trim()
        if (params["VALUE"] == "DATE" || Regex("^\\d{8}$").matches(v)) return runCatching { LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() }.getOrNull()
        val local = runCatching { LocalDateTime.parse(v.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm[ss]")) }.getOrNull() ?: return null
        val zone = when {
            v.endsWith("Z") -> ZoneOffset.UTC
            params["TZID"] != null -> runCatching { ZoneId.of(params.getValue("TZID")) }.getOrDefault(PARIS)
            else -> PARIS
        }
        return local.atZone(zone).withZoneSameInstant(PARIS).toLocalDateTime()
    }

    private fun dates(property: Property): List<LocalDateTime> = property.value.split(',').mapNotNull { dateTime(it, property.params) }

    /** The starts of an event within [window], its repetitions expanded. */
    fun occurrences(event: Event, window: WeekWindow): List<LocalDateTime> {
        val start = event.first("DTSTART")?.let { dateTime(it.value, it.params) } ?: return emptyList()
        val starts = LinkedHashSet<LocalDateTime>()
        starts += start
        event.first("RRULE")?.let { starts += expand(start, it.value, window) }
        event.all("RDATE").forEach { starts += dates(it) }
        val excluded = event.all("EXDATE").flatMap(::dates).toSet()
        return starts.filter { it !in excluded && window.contains(it) }.sorted()
    }

    private fun expand(start: LocalDateTime, rule: String, window: WeekWindow): List<LocalDateTime> {
        val parts = rule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].uppercase() } }.toMap()
        val interval = parts["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { dateTime(it, emptyMap()) }
        val days = parts["BYDAY"]?.split(',')?.mapNotNull { day(it.takeLast(2)) }?.toSet().orEmpty()
        val last = window.to.atTime(23, 59).let { if (until != null && until.isBefore(it)) until else it }
        val result = ArrayList<LocalDateTime>()
        var produced = 0
        fun add(t: LocalDateTime): Boolean {
            if (t.isAfter(last) || (count != null && produced >= count) || produced >= MAX_OCCURRENCES) return false
            if (!t.isBefore(start)) { result += t; produced++ }
            return true
        }
        when (parts["FREQ"]) {
            "DAILY" -> {
                var t = start
                while (add(t)) t = t.plusDays(interval)
            }
            "WEEKLY" -> {
                var weekStart = start.toLocalDate().with(DayOfWeek.MONDAY)
                val wanted = days.ifEmpty { setOf(start.dayOfWeek) }.sorted()
                loop@ while (true) {
                    for (d in wanted) {
                        val t = weekStart.plusDays((d.value - 1).toLong()).atTime(start.toLocalTime())
                        if (t.isBefore(start)) continue
                        if (!add(t)) break@loop
                    }
                    weekStart = weekStart.plusWeeks(interval)
                }
            }
            else -> Unit // Other frequencies are not used by the feed: the first date and the RDATE are kept.
        }
        return result
    }

    private fun day(code: String): DayOfWeek? = when (code) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    private val UID = Regex("[^A-Za-z0-9._@-]")
    private val COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    fun items(events: List<Event>, window: WeekWindow): List<WeekItem> = events.flatMap { event ->
        val start = event.first("DTSTART")?.let { dateTime(it.value, it.params) } ?: return@flatMap emptyList<WeekItem>()
        val end = event.first("DTEND")?.let { dateTime(it.value, it.params) }
        val duration = end?.let { ChronoUnit.MINUTES.between(start, it) }?.takeIf { it in 0..(24 * 60) }
        val uid = event.first("UID")?.value?.let { UID.replace(it, "").take(80) }?.ifEmpty { null } ?: return@flatMap emptyList<WeekItem>()
        val url = event.first("URL")?.value?.trim()?.takeIf { it.startsWith("https://www.senat.fr/") } ?: AGENDA_URL
        val status = when (event.first("STATUS")?.value?.trim()?.uppercase()) {
            "CONFIRMED" -> AgendaStatus.CONFIRMED
            "TENTATIVE" -> AgendaStatus.PROVISIONAL
            "CANCELLED" -> AgendaStatus.CANCELLED
            else -> null
        }
        val title = event.first("SUMMARY")?.let { cleanText(unescape(it.value)) }
        val detail = event.first("DESCRIPTION")?.let { cleanText(unescape(it.value)) }?.takeIf { it != title }
        val category = event.first("CATEGORIES")?.let { cleanText(unescape(it.value)) }
        val kind = if (category == null || category.contains("séance", ignoreCase = true)) SittingKind.PLENARY else SittingKind.OTHER
        occurrences(event, window).map { t ->
            WeekItem(
                id = "senat:$uid:${t.format(COMPACT)}",
                chamber = Chamber.SENAT,
                start = t.minutes(),
                end = duration?.let { t.plusMinutes(it).minutes() },
                kind = kind,
                title = title,
                detail = detail,
                status = status,
                url = url,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// European Parliament

/** The foreseen activities of the plenary sittings (`meetings/{MTG-PL-AAAA-MM-JJ}/foreseen-activities`). */
object EuroparlAgenda {
    const val PLENARY_URL = "https://www.europarl.europa.eu/plenary/fr/home.html"
    private val SITTING = Regex("^MTG-PL-(\\d{4}-\\d{2}-\\d{2})$")

    fun items(transport: (String) -> InputStream?, window: WeekWindow): List<WeekItem> {
        val api = JsonEuroparlApi { path, offset, _ -> if (offset > 0) null else transport(path) }
        val sittings = (window.from.year..window.to.year).flatMap { api.plenarySittings(it) }.distinct().sorted()
            .filter { id -> SITTING.find(id)?.groupValues?.get(1)?.let { d -> LocalDate.parse(d).atStartOfDay().let(window::contains) } ?: false }
        return sittings.flatMap { sitting -> activities(sitting, transport("meetings/$sitting/foreseen-activities"), window) }
    }

    /** The activities of one sitting, in their order; null [stream] for a sitting without published activities. */
    fun activities(sitting: String, stream: InputStream?, window: WeekWindow): List<WeekItem> {
        stream ?: return emptyList()
        val day = LocalDate.parse(SITTING.find(sitting)!!.groupValues[1])
        val data = try {
            stream.use { lenientJson.parseToJsonElement(readBounded(it, SourceEntry.MAX_ENTRY_BYTES, "europarl foreseen-activities").decodeToString()) }["data"].items()
        } catch (e: UnreadableSourceException) {
            throw e
        } catch (e: Exception) {
            throw UnreadableSourceException("europarl foreseen-activities: unexpected answer")
        }
        return data.mapIndexedNotNull { rank, activity ->
            val title = cleanText(languageText(activity["activity_label"]) ?: languageText(activity["agendaLabel"])) ?: return@mapIndexedNotNull null
            val order = activity["activity_order"].int() ?: (rank + 1)
            val start = activity["activity_start_date"].text()?.let(::dateTime)?.takeIf { it.toLocalDate() == day } ?: day.atStartOfDay()
            val end = activity["activity_end_date"].text()?.let(::dateTime)?.takeIf { !it.isBefore(start) }
            if (!window.contains(start)) return@mapIndexedNotNull null
            val type = activity["had_activity_type"].text()?.substringAfterLast('/')?.uppercase().orEmpty()
            WeekItem(
                id = "pe:$sitting:$order",
                chamber = Chamber.PE,
                start = start.minutes(),
                end = end?.minutes(),
                kind = SittingKind.PLENARY,
                title = title,
                pointKind = when {
                    "VOTE" in type -> "Votes"
                    "DEBATE" in type -> "Débat"
                    else -> null
                },
                status = AgendaStatus.PROVISIONAL,
                url = PLENARY_URL,
            )
        }
    }

    /** Strasbourg and Brussels are in the time of Paris: a time without offset is read as such. */
    private fun dateTime(value: String): LocalDateTime? = AnAgenda.dateTime(value) ?: runCatching { LocalDate.parse(value.take(10)).atStartOfDay() }.getOrNull()

    /** French first, then English, then any: a string, a language map or a list of `{"@language", "@value"}`. */
    private fun languageText(element: JsonElement?): String? {
        element ?: return null
        element.text()?.let { return it }
        if (element is JsonObject) {
            listOf("fr", "en").forEach { lang -> element[lang].text()?.let { return it } }
            return element.values.firstNotNullOfOrNull { it.text() }
        }
        val values = element.items()
        values.firstOrNull { it["@language"].text() == "fr" }?.get("@value").text()?.let { return it }
        values.firstOrNull { it["@language"].text() == "en" }?.get("@value").text()?.let { return it }
        return values.firstNotNullOfOrNull { it["@value"].text() ?: it.text() }
    }
}

// ---------------------------------------------------------------------------------------------
// The package

/**
 * Builds the package `semaine` of an import: the chambers of [only] are read again, the others
 * keep the items of the previous package of [out] (days before the window dropped). A source that
 * fails keeps its previous items too, and the log says so; the import goes on.
 */
class WeekImport(
    private val sources: ImportSources,
    private val only: Set<SourceId>,
    private val out: Path,
    private val log: (String) -> Unit,
    private val weekSources: () -> WeekSources? = { defaultWeekSources(sources) },
) {
    fun bytes(): ByteArray = ParliamentWeekFormat.write(build()).encodeToByteArray()

    fun build(): ParliamentWeekPackage {
        val window = WeekWindow.of(sources.today)
        val previous = previousItems()
        val items = ArrayList<WeekItem>()
        val week = weekSources()
        try {
            for (source in SourceId.entries) {
                val fresh = if (source in only && week != null) read(source, week, window) else null
                items += fresh ?: previous.filter { it.chamber == source.chamber && it.start.take(10) >= window.from.toString() && it.start.take(10) <= window.to.toString() }
            }
        } finally {
            week?.close()
        }
        val sorted = items.distinctBy { it.id }.sortedWith(ParliamentWeek.ORDER)
        log("week: " + SourceId.entries.joinToString { s -> "${s.id} ${sorted.count { it.chamber == s.chamber }} item(s)" })
        return ParliamentWeekPackage(example = sources.example, from = window.from.toString(), to = window.to.toString(), items = sorted)
    }

    private fun read(source: SourceId, week: WeekSources, window: WeekWindow): List<WeekItem>? = try {
        when (source) {
            SourceId.AN -> {
                val meetings = AnAgenda.meetings(week.anAgenda(), window)
                val wanted = meetings.filter { it.kind == SittingKind.COMMITTEE }.mapNotNull { it.organ }.toSet()
                AnAgenda.items(meetings, if (wanted.isEmpty()) emptyMap() else AnAgenda.organNames(week.anOrgans(), wanted))
            }
            SourceId.SENAT -> SenatICalendar.items(SenatICalendar.events(week.senatCalendar()), window)
            SourceId.PE -> EuroparlAgenda.items(week::europarl, window)
        }
    } catch (e: DownloadException) {
        log("week: ${source.id} not downloaded (${e.message}); previous items kept")
        null
    } catch (e: UnreadableSourceException) {
        log("week: ${source.id} unreadable (${e.message}); previous items kept")
        null
    }

    /** The items of the previous package, when it still matches the previous index. */
    private fun previousItems(): List<WeekItem> {
        val indexFile = out.resolve(Importer.INDEX_FILE)
        val file = out.resolve(ParliamentWeekFormat.PACKAGE + ".json")
        if (!indexFile.isRegularFile() || !file.isRegularFile()) return emptyList()
        val index = runCatching { ParliamentFormat.json.decodeFromString(ParliamentIndex.serializer(), SignedEvent.fromJson(Files.readString(indexFile)).content) }.getOrNull() ?: return emptyList()
        val ref = index.packageRef(ParliamentWeekFormat.PACKAGE) ?: return emptyList()
        val bytes = Files.readAllBytes(file)
        if (ParliamentFormat.sha256Hex(bytes) != ref.sha256) return emptyList()
        return ParliamentWeekFormat.read(bytes.decodeToString())?.items.orEmpty()
    }

    companion object {
        /** The example files for the example set, the institutions for [OfficialSources]; none for local official files (previous items kept). */
        fun defaultWeekSources(sources: ImportSources): WeekSources? = when {
            sources.example -> ExampleWeekSources()
            sources is OfficialSources -> OfficialWeekSources()
            else -> null
        }
    }
}
