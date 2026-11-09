package org.politisationpopulairefrancaise.core.agir.poster

/**
 * The colours of the printed poster, the only ones its drawing uses (the tokens of `PpfTheme` are
 * made for screens). Black on white for everything that must be read, the code first: the best
 * contrast on any printer, colour or not, and the one QR readers expect. The brand blue (#2B3A8F,
 * contrast 10.6 to 1 on white) only marks the kind of poster, the labels and the rule: it stays a
 * dark grey on a black and white printer, so nothing depends on it. No red (the accent): it turns
 * pale on many printers, and a poster says nothing in colour alone. White is the paper: nothing is
 * printed for it, which saves ink.
 */
enum class PosterColor(val rgb: Int) {
    INK(0x000000),
    BRAND(0x2B3A8F),
    PAPER(0xFFFFFF),
}

/** One label and its value on a poster: "Quand", "Samedi 14 novembre 2026, 14 h à 17 h". */
data class PosterFact(val label: String, val value: String, val emphasis: Boolean = false)

/**
 * The words of a poster, in the language of the interface, put together by the screen from the
 * [PosterContent] and the string resources; the layout only places them.
 */
data class PosterText(
    /** "[EXEMPLE]" for an example content, else null. */
    val marker: String?,
    /** What the poster announces: "RASSEMBLEMENT", "APPEL · SE RÉUNIR". */
    val kind: String,
    val title: String,
    val facts: List<PosterFact>,
    val body: String?,
    /** Beside the code: « Scannez pour ajouter à votre agenda, même sans réseau ». */
    val scanTitle: String,
    val scanHelp: String?,
    val footer: String,
    /** The printer's mention typed by the person, printed at the very bottom; null when none. */
    val printer: String?,
)

/** Width of a text in the font of the platform, in points, at [size] points. */
fun interface PosterMeasure {
    fun width(text: String, size: Float, bold: Boolean): Float
}

/** What the platform draws, in points from the top left corner of the page. */
sealed interface PosterElement {
    data class TextLine(val text: String, val x: Float, val baseline: Float, val size: Float, val bold: Boolean, val color: PosterColor) : PosterElement

    data class Rule(val x: Float, val y: Float, val width: Float, val height: Float, val color: PosterColor) : PosterElement

    /** The square of the QR code, its quiet zone included: the platform draws the modules of the payload inside. */
    data class Code(val x: Float, val y: Float, val side: Float) : PosterElement
}

/** A page ready to draw: the same list for the PDF of Android and the image of the computer. */
data class PosterLayout(val format: PosterFormat, val elements: List<PosterElement>) {
    val code: PosterElement.Code get() = elements.filterIsInstance<PosterElement.Code>().single()

    val lines: List<PosterElement.TextLine> get() = elements.filterIsInstance<PosterElement.TextLine>()
}

/**
 * Places the words and the code of a poster on an A4 or an A3 page, independently of any screen:
 * the platform gives the width of its texts ([PosterMeasure]) and draws the result on a PDF page
 * (Android) or an image (computer). Sizes are written for A4 and grow with the page.
 *
 * From top to bottom: the [EXEMPLE] marker, the kind, the title (as large as the rest allows, four
 * lines at most), a rule, the facts (when, where, who), the short text in the room left; at the
 * bottom, the code (88 mm a side on A4) with the line to scan beside it, then the footer and the
 * printer's mention. A text too long for its room ends with "…": the code carries the whole event.
 */
object PosterLayouts {
    const val MARGIN: Float = 40f
    const val CODE_SIDE: Float = 248f
    const val CODE_GAP: Float = 18f
    val TITLE_SIZES: List<Float> = listOf(48f, 42f, 36f, 31f, 27f, 24f)
    const val TITLE_MAX_LINES: Int = 4
    const val MARKER_SIZE: Float = 13f
    const val KIND_SIZE: Float = 15f
    const val LABEL_SIZE: Float = 12f
    const val VALUE_SIZE: Float = 18f
    const val EMPHASIS_SIZE: Float = 21f
    const val VALUE_MAX_LINES: Int = 3
    const val BODY_SIZE: Float = 14f
    const val SCAN_TITLE_SIZE: Float = 19f
    const val SCAN_HELP_SIZE: Float = 11.5f
    const val FOOTER_SIZE: Float = 10.5f
    const val PRINTER_SIZE: Float = 9f
    const val ELLIPSIS: String = "…"

