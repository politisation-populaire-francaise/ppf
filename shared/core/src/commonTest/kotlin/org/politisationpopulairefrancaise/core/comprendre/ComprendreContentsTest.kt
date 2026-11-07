package org.politisationpopulairefrancaise.core.comprendre

import kotlinx.coroutines.test.runTest
import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.LocalEventSigner
import org.politisationpopulairefrancaise.core.voir.PublicRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentBlocksTest {

    @Test
    fun objectionsArePairsOfAnObjectionAndItsAnswer() {
        val (items, problems) = ContentBlocks.parseObjections(
            listOf(
                "? « Le vote blanc est compté. »",
                "> Il est décompté à part,",
                "> mais il n'entre pas dans les suffrages exprimés (Code électoral, article L65).",
                "",
                "? Seconde objection",
                "> Seconde réponse.",
            ),
        )
        assertEquals(emptyList(), problems)
        assertEquals(2, items.size)
        assertEquals("« Le vote blanc est compté. »", items[0].objection)
        assertEquals("Il est décompté à part, mais il n'entre pas dans les suffrages exprimés (Code électoral, article L65).", items[0].answer)
    }

    @Test
    fun aMalformedObjectionsBlockIsReported() {
        assertTrue(ContentBlocks.parseObjections(listOf("? Sans réponse")).second.any { "without an answer" in it })
        assertTrue(ContentBlocks.parseObjections(listOf("> Réponse orpheline")).second.any { "without an objection" in it })
        assertTrue(ContentBlocks.parseObjections(listOf("? Q", "> R", "texte libre")).second.any { "unexpected line" in it })
        assertTrue(ContentBlocks.parseObjections(emptyList()).second.isNotEmpty())
    }

    @Test
    fun aChronologyHasDatesInOrderWrittenInFrench() {
        val (entries, problems) = ContentBlocks.parseTimeline(listOf("- 1936 : Année", "- 1936-05 : Mois", "- 1936-06-07 : Accords de Matignon"))
        assertEquals(emptyList(), problems)
        assertEquals(listOf("1936", "mai 1936", "7 juin 1936"), entries.map { it.frenchDate })
        assertEquals("Accords de Matignon", entries[2].text)

        assertTrue(ContentBlocks.parseTimeline(listOf("- 1936-06-07 : B", "- 1936-05-03 : A")).second.any { "date order" in it })
        assertTrue(ContentBlocks.parseTimeline(listOf("- 7 juin 1936 : Mauvais format")).second.isNotEmpty())
        assertTrue(ContentBlocks.parseTimeline(listOf("- 1936-02-30 : Date impossible")).second.isNotEmpty())
    }

    @Test
    fun theProblemsOfABodyCoverEveryBlock() {
        val body = "Texte.\n\n::: objections\n? Q\n:::\n\n::: chronologie\n- 1848 : A\n:::\n"
        val problems = ContentBlocks.problems(body)
        assertEquals(1, problems.size, problems.toString())
        assertTrue(ContentBlocks.hasObjections("::: objections\n? Q\n> R\n:::"))
        assertTrue(!ContentBlocks.hasObjections(body))
    }
}

class ComprendreContentsTest {

    private val keys = IdentityKeys.generate()
    private val signer = LocalEventSigner(keys)

    private suspend fun article(type: ArticleType, body: String = "Corps.\n\n## Sources\n\n- S", tags: List<List<String>>): Article = Article.from(
        signer.sign(
            Article.build(
                author = keys.publicKey,
                slug = "exemple",
                title = "[EXEMPLE] Titre",
                summary = "Résumé.",
                topics = emptyList(),
                type = type,
                origin = ContentOrigin.AI_ASSISTED,
                publishedAt = 1_794_000_000,
                updatedAt = 1_794_000_000,
                body = body,
                extraTags = tags,
            ),
        ),
    )!!

    @Test
    fun aFactCheckNeedsAVerdictAndAClaim() = runTest {
        val check = assertNotNull(
            Decryptage.from(
                article(
                    ArticleType.DECRYPTAGE,
                    tags = listOf(
                        listOf(ComprendreTags.VERDICT, "contredit-par-les-faits"),
                        listOf(ComprendreTags.CLAIM, "Le vote blanc compte dans les suffrages exprimés."),
                        listOf(ComprendreTags.CLAIM_CONTEXT, "Réseaux sociaux, novembre 2026"),
                    ),
                ),
            ),
        )
        assertEquals(Verdict.CONTRADICTED, check.verdict)
        assertEquals("Réseaux sociaux, novembre 2026", check.claimContext)
        assertNull(Decryptage.from(article(ArticleType.DECRYPTAGE, tags = listOf(listOf(ComprendreTags.VERDICT, "faux"), listOf(ComprendreTags.CLAIM, "X")))))
        assertNull(Decryptage.from(article(ArticleType.DECRYPTAGE, tags = listOf(listOf(ComprendreTags.VERDICT, "exact")))))
        assertEquals(5, Verdict.entries.size)
    }

