package org.politisationpopulairefrancaise.ui.voir.parlement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.PublicPerson
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentIndex
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parlement.Position
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.voir.VoirTags
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes

/**
 * « Parlementaires » (D-065) against a fake server serving fictional packages signed with a key
 * generated for the test: the card, the list, the member's and the ballot's pages, the [EXEMPLE]
 * banner, the warnings, the back button, on phone and computer widths.
 */
@OptIn(ExperimentalTestApi::class)
class ParliamentScreenTest {

    private val keys = ParliamentUiFixtures.Keys()
    private val packages = ParliamentUiFixtures.packages()

    private fun parliamentTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private suspend fun server(index: ParliamentIndex = ParliamentUiFixtures.index(packages)) =
        ParliamentUiFixtures.Server(keys.sign(index), packages)

    /** Shows the screen, opened on its list, and waits for the index. */
    private fun ComposeUiTest.show(
        server: ParliamentUiFixtures.Server?,
        width: Int,
        height: Int = 740,
        now: Long = ParliamentUiFixtures.NOW,
        bundled: suspend (String) -> ByteArray? = { null },
        extra: (@androidx.compose.runtime.Composable (ParliamentState) -> Unit)? = null,
    ): () -> ParliamentState {
        var holder: ParliamentState? = null
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { ParliamentState(ParliamentRepository(server, InMemoryParliamentStorage(), bundled, keys.trust, clock = { now }), scope).also { it.open() } }
            holder = state
            PpfTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    if (extra != null) Column { extra(state) } else if (state.isOpen) ParliamentScreen(state)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { holder?.loading == false && holder?.opening != null }
        waitForIdle()
        return { holder!! }
    }

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.downloadMembers() {
        onNodeWithTag(ParliamentTags.DOWNLOAD_MEMBERS).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ParliamentTags.DOWNLOAD_MEMBERS).fetchSemanticsNodes().isEmpty() }
        waitForIdle()
    }

    private fun ComposeUiTest.downloadMonth(name: String) {
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.month(name)))
        onNodeWithTag(ParliamentTags.month(name)).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasTestTag(ParliamentTags.month(name)) and hasText("sur cet appareil", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theMembersAreDownloadedOnAGestureAfterTheirSizeIsShown() = parliamentTest {
        val server = server()
        show(server, width = 400, height = 760)
        // Nothing but the index before the gesture; the size is on the button.
        assertEquals(listOf<String?>(null), server.requests)
        val size = (packages.getValue("membres").size + 999) / 1000
        onNodeWithTag(ParliamentTags.DOWNLOAD_MEMBERS).assert(hasText("Télécharger la liste ($size Ko)"))
        onNodeWithTag(ParliamentTags.EXAMPLE).assertExists()
        onNodeWithTag(ParliamentTags.DATA_DATE).assert(hasText("Données du 7 novembre 2026"))
        onNodeWithTag(ParliamentTags.ATTRIBUTION)
            .assert(hasAnyDescendant(hasText("D’après les données ouvertes de l’Assemblée nationale, mises à jour le 7 novembre 2026 ; réutilisation par PPF, sans lien avec cette institution. Licence : Licence Ouverte.")))
            .assert(hasAnyDescendant(hasText("du Sénat", substring = true)))
            .assert(hasAnyDescendant(hasText("du Parlement européen", substring = true)))
        onNodeWithTag(ParliamentTags.STALE).assertDoesNotExist()
        downloadMembers()
        assertEquals(listOf(null, "membres"), server.requests)
        // Search without accents.
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.SEARCH))
        onNodeWithTag(ParliamentTags.SEARCH).performTextInput("elise")
        waitForIdle()
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("an:PA900001")))
        onNodeWithTag(ParliamentTags.member("an:PA900002")).assertDoesNotExist()
    }

    @Test
    fun theListFiltersByChamberAndGroupInThreeColumnsOnAComputer() = parliamentTest {
        show(server(), width = 1000)
        downloadMembers()
        onNodeWithTag(ParliamentTags.chamber("an")).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.member("senat:90001A")).assertDoesNotExist()
        onNodeWithTag(ParliamentTags.group("an:PO900001")).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.member("an:PA900001")).assertDoesNotExist()
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("an:PA900002")))
        onNodeWithTag(ParliamentTags.member("an:PA900003")).assertExists()
        // Three columns: both cards on the same row.
        val first = onNodeWithTag(ParliamentTags.member("an:PA900003")).fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag(ParliamentTags.member("an:PA900002")).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top)
    }

    @Test
    fun aMemberPageShowsNeutralPositionsAndTheDefinitionOfParticipation() = parliamentTest {
        show(server(), width = 1000)
        downloadMembers()
        downloadMonth("scrutins-an-2026-10")
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("an:PA900001")))
        onNodeWithTag(ParliamentTags.member("an:PA900001")).performClick()
        waitForTag(ParliamentTags.MEMBER_PAGE)
        onNodeWithTag(ParliamentTags.section("identity"))
            .assert(hasAnyDescendant(hasText("Assemblée : Assemblée nationale")))
            .assert(hasAnyDescendant(hasText("Groupe : Groupe des acacias (GA)")))
            .assert(hasAnyDescendant(hasText("Territoire : 1re circonscription de Corse-du-Sud (2A01)")))
            .assert(hasAnyDescendant(hasText("Début du mandat : 18 juillet 2024")))
        // One ballot voted out of two: 50 %, with its definition.
        onNodeWithTag(ParliamentTags.PARTICIPATION).performScrollTo()
            .assert(hasAnyDescendant(hasText("50 %")))
        onNodeWithTag(ParliamentTags.PARTICIPATION_DEFINITION)
            .assert(hasText("Part des scrutins publics où ce membre a voté. Ne mesure pas le travail en commission ni les autres activités."))
        onNodeWithTag(ParliamentTags.vote("an:17:102")).performScrollTo()
            .assert(hasText("Position enregistrée : Non-votant"))
            .assert(hasText("Raison indiquée : présidence de la séance"))
        onNodeWithTag(ParliamentTags.vote("an:17:101")).performScrollTo()
            .assert(hasText("Position enregistrée : Contre"))
            .assert(hasText("par délégation"))
        // The official pages open only after « Vous quittez PPF ».
        onNodeWithTag(ParliamentTags.INTERESTS_LINK).performScrollTo().performClick()
        onNodeWithTag(ParliamentTags.LEAVE.dialog).assertExists()
        onNodeWithText("www.hatvp.fr", substring = true).assertExists()
        onNodeWithTag(ParliamentTags.LEAVE.stay).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.LEAVE.dialog).assertDoesNotExist()
        onNodeWithTag(ParliamentTags.OFFICIAL_LINK).performScrollTo().assertExists()
        onNodeWithTag(ParliamentTags.EXAMPLE).performScrollTo().assertExists()
    }

    @Test
    fun aCorrectionIsShownBesideTheRecordedVote() = parliamentTest {
        show(server(), width = 400, height = 760)
        downloadMembers()
        downloadMonth("scrutins-an-2026-10")
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("an:PA900002")))
        onNodeWithTag(ParliamentTags.member("an:PA900002")).performClick()
        waitForTag(ParliamentTags.MEMBER_PAGE)
        onNodeWithTag(ParliamentTags.vote("an:17:102")).performScrollTo()
            .assert(hasText("Position enregistrée : Contre"))
            .assert(hasText("Mise au point : ce membre a déclaré après le scrutin avoir voulu voter « Pour ». Le vote enregistré reste celui ci-dessus."))
    }

    @Test
    fun aBallotPageHasItsResultItsCountsAndAReadableTableThenBackClosesEachPage() = parliamentTest {
        val state = show(server(), width = 1000)
        downloadMembers()
        downloadMonth("scrutins-an-2026-10")
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.ballot("an:17:101")))
        onNodeWithTag(ParliamentTags.ballot("an:17:101")).performClick()
        waitForTag(ParliamentTags.BALLOT_PAGE)
        onNodeWithTag(ParliamentTags.section("result")).assert(hasText("Résultat : adopté"))
        onNodeWithText("Assemblée nationale · Scrutin public solennel · Scrutin n° 101 du 8 octobre 2026").assertExists()
        onNodeWithTag(ParliamentTags.section("counts")).performScrollTo()
            .assert(hasAnyDescendant(hasText("Votants : 2")))
            .assert(hasAnyDescendant(hasText("Pour : 1")))
        onNodeWithTag(ParliamentTags.groupRow("an:PO900001")).performScrollTo()
            .assert(hasContentDescription("Groupe des tilleuls (GT) : 1 pour, 0 contre, 0 abstention, 0 non-votant"))
        onNodeWithTag(ParliamentTags.position(Position.FOR)).performScrollTo().assert(hasText("Pour (1)"))
        onNodeWithTag(ParliamentTags.position(Position.ABSTENTION)).performScrollTo().assert(hasText("Abstention (0)"))
        onNodeWithTag(ParliamentTags.DOSSIER_LINK).performScrollTo().assertExists()
        // A member opens from the ballot; back closes one page at a time, then the screen.
        onNodeWithTag(ParliamentTags.member("an:PA900001")).performScrollTo().performClick()
        waitForTag(ParliamentTags.MEMBER_PAGE)
        onNodeWithTag(ParliamentTags.BACK).performClick()
        waitForTag(ParliamentTags.BALLOT_PAGE)
        onNodeWithTag(ParliamentTags.BACK).performClick()
        waitForTag(ParliamentTags.LIST)
        onNodeWithTag(ParliamentTags.BACK).performClick()
        waitForIdle()
        assertFalse(state().isOpen)
        onNodeWithTag(ParliamentTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun aSenatorHasTheSameSectionsWithVotesComingSoon() = parliamentTest {
        show(server(), width = 400, height = 760)
        downloadMembers()
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("senat:90001A")))
        onNodeWithTag(ParliamentTags.member("senat:90001A")).performClick()
        waitForTag(ParliamentTags.MEMBER_PAGE)
        onNodeWithTag(ParliamentTags.PARTICIPATION).performScrollTo().assert(hasAnyDescendant(hasText("Votes du Sénat : bientôt")))
        onNodeWithTag(ParliamentTags.PARTICIPATION_DEFINITION).assertExists()
        onNodeWithTag(ParliamentTags.section("votes")).performScrollTo().assert(hasAnyDescendant(hasText("Votes du Sénat : bientôt")))
        onNodeWithTag(ParliamentTags.section("links")).performScrollTo().assertExists()
        onNodeWithTag(ParliamentTags.OFFICIAL_LINK).assertExists()
    }

    @Test
    fun oldDataAreFlaggedAfterTwoWeeks() = parliamentTest {
        show(server(), width = 400, now = ParliamentUiFixtures.NOW + 86_400L * 15)
        onNodeWithTag(ParliamentTags.STALE).assert(hasAnyDescendant(hasText("Ces données n’ont pas été mises à jour depuis le 7 novembre 2026.")))
    }

    @Test
    fun withoutServerTheBundledExamplesAreShownWithTheirBanner() = parliamentTest {
        val files = packages + ("index" to keys.sign(ParliamentUiFixtures.index(packages)).toJson().encodeToByteArray())
        show(null, width = 400, height = 760, bundled = { files[it] })
        onNodeWithTag(ParliamentTags.DOWNLOAD_MEMBERS).assertDoesNotExist()
        onNodeWithTag(ParliamentTags.EXAMPLE).assertExists()
        onNodeWithText("Jeu d’exemple intégré à l’application.").assertExists()
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(ParliamentTags.member("an:PA900001")))
    }

    @Test
    fun theSheetsOfVoirShowTheLinksOnlyWithTheOfficialIdentifier() = parliamentTest {
        val files = packages + ("index" to keys.sign(ParliamentUiFixtures.index(packages)).toJson().encodeToByteArray())
        val tagged = person(listOf("ppf-official-id", "an:PA900001"))
        val untagged = person(null)
        val bill = bill(listOf("ppf-official-id", "an-dossier:DLR5L17N90001"))
        val untaggedBill = bill(null)
        val state = show(null, width = 400, height = 760, bundled = { files[it] }) { parliament ->
            if (!parliament.isOpen) {
                PersonOfficialVotesLink(untagged, parliament)
                BillOfficialVotes(untaggedBill, parliament)
                BillOfficialVotes(bill, parliament)
                PersonOfficialVotesLink(tagged, parliament)
            } else {
                ParliamentScreen(parliament)
            }
        }
        state().close()
        waitForIdle()
        // Only one of each: the untagged ones show nothing.
        assertEquals(1, onAllNodesWithTag(ParliamentTags.PERSON_LINK).fetchSemanticsNodes().size)
        assertEquals(1, onAllNodesWithTag(ParliamentTags.BILL_VOTES).fetchSemanticsNodes().size)
        onNodeWithTag(ParliamentTags.ballot("an:17:103")).assertExists()
        onNodeWithTag(ParliamentTags.ballot("an:17:101")).assertExists()
        onNodeWithTag(ParliamentTags.PERSON_LINK).performClick()
        waitForTag(ParliamentTags.MEMBER_PAGE)
        onNodeWithText("Élise Arnaud").assertExists()
    }

    @Test
    fun theCardOpensTheScreenFromThePowerAxisOfVoirAndBackClosesIt() = parliamentTest {
        val database = JvmDatabase.inMemory()
        val dependencies = AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(database),
            contentFollowStore = ContentFollowStore(database),
            database = database,
        )
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        onNodeWithText("Voir").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(VoirTags.card("manifestants-journees")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(VoirTags.tab(1)).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.CARD).performScrollTo()
            .assert(hasText("Tous les parlementaires et leurs votes"))
            .performClick()
        waitForTag(ParliamentTags.SCREEN)
        onNodeWithTag(ParliamentTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ParliamentTags.CARD).assertExists()
        onNodeWithTag(ShellTags.CONTENT).assertExists()
    }

    private fun event(identifier: String, tag: List<String>?): SignedEvent =
        SignedEvent("a".repeat(64), "b".repeat(64), 1, 30023, listOfNotNull(listOf("d", identifier), tag), "", "c".repeat(128))

    private fun article(identifier: String, title: String, type: ArticleType, tag: List<String>?) =
        Article(event(identifier, tag), ContentAddress(30023, "b".repeat(64), identifier), title, null, emptyList(), type, ContentOrigin.HUMAN, null)

    private fun person(tag: List<String>?) = PublicPerson(article(if (tag == null) "sans-id" else "avec-id", "[EXEMPLE] Élise Arnaud", ArticleType.PERSONNE, tag), emptyList(), null, null, emptyList(), emptyList(), emptyList())

    private fun bill(tag: List<String>?) = LegislativeText(
        article(if (tag == null) "texte-sans-id" else "texte-avec-id", "[EXEMPLE] Projet de loi sur les tilleuls", ArticleType.TEXTE, tag),
        null, TextStage.TABLED, emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )
}
