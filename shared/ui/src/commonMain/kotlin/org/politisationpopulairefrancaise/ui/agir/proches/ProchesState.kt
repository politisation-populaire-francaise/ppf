package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.agir.proches.Proche
import org.politisationpopulairefrancaise.core.agir.proches.ProcheCandidates
import org.politisationpopulairefrancaise.core.agir.proches.ProcheChange
import org.politisationpopulairefrancaise.core.agir.proches.ProcheInput
import org.politisationpopulairefrancaise.core.agir.proches.ProcheStep
import org.politisationpopulairefrancaise.core.agir.proches.ProcheSuggestion
import org.politisationpopulairefrancaise.core.agir.proches.ProcheSuggestions
import org.politisationpopulairefrancaise.core.agir.proches.ProchesData
import org.politisationpopulairefrancaise.core.agir.proches.ProchesRules
import org.politisationpopulairefrancaise.core.agir.proches.ProchesStorage
import org.politisationpopulairefrancaise.core.agir.proches.ProchesStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState

/** Why the form could not be saved. */
enum class ProcheDraftError {
    /** No nickname once cleaned. */
    NO_NICKNAME,

    /** The list is full ([ProchesRules.MAX_ENTRIES]). */
    FULL,

    /** The relative edited is no longer on the device. */
    GONE,
}

/**
 * The form of one relative while it is filled, **in memory only** (never `rememberSaveable`, whose
 * state the system may write to the disk): [editing] is the id of the relative edited, null for a new
 * one. Forgotten by the quick erase.
 */
data class ProcheDraft(
    val editing: String? = null,
    val nickname: String = "",
    val subjects: List<String> = emptyList(),
    val step: ProcheStep = ProcheStep.TALK,
    val reminder: CivilDate? = null,
    val error: ProcheDraftError? = null,
) {
    fun toInput(): ProcheInput = ProcheInput(nickname, subjects, step, reminder)

    // Never the nickname in a log or a crash report.
    override fun toString(): String = "ProcheDraft(editing=${editing != null}, subjects=${subjects.size})"

    companion object {
        fun of(entry: Proche): ProcheDraft = ProcheDraft(entry.id, entry.nickname, entry.subjects, entry.nextStep, entry.reminderDate)
    }
}

/**
 * « Mes proches à mobiliser » (AG-63): the private list of a few relatives, on the device only
 * ([ProchesStore]), with the contents of the device that suit each of them ([ProcheSuggestions]),
 * the reminders whose day has come first, and the form. Nothing here is ever sent: no request
 * carries the list, no content is published, the account is never involved.
 *
 * The slow steps (reading the platform's storage, the local copy of the contents) run on [worker].
 * [candidates] reads what can be suggested from the device only. The quick erase part
 * `agir-proches` ([eraseFromDevice]) is registered by the shell.
 */
