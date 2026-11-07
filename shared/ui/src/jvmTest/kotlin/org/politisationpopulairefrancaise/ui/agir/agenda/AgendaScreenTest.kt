package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.ParisTime
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.shell.AdaptiveShell
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The agenda screen on the bundled examples, on Friday 13 November 2026 at 10 h: the list by day,
 * the tabs, the filters, the detail with its link and its export, the private answers, and the
 * three widths.
 */
@OptIn(ExperimentalTestApi::class)
class AgendaScreenTest {
    private val paris = "rassemblement-services-publics-paris-2026-11-14"
    private val lyon = "assemblee-generale-lyon-2026-11-19"
    private val marseille = "manifestation-hopital-marseille-2026-11-21"
    private val strike = "greve-transports-2026-11-26"
    private val toulouse = "blocage-universite-toulouse-2026-12-01"
    private val cancelled = "rassemblement-maternite-seine-saint-denis-2026-12-05"
    private val rennes = "projection-debat-rennes-2026-11-03"

    private fun ComposeUiTest.awaitCards(count: Int = 7) {
        waitUntil(timeoutMillis = 20_000) { cardCount() == count }
    }

    private fun ComposeUiTest.card(identifier: String) = onNodeWithTag(AgendaTags.CARD + identifier)

    private fun ComposeUiTest.open(identifier: String) {
        card(identifier).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int) = AgendaTags.DAY + ParisTime.epochDay(AgendaClock.at(year, month, dayOfMonth, 12))

