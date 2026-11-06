package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Matching, overlay and sorting of the demands wall; the demands are built without signature (not checked here). */
class RevendicationQueryTest {

    private val editor = "1".repeat(64)
    private val me = "2".repeat(64)
    private val other = "3".repeat(64)

    private var counter = 0

    private fun demand(
        slug: String,
        title: String = "[EXEMPLE] $slug",
        topics: List<String> = emptyList(),
        territory: String? = "France",
        status: RevendicationStatus? = RevendicationStatus.NOUVELLE,
        publishedAt: Long? = 1_000,
        updatedAt: Long = 1_000,
        author: String = editor,
        summary: String? = null,
        body: String = "",
    ): Article {
        val tags = buildList {
            add(listOf("d", slug))
            add(listOf("title", title))
            summary?.let { add(listOf("summary", it)) }
            publishedAt?.let { add(listOf("published_at", it.toString())) }
            add(listOf("ppf-type", "revendication"))
            add(listOf("ppf-origin", "human"))
            status?.let { add(listOf("ppf-status", it.tagValue)) }
            territory?.let { add(listOf("ppf-territory", it)) }
            topics.forEach { add(listOf("t", it)) }
        }
        counter++
        val event = SignedEvent(id = counter.toString(16).padStart(64, '0'), pubkey = author, createdAt = updatedAt, kind = Article.KIND, tags = tags, content = body, sig = "0".repeat(128))
        return Article.from(event)!!
    }

    private val sante = demand("medecin", topics = listOf("sante", "services-publics"), summary = "Une consultation médicale de garde.")
    private val lyon = demand("lyon", title = "[EXEMPLE] Des transports la nuit", topics = listOf("transports", "metropole"), territory = "Métropole de Lyon", status = RevendicationStatus.TRES_SOUTENUE)
    private val creches = demand("creches", topics = listOf("Famille", "travail"), status = null, body = "Des crèches ouvertes tôt le matin.")
    private val mine = demand("mine", topics = listOf("logement"), territory = "Marseille", status = RevendicationStatus.CAMPAGNE, author = me)
    private val followedOne = demand("suivi", topics = listOf("ecole"), territory = null, author = other)
    private val all = listOf(sante, lyon, creches, mine, followedOne)

    private fun RevendicationQuery.slugs(context: RevendicationContext = RevendicationContext()) = all.filter { matches(it, context) }.map { it.slug }.toSet()

    @Test
    fun anEmptyQueryKeepsEverything() {
        val query = RevendicationQuery()
        assertTrue(query.isUnconstrained)
        assertEquals(0, query.constraintCount)
        assertEquals(all.map { it.slug }.toSet(), query.slugs())
    }

    @Test
    fun themesMatchAnyOfTheirIdsAndTheirAliases() {
        assertEquals(setOf("medecin"), RevendicationQuery(themes = setOf("sante")).slugs())
        // "Famille" is an alias of Société, "ecole" of Éducation.
        assertEquals(setOf("creches"), RevendicationQuery(themes = setOf("societe")).slugs())
        assertEquals(setOf("creches", "suivi"), RevendicationQuery(themes = setOf("societe", "education")).slugs())
        // A theme id this version does not know is compared with the topics themselves.
        assertEquals(setOf("lyon"), RevendicationQuery(themes = setOf("metropole")).slugs())
    }

    @Test
    fun territoriesAreComparedFoldedAndMissingTerritoriesNeverMatch() {
        assertEquals(setOf("lyon"), RevendicationQuery(territories = setOf("metropole de lyon")).slugs())
        assertEquals(setOf("lyon"), RevendicationQuery(territories = setOf("Métropole  de LYON")).slugs())
        assertEquals("metropole de lyon", RevendicationQuery.territoryKey("Métropole de Lyon"))
        assertEquals(setOf("medecin", "creches", "mine"), RevendicationQuery(territories = setOf("france", "marseille")).slugs())
    }

    @Test
    fun statusesMatchAnyAndADemandWithoutStatusMatchesNone() {
        assertEquals(setOf("lyon", "mine"), RevendicationQuery(statuses = setOf(RevendicationStatus.TRES_SOUTENUE, RevendicationStatus.CAMPAGNE)).slugs())
        assertFalse(RevendicationQuery(statuses = RevendicationStatus.entries.toSet()).matches(creches))
    }

