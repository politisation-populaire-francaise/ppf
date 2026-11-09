package org.politisationpopulairefrancaise.core.voir

import org.politisationpopulairefrancaise.core.editorial.Article
import org.politisationpopulairefrancaise.core.editorial.ArticleType
import org.politisationpopulairefrancaise.core.editorial.ContentOrigin
import org.politisationpopulairefrancaise.core.editorial.schema.CheckContext
import org.politisationpopulairefrancaise.core.editorial.schema.ContentDraft
import org.politisationpopulairefrancaise.core.editorial.schema.EditorialEngine
import org.politisationpopulairefrancaise.core.identity.IdentityKeys
import org.politisationpopulairefrancaise.core.signing.SignedEvent
import org.politisationpopulairefrancaise.core.time.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Transparence de PPF » (PP-01, D-104): four rubrics, the same every year; functions before names; the result computed. */
class TransparencyTest {
    private val keys = IdentityKeys.generate()
    private val context = CheckContext.of(keys.publicKey.hex, emptyMap())

    private fun page(slug: String, title: String, vararg tags: List<String>): Article {
        val unsigned = Article.build(keys.publicKey, slug, title, null, emptyList(), ArticleType.TRANSPARENCE, ContentOrigin.HUMAN, 1_793_000_000, 1_794_000_000, "[EXEMPLE] Corps.", extraTags = tags.toList())
        val event = SignedEvent(unsigned.idHex(), unsigned.pubkey, unsigned.createdAt, unsigned.kind, unsigned.tags, unsigned.content, "0".repeat(128))
        return Article.from(event)!!
    }

    private fun rubric(value: String) = listOf(VoirTags.RUBRIC, value)

    @Test
    fun anInstanceGivesItsFunctionsAndANameOnlyWhenWritten() {
        val bureau = assertNotNull(
            TransparencyEntry.from(
                page(
                    "bureau", "[EXEMPLE] Le bureau", rubric("instance"), listOf(VoirTags.INSTANCE, "bureau"),
                    listOf(VoirTags.SEAT, "Présidence", "", "2027-03-01", "Élue par le conseil d'administration"),
                    listOf(VoirTags.SEAT, "Trésorerie", "[EXEMPLE] Camille Exemple", "2027-03-01", ""),
                    listOf(VoirTags.SEAT, "", "Sans fonction"),
                ),
            ),
        )
        assertEquals(PpfInstance.BUREAU, bureau.instance)
        assertEquals(listOf("Présidence", "Trésorerie"), bureau.seats.map { it.function }, "a seat without function is left out")
        assertNull(bureau.seats[0].name, "no name unless written")
        assertEquals("[EXEMPLE] Camille Exemple", bureau.seats[1].name)
        assertEquals(CivilDate(2027, 3, 1), bureau.seats[0].since)
        assertNull(TransparencyEntry.from(page("sans-instance", "[EXEMPLE] Sans instance", rubric("instance"))), "an instance needs its code")
    }

    @Test
    fun eachRubricHasWhatItCannotDoWithout() {
        assertNull(TransparencyEntry.from(page("d", "[EXEMPLE] Décision sans date", rubric("decision"))))
        assertNull(TransparencyEntry.from(page("c", "[EXEMPLE] Comptes sans exercice", rubric("comptes"))))
        assertNull(TransparencyEntry.from(page("r", "[EXEMPLE] Rapport sans période", rubric("rapport"))))
        assertNull(TransparencyEntry.from(page("x", "[EXEMPLE] Rubrique inconnue", rubric("autre"))))
        val decision = assertNotNull(
            TransparencyEntry.from(
                page("d2", "[EXEMPLE] Adoption de la charte", rubric("decision"), listOf(VoirTags.INSTANCE, "assemblee-generale"), listOf(VoirTags.DATE, "2027-04-02"), listOf(VoirTags.TALLY, "41", "3", "2")),
            ),
        )
        assertEquals(Tally(41, 3, 2), decision.tally)
        assertNull(TransparencyEntry.from(page("d3", "[EXEMPLE] Vote illisible", rubric("decision"), listOf(VoirTags.DATE, "2027-04-02"), listOf(VoirTags.TALLY, "41", "beaucoup", "2")))?.tally)
    }