    @Test
    fun thePhoneShowsTheAgendaByDayWithTheMapComingSoon() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = false), 360, 740) }
        awaitCards()
        onNodeWithTag(AgendaTags.TITLE).assertTextContains("Carte et agenda")
        onNodeWithTag(AgendaTags.MAP_SOON).assertExists()
        onNodeWithText("La vue carte arrive bientôt").assertExists()
        onNodeWithTag(AgendaTags.COUNT).assertTextContains("7 mobilisations")
        // Saturday 14 is "Demain"; the other days are written out.
        onNodeWithTag(day(2026, 11, 14)).assertTextContains("Demain")
        onNodeWithTag(day(2026, 11, 19)).performScrollTo().assertTextContains("Jeudi 19 novembre")
        // Hours on the card (no-break spaces, French typography), place and territory.
        card(paris).assertTextContains("14 h à 17 h", substring = true)
        card(paris).assertTextContains("Place de la République, Paris · Paris", substring = true)
        // An event over two days gives both dates.
        card(strike).performScrollTo().assertTextContains("Du jeudi 26 novembre, 5 h au vendredi 27 novembre, 23 h", substring = true)
        // Cancelled and postponed, very visible.
        card(cancelled).performScrollTo()
        onNodeWithTag(AgendaTags.STATUS + "annule", useUnmergedTree = true).assertExists()
        onNodeWithTag(AgendaTags.STATUS + "reporte", useUnmergedTree = true).assertExists()
        // The past one is not among the upcoming ones; no "Mes propositions" without account.
        card(rennes).assertDoesNotExist()
        onNodeWithTag(AgendaTags.TAB + "mine").assertDoesNotExist()
    }

    @Test
    fun thePastTabShowsWhatIsOver() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = false), 360, 740) }
        awaitCards()
        onNodeWithTag(AgendaTags.TAB + "past").performClick()
        waitForIdle()
        card(rennes).assertExists()
        card(paris).assertDoesNotExist()
        onNodeWithTag(AgendaTags.COUNT).assertTextContains("1 mobilisation")
        onNodeWithTag(day(2026, 11, 3)).assertTextContains("Mardi 3 novembre")
        onNodeWithTag(AgendaTags.MAP_SOON).assertDoesNotExist()
        // A past event has neither answers nor export.
        open(rennes)
        onNodeWithTag(AgendaTags.RESPONSE + "going").assertDoesNotExist()
        onNodeWithTag(AgendaTags.EXPORT).assertDoesNotExist()
    }

    @Test
    fun filtersNarrowTheListAndTheirChipsRemoveThem() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = false), 1000, 740) }
        awaitCards()
        onNodeWithTag(AgendaTags.FILTERS).performClick()
        waitForIdle()
        onNodeWithTag(AgendaPanelTags.TYPE + MobilizationType.GREVE.tagValue).performScrollTo().performClick()
        onNodeWithTag(AgendaPanelTags.APPLY).assertTextContains("Afficher 1 mobilisation")
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        awaitCards(1)
        card(strike).assertExists()
        onNodeWithTag(AgendaTags.CHIP + "type:greve").performClick()
        waitForIdle()
        awaitCards(7)

        // This week (Monday 9 to Sunday 15 November): the Paris gathering only.
        onNodeWithTag(AgendaTags.FILTERS).performClick()
        waitForIdle()
        onNodeWithTag(AgendaPanelTags.PERIOD + "week").performClick()
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        mainClock.advanceTimeBy(500)
        awaitCards(1)
        card(paris).assertExists()
        onNodeWithTag(AgendaTags.CHIP + "period").assert(hasContentDescription("Retirer le filtre Cette semaine"))
        onNodeWithTag(AgendaTags.CLEAR_ALL).performClick()
        awaitCards(7)

        // Dates chosen by hand, a territory, and words.
        onNodeWithTag(AgendaTags.FILTERS).performClick()
        waitForIdle()
        onNodeWithTag(AgendaPanelTags.PERIOD + "range").performClick()
        onNodeWithTag(AgendaPanelTags.RANGE_FROM).performTextInput("26/11/2026")
        onNodeWithTag(AgendaPanelTags.RANGE_TO).performTextInput("30/11/2026")
        onNodeWithTag(AgendaPanelTags.APPLY).assertTextContains("Afficher 1 mobilisation")
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        mainClock.advanceTimeBy(500)
        awaitCards(1)
        onNodeWithTag(AgendaTags.CHIP + "period").assert(hasContentDescription("Retirer le filtre Du 26/11/2026 au 30/11/2026"))
        onNodeWithTag(AgendaTags.CLEAR_ALL).performClick()
        awaitCards(7)

        onNodeWithTag(AgendaTags.FILTERS).performClick()
        waitForIdle()
        onNodeWithTag(AgendaPanelTags.TERRITORY + "marseille").performScrollTo().performClick()
        onNodeWithTag(AgendaPanelTags.TEXT).performScrollTo().performTextInput("hopital")
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        mainClock.advanceTimeBy(500)
        awaitCards(1)
        card(marseille).assertExists()
        onNodeWithTag(AgendaTags.FILTERS).assert(hasContentDescription("Filtres, 2 filtres actifs"))

        // Nothing left: the list says so and offers to clear the filters.
        onNodeWithTag(AgendaTags.TAB + "past").performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.EMPTY_CLEAR).performClick()
        waitForIdle()
        awaitCards(1)
        card(rennes).assertExists()
    }

    @Test
    fun theDetailShowsEverythingAndTheLinkAsksFirst() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { TestAgenda(AgendaDevice(withAccount = false), 360, 740, onOpenLink = { opened += it }) }
        awaitCards()
        open(marseille)
        onNodeWithTag(AgendaTags.WHEN).assertTextContains("Samedi 21 novembre 2026, 10 h 30 à 13 h", substring = true)
        onNodeWithTag(AgendaTags.WHERE).assertTextContains("Départ devant la gare (lieu fictif), Marseille", substring = true)
        onNodeWithTag(AgendaTags.WHERE).assertTextContains("Territoire : Marseille", substring = true)
        onNodeWithText("Déclarée en préfecture").assertExists()
        onNodeWithTag(AgendaTags.DESCRIPTION).performScrollTo().assertTextContains("Événement fictif de démonstration", substring = true)
        onNodeWithText("Proposé par l’équipe éditoriale de PPF", substring = true).performScrollTo().assertExists()

        // The link: a warning first, the safe choice first.
        onNodeWithTag(AgendaTags.LINK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.LEAVE_DIALOG).assertExists()
        onNodeWithText("Ce lien ouvre un site qui n’appartient pas à PPF : exemple.invalid.", substring = true).assertExists()
        onNodeWithTag(AgendaTags.LEAVE_STAY).performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(emptyList(), opened)
        onNodeWithTag(AgendaTags.LINK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.LEAVE_OPEN).performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(listOf("https://exemple.invalid/marche-hopital"), opened)

        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.LIST).assertExists()
    }

    @Test
    fun addingToTheCalendarWarnsThenExportsTheIcsFile() = runComposeUiTest {
        val exported = mutableListOf<Pair<String, String>>()
        val exporter = CalendarExporter { name, content, _ ->
            exported += name to content
            CalendarExportResult.DONE
        }
        setContent { TestAgenda(AgendaDevice(withAccount = false), 360, 740, exporter = exporter) }
        awaitCards()
        open(paris)
        onNodeWithTag(AgendaTags.EXPORT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.EXPORT_DIALOG).assertExists()
        onNodeWithText("synchronisé avec un service en ligne", substring = true).assertExists()
        onNodeWithTag(AgendaTags.EXPORT_CANCEL).performClick()
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(emptyList(), exported)

        onNodeWithTag(AgendaTags.EXPORT).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.EXPORT_CONFIRM).performClick()
        mainClock.advanceTimeBy(500)
        waitUntil(timeoutMillis = 10_000) { exported.isNotEmpty() }
        val (name, content) = exported.single()
        assertEquals("$paris.ics", name)
        assertTrue(content.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue("SUMMARY:[EXEMPLE] Rassemblement pour les services publics\r\n" in content)
        assertTrue("DTSTART:20261114T130000Z\r\n" in content)
        assertTrue("LOCATION:Place de la République\\, Paris\r\n" in content)
        waitForIdle()
        onNodeWithTag(AgendaTags.EXPORT_MESSAGE).performScrollTo().assertTextContains("Le fichier d’agenda est prêt.")
    }

    @Test
    fun answersStayOnTheDeviceAndShowOnTheCard() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        setContent { TestAgenda(device, 360, 740) }
        awaitCards()
        open(paris)
        onNodeWithTag(AgendaTags.RESPONSE_NOTE).performScrollTo().assertTextContains("Votre réponse reste sur votre appareil", substring = true)
        onNodeWithTag(AgendaTags.RESPONSE + "going").performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { device.state.responses().values.singleOrNull() == AgendaResponse.GOING }
        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()
        card(paris).assertTextContains("J’y serai", substring = true)
        // Changing one's mind, then removing the answer.
        open(paris)
        onNodeWithTag(AgendaTags.RESPONSE + "interested").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { device.state.responses().values.singleOrNull() == AgendaResponse.INTERESTED }
        onNodeWithTag(AgendaTags.RESPONSE + "interested").performClick()
        waitUntil(timeoutMillis = 10_000) { device.state.responses().isEmpty() }
        // A cancelled event offers no answer.
        onNodeWithTag(AgendaTags.BACK).performScrollTo().performClick()
        waitForIdle()
        open(cancelled)
        onNodeWithTag(AgendaTags.NOTICE).assertTextContains("annulé", substring = true)
        onNodeWithTag(AgendaTags.RESPONSE + "going").assertDoesNotExist()
        onNodeWithTag(AgendaTags.EXPORT).assertDoesNotExist()
    }

    @Test
    fun organisersAreNamedByTheDirectoryOfOrganisations() = runComposeUiTest {
        val organisation = "c".repeat(64)
        val device = AgendaDevice(withAccount = false)
        device.store.store(
            AgendaKit().mobilization(
                "co-organise", "[EXEMPLE] Rassemblement co-organisé", start = AgendaClock.at(2026, 11, 16, 18),
                organizers = listOf(organisation, "d".repeat(64)),
            ),
        )
        setContent { TestAgenda(device, 1000, 740, organisationName = { if (it == organisation) "[EXEMPLE] Collectif fictif" else null }) }
        awaitCards(8)
        // Named without their confirmation (D-044): announced, and said so.
        card("co-organise").performScrollTo().assertTextContains("Organisé par [EXEMPLE] Collectif fictif (annoncée, non confirmée), ", substring = true)
        open("co-organise")
        onNodeWithTag(AgendaTags.ORGANIZERS).assertTextContains("[EXEMPLE] Collectif fictif", substring = true)
        onNodeWithTag(AgendaTags.ORGANIZERS).assertTextContains("annoncée, non confirmée", substring = true)
        // The organisers are offered as filters too (the cards are taller now: the button may have scrolled away).
        onNodeWithTag(AgendaTags.FILTERS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaPanelTags.ORGANIZER + organisation).performScrollTo().performClick()
        onNodeWithTag(AgendaPanelTags.APPLY).performClick()
        mainClock.advanceTimeBy(500)
        awaitCards(1)
    }

    @Test
    fun onePaneWithBackOnPhonesAndTabletsTwoPanesOnDesktop() {
        for ((width, twoPanes) in listOf(360 to false, 700 to false, 1000 to true)) {
            runComposeUiTest {
                setContent { TestAgenda(AgendaDevice(withAccount = false), width, 740) }
                awaitCards()
                if (twoPanes) onNodeWithTag(AgendaTags.SELECT_HINT).assertExists() else onNodeWithTag(AgendaTags.DETAIL).assertDoesNotExist()
                open(lyon)
                if (twoPanes) {
                    onNodeWithTag(AgendaTags.LIST).assertExists()
                    onNodeWithTag(AgendaTags.BACK).assertDoesNotExist()
                    // Another card replaces the detail; the list stays.
                    card(toulouse).performScrollTo().performClick()
                    waitForIdle()
                    onNodeWithTag(AgendaTags.NOTICE).assertTextContains("reporté", substring = true)
                } else {
                    onNodeWithTag(AgendaTags.LIST).assertDoesNotExist()
                    onNodeWithTag(AgendaTags.BACK).performClick()
                    waitForIdle()
                    onNodeWithTag(AgendaTags.LIST).assertExists()
                }
            }
        }
    }

    @Test
    fun theMapEntryOfTheShellOpensTheAgenda() = runComposeUiTest {
        val dependencies = AppDependencies.withDatabase(InMemoryIdentityStore(), JvmDatabase.inMemory())
        setContent { PpfTheme(darkTheme = false) { Box(Modifier.requiredSize(360.dp, 740.dp)) { AdaptiveShell(dependencies) } } }
        onNodeWithTag(ShellTags.navItem(Space.CARTE)).performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.TITLE).assertTextContains("Carte et agenda")
        onNodeWithTag(AgendaTags.MAP_SOON).assertExists()
    }
}
