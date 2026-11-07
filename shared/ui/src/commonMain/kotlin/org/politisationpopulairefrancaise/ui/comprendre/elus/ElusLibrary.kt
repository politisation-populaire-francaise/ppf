package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.elus.CommuneChoice
import org.politisationpopulairefrancaise.core.elus.DownloadOutcome
import org.politisationpopulairefrancaise.core.elus.DownloadPlan
import org.politisationpopulairefrancaise.core.elus.ElusDirectory
import org.politisationpopulairefrancaise.core.elus.ElusReader
import org.politisationpopulairefrancaise.core.elus.IndexOutcome
import org.politisationpopulairefrancaise.core.elus.MyElus

/** Why a step of the choice of the commune could not go on. */
enum class ChooserError { POSTAL_CODE, UNKNOWN_POSTAL_CODE, UNREACHABLE, REFUSED, UNREADABLE, NO_COMMUNE }

/** Where the user stands in the choice of the commune. */
sealed interface ChooserStep {
    /** Typing the postal code (the text as typed, kept when an error is shown). */
    data class PostalCode(val text: String = "", val error: ChooserError? = null, val working: Boolean = false) : ChooserStep

    /** The size of the packages is shown; nothing is downloaded before the user confirms. */
    data class Download(
        val plan: DownloadPlan,
        /** The device saves data: a second confirmation is asked. */
        val dataSaverWarning: Boolean = false,
        val working: Boolean = false,
        val error: ChooserError? = null,
    ) : ChooserStep

    /** The postal code covers several communes: the user picks one. */
    data class PickCommune(val postalCode: String, val choices: List<CommuneChoice>) : ChooserStep
}

/** Where the search for a newer version of the list stands, on the page « Mes élus ». */
enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, FAILED }

/**
 * The state of the directory of elected officials in "Comprendre" (D-047): the officials of the
 * chosen commune, the page « Mes élus », the choice of the commune and the search for an update.
 * Everything the user chose stays on the device ([ElusDirectory]); the network is used only after a
 * gesture (continue, download, update). Operations run one at a time, away from the main thread.
 */
