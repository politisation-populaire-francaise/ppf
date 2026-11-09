package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.Codes
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialMessages
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialSchemas
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The teaching sheets for the classroom (CO-13, D-096) and the workshops (CO-24): the schema, its rules and the reader. */
class TeachingSheetTest {
    private val author = IdentityKeys.generate().publicKey
    private val context = CheckContext.of(
        author.hex,
        mapOf(
            "le-referendum" to ArticleType.PARCOURS,
            "garde-a-vue" to ArticleType.FICHE_PRATIQUE,
        ),
    )
    private val schema = ComprendreSchemas.FICHE_PEDAGOGIQUE
    private val programme = "[EXEMPLE] Enseignement moral et civique, classe terminale, Bulletin officiel n° 0 du 1er janvier 2026 ; vérifié le 2026-11-08."

    private fun entries(vararg replaced: Pair<String, String?>): List<Pair<String, String>> {
        val base = listOf(
            "slug" to "classe-le-referendum-lycee",
            "title" to "[EXEMPLE] Pour la classe : le référendum",
            "summary" to "Une séance de 55 minutes.",
            "topics" to "institutions",
            "type" to "fiche-pedagogique",
            "origin" to "ai-assisted",
            "duration" to "55",
            "course" to "le-referendum",
            "level" to "lycee",
            "programme" to programme,
            "published_at" to "2026-11-08",
            "updated_at" to "2026-11-08",
        )
        val changes = replaced.toMap()
        return base.mapNotNull { (key, value) -> if (key in changes) changes[key]?.let { key to it } else key to value }
    }

    private val outline = listOf(
        "- 10 min : Lecture du parcours, projeté.",
        "  - un détail indenté sous l'étape, qui n'est pas une étape",
        "- 15 min : Questions à main levée.",
        "- 25 min : Débat guidé.",
        "- 5 min : Bilan.",
    )

    private val debate = listOf(
        "Question posée à la classe : faut-il plus de référendums ?",
        "",
        "::: objections",
        "? « Un argument d'un côté »",
        "> La réponse, avec sa source.",
        "? « Un argument de l'autre côté »",
        "> La réponse, avec sa source.",
        ":::",
    )

    private fun body(
        objectives: List<String> = listOf("- Comprendre le référendum."),
        prerequisites: List<String> = listOf("- Aucun."),
        steps: List<String> = outline,
        guided: List<String> = debate,
        further: List<String>? = listOf("- Lire la Constitution."),
        order: List<String> = TeachingSheetFormat.SECTIONS,
        extra: String = "",
    ): String {
        val sections = mapOf(
            TeachingSheetFormat.OBJECTIVES to objectives,
            TeachingSheetFormat.PREREQUISITES to prerequisites,
            TeachingSheetFormat.OUTLINE to steps,
            TeachingSheetFormat.DEBATE to guided,
            TeachingSheetFormat.FURTHER to further,
            TeachingSheetFormat.SOURCES to listOf("- Constitution, article 11."),
        )
        val text = order.mapNotNull { title -> sections[title]?.let { lines -> "## $title\n\n" + lines.joinToString("\n") + "\n" } }
        return "**[EXEMPLE] Brouillon rédigé avec l'aide d'une IA.**\n\n" + extra + text.joinToString("\n")
    }

    private fun check(entries: List<Pair<String, String>>, body: String) =
        EditorialEngine.check(schema, ContentDraft(ArticleType.FICHE_PEDAGOGIQUE, entries, body), context)

    private fun codes(entries: List<Pair<String, String>>, body: String) = check(entries, body).map { it.code }

    private fun signed(entries: List<Pair<String, String>>, body: String): SignedEvent {
        val unsigned = EditorialEngine.build(schema, ContentDraft(ArticleType.FICHE_PEDAGOGIQUE, entries, body), author)
        return SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
    }

    @Test
    fun theSchemaIsTheOneOfItsType() {
        assertEquals(schema, EditorialSchemas.of(ArticleType.FICHE_PEDAGOGIQUE))
        assertEquals("enseignants", schema.sourceDirectory)
        assertEquals("comprendre.json", schema.bundle)
        assertEquals(ComprendreSchemas.all.last(), schema, "written last in comprendre.json")
        assertEquals(ArticleType.FICHE_PEDAGOGIQUE, ArticleType.fromTag("fiche-pedagogique"))
        assertEquals("/comprendre/enseignants/classe-le-referendum-lycee", ComprendreLinks.pathOf(ArticleType.FICHE_PEDAGOGIQUE, "classe-le-referendum-lycee"))
    }

