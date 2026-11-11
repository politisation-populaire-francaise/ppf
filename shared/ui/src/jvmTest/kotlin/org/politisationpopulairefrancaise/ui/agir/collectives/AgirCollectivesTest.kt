package org.politisationpopulairefrancaise.ui.agir.collectives

import org.politisationpopulairefrancaise.ui.agir.TEST_WINDOW_HEIGHT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveFormat
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveScope
import org.politisationpopulairefrancaise.core.agir.collectives.CollectiveType
import org.politisationpopulairefrancaise.core.groups.Group
import org.politisationpopulairefrancaise.core.groups.GroupStore
import org.politisationpopulairefrancaise.core.groups.RoomModeration
import org.politisationpopulairefrancaise.core.groups.RoomRole
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.core.urne.UrneTokenStore
import org.politisationpopulairefrancaise.core.urne.VoterStatus
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.agenda.ProposeTags
import org.politisationpopulairefrancaise.ui.agir.agenda.rememberAgendaLibrary
import org.politisationpopulairefrancaise.ui.agir.calls.rememberCallsLibrary
import org.politisationpopulairefrancaise.ui.groups.RoomsFixtures
import org.politisationpopulairefrancaise.ui.groups.RoomsServer
import org.politisationpopulairefrancaise.ui.groups.rememberGroupsLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import org.politisationpopulairefrancaise.ui.urne.LocalVoting
import org.politisationpopulairefrancaise.ui.urne.VotingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The collectifs of "Agir" (M24, step 1, D-087): the directory filters on the device, an account not
 * validated in person gets the explanation and never the wizard, a validated account founds a
 * collectif after the charter and the warning, and the page lists the collectif's events and offers
 * its composers to its founder, administrators and moderators only. Every content is marked [EXEMPLE].
 */
@OptIn(ExperimentalTestApi::class)
class AgirCollectivesTest {
    private val me = LocalEventSigner(RoomsFixtures.phrase.deriveKeys())
    private val navigations = mutableListOf<Space>()
    private lateinit var library: CollectivesLibrary
    private lateinit var session: AccountSession

    private fun collective(
        signer: LocalEventSigner,
        id: String,
        name: String,
        territory: String,
        scope: CollectiveScope,
        type: CollectiveType,
        themes: List<String>,
        code: String?,
    ): SignedEvent = runBlocking {
        signer.sign(
            CollectiveFormat.build(
                author = signer.publicKey, id = id, name = name, about = "[EXEMPLE] Présentation du collectif.", territory = territory,
                createdAt = currentEpochSeconds() - 3_600, scope = scope, type = type, themes = themes, territoryCode = code,
            ),
        )
    }

    /** The rooms of [RoomsFixtures] (plain rooms), three collectifs founded by another member and one founded by the user. */
    private fun server(): RoomsServer = RoomsFixtures.server().apply {
        seed(collective(RoomsFixtures.other, CYCLISTES, "[EXEMPLE] Collectif des cyclistes", "Lyon", CollectiveScope.LOCAL, CollectiveType.COLLECTIF, listOf("vélo"), "com:69123"))
        seed(collective(RoomsFixtures.other, CROIX_ROUSSE, "[EXEMPLE] Assemblée de la Croix-Rousse", "Lyon", CollectiveScope.QUARTIER, CollectiveType.ASSEMBLEE, listOf("logement", "santé"), "arm:69384"))
        seed(collective(RoomsFixtures.other, PARIS_SANTE, "[EXEMPLE] Comité santé", "Paris", CollectiveScope.LOCAL, CollectiveType.COMITE, listOf("santé"), "com:75056"))
        seed(collective(me, LECTURE, "[EXEMPLE] Club de lecture", "Nantes", CollectiveScope.LOCAL, CollectiveType.CLUB, listOf("culture"), null))
    }

    /** The voting of the shell, with the level the server last gave the account (null: no status yet). */
    private fun voting(level: String?): VotingState =
        VotingState(null, UrneTokenStore(JvmDatabase.inMemory()), CoroutineScope(Dispatchers.Unconfined)).also { state ->
            if (level != null) state.status = VoterStatus(level = level, levelWeek = "2026-11-02", pending = emptyList())
        }

