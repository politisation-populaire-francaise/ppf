package org.politisationpopulairefrancaise.ui.content

import org.politisationpopulairefrancaise.ui.theme.CalloutType
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleMarkdownTest {

    @Test
    fun parsesHeadingsParagraphsAndBullets() {
        val blocks = SimpleMarkdown.parse(
            """
            **[EXEMPLE] Brouillon.**

            ## Durée

            Vingt-quatre heures,
            renouvelables une fois.

            - premier point
            - deuxième point
              qui continue
            * troisième point

            ### Sources
            - Code pénal
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("**[EXEMPLE] Brouillon.**"),
                MarkdownBlock.Heading(2, "Durée"),
                MarkdownBlock.Paragraph("Vingt-quatre heures, renouvelables une fois."),
                MarkdownBlock.Bullets(listOf("premier point", "deuxième point qui continue", "troisième point")),
                MarkdownBlock.Heading(3, "Sources"),
                MarkdownBlock.Bullets(listOf("Code pénal")),
            ),
            blocks,
        )
    }

    @Test
    fun parsesHighlightedBoxes() {
        val blocks = SimpleMarkdown.parse(
            """
            ::: en-bref
            - un
            - deux
            :::

            Texte.

            ::: illegal
            Interdit **absolument**.
            :::

            ::: inconnu
            Juste du texte.
            :::
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Callout(CalloutType.EN_BREF, listOf(MarkdownBlock.Bullets(listOf("un", "deux")))),
                MarkdownBlock.Paragraph("Texte."),
                MarkdownBlock.Callout(CalloutType.ILLEGAL, listOf(MarkdownBlock.Paragraph("Interdit **absolument**."))),
                MarkdownBlock.Paragraph("Juste du texte."),
            ),
            blocks,
        )
    }

    @Test
    fun inlineBoldAndLinks() {
        val text = SimpleMarkdown.inline("Un **mot** fort et un [lien](https://example.org) ici, **deux** fois.")
        assertEquals("Un mot fort et un lien (https://example.org) ici, deux fois.", text.text)
        assertEquals(2, text.spanStyles.size)
        assertEquals(3, text.spanStyles[0].start)
        assertEquals(6, text.spanStyles[0].end)
    }
}
