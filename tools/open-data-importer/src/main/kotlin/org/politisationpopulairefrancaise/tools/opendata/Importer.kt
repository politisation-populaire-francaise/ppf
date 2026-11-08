package org.politisationpopulairefrancaise.tools.opendata

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.Ballot
import org.politisationpopulairefrancaise.core.voir.parlement.BallotsPackage
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.DataSource
import org.politisationpopulairefrancaise.core.voir.parlement.HistoryPackage
import org.politisationpopulairefrancaise.core.voir.parlement.ImportGuard
import org.politisationpopulairefrancaise.core.voir.parlement.MembersPackage
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndex
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekFormat
import org.politisationpopulairefrancaise.core.voir.parlement.historyPackage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** The exit codes of the importer, given to the systemd timer and documented in voir-donnees-import.md. */
object ExitCodes {
    const val OK = 0
    const val USAGE = 1
    const val DOWNLOAD_FAILED = 2
    const val REFUSED_BY_GUARD = 3
    const val UNREADABLE_SOURCE = 4
    const val KEY_ERROR = 5
}

/** The three sources, by their identifier in the index. */
enum class SourceId(val id: String, val chamber: Chamber, val sourceName: String, val url: String, val licence: String) {
    AN("an", Chamber.AN, "Assemblée nationale, données ouvertes", "https://data.assemblee-nationale.fr/", "Licence Ouverte"),
    SENAT("senat", Chamber.SENAT, "Sénat, données ouvertes", "https://data.senat.fr/", "Licence Ouverte 2.0"),
    PE("pe", Chamber.PE, "Parlement européen, données ouvertes", "https://data.europarl.europa.eu/", "CC BY 4.0"),
    ;

    companion object {
        fun of(id: String): SourceId? = entries.firstOrNull { it.id == id }
    }
}

/** The import refused a new set (exit code 3). */
class GuardException(message: String) : Exception(message)

/**
 * One import: reads the sources of [only] and the history sources of [history], keeps the members,
 * ballots and history of the other sources from the previous set in [out], checks the new set against the previous one ([ImportGuard]), writes the
 * packages then the signed index. Nothing in [out] changes unless everything succeeded; the logs give
 * numbers and package names only.
 */
