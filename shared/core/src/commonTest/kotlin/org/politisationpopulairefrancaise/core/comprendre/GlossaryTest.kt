package org.politisationpopulairefrancaise.core.comprendre

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

private val editor = IdentityKeys.generate().publicKey

/** An entry of the glossary as the content signer builds it (unsigned: the models do not check signatures). */
internal fun glossaryEntry(
    slug: String,
    term: String,
    variants: List<String>,
    definition: String = "Définition de $term.",
    auto: String? = null,
    seeAlso: List<String> = emptyList(),
    related: List<String> = emptyList(),
    body: String = "Explication de $term.\n\n## Sources\n\n- [Légifrance](https://www.legifrance.gouv.fr/)\n",
    updatedAt: Long = 1_794_000_000,
): GlossaryEntry {
    val source = GlossarySource("$slug.md", slug, "[EXEMPLE] $term", definition, variants, seeAlso, related, auto, body)
    val unsigned = Article.build(
        author = editor,
        slug = slug,
        title = source.title,
        summary = definition,
        topics = listOf("institutions"),
        type = ArticleType.GLOSSAIRE,
        origin = ContentOrigin.AI_ASSISTED,
        publishedAt = 1_794_000_000,
        updatedAt = updatedAt,
        body = body,
        extraTags = GlossaryFormat.tags(source, editor.hex),
    )
    val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
    return assertNotNull(GlossaryEntry.from(assertNotNull(Article.from(event))))
}

class GlossaryEntryTest {

    @Test
    fun anEntryIsReadFromItsArticle() {
        val entry = glossaryEntry(
            slug = "motion-de-censure",
            term = "Motion de censure",
            variants = listOf("motion de censure", "motions de censure"),
            definition = "Vote par lequel les députés peuvent renverser le gouvernement.",
            seeAlso = listOf("49-3"),
            related = listOf("parcours:le-49-3-et-la-motion-de-censure"),
            body = "Explication.\n\n::: en-bref\n- Un point.\n:::\n\n## Sources\n\n- [Constitution, article 49](https://www.legifrance.gouv.fr/)\n",
        )
        assertEquals("motion-de-censure", entry.slug)
        assertEquals("Motion de censure", entry.term)
        assertTrue(entry.example)
        assertEquals("Vote par lequel les députés peuvent renverser le gouvernement.", entry.definition)
        assertEquals(listOf("motion de censure", "motions de censure"), entry.variants)
        assertTrue(entry.auto)
        assertEquals(listOf("49-3"), entry.seeAlso)
        assertEquals(listOf("30023:${editor.hex}:le-49-3-et-la-motion-de-censure"), entry.related)
        assertEquals("Explication.\n\n::: en-bref\n- Un point.\n:::", entry.explanation)
        assertEquals("- [Constitution, article 49](https://www.legifrance.gouv.fr/)", entry.sources)
    }

    @Test
    fun otherContentsAndIncompleteEntriesAreNotEntries() {
        val unsigned = Article.build(editor, "fiche", "[EXEMPLE] Fiche", "Résumé", emptyList(), ArticleType.FICHE_PRATIQUE, ContentOrigin.HUMAN, 1, 1, "x")
        val guide = Article.from(SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128)))!!
        assertNull(GlossaryEntry.from(guide))

        val withoutDefinition = Article.build(editor, "terme", "[EXEMPLE] Terme", null, emptyList(), ArticleType.GLOSSAIRE, ContentOrigin.HUMAN, 1, 1, "x")
        val article = Article.from(SignedEvent(withoutDefinition.idHex(), withoutDefinition.pubkey, withoutDefinition.createdAt, withoutDefinition.kind, withoutDefinition.tags, withoutDefinition.content, "0".repeat(128)))!!
        assertNull(GlossaryEntry.from(article))
    }

    @Test
    fun autoNonIsReadAndAnEntryNeverSeesItself() {
        val entry = glossaryEntry("loi", "Loi", listOf("loi"), auto = "non", seeAlso = listOf("loi", "decret"))
        assertFalse(entry.auto)
        assertEquals(listOf("decret"), entry.seeAlso)
    }
}

class GlossaryIndexTest {

