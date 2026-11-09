package org.politisationpopulairefrancaise.server.voir

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.Dashboard
import org.politisationpopulairefrancaise.core.voir.DashboardFormat
import org.politisationpopulairefrancaise.core.voir.DashboardMetric
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.web.voir.DashboardSource
import org.politisationpopulairefrancaise.web.voir.dashboardPage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** One figure of the dashboard, counted by the pole that owns its data: what each month (`AAAA-MM`) added. */
fun interface DashboardProvider {
    fun monthly(): Map<String, Long>
}

/**
 * « Tableau de bord fédérateur » (M34, PP-07, D-107; docs/integration/tableau-de-bord.md): the figures the server
 * can count honestly from what it already publishes, month by month; never a person, never a tracker (S-76). Voir
 * counts the published demands, calls and directory entries of organisations (one per address, in the month of
 * their first publication); the other figures come from the providers of their poles ([extra]), and until then
 * they are « non mesuré ». Computed at most every [ttl].
 */
class DashboardService(
    private val events: EventRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = 10.minutes,
    extra: Map<DashboardMetric, DashboardProvider> = emptyMap(),
) {
    private val providers: Map<DashboardMetric, DashboardProvider> = mapOf(
        DashboardMetric.DEMANDS to published(ArticleType.REVENDICATION),
        DashboardMetric.CALLS to published(ArticleType.APPEL),
        DashboardMetric.ORGANISATIONS to published(ArticleType.ORGANISATION),
    ) + extra

    private val lock = Mutex()
    private var cached: Dashboard? = null
    private var computedAt: Long = Long.MIN_VALUE

    suspend fun current(): Dashboard = lock.withLock {
        val now = clock.millis()
        cached?.takeIf { now - computedAt < ttl.inWholeMilliseconds }?.let { return@withLock it }
        val fresh = withContext(Dispatchers.IO) { compute() }
        cached = fresh
        computedAt = now
        fresh
    }

    private fun compute(): Dashboard {
        val today = Instant.ofEpochMilli(clock.millis()).atZone(PARIS).toLocalDate().toString()
        return DashboardFormat.of(today, providers.mapValues { (_, provider) -> provider.monthly() })
    }

    /** The published contents of [type] (kind 30023): one per address, in the month of their first publication (Paris). */
    private fun published(type: ArticleType) = DashboardProvider {
        val first = HashMap<String, Long>()
        var until: Long? = null
        var read = 0
        while (read < MAX_EVENTS) {
            val page = events.query(EventFilter(kinds = listOf(Article.KIND), tags = mapOf(Article.TAG_TYPE to listOf(type.tagValue)), until = until, limit = EventFilter.MAX_LIMIT))
            page.forEach { event ->
                val address = "${event.pubkey}:${event.firstTagValue("d").orEmpty()}"
                val at = publishedAt(event)
                first[address] = minOf(first[address] ?: Long.MAX_VALUE, at)
            }
            read += page.size
            if (page.size < EventFilter.MAX_LIMIT) break
            // Same-second events may repeat on the next page: an address is counted once.
            val last = page.last().createdAt
            if (last == until) break
            until = last
        }
        first.values.groupingBy { monthOf(it) }.eachCount().mapValues { it.value.toLong() }
    }

    private fun publishedAt(event: SignedEvent): Long = event.firstTagValue(Article.TAG_PUBLISHED_AT)?.toLongOrNull()?.takeIf { it > 0 } ?: event.createdAt

    private fun monthOf(seconds: Long): String = Instant.ofEpochSecond(seconds).atZone(PARIS).toLocalDate().toString().take(7)

    companion object {
        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /** A bound on what one computation reads per figure. */
        const val MAX_EVENTS: Int = 100_000
    }
}

/**
 * `GET /api/v1/tableau-de-bord` (the JSON of [Dashboard], the same for everyone, reusable as is) and the page
 * `/tableau-de-bord` of the site, with a period chosen by `?depuis=AAAA-MM&jusqua=AAAA-MM` (no script needed).
 */
fun Route.dashboardRoutes(service: DashboardService?) {
    if (service == null) return
    get(DashboardFormat.PATH) {
        val dashboard = service.current()
        // Public figures, the same for everyone: any cache may keep them a few minutes.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=600")
        call.respondText(DashboardFormat.write(dashboard), ContentType.Application.Json)
    }
    dashboardPage(DashboardSource { service.current() })
}
