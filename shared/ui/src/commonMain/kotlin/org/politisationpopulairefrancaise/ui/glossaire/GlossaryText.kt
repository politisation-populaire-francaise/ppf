package org.politisationpopulairefrancaise.ui.glossaire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import org.politisationpopulairefrancaise.core.comprendre.GlossaryEntry
import org.politisationpopulairefrancaise.core.comprendre.GlossaryIndex
import org.politisationpopulairefrancaise.core.comprendre.GlossaryMatch
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.ui.components.ExampleBadge
import org.politisationpopulairefrancaise.ui.components.PpfIconButton
import org.politisationpopulairefrancaise.ui.components.PpfTextButton
import org.politisationpopulairefrancaise.ui.components.SystemBack
import org.politisationpopulairefrancaise.ui.content.SimpleMarkdown
import org.politisationpopulairefrancaise.ui.icons.Lucide
import org.politisationpopulairefrancaise.ui.resources.Res
import org.politisationpopulairefrancaise.ui.resources.content_origin_ai
import org.politisationpopulairefrancaise.ui.resources.content_origin_human
import org.politisationpopulairefrancaise.ui.resources.glossaire_hint_spoken
import org.politisationpopulairefrancaise.ui.resources.glossaire_tooltip_close
import org.politisationpopulairefrancaise.ui.resources.glossaire_tooltip_more
import org.politisationpopulairefrancaise.ui.theme.PpfTheme
import kotlin.math.max

object GlossaryTags {
    const val TOOLTIP = "glossary-tooltip"
    const val TOOLTIP_CLOSE = "glossary-tooltip-close"
    const val MORE = "glossary-more"
    const val PAGE = "glossary-page"
    const val PAGE_CLOSE = "glossary-page-close"
    const val PAGE_BACK = "glossary-page-back"
    const val OPEN_FULL = "glossary-open-full"
    const val SCREEN = "glossary-screen"
    const val LIST = "glossary-list"
    const val SEARCH = "glossary-search"
    const val NONE = "glossary-none"
    const val BACK = "glossary-back"
    const val DETAIL = "glossary-detail"
    const val PICK = "glossary-pick"

    /** The small « ? » after a spotted form. */
    fun hint(slug: String): String = "glossary-hint-$slug"

    fun row(slug: String): String = "glossary-row-$slug"

    fun letter(letter: String): String = "glossary-letter-$letter"

    fun seeAlso(slug: String): String = "glossary-see-also-$slug"

    fun related(slug: String): String = "glossary-related-$slug"
}

/** An inline text and the forms of the glossary it links (first occurrences only). */
@Immutable
class GlossedText(val text: AnnotatedString, val marks: List<GlossaryMatch>) {
    companion object {
        fun plain(text: AnnotatedString): GlossedText = GlossedText(text, emptyList())
    }
}

/**
 * Spots the forms of the glossary in the pieces of one content, in reading order: each entry is
 * linked once, at its first occurrence, in this content and, with [occurrences], in the other
 * pieces of the same screen. Links (« texte (adresse) ») are left alone.
 */
internal class GlossaryMarker(
    private val index: GlossaryIndex,
    private val occurrences: GlossaryOccurrences?,
    private val owner: Any,
) {
    private val seen = HashSet<String>()

    fun mark(text: AnnotatedString): GlossedText {
        if (index.isEmpty || text.isEmpty()) return GlossedText.plain(text)
        val links = text.getStringAnnotations(SimpleMarkdown.LINK_TAG, 0, text.length).map { it.start until it.end }
        val marks = index.find(text.text, links, occurrences?.excluded.orEmpty())
            .filter { match -> seen.add(match.slug) && (occurrences?.claim(match.slug, owner) ?: true) }
        return GlossedText(text, marks)
    }
}


/** What [MarkedText] draws: the text with its links and « ? », and where each form lies in it. */
private class Display(val text: AnnotatedString, val forms: List<IntRange>, val hints: List<Pair<String, String>>)

private const val HINT_ID = "glossary-hint-"

/**
 * The text a « ? » stands for in the string: one invisible character (word joiner). A placeholder
 * counts as one character in the layout of every platform, so the offsets of the forms, of the links
 * and of the dotted underline stay right; and a copied paragraph carries nothing visible.
 */
