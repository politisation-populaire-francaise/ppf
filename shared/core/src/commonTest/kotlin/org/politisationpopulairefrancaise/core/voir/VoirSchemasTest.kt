package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The schemas of Voir: a valid [EXEMPLE] block of each type passes, and each rule refuses its broken example. */
class VoirSchemasTest {
    private val author = IdentityKeys.generate().publicKey
    private val context = CheckContext.of(
        author.hex,
        mapOf(
            "alex-modele" to ArticleType.PERSONNE,
            "parti-exemple-a" to ArticleType.PARTI,
            "pjl-exemple" to ArticleType.TEXTE,
            "manifestants-journees" to ArticleType.INDICATEUR,
            "affaire-exemple" to ArticleType.FAIT,
            "droits-en-manifestation" to ArticleType.FICHE_PRATIQUE,
        ),
    )

    private fun common(slug: String, type: String, summary: Boolean = true) = listOfNotNull(
        "slug" to slug,
        "title" to "[EXEMPLE] Titre",
        if (summary) "summary" to "Un résumé." else null,
        "type" to type,
        "origin" to "human",
        "published_at" to "2026-11-07",
        "updated_at" to "2026-11-08",
    )

    private val body = "[EXEMPLE] Texte de la brique.\n"
    private val source = "source" to "justice | Jugement du tribunal (exemple) | https://example.org/exemple/jugement | 2025-12-10"

    private fun check(schema: ContentSchema, entries: List<Pair<String, String>>) =
        EditorialEngine.check(schema, ContentDraft(schema.type, entries, body), context)

    private fun codes(schema: ContentSchema, entries: List<Pair<String, String>>) = check(schema, entries).map { it.code }

    /** The specific tags (the 7 common ones of Article.build dropped; no topic in these examples). */
    private fun tags(schema: ContentSchema, entries: List<Pair<String, String>>) =
        EditorialEngine.build(schema, ContentDraft(schema.type, entries, body), author).tags.filter { it[0] !in COMMON_TAGS }

    private fun List<Pair<String, String>>.with(key: String, value: String) = map { if (it.first == key) key to value else it }

    private fun address(slug: String) = "30023:${author.hex}:$slug"

    private val fact = common("affaire-test", "fait") + listOf(
        "fact" to "affaire",
        "date" to "2026-06-03",
        "place" to "Exempleville",
        "fact-status" to "enquete",
        "procedure" to "enquete",
        "person" to "alex-modele",
        source,
        "archive" to "771f0116299a5dfae07b63ae8aa1a93418e262ddb193307511e291bbf7fbd405 | opentimestamps | https://example.org/exemple/archive | 2026-10-09",
        "next" to "Vos droits | fiche:droits-en-manifestation",
        "next" to "Interpeller vos élus | espace:decider",
    )

    private val indicator = common("blesses-test", "indicateur") + listOf(
        "axis" to "mouvement",
        "unit" to "personnes",
        "rank" to "3",
        "breakdown" to "dont mineurs",
        "accepted-source" to "autorites",
        "accepted-source" to "signalements",
        "measure" to "b1-aut | 2026-10-20 | 1 200 | autorites | Ministère (exemple) | https://example.org/exemple/m | moyenne | 20 octobre | France | 4",
        "measure" to "b1-sig | 2026-10-20 | 35 | signalements | Signalements vérifiés (exemple) | | faible",
        "next" to "Voir les chiffres | voir:manifestants-journees",
    )

    private val party = common("parti-test", "parti") + listOf(
        "acronym" to "PET",
        "founded" to "2009-05-16",
        "financing" to "association-de-financement | Agrément CNCCFP n° 0000-A (exemple) | 2009-07-02 | texte-officiel | Journal officiel (exemple) | https://example.org/exemple/jo",
        "public-funding" to "2025 | 4 105 120,00 | Décret de répartition pour 2025 (exemple) | https://example.org/exemple/decret-2025",
        "public-funding" to "2026 | 4 210 345,60 | Décret de répartition pour 2026 (exemple) | https://example.org/exemple/decret-2026",
        "seats" to "assemblee | 120 | 2026-10-01 | parlement | Assemblée nationale (exemple) | https://example.org/exemple/an",
        "leader" to "alex-modele",
        "website" to "https://example.org/exemple/parti",
        source,
    )

    private val person = common("camille-test", "personne") + listOf(
        "role" to "depute",
        "group" to "Groupe d'exemple A",
        "territory" to "Exempleville",
        "mandate" to "Députée | 1re circonscription d'Exempleville | 2024-07-18 |",
        "party" to "parti-exemple-a",
        "official-id" to "an:PA900001",
        source,
    )

