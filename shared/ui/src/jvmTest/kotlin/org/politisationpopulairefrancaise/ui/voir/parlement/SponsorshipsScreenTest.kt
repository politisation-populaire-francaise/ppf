package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorMandate
import org.politisationpopulairefrancaise.core.voir.parrainages.Sponsorship
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsFormat
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsLoader
import org.politisationpopulairefrancaise.core.voir.parrainages.SponsorshipsPackage
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * « Parrainages de la présidentielle 2027 » (VO-37): nothing before the publications, the package downloaded on a
 * gesture with its size, every candidate in alphabetical order with the same figures, a department searched, and
 * never an action towards an official.
 */
@OptIn(ExperimentalTestApi::class)
class SponsorshipsScreenTest {

    private val keys = ParliamentUiFixtures.Keys()

    private fun s(last: String, first: String, mandate: SponsorMandate, territory: String?, department: String, candidate: String) =
        Sponsorship("[EXEMPLE] $last", first, mandate, null, territory, department, null, "[EXEMPLE] $candidate", "2027-02-16")

    private val pkg = SponsorshipsPackage(
        example = true,
        election = SponsorshipsFormat.ELECTION_2027,
        publishedOn = "2027-02-16",
        sponsorships = listOf(
            s("ZÉPHYR", "Lou", SponsorMandate.MAIRE, "Lyon", "Rhône", "ZOLA Bastien"),
            s("ABEILLE", "Noa", SponsorMandate.DEPUTE, "1ère circonscription", "Rhône", "ARNAUD Claire"),
            s("BRISE", "Sam", SponsorMandate.SENATEUR, null, "Finistère", "ARNAUD Claire"),
        ).sortedWith(SponsorshipsFormat.ORDER),
    )

    private fun sponsorshipsTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private suspend fun ComposeUiTest.show(published: Boolean): ParliamentUiFixtures.Server {
        val packages = ParliamentUiFixtures.packages() + if (published) mapOf(SponsorshipsFormat.PACKAGE_2027 to SponsorshipsFormat.write(pkg).encodeToByteArray()) else emptyMap()
        val server = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(packages)), packages)
        var holder: ParliamentState? = null
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember {
                val storage = InMemoryParliamentStorage()
                ParliamentState(
                    ParliamentRepository(server, storage, trust = keys.trust, clock = { ParliamentUiFixtures.NOW }),
                    scope,
                    sponsorships = SponsorshipsState(SponsorshipsLoader(server, storage), scope),
                ).also { it.open() }
            }
            holder = state
            PpfTheme(darkTheme = false) { Box(Modifier.requiredSize(1000.dp, 760.dp)) { if (state.isOpen) ParliamentScreen(state) } }
        }
        waitUntil(timeoutMillis = 10_000) { holder?.loading == false && holder?.opening != null }
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(SponsorshipsTags.ENTRY))
        onNodeWithTag(SponsorshipsTags.ENTRY).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(SponsorshipsTags.PAGE).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        return server
    }

    private fun ComposeUiTest.has(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun beforeThePublicationsThePageSaysWhenTheyCome() = sponsorshipsTest {
        val server = show(published = false)
        onNodeWithTag(SponsorshipsTags.NOT_YET).performScrollTo().assertExists()
        assertTrue(!has(SponsorshipsTags.DOWNLOAD))
        assertTrue(server.requests.none { it == SponsorshipsFormat.PACKAGE_2027 })
    }

    @Test
    fun everyCandidateHasTheSameFiguresAndNoActionTowardsAnOfficial() = sponsorshipsTest {
        val server = show(published = true)
        assertTrue(server.requests.none { it == SponsorshipsFormat.PACKAGE_2027 }, "nothing before the gesture")
        onNodeWithTag(SponsorshipsTags.DOWNLOAD).performScrollTo().assert(hasText("Télécharger les parrainages publiés", substring = true)).performClick()
        waitUntil(timeoutMillis = 10_000) { has(SponsorshipsTags.candidate("[EXEMPLE] ARNAUD Claire")) }
        onNodeWithTag(SponsorshipsTags.candidate("[EXEMPLE] ARNAUD Claire"))
            .performScrollTo()
            .assert(hasText("2 parrainages publiés · 2 départements ou collectivités cités · au plus 1 d’un même", substring = true))
        onNodeWithTag(SponsorshipsTags.candidate("[EXEMPLE] ZOLA Bastien")).assert(hasText("1 parrainages publiés", substring = true))
        // Alphabetical order of the candidates.
        val top = { tag: String -> onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y }
        assertTrue(top(SponsorshipsTags.candidate("[EXEMPLE] ARNAUD Claire")) < top(SponsorshipsTags.candidate("[EXEMPLE] ZOLA Bastien")))
        // Never a button to write to an official, nor a list of who did not present anyone.
        listOf("Écrire", "Contacter", "Interpeller", "n’a pas parrainé").forEach { assertEquals(0, onAllNodesWithText(it, substring = true).fetchSemanticsNodes().size, it) }

        // A department.
        onNodeWithTag(SponsorshipsTags.DEPARTMENT).performScrollTo().performTextInput("rhone")
        waitForIdle()
        assertTrue(onAllNodesWithText("Rhône · 2 parrainages").fetchSemanticsNodes().isNotEmpty())

        // The list of one candidate, by department, with neutral mandates.
        onNodeWithTag(SponsorshipsTags.candidate("[EXEMPLE] ARNAUD Claire")).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { has(SponsorshipsTags.CANDIDATE_PAGE) }
        assertTrue(onAllNodesWithText("Noa ABEILLE, Membre de l’Assemblée nationale, 1ère circonscription").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Sam BRISE, Membre du Sénat").fetchSemanticsNodes().isNotEmpty())
    }
}
