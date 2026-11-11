package org.politisationpopulairefrancaise.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.app_name
import org.politisationpopulairefrancaise.ui.resources.app_short_name
import org.politisationpopulairefrancaise.ui.resources.app_wordmark
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Test tags of the wordmark of the top bars. */
object WordmarkTags {
    const val FULL = "wordmark-full"
    const val SHORT = "wordmark-short"
}

/**
 * The name of the platform in a top bar, never cut: the full wordmark on two lines when it fits in
 * the room left by the buttons (tablets, wide phones at the default font size), otherwise the short
 * name « PPF ». Screen readers always hear the full name. The width is measured with the font size
 * chosen by the user, so a larger font switches to the short name instead of cutting a word.
 */
@Composable
fun AdaptiveWordmark(modifier: Modifier = Modifier) {
    val full = stringResource(Res.string.app_wordmark)
    val name = stringResource(Res.string.app_name)
    val style = MaterialTheme.typography.labelLarge.copy(
        fontSize = PpfTheme.dimens.wordmarkSize,
        lineHeight = PpfTheme.dimens.wordmarkLineHeight,
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        // Each line of the wordmark stays whole: the widest one decides.
        val fullWidth = remember(full, style, measurer) { measurer.measure(full, style, softWrap = false).size.width }
        if (fullWidth <= constraints.maxWidth) {
            Text(
                text = full,
                color = PpfTheme.palette.brand,
                style = style,
                maxLines = 2,
                modifier = Modifier.semantics { contentDescription = name }.testTag(WordmarkTags.FULL),
            )
        } else {
            Text(
                text = stringResource(Res.string.app_short_name),
                color = PpfTheme.palette.brand,
                style = style.copy(fontSize = PpfTheme.dimens.wordmarkShortSize, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = name }.testTag(WordmarkTags.SHORT),
            )
        }
    }
}