    @Test
    fun aValidSheetGivesItsTags() {
        assertEquals(emptyList(), check(entries(), body()))
        val event = signed(entries(), body())
        assertTrue(listOf("ppf-type", "fiche-pedagogique") in event.tags)
        assertTrue(listOf("ppf-duration", "55") in event.tags)
        assertEquals(
            listOf(
                listOf("a", "30023:${author.hex}:le-referendum", "", "parcours"),
                listOf("ppf-level", "lycee"),
                listOf("ppf-programme", programme),
            ),
            event.tags.drop(8),
        )
        // Without the optional « Pour aller plus loin ».
        assertEquals(emptyList(), check(entries(), body(further = null)))
    }

    @Test
    fun theReaderGivesTheCourseTheLevelTheProgrammeTheSectionsAndTheObjections() {
        val sheet = assertNotNull(Article.from(signed(entries(), body()))?.let(TeachingSheet::from))
        assertEquals("30023:${author.hex}:le-referendum", sheet.courseAddress)
        assertEquals("le-referendum", sheet.courseSlug)
        assertEquals(TeachingLevel.LYCEE, sheet.level)
        assertEquals(programme, sheet.programme)
        assertTrue(sheet.introduction.startsWith("**[EXEMPLE]"))
        assertEquals("- Comprendre le référendum.", sheet.objectives)
        assertEquals("- Aucun.", sheet.prerequisites)
        assertEquals(listOf(10, 15, 25, 5), sheet.steps.map { it.minutes })
        assertEquals("Lecture du parcours, projeté.", sheet.steps.first().text)
        assertEquals(55, sheet.totalMinutes)
        assertEquals(2, sheet.objections.size)
        assertEquals("« Un argument d'un côté »", sheet.objections.first().objection)
        assertEquals("Question posée à la classe : faut-il plus de référendums ?", sheet.debate)
        assertEquals("- Lire la Constitution.", sheet.further)
        assertEquals("- Constitution, article 11.", sheet.sources)
        assertEquals(55, sheet.article.durationMinutes)
        // Another type is never read as a sheet.
        assertNull(TeachingSheet.from(Article.from(signed(entries(), body()))!!.copy(type = ArticleType.PARCOURS)))
    }

    @Test
    fun aSheetWithoutCourseOrWithAnUnknownOrWrongCourseIsRefused() {
        assertEquals(listOf(Codes.FIELD_MISSING), codes(entries("course" to null), body()))
        assertEquals("course", check(entries("course" to null), body()).single().field)
        assertEquals(listOf(Codes.LINK_UNKNOWN), codes(entries("course" to "parcours-inconnu"), body()))
        assertEquals(listOf(Codes.LINK_WRONG_TYPE), codes(entries("course" to "garde-a-vue"), body()))
        assertEquals(listOf(Codes.FIELD_REPEATED), codes(entries() + ("course" to "le-referendum"), body()))
    }

