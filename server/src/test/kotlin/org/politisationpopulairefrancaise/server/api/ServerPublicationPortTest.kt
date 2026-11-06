package org.politisationpopulairefrancaise.server.api

import io.ktor.server.testing.testApplication
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RejectionReason
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.ServerPublicationPort
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The application-side adapter talking to the real server code, end to end. */
class ServerPublicationPortTest {

    private val signer = LocalEventSigner(IdentityKeys.generate())

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    @Test
    fun publishesAndFetchesThroughTheRealApi() = testApplication {
        application { module(TestDatabase.dataSource) }
        val port = ServerPublicationPort(client, baseUrl = "")
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] via le port", createdAt = 1_700_000_000)

        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(PublicationResult.Accepted(event.id), port.publish(event))
        assertEquals(
            PublicationResult.Rejected(event.id, RejectionReason.INVALID_SIGNATURE),
            port.publish(event.copy(content = "[EXEMPLE] falsifié")),
        )

        val fetched = port.fetch(RemoteFilter(kinds = listOf(EventKinds.TEXT_NOTE), authors = listOf(event.pubkey), since = 1_600_000_000))
        assertEquals(FetchResult.Events(listOf(event)), fetched)
        assertEquals(FetchResult.Events(emptyList()), port.fetch(RemoteFilter(kinds = listOf(EventKinds.METADATA))))
    }

    @Test
    fun aServerWithoutDatabaseIsReportedUnavailable() = testApplication {
        application { module(null) }
        val port = ServerPublicationPort(client, baseUrl = "")
        val event = signer.sign(kind = EventKinds.TEXT_NOTE, content = "x", createdAt = 1_700_000_000)
        assertIs<PublicationResult.Unavailable>(port.publish(event))
        assertIs<FetchResult.Unavailable>(port.fetch(RemoteFilter()))
    }
}
