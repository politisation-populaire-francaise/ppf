package org.politisationpopulairefrancaise.ui.agir.solidarity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.FundQuery
import org.politisationpopulairefrancaise.core.agir.solidarity.MissionQuery
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFormats
import org.politisationpopulairefrancaise.core.agir.solidarity.SolidarityFund
import org.politisationpopulairefrancaise.core.agir.solidarity.VolunteerMission
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res

/** Where the solidarity rubric's contents come from and whether the server could be reached. */
sealed interface SolidarityStatus {
    /** Nothing asked yet: the rubric loads the first time it is shown. */
    data object Idle : SolidarityStatus

    data object Loading : SolidarityStatus

    /** No database on this platform: nothing can be shown. */
    data object Unavailable : SolidarityStatus

    /** No server configured: the contents bundled with the application only. */
    data object Offline : SolidarityStatus

    data class Synced(val pulled: Int) : SolidarityStatus

    /** The server did not answer; what the device holds is shown. */
    data object Unreachable : SolidarityStatus
}

/** The tabs of the rubric. The ids are stable (tests). */
enum class SolidarityTab(val id: String) {
    /** AG-10: strike funds and legal solidarity funds. */
    FUNDS("caisses"),

    /** AG-12: "Je veux aider", the volunteer missions published by the team. */
    MISSIONS("missions"),

    /** AG-10: car sharing and housing between citizens, in preparation (questions 1 and 2 of the note). */
    MUTUAL_AID("entraide"),
}

/**
 * The solidarity rubric of "Agir" (M35: AG-10, AG-12) as the screens need it: the funds and the
 * missions known to the device, read from the local database (the [EXEMPLE] contents bundled with
 * the application, then those pulled from the server), the searches, the open tab and the open fund
 * or mission.
 *
 * Only contents signed by a trusted editorial key are imported, stored from the server or shown
 * ([SolidarityFund.from], [VolunteerMission.from]). Everything the user chooses stays on the device,
 * in memory: the searches, "près de chez moi", and the skills of "Mes compétences" are never sent and
 * never stored. The server only ever receives two general requests, the same for everybody
 * (`kind=30023`, `tag=ppf-type:caisse` and `tag=ppf-type:mission`, from the last content received).
 * Nothing is asked of the server before the rubric is first shown ([ensureStarted]).
 */
class SolidarityLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    /** The signed contents bundled with the application; injectable for the tests. */
    private val bundled: suspend () -> ByteArray? = { runCatching { Res.readBytes(BUNDLED_FILE) }.getOrNull() },
) {
    private var started = false
    private val pulls = Mutex()

    var status: SolidarityStatus by mutableStateOf(SolidarityStatus.Idle)
        private set

    /** Every fund known to the device, open first, including the closed and withdrawn ones. */
    var funds: List<SolidarityFund> by mutableStateOf(emptyList())
        private set

    /** Every mission known to the device, open first, including the filled and closed ones. */
    var missions: List<VolunteerMission> by mutableStateOf(emptyList())
        private set

    var tab: SolidarityTab by mutableStateOf(SolidarityTab.FUNDS)
        private set

    /** The search among the funds; kept in memory only, never sent and never stored. */
    var fundQuery: FundQuery by mutableStateOf(FundQuery())
        private set

    /** The search among the missions, with the user's skills; kept in memory only, never sent and never stored. */
    var missionQuery: MissionQuery by mutableStateOf(MissionQuery())
        private set

    var selectedFund: SolidarityFund? by mutableStateOf(null)
        private set

    var selectedMission: VolunteerMission? by mutableStateOf(null)
        private set

    /** A server is configured (even if unreachable right now). */
    val hasRemote: Boolean get() = remote != null

    /** A fund or a mission is open. */
    val hasSelection: Boolean get() = selectedFund != null || selectedMission != null

    val fundResults: List<SolidarityFund> get() = fundQuery.apply(funds)

    val missionResults: List<VolunteerMission> get() = missionQuery.apply(missions)

    /** At least one known fund no longer takes donations: the filter is worth offering. */
    val hasClosedFunds: Boolean get() = funds.any { !it.isOpen }

    /** At least one known mission is filled or closed. */
    val hasClosedMissions: Boolean get() = missions.any { !it.isOpen }

    /** The territories of the known funds, most funds first, as most of them write it; at most [MAX_TERRITORIES]. */
    val fundTerritories: List<String>
        get() = funds
            .groupBy { FrenchText.fold(it.territory) }
            .filterKeys { it.isNotEmpty() }
            .map { (_, entries) ->
                val label = entries.groupingBy { it.territory }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .first().key
                label to entries.size
            }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { FrenchText.fold(it.first) })
            .take(MAX_TERRITORIES)
            .map { it.first }

    /** Loads the rubric the first time it is shown; later calls do nothing. */
    fun ensureStarted() {
        if (started) return
        started = true
        val store = store
        if (store == null) {
            status = SolidarityStatus.Unavailable
            return
        }
        scope.launch {
            status = SolidarityStatus.Loading
            importBundled(store)
            reload(store)
            val remote = remote
            if (remote == null) status = SolidarityStatus.Offline else pull(store, remote)
        }
    }

    /** Asks the server again (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pull(store, remote) }
    }

    fun selectTab(tab: SolidarityTab) {
        this.tab = tab
        closeSelection()
    }

    // The funds (AG-10)

    fun searchFunds(text: String) {
        fundQuery = fundQuery.copy(text = text)
    }

    fun toggleFundType(type: String) {
        fundQuery = fundQuery.copy(types = if (type in fundQuery.types) fundQuery.types - type else fundQuery.types + type)
    }

    fun clearFundTypes() {
        fundQuery = fundQuery.copy(types = emptySet())
    }

    fun selectFundTerritory(territory: String?) {
        fundQuery = fundQuery.copy(territory = territory?.takeIf { it.isNotBlank() })
    }

    /** The user's own territory for "près de chez moi"; it stays in memory, on the device. */
    fun setNear(text: String) {
        fundQuery = fundQuery.copy(near = text)
    }

    fun toggleIncludeClosedFunds() {
        fundQuery = fundQuery.copy(includeClosed = !fundQuery.includeClosed)
    }

    fun clearFundFilters() {
        fundQuery = FundQuery()
    }

    fun openFund(fund: SolidarityFund) {
        selectedMission = null
        selectedFund = fund
    }

    // The missions (AG-12)

    fun searchMissions(text: String) {
        missionQuery = missionQuery.copy(text = text)
    }

    /** Adds or removes one of the user's skills ("Mes compétences"); kept in memory only. */
    fun toggleSkill(skill: String) {
        missionQuery = missionQuery.copy(skills = if (skill in missionQuery.skills) missionQuery.skills - skill else missionQuery.skills + skill)
    }

    fun clearSkills() {
        missionQuery = missionQuery.copy(skills = emptySet())
    }

    fun toggleRemoteOnly() {
        missionQuery = missionQuery.copy(remoteOnly = !missionQuery.remoteOnly)
    }

    fun toggleIncludeClosedMissions() {
        missionQuery = missionQuery.copy(includeClosed = !missionQuery.includeClosed)
    }

    fun clearMissionFilters() {
        missionQuery = MissionQuery()
    }

    fun openMission(mission: VolunteerMission) {
        selectedFund = null
        selectedMission = mission
    }

    fun closeSelection() {
        selectedFund = null
        selectedMission = null
    }

    /**
     * Forgets everything the user chose here (searches, territory, skills, open content), for the
     * quick erase of the device (S-44). Nothing of it was ever written anywhere.
     */
    fun clearPrivateChoices() {
        fundQuery = FundQuery()
        missionQuery = MissionQuery()
        closeSelection()
    }

    private suspend fun importBundled(store: LocalEventStore) {
        val bytes = bundled() ?: return
        val events = runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.Default) {
            events.filter { isTrustedContent(it, checkSignature = true) }.forEach { store.store(it) }
        }
    }

    /** A fund or a mission signed by a trusted editorial key, well-formed. */
    private fun isTrustedContent(event: SignedEvent, checkSignature: Boolean): Boolean =
        SolidarityFund.from(event, trust, checkSignature) != null || VolunteerMission.from(event, trust, checkSignature) != null

    /**
     * Pulls the funds, then the missions, published since the last one received, page by page, each
     * with its own cursor (like the directory of organisations): only authentic contents of trusted
     * editorial keys are kept.
     */
    private suspend fun pull(store: LocalEventStore, remote: RemoteEvents) = pulls.withLock {
        status = SolidarityStatus.Loading
        var pulled = 0
        for ((type, cursor) in listOf(SolidarityFormats.TYPE_FUND to CURSOR_FUNDS, SolidarityFormats.TYPE_MISSION to CURSOR_MISSIONS)) {
            val received = pullType(store, remote, type, cursor)
            if (received == null) {
                reload(store)
                status = SolidarityStatus.Unreachable
                return@withLock
            }
            pulled += received
        }
        reload(store)
        status = SolidarityStatus.Synced(pulled)
    }

    /** The contents of one `ppf-type` stored from the server; null when the server did not answer. */
    private suspend fun pullType(store: LocalEventStore, remote: RemoteEvents, type: String, cursor: String): Int? {
        val tags = listOf(TagFilter(AgirFormats.TAG_TYPE, type))
        val since = withContext(Dispatchers.Default) { store.cursor(cursor) }
        var until: Long? = null
        var pulled = 0
        var newest: Long? = null
        var pages = 0
        while (pages < MAX_PAGES) {
            pages++
            val filter = RemoteFilter(kinds = listOf(SolidarityFund.KIND), since = since, until = until, limit = PAGE)
            val events = when (val result = remote.fetchByTags(tags, filter)) {
                is FetchResult.Unavailable -> return null
                is FetchResult.Events -> result.events
            }
            pulled += withContext(Dispatchers.Default) {
                events.count { event -> isTrustedContent(event, checkSignature = true) && store.store(event) == LocalStoreOutcome.STORED }
            }
            events.maxOfOrNull { it.createdAt }?.let { newest = maxOf(newest ?: it, it) }
            if (events.size < PAGE) break
            val oldest = events.minOf { it.createdAt }
            if (until != null && oldest >= until) break
            until = oldest
        }
        withContext(Dispatchers.Default) {
            listOfNotNull(newest, since).maxOrNull()?.coerceAtMost(store.now())?.let { store.setCursor(cursor, it) }
        }
        return pulled
    }

    private suspend fun reload(store: LocalEventStore) {
        val (fundEntries, missionEntries) = withContext(Dispatchers.Default) {
            // Everything in the local database was checked on arrival; the author is checked again here.
            val readFunds = store.byTag(listOf(SolidarityFund.KIND), AgirFormats.TAG_TYPE, SolidarityFormats.TYPE_FUND, limit = CONTENT_LIMIT)
                .mapNotNull { SolidarityFund.from(it, trust, checkSignature = false) }
            val readMissions = store.byTag(listOf(VolunteerMission.KIND), AgirFormats.TAG_TYPE, SolidarityFormats.TYPE_MISSION, limit = CONTENT_LIMIT)
                .mapNotNull { VolunteerMission.from(it, trust, checkSignature = false) }
            FundQuery(includeClosed = true).apply(readFunds) to MissionQuery(includeClosed = true).apply(readMissions)
        }
        funds = fundEntries
        missions = missionEntries
        selectedFund?.let { current -> selectedFund = fundEntries.firstOrNull { it.address == current.address } }
        selectedMission?.let { current -> selectedMission = missionEntries.firstOrNull { it.address == current.address } }
    }

    companion object {
        const val BUNDLED_FILE: String = "files/contents/solidarite.json"

        /** The names of the cursors in the local database. */
        const val CURSOR_FUNDS: String = "pull:30023:tag:ppf-type=caisse"
        const val CURSOR_MISSIONS: String = "pull:30023:tag:ppf-type=mission"

        /** The part of the device the quick erase (S-44) goes through for this rubric. */
        const val ERASABLE_ID: String = "agir-solidarity"

        /** Contents asked per request, and pages followed at most per pass. */
        const val PAGE: Int = 200
        const val MAX_PAGES: Int = 5

        /** Contents of each type read from the device. */
        const val CONTENT_LIMIT: Int = 1000

        /** The territories offered as filters. */
        const val MAX_TERRITORIES: Int = 12

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The rubric for as long as its caller stays on screen; nothing is fetched before it is shown. The
 * user's choices are handed to the quick erase of the device while it lives.
 */
@Composable
fun rememberSolidarityLibrary(dependencies: AppDependencies): SolidarityLibrary {
    val scope = rememberCoroutineScope()
    val library = remember(dependencies) {
        SolidarityLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope)
    }
    DisposableEffect(library) {
        val unregister = dependencies.erasables.register(deviceErasable(SolidarityLibrary.ERASABLE_ID) { library.clearPrivateChoices() })
        onDispose { unregister() }
    }
    return library
}
