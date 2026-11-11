package org.politisationpopulairefrancaise.ui.urne

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.urne.MyBallotCheck
import org.politisationpopulairefrancaise.core.urne.MyBallotFinding
import org.politisationpopulairefrancaise.core.urne.RecountResult
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** « Mon bulletin a été compté » (DE-29): one button for everyone, an answer that names no choice and proves nothing. */
@OptIn(ExperimentalTestApi::class)
class MyBallotCheckViewsTest {

    @Test
    fun theCheckRunsOnlyWhenAskedAndSaysWhereTheBallotStands() = runComposeUiTest {
        var asked = 0
        setContent {
            PpfTheme { MyBallotCheckSection(check = { asked++; MyBallotCheck(MyBallotFinding.COUNTED, RecountResult.CORRECT) }) }
        }
        waitForIdle()
        assertEquals(0, asked, "nothing is downloaded before the button")
        onNodeWithTag(MyBallotTags.BUTTON).performClick()
        waitForIdle()
        assertEquals(1, asked)
        onNodeWithTag(MyBallotTags.FINDING).assertTextContains("avec le choix que vous avez fait", substring = true)
        onNodeWithTag(MyBallotTags.RECOUNT).assertTextContains("correspondent exactement", substring = true)
        onNodeWithText("ne peut servir de preuve à personne", substring = true).assertIsDisplayed()
    }

    @Test
    fun aMissingBallotOrAWrongRecountIsAnAlertAndAnUnavailableListIsSaid() = runComposeUiTest {
        var answer: MyBallotCheck? = MyBallotCheck(MyBallotFinding.MISSING, RecountResult.WRONG_TOTALS)
        setContent { PpfTheme { MyBallotCheckSection(check = { answer }) } }
        onNodeWithTag(MyBallotTags.BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(MyBallotTags.FINDING).assertTextContains("Alerte", substring = true)
        onNodeWithTag(MyBallotTags.RECOUNT).assertTextContains("les totaux publiés ne correspondent pas", substring = true)
        answer = null
        onNodeWithTag(MyBallotTags.BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(MyBallotTags.UNAVAILABLE).assertIsDisplayed()
    }
}
