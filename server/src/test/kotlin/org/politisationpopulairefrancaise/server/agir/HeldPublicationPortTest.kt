package org.politisationpopulairefrancaise.server.agir

import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.core.publication.HeldStatuses
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.events.EventRepository
import org.politisationpopulairefrancaise.server.events.PublicationPolicy
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The device's adapter (shared/core) talking to the real server code about held contents, end to end. */
class HeldPublicationPortTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val citizen = LocalEventSigner(IdentityKeys.generate())
    private val queue = HeldQueue(TestDatabase.dataSource, EventRepository(TestDatabase.dataSource))

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    @Test
    fun aHeldContentIsAcceptedForReviewAndFollowedByItsAuthor() = testApplication {
        application { module(TestDatabase.dataSource, PublicationPolicy(setOf(editor.publicKey.hex)), TEST_RELAY, testRevendications(), testAgir()) }
        val port = ServerPublicationPort(client, baseUrl = "")
        val proposed = citizen.mobilization()
        val witnesses = citizen.call(AgirFormats.CALL_TYPE_WITNESSES)
        val direct = editor.mobilization()

        assertEquals(PublicationResult.Accepted(proposed.id, heldForReview = true), port.publish(proposed))
        assertEquals(PublicationResult.Accepted(witnesses.id, heldForReview = true), port.publish(witnesses))
        assertEquals(PublicationResult.Accepted(direct.id), port.publish(direct))

        val pending = assertIs<HeldStatuses.Known>(port.heldStatuses(listOf(proposed.id, witnesses.id, direct.id))).states
        assertEquals(listOf(proposed.id to HeldStatus.PENDING, witnesses.id to HeldStatus.PENDING), pending.map { it.id to it.status })

        queue.approve(proposed.id)
        queue.reject(witnesses.id, "[EXEMPLE] Les faits doivent être décrits sans nommer personne.")
        val decided = assertIs<HeldStatuses.Known>(port.heldStatuses(listOf(proposed.id, witnesses.id))).states.associateBy { it.id }
        assertEquals(HeldStatus.APPROVED, decided.getValue(proposed.id).status)
        assertEquals(HeldStatus.REJECTED, decided.getValue(witnesses.id).status)
        assertEquals("[EXEMPLE] Les faits doivent être décrits sans nommer personne.", decided.getValue(witnesses.id).reason)

        // Synchronised like any content once approved.
        val synced = assertIs<FetchResult.Events>(port.fetch(RemoteFilter(kinds = listOf(AgirFormats.KIND_MOBILIZATION))))
        assertEquals(setOf(proposed, direct), synced.events.toSet())
    }
}
