package org.politisationpopulairefrancaise.core.editorial.schema

import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val author = IdentityKeys.generate().publicKey

private val context = CheckContext.of(
    author.hex,
    mapOf(
        "alex-modele" to ArticleType.PERSONNE,
        "parti-exemple" to ArticleType.PARTI,
        "garde-a-vue" to ArticleType.FICHE_PRATIQUE,
        "fait-exemple" to ArticleType.FAIT,
        "le-referendum" to ArticleType.PARCOURS,
    ),
)

/** A test schema using every kind of value and of rule (the type itself does not matter to the engine). */
private val schema = ContentSchema(
    type = ArticleType.FAIT,
    space = EditorialSpace.VOIR,
    labelKey = "test",
    sourceDirectory = "test",
    bundle = "test.json",
    summary = Presence.OPTIONAL,
    fields = listOf(
        FieldSpec("fact", "l", value = ValueSpec.Single(FieldType.Choice(listOf("affaire", "vote", "promesse"), "p."))),
        FieldSpec("procedure", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(40))),
        FieldSpec("sentence", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(40))),
        FieldSpec("date", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date())),
        FieldSpec("period", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date(yearOnly = true))),
        FieldSpec("hash", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Hash)),
        FieldSpec("minutes", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Minutes)),
        FieldSpec("website", "l", occurs = Occurs.MANY(max = 2), value = ValueSpec.Single(FieldType.Url)),
        FieldSpec("official-id", "l", occurs = Occurs.MANY(), value = ValueSpec.OfficialId("^(an:PA[0-9]{1,8}|senat:[0-9]{5}[A-Z]|pe:[0-9]{1,8})$")),
        FieldSpec(
            "public-funding", "l", occurs = Occurs.MANY(),
            value = ValueSpec.Positional(
                listOf(
                    Position("year", "l", FieldType.Number(min = 1988.0, max = 2100.0)),
                    Position("amount", "l", FieldType.Number(decimals = true)),
                    Position("url", "l", FieldType.Url, required = false),
                ),
            ),
        ),
        FieldSpec(
            "financing", "l", occurs = Occurs.OPTIONAL,
            value = ValueSpec.Positional(
                listOf(
                    Position("kind", "l", FieldType.Choice(listOf("association-de-financement", "mandataire-financier"), "p.")),
                    Position("reference", "l", FieldType.Text(100), required = false),
                    Position("since", "l", FieldType.Date(), required = false),
                ),
            ),
        ),
        FieldSpec("person", "l", occurs = Occurs.MANY(), value = ValueSpec.Link("personne", TargetType.PERSON)),
        FieldSpec("responsable", "l", occurs = Occurs.MANY(), value = ValueSpec.Link("responsable", TargetType.VOIR_BLOCK)),
        FieldSpec("next", "l", occurs = Occurs.MANY(), value = ValueSpec.NextStep),
        FieldSpec("forms", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(',', ValueSpec.Single(FieldType.Text(60)), escapable = true)),
        FieldSpec("all-forms", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(';', ValueSpec.Single(FieldType.Text(60)), each = Tagging.Each.ONE_TAG, joiner = " ; ")),
        FieldSpec(
            "related", "l", occurs = Occurs.OPTIONAL,
            value = ValueSpec.InlineList(',', ValueSpec.Link("lie", TargetType.GUIDE, mapOf("fiche" to TargetType.GUIDE, "parcours" to TargetType.COURSE))),
        ),
        FieldSpec("auto", "l", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(listOf("oui", "non"), "p.")), silentValues = setOf("oui")),
    ),
    body = BodySpec(
        blocks = listOf(
            BlockSpec("quiz", min = 1, max = 2, parser = BlockParser { lines -> if (lines.any { it.trim() == "bad" }) listOf("bad line") else emptyList() }),
        ),
    ),
    rules = listOf(
        CrossRule.RequiredWhen("procedure", whenField = "fact", values = setOf("affaire"), messageKey = "rule.procedure"),
        CrossRule.ForbiddenWhen("sentence", whenField = "fact", values = setOf("vote", "promesse"), messageKey = "rule.sentence"),
        CrossRule.PositionEmptyWhen("financing", position = "reference", whenPosition = "kind", values = setOf("mandataire-financier"), messageKey = "rule.agent"),
        CrossRule.RequiredWhen("financing", whenField = "financing", values = setOf("association-de-financement"), messageKey = "rule.since", position = "since", whenPosition = "kind"),
        CrossRule.UniquePosition("public-funding", position = "year", messageKey = "rule.year"),
        CrossRule.UniqueBy("website", positions = emptyList(), messageKey = "rule.website"),
        CrossRule.AnyOf(listOf("person", "responsable"), messageKey = "rule.anyone"),
        CrossRule.BlockRequiredWhen("chronologie", whenField = "fact", values = setOf("promesse"), messageKey = "rule.timeline"),
    ),
)

