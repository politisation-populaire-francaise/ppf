package org.politisationpopulairefrancaise.web.comprendre

import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.coroutines.runBlocking
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The public journal of corrections (`/corrections`), read from the `ppf-correction` tags of the signed events only. */
class CorrectionsPageTest {
    private val editor = LocalEventSigner(IdentityKeys.generate())
    private val stranger = LocalEventSigner(IdentityKeys.generate())

    private fun content(
        signer: LocalEventSigner,
        slug: String,
        title: String,
        type: ArticleType,
        createdAt: Long,
        corrections: List<List<String>>,
    ): SignedEvent {
        val unsigned = Article.build(signer.publicKey, slug, title, "[EXEMPLE] Résumé.", emptyList(), type, ContentOrigin.HUMAN, 1_800_000_000, createdAt, "[EXEMPLE] Texte.\n")
        return runBlocking { signer.sign(unsigned.copy(tags = unsigned.tags + corrections)) }
    }

    private fun render(document: HTML.() -> Unit): String = createHTML().html { document() }

    @Test
    fun theJournalListsTheCorrectionsOfThePublishedVersionsWithoutScript() {
        val fiche = content(
            editor, "garde-a-vue", "[EXEMPLE] La garde à vue", ArticleType.FICHE_PRATIQUE, 1_900_000_000,
            listOf(
                listOf("ppf-correction", "2026-11-02", "[EXEMPLE] La durée maximale était fausse.", "correction"),
                listOf("ppf-correction", "2026-11-08", "[EXEMPLE] Ajout d’une source & d’un lien <officiel>.", "mise-a-jour"),
            ),
        )
        // An older version of the same address: its corrections are those of the newest version only.
        val older = content(editor, "garde-a-vue", "[EXEMPLE] Ancien titre", ArticleType.FICHE_PRATIQUE, 1_899_000_000, listOf(listOf("ppf-correction", "2026-10-01", "[EXEMPLE] Ancienne ligne.", "correction")))
        val decryptage = content(editor, "une-affirmation", "[EXEMPLE] Une affirmation", ArticleType.DECRYPTAGE, 1_900_000_100, listOf(listOf("ppf-correction", "2026-11-05", "[EXEMPLE] Verdict précisé.", "correction")))
        // A malformed tag (no valid date, empty summary) is not shown; nor is a content of another key.
        val malformed = content(editor, "autre", "[EXEMPLE] Autre", ArticleType.FICHE_PRATIQUE, 1_900_000_200, listOf(listOf("ppf-correction", "hier", "[EXEMPLE] x"), listOf("ppf-correction", "2026-11-03", " ")))
        val forged = content(stranger, "faux", "Faux contenu", ArticleType.FICHE_PRATIQUE, 1_900_000_300, listOf(listOf("ppf-correction", "2026-11-09", "Ligne inventée.", "correction")))
        val catalog = ComprendreCatalog.fromEvents(listOf(fiche, older, decryptage, malformed, forged)) { it == editor.publicKey.hex }

        assertEquals(listOf("2026-11-08", "2026-11-05", "2026-11-02"), catalog.corrections.map { it.date.toIso() }, "the most recent first")
        assertEquals("/comprendre/fiches/garde-a-vue", catalog.corrections.first().path)
        assertEquals("/comprendre/decryptages/une-affirmation", catalog.corrections[1].path)

        val html = render { correctionsDocument(catalog) }
        assertTrue(html.contains("<html lang=\"fr\">"))
        assertTrue(Regex("<script[^>]*>").findAll(html).none(), "no script at all")
        assertFalse(html.contains("style=\""), "no inline style")
        assertFalse(Regex(" on[a-z]+=\"").containsMatchIn(html), "no event handler")
        assertFalse(html.contains("—"), "no em dash")
        assertTrue(html.contains("<h1"), "a heading")
        assertTrue(html.contains("Journal des corrections"))
        assertTrue(html.contains("<time datetime=\"2026-11-08\">"))
        assertTrue(html.contains("<a href=\"/comprendre/fiches/garde-a-vue\">[EXEMPLE] La garde à vue</a>"))
        assertTrue(html.contains("[EXEMPLE] La durée maximale était fausse."))
        assertTrue(html.contains("[EXEMPLE] Ajout d’une source &amp; d’un lien &lt;officiel&gt;."), "the summary is escaped")
        assertTrue(html.contains("Mise à jour") && html.contains("Correction"))
        listOf("Ancienne ligne", "Ligne inventée", "hier").forEach { assertFalse(html.contains(it), it) }
        assertTrue(html.contains("<a href=\"/comprendre\" aria-current=\"page\">Accueil de Comprendre</a>"), "the layout of the pages of Comprendre")
    }

    @Test
    fun anEmptyJournalSaysSoAndTheHomeLinksToIt() {
        val html = render { correctionsDocument(ComprendreCatalog.EMPTY) }
        assertTrue(html.contains("Aucune correction publiée pour l’instant."))
        assertTrue(render { comprendreHomeDocument(ComprendreCatalog.EMPTY) }.contains("href=\"$CORRECTIONS_PATH\""))
    }
}
