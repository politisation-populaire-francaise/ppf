package org.politisationpopulairefrancaise.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.ui.PpfApp
import org.politisationpopulairefrancaise.ui.agir.manifestation.ManifestationTags
import org.politisationpopulairefrancaise.ui.components.WordmarkTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The top bar of phones (D-215, D-181, D-182): the full name, never cut nor abbreviated, beside the
 * flat AIDE button, the messages, the notifications and the avatar, on the narrowest phones of the
 * review and with the largest font; every button at least 48 dp wide and high to the touch.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarTest {

    @Test
    fun theFullNameFitsBesideTheButtonsOnASmallPhoneWithALargeFont() {
        for (width in listOf(360, 384, 411)) for (fontScale in listOf(1f, 1.15f, 1.3f)) runComposeUiTest {
            setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Box(Modifier.requiredSize(width.dp, 740.dp)) { PpfApp() }
                }
            }
            waitForIdle()
            val case = "$width dp, font $fontScale"
            val wordmark = onNodeWithTag(WordmarkTags.WORDMARK).assertIsDisplayed().fetchSemanticsNode()
            assertEquals(listOf("Politisation Populaire Française"), wordmark.config.getOrNull(SemanticsProperties.ContentDescription), case)
            val shown = wordmark.config[SemanticsProperties.Text].joinToString("") { it.text }
            assertEquals("PolitisationPopulaireFrançaise", shown.replace("\n", "").replace(" ", ""), "$case: the whole name, no abbreviation")
            val layouts = mutableListOf<TextLayoutResult>()
            wordmark.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertFalse(layouts.single().hasVisualOverflow, "$case: the name is cut")

            // Nothing overlaps, from the menu to the avatar.
            val order = listOf(ShellTags.MENU_BUTTON, WordmarkTags.WORDMARK, ManifestationTags.HELP_BUTTON, ShellTags.navItem(Space.MESSAGES), ShellTags.NOTIFICATIONS, ShellTags.navItem(Space.MON_ESPACE))
            val bounds = order.map { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            bounds.zipWithNext().forEachIndexed { i, (a, b) -> assertTrue(a.right <= b.left + 0.5f, "$case: ${order[i]} overlaps ${order[i + 1]}") }
            assertTrue(bounds.last().right <= width * density.density + 0.5f, "$case: the avatar leaves the screen")
            for (tag in order - WordmarkTags.WORDMARK) {
                val node = onNodeWithTag(tag).fetchSemanticsNode()
                val touch = node.touchBoundsInRoot
                assertTrue(touch.width >= 48 * density.density - 0.5f && touch.height >= 48 * density.density - 0.5f, "$case: $tag is a ${touch.width / density.density} x ${touch.height / density.density} dp target")
            }
        }
    }
}