    @Test
    fun aStoryHasACountryAPeriodClaimsAndResults() = runTest {
        val story = assertNotNull(
            HistoryStory.from(
                article(
                    ArticleType.HISTOIRE,
                    tags = listOf(
                        listOf(ComprendreTags.PERIOD, "1936", "1938"),
                        listOf(ComprendreTags.COUNTRY, "France"),
                        listOf(ComprendreTags.CLAIM, "congés payés"),
                        listOf(ComprendreTags.RESULT, "deux semaines de congés payés"),
                    ),
                ),
            ),
        )
        assertEquals("1936-1938", story.period)
        assertEquals(1936, story.startYear)
        assertEquals(listOf("congés payés"), story.claims)
        assertEquals(listOf("deux semaines de congés payés"), story.results)

        val oneDay = assertNotNull(HistoryStory.from(article(ArticleType.HISTOIRE, tags = listOf(listOf(ComprendreTags.PERIOD, "1848-04-27"), listOf(ComprendreTags.COUNTRY, "France")))))
        assertEquals("27 avril 1848", oneDay.period)
        assertNull(HistoryStory.from(article(ArticleType.HISTOIRE, tags = listOf(listOf(ComprendreTags.COUNTRY, "France")))), "a story needs its period")
    }

    private val sheetBody = """
        ::: decideurs
        - commune | maire, elu-local | Construit et entretient les écoles (Code de l'éducation, article L212-4).
        - region | elu-local | Construit et entretient les lycées.
        - etat | ministre, depute, senateur | Fixe les programmes et recrute les enseignants.
        :::

        ::: en-bref
        - Trois niveaux.
        :::

        ## Le rôle de la commune

        Texte.

        ::: et-maintenant
        - [Proposer une revendication](ppf:revendications)
        :::

        ## Sources

        - Code de l'éducation
    """.trimIndent()

    @Test
    fun aWhoDecidesSheetReadsItsChainAndItsLinkedPersons() = runTest {
        val person = "30023:${keys.publicKey.hex}:dominique-fictif"
        val sheet = assertNotNull(QuiDecide.from(article(ArticleType.QUI_DECIDE, sheetBody, listOf(listOf("a", person, "", ComprendreTags.PERSON_LINK_ROLE)))))
        assertEquals(listOf(DecisionLevel.COMMUNE, DecisionLevel.REGION, DecisionLevel.ETAT), sheet.steps.map { it.level })
        assertEquals(listOf(PublicRole.MINISTER, PublicRole.DEPUTY, PublicRole.SENATOR), sheet.steps[2].roles)
        assertTrue(sheet.steps[0].description.startsWith("Construit et entretient les écoles"))
        assertEquals(listOf(person), sheet.persons)
        assertTrue("::: decideurs" !in sheet.body, "the chain is shown in the box, not in the text")
        assertTrue(sheet.body.startsWith("::: en-bref"))
        assertEquals(emptyList(), QuiDecide.parse(sheetBody).third)
    }

    @Test
    fun aMalformedChainIsReported() {
        fun problems(lines: String) = QuiDecide.parse("::: decideurs\n$lines\n:::").third.joinToString("\n")
        assertTrue("unknown level" in problems("- quartier | maire | X"))
        assertTrue("unknown role" in problems("- commune | prefet | X"))
        assertTrue("niveau | rôles | description" in problems("- commune | maire"))
        assertTrue("twice" in problems("- commune | maire | X\n- commune | maire | Y"))
        assertTrue("closest" in problems("- etat | ministre | X\n- commune | maire | Y"))
        assertTrue("exactly one" in QuiDecide.parse("Pas de bloc.").third.joinToString())
    }

    @Test
    fun aCourseKeepsItsObjectionsForTheEndPage() {
        val parsed = ParcoursFormat.parse(
            "## Étape\n\nTexte.\n\n::: quiz\n? Q ?\n- [x] A\n- [ ] B\n> R.\n:::\n\n::: objections\n? « Objection »\n> Réponse sourcée.\n:::\n\n::: et-maintenant\n- Agir\n:::\n\n## Sources\n\n- S\n",
        )
        assertEquals(emptyList(), parsed.problems)
        assertEquals(listOf(Objection("« Objection »", "Réponse sourcée.")), parsed.objections)
        assertTrue("::: objections" !in parsed.steps[0].body)
    }
}