class ProchesState(
    private val store: ProchesStore,
    private val scope: CoroutineScope,
    /** False where the platform has nowhere to keep the list (iOS for now): the screen says so. */
    val hasStorage: Boolean,
    private val candidates: () -> List<ProcheSuggestion> = { emptyList() },
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = ::currentEpochSeconds,
) {
    /** The relatives, in the order they were added. */
    var entries: List<Proche> by mutableStateOf(emptyList())
        private set

    /** The list was read from the device at least once. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    /** The contents of the device that can be suggested, read at each visit. */
    var suggestions: List<ProcheSuggestion> by mutableStateOf(emptyList())
        private set

    /** Today in Paris time, the day the reminders are compared with. */
    var today: CivilDate by mutableStateOf(dayOf(clock()))
        private set

    /** The warning shown before the first entry. */
    var warningShown: Boolean by mutableStateOf(false)
        private set

    /** « Commencer ma liste » was chosen during this run. */
    var warningAccepted: Boolean by mutableStateOf(false)
        private set

    /** The form, while it is open. */
    var draft: ProcheDraft? by mutableStateOf(null)
        private set

    /** The last write to the device failed: the screen says so, the list shown is what the device holds. */
    var writeFailed: Boolean by mutableStateOf(false)
        private set

    /** True while a change is written. */
    var busy: Boolean by mutableStateOf(false)
        private set

    /** The list as shown: reminders due first, then the others, those done at the end. */
    val ordered: List<Proche> get() = ProchesData(entries = entries).ordered(today)

    /** The relatives whose reminder day has come (« À faire aujourd'hui »). */
    val due: List<Proche> get() = ordered.filter { it.isDue(today) }

    val isFull: Boolean get() = entries.size >= ProchesRules.MAX_ENTRIES

    /** What suits [entry] among the contents of the device. */
    fun suggestionsFor(entry: Proche): List<ProcheSuggestion> = ProcheSuggestions.forEntry(entry, suggestions)

    /** The day that is [days] after [today], for the choices of the reminder. */
    fun dayIn(days: Int): CivilDate = ParisTime.dateOf(ParisTime.epochDayOf(today) + days)

    /** Reads the list and the contents that can be suggested again (each time the screen is shown). */
    suspend fun load() {
        val read = safely {
            withContext(worker) { store.read() to runCatching { candidates() }.getOrDefault(emptyList()) }
        }
        today = dayOf(clock())
        if (read != null) {
            entries = read.first.entries
            suggestions = read.second
        }
        loaded = true
    }

    /** « Ajouter un proche »: while the list is empty, the warning first; then the form. */
    fun startAdding() {
        if (!hasStorage || isFull || busy) return
        if (entries.isEmpty() && !warningAccepted) {
            warningShown = true
            return
        }
        draft = ProcheDraft()
    }

    /** « Annuler » on the warning: nothing is kept, nothing is written. */
    fun cancelWarning() {
        warningShown = false
    }

    /** « Commencer ma liste »: the form opens; nothing is written before « Enregistrer ». */
    fun acceptWarning() {
        warningShown = false
        warningAccepted = true
        draft = ProcheDraft()
    }

    fun edit(entry: Proche) {
        warningShown = false
        draft = ProcheDraft.of(entry)
    }

    fun typeNickname(text: String) {
        draft = draft?.copy(nickname = ProchesRules.typed(text), error = null)
    }

    /** Ticks or unticks a subject; a fourth one is refused (the chips say so by being disabled). */
    fun toggleSubject(id: String) {
        val current = draft ?: return
        draft = when {
            id in current.subjects -> current.copy(subjects = current.subjects - id)
            current.subjects.size >= ProchesRules.MAX_SUBJECTS || id !in ProchesRules.SUBJECTS -> current
            else -> current.copy(subjects = current.subjects + id)
        }
    }

    fun chooseStep(step: ProcheStep) {
        draft = draft?.copy(step = step)
    }

    fun chooseReminder(date: CivilDate?) {
        draft = draft?.copy(reminder = date)
    }

    /** « Annuler » on the form: what was typed is forgotten. */
    fun cancelDraft() {
        draft = null
    }

    /** « Enregistrer »: the relative is kept on the device, then the form closes. */
    fun saveDraft() {
        val current = draft ?: return
        if (ProchesRules.cleanNickname(current.nickname).isEmpty()) {
            draft = current.copy(error = ProcheDraftError.NO_NICKNAME)
            return
        }
        val input = current.toInput()
        val editing = current.editing
        write({ data -> if (editing == null) data.add(input) else data.update(editing, input) }) { change ->
            draft = when (change) {
                is ProcheChange.Saved, ProcheChange.Removed -> null
                ProcheChange.Full -> current.copy(error = ProcheDraftError.FULL)
                ProcheChange.NoNickname -> current.copy(error = ProcheDraftError.NO_NICKNAME)
                ProcheChange.Unknown -> current.copy(error = ProcheDraftError.GONE)
            }
        }
    }

    /** « Étape suivante »: one rung up the ladder. */
    fun advance(entry: Proche) {
        write({ data -> data.advance(entry.id) })
    }

    /** « Retirer », after the confirmation: the relative leaves the list (and the device). */
    fun remove(entry: Proche) {
        write({ data -> data.remove(entry.id) }) {
            if (draft?.editing == entry.id) draft = null
        }
    }

    /**
     * The part `agir-proches` of the quick erase (S-44), also run by « Effacer mes clés »: the list,
     * then checked; the form and the warning are forgotten too. Throws when the list is still there.
     */
    suspend fun eraseFromDevice() {
        withContext(worker) {
            store.clear()
            check(store.read().isEmpty) { "the list of relatives is not erased" }
        }
        entries = emptyList()
        draft = null
        warningShown = false
        warningAccepted = false
        writeFailed = false
    }

    /** « Effacer mes clés » erased the keys during the run: the list goes with them; a failure never stops the application. */
    suspend fun forget() {
        safely { eraseFromDevice() }
    }

    private fun write(change: (ProchesData) -> Pair<ProchesData, ProcheChange>, onDone: (ProcheChange) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            val result = safely { withContext(worker) { store.update(change) } }
            busy = false
            if (result == null) {
                writeFailed = true
                // What the device really holds, in case the write half failed.
                safely { withContext(worker) { store.read() } }?.let { entries = it.entries }
                return@launch
            }
            writeFailed = false
            entries = result.first.entries
            onDone(result.second)
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** The part of the device the quick erase (S-44) goes through for the list. */
        const val ERASABLE_ID: String = "agir-proches"

        /**
         * The owner of the device reminders of the list, once the reminders of « Décider » are in main
         * (`ReminderStore`, docs/integration/rappels.md): `agir:proches:<id of the entry>`.
         */
        const val REMINDER_OWNER_PREFIX: String = "agir:proches:"

        /** The internal link a reminder will open (the shell will lead it to this rubric). */
        const val TARGET: String = "ppf:agir/proches"

        private fun dayOf(epochSeconds: Long): CivilDate = ParisTime.dateOf(ParisTime.epochDay(epochSeconds))
    }
}

