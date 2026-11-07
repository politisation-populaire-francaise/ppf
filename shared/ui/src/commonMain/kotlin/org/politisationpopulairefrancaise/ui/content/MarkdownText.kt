package org.politisationpopulairefrancaise.ui.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.comprendre.Objection
import org.politisationpopulairefrancaise.core.comprendre.TimelineEntry
import org.politisationpopulairefrancaise.core.text.FrenchText
import org.politisationpopulairefrancaise.ui.glossaire.GlossaryMarker
import org.politisationpopulairefrancaise.ui.glossaire.GlossaryOccurrences
import org.politisationpopulairefrancaise.ui.glossaire.GlossedText
import org.politisationpopulairefrancaise.ui.glossaire.LocalGlossary
import org.politisationpopulairefrancaise.ui.glossaire.LocalGlossaryOccurrences
import org.politisationpopulairefrancaise.ui.glossaire.MarkedText
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.comprendre_objections_title
import org.politisationpopulairefrancaise.ui.theme.Callout
import org.politisationpopulairefrancaise.ui.theme.CalloutType
import org.politisationpopulairefrancaise.ui.theme.PpfTheme

/**
 * Renders the Markdown subset of [SimpleMarkdown] with the Material typography and the PPF boxes.
 *
 * When the shell provides the glossary ([LocalGlossary]), the first occurrence of each term in the
 * content becomes a link to its definition (D-048), except in headings, links and the sources (the
 * blocks after a « Sources » heading). [glossary] = false leaves the whole text alone (a list of
 * sources shown on its own). Inside a [org.politisationpopulairefrancaise.ui.glossaire.GlossaryScope],
 * a term already linked by another piece of the same screen is not linked again.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, glossary: Boolean = true) {
    val index = if (glossary) LocalGlossary.current?.index else null
    val occurrences = LocalGlossaryOccurrences.current
    val blocks = remember(markdown, index, occurrences) {
        Preparer(markerOf(index, occurrences, markdown)).blocks(SimpleMarkdown.parse(markdown))
    }
    Column(modifier) {
        ShownBlocks(blocks)
    }
}

private fun markerOf(index: GlossaryIndex?, occurrences: GlossaryOccurrences?, owner: Any): GlossaryMarker? =
    index?.takeIf { !it.isEmpty }?.let { GlossaryMarker(it, occurrences, owner) }

/** The blocks of [SimpleMarkdown] with their inline texts ready to draw, glossary links included. */
private sealed interface Shown {
    data class Heading(val level: Int, val text: AnnotatedString) : Shown
    data class Paragraph(val text: GlossedText) : Shown
    data class Bullets(val items: List<GlossedText>) : Shown
    data class Callout(val type: CalloutType, val blocks: List<Shown>) : Shown
    data class Objections(val items: List<Pair<GlossedText, GlossedText>>) : Shown
    data class Timeline(val entries: List<Pair<TimelineEntry, GlossedText>>) : Shown
}

/** Prepares the texts in reading order, so that each term is linked at its first occurrence. */
private class Preparer(private var marker: GlossaryMarker?) {

    fun text(raw: String): GlossedText {
        val inline = SimpleMarkdown.inline(raw)
        return marker?.mark(inline) ?: GlossedText.plain(inline)
    }

    fun blocks(blocks: List<MarkdownBlock>): List<Shown> = blocks.map { block ->
        when (block) {
            is MarkdownBlock.Heading -> {
                // Nothing is spotted in the sources, which close the contents.
                if (FrenchText.fold(block.text).let { it == SOURCES || it.startsWith("$SOURCES ") }) marker = null
                Shown.Heading(block.level, SimpleMarkdown.inline(block.text))
            }
            is MarkdownBlock.Paragraph -> Shown.Paragraph(text(block.text))
            is MarkdownBlock.Bullets -> Shown.Bullets(block.items.map(::text))
            is MarkdownBlock.Callout -> Shown.Callout(block.type, blocks(block.blocks))
            is MarkdownBlock.Objections -> Shown.Objections(block.items.map { text(it.objection) to text(it.answer) })
            is MarkdownBlock.Timeline -> Shown.Timeline(block.entries.map { it to text(it.text) })
        }
    }

    private companion object {
        const val SOURCES = "sources"
    }
}

@Composable
private fun ShownBlocks(blocks: List<Shown>, insideCallout: Boolean = false) {
    blocks.forEachIndexed { index, block ->
        when (block) {
            is Shown.Heading -> {
                if (index > 0) Spacer(Modifier.height(if (block.level == 1) 20.dp else 14.dp))
                Text(
                    text = block.text,
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
            is Shown.Paragraph -> {
                MarkedText(block.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(if (insideCallout) 6.dp else 14.dp))
            }
            is Shown.Bullets -> {
                block.items.forEach { item ->
                    Row(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        MarkedText(item, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(if (insideCallout) 2.dp else 10.dp))
            }
            is Shown.Callout -> {
                Callout(block.type) {
                    ShownBlocks(block.blocks, insideCallout = true)
                }
                Spacer(Modifier.height(16.dp))
            }
            is Shown.Objections -> {
                ObjectionsPanel(block.items)
                Spacer(Modifier.height(16.dp))
            }
            is Shown.Timeline -> {
                TimelinePanel(block.entries)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Objections and their sourced answers (pluralism): a quiet panel, each objection in bold as a
 * quotation, its answer under it. Its terms are spotted like any content.
 */
@Composable
fun ObjectionsBox(items: List<Objection>, modifier: Modifier = Modifier) {
    val index = LocalGlossary.current?.index
    val occurrences = LocalGlossaryOccurrences.current
    val shown = remember(items, index, occurrences) {
        val preparer = Preparer(markerOf(index, occurrences, items))
        items.map { preparer.text(it.objection) to preparer.text(it.answer) }
    }
    ObjectionsPanel(shown, modifier)
}

@Composable
private fun ObjectionsPanel(items: List<Pair<GlossedText, GlossedText>>, modifier: Modifier = Modifier) {
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
            items.forEach { (objection, answer) ->
                Spacer(Modifier.height(12.dp))
                MarkedText(objection, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(palette.brand))
                    MarkedText(answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** A chronology: each date in the brand colour on its own line, the event under it, joined by a rule. */
@Composable
fun TimelineList(entries: List<TimelineEntry>, modifier: Modifier = Modifier) {
    val index = LocalGlossary.current?.index
    val occurrences = LocalGlossaryOccurrences.current
    val shown = remember(entries, index, occurrences) {
        val preparer = Preparer(markerOf(index, occurrences, entries))
        entries.map { it to preparer.text(it.text) }
    }
    TimelinePanel(shown, modifier)
}

@Composable
private fun TimelinePanel(entries: List<Pair<TimelineEntry, GlossedText>>, modifier: Modifier = Modifier) {
    val palette = PpfTheme.palette
    Column(modifier.fillMaxWidth().testTag(MarkdownTags.TIMELINE)) {
        entries.forEachIndexed { index, (entry, text) ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp).fillMaxHeight()) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).background(palette.brand, CircleShape))
                    if (index < entries.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(palette.border))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 14.dp)) {
                    Text(entry.frenchDate, style = MaterialTheme.typography.labelLarge, color = palette.brand)
                    MarkedText(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

object MarkdownTags {
    const val OBJECTIONS = "markdown-objections"
    const val TIMELINE = "markdown-timeline"
}
