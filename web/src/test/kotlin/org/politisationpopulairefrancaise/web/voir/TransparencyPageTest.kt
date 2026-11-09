package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.voir.TransparencyPages
import org.politisationpopulairefrancaise.core.voir.VoirCatalog
import org.politisationpopulairefrancaise.core.voir.VoirTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Transparence de PPF » on the site (PP-01): readable without script, every rubric, no repeated identifier, « non mesuré » rather than zero. */
class TransparencyPageTest {
    private val keys = IdentityKeys.generate()

    private fun page(slug: String, title: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), ArticleType.TRANSPARENCE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Texte de la page.", extraTags = tags.toList())
        return Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
    }

    private val source = listOf(VoirTags.SOURCE, "texte-officiel", "Statuts (exemple)", "https://example.org/exemple/statuts", "2027-01-15")

    private val pages = VoirCatalog.from(
        listOf(
            page("bureau", "[EXEMPLE] Le bureau", listOf(VoirTags.RUBRIC, "instance"), listOf(VoirTags.INSTANCE, "bureau"), listOf(VoirTags.SEAT, "Présidence", "", "2027-03-01", ""), source),
            page("ag", "[EXEMPLE] L'assemblée générale", listOf(VoirTags.RUBRIC, "instance"), listOf(VoirTags.INSTANCE, "assemblee-generale"), source),
            page(
                "comptes-2026", "[EXEMPLE] Comptes 2026", listOf(VoirTags.RUBRIC, "comptes"), listOf(VoirTags.YEAR, "2026"),
                listOf(VoirTags.ACCOUNT_LINE, "produit", "Dons", "300"), listOf(VoirTags.ACCOUNT_LINE, "charge", "Serveur", "450,50"),
            ),
            page("rapport-2026", "[EXEMPLE] Rapport 2026", listOf(VoirTags.RUBRIC, "rapport"), listOf(VoirTags.PERIOD, "2026-11-01", "2026-12-31"), listOf(VoirTags.FIGURE, "signalements-recus", "120")),
        ),
    ).transparency

    private fun render(pages: TransparencyPages): String = createHTML(prettyPrint = false).html { transparencyDocument(pages) }

    @Test
    fun everyRubricIsThereWithItsFiguresAndNoRepeatedIdentifier() {
        val html = render(pages)
        assertFalse(html.contains("<script"), "readable without script")
        listOf("Les instances", "Les décisions importantes", "Les comptes", "Rapports de modération et de réclamations").forEach { assertTrue(html.contains(it), it) }
        assertTrue(html.contains("Rien n’est encore publié dans cette rubrique."), "the decisions have nothing yet")
        assertTrue(html.contains("Contenus de démonstration [EXEMPLE]"))
        assertTrue(html.contains("<dd>${euros(-15_050)}</dd>"), "the result is computed, with its sign")
        assertTrue(html.contains("<dt>Transmissions aux autorités</dt><dd>non mesuré</dd>"))
        assertTrue(html.contains("<dt>Signalements reçus</dt><dd>120</dd>"))
        // Two pages with sources: the identifiers of the page stay unique.
        val ids = Regex("""\bid="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(ids.distinct(), ids, "no repeated identifier")
        assertTrue(html.contains("href=\"/transparence\""), "linked from the footer")
        assertFalse(html.contains("—"))
    }

    @Test
    fun aDeficitKeepsItsSign() {
        assertEquals("-150,50${org.politisationpopulairefrancaise.core.voir.FrenchNumbers.GROUP_SEPARATOR}€", euros(-15_050))
        assertEquals("300${org.politisationpopulairefrancaise.core.voir.FrenchNumbers.GROUP_SEPARATOR}€", euros(30_000))
    }
}
