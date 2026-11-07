package org.politisationpopulairefrancaise.server.agir

import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.call
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilization
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.mobilizationTags
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.withTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The cancellation of an event and the closing of a call are built by the application
 * (core.agir.agenda.Mobilization.cancellation, core.agir.calls.Call.closedVersion) and recognised by
 * the server ([AuthorChanges.onlyEnds]), which then publishes them without review (decision of the
 * owner, 7 November 2026). Both sides were written apart: this test keeps them in agreement.
 */
class DeviceEndingsTest {
    private val signer = LocalEventSigner(IdentityKeys.generate())

    @Test
    fun theCancellationBuiltByTheDeviceIsRecognised() = runBlocking {
        val withoutStatus = signer.mobilization()
        val withStatus = signer.mobilization(tags = mobilizationTags().withTag("ppf-event-status", "reporte"))
        for (published in listOf(withoutStatus, withStatus)) {
            val mobilization = assertNotNull(Mobilization.from(published))
            val cancelled = signer.sign(Mobilization.cancellation(mobilization, now = published.createdAt + 60))
            assertTrue(AuthorChanges.onlyEnds(published, cancelled), "cancellation of ${published.tags}")
        }
    }

    @Test
    fun theClosingBuiltByTheDeviceIsRecognisedAndAWitnessReportIsNot() = runBlocking {
        val published = signer.call("soutenir")
        val call = assertNotNull(Call.from(published))
        assertTrue(AuthorChanges.onlyEnds(published, signer.sign(call.closedVersion("[EXEMPLE] Bilan.", now = published.createdAt + 60))))

        // A call for witnesses: closing it is published at once, a report added to it is reviewed.
        val witnesses = signer.call("temoins")
        val witnessCall = assertNotNull(Call.from(witnesses))
        assertTrue(AuthorChanges.onlyEnds(witnesses, signer.sign(witnessCall.closedVersion("", now = witnesses.createdAt + 60))))
        assertFalse(AuthorChanges.onlyEnds(witnesses, signer.sign(witnessCall.closedVersion("[EXEMPLE] Bilan.", now = witnesses.createdAt + 60))))
    }
}
