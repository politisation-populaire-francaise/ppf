package org.politisationpopulairefrancaise.ui.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.Objection
import org.politisationpopulairefrancaise.core.comprendre.TimelineEntry
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_objections_title
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/** Renders the Markdown subset of [SimpleMarkdown] with the Material typography and the PPF boxes. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        MarkdownBlocks(SimpleMarkdown.parse(markdown))
    }
}

@Composable
private fun MarkdownBlocks(blocks: List<MarkdownBlock>, insideCallout: Boolean = false) {
    blocks.forEachIndexed { index, block ->
        when (block) {
            is MarkdownBlock.Heading -> {
                if (index > 0) Spacer(Modifier.height(if (block.level == 1) 20.dp else 14.dp))
                Text(
                    text = SimpleMarkdown.inline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = if (insideCallout) LocalContentColor.current else PpfTheme.palette.brand,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
            }
            is MarkdownBlock.Paragraph -> {
                Text(SimpleMarkdown.inline(block.text), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(if (insideCallout) 6.dp else 14.dp))
            }
            is MarkdownBlock.Bullets -> {
                block.items.forEach { item ->
                    Row(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(SimpleMarkdown.inline(item), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(if (insideCallout) 2.dp else 10.dp))
            }
            is MarkdownBlock.Callout -> {
                Callout(block.type) {
                    MarkdownBlocks(block.blocks, insideCallout = true)
                }
                Spacer(Modifier.height(16.dp))
            }
            is MarkdownBlock.Objections -> {
                ObjectionsBox(block.items)
                Spacer(Modifier.height(16.dp))
            }
            is MarkdownBlock.Timeline -> {
                TimelineList(block.entries)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Objections and their sourced answers (pluralism): a quiet panel, each objection in bold as a
 * quotation, its answer under it.
 */
@Composable
fun ObjectionsBox(items: List<Objection>, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Surface(
        modifier = modifier.fillMaxWidth().testTag(MarkdownTags.OBJECTIONS),
        shape = MaterialTheme.shapes.medium,
        color = palette.surface2,
        contentColor = palette.ink,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.MessagesSquare, contentDescription = null, tint = palette.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.comprendre_objections_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            items.forEach { item ->
                Spacer(Modifier.height(12.dp))
                Text(SimpleMarkdown.inline(item.objection), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(palette.brand))
                    Text(SimpleMarkdown.inline(item.answer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** A chronology: each date in the brand colour on its own line, the event under it, joined by a rule. */
@Composable
fun TimelineList(entries: List<TimelineEntry>, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(MarkdownTags.TIMELINE)) {
        entries.forEachIndexed { index, entry ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp).fillMaxHeight()) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).background(palette.brand, CircleShape))
                    if (index < entries.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(palette.border))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 14.dp)) {
                    Text(entry.frenchDate, style = MaterialTheme.typography.labelLarge, color = palette.brand)
                    Text(SimpleMarkdown.inline(entry.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

object MarkdownTags {
    const val OBJECTIONS = "markdown-objections"
    const val TIMELINE = "markdown-timeline"
}
