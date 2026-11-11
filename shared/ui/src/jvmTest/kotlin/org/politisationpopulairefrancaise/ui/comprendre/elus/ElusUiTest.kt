package org.politisationpopulairefrancaise.ui.comprendre.elus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.comprendre.DecisionLevel
import org.politisationpopulairefrancaise.core.db.PpfDatabase
import org.politisationpopulairefrancaise.core.elus.ChosenCommune
import org.politisationpopulairefrancaise.core.elus.ElusStore
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.social.FollowStore
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.comprendre.ComprendreDetailTags
import org.politisationpopulairefrancaise.ui.content.ComprendreTags
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The directory of elected officials in "Comprendre" (D-047): the box of the « Qui décide de ça ? »
 * sheets without commune, the complete choice of a commune against a fake server, the official
 * directories when there is no server or it does not answer, and the page « Mes élus ».
 */
@OptIn(ExperimentalTestApi::class)
class ElusUiTest {

    private val sheet = "ecole"

    /** The tag of the line of test put in the slot under each official. */
    private val EXTRA = "elus-extra-test"

    /**
     * Each test walks a whole journey (several screens and a dialog): three minutes instead of the
     * default minute, so that a busy machine (several builds at once) does not fail it.
     */
    private fun elusTest(block: suspend ComposeUiTest.() -> Unit) =
        runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private fun dependencies(database: PpfDatabase, server: ElusTestServer?, dataSaver: Boolean = false) = AppDependencies(
        identityStore = InMemoryIdentityStore(),
        contentStore = LocalEventStore(database),
        remote = server,
        followStore = FollowStore(database),
        database = database,
        contentFollowStore = ContentFollowStore(database),
        dataSaver = { dataSaver },
    )

    private fun ComposeUiTest.openComprendre(dependencies: AppDependencies, width: Int = 400, height: Int = 800, extra: (@androidx.compose.runtime.Composable (org.politisationpopulairefrancaise.core.elus.ElectedOfficial) -> Unit)? = null) {
        setContent {
            CompositionLocalProvider(LocalElectedOfficialExtra provides extra) {
                Box(Modifier.requiredSize(width.dp, height.dp)) { PpfApp(dependencies) }
            }
        }
        if (width < 600) {
            onNodeWithTag(ShellTags.MENU_BUTTON).performClick()
            waitForIdle()
        }
        onNodeWithText("Comprendre").performClick()
        waitForIdle()
        if (width < 600) {
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComprendreTags.WHO_LIST).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openSheet() {
        waitFor(ComprendreTags.CARD + sheet)
        onNodeWithTag(ComprendreTags.CARD + sheet).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComprendreDetailTags.ELUS).assertExists()
    }

