package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_name
import org.politisationpopulairefrancaise.ui.resources.app_wordmark
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tag of the wordmark of the top bars. */
object WordmarkTags {
    const val WORDMARK = "wordmark"
}

/**
 * The name of the platform in a top bar, always whole and never cut (decision of the owner relayed
 * on 11 November 2026, D-182: the name is written in full, no abbreviation). The first way that fits
 * the room left by the buttons wins: the wordmark on two lines at its size; one word per line; one
 * word per line a little smaller; and, with a large font on a small phone, one word per line at a
 * size that no longer follows the font setting (the name is then a logo, read whole by screen
 * readers). The drawn text says whether it fits, with the font size chosen by the user and the
 * typeface really drawn; a word is never broken. The symbol of PPF comes before the name when the
 * whole name still fits beside it (D-185); otherwise the name alone is kept.
 */
@Composable
fun AdaptiveWordmark(modifier: Modifier = Modifier, withSymbol: Boolean = true) {
    val twoLines = stringResource(Res.string.app_wordmark)
    val name = stringResource(Res.string.app_name)
    val oneWordPerLine = remember(name) { name.split(' ').joinToString("\n") }
    val dimens = PpfTheme.dimens
    val fontScale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelLarge
    fun style(size: TextUnit) = base.copy(fontSize = size, lineHeight = (size.value * 1.15f).sp)
    val ways: List<Pair<String, TextStyle>> = listOf(
        twoLines to style(dimens.wordmarkSize),
        oneWordPerLine to style(dimens.wordmarkSize),
        oneWordPerLine to style(dimens.wordmarkSmallSize),
        // The same drawn size whatever the font setting (sp divided by the font scale), then a little smaller.
        oneWordPerLine to style((dimens.wordmarkSmallSize.value / fontScale).sp),
        oneWordPerLine to style((dimens.wordmarkSmallSize.value * 0.9f / fontScale).sp),
    )
    // With the symbol first (D-185); when the whole name cannot fit beside it, the name alone (D-182 comes first).
    val candidates: List<Triple<Boolean, String, TextStyle>> =
        (if (withSymbol) ways.map { Triple(true, it.first, it.second) } else emptyList()) + ways.map { Triple(false, it.first, it.second) }
    val brand = PpfTheme.palette.brand
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        // Starts again from the first way whenever the room or the font setting changes (a phone turned).
        var index by remember(constraints.maxWidth, constraints.maxHeight, fontScale, withSymbol) { mutableIntStateOf(0) }
        val (symbol, text, style) = candidates[index.coerceAtMost(candidates.lastIndex)]
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (symbol) {
                PpfSymbol(dimens.topBarSymbolSize, decorative = true)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = brand,
                style = style,
                softWrap = false,
                // The text itself says when it does not fit, in the typeface really drawn: the identity's is
                // loaded after the first frame, and a layout measured before it would be too narrow.
                onTextLayout = { layout ->
                    if ((layout.didOverflowWidth || layout.didOverflowHeight) && index < candidates.lastIndex) index++
                },
                modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = name }.testTag(WordmarkTags.WORDMARK),
            )
        }
    }
}
