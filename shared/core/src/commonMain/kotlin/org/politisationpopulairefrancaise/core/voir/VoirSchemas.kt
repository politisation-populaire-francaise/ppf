package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.Space
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.BodySpec
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.CrossRule
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.FrenchNumber
import org.politisationpopulairefrancaise.core.editorial.schema.Occurs
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Presence
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.SchemaCheck
import org.politisationpopulairefrancaise.core.editorial.schema.TargetType
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.signing.UnsignedEvent
import org.politisationpopulairefrancaise.core.voir.parlement.ParliamentFormat

/**
 * The schemas of the building blocks of "Voir" (D-034, docs/FORMATS.md section 4 quinquies), for the
 * editorial engine (D-092): indicators, sourced facts, public persons, bills, movements of the world,
 * dossiers, rights of reply and political parties. They state the rules tools/content-signer applied by
 * hand until 8 November 2026 (voirEvent, checkVoir, checkParty), and more: every field is now declared,
 * so an unknown key is refused instead of becoming a stray `ppf-` tag. The no-regression test of the tool
 * keeps the events identical. Each key becomes `ppf-<key>` (positional values separated by « | », an empty
 * position meaning « unknown »); links become `a` tags with the roles of [VoirTags].
 */
object VoirSchemas {
    private const val BUNDLE = "voir.json"

    private fun choice(values: List<String>, prefix: String) = FieldType.Choice(values, "voir.$prefix.")

    private val sourceKinds = choice(SourceKind.entries.map { it.tagValue }, "source_kind")

    /** No Markdown section is required: the sources of Voir are tags, not a « ## Sources » section. */
    private val body = BodySpec(requiredSections = emptyList())

    // ---------------------------------------------------------------------------------------------------
    // Fields shared by several types

    /** « justice | Jugement du tribunal (exemple) | https://… | 2025-12-10 »: kind, name, address, date (may be unknown). */
    private fun source(min: Int) = FieldSpec(
        "source", "voir.field.source", "voir.help.source", occurs = Occurs.MANY(min = min),
        value = ValueSpec.Positional(
            listOf(
                Position("kind", "voir.source.kind", sourceKinds),
                Position("name", "voir.source.name", FieldType.Text(300)),
                Position("url", "voir.source.url", FieldType.Url),
                Position("date", "voir.source.date", FieldType.Date(), required = false),
            ),
        ),
    )

    /** « 2026-06-05 | reason »: the public log of corrections (VO-22). */
    private val correction = FieldSpec(
        "correction", "voir.field.correction", "voir.help.correction", occurs = Occurs.MANY(),
        value = ValueSpec.Positional(
            listOf(
                Position("date", "voir.correction.date", FieldType.Date()),
                Position("reason", "voir.correction.reason", FieldType.Text(1000)),
            ),
        ),
    )

    /** « Et maintenant ? »: « label | fiche:<slug> », « label | voir:<slug> » or « label | espace:<id> ». */
    private val next = FieldSpec("next", "voir.field.next", "voir.help.next", occurs = Occurs.MANY(), value = ValueSpec.NextStep)

    /** « <sha256> | opentimestamps | https://… | 2026-10-09 »: an archived copy of an official document (VO-07). */
    private val archive = FieldSpec(
        "archive", "voir.field.archive", "voir.help.archive", occurs = Occurs.MANY(),
        value = ValueSpec.Positional(
            listOf(
                Position("sha256", "voir.archive.sha256", FieldType.Hash),
                Position("proof", "voir.archive.proof", choice(listOf("opentimestamps", "rfc3161"), "archive_proof"), required = false),
                Position("url", "voir.archive.url", FieldType.Url, required = false),
                Position("date", "voir.archive.date", FieldType.Date(), required = false),
            ),
        ),
    )

    private val axis = FieldSpec("axis", "voir.field.axis", value = ValueSpec.Single(choice(VoirAxis.entries.map { it.tagValue }, "axis")))

    // ---------------------------------------------------------------------------------------------------
    // Checks that do not describe themselves

