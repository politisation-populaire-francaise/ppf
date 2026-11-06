package org.politisationpopulairefrancaise.server.agir

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.DAY
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.HOUR
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.error
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.publish
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The agenda of the mobilizations: its index, GET /api/v1/agenda and its iCalendar feed. */
class AgendaApiTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val organiser = LocalEventSigner(IdentityKeys.generate())
    private val events = EventRepository(TestDatabase.dataSource)
    private val agenda = AgendaIndex(TestDatabase.dataSource)

    /** "Now" for the application: 6 November 2026 at noon, UTC. */
    private val now = Instant.parse("2026-11-06T12:00:00Z").epochSecond
    private val clock = MutableClock(Instant.ofEpochSecond(now))

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun ApplicationTestBuilder.start() {
        application {
            module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications(), testAgir(AgirConfig(cleanupInterval = Duration.ZERO), clock))
        }
    }

    private suspend fun HttpClient.agenda(query: String = ""): List<SignedEvent> {
        val response = get("/api/v1/agenda$query")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return Json.decodeFromString(ListSerializer(SignedEvent.serializer()), response.bodyAsText())
    }

    private suspend fun HttpClient.titles(query: String = ""): List<String?> = agenda(query).map { it.firstTagValue("title") }

    private fun event(
        d: String,
        start: Long,
        end: Long? = null,
        type: String = "manifestation",
        territory: String = "Lyon",
        status: String? = null,
        organizers: List<String> = emptyList(),
        author: LocalEventSigner = editor,
        createdAt: Long = now - 60,
    ): SignedEvent = runBlocking {
        author.mobilization(mobilizationTags(d = d, title = d, start = start, end = end, type = type, territory = territory, status = status, organizers = organizers), createdAt = createdAt)
    }

    @Test
    fun theDefaultPeriodGoesFromTwelveHoursAgoToNinetyDaysAhead() = testApplication {
        start()
        listOf(
            event("fini-hier", start = now - 2 * DAY, end = now - DAY),
            event("commence-il-y-a-11h", start = now - 11 * HOUR),
            event("commence-il-y-a-13h", start = now - 13 * HOUR),
            event("greve-en-cours", start = now - 3 * DAY, end = now + 2 * DAY, type = "greve"),
            event("dans-89-jours", start = now + 89 * DAY),
            event("dans-91-jours", start = now + 91 * DAY),
        ).forEach { assertEquals(HttpStatusCode.Created, client.publish(it).status) }
        // Sorted by start; an event under way (end in the future) stays listed.
        assertEquals(listOf("greve-en-cours", "commence-il-y-a-11h", "dans-89-jours"), client.titles())
        // Explicit bounds, both included: every event that overlaps them, the strike under way too.
        assertEquals(listOf("greve-en-cours", "fini-hier"), client.titles("?from=${now - 2 * DAY}&to=${now - DAY}"))
        assertEquals(listOf("dans-91-jours"), client.titles("?from=${now + 91 * DAY}&to=${now + 91 * DAY}"))
        // The clock moves: the default period follows it (the strike ends right now, still listed).
        clock.advanceSeconds(2 * DAY)
        assertEquals(listOf("greve-en-cours", "dans-89-jours", "dans-91-jours"), client.titles())
        clock.advanceSeconds(13 * HOUR)
        assertEquals(listOf("dans-89-jours", "dans-91-jours"), client.titles())
    }

    @Test
    fun filtersByTerritoryTypeAndOrganiser() = testApplication {
        start()
        val partner = LocalEventSigner(IdentityKeys.generate()).publicKey.hex
        listOf(
            event("lyon-manif", start = now + DAY, territory = "Lyon"),
            event("saint-etienne-ag", start = now + 2 * DAY, type = "assemblee-generale", territory = "Saint-Étienne"),
            event("france-rassemblement", start = now + 3 * DAY, type = "rassemblement", territory = "France", organizers = listOf(partner)),
            event("organisee-par-une-cle", start = now + 4 * DAY, author = organiser),
        ).forEach { events.store(it) }
        // Accents, case and punctuation ignored, the whole territory compared.
        assertEquals(listOf("saint-etienne-ag"), client.titles("?territory=saint%20etienne"))
        assertEquals(listOf("saint-etienne-ag"), client.titles("?territory=SAINT-%C3%89TIENNE"))
        assertEquals(emptyList(), client.titles("?territory=saint"))
        // A territory without letter nor digit does not filter.
        assertEquals(4, client.agenda("?territory=%20-%20").size)
        assertEquals(listOf("lyon-manif", "organisee-par-une-cle"), client.titles("?type=manifestation"))
        assertEquals(listOf("saint-etienne-ag", "france-rassemblement"), client.titles("?type=assemblee-generale&type=rassemblement"))
        // Organiser: the author, or a key named organiser.
        assertEquals(listOf("organisee-par-une-cle"), client.titles("?organizer=${organiser.publicKey.hex}"))
        assertEquals(listOf("france-rassemblement"), client.titles("?organizer=$partner"))
        assertEquals(listOf("france-rassemblement", "organisee-par-une-cle"), client.titles("?organizer=$partner&organizer=${organiser.publicKey.hex}"))
        assertEquals(listOf("lyon-manif"), client.titles("?type=manifestation&territory=lyon&organizer=${editor.publicKey.hex}"))
        assertEquals(listOf("lyon-manif", "saint-etienne-ag"), client.titles("?limit=2"))
    }

    @Test
    fun theIndexFollowsCorrectionsAndCancellations() = testApplication {
        start()
        val first = event("assemblee", start = now + DAY, createdAt = 1_000)
        client.publish(first)
        assertEquals(listOf(first), client.agenda())
        // Postponed to another date: same address, newer version, the old row goes with the old event.
        val moved = event("assemblee", start = now + 10 * DAY, status = "reporte", createdAt = 2_000)
        assertEquals(HttpStatusCode.Created, client.publish(moved).status)
        assertEquals(listOf(moved), client.agenda())
        assertEquals(emptyList(), client.agenda("?from=${now}&to=${now + 2 * DAY}"))
        // Cancelled: still listed (calendars must learn it), with its status.
        val cancelled = event("assemblee", start = now + 10 * DAY, status = "annule", createdAt = 3_000)
        client.publish(cancelled)
        assertEquals(listOf(cancelled), client.agenda())
        assertTrue(client.get("/api/v1/agenda.ics").bodyAsText().contains("STATUS:CANCELLED\r\n"))
        // An older version arriving late changes nothing.
        assertEquals(HttpStatusCode.OK, client.publish(event("assemblee", start = now + DAY, createdAt = 1_500)).status)
        assertEquals(listOf(cancelled), client.agenda())
        assertEquals(1, rows())
    }

    @Test
    fun heldMobilizationsAreNotInTheAgendaUntilApproved() = testApplication {
        start()
        val proposed = event("proposee", start = now + DAY, author = organiser)
        assertEquals(HttpStatusCode.Accepted, client.publish(proposed).status)
        assertEquals(emptyList(), client.agenda())
        HeldQueue(TestDatabase.dataSource, events).approve(proposed.id)
        assertEquals(listOf(proposed), client.agenda())
    }

    @Test
    fun mobilizationsStoredBeforeTheIndexAreIndexedOnce() = runBlocking {
        val stored = event("ancienne", start = now + DAY)
        events.store(stored)
        TestDatabase.dataSource.connection.use { it.createStatement().use { s -> s.executeUpdate("DELETE FROM agenda_entry") } }
        assertEquals(1, agenda.indexMissing())
        assertEquals(0, agenda.indexMissing())
        assertEquals(listOf(stored), agenda.query(AgendaFilter(from = now, to = now + 2 * DAY)))
        // An event stored directly without a readable start is kept out of the index, without failing the store.
        val broken = runBlocking { editor.mobilization(mobilizationTags(d = "sans-date").filterNot { it[0] == "start" }) }
        events.store(broken)
        assertEquals(1, rows())
    }

    @Test
    fun theParametersAreChecked() = testApplication {
        start()
        val bad = listOf(
            "from=hier", "to=-5", "from=10&to=5", "from=1&from=2", "territory=${"x".repeat(201)}", "territory=a&territory=b",
            "type=emeute", "organizer=ABC", "organizer=${"0".repeat(63)}", "limit=0", "limit=501", "limit=dix", "limit=1&limit=2",
            (1..21).joinToString("&") { "type=greve" },
        )
        for (query in bad) {
            for (path in listOf("/api/v1/agenda", "/api/v1/agenda.ics")) {
                val response = client.get("$path?$query")
                assertEquals(HttpStatusCode.BadRequest, response.status, "$path?$query")
                assertEquals("bad_parameter", response.error().error)
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/agenda?limit=500&from=0&to=0&type=greve").status)
    }

    @Test
    fun theFeedIsACalendarWithTheSameSelection() = testApplication {
        start()
        events.store(event("lyon", start = now + DAY, territory = "Lyon"))
        events.store(event("paris", start = now + 2 * DAY, territory = "Paris"))
        val response = client.get("/api/v1/agenda.ics?territory=paris")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("text/calendar; charset=utf-8", response.headers[HttpHeaders.ContentType]?.lowercase()?.replace("\"", ""))
        val body = response.bodyAsText()
        assertTrue(body.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(body.endsWith("END:VCALENDAR\r\n"))
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(body).count())
        // Unfolded (the line is longer than 75 octets): the stable address of the event.
        assertTrue(body.replace("\r\n ", "").contains("UID:31923:${editor.publicKey.hex}:paris\r\n"))
        // The security headers of every answer apply here too.
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
    }

    @Test
    fun theSiteShowsTheAgendaWithoutScript() = testApplication {
        start()
        events.store(event("lyon", start = now + DAY, territory = "Lyon"))
        events.store(event("paris", start = now + 2 * DAY, territory = "Paris", type = "reunion"))
        val page = client.get("/agenda?territory=LYON")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("<h3>lyon</h3>"), html)
        assertTrue(!html.contains("<h3>paris</h3>") && !html.contains("<script"))
        assertTrue(client.get("/agenda?type=reunion").bodyAsText().contains("<h3>paris</h3>"))
        // An unknown type is ignored rather than refused: the page always answers.
        assertEquals(HttpStatusCode.OK, client.get("/agenda?type=emeute").status)
    }

    private fun rows(): Int = TestDatabase.dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            val result = statement.executeQuery("SELECT count(*) FROM agenda_entry")
            result.next()
            result.getInt(1)
        }
    }
}
