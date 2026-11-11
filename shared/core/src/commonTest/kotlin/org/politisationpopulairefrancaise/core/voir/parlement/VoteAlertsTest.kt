package org.politisationpopulairefrancaise.core.voir.parlement

import org.politisationpopulairefrancaise.core.elus.ElectedOfficial
import org.politisationpopulairefrancaise.core.elus.Mandate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Mes élus ont voté » and the followed words (VO-34): matching and new ballots, on the device only. */
class VoteAlertsTest {
    private fun member(id: String, name: String, chamber: Chamber, territory: String) = Member(id, name, chamber, territory = territory, url = "https://example.org/$id")

    private val deputy = member("an:PA1", "[EXEMPLE] Camille Fictive", Chamber.AN, "6901")
    private val neighbour = member("an:PA2", "[EXEMPLE] Dominique Exemple", Chamber.AN, "6902")
    private val senatorA = member("senat:1A", "[EXEMPLE] Alex Modèle", Chamber.SENAT, "69")
    private val senatorB = member("senat:2B", "[EXEMPLE] Sacha Témoin", Chamber.SENAT, "69")
    private val european = member("pe:1", "[EXEMPLE] Lou Européenne", Chamber.PE, "FR")
    private val members = listOf(deputy, neighbour, senatorA, senatorB, european)

    private fun official(mandate: Mandate, territory: String, name: String) = ElectedOfficial(mandate, territory, null, name, null, null, null)

    private fun ballot(id: String, date: String, title: String, positions: Positions) =
        Ballot(id, id.substringAfterLast(':').toIntOrNull() ?: 1, date, title, counts = Counts(inFavour = 1, against = 1, abstention = 0), positions = positions)

    private val catalog = ParliamentCatalog(
        MembersPackage(example = true, members = members, groups = emptyList()),
        listOf(
            BallotsPackage(
                example = true, chamber = Chamber.AN, month = "2026-11",
                ballots = listOf(
                    ballot("an:17:10", "2026-11-03", "[EXEMPLE] Projet de loi relatif aux retraites", Positions(inFavour = listOf("an:PA1"), against = listOf("an:PA2"), delegated = listOf("an:PA1"))),
                    ballot("an:17:11", "2026-11-04", "[EXEMPLE] Motion de censure", Positions(against = listOf("an:PA1"), corrections = listOf(VoteCorrection("an:PA1", Position.FOR)))),
                    ballot("an:17:12", "2026-11-05", "[EXEMPLE] Amendement sur le logement", Positions(inFavour = listOf("an:PA2"))),
                ),
            ),
            BallotsPackage(
                example = true, chamber = Chamber.SENAT, month = "2026-11",
                ballots = listOf(ballot("senat:2026:5", "2026-11-06", "[EXEMPLE] Retraite progressive", Positions(abstention = listOf("senat:1A")))),
            ),
        ),
    )

    @Test
    fun deputiesAreMatchedByConstituencyAndSenatorsByDepartmentAndName() {
        val mine = MyParliamentarians.of(
            deputies = listOf(official(Mandate.DEPUTE, "6901", "[EXEMPLE] FICTIVE Camille")),
            senators = listOf(official(Mandate.SENATEUR, "69", "[EXEMPLE] MODELE Alex"), official(Mandate.SENATEUR, "69", "[EXEMPLE] Inconnu Quelqu’un")),
            members = members,
        )
        assertEquals(deputy, mine.deputies.single().member)
        assertEquals(senatorA, mine.senators[0].member, "the same name, accents, case and order ignored")
        assertNull(mine.senators[1].member, "never guessed: shown without matched votes")
        assertTrue(mine.europeans.isEmpty(), "no member of the European Parliament followed by default")
        assertEquals(listOf(deputy, senatorA), mine.members)
        assertEquals(setOf(Chamber.AN, Chamber.SENAT), mine.chambers)
        assertFalse(mine.sharedCommune)

        val shared = MyParliamentarians.of(listOf(official(Mandate.DEPUTE, "6901", "x"), official(Mandate.DEPUTE, "6902", "y")), emptyList(), members, europeans = setOf("pe:1"))
        assertTrue(shared.sharedCommune, "a commune split between two constituencies")
        assertEquals(listOf(deputy, neighbour, european), shared.members)
        assertFalse(MyParliamentarians.sameName("Camille Fictive", "Camille Fictif"))
    }

    @Test
    fun theNewBallotsGiveThePositionTheDelegationAndTheCorrectionOfMyParliamentarians() {
        val alerts = VoteAlerts.newVotes(listOf(deputy, senatorA), catalog, seen = emptySet())
        assertEquals(listOf("senat:2026:5", "an:17:11", "an:17:10"), alerts.map { it.entry.ballot.id }, "most recent first; the ballot without them is left out")
        val censure = alerts.first { it.entry.ballot.id == "an:17:11" }.votes.single()
        assertEquals(Position.AGAINST, censure.vote.position, "the recorded vote, never replaced")
        assertEquals(Position.FOR, censure.vote.correction?.intended, "the correction beside it")
        assertTrue(alerts.first { it.entry.ballot.id == "an:17:10" }.votes.single().vote.delegated)
        assertEquals(Position.ABSTENTION, alerts.first().votes.single().vote.position)
        // Already seen: nothing new.
        assertTrue(VoteAlerts.newVotes(listOf(deputy, senatorA), catalog, VoteAlerts.allBallotIds(catalog)).isEmpty())
        assertTrue(VoteAlerts.newVotes(emptyList(), catalog, emptySet()).isEmpty())
    }

    @Test
    fun aFollowedWordFindsTheTitlesWithoutAccentsNorCaseOnTheDevice() {
        val found = VoteAlerts.newWordMatches(listOf("  RETRAITE ", "logement social", "", "retraite"), catalog, seen = setOf("an:17:10"))
        assertEquals(listOf("senat:2026:5" to "RETRAITE"), found.map { it.entry.ballot.id to it.word }, "« retraite » finds « Retraite progressive »; « logement social » needs both words")
        assertTrue(VoteAlerts.newWordMatches(List(80) { "mot$it" }, catalog, emptySet()).isEmpty())
    }
}