private val common = listOf(
    "slug" to "fait-test",
    "title" to "[EXEMPLE] Un fait",
    "summary" to "Un résumé.",
    "topics" to "a, b",
    "type" to "fait",
    "origin" to "human",
    "published_at" to "2026-11-05",
    "updated_at" to "2026-11-07",
)

private const val BODY = "Texte.\n\n::: quiz\nok\n:::\n\n## Sources\n\n- une source\n"

private fun draft(vararg specific: Pair<String, String>, body: String = BODY) =
    ContentDraft(ArticleType.FAIT, common + listOf("fact" to "vote", "person" to "alex-modele") + specific.toList(), body)

private fun codes(d: ContentDraft) = EditorialEngine.check(schema, d, context).map { it.code }

class EditorialEngineTest {
    @Test
    fun aValidDraftHasNoProblem() {
        assertEquals(emptyList(), EditorialEngine.check(schema, draft(), context))
    }

    @Test
    fun commonFieldsAreChecked() {
        val d = ContentDraft(ArticleType.FAIT, listOf("slug" to "Pas Bon", "type" to "parti", "origin" to "robot", "published_at" to "2026-02-30", "fact" to "vote", "person" to "alex-modele"), BODY)
        val problems = EditorialEngine.check(schema, d, context)
        assertTrue(Problem(Codes.VALUE_SLUG, "slug", args = listOf("Pas Bon")) in problems)
        assertTrue(Problem(Codes.FIELD_MISSING, "title") in problems)
        assertTrue(Problem(Codes.TYPE_MISMATCH, "type", args = listOf("parti", "fait")) in problems)
        assertTrue(Problem(Codes.VALUE_CHOICE, "origin", args = listOf("robot", "human, ai-assisted")) in problems)
        assertTrue(Problem(Codes.VALUE_DATE, "published_at", args = listOf("2026-02-30")) in problems)
        assertTrue(Problem(Codes.FIELD_MISSING, "updated_at") in problems)
        assertTrue(Codes.FIELD_REPEATED in codes(draft("title" to "Encore")))
        assertTrue(Codes.FIELD_UNKNOWN in codes(draft("inconnu" to "x")))
        assertTrue(Codes.TYPE_MISMATCH in EditorialEngine.check(schema, draft().copy(type = ArticleType.PARTI), context).map { it.code })
    }

    @Test
    fun occurrencesAreCounted() {
        assertTrue(Codes.FIELD_MISSING in codes(ContentDraft(ArticleType.FAIT, common + ("person" to "alex-modele"), BODY)))
        assertTrue(Codes.FIELD_MISSING in codes(draft().copy(entries = common + listOf("fact" to "  ", "person" to "alex-modele"))))
        assertTrue(Codes.FIELD_REPEATED in codes(draft("fact" to "vote")))
        assertTrue(Codes.FIELD_REPEATED in codes(draft("date" to "2026-01-01", "date" to "2026-01-02")))
        assertEquals(emptyList(), codes(draft("date" to "")), "an empty optional field is absent")
        assertTrue(Codes.FIELD_TOO_MANY in codes(draft("website" to "https://a.fr", "website" to "https://b.fr", "website" to "https://c.fr")))
        val few = schema.copy(fields = schema.fields.map { if (it.key == "person") it.copy(occurs = Occurs.MANY(min = 2)) else it })
        assertEquals(listOf(Problem(Codes.FIELD_TOO_FEW, "person", args = listOf("2"))), EditorialEngine.check(few, draft(), context))
    }

