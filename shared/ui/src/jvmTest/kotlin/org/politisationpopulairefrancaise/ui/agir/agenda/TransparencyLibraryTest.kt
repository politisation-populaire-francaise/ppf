package org.politisationpopulairefrancaise.ui.agir.agenda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.Participation
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationStatus
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.ui.content.LibraryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The public responses of organisations on the device (D-044, contract version 2): pulled by type,
 * kept in the local store, published by an organisation's own key through the single port, kept on
 * the device without network, put back when refused.
 */
class TransparencyLibraryTest {
    private val author = AgendaKit()
    private val union = AgendaKit()

    private fun CoroutineScope.library(device: AgendaDevice, clock: () -> Long = { AgendaClock.NOW }) =
        AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), device.server, this, clock = clock)

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(20_000) {
        while (!condition()) delay(20)
    }

    private val strasbourg = "reunion-publique-strasbourg-2026-12-10"

    @Test
    fun theBundledExampleShowsTheParticipationOfTheEditorialTeam() = runBlocking {
        val device = AgendaDevice(withAccount = false)
        val agenda = library(device)
        agenda.start()
        awaitUntil { agenda.mobilizations.size == 8 && agenda.status == LibraryStatus.Offline }
        val meeting = agenda.mobilizations.single { it.identifier == strasbourg }
        assertEquals(setOf(meeting.address.toString()), agenda.participations.acceptedBy(EditorialTrust.EXAMPLE_EDITOR))
        val hosting = agenda.participations.hosting(meeting) { it == EditorialTrust.EXAMPLE_EDITOR }
        assertEquals(listOf(EditorialTrust.EXAMPLE_EDITOR), hosting.participants)
        // The examples of the agenda that name fictional organisations: announced, never confirmed.
        val paris = agenda.mobilizations.single { it.identifier == "rassemblement-services-publics-paris-2026-11-14" }
        assertEquals(paris.organizers, agenda.participations.hosting(paris) { true }.announced)
        coroutineContext.cancelChildren()
    }

    @Test
    fun responsesArePulledByTypeAndNoRequestNamesAnEvent() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = false, server = server)
        val event = author.mobilization("marche", "[EXEMPLE] Marche des familles", start = AgendaClock.at(2026, 11, 21, 15), organizers = listOf(union.pubkey))
        server.seed(event)
        server.seed(union.participation(event, role = ParticipationRole.CO_ORGANISER))
        val agenda = library(device)
        agenda.start()
        val address = checkNotNull(Mobilization.from(event)).address.toString()
        awaitUntil { agenda.participations.of(union.pubkey, address) != null }
        assertEquals(ParticipationRole.CO_ORGANISER, agenda.participations.of(union.pubkey, address)?.role)
        // The whole type, no author, no identifier, no tag: the server cannot tell what is looked at.
        assertTrue(server.filters.isNotEmpty())
        assertTrue(server.filters.all { it.authors.isEmpty() && it.identifiers.isEmpty() }, server.filters.toString())
        assertEquals(setOf(listOf(Mobilization.KIND), listOf(Participation.KIND)), server.filters.map { it.kinds }.toSet())
        // The first pull of the responses starts from the beginning, whatever the bundled examples.
        assertEquals(0L, server.filters.first { it.kinds == listOf(Participation.KIND) }.since)
        coroutineContext.cancelChildren()
    }

    @Test
    fun anOrganisationShowsItsParticipationThenWithdrawsIt() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val event = author.mobilization("rassemblement", "[EXEMPLE] Rassemblement", start = AgendaClock.at(2026, 11, 21, 15), organizers = listOf(device.pubkey))
        server.seed(event)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.mobilizations.any { it.event.id == event.id } && agenda.status is LibraryStatus.Synced }
        val m = agenda.mobilizations.single { it.event.id == event.id }
        agenda.respond(m, AgendaResponse.GOING)

        assertEquals(ParticipationOutcome.SHOWN, agenda.answerPublicly(m, device.signer, ParticipationStatus.ACCEPTED, ParticipationRole.CO_ORGANISER))
        val sent = server.received.single { it.kind == Participation.KIND }
        assertEquals(listOf("d", m.address.toString()), sent.tags[0])
        assertEquals(ParticipationRole.CO_ORGANISER, Participation.from(sent)?.role)
        assertEquals(listOf(device.pubkey), agenda.participations.hosting(m) { it == device.pubkey }.confirmed)
        // Shown publicly: the private answer is no longer needed.
        awaitUntil { agenda.responseTo(m) == null }

        assertEquals(ParticipationOutcome.WITHDRAWN, agenda.answerPublicly(m, device.signer, ParticipationStatus.DECLINED))
        val withdrawn = server.received.filter { it.kind == Participation.KIND }.last()
        assertEquals(ParticipationStatus.DECLINED, Participation.from(withdrawn)?.status)
        assertTrue(withdrawn.createdAt > sent.createdAt, "the newer version replaces the older one")
        assertEquals(ParticipationStatus.DECLINED, agenda.publicAnswerOf(m)?.status)
        assertEquals(emptyList(), agenda.participations.hosting(m) { it == device.pubkey }.participants)
        coroutineContext.cancelChildren()
    }

    @Test
    fun withoutNetworkTheResponseWaitsOnTheDeviceThenLeaves() = runBlocking {
        val server = AgendaServer().apply { online = false }
        val device = AgendaDevice(withAccount = true, server = server)
        val event = author.mobilization("reunion", "[EXEMPLE] Réunion", start = AgendaClock.at(2026, 11, 21, 18), type = org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType.REUNION)
        device.store.store(event)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.mobilizations.any { it.event.id == event.id } && agenda.status == LibraryStatus.Unreachable }
        val m = agenda.mobilizations.single { it.event.id == event.id }
        assertEquals(ParticipationOutcome.WAITING_FOR_SERVER, agenda.answerPublicly(m, device.signer, ParticipationStatus.ACCEPTED))
        assertTrue(agenda.publicAnswerWaiting(m))

        server.online = true
        agenda.refresh()
        awaitUntil { server.received.any { it.kind == Participation.KIND } }
        awaitUntil { !agenda.publicAnswerWaiting(m) }
        assertEquals(ParticipationStatus.ACCEPTED, agenda.publicAnswerOf(m)?.status)
        coroutineContext.cancelChildren()
    }

    @Test
    fun aRefusalPutsThePreviousAnswerBack() = runBlocking {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val event = author.mobilization("greve", "[EXEMPLE] Grève", start = AgendaClock.at(2026, 11, 24, 6))
        server.seed(event)
        val agenda = library(device)
        agenda.start()
        agenda.setAccount(device.pubkey)
        awaitUntil { agenda.mobilizations.any { it.event.id == event.id } && agenda.status is LibraryStatus.Synced }
        val m = agenda.mobilizations.single { it.event.id == event.id }
        assertEquals(ParticipationOutcome.SHOWN, agenda.answerPublicly(m, device.signer, ParticipationStatus.ACCEPTED))
        val shown = checkNotNull(agenda.publicAnswerOf(m)).event.id
        // The server refuses the next version for good: the published one comes back on the device.
        server.refuseAll = true
        assertEquals(ParticipationOutcome.REFUSED, agenda.answerPublicly(m, device.signer, ParticipationStatus.DECLINED))
        assertEquals(shown, agenda.publicAnswerOf(m)?.event?.id)
        // Another account cannot answer in the name of this one.
        assertEquals(ParticipationOutcome.FAILED, agenda.answerPublicly(m, author.signer, ParticipationStatus.ACCEPTED))
        assertNull(agenda.participations.of(author.pubkey, m.address.toString()))
        coroutineContext.cancelChildren()
    }
}
