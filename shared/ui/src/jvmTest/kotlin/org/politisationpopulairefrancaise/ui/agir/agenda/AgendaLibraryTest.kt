package org.politisationpopulairefrancaise.ui.agir.agenda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaTiming
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationDraft
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The agenda on the device: bundled examples, the server as gatekeeper of publication, the user's
 * own proposals and their review, and the private answers that never leave the device.
 */
class AgendaLibraryTest {
    private val other = AgendaKit()

    private fun CoroutineScope.library(device: AgendaDevice, clock: () -> Long = { AgendaClock.NOW }, trust: EditorialTrust = EditorialTrust.bundledExamples()) =
        AgendaLibrary(device.store, device.state, trust, device.server, this, clock = clock)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    private val validDraft = MobilizationDraft(
        type = MobilizationType.RASSEMBLEMENT,
        title = "[EXEMPLE] Rassemblement devant la maternité",
        startDate = "20/11/2026",
        startTime = "11h",
        endTime = "13h",
        location = "Devant la maternité, Villefictive",
        territory = "Villefictive",
    )

    @Test
    fun theBundledExamplesAreReadableOfflineWhenTheirKeyIsTrusted() = runBlocking {
        val device = AgendaDevice(withAccount = false)
        val agenda = library(device)
        agenda.start()
        awaitUntil { agenda.mobilizations.size == 8 && agenda.status == LibraryStatus.Offline }
        assertTrue(agenda.mobilizations.all { it.example && it.event.pubkey == EditorialTrust.EXAMPLE_EDITOR })
        assertEquals(setOf(MobilizationStatus.PREVU, MobilizationStatus.ANNULE, MobilizationStatus.REPORTE), agenda.mobilizations.map { it.status }.toSet())
        assertEquals(MobilizationType.entries.toSet(), agenda.mobilizations.map { it.type }.toSet(), "every type has an example")
        // One past, seven to come, on 13 November 2026.
        assertEquals(1, agenda.mobilizations.count { it.isPast(AgendaClock.NOW) })
        assertEquals(7, agenda.shown().size)
        agenda.tab = AgendaTab.PAST
        assertEquals(listOf("projection-debat-rennes-2026-11-03"), agenda.shown().map { it.identifier })
        coroutineContext.cancelChildren()
    }

    @Test
    fun examplesSignedByAnUntrustedKeyAreNotImported() = runBlocking {
        val device = AgendaDevice(withAccount = false)
        val agenda = library(device, trust = EditorialTrust(emptySet()))
        agenda.start()
        awaitUntil { agenda.status == LibraryStatus.Offline }
        assertEquals(emptyList(), agenda.mobilizations)
        assertEquals(0L, device.store.count())
        coroutineContext.cancelChildren()
    }

