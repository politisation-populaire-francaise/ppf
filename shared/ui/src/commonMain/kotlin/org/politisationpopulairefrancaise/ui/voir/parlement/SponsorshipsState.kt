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
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsHolding
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsLoader

/**
 * « Parrainages de la présidentielle 2027 » (VO-37), kept with the state of the Parliament: what the device holds
 * of the package `parrainages-2027`, its download on a gesture (size shown), and the department searched.
 */
class SponsorshipsState(private val loader: SponsorshipsLoader, private val scope: CoroutineScope) {
    private val lock = Mutex()

    var holding: SponsorshipsHolding? by mutableStateOf(null)
        private set

    var loaded: Boolean by mutableStateOf(false)
        private set

    var downloading: Boolean by mutableStateOf(false)
        private set

    var lastOutcome: PackageOutcome? by mutableStateOf(null)
        private set

    /** The department typed in the search, folded comparison on the page. */
    var department: String by mutableStateOf("")

    /** The size announced by the index, null before the publications. */
    fun bytes(snapshot: ParliamentSnapshot): Long? = loader.ref(snapshot)?.bytes

    /** Reads the device again (never the network). */
    fun refresh(snapshot: ParliamentSnapshot) {
        scope.launch {
            lock.withLock {
                holding = withContext(Dispatchers.Default) { loader.held(snapshot) }
                loaded = true
            }
        }
    }

    /** Downloads the package announced by the index, after its size was shown. */
    fun download(snapshot: ParliamentSnapshot) {
        if (downloading) return
        downloading = true
        lastOutcome = null
        scope.launch {
            try {
                lock.withLock {
                    lastOutcome = withContext(Dispatchers.Default) { loader.download(snapshot) }
                    holding = withContext(Dispatchers.Default) { loader.held(snapshot) }
                }
            } finally {
                downloading = false
            }
        }
    }

    /** The quick erase (S-44): what is on screen; the stored package goes with the parliamentary tables. */
    fun forgetOnScreen() {
        holding = null
        department = ""
        lastOutcome = null
    }
}
