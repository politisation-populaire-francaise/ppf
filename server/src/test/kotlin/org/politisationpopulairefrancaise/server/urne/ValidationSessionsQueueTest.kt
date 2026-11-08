package org.politisationpopulairefrancaise.server.urne

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.backoffice.ActionOutcome
import org.politisationpopulairefrancaise.server.backoffice.BackOfficeTestData
import org.politisationpopulairefrancaise.server.backoffice.MutableClock
import org.politisationpopulairefrancaise.server.backoffice.Outcome
import org.politisationpopulairefrancaise.server.backoffice.TeamActor
import org.politisationpopulairefrancaise.server.backoffice.TeamRole
import org.politisationpopulairefrancaise.server.backoffice.ValidationSession
import org.politisationpopulairefrancaise.server.backoffice.ValidationSessionState
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The queue decider.validation_sessions: the sessions with an abnormal pace, until a person reviews them. */
class ValidationSessionsQueueTest {
    private val clock = MutableClock(Instant.parse("2026-11-04T09:00:00Z"))
    private val organisation = IdentityKeys.generate().publicKey.hex
    private val backOffice = BackOfficeTestData.backOffice(clock, verified = setOf(organisation))
    private val queue = ValidationSessionsQueue(TestDatabase.dataSource, clock)
    private val admin = TeamActor.of(UUID.randomUUID(), "Admin", setOf(TeamRole.ADMIN), 2)
    private var volunteers = 0

    @BeforeTest
    fun emptyTables() {
        BackOfficeTestData.reset()
        UrneFixtures.reset()
    }

    private fun <T> Outcome<T>.ok(): T = when (this) {
        is Outcome.Ok -> value
        is Outcome.Denied -> error("Refused: $code")
    }

    private fun session(max: Int = 40): ValidationSession {
        val volunteer = BackOfficeTestData.member(backOffice, "Bénévole ${++volunteers}", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        return backOffice.openValidationSession(BackOfficeTestData.auth(backOffice, volunteer), IdentityKeys.generate().publicKey.hex, max, 4.hours).ok()
    }

    private fun validations(session: ValidationSession, count: Int) = repeat(count) { assertTrue(backOffice.validationGate.recordValidation(session)) }

    @Test
    fun aSessionWithAnAbnormalPaceWaitsForAReview() {
        val calm = session()
        validations(calm, 5)
        val fast = session()
        // 25 in half an hour: counted over one hour at least, still 25 an hour.
        validations(fast, 25)
        val full = session(max = 3)
        validations(full, 3)
        clock.advance(30.minutes)
        assertEquals(setOf(fast.id.toString(), full.id.toString()), queue.page(null, 10).items.map { it.id }.toSet())
        assertEquals(2, queue.pendingCount())
        val item = queue.page(null, 10).items.single { it.id == fast.id.toString() }
        assertEquals("Rythme rapide", item.summary)
        val page = createHTML().div { queue.render(fast.id.toString(), this) }
        assertTrue(page.contains("25 sur 40") && page.contains("clé « audit »"), page)
        // Revoked at once, then reviewed: it leaves the queue.
        assertEquals(listOf(ValidationSessionsQueue.REVOKE, ValidationSessionsQueue.NORMAL, ValidationSessionsQueue.TO_UNDO), queue.actions(fast.id.toString()).map { it.id })
        assertIs<ActionOutcome.Refused>(queue.perform(fast.id.toString(), ValidationSessionsQueue.REVOKE, null, admin))
        assertEquals(ActionOutcome.Done, queue.perform(fast.id.toString(), ValidationSessionsQueue.REVOKE, "Rythme impossible", admin))
        assertEquals(ValidationSessionState.REVOKED, backOffice.validationSession(fast.id)!!.state)
        assertEquals(ActionOutcome.Done, queue.perform(fast.id.toString(), ValidationSessionsQueue.TO_UNDO, "Validations à annuler", admin))
        assertEquals(ActionOutcome.AlreadyDecided, queue.perform(fast.id.toString(), ValidationSessionsQueue.NORMAL, "Encore", admin))
        assertTrue(queue.actions(fast.id.toString()).isEmpty())
        assertEquals(listOf(full.id.toString()), queue.page(null, 10).items.map { it.id })
        assertEquals(ActionOutcome.NotFound, queue.perform(UUID.randomUUID().toString(), ValidationSessionsQueue.NORMAL, "x", admin))
    }

    @Test
    fun manySessionsOfOneValidatorTheSameWeekAreShown() {
        val volunteer = BackOfficeTestData.member(backOffice, "Bénévole pressé", listOf(TeamRole.VALIDATOR), keys = 1, organisation = organisation)
        val opened = List(4) {
            val auth = BackOfficeTestData.auth(backOffice, volunteer)
            backOffice.openValidationSession(auth, IdentityKeys.generate().publicKey.hex, 40, 1.hours).ok().also { backOffice.closeValidationSession(auth, it.id).ok() }
        }
        assertEquals(opened.map { it.id.toString() }.toSet(), queue.page(null, 10).items.map { it.id }.toSet())
        assertTrue(queue.page(null, 10).items.all { it.summary == "Nombreuses séances du même validateur cette semaine" })
    }
}
