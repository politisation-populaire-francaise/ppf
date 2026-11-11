package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.decider.DocumentAccessMode
import org.politisationpopulairefrancaise.core.decider.DocumentRequestStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** DE-31: a request written on the device, noted with its day, then its deadlines and the CADA referral prepared. */
@OptIn(ExperimentalTestApi::class)
class DocumentRequestViewsTest {
    private val today = CivilDate(2026, 12, 15)

    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    @Test
    fun theRequestIsWrittenNotedAndFollowedUntilTheCada() = runComposeUiTest {
        val store = DocumentRequestStore(JvmDatabase.inMemory())
        setContent {
            PpfTheme {
                Box(Modifier.requiredSize(420.dp, 900.dp).verticalScroll(rememberScrollState())) {
                    DocumentRequestPage(store, reminders = null, today = today)
                }
            }
        }
        // Without answers, the letter keeps its blanks to fill in.
        onNodeWithTag(DocumentRequestTags.TEXT).assert(has("[Nom de l’administration]")).assert(has("Le 15 décembre 2026"))
        onNodeWithTag(DocumentRequestTags.ADMINISTRATION).performTextInput("[EXEMPLE] Mairie de Lyon")
        onNodeWithTag(DocumentRequestTags.DOCUMENT).performTextInput("[EXEMPLE] Délibération du 3 octobre 2026")
        onNodeWithTag(DocumentRequestTags.mode(DocumentAccessMode.COPY)).performScrollTo().performClick()
        onNodeWithTag(DocumentRequestTags.TEXT).performScrollTo()
            .assert(has("À l’attention de : [EXEMPLE] Mairie de Lyon"))
            .assert(has("frais de reproduction et d’envoi"))
        onNodeWithTag(DocumentRequestTags.COPY).performScrollTo().performClick()
        onNodeWithTag(DocumentRequestTags.COPIED).assertExists()

        // A day to come is refused; the day of reception is noted, on the device only.
        onNodeWithTag(DocumentRequestTags.RECEIVED_ON).performScrollTo().performTextClearance()
        onNodeWithTag(DocumentRequestTags.RECEIVED_ON).performTextInput("16/12/2026")
        onNodeWithTag(DocumentRequestTags.NOTE).performScrollTo().performClick()
        onNodeWithTag(DocumentRequestTags.ERROR).assertExists()
        onNodeWithTag(DocumentRequestTags.RECEIVED_ON).performTextClearance()
        onNodeWithTag(DocumentRequestTags.RECEIVED_ON).performTextInput("09/11/2026")
        onNodeWithTag(DocumentRequestTags.NOTE).performScrollTo().performClick()
        assertEquals(CivilDate(2026, 11, 9), store.all().single().receivedOn)

        // A month of silence is a refusal: two months to ask the CADA, and the referral prepared.
        onNodeWithTag(DocumentRequestTags.stage(0)).performScrollTo().assert(has("refusée depuis le 10 décembre 2026")).assert(has("jusqu’au 10 février 2027"))
        onNodeWithTag(DocumentRequestTags.prepareCada(0)).performScrollTo().performClick()
        onNodeWithTag(DocumentRequestTags.cadaText(0)).performScrollTo().assert(has("Le 9 novembre 2026, j’ai demandé à [EXEMPLE] Mairie de Lyon")).assert(has("ce qui vaut refus"))
        onNodeWithTag(DocumentRequestTags.cada(0)).performScrollTo().performClick()
        onNodeWithTag(DocumentRequestTags.stage(0)).performScrollTo().assert(has("CADA saisie le 15 décembre 2026")).assert(has("vers le 15 janvier 2027"))
        assertEquals(today, store.all().single().cadaOn)

        onNodeWithTag(DocumentRequestTags.remove(0)).performScrollTo().performClick()
        assertEquals(0, store.all().size)
    }
}