    private val loi = glossaryEntry("loi", "Loi", listOf("loi", "lois"))
    private val article493 = glossaryEntry("49-3", "49.3", listOf("49.3", "49-3", "article 49.3", "article 49 alinéa 3"))
    private val motion = glossaryEntry("motion", "Motion", listOf("motion"))
    private val censure = glossaryEntry("motion-de-censure", "Motion de censure", listOf("motion de censure", "motions de censure"))
    private val conseilEtat = glossaryEntry("conseil-d-etat", "Conseil d'État", listOf("Conseil d'État"))
    private val projet = glossaryEntry("projet-de-loi", "Projet de loi", listOf("projet de loi", "projets de loi"))
    private val finances = glossaryEntry("loi-de-finances", "Loi de finances", listOf("loi de finances"))
    private val index = GlossaryIndex(listOf(loi, article493, motion, censure, conseilEtat, projet, finances))

    private fun forms(text: String, index: GlossaryIndex = this.index): List<String> = index.find(text).map { text.substring(it.start, it.end) }

    @Test
    fun aFormIsFoundOnlyBetweenWordLimits() {
        assertEquals(emptyList(), forms("Le plein emploi et les emplois aidés."))
        assertEquals(listOf("loi"), forms("Une loi votée."))
        assertEquals(listOf("Loi"), forms("Loi du 9 décembre 1905."), "case is ignored")
        assertEquals(listOf("LOI"), forms("LA LOI."))
        assertEquals(listOf("loi"), forms("(loi)"))
        assertEquals(emptyList(), forms("loix"), "a letter after the form")
        assertEquals(emptyList(), forms("2loi"), "a digit before the form")
    }

    @Test
    fun theNumberOfThe493IsFoundUnderAllItsForms() {
        val text = "Le 49.3 a servi. Puis le 49-3 encore."
        val matches = index.find(text)
        assertEquals(listOf("49.3", "49-3"), matches.map { text.substring(it.start, it.end) })
        assertEquals(listOf("49-3", "49-3"), matches.map { it.slug })
        assertEquals(listOf("article 49.3"), forms("Selon l'article 49.3 de la Constitution."), "the longest form wins")
        assertEquals(listOf("article 49 alinéa 3"), forms("L'article 49 alinéa 3."))
        assertEquals(emptyList(), forms("149.3 et 49.31"), "digits around the form")
        assertEquals(listOf("49‑3"), forms("Le 49‑3."), "non-breaking hyphen")
        assertEquals(listOf("49.3"), forms("Le 49.3."), "the full stop of the sentence is a limit")
    }

    @Test
    fun theLongestFormWinsAndFormsNeverOverlap() {
        val text = "Une motion de censure, puis une motion simple."
        val matches = index.find(text)
        assertEquals(listOf("motion de censure" to "motion-de-censure", "motion" to "motion"), matches.map { text.substring(it.start, it.end) to it.slug })

        // « projet de loi » starts first: « loi de finances » cannot use its last word.
        val budget = "Le projet de loi de finances."
        assertEquals(listOf("projet de loi"), forms(budget))
        assertEquals(listOf("loi de finances"), forms("La loi de finances."))
    }

    @Test
    fun straightAndCurlyApostrophesAreAlikeButAccentsCount() {
        assertEquals(listOf("Conseil d’État"), forms("Le Conseil d’État a jugé."))
        assertEquals(listOf("conseil d'état"), forms("Le conseil d'état."))
        assertEquals(emptyList(), forms("Le Conseil d'Etat."), "accents are respected")
    }

    @Test
    fun aPluralIsFoundThroughItsVariant() {
        assertEquals(listOf("motions de censure"), forms("Deux motions de censure."))
        assertEquals(listOf("lois"), forms("Les lois."))
        assertEquals(listOf("projets de loi"), forms("Les projets de loi."))
    }

    @Test
    fun spacesOfTheTextMatchTheSpaceOfAForm() {
        assertEquals(listOf("motion de  censure"), forms("Une motion de  censure."))
    }

    @Test
    fun onlyTheFirstOccurrenceOfEachEntryIsLinked() {
        val text = "La loi, encore la loi, et le 49.3 puis les lois."
        val seen = mutableSetOf<String>()
        val first = index.firstOccurrences(text, seen)
        assertEquals(listOf(3 to "loi", 29 to "49-3"), first.map { it.start to it.slug })
        assertEquals(setOf("loi", "49-3"), seen)
    }

    @Test
    fun aSharedScopeLinksEachTermOnceInTheWholeContent() {
        val seen = mutableSetOf<String>()
        val pieces = listOf("Introduction : la loi et la motion de censure.", "Étape 1 : une loi, un 49.3.", "Étape 2 : la motion de censure, le 49-3.")
        val found = pieces.map { piece -> index.firstOccurrences(piece, seen).map { it.slug } }
        assertEquals(listOf(listOf("loi", "motion-de-censure"), listOf("49-3"), emptyList()), found)
    }

