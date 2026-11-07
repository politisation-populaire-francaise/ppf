package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.div
import kotlinx.html.html
import kotlinx.html.body
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.FrenchNumbers
import org.politisationpopulairefrancaise.core.voir.Indicator
import org.politisationpopulairefrancaise.core.voir.SourceKind
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirItem
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The chart drawn on the server, and the page of an indicator around it. */
class VoirChartTest {
    private val keys = IdentityKeys.generate()

    /** French figures use a narrow no-break space between groups. */
    private fun n(value: Int): String = FrenchNumbers.format(value.toDouble())

    private fun indicator(title: String, vararg measures: String): Indicator {
        val tags = listOf(listOf(VoirTags.UNIT, "personnes")) + measures.map { listOf(VoirTags.MEASURE) + it.split('|') }
        val unsigned = Article.build(keys.publicKey, "manifestants", title, "Ce que l’on compte.", emptyList(), ArticleType.INDICATEUR, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "## Méthode\n\nComptage.", extraTags = tags)
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Indicator.from(Article.from(event)!!)!!
    }

    private val sample = indicator(
        "[EXEMPLE] Manifestants <b>& co</b>",
        "a1|2026-09-18|312000|autorites|Ministère|https://example.org/a1|moyenne|Journée du 18 septembre|France",
        "o1|2026-09-18|1050000|organisateurs|Intersyndicale|javascript:alert(1)|moyenne|Journée du 18 septembre|France",
        "a2|2026-10-02|268000|autorites|Ministère|https://example.org/a2|moyenne|Journée du 2 octobre|France",
        "o2|2026-10-02|900000|organisateurs|Intersyndicale||moyenne|Journée du 2 octobre|France",
        "i2|2026-10-02|390000|comptage-independant|Collectif|https://example.org/i2|elevee|Journée du 2 octobre|France",
    )

    @Test
    fun eachKindOfSourceKeepsTheColourOfTheApp() {
        assertEquals(0, VoirChart.slotOf(SourceKind.AUTHORITIES))
        assertEquals(1, VoirChart.slotOf(SourceKind.ORGANISERS))
        assertEquals(2, VoirChart.slotOf(SourceKind.INDEPENDENT))
        assertEquals(7, VoirChart.slotOf(SourceKind.JUSTICE))
        // Two kinds sharing a slot: the second takes the next free one.
        assertEquals(mapOf(SourceKind.AUTHORITIES to 0, SourceKind.PARLIAMENT to 1, SourceKind.ORGANISERS to 2), VoirChart.slots(listOf(SourceKind.AUTHORITIES, SourceKind.PARLIAMENT, SourceKind.ORGANISERS)))
    }

    @Test
    fun theScaleUsesRoundSteps() {
        assertEquals(1_500_000.0 to 500_000.0, VoirChart.scale(1_050_000.0))
        assertEquals(1_000_000.0 to 250_000.0, VoirChart.scale(900_000.0))
        assertEquals(10.0 to 2.5, VoirChart.scale(9.0))
        assertEquals(1.0 to 0.25, VoirChart.scale(0.0))
    }

    @Test
    fun endLabelsNeverOverlap() {
        val placed = VoirChart.spread(listOf(100.0, 105.0, 250.0, 255.0), 26.0, 256.0)
        placed.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 30.0, placed.toString()) }
        assertTrue(placed.all { it in 26.0..256.0 }, placed.toString())
    }

    @Test
    fun theSvgIsAccessibleEscapedAndWithoutInlineStyle() {
        val svg = VoirChart.svg(sample)!!
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"chart\""))
        assertTrue(svg.contains("role=\"img\" aria-labelledby=\"graphique-titre graphique-description\""))
        assertTrue(svg.contains("<title id=\"graphique-titre\">Évolution de « [EXEMPLE] Manifestants &lt;b&gt;&amp; co&lt;/b&gt; »</title>"))
        assertTrue(svg.contains("Autorités : de ${n(312_000)} le 18 septembre 2026 à ${n(268_000)} le 2 octobre 2026"))
        assertTrue(svg.contains("Comptage indépendant : ${n(390_000)} le 2 octobre 2026"))
        // One line per kind with two values or more, a dot for every value, a grid, an end label per kind.
        assertEquals(2, Regex("<polyline").findAll(svg).count())
        assertEquals(5, Regex("<circle").findAll(svg).count())
        assertEquals(3, Regex("class=\"chart-label ").findAll(svg).count())
        assertTrue(svg.contains("<g class=\"chart-grid\">"))
        assertFalse(svg.contains("style"))
        assertFalse(svg.contains("<script"))
        assertNull(VoirChart.svg(indicator("[EXEMPLE] Vide")))
    }

    @Test
    fun theIndicatorPageGivesTheTableNextToTheChartAndOnlyWebLinks() {
        val catalog = VoirCatalog.from(listOf(sample.article))
        val html = createHTML().html { voirItemDocument(VoirItem.OfIndicator(catalog.indicators.single()), catalog) }
        val evolution = html.substringAfter("id=\"evolution\"").substringBefore("</section>")
        assertTrue(evolution.indexOf("<svg") in 0 until evolution.indexOf("<table class=\"chart-table\""))
        assertTrue(evolution.contains("<td class=\"num\">non publié</td>"), "the independent count has no value on 18 September")
        assertTrue(html.contains("href=\"https://example.org/a1\" rel=\"noopener noreferrer nofollow external\""))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("&lt;b&gt;&amp; co&lt;/b&gt;"))
        assertTrue(html.contains("Et maintenant ?") && html.contains("href=\"/agenda\""))
        assertTrue(html.contains("Rédigé par l’équipe"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun markdownIsEscapedAndLinksOnlyLeadToTheWeb() {
        val html = createHTML().html {
            body {
                div {
                    markdown(
                        """
                        ::: en-bref
                        - Un point **important**.
                        :::

                        ## Titre <i>
                        Texte avec un [lien](https://example.org/x) et un [piège](javascript:alert(1)), *en italique*.

                        > Citation
                        1. Premier
                        2. Second
                        <script>alert(1)</script>
                        """.trimIndent(),
                    )
                }
            }
        }
        assertTrue(html.contains("<aside class=\"md-box\" aria-label=\"En bref\">"))
        assertTrue(html.contains("<li>Un point <strong>important</strong>.</li>"))
        assertTrue(html.contains("<h2>Titre &lt;i&gt;</h2>"))
        assertTrue(html.contains("<a href=\"https://example.org/x\" rel=\"noopener noreferrer nofollow external\">lien<span class=\"external-mark\"> (site extérieur)</span></a>"))
        assertTrue(html.contains(" et un piège), <em>en italique</em>."))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("javascript:"))
    }
}
