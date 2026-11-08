package org.politisationpopulairefrancaise.core.voir.parlement

import kotlinx.coroutines.CancellationException
import org.politisationpopulairefrancaise.core.publication.OpenDataDownload
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** Where the data shown come from. */
enum class DataOrigin {
    /** The server answered with this index (or confirmed it had not changed). */
    SERVER,

    /** The server did not answer: the copy of the device is shown. */
    DEVICE,

    /** No server, or none answered and the device holds nothing: the fictional packages bundled with the application. */
    BUNDLED,
}

/** What the screens show: the trusted index, the catalog of the packages held, and what is missing. */
data class ParliamentSnapshot(
    val event: SignedEvent,
    val index: ParliamentIndex,
    val origin: DataOrigin,
    val catalog: ParliamentCatalog,
    /** Packages held on the device (or bundled) whose bytes match their fingerprint. */
    val held: Set<String>,
    /** Packages held in a version older than the one of the index (a newer version can be downloaded). */
    val outdated: Set<String>,
    /** A newer index sent by the server and refused (bad signature, untrusted key, older than the current one...). */
    val refused: IndexProblem? = null,
) {
    /** The index says the data are fictional ([EXEMPLE]), or a package does. */
    val example: Boolean get() = index.example || catalog.example

    val importDate: CivilDate? get() = index.importDate

    /** More than [ParliamentFormat.STALE_AFTER_DAYS] days since the import on [today]. */
    fun isStale(today: CivilDate): Boolean = ParliamentIndexReader.isStale(index, today)

    fun isHeld(name: String): Boolean = name in held

    /** The months of ballots of [chamber] announced by the index, most recent first. */
    fun months(chamber: Chamber): List<BallotMonth> = ParliamentCatalog.months(index, chamber)

    val membersRef: PackageRef? get() = index.packageRef(ParliamentFormat.MEMBERS_PACKAGE)

    /** The history packages announced by the index (`historique-*`, comparator of « Décider »), sorted by name. */
    val history: List<PackageRef> get() = index.packages.filter { ParliamentHistory.isHistoryPackage(it.name) }

    /** The history packages of [chamber] announced by the index. */
    fun history(chamber: Chamber): List<PackageRef> = history.filter { ParliamentHistory.parse(it.name)?.first == chamber }

    /** History packages absent from the device or held in an older version: what [ParliamentRepository.downloadHistory] fetches. */
    val historyToDownload: List<PackageRef> get() = history.filter { it.name !in held || it.name in outdated }

    /** The size to show before [ParliamentRepository.downloadHistory], in bytes. */
    val historyBytesToDownload: Long get() = historyToDownload.sumOf { it.bytes }
}

/** What [ParliamentRepository.open] found. */
sealed interface ParliamentOpening {
    data class Ready(val snapshot: ParliamentSnapshot) : ParliamentOpening

    /** No trusted index anywhere: not on the server, not on the device, not bundled. */
    data class Nothing(val refused: IndexProblem? = null) : ParliamentOpening
}

/** The outcome of downloading one package. */
sealed interface PackageOutcome {
    data object Done : PackageOutcome

    /** The bytes did not match the fingerprint of the index, or could not be read: nothing was kept. */
    data class Refused(val name: String) : PackageOutcome

    /** The server did not answer. */
    data class Unavailable(val name: String) : PackageOutcome

    /** The index does not list this package, or the server no longer has it. */
    data class Unknown(val name: String) : PackageOutcome
}

/**
 * The parliamentary open data on the device (D-065; contract docs/integration/voir-donnees-ouvertes.md,
 * section 4). The index is a NIP-01 event signed by a trusted import key ([OpenDataTrust]); it is
 * never replaced by an older one, and lists the sha256 of every package, checked before a package
 * is kept. Only [open] (the index, a few hundred bytes) and [download] (one package, after its size
 * was shown and the user asked for it) use the network; nothing is sent but the package name.
 *
 * Without a server, or when no server answered and the device holds nothing, the fictional packages
 * bundled with the application are read through [bundled] (`index` gives the signed index event,
 * any other name the package of that name) and checked the same way; they are never stored, so
 * they never stand in the way of a real index.
 *
 * Not thread-safe: callers run one operation at a time.
 */
