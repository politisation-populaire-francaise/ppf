package org.politisationpopulairefrancaise.core.agir.proches

import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.poster.AgirLinks
import org.politisationpopulairefrancaise.core.comprendre.ComprendreLinks
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.revendications.RevendicationTheme
import org.politisationpopulairefrancaise.core.revendications.RevendicationThemes
import org.politisationpopulairefrancaise.core.text.FrenchText

/** What kind of content a suggestion is. */
enum class SuggestionKind {
    /** A practical guide of "Comprendre" (fiche pratique). */
    FICHE,

    /** A course « Comprendre en 5 minutes ». */
    COURSE,

    /** A mobilization of the agenda, still to come. */
    MOBILIZATION,

    /** An open call of "Agir". */
    CALL,
}

/**
 * A content already on the device that may suit a relative (AG-63, « contenu adapté »): what the
 * screen shows and what « Partager » gives. [url] is the stable public address of the content, built
 * like the site's ([ComprendreLinks], [AgirLinks]), without any parameter: nothing in it says who
 * shared it, nor for whom. [key] is the address of the content (`kind:key:identifier`).
 */
data class ProcheSuggestion(
    val kind: SuggestionKind,
    val key: String,
    /** The title without the [EXEMPLE] marker, which the screen shows as a badge. */
    val title: String,
    val example: Boolean,
    val url: String,
    /** The `t` topics of the content, when it has some. */
    val topics: List<String> = emptyList(),
    val summary: String? = null,
    /** Mobilizations: the start; calls: the deadline, else the start; seconds since 1970. */
    val at: Long? = null,
)

/**
 * Picks, among the contents of the device, those that match the subjects of a relative, in an order
 * that follows the next step: something to read first while talking or sharing, a mobilization or a
 * call first when the next step is to invite or to go together. Everything runs on the device.
 */
object ProcheSuggestions {
    /** At most this many suggestions per relative. */
    const val LIMIT: Int = 4

    /** At most this many of one kind before the others get their turn (variety). */
    const val PER_KIND: Int = 2

    /**
     * The theme ids a content is about: one of its topics names the theme ([RevendicationTheme.matches]),
     * or its title or summary uses the theme's name or one of its aliases as whole words (the
     * mobilizations and calls carry no topics: « Marche pour l'hôpital public » is about « santé »).
     */
    fun themesOf(suggestion: ProcheSuggestion): List<String> {
        val text = " " + FrenchText.fold(suggestion.title + " " + (suggestion.summary ?: "")) + " "
        return RevendicationThemes.ALL
            .filter { theme -> suggestion.topics.any(theme::matches) || mentions(theme, text) }
            .map { it.id }
    }

    /** The suggestions for [entry]: none without a subject. */
    fun forEntry(entry: Proche, candidates: List<ProcheSuggestion>, limit: Int = LIMIT): List<ProcheSuggestion> {
        val wanted = ProchesRules.cleanSubjects(entry.subjects).toSet()
        if (wanted.isEmpty() || limit <= 0) return emptyList()
        val order = kindOrder(entry.nextStep)
        val sorted = candidates
            .mapNotNull { candidate ->
                val matched = themesOf(candidate).count { it in wanted }
                if (matched == 0) null else candidate to matched
            }
            .sortedWith(
                compareBy<Pair<ProcheSuggestion, Int>>(
                    { order.indexOf(it.first.kind) },
                    { -it.second },
                    { it.first.at ?: Long.MAX_VALUE },
                    { it.first.title.lowercase() },
                ),
            )
            .map { it.first }
            .distinctBy { it.key }
        val picked = mutableListOf<ProcheSuggestion>()
        for (candidate in sorted) {
            if (picked.size < limit && picked.count { it.kind == candidate.kind } < PER_KIND) picked += candidate
        }
        for (candidate in sorted) {
            if (picked.size < limit && candidate !in picked) picked += candidate
        }
        return picked
    }