    private val text = common("ppl-test", "texte") + listOf(
        "text-kind" to "proposition",
        "stage" to "commission",
        "stage-history" to "depot | 2026-10-01",
        "next-vote" to "2026-11-24 | Examen en séance",
        "493" to "2026-10-21",
        "impact" to "Salariés (exemple) | gagne | Leur salaire suivrait les prix. | Évaluation (exemple) | https://example.org/exemple/eval",
        "official-id" to "an-dossier:DLR5L17N90001",
        source,
    )

    private val movement = common("pays-test", "mouvement") + listOf(
        "country" to "Pays A (exemple)",
        "period" to "2026-01-15 |",
        "claim" to "Gel du prix de l'énergie",
        "result" to "Gel du prix de l'énergie pendant un an",
        source,
    )

    private val reply = common("reponse-test", "reponse", summary = false) + listOf(
        "respondent" to "[EXEMPLE] Dominique Fictif",
        "target" to "affaire-exemple",
    )

    private val dossier = common("dossier-test", "dossier") + ("axis" to "libertes")

    @Test
    fun everyTypeOfVoirHasItsSchema() {
        assertEquals(VoirCatalog.TYPES.toSet(), EditorialSchemas.of(EditorialSpace.VOIR).map { it.type }.toSet())
        assertEquals(VoirSchemas.PARTI, EditorialSchemas.of(ArticleType.PARTI))
        assertEquals(VoirCatalog.TYPES, VoirSchemas.all.map { it.type })
    }

    @Test
    fun aValidBlockOfEachTypePasses() {
        listOf(
            VoirSchemas.FAIT to fact, VoirSchemas.INDICATEUR to indicator, VoirSchemas.PARTI to party, VoirSchemas.PERSONNE to person,
            VoirSchemas.TEXTE to text, VoirSchemas.MOUVEMENT to movement, VoirSchemas.REPONSE to reply, VoirSchemas.DOSSIER to dossier,
        ).forEach { (schema, entries) -> assertEquals(emptyList(), check(schema, entries), schema.type.tagValue) }
    }

    @Test
    fun theTagsAreThoseVoirAlwaysWrote() {
        assertEquals(
            listOf(
                listOf("ppf-fact", "affaire"),
                listOf("ppf-date", "2026-06-03"),
                listOf("ppf-place", "Exempleville"),
                listOf("ppf-fact-status", "enquete"),
                listOf("ppf-procedure", "enquete"),
                listOf("a", address("alex-modele"), "", "personne"),
                listOf("ppf-source", "justice", "Jugement du tribunal (exemple)", "https://example.org/exemple/jugement", "2025-12-10"),
                listOf("ppf-archive", "771f0116299a5dfae07b63ae8aa1a93418e262ddb193307511e291bbf7fbd405", "opentimestamps", "https://example.org/exemple/archive", "2026-10-09"),
                listOf("ppf-next", "Vos droits", address("droits-en-manifestation")),
                listOf("ppf-next", "Interpeller vos élus", "space:decider"),
            ),
            tags(VoirSchemas.FAIT, fact),
        )
        // An empty position is kept, as « unknown »: the end of a running mandate, a measure without address.
        assertEquals(listOf("ppf-mandate", "Députée", "1re circonscription d'Exempleville", "2024-07-18", ""), tags(VoirSchemas.PERSONNE, person)[3])
        assertEquals(listOf("ppf-official-id", "an:PA900001"), tags(VoirSchemas.PERSONNE, person)[5])
        assertEquals(listOf("ppf-period", "2026-01-15", ""), tags(VoirSchemas.MOUVEMENT, movement)[1])
        assertEquals(listOf("a", address("parti-exemple-a"), "", "parti"), tags(VoirSchemas.PERSONNE, person)[4])
        assertEquals(listOf("a", address("alex-modele"), "", "responsable"), tags(VoirSchemas.PARTI, party)[6])
        assertEquals(listOf("a", address("affaire-exemple"), "", "cible"), tags(VoirSchemas.REPONSE, reply)[1])
    }