class ParliamentRepository(
    private val remote: RemoteEvents?,
    private val storage: ParliamentStorage,
    private val bundled: suspend (String) -> ByteArray? = { null },
    private val trust: OpenDataTrust = OpenDataKeys.bundledExamples(),
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** The last snapshot, null before [open]. */
    var snapshot: ParliamentSnapshot? = null
        private set

    /** Bundled packages read in memory, by name (never stored). */
    private val bundledPackages = HashMap<String, String>()

    /** Packages read from the device, by name, with the sha256 they were read for. */
    private val parsed = HashMap<String, Pair<String, Any>>()

    val today: CivilDate get() = CivilDate.fromEpochSeconds(clock())

    /**
     * Finds the index to trust: the server's when it answers with a valid one, else the copy of the
     * device, else the bundled examples. A refused index leaves the current one in place and is
     * reported in [ParliamentSnapshot.refused].
     */
    suspend fun open(): ParliamentOpening {
        val storedEvent = storedEvent()
        var refused: IndexProblem? = null
        if (remote != null) {
            val answer = try {
                remote.fetchParliament(name = null, etag = storage.index()?.takeIf { storedEvent != null }?.let { ParliamentFormat.sha256Hex(it.eventJson.encodeToByteArray()) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OpenDataDownload.Unavailable(e)
            }
            when (answer) {
                is OpenDataDownload.Body -> {
                    val text = utf8(answer.bytes)
                    val event = text?.let { runCatching { SignedEvent.fromJson(it) }.getOrNull() }
                    if (text == null || event == null) {
                        refused = IndexProblem.UNREADABLE
                    } else {
                        when (val reading = ParliamentIndexReader.read(event, trust, storedEvent?.first)) {
                            is IndexReading.Accepted -> {
                                storage.saveIndex(StoredIndex(text, reading.index.importedOn, clock()))
                                prune(reading.index)
                                return ready(reading.event, reading.index, DataOrigin.SERVER, null)
                            }
                            is IndexReading.Refused -> refused = reading.problem
                        }
                    }
                }
                OpenDataDownload.NotModified -> if (storedEvent != null) return ready(storedEvent.first, storedEvent.second, DataOrigin.SERVER, null)
                OpenDataDownload.UnknownPackage, is OpenDataDownload.Unavailable -> Unit
            }
        }
        // The server did not answer, or sent an index that was refused: the copy of the device stays.
        if (storedEvent != null) return ready(storedEvent.first, storedEvent.second, DataOrigin.DEVICE, refused)
        return openBundled(refused)
    }

    /**
     * Downloads the package [name] announced by the index (a gesture of the user, after its size was
     * shown). The bytes are kept only when they match the sha256 and the size of the index and read
     * as the package the name announces. With the bundled examples, the package is already there.
     */
    suspend fun download(name: String): PackageOutcome {
        val current = snapshot ?: return PackageOutcome.Unknown(name)
        val ref = current.index.packageRef(name) ?: return PackageOutcome.Unknown(name)
        if (current.origin == DataOrigin.BUNDLED) return if (name in current.held) PackageOutcome.Done else PackageOutcome.Unavailable(name)
        val server = remote ?: return PackageOutcome.Unavailable(name)
        val answer = try {
            server.fetchParliament(name = name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenDataDownload.Unavailable(e)
        }
        val bytes = when (answer) {
            is OpenDataDownload.Body -> answer.bytes
            OpenDataDownload.UnknownPackage -> return PackageOutcome.Unknown(name)
            OpenDataDownload.NotModified, is OpenDataDownload.Unavailable -> return PackageOutcome.Unavailable(name)
        }
        if (!ParliamentIndexReader.matches(ref, bytes)) return PackageOutcome.Refused(name)
        val text = utf8(bytes) ?: return PackageOutcome.Refused(name)
        val pkg = readPackage(name, text) ?: return PackageOutcome.Refused(name)
        storage.savePackage(StoredParliamentPackage(name, ref.sha256, text, sourceDate(current.index, name), clock()))
        // A history package is read again on demand ([history]), never kept parsed in memory.
        if (pkg !is HistoryPackage) parsed[name] = ref.sha256 to pkg
        snapshot = build(current.event, current.index, current.origin, current.refused)
        return PackageOutcome.Done
    }

    /**
     * Downloads every history package missing or outdated on the device, all at once and for every
     * party (a gesture of the user, after [ParliamentSnapshot.historyBytesToDownload] was shown):
     * never one request per party, which would tell the server which party interests the person.
     * Stops at the first package the server cannot give; returns the outcome of each package tried.
     */
    suspend fun downloadHistory(): Map<String, PackageOutcome> {
        val current = snapshot ?: return emptyMap()
        val outcomes = LinkedHashMap<String, PackageOutcome>()
        for (ref in current.historyToDownload) {
            val outcome = download(ref.name)
            outcomes[ref.name] = outcome
            if (outcome is PackageOutcome.Unavailable) break
        }
        return outcomes
    }

    /**
     * Reads the history package [name] held on the device (or bundled), checked again against the
     * sha256 of the current index; null when the index does not list it, it is not held, or its
     * bytes no longer match. Read on demand and never kept in memory (a legislature weighs a few Mo).
     */
    fun history(name: String): HistoryPackage? {
        val current = snapshot ?: return null
        val ref = current.index.packageRef(name)?.takeIf { ParliamentHistory.isHistoryPackage(name) } ?: return null
        val json = if (current.origin == DataOrigin.BUNDLED) {
            bundledPackages[name]
        } else {
            storage.storedPackage(name)?.takeIf { it.sha256 == ref.sha256 }?.json
        } ?: return null
        if (!ParliamentIndexReader.matches(ref, json.encodeToByteArray())) return null
        return ParliamentHistory.read(name, json)
    }

    /** Erases the index and every package of the device (the bundled examples stay in the application). */
    fun forget() {
        storage.forget()
        parsed.clear()
        snapshot = null
    }

    private suspend fun openBundled(refused: IndexProblem?): ParliamentOpening {
        val text = runCatching { bundled(INDEX_NAME) }.getOrNull()?.let(::utf8) ?: return ParliamentOpening.Nothing(refused)
        val event = runCatching { SignedEvent.fromJson(text) }.getOrNull() ?: return ParliamentOpening.Nothing(refused)
        val reading = ParliamentIndexReader.read(event, trust) as? IndexReading.Accepted ?: return ParliamentOpening.Nothing(refused)
        bundledPackages.clear()
        for (ref in reading.index.packages) {
            val bytes = runCatching { bundled(ref.name) }.getOrNull() ?: continue
            if (!ParliamentIndexReader.matches(ref, bytes)) continue
            val json = utf8(bytes) ?: continue
            if (readPackage(ref.name, json) != null) bundledPackages[ref.name] = json
        }
        return ready(reading.event, reading.index, DataOrigin.BUNDLED, refused)
    }

    private fun ready(event: SignedEvent, index: ParliamentIndex, origin: DataOrigin, refused: IndexProblem?): ParliamentOpening.Ready {
        val built = build(event, index, origin, refused)
        snapshot = built
        return ParliamentOpening.Ready(built)
    }

    private fun build(event: SignedEvent, index: ParliamentIndex, origin: DataOrigin, refused: IndexProblem?): ParliamentSnapshot {
        val held = LinkedHashSet<String>()
        val outdated = LinkedHashSet<String>()
        var members: MembersPackage? = null
        val ballots = ArrayList<BallotsPackage>()
        for (ref in index.packages) {
            if (ParliamentHistory.isHistoryPackage(ref.name)) {
                // Checked when kept and again when read ([history]); never parsed here.
                if (origin == DataOrigin.BUNDLED) {
                    if (ref.name in bundledPackages) held += ref.name
                } else {
                    val sha = storage.storedPackage(ref.name)?.sha256 ?: continue
                    held += ref.name
                    if (sha != ref.sha256) outdated += ref.name
                }
                continue
            }
            val pkg: Any = if (origin == DataOrigin.BUNDLED) {
                bundledPackages[ref.name]?.let { readPackage(ref.name, it) }
            } else {
                packageOnDevice(ref.name)?.also { (sha, _) -> if (sha != ref.sha256) outdated += ref.name }?.second
            } ?: continue
            held += ref.name
            when (pkg) {
                is MembersPackage -> members = pkg
                is BallotsPackage -> ballots += pkg
            }
        }
        return ParliamentSnapshot(event, index, origin, ParliamentCatalog(members, ballots), held, outdated, refused)
    }

    /** The trusted index of the device, checked again (a key may have been revoked by an update). */
    private fun storedEvent(): Pair<SignedEvent, ParliamentIndex>? {
        val stored = storage.index() ?: return null
        val event = runCatching { SignedEvent.fromJson(stored.eventJson) }.getOrNull()
        val reading = event?.let { ParliamentIndexReader.read(it, trust) }
        if (reading !is IndexReading.Accepted) {
            storage.forget()
            parsed.clear()
            return null
        }
        return reading.event to reading.index
    }

    /** Packages the new index no longer lists leave the device. */
    private fun prune(index: ParliamentIndex) {
        val listed = index.packages.map { it.name }.toSet()
        storage.packageNames().filter { it !in listed }.forEach {
            storage.deletePackage(it)
            parsed.remove(it)
        }
    }

    /** A package of the device with the sha256 it was kept for; null when absent, altered or unreadable. */
    private fun packageOnDevice(name: String): Pair<String, Any>? {
        val stored = storage.storedPackage(name) ?: return null
        parsed[name]?.let { if (it.first == stored.sha256) return it }
        if (ParliamentFormat.sha256Hex(stored.json.encodeToByteArray()) != stored.sha256) return null
        val pkg = readPackage(name, stored.json) ?: return null
        return (stored.sha256 to pkg).also { parsed[name] = it }
    }

    private fun sourceDate(index: ParliamentIndex, name: String): String? {
        ParliamentHistory.parse(name)?.let { (chamber, _) -> return index.source(ParliamentHistory.chamberCode(chamber) + "-history")?.updatedOn ?: index.importedOn }
        val chamber = Chamber.entries.firstOrNull { name.startsWith(it.ballotsPrefix + "-") }
        return chamber?.let { index.source(ParliamentCatalog.sourceId(it))?.updatedOn } ?: index.importedOn
    }

    private fun utf8(bytes: ByteArray): String? = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()

    companion object {
        /** The name under which [bundled] gives the signed index event. */
        const val INDEX_NAME: String = "index"

        /**
         * Reads a package as the one its name announces: `membres` as the members, a ballots name as
         * the ballots of that assembly and month, a history name as the [HistoryPackage] of that
         * assembly and period ([ParliamentHistory.read]). Strict on these, tolerant on unknown fields; an
         * unreadable package gives null and is ignored.
         */
        fun readPackage(name: String, json: String): Any? {
            val format = ParliamentFormat.json
            if (name == ParliamentFormat.MEMBERS_PACKAGE) {
                return runCatching { format.decodeFromString(MembersPackage.serializer(), json) }.getOrNull()?.takeIf { it.format == ParliamentFormat.FORMAT }
            }
            if (ParliamentHistory.isHistoryPackage(name)) return ParliamentHistory.read(name, json)
            val chamber = Chamber.entries.firstOrNull { name.startsWith(it.ballotsPrefix + "-") } ?: return null
            val month = name.removePrefix(chamber.ballotsPrefix + "-")
            return runCatching { format.decodeFromString(BallotsPackage.serializer(), json) }.getOrNull()
                ?.takeIf { it.format == ParliamentFormat.FORMAT && it.chamber == chamber && it.month == month }
        }
    }
}
