package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationStatus
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The agenda of an organisation on its page in the directory (AG-17, AG-19), wired by the shell. */
@OptIn(ExperimentalTestApi::class)
class OrganisationAgendaTest {

    @Test
    fun showsTheUpcomingEventsTheOrganisationHostsAndOpensOne() = runComposeUiTest {
        val organisation = AgendaKit()
        val other = AgendaKit()
        val device = AgendaDevice(withAccount = false)
        val hosted = organisation.mobilization("ag-de-novembre", "[EXEMPLE] Assemblée de novembre", start = AgendaClock.at(2026, 11, 20, 18))
        val named = other.mobilization("rassemblement-commun", "[EXEMPLE] Rassemblement commun", start = AgendaClock.at(2026, 11, 22, 14), organizers = listOf(organisation.pubkey))
        val past = organisation.mobilization("ag-d-octobre", "[EXEMPLE] Assemblée d'octobre", start = AgendaClock.at(2026, 10, 20, 18))
        val unrelated = other.mobilization("autre-reunion", "[EXEMPLE] Autre réunion", start = AgendaClock.at(2026, 11, 21, 18))
        listOf(hosted, named, past, unrelated).forEach { device.store.store(it) }
        val opened = mutableListOf<Mobilization>()
        setContent {
            val scope = rememberCoroutineScope()
            val agenda = remember {
                AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), null, scope) { AgendaClock.NOW }
            }
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 600.dp)) {
                    OrganisationAgenda(agenda, organisation.pubkey) { opened += it }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(OrganisationAgendaTags.ROW).fetchSemanticsNodes().size == 2 }
        onAllNodesWithTag(OrganisationAgendaTags.ROW).assertCountEquals(2)
        onNodeWithText("[EXEMPLE] Assemblée de novembre").performClick()
        assertEquals(listOf("ag-de-novembre"), opened.map { it.identifier })
    }

    @Test
    fun eachRowSaysWhatTheOrganisationDoesThereAsItSaidWithItsOwnKey() = runComposeUiTest {
        // D-044: organises (its own), co-organises or takes part (its own response), or only announced.
        val organisation = AgendaKit()
        val other = AgendaKit()
        val device = AgendaDevice(withAccount = false)
        val hosted = organisation.mobilization("ag-de-novembre", "[EXEMPLE] Assemblée de novembre", start = AgendaClock.at(2026, 11, 20, 18))
        val coOrganised = other.mobilization("marche-commune", "[EXEMPLE] Marche commune", start = AgendaClock.at(2026, 11, 21, 14), organizers = listOf(organisation.pubkey))
        val announced = other.mobilization("rassemblement-annonce", "[EXEMPLE] Rassemblement annoncé", start = AgendaClock.at(2026, 11, 22, 14), organizers = listOf(organisation.pubkey))
        val joined = other.mobilization("greve-locale", "[EXEMPLE] Grève locale", start = AgendaClock.at(2026, 11, 23, 6))
        val declined = other.mobilization("reunion-declinee", "[EXEMPLE] Réunion déclinée", start = AgendaClock.at(2026, 11, 24, 18))
        listOf(
            hosted, coOrganised, announced, joined, declined,
            organisation.participation(coOrganised, role = org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole.CO_ORGANISER),
            organisation.participation(joined),
            organisation.participation(declined, status = org.politisationpopulairefrancaise.core.agir.agenda.ParticipationStatus.DECLINED),
        ).forEach { device.store.store(it) }
        setContent {
            val scope = rememberCoroutineScope()
            val agenda = remember {
                AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), null, scope) { AgendaClock.NOW }.also { it.start() }
            }
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 700.dp)) {
                    OrganisationAgenda(agenda, organisation.pubkey, limit = 10) {}
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(OrganisationAgendaTags.ROW).fetchSemanticsNodes().size == 4 }
        fun row(title: String) = onNode(hasTestTag(OrganisationAgendaTags.ROW) and hasText(title))
        row("[EXEMPLE] Assemblée de novembre").assertTextContains("Organise")
        row("[EXEMPLE] Marche commune").assertTextContains("Co-organise")
        row("[EXEMPLE] Rassemblement annoncé").assertTextContains("Annoncée, non confirmée")
        row("[EXEMPLE] Grève locale").assertTextContains("Participe")
        onNodeWithText("[EXEMPLE] Réunion déclinée").assertDoesNotExist()
    }

    @Test
    fun saysSoWhenTheOrganisationAnnouncesNothing() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        setContent {
            val scope = rememberCoroutineScope()
            val agenda = remember {
                AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), null, scope) { AgendaClock.NOW }
            }
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 600.dp)) {
                    OrganisationAgenda(agenda, AgendaKit().pubkey) {}
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(OrganisationAgendaTags.NONE).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(OrganisationAgendaTags.NONE).assertExists()
    }

    @Test
    fun enDirectShowsTheNextMobilizationsButNotTheCancelledOnes() = runComposeUiTest {
        // Today, before the bundled examples of the agenda (which the library imports too).
        val kit = AgendaKit()
        val device = AgendaDevice(withAccount = false)
        listOf(
            kit.mobilization("premiere", "[EXEMPLE] Première", start = AgendaClock.at(2026, 11, 13, 11)),
            kit.mobilization("annulee", "[EXEMPLE] Annulée", start = AgendaClock.at(2026, 11, 13, 11, 15), status = MobilizationStatus.ANNULE),
            kit.mobilization("deuxieme", "[EXEMPLE] Deuxième", start = AgendaClock.at(2026, 11, 13, 11, 30)),
            kit.mobilization("troisieme", "[EXEMPLE] Troisième", start = AgendaClock.at(2026, 11, 13, 11, 45)),
            kit.mobilization("quatrieme", "[EXEMPLE] Quatrième", start = AgendaClock.at(2026, 11, 13, 11, 50)),
            kit.mobilization("passee", "[EXEMPLE] Passée", start = AgendaClock.at(2026, 11, 1, 10)),
        ).forEach { device.store.store(it) }
        setContent {
            val scope = rememberCoroutineScope()
            val agenda = remember {
                AgendaLibrary(device.store, device.state, EditorialTrust.bundledExamples(), null, scope) { AgendaClock.NOW }
            }
            PpfTheme {
                Box(Modifier.requiredSize(400.dp, 600.dp)) {
                    UpcomingMobilizations(agenda) {}
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(OrganisationAgendaTags.ROW).fetchSemanticsNodes().size == 3 }
        onNodeWithText("[EXEMPLE] Première").assertExists()
        onNodeWithText("[EXEMPLE] Troisième").assertExists()
        onNodeWithText("[EXEMPLE] Annulée").assertDoesNotExist()
        onNodeWithText("[EXEMPLE] Quatrième").assertDoesNotExist()
    }
}