    @Test
    fun aFactFollowsTheMethod() {
        assertEquals(listOf(VoirSchemas.Rules.CASE_NEEDS_PROCEDURE), codes(VoirSchemas.FAIT, fact.filter { it.first != "procedure" }))
        val vote = fact.with("fact", "vote").filter { it.first != "procedure" } + ("sentence" to "Six mois avec sursis")
        assertEquals(listOf(VoirSchemas.Rules.SENTENCE_ONLY_FOR_CASES), codes(VoirSchemas.FAIT, vote))
        assertEquals(emptyList(), codes(VoirSchemas.FAIT, fact + ("sentence" to "Six mois avec sursis")))
        assertEquals(listOf(Codes.FIELD_TOO_FEW), codes(VoirSchemas.FAIT, fact.filter { it.first != "source" }))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.FAIT, fact.filter { it.first != "fact-status" }))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(VoirSchemas.FAIT, fact.with("fact-status", "probable")))
        assertEquals(listOf(Codes.FIELD_UNKNOWN), codes(VoirSchemas.FAIT, fact + ("rumeur" to "oui")))
        assertEquals(listOf(Codes.VALUE_HASH), codes(VoirSchemas.FAIT, fact.with("archive", "abc | opentimestamps")))
        assertEquals(listOf(Codes.VALUE_URL), codes(VoirSchemas.FAIT, fact.with("source", "justice | Jugement | pas une adresse")))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(VoirSchemas.FAIT, fact + ("party" to "alex-modele")))
        assertEquals(listOf(Codes.LINK_UNKNOWN), codes(VoirSchemas.FAIT, fact.with("person", "personne-inconnue")))
        val space = fact.map { if (it.second.endsWith("espace:decider")) "next" to "Ailleurs | espace:nulle-part" else it }
        assertEquals(listOf(VoirSchemas.Rules.NEXT_SPACE), codes(VoirSchemas.FAIT, space))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(VoirSchemas.FAIT, fact + ("next" to "Le texte | fiche:pjl-exemple")))
    }

    @Test
    fun anIndicatorKeepsEveryMeasure() {
        assertEquals(listOf(VoirSchemas.Rules.MEASURE_ID_ONCE), codes(VoirSchemas.INDICATEUR, indicator.map { if (it.second.startsWith("b1-sig")) it.first to it.second.replace("b1-sig", "b1-aut") else it }))
        assertEquals(listOf(VoirSchemas.Rules.MEASURE_PART), codes(VoirSchemas.INDICATEUR, indicator.map { if (it.second.startsWith("b1-aut")) it.first to it.second.replace("| France | 4", "| France | 2 000") else it }))
        assertEquals(listOf(Codes.VALUE_NUMBER), codes(VoirSchemas.INDICATEUR, indicator.map { if (it.second.startsWith("b1-sig")) it.first to it.second.replace("| 35 |", "| 3.5 |") else it }))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(VoirSchemas.INDICATEUR, indicator.map { if (it.second.startsWith("b1-sig")) it.first to it.second.replace("faible", "douteuse") else it }))
        assertEquals(listOf(Codes.VALUE_POSITION_REQUIRED), codes(VoirSchemas.INDICATEUR, indicator.map { if (it.second.startsWith("b1-sig")) it.first to it.second.replace("| faible", "|") else it }))
        assertEquals(listOf(VoirSchemas.Rules.DUPLICATE), codes(VoirSchemas.INDICATEUR, indicator + ("accepted-source" to "autorites")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.INDICATEUR, indicator.filter { it.first != "unit" }))
    }

    @Test
    fun aPartyIsReadWithTheSameCriteriaForAll() {
        val agent = party.with("financing", "mandataire-financier | Jean Exemple | 1998-12-15")
        assertEquals(listOf(VoirSchemas.Rules.NO_AGENT_NAME), codes(VoirSchemas.PARTI, agent))
        assertEquals(emptyList(), codes(VoirSchemas.PARTI, party.with("financing", "mandataire-financier |  | 1998-12-15")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.PARTI, party.filter { it.first != "financing" }))
        assertEquals(listOf(Codes.FIELD_REPEATED), codes(VoirSchemas.PARTI, party + ("financing" to "mandataire-financier")))
        val sameYear = party.map { if (it.second.startsWith("2025 |")) it.first to it.second.replace("2025 |", "2026 |") else it }
        assertEquals(listOf(VoirSchemas.Rules.PUBLIC_FUNDING_YEAR_ONCE), codes(VoirSchemas.PARTI, sameYear))
        val before1988 = party.map { if (it.second.startsWith("2025 |")) it.first to it.second.replace("2025 |", "1987 |") else it }
        assertEquals(listOf(Codes.VALUE_NUMBER_MIN), codes(VoirSchemas.PARTI, before1988))
        assertEquals(listOf(Codes.VALUE_NUMBER_DECIMALS), codes(VoirSchemas.PARTI, party.with("seats", "assemblee | 12,5 | 2026-10-01 | parlement | AN (exemple) | https://example.org/exemple/an")))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(VoirSchemas.PARTI, party.with("seats", "chambre | 12 | 2026-10-01 | parlement | AN (exemple) | https://example.org/exemple/an")))
        // A count the engine reads but the application would not (« 0 120 »): the line would be dropped, so it is refused.
        val dropped = check(VoirSchemas.PARTI, party.with("seats", "assemblee | 0 120 | 2026-10-01 | parlement | AN (exemple) | https://example.org/exemple/an"))
        assertEquals(listOf(VoirSchemas.Rules.LINE_IGNORED), dropped.map { it.code })
        assertEquals("seats", dropped.single().field)
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(VoirSchemas.PARTI, party.with("leader", "parti-exemple-a")))
    }

    @Test
    fun aPersonABillAMovementAReplyAndADossier() {
        assertEquals(listOf(Codes.VALUE_OFFICIAL_ID), codes(VoirSchemas.PERSONNE, person.with("official-id", "an-dossier:DLR5L17N90001")))
        assertEquals(listOf(VoirSchemas.Rules.DUPLICATE), codes(VoirSchemas.PERSONNE, person + ("role" to "depute")))
        assertEquals(listOf(Codes.FIELD_REPEATED), codes(VoirSchemas.PERSONNE, person + ("party" to "parti-exemple-a")))
        assertEquals(listOf(Codes.VALUE_DATE), codes(VoirSchemas.PERSONNE, person.with("mandate", "Députée | Exempleville | juillet 2024 |")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.TEXTE, text.filter { it.first != "stage" }))
        assertEquals(listOf(VoirSchemas.Rules.DUPLICATE), codes(VoirSchemas.TEXTE, text + ("493" to "2026-10-21")))
        assertEquals(listOf(Codes.VALUE_OFFICIAL_ID), codes(VoirSchemas.TEXTE, text.with("official-id", "senat:12345A")))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(VoirSchemas.TEXTE, text.with("impact", "Salariés | gagne beaucoup | Effet.")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.MOUVEMENT, movement.filter { it.first != "country" }))
        assertEquals(listOf(Codes.VALUE_POSITION_REQUIRED), codes(VoirSchemas.MOUVEMENT, movement.with("period", "| 2026-05-31")))
        assertEquals(listOf(Codes.LINK_UNKNOWN), codes(VoirSchemas.REPONSE, reply.with("target", "contenu-inconnu")))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(VoirSchemas.REPONSE, reply.filter { it.first != "respondent" }))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(VoirSchemas.DOSSIER, dossier.with("axis", "societe")))
        assertEquals(listOf(Codes.FIELD_UNKNOWN), codes(VoirSchemas.DOSSIER, dossier + ("source" to source.second)))
    }

    @Test
    fun aPublishedBlockReadsBackAsItsDraft() {
        for ((schema, entries) in listOf(VoirSchemas.PARTI to party, VoirSchemas.FAIT to fact, VoirSchemas.INDICATEUR to indicator)) {
            val unsigned = EditorialEngine.build(schema, ContentDraft(schema.type, entries, body), author)
            val signed = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
            assertEquals(unsigned, EditorialEngine.build(schema, EditorialEngine.draftOf(schema, signed, context), author), schema.type.tagValue)
        }
    }

    @Test
    fun everyRuleOfVoirHasItsFrenchText() {
        listOf(
            VoirSchemas.Rules.UNREADABLE, VoirSchemas.Rules.LINE_IGNORED, VoirSchemas.Rules.CASE_NEEDS_PROCEDURE, VoirSchemas.Rules.SENTENCE_ONLY_FOR_CASES,
            VoirSchemas.Rules.NO_AGENT_NAME, VoirSchemas.Rules.PUBLIC_FUNDING_YEAR_ONCE, VoirSchemas.Rules.MEASURE_ID_ONCE, VoirSchemas.Rules.MEASURE_PART,
            VoirSchemas.Rules.DUPLICATE, VoirSchemas.Rules.NEXT_SPACE, VoirSchemas.Rules.SEAT_NAME,
        ).forEach { assertNotNull(EditorialMessages.template(it), it) }
    }

    private companion object {
        val COMMON_TAGS = setOf("d", "title", "summary", "published_at", "ppf-type", "ppf-origin", "t")
    }
}
