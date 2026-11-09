package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.agir.AgirFormats
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallFields
import org.politisationpopulairefrancaise.core.agir.calls.CallStatus
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.publication.HeldState
import org.politisationpopulairefrancaise.core.publication.HeldStatus
import org.politisationpopulairefrancaise.ui.account.PUBLISH_BLOCKED_TAG
import org.politisationpopulairefrancaise.ui.content.OwnDemandState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The form to launch a call: the type first, the fields of the type, the rules, close calls, and the ways a sending ends. */
@OptIn(ExperimentalTestApi::class)
class CallComposerTest {
    private val keys = IdentityKeys.generate()

    private fun ComposeUiTest.type(field: String, text: String) {
        onNodeWithTag(CallComposerTags.FIELD + field).performScrollTo().performTextInput(text)
    }

    private fun ComposeUiTest.date(tag: String, date: String, time: String) {
        onNodeWithTag(CallComposerTags.DATE + tag).performScrollTo().performTextInput(date)
        if (time.isNotEmpty()) onNodeWithTag(CallComposerTags.TIME + tag).performScrollTo().performTextInput(time)
    }

    /** Opens the form and chooses [type]. */
    private fun ComposeUiTest.launch(type: CallType) {
        onNodeWithTag(CallTags.LAUNCH).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.TYPE + type.tagValue).performScrollTo().performClick()
        waitForIdle()
    }

    /** A complete demonstration: date, place, organisers, declaration, the charter. */
    private fun ComposeUiTest.fillDemonstration() {
        launch(CallType.MANIFESTER)
        type("title", "[EXEMPLE] Rassemblement pour la piscine municipale")
        type("text", "[EXEMPLE] Rassemblons-nous pacifiquement pour demander la réouverture de la piscine.")
        type("territory", "Grenoble")
        date(AgirFormats.TAG_START, "28/11/2026", "11h")
        type(AgirFormats.TAG_LOCATION, "Devant la piscine fictive des Alpes")
        type(CallFields.ORGANIZER, "[EXEMPLE] Collectif fictif des nageurs")
        onNodeWithTag(CallComposerTags.CHOICE + AgirFormats.TAG_DECLARATION + ":en-cours").performScrollTo().performClick()
        onNodeWithTag(CallComposerTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun theTypeComesFirstAndFundraisingIsReservedToVerifiedOrganisations() = runComposeUiTest {
        var create = 0
        showCalls(onCreateAccount = { create++ })
        onNodeWithTag(CallTags.LAUNCH).performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.SCREEN).assertExists()
        onNodeWithTag(CallComposerTags.TYPE + "collecte").performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallComposerTags.TYPE + "se-reunir").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.TIPS).assertExists()
        onNodeWithTag(CallComposerTags.FIELD + CallFields.ACCESSIBILITY).performScrollTo().assertExists()
        // Without an account the form can be written, not sent.
        onNodeWithTag(CallComposerTags.SEND).assertDoesNotExist()
        onNodeWithTag(CallComposerTags.NEED_ACCOUNT).performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.CREATE_ACCOUNT).performClick()
        assertEquals(1, create)
        // Changing the type keeps what was typed.
        onNodeWithTag(CallComposerTags.FIELD + "title").performScrollTo().performTextInput("Un titre gardé")
        onNodeWithTag(CallComposerTags.CHANGE_TYPE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.TYPE + "petition").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.FIELD + CallFields.ACCESSIBILITY).assertDoesNotExist()
        onNodeWithTag(CallComposerTags.CHOICE + CallFields.PLATFORM + ":senat").performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.CLOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.WALL).assertExists()
    }

    @Test
    fun aCompromisedAccountCanWriteButNotSendAndIsToldWhy() = runComposeUiTest {
        val server = CallServer(held = true)
        showCalls(keys = keys, remote = server, canPublish = false)
        fillDemonstration()
        // Everything is filled in, but a compromised account no longer publishes (S-146).
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(PUBLISH_BLOCKED_TAG).performScrollTo().assertExists()
        onNodeWithText("compromis", substring = true).assertExists()
        assertTrue(server.received.isEmpty())
    }

    @Test
    fun theRulesOfTheTypeBlockTheSendingAndSayWhy() = runComposeUiTest {
        showCalls(keys = keys)
        launch(CallType.PETITION)
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallComposerTags.ISSUE + "missing_field:r").performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.ISSUE + "missing_field:ppf-platform").assertExists()

        type(AgirFormats.TAG_LINK, "http://example.org/petition")
        waitForIdle()
        onNodeWithTag(CallComposerTags.FIELD_ERROR + AgirFormats.TAG_LINK).performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.FIELD + AgirFormats.TAG_LINK).performTextReplacement("https://example.org/petition")
        type("title", "[EXEMPLE] Signer pour un passage piéton devant l'école")
        type("text", "[EXEMPLE] Les enfants traversent une route très passante sans passage protégé.")
        type("territory", "Toulouse")
        type(AgirFormats.TAG_GOAL, "300")
        date(AgirFormats.TAG_DEADLINE, "31/01/2027", "")
        onNodeWithTag(CallComposerTags.CHOICE + CallFields.PLATFORM + ":autre").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallComposerTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsEnabled()

        // Personal data, a past date: blocked again, and said under the field.
        onNodeWithTag(CallComposerTags.FIELD + "text").performScrollTo().performTextInput(" Écrivez-moi : prenom.nom@exemple.fr")
        waitForIdle()
        onNodeWithTag(CallComposerTags.FIELD_ERROR + "text").performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallComposerTags.FIELD + "text").performTextReplacement("[EXEMPLE] Les enfants traversent une route très passante.")
        onNodeWithTag(CallComposerTags.DATE + AgirFormats.TAG_DEADLINE).performScrollTo().performTextReplacement("01/11/2026")
        waitForIdle()
        onNodeWithTag(CallComposerTags.ISSUE + "past_date:ppf-deadline").performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aCallHeldForReviewIsSignedStoredAndFollowedInMyCalls() = runComposeUiTest {
        val server = CallServer(held = true)
        val harness = showCalls(keys = keys, remote = server)
        fillDemonstration()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.composer.outcome != null }
        onNodeWithTag(CallComposerTags.RESULT + "sent_for_review").assertExists()

        val sent = server.received.single()
        assertTrue(sent.verify())
        assertEquals(keys.publicKey.hex, sent.pubkey)
        val call = assertNotNull(Call.from(sent))
        assertEquals(CallType.MANIFESTER, call.type)
        assertEquals("en-cours", call.event.firstTagValue(AgirFormats.TAG_DECLARATION))
        assertEquals("Grenoble", call.territory)
        assertEquals(CallStatus.OPEN, call.status)
        assertEquals("1.0", call.event.firstTagValue(AgirFormats.TAG_CHARTER))

        onNodeWithTag(CallComposerTags.RESULT_BACK).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.TAB + "mine").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.singleOrNull()?.state == OwnDemandState.IN_REVIEW }
        onNodeWithTag(CallTags.OWN_STATE + "in_review", useUnmergedTree = true).assertExists()
        // Not on the wall of everyone while it waits.
        assertTrue(harness.calls.calls.none { it.slug == call.slug })

        // The team publishes it: the next refresh says so.
        server.decisions[sent.id] = HeldState(sent.id, HeldStatus.APPROVED, sent.createdAt, decidedAt = sent.createdAt + 60)
        harness.calls.refresh()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.single().state == OwnDemandState.PUBLISHED }
        server.decisions[sent.id] = HeldState(sent.id, HeldStatus.REJECTED, sent.createdAt, reason = "[EXEMPLE] Doublon d'un appel existant.")
        harness.calls.refresh()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.single().state == OwnDemandState.NOT_PUBLISHED }
        assertEquals("[EXEMPLE] Doublon d'un appel existant.", harness.calls.myCalls.single().reason)
    }

    @Test
    fun aCallPublishedAtOnceGoesOnTheWallAndItsAuthorClosesItWithAReport() = runComposeUiTest {
        val server = CallServer(held = false)
        val harness = showCalls(keys = keys, remote = server)
        fillDemonstration()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.composer.outcome != null }
        onNodeWithTag(CallComposerTags.RESULT + "published").assertExists()
        onNodeWithTag(CallComposerTags.RESULT_BACK).performClick()
        waitForIdle()
        val slug = assertNotNull(Call.from(server.received.single())).slug
        waitUntil(timeoutMillis = 5_000) { harness.calls.calls.any { it.slug == slug } }
        onNodeWithTag(CallTags.CARD + slug).performScrollTo().performClick()
        waitForIdle()

        // AP-09: the author closes it with its report, the safe choice first.
        onNodeWithTag(CallTags.CLOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.CLOSE_DIALOG).assertExists()
        onNodeWithTag(CallTags.CLOSE_KEEP).performClick()
        waitForIdle()
        onNodeWithTag(CallTags.CLOSE_DIALOG).assertDoesNotExist()
        onNodeWithTag(CallTags.CLOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.CLOSE_REPORT).performTextInput("[EXEMPLE] 150 personnes sont venues ; la piscine rouvre en mars.")
        onNodeWithTag(CallTags.CLOSE_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.calls.firstOrNull { it.slug == slug }?.status == CallStatus.CLOSED }
        onNodeWithTag(CallTags.CLOSE_OUTCOME).performScrollTo().assertExists()
        onNodeWithTag(CallTags.REPORT).performScrollTo().assertExists()
        onNodeWithTag(CallTags.CLOSE).assertDoesNotExist()
        val closing = server.received.last()
        assertEquals("clos", closing.firstTagValue(AgirFormats.TAG_CALL_STATUS))
        assertEquals(slug, closing.firstTagValue("d"))
    }

    @Test
    fun onlyAPublishedCallIsClosedAndItsClosingIsPublishedAtOnce() = runComposeUiTest {
        // New calls are reviewed; closings are published at once (decision of the owner, 2026-11-07).
        val server = CallServer(held = true)
        val harness = showCalls(keys = keys, remote = server)
        fillDemonstration()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.composer.outcome != null }
        onNodeWithTag(CallComposerTags.RESULT_BACK).performClick()
        waitForIdle()
        val sent = server.received.single()
        val slug = assertNotNull(Call.from(sent)).slug
        onNodeWithTag(CallTags.TAB + "mine").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.singleOrNull()?.state == OwnDemandState.IN_REVIEW }
        onNodeWithTag(CallTags.CARD + slug).performScrollTo().performClick()
        waitForIdle()
        // Still in review: nothing to close yet.
        onNodeWithTag(CallTags.DETAIL).assertExists()
        onNodeWithTag(CallTags.CLOSE).assertDoesNotExist()

        server.decisions[sent.id] = HeldState(sent.id, HeldStatus.APPROVED, sent.createdAt, decidedAt = sent.createdAt + 60)
        harness.calls.refresh()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.single().state == OwnDemandState.PUBLISHED }
        onNodeWithTag(CallTags.CLOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.CLOSE_REPORT).performTextInput("[EXEMPLE] La piscine rouvre en mars.")
        onNodeWithTag(CallTags.CLOSE_CONFIRM).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.single().call.status == CallStatus.CLOSED }
        onNodeWithText("L’appel est clos et son bilan publié.").performScrollTo().assertExists()
        assertEquals(OwnDemandState.PUBLISHED, harness.calls.myCalls.single().state)
        assertTrue(harness.calls.calls.any { it.slug == slug && it.status == CallStatus.CLOSED })
        val closing = server.received.last()
        assertTrue(server.published.events.any { it.id == closing.id })
        // Nothing but the status and the report changed.
        val before = sent.tags.filter { it[0] != AgirFormats.TAG_CALL_STATUS }
        val after = closing.tags.filter { it[0] != AgirFormats.TAG_CALL_STATUS && it[0] != AgirFormats.TAG_REPORT }
        assertEquals(before, after)
        assertEquals(sent.content, closing.content)
    }

    @Test
    fun withoutServerTheCallWaitsOnTheDevice() = runComposeUiTest {
        val harness = showCalls(keys = keys)
        fillDemonstration()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.composer.outcome != null }
        onNodeWithTag(CallComposerTags.RESULT + "waiting_for_server").assertExists()
        assertEquals(1, harness.store.pending().size)
        waitUntil(timeoutMillis = 5_000) { harness.calls.myCalls.singleOrNull()?.state == OwnDemandState.WAITING }
    }

    @Test
    fun closeCallsAreProposedBeforeSendingAndOpenOnRequest() = runComposeUiTest {
        val harness = showCalls(keys = keys)
        launch(CallType.PETITION)
        onNodeWithTag(CallComposerTags.SIMILAR).assertDoesNotExist()
        type("title", "Garder le bureau de poste du village")
        type("territory", "Creuse")
        waitForIdle()
        onNodeWithTag(CallComposerTags.SIMILAR).performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.SIMILAR_OPEN + Examples.PETITION).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.SCREEN).assertDoesNotExist()
        assertEquals(Examples.PETITION, harness.calls.selected?.slug)
        onNodeWithTag(CallTags.DETAIL).assertExists()
        // The draft is kept for later.
        assertEquals("Garder le bureau de poste du village", harness.calls.composer.draft.title)
    }

    @Test
    fun aFundraisingNeedsAVerifiedOrganisationAndThePledge() = runComposeUiTest {
        val server = CallServer(held = false)
        val harness = showCalls(keys = keys, remote = server, isVerifiedOrganisation = { it == keys.publicKey.hex })
        launch(CallType.COLLECTE)
        onNodeWithTag(CallComposerTags.FUNDRAISING_RULES).assertExists()
        type("title", "[EXEMPLE] Collecte pour les colis alimentaires de l'hiver")
        type("text", "[EXEMPLE] Notre association fictive prépare des colis pour les familles du quartier.")
        type("territory", "Lille")
        type(AgirFormats.TAG_GOAL, "2000")
        date(AgirFormats.TAG_DEADLINE, "20/12/2026", "")
        type(AgirFormats.TAG_LINK, "https://example.org/collecte-hiver")
        type(CallFields.BENEFICIARY, "[EXEMPLE] Association fictive d'entraide")
        type(CallFields.USE, "Acheter des denrées et des produits d'hygiène")
        onNodeWithTag(CallComposerTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.ISSUE + "fundraising_pledge:pledge").performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CallComposerTags.PLEDGE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.calls.composer.outcome != null }
        assertEquals("collecte", server.received.single().firstTagValue(AgirFormats.TAG_CALL_TYPE))
    }

    @Test
    fun aCallForWitnessesExplainsTheReviewAndTheSecureChannel() = runComposeUiTest {
        showCalls(keys = keys)
        launch(CallType.TEMOINS)
        onNodeWithTag(CallComposerTags.WITNESSES_RULES).assertExists()
        onNodeWithTag(CallComposerTags.DATE + AgirFormats.TAG_START).performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.DATE + AgirFormats.TAG_END).performScrollTo().assertExists()
        onNodeWithTag(CallComposerTags.FIELD + CallFields.RECIPIENT).performScrollTo().assertExists()
        // Facts are in the past: the end must come after the start.
        date(AgirFormats.TAG_START, "31/10/2026", "17:00")
        date(AgirFormats.TAG_END, "31/10/2026", "16:00")
        waitForIdle()
        onNodeWithTag(CallComposerTags.ISSUE + "end_before_start:end").performScrollTo().assertExists()
    }

    @Test
    fun theFormFitsAPhone() = runComposeUiTest {
        showCalls(width = 360, height = 700, keys = keys)
        launch(CallType.BENEVOLES)
        onNodeWithTag(CallComposerTags.FLAG + CallFields.REMOTE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallComposerTags.ISSUE + "missing_field:location|ppf-remote").assertDoesNotExist()
        onNodeWithTag(CallComposerTags.SEND).performScrollTo().assertIsNotEnabled()
    }
}
