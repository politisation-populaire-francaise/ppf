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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.LegislativeText
import org.politisationpopulairefrancaise.core.voir.TextStage
import org.politisationpopulairefrancaise.core.voir.parlement.AgendaStatus
import org.politisationpopulairefrancaise.core.voir.parlement.Chamber
import org.politisationpopulairefrancaise.core.voir.parlement.InMemoryParliamentStorage
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentLinks
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentRepository
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekFormat
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekLoader
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentWeekPackage
import org.politisationpopulairefrancaise.core.voir.parlement.SittingKind
import org.politisationpopulairefrancaise.core.voir.parlement.WeekItem
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * « Cette semaine au Parlement » (VO-31) against a fake server serving fictional packages signed
 * with a key generated for the test: the entry of the parliamentary screen, the page on phone and
 * computer widths, the [EXEMPLE] banner, the links, the block of « En direct » and the data saver.
 */
@OptIn(ExperimentalTestApi::class)
class ParliamentWeekScreenTest {

    private val keys = ParliamentUiFixtures.Keys()

    private val week = ParliamentWeekPackage(
        example = true,
        from = "2026-11-07",
        to = "2026-11-21",
        items = listOf(
            WeekItem("senat:exemple:202611101430", Chamber.SENAT, "2026-11-10T14:30", "2026-11-10T20:00", SittingKind.PLENARY, title = "[EXEMPLE] Projet de loi fictif sur les acacias", detail = "[EXEMPLE] Discussion générale.", status = AgendaStatus.CONFIRMED, url = "https://www.senat.fr/ordre-du-jour/ordre-du-jour.html"),
            WeekItem(
                "an:RU1:2", Chamber.AN, "2026-11-10T15:00", "2026-11-10T20:00", SittingKind.PLENARY,
                title = "[EXEMPLE] Suite de la discussion du projet de loi sur les tilleuls", pointKind = "Suite de la discussion",
                dossier = "an-dossier:DLR5L17N90001", dossierUrl = "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR5L17N90001",
                status = AgendaStatus.CONFIRMED, url = "https://www.assemblee-nationale.fr/dyn/seance-publique",
            ),
            WeekItem("an:RU3:1", Chamber.AN, "2026-11-11T09:30", "2026-11-11T12:30", SittingKind.COMMITTEE, body = "[EXEMPLE] Commission des affaires fictives", title = "[EXEMPLE] Examen de la proposition de loi sur les acacias", dossier = "an-dossier:DLR5L17N90002", status = AgendaStatus.CONFIRMED),
            WeekItem("an:RU2", Chamber.AN, "2026-11-12T09:00", "2026-11-12T13:00", SittingKind.PLENARY, status = AgendaStatus.PROVISIONAL),
            WeekItem("pe:MTG-PL-2026-11-18:1", Chamber.PE, "2026-11-18T09:00", "2026-11-18T11:50", SittingKind.PLENARY, title = "[EXEMPLE] Débat fictif sur les forêts", pointKind = "Débat", status = AgendaStatus.PROVISIONAL),
            WeekItem("an:RU0:1", Chamber.AN, "2026-11-06T15:00", null, SittingKind.PLENARY, title = "[EXEMPLE] Hier"),
        ),
    )
    private val weekBytes = ParliamentWeekFormat.write(week).encodeToByteArray()
    private val packages = ParliamentUiFixtures.packages() + (ParliamentWeekFormat.PACKAGE to weekBytes)

    private fun weekTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest(EmptyCoroutineContext, EmptyCoroutineContext, 3.minutes, block)

    private class Seen {
        val navigations = mutableListOf<Space>()
        val bills = mutableListOf<LegislativeText>()
        var week: ParliamentWeekState? = null
    }

    private val bill = LegislativeText(
        Article(
            SignedEvent("a".repeat(64), "b".repeat(64), 1, 30023, listOf(listOf("d", "tilleuls"), listOf("ppf-official-id", "an-dossier:DLR5L17N90001")), "", "c".repeat(128)),
            ContentAddress(30023, "b".repeat(64), "tilleuls"), "[EXEMPLE] Projet de loi sur les tilleuls", null, emptyList(), ArticleType.TEXTE, ContentOrigin.HUMAN, null,
        ),
        null, TextStage.TABLED, emptyList(), null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )

