package org.politisationpopulairefrancaise.core.revendications

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.RevendicationStatus
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The views of MVP 1: sorts by votes and debate, scopes known by the device. */
class RevendicationScopesTest {
    private val editor = "1".repeat(64)
    private var counter = 0

    private fun demand(slug: String, status: RevendicationStatus = RevendicationStatus.EN_DEBAT, code: String? = null, publishedAt: Long = 1_000): Article {
        val tags = buildList {
            add(listOf("d", slug)); add(listOf("title", "[EXEMPLE] $slug")); add(listOf("published_at", publishedAt.toString()))
            add(listOf("ppf-type", "revendication")); add(listOf("ppf-origin", "human")); add(listOf("ppf-status", status.tagValue))
            code?.let { add(listOf("ppf-territory-code", it)) }
        }
        counter++
        return Article.from(SignedEvent(counter.toString(16).padStart(64, '0'), editor, publishedAt, Article.KIND, tags, "", "0".repeat(128)))!!
    }

    private val a = demand("a", publishedAt = 1_000)
    private val b = demand("b", code = "69123", publishedAt = 2_000)
    private val c = demand("c", status = RevendicationStatus.RETENUE, publishedAt = 3_000)
    private val all = listOf(a, b, c)
    private fun key(article: Article) = article.address.toString()
    private val context = RevendicationContext(
        forCounts = mapOf(key(a) to 5L, key(b) to 1L),
        againstCounts = mapOf(key(a) to 1L, key(b) to 3L),
        debateCounts = mapOf(key(c) to 4, key(a) to 1),
        fromMovement = setOf(key(a)),
        atParliament = setOf(key(b)),
        nearMe = setOf("69123", "69"),
        myVotes = setOf(key(b)),
    )

    private fun slugs(query: RevendicationQuery) = query.apply(all, context).map { it.slug }

    @Test
    fun theSortsFollowTheVotesAndTheDebate() {
        assertEquals(listOf("a", "b", "c"), slugs(RevendicationQuery(sort = RevendicationSort.MOST_FOR)))
        assertEquals(listOf("b", "a", "c"), slugs(RevendicationQuery(sort = RevendicationSort.MOST_AGAINST)))
        assertEquals(listOf("c", "a", "b"), slugs(RevendicationQuery(sort = RevendicationSort.MOST_DEBATED)))
        assertEquals(listOf("c", "b", "a"), slugs(RevendicationQuery(sort = RevendicationSort.NEWEST)))
    }

    @Test
    fun theScopesComeFromWhatTheDeviceKnows() {
        assertEquals(listOf("a"), slugs(RevendicationQuery(scopes = setOf(RevendicationScope.MOVEMENT))))
        assertEquals(listOf("b"), slugs(RevendicationQuery(scopes = setOf(RevendicationScope.PARLIAMENT))))
        // Closed: retained, or more « contre » than « pour ».
        assertEquals(setOf("b", "c"), slugs(RevendicationQuery(scopes = setOf(RevendicationScope.CLOSED))).toSet())
        assertEquals(listOf("b"), slugs(RevendicationQuery(scopes = setOf(RevendicationScope.NEAR_ME))))
        assertEquals(listOf("b"), slugs(RevendicationQuery(scopes = setOf(RevendicationScope.MY_VOTES))))
        // A saved view keeps its scopes through its JSON form.
        val view = RevendicationQuery(scopes = setOf(RevendicationScope.MOVEMENT), sort = RevendicationSort.MOST_FOR)
        assertEquals(view, RevendicationQuery.fromJson(view.toJson()))
    }
}