    private fun ComposeUiTest.show(dependencies: AppDependencies, voting: VotingState?, home: List<String> = emptyList(), width: Int = 1000, height: Int = 1400) {
        setContent {
            Box(Modifier.requiredSize(width.dp, minOf(height, TEST_WINDOW_HEIGHT).dp)) {
                PpfTheme {
                    CompositionLocalProvider(LocalVoting provides voting) {
                        val account = rememberAccountSession(dependencies.identityStore)
                        session = account
                        val groups = rememberGroupsLibrary(dependencies)
                        val scope = rememberCoroutineScope()
                        library = remember(groups) {
                            CollectivesLibrary(groups, dependencies.contentStore, dependencies.remote, urne = null, scope = scope, homeSource = { home })
                        }
                        CollectivesScreen(
                            library = library,
                            session = account,
                            windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                            agenda = rememberAgendaLibrary(dependencies, start = false),
                            calls = rememberCallsLibrary(dependencies, start = false),
                            onBack = {},
                            onNavigate = { navigations += it },
                            onOpenRooms = {},
                            onCreateRoom = {},
                            onOpenMobilization = {},
                            onOpenCall = {},
                        )
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 20_000) { this@AgirCollectivesTest::library.isInitialized && library.collectives.size == 4 }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitTag(tag: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(tag: String): Int = onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun ComposeUiTest.awaitAccount() = waitUntil(timeoutMillis = 20_000) { this@AgirCollectivesTest::session.isInitialized && session.signer != null }

    @Test
    fun theDirectoryShowsOnlyTheCollectivesAndFiltersThemOnTheDevice() = runComposeUiTest {
        val server = server()
        show(RoomsFixtures.withoutAccount(server), voting = null, home = listOf("arm:69384", "com:69123", "epci:200046977", "dep:69"))
        awaitTag(CollectivesTags.CARD + CYCLISTES)
        // The plain rooms of the same directory are not collectifs.
        assertEquals(0, count(CollectivesTags.CARD + RoomsFixtures.SANTE))
        assertTrue(library.shown.none { it.id == RoomsFixtures.LIVREURS })
        // Near the home first (the district, then the town), then by name.
        assertEquals(listOf(CROIX_ROUSSE, CYCLISTES, LECTURE, PARIS_SANTE), library.shown.map { it.id })

        onNodeWithTag(CollectivesTags.THEME + "santé").performClick()
        waitForIdle()
        assertEquals(listOf(CROIX_ROUSSE, PARIS_SANTE), library.shown.map { it.id })
        assertEquals(0, count(CollectivesTags.CARD + CYCLISTES))

        onNodeWithTag(CollectivesTags.SCOPE + CollectiveScope.QUARTIER.tagValue).performClick()
        waitForIdle()
        assertEquals(listOf(CROIX_ROUSSE), library.shown.map { it.id })
        awaitTag(CollectivesTags.CARD + CROIX_ROUSSE)
        assertEquals(0, count(CollectivesTags.CARD + PARIS_SANTE))

        onNodeWithTag(CollectivesTags.CLEAR).performClick()
        waitForIdle()
        onNodeWithTag(CollectivesTags.TYPE + CollectiveType.COMITE.tagValue).performClick()
        waitForIdle()
        assertEquals(listOf(PARIS_SANTE), library.shown.map { it.id })

        onNodeWithTag(CollectivesTags.CLEAR).performClick()
        waitForIdle()
        onNodeWithTag(CollectivesTags.NEAR).performClick()
        waitForIdle()
        assertEquals(listOf(CROIX_ROUSSE, CYCLISTES), library.shown.map { it.id })

        // Nothing of the search was sent: reading the directory publishes nothing.
        assertTrue(server.published.isEmpty())
    }

    @Test
    fun withoutAKnownHomeTheNearMeFilterIsHidden() = runComposeUiTest {
        show(RoomsFixtures.withoutAccount(server()), voting = null)
        awaitTag(CollectivesTags.CARD + CYCLISTES)
        assertEquals(0, count(CollectivesTags.NEAR))
    }

    @Test
    fun anAccountNotValidatedInPersonGetsTheExplanationAndNoWizard() = runComposeUiTest {
        show(RoomsFixtures.withAccount(server()), voting("non-verifie"), width = 360, height = 740)
        awaitAccount()
        onNodeWithTag(CollectivesTags.CREATE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.NEEDS_VERIFIED)
        onNodeWithText("Votre compte n’est pas encore validé en personne.").assertExists()
        assertEquals(0, count(CollectivesTags.NAME))
        assertEquals(0, count(CollectivesTags.SUBMIT))
        // A salon stays open to all; the validation is explained in "Mon espace".
        onNodeWithTag(CollectivesTags.CREATE_ROOM).performScrollTo().assertIsEnabled()
        onNodeWithTag(CollectivesTags.OPEN_MY_SPACE).performScrollTo().performClick()
        assertEquals(listOf(Space.MON_ESPACE), navigations)
    }

    @Test
    fun withoutAnAccountTheWizardAsksForOne() = runComposeUiTest {
        show(RoomsFixtures.withoutAccount(server()), voting = null, width = 360, height = 740)
        onNodeWithTag(CollectivesTags.CREATE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.NEED_ACCOUNT)
        assertEquals(0, count(CollectivesTags.NAME))
    }

    @Test
    fun aValidatedAccountFoundsACollectiveAfterTheCharterAndTheWarning() = runComposeUiTest {
        val server = server()
        val dependencies = RoomsFixtures.withAccount(server)
        show(dependencies, voting("verifie"), width = 360, height = 740)
        awaitAccount()
        onNodeWithTag(CollectivesTags.CREATE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.NAME)

        // Step 1: name, territory, scope.
        onNodeWithTag(CollectivesTags.NEXT).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(CollectivesTags.NAME).performTextInput("[EXEMPLE] Collectif des jardins partagés")
        onNodeWithTag(CollectivesTags.TERRITORY).performTextInput("Grenoble")
        onNodeWithTag(CollectivesTags.SCOPE_CHOICE + CollectiveScope.LOCAL.tagValue).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CollectivesTags.NEXT).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()

        // Step 2: presentation, type, themes (French by default).
        awaitTag(CollectivesTags.ABOUT)
        onNodeWithTag(CollectivesTags.ABOUT).performTextInput("[EXEMPLE] Des potagers au pied des immeubles.")
        onNodeWithTag(CollectivesTags.TYPE_CHOICE + CollectiveType.COLLECTIF.tagValue).performScrollTo().performClick()
        onNodeWithTag(CollectivesTags.THEMES).performScrollTo().performTextInput("Jardins, alimentation")
        waitForIdle()
        onNodeWithTag(CollectivesTags.NEXT).performScrollTo().performClick()
        waitForIdle()

        // Step 3: the internal charter is optional.
        awaitTag(CollectivesTags.INTERNAL_CHARTER)
        onNodeWithTag(CollectivesTags.NEXT).performScrollTo().performClick()
        waitForIdle()

        // Step 4: public and open only, the charter unticked, the warning, « Annuler » before « Créer le collectif ».
        awaitTag(CollectivesTags.WARNING)
        onNodeWithTag(CollectivesTags.SOON).assertExists()
        onNodeWithTag(CollectivesTags.SUBMIT).performScrollTo().assertIsNotEnabled()
        val cancel = onNodeWithTag(CollectivesTags.CANCEL).fetchSemanticsNode().boundsInRoot
        val submit = onNodeWithTag(CollectivesTags.SUBMIT).fetchSemanticsNode().boundsInRoot
        assertTrue(cancel.top < submit.top || (cancel.top == submit.top && cancel.left < submit.left), "« Annuler » comes before « Créer le collectif »")
        assertTrue(server.published.isEmpty(), "nothing is sent before the charter")

        onNodeWithTag(CollectivesTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(CollectivesTags.SUBMIT).performScrollTo().assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 20_000) { server.published.any { it.kind == Group.KIND } }
        awaitTag(CollectivesTags.PAGE)

        val description = server.published.single { it.kind == Group.KIND }
        assertEquals(me.publicKey.hex, description.pubkey)
        assertEquals(listOf("[EXEMPLE] Collectif des jardins partagés"), description.tagValues(Group.TAG_NAME))
        assertEquals(listOf(CollectiveFormat.VERSION), description.tagValues(CollectiveFormat.TAG_COLLECTIVE))
        assertEquals(listOf("local"), description.tagValues(CollectiveFormat.TAG_SCOPE))
        assertEquals(listOf("collectif"), description.tagValues(CollectiveFormat.TAG_TYPE))
        assertEquals(listOf("jardins", "alimentation"), description.tagValues(CollectiveFormat.TAG_THEME))
        assertEquals(listOf("fr"), description.tagValues(CollectiveFormat.TAG_LANGUAGE))
        assertEquals(listOf(CollectiveFormat.VISIBILITY_PUBLIC), description.tagValues(CollectiveFormat.TAG_VISIBILITY))
        assertEquals(listOf(CollectiveFormat.JOIN_OPEN), description.tagValues(CollectiveFormat.TAG_JOIN))
        assertEquals(null, CollectiveFormat.problem(description.tags))
        // The founder joined it on this device, and only there.
        val id = description.tagValues("d").single()
        waitUntil(timeoutMillis = 10_000) { GroupStore(dependencies.database!!).isMember(id) }
        // Its page offers the composers to its founder.
        onNodeWithTag(CollectivesTags.PROPOSE_EVENT).assertIsEnabled()
    }

    @Test
    fun thePageListsTheEventsOfTheCollectiveAndOffersItsComposersToItsOrganisersOnly() = runComposeUiTest {
        val server = server()
        val now = currentEpochSeconds()
        val ride = runBlocking {
            RoomsFixtures.other.sign(
                CollectiveFormat.withCollective(
                    Mobilization.build(
                        RoomsFixtures.other.publicKey, "balade-cyclistes", now - 600, "[EXEMPLE] Balade du collectif",
                        start = now + 86_400, location = "[EXEMPLE] Place Bellecour", type = MobilizationType.RASSEMBLEMENT, territory = "Lyon",
                    ),
                    CYCLISTES,
                ),
            )
        }
        val elsewhere = runBlocking {
            RoomsFixtures.other.sign(
                Mobilization.build(
                    RoomsFixtures.other.publicKey, "fete-quartier", now - 600, "[EXEMPLE] Fête de quartier",
                    start = now + 2 * 86_400, location = "[EXEMPLE] Square", type = MobilizationType.RASSEMBLEMENT, territory = "Lyon",
                ),
            )
        }
        server.seed(ride)
        server.seed(elsewhere)
        // The founder of the Croix-Rousse names the user moderator (D-131).
        server.seed(runBlocking { RoomsFixtures.other.sign(RoomModeration.putUser(RoomsFixtures.other.publicKey, CROIX_ROUSSE, me.publicKey.hex, RoomRole.MODERATOR, now - 300)) })
        show(RoomsFixtures.withAccount(server), voting("non-verifie"))
        awaitAccount()

        // A collectif of another member, where the user holds no role: its events, no composer.
        onNodeWithTag(CollectivesTags.CARD + CYCLISTES).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.PAGE)
        awaitTag(CollectivesTags.EVENT + "balade-cyclistes")
        assertEquals(0, count(CollectivesTags.EVENT + "fete-quartier"))
        assertEquals(0, count(CollectivesTags.PROPOSE_EVENT))
        assertEquals(0, count(CollectivesTags.LAUNCH_CALL))
        onNodeWithTag(CollectivesTags.ROLES).assertExists()
        // Joining publishes nothing.
        onNodeWithTag(CollectivesTags.JOIN).performScrollTo().performClick()
        waitForIdle()
        awaitTag(CollectivesTags.LEAVE)
        assertTrue(server.published.isEmpty(), "joining publishes nothing")

        // Its own collectif: the founder proposes an event in its name, through the composer of the agenda.
        onNodeWithTag(CollectivesTags.PAGE_BACK).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.CARD + LECTURE)
        onNodeWithTag(CollectivesTags.CARD + LECTURE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.PROPOSE_EVENT)
        onNodeWithTag(CollectivesTags.LAUNCH_CALL).assertExists()
        onNodeWithTag(CollectivesTags.PROPOSE_EVENT).performScrollTo().performClick()
        waitForIdle()
        awaitTag(ProposeTags.FORM)
        onNodeWithTag(CollectivesTags.COMPOSER_NOTE).assertExists()
        onNodeWithText("Publié au nom du collectif « Club de lecture »", substring = true).assertExists()
        onNodeWithTag(ProposeTags.CLOSE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.PAGE)

        // A collectif where the user is a moderator: the composers once its public roles are read.
        onNodeWithTag(CollectivesTags.PAGE_BACK).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.CARD + CROIX_ROUSSE)
        onNodeWithTag(CollectivesTags.CARD + CROIX_ROUSSE).performClick()
        waitForIdle()
        awaitTag(CollectivesTags.PROPOSE_EVENT)
        onNodeWithTag(CollectivesTags.LAUNCH_CALL).assertExists()
    }

    companion object {
        const val CYCLISTES = "cyclistes-lyon"
        const val CROIX_ROUSSE = "assemblee-croix-rousse-lyon"
        const val PARIS_SANTE = "comite-sante-paris"
        const val LECTURE = "club-lecture-nantes"
    }
}
