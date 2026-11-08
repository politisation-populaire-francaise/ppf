package org.politisationpopulairefrancaise.core.decider

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneResults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The citizen deliberation (DE-25): its formats and the mechanical rules that choose wordings and measures. */
class DeliberationTest {
    private val team = LocalEventSigner(IdentityKeys.generate())
    private val start = 1_796_000_000L
    private val week = 7 * 86_400L

    private fun phases(propositions: Long = 3 * week, amendments: Long = 2 * week, vote: Long = 2 * week) = listOf(
        listOf("ppf-phase", "propositions", "$start", "${start + propositions}"),
        listOf("ppf-phase", "amendements", "${start + propositions}", "${start + propositions + amendments}"),
        listOf("ppf-phase", "vote", "${start + propositions + amendments}", "${start + propositions + amendments + vote}"),
    )

    private suspend fun deliberation(tags: List<List<String>> = phases(), measures: List<String> = listOf("ppf-measures", "5", "10")): SignedEvent =
        team.sign(
            kind = EventKinds.LONG_FORM_ARTICLE,
            tags = listOf(listOf("d", "pacte-2027"), listOf("ppf-type", "deliberation"), listOf("title", "[EXEMPLE] Pacte démocratique 2027"), measures) + tags,
            content = "[EXEMPLE] Délibération sur les mesures du Pacte.",
            createdAt = start - 86_400,
        )

    private suspend fun proposition(author: LocalEventSigner, d: String, deliberation: Deliberation, at: Long, start: Boolean = false): Proposition = Proposition.parse(
        author.sign(
            kind = EventKinds.LONG_FORM_ARTICLE,
            tags = listOfNotNull(
                listOf("d", d), listOf("ppf-type", "proposition"), listOf("a", deliberation.address.toString()), listOf("title", "[EXEMPLE] $d"),
                listOf("ppf-justification", "[EXEMPLE] Pourquoi"), if (start) listOf("ppf-origin", "depart") else null,
            ),
            content = "[EXEMPLE] Texte de $d",
            createdAt = at,
        ),
    )!!

    private suspend fun amendment(author: LocalEventSigner, d: String, proposition: Proposition, at: Long): Amendment = Amendment.parse(
        author.sign(
            kind = EventKinds.LONG_FORM_ARTICLE,
            tags = listOf(listOf("d", d), listOf("ppf-type", "amendement"), listOf("a", proposition.address.toString())),
            content = "[EXEMPLE] Nouvelle rédaction $d",
            createdAt = at,
        ),
    )!!

    private fun results(pour: Long, contre: Long, blanc: Long = 0, hidden: Boolean = false): UrneResults {
        val citizens = if (hidden) ChoiceCounts(emptyMap(), 0, hidden = true) else ChoiceCounts(mapOf("pour" to pour, "contre" to contre, "blanc" to blanc), pour + contre + blanc)
        val none = ChoiceCounts(emptyMap(), 0)
        // The elected officials' column is apart and never counts here.
        return UrneResults(none, none, citizens, ChoiceCounts(mapOf("pour" to 1_000), 1_000))
    }

    @Test
    fun aDeliberationIsDatedTheSameForEveryone() = runTest {
        val deliberation = assertNotNull(Deliberation.parse(deliberation()))
        assertEquals("[EXEMPLE] Pacte démocratique 2027", deliberation.title)
        assertEquals(5 to 10, deliberation.minMeasures to deliberation.maxMeasures)
        assertNull(deliberation.phaseAt(start - 1))
        assertEquals(DeliberationPhase.PROPOSITIONS, deliberation.phaseAt(start))
        assertEquals(DeliberationPhase.AMENDMENTS, deliberation.phaseAt(start + 3 * week))
        assertEquals(DeliberationPhase.VOTE, deliberation.phaseAt(start + 5 * week))
        assertTrue(deliberation.isOver(start + 7 * week))
        assertNull(deliberation.phaseAt(start + 7 * week))
        // A missing phase, phases out of order or overlapping, or a measure count beyond 10: refused.
        assertNull(Deliberation.parse(deliberation(tags = phases().take(2))))
        assertNull(Deliberation.parse(deliberation(tags = phases().reversed())))
        val overlapping = phases().toMutableList().also { it[1] = listOf("ppf-phase", "amendements", "${start + week}", "${start + 5 * week}") }
        assertNull(Deliberation.parse(deliberation(tags = overlapping)))
        assertNull(Deliberation.parse(deliberation(measures = listOf("ppf-measures", "5", "11"))))
    }

