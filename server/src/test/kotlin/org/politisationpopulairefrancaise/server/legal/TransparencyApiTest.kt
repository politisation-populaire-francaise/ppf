package org.politisationpopulairefrancaise.server.legal

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.politisationpopulairefrancaise.server.NO_EDITORS
import org.politisationpopulairefrancaise.server.TEST_RELAY
import org.politisationpopulairefrancaise.server.TestDatabase
import org.politisationpopulairefrancaise.server.agir.AgirFixtures.testAgir
import org.politisationpopulairefrancaise.server.module
import org.politisationpopulairefrancaise.server.testRevendications
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The public counter of the requisitions answered (D-077): counts per month, nothing else. */
class TransparencyApiTest {
    @BeforeTest
    fun emptyDatabase() = TestDatabase.reset()

    private fun answered(approval: String, at: String, operations: Int) {
        TestDatabase.dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "INSERT INTO legal_register.requisition_answer (approval_id, answered_at, operation_rows, pseudonym_rows) " +
                        "VALUES ('$approval', '$at', $operations, 1)",
                )
            }
        }
    }

    @Test
    fun onlyTheNumberOfRequisitionsAnsweredPerMonthIsPublic() = testApplication {
        application { module(TestDatabase.dataSource, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(), backOffice = null) }
        val empty = client.get("/api/v1/transparence/requisitions")
        assertEquals(HttpStatusCode.OK, empty.status)
        assertEquals(RequisitionTransparency(months = emptyList(), total = 0), Json.decodeFromString<RequisitionTransparency>(empty.bodyAsText()))
        answered("00000000-0000-0000-0000-000000000001", "2026-10-21T08:00:00Z", 3)
        answered("00000000-0000-0000-0000-000000000002", "2026-11-02T08:00:00Z", 12)
        answered("00000000-0000-0000-0000-000000000003", "2026-11-30T23:30:00Z", 0)
        val body = client.get("/api/v1/transparence/requisitions").bodyAsText()
        assertEquals(
            RequisitionTransparency(months = listOf(RequisitionMonth("2026-10", 1), RequisitionMonth("2026-11", 2)), total = 3),
            Json.decodeFromString<RequisitionTransparency>(body),
        )
        listOf("00000000-0000", "operation", "pseudonym", "08:00").forEach { assertFalse(body.contains(it), "the counter shows no $it") }
    }

    @Test
    fun withoutADatabaseTheRouteAnswersLikeTheRestOfTheApi() = testApplication {
        application { module(null, NO_EDITORS, TEST_RELAY, testRevendications(), testAgir(), backOffice = null) }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/v1/transparence/requisitions").status)
    }
}
