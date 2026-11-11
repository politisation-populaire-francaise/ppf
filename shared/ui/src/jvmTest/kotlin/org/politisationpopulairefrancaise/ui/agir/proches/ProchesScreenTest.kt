package org.politisationpopulairefrancaise.ui.agir.proches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.agir.proches.InMemoryProchesStorage
import org.politisationpopulairefrancaise.core.agir.proches.Proche
import org.politisationpopulairefrancaise.core.agir.proches.ProcheStep
import org.politisationpopulairefrancaise.core.agir.proches.ProchesData
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.identity.StoredIdentity
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.publication.FetchResult
import org.politisationpopulairefrancaise.core.publication.PublicationResult
import org.politisationpopulairefrancaise.core.publication.RemoteEvents
import org.politisationpopulairefrancaise.core.publication.RemoteFilter
import org.politisationpopulairefrancaise.core.publication.TagFilter
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.currentEpochSeconds
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.account.AccountSession
import org.politisationpopulairefrancaise.ui.account.AccountUiState
import org.politisationpopulairefrancaise.ui.account.rememberAccountSession
import org.politisationpopulairefrancaise.ui.agir.AgirFixtures
import org.politisationpopulairefrancaise.ui.agir.AgirRubrics
import org.politisationpopulairefrancaise.ui.agir.AgirScreen
import org.politisationpopulairefrancaise.ui.agir.AgirTags
import org.politisationpopulairefrancaise.ui.agir.RecordingUriHandler
import org.politisationpopulairefrancaise.ui.agir.awaitTag
import org.politisationpopulairefrancaise.ui.agir.count
import org.politisationpopulairefrancaise.ui.agir.security.RegisterErasable
import org.politisationpopulairefrancaise.ui.content.ContentLibrary
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A server that records everything it is asked: the list of relatives must never reach it. */
private class WatchingServer : RemoteEvents {
    val published = CopyOnWriteArrayList<SignedEvent>()
    val requests = CopyOnWriteArrayList<String>()

    override suspend fun publish(event: SignedEvent): PublicationResult {
        published += event
        return PublicationResult.Accepted(event.id)
    }

    override suspend fun fetch(filter: RemoteFilter): FetchResult {
        requests += filter.toString()
        return FetchResult.Events(emptyList())
    }

    override suspend fun fetchByTags(tags: List<TagFilter>, filter: RemoteFilter): FetchResult {
        requests += "$tags $filter"
        return FetchResult.Events(emptyList())
    }
}

/**
 * "Agir" alone, with the list of relatives handed in as the shell does (its quick erase part
 * registered), a storage kept by the test, a server that records every request and a clipboard
 * that records what is copied.
 */
private class ProchesHarness(initial: String? = null, withAccount: Boolean = false) {
    val server = WatchingServer()
    val identity = InMemoryIdentityStore(if (withAccount) StoredIdentity.of(IdentityKeys.generate(), null) else null)
    val dependencies: AppDependencies =
        AppDependencies.withDatabase(identity, JvmDatabase.inMemory(), editorialTrust = AgirFixtures.trust, remote = server)
    val storage = InMemoryProchesStorage(initial)
    val copied = CopyOnWriteArrayList<String>()
    private val recorder: (String) -> Unit = { text -> copied += text }
    lateinit var library: ContentLibrary
    lateinit var session: AccountSession
    lateinit var proches: ProchesState

    @Composable
    fun Content(width: Int, height: Int = 800, dark: Boolean = false) {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
            PpfTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalUriHandler provides RecordingUriHandler(),
                    LocalProchesStorage provides storage,
                    LocalProchesClipboard provides recorder,
                ) {
                    library = rememberContentLibrary(dependencies)
                    session = rememberAccountSession(dependencies.identityStore)
                    val state = rememberProchesState(dependencies, session)
                    proches = state
                    RegisterErasable(dependencies, ProchesState.ERASABLE_ID) { state.eraseFromDevice() }
                    AgirScreen(
                        dependencies = dependencies,
                        library = library,
                        session = session,
                        windowClass = WindowClass.of(width.toFloat(), height.toFloat()),
                        modifier = Modifier.fillMaxSize(),
                        proches = state,
                    )
                }
            }
        }
    }

    fun stored(): ProchesData = ProchesData.decode(storage.value)
}

