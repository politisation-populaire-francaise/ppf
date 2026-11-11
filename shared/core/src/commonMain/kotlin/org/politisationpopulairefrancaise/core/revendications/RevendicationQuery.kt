package org.politisationpopulairefrancaise.core.revendications

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.text.FrenchText

/** Whose demands a query keeps. */
@Serializable
enum class AuthorScope {
    /** Every author: no constraint. */
    @SerialName("anyone")
    ANYONE,

    /** The accounts the user follows (the list stays on the device). */
    @SerialName("followed")
    FOLLOWED,

    /** The user's own demands. */
    @SerialName("mine")
    MINE,
}

/** How the demands of the wall are ordered (DE-01: "tri par soutien, nouveauté..."). */
@Serializable
enum class RevendicationSort {
    /** First publication, newest first. */
    @SerialName("newest")
    NEWEST,

    /** Most supported first; see [RevendicationSorting.popularityRank] for what it means before votes open. */
    @SerialName("popular")
    POPULAR,

    /** Last update, most recent first. */
    @SerialName("updated")
    UPDATED,

    /** Most « pour » of the citizens first (the live results of the boxes, D-155, D-156). */
    @SerialName("most-for")
    MOST_FOR,

    /** Most « contre » of the citizens first. */
    @SerialName("most-against")
    MOST_AGAINST,

    /** Most comments in the debate first (DE-20). */
    @SerialName("most-debated")
    MOST_DEBATED,
}

/**
 * What a demand is about beyond its own fields (MVP 1, views asked by the owner on 2026-11-11): the device
 * knows it from elsewhere ([RevendicationContext]); a scope set in a query keeps the demands in ANY of them.
 */
@Serializable
enum class RevendicationScope {
    /** Already carried by the movement and gathered by the team (D-152). */
    @SerialName("mouvement")
    MOVEMENT,

    /** Debated in Parliament: an official petition or a bill is attached to it. */
    @SerialName("parlement")
    PARLIAMENT,

    /** Retained or a campaign (its box closed), or rejected by the vote (more « contre » than « pour »). */
    @SerialName("closes")
    CLOSED,

    /** Its territory is the commune or the department chosen on the device. */
    @SerialName("pres-de-chez-moi")
    NEAR_ME,

    /** Voted on this device (the choice never leaves it). */
    @SerialName("mes-votes")
    MY_VOTES,
}

/** One criterion of a query, as the filter panel shows it (and locks it when a view fixes it). */
enum class RevendicationCriterion { THEMES, TERRITORIES, STATUSES, AUTHORS, TEXT, SCOPES }

/**
 * What the device knows that a query may need: the followed accounts and the user's own key, and, keyed by the
 * address of a demand (`30023:<key>:<d>`), the live results of its box, the size of its debate, and the scopes
 * known from elsewhere. Nothing of it leaves the device.
 */
data class RevendicationContext(
    /** Public keys (lowercase hex) of the accounts the user follows. */
    val followed: Set<String> = emptySet(),
    /** The user's public key (lowercase hex), null without an account. */
    val myKey: String? = null,
    /** The citizens' « pour » of each demand with an open or closed box. */
    val forCounts: Map<String, Long> = emptyMap(),
    /** The citizens' « contre » of each demand. */
    val againstCounts: Map<String, Long> = emptyMap(),
    /** The number of comments of the debate of each demand. */
    val debateCounts: Map<String, Int> = emptyMap(),
    /** The demands of the movement (D-152). */
    val fromMovement: Set<String> = emptySet(),
    /** The demands with an official petition or a bill attached. */
    val atParliament: Set<String> = emptySet(),
    /** The codes of the territories chosen on the device (commune, department). */
    val nearMe: Set<String> = emptySet(),
    /** The demands voted on this device. */
    val myVotes: Set<String> = emptySet(),
)

/**
 * A query on the demands wall (DE-01, views and filters asked by the owner on 2026-11-06). The
 * same shape describes a saved VIEW (a named query) and the temporary FILTERS applied on top of
 * it; see [overlay]. Every criterion is a constraint only when set: empty sets, [AuthorScope.ANYONE]
 * and a blank [text] mean "no constraint". Sets match when the demand has ANY of their values.
 *
 * Its JSON form (see [toJson]) is stored in the views saved on the device: the names are stable,
 * unknown fields and unknown values are ignored on read, so that an older application can read
 * the views of a newer one. Views and filters reveal political interests (sensitive data, GDPR
 * article 9): they never leave the device.
 */
