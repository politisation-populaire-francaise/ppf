package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.VoirAxis
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.PackageOutcome
import org.politisationpopulairefrancaise.core.voir.parlement.PackageRef
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentSnapshot
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentStore
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekLoader
import org.politisationpopulairefrancaise.core.voir.parlement.WeekHolding
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.voir.VoirState

/**
 * Where an item of the week leads: the bill of "Voir" whose `ppf-official-id` is the legislative
 * file of the item, and the other spaces (« Qui décide de ça ? » in "Comprendre", interpellation
 * in "Décider", which has no page of its own yet).
 */
class ParliamentWeekLinks(
    val billFor: (dossier: String) -> LegislativeText? = { null },
    val openBill: (LegislativeText) -> Unit = {},
    val navigate: (Space) -> Unit = {},
)

/**
 * The state of « Cette semaine au Parlement » (VO-31), kept by the shell for the whole run next to
 * [ParliamentState], whose signed index it reads. The network is used only by [download]: a
 * gesture of the user, or the opening of « En direct » (and of the page) when a version of the
 * package is already on the device and the data saver is off ([refresh] with `autoUpdate`).
 */
class ParliamentWeekState(
    val parliament: ParliamentState,
    private val loader: ParliamentWeekLoader,
    private val scope: CoroutineScope,
    private val dataSaver: () -> Boolean = { false },
    var links: ParliamentWeekLinks = ParliamentWeekLinks(),
) {
    private val lock = Mutex()

    /** The package on the device for the current index; null when there is none (or not read yet). */
    var holding: WeekHolding? by mutableStateOf(null)
        private set

    /** The snapshot [holding] was read for; null before the first reading. */
    var checkedFor: ParliamentSnapshot? by mutableStateOf(null)
        private set

    var downloading: Boolean by mutableStateOf(false)
        private set

    var lastOutcome: PackageOutcome? by mutableStateOf(null)
        private set

    var chamberFilter: Chamber? by mutableStateOf(null)

    val snapshot: ParliamentSnapshot? get() = parliament.snapshot

    val today get() = parliament.today

    /** The package announced by the index, with its size; null when the server does not offer it. */
    val ref: PackageRef? get() = snapshot?.let(loader::ref)

    /** The index has been read and the device checked for it. */
    val checked: Boolean get() = snapshot != null && checkedFor === snapshot

    /**
     * Reads what the device holds for the current index (never the network); with [autoUpdate],
     * a version already held but outdated is downloaded again, unless the data saver is on.
     */
    fun refresh(autoUpdate: Boolean = false) {
        val current = parliament.snapshot ?: return
        scope.launch {
            val held = lock.withLock { withContext(Dispatchers.Default) { loader.held(current) } }
            holding = held
            checkedFor = current
            if (autoUpdate && held != null && held.outdated && loader.ref(current) != null && !dataSaver()) download()
        }
    }

    /** « En direct » is opened: the signed index (a few hundred bytes) is asked when not known yet, then the device is read. */
    fun onLiveOpened() {
        if (parliament.snapshot == null) parliament.load() else refresh(autoUpdate = true)
    }

    /** Downloads the package `semaine` (a gesture of the user, after its size was shown). */
    fun download() {
        val current = parliament.snapshot ?: return
        if (downloading) return
        downloading = true
        lastOutcome = null
        scope.launch {
            try {
                val outcome = lock.withLock { withContext(Dispatchers.Default) { loader.download(current) } }
                lastOutcome = outcome
                holding = lock.withLock { withContext(Dispatchers.Default) { loader.held(current) } }
                checkedFor = current
            } finally {
                downloading = false
            }
        }
    }

    /** Opens the page of the week above the parliamentary screen (in "Voir"). */
    fun openPage() {
        if (!parliament.isOpen) parliament.open()
        parliament.show(ParliamentPage.Week)
    }
}

/**
 * The state of the week, attached to [parliament] (`parliament.week`) so that the parliamentary
 * screen and « En direct » share it. [voir] and [library] give the bills of "Voir", [navigate]
 * the other spaces.
 */
@Composable
fun rememberParliamentWeekState(
    dependencies: AppDependencies,
    parliament: ParliamentState,
    library: ContentLibrary,
    voir: VoirState,
    navigate: (Space) -> Unit,
): ParliamentWeekState {
    val scope = rememberCoroutineScope()
    return remember(dependencies, parliament) {
        val storage = dependencies.database?.let(::ParliamentStore) ?: InMemoryParliamentStorage()
        val loader = ParliamentWeekLoader(dependencies.remote, storage, ::bundledParliamentFile)
        ParliamentWeekState(parliament, loader, scope, dependencies.dataSaver).also { week ->
            week.links = ParliamentWeekLinks(
                billFor = { dossier -> library.voir.bills().firstOrNull { ParliamentLinks.dossierId(it.article.event) == dossier } },
                openBill = { text ->
                    parliament.close()
                    voir.home(VoirAxis.POUVOIR)
                    voir.open(text.address)
                    navigate(Space.VOIR)
                },
                navigate = navigate,
            )
            parliament.week = week
        }
    }
}