private const val NICKNAME = "[EXEMPLE] Tante M."

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openList(harness: ProchesHarness, width: Int = 360, dark: Boolean = false) {
    setContent { harness.Content(width, dark = dark) }
    awaitTag(AgirTags.HOME)
    // The guides and courses bundled with the application are on the device before the list reads them.
    waitUntil(timeoutMillis = 60_000) { harness.library.guides.isNotEmpty() }
    onNodeWithTag(AgirTags.RUBRIC + AgirRubrics.PROCHES.id).performScrollTo().performClick()
    waitForIdle()
    awaitTag(ProchesTags.SCREEN)
    waitUntil(timeoutMillis = 20_000) { harness.proches.loaded }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.click(tag: String) {
    onNodeWithTag(tag).performScrollTo().performClick()
    waitForIdle()
}

/**
 * « Mes proches à mobiliser » (AG-63), written without compiling (docs/integration/agir-proches.md):
 * the warning before the first entry and « Annuler » that keeps nothing; an entry added, moved along
 * the ladder, shared and removed; the reminders due first; nothing ever sent to the server; the quick
 * erase and « Effacer mes clés » that empty the list; on a phone (360 dp), a tablet (700 dp, dark) and
 * a desktop (1000 dp). Every nickname is an [EXEMPLE].
 */
@OptIn(ExperimentalTestApi::class)
class ProchesScreenTest {

    @Test
    fun theWarningComesBeforeTheFirstEntryAndCancelKeepsNothing() {
        for (width in listOf(360, 700, 1000)) {
            val harness = ProchesHarness()
            runComposeUiTest {
                openList(harness, width, dark = width == 700)
                awaitTag(ProchesTags.INTRO)
                onNodeWithTag(ProchesTags.PRIVACY).performScrollTo().assertExists()
                click(ProchesTags.ADD)
                awaitTag(ProchesTags.WARNING)
                assertEquals(0, count(ProchesTags.EDITOR), "no form before the warning at $width dp")
                onNodeWithText("information sensible", substring = true).assertExists()
                onNodeWithText("Un surnom suffit : n’écrivez jamais son nom complet", substring = true).assertExists()
                // « Annuler » comes before « Commencer ma liste ».
                val cancel = onNodeWithTag(ProchesTags.WARNING_CANCEL).fetchSemanticsNode().boundsInRoot
                val start = onNodeWithTag(ProchesTags.WARNING_START).fetchSemanticsNode().boundsInRoot
                assertTrue(cancel.top < start.top || (cancel.top == start.top && cancel.left < start.left), "at $width dp: $cancel, $start")

                click(ProchesTags.WARNING_CANCEL)
                assertEquals(0, count(ProchesTags.WARNING))
                awaitTag(ProchesTags.INTRO)
                assertNull(harness.storage.value, "nothing kept at $width dp")
                assertEquals(0, harness.storage.writes)

                // « Commencer ma liste » opens the form, and still writes nothing; « Annuler » there forgets what was typed.
                click(ProchesTags.ADD)
                click(ProchesTags.WARNING_START)
                awaitTag(ProchesTags.EDITOR)
                onNodeWithTag(ProchesTags.NICKNAME).performScrollTo().performClick()
                onNodeWithTag(ProchesTags.NICKNAME).performTextInput(NICKNAME)
                click(ProchesTags.CANCEL)
                assertNull(harness.storage.value)
                assertNull(harness.proches.draft)
            }
        }
    }

    @Test
    fun anEntryIsAddedMovedAlongTheLadderSharedAndRemovedWithoutSendingAnything() {
        val harness = ProchesHarness()
        runComposeUiTest {
            openList(harness)
            click(ProchesTags.ADD)
            click(ProchesTags.WARNING_START)
            awaitTag(ProchesTags.EDITOR)

            // Without a nickname nothing is kept.
            click(ProchesTags.SAVE)
            awaitTag(ProchesTags.NICKNAME_ERROR)
            assertNull(harness.storage.value)

            // Three subjects at most: the fourth chip is disabled.
            listOf("sante", "logement", "travail").forEach { click(ProchesTags.SUBJECT + it) }
            onNodeWithTag(ProchesTags.SUBJECT + "culture").performScrollTo().assertIsNotEnabled()
            click(ProchesTags.SUBJECT + "sante")
            onNodeWithTag(ProchesTags.NICKNAME).performScrollTo().performClick()
            onNodeWithTag(ProchesTags.NICKNAME).performTextInput(NICKNAME)
            click(ProchesTags.STEP + ProcheStep.SHARE.id)
            click(ProchesTags.REMINDER + ProcheReminderChoice.TOMORROW.id)
            click(ProchesTags.SAVE)
            waitUntil(timeoutMillis = 20_000) { harness.stored().entries.size == 1 && count(ProchesTags.EDITOR) == 0 }
            waitForIdle()

            val entry = harness.stored().entries.single()
            assertEquals(NICKNAME, entry.nickname)
            assertEquals(listOf("logement", "travail"), entry.subjects)
            assertEquals(ProcheStep.SHARE, entry.nextStep)
            assertNotNull(entry.reminderDate)
            assertFalse(entry.isDue(harness.proches.today), "a reminder for tomorrow is not due today")
            awaitTag(ProchesTags.ENTRY + entry.id)
            onNodeWithText("Prochaine étape : Partager un contenu").assertExists()

            // One rung up the ladder.
            click(ProchesTags.NEXT + entry.id)
            waitUntil(timeoutMillis = 20_000) { harness.stored().entries.single().nextStep == ProcheStep.INVITE }
            waitForIdle()
            onNodeWithText("Prochaine étape : L’inviter à une mobilisation").assertExists()

            // A content of the device on its subjects, copied without a word about the relative.
            waitUntil(timeoutMillis = 20_000) { count(ProchesTags.suggestion(entry.id, 0)) > 0 }
            click(ProchesTags.copy(entry.id, 0))
            val copied = harness.copied.single()
            assertTrue(copied.contains("https://politisation-populaire-francaise.org/"), copied)
            assertFalse(copied.contains("Tante") || copied.contains('?'), copied)

            // Removed after the confirmation: nothing is left on the device.
            click(ProchesTags.REMOVE + entry.id)
            click(ProchesTags.REMOVE_CONFIRM + entry.id)
            waitUntil(timeoutMillis = 20_000) { harness.storage.value == null }
            waitForIdle()
            awaitTag(ProchesTags.INTRO)

            // Nothing of the list ever reached the server.
            assertTrue(harness.server.published.isEmpty(), harness.server.published.toString())
            assertTrue(harness.server.requests.none { "Tante" in it }, harness.server.requests.toString())
        }
    }

    @Test
    fun theRemindersWhoseDayHasComeAreShownFirstAndTheListAdaptsToTheWidth() {
        val yesterday = ParisTime.dateOf(ParisTime.epochDay(currentEpochSeconds()) - 1).toIso()
        val initial = ProchesData(
            entries = listOf(
                Proche("a1", "[EXEMPLE] Collègue", listOf("travail")),
                Proche("b2", "[EXEMPLE] Voisine", listOf("logement"), ProcheStep.INVITE.id, reminder = yesterday),
            ),
        ).encode()
        for (width in listOf(360, 1000)) {
            val harness = ProchesHarness(initial)
            runComposeUiTest {
                openList(harness, width)
                awaitTag(ProchesTags.TODAY)
                awaitTag(ProchesTags.DUE + "b2")
                assertEquals(0, count(ProchesTags.DUE + "a1"))
                val today = onNodeWithTag(ProchesTags.TODAY).fetchSemanticsNode().boundsInRoot
                val list = onNodeWithTag(ProchesTags.LIST).fetchSemanticsNode().boundsInRoot
                assertTrue(today.top < list.top, "« À faire aujourd’hui » first at $width dp")
                assertTrue(onAllNodesWithText("À faire aujourd’hui").fetchSemanticsNodes().size >= 2, "the title and the badge")
                onNodeWithText("Votre liste : 2 sur 12").performScrollTo().assertExists()
                // With entries, « Ajouter un proche » opens the form at once (the warning is for the first one).
                click(ProchesTags.ADD)
                awaitTag(ProchesTags.EDITOR)
                assertEquals(0, count(ProchesTags.WARNING))
                click(ProchesTags.CANCEL)
            }
        }
    }

    @Test
    fun twoCardsShareARowOnADesktopAndStackOnAPhone() {
        val initial = ProchesData(
            entries = listOf(Proche("a1", "[EXEMPLE] Collègue"), Proche("b2", "[EXEMPLE] Voisine")),
        ).encode()
        for ((width, sideBySide) in listOf(360 to false, 1000 to true)) {
            val harness = ProchesHarness(initial)
            runComposeUiTest {
                openList(harness, width)
                awaitTag(ProchesTags.ENTRY + "b2")
                val first = onNodeWithTag(ProchesTags.ENTRY + "a1").fetchSemanticsNode().boundsInRoot
                val second = onNodeWithTag(ProchesTags.ENTRY + "b2").fetchSemanticsNode().boundsInRoot
                assertEquals(sideBySide, first.top == second.top, "at $width dp: $first, $second")
                assertTrue(first.right <= second.left || first.bottom <= second.top, "no overlap at $width dp")
            }
        }
    }

    @Test
    fun theQuickEraseEmptiesTheList() {
        val harness = ProchesHarness(ProchesData(entries = listOf(Proche("a1", "[EXEMPLE] Voisine", listOf("sante")))).encode())
        runComposeUiTest {
            openList(harness)
            awaitTag(ProchesTags.ENTRY + "a1")
            assertTrue(ProchesState.ERASABLE_ID in harness.dependencies.erasables.ids)
            val report = runBlocking { harness.dependencies.erasables.eraseAll() }
            assertTrue(ProchesState.ERASABLE_ID in report.erased, report.toString())
            assertNull(harness.storage.value)
            waitForIdle()
            assertEquals(0, count(ProchesTags.ENTRY + "a1"))
            awaitTag(ProchesTags.INTRO)
        }
    }

    @Test
    fun erasingTheKeysErasesTheListToo() {
        val harness = ProchesHarness(ProchesData(entries = listOf(Proche("a1", "[EXEMPLE] Voisine"))).encode(), withAccount = true)
        runComposeUiTest {
            setContent { harness.Content(360) }
            waitUntil(timeoutMillis = 20_000) { harness.session.state is AccountUiState.Ready }
            waitForIdle()
            assertNotNull(harness.storage.value, "the list is there while the keys are")
            runOnIdle { harness.session.signOut() }
            waitUntil(timeoutMillis = 20_000) { harness.storage.value == null }
            assertTrue(harness.server.published.isEmpty())
        }
    }
}
