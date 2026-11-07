package org.politisationpopulairefrancaise.ui.agir.calls

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where Agir plugs "Témoigner" in, "Je témoigne" of a call for witnesses opens it with the call's address. */
@OptIn(ExperimentalTestApi::class)
class CallTestifyTest {
    @Test
    fun jeTemoigneOpensTheFormLinkedToTheCall() = runComposeUiTest {
        val opened = mutableListOf<Pair<String, String>>()
        val harness = showCalls(testify = { address, title -> opened += address to title }, waitForExamples = false)
        waitUntil(timeoutMillis = 30_000) { harness.library?.calls?.any { it.slug == Examples.WITNESSES } == true }
        val call = harness.call(Examples.WITNESSES)
        harness.calls.open(call)
        waitForIdle()
        onNodeWithTag(CallTags.ANSWER_NOTE).performScrollTo().assertTextContains("relu avant publication", substring = true)
        onNodeWithTag(CallTags.ANSWER).performScrollTo().assertIsEnabled().assertTextContains("Je témoigne", substring = true).performClick()
        waitForIdle()
        assertEquals(listOf(call.address.toString() to call.displayTitle), opened)
    }
}