    private fun ComposeUiTest.waitFor(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.typePostalCode(code: String) {
        waitFor(ElusTags.POSTAL_CODE)
        onNodeWithTag(ElusTags.POSTAL_CODE).performTextInput(code)
        onNodeWithTag(ElusTags.CONTINUE).performClick()
    }

    @Test
    fun withoutServerTheBoxLeadsToTheOfficialDirectoriesBehindTheWarning() = elusTest {
        openComprendre(dependencies(JvmDatabase.inMemory(), server = null))
        openSheet()
        onNodeWithText("annuaire officiel des élus", substring = true).performScrollTo()
        onNodeWithTag(ElusTags.BOX_CHOOSE).assertDoesNotExist()
        onNodeWithTag(ElusTags.BOX_DIRECTORIES).performScrollTo().performClick()
        waitForIdle()

        // The page « Mes élus » offers the official directories (option C).
        onNodeWithTag(ElusTags.PAGE).assertExists()
        onNodeWithTag(ElusTags.CHOOSE).assertDoesNotExist()
        onNodeWithText("ne contient pas encore l’annuaire des élus", substring = true).assertExists()
        onNodeWithTag(ElusTags.directory("senat")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.LEAVE_DIALOG).assertExists()
        onNodeWithText("pas sur PPF : www.senat.fr", substring = true).assertExists()
        onNodeWithTag(ElusTags.LEAVE_STAY).performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.LEAVE_DIALOG).assertDoesNotExist()

        // Back to the home: the entry « Mes élus » is there.
        onNodeWithTag(ElusTags.PAGE_BACK).performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.HOME_ENTRY, useUnmergedTree = true).performScrollTo().assert(hasAnyDescendant(hasText("Les annuaires officiels pour trouver vos élus.")))
    }

    @Test
    fun withAServerTheBoxOffersToIndicateTheCommune() = elusTest {
        val server = ElusTestServer()
        openComprendre(dependencies(JvmDatabase.inMemory(), server))
        openSheet()
        onNodeWithTag(ElusTags.BOX_CHOOSE).performScrollTo()
        onNodeWithText("Indiquez votre commune pour voir ici", substring = true).assertExists()
        onNodeWithText("sera intégré à l’application", substring = true).assertDoesNotExist()
        // Nothing was asked to the server before a gesture.
        assertEquals(emptyList(), server.requests.toList())
    }

    @Test
    fun choosingACommuneNamesItsOfficialsUnderEachLevel() = elusTest {
        val server = ElusTestServer()
        val database = JvmDatabase.inMemory()
        openComprendre(dependencies(database, server))
        openSheet()
        onNodeWithTag(ElusTags.BOX_CHOOSE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.PRIVACY).assertExists()
        onNodeWithText("ne garde pas son adresse IP", substring = true).assertExists()

        // A wrong postal code is refused on the device.
        typePostalCode("692")
        waitFor(ElusTags.CHOOSER_ERROR)
        onNodeWithText("cinq chiffres", substring = true).assertExists()
        assertEquals(emptyList(), server.requests.toList())

        onNodeWithTag(ElusTags.POSTAL_CODE).performTextInput("50")
        onNodeWithTag(ElusTags.CONTINUE).performClick()
        // The size is announced before anything is downloaded.
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithText("Rhône (69)", substring = true).assertExists()
        onNodeWithText("Taille du téléchargement", substring = true).assertExists()
        assertEquals(listOf<String?>(null), server.requests.toList())
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()

        // 69250 covers two communes: the user picks one.
        waitFor(ElusTags.commune("69143"))
        onNodeWithTag(ElusTags.commune("69003")).assertExists()
        onNodeWithTag(ElusTags.commune("69143")).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ElusTags.CHOOSER).fetchSemanticsNodes().isEmpty() }

        // The box names the officials of Neuville-sur-Saône under each level of the sheet.
        waitFor(ElusTags.boxLevel(DecisionLevel.COMMUNE))
        onNodeWithTag(ElusTags.boxLevel(DecisionLevel.COMMUNE), useUnmergedTree = true).assert(hasAnyDescendant(hasText("Charlie Neuville")))
        onNodeWithTag(ElusTags.boxLevel(DecisionLevel.INTERCOMMUNALITE), useUnmergedTree = true).assert(hasAnyDescendant(hasText("Maxime Neuville")))
        onNodeWithTag(ElusTags.boxLevel(DecisionLevel.ETAT), useUnmergedTree = true).performScrollTo().assert(hasAnyDescendant(hasText("Tal Septieme")))
        onNodeWithText("sera intégré à l’application", substring = true).assertDoesNotExist()
        onNodeWithText("Vos élus à Neuville-sur-Saône").performScrollTo()

        // Only codes of packages went to the server, never the postal code or the commune.
        assertEquals(listOf(null, "69", "FR"), server.requests.toList())
        assertEquals(ChosenCommune("69143", "69"), ElusStore(database).commune())

        // « Voir tous mes élus » opens the page.
        onNodeWithTag(ElusTags.BOX_ALL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.PAGE).assertExists()
        onNodeWithTag(ElusTags.COMMUNE).assert(hasAnyDescendant(hasText("Neuville-sur-Saône, Rhône (69)")))
    }

    @Test
    fun inDataSavingModeTheDownloadAsksASecondConfirmation() = elusTest {
        val server = ElusTestServer()
        openComprendre(dependencies(JvmDatabase.inMemory(), server, dataSaver = true))
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.CHOOSE)
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        waitFor(ElusTags.DATA_SAVER)
        onNodeWithText("Télécharger quand même").assertExists()
        assertEquals(listOf<String?>(null), server.requests.toList())
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        // A single commune for 69001: it is chosen at once.
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ElusTags.CHOOSER).fetchSemanticsNodes().isEmpty() }
        waitFor(ElusTags.level(DecisionLevel.COMMUNE))
        assertEquals(listOf(null, "69", "FR"), server.requests.toList())
    }

    @Test
    fun theMesElusPageListsEveryLevelAndForgetsTheCommune() = elusTest {
        val server = ElusTestServer()
        val database = JvmDatabase.inMemory()
        openComprendre(dependencies(database, server))
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.CHOOSE)
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        waitFor(ElusTags.level(DecisionLevel.COMMUNE))

        // The fictitious data are said so, every level is there, the mayor and the first deputy first.
        onNodeWithTag(ElusTags.EXAMPLE_BANNER).assertExists()
        DecisionLevel.entries.forEach { onNodeWithTag(ElusTags.level(it)).performScrollTo() }
        onNodeWithTag(ElusTags.level(DecisionLevel.COMMUNE)).assert(hasAnyDescendant(hasText("Noa Maire")))
        onNodeWithTag(ElusTags.level(DecisionLevel.COMMUNE)).assert(hasAnyDescendant(hasText("Maire · Depuis le 28 mars 2026")))
        onNodeWithTag(ElusTags.level(DecisionLevel.ETAT)).assert(hasAnyDescendant(hasText("Membre de l’Assemblée nationale · Depuis le 22 mars 2026")))
        onNodeWithTag(ElusTags.level(DecisionLevel.UNION_EUROPEENNE)).assert(hasAnyDescendant(hasText("Ana Europe")))
        // Nothing under the officials until another space fills the slot.
        assertTrue(onAllNodesWithTag(EXTRA).fetchSemanticsNodes().isEmpty())
        // The large council shows twelve members, the others on request.
        onNodeWithText("Membre Conseil20").assertDoesNotExist()
        onNodeWithTag(ElusTags.showMore(DecisionLevel.COMMUNE, 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Membre Conseil20").performScrollTo()

        // The source and its date.
        onNodeWithTag(ElusTags.SOURCE).performScrollTo().assert(hasAnyDescendant(hasText("Mise à jour de la source : 1er octobre 2026")))
        onNodeWithTag(ElusTags.SOURCE).assert(hasAnyDescendant(hasText("Licence : Licence Ouverte 2.0")))
        // The source opens only after « Vous quittez PPF ».
        onNodeWithTag(ElusTags.SOURCE_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("pas sur PPF : www.data.gouv.fr", substring = true).assertExists()
        onNodeWithTag(ElusTags.LEAVE_STAY).performClick()
        waitForIdle()

        // The list is up to date.
        onNodeWithTag(ElusTags.CHECK_UPDATE).performScrollTo().performClick()
        waitFor(ElusTags.UPDATE_STATE)
        onNodeWithText("La liste est à jour.").assertExists()

        // « Oublier ma commune » asks first, then erases the commune and the lists.
        onNodeWithTag(ElusTags.FORGET).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.FORGET_KEEP).assertExists()
        onNodeWithTag(ElusTags.FORGET_CONFIRM).performClick()
        waitFor(ElusTags.CHOOSE)
        val store = ElusStore(database)
        assertNull(store.commune())
        assertEquals(0L, store.countPackages())
    }

    @Test
    fun theSlotUnderEachOfficialShowsNothingUntilAnotherSpaceFillsIt() = elusTest {
        val server = ElusTestServer()
        val database = JvmDatabase.inMemory()
        // « Voir » will fill it (sponsorships read on the device, VO-34 votes); here, a line of test.
        openComprendre(dependencies(database, server), extra = { official -> Text("[EXEMPLE] Ligne de ${official.displayName}", Modifier.testTag(EXTRA)) })
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.CHOOSE)
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        waitFor(ElusTags.level(DecisionLevel.COMMUNE))
        // Under the official it belongs to, on the page « Mes élus » only.
        onNodeWithTag(ElusTags.level(DecisionLevel.COMMUNE)).assert(hasAnyDescendant(hasText("[EXEMPLE] Ligne de Noa Maire")))
        assertTrue(onAllNodesWithTag(EXTRA).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun anUnreachableServerLeadsToTheOfficialDirectories() = elusTest {
        val server = ElusTestServer().apply { online = false }
        openComprendre(dependencies(JvmDatabase.inMemory(), server))
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.CHOOSE)
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.CHOOSER_ERROR)
        onNodeWithTag(ElusTags.CHOOSER_ERROR).assert(hasText("Le serveur ne répond pas. Réessayez plus tard", substring = true))
        onNodeWithTag(ElusTags.CANCEL).performClick()
        waitForIdle()
        onNodeWithTag(ElusTags.DIRECTORIES).performScrollTo()
        onNodeWithText("ne répond pas pour le moment", substring = true).assertExists()
        onNodeWithTag(ElusTags.directory("assemblee")).performScrollTo()
        onNodeWithTag(ElusTags.directory("mairies")).performScrollTo()
    }

    @Test
    fun aPackageThatDoesNotMatchTheIndexIsRefused() = elusTest {
        val server = ElusTestServer().apply { tampered = true }
        val database = JvmDatabase.inMemory()
        openComprendre(dependencies(database, server))
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.CHOOSE)
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        waitFor(ElusTags.CHOOSER_ERROR)
        onNodeWithText("refusée par sécurité", substring = true).assertExists()
        assertNull(ElusStore(database).commune())
        assertNull(ElusStore(database).storedPackage("69"))
    }

    @Test
    fun aWideWindowShowsTheHomeBesideThePage() = elusTest {
        val server = ElusTestServer()
        openComprendre(dependencies(JvmDatabase.inMemory(), server), width = 1000, height = 740)
        onNodeWithTag(ElusTags.HOME_ENTRY).performScrollTo().performClick()
        waitFor(ElusTags.PAGE)
        onNodeWithTag(ComprendreTags.HOME).assertExists()
        onNodeWithTag(ElusTags.PAGE_BACK).assertDoesNotExist()
        onNodeWithTag(ElusTags.CHOOSE).performClick()
        typePostalCode("69001")
        waitFor(ElusTags.DOWNLOAD)
        onNodeWithTag(ElusTags.DOWNLOAD).performClick()
        waitFor(ElusTags.level(DecisionLevel.REGION))
        onNodeWithTag(ElusTags.HOME_ENTRY, useUnmergedTree = true).performScrollTo().assert(hasAnyDescendant(hasText("Votre commune : Lyon")))
        assertTrue(server.requests.none { it != null && it.length == 5 }, "no postal code was sent")
    }
}