class Importer(
    private val sources: ImportSources,
    private val keys: IdentityKeys,
    private val out: Path,
    private val only: Set<SourceId> = SourceId.entries.toSet(),
    /** The history sources to rebuild (none by default: the history is imported apart, `--history`). */
    private val history: Set<HistorySourceId> = emptySet(),
    private val log: (String) -> Unit = ::println,
) {
    private val json = ParliamentFormat.json

    fun run(): Int = try {
        importAll()
        ExitCodes.OK
    } catch (e: GuardException) {
        log("refused: ${e.message}; the previous packages are kept")
        ExitCodes.REFUSED_BY_GUARD
    } catch (e: DownloadException) {
        log("download failed: ${e.message}; the previous packages are kept")
        ExitCodes.DOWNLOAD_FAILED
    } catch (e: UnreadableSourceException) {
        log("unreadable source data: ${e.message}; the previous packages are kept")
        ExitCodes.UNREADABLE_SOURCE
    } finally {
        sources.close()
    }

    private class Previous(val event: SignedEvent?, val index: ParliamentIndex?, val members: MembersPackage?)

    private fun previous(): Previous {
        val indexFile = out.resolve(INDEX_FILE)
        if (!indexFile.isRegularFile()) return Previous(null, null, null)
        val event = runCatching { SignedEvent.fromJson(Files.readString(indexFile)) }.getOrNull()
        val index = event?.let { runCatching { json.decodeFromString(ParliamentIndex.serializer(), it.content) }.getOrNull() }
        if (index == null) {
            log("previous index unreadable: ignored")
            return Previous(null, null, null)
        }
        val members = index.packageRef(ParliamentFormat.MEMBERS_PACKAGE)?.let { ref -> readPackage(ref)?.let { json.decodeFromString(MembersPackage.serializer(), it.decodeToString()) } }
        return Previous(event, index, members)
    }

    /** A package of the previous set, only when it still matches its fingerprint. */
    private fun readPackage(ref: PackageRef): ByteArray? {
        val file = out.resolve(ref.name + ".json")
        if (!ParliamentFormat.isPackageName(ref.name) || !file.isRegularFile()) return null
        return Files.readAllBytes(file).takeIf { ParliamentFormat.sha256Hex(it) == ref.sha256 }
    }

    private fun ballotsCount(index: ParliamentIndex?, chamber: Chamber): Int? {
        index ?: return null
        val refs = index.packages.filter { it.name.startsWith(chamber.ballotsPrefix + "-") }
        if (refs.isEmpty()) return null
        return refs.sumOf { ref ->
            val bytes = readPackage(ref) ?: throw UnreadableSourceException("previous package ${ref.name} is damaged; remove the folder to start again")
            json.decodeFromString(BallotsPackage.serializer(), bytes.decodeToString()).ballots.size
        }
    }

    private fun historyCount(index: ParliamentIndex?, source: HistorySourceId): Int? {
        index ?: return null
        val refs = index.packages.filter { it.name.startsWith(source.packagePrefix) }
        if (refs.isEmpty()) return null
        return refs.sumOf { ref ->
            val bytes = readPackage(ref) ?: throw UnreadableSourceException("previous package ${ref.name} is damaged; remove the folder to start again")
            json.decodeFromString(HistoryPackage.serializer(), bytes.decodeToString()).ballots.size
        }
    }

    /** The packages of one history source, encoded at once (the objects are not kept), with their number of ballots. */
    private fun historyFiles(source: HistorySourceId, example: Boolean): Pair<Map<String, ByteArray>, Int> {
        val files = LinkedHashMap<String, ByteArray>()
        var count = 0
        fun add(pkg: HistoryPackage) {
            files[ParliamentFormat.historyPackage(pkg.chamber, pkg.period)] = json.encodeToString(HistoryPackage.serializer(), pkg).encodeToByteArray()
            count += pkg.ballots.size
        }
        when (source) {
            HistorySourceId.AN -> {
                val reference = AssembleeHistory.reference(sources.anHistoryActors())
                AssembleeHistory.LEGISLATURES.forEach { add(AssembleeHistory.ballots(it, sources.anHistoryBallots(it), reference, example)) }
            }
            HistorySourceId.SENAT -> {
                val groups = SenatHistory.groups(sources.senatHistoGroups())
                SenatHistory.packages(sources.senatDosleg(), groups, example).forEach(::add)
            }
            HistorySourceId.PE -> EuroparlHistory.packages(sources.europarl(), sources.today, example).forEach(::add)
        }
        return files to count
    }

    private fun importAll() {
        val previous = previous()
        val example = sources.example
        if (previous.index != null && previous.index.example != example) {
            throw UnreadableSourceException("the folder holds ${if (previous.index.example) "example" else "official"} packages; use another folder")
        }
        val rosters = HashMap<SourceId, Roster>()
        val ballots = HashMap<Chamber, List<Ballot>>()

        if (SourceId.AN in only) {
            rosters[SourceId.AN] = Assemblee.members(sources.anMembers())
            val dossiers = Assemblee.dossierPaths(sources.anDossiers())
            ballots[Chamber.AN] = Assemblee.ballots(sources.anBallots(), dossiers)
        }
        if (SourceId.SENAT in only) rosters[SourceId.SENAT] = Senat.members(sources.senators())
        if (SourceId.PE in only) {
            val api = sources.europarl()
            val roster = Europarl.members(api, sources.today)
            rosters[SourceId.PE] = roster
            ballots[Chamber.PE] = Europarl.ballots(api, roster.members.map { it.id.removePrefix("pe:") }.toSet(), sources.today)
        }

        val histories = LinkedHashMap<HistorySourceId, Pair<Map<String, ByteArray>, Int>>()
        HistorySourceId.entries.filter { it in history }.forEach { histories[it] = historyFiles(it, example) }

        // The guard: no source may lose more than 20 % of its members or ballots (a truncated file).
        rosters.forEach { (source, roster) ->
            val before = previous.members?.members?.count { it.chamber == source.chamber }
            if (!ImportGuard.accepts(before, roster.members.size)) throw GuardException("${source.id}: ${roster.members.size} member(s) instead of $before")
        }
        ballots.forEach { (chamber, list) ->
            val before = ballotsCount(previous.index, chamber)
            if (!ImportGuard.accepts(before, list.size)) throw GuardException("${chamber.name.lowercase()}: ${list.size} ballot(s) instead of $before")
        }
        histories.forEach { (source, built) ->
            val before = historyCount(previous.index, source)
            if (!ImportGuard.accepts(before, built.second)) throw GuardException("${source.id}: ${built.second} ballot(s) instead of $before")
        }

        // Members and groups of the sources not refreshed now come from the previous set.
        val kept = SourceId.entries.filter { it !in only }.map { it.chamber }.toSet()
        val members = (rosters.values.flatMap { it.members } + previous.members?.members.orEmpty().filter { it.chamber in kept }).sortedBy { it.id }
        val groups = (rosters.values.flatMap { it.groups } + previous.members?.groups.orEmpty().filter { it.chamber in kept }).sortedBy { it.id }
        val files = LinkedHashMap<String, ByteArray>()
        // Without any daily source refreshed (`--history`), the members package of the previous set is kept as it is.
        if (only.isNotEmpty()) {
            files[ParliamentFormat.MEMBERS_PACKAGE] = json.encodeToString(MembersPackage.serializer(), MembersPackage(example = example, members = members, groups = groups)).encodeToByteArray()
        }
        ballots.forEach { (chamber, list) ->
            list.groupBy { it.date.take(7) }.toSortedMap().forEach { (month, monthBallots) ->
                val name = ParliamentFormat.ballotsPackage(chamber, month.take(4).toInt(), month.substring(5, 7).toInt())
                val sorted = monthBallots.sortedWith(compareBy({ it.date }, { it.number }, { it.id }))
                files[name] = json.encodeToString(BallotsPackage.serializer(), BallotsPackage(example = example, chamber = chamber, month = month, ballots = sorted)).encodeToByteArray()
            }
        }
        histories.values.forEach { (packages, _) -> files.putAll(packages) }
        // « Cette semaine au Parlement » (Week.kt) comes with the daily sources; `--history` alone keeps the previous one.
        if (only.isNotEmpty()) files[ParliamentWeekFormat.PACKAGE] = WeekImport(sources, only, out, log).bytes()
        val refreshedChambers = ballots.keys
        val keptRefs = previous.index?.packages.orEmpty().filter { ref ->
            ref.name !in files &&
                Chamber.entries.none { it in refreshedChambers && ref.name.startsWith(it.ballotsPrefix + "-") } &&
                histories.keys.none { ref.name.startsWith(it.packagePrefix) }
        }.onEach { ref -> readPackage(ref) ?: throw UnreadableSourceException("previous package ${ref.name} is damaged; import every source") }

        val refs = (files.map { (name, bytes) -> PackageRef(name, ParliamentFormat.sha256Hex(bytes), bytes.size.toLong()) } + keptRefs).sortedBy { it.name }
        val sourcesOfIndex = SourceId.entries.mapNotNull { source ->
            if (source in only) DataSource(source.id, source.sourceName, source.url, source.licence, sources.today) else previous.index?.source(source.id)
        } + HistorySourceId.entries.mapNotNull { source ->
            if (source in history) DataSource(source.id, source.sourceName, source.url, source.licence, sources.today) else previous.index?.source(source.id)
        }
        val index = ParliamentIndex(example = example, importedOn = sources.today, sources = sourcesOfIndex, packages = refs)
        val unsigned = ParliamentFormat.indexEvent(keys.publicKey, index, sources.createdAt)
        // The same index as before keeps its event (and signature): two identical imports give the same bytes.
        val event = previous.event?.takeIf { it.unsigned() == unsigned && it.verify() }
            ?: runBlocking { LocalEventSigner(keys).sign(unsigned) }.also { check(it.verify()) { "signature check failed" } }

        write(files, event.toJson().encodeToByteArray(), refs.map { it.name }.toSet())
        log(
            "imported (${if (example) "example" else "official"}, ${sources.today}): " +
                SourceId.entries.filter { it in rosters }.joinToString { "${it.id} ${rosters.getValue(it).members.size} member(s)" } +
                ballots.entries.sortedBy { it.key }.joinToString("") { ", ${it.key.name.lowercase()} ${it.value.size} ballot(s)" } +
                histories.entries.joinToString("") { ", ${it.key.id} ${it.value.second} ballot(s) in ${it.value.first.size} package(s)" } +
                "; ${refs.size} package(s)",
        )
        refs.forEach { log("  ${it.name} ${it.bytes} bytes") }
    }

    /**
     * Every new file goes to a temporary name in [out], then is renamed over the old one; the index
     * comes last, so a device never sees an index naming a package that is not there yet. Packages
     * no longer named by the index are removed afterwards.
     */
    private fun write(packages: Map<String, ByteArray>, index: ByteArray, keep: Set<String>) {
        Files.createDirectories(out)
        val staged = (packages.map { (name, bytes) -> "$name.json" to bytes } + (INDEX_FILE to index)).mapNotNull { (file, bytes) ->
            val target = out.resolve(file)
            if (target.isRegularFile() && Files.readAllBytes(target).contentEquals(bytes)) return@mapNotNull null
            val temporary = Files.createTempFile(out, ".import-", ".tmp")
            Files.write(temporary, bytes)
            temporary to target
        }
        staged.forEach { (temporary, target) ->
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        out.listDirectoryEntries("*.json").filter { it.name != INDEX_FILE }.forEach { file ->
            val name = file.name.removeSuffix(".json")
            if (ParliamentFormat.isPackageName(name) && name !in keep) Files.deleteIfExists(file)
        }
    }

    companion object {
        const val INDEX_FILE: String = "index.event.json"
    }
}