    /** The parliamentary screen (or the block of « En direct » when [slot]), with its week state attached. */
    private fun ComposeUiTest.show(
        server: ParliamentUiFixtures.Server?,
        width: Int,
        height: Int = 740,
        slot: Boolean = false,
        bundled: suspend (String) -> ByteArray? = { null },
        storage: InMemoryParliamentStorage = InMemoryParliamentStorage(),
        dataSaver: Boolean = false,
        dark: Boolean = false,
    ): Seen {
        val seen = Seen()
        setContent {
            val scope = rememberCoroutineScope()
            val parliament = remember {
                ParliamentState(ParliamentRepository(server, storage, bundled, keys.trust, clock = { ParliamentUiFixtures.NOW }), scope).also { p ->
                    val links = ParliamentWeekLinks(
                        billFor = { d -> bill.takeIf { ParliamentLinks.dossierId(it.article.event) == d } },
                        openBill = { seen.bills += it },
                        navigate = { seen.navigations += it },
                    )
                    p.week = ParliamentWeekState(p, ParliamentWeekLoader(server, storage, bundled), scope, { dataSaver }, links).also { seen.week = it }
                    if (!slot) p.open()
                }
            }
            PpfTheme(darkTheme = dark) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    if (slot) Column { ParliamentWeekSlot(parliament) } else if (parliament.isOpen) ParliamentScreen(parliament)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { seen.week?.parliament?.snapshot != null }
        waitForIdle()
        return seen
    }

    private suspend fun server() = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(packages)), packages)

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openPage() {
        onNodeWithTag(ParliamentTags.LIST).performScrollToNode(hasTestTag(WeekTags.ENTRY))
        onNodeWithTag(WeekTags.ENTRY).performClick()
        waitForTag(WeekTags.PAGE)
    }

    @Test
    fun onAPhoneThePageDownloadsTheWeekOnAGestureThenShowsDaysAndLinks() = weekTest {
        val server = server()
        val seen = show(server, width = 400, height = 760)
        openPage()
        // Nothing but the index before the gesture; the size is on the button.
        waitForTag(WeekTags.DOWNLOAD)
        assertEquals(listOf<String?>(null), server.requests)
        val size = (weekBytes.size + 999) / 1000
        onNodeWithTag(WeekTags.DOWNLOAD).assert(hasText("Télécharger l’ordre du jour ($size Ko)"))
        onNodeWithTag(WeekTags.DOWNLOAD).performScrollTo().performClick()
        waitForTag(WeekTags.day("2026-11-10"))
        assertEquals(listOf(null, ParliamentWeekFormat.PACKAGE), server.requests)
        onNodeWithTag(WeekTags.DOWNLOAD).assertDoesNotExist()
        onNodeWithTag(WeekTags.EXAMPLE).assertExists()
        onNodeWithTag(WeekTags.DATA_DATE).assert(hasText("Données du 7 novembre 2026 · Du 7 novembre 2026 au 21 novembre 2026"))
        onNodeWithText("Mardi 10 novembre 2026").assertExists()
        // The day before today is over.
        onNodeWithTag(WeekTags.item("an:RU0:1")).assertDoesNotExist()
        // The Sénat at 14 h 30 first, then the Assemblée at 15 h.
        val senat = onNodeWithTag(WeekTags.item("senat:exemple:202611101430"))
        senat.assert(hasAnyDescendant(hasText("14 h 30 à 20 h · Sénat · Séance publique")))
        val an = onNodeWithTag(WeekTags.item("an:RU1:2"))
        an.assert(hasAnyDescendant(hasText("15 h à 20 h · Assemblée nationale · Séance publique")))
        an.assert(hasAnyDescendant(hasText("Suite de la discussion")))
        an.assert(hasAnyDescendant(hasText("Confirmé")))
        onNodeWithTag(WeekTags.item("an:RU3:1")).assert(hasAnyDescendant(hasText("Réunion de commission, Commission des affaires fictives", substring = true)))
        onNodeWithTag(WeekTags.item("an:RU2")).assert(hasAnyDescendant(hasText("Ordre du jour pas encore publié"))).assert(hasAnyDescendant(hasText("Prévisionnel")))
        // The bill of "Voir" whose ppf-official-id is the file of the point; no such link elsewhere.
        onNodeWithTag(WeekTags.link("bill", "an:RU3:1")).assertDoesNotExist()
        onNodeWithTag(WeekTags.link("bill", "an:RU1:2")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(bill), seen.bills)
        onNodeWithTag(WeekTags.link("who", "an:RU1:2")).performScrollTo().performClick()
        onNodeWithTag(WeekTags.link("interpellate", "an:RU1:2")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(Space.COMPRENDRE, Space.DECIDER), seen.navigations)
        // Official pages after « Vous quittez PPF ».
        onNodeWithTag(WeekTags.link("dossier", "an:RU1:2")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.LEAVE.dialog).assertExists()
        onNodeWithTag(ParliamentTags.LEAVE.stay).performClick()
        waitForIdle()
        onNodeWithTag(ParliamentTags.LEAVE.dialog).assertDoesNotExist()
        // Back returns to the list.
        onNodeWithTag(ParliamentTags.BACK).performClick()
        waitForTag(ParliamentTags.LIST)
    }

    @Test
    fun onAComputerDaysSitBesideTheirItemsAndTheFilterKeepsOneAssembly() = weekTest {
        val files = packages + ("index" to keys.sign(ParliamentUiFixtures.index(packages)).toJson().encodeToByteArray())
        show(null, width = 1024, height = 768, bundled = { files[it] })
        openPage()
        // The bundled examples are already there: no download button.
        waitForTag(WeekTags.day("2026-11-10"))
        onNodeWithTag(WeekTags.DOWNLOAD).assertDoesNotExist()
        onNodeWithTag(WeekTags.EXAMPLE).assertExists()
        onNodeWithText("Jeu d’exemple intégré à l’application.").assertExists()
        onNodeWithTag(WeekTags.ATTRIBUTION).assert(hasAnyDescendant(hasText("flux public de la séance", substring = true)))
        onNodeWithTag(WeekTags.chamber("pe")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(WeekTags.day("2026-11-10")).assertDoesNotExist()
        onNodeWithTag(WeekTags.item("pe:MTG-PL-2026-11-18:1")).assert(hasAnyDescendant(hasText("Débat")))
        onNodeWithText("Mercredi 18 novembre 2026").assertExists()
        onNodeWithTag(WeekTags.chamber("all")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(WeekTags.day("2026-11-10")).assertExists()
    }

    @Test
    fun theBlockOfEnDirectShowsTheNextPointsAndOpensTheWeekInVoir() = weekTest {
        val files = packages + ("index" to keys.sign(ParliamentUiFixtures.index(packages)).toJson().encodeToByteArray())
        val seen = show(null, width = 400, slot = true, bundled = { files[it] })
        waitForTag(WeekTags.slotItem("senat:exemple:202611101430"))
        onNodeWithTag(WeekTags.EXAMPLE).assertExists()
        onNodeWithTag(WeekTags.slotItem("senat:exemple:202611101430")).assert(hasText("Mardi 10 nov. 2026 · 14 h 30 · Sénat"))
        // Four points at most, then how many more.
        onNodeWithTag(WeekTags.slotItem("pe:MTG-PL-2026-11-18:1")).assertDoesNotExist()
        onNodeWithText("Autres points à l’ordre du jour : 1").assertExists()
        onNodeWithTag(WeekTags.SLOT_ALL).performClick()
        waitForIdle()
        assertEquals(listOf(Space.VOIR), seen.navigations)
        val parliament = seen.week!!.parliament
        assertTrue(parliament.isOpen)
        assertEquals(ParliamentPage.Week, parliament.current)
    }

    @Test
    fun theBlockOfEnDirectWaitsForAGestureAndRespectsTheDataSaver() = weekTest {
        val server = server()
        val storage = InMemoryParliamentStorage()
        show(server, width = 400, slot = true, storage = storage)
        waitForTag(WeekTags.DOWNLOAD)
        // Not on the device: only the index was asked, the size is shown.
        assertEquals(listOf<String?>(null), server.requests)
        onNodeWithTag(WeekTags.DOWNLOAD).performClick()
        waitForTag(WeekTags.slotItem("an:RU1:2"))
        assertEquals(listOf(null, ParliamentWeekFormat.PACKAGE), server.requests)
    }

    @Test
    fun anOutdatedWeekIsUpdatedOnOpeningOnlyWithoutTheDataSaver() = weekTest {
        // A first version on the device, then the server announces another one.
        val storage = InMemoryParliamentStorage()
        val older = ParliamentWeekFormat.write(week.copy(items = week.items.take(2))).encodeToByteArray()
        val first = ParliamentUiFixtures.packages() + (ParliamentWeekFormat.PACKAGE to older)
        val firstServer = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(first)), first)
        val repository = ParliamentRepository(firstServer, storage, trust = keys.trust, clock = { ParliamentUiFixtures.NOW })
        val loader = ParliamentWeekLoader(firstServer, storage)
        val snapshot = (repository.open() as org.politisationpopulairefrancaise.core.voir.parlement.ParliamentOpening.Ready).snapshot
        loader.download(snapshot)
        val server = ParliamentUiFixtures.Server(keys.sign(ParliamentUiFixtures.index(packages), ParliamentUiFixtures.NOW + 60), packages)
        show(server, width = 400, slot = true, storage = storage, dataSaver = true)
        waitForTag(WeekTags.DOWNLOAD)
        onNodeWithTag(WeekTags.DOWNLOAD).assert(hasText("Mettre à jour l’ordre du jour", substring = true))
        assertEquals(listOf<String?>(null), server.requests)
    }

    @Test
    fun theAppShowsTheBlockInEnDirect() = weekTest {
        val database = JvmDatabase.inMemory()
        val dependencies = AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(database),
            editorialTrust = EditorialTrust.bundledExamples(),
            database = database,
        )
        setContent { Box(Modifier.requiredSize(400.dp, 760.dp)) { PpfApp(dependencies) } }
        waitForIdle()
        onNodeWithTag(ShellTags.navItem(Space.EN_DIRECT)).performClick()
        waitForTag(WeekTags.SLOT)
        // The bundled example index of the application announces the package `semaine`.
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(WeekTags.LOADING).fetchSemanticsNodes().isEmpty() }
        onNodeWithTag(WeekTags.NOT_OFFERED).assertDoesNotExist()
        onNodeWithTag(WeekTags.EXAMPLE).assertExists()
    }
}