/** Lets tests and previews replace where the list is kept; null uses the platform's. */
val LocalProchesStorage = staticCompositionLocalOf<ProchesStorage?> { null }

/**
 * Where this platform keeps the list: a private preference of the Android application (system
 * backups are forbidden by the manifest), a private file of the user's application data on a
 * computer, nothing on iOS yet. Null when there is none.
 */
@Composable
expect fun rememberPlatformProchesStorage(): ProchesStorage?

/**
 * The list of the shell, for the whole run (AG-63): the shell registers its quick erase part
 * (`agir-proches`) and hands it to "Agir". It follows the account only to be erased with the keys
 * (« Effacer mes clés »): the list itself is never tied to the account, and works without one.
 * Nothing is read before the screen is shown ([ProchesState.load]).
 */
@Composable
fun rememberProchesState(dependencies: AppDependencies, session: AccountSession): ProchesState {
    val provided = LocalProchesStorage.current
    val platform = rememberPlatformProchesStorage()
    val ephemeral = remember(dependencies) { InMemoryProchesStorage() }
    // The list lives no longer than the identity of the device: with an identity kept in memory only
    // (tests, previews), the list is kept in memory too, never in the platform's file.
    val storage = provided ?: if (platform != null && dependencies.identityStore is InMemoryIdentityStore) ephemeral else platform
    val scope = rememberCoroutineScope()
    val key = (session.state as? AccountUiState.Ready)?.publicKey?.hex
    val account = rememberUpdatedState(key)
    val state = remember(dependencies, storage) {
        val events = dependencies.contentStore
        val trust = dependencies.editorialTrust
        ProchesState(
            store = ProchesStore(storage ?: InMemoryProchesStorage()),
            scope = scope,
            hasStorage = storage != null,
            candidates = { events?.let { ProcheCandidates.fromDevice(it, trust, currentEpochSeconds(), account.value) }.orEmpty() },
        )
    }
    val holder = remember(state) { AccountMemory() }
    LaunchedEffect(state, key) {
        // Keys erased during the run (« Effacer mes clés », the quick erase): the list goes with them.
        val signedOut = key == null && holder.hadAccount && session.state is AccountUiState.NoAccount
        if (key != null) holder.hadAccount = true
        if (signedOut) {
            holder.hadAccount = false
            state.forget()
        }
    }
    return state
}

/** Whether an account was seen during this run, to tell keys erased from a device that never had any. */
private class AccountMemory {
    var hadAccount: Boolean = false
}