    @Test
    fun whatTheServerServesIsPublishedAndOwnProposalsWaitForTheirReview() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val someoneElse = other.mobilization("marche", "[EXEMPLE] Marche des familles", start = AgendaClock.at(2026, 11, 21, 15))
        server.seed(someoneElse)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.mobilizations.any { it.event.id == someoneElse.id } }

        // A proposal of the user: held by the server, so in "Mes propositions" and not on the agenda.
        agenda.composer.draft = validDraft
        agenda.composer.charterAccepted = true
        assertEquals(MobilizationSendOutcome.SENT_FOR_REVIEW, agenda.composer.send(device.signer))
        val sent = server.received.single()
        agenda.onSent()
        awaitUntil { agenda.mine.singleOrNull()?.state == OwnProposalState.IN_REVIEW }
        assertTrue(agenda.mobilizations.none { it.event.id == sent.id })

        // Rejected by the team, with its reason.
        server.decide(sent.id, HeldStatus.REJECTED, "[EXEMPLE] Doublon d'un événement existant.")
        agenda.refreshReviews()
        awaitUntil { agenda.mine.single().state == OwnProposalState.NOT_PUBLISHED }
        assertEquals("[EXEMPLE] Doublon d'un événement existant.", agenda.mine.single().reason)
        assertTrue(agenda.mobilizations.none { it.event.id == sent.id })
        // Only ids travelled to ask.
        assertTrue(server.heldQueries.flatten().all { it == sent.id })
        coroutineContext.cancelChildren()
    }

    @Test
    fun anApprovedProposalJoinsTheAgenda() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.status is LibraryStatus.Synced }
        agenda.composer.draft = validDraft
        agenda.composer.charterAccepted = true
        agenda.composer.send(device.signer)
        val sent = server.received.single()
        server.decide(sent.id, HeldStatus.APPROVED)
        agenda.refreshReviews()
        awaitUntil { agenda.mine.singleOrNull()?.state == OwnProposalState.PUBLISHED }
        awaitUntil { agenda.mobilizations.any { it.event.id == sent.id } }
        coroutineContext.cancelChildren()
    }

    @Test
    fun aVerifiedOrganisationPublishesAtOnce() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        server.verified += device.pubkey
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.status is LibraryStatus.Synced }
        agenda.composer.draft = validDraft
        agenda.composer.charterAccepted = true
        assertEquals(MobilizationSendOutcome.PUBLISHED, agenda.composer.send(device.signer))
        agenda.onSent()
        awaitUntil { agenda.mobilizations.any { it.displayTitle == "Rassemblement devant la maternité" } }
        assertEquals(OwnProposalState.PUBLISHED, agenda.mine.single().state)
        // The server does not hold it: asking again keeps it published.
        agenda.refreshReviews()
        delay(200)
        assertEquals(OwnProposalState.PUBLISHED, agenda.mine.single().state)
        coroutineContext.cancelChildren()
    }

    @Test
    fun withoutNetworkAProposalWaitsOnTheDeviceThenLeaves() = runBlocking {
        val server = AgendaServer().apply { online = false }
        val device = AgendaDevice(withAccount = true, server = server)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.status == LibraryStatus.Unreachable }
        agenda.composer.draft = validDraft
        agenda.composer.charterAccepted = true
        assertEquals(MobilizationSendOutcome.WAITING_FOR_SERVER, agenda.composer.send(device.signer))
        agenda.onSent()
        awaitUntil { agenda.mine.singleOrNull()?.state == OwnProposalState.WAITING }
        assertTrue(agenda.composer.draft.isBlank, "the draft is emptied once kept on the device")

        server.online = true
        agenda.refresh()
        awaitUntil { agenda.mine.single().state == OwnProposalState.IN_REVIEW }
        assertEquals(1, server.received.size)
        assertEquals(1, server.held.size)
        coroutineContext.cancelChildren()
    }

    @Test
    fun answersStayOnTheDeviceAndAreErasedAfterTheEvent() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val gathering = other.mobilization("rassemblement", "[EXEMPLE] Rassemblement", start = AgendaClock.at(2026, 11, 14, 14), end = AgendaClock.at(2026, 11, 14, 17))
        server.seed(gathering)
        var now = AgendaClock.NOW
        val agenda = library(device, clock = { now })
        agenda.start()
        awaitUntil { agenda.mobilizations.size == 9 }
        val m = agenda.mobilizations.single { it.event.id == gathering.id }
        val requests = server.received.size + server.fetches + server.heldQueries.size

        agenda.respond(m, AgendaResponse.GOING)
        assertEquals(AgendaResponse.GOING, agenda.responseTo(m))
        agenda.respond(m, AgendaResponse.INTERESTED)
        assertEquals(AgendaResponse.INTERESTED, agenda.responseTo(m))
        awaitUntil { device.state.responses()[m.address.toString()] == AgendaResponse.INTERESTED }
        // Choosing the same answer again removes it.
        agenda.respond(m, AgendaResponse.INTERESTED)
        assertNull(agenda.responseTo(m))
        agenda.respond(m, AgendaResponse.GOING)
        awaitUntil { device.state.responses()[m.address.toString()] == AgendaResponse.GOING }
        delay(100)
        assertEquals(requests, server.received.size + server.fetches + server.heldQueries.size, "an answer sends nothing")
        coroutineContext.cancelChildren()

        // A day after the end of the event, the next start erases the answer.
        now = AgendaClock.at(2026, 11, 15, 17, 1)
        val later = library(device, clock = { now })
        later.start()
        awaitUntil { later.mobilizations.isNotEmpty() }
        assertEquals(emptyMap(), device.state.responses())
        coroutineContext.cancelChildren()
    }

    @Test
    fun withoutAccountThereIsNoProposalOfTheUser() = runBlocking {
        val device = AgendaDevice(withAccount = true)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        agenda.composer.draft = validDraft
        agenda.composer.charterAccepted = true
        assertEquals(MobilizationSendOutcome.WAITING_FOR_SERVER, agenda.composer.send(device.signer))
        agenda.onSent()
        awaitUntil { agenda.mine.size == 1 }
        agenda.tab = AgendaTab.MINE
        agenda.setAccount(null)
        awaitUntil { agenda.mine.isEmpty() }
        assertEquals(AgendaTab.UPCOMING, agenda.tab)
        assertTrue(!agenda.hasAccount)
        // Still not on the agenda: it never reached the server.
        assertTrue(agenda.mobilizations.none { it.displayTitle == "Rassemblement devant la maternité" })
        assertEquals(AgendaTiming.UPCOMING, agenda.query.timing)
        coroutineContext.cancelChildren()
    }
}
