package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
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
 * readers). Each way is measured with the font size chosen by the user, a word is never broken.
 */
@Composable
fun AdaptiveWordmark(modifier: Modifier = Modifier) {
    val twoLines = stringResource(Res.string.app_wordmark)
    val name = stringResource(Res.string.app_name)
    val oneWordPerLine = remember(name) { name.split(' ').joinToString("\n") }
    val dimens = PpfTheme.dimens
    val fontScale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelLarge
    fun style(size: TextUnit) = base.copy(fontSize = size, lineHeight = (size.value * 1.15f).sp)
    val candidates: List<Pair<String, TextStyle>> = listOf(
        twoLines to style(dimens.wordmarkSize),
        oneWordPerLine to style(dimens.wordmarkSize),
        oneWordPerLine to style(dimens.wordmarkSmallSize),
        // The same drawn size whatever the font setting (sp divided by the font scale), then a little smaller.
        oneWordPerLine to style((dimens.wordmarkSmallSize.value / fontScale).sp),
        oneWordPerLine to style((dimens.wordmarkSmallSize.value * 0.9f / fontScale).sp),
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val chosen = remember(candidates, maxWidth, maxHeight, measurer) {
            candidates.firstOrNull { (text, style) ->
                val size = measurer.measure(text, style, softWrap = false).size
                size.width <= maxWidth && size.height <= maxHeight
            } ?: candidates.last()
        }
        Text(
            text = chosen.first,
            color = PpfTheme.palette.brand,
            style = chosen.second,
            softWrap = false,
            modifier = Modifier.semantics { contentDescription = name }.testTag(WordmarkTags.WORDMARK),
        )
    }
}
