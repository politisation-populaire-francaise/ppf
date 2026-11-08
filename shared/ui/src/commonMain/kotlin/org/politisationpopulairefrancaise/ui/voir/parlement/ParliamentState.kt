package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res

/** A page of the parliamentary screen above its list. */
sealed interface ParliamentPage {
    data class OfMember(val id: String) : ParliamentPage
    data class OfBallot(val id: String) : ParliamentPage
}

/**
 * The state of the screen « Parlementaires », kept by the shell for the whole run like the state of
 * "Voir": whether it is open, its own stack of pages (the back button pops it, then closes the
 * screen), the search and the filters, and what the repository found. The network is used only by
 * [open] (the signed index, a few hundred bytes) and [download] (a gesture of the user).
 */
class ParliamentState(
    private val repository: ParliamentRepository,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    var isOpen: Boolean by mutableStateOf(false)
        private set

    /** The open pages above the list, the last one on top. */
    val stack = mutableStateListOf<ParliamentPage>()

    val current: ParliamentPage? get() = stack.lastOrNull()

    var loading: Boolean by mutableStateOf(false)
        private set

    /** Null before the first loading. */
    var opening: ParliamentOpening? by mutableStateOf(null)
        private set

    var snapshot: ParliamentSnapshot? by mutableStateOf(null)
        private set

    /** The package being downloaded. */
    var downloading: String? by mutableStateOf(null)
        private set

    /** The outcome of the last download, to say why it failed. */
    var lastOutcome: PackageOutcome? by mutableStateOf(null)
        private set

    var query: String by mutableStateOf("")
    var chamberFilter: Chamber? by mutableStateOf(null)
    var groupFilter: String? by mutableStateOf(null)

    val today get() = repository.today

    /** Opens the screen on its list; the index is asked again on each opening. */
    fun open() {
        stack.clear()
        isOpen = true
        load()
    }

    /** Opens the screen directly on a member's page (from the sheet of a person of "Voir"). */
    fun openMember(id: String) {
        if (!isOpen) {
            stack.clear()
            isOpen = true
            load()
        }
        show(ParliamentPage.OfMember(id))
    }

    fun show(page: ParliamentPage) {
        if (stack.lastOrNull() != page) stack.add(page)
    }

    /** Back one page, then out of the screen. */
    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else close()
    }

    fun close() {
        stack.clear()
        isOpen = false
    }

    fun selectChamber(chamber: Chamber?) {
        chamberFilter = chamber
        groupFilter = null
    }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val result = lock.withLock { withContext(Dispatchers.Default) { repository.open() } }
                opening = result
                snapshot = (result as? ParliamentOpening.Ready)?.snapshot
            } finally {
                loading = false
            }
        }
    }

    /** Downloads one package of the index (members or a month of ballots), after its size was shown. */
    fun download(name: String) {
        if (downloading != null) return
        downloading = name
        lastOutcome = null
        scope.launch {
            try {
                val outcome = lock.withLock { withContext(Dispatchers.Default) { repository.download(name) } }
                lastOutcome = outcome
                snapshot = repository.snapshot
            } finally {
                downloading = null
            }
        }
    }
}

/**
 * The bundled example packages of the import tool: `files/parlement/index.event.json` (the signed
 * index event) for [ParliamentRepository.INDEX_NAME], `files/parlement/<name>.json` for a package;
 * null when absent.
 */
suspend fun bundledParliamentFile(name: String): ByteArray? {
    val file = if (name == ParliamentRepository.INDEX_NAME) "index.event.json" else "$name.json"
    return runCatching { Res.readBytes("files/parlement/$file") }.getOrNull()
}

@Composable
fun rememberParliamentState(dependencies: AppDependencies): ParliamentState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        val storage = dependencies.database?.let(::ParliamentStore) ?: InMemoryParliamentStorage()
        ParliamentState(ParliamentRepository(dependencies.remote, storage, trust = dependencies.openDataTrust, bundled = ::bundledParliamentFile), scope)
    }
}
