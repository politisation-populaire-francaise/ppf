package org.politisationpopulairefrancaise.server.contact

import org.politisationpopulairefrancaise.core.contact.CaseNature
import org.politisationpopulairefrancaise.core.contact.CaseStatus
import org.politisationpopulairefrancaise.core.contact.ClaimMotif
import org.politisationpopulairefrancaise.core.contact.ContactCategory
import org.politisationpopulairefrancaise.core.contact.TicketSubtype
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** The durations of the cases (point d'attention n° 20, S-109), applied by [ContactSpace.retentionOnce] with a clock moved by hand. */
class ContactRetentionTest {
    private val clock = MutableClock(Instant.parse("2026-11-08T10:00:00Z"))
    private val space = ContactFixtures.space(clock)
    private val store = space.store()

    @BeforeTest
    fun emptyTables() = BackOfficeTestData.reset()

    private fun open(nature: CaseNature): ContactFixtures.Draft {
        val draft = ContactFixtures.Draft(nature, clock.instant())
        assertIs<CreateOutcome.Created>(space.create(draft.creation()))
        return draft
    }

    private fun close(draft: ContactFixtures.Draft) =
        store.change(draft.number.display) { _, case -> case.copy(status = CaseStatus.CLOSED, closedAt = clock.instant()) to Unit }

    private fun hold(draft: ContactFixtures.Draft, review: Instant) =
        store.change(draft.number.display) { _, case -> case.copy(legalHoldReason = "[EXEMPLE] Procédure en cours", legalHoldReviewAt = review) to Unit }

    private fun case(draft: ContactFixtures.Draft): StoredCase? = store.find(draft.number.display)

    private fun advance(duration: Duration) = clock.advance(duration)

    /** [n] calendar years from now, as the retention counts them. */
    private fun years(n: Long): Duration = (clock.instant().atOffset(ZoneOffset.UTC).plusYears(n).toInstant().epochSecond - clock.instant().epochSecond).seconds

    private fun teamAccount(): UUID = TestDatabase.dataSource.connection.use { connection ->
        BackOfficeTestData.accounts.create(connection, "Équipe ${UUID.randomUUID().toString().take(8)}", ByteArray(32).also { SecureRandom().nextBytes(it) }, null, clock.instant()).id
    }

    @Test
    fun aCaseAwaitingThePersonIsClosedAfterSixtyDaysWithoutNews() {
        val draft = open(CaseNature.Contact(ContactCategory.JOURNALIST))
        store.change(draft.number.display) { _, case -> case.copy(status = CaseStatus.AWAITING_YOU, lastActivityAt = clock.instant()) to Unit }
        val waiting = open(CaseNature.Contact(ContactCategory.CITIZEN))
        advance(59.days)
        assertEquals(0, space.retentionOnce().closed)
        assertEquals(CaseStatus.AWAITING_YOU, case(draft)!!.status)
        advance(2.days)
        assertEquals(1, space.retentionOnce().closed)
        val closed = assertNotNull(case(draft))
        assertEquals(CaseStatus.CLOSED, closed.status)
        assertEquals(clock.instant(), closed.closedAt)
        // A case the team has not answered yet is never closed for silence.
        assertEquals(CaseStatus.RECEIVED, case(waiting)!!.status)
    }

    @Test
    fun ticketsAndContactsAreDeletedThreeYearsAfterClosingWithTheirEnvelopes() {
        val bug = open(CaseNature.Ticket(TicketSubtype.BUG))
        val contact = open(CaseNature.Contact(ContactCategory.ASSOCIATION))
        val claim = open(CaseNature.Claim(ClaimMotif.OTHER))
        val stillOpen = open(CaseNature.Ticket(TicketSubtype.SUGGESTION))
        assertTrue(store.addOutgoing(contact.number.display, contact.reply(), teamAccount(), clock.instant()))
        listOf(bug, contact, claim).forEach(::close)
        advance(years(3) - 1.days)
        assertEquals(0, space.retentionOnce().deleted)
        advance(2.days)
        assertEquals(2, space.retentionOnce().deleted)
        assertNull(case(bug))
        assertNull(case(contact))
        assertEquals(0 to 0, store.envelopeCounts(contact.number.display))
        // A claim is not deleted; a case never closed is never deleted.
        assertNotNull(case(claim))
        assertNotNull(case(stillOpen))
    }

    @Test
    fun claimsAreAnonymisedFiveYearsAfterClosingAndNeverDeleted() {
        val claim = open(CaseNature.Claim(ClaimMotif.DATA_RIGHTS))
        val author = teamAccount()
        assertTrue(store.addOutgoing(claim.number.display, claim.reply(), author, clock.instant()))
        store.change(claim.number.display) { _, case ->
            case.copy(assignedTo = author, extendedAt = clock.instant(), extensionReason = "[EXEMPLE] Demande complexe", status = CaseStatus.CLOSED, closedAt = clock.instant()) to Unit
        }
        val closedAt = clock.instant()
        advance(years(5) - 1.days)
        assertEquals(0, space.retentionOnce().anonymized)
        advance(2.days)
        assertEquals(1, space.retentionOnce().anonymized)
        val anonymised = assertNotNull(case(claim))
        assertEquals(clock.instant(), anonymised.anonymizedAt)
        assertNull(anonymised.accessHash)
        assertNull(anonymised.casePubkey)
        assertNull(anonymised.assignedTo)
        assertNull(anonymised.extensionReason)
        assertEquals(0 to 0, store.envelopeCounts(claim.number.display))
        // What the statistics need stays: nature, dates, durations.
        assertEquals(CaseNature.Claim(ClaimMotif.DATA_RIGHTS), anonymised.nature)
        assertEquals(closedAt, anonymised.closedAt)
        assertNotNull(anonymised.extendedAt)
        assertNotNull(anonymised.deadlineAt)
        // Its number and code no longer open anything.
        assertIs<ReadOutcome.Refused>(space.state(claim.number.display, claim.secrets.authorization, null))
        advance(years(20))
        assertEquals(0, space.retentionOnce().total)
        assertNotNull(case(claim))
    }

    @Test
    fun nothingIsDeletedNorAnonymisedUnderAnActiveLegalHold() {
        val ticket = open(CaseNature.Ticket(TicketSubtype.REPORT))
        val claim = open(CaseNature.Claim(ClaimMotif.FUNDS))
        listOf(ticket, claim).forEach(::close)
        advance(years(5) - 10.days)
        listOf(ticket, claim).forEach { hold(it, clock.instant().plusSeconds(300.days.inWholeSeconds)) }
        advance(20.days)
        val report = space.retentionOnce()
        assertEquals(0, report.deleted)
        assertEquals(0, report.anonymized)
        assertEquals(1 to 0, store.envelopeCounts(ticket.number.display))
        assertNotNull(case(claim)!!.accessHash)
        // Past the review date and 30 more days without renewal, the ordinary durations resume.
        advance(280.days)
        assertEquals(2, space.retentionOnce().holdsDue)
        assertNotNull(case(ticket))
        advance(31.days)
        val lifted = space.retentionOnce()
        assertEquals(2, lifted.holdsLifted)
        assertEquals(1, lifted.deleted)
        assertEquals(1, lifted.anonymized)
        assertNull(case(ticket))
        assertNotNull(case(claim)!!.anonymizedAt)
    }

    @Test
    fun aHoldReachingItsReviewDateComesBackWithOneAlert() {
        val claim = open(CaseNature.Claim(ClaimMotif.TEAM_MEMBER))
        close(claim)
        hold(claim, clock.instant().plusSeconds(10.days.inWholeSeconds))
        val alerts = mutableListOf<Int>()
        space.onHoldsDue = { alerts += it }
        val viewer = UUID.randomUUID()
        fun inInbox() = store.inbox(InboxQuery(restricted = true, viewer = viewer)).any { it.number == claim.number.display }
        // Closed and held: not in the inbox to handle.
        assertEquals(false, inInbox())
        advance(10.days)
        assertEquals(1, space.retentionOnce().holdsDue)
        assertEquals(listOf(1), alerts)
        assertNotNull(case(claim)!!.legalHoldAlertedAt)
        // Back in the inbox, signalled once.
        assertTrue(inInbox())
        assertEquals(0, space.retentionOnce().holdsDue)
        assertEquals(listOf(1), alerts)
        advance(30.days)
        assertEquals(0, space.retentionOnce().holdsLifted)
        advance(1.days)
        assertEquals(1, space.retentionOnce().holdsLifted)
        val lifted = assertNotNull(case(claim))
        assertNull(lifted.legalHoldReason)
        assertNull(lifted.legalHoldReviewAt)
        assertNull(lifted.legalHoldAlertedAt)
        assertEquals(false, inInbox())
    }

    @Test
    fun aHoldRequestWithoutSecondPersonExpiresAfterSevenDays() {
        val claim = open(CaseNature.Claim(ClaimMotif.OTHER))
        val requester = teamAccount()
        TestDatabase.dataSource.connection.use { connection ->
            assertTrue(store.insertHoldRequest(connection, HoldRequest(claim.number.display, false, "[EXEMPLE] Motif", clock.instant().plusSeconds(100.days.inWholeSeconds), requester, clock.instant())))
        }
        advance(7.days)
        assertEquals(0, space.retentionOnce().requestsExpired)
        advance(1.days)
        assertEquals(1, space.retentionOnce().requestsExpired)
        assertNull(store.holdRequest(claim.number.display))
    }
}
