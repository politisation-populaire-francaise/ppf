package org.politisationpopulairefrancaise.ui.agir.organisations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.EventKinds
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirHarness
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.awaitCard
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.scrollListTo
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The directory of organisations and the page of an organisation: the examples, the filters by
 * type and territory, the search, "près de chez moi", the verified badge, the public contacts,
 * the warning before a web site (safe choice first), following an organisation (kept on the
 * device), its publications and its agenda; on a phone (360 dp), a tablet (700 dp) and a desktop
 * (1000 dp, two panes). "Comprendre" never lists these entries.
 */
@OptIn(ExperimentalTestApi::class)
class OrganisationsScreenTest {

    private fun ComposeUiTest.openDirectory(harness: AgirHarness, width: Int = 360, height: Int = 740, organisationAgenda: (@Composable (String, Modifier) -> Unit)? = null) {
        setContent { harness.Content(width, height, organisationAgenda = organisationAgenda) }
        awaitTag(AgirTags.HOME)
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.ORGANISATIONS.id).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationsTags.LIST)
        awaitCard(AgirFixtures.EXAMPLES.first())
    }

    private fun ComposeUiTest.countText(text: String) {
        scrollListTo(OrganisationsTags.COUNT)
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithTag(OrganisationsTags.COUNT).assertTextContains(text) }.isSuccess }
    }

    /** A chip or a button of the head of the list: brought on screen first (the list is lazy, the chip rows scroll). */
    private fun ComposeUiTest.tap(tag: String) {
        scrollListTo(tag)
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openCard(slug: String) {
        awaitCard(slug)
        onNodeWithTag(OrganisationsTags.CARD + slug).performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.PAGE)
    }

    @Test
    fun theDirectoryListsTheExamplesAndFiltersThemByType() = runComposeUiTest {
        openDirectory(AgirHarness())
        countText("8 organisations")
        AgirFixtures.EXAMPLES.forEach { awaitCard(it) }

        tap(OrganisationsTags.TYPE + "syndicat")
        waitForIdle()
        countText("2 organisations")
        assertEquals(0, count(OrganisationsTags.CARD + "ateliers-lanterne"))
        awaitCard("union-locale-lille")
        awaitCard("syndicat-livreurs-velo")

        tap(OrganisationsTags.TYPE + "legal-team")
        waitForIdle()
        countText("3 organisations")

        tap(OrganisationsTags.TYPE + "all")
        waitForIdle()
        countText("8 organisations")
    }

    @Test
    fun theSearchAndTheTerritoryNarrowTheListAndCanBeCleared() = runComposeUiTest {
        openDirectory(AgirHarness())
        scrollListTo(OrganisationsTags.SEARCH)
        onNodeWithTag(OrganisationsTags.SEARCH).performTextInput("avocats benevoles")
        waitForIdle()
        countText("1 organisation")
        awaitCard("reseau-avocats-benevoles")

        scrollListTo(OrganisationsTags.SEARCH)
        onNodeWithTag(OrganisationsTags.SEARCH).performTextReplacement("")
        waitForIdle()
        tap(OrganisationsTags.territory("Nord (59)"))
        waitForIdle()
        countText("1 organisation")
        awaitCard("legal-team-nord")

        scrollListTo(OrganisationsTags.SEARCH)
        onNodeWithTag(OrganisationsTags.SEARCH).performTextInput("boulangerie")
        waitForIdle()
        scrollListTo(OrganisationsTags.NONE)
        onNodeWithText("Aucune organisation ne correspond à votre recherche.").assertExists()
        scrollListTo(OrganisationsTags.CLEAR)
        onNodeWithTag(OrganisationsTags.CLEAR).performClick()
        waitForIdle()
        countText("8 organisations")
    }

    @Test
    fun nearMeIsComputedOnTheDeviceLocalOnesFirstThenNationalOnes() = runComposeUiTest {
        val harness = AgirHarness()
        openDirectory(harness)
        scrollListTo(OrganisationsTags.NEAR_PRIVATE)
        onNodeWithText("Ce territoire reste sur votre appareil", substring = true).assertExists()
        scrollListTo(OrganisationsTags.NEAR)
        onNodeWithTag(OrganisationsTags.NEAR).performTextInput("59000")
        waitForIdle()
        countText("4 organisations")
        scrollListTo(OrganisationsTags.NEAR_SECTION)
        awaitCard("legal-team-nord")
        awaitCard("union-locale-lille")
        scrollListTo(OrganisationsTags.NATIONAL_SECTION)
        awaitCard("reseau-avocats-benevoles")
        awaitCard("syndicat-livreurs-velo")
        assertEquals(0, count(OrganisationsTags.CARD + "ateliers-lanterne"))
        // Local ones first, then those that act everywhere in France; each group by name.
        assertEquals(
            listOf("legal-team-nord", "union-locale-lille", "reseau-avocats-benevoles", "syndicat-livreurs-velo"),
            harness.organisations.results.map { it.slug },
        )
        // The territory lives in memory, in the query of the device.
        assertEquals("59000", harness.organisations.query.near)
    }

    @Test
    fun onATabletThePageShowsTheVerificationTheContactsAndFollowingStaysOnTheDevice() = runComposeUiTest {
        val harness = AgirHarness()
        openDirectory(harness, width = 700)
        openCard(AgirFixtures.LANTERNE)
        onNodeWithTag(OrganisationPageTags.NAME).assertTextContains("Association fictive Les Ateliers de la Lanterne")
        onNodeWithText("Vérifiée par PPF le 12 octobre 2026").assertExists()
        onNodeWithText("Lyon (69)").assertExists()
        onNodeWithTag(OrganisationPageTags.FOLLOW_PRIVATE).performScrollTo()
        onNodeWithText("Votre abonnement reste sur votre appareil", substring = true).assertExists()

        onNodeWithTag(OrganisationPageTags.FOLLOW).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { harness.library.isFollowed(AgirFixtures.LANTERNE_ACCOUNT) }
        onNodeWithTag(OrganisationPageTags.FOLLOW).assertTextContains("Suivi")
        val follows = FollowStore(harness.dependencies.database!!)
        waitUntil(timeoutMillis = 5_000) { follows.isFollowed(AgirFixtures.LANTERNE_ACCOUNT) }

        // Telephone and e-mail open at once; nothing was sent anywhere else.
        onNodeWithTag(OrganisationPageTags.PHONE).performScrollTo().performClick()
        onNodeWithTag(OrganisationPageTags.EMAIL).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("tel:0465712014", "mailto:ateliers-lanterne@example.org"), harness.uris.opened)
        onNodeWithTag(OrganisationPageTags.ADDRESS).performScrollTo()
        onNodeWithTag(OrganisationPageTags.PUBLICATIONS_NONE).performScrollTo()
        onNodeWithTag(OrganisationPageTags.AGENDA_SOON).performScrollTo()
        onNodeWithText("Agenda de l’organisation : bientôt.").assertExists()

        // Following again stops following.
        onNodeWithTag(OrganisationPageTags.FOLLOW).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { !harness.library.isFollowed(AgirFixtures.LANTERNE_ACCOUNT) }

        onNodeWithTag(OrganisationPageTags.BACK).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationsTags.LIST)
    }

    @Test
    fun aWebSiteOpensOnlyAfterTheWarningWhoseSafeChoiceComesFirst() = runComposeUiTest {
        val harness = AgirHarness()
        openDirectory(harness)
        openCard(AgirFixtures.LANTERNE)
        onNodeWithTag(OrganisationPageTags.WEBSITE + 0).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.LEAVE_DIALOG)
        onNodeWithText("Vous quittez PPF").assertExists()
        onNodeWithText("Ce lien ouvre le site example.org, hors de PPF", substring = true).assertExists()
        assertTrue(harness.uris.opened.isEmpty(), "nothing opens before the user chooses")
        val stay = onNodeWithTag(OrganisationPageTags.LEAVE_STAY).fetchSemanticsNode().boundsInRoot.top
        val go = onNodeWithTag(OrganisationPageTags.LEAVE_GO).fetchSemanticsNode().boundsInRoot.top
        assertTrue(stay < go, "the safe choice comes first")

        onNodeWithTag(OrganisationPageTags.LEAVE_STAY).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(0, count(OrganisationPageTags.LEAVE_DIALOG))
        assertTrue(harness.uris.opened.isEmpty())

        onNodeWithTag(OrganisationPageTags.WEBSITE + 0).performScrollTo().performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.LEAVE_GO)
        onNodeWithTag(OrganisationPageTags.LEAVE_GO).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        assertEquals(listOf(AgirFixtures.LANTERNE_SITE), harness.uris.opened)
    }

    @Test
    fun anOrganisationWithoutAnAccountHasNothingToFollow() = runComposeUiTest {
        openDirectory(AgirHarness())
        openCard("union-locale-lille")
        onNodeWithTag(OrganisationPageTags.NO_ACCOUNT).assertExists()
        assertEquals(0, count(OrganisationPageTags.FOLLOW))
        assertEquals(0, count(OrganisationPageTags.WEBSITE + 0))
    }

    @Test
    fun onADesktopTheListAndThePageSitSideBySide() = runComposeUiTest {
        openDirectory(AgirHarness(), width = 1000)
        onNodeWithTag(OrganisationsTags.SELECT_HINT).assertExists()
        awaitCard("legal-team-nord")
        onNodeWithTag(OrganisationsTags.CARD + "legal-team-nord").performClick()
        waitForIdle()
        awaitTag(OrganisationPageTags.PAGE)
        onNodeWithTag(OrganisationsTags.LIST).assertExists()
        assertEquals(0, count(OrganisationPageTags.BACK), "no back button beside the list")
        onNodeWithTag(OrganisationPageTags.NAME).assertTextContains("Legal team de démonstration du Nord")
        awaitCard("union-locale-lille")
        onNodeWithTag(OrganisationsTags.CARD + "union-locale-lille").performClick()
        waitForIdle()
        onNodeWithTag(OrganisationPageTags.NAME).assertTextContains("Union locale fictive de Lille")
    }

    @Test
    fun thePageShowsTheOrganisationsNotesAndItsAgenda() = runComposeUiTest {
        val harness = AgirHarness()
        val organisation = LocalEventSigner(IdentityKeys.generate())
        val store = harness.dependencies.contentStore!!
        runBlocking {
            store.store(AgirFixtures.entry("compte-test", name = "[EXEMPLE] Collectif fictif avec un compte", type = "collectif", accountKey = organisation.publicKey.hex))
            store.store(organisation.sign(kind = EventKinds.TEXT_NOTE, content = "[EXEMPLE] Assemblée générale fictive jeudi soir.", createdAt = 1_793_900_000))
        }
        val note = store.recent(kinds = listOf(EventKinds.TEXT_NOTE)).single { it.pubkey == organisation.publicKey.hex }
        openDirectory(harness, organisationAgenda = { key, modifier -> Text("Agenda de $key", modifier = modifier.testTag("plugged-organisation-agenda")) })
        openCard("compte-test")
        awaitTag(OrganisationPageTags.PUBLICATION + note.id.take(8))
        onNodeWithText("Assemblée générale fictive jeudi soir.").assertExists()
        onNodeWithTag("plugged-organisation-agenda").performScrollTo().assertTextContains("Agenda de ${organisation.publicKey.hex}")
        assertEquals(0, count(OrganisationPageTags.AGENDA_SOON))
    }

    @Test
    fun comprendreNeverListsTheOrganisations() = runComposeUiTest {
        val dependencies = AgirFixtures.dependencies()
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithTag(ShellTags.navItem(Space.AGIR)).performClick()
        waitForIdle()
        onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.ORGANISATIONS.id).performClick()
        waitForIdle()
        awaitCard(AgirFixtures.LANTERNE)
        val store = dependencies.contentStore!!
        assertTrue(store.byTag(listOf(30023), "ppf-type", "organisation", limit = 50).size == AgirFixtures.EXAMPLES.size, "the entries are on the device")

        onNodeWithTag(ShellTags.navItem(Space.COMPRENDRE)).performClick()
        waitForIdle()
        awaitTag(ContentTags.GUIDE_CARD + "garde-a-vue")
        AgirFixtures.EXAMPLES.forEach { slug -> assertEquals(0, count(ContentTags.GUIDE_CARD + slug)) }
        onNodeWithText("Association fictive Les Ateliers de la Lanterne", substring = true).assertDoesNotExist()
    }
}
