package org.politisationpopulairefrancaise.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.core.identity.AccountType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.api.ApiError
import org.politisationpopulairefrancaise.server.module
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A collective account never votes and never becomes a person again (D-133). */
class AccountTypesTest {

    private val collective = LocalEventSigner(IdentityKeys.generate())
    private val person = LocalEventSigner(IdentityKeys.generate())
    private val types = AccountTypes(TestDatabase.dataSource)

    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private suspend fun HttpClient.publish(event: SignedEvent): HttpResponse = post("/api/v1/events") {
        contentType(ContentType.Application.Json)
        setBody(event.toJson())
    }

    private suspend fun LocalEventSigner.profile(type: AccountType?, at: Long): SignedEvent =
        sign(kind = EventKinds.METADATA, content = """{"name":"[EXEMPLE] Collectif vélo"}""", tags = listOfNotNull(type?.tag()), createdAt = at)

    @Test
    fun theTypeIsReadFromTheProfile() = testApplication {
        assertEquals(AccountType.PERSON, AccountType.of(person.profile(null, 1)))
        assertEquals(AccountType.PERSON, AccountType.of(person.profile(AccountType.PERSON, 1)))
        assertEquals(AccountType.COLLECTIVE, AccountType.of(collective.profile(AccountType.COLLECTIVE, 1)))
        // Only a profile declares a type.
        assertEquals(AccountType.PERSON, AccountType.of(collective.sign(kind = EventKinds.TEXT_NOTE, tags = listOf(AccountType.COLLECTIVE.tag()))))
    }

    @Test
    fun aCollectiveStaysACollective() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY) }
        assertEquals(HttpStatusCode.Created, client.publish(person.profile(null, 1_700_000_000)).status)
        assertFalse(types.isCollective(person.publicKey.hex))

        assertEquals(HttpStatusCode.Created, client.publish(collective.profile(AccountType.COLLECTIVE, 1_700_000_000)).status)
        assertTrue(types.isCollective(collective.publicKey.hex))

        // A newer profile that drops the type, or says person, is refused.
        listOf(null, AccountType.PERSON).forEachIndexed { index, type ->
            val refused = client.publish(collective.profile(type, 1_700_000_100L + index))
            assertEquals(HttpStatusCode.Conflict, refused.status)
            assertEquals("account_type_locked", Json.decodeFromString<ApiError>(refused.bodyAsText()).error)
        }
        // Updating the profile of a collective as a collective is fine.
        assertEquals(HttpStatusCode.Created, client.publish(collective.profile(AccountType.COLLECTIVE, 1_700_000_200)).status)
        assertTrue(types.isCollective(collective.publicKey.hex))

        // A person may later declare a collective profile, and is then locked as one.
        assertEquals(HttpStatusCode.Created, client.publish(person.profile(AccountType.COLLECTIVE, 1_700_000_300)).status)
        assertTrue(types.isCollective(person.publicKey.hex))
    }
}
