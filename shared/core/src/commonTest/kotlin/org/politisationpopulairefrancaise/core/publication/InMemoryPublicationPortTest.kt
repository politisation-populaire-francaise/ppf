package org.politisationpopulairefrancaise.core.publication

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryPublicationPortTest {

    @Test
    fun acceptsAuthenticEventsOnceAndRejectsForgedOnes() = runTest {
        val port = InMemoryPublicationPort()
        val signer = LocalEventSigner(IdentityKeys.generate())
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] première publication")

        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(listOf(event), port.events)

        val forged = event.copy(content = "[EXEMPLE] contenu modifié")
        assertEquals(PublicationResult.Rejected(forged.id, RejectionReason.INVALID_SIGNATURE), port.publish(forged))
        assertEquals(listOf(event), port.events)
    }
}
