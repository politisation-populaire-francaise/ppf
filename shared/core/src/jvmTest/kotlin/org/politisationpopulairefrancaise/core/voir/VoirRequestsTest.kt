package org.politisationpopulairefrancaise.core.voir

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.ChatMessage
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.GiftWrapException
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.messaging.MessagingService
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.InMemoryPublicationPort
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoirRequestsTest {

    private class FakeServer : RemoteEvents {
        private val port = InMemoryPublicationPort()
        var online = true
        val events: List<SignedEvent> get() = port.events
        override suspend fun publish(event: SignedEvent): PublicationResult = if (online) port.publish(event) else PublicationResult.Unavailable()
        override suspend fun fetch(filter: RemoteFilter): FetchResult = if (online) port.fetch(filter) else FetchResult.Unavailable()
        override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult =
            if (online) port.fetchByTags(tags, filter) else FetchResult.Unavailable()
    }

    private val address = "30023:${EditorialTrust.EXAMPLE_EDITOR}:affaire-dominique-marche-public"
    private val team = IdentityKeys.generate()
    private val server = FakeServer()

    private fun reply(text: String = "[EXEMPLE] Je conteste ces faits.", contentWords: Int = 300) = ReplyRequest(
        content = address,
        contentTitle = "[EXEMPLE] Une affaire",
        respondent = "[EXEMPLE] Dominique Fictif",
        capacity = ReplyCapacity.PERSON_NAMED,
        reply = text,
        contentWords = contentWords,
        statementOfTruth = true,
    )

    private fun report(anonymous: Boolean = true) = ErrorReport(
        content = address,
        contentTitle = "[EXEMPLE] Une affaire",
        wrong = "[EXEMPLE] La date est fausse.",
        correction = "[EXEMPLE] Le 3 octobre, pas le 4.",
        source = "https://example.org/communique",
        anonymous = anonymous,
    )

    @Test
    fun bothKindsSurviveARoundTripThroughTheirJson() {
        listOf(reply(), report(), report().copy(correction = null, source = null)).forEach { request ->
            val text = VoirRequests.encode(request)
            assertTrue(text.contains("\"format\":\"ppf-voir-request\""))
            assertTrue(text.contains("\"version\":1"))
            assertEquals(request, VoirRequests.decodeOrNull(text))
        }
        val json = VoirRequests.encode(reply())
        assertTrue(json.contains("\"type\":\"right-of-reply\""))
        assertTrue(json.contains("\"capacity\":\"person-named\""))
        assertTrue(json.contains("\"max_words\":1000"))
        assertTrue(VoirRequests.encode(report()).contains("\"type\":\"error-report\""))
        // An absent optional field is left out, not written as null.
        assertTrue(!VoirRequests.encode(report().copy(source = null)).contains("source"))
    }

    @Test
    fun anyOtherTextIsNotARequest() {
        assertNull(VoirRequests.decodeOrNull("Bonjour"))
        assertNull(VoirRequests.decodeOrNull("{\"format\":\"ppf-voir-request\"}"))
        assertNull(VoirRequests.decodeOrNull(VoirRequests.encode(report()).replace("\"version\":1", "\"version\":2")))
        assertNull(VoirRequests.decodeOrNull("{\"type\":\"other\",\"format\":\"ppf-voir-request\",\"version\":1}"))
    }

    @Test
    fun aReplyMayBeAsLongAsTheContentWithAtLeastAThousandWords() {
        assertEquals(1000, VoirRequests.maxReplyWords(0))
        assertEquals(1000, VoirRequests.maxReplyWords(999))
        assertEquals(2400, VoirRequests.maxReplyWords(2400))
        assertEquals(4, VoirRequests.countWords("  Je  conteste\nl’ensemble, - ici."))
        assertEquals(0, VoirRequests.countWords(" … ? "))

        val thousand = List(1000) { "mot" }.joinToString(" ")
        assertEquals(emptyList(), reply(thousand, contentWords = 10).problems())
        assertEquals(listOf(RequestProblem.REPLY_TOO_LONG), reply("$thousand encore", contentWords = 10).problems())
        assertEquals(emptyList(), reply("$thousand encore", contentWords = 1001).problems())
        assertEquals(1001, reply("$thousand encore", contentWords = 1001).replyWords)
    }

    @Test
    fun problemsAreFoundBeforeAnythingIsSent() {
        assertEquals(
            listOf(RequestProblem.RESPONDENT_MISSING, RequestProblem.REPLY_MISSING, RequestProblem.STATEMENT_MISSING),
            reply(text = " ").copy(respondent = " ", statementOfTruth = false).problems(),
        )
        assertEquals(listOf(RequestProblem.CONTENT_ADDRESS), report().copy(content = "nimporte").problems())
        assertEquals(listOf(RequestProblem.WRONG_MISSING), report().copy(wrong = "").problems())
        listOf("example.org", "ftp://example.org", "https://exa mple.org", "javascript:alert(1)", "https://x").forEach {
            assertEquals(listOf(RequestProblem.SOURCE_INVALID), report().copy(source = it).problems(), it)
        }
        val huge = List(3000) { "anticonstitutionnellement" }.joinToString(" ")
        assertTrue(RequestProblem.MESSAGE_TOO_LARGE in reply(huge, contentWords = 3000).problems())
    }

    @Test
    fun anAnonymousReportIsSignedByASingleUseKeyAndReadableByTheTeamOnly() = runTest {
        val account = IdentityKeys.generate()
        val outbox = VoirRequestOutbox(server, team.publicKey.hex)
        val first = outbox.sendAnonymously(report(), subject = "[EXEMPLE] Erreur")
        val second = outbox.sendAnonymously(report())
        assertEquals(AnonymousDelivery.SENT, first.delivery)
        assertNotEquals(account.publicKey, first.author)
        assertNotEquals(first.author, second.author)

        // One wrap per report, for the team alone: no copy for a sender who keeps nothing.
        assertEquals(2, server.events.size)
        val wrap = server.events.first()
        assertEquals(EventKinds.GIFT_WRAP, wrap.kind)
        assertEquals(listOf(team.publicKey.hex), wrap.tagValues("p"))
        assertTrue(!wrap.content.contains("date est fausse"))

        val rumor = GiftWrap.unwrap(wrap, LocalMessagingSigner(team))
        assertEquals(first.author.hex, rumor.pubkey)
        assertEquals(report(), VoirRequests.decodeOrNull(ChatMessage.fromRumor(rumor).text))
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, LocalMessagingSigner(account)) }
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(wrap, LocalMessagingSigner(IdentityKeys.generate())) }
    }

    @Test
    fun anAnonymousReportWaitsForTheServerThenLeaves() = runTest {
        server.online = false
        val outbox = VoirRequestOutbox(server, team.publicKey.hex)
        val receipt = outbox.sendAnonymously(report())
        assertEquals(AnonymousDelivery.PENDING, receipt.delivery)
        assertEquals(1, outbox.pendingCount())
        assertEquals(0, outbox.flush())
        server.online = true
        assertEquals(1, outbox.flush())
        assertEquals(0, outbox.pendingCount())
        val rumor = GiftWrap.unwrap(server.events.single(), LocalMessagingSigner(team))
        assertEquals(receipt.messageId, rumor.id)

        // Without any server, the report waits too.
        val offline = VoirRequestOutbox(null, team.publicKey.hex)
        assertEquals(AnonymousDelivery.PENDING, offline.sendAnonymously(report()).delivery)
        assertEquals(1, offline.pendingCount())
        assertFailsWith<IllegalArgumentException> { offline.sendAnonymously(report().copy(wrong = " ")) }
    }

    @Test
    fun aReplyFromAnAccountIsAPrivateMessageToTheTeamThatOnlyTheTeamAndTheSenderRead() = runTest {
        val keys = IdentityKeys.generate()
        val service = MessagingService(LocalMessagingSigner(keys), MessageStore(JvmDatabase.inMemory()), server)
        val sent = service.send(VoirRequests.encode(reply()), listOf(team.publicKey), subject = "[EXEMPLE] Droit de réponse")
        assertEquals(2, sent.delivered)
        val forTeam = server.events.single { team.publicKey.hex in it.tagValues("p") }
        val rumor = GiftWrap.unwrap(forTeam, LocalMessagingSigner(team))
        assertEquals(keys.publicKey.hex, rumor.pubkey)
        assertEquals(reply(), VoirRequests.decodeOrNull(rumor.content))
        assertFailsWith<GiftWrapException> { GiftWrap.unwrap(forTeam, LocalMessagingSigner(IdentityKeys.generate())) }
    }

    @Test
    fun theTeamInboxIsTheExampleEditorForNow() {
        assertEquals(EditorialTrust.EXAMPLE_EDITOR, VoirRequests.TEAM_INBOX)
        assertEquals(EditorialTrust.EXAMPLE_EDITOR, VoirRequestOutbox(null).team.hex)
    }
}