class ElusLibrary(
    private val directory: ElusDirectory,
    private val scope: CoroutineScope,
    private val dataSaver: () -> Boolean = { false },
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()
    private var job: Job? = null

    /** False for an application without server: the page shows the official directories (option C). */
    val hasServer: Boolean get() = directory.hasServer

    /** The officials of the chosen commune; null while none is chosen. */
    var mine: MyElus? by mutableStateOf(null)
        private set

    /** True once the device was read. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    var pageOpen: Boolean by mutableStateOf(false)
        private set

    /** The open step of the choice of the commune; null when the dialog is closed. */
    var chooser: ChooserStep? by mutableStateOf(null)
        private set

    /** True when the server did not answer the last time it was asked. */
    var unreachable: Boolean by mutableStateOf(false)
        private set

    var updateState: UpdateState by mutableStateOf(UpdateState.IDLE)
        private set

    /** The newer version announced by the server, when [updateState] is [UpdateState.AVAILABLE]. */
    var update: DownloadPlan? by mutableStateOf(null)
        private set

    /** Reads the chosen commune and its officials from the device, once (no network). */
    fun load() {
        if (loaded) return
        scope.launch {
            mine = work { directory.mine() }
            loaded = true
        }
    }

    fun openPage() {
        pageOpen = true
        load()
    }

    fun closePage() {
        pageOpen = false
    }

    fun startChoosing() {
        load()
        chooser = ChooserStep.PostalCode()
    }

    /** Closes the dialog; a download under way is abandoned (nothing half-done is kept). */
    fun cancelChoosing() {
        job?.cancel()
        job = null
        chooser = null
    }

    /** Looks for the departments of the postal code in the index of the server (the postal code is never sent). */
    fun submitPostalCode(text: String) {
        val postalCode = ElusReader.normalizePostalCode(text)
        if (postalCode == null) {
            chooser = ChooserStep.PostalCode(text, ChooserError.POSTAL_CODE)
            return
        }
        chooser = ChooserStep.PostalCode(text, working = true)
        job = scope.launch {
            when (val outcome = work { directory.index() }) {
                IndexOutcome.NoServer, IndexOutcome.Unreachable -> {
                    unreachable = true
                    chooser = ChooserStep.PostalCode(text, ChooserError.UNREACHABLE)
                }
                IndexOutcome.Unreadable -> chooser = ChooserStep.PostalCode(text, ChooserError.UNREADABLE)
                is IndexOutcome.Ready -> {
                    unreachable = !outcome.fresh
                    val plan = work { directory.plan(outcome.index, postalCode) }
                    when {
                        plan == null -> chooser = ChooserStep.PostalCode(text, ChooserError.UNKNOWN_POSTAL_CODE)
                        plan.missing.isEmpty() -> offerCommunes(plan)
                        else -> chooser = ChooserStep.Download(plan)
                    }
                }
            }
        }
    }

    /** Downloads the packages whose size was shown; in data saving mode, the first tap asks for a confirmation. */
    fun download() {
        val step = chooser as? ChooserStep.Download ?: return
        if (step.working) return
        if (dataSaver() && !step.dataSaverWarning) {
            chooser = step.copy(dataSaverWarning = true)
            return
        }
        chooser = step.copy(working = true, error = null)
        job = scope.launch {
            when (work { directory.download(step.plan) }) {
                DownloadOutcome.Done -> offerCommunes(step.plan)
                is DownloadOutcome.Refused -> chooser = step.copy(working = false, error = ChooserError.REFUSED)
                is DownloadOutcome.Unavailable -> {
                    unreachable = true
                    chooser = step.copy(working = false, error = ChooserError.UNREACHABLE)
                }
            }
        }
    }

    /** Keeps [choice] on the device and shows its officials. */
    fun pick(choice: CommuneChoice) {
        job = scope.launch { choose(choice) }
    }

    /** Goes back to the postal code (from the size or the list of communes). */
    fun backToPostalCode() {
        job?.cancel()
        val text = when (val step = chooser) {
            is ChooserStep.Download -> step.plan.postalCode.orEmpty()
            is ChooserStep.PickCommune -> step.postalCode
            else -> ""
        }
        chooser = ChooserStep.PostalCode(text)
    }

    /** « Oublier ma commune »: the commune and the downloaded lists leave the device. */
    fun forget() {
        job?.cancel()
        chooser = null
        scope.launch {
            work { directory.forget() }
            mine = null
            update = null
            updateState = UpdateState.IDLE
        }
    }

    /** Asks the server whether a newer version of the list of the department exists. */
    fun checkUpdate() {
        if (updateState == UpdateState.CHECKING || updateState == UpdateState.DOWNLOADING) return
        updateState = UpdateState.CHECKING
        scope.launch {
            when (val outcome = work { directory.index() }) {
                is IndexOutcome.Ready -> {
                    unreachable = !outcome.fresh
                    if (!outcome.fresh) {
                        updateState = UpdateState.FAILED
                        return@launch
                    }
                    val plan = work { directory.update(outcome.index) }
                    update = plan
                    updateState = if (plan == null) UpdateState.UP_TO_DATE else UpdateState.AVAILABLE
                }
                else -> {
                    unreachable = outcome != IndexOutcome.Unreadable
                    updateState = UpdateState.FAILED
                }
            }
        }
    }

    /** Downloads the newer version found by [checkUpdate] (its size is on the button). */
    fun downloadUpdate() {
        val plan = update ?: return
        if (updateState != UpdateState.AVAILABLE) return
        updateState = UpdateState.DOWNLOADING
        scope.launch {
            if (work { directory.download(plan) } == DownloadOutcome.Done) {
                mine = work { directory.mine() }
                update = null
                updateState = UpdateState.UP_TO_DATE
            } else {
                updateState = UpdateState.FAILED
            }
        }
    }

    private suspend fun offerCommunes(plan: DownloadPlan) {
        val choices = work { directory.communesFor(plan) }
        when (choices.size) {
            0 -> chooser = ChooserStep.PostalCode(plan.postalCode.orEmpty(), ChooserError.NO_COMMUNE)
            1 -> choose(choices.single())
            else -> chooser = ChooserStep.PickCommune(plan.postalCode.orEmpty(), choices)
        }
    }

    private suspend fun choose(choice: CommuneChoice) {
        mine = work {
            directory.choose(choice)
            directory.mine()
        }
        loaded = true
        update = null
        updateState = UpdateState.IDLE
        chooser = null
    }

    private suspend fun <T> work(block: suspend () -> T): T = lock.withLock { withContext(worker) { block() } }
}
