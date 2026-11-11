package org.politisationpopulairefrancaise.server.voir

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.voir.DashboardFormat
import org.politisationpopulairefrancaise.core.voir.DashboardMetric
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** « Tableau de bord fédérateur » (PP-07): the published contents counted once per address, in the month of their first publication. */
class DashboardServiceTest {
    private val events = EventRepository(TestDatabase.dataSource)
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC)

    private fun store(slug: String, type: ArticleType, publishedAt: Long, createdAt: Long = publishedAt) {
        val unsigned = Article.build(citizen.publicKey, slug, "[EXEMPLE] $slug", null, emptyList(), type, ContentOrigin.HUMAN, publishedAt, createdAt, "[EXEMPLE] Texte.")
        events.store(runBlocking { citizen.sign(unsigned) })
    }

    @BeforeTest
    fun storeSomeContents() {
        TestDatabase.reset()
        // 15 September and 2 October 2026 (Paris).
        store("salaires", ArticleType.REVENDICATION, 1_789_466_400)
        store("transports", ArticleType.REVENDICATION, 1_791_000_000)
        // A new version of the same demand, a month later: still one demand, in its first month.
        store("salaires", ArticleType.REVENDICATION, 1_789_466_400, createdAt = 1_791_100_000)
        store("greve", ArticleType.APPEL, 1_791_000_000)
    }

    @Test
    fun theDemandsAndTheCallsAreCountedOnceInTheMonthOfTheirFirstPublication() = runBlocking {
        val dashboard = DashboardService(events, clock, ttl = Duration.ZERO).current()
        assertEquals("2026-11-09", dashboard.updatedOn)
        val demands = dashboard.series(DashboardMetric.DEMANDS)
        assertTrue(demands.measured)
        assertEquals(mapOf("2026-09" to 1L, "2026-10" to 1L), demands.months.associate { it.month to it.added })
        assertEquals(1L, dashboard.series(DashboardMetric.CALLS).total())
        assertEquals(0L, dashboard.series(DashboardMetric.ORGANISATIONS).total())
        assertNull(dashboard.series(DashboardMetric.MEMBERS).total(), "no source yet: not measured")
        assertNull(dashboard.series(DashboardMetric.ACTIVE_ACCOUNTS).total(), "never a tracker (S-76 first)")
    }

    @Test
    fun theJsonAndThePageAreServed() = testApplication {
        val service = DashboardService(events, clock, ttl = Duration.ZERO)
        application { routing { dashboardRoutes(service) } }
        val json = client.get(DashboardFormat.PATH)
        assertEquals(HttpStatusCode.OK, json.status)
        val read = assertNotNull(DashboardFormat.read(json.bodyAsText()))
        assertEquals(2L, read.series(DashboardMetric.DEMANDS).total())
        val page = client.get("/tableau-de-bord?depuis=2026-10&jusqua=2026-10").bodyAsText()
        assertTrue(page.contains("Revendications publiées"), page)
        assertTrue(page.contains("non mesuré"))
        assertFalse(page.contains("<script"))
        assertEquals(HttpStatusCode.OK, client.get("/tableau-de-bord?depuis=nimporte").status, "a wrong month is ignored")
    }
}