    @Test
    fun theAccountsComputeTheirResultAndTheReportKeepsOnlyKnownFigures() {
        val accounts = assertNotNull(
            TransparencyEntry.from(
                page(
                    "comptes-2027", "[EXEMPLE] Comptes 2027", rubric("comptes"), listOf(VoirTags.YEAR, "2027"), listOf(VoirTags.APPROVED, "2028-04-10"),
                    listOf(VoirTags.ACCOUNT_LINE, "produit", "Cotisations", "12 340,50"),
                    listOf(VoirTags.ACCOUNT_LINE, "produit", "Dons", "8 000"),
                    listOf(VoirTags.ACCOUNT_LINE, "charge", "Serveur et noms de domaine", "300"),
                    listOf(VoirTags.ACCOUNT_LINE, "charge", "Négatif", "-5"),
                ),
            ),
        )
        assertEquals(2027, accounts.year)
        assertEquals(3, accounts.lines.size, "a negative amount is left out")
        assertEquals(2_034_050L, accounts.incomeCents)
        assertEquals(30_000L, accounts.expenseCents)
        assertEquals(2_004_050L, accounts.resultCents)

        val report = assertNotNull(
            TransparencyEntry.from(
                page(
                    "rapport-2027", "[EXEMPLE] Rapport 2027", rubric("rapport"), listOf(VoirTags.PERIOD, "2027-01-01", "2027-12-31"),
                    listOf(VoirTags.FIGURE, "signalements-recus", "120"),
                    listOf(VoirTags.FIGURE, "delai-median-heures", "36,5"),
                    listOf(VoirTags.FIGURE, "precision-automatique-pourcent", "140"),
                    listOf(VoirTags.FIGURE, "code-inconnu", "3"),
                ),
            ),
        )
        assertEquals(mapOf(ReportFigure.REPORTS_RECEIVED to 120.0, ReportFigure.MEDIAN_DELAY_HOURS to 36.5), report.figures, "a percentage above 100 and an unknown code are left out")
        assertTrue(ReportFigure.REPORTS_RECEIVED.moderation)
        assertTrue(!ReportFigure.CLAIMS_RECEIVED.moderation)
    }

    @Test
    fun thePagesKeepTheSameOrderEveryYearAndStayOutOfTheAxes() {
        val catalog = VoirCatalog.from(
            listOf(
                page("equipe", "[EXEMPLE] L'équipe éditoriale", rubric("instance"), listOf(VoirTags.INSTANCE, "equipe-editoriale")),
                page("ag", "[EXEMPLE] L'assemblée générale", rubric("instance"), listOf(VoirTags.INSTANCE, "assemblee-generale")),
                page("d1", "[EXEMPLE] Première décision", rubric("decision"), listOf(VoirTags.DATE, "2027-01-10")),
                page("d2", "[EXEMPLE] Seconde décision", rubric("decision"), listOf(VoirTags.DATE, "2027-06-10")),
                page("c27", "[EXEMPLE] Comptes 2027", rubric("comptes"), listOf(VoirTags.YEAR, "2027")),
                page("c28", "[EXEMPLE] Comptes 2028", rubric("comptes"), listOf(VoirTags.YEAR, "2028")),
            ),
        )
        val pages = catalog.transparency
        assertEquals(listOf(PpfInstance.GENERAL_ASSEMBLY, PpfInstance.EDITORIAL_TEAM), pages.instances.map { it.instance })
        assertEquals(listOf("d2", "d1"), pages.decisions.map { it.article.slug })
        assertEquals(listOf(2028, 2027), pages.accounts.map { it.year })
        assertTrue(catalog.isEmpty, "never a block of the axes of Voir")
        assertTrue(ArticleType.TRANSPARENCE in VoirCatalog.TYPES)
    }

