package org.politisationpopulairefrancaise.ui.agir.organisations

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.organisations.Organisation
import org.politisationpopulairefrancaise.core.agir.organisations.OrganisationQuery
import org.politisationpopulairefrancaise.core.agir.organisations.VerifiedOrganisations
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res

/** Where the directory comes from and whether the server could be reached. */
sealed interface OrganisationsStatus {
    /** Nothing asked yet: the directory loads the first time it is shown. */
    data object Idle : OrganisationsStatus

    data object Loading : OrganisationsStatus

    /** No database on this platform: nothing can be shown. */
    data object Unavailable : OrganisationsStatus

    /** No server configured: the entries bundled with the application only. */
    data object Offline : OrganisationsStatus

    data class Synced(val pulled: Int) : OrganisationsStatus

    /** The server did not answer; what the device holds is shown. */
    data object Unreachable : OrganisationsStatus
}

/**
 * The directory of organisations (M3, AG-03) as the screens need it: the entries known to the
 * device, read from the local database (the [EXEMPLE] entries bundled with the application, then
 * those pulled from the server), the [verified] keys derived from them, the search, the open
 * organisation and its publications found on the device.
 *
 * Only entries signed by a trusted editorial key are imported, stored from the server or shown
 * ([Organisation.from]). The search, the filters and "près de chez moi" run on the device: the
 * server only ever receives the general request for the directory (`kind=30023`,
 * `tag=ppf-type:organisation`, from the last entry received), never the territory typed by the
 * user nor which organisation is open. Nothing is asked of the server before the directory is
 * first shown ([ensureStarted]).
 */
class OrganisationsLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    /** The signed entries bundled with the application; injectable for the tests. */
    private val bundled: suspend () -> ByteArray? = { runCatching { Res.readBytes(BUNDLED_FILE) }.getOrNull() },
) {
    private var started = false
    private val pulls = Mutex()

    var status: OrganisationsStatus by mutableStateOf(OrganisationsStatus.Idle)
        private set

    /** Every entry known to the device, by name. */
    var organisations: List<Organisation> by mutableStateOf(emptyList())
        private set

    /** The keys of the verified organisations (the `p` of the trusted entries). */
    var verified: VerifiedOrganisations by mutableStateOf(VerifiedOrganisations.EMPTY)
        private set

    /** The current search; kept in memory only, never sent and never stored. */
    var query: OrganisationQuery by mutableStateOf(OrganisationQuery())
        private set

    var selected: Organisation? by mutableStateOf(null)
        private set

    /** The short notes signed by the open organisation's key that the device holds, newest first. */
    var publications: List<SignedEvent> by mutableStateOf(emptyList())
        private set

    /** A server is configured (even if unreachable right now). */
    val hasRemote: Boolean get() = remote != null

    /** The entries the search keeps, by name (with "près de chez moi": local ones first, then national ones). */
    val results: List<Organisation> get() = query.apply(organisations)

    /** The territories of the known entries, most entries first, as most entries write them; at most [MAX_TERRITORIES]. */
    val territories: List<String>
        get() = organisations
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

    /** Loads the directory the first time it is shown; later calls do nothing. */
    fun ensureStarted() {
        if (started) return
        started = true
        val store = store
        if (store == null) {
            status = OrganisationsStatus.Unavailable
            return
        }
        scope.launch {
            status = OrganisationsStatus.Loading
            importBundled(store)
            reload(store)
            val remote = remote
            if (remote == null) status = OrganisationsStatus.Offline else pull(store, remote)
        }
    }

    /** Asks the server again (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pull(store, remote) }
    }

    fun search(text: String) {
        query = query.copy(text = text)
    }

    /** Adds or removes a type among the filters. */
    fun toggleType(type: String) {
        query = query.copy(types = if (type in query.types) query.types - type else query.types + type)
    }

    /** Keeps every type again. */
    fun clearTypes() {
        query = query.copy(types = emptySet())
    }

    fun selectTerritory(territory: String?) {
        query = query.copy(territory = territory?.takeIf { it.isNotBlank() })
    }

    /** The user's own territory for "près de chez moi"; it stays in memory, on the device. */
    fun setNear(text: String) {
        query = query.copy(near = text)
    }

    fun clearFilters() {
        query = OrganisationQuery()
    }

    fun open(organisation: Organisation) {
        selected = organisation
        publications = emptyList()
        reloadPublications()
    }

    /**
     * Opens the page of the verified organisation whose key is [pubkeyHex] (from an organiser of
     * the agenda or the author of a call, for example). False when the key is not verified.
     */
    fun openKey(pubkeyHex: String): Boolean {
        val organisation = verified.organisationFor(pubkeyHex) ?: return false
        open(organisation)
        return true
    }

    fun close() {
        selected = null
        publications = emptyList()
    }

    /** Reads again the open organisation's notes from the device (after a synchronization of the feed, for example). */
    fun reloadPublications() {
        val store = store ?: return
        val key = selected?.accountKey
        if (key == null) {
            publications = emptyList()
            return
        }
        scope.launch {
            val notes = withContext(Dispatchers.Default) {
                store.recent(kinds = listOf(EventKinds.TEXT_NOTE), limit = NOTES_SCANNED).filter { it.pubkey == key }.take(MAX_PUBLICATIONS)
            }
            if (selected?.accountKey == key) publications = notes
        }
    }

    private suspend fun importBundled(store: LocalEventStore) {
        val bytes = bundled() ?: return
        val events = runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.Default) {
            events.filter { Organisation.from(it, trust) != null }.forEach { store.store(it) }
        }
    }

    /**
     * Pulls the entries published since the last one received, page by page, with the pass's own
     * cursor (like the general synchronization): only authentic entries of trusted editorial keys
     * are kept.
     */
    private suspend fun pull(store: LocalEventStore, remote: RemoteEvents) = pulls.withLock {
        status = OrganisationsStatus.Loading
        val tags = listOf(TagFilter(AgirFormats.TAG_TYPE, AgirFormats.TYPE_ORGANISATION))
        val since = withContext(Dispatchers.Default) { store.cursor(CURSOR) }
        var until: Long? = null
        var pulled = 0
        var newest: Long? = null
        var pages = 0
        while (pages < MAX_PAGES) {
            pages++
            val filter = RemoteFilter(kinds = listOf(Organisation.KIND), since = since, until = until, limit = PAGE)
            val events = when (val result = remote.fetchByTags(tags, filter)) {
                is FetchResult.Unavailable -> {
                    reload(store)
                    status = OrganisationsStatus.Unreachable
                    return@withLock
                }
                is FetchResult.Events -> result.events
            }
            pulled += withContext(Dispatchers.Default) {
                events.count { event -> Organisation.from(event, trust) != null && store.store(event) == LocalStoreOutcome.STORED }
            }
            events.maxOfOrNull { it.createdAt }?.let { newest = maxOf(newest ?: it, it) }
            if (events.size < PAGE) break
            val oldest = events.minOf { it.createdAt }
            if (until != null && oldest >= until) break
            until = oldest
        }
        withContext(Dispatchers.Default) {
            listOfNotNull(newest, since).maxOrNull()?.coerceAtMost(store.now())?.let { store.setCursor(CURSOR, it) }
        }
        reload(store)
        status = OrganisationsStatus.Synced(pulled)
    }

    private suspend fun reload(store: LocalEventStore) {
        val (entries, keys) = withContext(Dispatchers.Default) {
            // Everything in the local database was checked on arrival; the author is checked again here.
            val all = store.byTag(listOf(Organisation.KIND), AgirFormats.TAG_TYPE, AgirFormats.TYPE_ORGANISATION, limit = DIRECTORY_LIMIT)
                .mapNotNull { Organisation.from(it, trust, checkSignature = false) }
            OrganisationQuery().apply(all) to VerifiedOrganisations.of(all)
        }
        organisations = entries
        verified = keys
        val current = selected
        if (current != null) {
            val fresh = entries.firstOrNull { it.address == current.address }
            selected = fresh
            if (fresh == null) publications = emptyList()
        }
    }

    companion object {
        const val BUNDLED_FILE: String = "files/contents/organisations.json"

        /** The name of this pass's cursor in the local database. */
        const val CURSOR: String = "pull:30023:tag:ppf-type=organisation"

        /** Entries asked per request, and pages followed at most per pass. */
        const val PAGE: Int = 200
        const val MAX_PAGES: Int = 5

        /** Entries read from the device. */
        const val DIRECTORY_LIMIT: Int = 1000

        /** Recent notes looked through for the open organisation's, and how many are shown. */
        const val NOTES_SCANNED: Int = 500
        const val MAX_PUBLICATIONS: Int = 20

        /** The territories offered as filters. */
        const val MAX_TERRITORIES: Int = 12

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** The directory for as long as its caller stays on screen; nothing is fetched before it is shown. */
@Composable
fun rememberOrganisationsLibrary(dependencies: AppDependencies): OrganisationsLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        OrganisationsLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope)
    }
}
