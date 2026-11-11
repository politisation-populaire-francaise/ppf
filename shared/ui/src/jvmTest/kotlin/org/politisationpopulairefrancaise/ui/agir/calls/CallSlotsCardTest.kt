package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.agir.calls.Call
import org.politisationpopulairefrancaise.core.agir.calls.CallDraft
import org.politisationpopulairefrancaise.core.agir.calls.CallType
import org.politisationpopulairefrancaise.core.agir.calls.SlotClient
import org.politisationpopulairefrancaise.core.agir.calls.SlotDraft
import org.politisationpopulairefrancaise.core.agir.calls.SlotOutcome
import org.politisationpopulairefrancaise.core.agir.calls.SlotSignupRequest
import org.politisationpopulairefrancaise.core.agir.calls.SlotWithdrawal
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The slots of a call (AP-11): sign up with a name, withdraw, and the organizer reads the names. */
@OptIn(ExperimentalTestApi::class)
class CallSlotsCardTest {
    private val organizer = IdentityKeys.generate()

    /** A server in memory: one place in the morning slot, the envelopes kept as they came. */
    private class FakeSlots : SlotClient {
        val received = mutableListOf<SignupRequestCopy>()

        data class SignupRequestCopy(val slot: String, val envelope: SignedEvent)

        override suspend fun counts(call: String): Map<String, Int> = received.groupingBy { it.slot }.eachCount()

        override suspend fun signUp(request: SlotSignupRequest): SlotOutcome {
            received += SignupRequestCopy(request.slot, request.envelope)
            return SlotOutcome.Done(request.envelope.id, received.count { it.slot == request.slot })
        }

        override suspend fun withdraw(withdrawal: SlotWithdrawal): SlotOutcome {
            received.removeAll { it.envelope.id == withdrawal.id }
            return SlotOutcome.Done(withdrawal.id, 0)
        }

        override suspend fun signups(call: String): List<SignedEvent> = received.map { it.envelope }
    }

    private fun call(): Call {
        val draft: CallDraft = CallDraft(
            type = CallType.BENEVOLES,
            title = "[EXEMPLE] Piquet devant le dépôt",
            text = "[EXEMPLE] Nous cherchons du monde pour tenir le piquet.",
            territory = "Lyon",
            fields = mapOf("ppf-mission" to "[EXEMPLE] Tenir le piquet", "ppf-people" to "4"),
            slots = listOf(SlotDraft("Matin", "14/11/2026", "6h", "9h", "1"), SlotDraft("Soir", "14/11/2026", "18h", "21h", "3")),
        )
        val event = draft.toUnsignedEvent(organizer.publicKey, NOW, "piquet-depot")
        return requireNotNull(Call.from(SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))))
    }

    @Test
    fun aPersonSignsUpWithANameTheSlotIsFullAndTheOrganizerReadsTheName() = runComposeUiTest {
        val server = FakeSlots()
        val slots = SlotsLibrary(null, server, null) { NOW }
        val call = call()
        setContent {
            PpfTheme(darkTheme = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { SlotsCard(call, slots, LocalMessagingSigner(organizer)) }
            }
        }
        onNodeWithTag(SlotTags.SIGNUP + "c1").performClick()
        onNodeWithTag(SlotTags.NAME).performTextInput("Camille")
        onNodeWithTag(SlotTags.SEND).performClick()
        waitUntil(timeoutMillis = 20_000) { runCatching { onNodeWithTag(SlotTags.RESULT).assertExists() }.isSuccess }
        onNodeWithTag(SlotTags.CLOSE).performClick()
        onNodeWithTag(SlotTags.WITHDRAW + "c1").assertExists()
        onNodeWithText("1 sur 1 places prises").assertExists()
        assertEquals(1, server.received.size)
        // The organizer decrypts the name on this device.
        onNodeWithTag(SlotTags.ORGANIZER).performClick()
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithText("Camille").assertExists() }.isSuccess }
        // Withdrawn: the place is free again.
        onNodeWithTag(SlotTags.WITHDRAW + "c1").performClick()
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(SlotTags.SIGNUP + "c1").assertExists() }.isSuccess }
        assertEquals(0, server.received.size)
    }

    @Test
    fun aFullSlotCannotBeChosen() = runComposeUiTest {
        val server = FakeSlots()
        val call = call()
        server.received += FakeSlots.SignupRequestCopy("c1", SignedEvent("a".repeat(64), "b".repeat(64), 1, 1059, emptyList(), "", "0".repeat(128)))
        setContent { PpfTheme(darkTheme = false) { SlotsCard(call, SlotsLibrary(null, server, null) { NOW }, organizer = null) } }
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithText("Complet").assertExists() }.isSuccess }
        onNodeWithTag(SlotTags.SIGNUP + "c1").assertIsNotEnabled()
        onNodeWithTag(SlotTags.ORGANIZER).assertDoesNotExist()
    }
}
