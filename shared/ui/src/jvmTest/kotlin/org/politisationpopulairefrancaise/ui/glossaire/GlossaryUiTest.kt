package org.politisationpopulairefrancaise.ui.glossaire

import org.politisationpopulairefrancaise.ui.shell.openSpace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.WindowClass
import org.politisationpopulairefrancaise.core.identity.InMemoryIdentityStore
import org.politisationpopulairefrancaise.core.local.JvmDatabase
import org.politisationpopulairefrancaise.core.local.LocalEventStore
import org.politisationpopulairefrancaise.ui.AppDependencies
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.content.ComprendreTags
import org.politisationpopulairefrancaise.ui.content.ContentTags
import org.politisationpopulairefrancaise.ui.content.MarkdownText
import org.politisationpopulairefrancaise.ui.shell.ShellTags
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The glossary in the texts (D-048, contract of the third wave of "Comprendre", section 2.4): the
 * first occurrence of a term becomes a link with a « ? », its tooltip gives the short definition,
 * « En savoir plus » opens the page of the entry over the content, the full glossary is searched
 * without accents or case, and nothing changes without a glossary.
 */
@OptIn(ExperimentalTestApi::class)
class GlossaryUiTest {

    private fun ComposeUiTest.show(state: GlossaryState?, width: Int = 800, height: Int = 700, content: @Composable () -> Unit) {
        setContent {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                PpfTheme(darkTheme = false) {
                    ProvideGlossary(state) {
                        Box(Modifier.background(PpfTheme.palette.surface1).padding(16.dp)) { content() }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.showParagraph(state: GlossaryState?) = show(state) {
        // The bodies of "Comprendre" are selectable: the links must work inside a SelectionContainer.
        SelectionContainer { MarkdownText(GlossaryFixtures.PARAGRAPH + "\n\n## Sources\n\n- La motion de censure au Sénat") }
    }

    @Test
    fun aSpottedWordOpensItsTooltip() = runComposeUiTest {
        val state = GlossaryFixtures.state()
        showParagraph(state)
        // First occurrences only, never in the sources, never an entry marked « auto: non ».
        assertEquals(1, onAllNodesWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag(GlossaryTags.hint("49-3"), useUnmergedTree = true).assertExists()
        onNodeWithTag(GlossaryTags.hint("senat"), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(GlossaryTags.hint("gouvernement"), useUnmergedTree = true).assertDoesNotExist()
        // Screen readers find the « ? » as a button « <terme>, définition »; the text only gains an invisible character.
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).assertContentDescriptionEquals("motion de censure, définition")
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).assert(hasClickAction())
        onNodeWithText("avec le 49.3⁠. Les députés", substring = true).assertExists()

        // A touch on the « ? » (it lies on the link of the word) opens the tooltip.
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).performTouchInput { click(center) }
        waitForIdle()
        onNodeWithTag(GlossaryTags.TOOLTIP).assertExists()
        onNodeWithTag(GlossaryTags.TOOLTIP).assert(hasAnyDescendant(hasText("Vote par lequel les députés peuvent renverser le gouvernement.")))
        onNodeWithTag(GlossaryTags.TOOLTIP).assert(hasAnyDescendant(hasText("Motion de censure")))
        onNodeWithTag(GlossaryTags.TOOLTIP).assert(hasAnyDescendant(hasText("[EXEMPLE]")))

        // The close button closes it.
        onNodeWithTag(GlossaryTags.TOOLTIP_CLOSE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.TOOLTIP).assertDoesNotExist()

        // A touch on the word itself opens it too (the link covers the word, wherever it lies).
        val links = onAllNodes(linkOfTheParagraph, useUnmergedTree = true)
        links[1].performTouchInput { click(center) }
        waitForIdle()
        onNodeWithTag(GlossaryTags.TOOLTIP).assert(hasAnyDescendant(hasText("Motion de censure")))
    }

    /** The links of the paragraph: clickable children of its text, apart from the « ? » of the screen readers. */
    private val linkOfTheParagraph = hasClickAction() and hasParent(hasText("avec le 49.3⁠.", substring = true)) and !hasContentDescription("définition", substring = true)

    @Test
    fun theLinkIsReachedByTheKeyboardAndEscapeClosesTheTooltip() = runComposeUiTest {
        showParagraph(GlossaryFixtures.state())
        // The links of the text are clickable nodes of the paragraph: focus the first one, then Enter.
        val link = linkOfTheParagraph
        val links = onAllNodes(link, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, links.size, "one link per spotted term")
        onAllNodes(link, useUnmergedTree = true)[0].requestFocus()
        waitForIdle()
        onAllNodes(link, useUnmergedTree = true)[0].performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag(GlossaryTags.TOOLTIP).assert(hasAnyDescendant(hasText("49.3")))

        onNodeWithTag(GlossaryTags.TOOLTIP).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.TOOLTIP).assertDoesNotExist()
    }

    @Test
    fun moreOpensThePageOfTheEntryOverTheContent() = runComposeUiTest {
        val state = GlossaryFixtures.state()
        showParagraph(state)
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).performTouchInput { click(center) }
        waitForIdle()
        onNodeWithTag(GlossaryTags.MORE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.TOOLTIP).assertDoesNotExist()
        onNodeWithTag(GlossaryTags.PAGE).assertExists()
        onNodeWithTag(GlossaryTags.PAGE).assert(hasAnyDescendant(hasText("Vote par lequel les députés peuvent renverser le gouvernement.")))
        onNodeWithTag(GlossaryTags.PAGE).assert(hasAnyDescendant(hasText("Produit avec l’aide d’une IA", substring = true)))
        // The content stays underneath.
        onNodeWithText("Les députés répondent", substring = true).assertExists()
        // The explanation is spotted too (the 49.3), never for its own term.
        assertEquals(2, onAllNodesWithTag(GlossaryTags.hint("49-3"), useUnmergedTree = true).fetchSemanticsNodes().size, "the paragraph and the explanation")
        assertEquals(1, onAllNodesWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).fetchSemanticsNodes().size, "only the one of the paragraph")

        // « Voir aussi » opens another entry, the back button returns.
        onNodeWithTag(GlossaryTags.seeAlso("49-3")).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("motion-de-censure", "49-3"), state.pages)
        onNodeWithTag(GlossaryTags.PAGE_BACK).performClick()
        waitForIdle()
        assertEquals(listOf("motion-de-censure"), state.pages)

