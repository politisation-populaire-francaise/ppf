package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComprendreSchemasTest {
    private val author = IdentityKeys.generate().publicKey
    private val context = CheckContext.of(
        author.hex,
        mapOf(
            "alex-modele" to ArticleType.PERSONNE,
            "parti-exemple" to ArticleType.PARTI,
            "garde-a-vue" to ArticleType.FICHE_PRATIQUE,
            "le-referendum" to ArticleType.PARCOURS,
            "glossaire-49-3" to ArticleType.GLOSSAIRE,
        ),
    )

    private fun common(slug: String, type: String?) = listOfNotNull(
        "slug" to slug,
        "title" to "[EXEMPLE] Titre",
        "summary" to "Un résumé.",
        "topics" to "institutions",
        type?.let { "type" to it },
        "origin" to "ai-assisted",
        "published_at" to "2026-11-07",
        "updated_at" to "2026-11-07",
    )

    private val objections = "::: objections\n? Une objection\n> Une réponse sourcée.\n:::\n"
    private val sources = "## Sources\n\n- Constitution, article 49.\n"

    private fun check(schema: ContentSchema, entries: List<Pair<String, String>>, body: String) =
        EditorialEngine.check(schema, ContentDraft(schema.type, entries, body), context)

    private fun tags(schema: ContentSchema, entries: List<Pair<String, String>>, body: String) =
        EditorialEngine.build(schema, ContentDraft(schema.type, entries, body), author).tags.drop(7)

    @Test
    fun everyTypeOfComprendreHasItsSchema() {
        assertEquals(
            setOf(ArticleType.FICHE_PRATIQUE, ArticleType.PARCOURS, ArticleType.DECRYPTAGE, ArticleType.HISTOIRE, ArticleType.QUI_DECIDE, ArticleType.ARTICLE, ArticleType.GLOSSAIRE),
            EditorialSchemas.of(EditorialSpace.COMPRENDRE).map { it.type }.toSet(),
        )
        assertEquals(ComprendreSchemas.QUI_DECIDE, EditorialSchemas.of(ArticleType.QUI_DECIDE))
    }

    @Test
    fun aFactCheckNeedsItsVerdictItsClaimItsObjectionsAndItsSources() {
        val entries = common("49-3", "decryptage") + listOf("verdict" to "trompeur", "claim" to "Une affirmation.", "claim_context" to "Réseaux sociaux")
        assertEquals(emptyList(), check(ComprendreSchemas.DECRYPTAGE, entries, objections + sources))
        assertEquals(
            listOf(listOf("ppf-verdict", "trompeur"), listOf("ppf-claim", "Une affirmation."), listOf("ppf-claim-context", "Réseaux sociaux")),
            tags(ComprendreSchemas.DECRYPTAGE, entries, objections + sources),
        )
        val noContext = entries.map { if (it.first == "claim_context") it.first to "" else it }
        assertEquals(2, tags(ComprendreSchemas.DECRYPTAGE, noContext, objections + sources).size, "an empty context writes no tag")
        val wrong = entries.map { if (it.first == "verdict") it.first to "mensonge" else it }
        assertEquals(listOf(Codes.VALUE_CHOICE), check(ComprendreSchemas.DECRYPTAGE, wrong, objections + sources).map { it.code })
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_FEW), check(ComprendreSchemas.DECRYPTAGE, entries, sources).map { it.code })
        assertEquals(listOf(Codes.BODY_SECTION_MISSING), check(ComprendreSchemas.DECRYPTAGE, entries, objections).map { it.code })
        assertEquals(listOf(Codes.BODY_INVALID), check(ComprendreSchemas.DECRYPTAGE, entries, objections + "::: objections\n? Sans réponse\n:::\n" + sources).map { it.code })
        assertEquals(listOf(Codes.FIELD_MISSING), check(ComprendreSchemas.DECRYPTAGE, entries.filter { it.first != "claim" }, objections + sources).map { it.code })
    }

    @Test
    fun aStoryHasItsPeriodItsCountryAndItsLists() {
        val entries = common("front-populaire", "histoire") + listOf(
            "period" to "1936 | 1938",
            "country" to "France",
            "claims" to "hausse des salaires ; congés payés",
            "results" to "congés payés (loi du 20 juin 1936)",
        )
        assertEquals(emptyList(), check(ComprendreSchemas.HISTOIRE, entries, objections + sources))
        assertEquals(
            listOf(
                listOf("ppf-period", "1936", "1938"),
                listOf("ppf-country", "France"),
                listOf("ppf-claim", "hausse des salaires"),
                listOf("ppf-claim", "congés payés"),
                listOf("ppf-result", "congés payés (loi du 20 juin 1936)"),
            ),
            tags(ComprendreSchemas.HISTOIRE, entries, objections + sources),
        )
        val open = entries.map { if (it.first == "period") it.first to "1936-06 |" else it }
        assertEquals(emptyList(), check(ComprendreSchemas.HISTOIRE, open, objections + sources))
        assertEquals(listOf("ppf-period", "1936-06"), tags(ComprendreSchemas.HISTOIRE, open, objections + sources).first())
        fun period(value: String) = check(ComprendreSchemas.HISTOIRE, entries.map { if (it.first == "period") it.first to value else it }, objections + sources).map { it.code }
        assertEquals(listOf(Codes.VALUE_POSITION_REQUIRED), period("| 1938"))
        assertEquals(listOf(Codes.VALUE_DATE_OR_YEAR), period("printemps 1936"))
        assertEquals(listOf(Codes.VALUE_POSITIONS), period("1936 | 1937 | 1938"))
    }

    @Test
    fun aQuiDecideSheetLinksPersonsOfVoir() {
        val body = "::: decideurs\n- commune | maire | La commune décide.\n:::\n\nTexte.\n\n::: et-maintenant\n- Écrire au maire.\n:::\n\n$sources"
        val entries = common("ecole", "qui-decide") + ("persons" to "alex-modele")
        assertEquals(emptyList(), check(ComprendreSchemas.QUI_DECIDE, entries, body))
        assertEquals(listOf(listOf("a", "30023:${author.hex}:alex-modele", "", "personne")), tags(ComprendreSchemas.QUI_DECIDE, entries, body))
        assertEquals(emptyList(), check(ComprendreSchemas.QUI_DECIDE, common("ecole", "qui-decide") + ("persons" to ""), body))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), check(ComprendreSchemas.QUI_DECIDE, common("ecole", "qui-decide") + ("persons" to "parti-exemple"), body).map { it.code })
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_FEW), check(ComprendreSchemas.QUI_DECIDE, entries, body.replace("::: et-maintenant", "::: autre")).map { it.code })
        val unknownLevel = check(ComprendreSchemas.QUI_DECIDE, entries, body.replace("- commune |", "- quartier |"))
        assertEquals(listOf("comprendre.rule.qui_decide_readable"), unknownLevel.map { it.code })
    }

    @Test
    fun aCourseIsReadAsTheApplicationReadsIt() {
        val step = "## Une étape\n\nTexte.\n\n::: quiz\n? Une question ?\n- [x] Oui\n- [ ] Non\n> Parce que.\n:::\n\n"
        val body = "Introduction.\n\n$step::: et-maintenant\n- Agir.\n:::\n\n$sources"
        val entries = common("le-referendum", "parcours") + ("duration" to "5")
        assertEquals(emptyList(), check(ComprendreSchemas.PARCOURS, entries, body))
        val event = EditorialEngine.build(ComprendreSchemas.PARCOURS, ContentDraft(ArticleType.PARCOURS, entries, body), author)
        assertTrue(listOf("ppf-duration", "5") in event.tags)
        assertEquals(listOf(Codes.FIELD_MISSING), check(ComprendreSchemas.PARCOURS, entries.filter { it.first != "duration" }, body).map { it.code })
        assertEquals(listOf(Codes.VALUE_MINUTES), check(ComprendreSchemas.PARCOURS, common("x", "parcours") + ("duration" to "300"), body).map { it.code })
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_FEW), check(ComprendreSchemas.PARCOURS, entries, body.replace("::: quiz", "::: autre")).map { it.code })
    }

    @Test
    fun theGlossaryKeepsItsTagsAndItsLimits() {
        val entries = listOf(
            "slug" to "glossaire-motion",
            "title" to "[EXEMPLE] Motion de censure",
            "summary" to "Vote par lequel les députés peuvent renverser le Gouvernement.",
            "variants" to "motion de censure, article 49\\, alinéa 2",
            "auto" to "non",
            "topics" to "institutions",
            "see_also" to "glossaire-49-3",
            "related" to "parcours:le-referendum, fiche:garde-a-vue",
            "origin" to "ai-assisted",
            "published_at" to "2026-11-07",
            "updated_at" to "2026-11-07",
        )
        val body = "Explication.\n\n$sources"
        assertEquals(emptyList(), check(ComprendreSchemas.GLOSSAIRE, entries, body))
        val source = GlossarySource(
            file = "motion.md", slug = "glossaire-motion", title = "[EXEMPLE] Motion de censure", summary = null,
            variants = GlossaryFormat.list("motion de censure, article 49\\, alinéa 2"), seeAlso = listOf("glossaire-49-3"),
            related = listOf("parcours:le-referendum", "fiche:garde-a-vue"), auto = "non", body = body,
        )
        assertEquals(GlossaryFormat.tags(source, author.hex), tags(ComprendreSchemas.GLOSSAIRE, entries, body))
        val defaultAuto = entries.map { if (it.first == "auto") it.first to "oui" else it }
        assertTrue(tags(ComprendreSchemas.GLOSSAIRE, defaultAuto, body).none { it[0] == GlossaryEntry.TAG_AUTO })
        val long = entries.map { if (it.first == "summary") it.first to "x".repeat(201) else it }
        assertEquals(listOf("comprendre.rule.glossary_definition"), check(ComprendreSchemas.GLOSSAIRE, long, body).map { it.code })
        val noTerm = entries.map { if (it.first == "title") it.first to "[EXEMPLE]" else it }
        assertEquals(listOf("comprendre.rule.glossary_title"), check(ComprendreSchemas.GLOSSAIRE, noTerm, body).map { it.code })
        assertEquals(listOf(Codes.FIELD_MISSING), check(ComprendreSchemas.GLOSSAIRE, entries.filter { it.first != "variants" }, body).map { it.code })
        assertEquals(listOf(Codes.LINK_UNKNOWN), check(ComprendreSchemas.GLOSSAIRE, entries.map { if (it.first == "see_also") it.first to "glossaire-inconnu" else it }, body).map { it.code })
    }

    @Test
    fun aGuideAndTheMethodNeedTheirSources() {
        val guide = common("garde-a-vue", "fiche-pratique")
        assertEquals(emptyList(), check(ComprendreSchemas.FICHE_PRATIQUE, guide, "Texte.\n\n$sources"))
        assertEquals(listOf(Codes.BODY_SECTION_MISSING), check(ComprendreSchemas.FICHE_PRATIQUE, guide, "Texte.\n").map { it.code })
        assertEquals(listOf(Codes.TYPE_MISMATCH), check(ComprendreSchemas.FICHE_PRATIQUE, common("garde-a-vue", "dossier"), "Texte.\n\n$sources").map { it.code })
        assertEquals(emptyList(), check(ComprendreSchemas.METHODE, common("notre-methode", "article"), "Texte.\n\n$sources"))
    }

    @Test
    fun aPublishedContentReadsBackAsItsDraft() {
        val entries = common("front-populaire", "histoire") + listOf(
            "period" to "1936 | 1938",
            "country" to "France",
            "claims" to "hausse des salaires ; congés payés",
        )
        val draft = ContentDraft(ArticleType.HISTOIRE, entries, objections + sources)
        val unsigned = EditorialEngine.build(ComprendreSchemas.HISTOIRE, draft, author)
        val signed = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        val back = EditorialEngine.draftOf(ComprendreSchemas.HISTOIRE, signed, context)
        assertEquals(unsigned, EditorialEngine.build(ComprendreSchemas.HISTOIRE, back, author))
        assertEquals(listOf("period" to "1936 | 1938", "country" to "France", "claims" to "hausse des salaires ; congés payés"), back.entries.drop(8))
    }
}
