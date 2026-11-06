package org.politisationpopulairefrancaise.server.revendications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.revendications.checks.DemandText
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalChecker
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReport
import org.politisationpopulairefrancaise.core.revendications.checks.ProposalReviewModel
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewOutcome
import org.politisationpopulairefrancaise.core.revendications.checks.ReviewRequest
import org.politisationpopulairefrancaise.server.relay.RateLimiter
import org.slf4j.Logger
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Runs the automatic checks of the proposals ([ProposalChecker.checkOnServer]: rules, duplicates
 * among every published demand and the other pending proposals, then the AI model when one is
 * configured) in the background, one proposal at a time, so that the author gets the answer to
 * POST /api/v1/events at once and the server never runs more than one model request.
 *
 * Proposals wait in a bounded channel; when it is full, or after a restart, the periodic task
 * ([ProposalMaintenance]) schedules again the proposals left without a report. Failures are logged
 * by exception class only, never with a content, a key or an id.
 */
class ProposalReviewer(
    private val queue: ProposalQueue,
    private val checker: ProposalChecker,
    private val log: Logger,
) {
    private val waiting = Channel<String>(CAPACITY)
    private val scheduled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Asks for the checks of proposal [id]; does nothing if they are already scheduled or the channel is full. */
    fun schedule(id: String) {
        if (scheduled.add(id) && waiting.trySend(id).isFailure) scheduled.remove(id)
    }

    /** Checks the scheduled proposals until [scope] is cancelled (the application's own scope). */
    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        for (id in waiting) {
            try {
                check(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Automatic checks of a proposed demand failed (${e::class.simpleName}); the next periodic run tries again.")
            } finally {
                scheduled.remove(id)
            }
        }
    }

    /** Runs the checks of pending proposal [id] now and stores the report; null when it is no longer pending. */
    suspend fun check(id: String): ProposalReport? {
        val proposal = queue.find(id)?.takeIf { it.status == ProposalStatus.PENDING } ?: return null
        val article = Article.from(proposal.event) ?: return null
        val published = queue.publishedDemands().mapNotNull(Article::from).map(DemandText::of)
        val others = queue.pendingDemands(excluding = id).mapNotNull(Article::from).map(DemandText::of)
        // A published demand and the pending new version of it share an address: the published one is kept.
        val existing = (published + others).distinctBy { it.address }
        val report = checker.checkOnServer(DemandDrafts.draftOf(article), existing, ownAddress = article.address.toString())
        queue.saveReport(id, report)
        return report
    }

    private companion object {
        const val CAPACITY = 256
    }
}

/**
 * A ceiling on the requests sent to the AI model, all proposals together: with a paid service, a
 * flood of proposals from freshly minted keys must not turn into a bill. Over the ceiling the AI
 * check is reported failed and the team reads the proposal without it.
 */
class CappedReviewModel(private val model: ProposalReviewModel, private val budget: PeriodBudget) : ProposalReviewModel, AutoCloseable {
    override suspend fun review(request: ReviewRequest): ReviewOutcome =
        when (budget.tryAcquire(PeriodBudget.GLOBAL_KEY)) {
            is RateLimiter.Outcome.Limited -> ReviewOutcome.Failed("daily_ceiling")
            RateLimiter.Outcome.Allowed -> model.review(request)
        }

    override fun close() {
        (model as? AutoCloseable)?.close()
    }
}

/**
 * The periodic task of the queue: deletes the proposals decided more than [retention] ago (an
 * approved demand lives on in the events table) and schedules again the checks of the pending
 * proposals left without a report (after a restart, or when the channel was full).
 */
class ProposalMaintenance(
    private val queue: ProposalQueue,
    private val reviewer: ProposalReviewer?,
    private val retention: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Deletes the expired decided proposals once and returns how many were removed. */
    fun purgeOnce(): Int = queue.deleteDecidedBefore(clock.instant().minus(retention.toJavaDuration()))

    /** Schedules the checks of the pending proposals without a report; returns how many were scheduled. */
    fun catchUp(): Int {
        val reviewer = reviewer ?: return 0
        return queue.uncheckedPending(CATCH_UP_BATCH).onEach(reviewer::schedule).size
    }

    /** Runs both now and then every [interval], until [scope] is cancelled. Only counts are logged. */
    fun start(scope: CoroutineScope, interval: Duration, log: Logger): Job = scope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val deleted = purgeOnce()
                if (deleted > 0) log.info("Demand proposals retention: $deleted decided proposal(s) deleted.")
                catchUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Demand proposals maintenance failed (${e::class.simpleName}); next attempt in $interval.")
            }
            delay(interval)
        }
    }

    private companion object {
        const val CATCH_UP_BATCH = 100
    }
}
