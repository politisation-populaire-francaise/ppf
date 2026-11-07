package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class VoirLinksTest {

    private val keys = IdentityKeys.generate()

    private fun block(slug: String, type: ArticleType, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, "[EXEMPLE] Titre", null, emptyList(), type, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "Corps.", extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private val catalog = VoirCatalog.from(
        listOf(
            block("manifestants", ArticleType.INDICATEUR, listOf(VoirTags.UNIT, "personnes")),
            block("premiere-journee", ArticleType.FAIT, listOf(VoirTags.FACT, "mobilisation"), listOf(VoirTags.DATE, "2026-09-18"), listOf(VoirTags.FACT_STATUS, "etabli")),
            block("camille-exemple", ArticleType.PERSONNE),
            block("plf-2027", ArticleType.TEXTE, listOf(VoirTags.STAGE, "assemblee")),
            block("pays-a", ArticleType.MOUVEMENT, listOf(VoirTags.COUNTRY, "Pays A")),
            block("medias", ArticleType.DOSSIER, listOf(VoirTags.AXIS, "libertes")),
        ),
    )

    @Test
    fun eachKindHasItsOwnStablePath() {
        assertEquals("/voir/chiffres/manifestants", VoirLinks.pathOf(VoirItem.OfIndicator(catalog.indicators.single())))
        assertEquals("/voir/faits/premiere-journee", VoirLinks.pathOf(VoirItem.OfFact(catalog.facts.single())))
        assertEquals("/voir/personnes/camille-exemple", VoirLinks.pathOf(VoirItem.OfPerson(catalog.persons.single())))
        assertEquals("/voir/textes/plf-2027", VoirLinks.pathOf(VoirItem.OfText(catalog.texts.single())))
        assertEquals("/voir/monde/pays-a", VoirLinks.pathOf(VoirItem.OfMovement(catalog.movements.single())))
        assertEquals("/voir/dossiers/medias", VoirLinks.pathOf(VoirItem.OfDossier(catalog.dossiers.single())))
    }

    @Test
    fun theFullAddressUsesTheMainDomainOrTheGivenOne() {
        val item = VoirItem.OfText(catalog.texts.single())
        assertEquals("https://politisation-populaire-francaise.org/voir/textes/plf-2027", VoirLinks.urlOf(item))
        assertEquals("http://localhost:8080/voir/textes/plf-2027", VoirLinks.urlOf(item, "http://localhost:8080/"))
    }

    @Test
    fun identifiersAreEncodedAsOnePathSegment() {
        assertEquals("abc-1.2_x~", VoirLinks.encodeSegment("abc-1.2_x~"))
        assertEquals("d%C3%A9put%C3%A9", VoirLinks.encodeSegment("député"))
        assertEquals("a%2Fb%3Fc%23d%20e", VoirLinks.encodeSegment("a/b?c#d e"))
        assertEquals("%3Cscript%3E", VoirLinks.encodeSegment("<script>"))
        assertEquals("%2E%2E", VoirLinks.encodeSegment(".."))
        val odd = VoirItem.OfDossier(VoirCatalog.from(listOf(block("../admin", ArticleType.DOSSIER, listOf(VoirTags.AXIS, "pouvoir")))).dossiers.single())
        assertEquals("/voir/dossiers/..%2Fadmin", VoirLinks.pathOf(odd))
    }
}
