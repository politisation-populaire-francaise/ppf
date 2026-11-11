package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.OngoingMobilizations
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** D-197: « En ce moment » names the ongoing mobilizations, counts the announced blockades and opens each one. */
@OptIn(ExperimentalTestApi::class)
class OngoingBannerTest {
    private val keys = IdentityKeys.generate()
    private val start = 1_794_661_200L // a Saturday at 14 h, Paris
    private val now = start + 3_600

    private fun mobilization(id: String, type: MobilizationType = MobilizationType.RASSEMBLEMENT, end: Long? = start + 3 * 3_600): Mobilization =
        checkNotNull(
            Mobilization.from(
                runBlocking {
                    LocalEventSigner(keys).sign(
                        Mobilization.build(
                            author = keys.publicKey,
                            identifier = id,
                            createdAt = start - 86_400,
                            title = "[EXEMPLE] Mobilisation $id",
                            start = start,
                            end = end,
                            location = "Place de la Mairie",
                            type = type,
                            territory = "Villefictive",
                            status = MobilizationStatus.PREVU,
                            organizers = emptyList(),
                            links = emptyList(),
                            summary = null,
                            description = "[EXEMPLE] Description.",
                            declaration = null,
                        ),
                    )
                },
            ),
        )

    @Test
    fun theBannerNamesTheFirstThreeCountsTheBlockadesAndOpensOne() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val all = listOf(
            mobilization("a"),
            mobilization("blocage", type = MobilizationType.BLOCAGE, end = start + 2 * 3_600),
            mobilization("b"),
            mobilization("c"),
        )
        setContent { PpfTheme { OngoingBanner(OngoingMobilizations.of(all, now), onOpen = { opened += it.identifier }) } }
        awaitTag(OngoingTags.BANNER)
        onNodeWithTag(OngoingTags.SUMMARY).assertTextContains("4 mobilisations en cours, dont 1 blocage annoncé par ses organisateurs")
        // The blockade ends first, so it is named first; three are named, the fourth is counted.
        assertEquals(1, count(OngoingTags.ITEM + "blocage"))
        assertEquals(3, listOf("a", "b", "c", "blocage").count { count(OngoingTags.ITEM + it) == 1 })
        onNodeWithTag(OngoingTags.ITEM + "blocage").performClick()
        assertEquals(listOf("blocage"), opened)
    }

    @Test
    fun nothingIsShownWhenNothingHappens() = runComposeUiTest {
        setContent { PpfTheme { OngoingBanner(OngoingMobilizations.of(listOf(mobilization("tard", end = null)), start - 86_400), onOpen = {}) } }
        waitForIdle()
        assertEquals(0, count(OngoingTags.BANNER))
    }
}
