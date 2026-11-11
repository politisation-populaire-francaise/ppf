package org.politisationpopulairefrancaise.ui.agir.manifestation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.politisationpopulairefrancaise.core.agir.manifestation.LegalContacts
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPoint
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPointStorage
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.timer.TimerCandidate
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.agir.camera.ProtectedCameraState
import org.politisationpopulairefrancaise.ui.agir.camera.rememberProtectedCameraState
import org.politisationpopulairefrancaise.ui.agir.organisations.OrganisationsLibrary
import org.politisationpopulairefrancaise.ui.agir.timer.TimerState
import org.politisationpopulairefrancaise.ui.agir.timer.rememberTimerState
import org.politisationpopulairefrancaise.ui.content.ContentLibrary

/** The practical guides the AIDE button and the protest mode open (bundled with the application, readable offline). */
object ManifestationGuides {
    const val CUSTODY: String = "garde-a-vue"
    const val RIGHTS: String = "droits-en-manifestation"
    const val FILMING: String = "filmer-les-forces-de-l-ordre"
    const val INJURY: String = "apres-une-blessure-en-manifestation"

    /** « Vos droits » of the protest mode, in this order. */
    val rights: List<String> = listOf(RIGHTS, CUSTODY, FILMING)
}

/**
 * What the AIDE button (AG-27, first version) and the protest mode (AG-13) share for the whole
 * run: whether the emergency sheet is open, the guide open inside it, the rally point kept on the
 * device (AG-36), and the legal teams and volunteer lawyers of the directory near the user.
 *
 * Nothing here talks to a server: the numbers are dialled by the system, the directory and the
 * guides are those the device holds, the territory is compared on the device and stays in memory,
 * the rally point is written by [storage] only. The rally point is read the first time it is
 * needed ([rallyPoint]), not when the application starts.
 */
class HelpState(
    private val storage: RallyPointStorage?,
    private val directory: OrganisationsLibrary,
    private val contents: ContentLibrary?,
    /** The department of the commune chosen in "Comprendre" (Mes élus), if any: a default for "près de chez moi". */
    private val chosenDepartment: () -> String? = { null },
) {
    var isOpen: Boolean by mutableStateOf(false)
        private set

    /** The slug of the guide read inside the sheet, or null for the sheet itself. */
    var openGuide: String? by mutableStateOf(null)
        private set

    private var prepared = false

    /** The rally point kept on this device, read by [prepare]. */
    var rallyPoint: RallyPoint? by mutableStateOf(null)
        private set

    /** The department chosen in "Comprendre", read by [prepare]. */
    private var department: String? by mutableStateOf(null)

    /**
     * Reads what the device keeps (the rally point, the commune chosen in "Comprendre") and starts
     * the directory, the first time the sheet or the protest mode is shown; later calls do nothing.
     */
    fun prepare() {
        directory.ensureStarted()
        if (prepared) return
        prepared = true
        rallyPoint = runCatching { RallyPoint.decode(storage?.load()) }.getOrNull()
        department = runCatching { chosenDepartment() }.getOrNull()
    }

    /** False when this platform has nowhere to keep the rally point. */
    val canKeepRallyPoint: Boolean get() = storage != null

    /** The territory typed for "près de chez moi"; in memory only, never sent and never stored. */
    var territory: String by mutableStateOf("")
        private set

    private var territoryTouched = false

    fun open() {
        prepare()
        isOpen = true
    }

    fun close() {
        isOpen = false
        openGuide = null
    }

    /** Back: the open guide first, then the sheet. */
    fun back() {
        if (openGuide != null) openGuide = null else close()
    }

    fun showGuide(slug: String) {
        openGuide = slug
    }

    fun closeGuide() {
        openGuide = null
    }

    /** The guide of [slug] the device holds, if it has been imported. */
    fun guide(slug: String): Article? = contents?.guides?.firstOrNull { it.slug == slug }

    fun typeTerritory(text: String) {
        territoryTouched = true
        territory = text.take(MAX_TERRITORY)
    }

    /** What "près de chez moi" compares: the typed territory, else the department chosen in "Comprendre". */
    val effectiveTerritory: String?
        get() = territory.trim().takeIf { it.isNotEmpty() } ?: if (territoryTouched) null else department

    /** The legal teams and volunteer lawyers with a telephone, those of the territory first. */
    val legalContacts: List<Organisation> get() = LegalContacts.of(directory.organisations, effectiveTerritory)

    /**
     * « Sans nouvelles de moi » (AG-55, D-088), attached by the shell ([rememberTimerState]); null
     * outside it (the protest mode and the sheet alone, in tests), where its blocks show nothing.
     */
    var timer: TimerState? = null

    /**
     * « Caméra protégée » (AG-58, D-089), attached by the shell ([rememberProtectedCameraState]) so that
     * the protest mode and the testimony form use the one whose part `agir-camera` the quick erase
     * knows; null outside it (tests), where the screens build their own.
     */
    var camera: ProtectedCameraState? = null

    /**
     * The verified legal teams of the directory that have an account (their verified key, `p`): they
     * can receive the alert of a timer like a contact. Those whose verification was withdrawn are left out.
     */
    fun legalTeamCandidates(): List<TimerCandidate> = directory.organisations
        .filter { it.type == LEGAL_TEAM_TYPE && !it.revoked }
        .mapNotNull { organisation -> organisation.accountKey?.let { TimerCandidate(it, legalTeam = true, label = organisation.displayName) } }

    /** Keeps [point] on the device; false when it could not be written. */
    fun saveRallyPoint(point: RallyPoint): Boolean {
        val storage = storage ?: return false
        val ok = runCatching { storage.save(point.encode()) }.isSuccess
        if (ok) {
            prepared = true
            rallyPoint = point
        }
        return ok
    }

    fun clearRallyPoint() {
        runCatching { storage?.clear() }
        rallyPoint = null
    }

    companion object {
        const val MAX_TERRITORY: Int = 100

        /** The type of the legal teams in the directory (AgirFormats.ORG_TYPES). */
        const val LEGAL_TEAM_TYPE: String = "legal-team"
    }
}

/** The help state of the shell, read by the AIDE buttons and the protest mode; null outside the shell (tests). */
val LocalHelpState = staticCompositionLocalOf<HelpState?> { null }

/** Lets tests and previews replace where the rally point is kept; null uses the platform's. */
val LocalRallyPointStorage = staticCompositionLocalOf<RallyPointStorage?> { null }

@Composable
fun ProvideHelp(help: HelpState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHelpState provides help, content = content)
}

/**
 * Where this platform keeps the rally point: a private preference of the Android application
 * (system backups are forbidden by the manifest), a private file of the user's application data on
 * a computer, the application's defaults on iOS. Null when there is none.
 */
@Composable
expect fun rememberPlatformRallyPointStorage(): RallyPointStorage?

/** The help state for as long as its caller stays on screen (the shell keeps it for the whole run). */
@Composable
fun rememberHelpState(dependencies: AppDependencies, directory: OrganisationsLibrary, contents: ContentLibrary?): HelpState {
    val storage = LocalRallyPointStorage.current ?: rememberPlatformRallyPointStorage()
    return remember(dependencies, directory, contents, storage) {
        val elus = dependencies.database?.let(::ElusStore)
        HelpState(storage, directory, contents, chosenDepartment = { elus?.commune()?.departmentCode })
    }
}
