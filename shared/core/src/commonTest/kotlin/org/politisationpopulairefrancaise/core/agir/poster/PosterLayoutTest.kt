package org.politisationpopulairefrancaise.core.agir.poster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The layout of a poster (AG-61), independent of any screen: everything stays on the page, the code
 * keeps its size, a long title gets smaller then ends with "…", words and hours are never cut.
 * The widths come from a regular font of the test (half the size per character, a little more in bold).
 */
class PosterLayoutTest {
    private val measure = PosterMeasure { text, size, bold -> text.length * size * (if (bold) 0.56f else 0.5f) }

    private fun text(title: String = "[EXEMPLE] Rassemblement pour les services publics", body: String? = "[EXEMPLE] Un texte court.", printer: String? = null) = PosterText(
        marker = "[EXEMPLE]",
        kind = "RASSEMBLEMENT",
        title = title,
        facts = listOf(
            PosterFact("Quand", "Samedi 14 novembre 2026, 14 h à 17 h", emphasis = true),
            PosterFact("Où", "Place de la Mairie, Villefictive"),
            PosterFact("Organisé par", "L’équipe éditoriale de PPF"),
        ),
        body = body,
        scanTitle = "Scannez pour ajouter à votre agenda, même sans réseau",
        scanHelp = "Avec l’application PPF : « Carte et agenda », puis « Scanner une affiche ».",
        footer = "Affiche faite avec l’application Politisation Populaire Française",
        printer = printer,
    )

    @Test
    fun everythingStaysOnThePageAndTheCodeKeepsItsSize() {
        for (format in PosterFormat.entries) {
            val layout = PosterLayouts.layout(text(printer = "[EXEMPLE] Imprimé par nos soins"), format, measure)
            val code = layout.code
            val margin = PosterLayouts.MARGIN * format.scale
            assertEquals(PosterLayouts.CODE_SIDE * format.scale, code.side)
            assertTrue(code.x >= margin && code.y >= margin && code.x + code.side <= format.widthPt - margin + 0.01f && code.y + code.side <= format.heightPt - margin)
            // The largest code accepted still has modules of at least 0.7 mm.
            val millimetres = code.side / 72f * 25.4f
            assertTrue(millimetres / (PosterQr.MAX_MODULES + 8) >= 0.7f, "$millimetres mm")
            for (line in layout.lines) {
                assertTrue(line.x >= margin - 0.01f, line.text)
                assertTrue(line.x + measure.width(line.text, line.size, line.bold) <= format.widthPt - margin + 0.01f, "inside the page: ${line.text}")
                assertTrue(line.baseline > 0f && line.baseline <= format.heightPt - margin + 0.01f, line.text)
                // Nothing written over the code.
                val overCode = line.x < code.x + code.side && line.baseline > code.y && line.baseline - line.size < code.y + code.side
                assertTrue(!overCode, "over the code: ${line.text}")
            }
            val words = layout.lines.joinToString(" ") { it.text }
            assertTrue("Scannez pour ajouter à votre agenda, même sans réseau" in words, words)
            assertTrue("[EXEMPLE]" in words)
            assertTrue("Imprimé par nos soins" in words)
            assertTrue(layout.elements.count { it is PosterElement.Rule } >= 2)
        }
    }

    @Test
    fun anA3IsAnEnlargedA4() {
        val a4 = PosterLayouts.layout(text(), PosterFormat.A4, measure)
        val a3 = PosterLayouts.layout(text(), PosterFormat.A3, measure)
        assertEquals(a4.lines.map { it.text }, a3.lines.map { it.text })
        // The title is the largest black text.
        val title4 = a4.lines.filter { it.bold && it.color == PosterColor.INK }.maxOf { it.size }
        val title3 = a3.lines.filter { it.bold && it.color == PosterColor.INK }.maxOf { it.size }
        assertEquals(title4 * PosterFormat.A3.scale, title3, 0.01f)
        assertEquals(a4.code.side * PosterFormat.A3.scale, a3.code.side, 0.01f)
    }

    @Test
    fun aShortTitleIsLargeAndALongOneGetsSmallerThenEndsWithAnEllipsis() {
        val short = PosterLayouts.layout(text(title = "Grève"), PosterFormat.A4, measure)
        assertEquals(PosterLayouts.TITLE_SIZES.first(), short.lines.first { it.text == "Grève" }.size)

        val long = PosterLayouts.layout(text(title = (1..40).joinToString(" ") { "mobilisation" }), PosterFormat.A4, measure)
        val title = long.lines.filter { it.bold && it.color == PosterColor.INK && it.size >= PosterLayouts.TITLE_SIZES.last() }
        assertEquals(PosterLayouts.TITLE_MAX_LINES + 1, title.size)
        assertTrue(title.all { it.size == PosterLayouts.TITLE_SIZES.last() })
        assertTrue(title.last().text.endsWith("…"))
        // The facts still show under it.
        assertTrue(long.lines.any { it.text == "Quand" })
    }

    @Test
    fun wrappingKeepsWordsAndHoursWhole() {
        // Ten characters a line.
        val lines = PosterLayouts.wrap("Samedi 14 h à 17 h", width = 50f, size = 10f, bold = false, maxLines = 10, measure = measure)
        assertEquals(listOf("Samedi", "14 h à", "17 h"), lines)
        // A word wider than the line is cut where it must be.
        assertEquals(listOf("abcde", "fghij", "klmno", "p"), PosterLayouts.wrap("abcdefghijklmnop", 25f, 10f, false, 10, measure))
        // Beyond the lines allowed, the last one ends with "…"; line breaks of the text are kept, empty lines dropped.
        assertEquals(listOf("un deux", "trois…"), PosterLayouts.wrap("un deux trois quatre cinq six", 40f, 10f, false, 2, measure))
        assertEquals(listOf("un", "deux"), PosterLayouts.wrap("un\n\ndeux", 40f, 10f, false, 5, measure))
        assertEquals(emptyList(), PosterLayouts.wrap("un", 40f, 10f, false, 0, measure))
    }
}
