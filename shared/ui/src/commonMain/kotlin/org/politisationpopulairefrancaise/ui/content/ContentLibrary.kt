package org.politisationpopulairefrancaise.ui.content

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
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.local.LocalStoreOutcome
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.sync.SyncReport
import org.politisationpopulairefrancaise.core.sync.SyncService
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res
import kotlin.random.Random

/** Where the contents come from and whether the server could be reached. */
sealed interface LibraryStatus {
    data object Loading : LibraryStatus

    /** No database on this platform yet: nothing can be shown. */
    data object Unavailable : LibraryStatus

    /** No server configured: only the contents bundled with the application and the user's own. */
    data object Offline : LibraryStatus

    data class Synced(val pulled: Int) : LibraryStatus

    /** The server did not answer; what the device already holds is shown. */
    data object Unreachable : LibraryStatus
}

/** A short publication of the feed, as the screen needs it. */
data class FeedPost(
    val event: SignedEvent,
    /** Written by a trusted editorial account (shown with the PPF badge). */
    val editorial: Boolean,
    /** Not yet accepted by the server. */
    val pending: Boolean,
) {
    val id: String get() = event.id
    val topics: List<String> get() = event.tagValues("t")

    /** Example contents carry the marker required by the cahier des charges; it is shown as a badge. */
    val example: Boolean get() = event.content.trimStart().startsWith(EXAMPLE_MARKER)

    /** The text to display: the marker, when present, becomes the badge. */
    val text: String get() = event.content.trimStart().removePrefix(EXAMPLE_MARKER).trim()

    companion object {
        const val EXAMPLE_MARKER = "[EXEMPLE]"
    }
}

/**
 * The contents available on the device: those bundled with the application (imported once into the
 * local database), the user's own publications, and, when a server is configured, those pulled from
 * it. Every event is signature-checked before storage; practical guides are shown only when their
 * author is trusted.
 *
 * It also holds the accounts the user follows. That list is private (S-120): it is read from and
 * written to the device's [FollowStore] only, and the "Suivis" view is assembled here, on the
 * device. When a server is configured, the followed authors' publications are requested in
 * batches of at most [FOLLOW_BATCH] keys, in random order, so that no single request carries the
 * whole list. Without a [FollowStore] (tests, previews) the list lives in memory only.
 */
class ContentLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
    private val follows: FollowStore? = null,
    /** Shuffles the followed keys before batching them; injectable so that tests can check the order. */
    private val random: Random = Random.Default,
) {
    /** Writes to the device's list of followed accounts happen one at a time, in the order the user acted. */
    private val followWrites = Mutex()

    var status: LibraryStatus by mutableStateOf(LibraryStatus.Loading)
        private set

    var guides: List<Article> by mutableStateOf(emptyList())
        private set

    var selected: Article? by mutableStateOf(null)
        private set

    var posts: List<FeedPost> by mutableStateOf(emptyList())
        private set

    /** The demands of the wall (M11), newest first; only from trusted authors until citizens can propose. */
    var revendications: List<Article> by mutableStateOf(emptyList())
        private set

    var selectedRevendication: Article? by mutableStateOf(null)
        private set

    /** [EXEMPLE] Reactions stay on the device until their privacy design is settled (M18). */
    var liked: Set<String> by mutableStateOf(emptySet())
        private set

    /** Public keys (lowercase hex) of the accounts the user follows. Private: never sent as a list. */
    var followed: Set<String> by mutableStateOf(emptySet())
        private set

    /**
     * The publications of the followed accounts, newest first, assembled on the device from the
     * local copy. They are a subset of [posts] (the most recent notes), which is enough for now;
     * a dedicated query by author will replace this when the feed grows.
     */
    val followedPosts: List<FeedPost> get() = posts.filter { it.event.pubkey in followed }

    fun start() {
        if (store == null) {
            status = LibraryStatus.Unavailable
            return
        }
        scope.launch {
            loadFollowed()
            importBundledContents(store)
            reload(store)
            if (remote == null) status = LibraryStatus.Offline else pull(store, remote)
        }
    }

    fun isFollowed(pubkeyHex: String): Boolean = pubkeyHex in followed

    /**
     * Follows an account: the list is updated on the device at once and, when a server is
     * configured, that author's recent publications are fetched (one batch of one key: the rest of
     * the list stays home) so that the "Suivis" view fills. Anything but a valid key is ignored.
     */
    fun follow(pubkeyHex: String) {
        if (!FollowStore.isValidKey(pubkeyHex) || pubkeyHex in followed) return
        followed = followed + pubkeyHex
        scope.launch {
            persistFollowed { it.follow(pubkeyHex) }
            val store = store ?: return@launch
            val remote = remote ?: return@launch
            if (pullAuthors(store, remote, listOf(pubkeyHex)).pulled > 0) reload(store)
        }
    }

    fun unfollow(pubkeyHex: String) {
        if (pubkeyHex !in followed) return
        followed = followed - pubkeyHex
        scope.launch { persistFollowed { it.unfollow(pubkeyHex) } }
    }

    fun toggleFollow(pubkeyHex: String) {
        if (pubkeyHex in followed) unfollow(pubkeyHex) else follow(pubkeyHex)
    }

    /** Forgets every followed account, on the device too: called when the user erases the keys. */
    fun clearFollowed() {
        followed = emptySet()
        scope.launch { persistFollowed { it.clear() } }
    }

    private suspend fun persistFollowed(write: (FollowStore) -> Unit) {
        val follows = follows ?: return
        followWrites.withLock { withContext(Dispatchers.Default) { write(follows) } }
    }

    /** Asks the server again (no effect without a server). */
    fun refresh() {
        val store = store ?: return
        val remote = remote ?: return
        scope.launch { pull(store, remote) }
    }

    fun open(article: Article) {
        selected = article
    }

    fun close() {
        selected = null
    }

    fun openRevendication(revendication: Article) {
        selectedRevendication = revendication
    }

    fun closeRevendication() {
        selectedRevendication = null
    }

    fun toggleLike(postId: String) {
        liked = if (postId in liked) liked - postId else liked + postId
    }

    /** Signs and stores a short note; it leaves for the server as soon as one answers. */
    suspend fun publishNote(signer: EventSigner, text: String): Boolean {
        val store = store ?: return false
        val content = text.trim()
        if (content.isEmpty()) return false
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = content)
        withContext(Dispatchers.Default) { store.store(event, pending = true) }
        reload(store)
        remote?.let { remote ->
            SyncService(store, remote).publishPending()
            reload(store)
        }
        return true
    }

    private suspend fun importBundledContents(store: LocalEventStore) {
        for (path in BUNDLED_FILES) {
            val bytes = runCatching { Res.readBytes(path) }.getOrNull() ?: continue
            val events = runCatching { json.decodeFromString(ListSerializer(SignedEvent.serializer()), bytes.decodeToString()) }.getOrNull() ?: continue
            withContext(Dispatchers.Default) {
                events.filter { it.verify() && trust.isTrusted(it.pubkey) }.forEach { store.store(it) }
            }
        }
    }

    private suspend fun pull(store: LocalEventStore, remote: RemoteEvents) {
        status = LibraryStatus.Loading
        val sync = SyncService(store, remote)
        val up = sync.publishPending()
        var report = up + sync.pull(kinds = listOf(Article.KIND, EventKinds.TEXT_NOTE))
        // The batches of followed authors never travel in the same pass as a publication of the user:
        // a server could otherwise tie them to its author. After a publication they wait for the next refresh.
        if (up.reachable && up.published == 0) report += pullFollowed(store, remote)
        reload(store)
        status = if (report.reachable) LibraryStatus.Synced(report.pulled) else LibraryStatus.Unreachable
    }

    private suspend fun loadFollowed() {
        val follows = follows ?: return
        followed = withContext(Dispatchers.Default) { follows.all().toSet() }
    }

    /**
     * Asks for the recent publications of every followed account, by batches of at most
     * [FOLLOW_BATCH] keys taken in random order: the server never receives the whole list in one
     * request, and the order says nothing about when each account was followed.
     */
    private suspend fun pullFollowed(store: LocalEventStore, remote: RemoteEvents): SyncReport {
        val keys = followed.toList()
        if (keys.isEmpty()) return SyncReport()
        return pullAuthors(store, remote, keys.shuffled(random))
    }

    /**
     * Fetches the recent notes of [authors] through the existing `author` parameter of the events
     * API, [FOLLOW_BATCH] keys per request. Only authentic events from the requested authors are
     * kept. The first unreachable batch stops the round; the rest waits for the next refresh.
     */
    private suspend fun pullAuthors(store: LocalEventStore, remote: RemoteEvents, authors: List<String>): SyncReport {
        var report = SyncReport()
        for (batch in authors.chunked(FOLLOW_BATCH)) {
            val wanted = batch.toSet()
            val filter = RemoteFilter(kinds = listOf(EventKinds.TEXT_NOTE), authors = batch, limit = FOLLOW_FETCH_LIMIT)
            when (val result = remote.fetch(filter)) {
                is FetchResult.Unavailable -> return report.copy(reachable = false)
                is FetchResult.Events -> {
                    val stored = withContext(Dispatchers.Default) {
                        result.events.count { it.pubkey in wanted && it.verify() && store.store(it) == LocalStoreOutcome.STORED }
                    }
                    report = report.copy(pulled = report.pulled + stored)
                }
            }
        }
        return report
    }

    private suspend fun reload(store: LocalEventStore) {
        val (freshGuides, freshDemands, freshPosts) = withContext(Dispatchers.Default) {
            val guides = store.byTag(listOf(Article.KIND), Article.TAG_TYPE, ArticleType.FICHE_PRATIQUE.tagValue, limit = 200)
                .filter { trust.isTrusted(it.pubkey) }
                .mapNotNull(Article::from)
                .sortedBy { it.title.lowercase() }
            val demands = store.byTag(listOf(Article.KIND), Article.TAG_TYPE, ArticleType.REVENDICATION.tagValue, limit = 200)
                .filter { trust.isTrusted(it.pubkey) }
                .mapNotNull(Article::from)
                .sortedWith(compareByDescending<Article> { it.updatedAt }.thenBy { it.title.lowercase() })
            val pendingIds = store.pending().map { it.id }.toSet()
            val posts = store.recent(kinds = listOf(EventKinds.TEXT_NOTE), limit = 100)
                .map { FeedPost(it, editorial = trust.isTrusted(it.pubkey), pending = it.id in pendingIds) }
            Triple(guides, demands, posts)
        }
        guides = freshGuides
        revendications = freshDemands
        posts = freshPosts
        selected = selected?.let { current -> freshGuides.firstOrNull { it.address == current.address } }
        selectedRevendication = selectedRevendication?.let { current -> freshDemands.firstOrNull { it.address == current.address } }
    }

    companion object {
        /** At most this many followed keys per request to the server. */
        const val FOLLOW_BATCH: Int = 20

        /** Recent notes asked for per batch of followed authors. */
        const val FOLLOW_FETCH_LIMIT: Int = 100

        private val BUNDLED_FILES = listOf("files/contents/fiches.json", "files/contents/revendications.json", "files/contents/fil.json")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Composable
fun rememberContentLibrary(dependencies: AppDependencies): ContentLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        ContentLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope, follows = dependencies.followStore).also { it.start() }
    }
}