    @Test
    fun authorsFollowOrMineNeedTheDeviceContext() {
        val context = RevendicationContext(followed = setOf(other), myKey = me)
        assertEquals(setOf("suivi"), RevendicationQuery(authors = AuthorScope.FOLLOWED).slugs(context))
        assertEquals(setOf("mine"), RevendicationQuery(authors = AuthorScope.MINE).slugs(context))
        // Nothing followed: the view is empty; no account: nothing is "mine".
        assertEquals(emptySet(), RevendicationQuery(authors = AuthorScope.FOLLOWED).slugs(RevendicationContext()))
        assertEquals(emptySet(), RevendicationQuery(authors = AuthorScope.MINE).slugs(RevendicationContext(followed = setOf(me))))
    }

    @Test
    fun textMatchesEveryWordAsAPrefixOverTitleSummaryBodyTerritoryAndThemes() {
        assertEquals(setOf("medecin"), RevendicationQuery(text = "medicale garde").slugs())
        assertEquals(setOf("creches"), RevendicationQuery(text = "Crèches tôt").slugs())
        assertEquals(setOf("lyon"), RevendicationQuery(text = "lyon nuit").slugs())
        // A theme id folds like its label: "santé" finds the demand tagged "sante".
        assertEquals(setOf("medecin"), RevendicationQuery(text = "santé").slugs())
        assertEquals(setOf("suivi"), RevendicationQuery(text = "educ").slugs())
        assertEquals(emptySet(), RevendicationQuery(text = "lyon garde").slugs())
        assertEquals(all.size, RevendicationQuery(text = "   ").slugs().size)
    }

    @Test
    fun criteriaCombineWithAnd() {
        val query = RevendicationQuery(themes = setOf("sante", "transports"), territories = setOf("france"))
        assertEquals(setOf("medecin"), query.slugs())
        assertEquals(setOf(RevendicationCriterion.THEMES, RevendicationCriterion.TERRITORIES), query.constrained)
        assertEquals(3, query.constraintCount)
    }

    @Test
    fun theViewKeepsWhatItFixesAndFiltersFillTheRest() {
        val view = RevendicationQuery(themes = setOf("sante"), authors = AuthorScope.FOLLOWED)
        val filters = RevendicationQuery(
            themes = setOf("logement"),
            territories = setOf("lyon"),
            statuses = setOf(RevendicationStatus.NOUVELLE),
            authors = AuthorScope.MINE,
            text = "garde",
            sort = RevendicationSort.POPULAR,
        )
        val effective = view.overlay(filters)
        assertEquals(setOf("sante"), effective.themes)
        assertEquals(AuthorScope.FOLLOWED, effective.authors)
        assertEquals(setOf("lyon"), effective.territories)
        assertEquals(setOf(RevendicationStatus.NOUVELLE), effective.statuses)
        assertEquals("garde", effective.text)
        assertEquals(RevendicationSort.POPULAR, effective.sort)
        assertEquals(setOf(RevendicationCriterion.THEMES, RevendicationCriterion.AUTHORS), BuiltInRevendicationView("v", view, BuiltInKind.THEME).fixedCriteria)
    }

    @Test
    fun theSortComesFromTheFiltersThenTheViewThenNewest() {
        assertEquals(RevendicationSort.NEWEST, RevendicationQuery().overlay(RevendicationQuery()).sort)
        assertEquals(RevendicationSort.UPDATED, RevendicationQuery(sort = RevendicationSort.UPDATED).overlay(RevendicationQuery()).sort)
        assertEquals(RevendicationSort.POPULAR, RevendicationQuery(sort = RevendicationSort.UPDATED).overlay(RevendicationQuery(sort = RevendicationSort.POPULAR)).sort)
    }

    @Test
    fun withoutDropsTheGivenCriteriaAndKeepsTheSort() {
        val query = RevendicationQuery(themes = setOf("sante"), text = "x", authors = AuthorScope.MINE, sort = RevendicationSort.UPDATED)
        val rest = query.without(setOf(RevendicationCriterion.THEMES, RevendicationCriterion.AUTHORS))
        assertEquals(RevendicationQuery(text = "x", sort = RevendicationSort.UPDATED), rest)
    }

