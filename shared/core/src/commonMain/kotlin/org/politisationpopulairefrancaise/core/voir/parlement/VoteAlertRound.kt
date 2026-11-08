package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds

/** The network as the alert sees it before downloading anything (D-102). */
data class NetworkConditions(
    /** The user or the system asked to save data: nothing is downloaded in the background. */
    val dataSaver: Boolean,
    /** Wi-Fi or another unmetered network; « seulement en Wi-Fi » (on by default) waits for it. */
    val unmetered: Boolean,
)

/**
 * What the device remembers for « Mes élus ont voté » (VO-34), on the device only and never sent:
 * the ballots already seen (dated by their reception on the device), the followed words, the members
 * of the European Parliament chosen (none by default, D-102) and « seulement en Wi-Fi » (on by default).
 */
interface VoteAlertStorage {
    /** When the first round ran; null before: that round marks everything seen without alert. */
    fun startedAt(): Long?

    fun start(at: Long)

    fun seen(): Set<String>

    fun markSeen(ids: Collection<String>, at: Long)

    /** The followed words or phrases, in the order they were added. */
    fun words(): List<String>

    /** Follows [word]; false when it has no word, is already followed or [VoteAlerts.MAX_FOLLOWED_WORDS] are. */
    fun addWord(word: String, at: Long): Boolean

    fun removeWord(word: String)

    /** Identifiers of the members of the European Parliament chosen by the person. */
    fun europeans(): Set<String>

    fun setEuropeans(ids: Set<String>)

    fun wifiOnly(): Boolean

    fun setWifiOnly(on: Boolean)

    /** The quick erase (S-44) and « Effacer » of the page: everything above goes. */
    fun forget()
}

/** [VoteAlertStorage] in memory: tests, previews, a device without database. */
class InMemoryVoteAlertStorage : VoteAlertStorage {
    private var started: Long? = null
    private val seen = LinkedHashMap<String, Long>()
    private val words = mutableListOf<String>()
    private var europeans: Set<String> = emptySet()
    private var wifiOnly = true

    override fun startedAt(): Long? = started

    override fun start(at: Long) {
        if (started == null) started = at
    }

    override fun seen(): Set<String> = seen.keys.toSet()

    override fun markSeen(ids: Collection<String>, at: Long) {
        ids.forEach { seen.getOrPut(it) { at } }
    }

    override fun words(): List<String> = words.toList()

    override fun addWord(word: String, at: Long): Boolean {
        val clean = word.trim()
        if (FrenchText.words(clean).isEmpty() || words.size >= VoteAlerts.MAX_FOLLOWED_WORDS) return false
        if (words.any { FrenchText.fold(it) == FrenchText.fold(clean) }) return false
        words += clean
        return true
    }

    override fun removeWord(word: String) {
        words.removeAll { FrenchText.fold(it) == FrenchText.fold(word) }
    }

    override fun europeans(): Set<String> = europeans

    override fun setEuropeans(ids: Set<String>) {
        europeans = ids
    }

    override fun wifiOnly(): Boolean = wifiOnly

    override fun setWifiOnly(on: Boolean) {
        wifiOnly = on
    }

    override fun forget() {
        started = null
        seen.clear()
        words.clear()
        europeans = emptySet()
        wifiOnly = true
    }
}

/** What one round found: the ballots of my parliamentarians and those of the followed words, all new. */
data class VoteAlertFindings(
    /** When the device received them: the date of the alert (a ballot's own date has no hour and comes days later). */
    val receivedAt: Long,
    val votes: List<VoteAlert>,
    val words: List<WordAlert>,
    /** The packages downloaded by this round. */
    val downloaded: List<String>,
    /** The first round: everything on the device was marked seen, nothing is told. */
    val first: Boolean = false,
) {
    val isEmpty: Boolean get() = votes.isEmpty() && words.isEmpty()
}

