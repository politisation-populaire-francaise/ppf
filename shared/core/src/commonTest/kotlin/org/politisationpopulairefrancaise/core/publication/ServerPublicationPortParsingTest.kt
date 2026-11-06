package org.politisationpopulairefrancaise.core.publication

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pieces of the server adapter that do not need a network: error codes and the body of GET /api/v1/groups. */
class ServerPublicationPortParsingTest {

    @Test
    fun errorCodesBecomeReasons() {
        assertEquals(RejectionReason.INVALID_SIGNATURE, ServerPublicationPort.rejectionReason("invalid_signature"))
        assertEquals(RejectionReason.GROUP_EXISTS, ServerPublicationPort.rejectionReason("group_exists"))
        assertEquals(RejectionReason.GROUP_TAKEN, ServerPublicationPort.rejectionReason("group_taken"))
        assertEquals(RejectionReason.GROUP_CLOSED, ServerPublicationPort.rejectionReason("group_closed"))
        assertEquals(RejectionReason.GROUP_NOT_FOUND, ServerPublicationPort.rejectionReason("group_not_found"))
        assertEquals(RejectionReason.MISSING_TERRITORY, ServerPublicationPort.rejectionReason("missing_territory"))
        assertEquals(RejectionReason.MISSING_CHARTER, ServerPublicationPort.rejectionReason("missing_charter"))
        assertEquals(RejectionReason.REFUSED, ServerPublicationPort.rejectionReason("not_allowed"))
        assertEquals(RejectionReason.REFUSED, ServerPublicationPort.rejectionReason(null))
    }

    @Test
    fun theGroupsBodyBecomesRooms() = runTest {
        val signer = LocalEventSigner(IdentityKeys.generate())
        val event = signer.sign(Group.build(signer.publicKey, "collectif-demonstration", "[EXEMPLE] Collectif de démonstration", "Essai", "Lyon", createdAt = 1_700_000_000))
        val body = """
            [{"id":"collectif-demonstration","name":"[EXEMPLE] Collectif de démonstration","about":"Essai","picture":null,
              "territory":"Lyon","charter":"1.0","public":true,"open":true,"unknown_field":1,"event":${event.toJson()}},
             {"id":"ferme","name":"Fermé","about":null,"picture":null,"territory":null,"charter":null,"public":true,"open":false,"event":${event.toJson()}}]
        """.trimIndent()
        val groups = ServerPublicationPort.parseGroups(body)
        assertEquals(2, groups.size)
        val first = groups[0]
        assertEquals("collectif-demonstration", first.id)
        assertEquals("[EXEMPLE] Collectif de démonstration", first.name)
        assertEquals("Essai", first.about)
        assertEquals("Lyon", first.territory)
        assertEquals("1.0", first.charter)
        assertTrue(first.public && first.open)
        assertEquals(event, first.event)
        assertTrue(first.event.verify())
        assertFalse(groups[1].open)
        assertEquals(null, groups[1].territory)
    }
}
