package org.politisationpopulairefrancaise.server.urne

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.platform.SigningUse
import org.politisationpopulairefrancaise.core.platform.UrnClosures
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.revendications.MutableClock
import org.politisationpopulairefrancaise.server.signing.DraftAuthor
import org.politisationpopulairefrancaise.server.signing.DraftStatusResponse
import org.politisationpopulairefrancaise.server.signing.DraftSubmission
import org.politisationpopulairefrancaise.server.signing.FakePlatformSigner
import org.politisationpopulairefrancaise.server.signing.PlatformSigner
import org.politisationpopulairefrancaise.server.signing.SigningStatus
import org.politisationpopulairefrancaise.server.signing.SigningTicket
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The platform-signed closing statements of the ballot boxes (decider.urn_closure). */
class UrneClosureStatementsTest {
    private val clock = MutableClock(Instant.parse("2026-11-04T12:00:00Z"))
    private val store = UrneStore(TestDatabase.dataSource)
    private val service = UrneService.create(store, UrneFixtures.CONFIG, clock)
    private val events = EventRepository(TestDatabase.dataSource)
    private val fake = FakePlatformSigner { clock.instant.epochSecond }

    @BeforeTest
    fun emptyTables() {
        TestDatabase.reset()
        UrneFixtures.reset()
    }

    private fun closedBox(id: String): Long {
        val urne = service.open(id, "30023:${"a".repeat(64)}:$id", listOf("pour", "contre"), clock.instant, clock.instant.plus(Duration.ofHours(1)), liveResults = false)
        clock.instant = clock.instant.plus(Duration.ofHours(2))
        service.closeDue()
        return urne.number
    }

    private fun published(number: Long) = events.query(EventFilter(kinds = listOf(EventKinds.APP_DATA), identifiers = listOf(UrneClosureStatements.identifierOf(number)), limit = 10))

    @Test
    fun aClosedBoxGetsOneSignedStatementOfItsExactDocument() {
        val statements = UrneClosureStatements(store, service, fake, events, clock)
        val open = service.open("ouverte", "30023:${"b".repeat(64)}:ouverte", listOf("pour", "contre"), clock.instant, null, liveResults = true).number
        val number = closedBox("votation")
        assertEquals(0, statements.runDue(), "first pass: asked")
        assertEquals(1, statements.runDue(), "second pass: signed, kept, published")
        assertEquals(0, statements.runDue())
        assertEquals(1, fake.requests.size, "asked once")
        val event = published(number).single()
        assertEquals(listOf(UrneClosureStatements.identifierOf(number)), event.tagValues("d"))
        assertEquals(listOf(number.toString()), event.tagValues(UrneClosureStatements.TAG_URNE))
        assertEquals(listOf(UrneClosureStatements.sha256Hex(service.closingDocument(number))), event.tagValues(UrneClosureStatements.TAG_SHA256))
        assertEquals("votation", Json.parseToJsonElement(event.content).jsonObject.getValue("id").jsonPrimitive.content)
        // The applications keep this one, the first signed by the platform.
        assertEquals(event, UrnClosures.choose(published(number), fake.trust).getValue(UrneClosureStatements.identifierOf(number)).closure)
        // An open box gets nothing.
        assertTrue(published(open).isEmpty())
    }

    /** A signing service that answers « already closed »: the statement was signed before and lost here. */
    private class AlreadyClosed : PlatformSigner {
        var requests = 0
        override suspend fun currentKey(use: SigningUse): String = IdentityKeys.generate().publicKey.hex
        override suspend fun submitDraft(unsigned: UnsignedEvent, use: SigningUse, doubleValidation: Boolean, author: DraftAuthor?): DraftSubmission = error("automatic use")
        override suspend fun draftStatus(use: SigningUse, eventId: String): DraftStatusResponse? = null
        override suspend fun request(unsigned: UnsignedEvent, use: SigningUse): SigningTicket = SigningTicket("t${++requests}")
        override suspend fun status(ticket: SigningTicket): SigningStatus = SigningStatus.Refused(UrneClosureStatements.URN_ALREADY_CLOSED)
    }

    @Test
    fun alreadyClosedIsNeverAskedAgainAndAlertsTheTeam() {
        val signer = AlreadyClosed()
        val statements = UrneClosureStatements(store, service, signer, events, clock)
        val number = closedBox("perdue")
        repeat(4) { statements.runDue() }
        assertEquals(1, signer.requests)
        assertEquals(listOf(number to UrneClosureStatements.URN_ALREADY_CLOSED), store.refusedClosures())
        assertTrue(published(number).isEmpty())
    }
}
