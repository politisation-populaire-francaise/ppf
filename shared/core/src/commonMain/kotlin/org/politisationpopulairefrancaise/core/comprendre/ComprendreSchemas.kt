package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.BlockParser
import org.politisationpopulairefrancaise.core.editorial.schema.BlockSpec
import org.politisationpopulairefrancaise.core.editorial.schema.BodySpec
import org.politisationpopulairefrancaise.core.editorial.schema.ContentSchema
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSpace
import org.politisationpopulairefrancaise.core.editorial.schema.FieldSpec
import org.politisationpopulairefrancaise.core.editorial.schema.FieldType
import org.politisationpopulairefrancaise.core.editorial.schema.Occurs
import org.politisationpopulairefrancaise.core.editorial.schema.Position
import org.politisationpopulairefrancaise.core.editorial.schema.Presence
import org.politisationpopulairefrancaise.core.editorial.schema.Problem
import org.politisationpopulairefrancaise.core.editorial.schema.SchemaCheck
import org.politisationpopulairefrancaise.core.editorial.schema.TargetType
import org.politisationpopulairefrancaise.core.editorial.schema.ValueSpec
import org.politisationpopulairefrancaise.core.signing.SignedEvent

/**
 * The schemas of the contents of "Comprendre" (D-092): practical guides, courses, fact-checks, stories,
 * « Qui décide de ça ? » sheets, the method page and the glossary. They state the rules tools/content-signer
 * applied by hand until 7 November 2026 (the no-regression test of the tool keeps the events identical);
 * the server's form and checks will use the same schemas.
 */
object ComprendreSchemas {
    private val objections = BlockParser { ContentBlocks.parseObjections(it).second }
    private val timeline = BlockParser { ContentBlocks.parseTimeline(it).second }

    /** The blocks every content of Comprendre may have, checked by their readers (ContentBlocks). */
    private fun commonBlocks(objectionsMin: Int = 0) = listOf(
        BlockSpec(ContentBlocks.OBJECTIONS, min = objectionsMin, parser = objections),
        BlockSpec(ContentBlocks.TIMELINE, parser = timeline),
    )

    private fun readerCheck(code: String, problems: (String) -> List<String>) = SchemaCheck { draft, _, _ ->
        problems(draft.body).map { Problem(code, EditorialEngine.BODY, args = listOf(it)) }
    }

    val FICHE_PRATIQUE: ContentSchema = ContentSchema(
        type = ArticleType.FICHE_PRATIQUE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.fiche_pratique",
        sourceDirectory = "fiches",
        bundle = "fiches.json",
        fields = emptyList(),
        body = BodySpec(blocks = commonBlocks(), templateKey = "editorial.template.fiche_pratique"),
    )

