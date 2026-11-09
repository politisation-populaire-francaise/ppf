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
import org.politisationpopulairefrancaise.core.voir.application.ApplicationHolding
import org.politisationpopulairefrancaise.core.voir.application.ApplicationLoader
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot

/**
 * « Applicomètre » (VO-36), kept with the state of the Parliament: what the device holds of the packages
 * `application-lois-<législature>`, the download of one of them on a gesture (size shown), and the search.
 */
class LawApplicationState(private val loader: ApplicationLoader, private val scope: CoroutineScope) {
    private val lock = Mutex()

    var holding: ApplicationHolding? by mutableStateOf(null)
        private set

    var loaded: Boolean by mutableStateOf(false)
        private set

    /** The legislature being downloaded. */
    var downloading: Int? by mutableStateOf(null)
        private set

    var lastOutcome: PackageOutcome? by mutableStateOf(null)
        private set

    var query: String by mutableStateOf("")
    var pendingOnly: Boolean by mutableStateOf(false)

    /** Null for every legislature held. */
    var legislature: Int? by mutableStateOf(null)

    /** The legislatures the index announces, with their size; empty before the first import. */
    fun available(snapshot: ParliamentSnapshot): List<Pair<Int, PackageRef>> = loader.available(snapshot)

    /** Reads the device again (never the network). */
    fun refresh(snapshot: ParliamentSnapshot) {
        scope.launch {
            lock.withLock {
                holding = withContext(Dispatchers.Default) { loader.held(snapshot) }
                loaded = true
            }
        }
    }

    /** Downloads the package of [legislature] announced by the index, after its size was shown. */
    fun download(snapshot: ParliamentSnapshot, legislature: Int) {
        if (downloading != null) return
        downloading = legislature
        lastOutcome = null
        scope.launch {
            try {
                lock.withLock {
                    lastOutcome = withContext(Dispatchers.Default) { loader.download(snapshot, legislature) }
                    holding = withContext(Dispatchers.Default) { loader.held(snapshot) }
                    loaded = true
                }
            } finally {
                downloading = null
            }
        }
    }

    /** The quick erase (S-44): what is on screen; the stored packages go with the parliamentary tables. */
    fun forgetOnScreen() {
        holding = null
        loaded = false
        query = ""
        pendingOnly = false
        legislature = null
        lastOutcome = null
    }
}