    @Test
    fun excludedRangesAndEntriesAreLeftAlone() {
        val text = "La Constitution (https://exemple.invalid/loi) et la loi."
        val link = text.indexOf("(")..text.indexOf(")")
        assertEquals(listOf(text.lastIndexOf("loi")), index.find(text, excluded = listOf(link)).map { it.start })

        // On the page of « Motion de censure », its own term is not linked, nor is « motion » inside it.
        val page = "Une motion de censure, puis une motion simple."
        assertEquals(listOf("motion"), index.find(page, excludedSlugs = setOf("motion-de-censure")).map { it.slug })
        assertEquals(page.lastIndexOf("motion"), index.find(page, excludedSlugs = setOf("motion-de-censure")).single().start)
    }

    @Test
    fun anEntryMarkedAutoNonIsListedButNeverSpotted() {
        val common = glossaryEntry("gouvernement", "Gouvernement", listOf("gouvernement"), auto = "non")
        val withIt = GlossaryIndex(listOf(common, loi))
        assertEquals(emptyList(), withIt.find("Le gouvernement propose.").map { it.slug })
        assertEquals(listOf("gouvernement", "loi"), withIt.entries.map { it.slug })
        assertEquals(listOf("gouvernement"), withIt.search("gouv").map { it.slug })
    }

    @Test
    fun entriesAreInAlphabeticalOrderWithTheirLetter() {
        val etat = glossaryEntry("etat-d-urgence", "État d'urgence", listOf("état d'urgence"))
        val abstention = glossaryEntry("abstention", "Abstention", listOf("abstention"))
        val all = GlossaryIndex(listOf(loi, etat, article493, abstention, censure))
        assertEquals(listOf("49-3", "abstention", "etat-d-urgence", "loi", "motion-de-censure"), all.entries.map { it.slug })
        assertEquals(listOf("0-9", "A", "E", "L", "M"), all.entries.map(GlossaryIndex::letterOf))
    }

    @Test
    fun theSearchIgnoresAccentsAndCaseAndLooksAtDefinitions() {
        val senat = glossaryEntry("senat", "Sénat", listOf("Sénat"), definition = "La seconde chambre du Parlement.")
        val all = GlossaryIndex(listOf(senat, censure, article493))
        assertEquals(listOf("senat"), all.search("SENAT").map { it.slug })
        assertEquals(listOf("49-3"), all.search("49").map { it.slug })
        assertEquals(listOf("motion-de-censure"), all.search("Motions cens").map { it.slug })
        assertEquals(listOf("senat"), all.search("chambre").map { it.slug }, "the definition is searched too")
        assertEquals(all.entries, all.search("  "))
        assertEquals(emptyList(), all.search("zzz"))
    }

    @Test
    fun theNewestVersionOfAnEntryWins() {
        val old = glossaryEntry("loi", "Loi", listOf("loi"), definition = "Ancienne.", updatedAt = 1)
        val new = glossaryEntry("loi", "Loi", listOf("loi", "lois"), definition = "Nouvelle.", updatedAt = 2)
        val both = GlossaryIndex(listOf(new, old))
        assertEquals(1, both.size)
        assertEquals("Nouvelle.", both.entry("loi")?.definition)
        assertEquals(listOf("lois"), forms("Les lois.", both))
    }

    @Test
    fun aLongTextIsReadQuickly() {
        val many = (1..60).map { glossaryEntry("terme-$it", "Terme $it", listOf("terme numéro $it", "termes numéro $it")) } + listOf(loi, censure, article493)
        val big = GlossaryIndex(many)
        val text = buildString {
            repeat(1_000) { append("Le gouvernement engage la loi et la motion de censure au terme numéro ${it % 70} du débat. ") }
        }
        val mark = TimeSource.Monotonic.markNow()
        val found = big.find(text)
        assertTrue(mark.elapsedNow().inWholeMilliseconds < 2_000, "a text of ${text.length} characters took ${mark.elapsedNow()}")
        // Each sentence holds « loi », « motion de censure » and, for 60 sentences out of 70, a « terme numéro ».
        assertEquals(2_000 + 14 * 60 + 19, found.size)
        assertEquals(62, big.firstOccurrences(text, mutableSetOf()).size)
    }

