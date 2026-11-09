package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.manifestation.InMemoryRallyPointStorage
import org.politisationpopulairefrancaise.core.agir.manifestation.RallyPoint
import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.device.deviceErasable
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecuritySettings
import org.politisationpopulairefrancaise.core.agir.security.InMemoryDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.manifestation.LocalRallyPointStorage
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationScreen
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.agir.manifestation.ProvideHelp
import org.politisationpopulairefrancaise.ui.agir.manifestation.rememberHelpState
import org.politisationpopulairefrancaise.ui.agir.organisations.rememberOrganisationsLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A part of the quick erase that records it (and may fail), and the closing of the application. */
private class RecordingEraser(var fail: Boolean = false) {
    var erased = 0
    var closed = 0

    val part = deviceErasable("test-probe") {
        if (fail) error("[EXEMPLE] erase refused")
        erased++
    }
}

/** A launcher of the tests: the icon switched in memory. */
private class FakeIcons : AppIconSwitcher {
    override var isDiscreet: Boolean = false
        private set

    override fun setDiscreet(discreet: Boolean) {
        isDiscreet = discreet
    }
}

/** A server that records everything: the security of the device must never reach it. */
private class WatchingServer : RemoteEvents {
    val seen = mutableListOf<String>()

    override suspend fun publish(event: SignedEvent): PublicationResult {
        seen += event.toString()
        return PublicationResult.Accepted(event.id)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult {
        seen += filter.toString()
        return FetchResult.Events(emptyList())
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
        seen += "$tags $filter"
        return FetchResult.Events(emptyList())
    }
}

private const val PIN = "471905"

/** The parts the shell registers for the quick erase. */
private object DeviceSecurityTestIds {
    val shell = listOf("core-identity", "agir-rally-point", "agir-minuteur", "agir-data", "test-probe")
}

/** The whole application with the security of the device, its storage and eraser kept by the test. */
private class SecurityHarness(
    val storage: InMemoryDeviceSecurityStorage = InMemoryDeviceSecurityStorage(),
    val identity: InMemoryIdentityStore = InMemoryIdentityStore(StoredIdentity.of(IdentityKeys.generate(), null)),
    val eraser: RecordingEraser? = RecordingEraser(),
    val server: WatchingServer = WatchingServer(),
    val rally: InMemoryRallyPointStorage = InMemoryRallyPointStorage(),
) {
    val uris = RecordingUriHandler()
    val icons = FakeIcons()
    val database = JvmDatabase.inMemory()
    val dependencies: AppDependencies = AppDependencies.withDatabase(
        identity, database, editorialTrust = AgirFixtures.trust, remote = server,
        securityStorage = storage, closeApplication = eraser?.let { recorder -> { recorder.closed += 1 } },
    ).also { built -> eraser?.let { built.erasables.register(it.part) } }

    /** A lock already on, with few iterations so that the tests stay fast (the record keeps its own count). */
    fun lockOn(failedAttempts: Int = 0): SecurityHarness {
        AppLock(storage, iterations = 10).enable(PIN, PIN)
        if (failedAttempts > 0) {
            val settings = DeviceSecuritySettings.decode(storage.value)
            storage.save(settings.copy(failedAttempts = failedAttempts, lastFailureEpochSeconds = currentEpochSeconds()).encode())
        }
        return this
    }

    fun settings(): DeviceSecuritySettings = DeviceSecuritySettings.decode(storage.value)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.showApp(harness: SecurityHarness, width: Int) {
    setContent {
        CompositionLocalProvider(LocalUriHandler provides harness.uris, LocalRallyPointStorage provides harness.rally, LocalAppIconSwitcher provides harness.icons) {
            Box(Modifier.requiredSize(width.dp, 800.dp)) { PpfApp(harness.dependencies) }
        }
    }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openMySpace() {
    awaitTag(ShellTags.navItem(Space.MON_ESPACE))
    onNodeWithTag(ShellTags.navItem(Space.MON_ESPACE)).performClick()
    waitForIdle()
    awaitTag(SecurityTags.CARD)
    onNodeWithTag(SecurityTags.CARD).performScrollTo()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.awaitText(text: String) =
    waitUntil(timeoutMillis = 20_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.typeCode(tag: String, code: String) {
    onNodeWithTag(tag).performClick()
    onNodeWithTag(tag).performTextInput(code)
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
class DeviceSecurityTest {

    @Test
    fun theLockIsOffByDefaultAndTurnedOnFromMySpaceOnPhonesTabletsAndComputers() {
        for (width in listOf(360, 700, 1000)) {
            val harness = SecurityHarness()
            runComposeUiTest {
                showApp(harness, width)
                assertEquals(0, count(SecurityTags.LOCK_SCREEN), "off by default at $width dp")
                openMySpace()
                onNodeWithText("Code seul", substring = true).assertExists()
                // A computer cannot hide its content from the system: the setting is not offered there.
                assertEquals(0, count(SecurityTags.HIDE_TOGGLE))

                onNodeWithTag(SecurityTags.LOCK_TOGGLE).performScrollTo().performClick()
                waitForIdle()
                // A trivial code is refused and said in words.
                typeCode(SecurityTags.PIN_NEW, "123456")
                typeCode(SecurityTags.PIN_CONFIRM, "123456")
                onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
                waitForIdle()
                onNodeWithText("trop facile", substring = true).assertExists()
                assertNull(harness.storage.value)
            }
        }
    }

    @Test
    fun aCodeChosenInMySpaceLocksTheNextStartAndOnlyTheRightCodeOpens() {
        val harness = SecurityHarness()
        runComposeUiTest {
            showApp(harness, 360)
            openMySpace()
            onNodeWithTag(SecurityTags.LOCK_TOGGLE).performScrollTo().performClick()
            waitForIdle()
            typeCode(SecurityTags.PIN_NEW, PIN)
            typeCode(SecurityTags.PIN_CONFIRM, PIN)
            onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
            waitUntil(timeoutMillis = 20_000) { harness.settings().lockEnabled }
            waitForIdle()
            // Still open after choosing the code; the delays are offered, « Immédiatement » first.
            assertEquals(0, count(SecurityTags.LOCK_SCREEN))
            onNodeWithTag(SecurityTags.AFTER + 300).performScrollTo().performClick()
            waitForIdle()
            assertEquals(300, harness.settings().lockAfterSeconds)
        }
        // Never the code itself in what the device keeps.
        assertFalse(harness.storage.value!!.contains(PIN))

        runComposeUiTest {
            showApp(harness, 360)
            awaitTag(SecurityTags.LOCK_SCREEN)
            // The interface underneath is hidden from the screen reader too.
            assertEquals(0, count(ShellTags.CONTENT))
            onNodeWithText("PPF est verrouillé").assertExists()
            typeCode(SecurityTags.UNLOCK_PIN, "000001")
            onNodeWithTag(SecurityTags.UNLOCK).performClick()
            awaitText("Code incorrect.")
            assertEquals(1, harness.settings().failedAttempts)
            typeCode(SecurityTags.UNLOCK_PIN, PIN)
            onNodeWithTag(SecurityTags.UNLOCK_PIN).performImeAction()
            waitUntil(timeoutMillis = 20_000) { count(SecurityTags.LOCK_SCREEN) == 0 }
            awaitTag(ShellTags.CONTENT)
            assertEquals(0, harness.settings().failedAttempts)

            // « Verrouiller maintenant » locks at once.
            openMySpace()
            onNodeWithTag(SecurityTags.LOCK_NOW).performScrollTo().performClick()
            waitForIdle()
            awaitTag(SecurityTags.LOCK_SCREEN)
        }
        // Nothing of the lock ever reached the server.
        assertTrue(harness.server.seen.none { it.contains(PIN) || it.contains("pbkdf2") })
    }

    @Test
    fun afterTooManyWrongCodesTheLockScreenWaitsAndStillOffersTheEmergencyNumbers() = runComposeUiTest {
        val harness = SecurityHarness().lockOn(failedAttempts = 5)
        showApp(harness, 360)
        awaitTag(SecurityTags.LOCK_SCREEN)
        awaitText("Trop d’essais. Réessayez dans")
        typeCode(SecurityTags.UNLOCK_PIN, PIN)
        onNodeWithTag(SecurityTags.UNLOCK).assertIsNotEnabled()
        // An emergency never waits for a code.
        onNodeWithTag(ManifestationTags.NUMBER + "samu").performScrollTo().performClick()
        onNodeWithTag(ManifestationTags.NUMBER + "europe").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("tel:15", "tel:112"), harness.uris.opened)
        assertEquals(0, count(ManifestationTags.NUMBER + "police"), "only the 15 and the 112 on the lock screen")
    }

    @Test
    fun theQuickEraseRemindsThePhraseCanBeCancelledAndThenErasesEverything() = runComposeUiTest {
        val harness = SecurityHarness().lockOn()
        harness.rally.save(RallyPoint.of("[EXEMPLE] Devant la poste", "")!!.encode())
        val calls = CallResponseStore(harness.database)
        calls.respond("31924:cle:appel-exemple")
        showApp(harness, 1000)
        awaitTag(SecurityTags.LOCK_SCREEN)
        typeCode(SecurityTags.UNLOCK_PIN, PIN)
        onNodeWithTag(SecurityTags.UNLOCK).performClick()
        waitUntil(timeoutMillis = 20_000) { count(SecurityTags.LOCK_SCREEN) == 0 }
        openMySpace()

        onNodeWithTag(SecurityTags.WIPE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("phrase de récupération est le seul moyen", substring = true).assertExists()
        onNodeWithText("Rien n’est effacé sur le serveur", substring = true).assertExists()
        // « Annuler » erases nothing.
        onNodeWithTag(SecurityTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertEquals(0, harness.eraser!!.erased)
        assertTrue(harness.identity.snapshot != null)

        onNodeWithTag(SecurityTags.WIPE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitUntil(timeoutMillis = 20_000) { harness.eraser.erased == 1 }
        waitForIdle()
        awaitTag(SecurityTags.WIPED)
        assertNull(harness.identity.snapshot, "the keys are erased")
        assertNull(harness.storage.value, "the lock and its settings are erased")
        assertNull(harness.rally.load(), "the rally point is erased")
        assertEquals(0, calls.count(), "the data of Agir are erased")
        assertTrue(DeviceSecurityTestIds.shell.all { it in harness.dependencies.erasables.ids }, harness.dependencies.erasables.ids.toString())
        assertEquals(0, count(ShellTags.CONTENT), "nothing of the interface is shown any more")
        // How to come back: the recovery phrase; the application closes when the user has read it.
        onNodeWithText("J’ai déjà un compte", substring = true).assertExists()
        assertEquals(0, harness.eraser.closed)
        onNodeWithTag(SecurityTags.WIPE_CLOSE).performClick()
        waitForIdle()
        assertEquals(1, harness.eraser.closed)
    }

    @Test
    fun aForgottenCodeLeadsOnlyToTheQuickErase() = runComposeUiTest {
        val harness = SecurityHarness().lockOn()
        showApp(harness, 700)
        awaitTag(SecurityTags.LOCK_SCREEN)
        onNodeWithTag(SecurityTags.FORGOT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("la seule façon de rouvrir PPF", substring = true).assertExists()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitUntil(timeoutMillis = 20_000) { harness.eraser!!.erased == 1 }
        awaitTag(SecurityTags.WIPED)
        assertNull(harness.identity.snapshot)
    }

    @Test
    fun whereTheApplicationCannotCloseItselfItSaysSo() = runComposeUiTest {
        val harness = SecurityHarness(eraser = null)
        showApp(harness, 360)
        openMySpace()
        onNodeWithTag(SecurityTags.WIPE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        awaitTag(SecurityTags.WIPED)
        awaitText("Vous pouvez fermer l’application.")
        assertNull(harness.identity.snapshot)
    }

    @Test
    fun aFailedEraseSaysSoAndCanBeTriedAgain() = runComposeUiTest {
        val eraser = RecordingEraser(fail = true)
        val harness = SecurityHarness(eraser = eraser)
        showApp(harness, 360)
        openMySpace()
        onNodeWithTag(SecurityTags.WIPE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        awaitText("L’effacement n’a pas pu se terminer")
        onNodeWithText("Parties non effacées : 1.").assertExists()
        // The other parts were erased all the same.
        assertNull(harness.identity.snapshot)
        eraser.fail = false
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitUntil(timeoutMillis = 20_000) { eraser.erased == 1 }
        awaitTag(SecurityTags.WIPED)
    }

    @Test
    fun theAideSheetOffersTheQuickEraseLast() = runComposeUiTest {
        val harness = SecurityHarness()
        showApp(harness, 360)
        awaitTag(ManifestationTags.HELP_BUTTON)
        onNodeWithTag(ManifestationTags.HELP_BUTTON).performClick()
        waitForIdle()
        awaitTag(ManifestationTags.SHEET)
        onNodeWithTag(SecurityTags.HELP_WIPE).performScrollTo()
        val wipe = onNodeWithTag(SecurityTags.HELP_WIPE).fetchSemanticsNode().positionInRoot.y
        val rally = onNodeWithTag(ManifestationTags.RALLY).fetchSemanticsNode().positionInRoot.y
        assertTrue(rally < wipe, "after the rally point")
        onNodeWithTag(SecurityTags.WIPE).performClick()
        waitForIdle()
        onNodeWithText("Tout effacer de cet appareil ?").assertExists()
        onNodeWithTag(SecurityTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertEquals(0, harness.eraser!!.erased)
    }

    @Test
    fun theProtestModePreparesThePhoneInOneGesturePerItem() {
        for ((width, dark) in listOf(360 to false, 700 to true, 1000 to false)) {
            val harness = SecurityHarness()
            runComposeUiTest {
                setContent {
                    Box(Modifier.requiredSize(width.dp, 900.dp)) {
                        PpfTheme(darkTheme = dark) {
                            CompositionLocalProvider(LocalRallyPointStorage provides harness.rally) {
                                val library = rememberContentLibrary(harness.dependencies)
                                val organisations = rememberOrganisationsLibrary(harness.dependencies)
                                val help = rememberHelpState(harness.dependencies, organisations, library)
                                val security = rememberDeviceSecurity(harness.dependencies)
                                ProvideHelp(help) {
                                    ProvideDeviceSecurity(security) {
                                        DeviceSecurityGate(security) {
                                            ManifestationScreen(help, WindowClass.of(width.toFloat(), 900f), onBack = {}, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                awaitTag(SecurityTags.PREPARE)
                onNodeWithTag(SecurityTags.PREPARE).performScrollTo()
                // The announcement « bientôt » is gone: the functions are there.
                assertEquals(0, onAllNodesWithText("Bientôt dans l’application", substring = true).fetchSemanticsNodes().size)
                onNodeWithText("Toujours : PPF n’utilise ni l’empreinte ni le visage.").assertExists()
                onNodeWithText("21-83.146", substring = true).assertExists()
                onNodeWithText("article 434-15-2 du Code pénal, consultés le 9 novembre 2026", substring = true).assertExists()
                // AG-56: without notifications on this device, the line says so; the map line appears only where the map is drawn (Android).
                onNodeWithTag(SecurityTags.PREPARE_ITEM + "notifications").performScrollTo()
                onNodeWithText("PPF n’affiche aucune notification sur cet appareil.").assertExists()
                assertEquals(0, count(SecurityTags.PREPARE_ITEM + "map"))
                onNodeWithTag(SecurityTags.PREPARE_ITEM + "phrase").performScrollTo().performClick()
                waitForIdle()
                // The lock is turned on from here too, in one gesture and a code.
                onNodeWithTag(SecurityTags.PREPARE_LOCK).performScrollTo().performClick()
                waitForIdle()
                typeCode(SecurityTags.PIN_NEW, "582016")
                typeCode(SecurityTags.PIN_CONFIRM, "582016")
                onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
                waitUntil(timeoutMillis = 20_000) { harness.settings().lockEnabled }
                waitForIdle()
                assertEquals(0, count(SecurityTags.PREPARE_LOCK))
                assertEquals(0, count(ManifestationTags.SOON + "phone"))
            }
        }
    }

    @Test
    fun theDiscreetIconIsSwitchedAfterAWarningEitherWay() = runComposeUiTest {
        val harness = SecurityHarness()
        showApp(harness, 360)
        openMySpace()
        onNodeWithTag(SecurityTags.DISCREET_TOGGLE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("le raccourci peut disparaître", substring = true).assertExists()
        onNodeWithTag(SecurityTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertFalse(harness.icons.isDiscreet, "« Annuler » changes nothing")
        onNodeWithTag(SecurityTags.DISCREET_TOGGLE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        assertTrue(harness.icons.isDiscreet)
        // Back to the icon of PPF: a warning too.
        onNodeWithTag(SecurityTags.DISCREET_TOGGLE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Revenir à l’icône de PPF ?").assertExists()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        assertFalse(harness.icons.isDiscreet)
    }

    @Test
    fun showingTheContentAgainAsksFirstAndCancelChangesNothing() = runComposeUiTest {
        val harness = SecurityHarness()
        lateinit var security: DeviceSecurityState
        var open = true
        setContent {
            PpfTheme {
                security = rememberDeviceSecurity(harness.dependencies)
                if (open) ShowContentDialog(security) { open = false }
            }
        }
        waitForIdle()
        onNodeWithText("Afficher le contenu dans les applications récentes ?").assertExists()
        onNodeWithTag(SecurityTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertTrue(security.settings.hideContent, "hidden by default, still hidden after « Annuler »")
    }
}