    @Test
    fun newestSortsByPublicationThenTitle() {
        val a = demand("a", title = "[EXEMPLE] Bravo", publishedAt = 300, updatedAt = 900)
        val b = demand("b", title = "[EXEMPLE] Alpha", publishedAt = 300, updatedAt = 300)
        val c = demand("c", title = "[EXEMPLE] Zoulou", publishedAt = null, updatedAt = 500)
        val d = demand("d", title = "[EXEMPLE] Écho", publishedAt = 100, updatedAt = 1000)
        assertEquals(listOf("c", "b", "a", "d"), RevendicationSorting.sort(listOf(a, b, c, d), RevendicationSort.NEWEST).map { it.slug })
        // apply() sorts newest first when the query has no sort.
        assertEquals(listOf("c", "b", "a", "d"), RevendicationQuery().apply(listOf(d, a, c, b)).map { it.slug })
    }

    @Test
    fun updatedSortsByLastUpdate() {
        val a = demand("a", publishedAt = 300, updatedAt = 900)
        val b = demand("b", publishedAt = 800, updatedAt = 300)
        val c = demand("c", publishedAt = 100, updatedAt = 1000)
        assertEquals(listOf("c", "a", "b"), RevendicationQuery(sort = RevendicationSort.UPDATED).apply(listOf(a, b, c)).map { it.slug })
    }

    @Test
    fun popularityFollowsTheStatusUntilVotesOpenThenTheLastUpdate() {
        val campagne = demand("campagne", status = RevendicationStatus.CAMPAGNE, updatedAt = 10)
        val retenue = demand("retenue", status = RevendicationStatus.RETENUE, updatedAt = 10)
        val soutenue = demand("soutenue", status = RevendicationStatus.TRES_SOUTENUE, updatedAt = 10)
        val debatAncien = demand("debat-ancien", status = RevendicationStatus.EN_DEBAT, updatedAt = 10)
        val debatRecent = demand("debat-recent", status = RevendicationStatus.EN_DEBAT, updatedAt = 20)
        val nouvelle = demand("nouvelle", status = RevendicationStatus.NOUVELLE, updatedAt = 99)
        val sansStatut = demand("sans-statut", status = null, updatedAt = 100)
        val sorted = RevendicationSorting.sort(listOf(sansStatut, nouvelle, debatAncien, soutenue, debatRecent, retenue, campagne), RevendicationSort.POPULAR)
        assertEquals(listOf("campagne", "retenue", "soutenue", "debat-recent", "debat-ancien", "nouvelle", "sans-statut"), sorted.map { it.slug })
        assertEquals(0, RevendicationSorting.popularityRank(sansStatut))
    }

    @Test
    fun jsonRoundTripsWithStableNamesAndStatusTags() {
        val query = RevendicationQuery(
            themes = setOf("sante", "logement"),
            territories = setOf("metropole de lyon"),
            statuses = setOf(RevendicationStatus.EN_DEBAT, RevendicationStatus.TRES_SOUTENUE),
            authors = AuthorScope.FOLLOWED,
            text = "nuit",
            sort = RevendicationSort.POPULAR,
        )
        val json = query.toJson()
        assertEquals(
            """{"themes":["sante","logement"],"territories":["metropole de lyon"],"statuses":["en-debat","tres-soutenue"],"authors":"followed","text":"nuit","sort":"popular"}""",
            json,
        )
        assertEquals(query, RevendicationQuery.fromJson(json))
        assertEquals("{}", RevendicationQuery().toJson())
        assertEquals(RevendicationQuery(), RevendicationQuery.fromJson("{}"))
    }

    @Test
    fun readingIgnoresUnknownFieldsAndValues() {
        val read = RevendicationQuery.fromJson(
            """{"themes":["sante"],"statuses":["en-debat","inconnu"],"authors":"robots","sort":"au-hasard","future":{"a":1}}""",
        )
        assertEquals(RevendicationQuery(themes = setOf("sante"), statuses = setOf(RevendicationStatus.EN_DEBAT)), read)
        assertNull(RevendicationQuery.fromJson("pas du json"))
        assertNull(RevendicationQuery.fromJson("""{"themes":"sante"}"""))
    }
}
