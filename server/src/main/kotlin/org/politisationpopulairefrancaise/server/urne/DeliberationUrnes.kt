package org.politisationpopulairefrancaise.server.urne

import org.politisationpopulairefrancaise.core.decider.Amendment
import org.politisationpopulairefrancaise.core.decider.Deliberation
import org.politisationpopulairefrancaise.core.decider.DeliberationFormats
import org.politisationpopulairefrancaise.core.decider.DeliberationPhase
import org.politisationpopulairefrancaise.core.decider.DeliberationRules
import org.politisationpopulairefrancaise.core.decider.Proposition
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.urne.UrneResults
import org.politisationpopulairefrancaise.server.events.EventFilter
import org.politisationpopulairefrancaise.server.events.EventRepository
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The ballot boxes of the citizen deliberations (DE-25, docs/integration/deliberation.md, section 6), opened
 * by the task of the boxes at every pass, idempotently, as the phases published by the team come:
 * - phase of propositions: a box of support (`soutien`) per admissible proposition, closed at the end of
 *   the phase; it only breaks ties beyond 60 propositions;
 * - phase of amendments: a box `pour`, `contre`, `blanc` per amendment put to the vote, of the propositions
 *   that go on (those with a box of support), closed at the end of the phase;
 * - final vote: a box per proposition that goes on, on the wording the citizens' votes retained (the winning
 *   amendment, else the proposition), closed at the end of the vote.
 * A box opens when its phase is reached, never in advance; the end is known at the opening and never moved;
 * results are shown after the closing (questions, D-113). Only the deliberations of the editorial keys count.
 * Nothing about persons: propositions and amendments are public contents signed by pseudonyms.
 */
class DeliberationUrnes(
    private val events: EventRepository,
    private val urnes: UrneService,
    private val isEditor: (String) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The results of closed boxes never change: computed once. */
    private val closedResults = ConcurrentHashMap<String, UrneResults>()

    /** Opens the boxes due; returns how many were opened. */
    fun reconcile(): Int {
        val now = clock.instant().epochSecond
        var opened = 0
        for (deliberation in deliberations()) {
            val phase = deliberation.phaseAt(now) ?: continue
            val end = Instant.ofEpochSecond(deliberation.dates(phase).end)
            val propositions = DeliberationRules.admissiblePropositions(deliberation, propositionsOf(deliberation))
            when (phase) {
                DeliberationPhase.PROPOSITIONS -> propositions.forEach { proposition ->
                    opened += open(DeliberationRules.supportUrneId(proposition.address), proposition.address.toString(), SUPPORT_CHOICES, end)
                }
                DeliberationPhase.AMENDMENTS -> {
                    val voted = goingOn(propositions)
                    val amendments = amendmentsOf(voted)
                    voted.forEach { proposition ->
                        DeliberationRules.votedAmendments(proposition, amendments).forEach { amendment ->
                            opened += open(DeliberationRules.amendmentUrneId(amendment.address), amendment.address.toString(), CHOICES, end)
                        }
                    }
                }
                DeliberationPhase.VOTE -> {
                    val voted = goingOn(propositions)
                    val amendments = amendmentsOf(voted)
                    voted.forEach { proposition ->
                        // Only the amendments that had their box in the phase of amendments compete.
                        val competing = DeliberationRules.votedAmendments(proposition, amendments)
                            .mapNotNull { amendment -> urnes.byId(DeliberationRules.amendmentUrneId(amendment.address))?.let { amendment to resultsOf(it.id, it.number) } }
                        val retained = DeliberationRules.retainedAmendment(competing)
                        val subject = (retained?.address ?: proposition.address).toString()
                        opened += open(DeliberationRules.finalUrneId(proposition.address), subject, CHOICES, end)
                    }
                }
            }
        }
        return opened
    }

    /** The propositions with a box of support (opened in the phase of propositions) that go on to the vote. */
    private fun goingOn(propositions: List<Proposition>): List<Proposition> {
        val supported = propositions.mapNotNull { proposition -> urnes.byId(DeliberationRules.supportUrneId(proposition.address))?.let { proposition to it } }.toMap()
        return DeliberationRules.votedPropositions(supported.keys.toList()) { proposition ->
            supported[proposition]?.let { resultsOf(it.id, it.number) }?.takeIf { !it.citizens.hidden }?.citizens?.counts?.get(DeliberationFormats.SUPPORT)
        }
    }

    private fun resultsOf(id: String, number: Long): UrneResults? =
        closedResults[id] ?: urnes.finalResults(number)?.also { closedResults[id] = it }

    private fun open(id: String, subject: String, choices: List<String>, end: Instant): Int {
        if (urnes.byId(id) != null) return 0
        urnes.open(id, subject, choices, clock.instant(), closesAt = end, liveResults = false)
        return 1
    }

    private fun deliberations(): List<Deliberation> =
        events.query(EventFilter(kinds = listOf(EventKinds.LONG_FORM_ARTICLE), tags = mapOf(DeliberationFormats.TAG_TYPE to listOf(DeliberationFormats.TYPE_DELIBERATION)), limit = EventFilter.MAX_LIMIT))
            .filter { isEditor(it.pubkey) }
            .mapNotNull(Deliberation::parse)

    private fun propositionsOf(deliberation: Deliberation): List<Proposition> =
        events.query(
            EventFilter(
                kinds = listOf(EventKinds.LONG_FORM_ARTICLE),
                tags = mapOf(DeliberationFormats.TAG_TYPE to listOf(DeliberationFormats.TYPE_PROPOSITION), "a" to listOf(deliberation.address.toString())),
                limit = EventFilter.MAX_LIMIT,
            ),
        ).mapNotNull(Proposition::parse)
            // The starting proposal is the team's own: a citizen cannot pass one off as PPF's.
            .filter { !it.isStart || isEditor(it.event.pubkey) }

    private fun amendmentsOf(propositions: List<Proposition>): List<Amendment> =
        propositions.chunked(AMENDMENT_QUERY_CHUNK).flatMap { chunk ->
            events.query(
                EventFilter(
                    kinds = listOf(EventKinds.LONG_FORM_ARTICLE),
                    tags = mapOf(DeliberationFormats.TAG_TYPE to listOf(DeliberationFormats.TYPE_AMENDMENT), "a" to chunk.map { it.address.toString() }),
                    limit = EventFilter.MAX_LIMIT,
                ),
            ).mapNotNull(Amendment::parse)
        }

    companion object {
        val SUPPORT_CHOICES: List<String> = listOf(DeliberationFormats.SUPPORT)
        val CHOICES: List<String> = listOf("pour", "contre", "blanc")
        private const val AMENDMENT_QUERY_CHUNK = 10
    }
}
