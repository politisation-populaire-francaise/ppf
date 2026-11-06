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
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.EventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.sync.SyncService
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.resources.Res

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
 */
class ContentLibrary(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val remote: RemoteEvents?,
    private val scope: CoroutineScope,
) {
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

    fun start() {
        if (store == null) {
            status = LibraryStatus.Unavailable
            return
        }
        scope.launch {
            importBundledContents(store)
            reload(store)
            if (remote == null) status = LibraryStatus.Offline else pull(store, remote)
        }
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
        val report = sync.publishPending() + sync.pull(kinds = listOf(Article.KIND, EventKinds.TEXT_NOTE))
        reload(store)
        status = if (report.reachable) LibraryStatus.Synced(report.pulled) else LibraryStatus.Unreachable
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

    private companion object {
        val BUNDLED_FILES = listOf("files/contents/fiches.json", "files/contents/revendications.json", "files/contents/fil.json")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Composable
fun rememberContentLibrary(dependencies: AppDependencies): ContentLibrary {
    val scope = rememberCoroutineScope()
    return remember(dependencies) {
        ContentLibrary(dependencies.contentStore, dependencies.editorialTrust, dependencies.remote, scope).also { it.start() }
    }
}
