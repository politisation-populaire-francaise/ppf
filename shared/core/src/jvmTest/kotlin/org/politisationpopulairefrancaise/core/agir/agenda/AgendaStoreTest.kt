package org.politisationpopulairefrancaise.core.agir.agenda

import org.politisationpopulairefrancaise.core.local.JvmDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaStoreTest {
    private var now = 1_000L

    private fun bothStates(database: org.politisationpopulairefrancaise.core.db.PpfDatabase = JvmDatabase.inMemory()): List<AgendaState> =
        listOf(AgendaStore(database, clock = { now }), InMemoryAgendaState())

    @Test
    fun answersAreRecordedChangedAndRemoved() {
        for (state in bothStates()) {
            assertEquals(emptyMap(), state.responses())
            state.respond("31923:a:un", AgendaResponse.GOING, endsAt = 5_000)
            state.respond("31923:a:deux", AgendaResponse.INTERESTED, endsAt = 9_000)
            assertEquals(mapOf("31923:a:un" to AgendaResponse.GOING, "31923:a:deux" to AgendaResponse.INTERESTED), state.responses())
            state.respond("31923:a:un", AgendaResponse.INTERESTED, endsAt = 5_000)
            assertEquals(AgendaResponse.INTERESTED, state.responses()["31923:a:un"])
            state.respond("31923:a:un", null, endsAt = 5_000)
            assertEquals(setOf("31923:a:deux"), state.responses().keys)
            state.clearResponses()
            assertEquals(emptyMap(), state.responses())
        }
    }

    @Test
    fun answersOfEventsOverAreErased() {
        for (state in bothStates()) {
            state.respond("fini", AgendaResponse.GOING, endsAt = 5_000)
            state.respond("bientot", AgendaResponse.GOING, endsAt = 9_000)
            state.respond("reporte", AgendaResponse.GOING, endsAt = 4_000)
            // A newer version moved the postponed event later.
            state.updateEnd("reporte", 20_000)
            state.purgeResponses(before = 6_000)
            assertEquals(setOf("bientot", "reporte"), state.responses().keys)
        }
    }

    @Test
    fun theReviewOfOwnProposalsIsKept() {
        for (state in bothStates()) {
            state.recordProposal("e1", ProposalRecord(ProposalReview.REVIEW))
            state.recordProposal("e2", ProposalRecord(ProposalReview.REJECTED, "[EXEMPLE] Doublon d'un événement existant."))
            state.recordProposal("e1", ProposalRecord(ProposalReview.PUBLISHED))
            assertEquals(
                mapOf("e1" to ProposalRecord(ProposalReview.PUBLISHED), "e2" to ProposalRecord(ProposalReview.REJECTED, "[EXEMPLE] Doublon d'un événement existant.")),
                state.proposals(),
            )
            state.forgetProposal("e2")
            assertEquals(setOf("e1"), state.proposals().keys)
        }
    }

    @Test
    fun answersSurviveTheReopeningOfTheDatabase() {
        val file = Files.createTempFile("ppf-agenda-", ".db")
        Files.delete(file)
        file.toFile().deleteOnExit()
        AgendaStore(JvmDatabase.open(file)).respond("31923:a:un", AgendaResponse.GOING, endsAt = 5_000)
        val reopened = AgendaStore(JvmDatabase.open(file))
        assertEquals(mapOf("31923:a:un" to AgendaResponse.GOING), reopened.responses())
        assertEquals(1L, reopened.countResponses())
        assertEquals(0L, reopened.countProposals())
    }
}
