package org.politisationpopulairefrancaise.ui.agir.timer

import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.timer.InMemoryTimerStorage
import org.politisationpopulairefrancaise.core.agir.timer.TimerCandidate
import org.politisationpopulairefrancaise.core.agir.timer.TimerDeviceData
import org.politisationpopulairefrancaise.core.agir.timer.TimerFormats
import org.politisationpopulairefrancaise.core.agir.timer.TimerInbox
import org.politisationpopulairefrancaise.core.agir.timer.TimerRecipientSource
import org.politisationpopulairefrancaise.core.agir.timer.TimerService
import org.politisationpopulairefrancaise.core.agir.timer.TimerStore
import org.politisationpopulairefrancaise.core.agir.timer.TimerTransport
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.PublicKey
import org.politisationpopulairefrancaise.core.messaging.GiftWrap
import org.politisationpopulairefrancaise.core.messaging.LocalMessagingSigner
import org.politisationpopulairefrancaise.core.messaging.Rumor
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.ui.account.LocalCanPublish
import org.politisationpopulairefrancaise.ui.account.PUBLISH_BLOCKED_TAG
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CODE = "73914"
private const val DURESS = "2580"

/**
 * Seals for real (so that the device keeps real seals) and records, instead of a server, the rumor
 * of every seal that reaches it: the test reads what left the device, the server would not.
 */
private class RecordingTransport(private val signer: LocalMessagingSigner) : TimerTransport {
    private val rumorsBySeal = ConcurrentHashMap<String, Rumor>()
    val delivered = CopyOnWriteArrayList<Rumor>()
    var online = true

    override val author: PublicKey get() = signer.publicKey

    override suspend fun seal(rumor: Rumor, recipient: PublicKey): SignedEvent =
        GiftWrap.seal(rumor, signer, recipient).also { rumorsBySeal[it.id] = rumor }

    override suspend fun deliver(seal: SignedEvent, recipient: PublicKey): PublicationResult {
        if (!online) return PublicationResult.Unavailable()
        rumorsBySeal[seal.id]?.let { delivered += it }
        return PublicationResult.Accepted(seal.id)
    }

    fun actions(): List<String?> = delivered.map { it.firstTagValue(TimerFormats.TAG_ACTION) }
}

/** The timer of « Agir » alone, with two contacts, a clock of its own and few iterations. */
private class TimerHarness(val hasServer: Boolean = true, val withAccount: Boolean = true) {
    var now = 1_800_000_000L
    val me = IdentityKeys.generate()
    val bob = IdentityKeys.generate()
    val carol = IdentityKeys.generate()
    val storage = InMemoryTimerStorage()
    val store = TimerStore(storage)
    val transport = RecordingTransport(LocalMessagingSigner(me))
    lateinit var state: TimerState

