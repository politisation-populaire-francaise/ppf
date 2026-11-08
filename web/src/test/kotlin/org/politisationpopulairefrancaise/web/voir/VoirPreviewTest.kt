package org.politisationpopulairefrancaise.web.voir

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.VoirSchemas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The preview of a draft of « Voir » in the back-office (circuit éditorial, step 6): the page of the site, one heading level lower. */
class VoirPreviewTest {
    private val keys = IdentityKeys.generate()

    private fun common(slug: String, title: String) = listOf(
        "slug" to slug,
        "title" to title,
        "summary" to "[EXEMPLE] Ce que dit ce contenu en une phrase.",
        "origin" to "human",
        "published_at" to "2026-11-08",
        "updated_at" to "2026-11-08",
    )

    private fun build(schema: ContentSchema, entries: List<Pair<String, String>>, body: String = "[EXEMPLE] Contenu de démonstration.\n"): UnsignedEvent {
        val draft = ContentDraft(schema.type, entries, body)
        val problems = EditorialEngine.check(schema, draft, CheckContext.of(keys.publicKey.hex, mapOf("exemple-manifestation" to ArticleType.FAIT)))
        assertTrue(problems.isEmpty(), problems.toString())
        return EditorialEngine.build(schema, draft, keys.publicKey)
    }

    private fun fact(slug: String = "exemple-manifestation", title: String = "[EXEMPLE] Une journée de mobilisation") = build(
        VoirSchemas.FAIT,
        common(slug, title) + listOf(
            "fact" to "mobilisation",
            "date" to "2026-10-02",
            "place" to "Exempleville",
            "fact-status" to "etabli",
            "source" to "medias | Article du journal d’exemple | https://example.org/exemple/article | 2026-10-03",
        ),
    )

    private val party = build(
        VoirSchemas.PARTI,
        common("parti-exemple-z", "[EXEMPLE] Parti d’exemple Z") + listOf(
            "acronym" to "PEZ",
            "financing" to "association-de-financement | Agrément (exemple) | 2009-07-02 | texte-officiel | Journal officiel (exemple) | https://example.org/exemple/jo",
            "public-funding" to "2026 | 1 210 345,60 | Décret de répartition pour 2026 (exemple) | https://example.org/exemple/decret-2026",
            "public-funding" to "2025 | 1 105 120,00 | Décret de répartition pour 2025 (exemple) | https://example.org/exemple/decret-2025",
            "seats" to "assemblee | 12 | 2026-10-01 | parlement | Assemblée nationale, groupes (exemple) | https://example.org/exemple/an",
            "seats" to "senat | 4 | 2026-10-01 | parlement | Sénat, groupes (exemple) | https://example.org/exemple/senat",
            "source" to "texte-officiel | Statuts déposés en préfecture (exemple) | https://example.org/exemple/statuts | 2009-05-16",
        ),
    )

    private fun reply(target: String) = build(
        VoirSchemas.REPONSE,
        common("reponse-exemple", "[EXEMPLE] Droit de réponse de Camille Fictive").filter { it.first != "summary" } + listOf(
            "respondent" to "[EXEMPLE] Camille Fictive",
            "target" to target,
        ),
        body = "[EXEMPLE] Je conteste ce récit.\n",
    )

    private fun UnsignedEvent.published() = SignedEvent(idHex(), pubkey, createdAt, kind, tags, content, "0".repeat(128))

    private fun render(draft: UnsignedEvent, published: List<SignedEvent> = emptyList()): Pair<Boolean, String> {
        var shown = false
        val html = createHTML().div { shown = voirBlockPreview(draft, published) }
        return shown to html
    }

    @Test
    fun aFactIsShownAsItsPageOnTheSiteOneHeadingLevelLower() {
        val (shown, html) = render(fact())
        assertTrue(shown)
        assertTrue(html.contains("<div class=\"voir-preview\">"), html)
        assertFalse(html.contains("<h1"), "the back-office page keeps its own h1: $html")
        assertTrue(html.contains("<h2>[EXEMPLE] Une journée de mobilisation</h2>"), html)
        // The sections of the page move from h2 to h3, with their attributes (the stream of kotlinx.html accepts the renamed tags).
        assertTrue(html.contains("<h3 id=\"sources\">Sources</h3>"), html)
        assertTrue(html.contains("</h3>") && !html.contains("<h2 id="), html)
        assertTrue(html.contains("Article du journal d’exemple"), "the sources, as on the site")
        assertTrue(html.contains("Exempleville"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("style="))
    }

    @Test
    fun aPartyWithRepeatedLinesIsShownAsItsPage() {
        val (shown, html) = render(party)
        assertTrue(shown)
        assertTrue(html.contains("<h2>[EXEMPLE] Parti d’exemple Z</h2>"), html)
        assertTrue(html.contains("Parti politique · PEZ"), html)
        assertTrue(html.contains("Statuts déposés en préfecture (exemple)"), html)
        assertFalse(html.contains("<h1"))
    }

    @Test
    fun theDraftReplacesThePublishedVersionOfItsAddress() {
        val before = fact(title = "[EXEMPLE] Ancien titre").published()
        val (shown, html) = render(fact(title = "[EXEMPLE] Nouveau titre"), listOf(before))
        assertTrue(shown)
        assertTrue(html.contains("[EXEMPLE] Nouveau titre"))
        assertFalse(html.contains("[EXEMPLE] Ancien titre"), "only the draft is shown at its address")
    }

    @Test
    fun aReplyIsShownUnderTheContentItAnswersOrAloneWhenItIsNotPublished() {
        val target = fact().published()
        val (shown, html) = render(reply("exemple-manifestation"), listOf(target))
        assertTrue(shown)
        assertTrue(html.contains("<h2>[EXEMPLE] Une journée de mobilisation</h2>"), "the page of the content it answers: $html")
        assertTrue(html.contains("<h3 id=\"droit-de-reponse\">Droit de réponse</h3>"), html)
        assertTrue(html.contains("<h4>Droit de réponse de [EXEMPLE] Camille Fictive</h4>"), html)
        assertTrue(html.contains("[EXEMPLE] Je conteste ce récit."))

        val (alone, aloneHtml) = render(reply("exemple-manifestation"))
        assertTrue(alone)
        assertTrue(aloneHtml.contains("<h4>Droit de réponse de [EXEMPLE] Camille Fictive</h4>"), aloneHtml)
        assertFalse(aloneHtml.contains("Une journée de mobilisation"))
    }

    @Test
    fun aDraftTheSiteCannotReadShowsNothing() {
        // A fact without its kind, date, status or sources: the site would leave it out.
        val unreadable = Article.build(keys.publicKey, "exemple-illisible", "[EXEMPLE] Illisible", null, emptyList(), ArticleType.FAIT, ContentOrigin.HUMAN, 1_793_000_000, 1_793_000_000, "Texte.")
        val (shown, html) = render(unreadable)
        assertFalse(shown)
        assertEquals("<div></div>", html.trim())
    }
}