/**
 * One round of « Mes élus ont voté » (VO-34, D-102; docs/integration/voir-alertes-elus.md, section 5), run by
 * the notification source only while « Votes au Parlement » is on:
 * 1. nothing at all in data saver mode, nor on a metered network while « seulement en Wi-Fi » is on: not even
 *    the index is asked;
 * 2. the signed index, then the members and the packages of the current and the previous month of the
 *    assemblies of my parliamentarians only (the Assemblée and the Sénat as well when words are followed),
 *    when they are missing or changed: whole packages, the same for everyone, never a request per member;
 * 3. the new ballots, sorted on the device; all of them marked seen, so that each is told once.
 */
class VoteAlertRound(
    private val repository: ParliamentRepository,
    private val storage: VoteAlertStorage,
    /** My parliamentarians from the members of [ParliamentCatalog] and the chosen Europeans; null without a commune chosen. */
    private val parliamentarians: (ParliamentCatalog, Set<String>) -> MyParliamentarians?,
    private val network: () -> NetworkConditions,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    suspend fun run(): VoteAlertFindings? {
        val conditions = network()
        if (conditions.dataSaver || (storage.wifiOnly() && !conditions.unmetered)) return null
        val words = storage.words()
        val opening = repository.open() as? ParliamentOpening.Ready ?: return null
        val downloaded = mutableListOf<String>()
        suspend fun keepUpToDate(name: String) {
            val snapshot = repository.snapshot ?: return
            if (snapshot.origin == DataOrigin.BUNDLED || snapshot.index.packageRef(name) == null) return
            if (snapshot.isHeld(name) && name !in snapshot.outdated) return
            if (repository.download(name) == PackageOutcome.Done) downloaded += name
        }
        keepUpToDate(ParliamentFormat.MEMBERS_PACKAGE)
        val europeans = storage.europeans()
        val before = repository.snapshot ?: opening.snapshot
        val mine = parliamentarians(before.catalog, europeans)
        if (mine == null && words.isEmpty()) return null
        val chambers = chambersOf(mine) + if (words.isEmpty()) emptySet() else setOf(Chamber.AN, Chamber.SENAT)
        val months = recentMonths(clock())
        chambers.sortedBy { it.ordinal }.forEach { chamber ->
            before.months(chamber).filter { it.month in months }.forEach { keepUpToDate(it.name) }
        }

        val now = clock()
        val catalog = (repository.snapshot ?: before).catalog
        val all = VoteAlerts.allBallotIds(catalog)
        if (storage.startedAt() == null) {
            storage.markSeen(all, now)
            storage.start(now)
            return VoteAlertFindings(now, emptyList(), emptyList(), downloaded, first = true)
        }
        val seen = storage.seen()
        // Matched again on the final catalog: the members may have just been downloaded.
        val members = parliamentarians(catalog, europeans)?.members.orEmpty()
        val votes = VoteAlerts.newVotes(members, catalog, seen)
        val found = VoteAlerts.newWordMatches(words, catalog, seen)
        storage.markSeen(all - seen, now)
        return VoteAlertFindings(now, votes, found, downloaded)
    }

    companion object {
        /** The assemblies whose packages are kept up to date for [mine]: a matched member or not, an official of it. */
        fun chambersOf(mine: MyParliamentarians?): Set<Chamber> {
            if (mine == null) return emptySet()
            return buildSet {
                if (mine.deputies.isNotEmpty()) add(Chamber.AN)
                if (mine.senators.isNotEmpty()) add(Chamber.SENAT)
                if (mine.europeans.isNotEmpty()) add(Chamber.PE)
            }
        }

        /** The current and the previous month of [now], as the packages name them (`AAAA-MM`). */
        fun recentMonths(now: Long): Set<String> {
            val today = CivilDate.fromEpochSeconds(now)
            val previous = if (today.month == 1) CivilDate(today.year - 1, 12, 1) else CivilDate(today.year, today.month - 1, 1)
            return setOf(today, previous).map { it.toIso().take(7) }.toSet()
        }
    }
}