@Serializable
data class RevendicationQuery(
    /** Theme ids ([RevendicationThemes]). */
    @SerialName("themes") val themes: Set<String> = emptySet(),
    /** Territories, folded with [FrenchText.fold] ("Métropole de Lyon" is stored as "metropole de lyon"). */
    @SerialName("territories") val territories: Set<String> = emptySet(),
    /** Statuses, written with their tag values ("en-debat"). */
    @SerialName("statuses") @Serializable(with = StatusSetSerializer::class) val statuses: Set<RevendicationStatus> = emptySet(),
    @SerialName("authors") val authors: AuthorScope = AuthorScope.ANYONE,
    /** Words that must all start a word of the demand ([FrenchText.matchesAllWords]). */
    @SerialName("text") val text: String = "",
    /** Null: the sort of the view, or [RevendicationSort.NEWEST]. */
    @SerialName("sort") val sort: RevendicationSort? = null,
    /** Scopes ([RevendicationScope]): the demand is in ANY of them. */
    @SerialName("scopes") val scopes: Set<RevendicationScope> = emptySet(),
) {
    /** The criteria this query constrains (the sort is not a criterion). */
    val constrained: Set<RevendicationCriterion>
        get() = buildSet {
            if (themes.isNotEmpty()) add(RevendicationCriterion.THEMES)
            if (territories.isNotEmpty()) add(RevendicationCriterion.TERRITORIES)
            if (statuses.isNotEmpty()) add(RevendicationCriterion.STATUSES)
            if (authors != AuthorScope.ANYONE) add(RevendicationCriterion.AUTHORS)
            if (text.isNotBlank()) add(RevendicationCriterion.TEXT)
            if (scopes.isNotEmpty()) add(RevendicationCriterion.SCOPES)
        }

    /** True when no criterion is set (the sort does not count). */
    val isUnconstrained: Boolean get() = constrained.isEmpty()

    /**
     * How many removable filters this query holds: one per theme, territory and status, one for
     * the authors, one for the words. The "Filtres" button shows this number.
     */
    val constraintCount: Int
        get() = themes.size + territories.size + statuses.size +
            (if (authors != AuthorScope.ANYONE) 1 else 0) + (if (text.isNotBlank()) 1 else 0) + scopes.size

    /** True when [article] satisfies every criterion of this query. */
    fun matches(article: Article, context: RevendicationContext = RevendicationContext()): Boolean {
        if (themes.isNotEmpty() && themes.none { matchesTheme(article, it) }) return false
        if (territories.isNotEmpty()) {
            val territory = article.territory?.let(FrenchText::fold) ?: return false
            if (territories.none { FrenchText.fold(it) == territory }) return false
        }
        if (statuses.isNotEmpty() && article.status !in statuses) return false
        when (authors) {
            AuthorScope.ANYONE -> Unit
            AuthorScope.FOLLOWED -> if (article.author !in context.followed) return false
            AuthorScope.MINE -> if (context.myKey == null || article.author != context.myKey) return false
        }
        if (text.isNotBlank() && !FrenchText.matchesAllWords(searchableText(article), text)) return false
        if (scopes.isNotEmpty() && scopes.none { inScope(article, it, context) }) return false
        return true
    }

    /**
     * This query seen as a VIEW, with [filters] applied on top of it: every criterion the view
     * fixes keeps the view's value, the others take the filter's value. The sort is the filter's,
     * else the view's, else [RevendicationSort.NEWEST].
     */
    fun overlay(filters: RevendicationQuery): RevendicationQuery = RevendicationQuery(
        themes = themes.ifEmpty { filters.themes },
        territories = territories.ifEmpty { filters.territories },
        statuses = statuses.ifEmpty { filters.statuses },
        authors = if (authors != AuthorScope.ANYONE) authors else filters.authors,
        text = text.ifBlank { filters.text },
        sort = filters.sort ?: sort ?: RevendicationSort.NEWEST,
        scopes = scopes.ifEmpty { filters.scopes },
    )

    /** This query without the given criteria (they go back to "no constraint"); the sort is kept. */
    fun without(criteria: Set<RevendicationCriterion>): RevendicationQuery = copy(
        themes = if (RevendicationCriterion.THEMES in criteria) emptySet() else themes,
        territories = if (RevendicationCriterion.TERRITORIES in criteria) emptySet() else territories,
        statuses = if (RevendicationCriterion.STATUSES in criteria) emptySet() else statuses,
        authors = if (RevendicationCriterion.AUTHORS in criteria) AuthorScope.ANYONE else authors,
        text = if (RevendicationCriterion.TEXT in criteria) "" else text,
        scopes = if (RevendicationCriterion.SCOPES in criteria) emptySet() else scopes,
    )

    /** The demands of [articles] this query keeps, in its order ([sort], or newest first). */
    fun apply(articles: List<Article>, context: RevendicationContext = RevendicationContext()): List<Article> =
        RevendicationSorting.sort(articles.filter { matches(it, context) }, sort ?: RevendicationSort.NEWEST, context)

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        /** Lenient on read (unknown fields and enum values ignored), compact on write. */
        private val JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = false
            explicitNulls = false
        }

        /** Reads a query written by [toJson]; null when the text is not a query at all. */
        fun fromJson(json: String): RevendicationQuery? = runCatching { JSON.decodeFromString(serializer(), json) }.getOrNull()

        /** Whether [article] is in [scope], from what [context] knows. */
        fun inScope(article: Article, scope: RevendicationScope, context: RevendicationContext): Boolean {
            val address = article.address.toString()
            return when (scope) {
                RevendicationScope.MOVEMENT -> address in context.fromMovement
                RevendicationScope.PARLIAMENT -> address in context.atParliament
                RevendicationScope.CLOSED -> article.status == RevendicationStatus.RETENUE || article.status == RevendicationStatus.CAMPAGNE ||
                    (context.againstCounts[address] ?: 0) > (context.forCounts[address] ?: 0)
                RevendicationScope.NEAR_ME -> article.event.firstTagValue(TAG_TERRITORY_CODE)?.let { it in context.nearMe } == true
                RevendicationScope.MY_VOTES -> address in context.myVotes
            }
        }

        /** The official code of a demand's territory (D-090), beside its free label. */
        const val TAG_TERRITORY_CODE: String = "ppf-territory-code"

        /** A territory as stored in [territories]. */
        fun territoryKey(territory: String): String = FrenchText.fold(territory)

        private fun matchesTheme(article: Article, id: String): Boolean {
            val theme = RevendicationThemes.byId(id)
            return if (theme != null) {
                article.topics.any(theme::matches)
            } else {
                // A theme this version does not know (saved by a newer one): compare the topics themselves.
                article.topics.any { RevendicationThemes.foldTopic(it) == id }
            }
        }

        /**
         * The text searched by [text]: title, summary, body, territory, topics and theme ids. The
         * localized theme labels are not here (they belong to the interface), but a theme id folds
         * like its label ("sante" and "Santé").
         */
        fun searchableText(article: Article): String = buildString {
            append(article.title).append(' ')
            article.summary?.let { append(it).append(' ') }
            append(article.body).append(' ')
            article.territory?.let { append(it).append(' ') }
            article.topics.forEach { append(it).append(' ') }
            RevendicationThemes.of(article).forEach { append(it.id).append(' ') }
        }
    }
}