    @Test
    fun everyTypeOfValueIsChecked() {
        fun one(key: String, value: String) = EditorialEngine.check(schema, draft(key to value), context)
        assertEquals(Codes.VALUE_CHOICE, EditorialEngine.check(schema, draft().copy(entries = common + listOf("fact" to "autre", "person" to "alex-modele")), context).single().code)
        assertEquals(Codes.VALUE_TOO_LONG, one("procedure", "x".repeat(41)).single().code)
        assertEquals(emptyList(), one("date", "2026-11-07"))
        assertEquals(Codes.VALUE_DATE, one("date", "2026").single().code)
        assertEquals(emptyList(), one("period", "1936"))
        assertEquals(emptyList(), one("period", "1936-06"))
        assertEquals(emptyList(), one("period", "1936-06-07"))
        assertEquals(Codes.VALUE_DATE_OR_YEAR, one("period", "1936-13").single().code)
        assertEquals(emptyList(), one("hash", "a".repeat(64)))
        assertEquals(Codes.VALUE_HASH, one("hash", "A".repeat(64)).single().code)
        assertEquals(emptyList(), one("minutes", "5"))
        assertEquals(Codes.VALUE_MINUTES, one("minutes", "0").single().code)
        assertEquals(Codes.VALUE_MINUTES, one("minutes", "cinq").single().code)
        assertEquals(emptyList(), one("website", "https://www.assemblee-nationale.fr/dyn/17/dossiers"))
        assertEquals(Codes.VALUE_URL, one("website", "http://exemple.fr").single().code)
        assertEquals(Codes.VALUE_URL, one("website", "https://exemple fr").single().code)
        assertEquals(Codes.VALUE_URL, one("website", "javascript:alert(1)").single().code)
    }

    @Test
    fun officialIdentifiersFollowTheirPattern() {
        listOf("an:PA1234", "senat:12345A", "pe:197400").forEach { assertEquals(emptyList(), codes(draft("official-id" to it)), it) }
        listOf("an:PO1234", "senat:1234A", "PA1234", "an:PA").forEach {
            assertEquals(listOf(Problem(Codes.VALUE_OFFICIAL_ID, "official-id", 0, args = listOf(it))), EditorialEngine.check(schema, draft("official-id" to it), context), it)
        }
    }

    @Test
    fun numbersAreWrittenTheFrenchWay() {
        assertEquals(4_210_345.60, FrenchNumber.parse("4 210 345,60"))
        assertEquals(4_210_345.60, FrenchNumber.parse("4 210 345,60"))
        assertEquals(4_210_345.0, FrenchNumber.parse("4210345"))
        assertNull(FrenchNumber.parse("4.210.345"))
        assertNull(FrenchNumber.parse("4 21 345"))
        assertNull(FrenchNumber.parse("-3"))
        assertEquals(-3.5, FrenchNumber.parse("-3,5", allowNegative = true))
        assertNull(FrenchNumber.parse("3,5", decimals = false))
        assertEquals(emptyList(), codes(draft("public-funding" to "2026 | 4 210 345,60 | https://www.legifrance.gouv.fr/jorf/id/X")))
        assertEquals(listOf(Codes.VALUE_NUMBER_DECIMALS), codes(draft("public-funding" to "2026,5 | 10")))
        assertEquals(listOf(Codes.VALUE_NUMBER_NEGATIVE), codes(draft("public-funding" to "2026 | -10")))
        assertEquals(listOf(Codes.VALUE_NUMBER_MIN), codes(draft("public-funding" to "1950 | 10")))
        assertEquals(listOf(Codes.VALUE_NUMBER_MAX), codes(draft("public-funding" to "2200 | 10")))
        assertEquals(listOf(Codes.VALUE_NUMBER), codes(draft("public-funding" to "2026 | dix")))
    }

