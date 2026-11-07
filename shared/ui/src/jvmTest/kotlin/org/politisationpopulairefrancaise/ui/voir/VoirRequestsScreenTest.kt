package org.politisationpopulairefrancaise.ui.voir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.messaging.Conversation
import org.politisationpopulairefrancaise.core.messaging.MessageStore
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.voir.ErrorReport
import org.politisationpopulairefrancaise.core.voir.ReplyCapacity
import org.politisationpopulairefrancaise.core.voir.ReplyRequest
import org.politisationpopulairefrancaise.core.voir.VoirRequests
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.account.AccountTags
import org.politisationpopulairefrancaise.ui.messages.ConversationTags
import org.politisationpopulairefrancaise.ui.messages.FakeRelay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Right of reply and error reports from "Voir" (VO-21, VO-22): the forms open from a fact and from a
 * person, the counter follows max(words of the content, 1000), a reply needs an account and waits on
 * the device without a server, an error report leaves anonymously by default and with the account
 * only after a warning.
 */
@OptIn(ExperimentalTestApi::class)
class VoirRequestsScreenTest {

    private val keys = IdentityKeys.generate()
    private val database = JvmDatabase.inMemory()
    private val store = MessageStore(database)
    private val team = VoirRequests.TEAM_INBOX

    private fun dependencies(account: Boolean, remote: RemoteEvents? = null) = AppDependencies.withDatabase(
        identityStore = if (account) InMemoryIdentityStore(StoredIdentity.of(keys, null)) else InMemoryIdentityStore(),
        database = database,
        editorialTrust = EditorialTrust.bundledExamples(),
        remote = remote,
    )

    private fun ComposeUiTest.has(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.sees(text: String): Boolean = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openVoir(dependencies: AppDependencies) {
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        waitForIdle()
        onNodeWithText("Voir").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { has(VoirTags.card("manifestants-journees")) }
    }

    private fun ComposeUiTest.openPerson() {
        onNodeWithTag(VoirTags.tab(1)).performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.card("dominique-fictif")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("dominique-fictif")).assertExists()
    }

