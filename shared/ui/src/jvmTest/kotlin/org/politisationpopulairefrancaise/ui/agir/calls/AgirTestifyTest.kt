package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.calls.CallResponseStore
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirHarness
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.testimonies.TestimonyTags
import kotlin.test.Test

/**
 * The real path of the application: the calls plugged into "Agir" (as AdaptiveShell does), the wall
 * filled with the bundled examples, and "Je témoigne" of the call for witnesses opening "Témoigner"
 * linked to it; back returns to the calls.
 */
@OptIn(ExperimentalTestApi::class)
class AgirTestifyTest {
    @Test
    fun jeTemoigneInsideAgirOpensTemoignerThenComesBack() = runComposeUiTest {
        val agir = AgirHarness()
        var library: CallsLibrary? = null
        setContent {
            agir.Content(
                width = 1000,
                height = 740,
                calls = { modifier, onBack ->
                    val scope = rememberCoroutineScope()
                    val calls = remember {
                        val database = JvmDatabase.inMemory()
                        CallsLibrary(LocalEventStore(database) { NOW }, EditorialTrust.bundledExamples(), null, scope, CallResponseStore(database), clock = { NOW })
                            .also { library = it; it.start() }
                    }
                    val session = remember { AccountSession(InMemoryIdentityStore(), scope).also { it.start() } }
                    CallsScreen(library = calls, session = session, windowClass = WindowClass.of(1000f, 740f), onBack = onBack, modifier = modifier)
                },
            )
        }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.CALLS.id).performScrollTo().performClick()
        waitForIdle()
        // The wall fills inside Agir (where LocalTestify is provided).
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag(CallTags.CARD + Examples.WITNESSES).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(CallTags.CARD + Examples.WITNESSES).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        awaitTag(TestimonyTags.CALL)
        onNodeWithTag(TestimonyTags.BACK).performClick()
        waitForIdle()
        awaitTag(CallTags.WALL)
        requireNotNull(library)
    }
}