    @Test
    fun oneAccountProposesOnceAndThreeAmendmentsAtMost() = runTest {
        val deliberation = Deliberation.parse(deliberation())!!
        val alice = LocalEventSigner(IdentityKeys.generate())
        val bob = LocalEventSigner(IdentityKeys.generate())
        val departure = proposition(team, "depart-1", deliberation, start - 10, start = true)
        val first = proposition(alice, "alice-1", deliberation, start + 10)
        val second = proposition(alice, "alice-2", deliberation, start + 20)
        val third = proposition(bob, "bob-1", deliberation, start + 30)
        assertEquals(listOf(departure, first, third), DeliberationRules.admissiblePropositions(deliberation, listOf(third, second, first, departure)))
        val amendments = (1..5).map { amendment(alice, "a$it", first, start + 3 * week + it) } + amendment(bob, "b1", first, start + 3 * week + 100)
        assertEquals(listOf("a1", "a2", "a3", "b1"), DeliberationRules.votedAmendments(first, amendments).map { it.address.identifier })
    }

    @Test
    fun beyondSixtyPropositionsTheMostSupportedGoOnAndTheStartingOnesAlways() = runTest {
        val deliberation = Deliberation.parse(deliberation())!!
        val departure = proposition(team, "depart-1", deliberation, start - 10, start = true)
        val citizens = (1..70).map { proposition(LocalEventSigner(IdentityKeys.generate()), "p$it", deliberation, start + it) }
        // The supports grow with the number: the 59 last go on, with the starting proposal.
        val voted = DeliberationRules.votedPropositions(citizens + departure) { it.address.identifier.removePrefix("p").toLongOrNull() }
        assertEquals(60, voted.size)
        assertEquals(departure, voted.first())
        assertEquals((12..70).map { "p$it" }, voted.drop(1).map { it.address.identifier })
    }

    @Test
    fun theCitizensVotesChooseTheWordingAndTheMeasures() = runTest {
        val deliberation = Deliberation.parse(deliberation())!!
        val alice = LocalEventSigner(IdentityKeys.generate())
        val proposition = proposition(alice, "p1", deliberation, start + 10)
        val older = amendment(alice, "older", proposition, start + 3 * week + 1)
        val newer = amendment(LocalEventSigner(IdentityKeys.generate()), "newer", proposition, start + 3 * week + 2)
        val rejected = amendment(LocalEventSigner(IdentityKeys.generate()), "rejected", proposition, start + 3 * week + 3)
        // Rejected (more contre), or still hidden: never retained; the elected officials' column never counts.
        assertNull(DeliberationRules.retainedAmendment(listOf(rejected to results(10, 20), newer to results(0, 0, hidden = true))))
        assertEquals(proposition.text, DeliberationRules.finalText(proposition, null))
        // Several win: the most pour, then the fewest contre, then the oldest.
        assertEquals(newer, DeliberationRules.retainedAmendment(listOf(older to results(50, 10), newer to results(60, 40), rejected to results(5, 50))))
        assertEquals(older, DeliberationRules.retainedAmendment(listOf(older to results(60, 10), newer to results(60, 40))))
        assertEquals(older, DeliberationRules.retainedAmendment(listOf(newer to results(60, 10), older to results(60, 10))))
        assertEquals(older.text, DeliberationRules.finalText(proposition, older))
        // The measures kept: more pour than contre, the most supported first, at most the deliberation's maximum.
        val narrow = Deliberation.parse(deliberation(measures = listOf("ppf-measures", "2", "3")))!!
        val measures = listOf("m1" to results(100, 10), "m2" to results(300, 10), "m3" to results(5, 50), "m4" to results(200, 10), "m5" to results(150, 10))
        val kept = DeliberationRules.kept(narrow, measures.map { MeasureSelection.Candidate(it.first, it.second) })
        assertEquals(listOf("m2", "m4", "m5"), kept.measures)
        assertEquals(false, kept.tooFew)
        assertEquals(true, DeliberationRules.kept(narrow, measures.take(1).map { MeasureSelection.Candidate(it.first, it.second) }).tooFew)
    }
}