    /** The rule codes of the schemas of Voir (French texts in EditorialMessages). */
    object Rules {
        const val UNREADABLE: String = "voir.rule.unreadable"
        const val LINE_IGNORED: String = "voir.rule.line_ignored"
        const val CASE_NEEDS_PROCEDURE: String = "voir.rule.case_needs_procedure"
        const val SENTENCE_ONLY_FOR_CASES: String = "voir.rule.sentence_only_for_cases"
        const val NO_AGENT_NAME: String = "voir.rule.no_agent_name"
        const val PUBLIC_FUNDING_YEAR_ONCE: String = "voir.rule.public_funding_year_once"
        const val MEASURE_ID_ONCE: String = "voir.rule.measure_id_once"
        const val MEASURE_PART: String = "voir.rule.measure_part"
        const val DUPLICATE: String = "voir.rule.duplicate"
        const val NEXT_SPACE: String = "voir.rule.next_space"
    }

    private fun article(event: UnsignedEvent): Article? =
        Article.from(SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128)))

    /**
     * The application reads the block (as checkVoir did) and drops none of its lines: for each repeated
     * field, the number of tags written is the number of values the reader keeps. [counts] gives, for a
     * read block, the number kept per field key.
     */
    private fun <T : Any> readable(read: (Article) -> T?, counts: (T) -> Map<String, Int> = { emptyMap() }) = SchemaCheck { draft, event, _ ->
        val model = article(event)?.let(read) ?: return@SchemaCheck listOf(Problem(Rules.UNREADABLE))
        counts(model).mapNotNull { (key, kept) ->
            val written = draft.values(key).count { it.isNotBlank() }
            if (kept < written) Problem(Rules.LINE_IGNORED, key, args = listOf((written - kept).toString())) else null
        }
    }

    /** « espace:<id> » names a space of the application (an unknown one would be dropped by the reader). */
    private val nextSpaces = SchemaCheck { draft, _, _ ->
        draft.values(next.key).withIndex().mapNotNull { (i, raw) ->
            val target = EditorialEngine.positions(raw).getOrNull(1).orEmpty()
            val id = target.removePrefix(SPACE_PREFIX)
            if (target.startsWith(SPACE_PREFIX) && Space.fromId(id) == null) Problem(Rules.NEXT_SPACE, next.key, i, args = listOf(id)) else null
        }
    }

    private const val SPACE_PREFIX = "espace:"

    /** The part of a measure (« dont mineurs ») never exceeds its value; the reader would hide it. */
    private val measureParts = SchemaCheck { draft, _, _ ->
        draft.values("measure").withIndex().mapNotNull { (i, raw) ->
            val parts = EditorialEngine.positions(raw)
            val value = parts.getOrNull(2)?.let { FrenchNumber.parse(it, decimals = true) }
            val part = parts.getOrNull(9)?.takeIf { it.isNotEmpty() }?.let { FrenchNumber.parse(it, decimals = true) }
            if (value != null && part != null && part > value) Problem(Rules.MEASURE_PART, "measure", i, "part", listOf(parts[2], parts[9])) else null
        }
    }

    private fun counts(vararg pairs: Pair<String, Int>): Map<String, Int> = mapOf(*pairs)

    // ---------------------------------------------------------------------------------------------------
    // The types

    val INDICATEUR: ContentSchema = ContentSchema(
        type = ArticleType.INDICATEUR,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.indicateur",
        sourceDirectory = "voir/indicateurs",
        bundle = BUNDLE,
        fields = listOf(
            axis,
            FieldSpec("unit", "voir.field.unit", value = ValueSpec.Single(FieldType.Text(40))),
            FieldSpec("rank", "voir.field.rank", "voir.help.rank", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Number(min = 1.0, max = 999.0))),
            FieldSpec("declarative", "voir.field.declarative", "voir.help.declarative", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(listOf("true", "false"), "declarative"))),
            FieldSpec("breakdown", "voir.field.breakdown", "voir.help.breakdown", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(120))),
            FieldSpec("accepted-source", "voir.field.accepted_source", occurs = Occurs.MANY(), value = ValueSpec.Single(sourceKinds)),
            // « j1-aut | 2026-09-18 | 312000 | autorites | Ministère (exemple) | https://… | moyenne | Journée du 18 septembre | France | 12 »
            FieldSpec(
                "measure", "voir.field.measure", "voir.help.measure", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("id", "voir.measure.id", FieldType.Slug()),
                        Position("date", "voir.measure.date", FieldType.Date()),
                        Position("value", "voir.measure.value", FieldType.Number(decimals = true)),
                        Position("source-kind", "voir.source.kind", sourceKinds),
                        Position("source-name", "voir.source.name", FieldType.Text(300)),
                        Position("source-url", "voir.source.url", FieldType.Url, required = false),
                        Position("reliability", "voir.measure.reliability", choice(Reliability.entries.map { it.tagValue }, "reliability")),
                        Position("occasion", "voir.measure.occasion", FieldType.Text(120), required = false),
                        Position("place", "voir.measure.place", FieldType.Text(120), required = false),
                        Position("part", "voir.measure.part", FieldType.Number(decimals = true), required = false),
                    ),
                ),
            ),
            source(min = 0),
            correction,
            next,
        ),
        body = body,
        rules = listOf(
            CrossRule.UniqueBy("measure", listOf("id"), Rules.MEASURE_ID_ONCE),
            CrossRule.UniqueBy("accepted-source", emptyList(), Rules.DUPLICATE),
        ),
        checks = listOf(
            measureParts,
            nextSpaces,
            readable(Indicator::from) {
                counts("measure" to it.measures.size, "accepted-source" to it.acceptedSources.size, "source" to it.sources.size, "correction" to it.corrections.size)
            },
        ),
    )

    private val factKinds = FactKind.entries.map { it.tagValue }

    val FAIT: ContentSchema = ContentSchema(
        type = ArticleType.FAIT,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.fait",
        sourceDirectory = "voir/faits",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("fact", "voir.field.fact", value = ValueSpec.Single(choice(factKinds, "fact"))),
            FieldSpec("date", "voir.field.date", value = ValueSpec.Single(FieldType.Date())),
            FieldSpec("place", "voir.field.place", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(120))),
            FieldSpec("fact-status", "voir.field.fact_status", "voir.help.fact_status", value = ValueSpec.Single(choice(FactStatus.entries.map { it.tagValue }, "fact_status"))),
            FieldSpec("procedure", "voir.field.procedure", "voir.help.procedure", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(ProcedureStage.entries.map { it.tagValue }, "procedure"))),
            FieldSpec("sentence", "voir.field.sentence", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(300))),
            FieldSpec("sentence-served", "voir.field.sentence_served", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(300))),
            FieldSpec("promise", "voir.field.promise", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(PromiseStatus.entries.map { it.tagValue }, "promise"))),
            FieldSpec("vote", "voir.field.vote", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(VotePosition.entries.map { it.tagValue }, "vote"))),
            FieldSpec("quote", "voir.field.quote", "voir.help.quote", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(1000))),
            FieldSpec("liberty", "voir.field.liberty", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(LibertyCategory.entries.map { it.tagValue }, "liberty"))),
            FieldSpec("recourse", "voir.field.recourse", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(RecourseStatus.entries.map { it.tagValue }, "recourse"))),
            FieldSpec("person", "voir.field.person", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_PERSON, TargetType.PERSON)),
            FieldSpec("text", "voir.field.text", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_TEXT, TargetType.TEXT)),
            FieldSpec("indicator", "voir.field.indicator", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_INDICATOR, TargetType.INDICATOR)),
            // The party itself (a programme promise, a conviction of the party as a legal person): never its members.
            FieldSpec("party", "voir.field.party", "voir.help.fact_party", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_PARTY, TargetType.PARTY)),
            source(min = 1),
            archive,
            correction,
            next,
        ),
        body = body,
        rules = listOf(
            // Presumption of innocence: a case is never shown without the stage of its procedure.
            CrossRule.RequiredWhen("procedure", whenField = "fact", values = setOf(FactKind.CASE.tagValue), messageKey = Rules.CASE_NEEDS_PROCEDURE),
            CrossRule.ForbiddenWhen("sentence", whenField = "fact", values = factKinds.toSet() - FactKind.CASE.tagValue, messageKey = Rules.SENTENCE_ONLY_FOR_CASES),
            CrossRule.ForbiddenWhen("sentence-served", whenField = "fact", values = factKinds.toSet() - FactKind.CASE.tagValue, messageKey = Rules.SENTENCE_ONLY_FOR_CASES),
        ),
        checks = listOf(
            nextSpaces,
            readable(Fact::from) {
                counts("source" to it.sources.size, "archive" to it.archives.size, "correction" to it.corrections.size)
            },
        ),
    )

    val PERSONNE: ContentSchema = ContentSchema(
        type = ArticleType.PERSONNE,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.personne",
        sourceDirectory = "voir/personnes",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("role", "voir.field.role", "voir.help.role", occurs = Occurs.MANY(), value = ValueSpec.Single(choice(PublicRole.entries.map { it.tagValue }, "role"))),
            FieldSpec("group", "voir.field.group", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
            FieldSpec("territory", "voir.field.territory", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
            // « Députée | 1re circonscription d'Exempleville | 2024-07-18 | »: the end is empty while the mandate runs.
            FieldSpec(
                "mandate", "voir.field.mandate", "voir.help.mandate", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("title", "voir.mandate.title", FieldType.Text(200)),
                        Position("territory", "voir.mandate.territory", FieldType.Text(200), required = false),
                        Position("start", "voir.mandate.start", FieldType.Date(), required = false),
                        Position("end", "voir.mandate.end", FieldType.Date(), required = false),
                    ),
                ),
            ),
            FieldSpec("party", "voir.field.party", occurs = Occurs.OPTIONAL, value = ValueSpec.Link(VoirTags.ROLE_PARTY, TargetType.PARTY)),
            // Its identifier in the open data of the assemblies, as ParliamentLinks.memberId reads it.
            FieldSpec("official-id", "voir.field.official_id", "voir.help.official_id_person", occurs = Occurs.MANY(), value = ValueSpec.OfficialId("^(an|senat|pe):[A-Za-z0-9]{1,32}$"), tag = ParliamentFormat.TAG_OFFICIAL_ID),
            source(min = 1),
            correction,
        ),
        body = body,
        rules = listOf(CrossRule.UniqueBy("role", emptyList(), Rules.DUPLICATE)),
        checks = listOf(
            readable(PublicPerson::from) {
                counts("mandate" to it.mandates.size, "source" to it.sources.size, "correction" to it.corrections.size)
            },
        ),
    )

    val TEXTE: ContentSchema = ContentSchema(
        type = ArticleType.TEXTE,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.texte",
        sourceDirectory = "voir/textes",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("text-kind", "voir.field.text_kind", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(listOf("projet", "proposition"), "text_kind"))),
            FieldSpec("stage", "voir.field.stage", value = ValueSpec.Single(choice(TextStage.entries.map { it.tagValue }, "stage"))),
            FieldSpec(
                "stage-history", "voir.field.stage_history", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("stage", "voir.field.stage", choice(TextStage.entries.map { it.tagValue }, "stage")),
                        Position("date", "voir.stage_history.date", FieldType.Date()),
                    ),
                ),
            ),
            FieldSpec(
                "next-vote", "voir.field.next_vote", occurs = Occurs.OPTIONAL,
                value = ValueSpec.Positional(
                    listOf(
                        Position("date", "voir.next_vote.date", FieldType.Date()),
                        Position("label", "voir.next_vote.label", FieldType.Text(200), required = false),
                    ),
                ),
            ),
            FieldSpec("493", "voir.field.493", "voir.help.493", occurs = Occurs.MANY(), value = ValueSpec.Single(FieldType.Date())),
            FieldSpec(
                "impact", "voir.field.impact", "voir.help.impact", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("group", "voir.impact.group", FieldType.Text(200)),
                        Position("direction", "voir.impact.direction", choice(ImpactDirection.entries.map { it.tagValue }, "impact")),
                        Position("effect", "voir.impact.effect", FieldType.Text(1000)),
                        Position("source-name", "voir.source.name", FieldType.Text(300), required = false),
                        Position("source-url", "voir.source.url", FieldType.Url, required = false),
                    ),
                ),
            ),
            // Its legislative dossier in the open data of the Assemblée nationale, as ParliamentLinks.dossierId reads it.
            FieldSpec("official-id", "voir.field.official_id", "voir.help.official_id_text", occurs = Occurs.MANY(), value = ValueSpec.OfficialId("^an-dossier:[A-Za-z0-9]{1,64}$"), tag = ParliamentFormat.TAG_OFFICIAL_ID),
            source(min = 1),
            correction,
            next,
        ),
        body = body,
        rules = listOf(CrossRule.UniqueBy("493", emptyList(), Rules.DUPLICATE)),
        checks = listOf(
            nextSpaces,
            readable(LegislativeText::from) {
                counts(
                    "stage-history" to it.history.size, "next-vote" to listOfNotNull(it.nextVote).size, "493" to it.uses493.size,
                    "impact" to it.impacts.size, "source" to it.sources.size, "correction" to it.corrections.size,
                )
            },
        ),
    )

    val MOUVEMENT: ContentSchema = ContentSchema(
        type = ArticleType.MOUVEMENT,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.mouvement",
        sourceDirectory = "voir/monde",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("country", "voir.field.country", value = ValueSpec.Single(FieldType.Text(120))),
            // « 2026-01-15 | »: the end stays empty while the movement goes on (the tag keeps it, as before).
            FieldSpec(
                "period", "voir.field.period", occurs = Occurs.OPTIONAL,
                value = ValueSpec.Positional(
                    listOf(
                        Position("start", "voir.period.start", FieldType.Date()),
                        Position("end", "voir.period.end", FieldType.Date(), required = false),
                    ),
                ),
            ),
            FieldSpec("claim", "voir.field.claim", occurs = Occurs.MANY(), value = ValueSpec.Single(FieldType.Text(300))),
            FieldSpec("result", "voir.field.result", occurs = Occurs.MANY(), value = ValueSpec.Single(FieldType.Text(300))),
            source(min = 1),
        ),
        body = body,
        checks = listOf(
            readable(WorldMovement::from) { counts("claim" to it.claims.size, "result" to it.results.size, "source" to it.sources.size) },
        ),
    )

    val DOSSIER: ContentSchema = ContentSchema(
        type = ArticleType.DOSSIER,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.dossier",
        sourceDirectory = "voir/dossiers",
        bundle = BUNDLE,
        // A dossier without its axis is not shown in Voir.
        fields = listOf(axis),
        body = body,
        checks = listOf(readable(VoirDossier::from)),
    )

    val REPONSE: ContentSchema = ContentSchema(
        type = ArticleType.REPONSE,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.reponse",
        sourceDirectory = "voir/reponses",
        bundle = BUNDLE,
        summary = Presence.OPTIONAL,
        fields = listOf(
            FieldSpec("respondent", "voir.field.respondent", "voir.help.respondent", value = ValueSpec.Single(FieldType.Text(200))),
            // The content it answers (LCEN, article 6, IV): any block of Voir.
            FieldSpec("target", "voir.field.target", value = ValueSpec.Link(VoirTags.ROLE_TARGET, TargetType.VOIR_BLOCK)),
        ),
        body = body,
        checks = listOf(readable(RightOfReply::from)),
    )

    val PARTI: ContentSchema = ContentSchema(
        type = ArticleType.PARTI,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.parti",
        sourceDirectory = "voir/partis",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("acronym", "voir.field.acronym", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(20))),
            FieldSpec("founded", "voir.field.founded", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date())),
            // Exactly one financing structure (loi n° 88-227, articles 11 to 11-2), the same official criterion for all.
            FieldSpec(
                "financing", "voir.field.financing", "voir.help.financing",
                value = ValueSpec.Positional(
                    listOf(
                        Position("kind", "voir.financing.kind", choice(FinancingKind.entries.map { it.tagValue }, "financing")),
                        Position("reference", "voir.financing.reference", FieldType.Text(200), required = false),
                        Position("since", "voir.financing.since", FieldType.Date(), required = false),
                        Position("source-kind", "voir.source.kind", sourceKinds, required = false),
                        Position("source-name", "voir.source.name", FieldType.Text(300), required = false),
                        Position("source-url", "voir.source.url", FieldType.Url, required = false),
                    ),
                ),
            ),
            // « 2026 | 4 210 345,60 | Décret de répartition (exemple) | https://… »: public aid exists since 1988.
            FieldSpec(
                "public-funding", "voir.field.public_funding", "voir.help.public_funding", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("year", "voir.public_funding.year", FieldType.Number(min = 1988.0, max = 2100.0)),
                        Position("amount", "voir.public_funding.amount", FieldType.Number(decimals = true)),
                        Position("decree", "voir.public_funding.decree", FieldType.Text(300)),
                        Position("url", "voir.public_funding.url", FieldType.Url),
                    ),
                ),
            ),
            FieldSpec("european-party", "voir.field.european_party", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
            FieldSpec("european-group", "voir.field.european_group", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(200))),
            // « assemblee | 120 | 2026-10-01 | parlement | Assemblée nationale (exemple) | https://… »: one count, one source.
            FieldSpec(
                "seats", "voir.field.seats", "voir.help.seats", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("chamber", "voir.seats.chamber", choice(Chamber.entries.map { it.tagValue }, "chamber")),
                        Position("count", "voir.seats.count", FieldType.Number(max = 999.0)),
                        Position("date", "voir.seats.date", FieldType.Date()),
                        Position("source-kind", "voir.source.kind", sourceKinds),
                        Position("source-name", "voir.source.name", FieldType.Text(300)),
                        Position("source-url", "voir.source.url", FieldType.Url),
                    ),
                ),
            ),
            FieldSpec("leader", "voir.field.leader", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_LEADER, TargetType.PERSON)),
            FieldSpec("website", "voir.field.website", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Url)),
            source(min = 1),
            correction,
            next,
        ),
        body = body,
        rules = listOf(
            // The name of a financial agent is a private person's: never written.
            CrossRule.PositionEmptyWhen("financing", position = "reference", whenPosition = "kind", values = setOf(FinancingKind.AGENT.tagValue), messageKey = Rules.NO_AGENT_NAME),
            CrossRule.UniquePosition("public-funding", position = "year", messageKey = Rules.PUBLIC_FUNDING_YEAR_ONCE),
        ),
        checks = listOf(
            nextSpaces,
            readable(PoliticalParty::from) {
                counts(
                    "public-funding" to it.publicFunding.size, "seats" to it.seats.size, "website" to listOfNotNull(it.website).size,
                    "source" to it.sources.size, "correction" to it.corrections.size,
                )
            },
        ),
    )

    /**
     * « Transparence de PPF » (PP-01, D-104): an instance, a decision, the accounts of a year or a report of
     * moderation and claims. What each rubric cannot do without (the instance, the day of a decision, the year
     * of the accounts, the start of a report) is checked by the reader.
     */
    val TRANSPARENCE: ContentSchema = ContentSchema(
        type = ArticleType.TRANSPARENCE,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.transparence",
        sourceDirectory = "voir/transparence",
        bundle = BUNDLE,
        summary = Presence.OPTIONAL,
        fields = listOf(
            FieldSpec("rubric", "voir.field.rubric", value = ValueSpec.Single(choice(TransparencyRubric.entries.map { it.tagValue }, "rubric"))),
            FieldSpec("instance", "voir.field.instance", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(choice(PpfInstance.entries.map { it.tagValue }, "instance"))),
            // « Trésorière | | 2027-03-01 | élue par l'assemblée générale »: the name stays empty unless the person agreed in writing.
            FieldSpec(
                "seat", "voir.field.seat", "voir.help.seat", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("function", "voir.seat.function", FieldType.Text(120)),
                        Position("name", "voir.seat.name", FieldType.Text(120), required = false),
                        Position("since", "voir.seat.since", FieldType.Date(), required = false),
                        Position("designation", "voir.seat.designation", FieldType.Text(200), required = false),
                    ),
                ),
            ),
            FieldSpec("date", "voir.field.decision_date", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date())),
            FieldSpec(
                "tally", "voir.field.tally", occurs = Occurs.OPTIONAL,
                value = ValueSpec.Positional(
                    listOf(
                        Position("for", "voir.tally.for", FieldType.Number(min = 0.0)),
                        Position("against", "voir.tally.against", FieldType.Number(min = 0.0)),
                        Position("abstention", "voir.tally.abstention", FieldType.Number(min = 0.0)),
                    ),
                ),
            ),
            FieldSpec("year", "voir.field.year", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Number(min = 2026.0, max = 2100.0))),
            // « produit | Cotisations | 12 340,00 »: the result is computed, never typed.
            FieldSpec(
                "account-line", "voir.field.account_line", "voir.help.account_line", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("side", "voir.account_line.side", choice(AccountSide.entries.map { it.tagValue }, "account_side")),
                        Position("category", "voir.account_line.category", FieldType.Text(200)),
                        Position("amount", "voir.account_line.amount", FieldType.Number(decimals = true, min = 0.0)),
                    ),
                ),
            ),
            FieldSpec("approved", "voir.field.approved", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Date())),
            FieldSpec(
                "period", "voir.field.report_period", occurs = Occurs.OPTIONAL,
                value = ValueSpec.Positional(
                    listOf(
                        Position("start", "voir.period.start", FieldType.Date()),
                        Position("end", "voir.period.end", FieldType.Date(), required = false),
                    ),
                ),
            ),
            // « signalements-recus | 120 »: a fixed list of codes (ReportFigure), a code absent is « not measured ».
            FieldSpec(
                "figure", "voir.field.figure", "voir.help.figure", occurs = Occurs.MANY(),
                value = ValueSpec.Positional(
                    listOf(
                        Position("code", "voir.figure.code", choice(ReportFigure.entries.map { it.tagValue }, "figure")),
                        Position("value", "voir.figure.value", FieldType.Number(decimals = true, min = 0.0)),
                    ),
                ),
            ),
            archive,
            source(min = 0),
            correction,
        ),
        body = body,
        rules = listOf(
            CrossRule.UniquePosition("figure", position = "code", messageKey = Rules.DUPLICATE),
        ),
        checks = listOf(
            readable(TransparencyEntry::from) {
                counts(
                    "seat" to it.seats.size, "account-line" to it.lines.size, "figure" to it.figures.size,
                    "archive" to it.archives.size, "source" to it.sources.size, "correction" to it.corrections.size,
                )
            },
        ),
    )

    /**
     * « Nos victoires » (PP-03): what was obtained, the day, at least one source, and the blocks of Voir it concerns
     * (a promise kept, a law changed).
     */
    val VICTOIRE: ContentSchema = ContentSchema(
        type = ArticleType.VICTOIRE,
        space = EditorialSpace.VOIR,
        labelKey = "editorial.type.victoire",
        sourceDirectory = "voir/victoires",
        bundle = BUNDLE,
        fields = listOf(
            FieldSpec("victory", "voir.field.victory", value = ValueSpec.Single(choice(VictoryKind.entries.map { it.tagValue }, "victory"))),
            FieldSpec("date", "voir.field.victory_date", value = ValueSpec.Single(FieldType.Date())),
            FieldSpec("related", "voir.field.related", "voir.help.related", occurs = Occurs.MANY(), value = ValueSpec.Link(VoirTags.ROLE_RELATED, TargetType.VOIR_BLOCK)),
            archive,
            source(min = 1),
            correction,
            next,
        ),
        body = body,
        checks = listOf(
            nextSpaces,
            readable(Victory::from) {
                counts(
                    "related" to it.related.size, "archive" to it.archives.size, "source" to it.sources.size,
                    "correction" to it.corrections.size, "next" to it.nextSteps.size,
                )
            },
        ),
    )

    /** Every type of [VoirCatalog.TYPES], in its order. */
    val all: List<ContentSchema> = listOf(INDICATEUR, FAIT, PERSONNE, TEXTE, MOUVEMENT, DOSSIER, REPONSE, PARTI, TRANSPARENCE, VICTOIRE)
}
