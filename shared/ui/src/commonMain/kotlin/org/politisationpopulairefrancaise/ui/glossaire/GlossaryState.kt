package org.politisationpopulairefrancaise.ui.glossaire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.ui.AppDependencies

/** A content of "Comprendre" linked to an entry (`related`), as far as the device knows it. */
data class RelatedContent(val address: String, val title: String, val type: ArticleType)

/**
 * The glossary of the whole application (D-048), held by the shell for the whole run and handed to
 * every screen through [LocalGlossary]: the index of the trusted entries found on the device (those
 * bundled with the application in `glossaire.json` and those the server sends), the page of an entry
 * opened over the content (« En savoir plus »), and the full glossary inside "Comprendre".
 *
 * Nothing here leaves the device: which words the reader opens is never recorded.
 */
class GlossaryState(
    private val store: LocalEventStore?,
    private val trust: EditorialTrust,
    private val scope: CoroutineScope,
    initial: List<GlossaryEntry> = emptyList(),
    initialRelated: List<RelatedContent> = emptyList(),
) {
    /** Every entry, with the spotting of the forms in the texts; empty until the device's copy is read. */
    var index: GlossaryIndex by mutableStateOf(GlossaryIndex(initial))
        private set

    /** The related contents of the entries found on the device, by address. */
    var related: Map<String, RelatedContent> by mutableStateOf(initialRelated.associateBy { it.address })
        private set

    /**
     * The pages of entries opened over the content, the last one shown: « En savoir plus » opens one,
     * « Voir aussi » stacks another, the back button returns to the previous one.
     */
    var pages: List<String> by mutableStateOf(emptyList())
        private set

    /** The entry whose page is open over the content, if any. */
    val page: GlossaryEntry? get() = pages.lastOrNull()?.let(index::entry)

    /** True while the full glossary is shown inside "Comprendre". */
    var browsing: Boolean by mutableStateOf(false)
        private set

    /** The entry shown in the full glossary. */
    var selected: String? by mutableStateOf(null)
        private set

    /** The search of the full glossary; kept while the reader visits another space. */
    var query: String by mutableStateOf("")

    /** Set when the glossary asks the shell to show "Comprendre" (full glossary or related content). */
    var wantsComprendre: Boolean by mutableStateOf(false)
        private set

    /** The address of a content of "Comprendre" to open once the space is shown. */
    var pendingContent: String? by mutableStateOf(null)
        private set

    /** Reads the entries again from the device (after the bundled contents were imported or a synchronization). */
    fun reload() {
        val store = store ?: return
        scope.launch {
            val fresh = withContext(Dispatchers.Default) {
                val entries = store.byTag(listOf(Article.KIND), Article.TAG_TYPE, ArticleType.GLOSSAIRE.tagValue, limit = ENTRY_LIMIT)
                    .filter { trust.isTrusted(it.pubkey) }
                    .mapNotNull(Article::from)
                    .mapNotNull(GlossaryEntry::from)
                val index = GlossaryIndex(entries)
                val related = index.entries.flatMap { it.related }.distinct().mapNotNull { resolve(store, it) }
                index to related.associateBy { it.address }
            }
            index = fresh.first
            related = fresh.second
            pages = pages.filter { index.entry(it) != null }
            if (selected?.let(index::entry) == null) selected = null
        }
    }

    private fun resolve(store: LocalEventStore, address: String): RelatedContent? {
        val parsed = runCatching { ContentAddress.parse(address) }.getOrNull() ?: return null
        val event = store.byTag(listOf(parsed.kind), "d", parsed.identifier, limit = 20)
            .firstOrNull { it.pubkey == parsed.pubkey && trust.isTrusted(it.pubkey) } ?: return null
        val article = Article.from(event) ?: return null
        return RelatedContent(address, article.title, article.type)
    }

    /** « En savoir plus »: the page of [slug] over the content. */
    fun openPage(slug: String) {
        if (index.entry(slug) == null) return
        pages = pages + slug
    }

    /** Back to the previous page, or closes the last one. */
    fun backPage() {
        pages = pages.dropLast(1)
    }

    fun closePages() {
        pages = emptyList()
    }

    /** Shows the full glossary in "Comprendre", on [slug] when given. */
    fun browse(slug: String? = null) {
        pages = emptyList()
        browsing = true
        selected = slug?.takeIf { index.entry(it) != null }
        wantsComprendre = true
    }

    fun select(slug: String?) {
        selected = slug?.takeIf { index.entry(it) != null }
    }

    fun stopBrowsing() {
        browsing = false
        selected = null
    }

    /** Opens a related content of "Comprendre" (a course, a guide, a fact-check...). */
    fun openRelated(address: String) {
        pages = emptyList()
        browsing = false
        pendingContent = address
        wantsComprendre = true
    }

    /** Called by the shell once "Comprendre" is shown. */
    fun navigationDone() {
        wantsComprendre = false
    }

    /** Called by "Comprendre" once the related content is open (or cannot be). */
    fun pendingDone() {
        pendingContent = null
    }

    companion object {
        /** At most this many entries are read from the device's copy. */
        const val ENTRY_LIMIT: Int = 2_000
    }
}

/**
 * The glossary of the application, provided by the shell. Null (tests, previews, any screen shown
 * outside the shell): texts are shown exactly as before, nothing is spotted.
 */
val LocalGlossary = staticCompositionLocalOf<GlossaryState?> { null }

/**
 * The first occurrences of one content shown in several pieces (a course page, an entry page): the
 * first piece that meets an entry links it, the next pieces leave it alone. Pieces are recognized by
 * their text, so a piece composed again keeps its links. [excluded] entries are never linked (the
 * page of an entry does not link its own term).
 */
class GlossaryOccurrences(val excluded: Set<String> = emptySet()) {
    private val owners = HashMap<String, Any>()

    /** True when [owner] may link [slug]: nobody linked it before in this content, or [owner] did. */
    fun claim(slug: String, owner: Any): Boolean {
        val current = owners[slug]
        if (current == null) {
            owners[slug] = owner
            return true
        }
        return current == owner
    }
}

internal val LocalGlossaryOccurrences = staticCompositionLocalOf<GlossaryOccurrences?> { null }

/**
 * One content shown in several pieces: every [org.politisationpopulairefrancaise.ui.content.MarkdownText]
 * inside links each term once in the whole of it. A new [key] (another page) starts again.
 */
@Composable
fun GlossaryScope(key: Any, excluded: Set<String> = emptySet(), content: @Composable () -> Unit) {
    val occurrences = remember(key, excluded) { GlossaryOccurrences(excluded) }
    CompositionLocalProvider(LocalGlossaryOccurrences provides occurrences, content = content)
}

/** The glossary of the shell: read from the device's copy of the contents, kept for the whole run. */
@Composable
fun rememberGlossaryState(dependencies: AppDependencies): GlossaryState {
    val scope = rememberCoroutineScope()
    return remember(dependencies) { GlossaryState(dependencies.contentStore, dependencies.editorialTrust, scope) }
}

/**
 * Provides [state] to [content] and shows over it the page of the entry opened by « En savoir plus ».
 * Without a state, [content] is shown as it is.
 */
@Composable
fun ProvideGlossary(state: GlossaryState?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlossary provides state) {
        content()
        val page = state?.page
        if (state != null && page != null) GlossaryPageDialog(state, page)
    }
}