/**
 * The order of the wall. Kept in one place so that the popularity rank, a placeholder until votes
 * open, is replaced by real tallies in one function.
 */
object RevendicationSorting {

    fun sort(articles: List<Article>, sort: RevendicationSort, context: RevendicationContext = RevendicationContext()): List<Article> {
        fun count(map: Map<String, Long>, article: Article) = map[article.address.toString()] ?: 0L
        val byTitle = compareBy<Article> { FrenchText.fold(it.title) }
        val comparator = when (sort) {
            RevendicationSort.NEWEST -> compareByDescending<Article> { it.publishedAt ?: it.updatedAt }.then(byTitle)
            RevendicationSort.UPDATED -> compareByDescending<Article> { it.updatedAt }.then(byTitle)
            RevendicationSort.MOST_FOR -> compareByDescending<Article> { count(context.forCounts, it) }.thenByDescending { it.publishedAt ?: it.updatedAt }.then(byTitle)
            RevendicationSort.MOST_AGAINST -> compareByDescending<Article> { count(context.againstCounts, it) }.thenByDescending { it.publishedAt ?: it.updatedAt }.then(byTitle)
            RevendicationSort.MOST_DEBATED -> compareByDescending<Article> { context.debateCounts[it.address.toString()] ?: 0 }.thenByDescending { it.publishedAt ?: it.updatedAt }.then(byTitle)
            RevendicationSort.POPULAR -> compareByDescending<Article> { popularityRank(it) }
                .thenByDescending { it.updatedAt }
                .then(byTitle)
        }
        return articles.sortedWith(comparator)
    }

    /**
     * The popularity of a demand, higher first. Votes are not open: the "une personne, une voix"
     * method is still to be decided (brainstorming of the cahier des charges), and nothing may be
     * computed from votes before it is. Until tallies exist, popularity is how far the demand has
     * gone, which the team sets from the debate: CAMPAGNE > RETENUE > TRES_SOUTENUE > EN_DEBAT >
     * NOUVELLE > no status. Ties are broken by the last update in [sort]. Replace this function's
     * body with the tallies (supports, consensus) once they exist; the interface explains the
     * placeholder under the "Popularité" choice.
     */
    fun popularityRank(article: Article): Int = when (article.status) {
        RevendicationStatus.CAMPAGNE -> 5
        RevendicationStatus.RETENUE -> 4
        RevendicationStatus.TRES_SOUTENUE -> 3
        RevendicationStatus.EN_DEBAT -> 2
        RevendicationStatus.NOUVELLE -> 1
        null -> 0
    }
}

/** Statuses as a list of their tag values; unknown values are dropped on read. */
internal object StatusSetSerializer : KSerializer<Set<RevendicationStatus>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Set<RevendicationStatus>) {
        encoder.encodeSerializableValue(delegate, value.sortedBy { it.ordinal }.map { it.tagValue })
    }

    override fun deserialize(decoder: Decoder): Set<RevendicationStatus> =
        decoder.decodeSerializableValue(delegate).mapNotNull(RevendicationStatus::fromTag).toSet()
}
