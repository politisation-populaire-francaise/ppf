package org.politisationpopulairefrancaise.ui.content

import androidx.compose.foundation.layout.Column
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
        }
    }
}
