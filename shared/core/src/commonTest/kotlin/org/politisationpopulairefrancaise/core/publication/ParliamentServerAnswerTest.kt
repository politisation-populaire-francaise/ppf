package org.politisationpopulairefrancaise.core.publication

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** How the server's answers about the parliamentary open data are read. */
class ParliamentServerAnswerTest {

    @Test
    fun addressesOfTheIndexAndOfAPackage() {
        assertEquals("https://exemple.test/api/v1/parlement", ServerPublicationPort.parliamentUrl("https://exemple.test", null))
        assertEquals("https://exemple.test/api/v1/parlement/membres", ServerPublicationPort.parliamentUrl("https://exemple.test", "membres"))
    }

    @Test
    fun answersByStatus() {
        val body = assertIs<OpenDataDownload.Body>(ServerPublicationPort.parliamentAnswer(200, byteArrayOf(1, 2), "W/\"abc\"", isPackage = true))
        assertContentEquals(byteArrayOf(1, 2), body.bytes)
        assertEquals("abc", body.etag)
        assertEquals(OpenDataDownload.NotModified, ServerPublicationPort.parliamentAnswer(304, null, null, isPackage = true))
        assertEquals(OpenDataDownload.UnknownPackage, ServerPublicationPort.parliamentAnswer(404, null, null, isPackage = true))
        // A server without the open data answers 404 to the index: unavailable, not an unknown package.
        assertIs<OpenDataDownload.Unavailable>(ServerPublicationPort.parliamentAnswer(404, null, null, isPackage = false))
        assertIs<OpenDataDownload.Unavailable>(ServerPublicationPort.parliamentAnswer(503, null, null, isPackage = false))
    }

    @Test
    fun aSourceWithoutOpenDataAnswersUnavailable() = runTest {
        val source = object : RemoteEvents {
            override suspend fun publish(event: SignedEvent): PublicationResult = PublicationResult.Unavailable()
            override suspend fun fetch(filter: RemoteFilter): FetchResult = FetchResult.Unavailable()
        }
        assertIs<OpenDataDownload.Unavailable>(source.fetchParliament())
    }
}
