package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.urne.ChoiceCounts
import org.politisationpopulairefrancaise.core.urne.UrneInfo
import org.politisationpopulairefrancaise.core.urne.UrneJournal
import org.politisationpopulairefrancaise.core.urne.UrneResults
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common voting components (D-110, step 3): one choice, its state, and the results by level. */
@OptIn(ExperimentalTestApi::class)
class VoteControlTest {
    private val choices = listOf("pour" to "Pour", "contre" to "Contre")

    @Test
    fun aChoiceIsMadeThenSaidToLeaveLaterWithoutTheAccount() = runComposeUiTest {
        val chosen = mutableListOf<String>()
        var state by mutableStateOf(VoteUiState(available = true, hasToken = true, closed = false, choice = null, sent = false))
        setContent {
            PpfTheme {
                VoteControl(choices, state, onChoose = { chosen += it; state = state.copy(choice = it, sent = false) })
            }
        }
        onNodeWithTag(UrneTags.choice("pour")).performClick()
        waitForIdle()
        assertEquals(listOf("pour"), chosen)
        onNodeWithTag(UrneTags.choice("pour")).assertIsSelected()
        onNodeWithTag(UrneTags.STATUS).assertTextContains("sans votre compte", substring = true)
        state = state.copy(sent = true)
        waitForIdle()
        onNodeWithTag(UrneTags.STATUS).assertTextContains("enregistré, anonymement", substring = true)
        onNodeWithTag(UrneTags.WITHDRAW).performClick()
        waitForIdle()
        assertEquals(listOf("pour", UrneInfo.WITHDRAWN), chosen)
        onNodeWithText("pas même PPF", substring = true).assertExists()
    }

    @Test
    fun withoutTokenOrWhenClosedNoChoiceIsOffered() = runComposeUiTest {
        var state by mutableStateOf(VoteUiState(available = true, hasToken = false, closed = false, choice = null, sent = false))
        setContent { PpfTheme { VoteControl(choices, state, onChoose = {}) } }
        onNodeWithTag(UrneTags.choice("pour")).assertDoesNotExist()
        onNodeWithTag(UrneTags.STATUS).assertTextContains("connexion", substring = true)
        state = state.copy(hasToken = true, closed = true)
        waitForIdle()
        onNodeWithTag(UrneTags.STATUS).assertTextContains("clos", substring = true)
        state = state.copy(available = false)
        waitForIdle()
        onNodeWithText("pas disponible", substring = true).assertExists()
    }

    @Test
    fun theElectedOfficialsVoteIsShownApart() = runComposeUiTest {
        val journal = UrneJournal(
            number = 1L,
            accounts = mapOf("verifie" to 3L, "non-verifie" to 5L, "elu" to 1L),
            issued = mapOf("verifie" to 3L, "non-verifie" to 5L, "elu" to 1L),
            ballots = mapOf("verifie" to 2L, "non-verifie" to 3L, "elu" to 1L),
            replacements = 0L,
            results = UrneResults(
                verified = ChoiceCounts(mapOf("pour" to 2L), 2L),
                notVerified = ChoiceCounts(mapOf("pour" to 1L, "contre" to 2L), 3L),
                citizens = ChoiceCounts(mapOf("pour" to 3L, "contre" to 2L), 5L),
                electedOfficials = ChoiceCounts(mapOf("contre" to 1L), 1L),
            ),
            closed = true,
        )
        setContent { PpfTheme { UrneResultsView(journal, choices.toMap()) } }
        onNodeWithText("Vote des citoyens").assertExists()
        onNodeWithText("Comptes vérifiés en personne").assertExists()
        onNodeWithText("Total des citoyens").assertExists()
        onNodeWithText("Vote des élus").assertExists()
        onNodeWithText("jamais ajouté", substring = true).assertExists()
    }
}
