package org.politisationpopulairefrancaise.server.revendications

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewOutcome
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewRequest
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class PeriodBudgetTest {
    private var now = 0L
    private val budget = PeriodBudget(permits = 5, period = 24.hours, maxTrackedKeys = 2, nanoTime = { now })

    private fun advanceSeconds(seconds: Long) {
        now += seconds * 1_000_000_000L
    }

    @Test
    fun fivePerDayThenOneEveryFourHoursFortyEight() {
        repeat(5) { assertEquals(RateLimiter.Outcome.Allowed, budget.tryAcquire("a")) }
        val limited = assertIs<RateLimiter.Outcome.Limited>(budget.tryAcquire("a"))
        assertTrue(limited.retryAfterSeconds in 17_280..17_281, "24 h / 5 = 4 h 48 min, got ${limited.retryAfterSeconds}")
        assertEquals(RateLimiter.Outcome.Allowed, budget.tryAcquire("b"), "another key has its own budget")

        advanceSeconds(17_270)
        assertIs<RateLimiter.Outcome.Limited>(budget.tryAcquire("a"))
        advanceSeconds(20)
        assertEquals(RateLimiter.Outcome.Allowed, budget.tryAcquire("a"))
        assertIs<RateLimiter.Outcome.Limited>(budget.tryAcquire("a"))

        budget.refund("a")
        assertEquals(RateLimiter.Outcome.Allowed, budget.tryAcquire("a"), "a refunded token can be spent again")
    }

    @Test
    fun forgetsFullBucketsBeyondTheTrackedKeys() {
        budget.tryAcquire("a")
        advanceSeconds(24 * 3_600)
        budget.tryAcquire("b")
        assertEquals(2, budget.trackedKeys)
        budget.tryAcquire("c")
        // "a" was full again: it has been forgotten, and starts afresh with its whole budget.
        assertEquals(2, budget.trackedKeys)
        repeat(5) { assertEquals(RateLimiter.Outcome.Allowed, budget.tryAcquire("a")) }
    }

    @Test
    fun refusesNonsense() {
        assertFailsWith<IllegalArgumentException> { PeriodBudget(permits = 0) }
        assertFailsWith<IllegalArgumentException> { PeriodBudget(permits = 1, period = (-1).hours) }
    }

    @Test
    fun theModelIsNotAskedBeyondItsDailyCeiling() = runBlocking {
        var asked = 0
        val model = ProposalReviewModel {
            asked++
            ReviewOutcome.Reviewed(emptyList(), "modele-de-test")
        }
        val capped = CappedReviewModel(model, PeriodBudget(permits = 2, maxTrackedKeys = 1, nanoTime = { now }))
        val request = ReviewRequest(DemandFixtures.draft(), emptyList())
        assertIs<ReviewOutcome.Reviewed>(capped.review(request))
        assertIs<ReviewOutcome.Reviewed>(capped.review(request))
        assertEquals(ReviewOutcome.Failed("daily_ceiling"), capped.review(request))
        assertEquals(2, asked)
    }
}