        // « Ouvrir le glossaire complet » asks the shell for "Comprendre", on this entry.
        onNodeWithTag(GlossaryTags.OPEN_FULL).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.PAGE).assertDoesNotExist()
        assertEquals(true, state.browsing)
        assertEquals("motion-de-censure", state.selected)
        assertEquals(true, state.wantsComprendre)
    }

    @Test
    fun theCloseButtonClosesThePage() = runComposeUiTest {
        val state = GlossaryFixtures.state()
        showParagraph(state)
        state.openPage("senat")
        waitForIdle()
        onNodeWithTag(GlossaryTags.PAGE).assert(hasAnyDescendant(hasText("Sénat")))
        onNodeWithTag(GlossaryTags.PAGE_CLOSE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.PAGE).assertDoesNotExist()
        assertEquals(emptyList(), state.pages)
    }

    @Test
    fun withoutAGlossaryTheTextIsUnchanged() = runComposeUiTest {
        showParagraph(null)
        onNodeWithText(GlossaryFixtures.PARAGRAPH).assertExists()
        onAllNodesWithText("définition", substring = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyGlossaryLeavesTheTextUnchanged() = runComposeUiTest {
        showParagraph(GlossaryState(store = null, trust = GlossaryFixtures.trust, scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)))
        onNodeWithText(GlossaryFixtures.PARAGRAPH).assertExists()
        onNodeWithTag(GlossaryTags.hint("49-3"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theFullGlossaryIsSearchedWithoutAccentsOrCase() = runComposeUiTest {
        val state = GlossaryFixtures.state()
        show(state, width = 400, height = 800) {
            GlossaryScreen(state, WindowClass.of(widthDp = 400f, heightDp = 800f), onBack = { state.stopBrowsing() })
        }
        // Alphabetical order under the letters, the entries marked « auto: non » included.
        listOf("0-9", "G", "M", "S").forEach { onNodeWithTag(GlossaryTags.letter(it)).performScrollTo() }
        listOf("49-3", "gouvernement", "motion-de-censure", "senat").forEach { onNodeWithTag(GlossaryTags.row(it)).performScrollTo() }

        onNodeWithTag(GlossaryTags.SEARCH).performTextInput("SENAT")
        waitForIdle()
        onNodeWithTag(GlossaryTags.row("senat")).assertExists()
        onNodeWithTag(GlossaryTags.row("motion-de-censure")).assertDoesNotExist()
        onNodeWithTag(GlossaryTags.row("49-3")).assertDoesNotExist()
        onNodeWithText("1 mot").assertExists()

        // One pane: the entry opens in place of the list, then the back button returns to it.
        onNodeWithTag(GlossaryTags.row("senat")).performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.DETAIL).assert(hasAnyDescendant(hasText("La seconde chambre du Parlement, élue par les élus locaux.")))
        onNodeWithTag(GlossaryTags.LIST).assertDoesNotExist()
        onNodeWithTag(GlossaryTags.PAGE_BACK).performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.LIST).assertExists()

        state.query = "zzz"
        waitForIdle()
        onNodeWithTag(GlossaryTags.NONE).assertExists()
    }

    @Test
    fun aWideWindowShowsTheListBesideTheEntry() = runComposeUiTest {
        val state = GlossaryFixtures.state()
        show(state, width = 1000, height = 740) {
            GlossaryScreen(state, WindowClass.of(widthDp = 1000f, heightDp = 740f), onBack = { state.stopBrowsing() })
        }
        onNodeWithTag(GlossaryTags.PICK).assertExists()
        onNodeWithTag(GlossaryTags.row("49-3")).performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.LIST).assertExists()
        onNodeWithTag(GlossaryTags.DETAIL).assert(hasAnyDescendant(hasText("49.3")))
        onNodeWithTag(GlossaryTags.PAGE_BACK).assertDoesNotExist()
    }

    @Test
    fun theHomeOfComprendreOpensTheFullGlossary() = runComposeUiTest {
        // The entries reach the device signed by a trusted key, as glossaire.json or the server would bring them.
        val store = LocalEventStore(JvmDatabase.inMemory())
        GlossaryFixtures.events.forEach { store.store(it) }
        val dependencies = AppDependencies(identityStore = InMemoryIdentityStore(), contentStore = store, editorialTrust = GlossaryFixtures.trust)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        openSpace(Space.COMPRENDRE)
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ComprendreTags.GLOSSARY_OPEN).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Ouvrir le glossaire (4 mots)").assertExists()
        onNodeWithTag(ComprendreTags.GLOSSARY_OPEN).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.SCREEN).assertExists()
        onNodeWithTag(GlossaryTags.row("motion-de-censure")).performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.DETAIL).assert(hasAnyDescendant(hasText("Vote par lequel les députés peuvent renverser le gouvernement.")))
        // Back to the home of "Comprendre".
        onNodeWithTag(GlossaryTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(GlossaryTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(ComprendreTags.HOME).assertExists()
    }

    @Test
    fun inTheShellAGuideLeadsFromAWordToTheFullGlossary() = runComposeUiTest {
        val store = LocalEventStore(JvmDatabase.inMemory())
        (GlossaryFixtures.events + GlossaryFixtures.guide).forEach { store.store(it) }
        val dependencies = AppDependencies(identityStore = InMemoryIdentityStore(), contentStore = store, editorialTrust = GlossaryFixtures.trust)
        setContent { Box(Modifier.requiredSize(1000.dp, 740.dp)) { PpfApp(dependencies) } }
        openSpace(Space.COMPRENDRE)
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(ContentTags.GUIDE_CARD + GlossaryFixtures.GUIDE_SLUG).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ContentTags.GUIDE_CARD + GlossaryFixtures.GUIDE_SLUG).performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(GlossaryTags.hint("motion-de-censure"), useUnmergedTree = true).performScrollTo().performTouchInput { click(center) }
        waitForIdle()
        onNodeWithTag(GlossaryTags.MORE).performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.PAGE).assertExists()
        onNodeWithTag(GlossaryTags.OPEN_FULL).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(500)
        onNodeWithTag(GlossaryTags.PAGE).assertDoesNotExist()
        onNodeWithTag(GlossaryTags.SCREEN).assertExists()
        onNodeWithTag(GlossaryTags.DETAIL).assert(hasAnyDescendant(hasText("Vote par lequel les députés peuvent renverser le gouvernement.")))
    }
}