    @Test
    fun aSheetWithoutProgrammeOrLevelOrDurationIsRefused() {
        // A sheet of school cites its programme (one problem, on the field: the reader check stays quiet).
        val missing = check(entries("programme" to null), body())
        assertEquals(listOf(TeachingSheetFormat.RULE_PROGRAMME_REQUIRED), missing.map { it.code })
        assertEquals("programme", missing.single().field)
        assertEquals("Champ « programme » : une fiche pour la classe (niveau lycee) cite le programme officiel qu'elle sert", EditorialMessages.french(missing.single()))
        assertEquals(listOf(Codes.VALUE_TOO_LONG), codes(entries("programme" to "x".repeat(501)), body()))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(entries("level" to null), body()))
        assertEquals(listOf(Codes.VALUE_CHOICE), codes(entries("level" to "college"), body()))
        assertEquals(emptyList(), codes(entries("level" to "cycle-4"), body()))
        assertEquals(listOf(Codes.FIELD_MISSING), codes(entries("duration" to null), body()))
    }

    /** A workshop of popular education (CO-24): 90 minutes, no programme. */
    private fun workshop(vararg replaced: Pair<String, String?>): List<Pair<String, String>> {
        val base = arrayOf<Pair<String, String?>>(
            "slug" to "atelier-le-referendum",
            "title" to "[EXEMPLE] Pour un atelier : le référendum",
            "duration" to "90",
            "level" to "atelier",
            "programme" to null,
        )
        return entries(*base, *replaced)
    }

    private val workshopOutline = listOf(
        "- 10 min : Accueil.",
        "- 20 min : Lecture du parcours par petits groupes.",
        "- 15 min : Questions à main levée, avec les cartes.",
        "- 30 min : Débat guidé.",
        "- 15 min : Bilan.",
    )

    @Test
    fun aWorkshopHasNoProgrammeAndLastsFortyFiveMinutesToTwoHours() {
        assertEquals(emptyList(), check(workshop(), body(steps = workshopOutline)))
        val event = signed(workshop(), body(steps = workshopOutline))
        assertTrue(listOf("ppf-level", "atelier") in event.tags)
        assertTrue(event.tags.none { it.firstOrNull() == "ppf-programme" }, "no programme tag")
        val sheet = assertNotNull(Article.from(event)?.let(TeachingSheet::from))
        assertEquals(TeachingLevel.ATELIER, sheet.level)
        assertTrue(sheet.isWorkshop)
        assertTrue(!sheet.level.isSchool)
        assertNull(sheet.programme)
        assertEquals(90, sheet.totalMinutes)
        // A workshop serves no official programme.
        assertEquals(listOf(TeachingSheetFormat.RULE_PROGRAMME_WORKSHOP), codes(workshop("programme" to programme), body(steps = workshopOutline)))
        // 45 minutes to 2 hours, the timed steps adding up to the duration as for the classroom.
        val short = listOf("- 10 min : Accueil.", "- 20 min : Débat.")
        val tooShort = check(workshop("duration" to "30"), body(steps = short)).single()
        assertEquals(TeachingSheetFormat.RULE_WORKSHOP_DURATION, tooShort.code)
        assertEquals(listOf("45", "120", "30"), tooShort.args)
        assertEquals("Champ « duration » : un atelier dure de 45 à 120 minutes, pas 30", EditorialMessages.french(tooShort))
        val long = workshopOutline + "- 60 min : Deuxième débat."
        assertEquals(listOf(TeachingSheetFormat.RULE_WORKSHOP_DURATION), codes(workshop("duration" to "150"), body(steps = long)))
        val exactlyTwoHours = workshopOutline + "- 30 min : Mise en pratique."
        assertEquals(emptyList(), codes(workshop("duration" to "120"), body(steps = exactlyTwoHours)))
        // A stray programme tag on a workshop is never shown.
        val stray = signed(workshop("programme" to programme), body(steps = workshopOutline))
        assertNull(assertNotNull(Article.from(stray)?.let(TeachingSheet::from)).programme)
        // The level of school keeps its rules: no limit of duration but the sum of its steps.
        assertEquals(TeachingLevel.entries.last(), TeachingLevel.ATELIER, "new levels go at the end")
        assertTrue(TeachingLevel.LYCEE.isSchool && TeachingLevel.CYCLE_4.isSchool)
    }

    @Test
    fun everyRequiredSectionIsChecked() {
        for (missing in TeachingSheetFormat.REQUIRED_SECTIONS) {
            val problems = check(entries(), body(order = TeachingSheetFormat.SECTIONS - missing))
            // Without « Débat guidé », its objections block is missing too.
            assertTrue(problems.any { it.code == Codes.BODY_SECTION_MISSING && it.args == listOf(missing) }, "$missing: $problems")
        }
        assertEquals(listOf(Codes.BODY_SECTION_MISSING), codes(entries(), body(order = TeachingSheetFormat.SECTIONS - TeachingSheetFormat.PREREQUISITES)))
    }

    @Test
    fun theSectionsComeInTheirOrderOnceEachAndNeverEmpty() {
        val swapped = listOf(TeachingSheetFormat.PREREQUISITES, TeachingSheetFormat.OBJECTIVES) + TeachingSheetFormat.SECTIONS.drop(2)
        assertEquals(listOf(TeachingSheetFormat.RULE_SECTION_ORDER), codes(entries(), body(order = swapped)))
        val furtherLast = TeachingSheetFormat.SECTIONS - TeachingSheetFormat.FURTHER + TeachingSheetFormat.FURTHER
        assertEquals(listOf(TeachingSheetFormat.RULE_SECTION_ORDER), codes(entries(), body(order = furtherLast)))
        assertEquals(listOf(TeachingSheetFormat.RULE_SECTION_UNKNOWN), codes(entries(), body() + "\n## Annexe\n\nTexte.\n"))
        assertEquals(listOf(TeachingSheetFormat.RULE_SECTION_REPEATED), codes(entries(), body() + "\n## Sources\n\n- Une autre source.\n"))
        assertEquals(listOf(TeachingSheetFormat.RULE_SECTION_EMPTY), codes(entries(), body(prerequisites = emptyList())))
        // A level-3 heading is not a section.
        assertEquals(emptyList(), codes(entries(), body(objectives = listOf("### Savoirs", "", "- Comprendre le référendum."))))
    }

    @Test
    fun theDebatePresentsAtLeastTwoObjections() {
        val one = listOf("::: objections", "? « Un seul côté »", "> Une réponse.", ":::")
        assertEquals(listOf(TeachingSheetFormat.RULE_OBJECTIONS), codes(entries(), body(guided = one)))
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_FEW), codes(entries(), body(guided = listOf("Un débat sans objections."))))
        assertEquals(listOf(Codes.BODY_INVALID), codes(entries(), body(guided = debate + listOf("", "::: objections", "? Sans réponse", ":::"))))
        // Two objections outside the debate do not count for it.
        val elsewhere = body(guided = listOf("Un débat."), objectives = listOf("- Comprendre.") + debate.drop(2))
        assertEquals(listOf(TeachingSheetFormat.RULE_OBJECTIONS), codes(entries(), elsewhere))
    }

    @Test
    fun theTimedStepsAddUpToTheDuration() {
        assertEquals(listOf(TeachingSheetFormat.RULE_DURATION), codes(entries("duration" to "50"), body()))
        val short = outline.dropLast(1)
        val problem = check(entries(), body(steps = short)).single()
        assertEquals(TeachingSheetFormat.RULE_DURATION, problem.code)
        assertEquals(listOf("55", "50"), problem.args)
        assertEquals("Texte : les étapes du déroulé font 50 minutes en tout, la durée annoncée est de 55 minutes", EditorialMessages.french(problem))
        assertEquals(listOf(TeachingSheetFormat.RULE_STEP), codes(entries(), body(steps = outline + "- Bilan sans durée")))
        assertEquals(listOf(TeachingSheetFormat.RULE_STEP), codes(entries(), body(steps = outline + "- 0 min : Rien.")))
        assertEquals(listOf(TeachingSheetFormat.RULE_NO_STEP), codes(entries(), body(steps = listOf("Une séance sans étapes."))))
        // A no-break space before the colon, as French typography writes it, is accepted.
        assertEquals(emptyList(), codes(entries(), body(steps = outline.dropLast(1) + "- 5 min : Bilan.")))
    }

    @Test
    fun aSheetNeverWritesItsOwnQuiz() {
        val quiz = "::: quiz\n? Une question ?\n- [x] Oui\n- [ ] Non\n> Parce que.\n:::\n\n"
        assertEquals(listOf(Codes.BODY_BLOCK_TOO_MANY), codes(entries(), body(extra = quiz)))
    }

    @Test
    fun everyMessageOfTheRulesIsInFrench() {
        listOf(
            TeachingSheetFormat.RULE_SECTION_UNKNOWN, TeachingSheetFormat.RULE_SECTION_REPEATED, TeachingSheetFormat.RULE_SECTION_ORDER,
            TeachingSheetFormat.RULE_SECTION_EMPTY, TeachingSheetFormat.RULE_OBJECTIONS, TeachingSheetFormat.RULE_STEP,
            TeachingSheetFormat.RULE_NO_STEP, TeachingSheetFormat.RULE_DURATION, TeachingSheetFormat.RULE_READABLE,
            TeachingSheetFormat.RULE_PROGRAMME_REQUIRED, TeachingSheetFormat.RULE_PROGRAMME_WORKSHOP, TeachingSheetFormat.RULE_WORKSHOP_DURATION,
        ).forEach { code ->
            val template = assertNotNull(EditorialMessages.template(code), code)
            assertTrue('—' !in template, "$code: no em dash")
        }
    }

    @Test
    fun aPublishedSheetReadsBackAsItsDraft() {
        val event = signed(entries(), body())
        val back = EditorialEngine.draftOf(schema, event, context)
        assertEquals(
            listOf("course" to "le-referendum", "level" to "lycee", "programme" to programme),
            back.entries.filter { it.first in setOf("course", "level", "programme") },
        )
        val unsigned = EditorialEngine.build(schema, ContentDraft(ArticleType.FICHE_PEDAGOGIQUE, entries(), body()), author)
        assertEquals(unsigned, EditorialEngine.build(schema, back, author))
    }
}