    /** Room from the top of a line to its baseline, and from one line to the next, in sizes. */
    private const val ASCENT: Float = 0.86f
    private const val TIGHT: Float = 1.15f
    private const val LOOSE: Float = 1.32f

    fun layout(text: PosterText, format: PosterFormat, measure: PosterMeasure): PosterLayout {
        val s = format.scale
        val width = format.widthPt.toFloat()
        val height = format.heightPt.toFloat()
        val margin = MARGIN * s
        val content = width - 2 * margin
        val out = mutableListOf<PosterElement>()

        // The bottom first: it never moves, and the top takes what is left.
        val footer = wrap(text.footer, content, FOOTER_SIZE * s, false, 2, measure)
        val printer = text.printer?.trim()?.takeIf { it.isNotEmpty() }?.let { wrap(it, content, PRINTER_SIZE * s, false, 2, measure) }.orEmpty()
        var bottom = height - margin
        for (line in printer.reversed()) {
            out += PosterElement.TextLine(line, margin, bottom - (LOOSE - ASCENT) * PRINTER_SIZE * s, PRINTER_SIZE * s, false, PosterColor.INK)
            bottom -= LOOSE * PRINTER_SIZE * s
        }
        if (printer.isNotEmpty()) bottom -= 4 * s
        for (line in footer.reversed()) {
            out += PosterElement.TextLine(line, margin, bottom - (LOOSE - ASCENT) * FOOTER_SIZE * s, FOOTER_SIZE * s, false, PosterColor.INK)
            bottom -= LOOSE * FOOTER_SIZE * s
        }
        val side = CODE_SIDE * s
        val codeBottom = bottom - 14 * s
        val codeTop = codeBottom - side
        out += PosterElement.Rule(margin, codeTop - 16 * s, content, 1.5f * s, PosterColor.BRAND)
        out += PosterElement.Code(margin, codeTop, side)

        // Beside the code: what scanning it does, then how.
        val columnX = margin + side + CODE_GAP * s
        val column = content - side - CODE_GAP * s
        var y = codeTop + 10 * s
        val scanTitle = wrap(text.scanTitle, column, SCAN_TITLE_SIZE * s, true, 6, measure)
        y = place(out, scanTitle, columnX, y, SCAN_TITLE_SIZE * s, true, PosterColor.INK, TIGHT)
        text.scanHelp?.let { help ->
            y += 10 * s
            val room = ((codeBottom - y) / (LOOSE * SCAN_HELP_SIZE * s)).toInt()
            if (room > 0) place(out, wrap(help, column, SCAN_HELP_SIZE * s, false, room, measure), columnX, y, SCAN_HELP_SIZE * s, false, PosterColor.INK, LOOSE)
        }

        // The top: marker, kind, title, rule, facts, then the short text in the room left.
        val limit = codeTop - 30 * s
        y = margin
        text.marker?.let { y = place(out, listOf(it), margin, y, MARKER_SIZE * s, true, PosterColor.BRAND, LOOSE) }
        y = place(out, wrap(text.kind, content, KIND_SIZE * s, true, 2, measure), margin, y, KIND_SIZE * s, true, PosterColor.BRAND, LOOSE)
        y += 6 * s

        val facts = text.facts.map { fact ->
            val size = (if (fact.emphasis) EMPHASIS_SIZE else VALUE_SIZE) * s
            Triple(fact, size, wrap(fact.value, content, size, fact.emphasis, VALUE_MAX_LINES, measure))
        }
        val factsHeight = facts.sumOf { (_, size, lines) -> (LOOSE * LABEL_SIZE * s + lines.size * TIGHT * size + 12 * s).toDouble() }.toFloat()
        val ruleHeight = 3 * s + 2 * 14 * s
        val available = limit - y
        // The largest title that leaves room for the facts; the smallest one cut after a fifth line otherwise.
        var titleSize = TITLE_SIZES.last() * s
        var title = wrap(text.title, content, titleSize, true, TITLE_MAX_LINES + 1, measure)
        for (candidate in TITLE_SIZES) {
            val size = candidate * s
            val lines = wrap(text.title, content, size, true, Int.MAX_VALUE, measure)
            if (lines.size <= TITLE_MAX_LINES && lines.size * TIGHT * size + ruleHeight + factsHeight <= available) {
                titleSize = size
                title = lines
                break
            }
        }
        y = place(out, title, margin, y, titleSize, true, PosterColor.INK, TIGHT)
        y += 14 * s
        out += PosterElement.Rule(margin, y, content, 3 * s, PosterColor.BRAND)
        y += 3 * s + 14 * s

        for ((fact, size, lines) in facts) {
            val needed = LOOSE * LABEL_SIZE * s + TIGHT * size
            if (y + needed > limit) break
            y = place(out, listOf(fact.label), margin, y, LABEL_SIZE * s, true, PosterColor.BRAND, LOOSE)
            val room = ((limit - y) / (TIGHT * size)).toInt().coerceAtLeast(1)
            val shown = if (lines.size > room) wrap(fact.value, content, size, fact.emphasis, room, measure) else lines
            y = place(out, shown, margin, y, size, fact.emphasis, PosterColor.INK, TIGHT)
            y += 12 * s
        }

        text.body?.trim()?.takeIf { it.isNotEmpty() }?.let { body ->
            val room = ((limit - y) / (LOOSE * BODY_SIZE * s)).toInt()
            if (room > 0) place(out, wrap(body, content, BODY_SIZE * s, false, room, measure), margin, y + 4 * s, BODY_SIZE * s, false, PosterColor.INK, LOOSE)
        }
        return PosterLayout(format, out)
    }

