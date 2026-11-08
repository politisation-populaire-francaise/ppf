package org.politisationpopulairefrancaise.ui.agir.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.agir.security.AppLock
import org.politisationpopulairefrancaise.core.agir.security.DeviceSecuritySettings
import org.politisationpopulairefrancaise.core.agir.security.InMemoryDeviceSecurityStorage
import org.politisationpopulairefrancaise.core.device.DeviceEraseRegistry
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SETUP_PIN = "580364"

/** What the step reported to the creation of the account. */
private class SetupOutcome {
    var done = 0
    var skipped = 0
}

/** The step alone, on a security state with few iterations of the hash so that the test stays fast. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.showStep(
    storage: InMemoryDeviceSecurityStorage,
    outcome: SetupOutcome,
    width: Int = 360,
    dark: Boolean = false,
    withShell: Boolean = true,
) {
    setContent {
        Box(Modifier.requiredSize(width.dp, 800.dp)) {
            PpfTheme(darkTheme = dark) {
                val scope = rememberCoroutineScope()
                val state = remember { DeviceSecurityState(AppLock(storage, iterations = 10), DeviceEraseRegistry(), scope) }
                val step: @Composable () -> Unit = {
                    AppLockSetupStep(onDone = { outcome.done++ }, onSkip = { outcome.skipped++ }, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                if (withShell) ProvideDeviceSecurity(state) { step() } else step()
            }
        }
    }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.type(tag: String, code: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    onNodeWithTag(tag).performTextInput(code)
    waitForIdle()
}

private fun InMemoryDeviceSecurityStorage.settings() = DeviceSecuritySettings.decode(value)

@OptIn(ExperimentalTestApi::class)
class AppLockSetupStepTest {

    @Test
    fun theCodeIsPreselectedAndAChosenCodeTurnsTheLockOnOnPhonesTabletsAndComputers() {
        for (width in listOf(360, 700, 1000)) {
            for (dark in listOf(false, true)) {
                val storage = InMemoryDeviceSecurityStorage()
                val outcome = SetupOutcome()
                runComposeUiTest {
                    showStep(storage, outcome, width, dark)
                    onNodeWithText("Protéger PPF sur cet appareil").assertExists()
                    // The most protective choice comes first and is already selected (principle 10).
                    onNodeWithTag(AppLockSetupTags.CHOOSE).assertIsSelected()
                    onNodeWithTag(AppLockSetupTags.SKIP).assertIsNotSelected()
                    // A forgotten code only loses this device: said before choosing.
                    onNodeWithText("vous ne perdez que cet appareil", substring = true).assertExists()

                    // A trivial code is refused in words; nothing is stored, nothing is reported.
                    type(SecurityTags.PIN_NEW, "111111")
                    type(SecurityTags.PIN_CONFIRM, "111111")
                    onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
                    waitForIdle()
                    onNodeWithText("trop facile", substring = true).assertExists()
                    assertNull(storage.value, "at $width dp")
                    assertEquals(0, outcome.done)
                }
            }
        }
    }

    @Test
    fun aGoodCodeTypedTwiceTurnsTheLockOnAndEndsTheStep() = runComposeUiTest {
        val storage = InMemoryDeviceSecurityStorage()
        val outcome = SetupOutcome()
        showStep(storage, outcome)
        type(SecurityTags.PIN_NEW, SETUP_PIN)
        type(SecurityTags.PIN_CONFIRM, "580365")
        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Les deux codes sont différents.").assertExists()
        assertEquals(0, outcome.done)

        // The confirmation typed again, the same this time.
        onNodeWithTag(SecurityTags.PIN_CONFIRM).performScrollTo().performTextClearance()
        type(SecurityTags.PIN_CONFIRM, SETUP_PIN)
        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitUntil(timeoutMillis = 20_000) { outcome.done == 1 }
        assertTrue(storage.settings().lockEnabled)
        assertEquals(0, outcome.skipped)
        // Never the code itself in what the device keeps.
        assertFalse(storage.value!!.contains(SETUP_PIN))
    }

    @Test
    fun continuingWithoutACodeWarnsFirstAndCanBeCancelled() = runComposeUiTest {
        val storage = InMemoryDeviceSecurityStorage()
        val outcome = SetupOutcome()
        showStep(storage, outcome)
        onNodeWithTag(AppLockSetupTags.SKIP).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AppLockSetupTags.SKIP).assertIsSelected()
        // No code field once « Continuer sans code » is chosen.
        assertEquals(0, count(SecurityTags.PIN_NEW))

        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Continuer sans code ?").assertExists()
        onNodeWithText("Sécurité de cet appareil", substring = true).assertExists()
        // « Annuler » goes back to the step, nothing reported.
        onNodeWithTag(SecurityTags.DIALOG_CANCEL).performClick()
        waitForIdle()
        assertEquals(0, count(SecurityTags.DIALOG_CONFIRM))
        assertEquals(0, outcome.skipped)

        // « Confirmer » ends the step without a code.
        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SecurityTags.DIALOG_CONFIRM).performClick()
        waitForIdle()
        assertEquals(1, outcome.skipped)
        assertEquals(0, outcome.done)
        assertNull(storage.value)
    }

    @Test
    fun aLockAlreadyOnIsSaidAndTheStepJustContinues() = runComposeUiTest {
        val storage = InMemoryDeviceSecurityStorage()
        AppLock(storage, iterations = 10).enable(SETUP_PIN, SETUP_PIN)
        val outcome = SetupOutcome()
        showStep(storage, outcome)
        onNodeWithText("déjà activé", substring = true).assertExists()
        assertEquals(0, count(AppLockSetupTags.CHOOSE))
        onNodeWithTag(AppLockSetupTags.CONTINUE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, outcome.done)
    }

    @Test
    fun outsideTheShellTheStepShowsNothingAndEndsAtOnce() = runComposeUiTest {
        val outcome = SetupOutcome()
        showStep(InMemoryDeviceSecurityStorage(), outcome, withShell = false)
        assertEquals(0, count(AppLockSetupTags.STEP))
        assertEquals(1, outcome.done)
    }
}