private const val HINT_TEXT = "\u2060"

/**
 * A text whose spotted forms are links (owner's request of 7 November 2026; contract, section 2.4):
 * a discreet dotted underline under the form (drawn from the layout, on every line the form
 * covers) and a small « ? » in a circle right after it. Touching the word or the « ? » (or Tab then
 * Enter) opens the tooltip anchored to the word. Screen readers find the « ? » as a button named
 * « <terme>, définition », which opens the same tooltip.
 * Without a glossary or without forms, a plain [Text].
 */
@Composable
internal fun MarkedText(glossed: GlossedText, style: TextStyle, modifier: Modifier = Modifier) {
    val glossary = LocalGlossary.current
    if (glossary == null || glossed.marks.isEmpty()) {
        Text(glossed.text, modifier = modifier, style = style)
        return
    }
    val palette = PpfTheme.palette
    var open by remember(glossed) { mutableStateOf<Int?>(null) }
    val highlight = palette.brandTint
    val display = remember(glossed, highlight) {
        val styles = TextLinkStyles(
            focusedStyle = SpanStyle(background = highlight),
            hoveredStyle = SpanStyle(background = highlight),
            pressedStyle = SpanStyle(background = highlight),
        )
        val forms = mutableListOf<IntRange>()
        val hints = mutableListOf<Pair<String, String>>()
        val text = buildAnnotatedString {
            var cursor = 0
            glossed.marks.forEachIndexed { index, mark ->
                append(glossed.text.subSequence(cursor, mark.start))
                withLink(LinkAnnotation.Clickable(tag = "glossaire:${mark.slug}", styles = styles, linkInteractionListener = LinkInteractionListener { open = index })) {
                    val start = length
                    append(glossed.text.subSequence(mark.start, mark.end))
                    forms += start until length
                    hints += mark.slug to glossed.text.text.substring(mark.start, mark.end)
                    appendInlineContent(HINT_ID + index, HINT_TEXT)
                }
                cursor = mark.end
            }
            append(glossed.text.subSequence(cursor, glossed.text.length))
        }
        Display(text, forms, hints)
    }
    val hintColor = palette.brand
    val inline = remember(display, hintColor) {
        display.hints.mapIndexed { index, (slug, form) ->
            HINT_ID + index to InlineTextContent(Placeholder(width = 1.15.em, height = 1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)) {
                val label = stringResource(Res.string.glossaire_hint_spoken, form)
                // For screen readers only: no pointer input, a touch goes through to the link of the word.
                HintMark(hintColor, Modifier.testTag(GlossaryTags.hint(slug)).semantics { contentDescription = label; role = Role.Button; onClick { open = index; true } })
            }
        }.toMap()
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val dots = palette.brand.copy(alpha = 0.6f)
    Box(modifier) {
        // The tooltip is anchored to this box, which holds nothing but the text.
        Box {
            Text(
                text = display.text,
                style = style,
                inlineContent = inline,
                onTextLayout = { layout = it },
                modifier = Modifier.drawBehind { layout?.let { result -> display.forms.forEach { drawDottedUnderline(result, it, dots) } } },
            )
            val index = open
            val result = layout
            val entry = index?.let { glossed.marks.getOrNull(it) }?.let { glossary.index.entry(it.slug) }
            if (index != null && result != null && entry != null) {
                GlossaryTooltip(
                    entry = entry,
                    word = wordBounds(result, display.forms[index]),
                    onDismiss = { open = null },
                    onMore = {
                        open = null
                        glossary.openPage(entry.slug)
                    },
                )
            }
        }
    }
}

/** The « ? » in a circle, at the size of the text, a little apart from the word. */
@Composable
private fun HintMark(tint: Color, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Icon(Lucide.CircleQuestionMark, contentDescription = null, tint = tint, modifier = Modifier.fillMaxHeight(0.9f).aspectRatio(1f))
    }
}