    @Composable
    fun Content(width: Int, dark: Boolean = false, canPublish: Boolean = true, blocks: Boolean = false) {
        val scope = rememberCoroutineScope()
        val created = remember {
            TimerState(
                service = TimerService(store, clock = { now }, iterations = 2),
                inbox = TimerInbox(store, clock = { now }),
                store = store,
                scope = scope,
                hasStorage = true,
                hasServer = hasServer,
                contacts = TimerRecipientSource { listOf(TimerCandidate(bob.publicKey.hex), TimerCandidate(carol.publicKey.hex)) },
                clock = { now },
            )
        }
        state = created
        LaunchedEffect(created) {
            created.setAccount(if (withAccount) me.publicKey.hex else null, if (withAccount && hasServer) transport else null)
        }
        Box(Modifier.requiredSize(width.dp, TEST_WINDOW_HEIGHT.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalCanPublish provides canPublish) {
                    if (blocks) {
                        Column {
                            TimerAlerts(created)
                            TimerRelatives(created)
                        }
                    } else {
                        TimerScreen(created, WindowClass.of(width.toFloat(), 1000f), onBack = {})
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.show(harness: TimerHarness, width: Int, dark: Boolean = false, canPublish: Boolean = true, blocks: Boolean = false) {
    setContent { harness.Content(width, dark, canPublish, blocks) }
    waitForIdle()
    if (harness.withAccount) waitUntil(timeoutMillis = 10_000) { harness.state.account != null }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.type(tag: String, text: String) {
    // Inside the scrolled screen or the dialog; a field without a scrolled parent stays where it is.
    runCatching { onNodeWithTag(tag).performScrollTo() }
    onNodeWithTag(tag).performClick()
    onNodeWithTag(tag).performTextInput(text)
    waitForIdle()
}

/** Ticks a contact, types the codes and presses « Continuer ». */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.fillSetup(harness: TimerHarness, duress: String?) {
    awaitTag(TimerTags.SETUP)
    onNodeWithTag(TimerTags.RECIPIENT + harness.bob.publicKey.hex).performScrollTo().performClick()
    type(TimerTags.CODE, CODE)
    type(TimerTags.CODE_CONFIRM, CODE)
    if (duress != null) type(TimerTags.DURESS, duress)
    onNodeWithTag(TimerTags.CONTINUE).performScrollTo().performClick()
    waitForIdle()
}

/** Arms a timer for Bob through the screen and comes back to the timer in progress. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.armThroughTheScreen(harness: TimerHarness, duress: String? = DURESS) {
    fillSetup(harness, duress)
    awaitTag(TimerTags.CONFIRM_DIALOG)
    onNodeWithTag(TimerTags.CONFIRM_ARM).performClick()
    waitUntil(timeoutMillis = 20_000) { count(TimerTags.OUTCOME) > 0 }
    onNodeWithTag(TimerTags.OUTCOME_CLOSE).performScrollTo().performClick()
    waitForIdle()
    awaitTag(TimerTags.ARMED)
}

/** Opens « Je vais bien », types [code] and validates. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.checkIn(code: String) {
    onNodeWithTag(TimerTags.OK).performScrollTo().performClick()
    awaitTag(TimerTags.CODE_DIALOG)
    type(TimerTags.CODE_INPUT, code)
    onNodeWithTag(TimerTags.CODE_SUBMIT).performClick()
    waitForIdle()
}

/** Every text shown under the node tagged [tag], in order. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.textsUnder(tag: String): List<String> =
    onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

@OptIn(ExperimentalTestApi::class)
class TimerScreenTest {

    @Test
    fun armingShowsWhoReceivesWhatFirstOnPhonesTabletsAndComputers() {
        for (width in listOf(360, 700, 1000)) {
            val harness = TimerHarness()
            runComposeUiTest {
                show(harness, width, dark = width == 700)
                fillSetup(harness, DURESS)
                awaitTag(TimerTags.CONFIRM_DIALOG)
                onNodeWithText("même si votre téléphone est éteint, perdu ou saisi", substring = true).assertExists()
                assertTrue(harness.transport.delivered.isEmpty(), "nothing leaves before the confirmation at $width dp")

                // « Annuler » first: nothing is armed.
                onNodeWithTag(TimerTags.CONFIRM_CANCEL).performClick()
                waitForIdle()
                assertEquals(0, count(TimerTags.CONFIRM_DIALOG))
                assertNull(harness.storage.value?.let { TimerDeviceData.decode(it).armed })

                onNodeWithTag(TimerTags.CONTINUE).performScrollTo().performClick()
                awaitTag(TimerTags.CONFIRM_DIALOG)
                onNodeWithTag(TimerTags.CONFIRM_ARM).performClick()
                waitUntil(timeoutMillis = 20_000) { count(TimerTags.OUTCOME) > 0 }
                onNodeWithText("Minuteur armé").assertExists()
                assertEquals(listOf(TimerFormats.ACTION_DEPOSIT), harness.transport.actions())
                // Never the codes on the device.
                assertFalse(harness.storage.value!!.contains("\"$CODE\""))
                assertFalse(harness.storage.value!!.contains("\"$DURESS\""))

                onNodeWithTag(TimerTags.OUTCOME_CLOSE).performScrollTo().performClick()
                awaitTag(TimerTags.ARMED)
                onNodeWithTag(TimerTags.COUNTDOWN).assertExists()
            }
        }
    }

    @Test
    fun aWrongCodeIsRefusedAndTheRightCodeSendsTheCancellations() {
        val harness = TimerHarness()
        runComposeUiTest {
            show(harness, 700)
            armThroughTheScreen(harness)
            checkIn("1357")
            waitUntil(timeoutMillis = 20_000) { count(TimerTags.CODE_MESSAGE) > 0 }
            onNodeWithText("Code incorrect.").assertExists()
            assertEquals(listOf(TimerFormats.ACTION_DEPOSIT), harness.transport.actions())

            type(TimerTags.CODE_INPUT, CODE)
            onNodeWithTag(TimerTags.CODE_SUBMIT).performClick()
            waitUntil(timeoutMillis = 20_000) { count(TimerTags.OUTCOME) > 0 }
            onNodeWithText("Minuteur annulé").assertExists()
            waitUntil(timeoutMillis = 20_000) { TimerFormats.ACTION_CANCEL in harness.transport.actions() }
            assertEquals(listOf(TimerFormats.ACTION_DEPOSIT, TimerFormats.ACTION_CANCEL), harness.transport.actions())
        }
    }

    @Test
    fun theDuressCodeShowsExactlyTheSameScreenAndSendsNothing() {
        fun outcomeAfter(code: String): Pair<List<String>, List<String?>> {
            val harness = TimerHarness()
            var shown = emptyList<String>()
            runComposeUiTest {
                show(harness, 360)
                armThroughTheScreen(harness, DURESS)
                checkIn(code)
                waitUntil(timeoutMillis = 20_000) { count(TimerTags.OUTCOME) > 0 }
                waitForIdle()
                shown = textsUnder(TimerTags.OUTCOME)
                if (code == CODE) waitUntil(timeoutMillis = 20_000) { TimerFormats.ACTION_CANCEL in harness.transport.actions() }
                waitUntil(timeoutMillis = 20_000) { !harness.state.busy }
                // Nothing waits on the device afterwards (with the duress code, no cancellation was ever prepared).
                waitUntil(timeoutMillis = 20_000) { runBlocking { harness.store.read().pending.isEmpty() } }
                waitForIdle()

                // Afterwards, the device shows no timer in progress.
                onNodeWithTag(TimerTags.OUTCOME_CLOSE).performScrollTo().performClick()
                waitForIdle()
                awaitTag(TimerTags.SETUP)
                assertEquals(0, count(TimerTags.ARMED))
            }
            return shown to harness.transport.actions()
        }

        val (real, realSent) = outcomeAfter(CODE)
        val (duress, duressSent) = outcomeAfter(DURESS)
        assertTrue(real.contains("Minuteur annulé"), real.toString())
        assertEquals(real, duress)
        assertEquals(listOf(TimerFormats.ACTION_DEPOSIT, TimerFormats.ACTION_CANCEL), realSent)
        assertEquals(listOf(TimerFormats.ACTION_DEPOSIT), duressSent)
    }

    @Test
    fun repousserWithTheRightCodeSendsAReport() {
        val harness = TimerHarness()
        runComposeUiTest {
            show(harness, 1000)
            armThroughTheScreen(harness)
            onNodeWithTag(TimerTags.POSTPONE).performScrollTo().performClick()
            awaitTag(TimerTags.CODE_DIALOG)
            onNodeWithTag(TimerTags.PRESET + 4).performClick()
            type(TimerTags.CODE_INPUT, CODE)
            onNodeWithTag(TimerTags.CODE_SUBMIT).performClick()
            waitUntil(timeoutMillis = 20_000) { count(TimerTags.OUTCOME) > 0 }
            onNodeWithText("Minuteur repoussé").assertExists()
            waitUntil(timeoutMillis = 20_000) { TimerFormats.ACTION_REPORT in harness.transport.actions() }
        }
    }

    @Test
    fun aRecipientSeesTheDiscreetLineBeforeTheDeadlineAndTheAlertAfter() {
        val harness = TimerHarness()
        val id = IdentityKeys.generate().publicKey.hex
        val secret = IdentityKeys.generate().publicKey.hex
        val deposit = TimerFormats.deposit(
            harness.bob.publicKey.hex, harness.me.publicKey.hex, id, harness.now, harness.now + 3_600,
            TimerFormats.hashOf(secret)!!, "[EXEMPLE] Appelez ma sœur.", "[EXEMPLE] Place de la Nation",
        )
        runBlocking { TimerInbox(harness.store, clock = { harness.now }).accept(deposit, harness.me.publicKey.hex) }
        runComposeUiTest {
            show(harness, 360, blocks = true)
            awaitTag(TimerTags.RELATIVE + id)
            onNodeWithText("vous a désigné(e) pour un minuteur jusqu’à", substring = true).assertExists()
            assertEquals(0, count(TimerTags.ALERT + id))
            // Before the deadline, nothing of the alert itself.
            assertTrue(onAllNodesWithText("Appelez ma sœur", substring = true).fetchSemanticsNodes().isEmpty())

            harness.now += 3_601
            runOnIdle { harness.state.tick() }
            awaitTag(TimerTags.ALERT + id)
            onNodeWithText("Appelez ma sœur", substring = true).assertExists()
            onNodeWithText("Place de la Nation", substring = true).assertExists()
            onNodeWithText("une annulation a pu ne pas encore arriver", substring = true).assertExists()
            assertEquals(0, count(TimerTags.RELATIVE + id))

            // Hiding it asks first, then it does not come back.
            onNodeWithTag(TimerTags.ALERT_DISMISS + id).performClick()
            awaitTag(TimerTags.DISMISS_CONFIRM)
            onNodeWithTag(TimerTags.DISMISS_CONFIRM).performClick()
            waitUntil(timeoutMillis = 10_000) { count(TimerTags.ALERT + id) == 0 }
        }
    }

    @Test
    fun withoutServerOrAccountTheScreenExplainsAndOffersNothing() {
        for ((server, account) in listOf(false to true, true to false)) {
            val harness = TimerHarness(hasServer = server, withAccount = account)
            runComposeUiTest {
                show(harness, 360)
                awaitTag(TimerTags.UNAVAILABLE)
                assertEquals(0, count(TimerTags.SETUP))
            }
        }
    }

    @Test
    fun aCompromisedAccountCannotArm() {
        val harness = TimerHarness()
        runComposeUiTest {
            show(harness, 1000, canPublish = false)
            awaitTag(TimerTags.SETUP)
            onNodeWithTag(PUBLISH_BLOCKED_TAG).assertExists()
            onNodeWithTag(TimerTags.CONTINUE).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun theDeadlineChoices() {
        val now = 1_800_000_000L // 15 January 2027, 09:00 in Paris
        assertEquals(now + 2 * 3_600, deadlineFor(2, false, false, "", "", now, now))
        val evening = deadlineFor(null, true, false, "", "", now, now)!!
        assertEquals("23:59", clockText(evening))
        // A precise time already past today is tomorrow.
        val early = deadlineFor(null, false, true, "8", "30", now, now)!!
        assertEquals("08:30", clockText(early))
        assertTrue(early > now)
        assertNull(deadlineFor(null, false, true, "25", "", now, now))
        assertTrue(isValidDeadline(now + 3_600, now))
        assertFalse(isValidDeadline(now + 60, now))
        assertFalse(isValidDeadline(now + 3 * 24 * 3_600, now))
    }
}