    @Test
    fun anAcronymKeepsItsCapitalsAndTheOtherFormsDoNot() {
        val ue = glossaryEntry("union-europeenne", "Union européenne", listOf("Union européenne", "UE"))
        val qpc = glossaryEntry("qpc", "Question prioritaire de constitutionnalité", listOf("question prioritaire de constitutionnalité", "QPC"))
        val g7 = glossaryEntry("g7", "G7", listOf("G7"))
        val all = GlossaryIndex(listOf(ue, qpc, g7))
        assertTrue(GlossaryIndex.isAcronym("UE"))
        assertTrue(GlossaryIndex.isAcronym("EPCI"))
        assertTrue(GlossaryIndex.isAcronym("COP21"))
        assertFalse(GlossaryIndex.isAcronym("G7"), "one letter only")
        assertFalse(GlossaryIndex.isAcronym("Union européenne"))
        assertFalse(GlossaryIndex.isAcronym("49.3"))
        assertEquals(listOf("UE", "union européenne"), forms("L'UE, c'est l'union européenne.", all))
        assertEquals(emptyList(), forms("Une ue, une Ue, une uE.", all), "an acronym keeps its capitals")
        assertEquals(listOf("QPC", "Question prioritaire de constitutionnalité"), forms("Une QPC. Question prioritaire de constitutionnalité.", all))
        assertEquals(emptyList(), forms("une qpc", all))
        assertEquals(listOf("g7", "G7"), forms("Le g7 et le G7.", all), "a form with one letter is compared without case")
        assertEquals(listOf("UE"), forms("Les pays de l’UE.", all))
    }

    @Test
    fun aFormMayHoldAComma() {
        val cese = glossaryEntry("cese", "CESE", GlossaryFormat.list("Conseil économique\\, social et environnemental, CESE"))
        val alinea = glossaryEntry("49-3", "49.3", GlossaryFormat.list("49.3, article 49\\, alinéa 3"))
        val all = GlossaryIndex(listOf(cese, alinea))
        assertEquals(listOf("Conseil économique, social et environnemental", "CESE"), cese.variants)
        assertEquals(listOf("Conseil économique, social et environnemental", "CESE"), forms("Le Conseil économique, social et environnemental (CESE).", all))
        assertEquals(listOf("article 49, alinéa 3"), forms("Selon l'article 49, alinéa 3, de la Constitution.", all))
    }

    @Test
    fun anAddressWrittenOutInTheTextIsLeftAlone() {
        val text = "Voir https://exemple.invalid/loi, http://loi.exemple.invalid, www.loi.fr, WWW.LOI.FR et ppf:loi : la loi."
        assertEquals(listOf(text.lastIndexOf("loi")), index.find(text).map { it.start })
        assertEquals(listOf("loi"), forms("(https://exemple.invalid/a) et la loi."))
        assertEquals(listOf("loi"), forms("La loi."), "no address, nothing left out")
    }

    @Test
    fun anEmptyGlossaryFindsNothing() {
        assertTrue(GlossaryIndex.EMPTY.isEmpty)
        assertEquals(emptyList(), GlossaryIndex.EMPTY.find("La loi."))
    }
}

class GlossaryFormatTest {

    private fun source(
        slug: String,
        variants: List<String> = listOf(slug),
        summary: String? = "Une définition courte.",
        seeAlso: List<String> = emptyList(),
        related: List<String> = emptyList(),
        auto: String? = null,
        body: String = "Explication.\n\n## Sources\n\n- Constitution\n",
    ) = GlossarySource("$slug.md", slug, "[EXEMPLE] ${slug.replaceFirstChar { it.uppercase() }}", summary, variants, seeAlso, related, auto, body)

    private val known = mapOf("parcours" to setOf("le-49-3-et-la-motion-de-censure"), "qui-decide" to setOf("ecole"))

