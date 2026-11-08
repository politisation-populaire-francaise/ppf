package org.politisationpopulairefrancaise.ui.decider

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.editorial.EditorialTrust
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.core.signing.ContentAddress
import org.politisationpopulairefrancaise.core.time.CivilDate
import org.politisationpopulairefrancaise.core.voir.ContentFollowStore
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.content.rememberContentLibrary
import org.politisationpopulairefrancaise.ui.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Décider", from the contents bundled with the application (all [EXEMPLE]), on a fixed day. */
@OptIn(ExperimentalTestApi::class)
class DeciderScreenTest {

    private val database = JvmDatabase.inMemory()

    /** The text, on the node itself (merged cards) or on one of its children. */
    private fun has(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    private fun n(value: Long): String = FrenchNumbers.format(value.toDouble())

    private fun noon(date: CivilDate): Long = date.toEpochDays() * 86_400 + 43_200

    private class Seen {
        val navigations = mutableListOf<Space>()
        val opened = mutableListOf<ContentAddress>()
    }

    private fun ComposeUiTest.show(width: Int, height: Int = 740, today: CivilDate = CivilDate(2026, 11, 7)): Seen {
        val seen = Seen()
        val dependencies = AppDependencies(
            identityStore = InMemoryIdentityStore(),
            contentStore = LocalEventStore(database),
            editorialTrust = EditorialTrust.bundledExamples(),
            contentFollowStore = ContentFollowStore(database),
        )
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                val library = rememberContentLibrary(dependencies)
                val state = rememberDeciderState(dependencies)
                DeciderScreen(
                    library, state, WindowClass.of(width.toFloat(), height.toFloat()),
                    onNavigate = { seen.navigations += it },
                    onOpenVoir = { seen.opened += it },
                    now = { noon(today) },
                    dependencies = dependencies,
                )
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(DeciderTags.card("pacte-election-exemple")).fetchSemanticsNodes().isNotEmpty() }
        return seen
    }

    @Test
    fun theHomeShowsThePactAndTheOpenPetitions() = runComposeUiTest {
        show(width = 1000)
        onNodeWithTag(DeciderTags.card("pacte-election-exemple")).assert(has("3 candidats sur 4 ont répondu"))
        onNodeWithText("Pétitions officielles ouvertes (4)").assertExists()
        onNodeWithTag(DeciderTags.card("petition-an-ordre-public")).assert(has("${n(412_950)} signatures"))
        // « plus de 500 000 pétitionnaires » (Règlement de l'Assemblée, article 148, alinéa 6).
        onNodeWithTag(DeciderTags.card("petition-an-ordre-public")).assert(has("Plus que ${n(87_051)} signatures pour dépasser ${n(500_000)}"))
        // The closed one is one touch away, not mixed with the open ones.
        onNodeWithTag(DeciderTags.card("petition-senat-retraites")).assertDoesNotExist()
        onNodeWithTag(DeciderTags.CLOSED_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.card("petition-senat-retraites")).assertExists()
    }

    @Test
    fun aPetitionIsSignedOnTheOfficialSiteOnlyAfterAWarning() = runComposeUiTest {
        val seen = show(width = 1000)
        onNodeWithTag(DeciderTags.card("petition-an-ordre-public")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.detail("petition-an-ordre-public")).assertExists()
        onNodeWithText("Ce débat se termine sans vote", substring = true).assertExists()
        onNodeWithTag(DeciderTags.PRIVACY).assert(has("PPF ne sait jamais si vous avez signé"))
        onNodeWithTag(DeciderTags.SIGN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.LINK_DIALOG).assertExists()
        onNodeWithTag(VoirTags.LINK_DIALOG).assert(has("petitions.assemblee-nationale.fr"))
        onNodeWithText("Rester ici").performClick()
        waitForIdle()
        // The bill of "Voir" opens in its space.
        onNodeWithText("Projet de loi relatif à l'ordre public", substring = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals("pjl-ordre-public-manifestations", seen.opened.single().identifier)
    }

    @Test
    fun theThresholdsSayWhatTheyDoNotGuarantee() = runComposeUiTest {
        show(width = 1000)
        onNodeWithTag(DeciderTags.THRESHOLDS_OPEN).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.detail("seuils")).assertExists()
        for (platform in listOf("assemblee", "senat", "cese", "rip", "ice")) onNodeWithTag(DeciderTags.platform(platform)).performScrollTo().assertExists()
        onNodeWithTag(DeciderTags.platform("rip")).assert(has("185 parlementaires"))
        onAllNodesWithText("Vérifié auprès des sources officielles le 7 novembre 2026", substring = true).assertCountEquals(5)
        onNodeWithTag(DeciderTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.detail("seuils")).assertDoesNotExist()
    }

