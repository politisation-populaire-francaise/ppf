package org.politisationpopulairefrancaise.server.agir

import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.DateTimeText
import org.politisationpopulairefrancaise.core.agir.calls.SlotDraft
import org.politisationpopulairefrancaise.core.agir.calls.SlotOutcome
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignups
import org.politisationpopulairefrancaise.core.agir.calls.SlotWithdrawal
import org.politisationpopulairefrancaise.core.agir.calls.slots
import org.politisationpopulairefrancaise.core.contact.ContentReports
import org.politisationpopulairefrancaise.core.contact.ContactInbox
import org.politisationpopulairefrancaise.core.contact.ReportOutcome
import org.politisationpopulairefrancaise.core.contact.ReportReason
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.contact.ContactFixtures
import org.politisationpopulairefrancaise.server.contact.ContactSetup
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The journeys of « Agir » end to end: what the application builds (shared/core), sent by its server adapter to the
 * real server code on PostgreSQL. A call cut into slots, launched by a citizen, signed up to, withdrawn, a fake signup
 * removed by the organizer, the call closed with its report, then reported by someone else.
 */
class AgirJourneysTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val organizerKeys = IdentityKeys.generate()
    private val organizer = LocalEventSigner(organizerKeys)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    /** A date of the coming days, typed as in the form (Paris). */
    private fun inDays(days: Long): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy").format(Instant.now().plusSeconds(days * 86_400).atZone(ZoneId.of("Europe/Paris")))

    @Test
    fun aCallCutIntoSlotsLivesFromItsLaunchToItsReport() = testApplication {
        application {
            module(
                TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications(), testAgir(),
                contact = ContactSetup(ContactFixtures.config()), backOffice = null,
            )
        }
        val port = ServerPublicationPort(client, baseUrl = "")
        val now = Instant.now().epochSecond
        val draft = CallDraft(
            type = CallType.BENEVOLES,
            title = "[EXEMPLE] Tenir la permanence de la bourse du travail",
            text = "[EXEMPLE] Nous cherchons du monde pour accueillir les grévistes à la permanence, matin et soir.",
            territory = "Lyon",
            location = "[EXEMPLE] Bourse du travail, salle 2",
            dates = mapOf("ppf-deadline" to DateTimeText(inDays(10), "")),
            fields = mapOf("ppf-mission" to "[EXEMPLE] Accueillir et orienter", "ppf-people" to "6", "ppf-skills" to "[EXEMPLE] Aucune", "ppf-period" to "[EXEMPLE] La semaine prochaine"),
            slots = listOf(SlotDraft("Matin", inDays(3), "8h", "12h", "2"), SlotDraft("Soir", inDays(3), "17h", "20h", "4")),
        )
        // The form's own rules first: what the application would refuse never reaches the server.
        assertTrue(org.politisationpopulairefrancaise.core.agir.calls.CallRules.check(draft, canFundraise = false, now = now).isEmpty(),
            org.politisationpopulairefrancaise.core.agir.calls.CallRules.check(draft, false, now).toString())
        val event = organizer.sign(draft.toUnsignedEvent(organizer.publicKey, now))
        val published = port.publish(event)
        assertIs<PublicationResult.Accepted>(published, published.toString())
        // A call of a citizen waits for the team: nobody signs up before it is published.
        if (published.heldForReview) {
            assertEquals(SlotOutcome.Refused("unknown_call"), port.signUp(SlotSignups.prepare(requireNotNull(Call.from(event)), requireNotNull(Call.from(event)).slots[0], "Trop tôt").request))
            HeldQueue(TestDatabase.dataSource, org.politisationpopulairefrancaise.server.events.EventRepository(TestDatabase.dataSource)).approve(event.id)
        }
        val call = requireNotNull(Call.from(event))
        assertEquals(listOf("c1", "c2"), call.slots.map { it.id })

        // Two people sign up to the morning, the second takes the last place, a third finds it full.
        val first = SlotSignups.prepare(call, call.slots[0], "Camille")
        assertEquals(SlotOutcome.Done(first.request.envelope.id, 1), port.signUp(first.request))
        val second = SlotSignups.prepare(call, call.slots[0], "Faux nom")
        assertIs<SlotOutcome.Done>(port.signUp(second.request))
        assertEquals(SlotOutcome.Full, port.signUp(SlotSignups.prepare(call, call.slots[0], "Sam").request))
        assertEquals(mapOf("c1" to 2), port.counts(call.address.toString()))

        // The organizer reads the names on their device, and removes the fake one.
        val names = requireNotNull(port.signups(call.address.toString())).mapNotNull { SlotSignups.openWithId(it, LocalMessagingSigner(organizerKeys), call.address.toString()) }
        assertEquals(setOf("Camille", "Faux nom"), names.map { it.second.name }.toSet())
        val fake = names.first { it.second.name == "Faux nom" }.first
        assertIs<SlotOutcome.Refused>(port.remove(fake, LocalEventSigner(IdentityKeys.generate())), "only the organizer")
        assertIs<SlotOutcome.Done>(port.remove(fake, organizer))
        // Camille withdraws with her token: the slot is empty again.
        assertIs<SlotOutcome.Done>(port.withdraw(SlotWithdrawal(first.request.envelope.id, first.token)))
        assertEquals(emptyMap(), port.counts(call.address.toString()))

        // The organizer closes the call with a report: published at once, the slots stay readable.
        val closing = organizer.sign(call.closedVersion("[EXEMPLE] Merci : la permanence a tenu toute la semaine.", now + 1))
        assertIs<PublicationResult.Accepted>(port.publish(closing))
        assertEquals(SlotOutcome.Refused("closed"), port.signUp(SlotSignups.prepare(call, call.slots[1], "Lou").request))

        // Anyone reports the call to the team, without an account.
        val report = ContentReports.prepare(call.address.toString(), ReportReason.SPAM, "[EXEMPLE] Doublon d'un autre appel.", ContactInbox.EXAMPLE)
        assertIs<ReportOutcome.Sent>(port.createCase(report.creation))
    }
}
