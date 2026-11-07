package org.politisationpopulairefrancaise.tools.opendata

import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Counts
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryBallot
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PartyBasis
import org.politisationpopulairefrancaise.core.voir.parlement.PartyRef
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.text.Normalizer

/*
 * The history of the public ballots of the Sénat, since the session 2006-2007
 * (voir-historique-sources.md). The ballots and the vote of each senator are only in the copy of
 * the Dosleg database (`dosleg.zip`, a PostgreSQL 8.4 dump of 127 MB): its SQL text is read as a
 * stream, and only the rows of the `COPY scr`, `COPY votsen` and `COPY posvot` blocks are read;
 * nothing is ever loaded into a database or executed. The Sénat publishes no party of its
 * senators: the group at the date of the ballot (`ODSEN_HISTOGROUPES.csv`) stands for it, always
 * marked `groupe`. Only counts are kept: no name, no matricule, no nominal vote.
 */

/** One senator's groups over time ([Span.organ] is the code of the group). */
class SenatGroups(val bySenator: Map<String, List<Span>>, val labels: Map<String, Pair<String, String?>>) {
    private val membersByDay = HashMap<String, Map<String, Int>>()

    fun groupAt(matricule: String, day: String): String? = bySenator[matricule]?.at(day)?.organ

    /** The senators of each group at [day]. */
    fun members(day: String): Map<String, Int> = membersByDay.getOrPut(day) {
        val counts = HashMap<String, Int>()
        bySenator.values.forEach { spans -> spans.at(day)?.let { counts[it.organ] = (counts[it.organ] ?: 0) + 1 } }
        counts
    }
}

object SenatHistory {
    /** The Dosleg copy holds the public ballots since the session 2006-2007. */
    const val FIRST_SESSION: Int = 2006
    const val NO_GROUP: String = "aucun"
    const val SITE: String = "https://www.senat.fr"

    private val MATRICULE = Regex("^[0-9A-Z]{4,12}$")
    private val GROUP_CODE = Regex("^[0-9A-Za-z_-]{1,20}$")

    fun line(code: String): Line = Line("senat-grp:$code", PartyBasis.GROUP)

    // ------------------------------------------------------------ ODSEN_HISTOGROUPES.csv

    /** Lowercase, without accents nor spaces, to find the columns by their names. */
    private fun normalized(header: String): String =
        Normalizer.normalize(header, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    /** `JJ/MM/AAAA` (possibly with a time) or `AAAA-MM-JJ`, to AAAA-MM-JJ. */
    internal fun csvDay(value: String?): String? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        Regex("^(\\d{2})/(\\d{2})/(\\d{4})").find(v)?.let { m -> return "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}" }
        return Regex("^(\\d{4}-\\d{2}-\\d{2})").find(v)?.groupValues?.get(1)
    }

