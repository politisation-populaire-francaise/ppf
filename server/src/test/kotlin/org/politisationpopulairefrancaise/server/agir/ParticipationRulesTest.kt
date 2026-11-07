package org.politisationpopulairefrancaise.server.agir

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.Participation
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationStatus
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.organisationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.participation
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.participationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.publish
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.published
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withTag
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withoutTag
import org.politisationpopulairefrancaise.server.api.PublishResponse
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The public responses to a mobilization (kind 31925, NIP-52, contract version 2, D-044): their
 * shape, checked for every key, and their direct publication (no text shown, nothing to review).
 */
class ParticipationRulesTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val organisationKey = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val policy = PublicationPolicy(setOf(editor.publicKey.hex))
    private val events = EventRepository(TestDatabase.dataSource)
    private val queue = HeldQueue(TestDatabase.dataSource, events)

    private val target = "31923:${editor.publicKey.hex}:manif-exemple"

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun allowed(event: SignedEvent) = assertEquals(PublicationPolicy.Decision.Allowed, policy.check(event))

    private fun refused(event: SignedEvent): Pair<String, String> {
        val refusal = assertIs<PublicationPolicy.Decision.Refused>(policy.check(event))
        assertEquals(400, refusal.status, refusal.error)
        return refusal.error to refusal.message
    }

    @Test
    fun aResponseOfTheContractPassesWhoeverSignsIt() = runBlocking {
        assertEquals(AgirCategory.PARTICIPATION, AgirRules.categoryOf(organisationKey.participation(participationTags(target))))
        for (signer in listOf(editor, organisationKey, citizen)) allowed(signer.participation(participationTags(target)))
        for (status in AgirFormats.PARTICIPATION_STATUSES) allowed(citizen.participation(participationTags(target, status = status)))
        allowed(organisationKey.participation(participationTags(target, role = "organisateur")))
        // As other NIP-52 clients write them: a UUID as d, no role, no p, an e and fb.
        allowed(
            citizen.participation(
                listOf(
                    listOf("d", "6f1c2b9e-27b4-4c4e-9a0f-0d5b3c1e8a77"),
                    listOf("a", target, "wss://relais.exemple.invalid"),
                    listOf("e", "b".repeat(64)),
                    listOf("status", "tentative"),
                    listOf("fb", "busy"),
                ),
            ),
        )
        // A short note is fine; the application never shows it.
        allowed(organisationKey.participation(participationTags(target), content = "[EXEMPLE] Nous y serons avec nos banderoles."))
        allowed(organisationKey.participation(participationTags(target), content = "x".repeat(AgirFormats.MAX_PARTICIPATION_NOTE)))
    }

    @Test
    fun everyShapeErrorOfAResponseHasItsCode() = runBlocking {
        val tags = participationTags(target)
        assertEquals("missing_field" to "d", refused(citizen.participation(tags.withoutTag("d"))))
        assertEquals("missing_field" to "d", refused(citizen.participation(tags + listOf(listOf("d", "deuxieme")))))
        assertEquals("missing_field" to "d", refused(citizen.participation(tags.withTag("d", "avec espace"))))
        assertEquals("missing_field" to "d", refused(citizen.participation(tags.withTag("d", "x".repeat(AgirFormats.MAX_PARTICIPATION_ID + 1)))))
        assertEquals("missing_field" to "a", refused(citizen.participation(tags.withoutTag("a"))))
        assertEquals("missing_field" to "a", refused(citizen.participation(tags.withTag("a", "30023:${editor.publicKey.hex}:un-appel"))))
        assertEquals("missing_field" to "a", refused(citizen.participation(tags.withTag("a", "31923:pas-une-cle:manif"))))
        assertEquals("missing_field" to "a", refused(citizen.participation(tags.withTag("a", "31923:${editor.publicKey.hex}:Majuscules"))))
        assertEquals("missing_field" to "a", refused(citizen.participation(tags + listOf(listOf("a", "31923:${editor.publicKey.hex}:autre")))))
        assertEquals("missing_field" to "status", refused(citizen.participation(tags.withoutTag("status"))))
        assertEquals("missing_field" to "status", refused(citizen.participation(tags.withTag("status", "maybe"))))
        assertEquals("missing_field" to "ppf-role", refused(citizen.participation(tags.withTag("ppf-role", "president"))))
        assertEquals("missing_field" to "fb", refused(citizen.participation(tags.withTag("fb", "peut-etre"))))
        // p names the author of the mobilization, nobody else.
        assertEquals("missing_field" to "p", refused(citizen.participation(tags.withTag("p", citizen.publicKey.hex))))
        assertEquals("missing_field" to "e", refused(citizen.participation(tags + listOf(listOf("e", "pas-un-identifiant")))))
        assertEquals("too_long" to "content", refused(citizen.participation(tags, content = "x".repeat(AgirFormats.MAX_PARTICIPATION_NOTE + 1))))
        assertEquals("personal_data" to "content", refused(organisationKey.participation(tags, content = "[EXEMPLE] Contact : 06 12 34 56 78")))
        assertEquals("personal_data" to "content", refused(organisationKey.participation(tags, content = "[EXEMPLE] Écrire à jean.exemple@example.org")))
        assertEquals("malformed_event", refused(citizen.participation(tags, content = "[EXEMPLE] \u0007")).first)
        assertEquals("malformed_event", refused(citizen.participation(tags.withTag("status", "accepted\n"))).first)
    }

    @Test
    fun theResponseBuiltByTheDeviceIsAccepted() = runBlocking {
        val mobilization = checkNotNull(Mobilization.from(editor.mobilization()))
        for (role in ParticipationRole.entries) {
            for (status in ParticipationStatus.entries) {
                allowed(organisationKey.sign(Participation.build(organisationKey.publicKey, mobilization, status, AgirFixtures.nowSeconds(), role)))
            }
        }
    }

    @Test
    fun aResponseIsPublishedAtOnceAndServedByType() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications(), testAgir()) }
        // The editorial team verifies the organisation; the mobilization is the team's.
        assertEquals(HttpStatusCode.Created, client.publish(editor.organisation(organisationTags(member = organisationKey.publicKey.hex))).status)
        val mobilization = editor.mobilization()
        assertEquals(HttpStatusCode.Created, client.publish(mobilization).status)
        val address = "31923:${editor.publicKey.hex}:manif-exemple"
        // Whoever signs it, a response is published at once: nothing to review (no text shown).
        val fromOrganisation = organisationKey.participation(participationTags(address))
        val fromCitizen = citizen.participation(participationTags(address))
        for (event in listOf(fromOrganisation, fromCitizen)) {
            val response = client.publish(event)
            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertEquals(PublishResponse(event.id, "stored"), response.published())
            assertEquals(event, events.find(event.id))
            assertNull(queue.find(event.id))
        }
        // Withdrawing it: a newer version, declined, replaces it at the same address.
        val withdrawn = organisationKey.participation(participationTags(address, status = "declined"), createdAt = fromOrganisation.createdAt + 10)
        assertEquals(HttpStatusCode.Created, client.publish(withdrawn).status)
        assertNull(events.find(fromOrganisation.id))
        // A device asks for the whole type, never for one mobilization.
        val served = Json.decodeFromString(ListSerializer(SignedEvent.serializer()), client.get("/api/v1/events?kind=31925").bodyAsText())
        assertEquals(setOf(withdrawn.id, fromCitizen.id), served.map { it.id }.toSet())
        // A malformed one is refused with the code of the contract.
        val broken = client.publish(citizen.participation(participationTags(address).withoutTag("status")))
        assertEquals(HttpStatusCode.BadRequest, broken.status)
    }
}
