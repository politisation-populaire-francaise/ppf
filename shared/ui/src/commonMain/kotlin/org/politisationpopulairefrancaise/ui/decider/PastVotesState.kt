package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.decider.CandidateVotes
import org.politisationpopulairefrancaise.core.decider.ComparatorSheet
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.voir.parlement.bundledParliamentFile

/** Where the history packages of "Voir" stand on this device, for the past votes of the comparator (D-117). */
sealed interface PastVotesStatus {
    data object Loading : PastVotesStatus

    /** [names]: the history packages the index lists; [bytesToDownload]: what is missing or outdated, for every party at once. */
    data class Ready(val names: List<String>, val bytesToDownload: Long, val example: Boolean) : PastVotesStatus

    /** No trusted index of the parliamentary data anywhere. */
    data object Unavailable : PastVotesStatus
}

/**
 * The past votes of the comparator, computed on the device from the history packages of "Voir" (D-066, D-117):
 * the server never learns which candidate or theme is looked at. The packages are downloaded all at once, for
 * every party, by a gesture of the user; each candidate's ballots are then read one package at a time.
 */
class PastVotesState(private val repository: ParliamentRepository, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val votes = mutableStateMapOf<String, CandidateVotes>()

    var status: PastVotesStatus by mutableStateOf(PastVotesStatus.Loading)
        private set

    var downloading: Boolean by mutableStateOf(false)
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        reload()
    }

    private fun reload() {
        scope.launch {
            val opening = lock.withLock { withContext(Dispatchers.Default) { repository.open() } }
            status = when (opening) {
                is ParliamentOpening.Ready -> PastVotesStatus.Ready(
                    names = opening.snapshot.history.map { it.name },
                    bytesToDownload = opening.snapshot.historyBytesToDownload,
                    example = opening.snapshot.example,
                )
                is ParliamentOpening.Nothing -> PastVotesStatus.Unavailable
            }
            votes.clear()
        }
    }

    /** Downloads every history package missing on the device, for every party at once (never one request per party). */
    fun downloadAll() {
        if (downloading) return
        downloading = true
        scope.launch {
            try {
                lock.withLock { withContext(Dispatchers.Default) { repository.downloadHistory() } }
            } finally {
                downloading = false
            }
            reload()
        }
    }

    private fun key(sheet: ComparatorSheet, candidate: ContentAddress) = "${sheet.article.event.id}|$candidate"

    /** The ballots of [candidate] already read, or null. */
    fun cached(sheet: ComparatorSheet, candidate: ContentAddress): CandidateVotes? = votes[key(sheet, candidate)]

    /** Reads the ballots of [candidate] from the packages on the device, once the index is open. */
    suspend fun read(sheet: ComparatorSheet, candidate: ContentAddress) {
        val ready = status as? PastVotesStatus.Ready ?: return
        val key = key(sheet, candidate)
        if (key in votes) return
        val result = lock.withLock {
            withContext(Dispatchers.Default) { CandidateVotes.collect(sheet, candidate, ready.names, repository::history) }
        }
        votes[key] = result
    }
}

@Composable
fun rememberPastVotesState(dependencies: AppDependencies): PastVotesState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        val storage = dependencies.database?.let(::ParliamentStore) ?: InMemoryParliamentStorage()
        PastVotesState(ParliamentRepository(dependencies.remote, storage, trust = dependencies.openDataTrust, bundled = ::bundledParliamentFile), scope)
    }
}