    @Test
    fun thePactTableHasEveryCandidateInAlphabeticalOrderOnAPhone() = runComposeUiTest {
        show(width = 400)
        onNodeWithTag(DeciderTags.card("pacte-election-exemple")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.PACTE_METHOD).assertExists()
        onNodeWithTag(DeciderTags.FINGERPRINT).performScrollTo().assertExists()
        val rows = listOf("alex-modele", "camille-exemple", "dominique-fictif", "sacha-temoin")
        rows.forEach { onNodeWithTag(DeciderTags.row(it)).assertExists() }
        // Alphabetical order (the core checks it too): each line sits below the previous one.
        val tops = rows.map { slug -> onNodeWithTag(DeciderTags.row(slug)).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        onNodeWithTag(DeciderTags.row("sacha-temoin")).performScrollTo().assert(has("Aucune réponse reçue"))
        onNodeWithTag(DeciderTags.row("camille-exemple")).assert(has("5 signées, 1 refusées, 0 sans réponse"))
        onNodeWithTag(DeciderTags.row("dominique-fictif")).assert(has("En attente"))
    }

    @Test
    fun theComparatorGivesEveryCandidateTheSameThemesOnAPhone() = runComposeUiTest {
        val seen = show(width = 400)
        onNodeWithTag(DeciderTags.card("comparateur-election-exemple")).performScrollTo().assert(has("4 candidats, 3 thèmes")).performClick()
        waitForIdle()
        onNodeWithTag(ComparatorTags.METHOD).assert(has("PPF ne dit pas pour qui voter"))
        onNodeWithTag(ComparatorTags.METHOD).assert(has("figé de la veille du scrutin à zéro heure"))
        // Who appears (Q1 option B): the same delay for everyone, each request visible with its dates.
        onNodeWithTag(ComparatorTags.ENTRY).assert(has("dans un délai de 7 jours au plus"))
        onNodeWithTag(ComparatorTags.request("dominique-fictif")).performScrollTo().assert(has("Demande le 10 oct. 2026, ajout le 14 oct. 2026"))
        onNodeWithTag(ComparatorTags.FROZEN).assertDoesNotExist()
        // One theme at a time: every candidate under it, in alphabetical order.
        val rows = listOf("alex-modele", "camille-exemple", "dominique-fictif", "sacha-temoin")
        rows.forEach { onNodeWithTag(ComparatorTags.row(it)).performScrollTo().assertExists() }
        val tops = rows.map { slug -> onNodeWithTag(ComparatorTags.row(slug)).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        onNodeWithTag(ComparatorTags.cell("alex-modele", "democratie")).assert(has("référendum d’initiative citoyenne"))
        // Nothing published: said, never filled in.
        onNodeWithTag(ComparatorTags.cell("dominique-fictif", "democratie")).performScrollTo().assert(has("Pas de position publiée"))
        onNodeWithTag(ComparatorTags.theme("logement")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComparatorTags.cell("camille-exemple", "logement")).performScrollTo().assert(has("Ses mots"))
        onNodeWithTag(ComparatorTags.cell("alex-modele", "logement")).performScrollTo().assert(has("Pas de position publiée"))
        // The source opens only after the warning; the sheet opens in « Voir ».
        onNodeWithTag(ComparatorTags.cell("camille-exemple", "logement")).assert(has("Lire la source"))
        onAllNodesWithText("Lire la source", substring = true).onFirst().performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(VoirTags.LINK_DIALOG).assertExists()
        onNodeWithText("Rester ici").performClick()
        waitForIdle()
        onAllNodesWithText("Sa fiche dans « Voir »").onFirst().performScrollTo().performClick()
        waitForIdle()
        assertEquals("alex-modele", seen.opened.single().identifier)
    }

    @Test
    fun theDayBeforeTheVoteTheComparatorSaysItIsFrozenAndStaysReadable() = runComposeUiTest {
        // Round 1 of the example election: Sunday 7 March 2027, frozen from 0 h on Saturday 6 March.
        show(width = 400, today = CivilDate(2027, 3, 6))
        onNodeWithTag(DeciderTags.card("comparateur-election-exemple")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComparatorTags.FROZEN).assert(has("Tour 1"))
        onNodeWithTag(ComparatorTags.FROZEN).assert(has("jusqu’au 7 mars 2027 à 20 h"))
        onNodeWithTag(ComparatorTags.cell("alex-modele", "democratie")).performScrollTo().assert(has("référendum d’initiative citoyenne"))
    }

    @Test
    fun thePastVotesOfThePartyAreAllThereSortedByThePublishedWordsWithoutAnyComputedPosition() = runComposeUiTest {
        show(width = 400)
        onNodeWithTag(DeciderTags.card("comparateur-election-exemple")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ComparatorTags.votes("alex-modele")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PastVotesTags.PAGE).assertExists()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(PastVotesTags.ballot("an:17:9002")).fetchSemanticsNodes().isNotEmpty() }
        // Every vote of the party first, newest first, in numbers only.
        onNodeWithTag(PastVotesTags.RULE).assert(has("sans aucun tri"))
        onNodeWithTag(PastVotesTags.ballot("an:17:9002")).assert(has("pour")).assert(has("membres"))
        // A theme: the rule, its words and their version are shown, with a way to report a wrong one.
        onNodeWithTag(PastVotesTags.theme("democratie")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PastVotesTags.RULE).assert(has("il peut contenir des erreurs")).assert(has("motion de censure")).assert(has("version 1"))
        onNodeWithTag(PastVotesTags.ballot("an:17:9002")).assertExists()
        onNodeWithTag(PastVotesTags.ballot("an:16:9001")).assertDoesNotExist()
        onNodeWithTag(PastVotesTags.report("an:17:9002")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PastVotesTags.REPORT_DIALOG).assert(has("jamais ce seul scrutin à la main"))
        onNodeWithText("Fermer").performClick()
        waitForIdle()
        // Nothing is hidden: the ballots no theme takes are listed apart, and the free search reads every title.
        onNodeWithTag(PastVotesTags.OTHERS).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PastVotesTags.ballot("an:16:9001")).performScrollTo().assertExists()
        onNodeWithTag(PastVotesTags.ALL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(PastVotesTags.SEARCH).performScrollTo().performTextInput("écluses")
        waitForIdle()
        onNodeWithTag(PastVotesTags.ballot("an:17:9001")).performScrollTo().assertExists()
        onNodeWithTag(PastVotesTags.ballot("an:17:9002")).assertDoesNotExist()
    }

    @Test
    fun theComparatorIsATableOnAWideWindow() = runComposeUiTest {
        show(width = 1200)
        onNodeWithTag(DeciderTags.card("comparateur-election-exemple")).performScrollTo().performClick()
        waitForIdle()
        listOf("democratie", "sante", "logement").forEach { theme ->
            onNodeWithTag(ComparatorTags.cell("sacha-temoin", theme)).assertExists()
        }
        onNodeWithTag(ComparatorTags.cell("sacha-temoin", "sante")).assert(has("Pas de position publiée"))
    }

    @Test
    fun afterTheDeadlineSilenceIsShownAsNoAnswer() = runComposeUiTest {
        show(width = 1000, today = CivilDate(2026, 12, 1))
        onNodeWithTag(DeciderTags.card("pacte-election-exemple")).assert(has("Délai écoulé"))
        onNodeWithTag(DeciderTags.card("pacte-election-exemple")).performClick()
        waitForIdle()
        onNodeWithTag(DeciderTags.BOARD).performScrollTo().assert(has("Sans réponse"))
    }

    @Test
    fun aCandidateOpensTheirSheetInVoir() = runComposeUiTest {
        val seen = show(width = 1000)
        onNodeWithTag(DeciderTags.card("pacte-election-exemple")).performClick()
        waitForIdle()
        // The table scrolls sideways inside the page: bring the table into view first.
        onNodeWithTag(DeciderTags.BOARD).performScrollTo()
        onNodeWithContentDescription("Ouvrir la fiche de Camille Exemple").performClick()
        waitForIdle()
        assertEquals("camille-exemple", seen.opened.single().identifier)
    }

    @Test
    fun theWaysToTheOtherSpacesWork() = runComposeUiTest {
        val seen = show(width = 1000)
        onNodeWithTag(DeciderTags.link("revendications")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(Space.REVENDICATIONS), seen.navigations)
        onNodeWithTag(DeciderTags.section("votations")).performScrollTo().assert(has("une personne, une voix"))
    }
}