    @Test
    fun positionsAreCheckedOneByOne() {
        assertEquals(emptyList(), codes(draft("public-funding" to "2026 | 10")), "an optional last position may be left out")
        assertEquals(emptyList(), codes(draft("public-funding" to "2026 | 10 |")), "or left empty")
        assertEquals(listOf(Problem(Codes.VALUE_POSITION_REQUIRED, "public-funding", 0, "amount")), EditorialEngine.check(schema, draft("public-funding" to "2026 || https://a.fr"), context))
        assertEquals(listOf(Codes.VALUE_POSITIONS), codes(draft("public-funding" to "2026 | 10 | https://a.fr | trop")))
    }

    @Test
    fun linksMustNameAContentOfTheExpectedType() {
        assertEquals(listOf(Problem(Codes.LINK_UNKNOWN, "person", 1, args = listOf("inconnu"))), EditorialEngine.check(schema, draft("person" to "inconnu"), context))
        assertEquals(listOf(Problem(Codes.LINK_WRONG_TYPE, "person", 1, args = listOf("parti-exemple", "person", "parti"))), EditorialEngine.check(schema, draft("person" to "parti-exemple"), context))
        assertEquals(emptyList(), codes(draft("responsable" to "parti-exemple")), "a free role towards any block of Voir")
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(draft("responsable" to "garde-a-vue")))
        assertEquals(emptyList(), codes(draft("related" to "fiche:garde-a-vue, parcours:le-referendum")))
        assertEquals(listOf(Codes.LINK_KIND), codes(draft("related" to "garde-a-vue")))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(draft("related" to "parcours:garde-a-vue")))
    }

    @Test
    fun nextStepsNameAGuideABlockOrASpace() {
        assertEquals(emptyList(), codes(draft("next" to "Vos droits | fiche:garde-a-vue", "next" to "Le fait | voir:fait-exemple", "next" to "Agir | espace:agir")))
        assertEquals(listOf(Codes.VALUE_NEXT), codes(draft("next" to "fiche:garde-a-vue")))
        assertEquals(listOf(Codes.VALUE_NEXT_TARGET), codes(draft("next" to "Vos droits | ailleurs:garde-a-vue")))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(draft("next" to "Le fait | fiche:fait-exemple")))
        assertEquals(listOf(Codes.LINK_UNKNOWN), codes(draft("next" to "Le fait | voir:inconnu")))
    }

    @Test
    fun crossRulesAreChecked() {
        fun withFact(fact: String, vararg more: Pair<String, String>, body: String = BODY) =
            EditorialEngine.check(schema, ContentDraft(ArticleType.FAIT, common + listOf("fact" to fact, "person" to "alex-modele") + more.toList(), body), context)
        // RequiredWhen
        assertEquals(listOf(Problem("rule.procedure", "procedure")), withFact("affaire"))
        assertEquals(emptyList(), withFact("affaire", "procedure" to "enquete"))
        // ForbiddenWhen
        assertEquals(listOf(Problem("rule.sentence", "sentence", 0)), withFact("vote", "sentence" to "un an"))
        assertEquals(emptyList(), withFact("affaire", "procedure" to "jugement", "sentence" to "un an"))
        // PositionEmptyWhen
        assertEquals(listOf(Problem("rule.agent", "financing", 0, "reference")), withFact("vote", "financing" to "mandataire-financier | Jean Exemple"))
        assertEquals(emptyList(), withFact("vote", "financing" to "mandataire-financier"))
        // RequiredWhen on a position of the same occurrence
        assertEquals(listOf(Problem("rule.since", "financing", 0, "since")), withFact("vote", "financing" to "association-de-financement | Agrément n° 1"))
        assertEquals(emptyList(), withFact("vote", "financing" to "association-de-financement | Agrément n° 1 | 2009-07-02"))
        // UniquePosition and UniqueBy
        assertEquals(
            listOf(Problem("rule.year", "public-funding", 1, "year", listOf("2026"))),
            withFact("vote", "public-funding" to "2026 | 10", "public-funding" to "2026 | 20"),
        )
        assertEquals(listOf(Problem("rule.website", "website", 1, args = listOf("https://a.fr"))), withFact("vote", "website" to "https://a.fr", "website" to "https://a.fr"))
        // AnyOf
        assertEquals(
            listOf(Problem("rule.anyone", "person")),
            EditorialEngine.check(schema, ContentDraft(ArticleType.FAIT, common + ("fact" to "vote"), BODY), context),
        )
        // BlockRequiredWhen
        assertEquals(listOf(Problem("rule.timeline", EditorialEngine.BODY, args = listOf("chronologie"))), withFact("promesse"))
        assertEquals(emptyList(), withFact("promesse", body = BODY + "\n::: chronologie\n- 2026 : x\n:::\n"))
    }

    @Test
    fun theBodyIsChecked() {
        val noSources = "Texte.\n\n::: quiz\nok\n:::\n"
        assertEquals(listOf(Problem(Codes.BODY_SECTION_MISSING, EditorialEngine.BODY, args = listOf("Sources"))), EditorialEngine.check(schema, draft(body = noSources), context))
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_FEW), codes(draft(body = "Texte.\n\n## Sources\n")))
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_MANY), codes(draft(body = "::: quiz\n:::\n::: quiz\n:::\n::: quiz\n:::\n## Sources\n")))
        assertEquals(
            listOf(Problem(Codes.BODY_INVALID, EditorialEngine.BODY, args = listOf("quiz", "bad line"))),
            EditorialEngine.check(schema, draft(body = "::: quiz\nbad\n:::\n## Sources\n"), context),
        )
        assertEquals(listOf(Codes.BODY_TOO_LONG), codes(draft(body = BODY + "x".repeat(60_001))))
    }

    @Test
    fun checksRunOnTheBuiltEventWhenEverythingElseIsValid() {
        val seen = mutableListOf<String>()
        val checked = schema.copy(checks = listOf(SchemaCheck { _, event, _ -> seen += event.idHex(); listOf(Problem("rule.custom")) }))
        assertEquals(listOf(Problem("rule.custom")), EditorialEngine.check(checked, draft(), context))
        assertEquals(1, seen.size)
        EditorialEngine.check(checked, draft("fact" to "vote"), context)
        assertEquals(1, seen.size, "not run on a draft with problems")
    }

    @Test
    fun theEventFollowsTheOrderOfTheEntries() {
        val d = draft(
            "next" to "Vos droits | fiche:garde-a-vue",
            "public-funding" to "2026 | 4 210 345,60 |",
            "forms" to "article 49\\, alinéa 3, 49.3",
            "all-forms" to "un ; deux",
            "auto" to "oui",
            "related" to "fiche:garde-a-vue, parcours:le-referendum",
            "next" to "Agir | espace:agir",
        )
        val event = EditorialEngine.build(schema, d, author)
        val a = "30023:${author.hex}:"
        assertEquals(
            listOf(
                listOf("d", "fait-test"),
                listOf("title", "[EXEMPLE] Un fait"),
                listOf("summary", "Un résumé."),
                listOf("published_at", "1793880000"),
                listOf("ppf-type", "fait"),
                listOf("ppf-origin", "human"),
                listOf("t", "a"),
                listOf("t", "b"),
                listOf("ppf-fact", "vote"),
                listOf("a", "${a}alex-modele", "", "personne"),
                listOf("ppf-next", "Vos droits", "${a}garde-a-vue"),
                listOf("ppf-public-funding", "2026", "4 210 345,60", ""),
                listOf("ppf-forms", "article 49, alinéa 3"),
                listOf("ppf-forms", "49.3"),
                listOf("ppf-all-forms", "un", "deux"),
                listOf("a", "${a}garde-a-vue", "", "lie"),
                listOf("a", "${a}le-referendum", "", "lie"),
                listOf("ppf-next", "Agir", "space:agir"),
            ),
            event.tags,
        )
        assertEquals(1_794_052_800L, event.createdAt, "updated_at at noon UTC")
        assertEquals(1L, EditorialEngine.build(schema, d, author, createdAt = 1L).createdAt)
        assertEquals(BODY, event.content)
    }

    @Test
    fun aPublishedEventReadsBackAsItsDraft() {
        val d = ContentDraft(
            ArticleType.FAIT,
            common + listOf(
                "fact" to "affaire",
                "person" to "alex-modele",
                "next" to "Vos droits | fiche:garde-a-vue",
                "procedure" to "enquete",
                "public-funding" to "2026 | 4 210 345,60 |",
                "forms" to "article 49\\, alinéa 3, 49.3",
                "all-forms" to "un ; deux",
                "related" to "fiche:garde-a-vue, parcours:le-referendum",
                "next" to "Le fait | voir:fait-exemple",
            ),
            BODY,
        )
        val unsigned = EditorialEngine.build(schema, d, author)
        val signed = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        val back = EditorialEngine.draftOf(schema, signed, context)
        assertEquals(d, back)
        assertEquals(unsigned, EditorialEngine.build(schema, back, author))
        // Without a context the draft still builds the same event.
        assertEquals(unsigned, EditorialEngine.build(schema, EditorialEngine.draftOf(schema, signed), author))
    }

    @Test
    fun everyProblemReadsInFrench() {
        val codes = listOf(
            Codes.TYPE_MISMATCH, Codes.FIELD_MISSING, Codes.FIELD_REPEATED, Codes.FIELD_TOO_FEW, Codes.FIELD_TOO_MANY, Codes.FIELD_UNKNOWN,
            Codes.VALUE_EMPTY, Codes.VALUE_TOO_LONG, Codes.VALUE_MULTILINE, Codes.VALUE_CHOICE, Codes.VALUE_DATE, Codes.VALUE_DATE_OR_YEAR,
            Codes.VALUE_NUMBER, Codes.VALUE_NUMBER_DECIMALS, Codes.VALUE_NUMBER_NEGATIVE, Codes.VALUE_NUMBER_MIN, Codes.VALUE_NUMBER_MAX,
            Codes.VALUE_URL, Codes.VALUE_SLUG, Codes.VALUE_MINUTES, Codes.VALUE_HASH, Codes.VALUE_OFFICIAL_ID, Codes.VALUE_POSITIONS,
            Codes.VALUE_POSITION_REQUIRED, Codes.VALUE_NEXT, Codes.VALUE_NEXT_TARGET, Codes.LINK_UNKNOWN, Codes.LINK_WRONG_TYPE, Codes.LINK_KIND,
            Codes.BODY_TOO_LONG, Codes.BODY_SECTION_MISSING, Codes.BODY_BLOCK_TOO_FEW, Codes.BODY_BLOCK_TOO_MANY, Codes.BODY_INVALID,
            Codes.UNBUILDABLE, Codes.CHECK,
        )
        codes.forEach { assertNotNull(EditorialMessages.template(it), it) }
        assertEquals("Champ « title » : ce champ est obligatoire", EditorialMessages.french(Problem(Codes.FIELD_MISSING, "title")))
        assertEquals(
            "Champ « public-funding », ligne 2, valeur « amount » : « dix » n'est pas un nombre écrit à la française (par exemple 4 210 345,60)",
            EditorialMessages.french(Problem(Codes.VALUE_NUMBER, "public-funding", 1, "amount", listOf("dix"))),
        )
        assertEquals("Texte : il manque la section « ## Sources »", EditorialMessages.french(Problem(Codes.BODY_SECTION_MISSING, EditorialEngine.BODY, args = listOf("Sources"))))
    }
}