/** A dotted line under [range], line by line when the form is cut at the end of a line. */
private fun DrawScope.drawDottedUnderline(layout: TextLayoutResult, range: IntRange, color: Color) {
    if (range.isEmpty() || range.last >= layout.layoutInput.text.length) return
    val stroke = 1.5.dp.toPx()
    val gap = 3.dp.toPx()
    val below = 2.5.dp.toPx()
    val firstLine = layout.getLineForOffset(range.first)
    val lastLine = layout.getLineForOffset(range.last)
    for (line in firstLine..lastLine) {
        val from = if (line == firstLine) range.first else layout.getLineStart(line)
        val to = if (line == lastLine) range.last else minOf(range.last, layout.getLineEnd(line, visibleEnd = true) - 1)
        if (to < from) continue
        val left = layout.getBoundingBox(from).left
        val right = layout.getBoundingBox(to).right
        if (right <= left) continue
        val y = layout.getLineBaseline(line) + below
        drawLine(
            color = color,
            start = Offset(left + stroke / 2, y),
            end = Offset(right - stroke / 2, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, gap)),
        )
    }
}

/** The bounds of the last line of the form (where its « ? » is), in the coordinates of the text. */
private fun wordBounds(layout: TextLayoutResult, range: IntRange): IntRect {
    val lastLine = layout.getLineForOffset(range.last)
    val firstLine = layout.getLineForOffset(range.first)
    val left = if (firstLine == lastLine) layout.getBoundingBox(range.first).left else layout.getLineLeft(lastLine)
    val right = max(layout.getBoundingBox(range.last).right, left + 1f)
    return IntRect(left.toInt(), layout.getLineTop(lastLine).toInt(), right.toInt(), layout.getLineBottom(lastLine).toInt())
}

/**
 * Under the word, or above it when the window lacks room below; always inside the window, [margin]
 * away from its edges.
 */
private data class TooltipPosition(val word: IntRect, val gap: Int, val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val below = anchorBounds.top + word.bottom + gap
        val above = anchorBounds.top + word.top - gap - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height - margin -> below
            above >= margin -> above
            else -> max(margin, windowSize.height - popupContentSize.height - margin)
        }
        val x = (anchorBounds.left + word.left).coerceAtMost(windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

/**
 * The tooltip of a spotted word: the term, the short definition, the [EXEMPLE] badge, its origin,
 * « En savoir plus » and a button to close. It closes when touched elsewhere, with Escape and with
 * the system's back.
 */
@Composable
private fun GlossaryTooltip(entry: GlossaryEntry, word: IntRect, onDismiss: () -> Unit, onMore: () -> Unit) {
    val density = LocalDensity.current
    val provider = remember(word, density) { with(density) { TooltipPosition(word, gap = 6.dp.roundToPx(), margin = 8.dp.roundToPx()) } }
    SystemBack(enabled = true, onBack = onDismiss)
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        // The tooltip lives in its own window: the selection of the text around must not reach it.
        DisableSelection { TooltipCard(entry, onDismiss, onMore) }
    }
}

@Composable
private fun TooltipCard(entry: GlossaryEntry, onDismiss: () -> Unit, onMore: () -> Unit) {
    val palette = PpfTheme.palette
    val shape = MaterialTheme.shapes.small
    val focus = remember { FocusRequester() }
    // The keyboard and the screen readers move into the tooltip, so that Escape and Tab work at once.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier
            .padding(2.dp)
            .widthIn(max = 320.dp)
            .shadow(8.dp, shape)
            .background(palette.surface2, shape)
            .border(1.dp, palette.borderStrong, shape)
            .onKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focus)
            .focusable()
            .semantics { paneTitle = entry.term }
            .testTag(GlossaryTags.TOOLTIP)
            .padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.term, style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.weight(1f).semantics { heading() })
            if (entry.example) ExampleBadge()
            PpfIconButton(Lucide.X, stringResource(Res.string.glossaire_tooltip_close), onDismiss, Modifier.testTag(GlossaryTags.TOOLTIP_CLOSE), tint = palette.inkMuted)
        }
        Text(entry.definition, style = MaterialTheme.typography.bodyMedium, color = palette.ink, modifier = Modifier.padding(end = 12.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (entry.article.origin == ContentOrigin.AI_ASSISTED) Res.string.content_origin_ai else Res.string.content_origin_human),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle,
        )
        PpfTextButton(onClick = onMore, text = stringResource(Res.string.glossaire_tooltip_more), modifier = Modifier.testTag(GlossaryTags.MORE))
    }
}