    /** Adds [lines] from [top] down; returns the top of the next line. */
    private fun place(
        out: MutableList<PosterElement>,
        lines: List<String>,
        x: Float,
        top: Float,
        size: Float,
        bold: Boolean,
        color: PosterColor,
        leading: Float,
    ): Float {
        var y = top
        for (line in lines) {
            out += PosterElement.TextLine(line, x, y + ASCENT * size, size, bold, color)
            y += leading * size
        }
        return y
    }

    /**
     * [text] cut into lines no wider than [width]: between words (never at a no-break space, so
     * that "14 h" stays whole), inside a word only when it is wider than the line. Line breaks of
     * the text are kept, empty lines dropped. Beyond [maxLines], the last line ends with "…".
     */
    fun wrap(text: String, width: Float, size: Float, bold: Boolean, maxLines: Int, measure: PosterMeasure): List<String> {
        if (maxLines <= 0) return emptyList()
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure.width(candidate, size, bold) <= width) {
                    line = candidate
                    continue
                }
                if (line.isNotEmpty()) lines += line
                line = word
                // A word wider than the line is cut where it must be.
                while (measure.width(line, size, bold) > width && line.length > 1) {
                    var cut = line.length - 1
                    while (cut > 1 && measure.width(line.substring(0, cut), size, bold) > width) cut--
                    lines += line.substring(0, cut)
                    line = line.substring(cut)
                }
            }
            if (line.isNotEmpty()) lines += line
        }
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && measure.width(last.trimEnd() + ELLIPSIS, size, bold) > width) last = last.dropLast(1)
        kept[kept.lastIndex] = last.trimEnd(' ', ',', ';', ':', '.') + ELLIPSIS
        return kept
    }
}
