package org.politisationpopulairefrancaise.ui.agir.agenda

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.politisationpopulairefrancaise.core.agir.agenda.AgendaResponse
import org.politisationpopulairefrancaise.core.agir.agenda.Mobilization
import org.politisationpopulairefrancaise.core.agir.agenda.MobilizationType
import org.politisationpopulairefrancaise.core.agir.agenda.Participation
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationRole
import org.politisationpopulairefrancaise.core.agir.agenda.ParticipationStatus
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The decisions of the owner of 7 November 2026 on the agenda (D-044, "la transparence passe avant
 * tout"): an undeclared demonstration shown as such, "Ça m'intéresse", the organisers confirmed by
 * their own key, the participating organisations, and the public choice of an organisation.
 */
@OptIn(ExperimentalTestApi::class)
class TransparencyTest {
    private val author = AgendaKit()
    private val union = AgendaKit()
    private val collective = AgendaKit()
    private val association = AgendaKit()
    private val citizen = AgendaKit()

    private val directory = mapOf(
        union.pubkey to "[EXEMPLE] Union syndicale fictive",
        collective.pubkey to "[EXEMPLE] Collectif fictif",
        association.pubkey to "[EXEMPLE] Association fictive",
    )

    /** For a container that does not merge its texts (or a node of the unmerged tree): one of its descendants says [text]. */
    private fun SemanticsNodeInteraction.says(text: String): SemanticsNodeInteraction = assert(hasAnyDescendant(hasText(text, substring = true)))

    private fun ComposeUiTest.awaitCard(identifier: String) {
        waitUntil(timeoutMillis = 20_000) { cardCount() > 0 }
        waitUntil(timeoutMillis = 20_000) { onNodeWithTag(AgendaTags.CARD + identifier).runCatching { assertExists() }.isSuccess }
    }

    private fun ComposeUiTest.open(identifier: String) {
        onNodeWithTag(AgendaTags.CARD + identifier).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(AgendaTags.DETAIL).assertExists()
    }

    private fun ComposeUiTest.type(tag: String, text: String) {
        onNodeWithTag(tag).performScrollTo().performTextInput(text)
        waitForIdle()
    }

    @Test
    fun anUndeclaredDemonstrationIsOfferedWithAPublicNoteAndShownAsSuch() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        server.verified += device.pubkey
        setContent { TestAgenda(device, 380, 740) }
        waitUntil(timeoutMillis = 20_000) { cardCount() > 0 }
        onNodeWithTag(AgendaTags.PROPOSE).performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.TYPE + "manifestation").performScrollTo().performClick()
        type(ProposeTags.TITLE, "[EXEMPLE] Marche pour la gare")
        type(ProposeTags.START_DATE, "20/11/2026")
        type(ProposeTags.START_TIME, "11 h")
        type(ProposeTags.LOCATION, "Devant la gare (lieu fictif), Villefictive")
        type(ProposeTags.TERRITORY, "Villefictive")
        onNodeWithTag(ProposeTags.DECLARATION_PUBLIC).assertDoesNotExist()
        onNodeWithTag(ProposeTags.DECLARATION + "non-faite").performScrollTo().assertTextContains("Non déclarée en préfecture").performClick()
        waitForIdle()
        // Said before sending, without preventing it.
        onNodeWithTag(ProposeTags.DECLARATION_PUBLIC).performScrollTo().says("Cette mention est publique")
        onNodeWithTag(ProposeTags.CHARTER).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ProposeTags.SEND).performScrollTo().performClick()
        waitUntil(timeoutMillis = 20_000) { server.received.isNotEmpty() }
        val sent = server.received.single()
        assertEquals("non-faite", sent.firstTagValue("ppf-declaration"))
        waitUntil(timeoutMillis = 10_000) { onNodeWithTag(ProposeTags.RESULT_BACK).runCatching { assertExists() }.isSuccess }
        onNodeWithTag(ProposeTags.RESULT_BACK).performScrollTo().performClick()
        waitForIdle()
        val identifier = checkNotNull(sent.firstTagValue("d"))
        awaitCard(identifier)
        // The card and the detail say it clearly.
        onNodeWithTag(AgendaTags.CARD + identifier).performScrollTo().assertTextContains("Non déclarée en préfecture", substring = true)
        open(identifier)
        onNodeWithTag(AgendaTags.DECLARATION).performScrollTo().assertTextContains("Non déclarée en préfecture", substring = true)
    }

    @Test
    fun theAnswerIsSaidWithoutGenderAndTheDeclaredMarchShowsItOnItsCard() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = false), 380, 740) }
        val marseille = "manifestation-hopital-marseille-2026-11-21"
        awaitCard(marseille)
        onNodeWithTag(AgendaTags.CARD + marseille).performScrollTo().assertTextContains("Déclarée en préfecture", substring = true)
        open("rassemblement-services-publics-paris-2026-11-14")
        onNodeWithTag(AgendaTags.RESPONSE + AgendaResponse.INTERESTED.value).performScrollTo().assertTextContains("Ça m’intéresse")
        onNodeWithText("Intéressé").assertDoesNotExist()
    }

    @Test
    fun organisersShowWhenConfirmedAndTheParticipatingOrganisationsAreListed() = runComposeUiTest {
        val device = AgendaDevice(withAccount = false)
        val event = author.mobilization(
            "marche-commune", "[EXEMPLE] Marche commune", start = AgendaClock.at(2026, 11, 16, 14), type = MobilizationType.MANIFESTATION,
            organizers = listOf(union.pubkey, collective.pubkey),
        )
        listOf(
            event,
            // The union confirms it co-organises; the collective says nothing; the association takes part.
            union.participation(event, role = ParticipationRole.CO_ORGANISER, createdAt = AgendaClock.NOW - 7_200),
            association.participation(event, createdAt = AgendaClock.NOW - 3_600),
            // A citizen's public answer (another client): never shown, even claiming to co-organise.
            citizen.participation(event, role = ParticipationRole.CO_ORGANISER),
            // A declined answer is never shown either.
            collective.participation(event, status = ParticipationStatus.DECLINED),
        ).forEach { device.store.store(it) }
        setContent { TestAgenda(device, 1000, 740, organisationName = { directory[it] }) }
        awaitCard("marche-commune")
        onNodeWithTag(AgendaTags.CARD + "marche-commune").performScrollTo()
            .assertTextContains("Organisé par [EXEMPLE] Union syndicale fictive, [EXEMPLE] Collectif fictif (annoncée, non confirmée)", substring = true)
            .assertTextContains("2 organisations participantes", substring = true)
        open("marche-commune")
        onNodeWithTag(ParticipationTags.HOST + author.pubkey, useUnmergedTree = true).assertExists()
        onNodeWithTag(ParticipationTags.HOST + union.pubkey, useUnmergedTree = true).says("[EXEMPLE] Union syndicale fictive").says("Vérifiée")
        onNodeWithTag(ParticipationTags.ANNOUNCED + collective.pubkey, useUnmergedTree = true).says("[EXEMPLE] Collectif fictif").says("annoncée, non confirmée")
        onNodeWithTag(ParticipationTags.HOST + collective.pubkey, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ParticipationTags.PARTICIPANTS).performScrollTo().says("Organisations participantes (2)")
        onNodeWithTag(ParticipationTags.PARTICIPANT + union.pubkey, useUnmergedTree = true).assertExists()
        onNodeWithTag(ParticipationTags.PARTICIPANT + association.pubkey, useUnmergedTree = true).says("[EXEMPLE] Association fictive").says("Vérifiée")
        onNodeWithTag(ParticipationTags.PARTICIPANT + citizen.pubkey, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ParticipationTags.PARTICIPANT + collective.pubkey, useUnmergedTree = true).assertDoesNotExist()
        // A citizen's device: the usual private answers, no public choice.
        onNodeWithTag(ParticipationTags.ORG_RESPONSE).assertDoesNotExist()
        onNodeWithTag(AgendaTags.RESPONSE + AgendaResponse.GOING.value).performScrollTo().assertExists()
    }

    @Test
    fun theBundledMeetingShowsTheParticipationOfPpf() = runComposeUiTest {
        setContent { TestAgenda(AgendaDevice(withAccount = false), 1000, 740) }
        val strasbourg = "reunion-publique-strasbourg-2026-12-10"
        awaitCard(strasbourg)
        open(strasbourg)
        onNodeWithTag(ParticipationTags.PARTICIPANTS).performScrollTo().says("Organisation participante (1)")
        onNodeWithTag(ParticipationTags.PARTICIPANT + EditorialTrust.EXAMPLE_EDITOR, useUnmergedTree = true).says("L’équipe éditoriale de PPF")
    }

    @Test
    fun aVerifiedOrganisationChoosesToShowItsParticipationFirstOrToKeepItPrivate() = runComposeUiTest {
        val server = AgendaServer()
        val device = AgendaDevice(withAccount = true, server = server)
        val event = author.mobilization("rassemblement-commun", "[EXEMPLE] Rassemblement commun", start = AgendaClock.at(2026, 11, 18, 18), organizers = listOf(device.pubkey))
        server.seed(event)
        val names = directory + (device.pubkey to "[EXEMPLE] Collectif du quartier")
        setContent { TestAgenda(device, 380, 740, organisationName = { names[it] }) }
        awaitCard("rassemblement-commun")
        open("rassemblement-commun")
        val address = checkNotNull(Mobilization.from(event)).address.toString()
        onNodeWithTag(ParticipationTags.ORG_RESPONSE).performScrollTo().says("Participation de [EXEMPLE] Collectif du quartier")
        onNodeWithTag(AgendaTags.RESPONSE + AgendaResponse.GOING.value).assertDoesNotExist()
        // The owner's choice: showing it publicly comes first.
        val show = onNodeWithTag(ParticipationTags.ORG_SHOW).assertTextContains("Afficher la participation de [EXEMPLE] Collectif du quartier publiquement")
        val keepPrivate = onNodeWithTag(ParticipationTags.ORG_PRIVATE).assertTextContains("Participer sans l’afficher")
        assertTrue(show.getBoundsInRoot().top < keepPrivate.getBoundsInRoot().top)

        // Without showing it: the answer stays on the device, nothing is sent.
        keepPrivate.performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { device.state.responses()[address] == AgendaResponse.GOING }
        onNodeWithTag(ParticipationTags.ORG_STATE).assertTextContains("n’est pas affichée", substring = true)
        assertTrue(server.received.none { it.kind == Participation.KIND })

        // Shown publicly: a response of its own key, the private answer is no longer needed.
        onNodeWithTag(ParticipationTags.ORG_SHOW).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { server.received.count { it.kind == Participation.KIND } == 1 }
        waitUntil(timeoutMillis = 10_000) { device.state.responses().isEmpty() }
        waitForIdle()
        onNodeWithTag(ParticipationTags.ORG_STATE).performScrollTo().assertTextContains("est affichée publiquement", substring = true)
        onNodeWithTag(ParticipationTags.PARTICIPANT + device.pubkey, useUnmergedTree = true).assertExists()
        assertEquals(device.pubkey, server.received.last().pubkey)

        // Named by the event: it confirms that it co-organises.
        onNodeWithTag(ParticipationTags.ORG_CO_ORGANISE).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { server.received.count { it.kind == Participation.KIND } == 2 }
        waitForIdle()
        assertEquals("organisateur", server.received.last().firstTagValue("ppf-role"))
        onNodeWithTag(ParticipationTags.HOST + device.pubkey, useUnmergedTree = true).assertExists()
        onNodeWithTag(ParticipationTags.ORG_STATE).performScrollTo().assertTextContains("La co-organisation de [EXEMPLE] Collectif du quartier est affichée publiquement.")

        // Withdrawn: a newer version, declined.
        onNodeWithTag(ParticipationTags.ORG_WITHDRAW).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { server.received.count { it.kind == Participation.KIND } == 3 }
        waitForIdle()
        assertEquals("declined", server.received.last().firstTagValue("status"))
        onNodeWithTag(ParticipationTags.ANNOUNCED + device.pubkey, useUnmergedTree = true).assertExists()
        onNodeWithTag(ParticipationTags.PARTICIPANTS).assertDoesNotExist()
        onNodeWithTag(ParticipationTags.ORG_SHOW).performScrollTo().assertExists()
    }
}