    @Test
    fun aWellFormedGlossaryHasNoProblem() {
        val sources = listOf(
            source("motion-de-censure", listOf("motion de censure", "motions de censure"), seeAlso = listOf("dissolution"), related = listOf("parcours:le-49-3-et-la-motion-de-censure", "qui-decide:ecole")),
            source("dissolution", listOf("dissolution"), auto = "non"),
        )
        assertEquals(emptyList(), GlossaryFormat.problems(sources, known))
        val tags = GlossaryFormat.tags(sources[0], "ab".repeat(32))
        assertEquals(
            listOf(
                listOf("ppf-variant", "motion de censure"),
                listOf("ppf-variant", "motions de censure"),
                listOf("a", "30023:${"ab".repeat(32)}:dissolution", "", "voir-aussi"),
                listOf("a", "30023:${"ab".repeat(32)}:le-49-3-et-la-motion-de-censure", "", "lie"),
                listOf("a", "30023:${"ab".repeat(32)}:ecole", "", "lie"),
            ),
            tags,
        )
        assertEquals(listOf(listOf("ppf-variant", "dissolution"), listOf("ppf-auto", "non")), GlossaryFormat.tags(sources[1], "ab".repeat(32)))
        assertEquals(listOf("a", "b c"), GlossaryFormat.list(" a , b c ,, "))
    }

    private fun problemsOf(vararg sources: GlossarySource): List<String> = GlossaryFormat.problems(sources.toList(), known)

    @Test
    fun theDefinitionIsRequiredAndShort() {
        assertTrue(problemsOf(source("a", summary = null)).single().contains("no summary"))
        assertTrue(problemsOf(source("a", summary = "  ")).single().contains("no summary"))
        assertEquals(emptyList(), problemsOf(source("a", summary = "x".repeat(200))))
        assertTrue(problemsOf(source("a", summary = "x".repeat(201))).single().contains("201 characters"))
    }

    @Test
    fun formsAreRequiredAndBelongToOneEntry() {
        assertTrue(problemsOf(source("a", variants = emptyList())).single().contains("no variants"))
        assertTrue(problemsOf(source("a", variants = listOf("loi", "Loi"))).single().contains("appears twice"))
        val clash = problemsOf(source("conseil", variants = listOf("Conseil d'État")), source("conseil-etat", variants = listOf("conseil d’état")))
        assertTrue(clash.single().contains("already a variant of conseil.md"), clash.toString())
        assertTrue(problemsOf(source("a", variants = listOf("« loi »"))).single().contains("start and end with a letter or a digit"))
    }

    @Test
    fun aCommaInsideAFormIsEscaped() {
        assertEquals(listOf("Conseil économique, social et environnemental", "CESE"), GlossaryFormat.list("Conseil économique\\, social et environnemental, CESE"))
        assertEquals(listOf("49.3", "article 49, alinéa 3"), GlossaryFormat.list(" 49.3 ,article 49\\, alinéa 3 "))
        assertEquals(listOf("a\\b"), GlossaryFormat.list("a\\b"), "a backslash before anything else stays")
        assertEquals(emptyList(), problemsOf(source("cese", variants = GlossaryFormat.list("Conseil économique\\, social et environnemental, CESE"))))
        val tags = GlossaryFormat.tags(source("cese", variants = GlossaryFormat.list("Conseil économique\\, social et environnemental")), "ab".repeat(32))
        assertEquals(listOf("ppf-variant", "Conseil économique, social et environnemental"), tags.first())
    }

    @Test
    fun anAcronymAndTheSameWordInSmallLettersBelongToOneEntry() {
        assertTrue(problemsOf(source("ue", variants = listOf("UE")), source("ue-bis", variants = listOf("ue"))).single().contains("already a variant of ue.md"))
    }

    @Test
    fun sourcesAreRequired() {
        assertTrue(problemsOf(source("a", body = "Explication sans sources.")).single().contains("## Sources"))
    }

    @Test
    fun linksMustNameExistingEntriesAndContents() {
        assertTrue(problemsOf(source("a", seeAlso = listOf("inconnu"))).single().contains("unknown entry: inconnu"))
        assertTrue(problemsOf(source("a", seeAlso = listOf("a"))).single().contains("the entry itself"))
        assertTrue(problemsOf(source("a", related = listOf("parcours:inconnu"))).single().contains("unknown content: parcours:inconnu"))
        assertTrue(problemsOf(source("a", related = listOf("ecole"))).single().contains("<kind>:<slug>"))
        assertTrue(problemsOf(source("a", related = listOf("voir:ecole"))).single().contains("<kind>:<slug>"))
    }

    @Test
    fun slugsAreUniqueAndAutoIsOuiOrNon() {
        assertTrue(problemsOf(source("a", variants = listOf("x")), source("a", variants = listOf("y"))).single().contains("used twice"))
        assertTrue(problemsOf(source("a", auto = "peut-être")).single().contains("auto"))
        assertEquals(emptyList(), problemsOf(source("a", auto = "oui")))
    }
}
