package org.politisationpopulairefrancaise.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.callout_attention
import org.politisationpopulairefrancaise.ui.resources.callout_conseil
import org.politisationpopulairefrancaise.ui.resources.callout_en_bref
import org.politisationpopulairefrancaise.ui.resources.callout_illegal
import org.politisationpopulairefrancaise.ui.resources.callout_legal

/** The kinds of highlighted boxes; [key] is the word used in the contents (`::: en-bref`). */
enum class CalloutType(val key: String) {
    EN_BREF("en-bref"),
    LEGAL("legal"),
    ILLEGAL("illegal"),
    ATTENTION("attention"),
    CONSEIL("conseil"),
    ;

    companion object {
        fun fromKey(key: String): CalloutType? = entries.firstOrNull { it.key == key }
    }
}

@Composable
private fun CalloutType.colors(): CalloutColors = when (this) {
    CalloutType.EN_BREF -> PpfTheme.extended.enBref
    CalloutType.LEGAL -> PpfTheme.extended.legal
    CalloutType.ILLEGAL -> PpfTheme.extended.illegal
    CalloutType.ATTENTION -> PpfTheme.extended.attention
    CalloutType.CONSEIL -> PpfTheme.extended.conseil
}

private fun CalloutType.icon(): ImageVector = when (this) {
    CalloutType.EN_BREF -> Lucide.Info
    CalloutType.LEGAL -> Lucide.CircleCheck
    CalloutType.ILLEGAL -> Lucide.Ban
    CalloutType.ATTENTION -> Lucide.TriangleExclamationPoint
    CalloutType.CONSEIL -> Lucide.Lightbulb
}

@Composable
fun CalloutType.defaultTitle(): String = stringResource(
    when (this) {
        CalloutType.EN_BREF -> Res.string.callout_en_bref
        CalloutType.LEGAL -> Res.string.callout_legal
        CalloutType.ILLEGAL -> Res.string.callout_illegal
        CalloutType.ATTENTION -> Res.string.callout_attention
        CalloutType.CONSEIL -> Res.string.callout_conseil
    },
)

/**
 * A highlighted box of the identity: tinted background, 10 dp corners, an icon and a title in the
 * semantic colour, then whatever [content] the caller provides (text, bullet list). The colour
 * alone never carries the meaning: the icon and the title say it (WCAG 1.4.1).
 */
@Composable
fun Callout(
    type: CalloutType,
    modifier: Modifier = Modifier,
    title: String = type.defaultTitle(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = type.colors()
    Surface(
        color = colors.container,
        contentColor = colors.onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(type.icon(), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