    /** Splits one line of the CSV of the Sénat (`;`, fields possibly between double quotes). */
    internal fun csvFields(line: String): List<String> {
        val fields = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> { fields += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    /**
     * `ODSEN_HISTOGROUPES.csv` (Latin-1, `;`): matricule, group code and short name, start and end
     * of the membership. The names and first names of the file are never read; the columns are
     * found by their headers.
     */
    fun groups(stream: InputStream): SenatGroups {
        val text = stream.use { readBounded(it, SourceEntry.MAX_ENTRY_BYTES, "ODSEN_HISTOGROUPES.csv") }.toString(Charsets.ISO_8859_1)
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: empty")
        val headers = csvFields(lines.next()).map(::normalized)
        fun column(test: (String) -> Boolean): Int? = headers.indexOfFirst(test).takeIf { it >= 0 }
        val matricule = column { "matricule" in it } ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: no matricule column")
        val code = column { "code" in it && "groupe" in it } ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: no group code column")
        val short = column { "groupe" in it && "code" !in it && ("court" in it || "abrege" in it || "sigle" in it) }
            ?: column { "groupe" in it && "code" !in it && ("nom" in it || "libelle" in it) }
        val start = column { "debut" in it && "fonction" !in it } ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: no start column")
        val end = column { "fin" in it && "fonction" !in it && "debut" !in it } ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: no end column")

        val ids = Interner()
        val bySenator = HashMap<String, MutableList<Span>>()
        // The label of a code: the one of its latest membership.
        val labels = HashMap<String, Pair<String, Pair<String, String?>>>()
        lines.forEach { line ->
            val f = csvFields(line)
            val m = f.getOrNull(matricule)?.trim()?.uppercase()?.takeIf(MATRICULE::matches) ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: unreadable matricule")
            val c = f.getOrNull(code)?.trim()?.takeIf(GROUP_CODE::matches) ?: throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: unreadable group code")
            val s = csvDay(f.getOrNull(start))
            val e = csvDay(f.getOrNull(end))
            bySenator.getOrPut(ids.of(m)) { ArrayList() } += Span(ids.of(c), s, e)
            val label = short?.let { f.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) } ?: c
            val known = labels[c]
            if (known == null || (s ?: "") >= known.first) labels[c] = (s ?: "") to (label to c)
        }
        if (bySenator.isEmpty()) throw UnreadableSourceException("ODSEN_HISTOGROUPES.csv: no membership")
        return SenatGroups(bySenator, labels.mapValues { it.value.second })
    }

    // ------------------------------------------------------------ dosleg.sql

    /** One row of a `COPY` block, by column name; `\N` is null. */
    internal class CopyRow(private val columns: Map<String, Int>, private val values: List<String?>) {
        operator fun get(column: String): String? = columns[column]?.let { values.getOrNull(it) }
    }

    /** The table and columns of a `COPY table (a, b) FROM stdin;` line, null for another line. */
    internal fun copyHeader(line: String): Pair<String, List<String>>? {
        val m = Regex("^COPY\\s+([A-Za-z0-9_.\"]+)\\s*\\(([^)]*)\\)\\s+FROM\\s+stdin;?\\s*$").find(line) ?: return null
        val table = m.groupValues[1].replace("\"", "").substringAfterLast('.').lowercase()
        val columns = m.groupValues[2].split(',').map { it.trim().replace("\"", "").lowercase() }
        return table to columns
    }

    /** The text form of a value of a `COPY` row. */
    internal fun copyValue(raw: String): String? {
        if (raw == "\\N") return null
        if ('\\' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                i++
                out.append(
                    when (val e = raw[i]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'v' -> '\u000B'
                        else -> e
                    },
                )
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun charsetOf(name: String): Charset = when (name.uppercase().replace("-", "").replace("_", "")) {
        "UTF8", "UNICODE" -> Charsets.UTF_8
        "LATIN1", "ISO88591" -> Charsets.ISO_8859_1
        "LATIN9", "ISO885915" -> Charset.forName("ISO-8859-15")
        "WIN1252", "WINDOWS1252" -> Charset.forName("windows-1252")
        else -> throw UnreadableSourceException("dosleg.sql: unknown encoding")
    }

    /**
     * Reads the SQL text as a stream of lines, and calls [action] for each row of the blocks of
     * [tables] only. Lines are bytes until their encoding is known (`SET client_encoding`).
     */
    internal fun readCopyBlocks(stream: InputStream, tables: Set<String>, action: (table: String, row: CopyRow) -> Unit) {
        val input = BufferedInputStream(stream, 256 * 1024)
        var charset: Charset = Charsets.UTF_8
        var block: String? = null
        var columns: Map<String, Int> = emptyMap()
        var inOtherBlock = false
        val bytes = java.io.ByteArrayOutputStream(4096)
        while (true) {
            bytes.reset()
            var c = input.read()
            if (c == -1) break
            while (c != -1 && c != '\n'.code) {
                bytes.write(c)
                if (bytes.size() > MAX_LINE) throw UnreadableSourceException("dosleg.sql: line too long")
                c = input.read()
            }
            val raw = bytes.toByteArray()
            val ended = raw.size == 2 && raw[0] == '\\'.code.toByte() && raw[1] == '.'.code.toByte() ||
                raw.size == 3 && raw[0] == '\\'.code.toByte() && raw[1] == '.'.code.toByte() && raw[2] == '\r'.code.toByte()
            when {
                block != null -> if (ended) block = null else {
                    val line = String(raw, charset).trimEnd('\r')
                    action(block!!, CopyRow(columns, line.split('\t').map(::copyValue)))
                }
                inOtherBlock -> if (ended) inOtherBlock = false
                raw.size >= 5 && raw[0] == 'C'.code.toByte() && String(raw, 0, 5, Charsets.US_ASCII) == "COPY " -> {
                    val (table, cols) = copyHeader(String(raw, charset).trimEnd('\r')) ?: throw UnreadableSourceException("dosleg.sql: unreadable COPY line")
                    if (table in tables) {
                        block = table
                        columns = cols.withIndex().associate { it.value to it.index }
                    } else {
                        inOtherBlock = true
                    }
                }
                raw.size >= 4 && raw[0] == 'S'.code.toByte() -> {
                    val line = String(raw, Charsets.US_ASCII).trimEnd('\r')
                    Regex("^SET\\s+client_encoding\\s*=\\s*'([^']+)'").find(line)?.let { charset = charsetOf(it.groupValues[1]) }
                }
            }
        }
        if (block != null || inOtherBlock) throw UnreadableSourceException("dosleg.sql: truncated COPY block")
    }

    private class ScrBallot(
        val session: Int,
        val number: Int,
        val date: String,
        val title: String,
        val counts: Counts,
        val adopted: Boolean?,
    ) {
        val distribution = Distribution()
    }

    /** The code of a position to a [Position], by the label of `posvot` (`Pour`, `Contre`, `Abstention`, anything else is not voting). */
    private fun positionOf(label: String?, code: String): Position {
        val text = (label ?: code).let { normalized(it) }
        return when {
            label == null && code.lowercase() == "p" -> Position.FOR
            label == null && code.lowercase() == "c" -> Position.AGAINST
            label == null && code.lowercase() == "a" -> Position.ABSTENTION
            label == null -> Position.NOT_VOTING
            text.startsWith("pour") -> Position.FOR
            text.startsWith("contre") -> Position.AGAINST
            text.startsWith("abst") -> Position.ABSTENTION
            else -> Position.NOT_VOTING
        }
    }

    /**
     * The packages of every session since [FIRST_SESSION]: `dosleg.zip` (one `.sql` entry) is read
     * twice, first the ballots (`scr`) and the codes of positions (`posvot`), then the votes
     * (`votsen`), counted at once per group and never kept.
     */
    fun packages(dosleg: EntrySource, groups: SenatGroups, example: Boolean): List<HistoryPackage> {
        var sql: SourceEntry? = null
        dosleg.forEach { entry -> if (entry.name.endsWith(".sql")) sql = if (sql == null) entry else throw UnreadableSourceException("dosleg: several SQL files") }
        if (sql == null) throw UnreadableSourceException("dosleg: no SQL file")
        // The entries are reopened from the zip on disk (or the resources) for each pass.
        val ballots = HashMap<Long, ScrBallot>()
        val positionLabels = HashMap<String, String?>()
        var posvotSeen = false
        dosleg.forEach { entry ->
            if (!entry.name.endsWith(".sql")) return@forEach
            entry.open().use { stream ->
                readCopyBlocks(stream, setOf("scr", "posvot")) { table, row ->
                    if (table == "posvot") {
                        posvotSeen = true
                        val code = row["posvotcod"] ?: throw UnreadableSourceException("dosleg posvot: no code")
                        positionLabels[code] = row["posvotlib"] ?: row["posvotlibcourt"] ?: row["posvotlibelle"]
                        return@readCopyBlocks
                    }
                    val session = row["sesann"]?.trim()?.toIntOrNull() ?: throw UnreadableSourceException("dosleg scr: no session")
                    if (session < FIRST_SESSION) return@readCopyBlocks
                    val number = row["scrnum"]?.trim()?.toIntOrNull() ?: throw UnreadableSourceException("dosleg scr: no number")
                    val date = row["scrdat"]?.trim()?.take(10)?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) } ?: throw UnreadableSourceException("dosleg scr: no date")
                    val title = row["scrint"]?.trim()?.takeIf(String::isNotEmpty) ?: "Scrutin public n° $number"
                    fun n(column: String) = row[column]?.trim()?.toIntOrNull()
                    val inFavour = n("scrpou") ?: throw UnreadableSourceException("dosleg scr: no count for")
                    val against = n("scrcon") ?: throw UnreadableSourceException("dosleg scr: no count against")
                    val voters = n("scrvot")
                    val expressed = n("scrsuf")
                    val majority = n("scrmaj")
                    ballots[key(session, number)] = ScrBallot(
                        session = session,
                        number = number,
                        date = date,
                        title = title,
                        // The Sénat counts voters and votes cast: the difference is the abstentions.
                        counts = Counts(voters = voters, expressed = expressed, inFavour = inFavour, against = against, abstention = if (voters != null && expressed != null) maxOf(0, voters - expressed) else 0),
                        // Adopted when the votes for reach the absolute majority of the votes cast that the file gives.
                        adopted = majority?.let { inFavour >= it },
                    )
                }
            }
        }
        if (ballots.isEmpty()) throw UnreadableSourceException("dosleg: no ballot since $FIRST_SESSION")
        if (posvotSeen && positionLabels.isEmpty()) throw UnreadableSourceException("dosleg posvot: no code")
        var votes = 0
        dosleg.forEach { entry ->
            if (!entry.name.endsWith(".sql")) return@forEach
            entry.open().use { stream ->
                readCopyBlocks(stream, setOf("votsen")) { _, row ->
                    val session = row["sesann"]?.trim()?.toIntOrNull() ?: throw UnreadableSourceException("dosleg votsen: no session")
                    val number = row["scrnum"]?.trim()?.toIntOrNull() ?: throw UnreadableSourceException("dosleg votsen: no number")
                    val ballot = ballots[key(session, number)] ?: return@readCopyBlocks
                    val matricule = row["senmat"]?.trim()?.uppercase()?.takeIf(MATRICULE::matches) ?: throw UnreadableSourceException("dosleg votsen: unreadable matricule")
                    val code = row["posvotcod"]?.trim() ?: throw UnreadableSourceException("dosleg votsen: no position")
                    // senmatdel (the senator who voted by delegation) and stavotidt are never kept.
                    val position = positionOf(if (posvotSeen) positionLabels[code] ?: "" else null, code)
                    ballot.distribution.add(line(groups.groupAt(matricule, ballot.date) ?: NO_GROUP), position)
                    votes++
                }
            }
        }
        if (votes == 0) throw UnreadableSourceException("dosleg: no vote of a senator")

        return ballots.values.groupBy { it.session }.toSortedMap().map { (session, list) ->
            val history = list.map { b ->
                val members = groups.members(b.date)
                // Every group with senators that day has a line, even when none of them voted.
                members.keys.forEach { b.distribution.add(line(it), Position.NOT_VOTING, 0) }
                HistoryBallot(
                    id = "senat:${b.session}:${b.number}",
                    date = b.date,
                    title = b.title,
                    adopted = b.adopted,
                    counts = b.counts,
                    parties = b.distribution.build { members[it.id.removePrefix("senat-grp:")] ?: 0 },
                    url = "$SITE/scrutin-public/${b.session}/scr${b.session}-${b.number}.html",
                )
            }
            val parties = list.flatMap { it.distribution.lines }.toSet().map { partyRef(it, groups) }
            historyPackage(example, Chamber.SENAT, session, history, parties)
        }
    }

    private fun partyRef(line: Line, groups: SenatGroups): PartyRef {
        val code = line.id.removePrefix("senat-grp:")
        if (code == NO_GROUP) return partyRef(line, "Sans groupe connu à cette date", null)
        val (label, abbreviation) = groups.labels[code] ?: (code to null)
        return partyRef(line, label, abbreviation?.takeIf { it != label })
    }

    private fun key(session: Int, number: Int): Long = session.toLong() * 1_000_000 + number

    private const val MAX_LINE = 1024 * 1024
}
