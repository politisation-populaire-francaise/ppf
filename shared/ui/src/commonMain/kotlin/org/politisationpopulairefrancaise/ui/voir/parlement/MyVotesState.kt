package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.MyElus
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.Member
import org.politisationpopulairefrancaise.core.voir.parlement.MyParliamentarians
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentCatalog
import org.politisationpopulairefrancaise.core.voir.parlement.VoteAlert
import org.politisationpopulairefrancaise.core.voir.parlement.VoteAlertStorage
import org.politisationpopulairefrancaise.core.voir.parlement.VoteAlerts

/** Why a followed word was not added. */
enum class WordProblem { EMPTY, ALREADY, TOO_MANY }

/**
 * « Mes élus ont voté » (VO-34, D-102), kept with the state of the Parliament for the whole run: the
 * officials of the commune chosen in « Mes élus » (read from the device only), the members of the
 * European Parliament chosen (none by default), the followed words and « seulement en Wi-Fi » (on by
 * default). Everything stays on the device: the notification source reads the same [storage].
 */
class MyVotesState(
    private val storage: VoteAlertStorage,
    private val elus: ElusDirectory,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    private val lock = Mutex()

    /** The officials of the chosen commune; null while none is chosen. */
    var mine: MyElus? by mutableStateOf(null)
        private set

    var loaded: Boolean by mutableStateOf(false)
        private set

    var words: List<String> by mutableStateOf(emptyList())
        private set

    var europeans: Set<String> by mutableStateOf(emptySet())
        private set

    var wifiOnly: Boolean by mutableStateOf(true)
        private set

    var wordProblem: WordProblem? by mutableStateOf(null)
        private set

    /** Reads the device again: the commune may have been chosen in Comprendre in the meantime. */
    fun load() {
        scope.launch {
            lock.withLock {
                withContext(Dispatchers.Default) {
                    val found = runCatching { elus.mine() }.getOrNull()
                    val stored = Triple(storage.words(), storage.europeans(), storage.wifiOnly())
                    found to stored
                }.let { (found, stored) ->
                    mine = found
                    words = stored.first
                    europeans = stored.second
                    wifiOnly = stored.third
                    loaded = true
                }
            }
        }
    }

    /** My parliamentarians among the members of [catalog]; null without a commune chosen. */
    fun parliamentarians(catalog: ParliamentCatalog): MyParliamentarians? =
        mine?.let { MyParliamentarians.of(it.officials, catalog.members(), europeans) }
            ?: europeans.takeIf { it.isNotEmpty() }?.let { ids -> MyParliamentarians(emptyList(), emptyList(), ids.mapNotNull(catalog::member)) }

    /** The ballots on the device where one of my parliamentarians is listed, most recent first. */
    fun recentVotes(catalog: ParliamentCatalog, limit: Int = MAX_SHOWN): List<VoteAlert> {
        val members = parliamentarians(catalog)?.members.orEmpty()
        return VoteAlerts.newVotes(members, catalog, emptySet()).take(limit)
    }

    /** Adds a followed word; says why when it cannot. */
    fun addWord(word: String): Boolean {
        val clean = word.trim()
        val added = storage.addWord(clean, clock())
        wordProblem = when {
            added -> null
            FrenchText.words(clean).isEmpty() -> WordProblem.EMPTY
            words.size >= VoteAlerts.MAX_FOLLOWED_WORDS -> WordProblem.TOO_MANY
            else -> WordProblem.ALREADY
        }
        if (added) words = storage.words()
        return added
    }

    fun removeWord(word: String) {
        storage.removeWord(word)
        words = storage.words()
        wordProblem = null
    }

    fun setWifiOnly(on: Boolean) {
        storage.setWifiOnly(on)
        wifiOnly = on
    }

    fun setEuropeans(ids: Set<String>) {
        storage.setEuropeans(ids)
        europeans = ids
    }

    fun toggleEuropean(id: String) = setEuropeans(if (id in europeans) europeans - id else europeans + id)

    /** The quick erase (S-44): what is on screen; the storage is erased by its own part. */
    fun forgetOnScreen() {
        words = emptyList()
        europeans = emptySet()
        wifiOnly = true
        wordProblem = null
    }

    companion object {
        /** The page shows at most this many ballots, most recent first. */
        const val MAX_SHOWN: Int = 40

        /** The members of the European Parliament of the catalog, by national list (alphabetical), for the choice. */
        fun europeansByList(catalog: ParliamentCatalog): List<Pair<String?, List<Member>>> =
            catalog.members(chamber = Chamber.PE)
                .groupBy { it.nationalParty }
                .entries
                .sortedWith(compareBy(nullsLast<String>()) { it.key })
                .map { it.key to it.value }
    }
}