    val PARCOURS: ContentSchema = ContentSchema(
        type = ArticleType.PARCOURS,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.parcours",
        sourceDirectory = "parcours",
        bundle = "parcours.json",
        duration = Presence.REQUIRED,
        // « rhythm: quotidien »: one lesson a day (CO-19 « Je découvre »); absent for the other courses.
        fields = listOf(
            FieldSpec("rhythm", "editorial.field.rhythm", "editorial.help.rhythm", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(CourseRhythm.entries.map { it.tagValue }, "comprendre.rhythm.")), tag = ComprendreTags.RHYTHM),
        ),
        body = BodySpec(
            blocks = listOf(
                BlockSpec(ParcoursFormat.QUIZ_BLOCK, min = 1),
                BlockSpec(ParcoursFormat.NEXT_ACTIONS_BLOCK, min = 1, max = 1),
            ),
            templateKey = "editorial.template.parcours",
        ),
        // Steps, one quiz each, « Et maintenant ? », sources, objections: as the application reads the course.
        checks = listOf(readerCheck("comprendre.rule.course_readable") { ParcoursFormat.parse(it).problems }),
    )

    val DECRYPTAGE: ContentSchema = ContentSchema(
        type = ArticleType.DECRYPTAGE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.decryptage",
        sourceDirectory = "decryptages",
        bundle = "comprendre.json",
        fields = listOf(
            FieldSpec("verdict", "editorial.field.verdict", "editorial.help.verdict", value = ValueSpec.Single(FieldType.Choice(Verdict.entries.map { it.tagValue }, "comprendre.verdict.")), tag = ComprendreTags.VERDICT),
            FieldSpec("claim", "editorial.field.claim", "editorial.help.claim", value = ValueSpec.Single(FieldType.Text(1000)), tag = ComprendreTags.CLAIM),
            FieldSpec("claim_context", "editorial.field.claim_context", "editorial.help.claim_context", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(300)), tag = ComprendreTags.CLAIM_CONTEXT),
        ),
        // Pluralism: a fact-check presents the objections and answers them.
        body = BodySpec(blocks = commonBlocks(objectionsMin = 1), templateKey = "editorial.template.decryptage"),
    )

    val HISTOIRE: ContentSchema = ContentSchema(
        type = ArticleType.HISTOIRE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.histoire",
        sourceDirectory = "histoire",
        bundle = "comprendre.json",
        fields = listOf(
            // « 1936 | 1938 »: the start is required, the end may be unknown; the tag drops the empty end.
            FieldSpec(
                "period", "editorial.field.period", "editorial.help.period",
                value = ValueSpec.Positional(
                    listOf(
                        Position("start", "editorial.period.start", FieldType.Date(yearOnly = true)),
                        Position("end", "editorial.period.end", FieldType.Date(yearOnly = true), required = false),
                    ),
                    keepEmpty = false,
                ),
                tag = ComprendreTags.PERIOD,
            ),
            FieldSpec("country", "editorial.field.country", value = ValueSpec.Single(FieldType.Text(120)), tag = ComprendreTags.COUNTRY),
            FieldSpec("claims", "editorial.field.claims", "editorial.help.claims", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(';', ValueSpec.Single(FieldType.Text(300)), joiner = " ; "), tag = ComprendreTags.CLAIM),
            FieldSpec("results", "editorial.field.results", "editorial.help.results", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(';', ValueSpec.Single(FieldType.Text(300)), joiner = " ; "), tag = ComprendreTags.RESULT),
        ),
        body = BodySpec(blocks = commonBlocks(objectionsMin = 1), templateKey = "editorial.template.histoire"),
    )

    val QUI_DECIDE: ContentSchema = ContentSchema(
        type = ArticleType.QUI_DECIDE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.qui_decide",
        sourceDirectory = "qui-decide",
        bundle = "comprendre.json",
        fields = listOf(
            // « persons: alex-modele, sacha-exemple »: one ["a", "30023:<key>:<slug>", "", "personne"] per slug.
            FieldSpec("persons", "editorial.field.persons", "editorial.help.persons", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(',', ValueSpec.Link(ComprendreTags.PERSON_LINK_ROLE, TargetType.PERSON))),
        ),
        body = BodySpec(
            blocks = listOf(
                BlockSpec(QuiDecide.BLOCK, min = 1, max = 1),
                BlockSpec(ParcoursFormat.NEXT_ACTIONS_BLOCK, min = 1),
            ) + commonBlocks(),
            templateKey = "editorial.template.qui_decide",
        ),
        checks = listOf(readerCheck("comprendre.rule.qui_decide_readable") { QuiDecide.parse(it).third }),
    )

    /** The method page (`content/methode`), an article. */
    val METHODE: ContentSchema = ContentSchema(
        type = ArticleType.ARTICLE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.methode",
        sourceDirectory = "methode",
        bundle = "comprendre.json",
        fields = emptyList(),
        body = BodySpec(blocks = commonBlocks()),
    )

    val GLOSSAIRE: ContentSchema = ContentSchema(
        type = ArticleType.GLOSSAIRE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.glossaire",
        sourceDirectory = "glossaire",
        bundle = "glossaire.json",
        fields = listOf(
            FieldSpec("variants", "editorial.field.variants", "editorial.help.variants", value = ValueSpec.InlineList(',', ValueSpec.Single(FieldType.Text(120)), escapable = true), tag = GlossaryEntry.TAG_VARIANT),
            // « auto: oui » is the default and writes no tag.
            FieldSpec("auto", "editorial.field.auto", "editorial.help.auto", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Choice(listOf("oui", GlossaryEntry.AUTO_NO), "editorial.auto.")), tag = GlossaryEntry.TAG_AUTO, silentValues = setOf("oui")),
            FieldSpec("see_also", "editorial.field.see_also", occurs = Occurs.OPTIONAL, value = ValueSpec.InlineList(',', ValueSpec.Link(GlossaryEntry.ROLE_SEE_ALSO, TargetType.GLOSSARY), escapable = true)),
            FieldSpec(
                "related", "editorial.field.related", "editorial.help.related", occurs = Occurs.OPTIONAL,
                value = ValueSpec.InlineList(
                    ',',
                    ValueSpec.Link(
                        GlossaryEntry.ROLE_RELATED,
                        TargetType.GUIDE,
                        kinds = mapOf(
                            "parcours" to TargetType.COURSE,
                            "fiche" to TargetType.GUIDE,
                            "decryptage" to TargetType.FACT_CHECK,
                            "qui-decide" to TargetType.QUI_DECIDE,
                            "histoire" to TargetType.STORY,
                        ),
                    ),
                    escapable = true,
                ),
            ),
        ),
        body = BodySpec(),
        checks = listOf(
            SchemaCheck { draft, _, _ ->
                val summary = draft.entries.lastOrNull { it.first == ContentSchema.SUMMARY }?.second?.trim().orEmpty()
                if (summary.length > GlossaryEntry.MAX_DEFINITION) {
                    listOf(Problem("comprendre.rule.glossary_definition", ContentSchema.SUMMARY, args = listOf(GlossaryEntry.MAX_DEFINITION.toString(), summary.length.toString())))
                } else {
                    emptyList()
                }
            },
            SchemaCheck { draft, _, _ ->
                val title = draft.entries.lastOrNull { it.first == ContentSchema.TITLE }?.second.orEmpty()
                if (title.trim().removePrefix(GlossaryEntry.EXAMPLE_MARKER).isBlank()) listOf(Problem("comprendre.rule.glossary_title", ContentSchema.TITLE)) else emptyList()
            },
            SchemaCheck { _, event, _ ->
                val unchecked = SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))
                if (Article.from(unchecked)?.let(GlossaryEntry::from) == null) listOf(Problem("comprendre.rule.glossary_readable")) else emptyList()
            },
        ),
    )

    /**
     * A teaching sheet for the classroom (CO-13, D-096, docs/integration/comprendre-enseignants.md): one course,
     * one level, the official programme it serves, and the sections of [TeachingSheetFormat]. The quiz is the
     * one of the linked course, never rewritten in the sheet. The same format, with the level `atelier`, is the
     * animation sheet of a workshop of popular education (CO-24): no programme, 45 minutes to 2 hours.
     */
    val FICHE_PEDAGOGIQUE: ContentSchema = ContentSchema(
        type = ArticleType.FICHE_PEDAGOGIQUE,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.fiche_pedagogique",
        sourceDirectory = "enseignants",
        bundle = "comprendre.json",
        duration = Presence.REQUIRED,
        fields = listOf(
            // « course: le-referendum »: one ["a", "30023:<key>:le-referendum", "", "parcours"], a published course.
            FieldSpec("course", "editorial.field.course", "editorial.help.course", value = ValueSpec.Link(ComprendreTags.COURSE_LINK_ROLE, TargetType.COURSE)),
            FieldSpec("level", "editorial.field.level", "editorial.help.level", value = ValueSpec.Single(FieldType.Choice(TeachingLevel.entries.map { it.tagValue }, "comprendre.level.")), tag = ComprendreTags.LEVEL),
            // Required for a level of school, absent for a workshop (CO-24): checked below with the level.
            FieldSpec("programme", "editorial.field.programme", "editorial.help.programme", occurs = Occurs.OPTIONAL, value = ValueSpec.Single(FieldType.Text(TeachingSheetFormat.PROGRAMME_MAX)), tag = ComprendreTags.PROGRAMME),
        ),
        body = BodySpec(
            blocks = listOf(
                BlockSpec(ContentBlocks.OBJECTIONS, min = 1, parser = objections),
                BlockSpec(ContentBlocks.TIMELINE, parser = timeline),
                // The questions « à main levée » are those of the course: a sheet never writes its own quiz.
                BlockSpec(ParcoursFormat.QUIZ_BLOCK, max = 0),
            ),
            requiredSections = TeachingSheetFormat.REQUIRED_SECTIONS,
            templateKey = "editorial.template.fiche_pedagogique",
        ),
        checks = listOf(
            // Order of the sections, timed steps adding up to the duration, two objections in the debate.
            SchemaCheck { draft, _, _ ->
                val duration = draft.entries.lastOrNull { it.first == ContentSchema.DURATION }?.second?.trim()?.toIntOrNull()
                TeachingSheetFormat.problems(draft.body, duration)
            },
            // A programme for a level of school, none for a workshop, which lasts 45 minutes to 2 hours (CO-24);
            // then, as a last safety net, the sheet must read as the applications read it.
            SchemaCheck { draft, event, _ ->
                fun value(key: String) = draft.entries.lastOrNull { it.first == key }?.second?.trim()
                val level = TeachingSheetFormat.levelProblems(value(TeachingSheetFormat.LEVEL_KEY), value(TeachingSheetFormat.PROGRAMME_KEY), value(ContentSchema.DURATION)?.toIntOrNull())
                if (level.isNotEmpty()) return@SchemaCheck level
                val unchecked = SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))
                if (Article.from(unchecked)?.let(TeachingSheet::from) == null) listOf(Problem(TeachingSheetFormat.RULE_READABLE)) else emptyList()
            },
        ),
    )

    /**
     * A text of the series « Le cap du mouvement » (`content/cap`, D-200): its nature (sourced facts or a position
     * of PPF, shown in the label of origin, D-212), its rank in the series, a « Et maintenant ? » block that links it
     * to an action of the application, and its sources.
     */
    val CAP: ContentSchema = ContentSchema(
        type = ArticleType.CAP,
        space = EditorialSpace.COMPRENDRE,
        labelKey = "editorial.type.cap",
        sourceDirectory = "cap",
        bundle = "comprendre.json",
        fields = listOf(
            FieldSpec("nature", "editorial.field.nature", "editorial.help.nature", value = ValueSpec.Single(FieldType.Choice(CapNature.entries.map { it.tagValue }, "comprendre.nature.")), tag = CapText.TAG_NATURE),
            FieldSpec("rank", "editorial.field.rank", "editorial.help.rank", value = ValueSpec.Single(FieldType.Number(min = 1.0, max = 99.0)), tag = CapText.TAG_RANK),
        ),
        body = BodySpec(
            blocks = listOf(BlockSpec(ParcoursFormat.NEXT_ACTIONS_BLOCK, min = 1, max = 1)) + commonBlocks(),
            templateKey = "editorial.template.cap",
        ),
        checks = listOf(
            SchemaCheck { _, event, _ ->
                val unchecked = SignedEvent(event.idHex(), event.pubkey, event.createdAt, event.kind, event.tags, event.content, "0".repeat(128))
                if (Article.from(unchecked)?.let(CapText::from) == null) listOf(Problem("comprendre.rule.cap_readable")) else emptyList()
            },
        ),
    )

    /**
     * In the order the tool writes them (comprendre.json: fact-checks, stories, sheets, method, then the
     * teaching sheets and the texts of the series « Le cap », written last so that the events before them keep
     * their place).
     */
    val all: List<ContentSchema> = listOf(FICHE_PRATIQUE, PARCOURS, DECRYPTAGE, HISTOIRE, QUI_DECIDE, METHODE, GLOSSAIRE, FICHE_PEDAGOGIQUE, CAP)
}