    private fun ComposeUiTest.openFact() {
        openPerson()
        onNodeWithTag(VoirTags.card("affaire-dominique-marche-public")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.detail("affaire-dominique-marche-public")).assertExists()
    }

    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun aReplyFromAFactExplainsTheLawAndNeedsAnAccount() = runComposeUiTest {
        openVoir(dependencies(account = false))
        openFact()
        click(VoirTags.REPLY_OPEN)
        onNodeWithTag(VoirRequestTags.REPLY_FORM).assertExists()
        assertTrue(sees("dans les trois jours suivant sa réception"))
        assertTrue(sees("article 1-1, III"))
        // The only person the fact concerns is proposed for the mention.
        onNodeWithTag(VoirRequestTags.REPLY_RESPONDENT).assert(hasText("Dominique Fictif"))
        onNodeWithTag(VoirRequestTags.NO_ACCOUNT).assertExists()
        onNodeWithTag(VoirRequestTags.REPLY_SEND).performScrollTo().assertIsNotEnabled()
        // The draft survives the visit to "Mon espace" to create the account.
        onNodeWithTag(VoirRequestTags.REPLY_TEXT).performScrollTo().performTextInput("[EXEMPLE] Brouillon")
        click(VoirRequestTags.CREATE_ACCOUNT)
        waitUntil(timeoutMillis = 5_000) { has(AccountTags.CREATE) }
        onNodeWithText("Voir").performClick()
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPLY_TEXT).assert(hasText("[EXEMPLE] Brouillon"))
    }

    @Test
    fun aReplyFromAPersonCountsItsWordsAndWaitsOnTheDeviceWithoutAServer() = runComposeUiTest {
        openVoir(dependencies(account = true))
        openPerson()
        click(VoirTags.REPLY_OPEN)
        onNodeWithTag(VoirRequestTags.REPLY_FORM).assertExists()
        onNodeWithTag(VoirRequestTags.REPLY_RESPONDENT).assert(hasText("Dominique Fictif"))
        onNodeWithTag(VoirRequestTags.NO_ACCOUNT).assertDoesNotExist()
        onNodeWithTag(VoirRequestTags.REPLY_COUNTER).assert(hasText("0 mots sur 1000 au maximum"))

        // Over the limit, the counter says by how much.
        val tooLong = List(1001) { "mot" }.joinToString(" ")
        onNodeWithTag(VoirRequestTags.REPLY_TEXT).performScrollTo().performTextInput(tooLong)
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPLY_COUNTER).assert(hasText("1001 mots : 1 de trop (maximum 1000)"))
        onNodeWithTag(VoirRequestTags.REPLY_TEXT).performTextReplacement("[EXEMPLE] Je conteste ces faits.")
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPLY_COUNTER).assert(hasText("5 mots sur 1000 au maximum"))

        // Nothing leaves without the statement on honour.
        click(VoirRequestTags.REPLY_SEND)
        onNodeWithTag(VoirRequestTags.PROBLEMS).assertExists()
        assertTrue(sees("Cochez la déclaration"))
        click(VoirRequestTags.REPLY_REPRESENTATIVE)
        click(VoirRequestTags.REPLY_STATEMENT)
        click(VoirRequestTags.REPLY_SEND)
        waitUntil(timeoutMillis = 5_000) { has(VoirRequestTags.OUTCOME) }
        assertTrue(sees("En attente d’envoi"))

        // Stored on the device like any private message, to the team, both deliveries pending.
        val conversation = Conversation.idOf(listOf(keys.publicKey.hex, team))
        val message = store.messages(conversation).single()
        assertEquals(keys.publicKey.hex, message.author)
        assertEquals(2L, store.outgoingCount())
        val request = VoirRequests.decodeOrNull(message.text) as ReplyRequest
        assertEquals("Dominique Fictif", request.respondent)
        assertEquals(ReplyCapacity.LEGAL_REPRESENTATIVE, request.capacity)
        assertEquals("[EXEMPLE] Je conteste ces faits.", request.reply)
        assertEquals(1000, request.maxWords)
        assertTrue(request.statementOfTruth)
        assertTrue(request.content.endsWith(":dominique-fictif"))

        // The conversation with the team shows the request in plain words, not its JSON.
        click(VoirRequestTags.OPEN_MESSAGES)
        waitUntil(timeoutMillis = 5_000) { sees("Demande de droit de réponse") }
        assertTrue(!sees("ppf-voir-request"))
        onNodeWithTag(ConversationTags.PENDING + message.id.take(8), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aReplyWithAServerLeavesForTheTeam() = runComposeUiTest {
        val relay = FakeRelay()
        openVoir(dependencies(account = true, remote = relay))
        openPerson()
        click(VoirTags.REPLY_OPEN)
        onNodeWithTag(VoirRequestTags.REPLY_TEXT).performScrollTo().performTextInput("[EXEMPLE] Ma réponse.")
        click(VoirRequestTags.REPLY_STATEMENT)
        click(VoirRequestTags.REPLY_SEND)
        waitUntil(timeoutMillis = 5_000) { has(VoirRequestTags.OUTCOME) }
        assertTrue(sees("Demande envoyée à l’équipe de rédaction"))
        val wraps = relay.events.filter { it.kind == EventKinds.GIFT_WRAP }
        assertEquals(setOf(team, keys.publicKey.hex), wraps.map { it.firstTagValue("p") }.toSet())
        assertTrue(wraps.none { it.content.contains("Ma réponse") })
        assertEquals(0L, store.outgoingCount())
    }

    @Test
    fun anErrorReportWithoutAnAccountLeavesAnonymously() = runComposeUiTest {
        val relay = FakeRelay()
        openVoir(dependencies(account = false, remote = relay))
        openFact()
        click(VoirRequestTags.REPORT_LINK)
        onNodeWithTag(VoirRequestTags.REPORT_FORM).assertExists()
        onNodeWithTag(VoirRequestTags.NO_ACCOUNT).assertExists()
        onNodeWithTag(VoirRequestTags.REPORT_ANONYMOUS).assertDoesNotExist()
        onNodeWithTag(VoirRequestTags.REPORT_WRONG).performScrollTo().performTextInput("[EXEMPLE] La date est fausse.")
        onNodeWithTag(VoirRequestTags.REPORT_SOURCE).performScrollTo().performTextInput("example.org")
        click(VoirRequestTags.REPORT_SEND)
        assertTrue(sees("doit commencer par https://"))
        onNodeWithTag(VoirRequestTags.REPORT_SOURCE).performTextReplacement("https://example.org/source")
        click(VoirRequestTags.REPORT_SEND)
        waitUntil(timeoutMillis = 5_000) { has(VoirRequestTags.OUTCOME) }
        assertTrue(sees("Signalement envoyé"))
        assertTrue(sees("anonymement"))
        onNodeWithTag(VoirRequestTags.OPEN_MESSAGES).assertDoesNotExist()
        // One wrap, for the team alone; nothing kept on the device.
        val wrap = relay.events.single()
        assertEquals(listOf(team), wrap.tagValues("p"))
        assertEquals(0L, store.count())
    }

    @Test
    fun anErrorReportIsAnonymousByDefaultAndUsesTheAccountOnlyAfterAWarning() = runComposeUiTest {
        val relay = FakeRelay()
        openVoir(dependencies(account = true, remote = relay))
        onNodeWithTag(VoirTags.card("manifestants-journees")).performClick()
        waitForIdle()
        click(VoirRequestTags.REPORT_LINK)
        onNodeWithTag(VoirRequestTags.REPORT_ANONYMOUS).performScrollTo().assertIsSelected()
        click(VoirRequestTags.REPORT_ACCOUNT)
        onNodeWithTag(VoirRequestTags.REPORT_WARNING).assertExists()
        onNodeWithTag(VoirRequestTags.REPORT_WARNING_CANCEL).performClick()
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPORT_WARNING).assertDoesNotExist()
        onNodeWithTag(VoirRequestTags.REPORT_ANONYMOUS).assertIsSelected()

        click(VoirRequestTags.REPORT_ACCOUNT)
        onNodeWithTag(VoirRequestTags.REPORT_WARNING_CONFIRM).performClick()
        waitForIdle()
        onNodeWithTag(VoirRequestTags.REPORT_ACCOUNT).assertIsSelected()
        onNodeWithTag(VoirRequestTags.REPORT_WRONG).performScrollTo().performTextInput("[EXEMPLE] Le chiffre du 3 octobre est faux.")
        click(VoirRequestTags.REPORT_SEND)
        waitUntil(timeoutMillis = 5_000) { has(VoirRequestTags.OUTCOME) }
        assertTrue(sees("L’équipe pourra vous répondre"))
        val message = store.messages(Conversation.idOf(listOf(keys.publicKey.hex, team))).single()
        val report = VoirRequests.decodeOrNull(message.text) as ErrorReport
        assertEquals(false, report.anonymous)
        assertTrue(report.content.endsWith(":manifestants-journees"))
        assertEquals(2, relay.events.size)
    }

    @Test
    fun anAnonymousReportFromAnAccountLeavesNoTraceInItsMessages() = runComposeUiTest {
        val relay = FakeRelay()
        openVoir(dependencies(account = true, remote = relay))
        onNodeWithTag(VoirTags.card("manifestants-journees")).performClick()
        waitForIdle()
        click(VoirRequestTags.REPORT_LINK)
        onNodeWithTag(VoirRequestTags.REPORT_WRONG).performScrollTo().performTextInput("[EXEMPLE] Erreur de date.")
        onNodeWithTag(VoirRequestTags.REPORT_SEND).performScrollTo().assertIsEnabled()
        click(VoirRequestTags.REPORT_SEND)
        waitUntil(timeoutMillis = 5_000) { has(VoirRequestTags.OUTCOME) }
        assertEquals(listOf(team), relay.events.single().tagValues("p"))
        assertTrue(relay.events.single().pubkey != keys.publicKey.hex)
        assertEquals(0L, store.count())
    }
}