    /** Which kinds come first for the next [step]. */
    fun kindOrder(step: ProcheStep): List<SuggestionKind> = when (step) {
        ProcheStep.TALK, ProcheStep.SHARE -> listOf(SuggestionKind.COURSE, SuggestionKind.FICHE, SuggestionKind.CALL, SuggestionKind.MOBILIZATION)
        ProcheStep.INVITE, ProcheStep.GO_TOGETHER, ProcheStep.DONE ->
            listOf(SuggestionKind.MOBILIZATION, SuggestionKind.CALL, SuggestionKind.FICHE, SuggestionKind.COURSE)
    }

    /** True when the folded, space-padded [text] holds the theme's id or one of its aliases as whole words. */
    private fun mentions(theme: RevendicationTheme, text: String): Boolean =
        (listOf(theme.id) + theme.aliases).any { key -> text.contains(" " + key.replace('-', ' ') + " ") }
}

/**
 * Reads, from the local copy of the contents only (never the network), what can be suggested: the
 * practical guides and the courses signed by the editorial team, the mobilizations still to come and
 * not cancelled, the open calls. The person's own mobilizations and calls are left out (they may still
 * wait for the team's review, and their address would lead nowhere), and so is anything not yet
 * accepted by the server.
 */
object ProcheCandidates {
    /** At most this many contents of each family are read from the device. */
    const val MAX_READ: Int = 300

    private const val EXAMPLE_MARKER: String = "[EXEMPLE]"

    fun fromDevice(store: LocalEventStore, trust: EditorialTrust, now: Long, account: String? = null): List<ProcheSuggestion> {
        val pending = store.pending().map { it.id }.toSet()

        fun articles(type: ArticleType, kind: SuggestionKind): List<ProcheSuggestion> =
            store.byTag(listOf(Article.KIND), Article.TAG_TYPE, type.tagValue, limit = MAX_READ)
                .filter { trust.isTrusted(it.pubkey) && it.id !in pending }
                .mapNotNull(Article::from)
                .mapNotNull { article ->
                    val path = ComprendreLinks.pathOf(article) ?: return@mapNotNull null
                    ProcheSuggestion(
                        kind = kind,
                        key = article.address.toString(),
                        title = displayTitle(article.title),
                        example = isExample(article.title),
                        url = ComprendreLinks.urlOf(path),
                        topics = article.topics,
                        summary = article.summary,
                    )
                }

        val mobilizations = store.recent(kinds = listOf(Mobilization.KIND), limit = MAX_READ)
            .filter { it.id !in pending && it.pubkey != account }
            .mapNotNull(Mobilization::from)
            .filter { !it.cancelled && !it.isPast(now) }
            .map { mobilization ->
                ProcheSuggestion(
                    kind = SuggestionKind.MOBILIZATION,
                    key = mobilization.address.toString(),
                    title = mobilization.displayTitle,
                    example = mobilization.example,
                    url = AgirLinks.urlOf(mobilization),
                    topics = mobilization.event.tagValues(Article.TAG_TOPIC),
                    summary = mobilization.summary,
                    at = mobilization.start,
                )
            }

        val calls = store.byTag(listOf(Call.KIND), AgirFormats.TAG_TYPE, AgirFormats.TYPE_CALL, limit = MAX_READ)
            .filter { it.id !in pending && it.pubkey != account }
            .mapNotNull(Call::from)
            .filter { call -> call.isOpen && (call.deadline?.let { it > now } ?: true) }
            .map { call ->
                ProcheSuggestion(
                    kind = SuggestionKind.CALL,
                    key = call.address.toString(),
                    title = call.displayTitle,
                    example = call.isExample,
                    url = AgirLinks.urlOf(call),
                    topics = call.event.tagValues(Article.TAG_TOPIC),
                    summary = call.summary,
                    at = call.dueAt,
                )
            }

        return articles(ArticleType.FICHE_PRATIQUE, SuggestionKind.FICHE) +
            articles(ArticleType.PARCOURS, SuggestionKind.COURSE) +
            mobilizations +
            calls
    }

    private fun isExample(title: String): Boolean = title.trimStart().startsWith(EXAMPLE_MARKER)

    private fun displayTitle(title: String): String = title.trimStart().removePrefix(EXAMPLE_MARKER).trim()
}
