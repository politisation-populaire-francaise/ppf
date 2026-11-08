package org.politisationpopulairefrancaise.core.editorial

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.signing.sign
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** « Citer ce contenu » (S-144): the same reference for the site and the applications. */
class CitationTest {
    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)
    private val url = "https://politisation-populaire-francaise.org/voir/faits/exemple"
    private val today = CivilDate(2026, 11, 8)

    // 10 December 2025 and 6 November 2026, at noon UTC.
    private suspend fun article(origin: ContentOrigin = ContentOrigin.HUMAN, publishedAt: Long = 1_765_368_000, updatedAt: Long = 1_793_966_400): Article {
        val event = signer.sign(Article.build(keys.publicKey, "exemple", " [EXEMPLE] Un fait \"cité\" ", null, emptyList(), ArticleType.FAIT, origin, publishedAt, updatedAt, "Texte."))
        return Article.from(event)!!
    }

    @Test
    fun theReferenceGivesTheTitleTheDatesTheVersionTheAddressAndTheFingerprint() = runTest {
        val article = article()
        val citation = Citation.of(article, url, today)
        assertEquals(
            "Politisation Populaire Française, « [EXEMPLE] Un fait \"cité\" », publié le 10 décembre 2025, mis à jour le 6 novembre 2026, " +
                "version ${article.event.id.take(12)}, $url, empreinte SHA-256 ${article.event.id}, licence CC BY 4.0, consulté le 8 novembre 2026.",
            citation.text,
        )
        assertFalse(citation.text.contains("—"))
    }

    @Test
    fun aContentUpdatedTheDayOfItsPublicationGivesOneDateAndAnAiOriginIsSaid() = runTest {
        val article = article(origin = ContentOrigin.AI_ASSISTED, publishedAt = 1_793_966_400, updatedAt = 1_793_970_000)
        val text = Citation.of(article, url, today).text
        assertTrue(text.contains("« [EXEMPLE] Un fait \"cité\" » (rédigé avec l’aide d’une IA), publié le 6 novembre 2026, version "), text)
        assertFalse(text.contains("mis à jour"), text)
    }

    @Test
    fun theCslJsonIsTheSameReferenceForBibliographySoftware() = runTest {
        val article = article()
        val csl = Json.parseToJsonElement(Citation.of(article, url, today).cslJson).jsonObject
        assertEquals(article.address.toString(), csl["id"]!!.jsonPrimitive.content)
        assertEquals("webpage", csl["type"]!!.jsonPrimitive.content)
        assertEquals("[EXEMPLE] Un fait \"cité\"", csl["title"]!!.jsonPrimitive.content)
        assertEquals(Citation.PUBLISHER, csl["author"]!!.jsonArray.single().jsonObject["literal"]!!.jsonPrimitive.content)
        assertEquals("[[2025,12,10]]", csl["issued"]!!.jsonObject["date-parts"].toString())
        assertEquals("[[2026,11,8]]", csl["accessed"]!!.jsonObject["date-parts"].toString())
        assertEquals(url, csl["URL"]!!.jsonPrimitive.content)
        assertEquals(article.event.id.take(12), csl["version"]!!.jsonPrimitive.content)
        assertTrue(csl["note"]!!.jsonPrimitive.content.contains(article.event.id))
        assertTrue(csl["note"]!!.jsonPrimitive.content.contains("licence CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/deed.fr)"))
    }

    @Test
    fun aTextWhoseRightsStayWithItsAuthorCarriesNoLicenceOfPpf() = runTest {
        val event = signer.sign(Article.build(keys.publicKey, "reponse", "[EXEMPLE] Droit de réponse", null, emptyList(), ArticleType.REPONSE, ContentOrigin.HUMAN, 1_765_368_000, 1_765_368_000, "Texte."))
        val citation = Citation.of(Article.from(event)!!, url, today)
        assertFalse(citation.text.contains("licence"), citation.text)
        assertFalse(citation.cslJson.contains("licence"))
        assertEquals(null, ContentLicenses.of(ArticleType.REPONSE))
        assertEquals("CC-BY-4.0", ContentLicenses.of(ArticleType.FAIT)?.spdx)
        assertEquals("etalab-2.0", ContentLicenses.DATA.spdx)
    }
}