    @Test
    fun theSchemaWritesWhatTheReaderKeeps() {
        val entries = listOf(
            "slug" to "comptes-2027",
            "title" to "[EXEMPLE] Comptes 2027",
            "type" to "transparence",
            "origin" to "human",
            "published_at" to "2028-04-11",
            "updated_at" to "2028-04-11",
            "rubric" to "comptes",
            "year" to "2027",
            "approved" to "2028-04-10",
            "account-line" to "produit | Cotisations | 12 340,50",
            "account-line" to "charge | Serveur et noms de domaine | 300",
            "archive" to "${"a".repeat(64)} | opentimestamps | https://example.org/exemple/comptes-2027.pdf | 2028-04-11",
        )
        val draft = ContentDraft(ArticleType.TRANSPARENCE, entries, "[EXEMPLE] Les comptes de l'exercice.\n")
        assertEquals(emptyList(), EditorialEngine.check(VoirSchemas.TRANSPARENCE, draft, context).map { it.code })
        val decisionWithoutDate = ContentDraft(ArticleType.TRANSPARENCE, entries.map { if (it.first == "rubric") "rubric" to "decision" else it }, "[EXEMPLE] Corps.\n")
        assertEquals(listOf(VoirSchemas.Rules.UNREADABLE), EditorialEngine.check(VoirSchemas.TRANSPARENCE, decisionWithoutDate, context).map { it.code })
        val twice = ContentDraft(ArticleType.TRANSPARENCE, entries.map { if (it.first == "rubric") "rubric" to "rapport" else it } + listOf("period" to "2027-01-01 | 2027-12-31", "figure" to "signalements-recus | 1", "figure" to "signalements-recus | 2"), "[EXEMPLE] Corps.\n")
        assertTrue(VoirSchemas.Rules.DUPLICATE in EditorialEngine.check(VoirSchemas.TRANSPARENCE, twice, context).map { it.code })
    }

    @Test
    fun theBoardAndTheBureauAreAlwaysNamedTheOtherTeamsOnlyOnRequest() {
        val instance = listOf(
            "slug" to "bureau",
            "title" to "[EXEMPLE] Le bureau",
            "type" to "transparence",
            "origin" to "human",
            "published_at" to "2027-01-16",
            "updated_at" to "2027-01-16",
            "rubric" to "instance",
            "instance" to "bureau",
            "seat" to "Présidence | [EXEMPLE] Camille Exemple | 2027-01-15 | élue par le conseil d'administration",
            "seat" to "Trésorerie | | 2027-01-15 | élue par le conseil d'administration",
        )
        fun codes(entries: List<Pair<String, String>>) =
            EditorialEngine.check(VoirSchemas.TRANSPARENCE, ContentDraft(ArticleType.TRANSPARENCE, entries, "[EXEMPLE] Corps.\n"), context)

        val unnamed = codes(instance)
        assertEquals(listOf(VoirSchemas.Rules.SEAT_NAME), unnamed.map { it.code }, "a member of the bureau without a name is refused")
        assertEquals(1, unnamed.single().occurrence, "the problem points at the seat without a name")
        val board = instance.map { if (it.first == "instance") "instance" to "conseil-administration" else it }
        assertEquals(listOf(VoirSchemas.Rules.SEAT_NAME), codes(board).map { it.code }, "the same for the board")
        val moderation = instance.map { if (it.first == "instance") "instance" to "equipe-moderation" else it }
        assertEquals(emptyList(), codes(moderation).map { it.code }, "the other teams: functions only by default")
        assertEquals(emptyList(), codes(instance.filterNot { it.first == "seat" && it.second.startsWith("Trésorerie") }).map { it.code })
        assertEquals(setOf(PpfInstance.BOARD, PpfInstance.BUREAU), PpfInstance.NAMED)
    }
}
